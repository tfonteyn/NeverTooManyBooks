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
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

public class AuthorResolverTask
        extends MTask<Boolean> {

    private static final String TAG = "AuthorResolverTask";

    @Nullable
    private List<Author> authors;
    private boolean storeModifications;
    private boolean mergeWithDatabase;
    private SearchEngine searchEngine;

    public AuthorResolverTask() {
        super(R.id.TASK_ID_AUTHOR_RESOLVER, TAG);
    }

    /**
     * Start the task.
     *
     * @param context            Current context
     * @param engineId           the source for the resolvers to use
     * @param authors            to update
     * @param mergeWithDatabase  flag;
     *                           {@code true} to force a lookup/merge with the database BEFORE
     *                           resolving an author. {@code false} to skip.
     * @param storeModifications flag;
     *                           {@code true} to write all modifications directly to the database,
     *                           {@code false} not to.
     */
    @UiThread
    public void start(@NonNull final Context context,
                      @NonNull final EngineId engineId,
                      @NonNull final List<Author> authors,
                      final boolean mergeWithDatabase,
                      final boolean storeModifications) {
        this.authors = authors;
        this.storeModifications = storeModifications;
        this.mergeWithDatabase = mergeWithDatabase;

        searchEngine = engineId.createSearchEngine(context);
        searchEngine.setCaller(this);

        execute();
    }

    /**
     * Run the resolvers.
     * <p>
     * See {@link AuthorResolverHelper} for detailed docs.
     *
     * @return {@code true} if the any {@link Author}s were modified; {@code false} otherwise
     *
     * @throws CredentialsException on authentication/login failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws DaoWriteException    on failure
     */
    @Override
    @WorkerThread
    @NonNull
    protected Boolean doWork()
            throws CancellationException,
                   CredentialsException, SearchException, DaoWriteException {

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        //noinspection DataFlowIssue
        return new AuthorResolverHelper()
                .resolve(context, searchEngine, locale, authors,
                         mergeWithDatabase, storeModifications);
    }
}
