/*
 * @Copyright 2018-2026 HardBackNutter
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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteAuthModule;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.BookshelfMapper;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.CollectionFormParser;
import com.hardbacknutter.nevertoomanybooks.utils.JSoupHelper;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 * <a href="https://stripinfo.be/">https://stripinfo.be/</a>
 * <p>
 * Dutch language (and to an extent French and a minimal amount of other languages) comics website.
 * <p>
 * Implementing {@link SearchEngine.ByText} is problematic due to the site search-field
 * not being a true "search-terms" field. Instead, it expects a single search-term
 * which it will then use to search in distinct columns and list the results in a table
 * with the results in different areas.
 * (try the site to see what the above means...)
 * <p>
 * {@link SearchEngine.ByBarcode}: for barcodes (explicitly supported by the site
 * and invalid ISBNs (which the site stores as-is on purpose)
 * <p>
 * ENHANCE: check if we can implement {@link SearchEngine.AlternativeEditions}
 * and consequently {@link SearchEngine.CoverByEdition}
 * Comics re-published by a different publisher do have different ISBN's.
 * We need to check if there is a way of finding those alternative ISBN numbers.
 * Same remark for LastDodo
 */
public class StripInfoSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByExternalId,
                   SearchEngine.ByBarcode,
                   SearchEngine.Login {

    public static final String SITE_URL = "https://stripinfo.be";
    public static final String BOOK_URL = "https://stripinfo.be/reeks/strip/%s";
    public static final String AUTHOR_URL = "https://stripinfo.be/auteur/index/%s";

    private static final String PREFERENCE_KEY = "stripinfo";

    static final String PK_LOGIN_TO_SEARCH = PREFERENCE_KEY + ".login.to.search";
    /** Log tag. */
    private static final String TAG = "StripInfoSearchEngine";
    /** Colour string values as used on the site. Complete 2019-10-29. */
    private static final String COLOR_STRINGS = "Kleur|Zwart/wit|Zwart/wit met steunkleur";

    /** Param 1: external book ID; really a 'long'. */
    private static final String BY_EXTERNAL_ID = "/reeks/strip/%1$s";
    /** Param 1: ISBN. */
    private static final String BY_ISBN = "/zoek/zoek?zoekstring=%1$s";

    public static final String COLLECTION_FORM_URL = "/ajax_collectie.php";

    /** The description contains h4 tags which we remove to make the text shorter. */
    private static final Pattern H4_OPEN_PATTERN = Pattern.compile("<h4>\\s*");
    private static final Pattern H4_CLOSE_PATTERN = Pattern.compile("\\s*</h4>");

    /** The hostname MIGHT be with or without the 'www' part. */
    private static final Pattern AUTHOR_ID = Pattern.compile(
            "https://.*/auteur/index/(\\d+)_.*");
    /**
     * When a multi-result page is returned, its title will start with this text.
     * (Dutch for: Searching for...)
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

    private final DateParser<PartialDate> dateParser = new PartialDateParser();

    @NonNull
    private final RatingParser ratingParser;
    private final AuthorResolverHelper authorResolverHelper;
    @Nullable
    private SiteAuthModule siteAuthModule;
    @Nullable
    private CollectionFormParser collectionFormParser;

    /**
     * Constructor.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public StripInfoSearchEngine(@NonNull final Context appContext,
                                 @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        authorResolverHelper = new AuthorResolverHelper();
        ratingParser = new RatingParser(10);
    }

    /**
     * Called during startup to initialise the immutable/default engine configuration.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @return {@link EngineId.Builder}
     */
    @Keep
    @NonNull
    public static EngineId.Builder init() {
        return new EngineId.Builder(PREFERENCE_KEY,
                                    R.string.site_stripinfo_be,
                                    List.of(R.string.site_description_dutch_and_more,
                                            R.string.site_description_catalog,
                                            R.string.site_description_eu_comics),
                                    "https://www.stripinfo.be",
                                    new Locale("nl", "BE"))
                .setIdentifierKey(Identifier.SID_STRIP_INFO)
                .setPreferenceFragmentClazz(StripInfoBePreferencesFragment.class)
                .setConfig(cb -> cb
                        // default timeouts based on limited testing
                        .setConnectTimeoutMs(7_000)
                        .setReadTimeoutMs(60_000)
                        .build(SearchEngineConfig::new));
    }

    @Override
    public boolean isLoginToSearch(@NonNull final Context context) {
        if (BuildConfig.ENABLE_STRIP_INFO_LOGIN) {
            return PreferenceManager.getDefaultSharedPreferences(context)
                                    .getBoolean(PK_LOGIN_TO_SEARCH, false);
        } else {
            return false;
        }
    }

    @Override
    public void setAuthModule(@NonNull final SiteAuthModule authModule) {
        if (BuildConfig.DEBUG /* always */) {
            authModule.getUserId().orElseThrow();
        }
        this.siteAuthModule = authModule;
    }

    @Override
    public void login(@NonNull final Context context)
            throws CredentialsException, SearchException {
        // Depending on if we get here from a search or from a sync,
        // the module MIGHT already exist so don't login twice!
        if (siteAuthModule == null) {
            siteAuthModule = new StripInfoAuth(cookieManager);
            try {
                siteAuthModule.login(context);
            } catch (@NonNull final IOException | StorageException e) {
                siteAuthModule = null;
                throw new SearchException(getEngineId(), e);
            }
        }

        // Recreate every time we load a doc; the user could have changed the preferences.
        collectionFormParser = new CollectionFormParser(context, new BookshelfMapper());
    }

    @Override
    @AnyThread
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (collectionFormParser != null) {
                collectionFormParser.cancel();
            }
            if (siteAuthModule != null) {
                siteAuthModule.cancel();
            }
        }
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = getHostUrl() + String.format(BY_EXTERNAL_ID, externalId);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book);
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

        final String url = getHostUrl() + String.format(BY_ISBN, validIsbn);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            parseRootDocument(context, validIsbn, document, fetchCovers, book);
        }
        return book;
    }

    @VisibleForTesting
    public void parseRootDocument(@NonNull final Context context,
                                  @NonNull final String validIsbn,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {
        if (isMultiResult(document)) {
            parseMultiResult(context, document, fetchCovers, book);
        } else {
            parse(context, document, fetchCovers, book);
        }

        // Finally, replace potential invalid ISBN numbers.
        // See method docs for details
        processBarcode(validIsbn, book);
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
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    public void parseMultiResult(@NonNull final Context context,
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
                        parse(context, redirected, fetchCovers, book);
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
     * Parse the downloaded {@link org.jsoup.nodes.Document} for a single Book.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
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
                      @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // extracted from the page header.
        @Nullable
        String primarySeriesTitle = parsePrimarySeriesTitle(document);
        if (primarySeriesTitle != null) {
            primarySeriesTitle = cleanText(primarySeriesTitle);
        }

        // extracted from the title section.
        String primarySeriesBookNr = null;

        long externalId = 0;
        final Elements rows = document.select("div.row");
        for (final Element row : rows) {
            if (isCancelled()) {
                return;
            }

            //noinspection CheckStyle,OverlyBroadCatchBlock
            try {
                // use the title header to determine we are in a book row.
                final Element titleHeader = row.selectFirst("h2.title");
                if (titleHeader != null) {
                    final Element issueNumber = titleHeader
                            .selectFirst("span[itemprop=\"issueNumber\"]");
                    if (issueNumber != null) {
                        primarySeriesBookNr = issueNumber.text().strip();
                    }

                    final Element titleUrlElement = titleHeader.selectFirst(A_HREF_STRIP);
                    if (titleUrlElement != null) {
                        book.setTitle(cleanText(titleUrlElement));
                        externalId = parseExternalId(titleUrlElement, book);

                        final Elements tds = row.select("td");
                        int i = 0;
                        while (i < tds.size()) {
                            final Element td = tds.get(i);
                            final String label = td.text();

                            switch (label) {
                                case "Scenario":
                                case "Naar":
                                    i += parseAuthor(td, AuthorRole.WRITER, book);
                                    break;

                                case "Tekeningen":
                                    i += parseAuthor(td, AuthorRole.ARTIST, book);
                                    break;

                                case "Kleuren":
                                    i += parseAuthor(td, AuthorRole.COLORIST, book);
                                    break;
                                case "Inkting":
                                    i += parseAuthor(td, AuthorRole.INKING, book);
                                    break;

                                case "Cover":
                                    i += parseAuthor(td, AuthorRole.COVER_ARTIST, book);
                                    break;

                                case "Inkting cover":
                                    i += parseAuthor(td, AuthorRole.COVER_INKING, book);
                                    break;

                                case "Vertaling":
                                    i += parseAuthor(td, AuthorRole.TRANSLATOR, book);
                                    break;

                                case "Storyboard":
                                    i += parseAuthor(td, AuthorRole.STORYBOARD, book);
                                    break;

                                case "Lettering":
                                    i += parseAuthor(td, AuthorRole.LETTERING, book);
                                    break;

                                case "Uitgever(s)":
                                    i += parsePublisher(td, book);
                                    break;

                                case "Jaar": {
                                    final String text = extractText(td);
                                    if (text != null && !text.isEmpty()) {
                                        dateParser.parse(text).ifPresent(book::setPublicationDate);
                                        i++;
                                    }
                                    break;
                                }
                                case "Pagina's": {
                                    final String text = extractText(td);
                                    if (text != null) {
                                        book.setPages(text);
                                        i++;
                                    }
                                    break;
                                }
                                case "ISBN": {
                                    final String text = extractText(td);
                                    if (text != null) {
                                        book.setIsbn(text);
                                        i++;
                                    }
                                    break;
                                }
                                case "Kaft": {
                                    final String text = extractText(td);
                                    if (text != null) {
                                        book.setFormat(text);
                                        i++;
                                    }
                                    break;
                                }
                                case "Taal": {
                                    final String text = extractText(td);
                                    if (text != null) {
                                        book.setLanguage(text);
                                        i++;
                                    }
                                    break;
                                }
                                case "Collectie": {
                                    i += parseSeriesOrCollection(td, book);
                                    break;
                                }
                                case "Oplage": {
                                    final String text = extractText(td);
                                    if (text != null) {
                                        book.setPrintRun(text);
                                        i++;
                                    }
                                    break;
                                }
                                case "Barcode": {
                                    final String text = extractText(td);
                                    if (text != null) {
                                        book.putString(SiteField.BARCODE, text);
                                        i++;
                                    }
                                    break;
                                }
                                case "": {
                                    i += parseEmptyLabel(td, book);
                                    break;
                                }
                                case "Cycli":
                                    // not currently used. Example: Cyclus 2 nr. 1
                                    // This is subseries 2, book 1, inside a series.
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

        Element item;
        // find and process the description
        item = document.selectFirst("div.item > section.grid > div.row");
        if (item != null) {
            parseDescription(item, book);
        }

        // find and process the rating
        item = document.selectFirst("a#stripsScore");
        if (item != null) {
            ratingParser.parse(item.text()).ifPresent(book::setRating);
        }

        // Are we logged in ? Then look for any user data.
        // The only time the externalId might be 0 is if the site was changed
        // and parsing (partially) failed. i.e. ... we're paranoid
        if (siteAuthModule != null && externalId > 0) {
            parseUserdata(document, book, externalId);
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
                book.setContentType(Book.ContentType.Anthology);
            } else {
                book.setContentType(Book.ContentType.Collection);
            }
        }

        authorResolverHelper.resolve(context, this, book);

        // It's extremely unlikely, but should the language be missing, add Dutch.
        if (!book.contains(DBKey.LANGUAGE)) {
            book.setLanguage("nld");
        }

        if (isCancelled()) {
            return;
        }

        final String isbn = book.getIsbn();

        // front cover
        if (fetchCovers[0]) {
            parseCover(context, document, isbn, 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }

        if (isCancelled()) {
            return;
        }

        // back cover
        if (fetchCovers[1]) {
            parseCover(context, document, isbn, 1).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 1, fileSpec));
        }
    }

    /**
     * Try and replace potential invalid ISBNs
     * with the barcode as found on the site.
     * We do this because the site will ON PURPOSE list invalid ISBNs
     * as present of the physical book,
     * while the barcode field will (usually) contain the correct ISBN.
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
            if (isbnFromBarcode.isValid()
                // or, it was invalid, but it *IS* the one we were searching for
                || isbnFromBarcode.asText().equals(searchIsbnText)) {

                // Then the barcode always replaces the ISBN from the site!
                book.setIsbn(isbnFromBarcode.asText());
                book.remove(SiteField.BARCODE);
            }
        }
    }

    /**
     * Parses the given {@link Document} for the cover and fetches it when present.
     *
     * @param context  Current context
     * @param document to parse
     * @param bookId   (optional) isbn or native id of the book,
     *                 will only be used for the temporary cover filename
     * @param cIdx     0..n image index
     *
     * @return fileSpec
     *
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    private Optional<String> parseCover(@NonNull final Context context,
                                        @NonNull final Document document,
                                        @Nullable final String bookId,
                                        @SuppressWarnings("SameParameterValue")
                                        @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        String url = null;
        if (cIdx == 0) {
            final Element element = document.selectFirst(
                    "a.stripThumb > figure.stripThumbInnerWrapper > img");
            if (element != null) {
                url = element.attr("src");
            }
        } else if (cIdx == 1) {
            final Element element = document.selectFirst("a.belowImage");
            if (element != null) {
                url = element.attr("data-ajax-url");
            }
        }
        if (url == null) {
            return Optional.empty();
        }
        return saveCover(context, url, bookId, cIdx);

    }

    /**
     * Download the given cover index.
     *
     * @param context Current context
     * @param url     location
     * @param bookId  (optional) isbn or native id of the book,
     *                will only be used for the temporary cover filename
     * @param cIdx    0..n image index
     *
     * @return fileSpec
     *
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    private Optional<String> saveCover(@NonNull final Context context,
                                       @NonNull final String url,
                                       @Nullable final String bookId,
                                       @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        // if the site has no image: https://www.stripinfo.be/image.php?i=0
        // if the cover is an 18+ image: https://www.stripinfo.be/images/mature.png
        // 2020-08-11: parsing was modified to bypass the 18+ image block but leaving the tests
        // in place to guard against website changes.
        if (!url.isEmpty() && !url.endsWith("i=0") && !url.endsWith("mature.png")) {

            final Optional<String> oFileSpec = saveImage(context, url, null, bookId, cIdx, null);
            if (oFileSpec.isPresent()) {
                // Some back covers will return the "no cover available" image regardless.
                // Sadly, we need to check explicitly after the download.
                // But we need to check on "mature content" as well anyhow.
                final File file = new File(oFileSpec.get());
                final long fileLen = file.length();
                // check the length as a quick check first
                if (fileLen == NO_COVER_FILE_LEN
                    || fileLen == MATURE_FILE_LEN) {
                    // do the thorough check with md5 calculation as a second defence
                    final byte[] digest = md5(file);
                    if (Arrays.equals(digest, NO_COVER_MD5)
                        || Arrays.equals(digest, MATURE_COVER_MD5)) {
                        FileUtils.backgroundDelete(file);
                        return Optional.empty();
                    }
                }

                return oFileSpec;
            }
        }

        return Optional.empty();
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
     * table: books, with a primary title, and a list of secondary titles (i.e. the toc).
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
                                    number = nrNode.toString().strip();
                                }

                                // the number is not used in the TOC as we don't support
                                // linking a TOC entry to another book.
                                // Instead, prepend it to the title as a reference.
                                if (number != null) {
                                    title = number + ' ' + a.text();
                                } else {
                                    title = a.text();
                                }
                            }

                            if (title != null && !title.isEmpty()) {
                                // always use the first author only for TOC entries.
                                Author tocAuthor = book.getPrimaryAuthor();
                                if (tocAuthor == null) {
                                    tocAuthor = Author.createUnknownAuthor(context);
                                }
                                toc.add(new TocEntry(tocAuthor, title));
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
    private long parseExternalId(@NonNull final Element titleUrlElement,
                                 @NonNull final Book book) {
        long sid = 0;
        try {
            final String titleUrl = titleUrlElement.attr("href");
            // https://www.stripinfo.be/reeks/strip/336348
            // _Hauteville_House_14_De_37ste_parallel
            final String idString = titleUrl.substring(titleUrl.lastIndexOf('/') + 1)
                                            .split("_")[0];
            // Paranoia: parse to make sure it's a number
            sid = Long.parseLong(idString);
            if (sid > 0) {
                book.setIdentifierValue(Identifier.SID_STRIP_INFO, sid);
            }
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }

        return sid;
    }

    /**
     * Process a td which is pure text.
     *
     * @param td label td
     *
     * @return the text, or {@code null}
     */
    @Nullable
    private String extractText(@NonNull final Element td) {
        final Element dataElement = td.nextElementSibling();
        if (dataElement != null && dataElement.childNodeSize() == 1) {
            return cleanText(dataElement);
        }
        return null;
    }

    /**
     * At least one element does not have an actual label.
     * We inspect the value to try and guess the type.
     * <p>
     * Currently known (2019-10-11):
     * - the colour scheme of the comic.
     *
     * @param td   label td
     * @param book Bundle to update
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int parseEmptyLabel(@NonNull final Element td,
                                @NonNull final Book book) {
        final Element dataElement = td.nextElementSibling();
        if (dataElement != null && dataElement.childNodeSize() == 1) {
            final String text = dataElement.text().strip();

            // is it a colour ?
            if (COLOR_STRINGS.contains(text)) {
                book.setColor(text);
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
    private int parseAuthor(@NonNull final Element td,
                            @AuthorRole.Role final int type,
                            @NonNull final Book book) {
        final Element dataElement = td.nextElementSibling();
        if (dataElement == null) {
            return 0;
        }
        dataElement.select("a").forEach(a -> {
            final String name = cleanName(a);
            final Author author = Author.from(name);

            final String url = a.attr("href");
            final Matcher matcher = AUTHOR_ID.matcher(url);
            if (matcher.find()) {
                final String siId = matcher.group(1);
                if (siId != null) {
                    author.setIdentifierValue(Identifier.SID_STRIP_INFO, siId);
                }
            }

            addAuthor(author, type, book);
        });
        return 1;
    }

    /**
     * Extract the series title from the header.
     *
     * @param document to parse
     *
     * @return title, or {@code null} for none
     */
    @Nullable
    private String parsePrimarySeriesTitle(@NonNull final Element document) {
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
        if (seriesElement == null) {
            return null;
        }
        final Element img = seriesElement.selectFirst("img");
        if (img != null) {
            return img.attr("alt");
        }
        final Element a = seriesElement.selectFirst("a");
        if (a != null) {
            return a.text();
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
    private int parseSeriesOrCollection(@NonNull final Element td,
                                        @NonNull final Book book) {
        final Element dataElement = td.nextElementSibling();
        if (dataElement == null) {
            return 0;
        }
        final Elements as = dataElement.select("a");
        for (int i = 0; i < as.size(); i++) {
            final String text = cleanText(as.get(i));
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

    /**
     * Found a Publisher.
     *
     * @param td   label td
     * @param book Bundle to update
     *
     * @return 1 if we found a value td; 0 otherwise.
     */
    private int parsePublisher(@NonNull final Element td,
                               @NonNull final Book book) {
        final Element data = td.nextElementSibling();
        if (data == null) {
            return 0;
        }
        data.select("a")
            .stream()
            .map(this::cleanText)
            .filter(text -> !text.isBlank())
            .map(Publisher::from)
            .forEach(book::add);
        return 1;
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
     * @param item description element, containing 1+ sections
     * @param book Bundle to update
     */
    private void parseDescription(@NonNull final Element item,
                                  @NonNull final Book book) {
        final Elements sections = item.select("section.c4");
        if (sections.isEmpty()) {
            return;
        }
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
            book.setDescription(content.toString());
        }
    }

    /**
     * Parse the userdata.
     *
     * @param document   root element
     * @param book       Bundle to update
     * @param externalId StripInfo id for the book
     */
    private void parseUserdata(@NonNull final Element document,
                               @NonNull final Book book,
                               @IntRange(from = 1) final long externalId) {

        jSoupHelper.getPositiveLong(document, "stripCollectie-" + externalId).ifPresent(
                collectionId -> {
                    try {
                        //noinspection DataFlowIssue
                        collectionFormParser.parse(document, externalId, collectionId, book);

                    } catch (@NonNull final IOException | StorageException e) {
                        if (BuildConfig.DEBUG  /* always */) {
                            LoggerFactory.getLogger()
                                         .e(TAG, e, "externalId=" + externalId
                                                    + "|collectionId=" + collectionId);
                        }
                    }
                }
        );
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
