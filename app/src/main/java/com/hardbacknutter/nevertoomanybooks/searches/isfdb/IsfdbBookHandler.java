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
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

class IsfdbBookHandler
        extends AbstractBase {

    /** Log tag. */
    private static final String TAG = "IsfdbBookHandler";

    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_ISFDB";

    /** Param 1: ISFDB native book ID. */
    private static final String BOOK_URL = IsfdbSearchEngine.CGI_BIN
                                           + IsfdbSearchEngine.URL_PL_CGI + "?%1$s";

    private static final Map<String, Integer> TYPE_MAP = new HashMap<>();

    /**
     * Either the Web page itself, and/or the JSoup parser has used both decimal and hex
     * representation for the "•" character. Capturing all 3 possibilities here.
     */
    private static final String DOT = "(&#x2022;|&#8226;|•)";
    /** Character used by the site as string divider/splitter. */
    // private static final Pattern DOT_PATTERN = Pattern.compile(DOT);
    private static final Pattern YEAR_PATTERN = Pattern.compile(DOT + " \\(([1|2]\\d\\d\\d)\\)");
    /** ISFDB uses 00 for the day/month when unknown. We cut that out. */
    private static final Pattern UNKNOWN_M_D_PATTERN = Pattern.compile("-00", Pattern.LITERAL);
    /** A CSS select query. */
    private static final String CSS_Q_DIV_CONTENTBOX = "div.contentbox";

    /*
     * <a href="http://www.isfdb.org/wiki/index.php/Help:Screen:NewPub#Publication_Type">
     Publication_Type</a>
     *
     * ANTHOLOGY. A publication containing fiction by more than one author,
     * not written in collaboration
     *
     * COLLECTION. A publication containing two or more works
     * by a single author or authors writing in collaboration
     *
     * OMNIBUS. A publication may be classified as an omnibus if it contains multiple works
     * that have previously been published independently
     * generally this category should not be used
     *
     * Boxed sets. Boxed sets which have additional data elements (ISBNs, cover art, etc)
     * not present in the individual books that they collect should be entered
     * as OMNIBUS publications
     *
     * Put all these together into the Anthology bucket.
     * After processing TOC_MULTIPLE_AUTHORS is added when needed.
     *
     * Reminder: a "Digest" is a format, not a type.
     */
    static {
        // multiple works, one author
        TYPE_MAP.put("coll", Book.TOC_MULTIPLE_WORKS);
        TYPE_MAP.put("COLLECTION", Book.TOC_MULTIPLE_WORKS);

        // multiple works, multiple authors
        TYPE_MAP.put("anth",
                     Book.TOC_MULTIPLE_WORKS | Book.TOC_MULTIPLE_AUTHORS);
        TYPE_MAP.put("ANTHOLOGY",
                     Book.TOC_MULTIPLE_WORKS | Book.TOC_MULTIPLE_AUTHORS);

        // multiple works that have previously been published independently
        TYPE_MAP.put("omni",
                     Book.TOC_MULTIPLE_WORKS | Book.TOC_MULTIPLE_AUTHORS);
        TYPE_MAP.put("OMNIBUS",
                     Book.TOC_MULTIPLE_WORKS | Book.TOC_MULTIPLE_AUTHORS);

        // we assume magazines have multiple authors.
        TYPE_MAP.put("MAGAZINE", Book.TOC_MULTIPLE_WORKS | Book.TOC_MULTIPLE_AUTHORS);

        // others, treated as a standard book.
        // TYPE_MAP.put("novel", 0);
        // TYPE_MAP.put("NOVEL", 0);
        //
        // TYPE_MAP.put("chap", 0);
        // TYPE_MAP.put("CHAPBOOK", 0);
        //
        // TYPE_MAP.put("non-fic", 0);
        // TYPE_MAP.put("NONFICTION", 0);
    }

    /** accumulate all authors for this book. */
    @NonNull
    private final ArrayList<Author> mAuthors = new ArrayList<>();
    /** accumulate all Series for this book. */
    @NonNull
    private final ArrayList<Series> mSeries = new ArrayList<>();
    /** accumulate all Publishers for this book. */
    private final ArrayList<Publisher> mPublishers = new ArrayList<>();

    /** The fully qualified ISFDB search url. */
    private String mPath;
    /** List of all editions (ISFDB 'publicationRecord') of this book. */
    private List<Edition> mEditions;
    /** set during book load, used during content table load. */
    @Nullable
    private String mTitle;
    /** with some luck we'll get these as well. */
    @Nullable
    private String mFirstPublication;

    /**
     * Constructor.
     */
    IsfdbBookHandler(@NonNull final SearchEngine searchEngine) {
        super(searchEngine);
    }

    /**
     * Constructor used for testing.
     *
     * @param doc the JSoup Document.
     */
    @VisibleForTesting
    IsfdbBookHandler(@NonNull final SearchEngine searchEngine,
                     @NonNull final Document doc) {
        this(searchEngine);
        mDoc = doc;
    }

    @Nullable
    public List<Edition> getEditions() {
        return mEditions;
    }

    /**
     * Fetch a book.
     *
     * @param context          Current context
     * @param isfdbId          ISFDB native book ID (as a String)
     * @param addSeriesFromToc whether the TOC should get parsed for Series information
     * @param fetchThumbnail   Set to {@code true} if we want to get thumbnails
     * @param bookData         Bundle to save results in (passed in to allow mocking)
     *
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @NonNull
    Bundle fetchByNativeId(@NonNull final Context context,
                           @NonNull final String isfdbId,
                           final boolean addSeriesFromToc,
                           @NonNull final boolean[] fetchThumbnail,
                           @NonNull final Bundle bookData)
            throws SocketTimeoutException {

        return fetch(context,
                     IsfdbSearchEngine.getBaseURL(context) + String.format(BOOK_URL, isfdbId),
                     addSeriesFromToc, fetchThumbnail, bookData);
    }

    /**
     * Fetch a book.
     *
     * @param context          Current context
     * @param path             A fully qualified ISFDB search url
     * @param addSeriesFromToc whether the TOC should get parsed for Series information
     * @param fetchThumbnail   Set to {@code true} if we want to get thumbnails
     * @param bookData         Bundle to save results in (passed in to allow mocking)
     *
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @NonNull
    @WorkerThread
    private Bundle fetch(@NonNull final Context context,
                         @NonNull final String path,
                         final boolean addSeriesFromToc,
                         @NonNull final boolean[] fetchThumbnail,
                         @NonNull final Bundle bookData)
            throws SocketTimeoutException {

        mPath = path;

        if (loadPage(context, mPath) == null) {
            // null result, abort
            return bookData;
        }

        if (mSearchEngine.isCancelled()) {
            return bookData;
        }

        return parseDoc(context, addSeriesFromToc, fetchThumbnail, bookData);
    }

    /**
     * Fetch a book.
     *
     * @param context          Current context
     * @param editions         List of ISFDB Editions with native book ID
     * @param addSeriesFromToc whether the TOC should get parsed for Series information
     * @param fetchThumbnail   Set to {@code true} if we want to get thumbnails
     * @param bookData         Bundle to save results in (passed in to allow mocking)
     *
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @NonNull
    @WorkerThread
    public Bundle fetch(@NonNull final Context context,
                        @Size(min = 1) @NonNull final List<Edition> editions,
                        final boolean addSeriesFromToc,
                        @NonNull final boolean[] fetchThumbnail,
                        @NonNull final Bundle bookData)
            throws SocketTimeoutException {

        mEditions = editions;

        final Edition edition = editions.get(0);
        mPath = IsfdbSearchEngine.getBaseURL(context) + String.format(BOOK_URL, edition.isfdbId);

        // check if we already got the book
        if (edition.doc != null) {
            mDoc = edition.doc;
        } else {
            // nop, go get it.
            if (loadPage(context, mPath) == null) {
                // null result, abort
                return bookData;
            }
        }

        if (mSearchEngine.isCancelled()) {
            return bookData;
        }

        return parseDoc(context, addSeriesFromToc, fetchThumbnail, bookData);
    }

    /**
     * Fetch a cover.
     *
     * @param context  Current context
     * @param editions List of ISFDB Editions with native book ID
     * @param bookData Bundle to save results in (passed in to allow mocking)
     *
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @NonNull
    @WorkerThread
    Bundle fetchCover(@NonNull final Context context,
                      @Size(min = 1) @NonNull final List<Edition> editions,
                      @NonNull final Bundle bookData)
            throws SocketTimeoutException {
        mEditions = editions;

        final Edition edition = editions.get(0);
        mPath = IsfdbSearchEngine.getBaseURL(context) + String.format(BOOK_URL, edition.isfdbId);

        // check if we already got the book
        if (edition.doc != null) {
            mDoc = edition.doc;
        } else {
            // nop, go get it.
            if (loadPage(context, mPath) == null) {
                // null result, abort
                return bookData;
            }
        }

        if (mSearchEngine.isCancelled()) {
            return bookData;
        }

        parseDocForCover(context, bookData);

        return bookData;
    }

    /**
     * Parses the downloaded {@link #mDoc}.
     * <p>
     * First "ContentBox" contains all basic details.
     * IMPORTANT: 2019-07: the format has slightly changed! See below.
     * <pre>
     * {@code
     * <div class="ContentBox">
     *    <table>
     *      <tr class="scan">
     *
     *        <td>
     *          <a href="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg">
     *            <img src="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg"
     *                 alt="picture" class="scan"></a>
     *        </td>
     *
     *        <td class="pubheader">
     *          <ul>
     *            <li><b>Publication:</b> The Days of Perky Pat <span class="recordID">
     *                <b>Publication Record # </b>230949
     *                [<a href="http://www.isfdb.org/cgi-bin/edit/editpub.cgi?230949">Edit</a>]
     *                </span>
     *            <li><b>Author:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23">Philip K. Dick</a>
     *            <li><b>Date:</b> 1991-05-00
     *            <li><b>ISBN:</b> 0-586-20768-6 [<small>978-0-586-20768-0</small>]
     *            <li><b>Publisher:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/publisher.cgi?62">Grafton</a>
     *            <li><b>Price:</b> £5.99
     *            <li><b>Pages:</b> 494
     *            <li><b>Format:</b> <div class="tooltip">tp<sup class="mouseover">?</sup>
     *            <span class="tooltiptext tooltipnarrow">Trade paperback...</span></div>
     *            <li><b>Type:</b> COLLECTION
     *            <li><b>Cover:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/title.cgi?737949">
     *                  The Days of Perky Pat</a>
     *                  by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?21338">Chris Moore</a>
     *            <li>
     *              <div class="notes"><b>Notes:</b>
     *                "Published by Grafton Books 1991" on copyright page
     *                Artist credited on back cover
     *                "Top Stand-By Job" listed in contents and title page of story as "Stand-By"
     *                • Month from Locus1
     *                • Notes from page 487 to 494
     *                • OCLC <A href="http://www.worldcat.org/oclc/60047795">60047795</a>
     *              </div>
     *            </ul>
     *          </td>
     *    </table>
     *  Cover art supplied by
     *      <a href="http://www.isfdb.org/wiki/index.php/Image:THDSFPRKPT1991.jpg"
     *                           target="_blank">ISFDB</a>
     * </div>
     * }
     * </pre>
     * <p>
     * <p>
     * 20-19-07: new format:
     * <pre>
     * {@code
     * <div class="ContentBox">
     *   <table>
     *     <tr class="scan">
     *
     *       <td>
     *         <a href="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg">
     *         <img src="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg" class="scan"></a>
     *       </td>
     *
     *       <td class="pubheader">
     *         <ul>
     *           <li><b>Publication:</b> The Days of Perky Pat<span class="recordID">
     *               <b>Publication Record # </b>230949
     *               [<a href="http://www.isfdb.org/cgi-bin/edit/editpub.cgi?230949">Edit</a>]
     *               </span>
     *           <li><b>Author:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23">Philip K. Dick</a>
     *           <li> <b>Date:</b> 1991-05-00
     *           <li><b>ISBN:</b> 0-586-20768-6 [<small>978-0-586-20768-0</small>]
     *           <li><b>Publisher:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/publisher.cgi?62">Grafton</a>
     *           <li><b>Price:</b> £5.99
     *           <li><b>Pages:</b> 494
     *           <li><b>Format:</b> <div class="tooltip">tp<sup class="mouseover">?</sup>
     *              <span class="tooltiptext tooltipnarrow">Trade paperback...</span></div>
     *           <li><b>Type:</b> COLLECTION
     *           <li><b>Cover:</b>
     *              <a href="http://www.isfdb.org/cgi-bin/title.cgi?737949">
     *                  The Days of Perky Pat</a>
     *                  by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?21338">Chris Moore</a>
     *           <li>
     *             <div class="notes"><b>Notes:</b>
     *               "Published by Grafton Books 1991" on copyright page
     *               Artist credited on back cover
     *               "Top Stand-By Job" listed in contents and title page of story as "Stand-By"
     *               • Month from Locus1
     *               • Notes from page 487 to 494
     *             </div>
     *           <li>
     *             <b>External IDs:</b>
     *             <ul class="noindent">
     *               <li> <abbr class="template" title="Online Computer Library Center">
     *                   OCLC/WorldCat</abbr>:
     *                   <a href="http://www.worldcat.org/oclc/60047795">60047795</a>
     *             </ul>
     *           <li><a href="http://www.isfdb.org/wiki/index.php/Special:Upload?
     *                  wpDestFile=THDSFPRKPT1991.jpg
     *                  &amp;wpUploadDescription=%7B%7BCID1%0A
     *                  %7CTitle%3DThe%20Days%20of%20Perky%20Pat%0A
     *                  %7CEdition%3DGrafton%201991%20tp%0A
     *                  %7CPub%3DTHDSFPRKPT1991%0A
     *                  %7CPublisher%3DGrafton%0A
     *                  %7CArtist%3DChris%20Moore%0A
     *                  %7CArtistId%3D21338%0A
     *                  %7CSource%3DScanned%20by%20%5B%5BUser%3AHardbackNut%5D%5D%7D%7D"
     *                  >Upload new cover scan</a>
     *         </ul>
     *       </td>
     *   </table>
     * Cover art supplied by <a href="http://www.isfdb.org" target="_blank">ISFDB</a> on
     * <a href="http://www.isfdb.org/wiki/index.php/Image:THDSFPRKPT1991.jpg"
     * target="_blank">this Web page</a>
     * </div>
     * }
     * </pre>
     * <p>
     * => external ID's have their own section
     * Example from elsewhere:
     * <pre>
     * {@code
     * <li>
     *  <b>External IDs:</b>
     *  <ul class="noindent">
     *    <li> <abbr class="template" title="Library of Congress Control Number">LCCN</abbr>:
     *    <a href="http://lccn.loc.gov/85070137" target="_blank">85070137</a>
     *    <li> <abbr class="template" title="Online Computer Library Center">OCLC/WorldCat</abbr>:
     *    <a href="http://www.worldcat.org/oclc/13063516" target="_blank">13063516</a>
     *  </ul>
     * <li>
     * }
     * </pre>
     * => not in above example, but seen elsewhere: the notes are HTML structured now:
     * Example:
     * <pre>
     * {@code
     * <div class="notes">
     *   <b>Notes:</b>
     *   <ul>
     *     <li>Month from Amazon.co.uk and publisher's web site.<li>First print by
     *     full number line 1 3 5 7 9 10 8 6 4 2
     *     <li>Cover image: © Shutterstock<li>Design by www.blacksheep-uk.com
     *     <li>Author photo: Alastair Reynolds © Barbara Bella
     *     <li>Afterword on page [327]
     *   </ul>
     * </div>
     * }
     * </pre>
     *
     * @param context          Current context
     * @param addSeriesFromToc whether the TOC should get parsed for Series information
     * @param fetchThumbnail   Set to {@code true} if we want to get thumbnails
     * @param bookData         Bundle to save results in (passed in to allow mocking)
     *
     * @return Bundle with book data, can be empty, but never {@code null}
     *
     * @throws SocketTimeoutException if the connection times out while fetching the TOC
     */
    @NonNull
    @VisibleForTesting
    Bundle parseDoc(@NonNull final Context context,
                    final boolean addSeriesFromToc,
                    @NonNull final boolean[] fetchThumbnail,
                    @NonNull final Bundle bookData)
            throws SocketTimeoutException {

        final Elements allContentBoxes = mDoc.select(CSS_Q_DIV_CONTENTBOX);
        // sanity check
        if (allContentBoxes == null) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "parseDoc|no contentbox found|mDoc.location()=" + mDoc.location());
            }
            return bookData;
        }

        final Element contentBox = allContentBoxes.first();
        final Element ul = contentBox.selectFirst("ul");
        final Elements lis = ul.children();

        String tmpString;

        for (Element li : lis) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB) {
                Log.d(TAG, "fetch|" + li.toString());
            }

            if (mSearchEngine.isCancelled()) {
                return bookData;
            }

            try {
                String fieldName = null;

                // We want the first 'bold' child Element of the li; e.g. "<b>Publisher:</b>"
                final Element fieldLabelElement = li.selectFirst("b");
                if (fieldLabelElement != null) {
                    fieldName = fieldLabelElement.text();
                }

                if (fieldName == null) {
                    continue;
                }

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB) {
                    Log.d(TAG, "fetch|fieldName=" + fieldName);
                }

                if ("Publication:".equalsIgnoreCase(fieldName)) {
                    mTitle = fieldLabelElement.nextSibling().toString().trim();
                    bookData.putString(DBDefinitions.KEY_TITLE, mTitle);

                } else if ("Author:".equalsIgnoreCase(fieldName)
                           || "Authors:".equalsIgnoreCase(fieldName)) {
                    final Elements as = li.select("a");
                    if (as != null) {
                        for (Element a : as) {
                            final Author author = Author.from(a.text());
                            // author.setIsfDbId(stripNumber(a.attr("href"), '?'));
                            mAuthors.add(author);
                        }
                    }
                } else if ("Date:".equalsIgnoreCase(fieldName)) {
                    // dates are in fact displayed as YYYY-MM-DD which is very nice.
                    tmpString = fieldLabelElement.nextSibling().toString().trim();
                    // except that ISFDB uses 00 for the day/month when unknown ...
                    // e.g. "1975-04-00" or "1974-00-00" Cut that part off.
                    tmpString = UNKNOWN_M_D_PATTERN.matcher(tmpString).replaceAll("");
                    // and we're paranoid...
                    final Date d = DateUtils.parseDate(tmpString);
                    if (d != null) {
                        // Note that partial dates, e.g. "1987", "1978-03"
                        // will get 'completed' to "1987-01-01", "1978-03-01"
                        // This should be acceptable IMHO.
                        bookData.putString(DBDefinitions.KEY_DATE_PUBLISHED,
                                           DateUtils.utcSqlDate(d));
                    }

                } else if ("ISBN:".equalsIgnoreCase(fieldName)) {
                    // we use them in the order found here.
                    //   <li><b>ISBN:</b> 0-00-712774-X [<small>978-0-00-712774-0</small>]
                    tmpString = fieldLabelElement.nextSibling().toString().trim();
                    tmpString = digits(tmpString, true);
                    if (!tmpString.isEmpty()) {
                        bookData.putString(DBDefinitions.KEY_ISBN, tmpString);
                    }

                    tmpString = fieldLabelElement.nextElementSibling().text();
                    tmpString = digits(tmpString, true);
                    if (!tmpString.isEmpty()) {
                        bookData.putString(BookField.ISBN_2, tmpString);
                    }

                } else if ("Publisher:".equalsIgnoreCase(fieldName)) {
                    final Elements as = li.select("a");
                    if (as != null) {
                        for (Element a : as) {
                            final Publisher publisher = Publisher.from(a.text());
                            // publisher.setIsfDbId(stripNumber(a.attr("href"), '?'));
                            mPublishers.add(publisher);
                        }
                    }

                } else if ("Pub. Series:".equalsIgnoreCase(fieldName)) {
                    final Elements as = li.select("a");
                    if (as != null) {
                        for (Element a : as) {
                            final Series series = Series.from(a.text());
                            // series.setIsfDbId(stripNumber(a.attr("href"), '?'));
                            mSeries.add(series);
                        }
                    }
                } else if ("Pub. Series #:".equalsIgnoreCase(fieldName)) {
                    tmpString = fieldLabelElement.nextSibling().toString().trim();
                    // assume that if we get here, then we added a "Pub. Series:" as last one.
                    mSeries.get(mSeries.size() - 1).setNumber(tmpString);

                } else if ("Price:".equalsIgnoreCase(fieldName)) {
                    tmpString = fieldLabelElement.nextSibling().toString().trim();
                    final Money money = new Money(IsfdbSearchEngine.SITE_LOCALE, tmpString);
                    if (money.getCurrency() != null) {
                        bookData.putDouble(DBDefinitions.KEY_PRICE_LISTED, money.doubleValue());
                        bookData.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                                           money.getCurrency());
                    } else {
                        bookData.putString(DBDefinitions.KEY_PRICE_LISTED, tmpString);
                    }

                } else if ("Pages:".equalsIgnoreCase(fieldName)) {
                    tmpString = fieldLabelElement.nextSibling().toString().trim();
                    bookData.putString(DBDefinitions.KEY_PAGES, tmpString);

                } else if ("Format:".equalsIgnoreCase(fieldName)) {
                    // <li><b>Format:</b> <div class="tooltip">tp<sup class="mouseover">?</sup>
                    // <span class="tooltiptext tooltipnarrow">Trade paperback. bla bla...
                    // need to lift "tp".
                    tmpString = fieldLabelElement.nextElementSibling().ownText();
                    bookData.putString(DBDefinitions.KEY_FORMAT, tmpString);

                } else if ("Type:".equalsIgnoreCase(fieldName)) {
                    // <li><b>Type:</b> COLLECTION
                    tmpString = fieldLabelElement.nextSibling().toString().trim();
                    bookData.putString(BookField.BOOK_TYPE, tmpString);
                    final Integer type = TYPE_MAP.get(tmpString);
                    if (type != null) {
                        bookData.putLong(DBDefinitions.KEY_TOC_BITMASK, type);
                    }

                } else if ("Cover:".equalsIgnoreCase(fieldName)) {
                    final Elements as = li.select("a");
                    if (as != null) {
                        //TODO: if there are multiple art/artists... will this barf ?
                        // bookData.putString(BookField.BOOK_COVER_ART_TXT, as.text());

                        if (as.size() > 1) {
                            // Cover artist
                            final Element a = as.get(1);
                            final Author author = Author.from(a.text());
                            author.setType(Author.TYPE_COVER_ARTIST);
                            // author.setIsfDbId(stripNumber(a.attr("href"),'?'));
                            mAuthors.add(author);
                        }
                    }

                } else if ("External IDs:".equalsIgnoreCase(fieldName)) {
                    // send the <ul> children
                    handleExternalIdElements(li.select("ul li"), bookData);

                } else if ("Editors:".equalsIgnoreCase(fieldName)) {
                    final Elements as = li.select("a");
                    if (as != null) {
                        for (Element a : as) {
                            final Author author = Author.from(a.text());
                            author.setType(Author.TYPE_EDITOR);
                            // author.setIsfDbId(stripNumber(a.attr("href"), '?'));
                            mAuthors.add(author);
                        }
                    }
                }
            } catch (@NonNull final IndexOutOfBoundsException e) {
                // does not happen now, but could happen if we come about non-standard entries,
                // or if ISFDB website changes
                Logger.error(context, TAG, e,
                             "path: " + mPath + "\n\nLI: " + li.toString());
            }
        }

        // publication record.
        final Elements recordIDDiv = contentBox.select("span.recordID");
        if (recordIDDiv != null) {
            tmpString = recordIDDiv.first().ownText();
            tmpString = digits(tmpString, false);
            if (!tmpString.isEmpty()) {
                try {
                    final long record = Long.parseLong(tmpString);
                    bookData.putLong(DBDefinitions.KEY_EID_ISFDB, record);
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
        }

        //ENHANCE: it would make much more sense to get the notes from the URL_TITLE_CGI page.
        // and if there are none, then fall back to the notes on this page.
        final Elements notesDiv = contentBox.select("div.notes");
        if (notesDiv != null) {
            tmpString = notesDiv.html();
            // it should always have this at the start, but paranoia...
            if (tmpString.startsWith("<b>Notes:</b>")) {
                tmpString = tmpString.substring(13).trim();
            }
            bookData.putString(DBDefinitions.KEY_DESCRIPTION, tmpString);
        }

        // ISFDB does not offer the books language on the main page (although they store
        // it in their database).
        //ENHANCE: the site is adding language to the data.. revisit.
        // For now, default to a localised 'English" as ISFDB is after all (I presume) 95% english
        bookData.putString(DBDefinitions.KEY_LANGUAGE, "eng");

        // the table of content
        final ArrayList<TocEntry> tocEntries = getTocList(context, bookData, addSeriesFromToc);
        if (!tocEntries.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_TOC_ENTRY_ARRAY, tocEntries);
        }

        // store accumulated ArrayList's, do this *after* we got the TOC
        if (!mAuthors.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, mAuthors);
        }
        if (!mSeries.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, mSeries);
        }
        if (!mPublishers.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY, mPublishers);
        }

        // Anthology type: make sure TOC_MULTIPLE_AUTHORS is correct.
        if (!tocEntries.isEmpty()) {
            @Book.TocBits
            long type = bookData.getLong(DBDefinitions.KEY_TOC_BITMASK);
            if (TocEntry.hasMultipleAuthors(tocEntries)) {
                type |= Book.TOC_MULTIPLE_AUTHORS;
            }
            bookData.putLong(DBDefinitions.KEY_TOC_BITMASK, type);
        }

        // try to deduce the first publication date
        if (tocEntries.size() == 1) {
            // if the content table has only one entry,
            // then this will have the first publication year for sure
            tmpString = digits(tocEntries.get(0).getFirstPublication(), false);
            if (!tmpString.isEmpty()) {
                bookData.putString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION, tmpString);
            }
        } else if (tocEntries.size() > 1) {
            // we gamble and take what we found in the TOC
            if (mFirstPublication != null) {
                bookData.putString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION, mFirstPublication);
            } // else take the book pub date? ... but that might be wrong....
        }

        if (mSearchEngine.isCancelled()) {
            return bookData;
        }

        // optional fetch of the cover.
        if (fetchThumbnail[0]) {
            parseDocForCover(context, bookData);
        }

        return bookData;
    }

    /**
     * Parses the downloaded {@link #mDoc} for the cover and fetches it when present.
     *
     * @param context  Current context
     * @param bookData Bundle to save results in (passed in to allow mocking)
     */
    private void parseDocForCover(@NonNull final Context context,
                                  @NonNull final Bundle bookData) {
        /* First "ContentBox" contains all basic details.
         * <pre>
         *   {@code
         *     <div class="ContentBox">
         *       <table>
         *       <tr class="scan">
         *         <td>
         *           <a href="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg">
         *           <img src="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg"
         *              alt="picture" class="scan"></a>
         *         </td>
         *         ...
         *     }
         * </pre>
         */
        final Element img = mDoc.selectFirst(CSS_Q_DIV_CONTENTBOX).selectFirst("img");
        if (img != null) {
            fetchCover(context, img.attr("src"), bookData);
        }
    }

    /**
     * All lines are normally.
     * {@code
     * <li> <abbr class="template" title="Online Computer Library Center">OCLC/WorldCat</abbr>:
     * <a href="http://www.worldcat.org/oclc/963112443" target="_blank">963112443</a>
     * }
     * Except for Amazon:
     * {@code
     * <li><abbr class="template" ... >ASIN</abbr>:  B003ODIWEG
     * (<a href="https://www.amazon.com.au/dp/B003ODIWEG" target="_blank">AU</a>
     * <a href="https://www.amazon.com.br/dp/B003ODIWEG" target="_blank">BR</a>
     * <a href="https://www.amazon.ca/dp/B003ODIWEG" target="_blank">CA</a>
     * <a href="https://www.amazon.cn/dp/B003ODIWEG" target="_blank">CN</a>
     * <a href="https://www.amazon.de/dp/B003ODIWEG" target="_blank">DE</a>
     * <a href="https://www.amazon.es/dp/B003ODIWEG" target="_blank">ES</a>
     * <a href="https://www.amazon.fr/dp/B003ODIWEG" target="_blank">FR</a>
     * <a href="https://www.amazon.in/dp/B003ODIWEG" target="_blank">IN</a>
     * <a href="https://www.amazon.it/dp/B003ODIWEG" target="_blank">IT</a>
     * <a href="https://www.amazon.co.jp/dp/B003ODIWEG" target="_blank">JP</a>
     * <a href="https://www.amazon.com.mx/dp/B003ODIWEG" target="_blank">MX</a>
     * <a href="https://www.amazon.nl/dp/B003ODIWEG" target="_blank">NL</a>
     * <a href="https://www.amazon.co.uk/dp/B003ODIWEG?ie=UTF8&amp;tag=isfdb-21"
     * target="_blank">UK</a>
     * <a href="https://www.amazon.com/dp/B003ODIWEG?ie=UTF8&amp;tag=isfdb-20&amp;
     * linkCode=as2&amp;camp=1789&amp;creative=9325" target="_blank">US</a>)
     * }
     * <p>
     * So for Amazon we only get a single link which is ok as the ASIN is the same in all.
     *
     * @param elements LI elements
     * @param bookData Bundle to populate
     */
    private void handleExternalIdElements(@NonNull final Iterable<Element> elements,
                                          @NonNull final Bundle bookData) {
        final Collection<String> externalIdUrls = new ArrayList<>();
        for (Element extIdLi : elements) {
            final Element extIdLink = extIdLi.select("a").first();
            externalIdUrls.add(extIdLink.attr("href"));
        }
        if (!externalIdUrls.isEmpty()) {
            for (String url : externalIdUrls) {
                if (url.contains("www.worldcat.org")) {
                    // http://www.worldcat.org/oclc/60560136
                    bookData.putString(DBDefinitions.KEY_EID_WORLDCAT, stripString(url, '/'));

                } else if (url.contains("amazon")) {
                    int start = url.lastIndexOf('/');
                    if (start != -1) {
                        int end = url.indexOf('?', start);
                        if (end == -1) {
                            end = url.length();
                        }
                        final String asin = url.substring(start + 1, end);
                        bookData.putString(DBDefinitions.KEY_EID_ASIN, asin);
                    }
                } else if (url.contains("lccn.loc.gov")) {
                    // Library of Congress (USA)
                    // http://lccn.loc.gov/2008299472
                    // http://lccn.loc.gov/95-22691
                    bookData.putString(DBDefinitions.KEY_EID_LCCN, stripString(url, '/'));

                    //            } else if (url.contains("explore.bl.uk")) {
                    // http://explore.bl.uk/primo_library/libweb/action/dlDisplay.do?
                    // vid=BLVU1&docId=BLL01014057142
                    // British Library

                    //            } else if (url.contains("d-nb.info")) {
                    // http://d-nb.info/986851329
                    // DEUTSCHEN NATIONALBIBLIOTHEK

                    //            } else if (url.contains("picarta.pica.nl")) {
                    // http://picarta.pica.nl/xslt/DB=3.9/XMLPRS=Y/PPN?PPN=802041833
                    // Nederlandse Bibliografie


                    //           } else if (url.contains("tercerafundacion.net")) {
                    // Spanish
                    // https://tercerafundacion.net/biblioteca/ver/libro/2329


                }
            }
        }
    }

    /**
     * Filter a string of all non-digits. Used to clean isbn strings, years... etc.
     *
     * @param s      string to parse
     * @param isIsbn When set will also allow 'X' and 'x'
     *
     * @return stripped string
     */
    @NonNull
    private String digits(@Nullable final String s,
                          final boolean isIsbn) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            // allows an X anywhere instead of just at the end; doesn't really matter.
            if (Character.isDigit(c) || (isIsbn && Character.toUpperCase(c) == 'X')) {
                sb.append(c);
            }
        }
        // ... but let empty Strings here just return.
        return sb.toString();
    }

    /**
     * Fetch the cover from the url.
     *
     * @param appContext Application context
     * @param coverUrl   fully qualified url
     * @param bookData   Bundle to populate
     */
    private void fetchCover(@NonNull final Context appContext,
                            @NonNull final String coverUrl,
                            @NonNull final Bundle /* in/out */ bookData) {
        final String tmpName = createTempCoverFileName(bookData);
        final String fileSpec = ImageUtils.saveImage(appContext, coverUrl, tmpName,
                                                     mSearchEngine.getConnectTimeoutMs(), null);

        if (fileSpec != null) {
            ArrayList<String> imageList =
                    bookData.getStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[0]);
            if (imageList == null) {
                imageList = new ArrayList<>();
            }
            imageList.add(fileSpec);
            bookData.putStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[0], imageList);
        }
    }

    @NonNull
    private String createTempCoverFileName(@NonNull final Bundle bookData) {
        String name = bookData.getString(DBDefinitions.KEY_ISBN, "");
        if (name.isEmpty()) {
            name = bookData.getString(DBDefinitions.KEY_EID_ISFDB, "");
        }
        if (name.isEmpty()) {
            // just use something...
            name = String.valueOf(System.currentTimeMillis());
        }
        return name + FILENAME_SUFFIX;
    }

    /**
     * Second ContentBox contains the TOC.
     * <pre>
     *     {@code
     *
     * <div class="ContentBox">
     *  <span class="containertitle">Collection Title:</span>
     *  <a href="http://www.isfdb.org/cgi-bin/title.cgi?37576">
     *      The Days of Perky Pat
     *  </a> &#8226;
     *  [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?22461">
     *      The Collected Stories of Philip K. Dick</a> &#8226; 4] &#8226; (1987) &#8226;
     *      collection by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23"
     *     >Philip K. Dick</a>
     *  <h2>Contents <a href="http://www.isfdb.org/cgi-bin/pl.cgi?230949+c">
     *      <span class="listingtext">(view Concise Listing)</span></a></h2>
     *  <ul>
     *
     * <li> == entry
     *
     * }
     * </pre>
     *
     * @param context          Current context
     * @param addSeriesFromToc whether the TOC should get parsed for Series information
     *
     * @return the TOC
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @NonNull
    @WorkerThread
    private ArrayList<TocEntry> getTocList(@NonNull final Context context,
                                           @NonNull final Bundle bookData,
                                           final boolean addSeriesFromToc)
            throws SocketTimeoutException {

        final ArrayList<TocEntry> results = new ArrayList<>();

        if (loadPage(context, mPath) == null) {
            // null result, abort
            return results;
        }

        // <div class="ContentBox"> but there are two, so get last one
        final Element contentBox = mDoc.select(CSS_Q_DIV_CONTENTBOX).last();
        final Elements lis = contentBox.select("li");
        for (Element li : lis) {

            /* LI entries, possibilities:
            7
            &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?118799">
                Introduction (The Days of Perky Pat)</a>
            &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226">
                Introductions to the Collected Stories of Philip K. Dick</a> &#8226; 4]
            &#8226; (1987)
            &#8226; essay by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?57">James Tiptree, Jr.</a>

            11
            &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?53646">Autofac</a>
            &#8226; (1955)
            &#8226; novelette by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23">
                Philip K. Dick</a>

            <a href="http://www.isfdb.org/cgi-bin/title.cgi?41613">Beyond Lies the Wub</a>
            &#8226; (1952)
            &#8226; short story by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23">
                Philip K. Dick</a>

            <a href="http://www.isfdb.org/cgi-bin/title.cgi?118803">
            Introduction (Beyond Lies the Wub)</a>
            &#8226; [ <a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226">
            Introductions to the Collected Stories of Philip K. Dick</a> &#8226; 1]
            &#8226; (1987)
            &#8226; essay by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?69">Roger Zelazny</a>

            61
            &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?417331">
                That Thou Art Mindful of Him</a>
            &#8226; (1974)
            &#8226; novelette by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?5">Isaac Asimov</a>
            (variant of <i><a href="http://www.isfdb.org/cgi-bin/title.cgi?50798">
                —That Thou Art Mindful of Him!</a></i>)

            A book belonging to a Series will have one content entry with the same title
            as the book, and potentially have the Series/nr in it:

            <a href="http://www.isfdb.org/cgi-bin/title.cgi?2210372">
                The Delirium Brief</a>
            &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?23081">
                Laundry Files</a> &#8226; 8]
            &#8226; (2017)
            &#8226; novel by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?2200">Charles Stross</a>

            ENHANCE: type of entry: "short story", "novelette", "essay", "novel"
            ENHANCE: if type "novel" -> *that* is the one to use for the first publication year

            2019-07: translation information seems to be added,
            and a further sub-classification (here: 'juvenile')

            <a href="http://www.isfdb.org/cgi-bin/title.cgi?1347238">
                Zwerftocht Tussen de Sterren</a>
            &#8226; juvenile
            &#8226; (1973)
            &#8226; novel by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?29">Robert A. Heinlein</a>
            (trans. of <a href="http://www.isfdb.org/cgi-bin/title.cgi?2233">
                <i>Citizen of the Galaxy</i></a> 1957)

            2019-09-26: this has been there for a longer time, but just noticed these:
            ISBN: 90-290-1541-1
            7 •  Één Nacht per Jaar • interior artwork by John Stewart
            9 • Één Nacht per Jaar • [Cyrion] • novelette by Tanith Lee
                (trans. of One Night of the Year 1980)
            39 •  Aaches Geheim • interior artwork by Jim Pitts
            41 • Aaches Geheim • [Dilvish] • short story by Roger Zelazny
                (trans. of The Places of Aache 1980)

            iow: each story appears twice due to the extra interior artwork.
            For now, we will get two entries in the TOC, same title but different author.
            TODO: avoid duplicate TOC entries when there are two lines.
             */
            final String liAsString = li.toString();
            String title = null;
            Author author = null;
            final Elements aas = li.select("a");
            // find the first occurrence of each
            for (Element a : aas) {
                final String href = a.attr("href");

                if (title == null && href.contains(IsfdbSearchEngine.URL_TITLE_CGI)) {
                    title = cleanUpName(a.text());
                    //ENHANCE: tackle 'variant' titles later

                } else if (author == null && href.contains(IsfdbSearchEngine.URL_EA_CGI)) {
                    author = Author.from(cleanUpName(a.text()));

                } else if (addSeriesFromToc && href.contains(IsfdbSearchEngine.URL_PE_CGI)) {
                    final Series series = Series.from(a.text());

                    //  • 4] • (1987) • novel by
                    String nr = a.nextSibling().toString();
                    final int dotIdx = nr.indexOf('•');
                    if (dotIdx != -1) {
                        final int closeBrIdx = nr.indexOf(']');
                        if (closeBrIdx > dotIdx) {
                            nr = nr.substring(dotIdx + 1, closeBrIdx).trim();
                            if (!nr.isEmpty()) {
                                series.setNumber(nr);
                            }
                        }
                    }
                    mSeries.add(series);
                }
            }

            // no author found, set to 'unknown, unknown'
            // example when this happens: ISBN=044100590X
            // <li> 475 • <a href="http://www.isfdb.org/cgi-bin/title.cgi?1659151">
            //      Appendixes (Dune)</a> • essay by uncredited
            // </li>
            if (author == null) {
                author = Author.createUnknownAuthor(context);
            }
            // very unlikely
            if (title == null) {
                title = "";
                Logger.warn(context, TAG, "getTocList"
                                          + "|ISBN=" + bookData.getString(DBDefinitions.KEY_ISBN)
                                          + "|no title for li=" + li);
            }

            // scan for first occurrence of "• (1234)"
            final Matcher matcher = YEAR_PATTERN.matcher(liAsString);
            final String year = matcher.find() ? matcher.group(2) : "";
            // see if we can use it as the first publication year for the book.
            // i.e. if this entry has the same title as the book title
            if ((mFirstPublication == null || mFirstPublication.isEmpty())
                && title.equalsIgnoreCase(mTitle)) {
                mFirstPublication = digits(year, false);
            }

            final TocEntry tocEntry = new TocEntry(author, title, year);
            results.add(tocEntry);
        }

        return results;
    }

    /**
     * ISFDB specific field names we add to the bundle based on parsed XML data.
     */
    static class BookField {
        // private static final String AUTHOR_ID = "__ISFDB_AUTHORS_ID";
        // private static final String SERIES_ID = "__ISFDB_SERIES_ID";
        // private static final String PUBLISHER_ID = "__ISFDB_PUBLISHER_ID";
        // private static final String EDITORS_ID = "__ISFDB_EDITORS_ID";
        // private static final String BOOK_COVER_ARTIST_ID = "__ISFDB_BOOK_COVER_ARTIST_ID";
        // private static final String BOOK_COVER_ART_TXT = "__BOOK_COVER_ART_TXT";

        static final String BOOK_TYPE = "__ISFDB_BOOK_TYPE";
        static final String ISBN_2 = "__ISFDB_ISBN2";
    }
}
