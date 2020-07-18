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

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.JsoupBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * Given an ISBN, search for other editions on the site.
 * <p>
 * Uses JSoup screen scraping.
 */
class IsfdbEditionsHandler
        extends JsoupBase {

    /** Log tag. */
    private static final String TAG = "IsfdbEditionsHandler";
    /** Search URL template. */
    private static final String EDITIONS_URL = IsfdbSearchEngine.CGI_BIN
                                               + IsfdbSearchEngine.URL_SE_CGI + "?arg=%s&type=ISBN";
    /** List of ISFDB native book id for all found editions. */
    private final List<Edition> mEditions = new ArrayList<>();

    /** The ISBN we searched for. Not guaranteed to be identical to the book we find. */
    private String mIsbn;

    /**
     * Constructor.
     *
     * @param context      current context
     * @param searchEngine to use
     */
    IsfdbEditionsHandler(@NonNull final Context context,
                         @NonNull final SearchEngine searchEngine) {
        super(context, searchEngine);
        setCharSetName(IsfdbSearchEngine.CHARSET_DECODE_PAGE);
    }

    /**
     * Constructor used for testing.
     *
     * @param context      current context
     * @param searchEngine to use
     * @param doc          the pre-loaded Jsoup document.
     */
    @VisibleForTesting
    IsfdbEditionsHandler(@NonNull final Context context,
                         @NonNull final SearchEngine searchEngine,
                         @NonNull final Document doc) {
        this(context, searchEngine);
        mDoc = doc;
    }

    /**
     * Fails silently, returning an empty list.
     *
     * @param validIsbn to get editions for. MUST be valid.
     *
     * @return a list with native ISFDB book ID's pointing to individual editions
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @WorkerThread
    @NonNull
    public List<Edition> fetch(@NonNull final String validIsbn)
            throws SocketTimeoutException {
        mIsbn = validIsbn;

        final String url = mSearchEngine.getUrl(mContext) + String.format(EDITIONS_URL, validIsbn);
        if (loadPage(url) == null || mDoc == null) {
            return mEditions;
        }

        if (mSearchEngine.isCancelled()) {
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
    @WorkerThread
    @NonNull
    public List<Edition> fetchUrl(@NonNull final String url)
            throws SocketTimeoutException {

        if (loadPage(url) == null || mDoc == null) {
            return mEditions;
        }

        if (mSearchEngine.isCancelled()) {
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
    List<Edition> parseDoc() {
        //noinspection ConstantConditions
        final String pageUrl = mDoc.location();

        if (pageUrl.contains(IsfdbSearchEngine.URL_PL_CGI)) {
            // We got redirected to a book. Populate with the doc (web page) we got back.
            mEditions.add(new Edition(stripNumber(pageUrl, '?'), mIsbn, mDoc));

        } else if (pageUrl.contains(IsfdbSearchEngine.URL_TITLE_CGI)
                   || pageUrl.contains(IsfdbSearchEngine.URL_SE_CGI)
                   || pageUrl.contains(IsfdbSearchEngine.URL_ADV_SEARCH_RESULTS_CGI)) {
            // example: http://www.isfdb.org/cgi-bin/title.cgi?11169
            // we have multiple editions. We get here from one of:
            // - direct link to the "title" of the publication; i.e. 'show the editions'
            // - search or advanced-search for the title.

            final Element publications = mDoc.selectFirst("table.publications");
            if (publications != null) {
                // first edition line is a "tr.table1", 2nd "tr.table0", 3rd "tr.table1" etc...
                final Elements oddEntries = publications.select("tr.table1");
                final Elements evenEntries = publications.select("tr.table0");

                // combine them in a sorted list
                final Collection<Element> entries = new Elements();
                int i = 0;
                while (i < oddEntries.size() && i < evenEntries.size()) {
                    entries.add(oddEntries.get(i));
                    entries.add(evenEntries.get(i));
                    i++;
                }

                // either odd or even list might have another element.
                if (i < oddEntries.size()) {
                    entries.add(oddEntries.get(i));
                } else if (i < evenEntries.size()) {
                    entries.add(evenEntries.get(i));
                }

                for (Element tr : entries) {
                    // 1st column: Title == the book link
                    final Element edLink = tr.child(0).select("a").first();
                    if (edLink != null) {
                        final String url = edLink.attr("href");
                        if (url != null) {
                            String isbnStr = null;
                            // 4th column: the ISBN/Catalog ID.
                            final String catNr = tr.child(4).text();
                            if (!catNr.isEmpty()) {
                                final ISBN isbn = ISBN.createISBN(catNr);
                                if (isbn.isValid(true)) {
                                    isbnStr = isbn.asText();
                                }
                            }

                            mEditions.add(new Edition(stripNumber(url, '?'), isbnStr));
                        }
                    }
                }
            }

        } else {
            // dunno, let's log it
            Logger.warn(mContext, TAG, "parseDoc|pageUrl=" + pageUrl);
        }

        return mEditions;
    }

    /**
     * A url ends with 'last'123.  Strip and return the '123' part.
     *
     * @param url  to handle
     * @param last character to look for as last-index
     *
     * @return the number
     */
    private long stripNumber(@NonNull final String url,
                             @SuppressWarnings("SameParameterValue") final char last) {
        final int index = url.lastIndexOf(last) + 1;
        if (index == 0) {
            return 0;
        }

        return Long.parseLong(url.substring(index));
    }
}
