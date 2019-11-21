/*
 * @Copyright 2019 HardBackNutter
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
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;

/**
 * <a href="https://stripinfo.be/">https://stripinfo.be/</a>
 * <p>
 * Dutch language (and to an extend French and a minimal amount of other languages) comics website.
 */
public class StripInfoManager
        implements SearchEngine,
                   SearchEngine.ByNativeId,
                   SearchEngine.ByIsbn {

    /** This is a Dutch website. */
    public static final Locale SITE_LOCALE = new Locale("nl", "BE");
    /** Preferences prefix. */
    private static final String PREF_PREFIX = "stripinfo.";
    /** Type: {@code String}. */
    private static final String PREFS_HOST_URL = PREF_PREFIX + "host.url";

    @NonNull
    public static String getBaseURL(@NonNull final Context appContext) {
        return PreferenceManager.getDefaultSharedPreferences(appContext)
                                .getString(PREFS_HOST_URL, "https://stripinfo.be");
    }

    /**
     * View a Book on the web site.
     *
     * @param appContext Application context
     * @param bookId  site native book id to show
     */
    public static void openWebsite(@NonNull final Context appContext,
                                   final long bookId) {
        String url = getBaseURL(appContext) + "/reeks/strip/" + bookId;
        appContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @NonNull
    @Override
    public String getUrl(@NonNull final Context appContext) {
        return getBaseURL(appContext);
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context localizedAppContext,
                               @NonNull final String isbn,
                               final boolean fetchThumbnail)
            throws IOException {
        return new StripInfoBookHandler(localizedAppContext)
                .fetch(isbn, new Bundle(), fetchThumbnail);
    }

    @NonNull
    @Override
    public Bundle searchByNativeId(@NonNull final Context localizedAppContext,
                                   @NonNull final String nativeId,
                                   final boolean fetchThumbnail)
            throws IOException {
        return new StripInfoBookHandler(localizedAppContext)
                .fetchByNativeId(nativeId, new Bundle(), fetchThumbnail);
    }

    @Override
    public void checkForSeriesNameInTitle(@NonNull final Bundle bookData) {
        // StripInfo has clean titles and the default cleaner gives false positives.
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.stripinfo;
    }
}
