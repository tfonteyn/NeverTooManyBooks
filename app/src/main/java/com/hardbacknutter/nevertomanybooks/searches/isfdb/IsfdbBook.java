/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.searches.isfdb;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.entities.Author;
import com.hardbacknutter.nevertomanybooks.entities.Format;
import com.hardbacknutter.nevertomanybooks.entities.Series;
import com.hardbacknutter.nevertomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;

public class IsfdbBook
        extends AbstractBase {

    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_ISFDB";

    /** Param 1: ISFDB native book id. */
    private static final String BOOK_URL = IsfdbManager.CGI_BIN
                                           + IsfdbManager.URL_PL_CGI + "?%1$s";

    private static final Map<String, Integer> TYPE_MAP = new HashMap<>();
    /**
     * Either the Web page itself, and/or the JSoup parser has used both decimal and hex
     * representation for the "•" character. Capturing all 3 possibilities here.
     */
    private static final String DOT = "(&#x2022;|&#8226;|•)";
    /** Character used by the site as string divider/splitter. */
    private static final Pattern DOT_PATTERN = Pattern.compile(DOT);
    private static final Pattern YEAR_PATTERN = Pattern.compile(DOT + " \\(([1|2]\\d\\d\\d)\\)");
    /** ISFDB uses 00 for the day/month when unknown. We cut that out. */
    private static final Pattern UNKNOWN_M_D_PATTERN = Pattern.compile("-00", Pattern.LITERAL);

    /*
     * <a href="http://www.isfdb.org/wiki/index.php/Help:Screen:NewPub#Publication_Type">
     *     http://www.isfdb.org/wiki/index.php/Help:Screen:NewPub#Publication_Type
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
     * After processing MULTIPLE_AUTHORS is added when needed.
     *
     * Reminder: a "Digest" is a format, not a type.
     */
    static {
        // multiple works, one author
        TYPE_MAP.put("coll", TocEntry.Authors.MULTIPLE_WORKS);
        TYPE_MAP.put("COLLECTION", TocEntry.Authors.MULTIPLE_WORKS);

        // multiple works, multiple authors
        TYPE_MAP.put("anth",
                     TocEntry.Authors.MULTIPLE_WORKS | TocEntry.Authors.MULTIPLE_AUTHORS);
        TYPE_MAP.put("ANTHOLOGY",
                     TocEntry.Authors.MULTIPLE_WORKS | TocEntry.Authors.MULTIPLE_AUTHORS);

        // multiple works that have previously been published independently
        TYPE_MAP.put("omni",
                     TocEntry.Authors.MULTIPLE_WORKS | TocEntry.Authors.MULTIPLE_AUTHORS);
        TYPE_MAP.put("OMNIBUS",
                     TocEntry.Authors.MULTIPLE_WORKS | TocEntry.Authors.MULTIPLE_AUTHORS);

        TYPE_MAP.put("MAGAZINE", TocEntry.Authors.MULTIPLE_WORKS);

        // others, treated as a standard book.
//        TYPE_MAP.put("novel", 0);
//        TYPE_MAP.put("NOVEL", 0);
//
//        TYPE_MAP.put("chap", 0);
//        TYPE_MAP.put("CHAPBOOK", 0);
//
//        TYPE_MAP.put("non-fic", 0);
//        TYPE_MAP.put("NONFICTION", 0);
    }

    /** accumulate all authors for this book. */
    @NonNull
    private final ArrayList<Author> mAuthors = new ArrayList<>();
    /** accumulate all series for this book. */
    @NonNull
    private final ArrayList<Series> mSeries = new ArrayList<>();
    @NonNull
    private final Format mFormatMap;
    private String mPath;
    /** List of all editions (ISFDB 'publicationRecord') of this book. */
    private List<Editions.Edition> mEditions;
    /** set during book load, used during content table load. */
    @Nullable
    private String mTitle;
    /** with some luck we'll get these as well. */
    @Nullable
    private String mFirstPublication;

    IsfdbBook(@NonNull final Context context) {
        super();
        mFormatMap = new Format(context);
    }

    //ENHANCE: pass and store these ISFDB ID's?
//    private final ArrayList<Long> ISFDB_AUTHOR_ID_LIST = new ArrayList<>();
//    private final ArrayList<Long> ISFDB_SERIES_ID_LIST = new ArrayList<>();


    @VisibleForTesting
    IsfdbBook(@NonNull final Context context,
              @Nullable final Document doc) {
        super(doc);
        mFormatMap = new Format(context);
    }

    @Nullable
    public List<Editions.Edition> getEditions() {
        return mEditions;
    }

    /**
     * @param isfdbId        ISFDB native book id
     * @param fetchThumbnail whether to get thumbnails as well
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException on timeout
     */
    @NonNull
    public Bundle fetch(final long isfdbId,
                        final boolean fetchThumbnail)
            throws SocketTimeoutException {

        return fetch(IsfdbManager.getBaseURL() + String.format(BOOK_URL, isfdbId),
                     fetchThumbnail);
    }

    /**
     * @param path           A fully qualified ISFDB search url
     * @param fetchThumbnail whether to get thumbnails as well
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException on timeout
     */
    @NonNull
    public Bundle fetch(@NonNull final String path,
                        final boolean fetchThumbnail)
            throws SocketTimeoutException {

        mPath = path;

        if (loadPage(mPath) == null) {
            return new Bundle();
        }

        Bundle bookData = new Bundle();
        return parseDoc(bookData, fetchThumbnail);
    }

    /**
     * @param editions       List of ISFDB Editions with native book id
     * @param fetchThumbnail whether to get thumbnails as well
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException on timeout
     */
    @NonNull
    public Bundle fetch(@Size(min = 1) @NonNull final List<Editions.Edition> editions,
                        final boolean fetchThumbnail)
            throws SocketTimeoutException {

        mEditions = editions;

        Editions.Edition edition = editions.get(0);
        mPath = IsfdbManager.getBaseURL() + String.format(BOOK_URL, edition.isfdbId);

        // check if we already got the book
        if (edition.doc != null) {
            mDoc = edition.doc;
        } else {
            // nop, go get it.
            if (loadPage(mPath) == null) {
                return new Bundle();
            }
        }

        Bundle bookData = new Bundle();
        return parseDoc(bookData, fetchThumbnail);
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
     *            <li><b>Publication:</b> The Days of Perky Pat <span class="recordID"><b>Publication Record # </b>230949</span>
     *            <li><b>Author:</b> <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
     *            <li><b>Date:</b> 1991-05-00
     *            <li><b>ISBN:</b> 0-586-20768-6 [<small>978-0-586-20768-0</small>]
     *            <li><b>Publisher:</b> <a href="http://www.isfdb.org/cgi-bin/publisher.cgi?62" dir="ltr">Grafton</a>
     *            <li><b>Price:</b> £5.99
     *            <li><b>Pages:</b> 494
     *            <li><b>Format:</b> <div class="tooltip">tp<sup class="mouseover">?</sup><span class="tooltiptext tooltipnarrow">Trade paperback. Any softcover book which is at least 7.25" (or 19 cm) tall, or at least 4.5" (11.5 cm) wide/deep.</span></div>
     *            <li><b>Type:</b> COLLECTION
     *            <li><b>Cover:</b><a href="http://www.isfdb.org/cgi-bin/title.cgi?737949" dir="ltr">The Days of Perky Pat</a>  by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?21338" dir="ltr">Chris Moore</a>
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
     *  Cover art supplied by <a href="http://www.isfdb.org/wiki/index.php/Image:THDSFPRKPT1991.jpg"
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
     *         <img src="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg" alt="picture" class="scan"></a>
     *       </td>
     *
     *       <td class="pubheader">
     *         <ul>
     *           <li><b>Publication:</b> The Days of Perky Pat<span class="recordID"><b>Publication Record # </b>230949 [<a href="http://www.isfdb.org/cgi-bin/edit/editpub.cgi?230949">Edit</a>]</span>
     *           <li><b>Author:</b><a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
     *           <li> <b>Date:</b> 1991-05-00
     *           <li><b>ISBN:</b> 0-586-20768-6 [<small>978-0-586-20768-0</small>]
     *           <li><b>Publisher:</b> <a href="http://www.isfdb.org/cgi-bin/publisher.cgi?62" dir="ltr">Grafton</a>
     *           <li><b>Price:</b> £5.99
     *           <li><b>Pages:</b> 494
     *           <li><b>Format:</b> <div class="tooltip">tp<sup class="mouseover">?</sup><span class="tooltiptext tooltipnarrow">Trade paperback. Any softcover book which is at least 7.25" (or 19 cm) tall, or at least 4.5" (11.5 cm) wide/deep.</span></div>
     *           <li><b>Type:</b> COLLECTION
     *           <li><b>Cover:</b><a href="http://www.isfdb.org/cgi-bin/title.cgi?737949" dir="ltr">The Days of Perky Pat</a>  by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?21338" dir="ltr">Chris Moore</a>
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
     *               <li> <abbr class="template" title="Online Computer Library Center">OCLC/WorldCat</abbr>:  <a href="http://www.worldcat.org/oclc/60047795" target="_blank">60047795</a>
     *             </ul>
     *           <li><a href="http://www.isfdb.org/wiki/index.php/Special:Upload?wpDestFile=THDSFPRKPT1991.jpg&amp;wpUploadDescription=%7B%7BCID1%0A%7CTitle%3DThe%20Days%20of%20Perky%20Pat%0A%7CEdition%3DGrafton%201991%20tp%0A%7CPub%3DTHDSFPRKPT1991%0A%7CPublisher%3DGrafton%0A%7CArtist%3DChris%20Moore%0A%7CArtistId%3D21338%0A%7CSource%3DScanned%20by%20%5B%5BUser%3AHardbackNut%5D%5D%7D%7D" target="_blank">Upload new cover scan</a>
     *         </ul>
     *       </td>
     *   </table>
     * Cover art supplied by <a href="http://www.isfdb.org" target="_blank">ISFDB</a> on <a href="http://www.isfdb.org/wiki/index.php/Image:THDSFPRKPT1991.jpg" target="_blank">this Web page</a>
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
     *    <li> <abbr class="template" title="Library of Congress Control Number">LCCN</abbr>:  <a href="http://lccn.loc.gov/85070137" target="_blank">85070137</a>
     *    <li> <abbr class="template" title="Online Computer Library Center">OCLC/WorldCat</abbr>:  <a href="http://www.worldcat.org/oclc/13063516" target="_blank">13063516</a>
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
     *     <li>Month from Amazon.co.uk and publisher's web site.<li>First print by full number line 1 3 5 7 9 10 8 6 4 2
     *     <li>Cover image: © Shutterstock<li>Design by www.blacksheep-uk.com
     *     <li>Author photo: Alastair Reynolds © Barbara Bella
     *     <li>Afterword on page [327]
     *   </ul>
     * </div>
     * }
     * </pre>
     *
     * @param bookData       a new Bundle()  (must be passed in so we mock it in test)
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data, can be empty, but never {@code null}
     */
    @NonNull
    @VisibleForTesting
    Bundle parseDoc(@NonNull final Bundle bookData,
                    final boolean fetchThumbnail)
            throws SocketTimeoutException {

        Element contentBox = mDoc.select("div.contentbox").first();
        Element ul = contentBox.select("ul").first();

        Elements lis = ul.children();

        String tmpString;

        for (Element li : lis) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB_SEARCH) {
                Logger.debug(this, "fetch", li.toString());
            }
            try {
                Elements children = li.children();

                String fieldName = null;

                Element fieldLabelElement = children.first();
                if (fieldLabelElement != null && fieldLabelElement.childNodeSize() > 0) {
                    fieldName = fieldLabelElement.childNode(0).toString();
                }

                if (fieldName == null) {
                    continue;
                }

                // bit of a hack, but it's getting around yet another complication
                // where a field can be embedded in an extra <div>
                if (fieldName.startsWith("<b>") && fieldName.endsWith("</b>")) {
                    fieldName = fieldName.substring(3, fieldName.length() - 4);
                }

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB_SEARCH) {
                    Logger.debug(this, "fetch", "fieldName=`" + fieldName + '`');
                }

                if ("Publication:".equalsIgnoreCase(fieldName)) {
                    mTitle = li.childNode(1).toString().trim();
                    bookData.putString(DBDefinitions.KEY_TITLE, mTitle);

                } else if ("Author:".equalsIgnoreCase(fieldName)
                           || "Authors:".equalsIgnoreCase(fieldName)) {
                    Elements as = li.select("a");
                    if (as != null) {
                        for (Element a : as) {
                            mAuthors.add(Author.fromString(a.text()));
                            //ENHANCE: pass and store these ISFDB ID's
//                            ISFDB_AUTHOR_ID_LIST.add(stripNumber(a.attr("href")));
                        }
                    }
                } else if ("Date:".equalsIgnoreCase(fieldName)) {
                    // dates are in fact displayed as YYYY-MM-DD which is very nice.
                    tmpString = li.childNode(2).toString().trim();
                    // except that ISFDB uses 00 for the day/month when unknown ...
                    // e.g. "1975-04-00" or "1974-00-00" Cut that part off.
                    tmpString = UNKNOWN_M_D_PATTERN.matcher(tmpString).replaceAll(
                            Matcher.quoteReplacement(""));
                    // and we're paranoid...
                    Date d = DateUtils.parseDate(tmpString);
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
                    tmpString = li.childNode(1).toString().trim();
                    bookData.putString(DBDefinitions.KEY_ISBN, digits(tmpString, true));

                    tmpString = li.childNode(2).childNode(0).toString().trim();
                    bookData.putString(BookField.ISBN_2, digits(tmpString, true));

                } else if ("Publisher:".equalsIgnoreCase(fieldName)) {
                    //tmp = li.childNode(3).attr("href");
                    //ENHANCE: pass and store these ISFDB ID's
                    //bookData.putString(BookField.PUBLISHER_ID, String.valueOf(stripNumber(tmp)));

                    tmpString = li.childNode(3).childNode(0).toString().trim();
                    bookData.putString(DBDefinitions.KEY_PUBLISHER, tmpString);

                } else if ("Pub. Series:".equalsIgnoreCase(fieldName)) {
                    Elements as = li.select("a");
                    if (as != null) {
                        for (Element a : as) {
                            mSeries.add(Series.fromString(a.text()));
                            //ENHANCE: pass and store these ISFDB ID's
//                            ISFDB_BKEY_SERIES_ID_LIST.add(stripNumber(a.attr("href")));
                        }
                    }
                } else if ("Pub. Series #:".equalsIgnoreCase(fieldName)) {
                    tmpString = li.childNode(2).toString().trim();
                    // assume that if we get here, then we added a "Pub. Series:" as last one.
                    mSeries.get(mSeries.size() - 1).setNumber(tmpString);

                } else if ("Price:".equalsIgnoreCase(fieldName)) {
                    tmpString = li.childNode(2).toString().trim();
                    LocaleUtils.splitPrice(tmpString,
                                           DBDefinitions.KEY_PRICE_LISTED,
                                           DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                                           bookData);

                } else if ("Pages:".equalsIgnoreCase(fieldName)) {
                    tmpString = li.childNode(2).toString().trim();
                    bookData.putString(DBDefinitions.KEY_PAGES, tmpString);

                } else if ("Format:".equalsIgnoreCase(fieldName)) {
                    // <li><b>Format:</b> <div class="tooltip">tp<sup class="mouseover">?</sup>
                    // <span class="tooltiptext tooltipnarrow">Trade paperback. bla bla...
                    // need to lift "tp".
                    tmpString = li.childNode(3).childNode(0).toString().trim();
                    bookData.putString(DBDefinitions.KEY_FORMAT, mFormatMap.map(tmpString));

                } else if ("Type:".equalsIgnoreCase(fieldName)) {
                    // <li><b>Type:</b> COLLECTION
                    tmpString = li.childNode(2).toString().trim();
                    bookData.putString(BookField.BOOK_TYPE, tmpString);
                    Integer type = TYPE_MAP.get(tmpString);
                    if (type != null) {
                        bookData.putLong(DBDefinitions.KEY_TOC_BITMASK, type);
                    }

//                } else if ("Cover:".equalsIgnoreCase(fieldName)) {
//                    //TODO: if there are multiple art/artists... will this barf ?
//                    tmp = li.childNode(2).childNode(0).toString().trim();
//                    bookData.putString(BookField.BOOK_COVER_ART_TXT, tmp);
//
//                    // Cover artist
//                    Node node_a = li.childNode(4);
//                    StringList.addOrAppend(bookData, BookField.BOOK_COVER_ARTIST_ID,
//                                           String.valueOf(stripNumber(node_a.attr("href"))));
//                    StringList.addOrAppend(bookData, BookField.BOOK_COVER_ARTIST,
//                                           node_a.childNode(0).toString().trim());

                } else if ("External IDs:".equalsIgnoreCase(fieldName)) {
                    // send the <ul> children
                    handleExternalIdUrls(li.child(1).children(), bookData);

//                } else if ("Editors:".equalsIgnoreCase(fieldName)) {
                    // add these to the authors as other sites do ?
//                    Elements as = li.select("a");
//                    if (as != null) {
//                        for (Element a : as) {
//                            StringList.addOrAppend(bookData, BookField.BKEY_EDITORS_ID,
//                                                   String.valueOf(stripNumber(a.attr("href"))));
//                            StringList.addOrAppend(bookData, BookField.EDITORS, a.text());
//                        }
//                    }
                }
            } catch (@NonNull final IndexOutOfBoundsException e) {
                // does not happen now, but could happen if we come about non-standard entries,
                // or if ISFDB website changes
                Logger.error(this, e, "path: " + mPath + "\n\nLI: " + li.toString());
            }
        }

        // publication record.
//        tmpString = li.childNode(2).childNode(1).toString().trim();
//        try {
//            long record = Long.parseLong(tmpString);
//            bookData.putLong(DBDefinitions.KEY_ISFDB_ID, record);
//        } catch (@NonNull final NumberFormatException ignore) {
//        }
        Elements recordIDDiv = contentBox.select("span.recordID");
        if (recordIDDiv != null) {
            tmpString = recordIDDiv.first().childNode(1).toString().trim();
            tmpString = digits(tmpString, false);
            if (tmpString != null && !tmpString.isEmpty()) {
                try {
                    long record = Long.parseLong(tmpString);
                    bookData.putLong(DBDefinitions.KEY_ISFDB_ID, record);
                } catch (@NonNull final NumberFormatException ignore) {
                }
            }
        }

        //ENHANCE: it would make much more sense to get the notes from the URL_TITLE_CGI page.
        Elements notesDiv = contentBox.select("div.notes");
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
        //ENHANCE: they are adding language to the data.. revisit.
        // Default to a localised 'English" as ISFDB is after all (I presume) 95% english
        bookData.putString(DBDefinitions.KEY_LANGUAGE, Locale.ENGLISH.getISO3Language());

        boolean addSeriesFromToc = PreferenceManager
                                           .getDefaultSharedPreferences(App.getAppContext())
                                           .getBoolean(IsfdbManager.PREFS_SERIES_FROM_TOC,
                                                       false);

        // the table of content
        ArrayList<TocEntry> toc = getTocList(bookData, addSeriesFromToc);
        bookData.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, toc);

        // store accumulated ArrayList's, do this *after* we got the TOC
        bookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mAuthors);
        bookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, mSeries);

        //ENHANCE: pass and store these ISFDB ID's
//        bookData.putParcelableArrayList(BookField.AUTHOR_ID, ISFDB_BKEY_AUTHOR_ID_LIST);
//        bookData.putParcelableArrayList(BookField.SERIES_ID, ISFDB_BKEY_SERIES_ID_LIST);

        // set Anthology type
        if (!toc.isEmpty()) {
            int type = TocEntry.Authors.MULTIPLE_WORKS;
            if (TocEntry.hasMultipleAuthors(toc)) {
                type |= TocEntry.Authors.MULTIPLE_AUTHORS;
            }
            bookData.putLong(DBDefinitions.KEY_TOC_BITMASK, type);
        }

        // try to deduce the first publication date
        if (toc.size() == 1) {
            // if the content table has only one entry,
            // then this will have the first publication year for sure
            String d = digits(toc.get(0).getFirstPublication(), false);
            if (d != null && !d.isEmpty()) {
                bookData.putString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION, d);
            }
        } else if (toc.size() > 1) {
            // we gamble and take what we found in the TOC
            if (mFirstPublication != null) {
                bookData.putString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION,
                                   digits(mFirstPublication, false));
            } // else take the book pub date ... but that might be wrong....
        }

        // optional fetch of the cover.
        if (fetchThumbnail) {
            fetchCover(bookData, mDoc.select("div.contentbox").first());
        }

        return bookData;
    }

    /**
     * All lines are normally:
     * <li> <abbr class="template" title="Online Computer Library Center">OCLC/WorldCat</abbr>:
     * <a href="http://www.worldcat.org/oclc/963112443" target="_blank">963112443</a>
     * <p>
     * Except for Amazon:
     *
     * <li> <abbr class="template" title="Amazon Standard Identification Number">ASIN</abbr>:  B003ODIWEG
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
     * <a href="https://www.amazon.co.uk/dp/B003ODIWEG?ie=UTF8&amp;tag=isfdb-21" target="_blank">UK</a>
     * <a href="https://www.amazon.com/dp/B003ODIWEG?ie=UTF8&amp;tag=isfdb-20&amp;linkCode=as2&amp;camp=1789&amp;creative=9325" target="_blank">US</a>)
     *
     * @param elements LI elements
     * @param bookData bundle to store the findings.
     */
    private void handleExternalIdUrls(@NonNull final Elements elements,
                                      @NonNull final Bundle bookData) {
        List<String> externalIdUrls = new ArrayList<>();
        for (Element extIdLi : elements) {
            Element extIdLink = extIdLi.select("a").first();
            externalIdUrls.add(extIdLink.attr("href"));
        }
        if (externalIdUrls.size() > 0) {
            handleExternalIdUrls(externalIdUrls, bookData);
        }
    }

    /**
     * @param urlList  clean url strings to external sites.
     * @param bookData bundle to store the findings.
     */
    private void handleExternalIdUrls(@NonNull final List<String> urlList,
                                      @NonNull final Bundle bookData) {
        for (String url : urlList) {
            if (url.contains("www.worldcat.org")) {
                // http://www.worldcat.org/oclc/60560136
                bookData.putLong(DBDefinitions.KEY_WORLDCAT_ID, stripNumber(url, '/'));
            } else if (url.contains("amazon")) {
                // this is an Amazon ASIN link.
                bookData.putString(DBDefinitions.KEY_ASIN, stripString(url, '/'));


//            } else if (url.contains("lccn.loc.gov")) {
                // Library of Congress (USA)
                // http://lccn.loc.gov/2008299472
                // http://lccn.loc.gov/95-22691


//            } else if (url.contains("explore.bl.uk")) {
                // http://explore.bl.uk/primo_library/libweb/action/dlDisplay.do?vid=BLVU1&docId=BLL01014057142
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

    /**
     * Filter a string of all non-digits. Used to clean isbn strings, years... etc.
     *
     * @param s      string to parse
     * @param isIsbn When set will also allow 'X' and 'x'
     *
     * @return stripped string
     */
    @Nullable
    private String digits(@Nullable final String s,
                          final boolean isIsbn) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // allows an X anywhere instead of just at the end; doesn't really matter.
            if (Character.isDigit(c) || (isIsbn && Character.toUpperCase(c) == 'X')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * First "ContentBox" contains all basic details.
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
    private void fetchCover(@NonNull final Bundle /* out */ bookData,
                            @NonNull final Element contentBox) {
        Element img = contentBox.selectFirst("img");
        if (img != null) {
            String url = img.attr("src");
            String isbn = bookData.getString(DBDefinitions.KEY_ISBN, "");
            if (isbn.isEmpty()) {
                isbn = bookData.getString(DBDefinitions.KEY_ISFDB_ID, "");
            }
            String fileSpec = ImageUtils.saveImage(url, isbn, FILENAME_SUFFIX);

            if (fileSpec != null) {
                ArrayList<String> imageList =
                        bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
                if (imageList == null) {
                    imageList = new ArrayList<>();
                }
                imageList.add(fileSpec);
                bookData.putStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY, imageList);
            }
        }
    }

    /**
     * Second ContentBox contains the TOC.
     * <pre>
     *     {@code
     *
     * <div class="ContentBox">
     *  <span class="containertitle">Collection Title:</span>
     *  <a href="http://www.isfdb.org/cgi-bin/title.cgi?37576" dir="ltr">
     *      The Days of Perky Pat
     *  </a> &#8226;
     *  [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?22461" dir="ltr">
     *      The Collected Stories of Philip K. Dick</a> &#8226; 4] &#8226; (1987) &#8226;
     *      collection by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>
     *  <h2>Contents <a href="http://www.isfdb.org/cgi-bin/pl.cgi?230949+c">
     *      <span class="listingtext">(view Concise Listing)</span></a></h2>
     *  <ul>
     *
     * <li> == entry
     *
     * }
     * </pre>
     *
     * @return the TOC
     *
     * @throws SocketTimeoutException on timeout
     */
    @NonNull
    private ArrayList<TocEntry> getTocList(@NonNull final Bundle bookData,
                                           final boolean addSeriesFromToc)
            throws SocketTimeoutException {

        final ArrayList<TocEntry> results = new ArrayList<>();

        if (loadPage(mPath) == null) {
            return results;
        }

        // <div class="ContentBox"> but there are two, so get last one
        Element contentBox = mDoc.select("div.contentbox").last();
        Elements lis = contentBox.select("li");
        for (Element li : lis) {

            /* LI entries, possibilities:

            7
            &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?118799" dir="ltr">Introduction (The Days of Perky Pat)</a>
            &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226" dir="ltr">Introductions to the Collected Stories of Philip K. Dick</a> &#8226; 4]
            &#8226; (1987)
            &#8226; essay by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?57" dir="ltr">James Tiptree, Jr.</a>


            11
            &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?53646" dir="ltr">Autofac</a>
            &#8226; (1955)
            &#8226; novelette by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>


            <a href="http://www.isfdb.org/cgi-bin/title.cgi?41613" dir="ltr">Beyond Lies the Wub</a>
            &#8226; (1952)
            &#8226; short story by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?23" dir="ltr">Philip K. Dick</a>


            <a href="http://www.isfdb.org/cgi-bin/title.cgi?118803" dir="ltr">Introduction (Beyond Lies the Wub)</a>
            &#8226; [ <a href="http://www.isfdb.org/cgi-bin/pe.cgi?31226" dir="ltr">Introductions to the Collected Stories of Philip K. Dick</a> &#8226; 1]
            &#8226; (1987)
            &#8226; essay by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?69" dir="ltr">Roger Zelazny</a>


            61
            &#8226; <a href="http://www.isfdb.org/cgi-bin/title.cgi?417331" dir="ltr">That Thou Art Mindful of Him</a>
            &#8226; (1974)
            &#8226; novelette by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?5" dir="ltr">Isaac Asimov</a>
            (variant of <i><a href="http://www.isfdb.org/cgi-bin/title.cgi?50798" dir="ltr">—That Thou Art Mindful of Him!</a></i>)


            A book belonging to a series will have one content entry with the same title as the book.
            And potentially have the series/nr in it:

            <a href="http://www.isfdb.org/cgi-bin/title.cgi?2210372" dir="ltr">The Delirium Brief</a>
            &#8226; [<a href="http://www.isfdb.org/cgi-bin/pe.cgi?23081" dir="ltr">Laundry Files</a> &#8226; 8]
            &#8226; (2017)
            &#8226; novel by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?2200" dir="ltr">Charles Stross</a>

            ENHANCE: type of entry: "short story", "novelette", "essay", "novel"
            ENHANCE: if type "novel" -> *that* is the one to use for the first publication year


            2019-07: translation information seems to be added,
            and a further sub-classification (here: 'juvenile')

            <a href="http://www.isfdb.org/cgi-bin/title.cgi?1347238" dir="ltr">Zwerftocht Tussen de Sterren</a>
            &#8226; juvenile
            &#8226; (1973)
            &#8226; novel by <a href="http://www.isfdb.org/cgi-bin/ea.cgi?29" dir="ltr">Robert A. Heinlein</a>
            (trans. of <a href="http://www.isfdb.org/cgi-bin/title.cgi?2233" dir="ltr"><i>Citizen of the Galaxy</i></a> 1957)

             */
            String liAsString = li.toString();
            String title = null;
            Author author = null;
            Elements aas = li.select("a");
            // find the first occurrence of each
            for (Element a : aas) {
                String href = a.attr("href");

                if (title == null && href.contains(IsfdbManager.URL_TITLE_CGI)) {
                    title = cleanUpName(a.text());
                    //ENHANCE: tackle 'variant' titles later

                } else if (author == null && href.contains(IsfdbManager.URL_EA_CGI)) {
                    author = Author.fromString(cleanUpName(a.text()));

                } else if (addSeriesFromToc && mSeries.isEmpty()
                           && href.contains(IsfdbManager.URL_PE_CGI)) {
                    String seriesName = a.text();
                    String seriesNum = "";
                    // check for the number; series don't always have a number
                    int start = liAsString.indexOf('[');
                    int end = liAsString.indexOf(']');
                    if (start > 1 && end > start) {
                        String tmp = liAsString.substring(start, end);
                        String[] data = DOT_PATTERN.split(tmp);
                        // check if there really was a series number
                        if (data.length > 1) {
                            seriesNum = Series.cleanupSeriesPosition(data[1]);
                        }
                    }
                    Series newSeries = new Series(seriesName);
                    newSeries.setNumber(seriesNum);
                    mSeries.add(newSeries);
                }
            }

            // unlikely, but if so, then grab first book author
            if (author == null) {
                author = mAuthors.get(0);
                Logger.warn(this, "getTocList",
                            "ISBN=" + bookData.getString(DBDefinitions.KEY_ISBN),
                            "ISFDB search for content found no author for li=" + li);
            }
            // very unlikely
            if (title == null) {
                title = "";
                Logger.warn(this, "getTocList",
                            "ISBN=" + bookData.getString(DBDefinitions.KEY_ISBN),
                            "ISFDB search for content found no title for li=" + li);
            }

            // scan for first occurrence of "• (1234)"
            Matcher matcher = YEAR_PATTERN.matcher(liAsString);
            String year = matcher.find() ? matcher.group(2) : "";
            // see if we can use it as the first publication year for the book.
            // i.e. if this entry has the same title as the book title
            if (mFirstPublication == null && title.equalsIgnoreCase(mTitle)) {
                mFirstPublication = year;
            }

            TocEntry tocEntry = new TocEntry(author, title, year);
            results.add(tocEntry);
        }

        return results;
    }

    /**
     * ISFDB specific field names we add to the bundle based on parsed XML data.
     */
    static class BookField {

//        private static final String AUTHOR_ID = "__ISFDB_AUTHORS_ID";
//        private static final String SERIES_ID = "__ISFDB_SERIES_ID";
//        private static final String PUBLISHER_ID = "__ISFDB_PUBLISHER_ID";
//        private static final String EDITORS_ID = "__ISFDB_EDITORS_ID";
//        private static final String BOOK_COVER_ARTIST_ID = "__ISFDB_BOOK_COVER_ARTIST_ID";
//        private static final String BOOK_COVER_ART_TXT = "__BOOK_COVER_ART_TXT";

        static final String BOOK_TYPE = "__ISFDB_BOOK_TYPE";
        static final String ISBN_2 = "__ISFDB_ISBN2";
    }
}
