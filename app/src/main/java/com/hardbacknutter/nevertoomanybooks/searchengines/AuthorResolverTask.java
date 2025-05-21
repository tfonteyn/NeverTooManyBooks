/*
 * @Copyright 2018-2025 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */

package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.util.logger.LoggerFactory;

public class AuthorResolverTask
        extends MTask<Boolean> {

    private static final String TAG = "AuthorResolverTask";

    @Nullable
    private List<AuthorResolver> resolvers;
    @Nullable
    private List<Author> authors;
    private boolean storeModifications;

    public AuthorResolverTask() {
        super(R.id.TASK_ID_AUTHOR_RESOLVER, TAG);
    }

    /**
     * Start the task.
     *
     * @param context            Current context
     * @param engineId           the source for the resolvers to use
     * @param authors            to update
     * @param storeModifications flag;
     *                           {@code true} to write all modifications directly to the database,
     *                           {@code false} not to.
     */
    @UiThread
    public void start(@NonNull final Context context,
                      @NonNull final EngineId engineId,
                      @NonNull final List<Author> authors,
                      final boolean storeModifications) {
        this.authors = authors;
        this.storeModifications = storeModifications;

        final SearchEngine searchEngine = engineId.createSearchEngine(context);
        searchEngine.setCaller(this);
        this.resolvers = AuthorResolverFactory.getResolvers(context, searchEngine);
        execute();
    }

    /**
     * Run the resolvers.
     * <p>
     * Any {@link SearchException} or {@code DaoWriteException} will cause an abort.
     * All database writes happen in a transaction which will be aborted in this case,
     * but the authors in the list authors may have been modified!
     * <strong>ALL results should be discarded in this case</strong>
     *
     * @return {@code true} if the any {@link Author}s were modified; {@code false} otherwise
     *
     * @throws CredentialsException on authentication/login failures
     */
    @NonNull
    @Override
    protected Boolean doWork()
            throws CancellationException,
                   CredentialsException {
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final SynchronizedDb db = serviceLocator.getDb();
        final Context context = serviceLocator.getLocalizedAppContext();
        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        boolean result = false;
        Synchronizer.SyncLock txLock = null;
        try {
            if (storeModifications) {
                txLock = db.beginTransaction(true);
            }
            // loop Authors first, this way we don't hit a single resolver
            // continuously (well... if we use more than one resolver at least)
            //noinspection DataFlowIssue
            for (final Author author : authors) {
                //noinspection DataFlowIssue
                for (final AuthorResolver resolver : resolvers) {
                    final boolean modified = resolver.resolve(context, author);
                    if (modified && storeModifications) {
                        serviceLocator.getAuthorDao().update(context, author, locale);
                    }

                    result = modified || result;
                }
            }

            if (storeModifications) {
                db.setTransactionSuccessful();
            }
        } catch (@NonNull final SearchException | DaoWriteException e) {
            LoggerFactory.getLogger().e(TAG, e);
        } finally {
            if (storeModifications) {
                db.endTransaction(txLock);
            }
        }

        return result;
    }
}
