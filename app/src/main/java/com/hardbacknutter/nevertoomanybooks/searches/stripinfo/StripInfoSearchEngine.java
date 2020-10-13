/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

/**
 * <a href="https://stripinfo.be/">https://stripinfo.be/</a>
 * <p>
 * Dutch language (and to an extend French and a minimal amount of other languages) comics website.
 */
@SearchEngine.Configuration(
        id = SearchSites.STRIP_INFO_BE,
        nameResId = R.string.site_stripinfo_be,
        prefKey = "stripinfo",
        url = "https://stripinfo.be",
        lang = "nl",
        country = "be",
        domainMenuId = R.id.MENU_VIEW_BOOK_AT_STRIP_INFO_BE,
        domainViewId = R.id.site_strip_info_be,
        domainKey = DBDefinitions.KEY_EID_STRIP_INFO_BE,
        connectTimeoutMs = 7_000,
        readTimeoutMs = 60_000,
        filenameSuffix = "SI"
)
public class StripInfoSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByExternalId,
                   SearchEngine.ByIsbn,
                   SearchEngine.ByBarcode {

    /** Log tag. */
    private static final String TAG = "StripInfoSearchEngine";

    /** Color string values as used on the site. Complete 2019-10-29. */
    private static final String COLOR_STRINGS = "Kleur|Zwart/wit|Zwart/wit met steunkleur";

    /** Param 1: external book ID; really a 'long'. */
    private static final String BY_EXTERNAL_ID = "/reeks/strip/%1$s";

    /** Param 1: ISBN. */
    private static final String BY_ISBN = "/zoek/zoek?zoekstring=%1$s";

    /** The description contains h4 tags which we remove to make the text shorter. */
    private static final Pattern H4_OPEN_PATTERN = Pattern.compile("<h4>\\s*");
    private static final Pattern H4_CLOSE_PATTERN = Pattern.compile("\\s*</h4>");

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

    /** The site specific 'mature' image. Correct 2019-12-19. */
    private static final int MATURE_FILE_LEN = 21578;

    /** The site specific 'mature' image. Correct 2019-12-19. */
    private static final byte[] MATURE_COVER_MD5 = {
            (byte) 0x22, (byte) 0x78, (byte) 0x58, (byte) 0x89,
            (byte) 0x8b, (byte) 0xba, (byte) 0x3e, (byte) 0xee,
            (byte) 0x4a, (byte) 0x65, (byte) 0x68, (byte) 0xc9,
            (byte) 0x46, (byte) 0x54, (byte) 0x59, (byte) 0x4b};

    /** JSoup selector to get book url tags. */
    private static final String A_HREF_STRIP = "a[href*=/strip/]";

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext Application context
     */
    public StripInfoSearchEngine(@NonNull final Context appContext) {
        super(appContext);
    }

    @NonNull
    @Override
    public String createUrl(@NonNull final String externalId) {
        return getSiteUrl() + String.format(BY_EXTERNAL_ID, externalId);
    }

    @NonNull
    @Override
    public Bundle searchByExternalId(@NonNull final String externalId,
                                     @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        final Bundle bookData = new Bundle();

        final String url = getSiteUrl() + String.format(BY_EXTERNAL_ID, externalId);
        final Document document = loadDocument(url);
        if (document != null && !isCancelled()) {
            parse(document, fetchThumbnail, bookData);
        }
        return bookData;
    }

    /**
     * Also handles {@link SearchEngine.ByBarcode}.
     *
     * <br><br>{@inheritDoc}
     */
    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        final Bundle bookData = new Bundle();

        final String url = getSiteUrl() + String.format(BY_ISBN, validIsbn);
        final Document document = loadDocument(url);
        if (document != null && !isCancelled()) {
            if (isMultiResult(document)) {
                parseMultiResult(document, fetchThumbnail, bookData);
            } else {
                parse(document, fetchThumbnail, bookData);
            }
        }
        return bookData;
    }

    @NonNull
    @Override
    public Bundle searchByBarcode(@NonNull final String barcode,
                                  @NonNull final boolean[] fetchThumbnail)
            throws IOException {
        // the search url is the same
        return searchByIsbn(barcode, fetchThumbnail);
    }

    private boolean isMultiResult(@NonNull final Document document) {
        return document.title().startsWith(MULTI_RESULT_PAGE_TITLE);
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retries.
     *
     * @param document       to parse
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to update
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @VisibleForTesting
    void parseMultiResult(@NonNull final Document document,
                          @NonNull final boolean[] fetchThumbnail,
                          @NonNull final Bundle bookData)
            throws IOException {

        final Elements sections = document.select("section.c6");
        if (sections != null) {
            for (final Element section : sections) {
                // A series:
                // <a href="https://stripinfo.be/reeks/index/481
                //      _Het_narrenschip">Het narrenschip</a>
                // The book:
                // <a href="https://stripinfo.be/reeks/strip/1652
                //      _Het_narrenschip_2_Pluvior_627">Pluvior 627</a>
                final Element urlElement = section.selectFirst(A_HREF_STRIP);
                if (urlElement != null) {
                    final Document redirected = loadDocument(urlElement.attr("href"));
                    if (redirected != null && !isCancelled()) {
                        // prevent looping.
                        if (!isMultiResult(redirected)) {
                            parse(redirected, fetchThumbnail, bookData);
                        }
                    }
                    return;
                }
                // A no-results page will contain:

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
    }

    @Override
    public void parse(@NonNull final Document document,
                      @NonNull final boolean[] fetchThumbnail,
                      @NonNull final Bundle bookData)
            throws IOException {
        super.parse(document, fetchThumbnail, bookData);

        // extracted from the page header.
        final String primarySeriesTitle = processPrimarySeriesTitle(document);
        // extracted from the title section.
        String primarySeriesBookNr = null;

        final Elements rows = document.select("div.row");
        for (final Element row : rows) {

            if (isCancelled()) {
                return;
            }

            // this code is not 100% foolproof yet, so surround with try/catch.
            try {
                // use the title header to determine we are in a book row.
                final Element titleHeader = row.selectFirst("h2.title");
                if (titleHeader != null) {
                    primarySeriesBookNr = titleHeader.textNodes().get(0).text().trim();

                    final Element titleUrlElement = titleHeader.selectFirst(A_HREF_STRIP);
                    bookData.putString(DBDefinitions.KEY_TITLE, cleanText(titleUrlElement.text()));
                    // extract the external (site) id from the url
                    processExternalId(titleUrlElement, bookData);

                    final Elements tds = row.select("td");
                    int i = 0;
                    while (i < tds.size()) {
                        final Element td = tds.get(i);
                        final String label = td.text();

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
                                i += processLanguage(td, bookData);
                                break;

                            case "Collectie":
                                i += processCollectie(td);
                                break;

                            case "Oplage":
                                i += processText(td, DBDefinitions.KEY_PRINT_RUN, bookData);
                                break;

                            case "Barcode":
                                i += processText(td, SiteField.BARCODE, bookData);
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
                    Logger.d(TAG, e, "row=" + row);
                }
            }
        }

        // process the description
        final Element item = document.selectFirst("div.item");
        if (item != null) {
            processDescription(item, bookData);
        }

        if (primarySeriesTitle != null && !primarySeriesTitle.isEmpty()) {
            final Series series = Series.from3(primarySeriesTitle);
            series.setNumber(primarySeriesBookNr);
            // add to the top as this is the primary series.
            mSeries.add(0, series);
        }

        final ArrayList<TocEntry> toc = parseToc(document);
        // We DON'T store a toc with a single entry (i.e. the book title itself).
        if (toc != null && toc.size() > 1) {
            bookData.putParcelableArrayList(Book.BKEY_TOC_LIST, toc);
            bookData.putLong(DBDefinitions.KEY_TOC_BITMASK, Book.TOC_MULTIPLE_WORKS);
        }

        // store accumulated ArrayList's *after* we got the TOC
        if (!mAuthors.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, mAuthors);
        }
        if (!mSeries.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_SERIES_LIST, mSeries);
        }
        if (!mPublishers.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, mPublishers);
        }

        if (isCancelled()) {
            return;
        }

        // Anthology type: make sure TOC_MULTIPLE_AUTHORS is correct.
        if (toc != null && !toc.isEmpty()) {
            @Book.TocBits
            long type = bookData.getLong(DBDefinitions.KEY_TOC_BITMASK);
            if (TocEntry.hasMultipleAuthors(toc)) {
                type |= Book.TOC_MULTIPLE_AUTHORS;
            }
            bookData.putLong(DBDefinitions.KEY_TOC_BITMASK, type);
        }

        if (isCancelled()) {
            return;
        }

        // front cover
        if (fetchThumbnail[0]) {
            final String isbn = bookData.getString(DBDefinitions.KEY_ISBN);
            final ArrayList<String> imageList = parseCovers(document, isbn, 0);
            if (!imageList.isEmpty()) {
                bookData.putStringArrayList(SearchCoordinator.BKEY_TMP_FILE_SPEC_ARRAY[0],
                                            imageList);
            }
        }

        if (isCancelled()) {
            return;
        }

        // back cover
        if (fetchThumbnail.length > 1 && fetchThumbnail[1]) {
            final String isbn = bookData.getString(DBDefinitions.KEY_ISBN);
            final ArrayList<String> imageList = parseCovers(document, isbn, 1);
            if (!imageList.isEmpty()) {
                bookData.putStringArrayList(SearchCoordinator.BKEY_TMP_FILE_SPEC_ARRAY[1],
                                            imageList);
            }
        }
    }

    /**
     * Parses the downloaded {@link Document} for the cover and fetches it when present.
     *
     * @param document to parse
     * @param isbn     (optional) ISBN of the book, will be used for the cover filename
     * @param cIdx     0..n image index
     */
    @WorkerThread
    @VisibleForTesting
    @NonNull
    private ArrayList<String> parseCovers(@NonNull final Document document,
                                          @Nullable final String isbn,
                                          @IntRange(from = 0, to = 1) final int cIdx) {
        final Element coverElement;
        String url = null;

        switch (cIdx) {
            case 0:
                coverElement = document
                        .selectFirst("a.stripThumb > figure.stripThumbInnerWrapper > img");
                if (coverElement != null) {
                    url = coverElement.attr("src");
                }
                break;
            case 1:
                coverElement = document.selectFirst("a.belowImage");
                if (coverElement != null) {
                    url = coverElement.attr("data-ajax-url");
                }
                break;
            default:
                throw new IllegalArgumentException(String.valueOf(cIdx));
        }

        final ArrayList<String> imageList = new ArrayList<>();

        // if the site has no image: https://www.stripinfo.be/image.php?i=0
        // if the cover is an 18+ image: https://www.stripinfo.be/images/mature.png
        // 2020-08-11: parsing modified to bypass the 18+ image block but leaving the tests
        // in place to guard against website changes.
        if (url != null && !url.isEmpty()
            && !url.endsWith("i=0")
            && !url.endsWith("mature.png")) {

            final String fileSpec = saveImage(url, isbn, cIdx, null);
            if (fileSpec != null) {
                // Some back covers will return the "no cover available" image regardless.
                // Sadly, we need to check explicitly after the download.
                // But we need to check on "mature content" as well anyhow.
                final File file = new File(fileSpec);
                final long fileLen = file.length();
                // check the length as a quick check first
                if (fileLen == NO_COVER_FILE_LEN
                    || fileLen == MATURE_FILE_LEN) {
                    // do the thorough check with md5 calculation as a second defence
                    final byte[] digest = md5(file);
                    if (Arrays.equals(digest, NO_COVER_MD5)
                        || Arrays.equals(digest, MATURE_COVER_MD5)) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        return imageList;
                    }
                }

                imageList.add(fileSpec);
            }
        }

        return imageList;
    }

    @NonNull
    private ArrayList<String> parseCovers_old(@NonNull final Document document,
                                              @Nullable final String isbn,
                                              @IntRange(from = 0, to = 1) final int cIdx) {
        final Element coverElement;
        switch (cIdx) {
            case 0:
                coverElement = document.selectFirst("a.stripThumb");
                break;
            case 1:
                coverElement = document.selectFirst("a.belowImage");
                break;
            default:
                throw new IllegalArgumentException(String.valueOf(cIdx));
        }

        final ArrayList<String> imageList = new ArrayList<>();

        if (coverElement != null) {
            final String url = coverElement.attr("data-ajax-url");
            // if the site has no image: https://www.stripinfo.be/image.php?i=0
            // if the cover is an 18+ image: https://www.stripinfo.be/images/mature.png
            if (url != null && !url.isEmpty()
                && !url.endsWith("i=0")
                && !url.endsWith("mature.png")) {

                final String fileSpec = saveImage(url, isbn, cIdx, null);
                if (fileSpec != null) {
                    // Some back covers will return the "no cover available" image regardless.
                    // Sadly, we need to check explicitly after the download.
                    // But we need to check on "mature content" as well anyhow.
                    final File file = new File(fileSpec);
                    final long fileLen = file.length();
                    // check the length as a quick check first
                    if (fileLen == NO_COVER_FILE_LEN
                        || fileLen == MATURE_FILE_LEN) {
                        // do the thorough check with md5 calculation as a second defence
                        final byte[] digest = md5(file);
                        if (Arrays.equals(digest, NO_COVER_MD5)
                            || Arrays.equals(digest, MATURE_COVER_MD5)) {
                            //noinspection ResultOfMethodCallIgnored
                            file.delete();
                            return imageList;
                        }
                    }

                    imageList.add(fileSpec);
                }
            }
        }
        return imageList;
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
     * @param document to parse
     *
     * @return the toc list
     */
    @Nullable
    private ArrayList<TocEntry> parseToc(@NonNull final Document document) {
        final Elements sections = document.select("div.c12");
        if (sections != null) {
            for (final Element section : sections) {
                final Element divs = section.selectFirst("div");
                if (divs != null) {
                    final Elements sectionChildren = divs.children();
                    if (!sectionChildren.isEmpty()) {
                        final Element sectionContent = sectionChildren.get(0);
                        // the section header we're hoping to find.
                        // <h4>Dit is een bundeling. De inhoud komt uit volgende strips:</h4>
                        final Node header = sectionContent.selectFirst("h4");
                        if (header != null && header.toString().contains("bundeling")) {
                            // the div's inside Element 'row' should now contain the TOC.
                            final ArrayList<TocEntry> toc = new ArrayList<>();
                            for (final Element entry : sectionContent.select("div div")) {
                                String number = null;
                                String title = null;

                                final Element a = entry.selectFirst(A_HREF_STRIP);
                                if (a != null) {
                                    final Node nrNode = a.previousSibling();
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
                                    final Author author;
                                    if (!mAuthors.isEmpty()) {
                                        author = mAuthors.get(0);
                                    } else {
                                        author = Author.createUnknownAuthor(mAppContext);
                                    }
                                    final TocEntry tocEntry = new TocEntry(author, title, null);
                                    toc.add(tocEntry);
                                }
                            }
                            return toc;
                        }
                    }
                }
            }
        }
        return null;
    }


    /**
     * Extract the site book id from the url.
     *
     * @param titleUrlElement element containing the book url
     * @param bookData        Bundle to update
     */
    private void processExternalId(@NonNull final Element titleUrlElement,
                                   @NonNull final Bundle bookData) {
        try {
            final String titleUrl = titleUrlElement.attr("href");
            // https://www.stripinfo.be/reeks/strip/336348
            // _Hauteville_House_14_De_37ste_parallel
            final String idString = titleUrl.substring(titleUrl.lastIndexOf('/') + 1)
                                            .split("_")[0];
            final long bookId = Long.parseLong(idString);
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
     * @param document to parse
     *
     * @return title, or {@code null} for none
     */
    @Nullable
    private String processPrimarySeriesTitle(@NonNull final Element document) {
        final Element seriesElement = document.selectFirst("h1.c12");
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
            final Element img = seriesElement.selectFirst("img");
            if (img != null) {
                return img.attr("alt");
            } else {
                final Element a = seriesElement.selectFirst("a");
                if (a != null) {
                    return a.text();
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
     * @param bookData Bundle to update
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processText(@NonNull final Element td,
                            @NonNull final String key,
                            @NonNull final Bundle bookData) {
        final Element dataElement = td.nextElementSibling();
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
     * @param bookData Bundle to update
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processEmptyLabel(@NonNull final Element td,
                                  @NonNull final Bundle bookData) {
        final Element dataElement = td.nextElementSibling();
        if (dataElement.childNodeSize() == 1) {
            final String text = dataElement.text().trim();

            // is it a color ?
            if (COLOR_STRINGS.contains(text)) {
                bookData.putString(DBDefinitions.KEY_COLOR, text);
            }
            return 1;
        }
        return 0;
    }

    private int processLanguage(@NonNull final Element td,
                                @NonNull final Bundle bookData) {
        final int found = processText(td, DBDefinitions.KEY_LANGUAGE, bookData);
        String lang = bookData.getString(DBDefinitions.KEY_LANGUAGE);
        if (lang != null && !lang.isEmpty()) {
            lang = Languages
                    .getInstance().getISO3FromDisplayName(mAppContext, getLocale(), lang);
            bookData.putString(DBDefinitions.KEY_LANGUAGE, lang);
        }
        return found;
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
        final Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            final Elements as = dataElement.select("a");
            for (int i = 0; i < as.size(); i++) {
                final String name = as.get(i).text();
                final Author currentAuthor = Author.from(name);
                boolean add = true;
                // check if already present
                for (final Author author : mAuthors) {
                    if (author.equals(currentAuthor)) {
                        // merge types.
                        author.addType(currentAuthorType);
                        add = false;
                        // keep looping
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
    private int processCollectie(@NonNull final Element td) {
        final Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            final Elements as = dataElement.select("a");
            for (int i = 0; i < as.size(); i++) {
                final String text = cleanText(as.get(i).text());
                final Series currentSeries = Series.from3(text);
                // check if already present
                if (mSeries.stream().anyMatch(series -> series.equals(currentSeries))) {
                    return 1;
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
        final Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            final Elements aas = dataElement.select("a");
            for (int i = 0; i < aas.size(); i++) {
                final String name = cleanText(aas.get(i).text());
                final Publisher currentPublisher = Publisher.from(name);
                // check if already present
                if (mPublishers.stream().anyMatch(pub -> pub.equals(currentPublisher))) {
                    return 1;
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
     *      <li>Covertekst</li>
     *      <li>Opmerking uitgave</li>
     *      <li>Opmerking inhoud</li>
     * </ul>
     *
     * <strong>Note:</strong> the description sometimes contains a TOC (solely,
     * or in addition to the page TOC) but it's not in a standard format so we cannot
     * capture it.
     *
     * @param item     description element, containing 1+ sections
     * @param bookData Bundle to update
     */
    private void processDescription(@NonNull final Element item,
                                    @NonNull final Bundle bookData) {
        final Elements sections = item.select("section.c4");
        if (sections != null && !sections.isEmpty()) {
            final StringBuilder content = new StringBuilder();
            for (int i = 0; i < sections.size(); i++) {
                final Element sectionElement = sections.get(i);
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

    /**
     * Calculate the MD5 sum.
     *
     * @param file to check
     *
     * @return md5, or {@code null} on failure
     */
    @Nullable
    private byte[] md5(@NonNull final File file) {
        byte[] digest = null;
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = new FileInputStream(file);
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                // read and discard. The images are small enough to always read in one go.
                //noinspection ResultOfMethodCallIgnored
                dis.read(new byte[(int) file.length() + 1]);
            }
            digest = md.digest();

        } catch (@NonNull final NoSuchAlgorithmException | IOException ignore) {
            // ignore
        }
        return digest;
    }

    /**
     * StripInfo specific field names we add to the bundle based on parsed XML data.
     */
    public static final class SiteField {

        /** The barcode (e.g. the EAN code) is not always an ISBN. */
        static final String BARCODE = "__barcode";

        private SiteField() {
        }
    }
}
