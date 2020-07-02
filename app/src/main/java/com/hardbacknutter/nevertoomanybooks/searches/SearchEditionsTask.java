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
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;

/**
 * Fetch alternative edition isbn's.
 * <p>
 * The sites are contacted one by one, in the order as set in user preferences.
 */
public class SearchEditionsTask
        extends TaskBase<Collection<String>> {

    /** Log tag. */
    private static final String TAG = "SearchEditionsTask";
    /** the book to look up. */
    @NonNull
    private final String mIsbn;

    /**
     * Constructor.
     *
     * @param taskId       identifier
     * @param isbnStr      to search for, <strong>must</strong> be valid.
     * @param taskListener to send results to
     */
    @UiThread
    public SearchEditionsTask(final int taskId,
                              @NonNull final String isbnStr,
                              @NonNull final TaskListener<Collection<String>> taskListener) {
        super(taskId, taskListener);

        // sanity check
        if (BuildConfig.DEBUG /* always */) {
            if (!ISBN.isValidIsbn(isbnStr)) {
                throw new IllegalStateException("isbn must be valid");
            }
        }

        mIsbn = isbnStr;
    }

    @Override
    @NonNull
    @WorkerThread
    protected Collection<String> doInBackground(@Nullable final Void... voids) {
        Thread.currentThread().setName(TAG + mIsbn);
        final Context context = App.getTaskContext();

        final Locale locale = LocaleUtils.getUserLocale(context);
        // keep the order, but eliminate duplicates.
        final Collection<String> editions = new LinkedHashSet<>();
        final List<Site> sites = SiteList.getList(context, locale, SiteList.Type.AltEditions)
                                         .getSites(true);
        for (Site site : sites) {
            final SearchEngine searchEngine = site.getSearchEngine(this);
            try {
                // can we reach the site at all ?
                NetworkUtils.ping(context, searchEngine.getUrl(context));

                editions.addAll(((SearchEngine.AlternativeEditions) searchEngine)
                                        .getAlternativeEditions(context, mIsbn));

            } catch (@NonNull final IOException | RuntimeException e) {
                Logger.error(context, TAG, e);
                mException = e;
            }
        }
        return editions;
    }
}
