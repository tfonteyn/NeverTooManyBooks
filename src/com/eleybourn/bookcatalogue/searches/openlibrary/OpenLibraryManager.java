package com.eleybourn.bookcatalogue.searches.openlibrary;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.searches.SearchEngine;
import com.eleybourn.bookcatalogue.tasks.TerminatorConnection;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ISBN;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * https://openlibrary.org/developers/api
 * <p>
 * Initial testing... TLDR: works, but data not complete or not stable (maybe I am to harsh though)
 * <p>
 * https://openlibrary.org/dev/docs/api/books
 * - allows searching by all identifiers. Example isbn:  bibkeys=ISBN:0201558025
 * <ul>
 * <li> response format: jscmd=data:<br>
 * Does not return all the info that is known to be present.
 * (use the website itself to look up an isbn)
 * Example: "physical_format": "Paperback" is NOT part of the response.</li>
 * <li>response format: jscmd=detail:<br>
 * The docs state: "It is advised to use jscmd=data instead of this as that is more stable format."
 * The response seems to (mostly?) contain the same info as from 'data' but with additional fields.
 * Some fields have a different schema: "identifiers" with "data" has sub object with ALL
 * identifiers (including isbn). But "identifiers" with "detail" has no isbn's.
 * Instead isbn's are on the same level as "identifiers" itself.</li>
 * </ul>
 * Problems:
 * <ul>
 * <li>"data" does not contain all information that the site has.</li>
 * <li>"details" seems, by their own admission, not to be stable yet.</li>
 * <li>both: dates are not structured, but {@link DateUtils#parseDate(String)}
 * can work around that.</li>
 * <li>last update dates on the website & api docs are sometimes from years ago.
 * Is this still developed ?</li>
 * </ul>
 * Below is a rudimentary "data" implementation. "details" was tested with curl.
 */
public class OpenLibraryManager
        implements SearchEngine {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "OpenLibrary.";

    private static final String PREFS_HOST_URL = PREF_PREFIX + "hostUrl";

    /** param 1: isbn, param 2: L/M/S for the size. */
    private static final String BASE_URL_COVERS
            = "https://covers.openlibrary.com/b/isbn/%1$s-%2$s.jpg?default=false";

    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_OL";

    /**
     * Constructor.
     */
    public OpenLibraryManager() {
    }

    @NonNull
    public static String getBaseURL() {
        //noinspection ConstantConditions
        return App.getPrefs().getString(PREFS_HOST_URL, "https://openlibrary.org");
    }

    public static void openWebsite(@NonNull final Context context,
                                   @NonNull final String bookId) {
        String url = getBaseURL() + "/books/" + bookId;
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    /**
     * https://openlibrary.org/dev/docs/api/covers
     * <p>
     * http://covers.openlibrary.org/b/isbn/0385472579-S.jpg?default=false
     * <p>
     * S/M/L
     *
     * @param isbn to search for
     * @param size of image to get.
     *
     * @return found/saved File, or {@code null} when none found (or any other failure)
     */
    @Nullable
    @Override
    @WorkerThread
    public File getCoverImage(@NonNull final String isbn,
                              @Nullable final ImageSizes size) {

        // sanity check
        if (!ISBN.isValid(isbn)) {
            return null;
        }

        String sizeParam;
        if (size == null) {
            sizeParam = "L";
        } else {
            switch (size) {
                case SMALL:
                    sizeParam = "S";
                    break;
                case MEDIUM:
                    sizeParam = "M";
                    break;
                case LARGE:
                    sizeParam = "L";
                    break;

                default:
                    sizeParam = "L";
                    break;
            }
        }

        // Fetch, then save it with a suffix
        String fileSpec = ImageUtils.saveImage(String.format(BASE_URL_COVERS, isbn, sizeParam),
                                               FILENAME_SUFFIX + '_' + isbn + '_' + size);
        if (fileSpec != null) {
            return new File(fileSpec);
        }
        return null;

    }

    @Override
    @WorkerThread
    public boolean isAvailable() {
        return NetworkUtils.isAlive(getBaseURL());
    }

    /**
     * {@link #search(String, String, String, boolean)} only implements isbn searches for now.
     *
     * @return {@code true}
     */
    @Override
    public boolean isIsbnOnly() {
        return true;
    }

    @Override
    public boolean siteSupportsMultipleSizes() {
        return true;
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.open_library;
    }

    /**
     * https://openlibrary.org/dev/docs/api/books
     *
     * <br>
     * <br>{@inheritDoc}
     */
    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@Nullable final String isbn,
                         @Nullable final /* not supported */ String author,
                         @Nullable final /* not supported */ String title,
                         final boolean fetchThumbnail)
            throws IOException {

        if (ISBN.isValid(isbn)) {
            String url = getBaseURL() + "/api/books?jscmd=data&format=json&bibkeys=ISBN:" + isbn;

            // get and store the result into a string.
            String response;
            try (TerminatorConnection con = TerminatorConnection.getConnection(url)) {
                BufferedReader streamReader = new BufferedReader(
                        new InputStreamReader(con.inputStream, StandardCharsets.UTF_8));
                String inputStr;
                StringBuilder responseStrBuilder = new StringBuilder();
                while ((inputStr = streamReader.readLine()) != null) {
                    responseStrBuilder.append(inputStr);
                }
                response = responseStrBuilder.toString();
            }

            // json-ify and handle.
            try {
                Bundle bookData = handleResponse(new JSONObject(response), fetchThumbnail);
                return bookData != null ? bookData : new Bundle();

            } catch (@NonNull final JSONException e) {
                throw new IOException(e);
            }

        } else if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {
            throw new UnsupportedOperationException();

        } else {
            return new Bundle();
        }
    }

    /**
     * <pre>
     *  "ISBN:9780980200447": {
     *         "publishers": [
     *           {
     *             "name": "Litwin Books"
     *           }
     *         ],
     *         "pagination": "80p.",
     *         "identifiers": {
     *           "google": [
     *             "4LQU1YwhY6kC"
     *           ],
     *           "lccn": [
     *             "2008054742"
     *           ],
     *           "openlibrary": [
     *             "OL22853304M"
     *           ],
     *           "isbn_13": [
     *             "9780980200447",
     *             "9781936117369"
     *           ],
     *           "amazon": [
     *             "098020044X"
     *           ],
     *           "isbn_10": [
     *             "1936117363"
     *           ],
     *           "oclc": [
     *             "297222669"
     *           ],
     *           "goodreads": [
     *             "6383507"
     *           ],
     *           "librarything": [
     *             "8071257"
     *           ]
     *         },
     *         "table_of_contents": [
     *           {
     *             "title": "The personal nature of slow reading",
     *             "label": "",
     *             "pagenum": "",
     *             "level": 0
     *           },
     *           {
     *             "title": "Slow reading in an information ecology",
     *             "label": "",
     *             "pagenum": "",
     *             "level": 0
     *           },
     *           {
     *             "title": "The slow movement and slow reading",
     *             "label": "",
     *             "pagenum": "",
     *             "level": 0
     *           },
     *           {
     *             "title": "The psychology of slow reading",
     *             "label": "",
     *             "pagenum": "",
     *             "level": 0
     *           },
     *           {
     *             "title": "The practice of slow reading.",
     *             "label": "",
     *             "pagenum": "",
     *             "level": 0
     *           }
     *         ],
     *         "links": [
     *           {
     *             "url": "http:\/\/johnmiedema.ca",
     *             "title": "Author's Website"
     *           },
     *           {
     *             "url": "http:\/\/litwinbooks.com\/slowreading-ch2.php",
     *             "title": "Chapter 2"
     *           },
     *           {
     *             "url": "http:\/\/www.powells.com\/biblio\/91-9781936117369-0",
     *             "title": "Get the e-book"
     *           }
     *         ],
     *         "weight": "1 grams",
     *         "title": "Slow reading",
     *         "url": "https:\/\/openlibrary.org\/books\/OL22853304M\/Slow_reading",
     *         "classifications": {
     *           "dewey_decimal_class": [
     *             "028\/.9"
     *           ],
     *           "lc_classifications": [
     *             "Z1003 .M58 2009"
     *           ]
     *         },
     *         "notes": "Includes bibliographical references and index.",
     *         "number_of_pages": 92,
     *         "cover": {
     *           "small": "https:\/\/covers.openlibrary.org\/b\/id\/5546156-S.jpg",
     *           "large": "https:\/\/covers.openlibrary.org\/b\/id\/5546156-L.jpg",
     *           "medium": "https:\/\/covers.openlibrary.org\/b\/id\/5546156-M.jpg"
     *         },
     *         "subjects": [
     *           {
     *             "url": "https:\/\/openlibrary.org\/subjects\/books_and_reading",
     *             "name": "Books and reading"
     *           },
     *           {
     *             "url": "https:\/\/openlibrary.org\/subjects\/in_library",
     *             "name": "In library"
     *           },
     *           {
     *             "url": "https:\/\/openlibrary.org\/subjects\/reading",
     *             "name": "Reading"
     *           }
     *         ],
     *         "publish_date": "March 2009",
     *         "key": "\/books\/OL22853304M",
     *         "authors": [
     *           {
     *             "url": "https:\/\/openlibrary.org\/authors\/OL6548935A\/John_Miedema",
     *             "name": "John Miedema"
     *           }
     *         ],
     *         "by_statement": "by John Miedema.",
     *         "publish_places": [
     *           {
     *             "name": "Duluth, Minn"
     *           }
     *         ],
     *         "ebooks": [
     *           {
     *             "checkedout": true,
     *             "formats": {},
     *             "preview_url": "https:\/\/archive.org\/details\/slowreading00mied",
     *             "borrow_url": "https:\/\/openlibrary.org\/books\/OL22853304M\/Slow_reading\/borrow",
     *             "availability": "borrow"
     *           }
     *         ]
     *       }
     *     }
     * </pre>
     * <p>
     * The keys (jsonObject.keys()) are:
     * "ISBN:9780980200447"
     *
     * @param jsonObject the complete book record.
     *
     * @return the book data bundle
     *
     * @throws JSONException upon any error
     */
    private Bundle handleResponse(@NonNull final JSONObject jsonObject,
                                  final boolean fetchThumbnail)
            throws JSONException {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.OPEN_LIBRARY_SEARCH) {
            Logger.debugEnter(this, "handleResponse", jsonObject.toString(2));
        }
        Iterator<String> it = jsonObject.keys();
        // we only handle the first result for now.
        if (it.hasNext()) {
            String key = it.next();
            String[] data = key.split(":");
            if (data.length == 2 && "ISBN".equals(data[0])) {
                return handleBook(data[1], fetchThumbnail, jsonObject.getJSONObject(key));
            }
        }

        return null;
    }

    private Bundle handleBook(@NonNull final String isbn,
                              final boolean fetchThumbnail,
                              @NonNull final JSONObject result)
            throws JSONException {
        Bundle bookData = new Bundle();

        JSONObject o;
        JSONArray a;
        String s;
        int i;

        // mandatory, if no title found, throw
        s = result.optString("title");
        if (!s.isEmpty()) {
            bookData.putString(DBDefinitions.KEY_TITLE, s);
        } else {
            throw new JSONException("no title");
        }

        // mandatory, if no authors found, throw
        ArrayList<Author> authors = new ArrayList<>();
        a = result.optJSONArray("authors");
        if (a != null && a.length() > 0) {
            for (int ai = 0; ai < a.length(); ai++) {
                o = a.optJSONObject(ai);
                String name = o.optString("name");
                if (!name.isEmpty()) {
                    authors.add(Author.fromString(name));
                }
            }
        }
        if (authors.isEmpty()) {
            throw new JSONException("no authors");
        }
        bookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, authors);

        // store the isbn; we might override it later on though (e.g. isbn 13v10)
        // not sure if this is needed though. Need more data.
        bookData.putString(DBDefinitions.KEY_ISBN, isbn);

        // everything below is optional.

        // "notes" is a specific (set of) remarks on this particular edition of the book.
        s = result.optString("notes");
        if (!s.isEmpty()) {
            bookData.putString(DBDefinitions.KEY_DESCRIPTION, s);
        }

        s = result.optString("publish_date");
        if (!s.isEmpty()) {
            Date date = DateUtils.parseDate(s);
            if (date != null) {
                bookData.putString(DBDefinitions.KEY_DATE_PUBLISHED, s);
            }
        }

//        s = jsonObject.optString("pagination");
//        if (!s.isEmpty()) {
//            bookData.putString(DBDefinitions.KEY_PAGES, s);
//        } else {
        i = result.optInt("number_of_pages");
        if (i > 0) {
            bookData.putString(DBDefinitions.KEY_PAGES, String.valueOf(i));
        }
//        }

        o = result.optJSONObject("identifiers");
        if (o != null) {
            // see if we have a better isbn.
            a = o.optJSONArray("isbn_13");
            if (a != null && a.length() > 0) {
                bookData.putString(DBDefinitions.KEY_ISBN, a.getString(0));
            } else {
                a = o.optJSONArray("isbn_10");
                if (a != null && a.length() > 0) {
                    bookData.putString(DBDefinitions.KEY_ISBN, a.getString(0));
                }
            }
            a = o.optJSONArray("openlibrary");
            if (a != null && a.length() > 0) {
                bookData.putString(DBDefinitions.KEY_OPEN_LIBRARY_ID, a.getString(0));
            }
            a = o.optJSONArray("librarything");
            if (a != null && a.length() > 0) {
                bookData.putString(DBDefinitions.KEY_LIBRARY_THING_ID, a.getString(0));
            }
            a = o.optJSONArray("goodreads");
            if (a != null && a.length() > 0) {
                bookData.putString(DBDefinitions.KEY_GOODREADS_ID, a.getString(0));
            }
        }

        if (fetchThumbnail) {
            // get the largest cover image available.
            o = result.optJSONObject("cover");
            if (o != null) {
                String url = o.optString("large");
                if (url.isEmpty()) {
                    url = o.optString("medium");
                    if (url.isEmpty()) {
                        url = o.optString("small");
                    }
                }
                // we assume that the download will work if there is a url.
                if (!url.isEmpty()) {
                    String fileSpec = ImageUtils.saveImage(url, FILENAME_SUFFIX);
                    if (fileSpec != null) {
                        ArrayList<String> imageList =
                                bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
                        if (imageList == null) {
                            imageList = new ArrayList<>();
                        }
                        imageList.add(fileSpec);
                        bookData.putStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY, imageList);
                    }
                }
            }
        }

        // we get an array, but our app only supports 1 publisher so grab the first one
        a = result.optJSONArray("publishers");
        if (a != null && a.length() > 0) {
            // only use the first one.
            o = a.optJSONObject(0);
            String name = o.optString("name");
            if (!name.isEmpty()) {
                bookData.putString(DBDefinitions.KEY_PUBLISHER, name);
            }
        }

        // always use the first author only for TOC entries.
        ArrayList<TocEntry> toc = new ArrayList<>();
        a = result.optJSONArray("table_of_contents");
        if (a != null && a.length() > 0) {
            for (int ai = 0; ai < a.length(); ai++) {
                o = a.optJSONObject(ai);
                String title = o.optString("title");
                if (!title.isEmpty()) {
                    toc.add(new TocEntry(authors.get(0), title, ""));
                }
            }
        }
        bookData.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, toc);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.OPEN_LIBRARY_SEARCH) {
            Logger.debugExit(this, "handleBook", bookData.toString());
        }
        return bookData;
    }
}
