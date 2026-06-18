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
package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.covers.ImageWebSize;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionIsbn;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteAuthModule;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.AuthorRoleMapper;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

import okhttp3.Request;

/**
 * <a href="https://openlibrary.org/dev/docs/api/search">Open Library Search API</a>.
 * <p>
 * 2024-12-02: fetching covers using the
 * "covers": [
 * 5546156
 * ],
 * section is hit-and-miss. Due to the servers multiple redirect
 * we sometimes get a cover and sometimes not. We see error 500, sometimes 403.
 * <p>
 * Leaving as-is for now. Based on experience, and now this one...
 * OpenLibrary does not seem to be the most stable server ...
 */
public class OpenLibrarySearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText,
                   SearchEngine.ByExternalId,
                   SearchEngine.CoverByEdition,
                   SearchEngine.Login,
                   SearchEngine.AlternativeEditions<AltEditionOpenLibrary> {

    private static final String SITE_URL = "https://openlibrary.org";
    private static final String BOOK_URL = "https://openlibrary.org/books/%s";
    static final String AUTHOR_URL = "https://openlibrary.org/authors/%s";
    private static final String SERIES_URL = "https://openlibrary.org/series/%s";

    private static final String PREFERENCE_KEY = "openlibrary";

    static final String PK_LOGIN_TO_SEARCH = PREFERENCE_KEY
                                             + SiteAuthModule.PK_SUFFIX_LOGIN_TO_SEARCH;
    private static final String BASE_BOOK_URL = "/search.json?"
                                                + "q=%1$s"
                                                + "&fields=key,editions";

    private static final String SEARCH_BY_EXTERNAL_ID = "/books/%1$s.json";

    /**
     * The covers are available in 3 sizes.
     * <p>
     * S: Small, suitable for use as a thumbnail on a results page on Open Library,
     * M: Medium, suitable for display on a details page on Open Library and,
     * L: Large
     * The URL pattern to access book covers is:
     * <p>
     * {@code http://covers.openlibrary.org/b/$key/$value-$size.jpg}
     * <p>
     * Where:
     * <p>
     * param 1: {@link #COVER_KEY_BOOK} for books, or {@link #COVER_KEY_AUTHOR} for authors
     * param 2: key can be any one of ISBN, OCLC, LCCN, OLID and ID (case-insensitive)
     * param 3: value of the chosen key
     * param 4: one of S, M and L for small, medium and large respectively.
     * <p>
     * When there is no cover, the server returns a blank image by default.
     * Adding "?default=false": forces a 404 to be returned
     */
    private static final String COVER_BY_KEY =
            "https://covers.openlibrary.org/%1$s/%2$s/%3$s-%4$s.jpg?default=false";

    private static final char COVER_KEY_AUTHOR = 'a';
    private static final char COVER_KEY_BOOK = 'b';

    /**
     * <a href="https://openlibrary.org/dev/docs/api/covers">Covers API</a>
     * The cover access by ids other than CoverID and OLID are rate-limited.
     * Currently only 100 requests/IP are allowed for every 5 minutes.
     * If any IP tries to access more that the allowed limit,
     * the service will return "403 Forbidden" status.
     */
    private static final int COVER_BY_ISBN_REQUEST_DELAY = 3_000;

    /**
     * Key's that map 1:1 are not listed.
     * This list only maps <strong>known</strong> keys
     * from the predefined list at app install time.
     * <p>
     * Other keys we've seen now and then:
     * "better_world_books"
     * "paperback_swap"
     * "alibris_id"
     * "depu00f3sito_legal"
     * "bookbrainz"
     * ...
     */
    private static final Map<String, String> IDENTIFIER_MAPPING = Map.ofEntries(
            Map.entry("amazon", Identifier.SID_ASIN),
            Map.entry("amazon.co.uk_asin", Identifier.SID_ASIN),
            Map.entry("oclc_numbers", Identifier.SID_OCLC)
    );
    private static final String TYPE_TEXT = "/type/text";

    private final AuthorRoleMapper authorRoleMapper = new AuthorRoleMapper();
    private final DateParser<PartialDate> dateParser = new PartialDateParser();
    @Nullable
    private FutureHttp<String> httpGet;
    @Nullable
    private SiteAuthModule siteAuthModule;
    @Nullable
    private AuthorParser authorParser;

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
    public OpenLibrarySearchEngine(@NonNull final Context appContext,
                                   @NonNull final SearchEngineConfig config) {
        super(appContext, config);
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
                                    R.string.site_open_library,
                                    List.of(R.string.site_description_english_and_more,
                                            R.string.site_description_catalog),
                                    "https://openlibrary.org",
                                    Locale.US)
                .setIdentifierKeys(Identifier.SID_OPEN_LIBRARY)
                .setMultipleCoverSizes(true)
                .setPreferenceFragmentClazz(OpenLibraryPreferencesFragment.class)
                .setAuthorResolverSupplier(OpenLibraryAuthorResolver::create);
    }

    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_open_library);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Text,
                               Identifier.SID_OPEN_LIBRARY,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               "P648"),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Text,
                               Identifier.SID_OPEN_LIBRARY,
                               name,
                               SITE_URL,
                               AUTHOR_URL,
                               "P648"),
                new Identifier(Identifier.EntityType.Series,
                               Identifier.Type.Text,
                               Identifier.SID_OPEN_LIBRARY,
                               name,
                               SITE_URL,
                               SERIES_URL,
                               null)
        );
    }

    @Override
    public boolean isLoginToSearch(@NonNull final Context context) {
        if (BuildConfig.ENABLE_OPEN_LIBRARY_LOGIN) {
            return ServiceLocator.getInstance().getSharedPreferences()
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
            siteAuthModule = new OpenLibraryAuth();
            try {
                siteAuthModule.login(context);
            } catch (@NonNull final IOException | StorageException e) {
                siteAuthModule = null;
                throw new SearchException(getEngineId(), e);
            }
        }
    }

    @Override
    @AnyThread
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (httpGet != null) {
                httpGet.cancel();
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

        final String url = getHostUrl() + String.format(SEARCH_BY_EXTERNAL_ID, externalId);
        try {
            final String response = loadDocument(context, url);
            parse(context, new JSONObject(response), fetchCovers, book);

        } catch (@NonNull final IOException | JSONException e) {
            throw new SearchException(getEngineId(), e);
        }

        Series.checkForSeriesNameInTitle(book);
        return book;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final ISBN isbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final String validIsbn = SearchEngineUtils.formatIsbn(getEngineId(), isbn);

        final Book book = new Book();

        final String url = getHostUrl() + String.format(BASE_BOOK_URL, validIsbn);

        fetchBook(context, url, fetchCovers, book);
        return book;
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria,
                       @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner words = criteria.concatTextCriteria(" ");

        final ISBN isbn = criteria.getIsbn();
        if (isbn != null) {
            final String code = SearchEngineUtils.formatIsbn(getEngineId(), isbn);
            if (!code.isEmpty()) {
                words.add(code);
            }
        }

        final Book book = new Book();

        // Sanity check
        if (words.length() == 0) {
            return book;
        }

        // Limit the result to a single book for performance.
        final String url = getHostUrl() + String.format(BASE_BOOK_URL, words) + "&limit=1";

        fetchBook(context, url, fetchCovers, book);
        return book;
    }

    @NonNull
    private String loadDocument(@NonNull final Context context,
                                @NonNull final String url)
            throws StorageException, SearchException {
        httpGet = createGetDocumentRequest(context);
        try {
            return httpGet.getAsString(url, (con, s) -> s);
        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        } finally {
            httpGet = null;
        }
    }

    /**
     * Fetch and parse.
     *
     * @param context     Current context
     * @param url         to fetch
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     *
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws CredentialsException on authentication/login failures
     */
    private void fetchBook(@NonNull final Context context,
                           @NonNull final String url,
                           @NonNull final boolean[] fetchCovers,
                           @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        String response = loadDocument(context, url);

        try {
            final JSONObject jsonObject = new JSONObject(response);
            int numFound = jsonObject.optInt("numFound");
            if (numFound < 1) {
                return;
            }

            // https://openlibrary.org/search.json?q=9780980200447&fields=key,editions
            // {
            //    "numFound": 1,
            //    "start": 0,
            //    "numFoundExact": true,
            //    "docs": [
            //        {
            //            "key": "/works/OL13694821W",
            //            "editions": {
            //                "numFound": 1,
            //                "start": 0,
            //                "numFoundExact": true,
            //                "docs": [
            //                    {
            //                        "key": "/books/OL22853304M"
            //                    }
            //                ]
            //            }
            //        }
            //    ],
            //    "num_found": 1,
            //    "q": "9780980200447",
            //    "offset": null
            //}
            final JSONObject editions = jsonObject.getJSONArray("docs")
                                                  .getJSONObject(0)
                                                  .getJSONObject("editions");
            numFound = editions.optInt("numFound");
            if (numFound < 1) {
                return;
            }

            final String key = editions.getJSONArray("docs")
                                       .getJSONObject(0)
                                       .getString("key");

            // "/books/OL22853304M.json"
            final String editionUrl = getHostUrl() + key + ".json";
            response = loadDocument(context, editionUrl);

            parse(context, new JSONObject(response), fetchCovers, book);

        } catch (@NonNull final JSONException | IOException e) {
            throw new SearchException(getEngineId(), e);
        }

        Series.checkForSeriesNameInTitle(book);
    }

    /**
     * Parse the results, and build the book.
     *
     * <pre>
     *     https://openlibrary.org/books/OL22853304M.json
     *
     *     {
     *     "number_of_pages": 92,
     *     "table_of_contents": [
     *         {
     *             "level": 0,
     *             "label": "",
     *             "title": "The personal nature of slow reading",
     *             "pagenum": ""
     *         },
     *         {
     *             "level": 0,
     *             "label": "",
     *             "title": "Slow reading in an information ecology",
     *             "pagenum": ""
     *         },
     *         {
     *             "level": 0,
     *             "label": "",
     *             "title": "The slow movement and slow reading",
     *             "pagenum": ""
     *         },
     *         {
     *             "level": 0,
     *             "label": "",
     *             "title": "The psychology of slow reading",
     *             "pagenum": ""
     *         },
     *         {
     *             "level": 0,
     *             "label": "",
     *             "title": "The practice of slow reading.",
     *             "pagenum": ""
     *         }
     *     ],
     *     "contributors": [
     *         {
     *             "role": "Cover Photographs",
     *             "name": "C. Ekholm"
     *         }
     *     ],
     *     "isbn_10": [
     *         "1936117363"
     *     ],
     *     "covers": [
     *         5546156
     *     ],
     *     "lc_classifications": [
     *         "Z1003 .M58 2009"
     *     ],
     *     "ocaid": "slowreading00mied",
     *     "weight": "1 grams",
     *     "source_records": [
     *         "marc:marc_loc_updates/v37.i01.records.utf8:4714764:907",
     *         "marc:marc_loc_updates/v37.i24.records.utf8:7913973:914",
     *         "marc:marc_loc_updates/v37.i30.records.utf8:11406606:914",
     *         "ia:slowreading00mied",
     *         "marc:marc_openlibraries_sanfranciscopubliclibrary/sfpl_chq_2018_12_24_run04.mrc:135742902:2094",
     *         "marc:marc_loc_2016/BooksAll.2016.part35.utf8:160727336:914",
     *         "promise:bwb_daily_pallets_2022-09-12",
     *         "marc:harvard_bibliographic_metadata/ab.bib.11.20150123.full.mrc:833417229:1085"
     *     ],
     *     "title": "Slow reading",
     *     "languages": [
     *         {
     *             "key": "/languages/eng"
     *         }
     *     ],
     *     "subjects": [
     *         "Books and reading",
     *         "Reading"
     *     ],
     *     "publish_country": "mnu",
     *     "by_statement": "by John Miedema.",
     *     "oclc_numbers": [
     *         "297222669"
     *     ],
     *     "type": {
     *         "key": "/type/edition"
     *     },
     *     "physical_dimensions": "7.81 x 5.06 x 1 inches",
     *     "publishers": [
     *         "Litwin Books"
     *     ],
     *     "description": "\"A study of voluntary slow reading from diverse angles....",
     *     "physical_format": "Paperback",
     *     "key": "/books/OL22853304M",
     *     "authors": [
     *         {
     *             "key": "/authors/OL6548935A"
     *         }
     *     ],
     *     "publish_places": [
     *         "Duluth, Minn"
     *     ],
     *     "pagination": "80p.",
     *     "classifications": {},
     *     "lccn": [
     *         "2008054742"
     *     ],
     *     "notes": "Includes bibliographical references and index.",
     *     "identifiers": {
     *         "amazon": [
     *             "098020044X"
     *         ],
     *         "google": [
     *             "4LQU1YwhY6kC"
     *         ],
     *         "librarything": [
     *             "8071257"
     *         ],
     *         "goodreads": [
     *             "6383507"
     *         ]
     *     },
     *     "isbn_13": [
     *         "9780980200447",
     *         "9781936117369"
     *     ],
     *     "dewey_decimal_class": [
     *         "028/.9"
     *     ],
     *     "local_id": [
     *         "urn:sfpl:31223095026424",
     *         "urn:bwbsku:O8-CNK-818"
     *     ],
     *     "publish_date": "March 2009",
     *     "works": [
     *         {
     *             "key": "/works/OL13694821W"
     *         }
     *     ],
     *     "latest_revision": 25,
     *     "revision": 25,
     *     "created": {
     *         "type": "/type/datetime",
     *         "value": "2009-01-07T22:16:11.381678"
     *     },
     *     "last_modified": {
     *         "type": "/type/datetime",
     *         "value": "2023-11-30T11:54:53.617849"
     *     }
     * }
     *
     * </pre>
     *
     * @param context     Current context
     * @param document    JSON result data for the "/books/*.json" url
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws IOException          when fetching the Author details fails
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    private void parse(@NonNull final Context context,
                       @NonNull final JSONObject document,
                       @NonNull final boolean[] fetchCovers,
                       @NonNull final Book book)
            throws StorageException, IOException, SearchException, CredentialsException {

        // 2025-06: the site has started to remove several data items from the "book.json"
        // result. It seems they now expect us to ALWAYS fetch the "work.json" as well.

        JSONObject workDocument = null;
        // "works": [{"key": "/works/OL25312237W"}],
        final JSONArray works = document.optJSONArray("works");
        if (works != null && !works.isEmpty()) {
            final String work = works.getJSONObject(0).optString("key");
            if (!work.isEmpty()) {
                final String editionUrl = getHostUrl() + work + ".json";
                final String workResponse = loadDocument(context, editionUrl);
                workDocument = new JSONObject(workResponse);
            }
        }

        parse(context, document, workDocument, fetchCovers, book);
    }

    @VisibleForTesting
    void parse(@NonNull final Context context,
               @NonNull final JSONObject document,
               @Nullable final JSONObject workDocument,
               @NonNull final boolean[] fetchCovers,
               @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // ALWAYS FIRST parse the work; it typically contains more detailed information.
        if (workDocument != null) {
            parseWork(context, workDocument, book);
        }

        JSONArray a;
        String s;
        final int i;

        // "/books/OL22853304M"
        s = document.optString("key", null);
        if (s != null && !s.isEmpty()) {
            if (s.startsWith("/books/")) {
                final String sid = s.substring("/books/".length());
                book.setIdentifierValue(Identifier.SID_OPEN_LIBRARY, sid);
            }
        }

        s = document.optString("title", null);
        if (s != null && !s.isEmpty()) {
            book.setTitle(s);
        }

        // ENHANCE: add a preference switch to optionally fetch the subtitle
        //  and concatenate it with the title
        // s = document.optString("subtitle");

        // Reparse for fields which should have been in the "work"
        // This is OL... better parse twice to be sure
        // Some of the parsing WILL fail - that's expected/as-designed
        // Maybe even all of it... TODO: review parseBookOrWork now and then
        parseBookOrWork(context, document, book);

        // There is also a key "pagination" which for example
        // contains ""xxii, 781p."
        // We're ignoring that one...
        i = document.optInt("number_of_pages");
        if (i > 0) {
            book.setPages(i);
        }

        // TODO: There is another field "physical_dimensions".
        //  Maybe use that if the format is not present?
        s = document.optString("physical_format", null);
        if (s != null && !s.isEmpty()) {
            book.setFormat(s);
        }

        a = document.optJSONArray("languages");
        if (a != null && !a.isEmpty()) {
            parseLanguages(a, book);
        }

        parseIsbn(document, book);

        parseIdentifiers(document, book);

        // ONLY try to get series from the book data if we did not find any on the work data.
        if (book.getSeries().isEmpty()) {
            a = document.optJSONArray("series");
            if (a != null && !a.isEmpty()) {
                parseSeriesFromBook(a, book);
            }
        }

        a = document.optJSONArray("publishers");
        if (a != null && !a.isEmpty()) {
            parsePublishers(a, book);
        }

        parsePublicationDate(document, book);
        parseFirstPublicationDate(document, book);

        a = document.optJSONArray("table_of_contents");
        if (a != null && !a.isEmpty()) {
            parseToc(context, a, book);
        }

        // We would normally call
        //   AuthorResolverFactory.resolve(context, this, book);
        // at this point, but we already had to resolve the "authors" in #parseAuthors

        if (isCancelled()) {
            return;
        }

        // "covers": [
        //       5546156
        // ]
        a = document.optJSONArray("covers");
        if (a != null && !a.isEmpty()) {
            fetchCoverByCoverId(context, a, fetchCovers, book);
        }
    }

    /**
     * Parse "work" specific fields.
     *
     * <pre>{@code
     * {
     *   "title": "Control Your Mind and Master Your Feelings",
     *   "authors": [
     *     {
     *       "author": {
     *         "key": "/authors/OL14948835A"
     *       },
     *       "type": {
     *         "key": "/type/author_role"
     *       }
     *     }
     *   ],
     *   "key": "/works/OL25312237W",
     *   "type": {
     *     "key": "/type/work"
     *   },
     *   "covers": [
     *     12009823
     *   ],
     *   "subjects": [
     *     "Self-help",
     *     "personal development",
     *     "emotional intelligence",
     *     "mindfulness",
     *     "meditation",
     *     "cognitive behavioral therapy",
     *     "CBT",
     *     "anxiety",
     *     "depression",
     *     "anger management",
     *     "stress management",
     *     "positive thinking",
     *     "self-control",
     *     "mental health",
     *     "emotional well-being"
     *   ],
     *   "description": {
     *     "type": "/type/text",
     *     "value": "We oftentimes look towards the outside world to find the roots of our problems. However, most of the times, we should be looking inwards. Our mind and our emotions determine our state of being in the present moment. If those aspects are left unchecked, we can get easily overwhelmed and are left feeling unfulfilled every single day. \r\n\r\nThis book contains two manuscripts designed to help you discover the best and most efficient way to control your thoughts and master your feelings.\r\n\r\nIn the first part of the bundle called Breaking Overthinking, you will discover:\r\n\r\nHow overthinking can be detrimental to your social life. \r\nThe hidden dangers of overthinking and what can happen to you if it’s left untreated. \r\nHow to declutter your mind from all the noise of the modern world. \r\nHow overthinking affects your body, your energy levels, and your everyday mood. \r\nHow your surroundings affect your state of mind, and what you NEED to do in order to break out of that state. \r\nBad habits we perform every day and don’t even realize are destroying our sanity (and how to overcome them properly). \r\nHow to cut out toxic people from your life, which cloud your judgment and make you feel miserable."
     *   },
     *   "latest_revision": 13,
     *   "revision": 13,
     *   "created": {
     *     "type": "/type/datetime",
     *     "value": "2021-09-27T18:56:20.746460"
     *   },
     *   "last_modified": {
     *     "type": "/type/datetime",
     *     "value": "2024-12-23T09:10:28.372342"
     *   }
     * }
     * }</pre>
     */
    private void parseWork(@NonNull final Context context,
                           @NonNull final JSONObject document,
                           @NonNull final Book book)
            throws SearchException, StorageException {

        parseBookOrWork(context, document, book);
    }

    /**
     * Shared parsing for "book" and "work".
     * <p>
     * We don't parse the "covers" here, only in the "book".
     *
     * @param context  Current context
     * @param work     document from the download
     * @param book     destination
     *
     * @throws StorageException on storage related failures
     * @throws SearchException  on generic exceptions (wrapped) during search
     */
    private void parseBookOrWork(@NonNull final Context context,
                                 @NonNull final JSONObject work,
                                 @NonNull final Book book)
            throws SearchException, StorageException {

        JSONArray a;
        String s;

        // "authors" contains structured Author data
        a = work.optJSONArray("authors");
        if (a != null && !a.isEmpty()) {
            parseAuthorsFromWork(context, a, book);
        }

        // "series" contains structured Series data
        a = work.optJSONArray("series");
        if (a != null && !a.isEmpty()) {
            parseSeriesFromWork(context, a, book);
        }

        // "by_statement" contains NON-structured author data:
        //     "by John Miedema."
        //     "Katja Diehl, mit zahlreichen Illustrationen von Doris Reich"
        //
        // We'll try and catch some of the inconsistencies, but can't catch them all,
        // so we leave those to the user.
        // Note we also will not try and resolve these names on purpose.
        s = work.optString("by_statement", null);
        if (s != null && !s.isEmpty()) {
            // drop trailing '.'
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
            // remove "by " from the start
            if (s.startsWith("by ") && s.length() > 3) {
                s = s.substring(3);
                addAuthor(Author.from(s), AuthorRole.UNKNOWN, book);

            } else if (s.contains(",")) {
                // only grab the part before a comma
                final String[] split = s.split(",");
                if (split.length > 0) {
                    addAuthor(Author.from(split[0]), AuthorRole.UNKNOWN, book);
                }
            }
        }

        a = work.optJSONArray("contributors");
        if (a != null && !a.isEmpty()) {
            parseContributorsFromWork(context, a, book);
        }

        parseDescriptionAndNotesFromWork(work, book);

        // ENHANCE: "subjects" could be used for tags...
        //  but the subject list for a single book can be very large
        //  and contain various entries of dubious quality.
        // I mean.. seriously, 47 tags ?
        // https://openlibrary.org/works/OL257943W.json
        //
        // There are also two formats:
        // "subjects": [
        //            {
        //                "name": "History",
        //                "url": "https://openlibrary.org/subjects/history"
        //            },
        //
        // Also seen in a different format:
        //  "subjects": [
        //    "Fiction - Espionage / Thriller",
        //    "Fiction",
        //    "Espionage/Intrigue",
        //    "Thrillers",
        //    "Fiction / Thrillers",
        //    "Action & Adventure"
        //  ]
    }

    /**
     * A single Author element.
     * <p>
     * === "book" ===
     * <pre>
     *     {
     *   "name": "John Miedema",
     *   "links": [
     *     {
     *       "url": "http://johnmiedema.ca",
     *       "type": {
     *         "key": "/type/link"
     *       },
     *       "title": "Author's blog"
     *     }
     *   ],
     *   "personal_name": "John Miedema",
     *   "created": {
     *     "type": "/type/datetime",
     *     "value": "2009-01-07T22:16:11.381678"
     *   },
     *   "last_modified": {
     *     "type": "/type/datetime",
     *     "value": "2010-03-21T02:34:14.507387"
     *   },
     *   "latest_revision": 2,
     *   "key": "/authors/OL6548935A",
     *   "type": {
     *     "key": "/type/author"
     *   },
     *   "id": 33494095,
     *   "revision": 2
     * }
     * </pre>
     * <p>
     * === "work" ===
     * <pre>{@code
     * }</pre>
     * {
     * "author": {
     * "key": "/authors/OL14948835A"
     * },
     * "type": {
     * "key": "/type/author_role"
     * }
     * }
     * <p>
     * The authors parsed here will be fully resolved.
     *
     * @param context Current context
     * @param a       array with author elements
     * @param book    destination
     *
     * @throws StorageException on storage related failures
     * @throws SearchException  on generic exceptions (wrapped) during search
     */
    private void parseAuthorsFromWork(@NonNull final Context context,
                                      @NonNull final JSONArray a,
                                      @NonNull final Book book)
            throws StorageException, SearchException {

        JSONObject element;
        for (int ai = 0; ai < a.length(); ai++) {
            element = a.optJSONObject(ai);
            if (element != null) {
                // work:
                // {"author":{"key":"/authors/OL14948835A"}, "type":{"key":"/type/author_role"}}
                final JSONObject author = element.optJSONObject("author");
                if (author != null) {
                    final String key = author.optString("key", null);
                    fetchAndParseAuthor(context, key, book);
                } else {
                    // book:
                    // "key": "/authors/OL6548935A"
                    final String key = element.optString("key", null);
                    fetchAndParseAuthor(context, key, book);
                }
            }
        }
    }

    private void fetchAndParseAuthor(@NonNull final Context context,
                                     @Nullable final String key,
                                     @NonNull final Book book)
            throws StorageException, SearchException {
        if (key == null || key.isEmpty()) {
            return;
        }

        if (authorParser == null) {
            authorParser = new AuthorParser(context, this);
        }

        final String authorUrl = getHostUrl() + key + ".json";
        final String response = loadDocument(context, authorUrl);
        final JSONObject document = new JSONObject(response);
        final Author author = authorParser.parse(context, document);
        if (author != null) {
            addAuthor(author, AuthorRole.UNKNOWN, book);
        }
    }

    private void parseContributorsFromWork(@NonNull final Context context,
                                           @NonNull final JSONArray a,
                                           @NonNull final Book book) {
        for (int ai = 0; ai < a.length(); ai++) {
            final JSONObject c = a.optJSONObject(ai);
            if (c != null) {
                final String name = c.optString("name", null);
                if (name != null) {
                    final Author author = Author.from(name);
                    final int type;
                    final String role = c.optString("role", null);
                    if (role != null) {
                        type = authorRoleMapper.map(getLocale(context), role);
                    } else {
                        type = AuthorRole.UNKNOWN;
                    }
                    addAuthor(author, type, book);
                }
            }
        }
    }

    private void parseSeriesFromBook(@NonNull final JSONArray a,
                                     @NonNull final Book book) {
        String name;
        for (int ai = 0; ai < a.length(); ai++) {
            name = a.optString(ai, null);
            if (name != null && !name.isEmpty()) {
                book.add(Series.from(name));
            }
        }
    }

    /**
     * In the work data.
     *
     * <pre>
     * "series": [
     *     {
     *       "series": {
     *         "key": "/series/OL329813L"
     *       },
     *       "position": "1"
     *     }
     *   ]
     * </pre>
     *
     * @param context Current context
     * @param a       array with series elements
     * @param book    destination
     *
     * @throws StorageException on storage related failures
     * @throws SearchException  on generic exceptions (wrapped) during search
     */
    private void parseSeriesFromWork(@NonNull final Context context,
                                     @NonNull final JSONArray a,
                                     @NonNull final Book book)
            throws StorageException, SearchException {
        JSONObject element;
        for (int ai = 0; ai < a.length(); ai++) {
            element = a.optJSONObject(ai);
            if (element != null) {
                final JSONObject series = element.optJSONObject("series");
                final int position = element.optInt("position", Integer.MIN_VALUE);
                if (series != null) {
                    final String nr = position == Integer.MIN_VALUE ? null
                                                                    : String.valueOf(position);
                    final String key = series.optString("key", null);
                    fetchAndParseSeries(context, key, nr, book);
                }
            }
        }
    }

    /**
     * Fetch the series json and parse it.
     *
     * <pre>
     * {
     *   "links": {
     *     "self": "/series/OL329813L",
     *     "seeds": "/series/OL329813L/seeds"
     *   },
     *   "name": "Foundation Trilogy",
     *   "type": {
     *     "key": "/series/OL329813L"
     *   },
     *   "description": null,
     *   "seed_count": 4,
     *   "meta": {
     *     "revision": 1,
     *     "created": "2026-03-27T20:02:04.462751",
     *     "last_modified": "2026-03-27T20:02:04.462751"
     *   }
     * }
     * </pre>
     *
     * @param context Current context
     * @param key     to fetch
     * @param nr      in the series; {@code null} for none
     * @param book    destination
     *
     * @throws StorageException on storage related failures
     * @throws SearchException  on generic exceptions (wrapped) during search
     */
    private void fetchAndParseSeries(@NonNull final Context context,
                                     @Nullable final String key,
                                     @Nullable final String nr,
                                     @NonNull final Book book)
            throws StorageException, SearchException {
        if (key == null || key.isEmpty()) {
            return;
        }

        final String url = getHostUrl() + key + ".json";
        final String response = loadDocument(context, url);
        final JSONObject document = new JSONObject(response);

        final String title = document.optString("name");
        if (title.isBlank()) {
            return;
        }
        final Series series = Series.from(title);
        series.setNumber(nr);

        // Paranoia...
        if (key.startsWith("/series/")) {
            final String sid = key.substring("/series/".length());
            series.setIdentifierValue(Identifier.SID_OPEN_LIBRARY, sid);
        }

        book.add(series);
    }

    private void parsePublishers(@NonNull final JSONArray a,
                                 @NonNull final Book book) {
        for (int ai = 0; ai < a.length(); ai++) {
            final String name = a.optString(ai);
            if (!name.isBlank()) {
                book.add(Publisher.from(name));
            }
        }
    }

    private void parsePublicationDate(@NonNull final JSONObject document,
                                      @NonNull final Book book) {
        final String s = document.optString("publish_date", null);
        if (s != null && !s.isEmpty()) {
            // The site serves dates in multiple formats...
            // "2013"
            // "1984-10"
            // "2022-02-09"
            // "March 2009"
            // "18 October 2006"
            // "May 1, 1983"
            // hope for the best by parsing
            dateParser.parse(s).ifPresent(book::setPublicationDate);
        }
    }

    private void parseFirstPublicationDate(@NonNull final JSONObject document,
                                           @NonNull final Book book) {
        String s;
        s = document.optString("first_publish_date", null);
        if (s != null && !s.isEmpty()) {
            dateParser.parse(s).ifPresent(book::setFirstPublicationDate);
            return;
        }

        //  "copyright_date": "1982, 1994",
        //  "copyright_date": "2022",
        s = document.optString("copyright_date", null);
        if (s != null && !s.isEmpty()) {
            // grab the first, we'll assume it will the earlier date.
            // Given OL track record of structure we'll probably be wrong sometimes
            final String[] split = s.split(",");
            if (split[0] == null || split[0].isBlank()) {
                return;
            }

            dateParser.parse(split[0]).ifPresent(book::setFirstPublicationDate);
        }
    }

    /**
     * "description" seems to be on the "work" only. Only one format (we hope...)
     * <p>
     * "notes" is a specific (set of) remarks on this particular edition of the book.
     * There are two known formats returned:
     * <pre>
     *      "notes": "Includes bibliographical references and index.",
     *      "notes": {"type": "/type/text", "value": "Mit zahlreichen farbigen Illustrationen"}
     * </pre>
     * <p>
     * If we previously parsed a description, the "notes" will be appended.
     *
     * @param document to parse
     * @param book     to update
     */
    private void parseDescriptionAndNotesFromWork(@NonNull final JSONObject document,
                                                  @NonNull final Book book) {
        JSONObject element;
        String s;

        element = document.optJSONObject("description");
        if (element != null) {
            // Sanity check, no idea if there are others types
            if (TYPE_TEXT.equals(element.optString("type"))) {
                s = element.optString("value", null);
                if (s != null && !s.isEmpty()) {
                    final String previous = book.getDescription();
                    if (!previous.isEmpty()) {
                        s = previous + "\n" + s;
                    }
                    book.setDescription(s);
                }
            }
        }

        element = document.optJSONObject("notes");
        if (element != null) {
            // Sanity check, no idea if there are others types
            if (TYPE_TEXT.equals(element.optString("type"))) {
                s = element.optString("value", null);
                if (s != null && !s.isEmpty()) {
                    final String previous = book.getDescription();
                    if (!previous.isEmpty()) {
                        s = previous + "\n" + s;
                    }
                    book.setDescription(s);
                }
            }
        } else {
            // Try the plain string format
            s = document.optString("notes", null);
            if (s != null && !s.isEmpty()) {
                final String previous = book.getDescription();
                if (!previous.isEmpty()) {
                    s = previous + "\n" + s;
                }
                book.setDescription(s);
            }
        }
    }

    private void parseLanguages(@NonNull final JSONArray a,
                                @NonNull final Book book) {
        final JSONObject element = a.optJSONObject(0);
        if (element != null) {
            final String s = element.optString("key", null);
            if (s != null && s.startsWith("/languages/")) {
                book.setLanguage(s.substring("/languages/".length()));
            }
        }
    }

    private void parseIsbn(@NonNull final JSONObject element,
                           @NonNull final Book book) {
        // get the 'longest' ISBN available
        JSONArray a = element.optJSONArray("isbn_13");
        if (a != null && !a.isEmpty()) {
            // Overwrite
            book.setIsbn(a.getString(0));
        } else {
            a = element.optJSONArray("isbn_10");
            if (a != null && !a.isEmpty()) {
                // Do NOT overwrite
                if (!book.hasIsbn()) {
                    book.setIsbn(a.getString(0));
                }
            }
        }
    }

    /**
     * <pre>
     *     {
     *   "oclc_numbers": [
     *     "297222669"
     *   ],
     *   "lccn": [
     *     "2008054742"
     *   ],
     *   "identifiers": {
     *     "goodreads": [
     *       "596906"
     *     ],
     *     "librarything": [
     *       "1044504"
     *     ],
     *     "wikidata": [
     *       "Q108810998"
     *     ]
     *   }
     * </pre>
     * <p>
     * The majority of the openlibrary provided doi numbers
     * have a prefix "10.1604" and will NOT resolve.
     * Example: 10.1604/9780910663519
     * See <a href="https://doi.org/10.1604">https://doi.org/10.1604</a>
     * --> Took away permissions 3/21/13... do not think this is really a bowker prefix.
     *
     * @param document to parse
     * @param book     to update
     */
    private void parseIdentifiers(@NonNull final JSONObject document,
                                  @NonNull final Book book) {

        // the SID_OPEN_LIBRARY should already be there, so get the current list!
        final List<Identifier.Value> ivs = book.getIdentifiers();

        // "identifiers" contains foreign-site codes (e.g. amazon ASIN)
        final JSONObject element = document.optJSONObject("identifiers");
        if (element != null) {
            element.keySet().stream()
                   .map(olKey -> parseIdentifier(element, olKey))
                   .flatMap(Optional::stream)
                   // HACK: the site has a lot of DOI values with prefix "10.1604"
                   // which is invalid/revoked. Filter/drop those
                   .filter(iv -> !(Identifier.SID_DOI.equals(iv.getKey())
                                   && iv.getSid().startsWith("10.1604")))
                   .forEach(ivs::add);
        }

        // lccn and oclc can also be found at the top-level...
        parseIdentifier(document, "lccn").ifPresent(ivs::add);
        parseIdentifier(document, "oclc_numbers").ifPresent(ivs::add);

        if (!ivs.isEmpty()) {
            book.setIdentifiers(ivs);
        }
    }

    @NonNull
    private Optional<Identifier.Value> parseIdentifier(@NonNull final JSONObject element,
                                                       @NonNull final String olKey) {
        final JSONArray data = element.optJSONArray(olKey);
        if (data != null && !data.isEmpty()) {
            // MUST be converted to lc before we try and map
            final String olKeyLc = olKey.toLowerCase(Locale.ENGLISH);
            // Map the olKeyLc to our key, or if not found, just use the olKeyLc itself
            final String key = Objects.requireNonNullElse(IDENTIFIER_MAPPING.get(olKeyLc), olKeyLc);
            // The site supports multiple identifier of the same type.
            // We just grab the first entry in their array.
            return Optional.of(new Identifier.Value(key, data.getString(0)));
        }
        return Optional.empty();
    }

    private void parseToc(@NonNull final Context context,
                          @NonNull final JSONArray a,
                          @NonNull final Book book) {
        JSONObject element;
        // always use the first author only for TOC entries.
        Author tocAuthor = book.getPrimaryAuthor();
        if (tocAuthor == null) {
            tocAuthor = Author.createUnknownAuthor(context);
        }

        final List<TocEntry> toc = new ArrayList<>();
        for (int ai = 0; ai < a.length(); ai++) {
            element = a.optJSONObject(ai);
            if (element != null) {
                final String title = element.optString("title", null);
                if (title != null && !title.isEmpty()) {
                    toc.add(new TocEntry(tocAuthor, title));
                }
            }
        }

        if (!toc.isEmpty()) {
            book.setToc(toc);
            if (toc.size() > 1) {
                book.setContentType(Book.ContentType.Collection);
            }
        }
    }

    private void fetchCoverByCoverId(@NonNull final Context context,
                                     @NonNull final JSONArray coverIds,
                                     @NonNull final boolean[] fetchCovers,
                                     @NonNull final Book book)
            throws StorageException {
        for (int cIdx = 0; cIdx < fetchCovers.length; cIdx++) {
            // Should we fetch && is there one to fetch?
            if (fetchCovers[cIdx] && coverIds.length() > cIdx) {
                final int coverId = coverIds.optInt(cIdx);
                // We have seen cover id "-1", so check!
                if (coverId > 0) {
                    final int finalCIdx = cIdx;
                    searchBestCover(context, "id", String.valueOf(coverId), finalCIdx).ifPresent(
                            fileSpec -> CoverFileSpecArray.setFileSpec(book, finalCIdx, fileSpec));
                }
            }
        }
    }

    /**
     * {@code https://openlibrary.org/isbn/9780141339092.json}
     * => redirects to: {@code https://openlibrary.org/books/OL27104332M.json}
     * <pre>
     *     {
     *      ...
     *      "works": [
     *          {
     *              "key": "/works/OL5725956W"
     *          }
     *      ],
     *   ...
     *   }
     * </pre>
     * Now issue: {@code https://openlibrary.org/works/OL5725956W/editions.json}
     * and continue in {@link #parseEditions(JSONObject)}.
     *
     * @param context   Current context
     * @param validIsbn to search for, <strong>must</strong> be valid.
     */
    @NonNull
    @Override
    public List<AltEditionOpenLibrary> searchAlternativeEditions(@NonNull final Context context,
                                                                 @NonNull final String validIsbn)
            throws SearchException {

        String url = getHostUrl() + "/isbn/" + validIsbn + ".json";
        try {
            String response = loadDocument(context, url);

            final JSONObject jsonObject = new JSONObject(response);
            final JSONArray works = jsonObject.optJSONArray("works");
            if (works != null && !works.isEmpty()) {
                final String worksKey = works.getJSONObject(0).optString("key");
                url = getHostUrl() + worksKey + "/editions.json";
                response = loadDocument(context, url);
                return parseEditions(new JSONObject(response));
            }
        } catch (@NonNull final StorageException | JSONException e) {
            throw new SearchException(getEngineId(), e);
        }

        return List.of();
    }

    /**
     * Parse the edition data as retrieved by {@link #searchAlternativeEditions(Context, String)}.
     * <pre>
     * {
     *   "links": {
     *     "self": "/works/OL5725956W/editions.json",
     *     "work": "/works/OL5725956W",
     *     "next": "/works/OL5725956W/editions.json?offset=50"
     *   },
     *   "size": 93,
     *   "entries": [
     *     {
     *         "type": {
     *         "key": "/type/edition"
     *       },
     *       "title": "Artemis Fowl",
     *       "authors": [
     *         {
     *           "key": "/authors/OL1392395A"
     *         },
     *         {
     *           "key": "/authors/OL9169368A"
     *         },
     *         {
     *           "key": "/authors/OL7980735A"
     *         }
     *       ],
     *       "publish_date": "Mar 11, 2020",
     *       "source_records": [
     *         "amazon:8491378251"
     *       ],
     *       "number_of_pages": 320,
     *       "publishers": [
     *         "Estrella Polar"
     *       ],
     *       "physical_format": "hardcover",
     *       "full_title": "Artemis Fowl",
     *       "covers": [
     *         14080792
     *       ],
     *       "works": [
     *         {
     *           "key": "/works/OL5725956W"
     *         }
     *       ],
     *       "key": "/books/OL47473653M",
     *       "identifiers": {},
     *       "isbn_10": [
     *         "8491378251"
     *       ],
     *       "isbn_13": [
     *         "9788491378259"
     *       ],
     *       "classifications": {},
     *       "languages": [
     *         {
     *           "key": "/languages/cat"
     *         }
     *       ],
     *       "translated_from": [
     *         {
     *           "key": "/languages/eng"
     *         }
     *       ],
     *       "latest_revision": 4,
     *       "revision": 4,
     *       "created": {
     *         "type": "/type/datetime",
     *         "value": "2023-04-17T05:27:07.889785"
     *       },
     *       "last_modified": {
     *         "type": "/type/datetime",
     *         "value": "2024-09-02T08:05:47.184003"
     *       }
     *     }
     *     ...
     * </pre>
     *
     * @param works object
     *
     * @return a list with {@link AltEditionOpenLibrary}s.
     */
    @NonNull
    private List<AltEditionOpenLibrary> parseEditions(@NonNull final JSONObject works) {
        final int size = works.optInt("size");
        if (size <= 0) {
            return List.of();
        }

        final JSONArray entries = works.optJSONArray("entries");
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        final List<AltEditionOpenLibrary> editionList = new ArrayList<>();

        for (int i = 0; i < entries.length(); i++) {
            final JSONObject edition = entries.optJSONObject(i);
            if (edition != null) {
                // Get the root level book key.
                // i.e. not the works key!
                String olid = edition.optString("key", null);
                if (olid != null && olid.startsWith("/books/")) {
                    olid = olid.substring("/books/".length());
                    if (!olid.isEmpty()) {
                        editionList.add(parseEdition(edition, olid));
                    }
                }
            }
        }

        return editionList;
    }

    /**
     * Given a single entry of the edition "entries" list,
     * extract the information needed for our {@link AltEditionOpenLibrary}.
     *
     * @param entry to parse
     * @param olid  pre-parsed OL ID for the book.
     *
     * @return instance
     */
    @NonNull
    private AltEditionOpenLibrary parseEdition(@NonNull final JSONObject entry,
                                               @NonNull final String olid) {
        String isbn = null;
        String langIso3 = null;
        String publisher = null;
        JSONArray a;

        a = entry.optJSONArray("isbn_13");
        if (a != null && !a.isEmpty()) {
            isbn = a.optString(0);
        }
        if (isbn == null || isbn.isEmpty()) {
            a = entry.optJSONArray("isbn_10");
            if (a != null && !a.isEmpty()) {
                isbn = a.optString(0);
            }
        }

        a = entry.optJSONArray("languages");
        if (a != null && !a.isEmpty()) {
            final JSONObject o = a.optJSONObject(0);
            if (o != null) {
                langIso3 = o.optString("key", null);
                if (langIso3 != null && langIso3.startsWith("/languages/")) {
                    langIso3 = langIso3.substring("/languages/".length());
                }
            }
        }
        a = entry.optJSONArray("publishers");
        if (a != null && !a.isEmpty()) {
            publisher = a.optString(0);
        }

        final long[] covers = new long[DBKey.NR_OF_BOOK_COVERS];
        a = entry.optJSONArray("covers");
        if (a != null && !a.isEmpty()) {
            final int maxCovers = Math.min(a.length(), DBKey.NR_OF_BOOK_COVERS);
            for (int cIdx = 0; cIdx < maxCovers; cIdx++) {
                covers[cIdx] = a.optInt(cIdx);
            }
        }
        return new AltEditionOpenLibrary(olid, isbn, langIso3, publisher, covers);
    }

    /**
     * <a href="https://openlibrary.org/dev/docs/api/covers">API covers</a>.
     * <p>
     * {@code
     * http://covers.openlibrary.org/b/isbn/0385472579-S.jpg?default=false
     * }
     * <p>
     * S/M/L
     * <p>
     * {@inheritDoc}
     *
     * @see #fetchImageByKey(Context, char, String, String, int, ImageWebSize)
     * @see #searchBestCover(Context, String, String, int)
     */
    @SuppressWarnings("ChainOfInstanceofChecks")
    @Override
    @NonNull
    public Optional<String> searchCoverByEdition(@NonNull final Context context,
                                                 @NonNull final AltEdition altEdition,
                                                 @IntRange(from = 0, to = 3) final int cIdx,
                                                 @Nullable final ImageWebSize size)
            throws CoverStorageException {

        if (altEdition instanceof AltEditionOpenLibrary) {
            final AltEditionOpenLibrary edition = (AltEditionOpenLibrary) altEdition;
            final long[] covers = edition.getCovers();

            // The cover should always be valid, but paranoia...
            if (covers.length >= cIdx && covers[cIdx] > 0) {
                return fetchImageByKey(context, COVER_KEY_BOOK, "id",
                                       String.valueOf(covers[cIdx]), cIdx, size);
            }
        } else if (altEdition instanceof AltEditionIsbn) {
            if (cIdx > 0) {
                // ENHANCE: we cannot return a back-cover here, as we need the native
                //  OL cover-id ( != OLID book id) which we do not store locally.
                //  We'd basically need to do a new book search (2 requests),
                //  extract the cover-id(s) and run 2 more requests.
                //  For now, users can get the back-cover when doing an "Update fields" for a book
                return Optional.empty();
            }

            final AltEditionIsbn edition = (AltEditionIsbn) altEdition;
            final String isbn = edition.getIsbn();

            // Frontcover only
            return fetchImageByKey(context, COVER_KEY_BOOK, "isbn", isbn, 0, size);
        }

        return Optional.empty();
    }

    /**
     * Search for the best cover using the given key/id values.
     *
     * @param context Current context
     * @param key     to use for the search
     * @param id      value for the above key
     * @param cIdx    0..n image index
     *
     * @return fileSpec
     *
     * @throws CoverStorageException on storage related failures
     */
    @NonNull
    private Optional<String> searchBestCover(@NonNull final Context context,
                                             @NonNull final String key,
                                             @NonNull final String id,
                                             @IntRange(from = 0, to = 3) final int cIdx)
            throws CoverStorageException {

        Optional<String> oFileSpec = fetchImageByKey(context, COVER_KEY_BOOK, key, id, cIdx,
                                                     ImageWebSize.Large);
        if (oFileSpec.isEmpty()) {
            oFileSpec = fetchImageByKey(context, COVER_KEY_BOOK, key, id, cIdx,
                                        ImageWebSize.Medium);
            if (oFileSpec.isEmpty()) {
                oFileSpec = fetchImageByKey(context, COVER_KEY_BOOK, key, id, cIdx,
                                            ImageWebSize.Small);
            }
        }
        return oFileSpec;
    }

    /**
     * Common code to do the actual cover search.
     *
     * @param context Current context
     * @param type    {@link #COVER_KEY_BOOK} for books, or {@link #COVER_KEY_AUTHOR} for authors
     *                There is NO check!
     * @param key     to use for the search
     * @param id      value for the above key
     * @param cIdx    0..n image index
     * @param size    of image to get.
     *
     * @return fileSpec
     *
     * @throws CoverStorageException on storage related failures
     */
    @NonNull
    Optional<String> fetchImageByKey(@NonNull final Context context,
                                     final char type,
                                     @NonNull final String key,
                                     @NonNull final String id,
                                     @IntRange(from = 0, to = 3) final int cIdx,
                                     @Nullable final ImageWebSize size)
            throws CoverStorageException {

        final String sizeParam;
        if (size == null) {
            sizeParam = "L";
        } else {
            switch (size) {
                case Small:
                    sizeParam = "S";
                    break;
                case Medium:
                    sizeParam = "M";
                    break;
                case Large:
                default:
                    sizeParam = "L";
                    break;
            }
        }

        final String url = String.format(COVER_BY_KEY, type, key, id, sizeParam);

        // The traffic from a simple request for a cover when using wget:
        // $ wget -d -O image.jpg https://covers.openlibrary.org/b/id/13769253-L.jpg?default=false
        //302 Found
        //Location: https://archive.org/download/l_covers_0013/l_covers_0013_76.zip/0013769253-L.jpg
        //302 Found
        //Location: https://ia801909.us.archive.org/view_archive.php?archive=/31/items/l_covers_0013/l_covers_0013_76.zip&file=0013769253-L.jpg
        //200 OK
        //Saving to: ‘image.jpg’

        if ("isbn".equals(key)) {
            //noinspection DataFlowIssue
            getEngineId().getConfig().getThrottler().waitUntilRequestAllowed(
                    COVER_BY_ISBN_REQUEST_DELAY);
        }
        return saveImage(context, url, null, id, cIdx, size);
    }

    @NonNull
    @Override
    public <T> FutureHttp<T> createGetDocumentRequest(@NonNull final Context context) {
        final FutureHttp<T> request = super.createGetDocumentRequest(context);
        request.setEnable404Redirect(true);

        return request;
    }

    @NonNull
    @Override
    protected Request createImageRequest(@NonNull final Context context,
                                         @NonNull final String urlStr,
                                         @Nullable final Map<String, String> requestProperties) {

        // DO NOT ADD ANY HEADERS.... OL only works with the defaults ?!
        return new Request.Builder().url(urlStr).build();
    }
}
