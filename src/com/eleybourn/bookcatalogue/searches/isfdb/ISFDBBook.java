package com.eleybourn.bookcatalogue.searches.isfdb;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Format;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

public class ISFDBBook
        extends AbstractBase {

    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_ISFDB";
    /** Param 1: ISFDB native book id. */
    private static final String BOOK_URL = "/cgi-bin/pl.cgi?%1$s";
    /**
     * ISFDB extra fields in the results for potential future usage.
     * ENHANCE: pass and store these ISFBD id's
     */
//    private static final String ISFDB_BKEY_AUTHOR_ID = "__ISFDB_AUTHORS_ID";
//    private static final String ISFDB_BKEY_SERIES_ID = "__ISFDB_SERIES_ID";
//    private static final String ISFDB_BKEY_PUBLISHER_ID = "__ISFDB_PUBLISHER_ID";
//    private static final String ISFDB_BKEY_EDITORS_ID = "__ISFDB_EDITORS_ID";
//    private static final String ISFDB_BKEY_BOOK_COVER_ARTIST_ID = "__ISFDB_BOOK_COVER_ARTIST_ID";

    private static final String ISFDB_BKEY_BOOK_TYPE = "__ISFDB_BOOK_TYPE";
    private static final String ISFDB_BKEY_ISBN_2 = "__ISFDB_ISBN2";
    private static final Map<String, Integer> TYPE_MAP = new HashMap<>();
    private static final Pattern YEAR_FROM_LI = Pattern.compile("&#x2022; \\(([1|2]\\d\\d\\d)\\)");

    /*
     * http://www.isfdb.org/wiki/index.php/Help:Screen:NewPub#Publication_Type
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
        TYPE_MAP.put("coll", TocEntry.Type.MULTIPLE_WORKS);
        TYPE_MAP.put("COLLECTION", TocEntry.Type.MULTIPLE_WORKS);

        // multiple works, multiple authors
        TYPE_MAP.put("anth", TocEntry.Type.MULTIPLE_WORKS | TocEntry.Type.MULTIPLE_AUTHORS);
        TYPE_MAP.put("ANTHOLOGY", TocEntry.Type.MULTIPLE_WORKS | TocEntry.Type.MULTIPLE_AUTHORS);

        // multiple works that have previously been published independently
        TYPE_MAP.put("omni", TocEntry.Type.MULTIPLE_WORKS | TocEntry.Type.MULTIPLE_AUTHORS);
        TYPE_MAP.put("OMNIBUS", TocEntry.Type.MULTIPLE_WORKS | TocEntry.Type.MULTIPLE_AUTHORS);

        TYPE_MAP.put("MAGAZINE", TocEntry.Type.MULTIPLE_WORKS);

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
    private String mPath;
    /** List of all editions (ISFDB 'publicationRecord') of this book. */
    private List<Editions.Edition> mEditions;
    /** set during book load, used during content table load. */
    @Nullable
    private String mTitle;
    /** with some luck we'll get these as well. */
    @Nullable
    private String mFirstPublication;

    //ENHANCE: pass and store these ISFBD id's?
//    private final ArrayList<Long> ISFDB_BKEY_AUTHOR_ID_LIST = new ArrayList<>();
//    private final ArrayList<Long> ISFDB_BKEY_SERIES_ID_LIST = new ArrayList<>();

    @Nullable
    public List<Editions.Edition> getEditions() {
        return mEditions;
    }

    /**
     * First "ContentBox" contains all basic details
     * <pre>
     * {@code
     * <div class="ContentBox">
     *    <table>
     *      <tr class="scan">
     *
     *        <td>
     *          <a href="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg">
     *            <img src="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg" alt="picture" class="scan"></a>
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
     *          [.. tr missing.. ]
     *    </table>
     *  Cover art supplied by <a href="http://www.isfdb.org/wiki/index.php/Image:THDSFPRKPT1991.jpg" target="_blank">ISFDB</a>
     * </div>
     * }
     * </pre>
     *
     * @param editions List of ISFDB native book id
     */
    @Nullable
    public Bundle fetch(@Size(min = 1) @NonNull final List<Editions.Edition> editions,
                        @NonNull final Bundle /* out */ bookData,
                        final boolean fetchThumbnail)
            throws SocketTimeoutException {

        mEditions = editions;

        Editions.Edition edition = editions.get(0);
        mPath = ISFDBManager.getBaseURL() + String.format(BOOK_URL, edition.isfdbId);

        // check if we already got the book
        if (edition.doc != null) {
            mDoc = edition.doc;
        } else {
            // nop, go get it.
            if (!loadPage(mPath, true)) {
                return null;
            }
        }

        Element contentBox = mDoc.select("div.contentbox").first();
        Elements lis = contentBox.select("li");
        String tmp;

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

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ISFDB_SEARCH) {
                    Logger.debug(this, "fetch", "fieldName=`" + fieldName + '`');
                }

                if ("Publication:".equalsIgnoreCase(fieldName)) {
                    mTitle = li.childNode(1).toString().trim();
                    bookData.putString(DBDefinitions.KEY_TITLE, mTitle);

                    // publication record.
                    tmp = li.childNode(2).childNode(1).toString().trim();
                    try {
                        long record = Long.parseLong(tmp);
                        bookData.putLong(DBDefinitions.KEY_ISFDB_ID, record);
                    } catch (NumberFormatException ignore) {
                    }
                } else if ("Author:".equalsIgnoreCase(fieldName)
                        || "Authors:".equalsIgnoreCase(fieldName)) {
                    Elements as = li.select("a");
                    if (as != null) {
                        for (Element a : as) {
                            mAuthors.add(Author.fromString(a.text()));
                            //ENHANCE: pass and store these ISFBD id's
//                            ISFDB_BKEY_AUTHOR_ID_LIST.add(stripNumber(a.attr("href")));
                        }
                    }
                } else if ("Date:".equalsIgnoreCase(fieldName)) {
                    // dates are in fact displayed as YYYY-MM-DD which is very nice.
                    tmp = li.childNode(2).toString().trim();
                    // except that ISFDB uses 00 for the day/month when unknown ...
                    // so lets arbitrarily change that to 01
                    tmp = tmp.replace("-00", "-01");
                    // and we're paranoid...
                    Date d = DateUtils.parseDate(tmp);
                    if (d != null) {
                        bookData.putString(DBDefinitions.KEY_DATE_PUBLISHED,
                                           DateUtils.utcSqlDate(d));
                    }

                } else if ("ISBN:".equalsIgnoreCase(fieldName)) {
                    // always use the first one, as that will be the one used at publication
                    tmp = li.childNode(1).toString().trim();
                    bookData.putString(DBDefinitions.KEY_ISBN, digits(tmp));

                    tmp = li.childNode(2).childNode(0).toString().trim();
                    bookData.putString(ISFDB_BKEY_ISBN_2, digits(tmp));

                } else if ("Publisher:".equalsIgnoreCase(fieldName)) {
                    //tmp = li.childNode(3).attr("href");
                    //ENHANCE: pass and store these ISFBD id's
                    //bookData.putString(ISFDB_BKEY_PUBLISHER_ID, String.valueOf(stripNumber(tmp)));

                    tmp = li.childNode(3).childNode(0).toString().trim();
                    bookData.putString(DBDefinitions.KEY_PUBLISHER, tmp);

                } else if ("Pub. Series:".equalsIgnoreCase(fieldName)) {
                    Elements as = li.select("a");
                    if (as != null) {
                        for (Element a : as) {
                            mSeries.add(Series.fromString(a.text()));
                            //ENHANCE: pass and store these ISFBD id's
//                            ISFDB_BKEY_SERIES_ID_LIST.add(stripNumber(a.attr("href")));
                        }
                    }

                } else if ("Price:".equalsIgnoreCase(fieldName)) {
                    tmp = li.childNode(2).toString().trim();
                    // split on first digit, but leave it in the second part
                    String[] data = tmp.split("(?=\\d)", 2);
                    if (data.length == 1) {
                        bookData.putString(DBDefinitions.KEY_PRICE_LISTED, tmp);
                        bookData.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY, "");
                        // I don't think the Shilling/Pence from the UK ever had an
                        // international code.
//                        if (tmp.contains("/")) {
//                            // UK Shilling was written as "1/-", for example:
//                            // three shillings and six pence => 3/6
//                            bookData.putString(UniqueId.KEY_PRICE_LISTED_CURRENCY, "???");
//                        }
                    } else {
                        String currencyCode = LocaleUtils.currencyToISO(data[0]);
                        try {
                            Currency currency = Currency.getInstance(currencyCode);
                            int decDigits = currency.getDefaultFractionDigits();
                            // format with 'digits' decimal places
                            Float price = Float.parseFloat(data[1]);
                            String priceStr = String.format("%." + decDigits + 'f', price);

                            bookData.putString(DBDefinitions.KEY_PRICE_LISTED, priceStr);
                            // re-get the code just in case ISFDB/Utils uses a recognised but
                            // non-standard one
                            bookData.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                                               currency.getCurrencyCode());

                        } catch (NumberFormatException e) {
                            bookData.putString(DBDefinitions.KEY_PRICE_LISTED, data[1]);
                            bookData.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY,
                                               currencyCode);
                        }
                    }

                } else if ("Pages:".equalsIgnoreCase(fieldName)) {
                    tmp = li.childNode(2).toString().trim();
                    bookData.putString(DBDefinitions.KEY_PAGES, tmp);

                } else if ("Format:".equalsIgnoreCase(fieldName)) {
                    tmp = li.childNode(3).childNode(0).toString().trim();
                    bookData.putString(DBDefinitions.KEY_FORMAT,
                                       // it would be really hard, maybe impossible to get a proper context here.
                                       Format.map(App.getAppContext(), tmp));

                } else if ("Type:".equalsIgnoreCase(fieldName)) {
                    tmp = li.childNode(2).toString().trim();
                    bookData.putString(ISFDB_BKEY_BOOK_TYPE, tmp);
                    Integer type = TYPE_MAP.get(tmp);
                    if (type != null) {
                        bookData.putLong(DBDefinitions.KEY_TOC_BITMASK, type);
                    }

                } else if ("Notes:".equalsIgnoreCase(fieldName)) {
                    tmp = li.childNode(1).childNode(1).toString().trim();
                    bookData.putString(DBDefinitions.KEY_DESCRIPTION, tmp);


//                } else if ("Cover:".equalsIgnoreCase(fieldName)) {
//                    //TODO: if there are multiple art/artists... will this barf ?
//                    tmp = li.childNode(2).attr("href");
//                    bookData.putString(ISFDB_BKEY_BOOK_COVER_ART_URL, tmp);
//
//                    tmp = li.childNode(2).childNode(0).toString().trim();
//                    bookData.putString(ISFDB_BKEY_BOOK_COVER_ART_TXT, tmp);
//
//                    // Cover artist
//                    Node node_a = li.childNode(4);
//                    StringList.addOrAppend(bookData, ISFDB_BKEY_BOOK_COVER_ARTIST_ID,
//                                           String.valueOf(stripNumber(node_a.attr("href"))));
//                    StringList.addOrAppend(bookData, ISFDB_BKEY_BOOK_COVER_ARTIST,
//                                           node_a.childNode(0).toString().trim());
//
//                } else if ("Editors:".equalsIgnoreCase(fieldName)) {
//                    Elements as = li.select("a");
//                    if (as != null) {
//                        for (Element a : as) {
//                            StringList.addOrAppend(bookData, ISFDB_BKEY_BKEY_EDITORS_ID,
//                                                   String.valueOf(stripNumber(a.attr("href"))));
//                            StringList.addOrAppend(bookData, ISFDB_BKEY_EDITORS, a.text());
//                        }
//                    }
                }
            } catch (IndexOutOfBoundsException e) {
                // does not happen now, but could happen if we come about non-standard entries,
                // or if ISFDB website changes
                Logger.error(this, e, "path: " + mPath + "\n\nLI: " + li.toString());
            }
        }

        // ISFDB does not offer the books language on the main page (although they store
        // it in their database).
        // Default to a localised 'English" as ISFDB is after all (I presume) 95% english
        bookData.putString(DBDefinitions.KEY_LANGUAGE, Locale.ENGLISH.getISO3Language());

        // store accumulated ArrayList's
        bookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mAuthors);
        //ENHANCE: pass and store these ISFBD id's
//        bookData.putParcelableArrayList(ISFDB_BKEY_AUTHOR_ID, ISFDB_BKEY_AUTHOR_ID_LIST);
//        bookData.putParcelableArrayList(ISFDB_BKEY_SERIES_ID, ISFDB_BKEY_SERIES_ID_LIST);

        // the table of content
        ArrayList<TocEntry> toc = getTableOfContentList();
        bookData.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, toc);

        // store accumulated Series, do this *after* we go the TOC
        bookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, mSeries);

        // set Anthology type
        if (!toc.isEmpty()) {
            int type = TocEntry.Type.MULTIPLE_WORKS;
            if (TocEntry.hasMultipleAuthors(toc)) {
                type |= TocEntry.Type.MULTIPLE_AUTHORS;
            }
            bookData.putLong(DBDefinitions.KEY_TOC_BITMASK, type);
        }

        // try to deduce the first publication date
        if (toc.size() == 1) {
            // if the content table has only one entry,
            // then this will have the first publication year for sure
            String d = digits(toc.get(0).getFirstPublication());
            if (d != null && !d.isEmpty()) {
                bookData.putString(DBDefinitions.KEY_DATE_FIRST_PUBLISHED, d);
            }
        } else if (toc.size() > 1) {
            // we gamble and take what we found in the content
            if (mFirstPublication != null) {
                bookData.putString(DBDefinitions.KEY_DATE_FIRST_PUBLISHED,
                                   digits(mFirstPublication));
            } // else take the book pub date ... but that might be wrong....
        }

        // optional fetch of the cover.
        if (fetchThumbnail) {
            fetchCover(bookData, mDoc.select("div.contentbox").first());
        }

        return bookData;
    }

    /**
     * Filter a string of all non-digits. Used to clean isbn strings.
     */
    @Nullable
    private String digits(@Nullable final String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /* First "ContentBox" contains all basic details.

    <div class="ContentBox">
    <table>
    <tr class="scan">
    <td>
        <a href="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg">
        <img src="http://www.isfdb.org/wiki/images/e/e6/THDSFPRKPT1991.jpg"
             alt="picture" class="scan"></a>
    </td>
    */
    private void fetchCover(@NonNull final Bundle /* out */ bookData,
                            @NonNull final Element contentBox) {
        Element img = contentBox.selectFirst("img");
        if (img != null) {
            String url = img.attr("src");
            String fileSpec = ImageUtils.saveImage(url, FILENAME_SUFFIX);
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
     * Second ContentBox contains the TOC
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
    private ArrayList<TocEntry> getTableOfContentList()
            throws SocketTimeoutException {

        final ArrayList<TocEntry> results = new ArrayList<>();

        if (!loadPage(mPath, true)) {
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
             */
            String liAsString = li.toString();
            String title = null;
            Author author = null;
            Elements aas = li.select("a");
            // find the first occurrence of each
            for (Element a : aas) {
                String href = a.attr("href");

                if (title == null && href.contains("title.cgi")) {
                    title = cleanUpName(a.text());
                    //ENHANCE: tackle 'variant' titles later

                } else if (author == null && href.contains("ea.cgi")) {
                    author = Author.fromString(cleanUpName(a.text()));

                } else if (mSeries.isEmpty() && href.contains("pe.cgi")) {
                    String seriesName = a.text();
                    String seriesNum = "";
                    // check for the number; series don't always have a number
                    int start = liAsString.indexOf('[');
                    int end = liAsString.indexOf(']');
                    if (start > 1 && end > start) {
                        String tmp = liAsString.substring(start, end);
                        // yes, hex... despite browser view-source shows &#8226;
                        // (see comment section above)
                        String[] data = tmp.split("&#x2022;");
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
                Logger.warn(this, "getTableOfContentList",
                            "ISFDB search for content found no author for li=" + li);
            }
            // very unlikely
            if (title == null) {
                title = "";
                Logger.warn(this, "getTableOfContentList",
                            "ISFDB search for content found no title for li=" + li);
            }

            // scan for first occurrence of "&#x2022; (1234)"
            Matcher matcher = YEAR_FROM_LI.matcher(liAsString);
            String year = matcher.find() ? matcher.group(1) : "";
            // see if we can use it as a book year as well. e.g. if same title as book title
            if (mFirstPublication == null && title.equalsIgnoreCase(mTitle)) {
                mFirstPublication = year;
            }

            TocEntry tocEntry = new TocEntry(author, title, year);
            results.add(tocEntry);
        }

        return results;
    }
}
