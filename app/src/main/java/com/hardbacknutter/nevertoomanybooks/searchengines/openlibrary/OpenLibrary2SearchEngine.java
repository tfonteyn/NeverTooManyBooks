/*
 * @Copyright 2018-2024 HardBackNutter
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
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.AuthorTypeMapper;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * <a href="https://openlibrary.org/dev/docs/api/search">Open Library Search API</a>
 * <p>
 * ENHANCE: {@link SearchEngine.ByText} could be added now by using
 *   https://openlibrary.org/search.json?q=SEARCHTEXT
 *   &fields=key,editions
 *   &limit=1
 *   or without/a reasonable limit multiple results.
 */
public class OpenLibrary2SearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ViewBookByExternalId,
                   SearchEngine.CoverByIsbn,
                   SearchEngine.AlternativeEditions<AltEditionOpenLibrary> {

    private static final String BASE_BOOK_URL = "/search.json?q=%1$s" +
                                                "&fields=key,editions";

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
     * key can be any one of ISBN, OCLC, LCCN, OLID and ID (case-insensitive)
     * value is the value of the chosen key
     * size can be one of S, M and L for small, medium and large respectively.
     * <p>
     * param 1: key-name, param 2: key-value, param 3: L/M/S for the size.
     */
    private static final String BASE_COVER_URL =
            "https://covers.openlibrary.org/b/%1$s/%2$s-%3$s.jpg?default=false";

    /**
     * <a href="https://openlibrary.org/dev/docs/api/covers">Covers API</a>
     * The cover access by ids other than CoverID and OLID are rate-limited.
     * Currently only 100 requests/IP are allowed for every 5 minutes.
     * If any IP tries to access more that the allowed limit,
     * the service will return "403 Forbidden" status.
     */
    private static final int COVER_BY_ISBN_REQUEST_DELAY = 3_000;
    private final AuthorTypeMapper authorTypeMapper = new AuthorTypeMapper();
    @Nullable
    private FutureHttpGet<String> futureHttpGet;

    /**
     * Constructor. Called using reflection, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public OpenLibrary2SearchEngine(@NonNull final Context appContext,
                                    @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    @NonNull
    @Override
    public String createBrowserUrl(@NonNull final Context context,
                                   @NonNull final String externalId) {
        return getHostUrl(context) + "/books/" + externalId;
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(BASE_BOOK_URL, externalId);

        fetchBook(context, url, fetchCovers, book);
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

    /**
     * <a href="https://openlibrary.org/dev/docs/api/covers">API covers</a>.
     * <p>
     * {@code
     * http://covers.openlibrary.org/b/isbn/0385472579-S.jpg?default=false
     * }
     * <p>
     * S/M/L
     *
     * @see #searchCoverByKey(Context, String, String, int, Size)
     * @see #searchBestCoverByKey(Context, String, String, int)
     */
    @NonNull
    @Override
    @WorkerThread
    public Optional<String> searchCoverByIsbn(@NonNull final Context context,
                                              @NonNull final String validIsbn,
                                              @IntRange(from = 0, to = 1) final int cIdx,
                                              @Nullable final Size size)
            throws StorageException {
        if (cIdx == 1) {
            // ENHANCE: we cannot return a back-cover here, as we need to native
            //  OL cover-id ( != OLID book id) which we do not store locally.
            //  We'd basically need to do a new book search (2 requests) here,
            //  extract the cover-id(s) and run 2 more requests.
            //  For now, users can get the back-cover when doing an "Internet update"
            return Optional.empty();
        }

        //noinspection DataFlowIssue
        getEngineId().getConfig().getThrottler().waitUntilRequestAllowed(
                COVER_BY_ISBN_REQUEST_DELAY);

        // Frontcover as usual
        return searchCoverByKey(context, "isbn", validIsbn, 0, size);
    }

    @NonNull
    public Optional<String> searchCoverByKey(@NonNull final Context context,
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

        final String url = String.format(BASE_COVER_URL, key, id, sizeParam);

        // see {@link FutureHttpGetBase#setEnable404Redirect(boolean)}
        imageDownloader404redirect = true;
        return saveImage(context, url, id, cIdx, size);
    }

    @NonNull
    private Optional<String> searchBestCoverByKey(@NonNull final Context context,
                                                  @NonNull final String key,
                                                  @NonNull final String id,
                                                  final int cIdx)
            throws StorageException {

        Optional<String> oFileSpec = searchCoverByKey(context, key, id, cIdx, Size.Large);
        if (oFileSpec.isEmpty() && supportsMultipleCoverSizes()) {
            oFileSpec = searchCoverByKey(context, key, id, cIdx, Size.Medium);
            if (oFileSpec.isEmpty()) {
                oFileSpec = searchCoverByKey(context, key, id, cIdx, Size.Small);
            }
        }
        return oFileSpec;
    }

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (futureHttpGet != null) {
                futureHttpGet.cancel();
            }
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
     * @throws StorageException on storage related failures
     * @throws SearchException  on generic exceptions (wrapped) during search
     */
    private void fetchBook(@NonNull final Context context,
                           @NonNull final String url,
                           @NonNull final boolean[] fetchCovers,
                           @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        futureHttpGet = createFutureGetRequest(context);

        try {
            // get and store the result into a string.
            String response = futureHttpGet.get(url, (con, is) ->
                    readResponseStream(is));

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
            response = futureHttpGet.get(editionUrl, (con, is) ->
                    readResponseStream(is));

            parse(context, new JSONObject(response), fetchCovers, book);

            Series.checkForSeriesNameInTitle(book);

        } catch (@NonNull final IOException | JSONException e) {
            throw new SearchException(getEngineId(), e);
        } finally {
            futureHttpGet = null;
        }
    }

    /**
     * Read the entire InputStream into a String.
     *
     * @param is to read
     *
     * @return the entire content
     *
     * @throws UncheckedIOException on any failure
     */
    @VisibleForTesting
    @NonNull
    String readResponseStream(@NonNull final InputStream is)
            throws UncheckedIOException {
        // Don't close this stream!
        final InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        final BufferedReader reader = new BufferedReader(isr);

        return reader.lines().collect(Collectors.joining());
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
     *     "description": "\"A study of voluntary slow reading from diverse angles\"--Provided by publisher.",
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
     * @throws StorageException on storage related failures
     */
    @VisibleForTesting
    void parse(@NonNull final Context context,
               @NonNull final JSONObject document,
               @NonNull final boolean[] fetchCovers,
               @NonNull final Book book)
            throws StorageException, IOException, SearchException, CredentialsException {

        JSONObject element;
        JSONArray a;
        String s;
        final int i;

        // "/books/OL22853304M"
        s = document.optString("key");
        if (s != null && !s.isEmpty()) {
            book.putString(DBKey.SID_OPEN_LIBRARY, s.substring("/books/".length()));
        }

        s = document.optString("title");
        if (s != null && !s.isEmpty()) {
            book.putString(DBKey.TITLE, s);
        }

        // ENHANCE: add a preference switch to optionally fetch the subtitle
        //  and concatenate it with the title
        // s = document.optString("subtitle");

        // "authors" contains structured Author data
        a = document.optJSONArray("authors");
        if (a != null && !a.isEmpty()) {
            processAuthors(context, a, book);
        }
        // "by_statement" contains NON-structured author data:
        //     "by John Miedema."
        //     "Katja Diehl, mit zahlreichen Illustrationen von Doris Reich"
        //
        // In the above example "John Miedema." will be created WITH the "." at the end.
        // There are just to many inconsistencies to catch them all, so we leave those
        // to the user.
        s = document.optString("by_statement");
        if (s != null && !s.isEmpty()) {
            // These are gambles.... we don't have enough data samples
            if (s.startsWith("by ") && s.length() > 3) {
                s = s.substring(3);
                processAuthor(Author.from(s), Author.TYPE_UNKNOWN, book);
            }
            if (s.contains(",")) {
                final String[] split = s.split(",");
                if (split.length > 0) {
                    processAuthor(Author.from(split[0]), Author.TYPE_UNKNOWN, book);
                }
            }
        }

        a = document.optJSONArray("contributors");
        if (a != null && !a.isEmpty()) {
            processContributors(context, a, book);
        }

        // There is also a key "pagination" which I believe to be the number of
        // *numbered* pages - we're ignoring that one...
        i = document.optInt("number_of_pages");
        if (i > 0) {
            book.putString(DBKey.PAGE_COUNT, String.valueOf(i));
        }

        s = document.optString("physical_format");
        if (s != null && !s.isEmpty()) {
            book.putString(DBKey.FORMAT, s);
        }

        a = document.optJSONArray("languages");
        if (a != null && !a.isEmpty()) {
            processLanguages(a, book);
        }

        // Root level contains ISBN etc
        processIdentifiers(document, book);
        // "identifiers" contains foreign-site codes (e.g. amazon ASIN)
        element = document.optJSONObject("identifiers");
        if (element != null) {
            processIdentifiers(element, book);
        }

        // seemingly unstructured
        a = document.optJSONArray("series");
        if (a != null && !a.isEmpty()) {
            processSeries(a, book);
        }

        a = document.optJSONArray("publishers");
        if (a != null && !a.isEmpty()) {
            processPublishers(a, book);
        }

        s = document.optString("publish_date");
        if (s != null && !s.isEmpty()) {
            processPublicationDate(context, getLocale(context), s, book);
        }

        // "subjects": [
        //            {
        //                "name": "History",
        //                "url": "https://openlibrary.org/subjects/history"
        //            },
        // could be used for genres... but the subject list for a single book can be very large


        // "notes" is a specific (set of) remarks on this particular edition of the book.
        // There are two known formats returned
        //
        // "notes": "Includes bibliographical references and index.",
        // "notes": {"type": "/type/text", "value": "Mit zahlreichen farbigen Illustrationen"}
        //
        element = document.optJSONObject("notes");
        if (element != null) {
            // Sanity check, no idea if there are others types
            if ("/type/text".equals(element.optString("type"))) {
                s = element.optString("value");
                if (s != null && !s.isEmpty()) {
                    book.putString(DBKey.DESCRIPTION, s);
                }
            }
        } else {
            // Try the plain string format
            s = document.optString("notes");
            if (s != null && !s.isEmpty()) {
                book.putString(DBKey.DESCRIPTION, s);
            }
        }

        a = document.optJSONArray("table_of_contents");
        if (a != null && !a.isEmpty()) {
            processToc(context, a, book);
        }

        if (isCancelled()) {
            return;
        }

        // "covers": [
        //       5546156
        // ]
        a = document.optJSONArray("covers");
        if (a != null && !a.isEmpty()) {
            parseCovers(context, a, fetchCovers, book);
        }
    }


    private void parseCovers(@NonNull final Context context,
                             @NonNull final JSONArray coverIds,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws StorageException {
        for (int cIdx = 0; cIdx < 2; cIdx++) {
            if (fetchCovers[cIdx] && coverIds.length() > cIdx) {
                final int coverId = coverIds.optInt(cIdx);
                if (coverId > 0) {
                    final int finalCIdx = cIdx;
                    searchBestCoverByKey(context, "id", String.valueOf(coverId), cIdx).ifPresent(
                            fileSpec -> CoverFileSpecArray.setFileSpec(book, finalCIdx, fileSpec));
                }
            }
        }
    }

    private void processSeries(@NonNull final JSONArray a,
                               @NonNull final Book book) {
        String name;
        for (int ai = 0; ai < a.length(); ai++) {
            name = a.optString(ai);
            if (name != null && !name.isEmpty()) {
                book.add(Series.from(name));
            }
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
     * @param context Current context
     * @param a       array with author elements
     * @param book    destination
     */
    private void processAuthors(@NonNull final Context context,
                                @NonNull final JSONArray a,
                                @NonNull final Book book)
            throws StorageException, IOException {

        // depending how we got here, we might not have GET build
        if (futureHttpGet == null) {
            futureHttpGet = createFutureGetRequest(context);
        }

        JSONObject element;
        for (int ai = 0; ai < a.length(); ai++) {
            element = a.optJSONObject(ai);
            if (element != null) {
                final String key = element.optString("key");
                if (key != null && !key.isEmpty()) {
                    final String authorUrl = getHostUrl(context) + key + ".json";
                    final String response = futureHttpGet.get(authorUrl, (con, is) ->
                            readResponseStream(is));
                    final JSONObject jsonObject = new JSONObject(response);
                    final String name = jsonObject.optString("name");
                    if (name != null && !name.isEmpty()) {
                        processAuthor(Author.from(name), Author.TYPE_UNKNOWN, book);
                    }
                }
            }
        }
    }

    private void processContributors(@NonNull final Context context,
                                     @NonNull final JSONArray a,
                                     @NonNull final Book book) {
        for (int ai = 0; ai < a.length(); ai++) {
            final JSONObject c = a.optJSONObject(ai);
            if (c != null) {
                final String name = c.optString("name");
                if (name != null) {
                    final Author author = Author.from(name);
                    final String role = c.optString("role");
                    if (role != null) {
                        author.setType(authorTypeMapper.map(getLocale(context), role));
                    }
                    book.add(author);
                }
            }
        }
    }

    private void processLanguages(@NonNull final JSONArray a,
                                  @NonNull final Book book) {
        final JSONObject element = a.optJSONObject(0);
        if (element != null) {
            final String s = element.optString("key");
            if (s != null && s.startsWith("/languages/")) {
                book.putString(DBKey.LANGUAGE, s.substring("/languages/".length()));
            }
        }
    }

    private void processPublishers(@NonNull final JSONArray a,
                                   @NonNull final Book book) {
        String name;
        for (int ai = 0; ai < a.length(); ai++) {
            name = a.optString(ai);
            if (name != null && !name.isEmpty()) {
                book.add(Publisher.from(name));
            }
        }
    }

    private void processIdentifiers(@NonNull final JSONObject element,
                                    @NonNull final Book book) {

        JSONArray a;

        // see if we have a better isbn.
        a = element.optJSONArray("isbn_13");
        if (a != null && !a.isEmpty()) {
            book.putString(DBKey.BOOK_ISBN, a.getString(0));
        } else {
            a = element.optJSONArray("isbn_10");
            if (a != null && !a.isEmpty()) {
                book.putString(DBKey.BOOK_ISBN, a.getString(0));
            }
        }
        a = element.optJSONArray("amazon");
        if (a != null && !a.isEmpty()) {
            book.putString(DBKey.SID_ASIN, a.getString(0));
        }
        a = element.optJSONArray("openlibrary");
        if (a != null && !a.isEmpty()) {
            book.putString(DBKey.SID_OPEN_LIBRARY, a.getString(0));
        }
        a = element.optJSONArray("librarything");
        if (a != null && !a.isEmpty()) {
            book.putLong(DBKey.SID_LIBRARY_THING, a.getLong(0));
        }
        a = element.optJSONArray("goodreads");
        if (a != null && !a.isEmpty()) {
            book.putLong(DBKey.SID_GOODREADS_BOOK, a.getLong(0));
        }
        a = element.optJSONArray("google");
        if (a != null && !a.isEmpty()) {
            book.putString(DBKey.SID_GOOGLE, a.getString(0));
        }
        a = element.optJSONArray("lccn");
        if (a != null && !a.isEmpty()) {
            book.putString(DBKey.SID_LCCN, a.getString(0));
        }

        a = element.optJSONArray("oclc_numbers");
        if (a != null && !a.isEmpty()) {
            book.putString(DBKey.SID_OCLC, a.getString(0));
        }
        // legacy key name
        a = element.optJSONArray("oclc");
        if (a != null && !a.isEmpty()) {
            book.putString(DBKey.SID_OCLC, a.getString(0));
        }
    }

    private void processToc(@NonNull final Context context,
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
                final String title = element.optString("title");
                if (title != null && !title.isEmpty()) {
                    toc.add(new TocEntry(tocAuthor, title));
                }
            }
        }

        if (!toc.isEmpty()) {
            book.setToc(toc);
            if (toc.size() > 1) {
                book.putLong(DBKey.BOOK_CONTENT_TYPE,
                             Book.ContentType.Collection.getId());
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
     * and continue in {@link #parseWorks(JSONObject)}.
     *
     * @param context   Current context
     * @param validIsbn to search for, <strong>must</strong> be valid.
     */
    @NonNull
    @Override
    public List<AltEditionOpenLibrary> searchAlternativeEditions(@NonNull final Context context,
                                                                 @NonNull final String validIsbn)
            throws SearchException, CredentialsException {

        return fetchEditionsByIsbn(context, validIsbn);
    }

    @VisibleForTesting
    @NonNull
    List<AltEditionOpenLibrary> fetchEditionsByIsbn(@NonNull final Context context,
                                                    @NonNull final String validIsbn)
            throws SearchException {
        futureHttpGet = createFutureGetRequest(context);

        String url = getHostUrl(context) + "/isbn/" + validIsbn + ".json";

        String response;
        try {
            // get and store the result into a string.
            response = futureHttpGet.get(url, (con, is) -> readResponseStream(is));

            final JSONObject jsonObject = new JSONObject(response);
            final JSONArray works = jsonObject.optJSONArray("works");
            if (works != null && !works.isEmpty()) {
                final String worksKey = works.getJSONObject(0).optString("key");
                url = getHostUrl(context) + worksKey + "/editions.json";
                response = futureHttpGet.get(url, (con, is) -> readResponseStream(is));
                return parseWorks(new JSONObject(response));
            }
        } catch (@NonNull final StorageException | IOException | JSONException e) {
            throw new SearchException(getEngineId(), e);
        } finally {
            futureHttpGet = null;
        }

        return List.of();
    }

    /**
     * <pre>
     *     {
     *         ...
     *         "size": 87,
     *          "entries": [
     *     {
     *       "works": [
     *         {
     *           "key": "/works/OL5725956W"
     *         }
     *       ],
     *       "title": "Artemis Fowl",
     *       "publishers": [
     *         "Carlsen"
     *       ],
     *       "publish_date": "2009",
     *       "key": "/books/OL49350279M",
     *       "type": {
     *         "key": "/type/edition"
     *       },
     *       "identifiers": {},
     *       "covers": [
     *         14414864
     *       ],
     *       "isbn_13": [
     *         "9783551357793"
     *       ],
     *       "classifications": {},
     *       "physical_format": "Taschenbuch",
     *       "translation_of": "Artemis Fowl",
     *       "languages": [
     *         {
     *           "key": "/languages/ger"
     *         }
     *       ],
     *       "copyright_date": "2001; 2003, 2004",
     *       "edition_name": "2. Auflage",
     *       "translated_from": [
     *         {
     *           "key": "/languages/eng"
     *         }
     *       ],
     *       "number_of_pages": 237,
     *       "latest_revision": 3,
     *       "revision": 3,
     *       "created": {
     *         "type": "/type/datetime",
     *         "value": "2023-08-26T12:46:10.568538"
     *       },
     *       "last_modified": {
     *         "type": "/type/datetime",
     *         "value": "2023-08-26T12:48:34.820086"
     *       }
     *     },
     *     .... and 86 more
     *     }
     * </pre>
     *
     * @param works object
     *
     * @return the list with Editions.
     */
    @NonNull
    private List<AltEditionOpenLibrary> parseWorks(@NonNull final JSONObject works) {
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
            final JSONObject work = entries.optJSONObject(i);
            if (work != null) {
                String olid;
                String isbn = null;
                String langIso3 = null;
                String publisher = null;
                final long[] covers = new long[2];

                JSONArray a;
                JSONObject o;

                olid = work.optString("key");
                if (olid != null && olid.startsWith("/books/")) {
                    olid = olid.substring("/books/".length());
                }

                a = work.optJSONArray("isbn_13");
                if (a != null && !a.isEmpty()) {
                    isbn = a.optString(0);
                }
                if (isbn == null || isbn.isEmpty()) {
                    a = work.optJSONArray("isbn_10");
                    if (a != null && !a.isEmpty()) {
                        isbn = a.optString(0);
                    }
                }

                a = work.optJSONArray("languages");
                if (a != null && !a.isEmpty()) {
                    o = a.optJSONObject(0);
                    if (o != null) {
                        langIso3 = o.optString("key");
                        if (langIso3 != null && langIso3.startsWith("/languages/")) {
                            langIso3 = langIso3.substring("/languages/".length());
                        }
                    }
                }
                a = work.optJSONArray("publishers");
                if (a != null && !a.isEmpty()) {
                    publisher = a.optString(0);
                }
                a = work.optJSONArray("covers");
                if (a != null && !a.isEmpty()) {
                    covers[0] = a.optInt(0);
                    if (a.length() > 1) {
                        covers[1] = a.optInt(1);
                    }
                }
                if (olid != null && !olid.isEmpty()) {
                    editionList.add(new AltEditionOpenLibrary(olid, isbn, langIso3, publisher,
                                                              covers));
                }
            }
        }

        return editionList;
    }
}
