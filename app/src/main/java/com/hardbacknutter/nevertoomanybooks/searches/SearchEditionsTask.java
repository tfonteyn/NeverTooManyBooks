/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * Fetch alternative edition isbn's.
 * <p>
 * The sites are contacted one by one, in the order as set in user preferences.
 */
public class SearchEditionsTask
        extends VMTask<Collection<String>> {

    /** Log tag. */
    private static final String TAG = "SearchEditionsTask";
    /** the book to look up. */
    private String mIsbn;

    /**
     * Start the task.
     *
     * @param validIsbn to search for, <strong>must</strong> be valid.
     */
    @UiThread
    public void search(@NonNull final String validIsbn) {
        // sanity check
        if (BuildConfig.DEBUG /* always */) {
            ISBN.requireValidIsbn(validIsbn);
        }

        mIsbn = validIsbn;

        execute(R.id.TASK_ID_SEARCH_EDITIONS);
    }

    @NonNull
    @Override
    @WorkerThread
    protected Collection<String> doWork(@NonNull final Context context) {
        Thread.currentThread().setName(TAG + mIsbn);

        // keep the order, but eliminate duplicates.
        final Collection<String> isbnList = new LinkedHashSet<>();
        // Always add the original isbn!
        isbnList.add(mIsbn);

        for (final Site site : Site.filterForEnabled(Site.Type.AltEditions.getSites())) {
            final SearchEngine searchEngine = site.getSearchEngine(context, this);
            try {
                // can we reach the site at all ?
                NetworkUtils.ping(context, searchEngine.getSiteUrl());

                isbnList.addAll(((SearchEngine.AlternativeEditions) searchEngine)
                                        .searchAlternativeEditions(mIsbn));

            } catch (@NonNull final IOException | GeneralParsingException | RuntimeException e) {
                // Silently ignore individual failures, we'll return what we get from
                // the sites that worked.
                Logger.error(context, TAG, e);

            }
        }
        return isbnList;
    }
}
