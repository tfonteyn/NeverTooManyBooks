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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * <a href="https://openlibrary.org/dev/docs/api/books">API</a>
 * <p>
 * TLDR: works, but data not complete or not stable (maybe I am to harsh though)
 * <ol>
 *     <li>The Works API (by Work ID)</li>
 *     <li>The Editions API (by Edition ID)</li>
 *     <li>The ISBN API (by ISBN)
 *          <br>Pro: non-english books have a language tag embedded
 *          <br>Con: No Authors embedded (links instead), no covers (id's, but no sizes),
 *              key/values different from api 4 (below)
 *     </li>
 *     <li>The Books API (generic) - what we use; see below</li>
 * </ol>
 *
 * <a href="https://openlibrary.org/dev/docs/api/books">API books</a>
 * Allows searching by all identifiers. Example isbn:  bibkeys=ISBN:0201558025
 * <ul>
 *      <li>response format: jscmd=data:<br>
 *          Does not return all the info that is known to be present.
 *          (use the website itself to look up an isbn)
 *          Example: "physical_format": "Paperback" is NOT part of the response;
 *          Example: language info is missing
 *      </li>
 *      <li>response format: jscmd=detail:<br>
 *          The docs state: "It is advised to use jscmd=data instead of this as that is
 *          more stable format."
 *          The response seems to (mostly?) contain the same info as from 'data' but with
 *          additional fields. Some fields have a different schema: "identifiers" with
 *          "data" has sub object with all identifiers (including isbn).
 *          But "identifiers" with "detail" has no isbn number.
 *          Instead isbn numbers are on the same level as "identifiers" itself.<br>
 *          PRO: contains language and series tags!
 *      </li>
 * </ul>
 * <ul>Problems:
 *      <li>"data" does not contain all information that the site has</li>
 *      <li>"details" seems, by their own admission, not to be stable yet.</li>
 *      <li>both: dates are not structured, but {@link FullDateParser} can work around that.</li>
 *      <li>last update dates on the website & api docs are sometimes from years ago.
 * Is this still developed ?</li>
 * </ul>
 * Implementing SearchEngine.ByText is possible, but an issue is how much data could be returned.
 * Example: {@code https://openlibrary.org/search.json?author=tolkien&title=hobbit}
 * will return a 9000+ lines long document...
 * <p>
 *  Below is a rudimentary "data" implementation. "details" was tested with curl.
 */
public class OpenLibrarySearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ViewBookByExternalId,
                   SearchEngine.CoverByIsbn {

    /**
     * bibkeys
     * <p>
     * List of IDs to request the information.
     * The API supports ISBNs, LCCNs, OCLC numbers and OLIDs (Open Library IDs).
     * <p>
     * ISBN
     * <p>
     * Ex. &bibkeys=ISBN:0451526538 (The API supports both ISBN 10 and 13.)
     * OCLC
     * <p>
     * &bibkeys=OCLC:#########
     * LCCN
     * <p>
     * &bibkeys=LCCN:#########
     * OLID
     * <p>
     * &bibkeys=OLID:OL123M
     * <p>
     * param 1: key-name, param 2: key-value
     * <p>
     * {@link #handleResponse} only tested with ISBN and OLID for now.
     */
    private static final String BASE_BOOK_URL =
            "/api/books?jscmd=data&format=json&bibkeys=%1$s:%2$s";

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

    /** The search keys in the json object we support: ISBN, external id. */
    private static final String SUPPORTED_KEYS = "ISBN,OLID";
    @Nullable
    private FutureHttpGet<String> futureHttpGet;

    /**
     * Constructor. Called using reflection, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public OpenLibrarySearchEngine(@NonNull final Context appContext,
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
            throws StorageException, SearchException {

        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(BASE_BOOK_URL, "OLID", externalId);

        fetchBook(context, url, fetchCovers, book);
        return book;
    }

    /**
     * <a href="https://openlibrary.org/dev/docs/api/books">API books</a>.
     *
     * <br><br>{@inheritDoc}
     */
    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException {

        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(BASE_BOOK_URL, "ISBN", validIsbn);

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
     * <br><br>{@inheritDoc}
     */
    @Nullable
    @Override
    @WorkerThread
    public String searchCoverByIsbn(@NonNull final Context context,
                                    @NonNull final String validIsbn,
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

        final String url = String.format(BASE_COVER_URL, "isbn", validIsbn, sizeParam);
        return saveImage(context, url, validIsbn, cIdx, size);
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
            throws StorageException, SearchException {

        futureHttpGet = createFutureGetRequest(context, true);

        try {
            // get and store the result into a string.
            final String json = futureHttpGet.get(url, (con, is) ->
                    readResponseStream(is));

            if (handleResponse(context, json, fetchCovers, book)) {
                checkForSeriesNameInTitle(book);
            }

        } catch (@NonNull final IOException e) {
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
     * A search on ISBN returns:
     *
     * <pre>{@code
     *  "ISBN:9780980200447": {
     *    "publishers": [{"name": "Litwin Books"}],
     *    "pagination": "80p.",
     *    "identifiers": {
     *      "google": ["4LQU1YwhY6kC"],
     *      "lccn": ["2008054742"],
     *      "openlibrary": ["OL22853304M"],
     *      "isbn_13": ["9780980200447", "9781936117369"],
     *      "amazon": ["098020044X"],
     *      "isbn_10": ["1936117363"],
     *      "oclc": ["297222669"],
     *      "goodreads": ["6383507"],
     *      "librarything": ["8071257"]
     *    },
     *    "table_of_contents": [
     *      {"title": "The personal nature of slow reading",
     *        "label": "", "pagenum": "", "level": 0},
     *      {"title": "Slow reading in an information ecology",
     *        "label": "", "pagenum": "", "level": 0},
     *      {"title": "The slow movement and slow reading",
     *        "label": "", "pagenum": "", "level": 0},
     *      {"title": "The psychology of slow reading",
     *        "label": "", "pagenum": "", "level": 0},
     *      {"title": "The practice of slow reading.",
     *        "label": "", "pagenum": "", "level": 0}
     *    ],
     *    "links": [
     *      {"url": "http:\/\/johnmiedema.ca",
     *        "title": "Author's Website"
     *      },
     *      {"url": "http:\/\/litwinbooks.com\/slowreading-ch2.php",
     *        "title": "Chapter 2"
     *      },
     *      {"url": "http:\/\/www.powells.com\/biblio\/91-9781936117369-0",
     *        "title": "Get the e-book"
     *      }
     *    ],
     *    "weight": "1 grams",
     *    "title": "Slow reading",
     *    "url": "https:\/\/openlibrary.org\/books\/OL22853304M\/Slow_reading",
     *    "classifications": {
     *      "dewey_decimal_class": ["028\/.9"],
     *      "lc_classifications": ["Z1003 .M58 2009"]
     *    },
     *    "notes": "Includes bibliographical references and index.",
     *    "number_of_pages": 92,
     *    "cover": {
     *      "small": "https:\/\/covers.openlibrary.org\/b\/id\/5546156-S.jpg",
     *      "large": "https:\/\/covers.openlibrary.org\/b\/id\/5546156-L.jpg",
     *      "medium": "https:\/\/covers.openlibrary.org\/b\/id\/5546156-M.jpg"
     *    },
     *    "subjects": [{
     *        "url": "https:\/\/openlibrary.org\/subjects\/books_and_reading",
     *        "name": "Books and reading"},
     *      {"url": "https:\/\/openlibrary.org\/subjects\/in_library",
     *        "name": "In library"},
     *      {"url": "https:\/\/openlibrary.org\/subjects\/reading",
     *        "name": "Reading"}
     *    ],
     *    "publish_date": "March 2009",
     *    "key": "\/books\/OL22853304M",
     *    "authors": [{
     *        "url": "https:\/\/openlibrary.org\/authors\/OL6548935A\/John_Miedema",
     *        "name": "John Miedema"
     *      }],
     *    "by_statement": "by John Miedema.",
     *    "publish_places": [{
     *        "name": "Duluth, Minn"}],
     *    "ebooks": [{
     *        "checkedout": true,
     *        "formats": {},
     *        "preview_url": "https:\/\/archive.org\/details\/slowreading00mied",
     *        "borrow_url": "https:\/\/openlibrary.org\/books\/OL22853304M\/Slow_reading\/borrow",
     *        "availability": "borrow"
     *      }
     *    ]
     *       }
     *     }
     * }</pre>
     * <p>
     * A search on OLID returns:
     * <pre>{@code
     *  {
     *    "OLID:OL10393624M":{
     *      "publishers":[{"name":"Tor Books"}],
     *      "identifiers":{
     *          "openlibrary":["OL10393624M"],
     *          "isbn_13":["9780312859664"],
     *          "isbn_10":["031285966X"],
     *          "oclc":["32665311"],
     *          "librarything":["81766"],
     *          "goodreads":["872340"]},
     *      "subtitle":"Alastor 1716",
     *      "weight":"1.4 pounds",
     *      "title":"Alastor: Trullion : Alastor 2262  Marune : Alastor 933 Wyst ",
     *      "url":"https:\/\/openlibrary.org\/books\/OL10393624M\/
     *                Alastor_Trullion_Alastor_2262_Marune_Alastor_933_Wyst",
     *      "number_of_pages":479,
     *      "cover":{
     *          "small":"https:\/\/covers.openlibrary.org\/b\/id\/5059003-S.jpg",
     *          "large":"https:\/\/covers.openlibrary.org\/b\/id\/5059003-L.jpg",
     *          "medium":"https:\/\/covers.openlibrary.org\/b\/id\/5059003-M.jpg"},
     *      "subjects":[{
     *          "url":"https:\/\/openlibrary.org\/subjects\/internet_archive_wishlist",
     *          "name":"Internet Archive Wishlist"}],
     *      "publish_date":"September 1995",
     *      "key":"\/books\/OL10393624M",
     *      "authors":[{
     *          "url":"https:\/\/openlibrary.org\/authors\/OL253641A\/Jack_Vance",
     *          "name":"Jack Vance"}]
     *    }
     *  }
     * }</pre>
     * The keys (jsonObject.keys()) are:
     * "ISBN:9780980200447"
     *
     * @param context     Current context
     * @param response    the complete response; a String containing JSON
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @return {@code true} on success, {@code false} if we were cancelled.
     *
     * @throws StorageException on storage related failures
     * @throws SearchException  on generic exceptions (wrapped) during search
     */
    @VisibleForTesting
    boolean handleResponse(@NonNull final Context context,
                           @NonNull final String response,
                           @NonNull final boolean[] fetchCovers,
                           @NonNull final Book book)
            throws StorageException,
                   SearchException {

        try {
            final JSONObject jsonObject = new JSONObject(response);

            final Iterator<String> it = jsonObject.keys();
            // we only handle the first result for now.
            if (it.hasNext()) {
                if (isCancelled()) {
                    return false;
                }

                final String topLevelKey = it.next();
                final String[] data = topLevelKey.split(":");
                if (data.length == 2 && SUPPORTED_KEYS.contains(data[0])) {
                    parse(context, data[1],
                          jsonObject.getJSONObject(topLevelKey),
                          fetchCovers,
                          book);
                }
            }
        } catch (@NonNull final JSONException e) {
            throw new SearchException(getEngineId(), e);
        }

        return true;
    }

    /**
     * Parse the results, and build the book.
     *
     * @param context     Current context
     * @param validIsbn   of the book
     * @param document    JSON result data
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws StorageException on storage related failures
     */
    private void parse(@NonNull final Context context,
                       @NonNull final String validIsbn,
                       @NonNull final JSONObject document,
                       @NonNull final boolean[] fetchCovers,
                       @NonNull final Book book)
            throws StorageException {

        final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
        final List<Locale> locales = LocaleListUtils.asList(context, getLocale(context));

        final DateParser dateParser = new FullDateParser(systemLocale, locales);

        // store the isbn; we might override it later on though (e.g. isbn 13v10)
        // not sure if this is needed though. Need more data.
        book.putString(DBKey.BOOK_ISBN, validIsbn);

        JSONObject element;
        JSONArray a;
        String s;
        final int i;

        s = document.optString("title");
        if (s != null && !s.isEmpty()) {
            book.putString(DBKey.TITLE, s);
        }

        // s = document.optString("subtitle");

        a = document.optJSONArray("authors");
        if (a != null && !a.isEmpty()) {
            for (int ai = 0; ai < a.length(); ai++) {
                element = a.optJSONObject(ai);
                if (element != null) {
                    final String name = element.optString("name");
                    if (name != null && !name.isEmpty()) {
                        processAuthor(Author.from(name), Author.TYPE_UNKNOWN, book);
                    }
                }
            }
        }

        // s = jsonObject.optString("pagination");
        // if (!s.isEmpty()) {
        //     book.putString(DBDefinitions.KEY_PAGES, s);
        // } else {
        i = document.optInt("number_of_pages");
        if (i > 0) {
            book.putString(DBKey.PAGE_COUNT, String.valueOf(i));
        }
        // }

        element = document.optJSONObject("identifiers");
        if (element != null) {
            processIdentifiers(element, book);
        }

        a = document.optJSONArray("publishers");
        if (a != null && !a.isEmpty()) {
            processPublishers(a, book);
        }

        s = document.optString("publish_date");
        if (s != null && !s.isEmpty()) {
            final LocalDateTime date = dateParser.parse(s, getLocale(context));
            if (date != null) {
                book.putString(DBKey.BOOK_PUBLICATION__DATE,
                               date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }

        // "subjects": [
        //            {
        //                "name": "History",
        //                "url": "https://openlibrary.org/subjects/history"
        //            },
        // could be used for genres... but the subject list for a single book can be very large


        // "notes" is a specific (set of) remarks on this particular edition of the book.
        s = document.optString("notes");
        if (s != null && !s.isEmpty()) {
            book.putString(DBKey.DESCRIPTION, s);
        }


        // always use the first author only for TOC entries.
        final Author primAuthor = book.getPrimaryAuthor();
        a = document.optJSONArray("table_of_contents");
        if (a != null && !a.isEmpty()) {
            final List<TocEntry> toc = new ArrayList<>();
            for (int ai = 0; ai < a.length(); ai++) {
                element = a.optJSONObject(ai);
                if (element != null) {
                    final String title = element.optString("title");
                    if (title != null && !title.isEmpty()) {
                        //noinspection DataFlowIssue
                        toc.add(new TocEntry(primAuthor, title));
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

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final ArrayList<String> list = parseCovers(context, document, validIsbn, 0);
            if (!list.isEmpty()) {
                book.putStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0], list);
            }
        }
    }

    private void processPublishers(@NonNull final JSONArray a,
                                   @NonNull final Book book) {
        JSONObject element;
        for (int ai = 0; ai < a.length(); ai++) {
            element = a.optJSONObject(ai);
            if (element != null) {
                final String name = element.optString("name");
                if (name != null && !name.isEmpty()) {
                    book.add(Publisher.from(name));
                }
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
        a = element.optJSONArray("oclc");
        if (a != null && !a.isEmpty()) {
            book.putString(DBKey.SID_OCLC, a.getString(0));
        }
    }

    @NonNull
    private ArrayList<String> parseCovers(@NonNull final Context context,
                                          @NonNull final JSONObject element,
                                          @NonNull final String validIsbn,
                                          @SuppressWarnings("SameParameterValue")
                                          @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        final ArrayList<String> list = new ArrayList<>();

        // get the largest cover image available.
        final JSONObject o = element.optJSONObject("cover");
        if (o != null) {
            Size size = Size.Large;
            String coverUrl = o.optString("large");
            if (coverUrl == null || coverUrl.isEmpty()) {
                size = Size.Medium;
                coverUrl = o.optString("medium");
                if (coverUrl == null || coverUrl.isEmpty()) {
                    size = Size.Small;
                    coverUrl = o.optString("small");
                }
            }

            // we assume that the download will work if there is a url.
            if (coverUrl != null && !coverUrl.isEmpty()) {
                final String fileSpec = saveImage(context, coverUrl, validIsbn, cIdx, size);
                if (fileSpec != null) {
                    list.add(fileSpec);
                }
            }
        }

        return list;
    }
}
