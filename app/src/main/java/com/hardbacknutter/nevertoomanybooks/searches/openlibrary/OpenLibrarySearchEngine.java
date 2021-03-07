/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches.openlibrary;

import android.os.Bundle;

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
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * <a href="https://openlibrary.org/developers/api">API</a>
 * <p>
 * Initial testing... TLDR: works, but data not complete or not stable (maybe I am to harsh though)
 * <p>
 * <a href="https://openlibrary.org/dev/docs/api/books">API books</a>
 * - allows searching by all identifiers. Example isbn:  bibkeys=ISBN:0201558025
 * <ul>
 *      <li>response format: jscmd=data:<br>
 *          Does not return all the info that is known to be present.
 *          (use the website itself to look up an isbn)
 *          Example: "physical_format": "Paperback" is NOT part of the response.</li>
 *      <li>response format: jscmd=detail:<br>
 *          The docs state: "It is advised to use jscmd=data instead of this as that is
 *          more stable format."
 *          The response seems to (mostly?) contain the same info as from 'data' but with
 *          additional fields. Some fields have a different schema: "identifiers" with
 *          "data" has sub object with all identifiers (including isbn).
 *          But "identifiers" with "detail" has no isbn's.
 *          Instead isbn's are on the same level as "identifiers" itself.</li>
 * </ul>
 * <ul>Problems:
 *      <li>"data" does not contain all information that the site has.</li>
 *      <li>"details" seems, by their own admission, not to be stable yet.</li>
 *      <li>both: dates are not structured, but {@link DateParser} can work around that.</li>
 *      <li>last update dates on the website & api docs are sometimes from years ago.
 * Is this still developed ?</li>
 * </ul>
 * Below is a rudimentary "data" implementation. "details" was tested with curl.
 */
public class OpenLibrarySearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
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
     * http://covers.openlibrary.org/b/$key/$value-$size.jpg
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

    /**
     * Constructor. Called using reflection, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param engineId the search engine id
     */
    @Keep
    public OpenLibrarySearchEngine(@SearchSites.EngineId final int engineId) {
        super(engineId);
    }

    public static SearchEngineRegistry.Config createConfig() {
        return new SearchEngineRegistry.Config.Builder(OpenLibrarySearchEngine.class,
                                                       SearchSites.OPEN_LIBRARY,
                                                       R.string.site_open_library,
                                                       "openlibrary",
                                                       "https://openlibrary.org")

                .setSupportsMultipleCoverSizes(true)
                .setFilenameSuffix("OL")

                .setDomainKey(DBKeys.KEY_ESID_OPEN_LIBRARY)
                .setDomainViewId(R.id.site_open_library)
                .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY)
                .build();
    }

    @NonNull
    @Override
    public String createUrl(@NonNull final String externalId) {
        return getSiteUrl() + "/books/" + externalId;
    }

    @NonNull
    @Override
    public Bundle searchByExternalId(@NonNull final String externalId,
                                     @NonNull final boolean[] fetchThumbnail)
            throws GeneralParsingException, IOException {

        final Bundle bookData = new Bundle();

        final String url = getSiteUrl() + String.format(BASE_BOOK_URL, "OLID", externalId);
        fetchBook(url, fetchThumbnail, bookData);
        return bookData;
    }

    /**
     * <a href="https://openlibrary.org/dev/docs/api/books">API books</a>.
     *
     * <br><br>{@inheritDoc}
     */
    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws GeneralParsingException, IOException {

        final Bundle bookData = new Bundle();

        final String url = getSiteUrl() + String.format(BASE_BOOK_URL, "ISBN", validIsbn);
        fetchBook(url, fetchThumbnail, bookData);
        return bookData;
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
    public String searchCoverImageByIsbn(@NonNull final String validIsbn,
                                         @IntRange(from = 0, to = 1) final int cIdx,
                                         @Nullable final ImageFileInfo.Size size) {
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
        return saveImage(url, validIsbn, cIdx, size);
    }


    private void fetchBook(@NonNull final String url,
                           @NonNull final boolean[] fetchThumbnail,
                           @NonNull final Bundle bookData)
            throws GeneralParsingException, IOException {
        // get and store the result into a string.
        final String response;
        try (TerminatorConnection con = createConnection(url);
             InputStream is = con.getInputStream()) {
            response = readResponseStream(is);
        }

        if (isCancelled()) {
            return;
        }

        // json-ify and handle.
        try {
            handleResponse(new JSONObject(response), fetchThumbnail, bookData);
        } catch (@NonNull final JSONException e) {
            throw new GeneralParsingException(e);
        }

        checkForSeriesNameInTitle(bookData);
    }

    /**
     * Read the entire InputStream into a String.
     *
     * @param is to read
     *
     * @return the entire content
     *
     * @throws IOException on any failure
     */
    @VisibleForTesting
    @NonNull
    String readResponseStream(@NonNull final InputStream is)
            throws IOException {
        // Don't close this stream!
        final InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        final BufferedReader reader = new BufferedReader(isr);

        try {
            return reader.lines().collect(Collectors.joining());
        } catch (@NonNull final UncheckedIOException e) {
            //noinspection ConstantConditions
            throw e.getCause();
        }
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
     * @param jsonObject     the complete book record.
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to update
     *
     * @throws JSONException upon any error
     */
    @VisibleForTesting
    void handleResponse(@NonNull final JSONObject jsonObject,
                        @NonNull final boolean[] fetchThumbnail,
                        @NonNull final Bundle bookData)
            throws JSONException {

        final Iterator<String> it = jsonObject.keys();
        // we only handle the first result for now.
        if (it.hasNext()) {
            final String topLevelKey = it.next();
            final String[] data = topLevelKey.split(":");
            if (data.length == 2 && SUPPORTED_KEYS.contains(data[0])) {
                parse(data[1],
                      jsonObject.getJSONObject(topLevelKey),
                      fetchThumbnail,
                      bookData);
            }
        }
    }

    /**
     * Parse the results, and build the bookData bundle.
     *
     * @param validIsbn      of the book
     * @param document       JSON result data
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to update
     *
     * @throws JSONException upon any error
     */
    private void parse(@NonNull final String validIsbn,
                       @NonNull final JSONObject document,
                       @NonNull final boolean[] fetchThumbnail,
                       @NonNull final Bundle bookData)
            throws JSONException {

        JSONObject element;
        JSONArray a;
        String s;
        final int i;

        s = document.optString("title");
        if (!s.isEmpty()) {
            bookData.putString(DBKeys.KEY_TITLE, s);
        }

        final ArrayList<Author> authors = new ArrayList<>();
        a = document.optJSONArray("authors");
        if (a != null && !a.isEmpty()) {
            for (int ai = 0; ai < a.length(); ai++) {
                element = a.optJSONObject(ai);
                final String name = element.optString("name");
                if (!name.isEmpty()) {
                    authors.add(Author.from(name));
                }
            }
        }
        if (!authors.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authors);
        }

        // store the isbn; we might override it later on though (e.g. isbn 13v10)
        // not sure if this is needed though. Need more data.
        bookData.putString(DBKeys.KEY_ISBN, validIsbn);

        // everything below is optional.

        // "notes" is a specific (set of) remarks on this particular edition of the book.
        s = document.optString("notes");
        if (!s.isEmpty()) {
            bookData.putString(DBKeys.KEY_DESCRIPTION, s);
        }

        s = document.optString("publish_date");
        if (!s.isEmpty()) {
            final LocalDateTime date =
                    DateParser.getInstance(getContext()).parse(s, getLocale());
            if (date != null) {
                bookData.putString(DBKeys.KEY_BOOK_DATE_PUBLISHED,
                                   date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }

//        s = jsonObject.optString("pagination");
//        if (!s.isEmpty()) {
//            bookData.putString(DBDefinitions.KEY_PAGES, s);
//        } else {
        i = document.optInt("number_of_pages");
        if (i > 0) {
            bookData.putString(DBKeys.KEY_PAGES, String.valueOf(i));
        }
//        }

        element = document.optJSONObject("identifiers");
        if (element != null) {
            processIdentifiers(element, bookData);
        }

        if (isCancelled()) {
            return;
        }

        if (fetchThumbnail[0]) {
            final ArrayList<String> imageList = parseCovers(document, validIsbn, 0);
            if (!imageList.isEmpty()) {
                bookData.putStringArrayList(SearchCoordinator.BKEY_TMP_FILE_SPEC_ARRAY[0],
                                            imageList);
            }
        }

        if (isCancelled()) {
            return;
        }

        a = document.optJSONArray("publishers");
        if (a != null && !a.isEmpty()) {
            processPublishers(a, bookData);
        }

        // always use the first author only for TOC entries.
        a = document.optJSONArray("table_of_contents");
        if (a != null && !a.isEmpty()) {
            final ArrayList<TocEntry> toc = new ArrayList<>();
            for (int ai = 0; ai < a.length(); ai++) {
                element = a.optJSONObject(ai);
                final String title = element.optString("title");
                if (!title.isEmpty()) {
                    toc.add(new TocEntry(authors.get(0), title, null));
                }
            }

            if (!toc.isEmpty()) {
                bookData.putParcelableArrayList(Book.BKEY_TOC_LIST, toc);
                if (toc.size() > 1) {
                    bookData.putLong(DBKeys.KEY_TOC_BITMASK, Book.TOC_MULTIPLE_WORKS);
                }
            }
        }
    }

    private void processPublishers(@NonNull final JSONArray a,
                                   @NonNull final Bundle bookData) {
        JSONObject element;
        final ArrayList<Publisher> publishers = new ArrayList<>();
        for (int ai = 0; ai < a.length(); ai++) {
            element = a.optJSONObject(ai);
            final String name = element.optString("name");
            if (!name.isEmpty()) {
                publishers.add(Publisher.from(name));
            }
        }
        if (!publishers.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publishers);
        }
    }

    private void processIdentifiers(@NonNull final JSONObject element,
                                    @NonNull final Bundle bookData)
            throws JSONException {

        JSONArray a;

        // see if we have a better isbn.
        a = element.optJSONArray("isbn_13");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKeys.KEY_ISBN, a.getString(0));
        } else {
            a = element.optJSONArray("isbn_10");
            if (a != null && !a.isEmpty()) {
                bookData.putString(DBKeys.KEY_ISBN, a.getString(0));
            }
        }
        a = element.optJSONArray("amazon");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKeys.KEY_ESID_ASIN, a.getString(0));
        }
        a = element.optJSONArray("openlibrary");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKeys.KEY_ESID_OPEN_LIBRARY, a.getString(0));
        }
        a = element.optJSONArray("librarything");
        if (a != null && !a.isEmpty()) {
            bookData.putLong(DBKeys.KEY_ESID_LIBRARY_THING, a.getLong(0));
        }
        a = element.optJSONArray("goodreads");
        if (a != null && !a.isEmpty()) {
            bookData.putLong(DBKeys.KEY_ESID_GOODREADS_BOOK, a.getLong(0));
        }
        a = element.optJSONArray("google");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKeys.KEY_ESID_GOOGLE, a.getString(0));
        }
        a = element.optJSONArray("lccn");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKeys.KEY_ESID_LCCN, a.getString(0));
        }
        a = element.optJSONArray("oclc");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKeys.KEY_ESID_OCLC, a.getString(0));
        }
    }

    private ArrayList<String> parseCovers(@NonNull final JSONObject element,
                                          @NonNull final String validIsbn,
                                          @SuppressWarnings("SameParameterValue")
                                          @IntRange(from = 0, to = 1) final int cIdx) {

        final ArrayList<String> imageList = new ArrayList<>();

        // get the largest cover image available.
        final JSONObject o = element.optJSONObject("cover");
        if (o != null) {
            ImageFileInfo.Size size = ImageFileInfo.Size.Large;
            String coverUrl = o.optString("large");
            if (coverUrl.isEmpty()) {
                size = ImageFileInfo.Size.Medium;
                coverUrl = o.optString("medium");
                if (coverUrl.isEmpty()) {
                    size = ImageFileInfo.Size.Small;
                    coverUrl = o.optString("small");
                }
            }

            // we assume that the download will work if there is a url.
            if (!coverUrl.isEmpty()) {
                final String fileSpec = saveImage(coverUrl, validIsbn, cIdx, size);
                if (fileSpec != null) {
                    imageList.add(fileSpec);
                }
            }
        }

        return imageList;
    }
}
