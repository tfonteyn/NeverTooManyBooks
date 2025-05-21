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
import java.util.concurrent.CancellationException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
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

    public AuthorResolverTask() {
        super(R.id.TASK_ID_AUTHOR_RESOLVER, TAG);
    }

    @UiThread
    public void start(@NonNull final Context context,
                      @NonNull final EngineId engineId,
                      @NonNull final List<Author> authors) {
        this.authors = authors;

        final SearchEngine searchEngine = engineId.createSearchEngine(context);
        searchEngine.setCaller(this);
        this.resolvers = AuthorResolverFactory.getResolvers(context, searchEngine);
        execute();
    }

    /**
     * Run the resolvers.
     * <p>
     * Any {@link SearchException} will cause an abort.
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
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        boolean result = false;
        try {
            // loop Authors first, this way we don't hit a single resolver
            // continuously (well... if we use more than one resolver at least)
            //noinspection DataFlowIssue
            for (final Author author : authors) {
                //noinspection DataFlowIssue
                for (final AuthorResolver resolver : resolvers) {
                    result = resolver.resolve(context, author) || result;
                }
            }
        } catch (@NonNull final SearchException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }

        return result;
    }
}
