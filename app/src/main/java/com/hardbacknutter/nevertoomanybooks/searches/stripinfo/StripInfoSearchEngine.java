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
package com.hardbacknutter.nevertoomanybooks.searches.stripinfo;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.IOException;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;

/**
 * <a href="https://stripinfo.be/">https://stripinfo.be/</a>
 * <p>
 * Dutch language (and to an extend French and a minimal amount of other languages) comics website.
 */
public class StripInfoSearchEngine
        implements SearchEngine,
                   SearchEngine.ByNativeId,
                   SearchEngine.ByIsbn,
                   SearchEngine.ByBarcode {

    /** This is a Dutch language website. */
    public static final Locale SITE_LOCALE = new Locale("nl", "BE");

    /** base urls. */
    public static final String BASE_URL = "https://stripinfo.be";

    /** connect-timeout. */
    private static final int SOCKET_TIMEOUT_MS = 7_000;

    /**
     * View a Book on the web site.
     *
     * @param context Current context
     * @param bookId  site native book id to show
     */
    public static void openWebsite(@NonNull final Context context,
                                   final long bookId) {
        String url = BASE_URL + "/reeks/strip/" + bookId;
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @Override
    public int getConnectTimeoutMs() {
        return SOCKET_TIMEOUT_MS;
    }

    @NonNull
    @Override
    public String getUrl(@NonNull final Context context) {
        return BASE_URL;
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        return SITE_LOCALE;
    }

    /**
     * Also handles {@link SearchEngine.ByBarcode}.
     * <p>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws IOException {
        return new StripInfoBookHandler(context, this)
                .fetch(validIsbn, fetchThumbnail, new Bundle());
    }

    @NonNull
    @Override
    public Bundle searchByNativeId(@NonNull final Context context,
                                   @NonNull final String nativeId,
                                   @NonNull final boolean[] fetchThumbnail)
            throws IOException {
        return new StripInfoBookHandler(context, this)
                .fetchByNativeId(nativeId, fetchThumbnail, new Bundle());
    }

    @Override
    public void checkForSeriesNameInTitle(@NonNull final Bundle bookData) {
        // StripInfo always has clean titles and uses ()
        // which causes the default cleaner to give false positives.
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.site_stripinfo;
    }
}
