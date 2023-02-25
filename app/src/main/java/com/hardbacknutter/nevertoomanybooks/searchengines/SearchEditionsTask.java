/*
 * @Copyright 2018-2023 HardBackNutter
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
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;

/**
 * Fetch alternative edition isbn numbers.
 * <p>
 * The sites are contacted one by one, in the order as set in user preferences.
 */
public class SearchEditionsTask
        extends MTask<Collection<String>> {

    /** Log tag. */
    private static final String TAG = "SearchEditionsTask";
    /** the book to look up. */
    private String isbn;

    public SearchEditionsTask() {
        super(R.id.TASK_ID_SEARCH_EDITIONS, TAG);
    }

    /**
     * Start the task.
     *
     * @param validIsbn to search for, <strong>must</strong> be valid.
     */
    @UiThread
    public void search(@NonNull final String validIsbn) {
        // sanity check
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValidIsbn(validIsbn);
        }

        isbn = validIsbn;

        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected Collection<String> doWork()
            throws NetworkUnavailableException {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();


        // keep the order, but eliminate duplicates.
        final Collection<String> isbnList = new LinkedHashSet<>();
        // Always add the original isbn!
        isbnList.add(isbn);

        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable(context)) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        Site.Type.AltEditions
                .getSites()
                .stream()
                .filter(Site::isActive)
                .map(site -> site.getEngineId().createSearchEngine(context))
                .forEach(searchEngine -> {
                    searchEngine.setCaller(this);
                    try {
                        // can we reach the site ?
                        searchEngine.ping();

                        isbnList.addAll(((SearchEngine.AlternativeEditions) searchEngine)
                                                .searchAlternativeEditions(context, isbn));

                    } catch (@NonNull final IOException | CredentialsException |
                                            SearchException
                                            | RuntimeException e) {
                        // Silently ignore individual failures,
                        // we'll return what we get from the sites that worked.
                        if (BuildConfig.DEBUG /* always */) {
                            LoggerFactory.getLogger()
                                         .e(TAG, e, "searchEngine="
                                                    + searchEngine.getName(context));
                        }
                    }
                });
        return isbnList;
    }
}
