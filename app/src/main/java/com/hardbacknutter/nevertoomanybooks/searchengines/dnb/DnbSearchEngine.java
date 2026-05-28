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

package com.hardbacknutter.nevertoomanybooks.searchengines.dnb;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.network.RateLimitInterceptor;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.core.network.ThrottlingInterceptor;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
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
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.nevertoomanybooks.utils.OkHttpLoggerFactory;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.AuthorRoleMapper;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import okhttp3.OkHttpClient;

/**
 * German language books & comics.
 * <p>
 * <a href="https://www.dnb.de">Deutsche Nationalbibliothek (DNB)</a>
 * <a href="https://www.dnb.de">Germany's National Library (DNB)</a>
 * <p>
 * There is no {@code robots.txt} on https://katalog.dnb.de
 * We'll respect the one from the main site instead: https://www.dnb.de/robots.txt
 * <pre>
 * User-agent: *
 * Disallow: /SiteGlobals/
 * Disallow: /DE/Service/
 * Disallow: /EN/Service/
 * Allow: /SiteGlobals/Modules/
 * Allow: /SiteGlobals/StyleBundles/
 * Allow: /SiteGlobals/Frontend/
 * Allow: /SiteGlobals/Forms/DNBWeb/Veranstaltungsuebersicht/
 * Allow: /DE/Service/Glossar/
 * Allow: /EN/Service/Glossar/
 * Crawl-delay: 10   <===== THROTLING in seconds
 * Sitemap: https://www.dnb.de/Sitemap_NavNode.xml
 * </pre>
 * <p>
 * Note the {@code Crawl-delay: 10} is not read dynamically, we just hardcode it.
 */
public class DnbSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText {

    /** Main site, but NOT the search site. */
    private static final String SITE_URL = "https://www.dnb.de";
    private static final String BOOK_URL = "https://d-nb.info/%s";
    private static final String AUTHOR_URL = "https://d-nb.info/gnd/%s";

    private static final String TAG = "DnbSearchEngine";

    /** The search site. */
    static final String KATALOG_DNB_DE = "https://katalog.dnb.de";

    private static final String PREFERENCE_KEY = "dnb";

    /**
     * Overrides the default from {@link SearchEngineConfig}.
     * <p>
     * GitHub #229: http response 429 Too Many Request
     * robots.txt: Crawl-delay: 10  (seconds)
     * <p>
     * 2026-02-17: we'll gamble... use 5 seconds...
     */
    private static final int THROTTLER_DELAY_IN_MS = 5_000;

    /**
     * Preference key: Whether to try loading cover images from the portal ('old' site)
     * where they seem to have a higher resolution.
     * <p>
     * {@code boolean}
     */
    static final String PK_COVERS_FROM_PORTAL =
            PREFERENCE_KEY + ".covers.from.portal";

    // we could probably just use the bare "https://katalog.dnb.de"...
    private static final Map<String, String> ROOT_REFERER = Map.of(
            HttpConstants.REFERER,
            KATALOG_DNB_DE + "/DE/home.html?pr=0&sortA=bez&sortD=-dat&v=plist");

    private static final String SELECT_SINGLE_RESULT = "div.l-catalog-single-content";
    private static final String SELECT_MULTI_RESULT = "div.l-catalog-results__entry";

    /**
     * Param 1: the search type.
     * ISBN: "num" -> form dropdown set to "ID"
     * Text: "all" -> form dropdown set to "Alles"
     * Param 2: the search term(s)
     */
    private static final String SEARCH_URL = "/DE/list.html?"
                                             + "key=%1$s"
                                             + "&key.GROUP=1"
                                             + "&t=%2$s"
                                             + "&sortD=-dat"
                                             + "&sortA=bez"
                                             + "&pr=0"
                                             + "&v=plist";
    private static final String SEARCH_TYPE_ISBN = "num";
    private static final String SEARCH_TYPE_TEXT = "all";

    /** Concat with the isbn. */
    private static final String HIRES_IMAGE_SEARCH = "https://portal.dnb.de/opac/mvb/cover?isbn=";

    /**
     * Suffixes we try to detect and remove from the title field.
     * Although we've not found others... it's presumed there are more.
     * Will be added when needed.
     */
    private static final String[] TITLE_SUFFIXES = {
            ": Roman",
            ": Thriller",
            ": Psychothriller",
            ": Kriminalroman",
            // We've seen this suffix without the ": " as well.
            "Kriminalroman"
    };

    private static final Pattern PATTERN_SERIES_NR = Pattern.compile(" ; ");
    private static final Pattern PATTERN_BAR = Pattern.compile("\\|");
    private static final Pattern PATTERN_SLASH = Pattern.compile("/");
    private static final Pattern PATTERN_PAGE_NUMBER = Pattern.compile("\\d+");
    private static final Pattern PATTERN_BR = Pattern.compile("<br>");

    /** Example: {@code DE/resource.html?id=118646109&pr=0&sortA=bez&sortD=-dat&v=plist}. */
    private static final Pattern AUTHOR_ID = Pattern.compile(
            "DE/resource\\.html\\?id=(\\d+)&.*");

    private final AuthorRoleMapper authorRoleMapper = new AuthorRoleMapper();
    private final DateParser<PartialDate> partialDateParser = new PartialDateParser();

    private final AuthorResolverHelper authorResolverHelper;

    /**
     * Constructor.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     *
     * @throws IllegalStateException when we could not create the SslContext.
     */
    @Keep
    public DnbSearchEngine(@NonNull final Context appContext,
                           @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        authorResolverHelper = new AuthorResolverHelper();
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
                                    R.string.site_dnb_de,
                                    List.of(R.string.site_description_german,
                                            R.string.site_description_catalog),
                                    KATALOG_DNB_DE,
                                    new Locale("de", "DE"))
                .setIdentifierKey(Identifier.SID_DNB)
                .setPreferenceFragmentClazz(DnbPreferencesFragment.class)
                .setConfig(cb -> cb
                        .setThrottlerDelayInMs(THROTTLER_DELAY_IN_MS)
                        .build(SearchEngineConfig::new));
    }

    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_dnb);
        return Set.of(
                Identifier.createBook(
                        Identifier.SID_DNB,
                        Identifier.Type.Text,
                        name,
                        SITE_URL,
                        BOOK_URL),
                Identifier.createAuthor(
                        Identifier.SID_DNB,
                        Identifier.Type.Text,
                        name,
                        SITE_URL,
                        AUTHOR_URL,
                        "P7902")
        );
    }

    @NonNull
    @Override
    protected OkHttpClient createHttpClient() {
        final SearchEngineConfig config = getEngineId().getConfig();
        //noinspection DataFlowIssue
        final Throttler throttler = config.getThrottler();
        final boolean enableLog = config.isLogHttpGetRequests();

        final OkHttpClient.Builder builder = ServiceLocator
                .getInstance()
                .getOkHttpClient()
                .newBuilder()
                .connectTimeout(config.getConnectTimeoutInMs(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getReadTimeoutInMs(), TimeUnit.MILLISECONDS)
                .addInterceptor(new ThrottlingInterceptor(throttler))
                .addInterceptor(new RateLimitInterceptor(throttler, enableLog));

        if (enableLog) {
            // use the app context, it's the non-translatable name used as a log tag
            final String tag = getName(ServiceLocator.getInstance().getAppContext());
            builder.addNetworkInterceptor(OkHttpLoggerFactory.getLogger(tag));
        }

        return builder.build();
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = getHostUrl() + String.format(SEARCH_URL,
                                                        SEARCH_TYPE_ISBN, validIsbn);
        final Document document = loadDocument(context, url, ROOT_REFERER);
        if (!isCancelled()) {
            // Check single result first
            final Element single = document.selectFirst(SELECT_SINGLE_RESULT);
            if (single != null) {
                parse(context, document, fetchCovers, book);
            } else {
                // Fallback to checking multi result
                final Element multi = document.selectFirst(SELECT_MULTI_RESULT);
                if (multi != null) {
                    parseMultiResult(context, document, fetchCovers, book);
                }
            }
        }
        return book;
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria,
                       @Nullable final String code,
                       @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner words = criteria.concatTextCriteria(" ");
        if (code != null && !code.isEmpty()) {
            words.add(code);
        }

        final Book book = new Book();

        // Sanity check
        if (words.length() == 0) {
            return book;
        }

        final String url = getHostUrl() + String.format(SEARCH_URL, SEARCH_TYPE_TEXT, words);
        final Document document = loadDocument(context, url, ROOT_REFERER);
        if (!isCancelled()) {
            // Check multi result first
            final Element multi = document.selectFirst(SELECT_MULTI_RESULT);
            if (multi != null) {
                parseMultiResult(context, document, fetchCovers, book);
            } else {
                // Fallback to checking single result
                final Element single = document.selectFirst(SELECT_SINGLE_RESULT);
                if (single != null) {
                    parse(context, document, fetchCovers, book);
                }
            }
        }
        return book;
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
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    @VisibleForTesting
    @WorkerThread
    public void parseMultiResult(@NonNull final Context context,
                                 @NonNull final Document document,
                                 @NonNull final boolean[] fetchCovers,
                                 @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {
        final Element section = document.selectFirst(SELECT_MULTI_RESULT);
        // Paranoia/sanity, we already checked in the caller
        if (section == null) {
            return;
        }

        final Elements aas = section.select("a");
        // The first one is an anchor only, while the 2nd one is the link we need to follow
        if (aas.size() > 1) {
            final Element aLink = aas.get(1);
            String bookUrl = aLink.attr("href");
            if (!bookUrl.startsWith("/")) {
                bookUrl = "/" + bookUrl;
            }
            final String url = getHostUrl() + bookUrl;
            final Document redirected = loadDocument(context, url, Map.of(HttpConstants.REFERER,
                                                                          document.location()));
            if (!isCancelled()) {
                parse(context, redirected, fetchCovers, book);
            }
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

        final Locale locale = getLocale(context);
        final Languages languages = ServiceLocator.getInstance().getLanguages();

        final Element titleElement = document
                .selectFirst("h3.c-catalog-result__ueberschrift > span");
        if (titleElement != null) {
            parseTitle(titleElement, book);
        }

        // We could collapse the this with the select for the tr's (see below)
        // But there is a tbody in between, let's take the safe route for now
        final Element tableElement = document.selectFirst("table.c-catalog-table__table");
        if (tableElement == null) {
            return;
        }
        final Elements trs = tableElement.select("tr.c-catalog-table__row");
        for (final Element tr : trs) {
            final Element label = tr.selectFirst("th.c-catalog-table__head > p");
            if (label != null) {
                final Element td = tr.selectFirst("td.c-catalog-table__content > p");
                if (td != null) {
                    final String s = label.text();
                    // We should get the german labels, but it seems we might get the
                    // English ones despite the "DE" in the url. So... check on both!
                    switch (s) {
                        case "Titel":
                        case "Title": {
                            if (!book.contains(DBKey.TITLE)) {
                                parseTitle(td, book);
                            }
                            break;
                        }
                        case "Beteiligt":
                        case "Involved": {
                            parseAuthor(context, td, book);
                            break;
                        }
                        case "Erschienen":
                        case "Published": {
                            parsePublisher(context, td, book);
                            break;
                        }
                        case "Umfang":
                        case "Extent": {
                            // can also contain colour and format
                            parsePageNumber(context, td, book);
                            break;
                        }
                        case "ISBN": {
                            parseIsbn(td, book);
                            break;
                        }
                        case "Sprache":
                        case "Language": {
                            book.setLanguage(
                                    languages.getISO3FromDisplayLanguage(td.text(), locale));
                            break;
                        }
                        case "Genre": {
                            parseGenreTags(td, book);
                            break;
                        }
                        case "Werk":
                        case "Work": {
                            // The original title for a translated book
                            // Can have Series/nr prefixed; let the user clean that up.
                            book.setTranslatedFromTitle(cleanName(td));
                            break;
                        }
                        case "Teil von":
                        case "Part of":
                            parsePartOf(td, book);
                            break;

                        case "Reihe":
                        case "Series": {
                            parseSeries(td, book);
                            break;
                        }
                        case "Persistent Identifier": {
                            parseIdentifiers(td, book);
                            break;
                        }
                        case "Datensatz-ID":
                        case "Record ID": {
                            book.setIdentifierValue(Identifier.SID_DNB, td.text());
                            break;
                        }
                        case "Originalsprache":
                        case "Original language": {
                            book.setTranslatedFromLanguage(
                                    languages.getISO3FromDisplayLanguage(td.text(), locale));
                            break;
                        }
                        case "Land":
                        case "Country":
                        case "DDC-Notation":
                        case "DDC notation":
                            // ignored for now as we don't have a field for it.
                            // ddc: Dewey-Decimal
                            break;
                    }
                }
            }
        }

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            if (ServiceLocator.getInstance().getSharedPreferences()
                              .getBoolean(PK_COVERS_FROM_PORTAL, false)) {
                final String url = HIRES_IMAGE_SEARCH + book.getIsbn();
                // No referer
                final Optional<String> fileSpec = saveImage(
                        context, url, null, book.getIsbn(), 0, null);
                if (fileSpec.isPresent()) {
                    CoverFileSpecArray.setFileSpec(book, 0, fileSpec.get());
                    // success, we're done here
                    return;
                }
            }
            // Standard parsing, also used as fallback if the hires/portal call fails.
            parseCover(context, document, book.getIsbn(), 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    /**
     * The title data from the site is very bad... as a library site they should be ashamed :(
     * <p>
     * 978-3-453-32189-2
     * Nemesis : Roman / Isaac Asimov ; deutsche Übersetzung von Irene Holicki
     * 978-3-426-22668-1
     * Totholz : Was vergraben ist, ist nicht vergessen. Kriminalroman | Nummer 1 SPIEGEL Bestseller-Autor / Andreas Föhr
     * 978-3-453-44215-3
     * Wenn sie wüsste : Thriller / Freida McFadden ; aus dem Amerikanischen von Astrid Gravert und Renate Weitbrecht
     * 978-3-551-03302-4
     * Das kleine Wir im Kindergarten / Daniela Kunkel
     * 978-3-499-01410-9
     * Brüssel sehen und sterben : wie ich im Europaparlament meinen Glauben an (fast) alles verloren habe / Nico Semsrott
     * 978-3-96584-423-0
     * Der Glukose-Masterplan / Ernährungs-Doc Dr. med. Matthias Riedl ; mit Texten von Franziska Pfeiffer und Rezepten von Inga Pfannebecker
     *
     * @param element to parse
     * @param book    Bundle to update
     */
    private void parseTitle(@NonNull final Element element,
                            @NonNull final Book book) {

        String text = element.text();
        if (text.contains("/")) {
            text = PATTERN_SLASH.split(text)[0];
        }
        if (text.contains("|")) {
            text = PATTERN_BAR.split(text)[0];
        }

        text = cleanName(text);
        for (final String suffix : TITLE_SUFFIXES) {
            if (text.endsWith(suffix)) {
                text = text.substring(0, text.length() - suffix.length() - 1).strip();
            }
        }

        book.setTitle(text);
    }

    /**
     * <pre>
     *     {@code
     *     <td class="c-catalog-table__content"><p>978-3-89908-675-1</p></td>
     *     }
     * </pre>
     * <pre>
     *     {@code
     *     <td class="c-catalog-table__content"><p>978-3-96804-070-7<br>978-3-96804-071-4</p></td>
     *     }
     * </pre>
     *
     * @param td   to parse
     * @param book to update
     */
    private void parseIsbn(@NonNull final Element td,
                           @NonNull final Book book) {
        // Only add if not already there
        if (!book.hasIsbn()) {
            // grab first ISBN only
            final String text = PATTERN_BR.split(td.text())[0];
            final String isbnText = ISBN.cleanText(text);
            // (don't do a full ISBN test here, no need)
            if (isbnText.length() == 10 || isbnText.length() == 13) {
                book.setIsbn(isbnText);
            }
        }
    }

    private void parseAuthor(@NonNull final Context context,
                             @NonNull final Element td,
                             @NonNull final Book book) {
        final Iterator<Element> it = td.children().iterator();
        while (it.hasNext()) {
            @Nullable
            Element e = it.next();

            while (e != null && e.nameIs("a")) {
                final String name = cleanName(e);
                final Author author = Author.from(name);
                final String url = e.attr("href");
                final Matcher matcher = AUTHOR_ID.matcher(url);
                if (matcher.find()) {
                    final String aId = matcher.group(1);
                    if (aId != null) {
                        author.setIdentifierValue(Identifier.SID_DNB, aId);
                    }
                }

                // the role of Author MAY be listed in the next tag
                if (it.hasNext()) {
                    // The tag AFTER the "a" can be a "<small>" or a "<br>"
                    e = it.next();
                    if (e.nameIs("small")) {
                        String authorRoleText = e.text();
                        // Sanity check, this is always true ... flw
                        if (authorRoleText.startsWith("(") && authorRoleText.endsWith(")")
                            && authorRoleText.length() > 3) {
                            authorRoleText = authorRoleText
                                    .substring(1, authorRoleText.length() - 1);
                        }
                        @AuthorRole.Role
                        final int authorRole = authorRoleMapper
                                .map(getLocale(context), authorRoleText);

                        addAuthor(author, authorRole, book);
                        if (it.hasNext()) {
                            // The tag AFTER the "small" can be a "br" or an "a"
                            e = it.next();
                            continue;
                        }
                    }
                    if (e.nameIs("br")) {
                        addAuthor(author, AuthorRole.UNKNOWN, book);
                        if (it.hasNext()) {
                            e = it.next();
                            continue;
                        }
                    }
                }
                // unknown or no more tags,
                e = null;
            }
        }
    }

    /**
     * Part-of. Seems to overlap with the above "Series" field?
     * <pre>
     * {@code
     * <td class="c-catalog-table__content">
     *   <p>
     *     <a href="DE/resource.html?id=1300481129&amp;pr=0&amp;sortA=bez&amp;sortD=-dat&amp;v=plist">
     *       <span>Star Wars Thrawn - der Aufstieg</span>
     *     </a> ; 3
     *   </p></td>
     * }
     * </pre>
     *
     * @param td   to parse
     * @param book to update
     *
     * @see #parseSeries(Element, Book)
     */
    private void parsePartOf(@NonNull final Element td,
                             @NonNull final Book book) {
        final Element a = td.selectFirst("a");
        if (a == null) {
            return;
        }
        final String title = cleanName(a);
        if (title.isBlank()) {
            return;
        }
        final Series series = Series.from(title);
        final Node node = a.nextSibling();
        if (node != null) {
            final String nrText = node.outerHtml();
            if (nrText.startsWith(" ; ") && nrText.length() > 3) {
                series.setNumber(nrText.substring(3));
            }
        }
        book.add(series);
    }

    /**
     * Series. Seems to overlap with the above "Part of" field?
     *
     * <pre>
     *     {@code
     *     <td class="c-catalog-table__content">
     *         <p>Die Foundation-Saga</p>
     *     </td>
     *     }
     * </pre>
     *
     * @param td   to parse
     * @param book to update
     *
     * @see #parsePartOf(Element, Book)
     */
    private void parseSeries(@NonNull final Element td,
                             @NonNull final Book book) {
        final String text = td.text();
        if (text.contains(" ; ")) {
            final String[] split = PATTERN_SERIES_NR.split(text);
            final String title = cleanName(split[0]);
            final Series series = Series.from(title);
            series.setNumber(split[1].strip());
            book.add(series);
        } else {
            final String title = cleanName(text);
            if (!title.isBlank()) {
                book.add(Series.from(title));
            }
        }
    }

    /**
     * Publisher and publication-date. Prefixed by the city/region.
     * Examples:
     * <pre>{@code
     *      <p>München: Wilhelm Heyne Verlag<br>01/2023</p>
     *      <p>München: Knaur<br>Juni 2024</p>
     *      <p>Wattenheim: Salleck Publ.<br>2014</p>
     *      <p>München: Blanvalet<br>[2023]</p>
     *      <p>Wattenheim: Salleck Publications<br>[2017]-</p>
     * }</pre>
     *
     * <pre>
     *     "Bamberg: Karl-May-Verlag
     *     to be released in November 2024"
     *   ==> date parsing will fail.
     * </pre>
     *
     * @param context Current context
     * @param td      to parse
     * @param book    to update
     */
    private void parsePublisher(@NonNull final Context context,
                                @NonNull final Element td,
                                @NonNull final Book book) {
        final Element p = td.selectFirst("p");
        if (p == null) {
            return;
        }

        // The part before '<br>' is a city/region name followed by ':' and the pub. name
        // The part after '<br>' is the publishing date
        final String[] brSplit = PATTERN_BR.split(p.html());

        if (brSplit.length == 0 || brSplit[0].isBlank()) {
            return;
        }

        final String name;
        if (brSplit[0].contains(":")) {
            final String[] parts = brSplit[0].split(":", 2);
            name = cleanName(parts[parts.length - 1]);
        } else {
            name = cleanName(brSplit[0]);
        }

        if (!name.isBlank()) {
            book.add(Publisher.from(name));
        }

        if (brSplit.length > 1) {
            // strip to remove line-feeds and other blanks
            String dateStr = brSplit[1].strip();
            if (!dateStr.isBlank()) {
                // Handle "[text]" with text a minimum of 4 characters, i.e. a year
                if (dateStr.length() > 5
                    && dateStr.startsWith("[")
                    && dateStr.endsWith("]")) {
                    dateStr = dateStr.substring(1, dateStr.length() - 1);
                }
                partialDateParser.parse(dateStr, getLocale(context))
                                 .ifPresent(book::setPublicationDate);
            }
        }
    }

    private void parseGenreTags(@NonNull final Element td,
                                @NonNull final Book book) {
        // there is also:
        // Themen­gebiet / Topic
        // Thema / Subject
        final String[] split = td.text().split(",");
        final List<String> tagNames = Arrays
                .stream(split)
                .map(this::cleanText)
                .filter(name -> !name.isBlank())
                .collect(Collectors.toList());
        setTags(tagNames, book);
    }

    /**
     * Page number. Patterns seen:
     * <pre>
     * "123 Seiten"
     * "182 Seiten : Illustrationen"
     * "Online-Ressource, 224 Seiten"
     * </pre>
     * We grab the first set of digit's we find.
     *
     * @param context Current context
     * @param td      to parse
     * @param book    to update
     */
    private void parsePageNumber(@NonNull final Context context,
                                 @NonNull final Element td,
                                 @NonNull final Book book) {

        final String text = td.text();
        // there may be more text snippets we can check for.
        if (text.contains("Online-Ressource")) {
            book.setFormat(context.getString(R.string.book_format_ebook));
        }
        if (text.contains("Farbig illustriert")
            || text.contains("Mit farbigen Zeichnungen")) {
            book.setColor(context.getString(R.string.book_color_full_color));
        }

        final Matcher matcher = PATTERN_PAGE_NUMBER.matcher(text);
        if (matcher.find()) {
            book.setPages(matcher.group());
        }
    }

    private void parseIdentifiers(@NonNull final Element td,
                                  @NonNull final Book book) {
        // we grab these from the url, so we get both the type and the value in one go.
        td.select("a")
          .stream()
          .map(a -> a.attr("href"))
          .forEach(url -> {
              if (url.startsWith("https://nbn-resolving.org/")) {
                  // "https://nbn-resolving.org/urn:nbn:de:101:1-2023102516403481026473"
                  book.setIdentifierValue(Identifier.SID_URN,
                                          url.substring(26));

              } else if (url.startsWith("https://dx.doi.org/")) {
                  // "https://dx.doi.org/10.1007/s40278-023-43601-1"
                  book.setIdentifierValue(Identifier.SID_DOI,
                                          url.substring(19));
              }
          });
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
    @VisibleForTesting
    @NonNull
    private Optional<String> parseCover(@NonNull final Context context,
                                        @NonNull final Document document,
                                        @Nullable final String bookId,
                                        @SuppressWarnings("SameParameterValue")
                                            @IntRange(from = 0, to = 0) final int cIdx)
            throws StorageException {

        final Element img = document.selectFirst(
                "div.c-catalog-result__image-wrapper > div > span > img");
        if (img == null) {
            return Optional.empty();
        }
        String url = img.attr("src");
        if (url.isEmpty()) {
            return Optional.empty();
        }
        // sanity check
        if (!url.startsWith("https")) {
            url = getHostUrl() + url;
        }

        // 2025-06-14: instead of the above, we could also
        // just TRY "https://portal.dnb.de/opac/mvb/cover?isbn=..."
        // for a high-res image...
        // And if that fails, fall back to the parsed url.
        // But that disrupts the call-flow of the website and could lead
        // to access issues (see referer usage)
        return saveImage(context, url,
                         Map.of(HttpConstants.REFERER, document.location()),
                         bookId, cIdx, null);
    }
}
