/*
 * @Copyright 2018-2022 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

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
    protected Collection<String> doWork(@NonNull final Context context)
            throws NetworkUnavailableException {

        // keep the order, but eliminate duplicates.
        final Collection<String> isbnList = new LinkedHashSet<>();
        // Always add the original isbn!
        isbnList.add(isbn);

        if (!NetworkUtils.isNetworkAvailable(context)) {
            throw new NetworkUnavailableException(this.getClass().getName());
        }

        for (final Site site : Site.filterActive(Site.Type.AltEditions.getSites())) {
            final SearchEngine searchEngine = site.getSearchEngine();
            searchEngine.setCaller(this);
            try {
                // can we reach the site ?
                searchEngine.ping();

                isbnList.addAll(((SearchEngine.AlternativeEditions) searchEngine)
                                        .searchAlternativeEditions(context, isbn));

            } catch (@NonNull final IOException | CredentialsException | SearchException
                    | RuntimeException e) {
                // Silently ignore individual failures,
                // we'll return what we get from the sites that worked.
                if (BuildConfig.DEBUG /* always */) {
                    ServiceLocator.getInstance().getLogger().d(TAG, e, "site=" + site);
                }
            }
        }
        return isbnList;
    }
}
