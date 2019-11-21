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
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;

/**
 * See notes in the package-info.java file.
 * <p>
 * 2019-10-01: "http://www.isfdb.org" is not available on https.
 */
public class IsfdbManager
        implements SearchEngine,
                   SearchEngine.ByText,
                   SearchEngine.ByIsbn,
                   SearchEngine.ByNativeId {

    /**
     * The site claims to use ISO-8859-1.
     * <pre>
     * {@code <meta http-equiv="content-type" content="text/html; charset=iso-8859-1">}
     * </pre>
     * but the real encoding seems to be Windows-1252.
     * For example, a books list price with a specific currency symbol (e.g. dutch guilders)
     * fails to be decoded unless we force Windows-1252
     * (tested with UTF-8 similarly fails to decode those symbols)
     */
    static final String CHARSET_DECODE_PAGE = "Windows-1252";

    /** But to encode the search url (a GET), the charset must be 8859-1. */
    @SuppressWarnings("WeakerAccess")
    static final String CHARSET_ENCODE_URL = "iso-8859-1";

    /** The ISFDB site uses US style currency notation. */
    static final Locale SITE_LOCALE = Locale.US;

    /** Common CGI directory. */
    static final String CGI_BIN = "/cgi-bin/";
    /** bibliographic information for one title. */
    static final String URL_TITLE_CGI = "title.cgi";
    /** bibliographic information for one publication. */
    static final String URL_PL_CGI = "pl.cgi";
    /** ISFDB bibliography for one author. */
    static final String URL_EA_CGI = "ea.cgi";
    /** titles associated with a particular Series. */
    static final String URL_PE_CGI = "pe.cgi";
    /** Search by type; e.g.  arg=%s&type=ISBN. */
    static final String URL_SE_CGI = "se.cgi";
    /** Advanced search FORM submission (using GET), and the returned results page url. */
    static final String URL_ADV_SEARCH_RESULTS_CGI = "adv_search_results.cgi";

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "isfdb.";
    /** Type: {@code boolean}. */
    public static final String PREFS_SERIES_FROM_TOC = PREF_PREFIX + "search.toc.series";
    /** Type: {@code boolean}. */
    public static final String PREFS_USE_PUBLISHER = PREF_PREFIX + "search.uses.publisher";
    /** Type: {@code String}. */
    private static final String PREFS_HOST_URL = PREF_PREFIX + "host.url";

    @NonNull
    public static String getBaseURL(@NonNull final Context appContext) {
        return PreferenceManager.getDefaultSharedPreferences(appContext)
                                .getString(PREFS_HOST_URL, "http://www.isfdb.org");
    }

    /**
     * View a Book on the web site.
     *
     * @param appContext Application context
     * @param bookId     site native book id to show
     */
    public static void openWebsite(@NonNull final Context appContext,
                                   final long bookId) {
        String url = getBaseURL(appContext) + CGI_BIN + URL_PL_CGI + "?" + bookId;
        appContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @NonNull
    @Override
    public Bundle searchByNativeId(@NonNull final Context localizedAppContext,
                                   @NonNull final String nativeId,
                                   final boolean fetchThumbnail)
            throws IOException {

        return new IsfdbBookHandler(localizedAppContext)
                .fetchByNativeId(nativeId, isAddSeriesFromToc(localizedAppContext), fetchThumbnail);
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context localizedAppContext,
                               @NonNull final String isbn,
                               final boolean fetchThumbnail)
            throws IOException {

        ArrayList<IsfdbEditionsHandler.Edition> editions =
                new IsfdbEditionsHandler(localizedAppContext).fetch(isbn);
        return fetchBook(localizedAppContext, editions, fetchThumbnail);
    }

    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@NonNull final Context localizedAppContext,
                         @Nullable final String isbn,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final String publisher,
                         final boolean fetchThumbnail)
            throws IOException {

        String url = getBaseURL(localizedAppContext) + CGI_BIN + URL_ADV_SEARCH_RESULTS_CGI + "?"
                     + "ORDERBY=pub_title"
                     + "&ACTION=query"
                     + "&START=0"
                     + "&TYPE=Publication"
                     + "&C=AND"
                     + "&";

        int index = 0;

        if (author != null && !author.isEmpty()) {
            index++;
            url += "&USE_" + index + "=author_canonical"
                   + "&O_" + index + "=contains"
                   + "&TERM_" + index + "=" + URLEncoder.encode(author, CHARSET_ENCODE_URL);
        }

        if (title != null && !title.isEmpty()) {
            index++;
            url += "&USE_" + index + "=pub_title"
                   + "&O_" + index + "=contains"
                   + "&TERM_" + index + "=" + URLEncoder.encode(title, CHARSET_ENCODE_URL);
        }

        // as per user settings.
        if (PreferenceManager.getDefaultSharedPreferences(localizedAppContext)
                             .getBoolean(PREFS_USE_PUBLISHER, false)) {
            if (publisher != null && !publisher.isEmpty()) {
                index++;
                url += "&USE_" + index + "=pub_publisher"
                       + "&O_" + index + "=contains"
                       + "&TERM_" + index + "=" + URLEncoder.encode(publisher, CHARSET_ENCODE_URL);
            }
        }

        // there is support for up to 6 search terms.
        // &USE_4=pub_title&O_4=exact&TERM_4=
        // &USE_5=pub_title&O_5=exact&TERM_5=
        // &USE_6=pub_title&O_6=exact&TERM_6=

        return fetchBook(localizedAppContext,
                         new IsfdbEditionsHandler(localizedAppContext).fetchPath(url),
                         fetchThumbnail);
    }

    private Bundle fetchBook(@NonNull final Context localizedAppContext,
                             final List<IsfdbEditionsHandler.Edition> editions,
                             final boolean fetchThumbnail)
            throws SocketTimeoutException {
        if (!editions.isEmpty()) {
            return new IsfdbBookHandler(localizedAppContext)
                    .fetch(editions, isAddSeriesFromToc(localizedAppContext), fetchThumbnail);
        } else {
            return new Bundle();
        }
    }

    private boolean isAddSeriesFromToc(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(IsfdbManager.PREFS_SERIES_FROM_TOC, false);
    }


    @NonNull
    @Override
    public String getUrl(@NonNull final Context appContext) {
        return getBaseURL(appContext);
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.isfdb;
    }

}
