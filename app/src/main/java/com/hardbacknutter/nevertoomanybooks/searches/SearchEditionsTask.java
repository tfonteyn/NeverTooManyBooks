/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

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
     * @param isbnStr to search for, <strong>must</strong> be valid.
     */
    @UiThread
    public void startTask(@NonNull final String isbnStr) {
        // sanity check
        if (BuildConfig.DEBUG /* always */) {
            if (!ISBN.isValidIsbn(isbnStr)) {
                throw new IllegalStateException("isbn must be valid");
            }
        }

        mIsbn = isbnStr;

        execute(R.id.TASK_ID_SEARCH_EDITIONS);
    }

    @Override
    @NonNull
    @WorkerThread
    protected Collection<String> doWork() {
        Thread.currentThread().setName(TAG + mIsbn);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        // keep the order, but eliminate duplicates.
        final Collection<String> isbnList = new LinkedHashSet<>();
        // Always add the original isbn!
        isbnList.add(mIsbn);

        final List<Site> sites = SiteList.getList(SiteList.Type.AltEditions)
                                         .getEnabledSites();
        for (Site site : sites) {
            final SearchEngine searchEngine = site.getSearchEngine(this);
            try {
                // can we reach the site at all ?
                NetworkUtils.ping(context, searchEngine.getSiteUrl());

                isbnList.addAll(((SearchEngine.AlternativeEditions) searchEngine)
                                        .searchAlternativeEditions(mIsbn));

            } catch (@NonNull final CredentialsException | IOException | RuntimeException e) {
                // Silently ignore individual failures, we'll return what we get from
                // the sites that worked.
                Logger.error(context, TAG, e);

            }
        }
        return isbnList;
    }
}
