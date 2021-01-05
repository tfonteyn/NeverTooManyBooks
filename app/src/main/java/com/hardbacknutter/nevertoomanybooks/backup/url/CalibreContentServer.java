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
package com.hardbacknutter.nevertoomanybooks.backup.url;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

/**
 * <a href="https://manual.calibre-ebook.com/server.html">User manual</a>
 * <p>
 * <a href="https://github.com/kovidgoyal/calibre/blob/master/src/calibre/srv/ajax.py">
 * AJAX API</a>
 */
public class CalibreContentServer {

    /** Preferences prefix. */
    private static final String PREF_KEY = "calibre";
    /** Type: {@code String}. Matches "res/xml/preferences_calibre.xml". */
    public static final String PK_HOST_URL = PREF_KEY + ".host.url";
    /** Whether to show any sync menus at all. */
    private static final String PK_SHOW_MENUS = PREF_KEY + ".showMenu";

    /** last time we synced with Calibre. */
    private static final String PK_LAST_SYNC_DATE = PREF_KEY + ".last.sync.date";

    /** Log tag. */
    private static final String TAG = "CalibreContentServer";
    public static final String CA_FILE = TAG + ".ca";

    /** Access the list of Libraries. */
    private static final String URL_LIBRARY_LIST = "/ajax/library-info";
    /** Paged access to the "All Books" category. Returns a list of book id's. */
    private static final String URL_CATEGORY_ALL_BOOKS = "/ajax/category/616c6c626f6f6b73/";
    /** Access to the books (by specifying a list of id's). Returns a list of Books. */
    private static final String URL_BOOKS = "/ajax/books/";

    @NonNull
    private final String mHostUrl;
    private final Map<String, CalibreLibrary> mLibraryMap = new HashMap<>();
    @NonNull
    private String mLibraryId = "Calibre_Library";
    @Nullable
    private SSLContext mSslContext;

    @AnyThread
    CalibreContentServer(@NonNull final Context context,
                         @NonNull final Uri uri)
            throws IOException, CertificateException, KeyStoreException,
                   NoSuchAlgorithmException, KeyManagementException {

        mHostUrl = uri.toString();

        if ("https".equals(uri.getScheme())) {
            mSslContext = getSslContext(context);
        }
    }

    @AnyThread
    public static boolean isShowSyncMenus(@NonNull final SharedPreferences global) {
        return global.getBoolean(PK_SHOW_MENUS, true);
    }

    /**
     * Get the default/stored host url.
     *
     * @param context current context
     *
     * @return url
     */
    @NonNull
    public static String getHostUrl(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(PK_HOST_URL, "");
    }

    @Nullable
    public static LocalDateTime getLastSyncDate(@NonNull final Context context) {
        final String date = PreferenceManager.getDefaultSharedPreferences(context)
                                             .getString(PK_LAST_SYNC_DATE, null);

        if (date != null && !date.isEmpty()) {
            return DateParser.getInstance(context).parseISO(date);
        }

        return null;
    }

    public static void setLastSyncDate(@NonNull final Context context,
                                       @Nullable final LocalDateTime dateTime) {
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        if (dateTime == null) {
            global.edit().remove(PK_LAST_SYNC_DATE).apply();
        } else {
            global.edit()
                  .putString(PK_LAST_SYNC_DATE, dateTime
                          .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                  .apply();
        }
    }

    /**
     * Loads the list of available libraries and sets the default library.
     *
     * <pre>
     * {"library_map": {"Calibre_Library": "Calibre Library"},
     *  "default_library": "Calibre_Library"
     * }
     * </pre>
     *
     * @throws SSLException if a secure connection failed
     * @throws IOException  on other failures
     */
    @WorkerThread
    void ensureLibraries()
            throws IOException, SSLException, GeneralParsingException {
        if (mLibraryMap.isEmpty()) {
            try {
                final String url = mHostUrl + URL_LIBRARY_LIST;
                final JSONObject source = new JSONObject(fetch(url));

                mLibraryId = source.getString("default_library");

                final JSONObject libs = source.getJSONObject("library_map");
                final Iterator<String> it = libs.keys();
                while (it.hasNext()) {
                    final String id = it.next();
                    mLibraryMap.put(id, new CalibreLibrary(id, libs.getString(id),
                                                           id.equals(mLibraryId)));
                }
            } catch (@NonNull final JSONException e) {
                throw new GeneralParsingException(e);
            }
        }
    }

    @NonNull
    @AnyThread
    public Map<String, CalibreLibrary> getLibraries() {
        return mLibraryMap;
    }

    @NonNull
    public CalibreLibrary getCurrentLibrary() {
        //noinspection ConstantConditions
        return mLibraryMap.get(mLibraryId);
    }

    /**
     * Set the current library.
     *
     * @param id of library to use for all calls
     *
     * @return {@code true} if the library exists;
     * {@code false} otherwise, and the default is not changed
     */
    @AnyThread
    public boolean setCurrentLibrary(@NonNull final String id) {
        if (mLibraryMap.containsKey(id)) {
            mLibraryId = id;
            return true;
        }
        return false;
    }

    /**
     * https://github.com/kovidgoyal/calibre/blob/master/src/calibre/srv/ajax.py#297
     * <p>
     * JSON response:
     * <pre>
     *     {
     *     "total_num": 255,
     *     "sort_order": "desc",
     *     "offset": 200,
     *     "num": 10,
     *     "sort": "timestamp",
     *     "base_url": "/ajax/books_in/616c6c626f6f6b73/30/Calibre_Library",
     *     "book_ids": [73, 72, 71, 70, 69, 68, 67, 66, 65, 64]
     *     }
     * </pre>
     *
     * @param num    number of books to fetch
     * @param offset to start fetching from
     *
     * @return see above
     */
    @WorkerThread
    @NonNull
    JSONObject getBookIds(@SuppressWarnings("SameParameterValue") final int num,
                          final int offset)
            throws IOException, JSONException {

        final String url = mHostUrl + URL_CATEGORY_ALL_BOOKS + mLibraryId
                           + "?num=" + num + "&offset=" + offset;
        return new JSONObject(fetch(url));
    }

    /**
     * NOT an array!
     *
     * <pre>
     *     {
     *     "6": {
     *         "series": null,
     *         "tags": [
     *             "Fiction",
     *             "Science Fiction"
     *         ],
     *         "thumbnail": "/get/thumb/6/Calibre_Library",
     *         "author_sort": "Stross, Charles",
     *         "rating": 5,
     *         "pubdate": "2005-06-25T23:00:00+00:00",
     *         "application_id": 6,
     *         "cover": "/get/cover/6/Calibre_Library",
     *         "series_index": null,
     *         "author_link_map": {
     *             "Charles Stross": ""
     *         },
     *         "author_sort_map": {
     *             "Charles Stross": "Stross, Charles"
     *         },
     *         "publisher": "Ace",
     *         "user_categories": {},
     *         "comments": "<div>\n<p>The Singularity. blah blah...",
     *         "title_sort": "Accelerando",
     *         "identifiers": {
     *             "amazon": "0441014151",
     *             "isbn": "9780441014156",
     *             "google": "F3i9DAEACAAJ"
     *         },
     *         "uuid": "4ec36562-d8e8-4499-9c6c-d1e7ae2af42f",
     *         "title": "Accelerando",
     *         "authors": [
     *             "Charles Stross"
     *         ],
     *         "last_modified": "2020-11-20T11:17:51+00:00",
     *         "languages": [
     *             "eng"
     *         ],
     *         "timestamp": "2019-04-11T12:02:03+00:00",
     *         "user_metadata": {
     *             "#notes": {
     *                 "table": "custom_column_4",
     *                 "column": "value",
     *                 "datatype": "comments",
     *                 "is_multiple": null,
     *                 "kind": "field",
     *                 "name": "Notes",
     *                 "search_terms": [
     *                     "#notes"
     *                 ],
     *                 "label": "notes",
     *                 "colnum": 4,
     *                 "display": {
     *                     "description": "Personal notes",
     *                     "heading_position": "above",
     *                     "interpret_as": "html"
     *                 },
     *                 "is_custom": true,
     *                 "is_category": false,
     *                 "link_column": "value",
     *                 "category_sort": "value",
     *                 "is_csp": false,
     *                 "is_editable": true,
     *                 "rec_index": 22,
     *                 "#value#": null,
     *                 "#extra#": null,
     *                 "is_multiple2": {}
     *             },
     *             "#read": {
     *                 "table": "custom_column_2",
     *                 "column": "value",
     *                 "datatype": "bool",
     *                 "is_multiple": null,
     *                 "kind": "field",
     *                 "name": "Read",
     *                 "search_terms": [
     *                     "#read"
     *                 ],
     *                 "label": "read",
     *                 "colnum": 2,
     *                 "display": {
     *                     "description": ""
     *                 },
     *                 "is_custom": true,
     *                 "is_category": false,
     *                 "link_column": "value",
     *                 "category_sort": "value",
     *                 "is_csp": false,
     *                 "is_editable": true,
     *                 "rec_index": 23,
     *                 "#value#": null,
     *                 "#extra#": null,
     *                 "is_multiple2": {}
     *             },
     *             "#read_end": {
     *                 "table": "custom_column_3",
     *                 "column": "value",
     *                 "datatype": "datetime",
     *                 "is_multiple": null,
     *                 "kind": "field",
     *                 "name": "Finished reading",
     *                 "search_terms": [
     *                     "#read_end"
     *                 ],
     *                 "label": "read_end",
     *                 "colnum": 3,
     *                 "display": {
     *                     "date_format": null,
     *                     "description": ""
     *                 },
     *                 "is_custom": true,
     *                 "is_category": false,
     *                 "link_column": "value",
     *                 "category_sort": "value",
     *                 "is_csp": false,
     *                 "is_editable": true,
     *                 "rec_index": 24,
     *                 "#value#": "None",
     *                 "#extra#": null,
     *                 "is_multiple2": {}
     *             },
     *             "#read_start": {
     *                 "table": "custom_column_7",
     *                 "column": "value",
     *                 "datatype": "datetime",
     *                 "is_multiple": null,
     *                 "kind": "field",
     *                 "name": "Started reading",
     *                 "search_terms": [
     *                     "#read_start"
     *                 ],
     *                 "label": "read_start",
     *                 "colnum": 7,
     *                 "display": {
     *                     "date_format": null,
     *                     "description": ""
     *                 },
     *                 "is_custom": true,
     *                 "is_category": false,
     *                 "link_column": "value",
     *                 "category_sort": "value",
     *                 "is_csp": false,
     *                 "is_editable": true,
     *                 "rec_index": 25,
     *                 "#value#": "None",
     *                 "#extra#": null,
     *                 "is_multiple2": {}
     *             },
     *             "#status": {
     *                 "table": "custom_column_5",
     *                 "column": "value",
     *                 "datatype": "enumeration",
     *                 "is_multiple": null,
     *                 "kind": "field",
     *                 "name": "Status",
     *                 "search_terms": [
     *                     "#status"
     *                 ],
     *                 "label": "status",
     *                 "colnum": 5,
     *                 "display": {
     *                     "enum_values": [
     *                         "OK",
     *                         "spelling",
     *                         "OCR issues",
     *                         "bad"
     *                     ],
     *                     "use_decorations": 0,
     *                     "description": "",
     *                     "enum_colors": [
     *                         "green",
     *                         "blue",
     *                         "orange",
     *                         "red"
     *                     ]
     *                 },
     *                 "is_custom": true,
     *                 "is_category": true,
     *                 "link_column": "value",
     *                 "category_sort": "value",
     *                 "is_csp": false,
     *                 "is_editable": true,
     *                 "rec_index": 26,
     *                 "#value#": null,
     *                 "#extra#": null,
     *                 "is_multiple2": {}
     *             }
     *         },
     *         "format_metadata": {
     *             "epub": {
     *                 "path": "full absolute path to the epub file",
     *                 "size": 408763,
     *                 "mtime": "2020-09-18T15:26:14.871190+00:00"
     *             }
     *         },
     *         "formats": [
     *             "epub"
     *         ],
     *         "main_format": {
     *             "epub": "/get/epub/6/Calibre_Library"
     *         },
     *         "other_formats": {},
     *         "category_urls": {
     *             "series": {},
     *             "tags": {
     *                 "Fiction": "/ajax/books_in/74616773/3139/Calibre_Library",
     *                 "Science Fiction": "/ajax/books_in/74616773/34/Calibre_Library"
     *             },
     *             "publisher": {
     *                 "Ace": "/ajax/books_in/7075626c6973686572/3735/Calibre_Library"
     *             },
     *             "authors": {
     *                 "Charles Stross": "/ajax/books_in/617574686f7273/32/Calibre_Library"
     *             },
     *             "languages": {},
     *             "#status": {}
     *         }
     *     },
     * </pre>
     * <p>
     * https://github.com/kovidgoyal/calibre/blob/master/src/calibre/srv/ajax.py#183
     */
    @WorkerThread
    @NonNull
    JSONObject getBooks(@NonNull final JSONArray bookIds)
            throws JSONException, IOException {

        final StringJoiner ids = new StringJoiner(",");
        for (int i = 0; i < bookIds.length(); i++) {
            ids.add(String.valueOf(bookIds.getInt(i)));
        }

        final String url = mHostUrl + URL_BOOKS + mLibraryId + "?category_urls=false&ids=" + ids;
        return new JSONObject(fetch(url));
    }

    @WorkerThread
    @Nullable
    File getCover(@NonNull final Context context,
                  final int calibreBookId,
                  @NonNull final String coverUrl) {

        final String filename = ImageUtils
                .createFilename(TAG, String.valueOf(calibreBookId), 0, null);

        return ImageUtils.saveImage(context, mHostUrl + coverUrl, filename, 0, 0, null);
    }

    /**
     * Fetch the given URL content as a single string.
     *
     * @param url to read
     *
     * @return content
     *
     * @throws IOException on failure
     */
    @NonNull
    private String fetch(@NonNull final String url)
            throws IOException {

        try (TerminatorConnection con = new TerminatorConnection(url, mSslContext, 0, 0, null);
             InputStream is = con.getInputStream();
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {

            return reader.lines().collect(Collectors.joining());

        } catch (@NonNull final UncheckedIOException e) {
            //noinspection ConstantConditions
            throw e.getCause();
        }
    }

    @NonNull
    private SSLContext getSslContext(@NonNull final Context context)
            throws IOException, CertificateException, KeyStoreException,
                   NoSuchAlgorithmException, KeyManagementException {

        final Certificate ca;
        try (InputStream is = context.openFileInput(CA_FILE)) {
            ca = CertificateFactory.getInstance("X.509").generateCertificate(is);
        }

        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("calibre", ca);

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        // Should use "TLSv1.3" ... but that's presuming Calibre supports it.
        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);

        return sslContext;
    }
}
