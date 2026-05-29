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

package com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpCookie;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
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
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 * <a href="https://www.bedetheque.com/">bedetheque</a>.
 * <p>
 * French language (and to some extent other languages) comics.
 * <p>
 * Implementing {@link SearchEngine.ByText} is not possible.
 * The form on the site seems to insist on doing lookups for each field individually
 * e.g. when entering a series name, it wil do a lookup immediately and retrieve the internal
 * id for that series. The combined search relies on those type of fields having resolved
 * all id's before the actual search is done.
 */
public class BedethequeSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId {

    private static final String SITE_URL = "https://www.bedetheque.com";
    private static final String BOOK_URL = "https://www.bedetheque.com/BD-x-%s.html";
    private static final String AUTHOR_URL = "https://www.bedetheque.com/auteur-%s-BD-x.html";
    private static final String SERIES_URL = "https://www.bedetheque.com/serie-%s-BD-x.html";

    private static final String PREFERENCE_KEY = "bedetheque";

    /** Get the id from an Author url. */
    private static final Pattern AUTHOR_ID = Pattern.compile(".*/auteur-(\\d+)-");
    /** Get the id from a Series url. */
    private static final Pattern SERIES_ID = Pattern.compile(".*/serie-(\\d+)-");

    /** Later editions; heading format. */
    private static final Pattern NR_TITLE_PATTERN = Pattern.compile(
            "(\\d*)\\s*<span.*/span>\\s?\\.\\s?(.*)");
    /** MM-YYYY. */
    private static final Pattern PUB_DATE = Pattern.compile("\\d\\d/\\d\\d\\d\\d");

    /** Whether we can map as usual, or (true) if we want to keep the French format names. */
    static final String PK_BEDETHEQUE_PRESERVE_FORMAT_NAMES =
            PREFERENCE_KEY + ".resolve.formats";

    /** These are generic author names which are really the colour. */
    private static final List<String> AUTHOR_NAME_COLOR =
            List.of("<N&B>", "<Monochromie>", "<Bichromie>", "<Trichromie>", "<Quadrichromie>");

    /** A text indicating it's a softcover. Can occur in more than one field. */
    private static final String FORMAT_COUVERTURE_SOUPLE = "Couverture souple";

    /** The "en" must be as-is. */
    private static final Pattern SERIES_WITH_LANGUAGE = Pattern
            .compile("(.*)\\s+\\(en (.*)\\)");

    private static final Pattern SERIES_WITH_SIMPLE_PREFIX = Pattern
            .compile("(.*)\\s+\\((le|la|les|l'|the)\\)",
                     Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String COOKIE_NAME = "csrf_cookie_bel";
    private static final String COOKIE_DOMAIN = ".bedetheque.com";
    private static final String SEARCH_URL = "/search";

    /**
     * Search.
     * <p>
     * Param 1: cookie
     * Param 2: ISBN
     *
     * @see #ensureCookie(Context)
     */
    private static final String BY_ISBN = SEARCH_URL + "/albums?"
                                          + "RechIdSerie="
                                          + "&RechIdAuteur="
                                          // cookie name=value
                                          + "&%1$s"
                                          + "&RechSerie="
                                          + "&RechTitre="
                                          + "&RechEditeur="
                                          + "&RechCollection="
                                          + "&RechStyle="
                                          + "&RechAuteur="
                                          + "&RechISBN=%2$s"
                                          + "&RechParution="
                                          + "&RechOrigine="
                                          + "&RechLangue="
                                          + "&RechMotCle="
                                          + "&RechDLDeb="
                                          + "&RechDLFin="
                                          + "&RechCoteMin="
                                          + "&RechCoteMax="
                                          + "&RechEO=0";

    /**
     * 'x' is normally the title, which the site will ignore.
     * The site recognises the url by the prefix 'BD-' and the last '-' followed by the sid
     */
    private static final String BY_EXTERNAL_ID = "/BD-x-%s.html";

    private final Map<String, String> extraRequestProperties;
    private final DateParser<PartialDate> dateParser = new PartialDateParser();
    private final AuthorResolverHelper authorResolverHelper;
    @Nullable
    private HttpCookie sessionCookie;

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
    public BedethequeSearchEngine(@NonNull final Context appContext,
                                  @NonNull final SearchEngineConfig config) {
        super(appContext, config);
        extraRequestProperties = Map.of(HttpConstants.REFERER, getHostUrl() + SEARCH_URL);

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
                                    R.string.site_bedetheque,
                                    List.of(R.string.site_description_french,
                                            R.string.site_description_catalog,
                                            R.string.site_description_eu_comics),
                                    "https://www.bedetheque.com",
                                    Locale.FRANCE)
                .setIdentifierKeys(Identifier.SID_BEDETHEQUE)
                .setPreferenceFragmentClazz(BedethequePreferencesFragment.class)
                .setAuthorResolverSupplier(BedethequeAuthorResolver::create)
                .setConfig(cb -> cb
                        // default timeouts based on limited testing
                        .setConnectTimeoutMs(15_000)
                        .setReadTimeoutMs(60_000)
                        .build(SearchEngineConfig::new));
    }

    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_bedetheque);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Number,
                               Identifier.SID_BEDETHEQUE,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               null),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Number,
                               Identifier.SID_BEDETHEQUE,
                               name,
                               SITE_URL,
                               AUTHOR_URL,
                               "P5491"),
                new Identifier(Identifier.EntityType.Series,
                               Identifier.Type.Number,
                               Identifier.SID_BEDETHEQUE,
                               name,
                               SITE_URL,
                               SERIES_URL,
                               "P8619")
        );
    }

    /**
     * Fetch the session cookie. We only fetch it once, then cache it in this instance.
     *
     * @param context Current context
     *
     * @return name=value string for the cookie
     *
     * @throws SearchException on generic exceptions (wrapped) during search
     */
    @NonNull
    private String ensureCookie(@NonNull final Context context)
            throws SearchException {
        if (sessionCookie == null || sessionCookie.hasExpired()) {
            try {
                final FutureHttp<HttpCookie> httpHead = createHeadRequest();
                // Reminder: the "request" will be connected and the response code will be OK,
                // so just extract the cookie we need for the next request
                sessionCookie = httpHead.head(getHostUrl() + SEARCH_URL, response ->
                        ServiceLocator.getInstance().getCookieManager()
                                      .getCookieStore()
                                      .getCookies()
                                      .stream()
                                      .filter(c -> COOKIE_DOMAIN.equals(c.getDomain())
                                                   && COOKIE_NAME.equals(c.getName()))
                                      .findFirst()
                                      .orElse(new HttpCookie(COOKIE_NAME, "")));
            } catch (@NonNull final IOException | UncheckedIOException | StorageException e) {
                throw new SearchException(getEngineId(), e);
            }
        }
        if (sessionCookie == null || sessionCookie.getValue().isEmpty()) {
            throw new SearchException(getEngineId(), "no sessionCookie",
                                      context.getString(R.string.httpError));
        }

        return sessionCookie.getName() + '=' + Objects.requireNonNull(sessionCookie.getValue());
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        //The site is very "defensive". We must specify the full url and set the "Referer".
        final String url = getHostUrl() + String.format(
                BY_ISBN, ensureCookie(context), validIsbn);

        final Document document = loadDocument(context, url, extraRequestProperties);

        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, book,
                             validIsbn);
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();
        final String url = getHostUrl() + String.format(BY_EXTERNAL_ID, externalId);
        final Document document = loadDocument(context, url, extraRequestProperties);
        parse(context, document, fetchCovers, null, book);

        return book;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context      Current context
     * @param document     to parse
     * @param fetchCovers  Set array indexes to {@code true} to fetch a cover for that index.
     *                     Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book         to update
     * @param searchedIsbn ISBN from user-search
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    @WorkerThread
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Book book,
                                  @Nullable final String searchedIsbn)
            throws StorageException, SearchException, CredentialsException {

        // Grab the first search result, and redirect to that page
        final Element section = document.selectFirst("ul.search-list");
        if (section != null) {
            final Element urlElement = section.selectFirst("a");
            if (urlElement != null) {
                final String url = urlElement.attr("href");
                if (!url.isBlank()) {
                    final Document redirected = loadDocument(context, url, extraRequestProperties);
                    if (!isCancelled()) {
                        parse(context, redirected, fetchCovers, searchedIsbn, book);
                    }
                }
            }
        }
    }

    /**
     * Parse the downloaded {@link org.jsoup.nodes.Document} for a single Book.
     *
     * @param context         Current context
     * @param document        to parse
     * @param fetchCovers     Set array indexes to {@code true} to fetch a cover for that index.
     *                        Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param searchedIsbnStr the ISBN the user searched for;
     *                        Will be {@code null} if the search was done by SID
     * @param book            to update
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
                      @Nullable final String searchedIsbnStr,
                      @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // The main book.
        // If we searched by SID, this will be the exact edition we wanted.
        // This section is the panel on the right "Informations sur l'album".
        final Element mainSection = document.selectFirst(
                "div.tab_content_liste_albums > ul.infos-albums");
        if (mainSection == null) {
            return;
        }

        // Get the series from the top of the page.
        final Element a = document.selectFirst("a[href^='https://www.bedetheque.com/serie-']");
        if (a != null) {
            parseSeries(a, book);
        }

        boolean isMainEdition = true;

        if (searchedIsbnStr == null) {
            // search by SID, always/only the main edition
            parseLabels(context, book, mainSection);
        } else {
            // search by ISBN
            final ISBN searchedIsbn = new ISBN(searchedIsbnStr, true);
            // check if the main edition is an exact match
            if (matches(mainSection, searchedIsbn)) {
                parseLabels(context, book, mainSection);
            } else {
                // check the other editions
                final Elements editions = document.select("ul.liste-albums > li");
                for (final Element edition : editions) {
                    final Element albumMain = edition.selectFirst("div.album-main");
                    if (albumMain != null) {
                        final Element infos = albumMain.selectFirst("div.album-main > ul.infos");
                        if (infos != null && matches(infos, searchedIsbn)) {
                            parseEditionDetails(context, mainSection, albumMain, infos, book);
                            parseEditionCovers(context, edition, fetchCovers, book);
                            // quit the for-loop
                            isMainEdition = false;
                            break;
                        }
                    }
                }
            }

            if (book.getTitle().isEmpty()) {
                return;
            }
        }

        final Element description = document.selectFirst("span[itemprop='description']");
        if (description != null) {
            final String s = cleanText(description);
            if (!s.isBlank()) {
                book.setDescription(s);
            }
        }

        authorResolverHelper.resolve(context, this, book);

        // Unless present, add the default language
        if (!book.contains(DBKey.LANGUAGE)) {
            book.setLanguage("fra");
        }

        if (isCancelled()) {
            return;
        }

        if (isMainEdition) {
            parseMainCovers(context, document, fetchCovers, book);
        }
    }

    private void parseEditionDetails(@NonNull final Context context,
                                   @NonNull final Element mainSection,
                                   @NonNull final Element albumMain,
                                   @NonNull final Element infos,
                                   @NonNull final Book book) {

        // The title and series nr is a heading
        final Element titleElement = albumMain.selectFirst("h3.titre");
        if (titleElement != null) {
            // <h3 class="titre">9<span class="numa">a1978/01</span> . Les soucoupes volantes</h3>
            // grab the HTML, to avoid the concatenation of the text
            // in the span. We might later want to extract that text as well
            final Matcher matcher = NR_TITLE_PATTERN.matcher(titleElement.html());
            if (matcher.find()) {
                final String s = matcher.group(2);
                if (s != null) {
                    final String title = cleanText(s);
                    if (!title.isBlank()) {
                        book.setTitle(title);
                        final String nrInSeries = matcher.group(1);
                        // educated gamble, add the nr to the first/only series we parsed earlier
                        final List<Series> series = book.getSeries();
                        if (!series.isEmpty()) {
                            series.get(0).setNumber(nrInSeries);
                        }
                    }
                }
            }
        }

        parseLabels(context, book, infos);
    }

    private void parseEditionCovers(@NonNull final Context context,
                                    @NonNull final Element edition,
                                    @NonNull final boolean[] fetchCovers,
                                    @NonNull final Book book)
            throws StorageException {

        // contains "front-cover" + "extra-images" + "back-cover"
        List<String> coverUrls = edition
                .select("div.album-side > div.sous-couv > a")
                .stream()
                .map(element -> element.attr("href"))
                .collect(Collectors.toList());

        final String isbn = book.getIsbn();

        if (fetchCovers[0]) {
            final String url = coverUrls.get(0);
            fetchCover(context, url, 0, isbn, book);
        }

        if (isCancelled()) {
            return;
        }

        if (coverUrls.size() > 1) {
            // Drop the front-cover
            coverUrls = coverUrls.subList(1, coverUrls.size());
            handleExtraImagesAndBackCover(context, coverUrls, fetchCovers, book, isbn);
        }
    }

    private void parseMainCovers(@NonNull final Context context,
                                 @NonNull final Document document,
                                 @NonNull final boolean[] fetchCovers,
                                 @NonNull final Book book)
            throws StorageException {

        final String isbn = book.getIsbn();

        if (fetchCovers[0]) {
            final Element a = document.selectFirst("div.bandeau-principal > div.bandeau-image > a");
            if (a != null) {
                final String url = a.attr("href");
                fetchCover(context, url, 0, isbn, book);
            }
        }

        if (isCancelled()) {
            return;
        }

        // bandeau-vignette contains a list, each "li" contains at least one "a",
        // Contains "extra-images" + "back-cover"
        final List<String> coverUrls = document
                .select("div.bandeau-vignette a")
                .stream()
                .map(e -> e.attr("href"))
                .collect(Collectors.toList());
        handleExtraImagesAndBackCover(context, coverUrls, fetchCovers, book, isbn);
    }

    /**
     * Fetch the extras + back-cover.
     *
     * @param context     Current context
     * @param coverUrls   containing "extra-images" + "back-cover"
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     * @param isbn        of the book
     *
     * @throws StorageException on storage related failures
     */
    private void handleExtraImagesAndBackCover(@NonNull final Context context,
                                               @NonNull final List<String> coverUrls,
                                               @NonNull final boolean[] fetchCovers,
                                               @NonNull final Book book,
                                               @NonNull final String isbn)
            throws StorageException {
        // sanity check
        if (coverUrls.isEmpty()) {
            return;
        }

        if (fetchCovers[1]) {
            // The last one is the back-cover.
            // If a book has extra images, but NO back-cover... we pick the wrong one here.
            fetchCover(context, coverUrls.get(coverUrls.size() - 1), 1, isbn, book);
        }

        if (isCancelled()) {
            return;
        }

        // Are there extra images?
        if (coverUrls.size() > 1) {
            // Drop the last/back-cover
            final List<String> extraImageUrls = coverUrls.subList(0, coverUrls.size() - 1);

            final int maxCovers = Math.min(extraImageUrls.size(), DBKey.NR_OF_BOOK_COVERS - 2);
            for (int cIdx = 0; cIdx < maxCovers; cIdx++) {
                if (fetchCovers[cIdx + 1]) {
                    fetchCover(context, extraImageUrls.get(cIdx), cIdx + 2, isbn, book);
                }
            }
        }
    }

    private void fetchCover(@NonNull final Context context,
                            @Nullable final String url,
                            @IntRange(from = 0, to = 3) final int cIdx,
                            @NonNull final String isbn,
                            @NonNull final Book book)
            throws StorageException {
        if (url != null && !url.isBlank()) {
            saveImage(context, url, null, isbn, cIdx, null).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, cIdx, fileSpec));
        }
    }

    private boolean matches(@NonNull final Element section,
                            @NonNull final ISBN searchedIsbn) {
        final Element isbnLabel = section.selectFirst("li > label:contains(EAN/ISBN :)");
        if (isbnLabel != null) {
            final String isbnStr = parseLabelText(isbnLabel);
            if (isbnStr != null) {
                final ISBN isbnFound = new ISBN(isbnStr, true);
                return isbnFound.equals(searchedIsbn);
            }
        }
        return false;
    }

    private void parseLabels(@NonNull final Context context,
                             @NonNull final Book book,
                             @NonNull final Element section) {
        int lastAuthorRole = -1;

        String currentFormat = null;

        final Elements labels = section.select("li > label");
        for (final Element labelElement : labels) {
            final String label = labelElement.text();
            // check for multiple author entries of the same role
            if (label.isBlank() && lastAuthorRole != -1) {
                final Element a = labelElement.nextElementSibling();
                if (a != null) {
                    parseAuthor(context, a, lastAuthorRole, book);
                }
                // skip to next label
                continue;
            }
            lastAuthorRole = -1;

            //noinspection SwitchStatementWithoutDefaultBranch
            switch (label) {
                case "Série :": {
                    // We had to parse the Series title earlier from the main page
                    // as in this labelled-section, the series has NO link
                    break;
                }
                case "Titre :": {
                    final Node textNode = labelElement.nextSibling();
                    if (textNode != null) {
                        book.setTitle(textNode.toString().strip());
                    }
                    break;
                }
                case "Tome :": {
                    //FIXME: some books (non-french only?) have two numbers
                    // which the site concatenates.
                    // e.g. the series "Lucky Luke (en anglais)":
                    // https://www.bedetheque.com/BD-Lucky-Luke-en-anglais-Tome-148-Dick-Digger-s-Gold-Mine-227463.html
                    // have BOTH "1" and "48" ... and we end up with "148"
                    // The "1" is the number in the original series.
                    // The "48" is the number of the actual book in this specific series.
                    // i.o.w. this specific series published the books in a new/different order.
                    // This is clearly a bug on the site... not much we can do about that.
                    // The only solution... never parse the mainSection,
                    // but always parse the edition-section...  to be decided later...
                    final Node textNode = labelElement.nextSibling();
                    final List<Series> seriesList = book.getSeries();
                    if (textNode != null && !seriesList.isEmpty()) {
                        seriesList.get(seriesList.size() - 1)
                                  .setNumber(textNode.toString().strip());
                    }
                    break;
                }
                case "Identifiant :": {
                    final Node textNode = labelElement.nextSibling();
                    if (textNode != null) {
                        book.setIdentifierValue(Identifier.SID_BEDETHEQUE,
                                                textNode.toString().strip());
                    }
                    break;
                }

                case "Scénario :":
                case "Adapté de :": {
                    final Element a = labelElement.nextElementSibling();
                    if (a != null) {
                        lastAuthorRole = AuthorRole.WRITER;
                        parseAuthor(context, a, AuthorRole.WRITER, book);
                    }
                    break;
                }
                case "Dessin :": {
                    final Element a = labelElement.nextElementSibling();
                    if (a != null) {
                        lastAuthorRole = AuthorRole.ARTIST;
                        parseAuthor(context, a, AuthorRole.ARTIST, book);
                    }
                    break;
                }
                case "Encrage :": {
                    final Element a = labelElement.nextElementSibling();
                    if (a != null) {
                        lastAuthorRole = AuthorRole.INKING;
                        parseAuthor(context, a, AuthorRole.INKING, book);
                    }
                    break;
                }
                case "Couleurs :": {
                    final Element a = labelElement.nextElementSibling();
                    if (a != null) {
                        lastAuthorRole = AuthorRole.COLORIST;

                        final String colorOrColorist = a.text();
                        if (AUTHOR_NAME_COLOR.contains(colorOrColorist)) {
                            // REMOVE the "<>" as we really don't want fake HTML tags
                            book.setColor(
                                    colorOrColorist.substring(1, colorOrColorist.length() - 1));
                        } else {
                            // it's a real name
                            parseAuthor(context, a, AuthorRole.COLORIST, book);
                        }
                    }
                    break;
                }
                case "Couverture :": {
                    final Element a = labelElement.nextElementSibling();
                    if (a != null) {
                        lastAuthorRole = AuthorRole.COVER_ARTIST;
                        parseAuthor(context, a, AuthorRole.COVER_ARTIST, book);
                    }
                    break;
                }
                case "Préface :": {
                    final Element a = labelElement.nextElementSibling();
                    if (a != null) {
                        lastAuthorRole = AuthorRole.FOREWORD;
                        parseAuthor(context, a, AuthorRole.FOREWORD, book);
                    }
                    break;
                }
                case "Traduction :": {
                    final Element a = labelElement.nextElementSibling();
                    if (a != null) {
                        lastAuthorRole = AuthorRole.TRANSLATOR;
                        parseAuthor(context, a, AuthorRole.TRANSLATOR, book);
                    }
                    break;
                }
                case "Autres :": {
                    final Element a = labelElement.nextElementSibling();
                    if (a != null) {
                        lastAuthorRole = AuthorRole.CONTRIBUTOR;
                        parseAuthor(context, a, AuthorRole.CONTRIBUTOR, book);
                    }
                    break;
                }

                case "Dépot légal :": {
                    final Node textNode = labelElement.nextSibling();
                    if (textNode != null) {
                        String date = textNode.toString().strip();
                        if (!date.isBlank()) {
                            if (PUB_DATE.matcher(date).matches()) {
                                // Flip to "YYYY-MM" (or use as-is)
                                date = date.substring(3) + "-" + date.substring(0, 2);
                                book.setPublicationDate(date);
                            } else {
                                // we should never get here unless the site changes
                                dateParser.parse(date).ifPresent(book::setPublicationDate);
                            }
                        }

                    }
                    break;
                }
                case "Editeur :": {
                    String text = parseLabelText(labelElement);
                    if (text != null) {
                        text = cleanName(text);
                        if (!text.isBlank()) {
                            book.add(Publisher.from(text));
                        }
                    }
                    break;
                }
                case "Format :": {
                    final Node textNode = labelElement.nextSibling();
                    if (textNode != null) {
                        currentFormat = textNode.toString().strip();
                        mapFormat(context, currentFormat, false, book);
                    }
                    break;
                }
                case "EAN/ISBN :": {
                    final String text = parseLabelText(labelElement);
                    if (text != null) {
                        book.setIsbn(ISBN.cleanText(text));
                    }
                    break;
                }
                case "Planches :": {
                    final String text = parseLabelText(labelElement);
                    if (text != null) {
                        book.setPages(text);
                    }
                    break;
                }
                case "Autres info :": {
                    if (labelElement.nextElementSiblings()
                                    .stream()
                                    .map(sib -> sib.attr("title"))
                                    .anyMatch(FORMAT_COUVERTURE_SOUPLE::equals)) {
                        // Sanity check, it should never be null at this point.
                        if (currentFormat == null) {
                            currentFormat = FORMAT_COUVERTURE_SOUPLE;
                        }
                        mapFormat(context, currentFormat, true, book);
                    }
                }

                // Collection : publisher collection
            }
        }
    }

    @Nullable
    private String parseLabelText(@NonNull final Element label) {
        // the main section has a span
        final Element span = label.nextElementSibling();
        if (span != null) {
            return span.text();
        }

        // a later edition is a plain text
        final Node node = label.nextSibling();
        if (node != null) {
            return node.toString().strip();
        }

        return null;
    }

    /**
     * Parse an Author. Handles Bedetheque specific hardcoded pseudo-names.
     *
     * @param context Current context
     * @param a       link element to parse
     * @param role    of the Author
     * @param book    Bundle to update
     */
    private void parseAuthor(@NonNull final Context context,
                             @NonNull final Element a,
                             @AuthorRole.Role final int role,
                             @NonNull final Book book) {

        final String url = a.attr("href");
        final Matcher matcher = AUTHOR_ID.matcher(url);
        final String sid = matcher.find() ? matcher.group(1) : null;

        // REMOVE potential "<>" as we really don't want fake HTML tags
        String names = a.text();
        if (names.startsWith("<")) {
            names = names.substring(1);
        }
        if (names.endsWith(">")) {
            names = names.substring(0, names.length() - 1);
        }

        // Colours - is handled by the "Couleurs" label.
        //"<N&B>", "<Monochromie>", "<Bichromie>", "<Trichromie>", "<Quadrichromie>"
        // scenario author for an art-book; ignore
        // "<Art Book>"
        // Used for books; The authors for "dessin" and "couleurs"; ignore
        // "<Texte non illustré>"
        switch (names) {
            case "Indéterminé": {
                final Author author = Author.createUnknownAuthor(context);
                addAuthor(author, role, book);
                break;
            }
            case "Anonyme": {
                final Author author = new Author(context.getString(R.string.anonymous_author), "");
                addAuthor(author, role, book);
                break;
            }
            case "Art Book":
            case "Texte non illustré": {
                // ignore these
                return;
            }
            case "Collectif":
            default: {
                final String s = cleanName(names);
                if (!s.isBlank()) {
                    final Author author = Author.from(s);
                    if (sid != null) {
                        author.setIdentifierValue(Identifier.SID_BEDETHEQUE, sid);
                    }
                    addAuthor(author, role, book);
                }
                break;
            }
        }
    }

    private void parseSeries(@NonNull final Element a,
                             @NonNull final Book book) {
        final String url = a.attr("href");
        final Matcher matcher = SERIES_ID.matcher(url);
        final String sid = matcher.find() ? matcher.group(1) : null;

        final String title = a.text();
        if (!title.isEmpty()) {
            final Series series = parseSeries(title, book);
            if (sid != null) {
                series.setIdentifierValue(Identifier.SID_BEDETHEQUE, sid);
            }
            book.add(series);
        }
    }

    /**
     * Parse the text from a series field.
     * If it contains a language part, that language is is set on the given book.
     * The text itself and simple prefixes are cleaned.
     * <p>
     * Dev note: this method only exists to ease testing. It should
     * only be interpreted as used by {@link #parseSeries(Element, Book)}.
     *
     * @param text to parse
     * @param book for adding the potential language to
     *
     * @return a new Series instance, <strong>NOT added to the book</strong>
     */
    @VisibleForTesting
    @NonNull
    Series parseSeries(@NonNull final String text,
                       @NonNull final Book book) {
        // Series names can be formatted in a LOT of ways.
        // We're not going to try and capture each and every special format
        // but stick to the most common ones.
        String seriesName = cleanName(text);

        Matcher matcher;

        // Try extracting a language
        matcher = SERIES_WITH_LANGUAGE.matcher(seriesName);
        if (matcher.find()) {
            String maybeLanguage = matcher.group(2);
            if (maybeLanguage != null) {
                final int space = maybeLanguage.indexOf(' ');
                if (space > 1) {
                    // Lucky Luke Classics (en espagnol - Ediciones Kraken)
                    maybeLanguage = maybeLanguage.substring(0, space);
                } else {
                    // The brackets part was a 'pure' language; strip it
                    final String n = matcher.group(1);
                    if (n != null) {
                        seriesName = n;
                    }
                }
                book.setLanguage(maybeLanguage);
            }
        }

        // Find/move a simple "Le|La/Les|L'" prefix
        matcher = SERIES_WITH_SIMPLE_PREFIX.matcher(seriesName);
        if (matcher.find()) {
            final String n = matcher.group(1);
            final String prefix = matcher.group(2);
            if (n != null && prefix != null) {
                if (prefix.endsWith("'")) {
                    seriesName = prefix + n;
                } else {
                    seriesName = prefix + ' ' + n;
                }
            }
        }

        // plain constructor, no extra parsing
        return new Series(seriesName);
    }

    /**
     * Map Bedetheque specific formats to our generalised ones if allowed.
     *
     * @param context       Current context
     * @param currentFormat original french format string
     * @param softcover     {@code true} if the books is a softcover, {@code false} for hardcover
     * @param book          Bundle to update
     */
    private void mapFormat(@NonNull final Context context,
                           @NonNull final String currentFormat,
                           final boolean softcover,
                           @NonNull final Book book) {
        if (ServiceLocator.getInstance().getSharedPreferences()
                          .getBoolean(PK_BEDETHEQUE_PRESERVE_FORMAT_NAMES, false)) {
            book.setFormat(currentFormat + (softcover ? "; " + FORMAT_COUVERTURE_SOUPLE : ""));
            return;
        }

        final String format;
        switch (currentFormat) {
            case FORMAT_COUVERTURE_SOUPLE:
                format = context.getString(R.string.book_format_softcover);
                break;

            case "Format normal":
            case "Grand format":
                format = context.getString(softcover ? R.string.book_format_softcover
                                                     : R.string.book_format_hardcover);
                break;

            case "A l'italienne":
                format = context.getString(softcover ? R.string.book_format_softcover_oblong
                                                     : R.string.book_format_hardcover_oblong);
                break;

            case "Format comics":
                format = context.getString(softcover ? R.string.book_format_comic
                                                     : R.string.book_format_hardcover);
                break;

            case "Format manga":
            case "Format poche":
                format = context.getString(softcover ? R.string.book_format_paperback
                                                     : R.string.book_format_hardcover);
                break;

            case "Autre format":
                format = context.getString(R.string.book_format_other);
                break;

            default:
                // fallback
                format = currentFormat;
                break;
        }

        book.setFormat(format);
    }

}
