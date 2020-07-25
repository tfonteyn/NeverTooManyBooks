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
package com.hardbacknutter.nevertoomanybooks.searches.goodreads;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsRegistrationActivity;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsWork;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.Http404Exception;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * <a href="https://www.goodreads.com">https://www.goodreads.com</a>
 * <p>
 * Uses {@link SearchEngine.CoverByIsbn#searchCoverImageByIsbnFallback} because the API
 * has no specific cover access methods, but the site has very good covers making the overhead
 * worth it.
 */
@SearchEngine.Configuration(
        id = SearchSites.GOODREADS,
        nameResId = R.string.site_goodreads,
        url = GoodreadsManager.BASE_URL,
        prefKey = GoodreadsManager.PREF_KEY,
        domainKey = DBDefinitions.KEY_EID_GOODREADS_BOOK,
        domainViewId = R.id.site_goodreads,
        domainMenuId = R.id.MENU_VIEW_BOOK_AT_GOODREADS,
        connectTimeoutMs = GoodreadsManager.CONNECTION_TIMEOUT_MS
)
public class GoodreadsSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ByText,
                   SearchEngine.CoverByIsbn {

    /** file suffix for cover files. */
    public static final String FILENAME_SUFFIX = "_GR";
    /** Can only send requests at a throttled speed. */
    @NonNull
    public static final Throttler THROTTLER = new Throttler();

    @NonNull
    private final GoodreadsManager mGoodreadsManager;
    @NonNull
    private final GoodreadsAuth mGoodreadsAuth;

    /**
     * Constructor.
     *
     * @param appContext Application context
     */
    public GoodreadsSearchEngine(@NonNull final Context appContext) {
        super(appContext);
        mGoodreadsAuth = new GoodreadsAuth(mAppContext);
        mGoodreadsManager = new GoodreadsManager(mAppContext, mGoodreadsAuth);
    }

    @NonNull
    @Override
    public String createUrl(@NonNull final String externalId) {
        return getSiteUrl() + "/book/show/" + externalId;
    }

    @Override
    public boolean isAvailable() {
        // makes sure we *have* credentials, but does not check them.
        return !mGoodreadsAuth.getDevKey().isEmpty() && mGoodreadsAuth.hasCredentials(mAppContext);
    }

    @Nullable
    @Override
    public Throttler getThrottler() {
        return THROTTLER;
    }


    @Override
    public boolean promptToRegister(@NonNull final Context context,
                                    final boolean required,
                                    @NonNull final String prefSuffix) {
        if (mGoodreadsAuth.hasCredentials(context)) {
            return false;
        }

        final String key = GoodreadsManager.PREFS_HIDE_ALERT + prefSuffix;
        final boolean show = required || !PreferenceManager.getDefaultSharedPreferences(context)
                                                           .getBoolean(key, false);

        if (show) {
            final Intent intent = new Intent(context, GoodreadsRegistrationActivity.class);
            StandardDialogs.registerOnSite(context, R.string.site_goodreads, intent, required, key);
        }
        return show;
    }


    @NonNull
    @Override
    public Bundle searchByExternalId(@NonNull final String externalId,
                                     @NonNull final boolean[] fetchThumbnail)
            throws CredentialsException, IOException {

        final Bundle bookData = new Bundle();
        try {
            final long grBookId = Long.parseLong(externalId);
            return mGoodreadsManager.getBookById(grBookId, fetchThumbnail, bookData);

        } catch (@NonNull final Http404Exception | NumberFormatException e) {
            // ignore
        }
        return bookData;
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws CredentialsException, IOException {

        final Bundle bookData = new Bundle();
        try {
            return mGoodreadsManager.getBookByIsbn(validIsbn, fetchThumbnail, bookData);

        } catch (@NonNull final Http404Exception ignore) {
            // ignore
        }
        return bookData;
    }

    /**
     * Search for a book.
     * <p>
     * The search will in fact search/find a list of 'works' but we only return the first one.
     *
     * @return first book found, or an empty bundle if none found.
     */
    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@Nullable final String code,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final /* not supported */ String publisher,
                         @NonNull final boolean[] fetchThumbnail)
            throws CredentialsException,
                   IOException {

        final Bundle bookData = new Bundle();
        if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {
            try {
                final List<GoodreadsWork> works =
                        mGoodreadsManager.search(author + ' ' + title);
                // only return the first one found
                if (!works.isEmpty()) {
                    return mGoodreadsManager.getBookById(works.get(0).grBookId,
                                                         fetchThumbnail, bookData);
                }
            } catch (@NonNull final Http404Exception ignore) {
                // ignore
            }
        }
        return bookData;
    }

    @Nullable
    @Override
    @WorkerThread
    public String searchCoverImageByIsbn(@NonNull final String validIsbn,
                                         @IntRange(from = 0) final int cIdx,
                                         @Nullable final ImageFileInfo.Size size) {
        if (!mGoodreadsAuth.hasValidCredentials(mAppContext)) {
            return null;
        }
        return searchCoverImageByIsbnFallback(validIsbn, cIdx);
    }
}

