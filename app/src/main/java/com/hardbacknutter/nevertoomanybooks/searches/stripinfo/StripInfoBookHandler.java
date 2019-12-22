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
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.JsoupBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;

public class StripInfoBookHandler
        extends JsoupBase {

    public static final String FILENAME_SUFFIX = "_SI";

    /** read-timeout. Default is 10_000. */
    private static final int READ_TIMEOUT = 60_000;
    /** Log tag. */
    private static final String TAG = "StripInfoBookHandler";
    /** Color string values as used on the site. Complete 2019-10-29. */
    private static final String COLOR_STRINGS = "Kleur|Zwart/wit|Zwart/wit met steunkleur";

    /** Param 1: search criteria. */
    private static final String BOOK_SEARCH_URL = "/zoek/zoek?zoekstring=%1$s";

    /** Param 1: the native id; really a 'long'. */
    private static final String BOOK_BY_NATIVE_ID = "/reeks/strip/%1$s";


    /** The description contains h4 and div tags which we remove to make the text shorter. */
    private static final Pattern H4_OPEN_PATTERN = Pattern.compile("<h4>\\s*");
    private static final Pattern H4_CLOSE_PATTERN = Pattern.compile("\\s*</h4>");
    private static final Pattern DIV_PATTERN = Pattern.compile("(\n*\\s*<div>\\s*|\\s*</div>)");
    private static final Pattern AMPERSAND_PATTERN = Pattern.compile("&amp;", Pattern.LITERAL);
    /**
     * When a multi-result page is returned, its title will start with this text.
     * (dutch for: Searching for...)
     */
    private static final String MULTI_RESULT_PAGE_TITLE = "Zoeken naar";

    /** The site specific 'no cover' image. Correct 2019-12-19. */
    private static final int NO_COVER_FILE_LEN = 15779;
    /** The site specific 'no cover' image. Correct 2019-12-19. */
    private static final byte[] NO_COVER_MD5 = {
            (byte) 0xa1, (byte) 0x30, (byte) 0x43, (byte) 0x10,
            (byte) 0x09, (byte) 0x16, (byte) 0xd8, (byte) 0x93,
            (byte) 0xe4, (byte) 0xb5, (byte) 0x32, (byte) 0xcf,
            (byte) 0x3d, (byte) 0x7d, (byte) 0xa9, (byte) 0x37};

    /** JSoup selector to get book url tags. */
    private static final String A_HREF_STRIP = "a[href*=/strip/]";

    /** accumulate all Authors for this book. */
    private final ArrayList<Author> mAuthors = new ArrayList<>();
    /** accumulate all Series for this book. */
    private final ArrayList<Series> mSeries = new ArrayList<>();
    /** accumulate all Publishers for this book. */
    private final ArrayList<Publisher> mPublishers = new ArrayList<>();
    /** cached ApplicationContext. */
    @NonNull
    private final Context mContext;
    /** ISBN of the book we want. Only kept for logging reference. */
    private String mIsbn;

    /**
     * Constructor.
     *
     * @param localizedAppContext Localised application context
     * @param searchEngine        the SearchEngine (to get configuration params)
     */
    StripInfoBookHandler(@NonNull final Context localizedAppContext,
                         @NonNull final SearchEngine searchEngine) {
        super();
        initSite(searchEngine);
        mContext = localizedAppContext;
    }

    /**
     * Constructor for mocking.
     *
     * @param localizedAppContext Localised application context
     * @param searchEngine        the SearchEngine (to get configuration params)
     * @param doc                 the pre-loaded Jsoup document.
     */
    @VisibleForTesting
    StripInfoBookHandler(@NonNull final Context localizedAppContext,
                         @NonNull final SearchEngine searchEngine,
                         @NonNull final Document doc) {
        super(doc);
        initSite(searchEngine);
        mContext = localizedAppContext;
    }

    /**
     * Set some site specific parameters.
     *
     * @param searchEngine the SearchEngine
     */
    private void initSite(@NonNull final SearchEngine searchEngine) {
        setConnectTimeout(searchEngine.getConnectTimeoutMs());
        setReadTimeout(READ_TIMEOUT);
    }

    /**
     * Fetch a book.
     *
     * @param isbn           to search
     * @param bookData       to populate
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @NonNull
    public Bundle fetch(@NonNull final String isbn,
                        @NonNull final Bundle bookData,
                        final boolean fetchThumbnail)
            throws SocketTimeoutException {
        // keep for reference
        mIsbn = isbn;

        String path = StripInfoManager.BASE_URL + String.format(BOOK_SEARCH_URL, isbn);
        if (loadPage(mContext, path) == null) {
            return bookData;
        }

        if (mDoc.title().startsWith(MULTI_RESULT_PAGE_TITLE)) {
            // handle multi-results page.
            return parseMultiResult(bookData, fetchThumbnail);
        }

        return parseDoc(bookData, fetchThumbnail);
    }

    /**
     * Get the book using the site native book id.
     *
     * @param nativeId       to search
     * @param bookData       to populate
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @NonNull
    Bundle fetchByNativeId(@NonNull final String nativeId,
                           @NonNull final Bundle bookData,
                           final boolean fetchThumbnail)
            throws SocketTimeoutException {

        String path = StripInfoManager.BASE_URL + String.format(BOOK_BY_NATIVE_ID, nativeId);
        if (loadPage(mContext, path) == null) {
            return bookData;
        }

        return parseDoc(bookData, fetchThumbnail);
    }

    /**
     * Fetch a book.
     *
     * @param path           to load
     * @param bookData       to populate
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    Bundle fetchByPath(@NonNull final String path,
                       @NonNull final Bundle bookData,
                       final boolean fetchThumbnail)
            throws SocketTimeoutException {

        if (loadPage(mContext, path) == null) {
            return bookData;
        }

        if (mDoc.title().startsWith(MULTI_RESULT_PAGE_TITLE)) {
            // prevent looping.
            return bookData;
        }

        return parseDoc(bookData, fetchThumbnail);
    }

    /**
     * Parses the downloaded {@link #mDoc}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param bookData       to populate
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data, can be empty, but never {@code null}
     */
    Bundle parseDoc(@NonNull final Bundle bookData,
                    final boolean fetchThumbnail) {

        // extracted from the page header.
        String primarySeriesTitle = extractPrimarySeriesTitle();
        // extracted from the title section.
        String primarySeriesBookNr = null;

        Elements rows = mDoc.select("div.row");
        for (Element row : rows) {
            // this code is not 100% foolproof yet, so surround with try/catch.
            try {
                // use the title header to determine we are in a book row.
                Element titleHeader = row.selectFirst("h2.title");
                if (titleHeader != null) {
                    primarySeriesBookNr = titleHeader.textNodes().get(0).text().trim();

                    Element titleUrlElement = titleHeader.selectFirst(A_HREF_STRIP);
                    bookData.putString(DBDefinitions.KEY_TITLE, cleanText(titleUrlElement.text()));
                    // extract the site native id from the url
                    processSiteNativeId(titleUrlElement, bookData);

                    Elements tds = row.select("td");
                    int i = 0;
                    while (i < tds.size()) {
                        Element td = tds.get(i);
                        String label = td.text();

                        switch (label) {
                            case "Scenario":
                            case "Naar":
                                i += processAuthor(td, Author.TYPE_WRITER);
                                break;

                            case "Tekeningen":
                                i += processAuthor(td, Author.TYPE_ARTIST);
                                break;

                            case "Kleuren":
                                i += processAuthor(td, Author.TYPE_COLORIST);
                                break;
                            case "Inkting":
                                i += processAuthor(td, Author.TYPE_INKING);
                                break;

                            case "Cover":
                                i += processAuthor(td, Author.TYPE_COVER_ARTIST);
                                break;

                            case "Inkting cover":
                                i += processAuthor(td, Author.TYPE_COVER_INKING);
                                break;

                            case "Vertaling":
                                i += processAuthor(td, Author.TYPE_TRANSLATOR);
                                break;

                            case "Uitgever(s)":
                                i += processPublisher(td);
                                break;

                            case "Jaar":
                                i += processText(td, DBDefinitions.KEY_DATE_PUBLISHED, bookData);
                                break;

                            case "Pagina's":
                                i += processText(td, DBDefinitions.KEY_PAGES, bookData);
                                break;

                            case "ISBN":
                                i += processText(td, DBDefinitions.KEY_ISBN, bookData);
                                break;

                            case "Kaft":
                                i += processText(td, DBDefinitions.KEY_FORMAT, bookData);
                                break;

                            case "Taal":
                                i += processText(td, DBDefinitions.KEY_LANGUAGE, bookData);
                                String lang = bookData.getString(DBDefinitions.KEY_LANGUAGE);
                                if (lang != null && !lang.isEmpty()) {
                                    // the language is a 'DisplayName' so convert to iso first.
                                    lang = LanguageUtils
                                            .getISO3FromDisplayName(mContext, lang);
                                    bookData.putString(DBDefinitions.KEY_LANGUAGE, lang);
                                }
                                break;

                            case "Collectie":
                                i += processSeries(td);
                                break;

                            case "Oplage":
                                i += processText(td, DBDefinitions.KEY_PRINT_RUN, bookData);
                                break;

                            case "Barcode":
                                i += processText(td, StripInfoField.BARCODE, bookData);
                                break;

                            case "":
                                i += processEmptyLabel(td, bookData);
                                break;

                            case "Cycli":
                                // not currently used. Example: Cyclus 2 nr. 1
                                // This is sub-series 2, book 1, inside a series.
                                // (also known as 'story-arc')
                                break;

                            case "Redactie":
                            case "Vormgeving":
                                // type: list of Authors
                                // not currently used. Defined by multi-author "concept" series.
                                // Example: https://www.stripinfo.be/reeks/strip/
                                // 62234_XIII_Mystery_1_De_Mangoest
                                break;

                            default:
                                if (BuildConfig.DEBUG /* always */) {
                                    Log.d(TAG, "parseDoc|unknown label=" + label);
                                }
                        }
                        i++;
                    }
                    // we found a book, quit the for(Element row : rows)
                    break;
                }

            } catch (@NonNull final Exception e) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "mIsbn=" + mIsbn, e);
                }
            }
        }

        // process the description
        Element item = mDoc.selectFirst("div.item");
        if (item != null) {
            processDescription(item, bookData);
        }

        if (primarySeriesTitle != null && !primarySeriesTitle.isEmpty()) {
            Series series = Series.fromString3(primarySeriesTitle);
            series.setNumber(primarySeriesBookNr);
            // add to the top as this is the primary series.
            mSeries.add(0, series);
        }

        ArrayList<TocEntry> toc = processAnthology();
        if (toc != null && !toc.isEmpty()) {
            bookData.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, toc);
            bookData.putLong(DBDefinitions.KEY_TOC_BITMASK, Book.TOC_MULTIPLE_WORKS);
        }

        if (!mAuthors.isEmpty()) {
            bookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mAuthors);
        }
        if (!mSeries.isEmpty()) {
            bookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, mSeries);
        }
        if (!mPublishers.isEmpty()) {
            bookData.putParcelableArrayList(UniqueId.BKEY_PUBLISHER_ARRAY, mPublishers);
        }

        if (fetchThumbnail) {
            // front cover
            fetchCoverFromElement("a.stripThumb", 0, bookData);
            // back cover
            fetchCoverFromElement("a.belowImage", 1, bookData);
        }

        return bookData;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retries.
     *
     * @param bookData       to populate
     * @param fetchThumbnail whether to get thumbnails as well
     *
     * @return Bundle with book data, can be empty, but never {@code null}
     *
     * @throws SocketTimeoutException if the connection times out
     */
    Bundle parseMultiResult(@NonNull final Bundle bookData,
                            final boolean fetchThumbnail)
            throws SocketTimeoutException {

        Elements sections = mDoc.select("section.c6");
        if (sections != null) {
            for (Element section : sections) {
                // A series:
                // <a href="https://stripinfo.be/reeks/index/481
                //      _Het_narrenschip">Het narrenschip</a>
                // The book:
                // <a href="https://stripinfo.be/reeks/strip/1652
                //      _Het_narrenschip_2_Pluvior_627">Pluvior 627</a>
                Element url = section.selectFirst(A_HREF_STRIP);
                if (url != null) {
                    String href = url.attr("href");
                    mDoc = null;
                    return fetchByPath(href, bookData, fetchThumbnail);
                }

                // <section class="c6 fullInMediumScreens bottomMargin">
                // <h4 class="title"></h4>
                // <table>
                //  <tbody>
                //   <tr>
                //    <td>Er werden geen resultaten gevonden voor uw zoekopdracht</td>
                //   </tr>
                //  </tbody>
                // </table>
                //</section>
            }
        }

        return bookData;
    }

    private void fetchCoverFromElement(@NonNull final String elementQuery,
                                       final int cIdx,
                                       @NonNull final Bundle bookData) {
        Element coverElement = mDoc.selectFirst(elementQuery);
        if (coverElement != null) {
            String coverUrl = coverElement.attr("data-ajax-url");
            // if the site has no image: https://www.stripinfo.be/image.php?i=0
            if (coverUrl != null && !coverUrl.isEmpty() && !coverUrl.endsWith("i=0")) {
                fetchCover(coverUrl, bookData, cIdx);
            }
        }
    }

    /**
     * Fetch the cover from the url. Uses the ISBN (if any) from the bookData bundle.
     * <p>
     * <img src="https://www.stripinfo.be/image.php?i=437246&amp;s=348664" srcset="
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=440 311w,
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=380 269w,
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=310 219w,
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=270 191w,
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=250 177w,
     * https://www.stripinfo.be/image.php?i=437246&amp;s=348664&amp;m=200 142w
     * " alt="Uitvergroten" class="">
     * <p>
     * https://www.stripinfo.be/image.php?i=437246&s=348664
     *
     * @param coverUrl fully qualified url
     * @param bookData destination bundle
     * @param cIdx     0..n image index
     */
    private void fetchCover(@NonNull final String coverUrl,
                            @NonNull final Bundle /* in/out */ bookData,
                            final int cIdx) {

        // do not use the isbn we searched for, use the one we found even if empty!
        String isbn = bookData.getString(DBDefinitions.KEY_ISBN, "");
        // download
        String fileSpec = ImageUtils.saveImage(mContext, coverUrl,
                                               isbn, FILENAME_SUFFIX + cIdx, null);

        if (fileSpec != null) {
            // Some back covers will return the "no cover available" image regardless.
            // Sadly, we need to check explicitly after the download.
            File file = new File(fileSpec);
            long fileLen = file.length();
            if (fileLen == NO_COVER_FILE_LEN) {
                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    try (InputStream is = new FileInputStream(file)) {
                        try (DigestInputStream dis = new DigestInputStream(is, md)) {
                            // read and discard. 15k can be read in one go obviously.
                            //noinspection ResultOfMethodCallIgnored
                            dis.read(new byte[NO_COVER_FILE_LEN + 1]);
                        }
                    }
                    byte[] digest = md.digest();
                    if (Arrays.equals(digest, NO_COVER_MD5)) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        return;
                    }
                } catch (NoSuchAlgorithmException | IOException ignore) {
                    // ignore
                }
            }

            if (cIdx == 0) {
                String key = UniqueId.BKEY_FILE_SPEC_ARRAY;
                ArrayList<String> imageList = bookData.getStringArrayList(key);
                if (imageList == null) {
                    imageList = new ArrayList<>();
                }
                imageList.add(fileSpec);
                bookData.putStringArrayList(key, imageList);

            } else {
                bookData.putString(UniqueId.BKEY_FILE_SPEC[1], fileSpec);
            }
        }
    }

    /**
     * Extract the site native id from the url.
     *
     * @param titleUrlElement element containing the book url
     * @param bookData        bundle to add to
     */
    private void processSiteNativeId(@NonNull final Element titleUrlElement,
                                     @NonNull final Bundle bookData) {

        try {
            String titleUrl = titleUrlElement.attr("href");
            // https://www.stripinfo.be/reeks/strip/336348
            // _Hauteville_House_14_De_37ste_parallel
            String idString = titleUrl.substring(titleUrl.lastIndexOf('/') + 1)
                                      .split("_")[0];
            long bookId = Long.parseLong(idString);
            if (bookId > 0) {
                bookData.putLong(DBDefinitions.KEY_EID_STRIP_INFO_BE, bookId);
            }
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }
    }

    /**
     * Extract the series title from the header.
     *
     * @return title, or {@code null} for none
     */
    @Nullable
    private String extractPrimarySeriesTitle() {
        Element seriesElement = mDoc.selectFirst("h1.c12");
        // Two possibilities:
        // <h1 class="c12">
        // <a href="https://www.stripinfo.be/reeks/index/831_Capricornus">
        // <img src="https://www.stripinfo.be/images/images/380000/381645.gif"
        //      alt="Capricornus">
        // </a>
        // </h1>
        // or:
        // <h1 class="c12">
        // <a href="https://www.stripinfo.be/reeks/index/632_Coutoo">
        //    Coutoo
        // </a>
        // </h1>
        if (seriesElement != null) {
            Element img = seriesElement.selectFirst("img");
            if (img != null) {
                return img.attr("alt");
            } else {
                Element a = seriesElement.selectFirst("a");
                if (a != null) {
                    return a.text();
                }
            }
        }

        return null;
    }

    /**
     * Extract the (optional) table of content from the header.
     * <p>
     * <strong>Note:</strong> should only be called <strong>AFTER</strong> we have processed
     * the authors as we use the first Author of the book for all TOCEntries.
     * <p>
     * This is likely not correct, but the alternative is to store each entry in a TOC
     * as an individual book, and declare a Book TOC as a list of books.
     * i.o.w. the database structure would need to become
     * table: titles (book and toc-entry titles) each entry referencing 1..n authors.
     * table: books, with a primary title, and a list of secondary titles (i.e the toc).
     * (All of which referencing the 'titles' table)
     * <p>
     * This is not practical in the scope of this application.
     *
     * @return toc list
     */
    @Nullable
    private ArrayList<TocEntry> processAnthology() {
        Elements sections = mDoc.select("div.c12");
        if (sections != null) {
            for (Element section : sections) {
                Element divs = section.selectFirst("div");
                if (divs != null) {
                    Elements sectionChildren = divs.children();
                    if (!sectionChildren.isEmpty()) {
                        Element sectionContent = sectionChildren.get(0);
                        // the section header we're hoping to find.
                        // <h4>Dit is een bundeling. De inhoud komt uit volgende strips:</h4>
                        Node header = sectionContent.selectFirst("h4");
                        if (header != null && header.toString().contains("bundeling")) {
                            // the div's inside Element 'row' should now contain the TOC.
                            ArrayList<TocEntry> tocEntries = new ArrayList<>();
                            for (Element entry : sectionContent.select("div div")) {
                                String number = null;
                                String title = null;

                                Element a = entry.selectFirst(A_HREF_STRIP);
                                if (a != null) {
                                    Node nrNode = a.previousSibling();
                                    if (nrNode != null) {
                                        number = nrNode.toString().trim();
                                    }

                                    // the number is not used in the TOC as we don't support
                                    // linking a TOC entry to another book.
                                    // Instead prepend it to the title as a reference.
                                    if (number != null) {
                                        title = number + ' ' + a.text();
                                    } else {
                                        title = a.text();
                                    }
                                }

                                if (title != null && !title.isEmpty()) {
                                    TocEntry tocEntry = new TocEntry(mAuthors.get(0), title, "");
                                    tocEntries.add(tocEntry);
                                }
                            }
                            return tocEntries;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Process a td which is pure text.
     *
     * @param td       label td
     * @param key      for this field
     * @param bookData bundle to add to
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processText(@NonNull final Element td,
                            @NonNull final String key,
                            @NonNull final Bundle bookData) {
        Element dataElement = td.nextElementSibling();
        if (dataElement.childNodeSize() == 1) {
            bookData.putString(key, cleanText(dataElement.text()));
            return 1;
        }
        return 0;
    }

    /**
     * At least one element does not have an actual label.
     * We inspect the value to try and guess the type.
     * <p>
     * Currently known (2019-10-11):
     * - the color scheme of the comic.
     *
     * @param td       label td
     * @param bookData bundle to add to
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processEmptyLabel(@NonNull final Element td,
                                  @NonNull final Bundle bookData) {
        Element dataElement = td.nextElementSibling();
        if (dataElement.childNodeSize() == 1) {
            String text = dataElement.text().trim();

            // is it a color ?
            if (COLOR_STRINGS.contains(text)) {
                bookData.putString(DBDefinitions.KEY_COLOR, text);
            }
            return 1;
        }
        return 0;
    }

    /**
     * Found an Author.
     *
     * @param td                label td
     * @param currentAuthorType of this entry
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processAuthor(@NonNull final Element td,
                              @Author.Type final int currentAuthorType) {
        Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            Elements as = dataElement.select("a");
            for (int i = 0; i < as.size(); i++) {
                String name = as.get(i).text();
                Author currentAuthor = Author.fromString(name);
                boolean add = true;
                // check if already present
                for (Author author : mAuthors) {
                    if (author.equals(currentAuthor)) {
                        // merge types.
                        author.addType(currentAuthorType);
                        add = false;
                    }
                }

                if (add) {
                    currentAuthor.setType(currentAuthorType);
                    mAuthors.add(currentAuthor);
                }
            }
            return 1;
        }
        return 0;
    }

    /**
     * Found a Series/Collection.
     *
     * @param td label td
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processSeries(@NonNull final Element td) {
        Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            Elements as = dataElement.select("a");
            for (int i = 0; i < as.size(); i++) {
                String text = cleanText(as.get(i).text());
                Series currentSeries = Series.fromString3(text);
                // check if already present
                for (Series series : mSeries) {
                    if (series.equals(currentSeries)) {
                        return 1;
                    }
                }
                // just add
                mSeries.add(currentSeries);
            }
            return 1;
        }
        return 0;
    }

    /**
     * Found a Publisher.
     *
     * @param td label td
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processPublisher(@NonNull final Element td) {
        Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            Elements aas = dataElement.select("a");
            for (int i = 0; i < aas.size(); i++) {
                String name = cleanText(aas.get(i).text());
                Publisher currentPublisher = Publisher.fromString(name);
                // check if already present
                for (Publisher publisher : mPublishers) {
                    if (publisher.equals(currentPublisher)) {
                        return 1;
                    }
                }
                // just add
                mPublishers.add(currentPublisher);
            }
            return 1;
        }
        return 0;
    }

    /**
     * Found the description element. Consists of a number of sections which we combine.
     * <ul>
     * <li>Covertekst</li>
     * <li>Opmerking uitgave</li>
     * <li>Opmerking inhoud</li>
     * </ul>
     *
     * <strong>Note:</strong> the description sometimes contains a TOC (solely,
     * or in addition to the page TOC) but it's not in a standard format so we cannot
     * capture it.
     *
     * @param item     description element, containing 1+ sections
     * @param bookData bundle to add to
     */
    private void processDescription(@NonNull final Element item,
                                    @NonNull final Bundle bookData) {
        Elements sections = item.select("section.c4");
        if (sections != null && !sections.isEmpty()) {
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < sections.size(); i++) {
                Element sectionElement = sections.get(i);
                // a section usually has 'h4' tags, replace with 'b' and add a line feed 'br'
                String text = H4_OPEN_PATTERN
                        .matcher(sectionElement.html())
                        .replaceAll(Matcher.quoteReplacement("<b>"));
                text = H4_CLOSE_PATTERN
                        .matcher(text)
                        .replaceAll(Matcher.quoteReplacement("</b>\n<br>"));

                content.append(cleanText(text));
                if (i < sections.size() - 1) {
                    // separate multiple sections
                    content.append("\n<br>\n<br>");
                }
            }
            if (content.length() > 0) {
                bookData.putString(DBDefinitions.KEY_DESCRIPTION, content.toString());
            }
        }
    }


    private String cleanText(@NonNull final String textToClean) {
        String text = textToClean.trim();
        // add more rules when needed.
        if (text.contains("&")) {
            text = AMPERSAND_PATTERN.matcher(text).replaceAll(Matcher.quoteReplacement("&"));
        }
        if (text.contains("<div>")) {
            // the div elements only create empty lines, we remove them to save screen space
            text = DIV_PATTERN.matcher(text).replaceAll("");
        }
        return text;
    }

    /**
     * StripInfo specific field names we add to the bundle based on parsed XML data.
     */
    public static final class StripInfoField {

        /** The barcode (e.g. the EAN code) is not always an ISBN. */
        public static final String BARCODE = "__barcode";

        private StripInfoField() {
        }
    }

}
