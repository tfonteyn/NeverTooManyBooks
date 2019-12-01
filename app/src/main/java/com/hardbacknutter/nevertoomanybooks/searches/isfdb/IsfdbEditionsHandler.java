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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.net.SocketTimeoutException;
import java.util.ArrayList;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * Given an ISBN, search for other editions on the site.
 * <p>
 * Uses JSoup screen scraping.
 */
public class IsfdbEditionsHandler
        extends AbstractBase {

    private static final String TAG = "IsfdbEditionsHandler";

    /** Search URL template. */
    private static final String EDITIONS_URL = IsfdbManager.CGI_BIN
                                               + IsfdbManager.URL_SE_CGI + "?arg=%s&type=ISBN";

    /** List of ISFDB native book id for all found editions. */
    private final ArrayList<Edition> mEditions = new ArrayList<>();
    @NonNull
    private final Context mAppContext;
    /** The ISBN we searched for. Not guaranteed to be identical to the book we find. */
    private String mIsbn;

    /**
     * Constructor.
     *
     * @param appContext Application context
     */
    IsfdbEditionsHandler(@NonNull final Context appContext) {
        mAppContext = appContext;
    }

    /**
     * Constructor used for testing.
     *
     * @param appContext Application context
     * @param doc        the JSoup Document.
     */
    @VisibleForTesting
    IsfdbEditionsHandler(@NonNull final Context appContext,
                         @NonNull final Document doc) {
        super(doc);
        mAppContext = appContext;
    }

    /**
     * Fails silently, returning an empty list.
     *
     * @param isbn to get editions for. MUST be valid.
     *
     * @return a list with native ISFDB book ID's pointing to individual editions
     *
     * @throws SocketTimeoutException if the connection times out
     */
    public ArrayList<Edition> fetch(@NonNull final String isbn)
            throws SocketTimeoutException {
        mIsbn = isbn;

        String url = IsfdbManager.getBaseURL(mAppContext) + String.format(EDITIONS_URL, isbn);

        if (loadPage(mAppContext, url) == null) {
            // failed to load, return an empty list.
            return mEditions;
        }

        return parseDoc();
    }

    /**
     * Get the list with native ISFDB book ID's pointing to individual editions.
     *
     * @param url A fully qualified ISFDB search url
     *
     * @return list
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @SuppressWarnings("WeakerAccess")
    public ArrayList<Edition> fetchPath(@NonNull final String url)
            throws SocketTimeoutException {

        if (loadPage(mAppContext, url) == null) {
            // failed to load, return an empty list.
            return mEditions;
        }

        return parseDoc();
    }

    /**
     * Do the parsing of the Document.
     *
     * @return list of editions found, can be empty, but never {@code null}
     */
    @NonNull
    @VisibleForTesting
    ArrayList<Edition> parseDoc() {
        String pageUrl = mDoc.location();

        if (pageUrl.contains(IsfdbManager.URL_PL_CGI)) {
            // We got redirected to a book. Populate with the doc (web page) we got back.
            mEditions.add(new Edition(stripNumber(pageUrl, '?'), mIsbn, mDoc));

        } else if (pageUrl.contains(IsfdbManager.URL_TITLE_CGI)
                   || pageUrl.contains(IsfdbManager.URL_SE_CGI)
                   || pageUrl.contains(IsfdbManager.URL_ADV_SEARCH_RESULTS_CGI)) {
            // example: http://www.isfdb.org/cgi-bin/title.cgi?11169
            // we have multiple editions. We get here from one of:
            // - direct link to the "title" of the publication; i.e. 'show the editions'
            // - search or advanced-search for the title.

            findEntries(mDoc);
        } else {
            // dunno, let's log it
            Logger.warn(TAG, "parseDoc|pageUrl=" + pageUrl);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB) {
            Log.d(TAG, "EXIT|fetch|" + mEditions);
        }
        return mEditions;
    }

    /**
     * Search/scrape for the selectors to build the edition list.
     *
     * TODO: currently fetches the list odd rows first, followed by all even rows.
     *
     * @param doc       to parse
     */
    private void findEntries(@NonNull final Document doc) {
        Element publications = doc.selectFirst("table.publications");
        if (publications == null) {
            return;
        }

        // first edition line is a "tr.table1", 2nd "tr.table0", 3rd "tr.table1" etc...
        String[] selectors = {"tr.table1", "tr.table0"};
        for (String selector : selectors) {
            Elements entries = publications.select(selector);
            for (Element tr : entries) {
                // 1th column: Title ==the book link
                Element edLink = tr.child(0).select("a").first();
                if (edLink != null) {
                    String url = edLink.attr("href");
                    if (url != null) {
                        // 4th column: ISBN/Catalog ID
                        String isbnStr = tr.child(4).text();
                        if (isbnStr.length() < 10) {
                            isbnStr = null;
                        } else {
                            ISBN isbn = new ISBN(isbnStr);
                            if (isbn.isValid()) {
                                isbnStr = isbn.asText();
                            } else {
                                isbnStr = null;
                            }
                        }
                        mEditions.add(new Edition(stripNumber(url, '?'), isbnStr));
                    }
                }
            }
        }
    }
}
