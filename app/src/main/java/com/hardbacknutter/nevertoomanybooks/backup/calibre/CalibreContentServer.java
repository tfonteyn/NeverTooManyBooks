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
package com.hardbacknutter.nevertoomanybooks.backup.calibre;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.HttpStatusException;

/**
 * <ul>
 *     <li><a href="https://manual.calibre-ebook.com/server.html">User manual</a></li>
 *     <li><a href="https://github.com/kovidgoyal/calibre/blob/master/src/calibre/srv/ajax.py">
 *          Reading API</a></li>
 *     <li><a href="https://github.com/kovidgoyal/calibre/blob/master/src/calibre/srv/cdb.py">
 *          Writing API</a></li>
 * </ul>
 * <p>
 * This class can handle multiple Calibre Libraries on a <strong>single</strong> Calibre server.
 * (Note that this seems not to be fully implemented on the Calibre side)
 */
public class CalibreContentServer {

    /** A text "None" as value. Can/will be seen. This is the python equivalent of {@code null}. */
    static final String VALUE_IS_NONE = "None";
    /** Response root tag: Total number of items found in a query. */
    static final String RESPONSE_TAG_TOTAL_NUM = "total_num";
    /** Response root tag: Number of items returned in 'this' call. */
    static final String RESPONSE_TAG_NUM = "num";
    /** Response root tag: The array of book ids returned in 'this' call. */
    static final String RESPONSE_TAG_BOOK_IDS = "book_ids";

    /** Log tag. */
    private static final String TAG = "CalibreContentServer";
    /** DER encoded CA certificate. */
    public static final String CA_FILE = TAG + ".ca";
    /** Preferences prefix. */
    private static final String PREF_KEY = "calibre";
    /** Type: {@code String}. Matches "res/xml/preferences_calibre.xml". */
    public static final String PK_HOST_URL = PREF_KEY + ".host.url";
    public static final String PK_HOST_USER = PREF_KEY + ".host.user";
    public static final String PK_HOST_PASS = PREF_KEY + ".host.password";
    /** Whether to show any sync menus at all. */
    public static final String PK_ENABLED = PREF_KEY + ".enabled";
    private static final String PK_LOCAL_FOLDER_URI = PREF_KEY + ".folder";
    /** last time we synced with Calibre. */
    private static final String PK_LAST_SYNC_DATE = PREF_KEY + ".last.sync.date";
    /**
     * The buffer used for all small reads.
     * 8k is the same as the default in BufferedReader.
     */
    private static final int BUFFER_SMALL = 8_192;
    /**
     * The buffer used for a single book; it's usually just above 8k.
     */
    private static final int BUFFER_BOOK = 16_384;
    /**
     * We're using a larger read buffer for {@link #getBookIds(String, int, int)};
     * The size is based on a rough minimum of
     * 8K of data for a single book and we fetch 10 books at a time... hence 128k.
     */
    private static final int BUFFER_BOOK_LIST = 131_072;
    /**
     * And a huge buffer to download the eBook files themselves.
     */
    private static final int BUFFER_FILE = 1_048_576;

    private static final int CONNECT_TIMEOUT_IN_MS = 5_000;
    private static final int READ_TIMEOUT_IN_MS = 5_000;

    @NonNull
    private final Uri mServerUri;
    /** The header string: "Basic user:password". (in base64) */
    @Nullable
    private final String mAuthHeader;
    /**
     * The custom fields <strong>present</strong> on the server.
     * This will be a subset of the supported fields from {@link CustomFields}.
     */
    private final Set<CustomFields.Field> mCustomFields = new HashSet<>();
    private final Map<String, Integer> mTotalBooks = new HashMap<>();
    /** As read from the Content Server. */
    @NonNull
    private final ArrayList<CalibreLibrary> mLibraries = new ArrayList<>();
    @NonNull
    private final ArrayList<CalibreLibrary> mVirtualLibraries = new ArrayList<>();
    /** As read from the Content Server. */
    @Nullable
    private CalibreLibrary mDefaultLibrary;
    @Nullable
    private ImageDownloader mImageDownloader;
    @Nullable
    private SSLContext mSslContext;

    private boolean mCalibreExtensionInstalled;

    /**
     * Constructor.
     * Uses the configured content server Uri
     *
     * @param context Current context
     *
     * @throws IOException          on failures
     * @throws CertificateException on failures related to the user installed CA.
     */
    @AnyThread
    CalibreContentServer(@NonNull final Context context)
            throws IOException, CertificateException {
        this(context, Uri.parse(getHostUrl(context)));
    }

    /**
     * Constructor.
     *
     * @param context Current context
     * @param uri     for the content server
     *
     * @throws IOException          on failures
     * @throws CertificateException on failures related to the user installed CA.
     */
    @AnyThread
    CalibreContentServer(@NonNull final Context context,
                         @NonNull final Uri uri)
            throws IOException, CertificateException {

        mServerUri = uri;

        // accommodate the (usually) self-signed CA certificate
        if ("https".equals(mServerUri.getScheme())) {
            mSslContext = getSslContext(context);
        }

        // We're assuming Calibre will be setup with basic-auth as per their SSL recommendations
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        final String username = global.getString(PK_HOST_USER, "");
        if (!username.isEmpty()) {
            final String password = global.getString(PK_HOST_PASS, "");
            mAuthHeader = "Basic " + Base64.encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8), 0);
        } else {
            mAuthHeader = null;
        }
    }

    @AnyThread
    public static boolean isEnabled(@NonNull final SharedPreferences global) {
        return global.getBoolean(PK_ENABLED, true);
    }

    /**
     * Get the default/stored host url.
     *
     * @param context current context
     *
     * @return url
     */
    @NonNull
    @AnyThread
    public static String getHostUrl(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(PK_HOST_URL, "");
    }

    @Nullable
    @AnyThread
    static LocalDateTime getLastSyncDate(@NonNull final Context context) {
        final String date = PreferenceManager.getDefaultSharedPreferences(context)
                                             .getString(PK_LAST_SYNC_DATE, null);

        if (date != null && !date.isEmpty()) {
            return DateParser.getInstance(context).parseISO(date);
        }

        return null;
    }

    @AnyThread
    static void setLastSyncDate(@NonNull final Context context,
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

    @AnyThread
    public static void setFolderUri(@NonNull final Context context,
                                    @NonNull final Uri uri)
            throws SecurityException {
        final ContentResolver contentResolver = context.getContentResolver();

        final String oldFolder = PreferenceManager.getDefaultSharedPreferences(context)
                                                  .getString(PK_LOCAL_FOLDER_URI, "");

        // If the old one is different then the current selection, release the previous Uri
        if (!oldFolder.equals(uri.toString())) {
            getFolderUri(context).ifPresent(
                    oldUri -> contentResolver.releasePersistableUriPermission(
                            oldUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        }

        try {
            // Take and store the new Uri
            contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                         | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            PreferenceManager.getDefaultSharedPreferences(context)
                             .edit()
                             .putString(PK_LOCAL_FOLDER_URI, uri.toString())
                             .apply();
        } catch (@NonNull final SecurityException e) {
            Logger.error(context, TAG, e, "uri=" + uri.toString());
            throw e;
        }
    }

    @NonNull
    @AnyThread
    public static Optional<Uri> getFolderUri(@NonNull final Context context) {

        final String folder = PreferenceManager.getDefaultSharedPreferences(context)
                                               .getString(PK_LOCAL_FOLDER_URI, "");
        if (folder.isEmpty()) {
            return Optional.empty();
        }

        return context.getContentResolver()
                      .getPersistedUriPermissions()
                      .stream()
                      .map(UriPermission::getUri)
                      .filter(uri -> uri.toString().equals(folder))
                      .findFirst();
    }

    /**
     * Make a short call to test the connection.
     *
     * @return {@code true} if al is well.
     *
     * @throws IOException on failure
     */
    boolean createTestConnection()
            throws IOException {
        return !fetch("/ajax/library-info", BUFFER_SMALL).isEmpty();
    }

    /**
     * Read the required meta data from the server.
     * <ul>
     *     <li>number of books in the given library</li>
     *     <li>user custom fields definitions</li>
     *     <li>Virtual libraries</li>
     * </ul>
     *
     * @param context Current context
     * @param db      Database access
     *
     * @throws IOException on other failures
     */
    @WorkerThread
    void readMetaData(@NonNull final Context context,
                      @NonNull final DAO db)
            throws IOException, JSONException {

        loadLibraries(context, db);
        // Sanity check
        Objects.requireNonNull(mDefaultLibrary, "mDefaultLibrary");
        try {
            loadVirtualLibraries(context, db, mDefaultLibrary);
        } catch (@NonNull final HttpNotFoundException e) {
            Logger.warn(context, TAG, "Calibre extension not installed");
        } catch (@NonNull final HttpStatusException e) {
            Logger.warn(context, TAG, e.toString());
        }

        // read the first book available to get the customs fields (if any)
        final JSONObject result = getBookIds(mDefaultLibrary.getLibraryId(), 1, 0);
        // grab the initial/current total number of books while we have it
        mTotalBooks.put(mDefaultLibrary.getLibraryId(), result.optInt(RESPONSE_TAG_TOTAL_NUM));

        final JSONArray calibreIds = result.optJSONArray(RESPONSE_TAG_BOOK_IDS);
        if (calibreIds != null && calibreIds.length() > 0) {
            loadCustomFieldDefinitions(mDefaultLibrary.getLibraryId(), calibreIds.getInt(0));
        }
    }

    /**
     * endpoint('/ajax/library-info', postprocess=json)
     * <p>
     * Return info about available libraries.
     *
     * <pre>
     * {"library_map":
     *      {"Calibre_Library": "Calibre Library"},
     *      "default_library": "Calibre_Library"
     * }
     * </pre>
     * <p>
     * populates {@link #mDefaultLibrary} + {@link #mLibraries}
     *
     * @param context Current context
     * @param db      Database access
     *
     * @throws IOException on failures
     */
    @WorkerThread
    private void loadLibraries(@NonNull final Context context,
                               @NonNull final DAO db)
            throws IOException, JSONException {

        mLibraries.clear();
        mDefaultLibrary = null;

        final Bookshelf currentBookshelf = Bookshelf
                .getBookshelf(context, db, Bookshelf.PREFERRED, Bookshelf.DEFAULT);

        final String url = "/ajax/library-info";
        final JSONObject source = new JSONObject(fetch(url, BUFFER_SMALL));

        final JSONObject server_libs = source.getJSONObject("library_map");
        final String defaultLibraryId = source.getString("default_library");

        final Iterator<String> it = server_libs.keys();
        while (it.hasNext()) {
            final String libraryId = it.next();
            final String name = server_libs.getString(libraryId);

            CalibreLibrary dbLib = db.getCalibreLibrary(context, libraryId);
            if (dbLib == null) {
                dbLib = new CalibreLibrary(libraryId, name, "", currentBookshelf);
                db.insert(dbLib);
            } else {
                // Check if any data was changed
                if (!name.equals(dbLib.getName())) {
                    dbLib.setName(name);
                    db.update(dbLib);
                }
            }

            mLibraries.add(dbLib);
            if (libraryId.equals(defaultLibraryId)) {
                mDefaultLibrary = dbLib;
            }
        }
    }


    /**
     * endpoint('/ntmb/virtual-library-info/{library_id=None}', postprocess=json)
     * <p>
     * Return info about available virtual libraries
     * <p>
     * This method uses an extension which needs to be installed on the Calibre Content Server.
     * <p>
     * Example response:
     * <pre>
     *      {
     *      "virtual_libraries": {
     *              "Non-Fiction": "tags:\"=Non-Fiction\"",
     *              "Fiction": "not tags:\"Non-Fiction\""
     *          }
     *      }
     * </pre>
     *
     * @param context Current context
     * @param db      Database access
     *
     * @throws HttpNotFoundException if our extension is not installed
     * @throws IOException           on other failures
     */
    @WorkerThread
    private void loadVirtualLibraries(@NonNull final Context context,
                                      @NonNull final DAO db,
                                      @NonNull final CalibreLibrary library)
            throws IOException, JSONException {

        mVirtualLibraries.clear();

        final String url = "/ntmb/virtual-library-info/" + library.getLibraryId();
        final JSONObject result = new JSONObject(fetch(url, BUFFER_SMALL));

        // If we get here without getting a 404, our extension is installed.
        mCalibreExtensionInstalled = true;

        if (!result.isNull("virtual_libraries")) {
            final JSONObject server_libs = result.getJSONObject("virtual_libraries");
            final Iterator<String> it = server_libs.keys();
            while (it.hasNext()) {
                final String name = it.next();
                final String expr = server_libs.getString(name);

                CalibreLibrary dbLib = db.getCalibreLibrary(context, library, name);
                if (dbLib == null) {
                    dbLib = new CalibreLibrary(library.getLibraryId(), name, expr,
                                               library.getMappedBookshelf());
                    db.insert(dbLib);
                } else {
                    // Check if any data was changed
                    if (!name.equals(dbLib.getName()) || !expr.equals(dbLib.getExpr())) {
                        dbLib.setName(name);
                        dbLib.setExpr(expr);
                        db.update(dbLib);
                    }
                }
                mVirtualLibraries.add(dbLib);
            }
        }

        // Now remove any virtual libs in the database which are not present in mVirtualLibraries
        for (final CalibreLibrary dbLib
                : db.getCalibreVirtualLibraries(context, library.getLibraryId())) {
            if (mVirtualLibraries.stream()
                                 .map(CalibreLibrary::getId)
                                 .noneMatch(id -> id == dbLib.getId())) {
                db.delete(dbLib);
            }
        }
    }

    private void loadCustomFieldDefinitions(@NonNull final String libraryId,
                                            final int bookId)
            throws IOException, JSONException {

        mCustomFields.clear();
        final CalibreBook calibreBook = getBook(libraryId, bookId);
        final JSONObject userMetaData = calibreBook.optJSONObject(CalibreBook.USER_METADATA);
        if (userMetaData != null) {
            // check the supported fields
            for (final CustomFields.Field cf : CustomFields.getFields()) {
                final JSONObject data = userMetaData.optJSONObject(cf.calibreKey);
                // do we have a match? (this check is needed, it's NOT a sanity check)
                if (data != null && cf.type.equals(data.getString(
                        CustomFields.METADATA_DATATYPE))) {
                    mCustomFields.add(cf);
                }
            }
        }
    }


    @NonNull
    @AnyThread
    Set<CustomFields.Field> getCustomFields() {
        return mCustomFields;
    }


    @AnyThread
    boolean isCalibreExtensionInstalled() {
        return mCalibreExtensionInstalled;
    }

    /**
     * Get the full list of <strong>physical</strong> libraries; usually just the one.
     *
     * @return list
     */
    @NonNull
    @AnyThread
    ArrayList<CalibreLibrary> getLibraries() {
        return mLibraries;
    }

    /**
     * Get the default <strong>physical</strong> library.
     *
     * @return library
     */
    @NonNull
    CalibreLibrary getDefaultLibrary() {
        return Objects.requireNonNull(mDefaultLibrary, "mDefaultLibrary");
    }

    /**
     * Get the <strong>physical</strong> library for the given libraryId.
     *
     * @return library
     */
    @NonNull
    @AnyThread
    Optional<CalibreLibrary> getLibrary(@NonNull final String libraryId) {
        return mLibraries.stream()
                         .filter(lib -> lib.getLibraryId().equals(libraryId))
                         .filter(CalibreLibrary::isPhysical)
                         .findFirst();
    }

    /**
     * Get the <strong>virtual</strong> library for the given libraryId + name.
     *
     * @return library
     */
    @NonNull
    @AnyThread
    Optional<CalibreLibrary> getLibrary(@NonNull final String libraryId,
                                        @NonNull final String name) {
        return mLibraries.stream()
                         .filter(lib -> lib.getLibraryId().equals(libraryId))
                         .filter(lib -> lib.getName().equals(name))
                         .filter(CalibreLibrary::isVirtual)
                         .findFirst();
    }

    /**
     * Get the list of <strong>virtual</strong> libraries.
     * Call {@link #isCalibreExtensionInstalled()} before calling this method.
     *
     * @return list
     */
    @NonNull
    @AnyThread
    ArrayList<CalibreLibrary> getVirtualLibraries() {
        if (!mCalibreExtensionInstalled) {
            throw new IllegalStateException("no ntmb ext");
        }

        return mVirtualLibraries;
    }


    /**
     * Get the number of books in the given library.
     *
     * @return number
     */
    @AnyThread
    int getTotalBooks(@NonNull final String libraryId) {
        return Objects.requireNonNull(mTotalBooks.get(libraryId), "Must call readMetaData first");
    }

    /**
     * endpoint('/ntmb/virtual-libraries-for-books/{library_id=None}', postprocess=json)
     * <p>
     * Return the book ids with their virtual libraries
     * Mandatory Query parameters; example: ?ids=271,7,200
     * <p>
     * This method uses an extension which needs to be installed on the Calibre Content Server.
     * <p>
     * Example response:
     * <pre>
     *      {
     *          "271": ["Fiction"],
     *          "7": ["Fiction"],
     *          "200": ["Fiction", "Non-Fiction"]
     *      }
     * </pre>
     *
     * @param libraryId to read from
     *
     * @return see above, or {@code null} if the extension is missing
     *
     * @throws IOException on other failures
     */
    @WorkerThread
    @Nullable
    JSONObject getVirtualLibrariesForBooks(@NonNull final String libraryId,
                                           @NonNull final JSONArray calibreIds)
            throws IOException, JSONException {
        if (!mCalibreExtensionInstalled) {
            return null;
        }

        final String url = "/ntmb/virtual-libraries-for-books/" + libraryId
                           + "?ids=" + getCsvIds(calibreIds);
        return new JSONObject(fetch(url, BUFFER_SMALL));
    }


    /**
     * endpoint('/ajax/category/{encoded_name}/{library_id=None}', postprocess=json)
     * <p>
     * Return a dictionary describing the category specified by name.
     * <p>
     * Optional: ?num=100&offset=0&sort=name&sort_order=asc
     * <p>
     * We're always using the "616c6c626f6f6b73" == "All books" category
     * <p>
     * Example response:
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
     * @param libraryId to read from
     * @param num       number of books to fetch
     * @param offset    to start fetching from
     *
     * @return see above
     *
     * @throws IOException on failures
     */
    @WorkerThread
    @NonNull
    JSONObject getBookIds(@NonNull final String libraryId,
                          @SuppressWarnings("SameParameterValue") final int num,
                          final int offset)
            throws IOException, JSONException {

        final String url = "/ajax/category/616c6c626f6f6b73/" + libraryId
                           + "?num=" + num + "&offset=" + offset;
        return new JSONObject(fetch(url, BUFFER_SMALL));
    }

    /**
     * endpoint('/ajax/search/{library_id=None}', postprocess=json)
     * <p>
     * Return the books matching the specified search query.
     * <p>
     * Optional: ?num=100&offset=0&sort=title&sort_order=asc&query=&vl=
     * <p>
     * http://192.168.0.202:8080/ajax/search?num=10&query=last_modified:%22%3E2021-1-10%22
     * <p>
     * Example query:  query=last_modified:">2021-1-10"
     * <p>
     * Example response:
     * <pre>
     * {
     *      "total_num": 9,
     *      "sort_order": "asc",
     *      "num_books_without_search": 265,
     *      "offset": 0,
     *      "num": 10,
     *      "sort": "title",
     *      "base_url": "/ajax/search/Calibre_Library",
     *      "query": "last_modified:\">2021-1-10\"",
     *      "library_id": "Calibre_Library",
     *      "book_ids": [6, 294, 219, 300, 34, 299, 298, 302, 301],
     *      "vl": ""}
     * </pre>
     */
    @WorkerThread
    @NonNull
    JSONObject search(@NonNull final String libraryId,
                      @SuppressWarnings("SameParameterValue") final int num,
                      final int offset,
                      @NonNull final String query)
            throws IOException, JSONException {

        final String url = "/ajax/search/" + libraryId
                           + "?num=" + num + "&offset=" + offset + "&query=" + query;
        return new JSONObject(fetch(url, BUFFER_BOOK_LIST));
    }

    /**
     * endpoint('/ajax/books/{library_id=None}', postprocess=json)
     * <p>
     * Return the metadata for the books as a JSON dictionary.
     * <p>
     * Query parameters: ?ids=all&category_urls=true&id_is_uuid=false&device_for_template=None
     * <p>
     * If category_urls is true the returned dictionary also contains a
     * mapping of category (field) names to URLs that return the list of books in the
     * given category.
     * <p>
     * If id_is_uuid is true then the book_id is assumed to be a book uuid instead.
     * <p>
     * Example response:
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
     *         "comments": "<p>The Singularity. blah blah...</p>",
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
     *                 "path": "/home/calibre/library
     *                      /Charles Stross
     *                      /Accelerando (6)
     *                      /Accelerando - Charles Stross.epub",
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
     * Books with multiple formats:
     * <pre>
     *     "main_format": {
     *         "epub": "/get/epub/87/library"
     *     },
     *     "other_formats": {
     *         "pdf": "/get/pdf/87/library"
     *     },
     *
     *     "formats": [
     *         "epub",
     *         "pdf"
     *      ],
     *     "format_metadata": {
     *         "pdf": {
     *             "path": "/home/calibre/library/some-author/some-title/some-book.pdf",
     *             "size": 21951985,
     *             "mtime": "2021-01-09T13:55:00.100514+00:00"
     *         },
     *         "epub": {
     *             "path": "/home/calibre/library/some-author/some-title/some-book.epub",
     *             "size": 25307259,
     *             "mtime": "2021-01-09T13:54:52.140562+00:00"
     *         }
     *     },
     * </pre>
     *
     * @param libraryId to read from
     *
     * @return JSONObject with a list of Calibre book objects; NOT an array.
     *
     * @throws IOException on failures
     */
    @WorkerThread
    @NonNull
    JSONObject getBooks(@NonNull final String libraryId,
                        @NonNull final JSONArray calibreIds)
            throws IOException, JSONException {

        final String url = "/ajax/books/" + libraryId
                           + "?category_urls=false&ids=" + getCsvIds(calibreIds);
        return new JSONObject(fetch(url, BUFFER_BOOK_LIST));
    }

    @NonNull
    private String getCsvIds(@NonNull final JSONArray calibreIds)
            throws JSONException {
        final StringJoiner ids = new StringJoiner(",");
        for (int i = 0; i < calibreIds.length(); i++) {
            ids.add(String.valueOf(calibreIds.getInt(i)));
        }
        return ids.toString();
    }

    /**
     * endpoint('/ajax/book/{book_id}/{library_id=None}', postprocess=json)
     * <p>
     * Return the metadata of the book as a JSON dictionary.
     * <p>
     * Query parameters: ?category_urls=true&id_is_uuid=false&device_for_template=None
     * <p>
     * If category_urls is true the returned dictionary also contains a
     * mapping of category (field) names to URLs that return the list of books in the
     * given category.
     * <p>
     * If id_is_uuid is true then the book_id is assumed to be a book uuid instead.
     *
     * @param libraryId   to read from
     * @param calibreUuid of the book to get
     *
     * @return Calibre book object
     *
     * @throws IOException on failures
     */
    @WorkerThread
    @NonNull
    public CalibreBook getBook(@NonNull final String libraryId,
                               @NonNull final String calibreUuid)
            throws IOException, JSONException {

        final String url = "/ajax/book/" + calibreUuid + '/' + libraryId + "?id_is_uuid=true";
        return new CalibreBook(new JSONObject(fetch(url, BUFFER_BOOK)));
    }

    /**
     * See{@link #getBook(String, String)}.
     *
     * @param libraryId to read from
     * @param calibreId of the book to get
     *
     * @return Calibre book object
     *
     * @throws IOException on failures
     */
    @WorkerThread
    @NonNull
    public CalibreBook getBook(@NonNull final String libraryId,
                               final int calibreId)
            throws IOException, JSONException {

        final String url = "/ajax/book/" + calibreId + '/' + libraryId;
        return new CalibreBook(new JSONObject(fetch(url, BUFFER_BOOK)));
    }

    @WorkerThread
    @Nullable
    File getCover(@NonNull final Context context,
                  final int calibreId,
                  @NonNull final String coverUrl) {
        try {
            if (mImageDownloader == null) {
                mImageDownloader = new ImageDownloader(mSslContext, mAuthHeader);
            }
            final File tmpFile = mImageDownloader
                    .createTmpFile(context, TAG, String.valueOf(calibreId), 0, null);
            return mImageDownloader.fetch(context, mServerUri + coverUrl, tmpFile);

        } catch (@NonNull final ExternalStorageException ignore) {
            // a fail here is never critical, we're ignoring any
        }
        return null;
    }

    /**
     * Download the main format file for the given book and store it in the given folder.
     *
     * @param context Current context
     * @param folder  to store the download in
     * @param book    to download
     *
     * @return the file
     *
     * @throws IOException on failures
     */
    @Nullable
    Uri getFile(@NonNull final Context context,
                @NonNull final Uri folder,
                @NonNull final Book book,
                @NonNull final ProgressListener progressListener)
            throws IOException {

        // Build the URL from where to download the file
        final int id = book.getInt(DBDefinitions.KEY_CALIBRE_BOOK_ID);
        final String format = book.getString(DBDefinitions.KEY_CALIBRE_BOOK_MAIN_FORMAT);
        final String libraryId = book.getString(DBDefinitions.KEY_CALIBRE_LIBRARY_ID);
        final String url = mServerUri + "/get/" + format + "/" + id + "/" + libraryId;

        // and where to write the file
        final DocumentFile destFile = getDocumentFile(context, book, folder, true);
        final Uri destUri = destFile.getUri();

        try (TerminatorConnection con = new TerminatorConnection(url)) {
            con.setSSLContext(mSslContext);
            if (mAuthHeader != null) {
                con.setRequestProperty(HttpConstants.AUTHORIZATION, mAuthHeader);
            }
            checkResponseCode(con.getRequest(), R.string.site_calibre);

            try (OutputStream os = context.getContentResolver().openOutputStream(destUri)) {
                if (os != null) {
                    try (InputStream is = con.getInputStream();
                         BufferedInputStream bis = new BufferedInputStream(is, BUFFER_FILE);
                         BufferedOutputStream bos = new BufferedOutputStream(os)) {

                        progressListener.publishProgressStep(0, context.getString(
                                R.string.progress_msg_loading));
                        FileUtils.copy(bis, bos);
                    }
                }
            }
        }

        if (destFile.exists()) {
            return destUri;
        } else {
            return null;
        }
    }


    /**
     * Send updates to the server.
     *
     * @param libraryId to write to
     * @param calibreId book to update
     * @param changes   to send
     *
     * @throws IOException on other failures
     */
    void pushChanges(@NonNull final String libraryId,
                     final int calibreId,
                     @NonNull final JSONObject changes)
            throws IOException, JSONException {

        final JSONObject data = new JSONObject();
        data.put("changes", changes);

        final JSONArray loadedBookIds = new JSONArray();
        loadedBookIds.put(calibreId);
        data.put("loaded_book_ids", loadedBookIds);
        final String postBody = data.toString();

        final String url = mServerUri + "/cdb/set-fields/" + calibreId + '/' + libraryId;

        final HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setRequestMethod(HttpConstants.POST);
        request.setRequestProperty(HttpConstants.CONTENT_TYPE, HttpConstants.CONTENT_TYPE_JSON);
        request.setDoOutput(true);
        if (mSslContext != null) {
            ((HttpsURLConnection) request).setSSLSocketFactory(mSslContext.getSocketFactory());
        }
        if (mAuthHeader != null) {
            request.setRequestProperty(HttpConstants.AUTHORIZATION, mAuthHeader);
        }

        // explicit connect for clarity
        request.connect();

        try (OutputStream os = request.getOutputStream();
             Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer writer = new BufferedWriter(osw)) {
            writer.write(postBody);
            writer.flush();
        }

        try {
            checkResponseCode(request, R.string.site_calibre);
        } finally {
            request.disconnect();
        }
    }

    /**
     * Get the book file from the local folder.
     * This only works if the user has not renamed the file outside of this app.
     *
     * @param context Current context
     * @param book    to get
     *
     * @return book
     *
     * @throws FileNotFoundException on ...
     */
    @NonNull
    Uri getDocumentUri(@NonNull final Context context,
                       @NonNull final Book book)
            throws FileNotFoundException {

        final Optional<Uri> optFolderUri = getFolderUri(context);
        if (optFolderUri.isPresent()) {
            try {
                return getDocumentFile(context, book, optFolderUri.get(), false).getUri();
            } catch (@NonNull final IOException e) {
                // Keep it simple.
                throw new FileNotFoundException();
            }
        }
        throw new FileNotFoundException();
    }

    /**
     * Get the DocumentFile for the given book.
     *
     * @param context  Current context
     * @param book     to get
     * @param folder   where the files are
     * @param creating set {@code true} when creating, set {@code false} for checking existence
     */
    @NonNull
    private DocumentFile getDocumentFile(@NonNull final Context context,
                                         @NonNull final Book book,
                                         @NonNull final Uri folder,
                                         final boolean creating)
            throws IOException {

        // we're not assuming ANYTHING....
        final DocumentFile root = DocumentFile.fromTreeUri(context, folder);
        if (root == null) {
            throw new FileNotFoundException(folder.toString());
        }

        final Author primaryAuthor = book.getPrimaryAuthor();
        if (primaryAuthor == null) {
            // This should never happen... flw
            throw new IOException("primaryAuthor was null");
        }

        String author = primaryAuthor.getFormattedName(false);
        // A little nastiness... if our name ends with a '.'
        // then Android, in its infinite wisdom, will remove it
        // If we escape it, Android will turn it into a '_'
        // Hence, we remove it ourselves, so a subsequent lookup will work.
        if (author.endsWith(".")) {
            author = author.substring(0, author.length() - 2).trim();
        }
        // FIRST check if it exists
        DocumentFile authorFolder = root.findFile(author);
        if (authorFolder == null) {
            if (creating) {
                authorFolder = root.createDirectory(author);
            }
            if (authorFolder == null) {
                throw new FileNotFoundException(author);
            }
        }

        String seriesPrefix = "";
        final Series primarySeries = book.getPrimarySeries();
        if (primarySeries != null) {
            seriesPrefix = primarySeries.getLabel(context) + " - ";
        }
        final String fileName = seriesPrefix + book.getTitle();

        final String format = book.getString(DBDefinitions.KEY_CALIBRE_BOOK_MAIN_FORMAT);

        // FIRST check if it exists using the format extension
        DocumentFile bookFile = authorFolder.findFile(fileName + "." + format);
        if (bookFile == null) {
            if (creating) {
                // when creating, we must NOT directly use the extension,
                // but deduce the mime type from the extension.
                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(format);
                if (mimeType == null) {
                    // shouldn't be needed, ... flw
                    mimeType = "application/" + format;
                }
                bookFile = authorFolder.createFile(mimeType, fileName);
            }
            if (bookFile == null) {
                throw new FileNotFoundException(fileName);
            }
        }

        return bookFile;
    }

    /**
     * Fetch the given url path content as a single string.
     *
     * @param path   the path to read
     * @param buffer size for the read
     *
     * @return content
     *
     * @throws IOException on failures
     */
    @NonNull
    private String fetch(@NonNull final String path,
                         final int buffer)
            throws IOException {

        try (TerminatorConnection con = new TerminatorConnection(mServerUri + path)) {
            con.setTimeouts(CONNECT_TIMEOUT_IN_MS, READ_TIMEOUT_IN_MS);
            con.setSSLContext(mSslContext);
            if (mAuthHeader != null) {
                con.setRequestProperty(HttpConstants.AUTHORIZATION, mAuthHeader);
            }
            checkResponseCode(con.getRequest(), R.string.site_calibre);

            try (InputStream is = con.getInputStream();
                 InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr, buffer)) {

                return reader.lines().collect(Collectors.joining());
            }
        } catch (@NonNull final UncheckedIOException e) {
            //noinspection ConstantConditions
            throw e.getCause();
        }
    }

    /**
     * Create the custom SSLContext if there is a custom CA file configured.
     *
     * @param context Current context
     *
     * @return an SSLContext, or {@code null} if the custom CA file (certificate) was not found.
     *
     * @throws IOException          on failures
     * @throws CertificateException on failures related to the user installed CA.
     */
    @Nullable
    private SSLContext getSslContext(@NonNull final Context context)
            throws IOException, CertificateException {

        try {
            final Certificate ca;
            try (InputStream is = context.openFileInput(CA_FILE)) {
                ca = CertificateFactory.getInstance("X.509").generateCertificate(is);
            } catch (@NonNull final FileNotFoundException ignore) {
                // we are (have to) assuming that the server CA is loaded
                // in the Android system keystore.
                return null;
            }

            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("calibre", ca);

            final TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            return sslContext;

        } catch (@NonNull final KeyStoreException | NoSuchAlgorithmException e) {
            // As we're using defaults for the keystore type and trust algorithm,
            // we should never get these 2... flw
            throw new SSLException(e);

        } catch (@NonNull final KeyManagementException e) {
            // wrap for ease of handling; it is in fact almost certain that
            // we would throw a CertificateException BEFORE we can even
            // get a KeyManagementException
            throw new CertificateException(e);
        }
    }

    /**
     * Implicitly connect (if not already done so) and check the response code.
     *
     * @param request   to check
     * @param siteResId site identifier
     *
     * @throws CredentialsException  on login failure
     * @throws HttpNotFoundException the URL was not found
     * @throws HttpStatusException   on other HTTP failures
     * @throws IOException           on other failures
     */
    private void checkResponseCode(@NonNull final HttpURLConnection request,
                                   @SuppressWarnings("SameParameterValue")
                                   @StringRes final int siteResId)
            throws CredentialsException, HttpNotFoundException, HttpStatusException, IOException {
        // Make sure the server was happy.
        final int responseCode = request.getResponseCode();
        switch (responseCode) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                break;

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                throw new CredentialsException(siteResId,
                                               request.getResponseMessage(),
                                               request.getURL());

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new HttpNotFoundException(siteResId,
                                                request.getResponseMessage(),
                                                request.getURL());

            default:
                throw new HttpStatusException(siteResId,
                                              responseCode,
                                              request.getResponseMessage(),
                                              request.getURL());
        }
    }
}
