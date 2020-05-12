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
package com.hardbacknutter.nevertoomanybooks.searches.isfdb;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.ImageSize;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;

/**
 * See notes in the package-info.java file.
 * <p>
 * 2020-01-04: "http://www.isfdb.org" is not available on https.
 * see "res/xml/network_security_config.xml"
 */
public class IsfdbSearchEngine
        implements SearchEngine,
                   SearchEngine.ByText,
                   SearchEngine.ByIsbn,
                   SearchEngine.ByNativeId,
                   SearchEngine.CoverByIsbn,
                   SearchEngine.AlternativeEditions {

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
    @VisibleForTesting
    public static final String PREFS_HOST_URL = PREF_PREFIX + "host.url";

    @NonNull
    public static String getBaseURL(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(PREFS_HOST_URL, "http://www.isfdb.org");
    }

    /**
     * View a Book on the web site.
     *
     * @param context Current context
     * @param bookId  site native book id to show
     */
    public static void openWebsite(@NonNull final Context context,
                                   final long bookId) {
        String url = getBaseURL(context) + CGI_BIN + URL_PL_CGI + "?" + bookId;
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        return SITE_LOCALE;
    }

    @NonNull
    @Override
    public Bundle searchByNativeId(@NonNull final Context context,
                                   @NonNull final String nativeId,
                                   @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        return new IsfdbBookHandler()
                .fetchByNativeId(context, nativeId, isAddSeriesFromToc(context),
                                 fetchThumbnail, new Bundle());
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        List<Edition> editions = new IsfdbEditionsHandler().fetch(context, validIsbn);
        if (editions.isEmpty()) {
            return new Bundle();
        } else {
            return new IsfdbBookHandler()
                    .fetch(context, editions, isAddSeriesFromToc(context),
                           fetchThumbnail, new Bundle());
        }
    }

    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@NonNull final Context context,
                         @Nullable final String code,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final String publisher,
                         @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        String url = getBaseURL(context) + CGI_BIN + URL_ADV_SEARCH_RESULTS_CGI + "?"
                     + "ORDERBY=pub_title"
                     + "&ACTION=query"
                     + "&START=0"
                     + "&TYPE=Publication"
                     + "&C=AND";

        int index = 0;
        String args = "";

        if (author != null && !author.isEmpty()) {
            index++;
            args += "&USE_" + index + "=author_canonical"
                    + "&O_" + index + "=contains"
                    + "&TERM_" + index + "=" + URLEncoder.encode(author, CHARSET_ENCODE_URL);
        }

        if (title != null && !title.isEmpty()) {
            index++;
            args += "&USE_" + index + "=pub_title"
                    + "&O_" + index + "=contains"
                    + "&TERM_" + index + "=" + URLEncoder.encode(title, CHARSET_ENCODE_URL);
        }

        // as per user settings.
        if (PreferenceManager.getDefaultSharedPreferences(context)
                             .getBoolean(PREFS_USE_PUBLISHER, false)) {
            if (publisher != null && !publisher.isEmpty()) {
                index++;
                args += "&USE_" + index + "=pub_publisher"
                        + "&O_" + index + "=contains"
                        + "&TERM_" + index + "=" + URLEncoder.encode(publisher, CHARSET_ENCODE_URL);
            }
        }

        // there is support for up to 6 search terms.
        // &USE_4=pub_title&O_4=exact&TERM_4=
        // &USE_5=pub_title&O_5=exact&TERM_5=
        // &USE_6=pub_title&O_6=exact&TERM_6=

        // no data to search for.
        if (args.isEmpty()) {
            return new Bundle();
        }
        List<Edition> editions = new IsfdbEditionsHandler()
                .fetchPath(context, url + args);
        if (editions.isEmpty()) {
            return new Bundle();
        } else {
            return new IsfdbBookHandler()
                    .fetch(context, editions, isAddSeriesFromToc(context),
                           fetchThumbnail, new Bundle());
        }
    }

    @Nullable
    @Override
    public String searchCoverImageByIsbn(@NonNull final Context context,
                                         @NonNull final String validIsbn,
                                         final int cIdx,
                                         @Nullable final ImageSize size) {
        try {
            List<Edition> editions = new IsfdbEditionsHandler().fetch(context, validIsbn);
            if (editions.isEmpty()) {
                return null;
            } else {
                final Bundle bookData =
                        new IsfdbBookHandler().fetchCover(context, editions, new Bundle());
                return CoverByIsbn.getFirstCoverFileFoundPath(bookData, cIdx);
            }
        } catch (@NonNull final IOException ignore) {
            // ignored
        }
        return null;
    }

    private boolean isAddSeriesFromToc(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(IsfdbSearchEngine.PREFS_SERIES_FROM_TOC, false);
    }

    @NonNull
    @Override
    public String getUrl(@NonNull final Context context) {
        return getBaseURL(context);
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.site_isfdb;
    }

    @NonNull
    @Override
    public List<String> getAlternativeEditions(@NonNull final Context context,
                                               @NonNull final String isbn) {

        // the resulting data we'll return
        List<String> isbnList = new ArrayList<>();
        // add the original isbn
        isbnList.add(isbn);

        try {
            List<Edition> editions = new IsfdbEditionsHandler().fetch(context, isbn);
            for (Edition edition : editions) {
                if (edition.isbn != null) {
                    isbnList.add(edition.isbn);
                }
            }
        } catch (@NonNull final SocketTimeoutException ignore) {
            // ignore
        }
        return isbnList;
    }
}
