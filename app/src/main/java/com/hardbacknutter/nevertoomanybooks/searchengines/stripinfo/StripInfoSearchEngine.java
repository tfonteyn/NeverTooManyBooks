/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeAuthorResolver;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.BookshelfMapper;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.CollectionFormParser;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoAuth;
import com.hardbacknutter.nevertoomanybooks.utils.JSoupHelper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 * <a href="https://stripinfo.be/">https://stripinfo.be/</a>
 * <p>
 * Dutch language (and to an extend French and a minimal amount of other languages) comics website.
 */
public class StripInfoSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByExternalId,
                   SearchEngine.ViewBookByExternalId,
                   SearchEngine.ByBarcode {

    public static final String COLLECTION_FORM_URL = "/ajax_collectie.php";
    static final String PK_RESOLVE_AUTHORS_ON_BEDETHEQUE = "stripinfo.resolve.authors.bedetheque";
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
    /** Delegate common Element handling. */
    private final JSoupHelper jSoupHelper = new JSoupHelper();
    @Nullable
    private StripInfoAuth loginHelper;
    @Nullable
    private CollectionFormParser collectionFormParser;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public StripInfoSearchEngine(@NonNull final Context appContext,
                                 @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    @Nullable
    private AuthorResolver getAuthorResolver(@NonNull final Context context) {
        if (ServiceLocator.getInstance().getGlobalFieldVisibility()
                          .isShowField(DBKey.AUTHOR_REAL_AUTHOR)
            && PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PK_RESOLVE_AUTHORS_ON_BEDETHEQUE, false)) {
            return new BedethequeAuthorResolver(context, this);
        } else {
            return null;
        }
    }

    @NonNull
    @Override
    public String createBrowserUrl(@NonNull final Context context,
                                   @NonNull final String externalId) {
        return getHostUrl(context) + String.format(BY_EXTERNAL_ID, externalId);
    }

    public void setLoginHelper(@NonNull final StripInfoAuth loginHelper) {
        if (BuildConfig.DEBUG /* always */) {
            loginHelper.getUserId().orElseThrow();
        }
        this.loginHelper = loginHelper;
    }

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (collectionFormParser != null) {
                collectionFormParser.cancel();
            }
            if (loginHelper != null) {
                loginHelper.cancel();
            }
        }
    }

    @NonNull
    @Override
    public Document loadDocument(@NonNull final Context context,
                                 @NonNull final String url,
                                 @Nullable final Map<String, String> requestProperties)
            throws SearchException, CredentialsException {

        if (StripInfoAuth.isLoginToSearch(context)) {
            if (loginHelper == null) {
                final CookieManager cookieManager = ServiceLocator.getInstance().getCookieManager();
                loginHelper = new StripInfoAuth(context, cookieManager);
                try {
                    loginHelper.login();
                } catch (@NonNull final IOException | StorageException e) {
                    loginHelper = null;
                    throw new SearchException(getEngineId(), e);
                }
            }

            // Recreate every time we load a doc; the user could have changed the preferences.
            collectionFormParser = new CollectionFormParser(context, new BookshelfMapper());
        }

        return super.loadDocument(context, url, requestProperties);
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(BY_EXTERNAL_ID, externalId);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book, getAuthorResolver(context));
        }
        return book;
    }

    /**
     * Also handles {@link ByBarcode}.
     *
     * <br><br>{@inheritDoc}
     */
    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(BY_ISBN, validIsbn);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            processDocument(context, validIsbn, document, fetchCovers, book);
        }
        return book;
    }

    @VisibleForTesting
    public void processDocument(@NonNull final Context context,
                                @NonNull final String validIsbn,
                                @NonNull final Document document,
                                @NonNull final boolean[] fetchCovers,
                                @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {
        if (isMultiResult(document)) {
            parseMultiResult(context, document, fetchCovers, book);
        } else {
            parse(context, document, fetchCovers, book, getAuthorResolver(context));
        }

        // Finally, try and replace potential invalid ISBN numbers
        // with the barcode as  found on the site
        processBarcode(validIsbn, book);
    }

    @NonNull
    @Override
    public Book searchByBarcode(@NonNull final Context context,
                                @NonNull final String barcode,
                                @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        // the search url is the same but we need to specifically support barcodes
        // to allow non-isbn codes.
        return searchByIsbn(context, barcode, fetchCovers);
    }

    private boolean isMultiResult(@NonNull final Document document) {
        return document.title().startsWith(MULTI_RESULT_PAGE_TITLE);
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        for (final Element section : document.select("section.c6")) {
            // A series:
            // <a href="https://stripinfo.be/reeks/index/481
            //      _Het_narrenschip">Het narrenschip</a>
            // The book:
            // <a href="https://stripinfo.be/reeks/strip/1652
            //      _Het_narrenschip_2_Pluvior_627">Pluvior 627</a>
            final Element urlElement = section.selectFirst(A_HREF_STRIP);
            if (urlElement != null) {
                final Document redirected = loadDocument(context, urlElement.attr("href"), null);
                if (!isCancelled()) {
                    // prevent looping.
                    if (!isMultiResult(redirected)) {
                        parse(context, redirected, fetchCovers, book, getAuthorResolver(context));
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

    /**
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param context        Current context
     * @param document       to parse
     * @param fetchCovers    Set to {@code true} if we want to get covers
     *                       The array is guaranteed to have at least one element.
     * @param book           Bundle to update
     * @param authorResolver (optional) {@link AuthorResolver} to use
     *
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     *                              This should only occur if the engine calls/relies on
     *                              secondary sites.
     */
    @VisibleForTesting
    @WorkerThread
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book,
                      @Nullable final AuthorResolver authorResolver)
            throws StorageException, SearchException, CredentialsException {

        // extracted from the page header.
        final String primarySeriesTitle = processPrimarySeriesTitle(document);
        // extracted from the title section.
        String primarySeriesBookNr = null;

        long externalId = 0;
        final Elements rows = document.select("div.row");
        for (final Element row : rows) {
            if (isCancelled()) {
                return;
            }

            try {
                // use the title header to determine we are in a book row.
                final Element titleHeader = row.selectFirst("h2.title");
                if (titleHeader != null) {
                    primarySeriesBookNr = titleHeader.textNodes().get(0).text().trim();

                    final Element titleUrlElement = titleHeader.selectFirst(A_HREF_STRIP);
                    if (titleUrlElement != null) {
                        book.putString(DBKey.TITLE, SearchEngineUtils
                                .cleanText(titleUrlElement.text()));
                        // extract the external (site) id from the url
                        externalId = processExternalId(titleUrlElement, book);

                        final Elements tds = row.select("td");
                        int i = 0;
                        while (i < tds.size()) {
                            final Element td = tds.get(i);
                            final String label = td.text();

                            switch (label) {
                                case "Scenario":
                                case "Naar":
                                    i += processAuthor(td, Author.TYPE_WRITER, book);
                                    break;

                                case "Tekeningen":
                                    i += processAuthor(td, Author.TYPE_ARTIST, book);
                                    break;

                                case "Kleuren":
                                    i += processAuthor(td, Author.TYPE_COLORIST, book);
                                    break;
                                case "Inkting":
                                    i += processAuthor(td, Author.TYPE_INKING, book);
                                    break;

                                case "Cover":
                                    i += processAuthor(td, Author.TYPE_COVER_ARTIST, book);
                                    break;

                                case "Inkting cover":
                                    i += processAuthor(td, Author.TYPE_COVER_INKING, book);
                                    break;

                                case "Vertaling":
                                    i += processAuthor(td, Author.TYPE_TRANSLATOR, book);
                                    break;

                                case "Uitgever(s)":
                                    i += processPublisher(td, book);
                                    break;

                                case "Jaar":
                                    i += processText(td, DBKey.BOOK_PUBLICATION__DATE, book);
                                    break;

                                case "Pagina's":
                                    i += processText(td, DBKey.PAGE_COUNT, book);
                                    break;

                                case "ISBN":
                                    i += processText(td, DBKey.BOOK_ISBN, book);
                                    break;

                                case "Kaft":
                                    i += processText(td, DBKey.FORMAT, book);
                                    break;

                                case "Taal":
                                    i += processText(td, DBKey.LANGUAGE, book);
                                    break;

                                case "Collectie":
                                    i += processSeriesOrCollection(td, book);
                                    break;

                                case "Oplage":
                                    i += processText(td, DBKey.PRINT_RUN, book);
                                    break;

                                case "Barcode":
                                    i += processText(td, SiteField.BARCODE, book);
                                    break;

                                case "":
                                    i += processEmptyLabel(td, book);
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
                                        LoggerFactory.getLogger()
                                                     .d(TAG, "parseDoc", "unknown label=" + label);
                                    }
                            }
                            i++;
                        }
                        // we found a book, quit the for(Element row : rows)
                        break;
                    }
                }

            } catch (@NonNull final Exception e) {
                LoggerFactory.getLogger().e(TAG, e, "row=" + row);
            }
        }

        // find and process the description
        final Element item = document.selectFirst("div.item > section.grid > div.row");
        if (item != null) {
            processDescription(item, book);
        }

        // are we logged in ? Then look for any user data.
        if (loginHelper != null) {
            processUserdata(document, book, externalId);
        }

        // post-process all found data.

        if (primarySeriesTitle != null && !primarySeriesTitle.isEmpty()) {
            final Series series = Series.from3(primarySeriesTitle);
            series.setNumber(primarySeriesBookNr);
            // add to the top as this is the primary series.
            book.add(0, series);
        }

        // We DON'T store a toc with a single entry (i.e. the book title itself).
        final List<TocEntry> toc = parseToc(context, document, book);
        if (!toc.isEmpty()) {
            book.setToc(toc);
            if (TocEntry.hasMultipleAuthors(toc)) {
                book.putLong(DBKey.BOOK_CONTENT_TYPE, Book.ContentType.Anthology.getId());
            } else {
                book.putLong(DBKey.BOOK_CONTENT_TYPE, Book.ContentType.Collection.getId());
            }
        }

        if (authorResolver != null) {
            for (final Author author : book.getAuthors()) {
                authorResolver.resolve(author);
            }
        }

        // It's extremely unlikely, but should the language be missing, add dutch.
        if (!book.contains(DBKey.LANGUAGE)) {
            book.putString(DBKey.LANGUAGE, "nld");
        }

        if (isCancelled()) {
            return;
        }

        // front cover
        if (fetchCovers[0]) {
            processCover(context, document, 0, book);
        }

        if (isCancelled()) {
            return;
        }

        // back cover
        if (fetchCovers.length > 1 && fetchCovers[1]) {
            processCover(context, document, 1, book);
        }
    }

    /**
     * Parse the barcode found during the search.
     *
     * @param searchIsbnText the ISBN which we searched for
     * @param book           Bundle to update
     */
    @VisibleForTesting
    public void processBarcode(@NonNull final String searchIsbnText,
                               @NonNull final Book book) {

        final String barcode = book.getString(SiteField.BARCODE, null);
        if (barcode != null && !barcode.isEmpty()) {
            final ISBN isbnFromBarcode = new ISBN(barcode, true);
            // We found a valid barcode
            if (isbnFromBarcode.isValid(true)
                // or, it was invalid, but it *IS* the one we were searching for
                || isbnFromBarcode.asText().equals(searchIsbnText)) {

                // Then the barcode always replaces the ISBN from the site!
                book.putString(DBKey.BOOK_ISBN, isbnFromBarcode.asText());
                book.remove(SiteField.BARCODE);
            }
        }
    }

    /**
     * Parses the downloaded {@link Document} for the given cover index and saves/stores
     * the found file.
     *
     * @param context  Current context
     * @param document to parse
     * @param cIdx     0..n image index
     * @param book     Bundle to update
     *
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    private void processCover(@NonNull final Context context,
                              @NonNull final Document document,
                              @IntRange(from = 0, to = 1) final int cIdx,
                              @NonNull final Book book)
            throws StorageException {

        final String isbn = book.getString(DBKey.BOOK_ISBN);
        final String url = parseCover(document, cIdx);
        if (url != null) {
            final String fileSpec = saveCover(context, isbn, cIdx, url);
            if (fileSpec != null && !fileSpec.isEmpty()) {
                book.setCoverFileSpec(cIdx, fileSpec);
            }
        }
    }

    /**
     * Parses the downloaded {@link Document} for the given cover index.
     *
     * @param document to parse
     * @param cIdx     0..n image index
     *
     * @return full url, or {@code null} when no image found
     *
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    @AnyThread
    @Nullable
    private String parseCover(@NonNull final Document document,
                              @IntRange(from = 0, to = 1) final int cIdx) {
        switch (cIdx) {
            case 0: {
                final Element element = document
                        .selectFirst("a.stripThumb > figure.stripThumbInnerWrapper > img");
                if (element != null) {
                    return element.attr("src");
                }
                break;
            }
            case 1: {
                final Element element = document.selectFirst("a.belowImage");
                if (element != null) {
                    return element.attr("data-ajax-url");
                }
                break;
            }
            default:
                throw new IndexOutOfBoundsException(String.valueOf(cIdx));
        }
        return null;
    }

    /**
     * Download the given cover index.
     *
     * @param context Current context
     * @param isbn    (optional) ISBN of the book, will be used for the tmp cover filename
     * @param cIdx    0..n image index
     * @param url     location
     *
     * @return fileSpec, or {@code null} when not found
     *
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @Nullable
    private String saveCover(@NonNull final Context context,
                             @Nullable final String isbn,
                             @IntRange(from = 0, to = 1) final int cIdx,
                             @NonNull final String url)
            throws StorageException {

        // if the site has no image: https://www.stripinfo.be/image.php?i=0
        // if the cover is an 18+ image: https://www.stripinfo.be/images/mature.png
        // 2020-08-11: parsing was modified to bypass the 18+ image block but leaving the tests
        // in place to guard against website changes.
        if (!url.isEmpty() && !url.endsWith("i=0") && !url.endsWith("mature.png")) {

            final String fileSpec = saveImage(context, url, isbn, cIdx, null);
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
                        return null;
                    }
                }

                return fileSpec;
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
     * @param context  Current context
     * @param document to parse
     * @param book     Bundle to update
     *
     * @return the toc list with either {@code 0} or {@code 2} or more entries
     */
    @NonNull
    private List<TocEntry> parseToc(@NonNull final Context context,
                                    @NonNull final Document document,
                                    @NonNull final Book book) {
        for (final Element section : document.select("div.c12")) {
            final Element divs = section.selectFirst("div");
            if (divs != null) {
                final Elements sectionChildren = divs.children();
                if (!sectionChildren.isEmpty()) {
                    final Element sectionContent = sectionChildren.get(0);
                    // the section header we're hoping to find.
                    // <h4>Dit is een bundeling. De inhoud komt uit volgende strips:</h4>
                    final Node header = sectionContent.selectFirst("h4");
                    if (header != null && header.toString().contains("bundeling")) {
                        // the div elements inside 'row' should now contain the TOC.
                        final List<TocEntry> toc = new ArrayList<>();
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
                                final List<Author> authorList = book.getAuthors();
                                if (authorList.isEmpty()) {
                                    author = Author.createUnknownAuthor(context);
                                } else {
                                    author = authorList.get(0);
                                }
                                final TocEntry tocEntry = new TocEntry(author, title);
                                toc.add(tocEntry);
                            }
                        }
                        if (toc.size() > 1) {
                            return toc;
                        }
                    }
                }
            }
        }
        return List.of();
    }

    /**
     * Extract the site book id from the url.
     *
     * @param titleUrlElement element containing the book url
     * @param book            Bundle to update
     *
     * @return the website book id, or {@code 0} if not found.
     *         The latter should never happen unless the website structure was changed.
     */
    private long processExternalId(@NonNull final Element titleUrlElement,
                                   @NonNull final Book book) {
        long bookId = 0;
        try {
            final String titleUrl = titleUrlElement.attr("href");
            // https://www.stripinfo.be/reeks/strip/336348
            // _Hauteville_House_14_De_37ste_parallel
            final String idString = titleUrl.substring(titleUrl.lastIndexOf('/') + 1)
                                            .split("_")[0];
            bookId = Long.parseLong(idString);
            if (bookId > 0) {
                book.putLong(DBKey.SID_STRIP_INFO, bookId);
            }
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }

        return bookId;
    }

    /**
     * Process a td which is pure text.
     *
     * @param td   label td
     * @param key  for this field
     * @param book Bundle to update
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processText(@NonNull final Element td,
                            @NonNull final String key,
                            @NonNull final Book book) {
        final Element dataElement = td.nextElementSibling();
        if (dataElement != null && dataElement.childNodeSize() == 1) {
            book.putString(key, SearchEngineUtils.cleanText(dataElement.text()));
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
     * @param td   label td
     * @param book Bundle to update
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processEmptyLabel(@NonNull final Element td,
                                  @NonNull final Book book) {
        final Element dataElement = td.nextElementSibling();
        if (dataElement != null && dataElement.childNodeSize() == 1) {
            final String text = dataElement.text().trim();

            // is it a color ?
            if (COLOR_STRINGS.contains(text)) {
                book.putString(DBKey.COLOR, text);
            }
            return 1;
        }
        return 0;
    }

    /**
     * Found an Author.
     *
     * @param td   label td
     * @param type of this Author entry
     * @param book Bundle to update
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processAuthor(@NonNull final Element td,
                              @Author.Type final int type,
                              @NonNull final Book book) {
        final Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            final Elements as = dataElement.select("a");
            for (int i = 0; i < as.size(); i++) {
                final String name = as.get(i).text();
                final Author currentAuthor = Author.from(name);

                processAuthor(currentAuthor, type, book);
            }
            return 1;
        }
        return 0;
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
     * Found a Series/Collection. The latter being a publisher-named collection.
     *
     * @param td   label td
     * @param book Bundle to update
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processSeriesOrCollection(@NonNull final Element td,
                                          @NonNull final Book book) {
        final Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            final Elements as = dataElement.select("a");
            for (int i = 0; i < as.size(); i++) {
                final String text = SearchEngineUtils.cleanText(as.get(i).text());
                final Series currentSeries = Series.from3(text);
                // check if already present
                if (book.getSeries().stream()
                        .anyMatch(series -> series.equals(currentSeries))) {
                    return 1;
                }
                // just add
                book.add(currentSeries);
            }
            return 1;
        }
        return 0;
    }

    /**
     * Found a Publisher.
     *
     * @param td   label td
     * @param book Bundle to update
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int processPublisher(@NonNull final Element td,
                                 @NonNull final Book book) {
        final Element dataElement = td.nextElementSibling();
        if (dataElement != null) {
            final Elements aas = dataElement.select("a");
            for (int i = 0; i < aas.size(); i++) {
                final String name = SearchEngineUtils.cleanText(aas.get(i).text());
                final Publisher currentPublisher = Publisher.from(name);
                // check if already present
                if (book.getPublishers().stream()
                        .anyMatch(pub -> pub.equals(currentPublisher))) {
                    return 1;
                }
                // just add
                book.add(currentPublisher);
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
     *  @param item     description element, containing 1+ sections
     *
     * @param book Bundle to update
     */
    private void processDescription(@NonNull final Element item,
                                    @NonNull final Book book) {
        final Elements sections = item.select("section.c4");
        if (!sections.isEmpty()) {
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

                content.append(SearchEngineUtils.cleanText(text));
                if (i < sections.size() - 1) {
                    // separate multiple sections
                    content.append("\n<br>\n<br>");
                }
            }
            if (content.length() > 0) {
                book.putString(DBKey.DESCRIPTION, content.toString());
            }
        }
    }

    /**
     * Parse the userdata.
     *
     * @param document   root element
     * @param book       Bundle to update
     * @param externalId StripInfo id for the book
     */
    private void processUserdata(@NonNull final Element document,
                                 @NonNull final Book book,
                                 final long externalId) {

        final long collectionId = jSoupHelper.getInt(document, "stripCollectie-" + externalId);
        if (collectionId > 0) {
            try {
                //noinspection DataFlowIssue
                collectionFormParser.parse(document, externalId, collectionId, book);

            } catch (@NonNull final IOException | StorageException e) {
                if (BuildConfig.DEBUG  /* always */) {
                    LoggerFactory.getLogger()
                                 .e(TAG, e, "stripId=" + externalId
                                            + "|collectieId=" + collectionId);
                }
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

        /** String - The barcode (e.g. the EAN code) is not always an ISBN. */
        public static final String BARCODE = "__barcode";

        private SiteField() {
        }
    }
}
