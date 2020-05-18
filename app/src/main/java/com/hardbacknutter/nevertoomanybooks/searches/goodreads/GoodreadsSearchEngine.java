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
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsWork;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.Http404Exception;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * <a href="https://www.goodreads.com">https://www.goodreads.com</a>
 *
 * Uses {@link SearchEngine.CoverByIsbn#getCoverImageFallback} because the API
 * has no specific cover access methods, but the site has very good covers making the overhead
 * worth it.
 */
public class GoodreadsSearchEngine
        implements SearchEngine,
                   SearchEngine.ByIsbn,
                   SearchEngine.ByNativeId,
                   SearchEngine.ByText,
                   SearchEngine.CoverByIsbn {

    /** file suffix for cover files. */
    public static final String FILENAME_SUFFIX = "_GR";
    /** Can only send requests at a throttled speed. */
    @NonNull
    public static final Throttler THROTTLER = new Throttler();

    @NonNull
    private final GoodreadsHandler mApiHandler;
    @NonNull
    private final GoodreadsAuth mGoodreadsAuth;

    /**
     * Constructor.
     */
    public GoodreadsSearchEngine() {
        mGoodreadsAuth = new GoodreadsAuth(App.getAppContext());
        mApiHandler = new GoodreadsHandler(mGoodreadsAuth);
    }

    @VisibleForTesting
    public GoodreadsSearchEngine(@NonNull final Context context) {
        mGoodreadsAuth = new GoodreadsAuth(context);
        mApiHandler = new GoodreadsHandler(mGoodreadsAuth);
    }

    /**
     * View a Book on the web site.
     *
     * @param context Current context
     * @param bookId  site native book id to show
     */
    public static void openWebsite(@NonNull final Context context,
                                   final long bookId) {
        String url = GoodreadsHandler.BASE_URL + "/book/show/" + bookId;
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        return GoodreadsHandler.SITE_LOCALE;
    }

    @Override
    public boolean promptToRegister(@NonNull final Context context,
                                    final boolean required,
                                    @NonNull final String prefSuffix) {
        if (mGoodreadsAuth.hasCredentials(context)) {
            return false;
        }

        final String key = GoodreadsHandler.PREFS_HIDE_ALERT + prefSuffix;
        boolean show = required || !PreferenceManager.getDefaultSharedPreferences(context)
                                                     .getBoolean(key, false);

        if (show) {
            Intent intent = new Intent(context, GoodreadsRegistrationActivity.class);
            StandardDialogs.registerOnSite(context, R.string.site_goodreads,
                                           intent, required, key);
        }
        return show;
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws CredentialsException, IOException {

        try {
            return mApiHandler.getBookByIsbn(context, validIsbn, fetchThumbnail, new Bundle());

        } catch (@NonNull final Http404Exception ignore) {
            // ignore
        }
        return new Bundle();
    }

    @NonNull
    @Override
    public Bundle searchByNativeId(@NonNull final Context context,
                                   @NonNull final String nativeId,
                                   @NonNull final boolean[] fetchThumbnail)
            throws CredentialsException, IOException {

        try {
            long grBookId = Long.parseLong(nativeId);
            return mApiHandler.getBookById(context, grBookId, fetchThumbnail, new Bundle());

        } catch (@NonNull final Http404Exception | NumberFormatException e) {
            // ignore
        }
        return new Bundle();
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
    public Bundle search(@NonNull final Context context,
                         @Nullable final String code,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final /* not supported */ String publisher,
                         @NonNull final boolean[] fetchThumbnail)
            throws CredentialsException,
                   IOException {

        if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {
            try {
                List<GoodreadsWork> works = mApiHandler.search(context, author + ' ' + title);
                // only return the first one found
                if (!works.isEmpty()) {
                    return mApiHandler.getBookById(context, works.get(0).grBookId,
                                                   fetchThumbnail, new Bundle());
                }
            } catch (@NonNull final Http404Exception ignore) {
                // ignore
            }
        }
        return new Bundle();
    }

    @Nullable
    @Override
    @WorkerThread
    public String searchCoverImageByIsbn(@NonNull final Context context,
                                         @NonNull final String validIsbn,
                                         @IntRange(from = 0) final int cIdx,
                                         @Nullable final ImageFileInfo.Size size) {
        if (!mGoodreadsAuth.hasValidCredentials(context)) {
            return null;
        }
        return CoverByIsbn.getCoverImageFallback(context, this, validIsbn, cIdx);
    }

    @Override
    public boolean isAvailable(@NonNull final Context context) {
        // makes sure we *have* credentials, but does not check them.
        return !mGoodreadsAuth.getDevKey().isEmpty() && mGoodreadsAuth.hasCredentials(context);
    }

    @NonNull
    @Override
    public String getUrl(@NonNull final Context context) {
        return GoodreadsHandler.BASE_URL;
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.site_goodreads;
    }
}

