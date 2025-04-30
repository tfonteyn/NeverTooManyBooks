/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionIsbn;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinatorCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteAuthModule;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.AuthorTypeMapper;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * <a href="https://openlibrary.org/dev/docs/api/search">Open Library Search API</a>
 * <p>
 * 2024-12-02: fetching covers using the
 * "covers": [
 * 5546156
 * ],
 * section is hit and miss. Due to the servers multiple redirect
 * we sometimes get a cover and sometimes not. We see error 500, sometimes 403.
 * <p>
 * Leaving as-is for now. Based on past experience, and now this one...
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

    public static final String SITE_URL = "https://openlibrary.org";
    public static final String BOOK_URL = "https://openlibrary.org/books/%s";
    public static final String AUTHOR_URL = "https://openlibrary.org/authors/%s";

    private static final String PREFERENCE_KEY = "openlibrary";

    static final String PK_LOGIN_TO_SEARCH = PREFERENCE_KEY + ".login.to.search";
    private static final String BASE_BOOK_URL = "/search.json?"
                                                + "q=%1$s"
                                                + "&fields=key,editions";

    private static final String SEARCH_BY_EXTERNAL_ID = "/books/%1$s.json";

    /**
     * The covers are available in 3 sizes:
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
     * param 1: key can be any one of ISBN, OCLC, LCCN, OLID and ID (case-insensitive)
     * param 2: value of the chosen key
     * param 3: one of S, M and L for small, medium and large respectively.
     * <p>
     * When there is no cover, the server returns a blank image by default.
     * Adding "?default=false": forces a 404 to be returned
     */
    private static final String COVER_BY_KEY =
            "https://covers.openlibrary.org/b/%1$s/%2$s-%3$s.jpg?default=false";

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
    private final AuthorTypeMapper authorTypeMapper = new AuthorTypeMapper();
    @NonNull
    private final CookieManager cookieManager;
    @Nullable
    private FutureHttpGet<String> futureHttpGet;
    @Nullable
    private SiteAuthModule siteAuthModule;

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

        // We MUST bootstrap it here to ensure it's active before the first http request send
        cookieManager = ServiceLocator.getInstance().getCookieManager();
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
                .setIdentifierKey(Identifier.SID_OPEN_LIBRARY)
                .setMultipleCoverSizes(true)
                .setPreferenceFragmentClazz(OpenLibraryPreferencesFragment.class);
    }

    @Override
    public boolean isLoginToSearch(@NonNull final Context context) {
        if (BuildConfig.ENABLE_OPEN_LIBRARY_LOGIN) {
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
        // depending if we get here from a search or from a sync,
        // the module MIGHT already exist so don't login twice!
        if (siteAuthModule == null) {
            siteAuthModule = new OpenLibraryAuth(cookieManager);
            try {
                siteAuthModule.login(context);
            } catch (@NonNull final IOException | StorageException e) {
                siteAuthModule = null;
                throw new SearchException(getEngineId(), e);
            }
        }
    }

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (futureHttpGet != null) {
                futureHttpGet.cancel();
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

        final String url = getHostUrl(context) + String.format(SEARCH_BY_EXTERNAL_ID, externalId);
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
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(BASE_BOOK_URL, validIsbn);

        fetchBook(context, url, fetchCovers, book);
        return book;
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final SearchCoordinatorCriteria criteria,
                       @Nullable final String code,
                       @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner words = criteria.concat(" ");
        if (code != null && !code.isEmpty()) {
            words.add(code);
        }

        final Book book = new Book();

        // Sanity check
        if (words.length() == 0) {
            return book;
        }

        // Limit the result to a single book for performance.
        final String url = getHostUrl(context) + String.format(BASE_BOOK_URL, words) + "&limit=1";

        fetchBook(context, url, fetchCovers, book);
        return book;
    }

    @NonNull
    private String loadDocument(@NonNull final Context context,
                                @NonNull final String url)
            throws StorageException, SearchException {
        futureHttpGet = createGetDocumentRequest(context);
        try {
            return futureHttpGet.getAsString(url, (con, s) -> s);
        } catch (@NonNull final IOException e) {
            throw new SearchException(getEngineId(), e);
        } finally {
            futureHttpGet = null;
        }
    }

    /**
     * Fetch and parse.
     *
     * @param context     Current context
     * @param url         to fetch
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
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
            final String editionUrl = getHostUrl(context) + key + ".json";
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
     * @param document    JSON result data
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws IOException          when fetching the Author details fails
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    @VisibleForTesting
    void parse(@NonNull final Context context,
               @NonNull final JSONObject document,
               @NonNull final boolean[] fetchCovers,
               @NonNull final Book book)
            throws StorageException, IOException, SearchException, CredentialsException {

        final List<AuthorResolver> authorResolvers = AuthorResolverFactory
                .getResolvers(context, this);

        JSONArray a;
        String s;
        final int i;

        // "/books/OL22853304M"
        s = document.optString("key", null);
        if (s != null && !s.isEmpty()) {
            book.setIdentifierValue(Identifier.SID_OPEN_LIBRARY, s.substring("/books/".length()));
        }

        s = document.optString("title", null);
        if (s != null && !s.isEmpty()) {
            book.setTitle(s);
        }

        // ENHANCE: add a preference switch to optionally fetch the subtitle
        //  and concatenate it with the title
        // s = document.optString("subtitle");

        // "authors" contains structured Author data
        a = document.optJSONArray("authors");
        if (a != null && !a.isEmpty()) {
            parseAuthors(context, a, authorResolvers, book);
        }
        // "by_statement" contains NON-structured author data:
        //     "by John Miedema."
        //     "Katja Diehl, mit zahlreichen Illustrationen von Doris Reich"
        //
        // In the above example "John Miedema." will be created WITH the "." at the end.
        // There are just to many inconsistencies to catch them all, so we leave those
        // to the user.
        s = document.optString("by_statement", null);
        if (s != null && !s.isEmpty()) {
            // These are gambles.... we don't have enough data samples
            if (s.startsWith("by ") && s.length() > 3) {
                s = s.substring(3);
                addAuthor(Author.from(s), Author.TYPE_UNKNOWN, book);
            }
            if (s.contains(",")) {
                final String[] split = s.split(",");
                if (split.length > 0) {
                    addAuthor(Author.from(split[0]), Author.TYPE_UNKNOWN, book);
                }
            }
        }

        a = document.optJSONArray("contributors");
        if (a != null && !a.isEmpty()) {
            parseContributors(context, a, authorResolvers, book);
        }

        // There is also a key "pagination" which for example
        // contains ""xxii, 781p."
        // We're ignoring that one...
        i = document.optInt("number_of_pages");
        if (i > 0) {
            book.putString(DBKey.PAGES, String.valueOf(i));
        }

        // TODO: There is another field "physical_dimensions".
        //  Maybe use that if the format is not present?
        s = document.optString("physical_format", null);
        if (s != null && !s.isEmpty()) {
            book.putString(DBKey.FORMAT, s);
        }

        a = document.optJSONArray("languages");
        if (a != null && !a.isEmpty()) {
            parseLanguages(a, book);
        }

        parseIsbn(document, book);

        parseIdentifiers(document, book);

        a = document.optJSONArray("series");
        if (a != null && !a.isEmpty()) {
            parseSeries(a, book);
        }

        a = document.optJSONArray("publishers");
        if (a != null && !a.isEmpty()) {
            parsePublishers(a, book);
        }

        s = document.optString("publish_date", null);
        if (s != null && !s.isEmpty()) {
            // The site serves dates in multiple formats...
            // "2013"
            // "1984-10"
            // "2022-02-09"
            // "March 2009"
            // "18 October 2006"
            // "May 1, 1983"
            // hope for the best by parsing
            addPublicationDate(context, getLocale(context), s, book);
        }

        parseFirstPublicationDate(context, document, book);

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

        parseNotes(document, book);

        a = document.optJSONArray("table_of_contents");
        if (a != null && !a.isEmpty()) {
            parseToc(context, a, book);
        }

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
     * A single Author element:
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
     *
     * @param context         Current context
     * @param a               array with author elements
     * @param authorResolvers to use
     * @param book            destination
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    private void parseAuthors(@NonNull final Context context,
                              @NonNull final JSONArray a,
                              @NonNull final List<AuthorResolver> authorResolvers,
                              @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        JSONObject element;
        for (int ai = 0; ai < a.length(); ai++) {
            element = a.optJSONObject(ai);
            if (element != null) {
                final String key = element.optString("key", null);
                if (key != null && !key.isEmpty()) {
                    final String authorUrl = getHostUrl(context) + key + ".json";
                    final String response = loadDocument(context, authorUrl);
                    final JSONObject jsonObject = new JSONObject(response);
                    final String name = jsonObject.optString("name", null);
                    if (name != null && !name.isEmpty()) {
                        final Author author = Author.from(name);
                        // extract the OL id it from the key as it's not in the json
                        // Sanity check
                        if (key.startsWith("/authors/")) {
                            final String iv = key.substring(9);
                            author.setIdentifierValue(Identifier.SID_OPEN_LIBRARY, iv);
                        }
                        for (final AuthorResolver resolver : authorResolvers) {
                            resolver.resolve(context, author);
                        }
                        addAuthor(author, Author.TYPE_UNKNOWN, book);
                    }
                }
            }
        }
    }

    private void parseContributors(@NonNull final Context context,
                                   @NonNull final JSONArray a,
                                   @NonNull final List<AuthorResolver> authorResolvers,
                                   @NonNull final Book book)
            throws SearchException, CredentialsException {
        for (int ai = 0; ai < a.length(); ai++) {
            final JSONObject c = a.optJSONObject(ai);
            if (c != null) {
                final String name = c.optString("name", null);
                if (name != null) {
                    final Author author = Author.from(name);
                    final int type;
                    final String role = c.optString("role", null);
                    if (role != null) {
                        type = authorTypeMapper.map(getLocale(context), role);
                    } else {
                        type = Author.TYPE_UNKNOWN;
                    }
                    for (final AuthorResolver resolver : authorResolvers) {
                        resolver.resolve(context, author);
                    }
                    addAuthor(author, type, book);
                }
            }
        }
    }

    /**
     * The series object is rather unstructured.
     * It's an array, but my (limited) tests have only ever found 1 entry.
     * However, a single entry can apparently have data for 2 series (oh boy...).
     * We are NOT even going to attempt to parse the latter case....
     *
     * <pre>
     * "series": [
     *     "Nevermoor"
     * ]
     *
     * "series": [
     *     "The Dark Tower, 5"
     * ]
     *
     * "series": [
     *     "NUMA Files, 1; Dirk Pitt Adventures, 1"
     *  ],
     * </pre>
     *
     * @param a    array with series elements
     * @param book destination
     */
    private void parseSeries(@NonNull final JSONArray a,
                             @NonNull final Book book) {
        String name;
        for (int ai = 0; ai < a.length(); ai++) {
            name = a.optString(ai, null);
            if (name != null && !name.isEmpty()) {
                book.add(Series.from(name));
            }
        }
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

    private void parseFirstPublicationDate(@NonNull final Context context,
                                           @NonNull final JSONObject document,
                                           @NonNull final Book book) {
        String s;
        s = document.optString("first_publish_date", null);
        if (s != null && !s.isEmpty()) {
            addFirstPublicationDate(context, getLocale(context), s, book);
        } else {
            //  "copyright_date": "1982, 1994",
            //  "copyright_date": "2022",
            s = document.optString("copyright_date", null);
            if (s != null && !s.isEmpty()) {
                // grab the first, we'll assume it will the earlier date.
                // Given OL track record of structure we'll probably be wrong sometimes
                final String[] split = s.split(",");
                addFirstPublicationDate(context, getLocale(context), split[0], book);
            }
        }
    }

    /**
     * "notes" is a specific (set of) remarks on this particular edition of the book.
     * There are two known formats returned
     * <pre>
     *      "notes": "Includes bibliographical references and index.",
     *      "notes": {"type": "/type/text", "value": "Mit zahlreichen farbigen Illustrationen"}
     * </pre>
     *
     * @param document to parse
     * @param book     to update
     */
    private void parseNotes(@NonNull final JSONObject document,
                            @NonNull final Book book) {

        final JSONObject element = document.optJSONObject("notes");
        if (element != null) {
            // Sanity check, no idea if there are others types
            if ("/type/text".equals(element.optString("type"))) {
                final String s = element.optString("value", null);
                if (s != null && !s.isEmpty()) {
                    book.putString(DBKey.DESCRIPTION, s);
                }
            }
        } else {
            // Try the plain string format
            final String s = document.optString("notes", null);
            if (s != null && !s.isEmpty()) {
                book.putString(DBKey.DESCRIPTION, s);
            }
        }
    }

    private void parseLanguages(@NonNull final JSONArray a,
                                @NonNull final Book book) {
        final JSONObject element = a.optJSONObject(0);
        if (element != null) {
            final String s = element.optString("key", null);
            if (s != null && s.startsWith("/languages/")) {
                book.putString(DBKey.LANGUAGE, s.substring("/languages/".length()));
            }
        }
    }

    private void parseIsbn(@NonNull final JSONObject element,
                           @NonNull final Book book) {
        // get the 'longest' ISBN available
        JSONArray a = element.optJSONArray("isbn_13");
        if (a != null && !a.isEmpty()) {
            // Overwrite
            book.putString(DBKey.ISBN, a.getString(0));
        } else {
            a = element.optJSONArray("isbn_10");
            if (a != null && !a.isEmpty()) {
                // Do NOT overwrite
                if (!book.contains(DBKey.ISBN)) {
                    book.putString(DBKey.ISBN, a.getString(0));
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
     * --> Took away permissions 3/21/13.. do not think this is really a bowker prefix.
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
            throws StorageException, SearchException, CredentialsException {
        for (int cIdx = 0; cIdx < 2; cIdx++) {
            if (fetchCovers[cIdx] && coverIds.length() > cIdx) {
                final int coverId = coverIds.optInt(cIdx);
                // We have seen cover id "-1", so check!
                if (coverId > 0) {
                    final int finalCIdx = cIdx;
                    searchBestCover(context, "id", String.valueOf(coverId), cIdx).ifPresent(
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

        String url = getHostUrl(context) + "/isbn/" + validIsbn + ".json";
        try {
            String response = loadDocument(context, url);

            final JSONObject jsonObject = new JSONObject(response);
            final JSONArray works = jsonObject.optJSONArray("works");
            if (works != null && !works.isEmpty()) {
                final String worksKey = works.getJSONObject(0).optString("key");
                url = getHostUrl(context) + worksKey + "/editions.json";
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
        final long[] covers = new long[2];
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
        a = entry.optJSONArray("covers");
        if (a != null && !a.isEmpty()) {
            covers[0] = a.optInt(0);
            if (a.length() > 1) {
                covers[1] = a.optInt(1);
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
     * @see #searchCoverByKey(Context, String, String, int, Size)
     * @see #searchBestCover(Context, String, String, int)
     */
    @Override
    @NonNull
    public Optional<String> searchCoverByEdition(@NonNull final Context context,
                                                 @NonNull final AltEdition altEdition,
                                                 @IntRange(from = 0, to = 1) final int cIdx,
                                                 @Nullable final Size size)
            throws StorageException, SearchException, CredentialsException {

        if (altEdition instanceof AltEditionOpenLibrary) {
            final AltEditionOpenLibrary edition = (AltEditionOpenLibrary) altEdition;
            final long[] covers = edition.getCovers();

            // The cover should always be valid, but paranoia...
            if (covers[cIdx] > 0) {
                return searchCoverByKey(context, "id", String.valueOf(covers[cIdx]), cIdx, size);
            }
        } else if (altEdition instanceof AltEditionIsbn) {
            if (cIdx == 1) {
                // ENHANCE: we cannot return a back-cover here, as we need to native
                //  OL cover-id ( != OLID book id) which we do not store locally.
                //  We'd basically need to do a new book search (2 requests) here,
                //  extract the cover-id(s) and run 2 more requests.
                //  For now, users can get the back-cover when doing an "Internet update"
                return Optional.empty();
            }

            final AltEditionIsbn edition = (AltEditionIsbn) altEdition;
            final String isbn = edition.getIsbn();

            // Frontcover as usual
            return searchCoverByKey(context, "isbn", isbn, 0, size);
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
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    @NonNull
    private Optional<String> searchBestCover(@NonNull final Context context,
                                             @NonNull final String key,
                                             @NonNull final String id,
                                             final int cIdx)
            throws StorageException, SearchException, CredentialsException {

        Optional<String> oFileSpec = searchCoverByKey(context, key, id, cIdx, Size.Large);
        if (oFileSpec.isEmpty()) {
            oFileSpec = searchCoverByKey(context, key, id, cIdx, Size.Medium);
            if (oFileSpec.isEmpty()) {
                oFileSpec = searchCoverByKey(context, key, id, cIdx, Size.Small);
            }
        }
        return oFileSpec;
    }

    /**
     * Common code to do the actual cover search.
     *
     * @param context Current context
     * @param key     to use for the search
     * @param id      value for the above key
     * @param cIdx    0..n image index
     * @param size    of image to get.
     *
     * @return fileSpec
     *
     * @throws StorageException on storage related failures
     */
    @NonNull
    private Optional<String> searchCoverByKey(@NonNull final Context context,
                                              @NonNull final String key,
                                              @NonNull final String id,
                                              @IntRange(from = 0, to = 1) final int cIdx,
                                              @Nullable final Size size)
            throws StorageException {

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

        final String url = String.format(COVER_BY_KEY, key, id, sizeParam);

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
    public <T> FutureHttpGet<T> createGetDocumentRequest(@NonNull final Context context) {
        final FutureHttpGet<T> request = super.createGetDocumentRequest(context);
        request.setEnable404Redirect(true);

        return request;
    }

    @NonNull
    public <T> FutureHttpGet<T> createGetImageRequest(@NonNull final Context context) {
        final FutureHttpGet<T> request = createGetDocumentRequest(context);
        request.setInstanceFollowRedirects(true);
        request.setEnable404Redirect(true);

        //        request.setRequestProperty(HttpConstants.ACCEPT, "*/*");
        //        request.setRequestProperty(HttpConstants.ACCEPT_ENCODING,
        //                                   HttpConstants.ACCEPT_ENCODING_IDENTITY);
        return request;
    }
}
