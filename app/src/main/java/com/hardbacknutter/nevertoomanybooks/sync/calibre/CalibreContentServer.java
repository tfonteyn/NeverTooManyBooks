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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.network.ConnectionValidator;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpPost;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

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
 * <p>
 * Notes on using multiple libraries:
 *     src/calibre/srv/standalone.py, "main"
 * <p>
 *    calibre-server ... /path/to/lib
 *    ==> will serve the single specified lib.
 * <p>
 *    WITHOUT specifying the path, Calibre will read from
 * <p>
 *    C:\Users\USER\AppData\Roaming\calibre\gui.json
 *    /home/USER/.config/calibre/gui.json
 *    key:
 *    "library_usage_stats": {
 *     "C:/Users/USER/Calibre Library": 184,
 *     "C:/Users/USER/Downloads/test": 1
 *   },
 * <p>
 * The default lib seems to be simply the first one in the list.
 * <p>
 *   NOT actually tested on Linux, but other config files are in visible USER/.config/calibre
 * <p>
 *   To check:
 * <p>
 *   seems if it does not find the above, it will look for "global.py.json"
 *   key:
 *   "library_path": "C:\\Users\\USER\\Downloads\\test",
 */
public class CalibreContentServer
        implements ConnectionValidator {

    /** CA certificate identifier. */
    public static final String SERVER_CA = "CalibreContentServer.ca";
    /** Response root tag: The array of book ids returned in 'this' call. */
    static final String RESPONSE_TAG_BOOK_IDS = "book_ids";
    /** Preferences prefix. */
    static final String PREF_KEY = "calibre";

    /** Type: {@code String}. Matches "res/xml/preferences_calibre.xml". */
    static final String PK_HOST_URL = PREF_KEY + Prefs.pk_suffix_host_url;
    static final String PK_HOST_USER = PREF_KEY + ".host.user";
    static final String PK_HOST_PASS = PREF_KEY + ".host.password";
    /** A text "None" as value. Can/will be seen. This is the python equivalent of {@code null}. */
    static final String VALUE_IS_NONE = "None";
    /** Response root tag: Total number of items found in a query. */
    static final String RESPONSE_TAG_TOTAL_NUM = "total_num";
    /** Response root tag: Number of items returned in 'this' call. */
    static final String RESPONSE_TAG_NUM = "num";

    /** Log tag. */
    private static final String TAG = "CalibreContentServer";

    /** Custom field for {@link SyncReaderMetaData}. */
    public static final String BKEY_LIBRARY = TAG + ":defLib";
    /** Custom field for {@link SyncReaderMetaData}. */
    public static final String BKEY_LIBRARY_LIST = TAG + ":libs";
    static final String BKEY_EXT_INSTALLED = TAG + ":extInst";

    /** Response root tag. */
    private static final String RESPONSE_TAG_VIRTUAL_LIBRARIES = "virtual_libraries";
    private static final String PK_LOCAL_FOLDER_URI = PREF_KEY + ".folder";

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
    private static final int READ_TIMEOUT_IN_MS = 3_000;

    private static final String ULR_AJAX_LIBRARY_INFO = "/ajax/library-info";
    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "CL";
    @NonNull
    private final Uri serverUri;
    /** The header string: "Basic user:password". (in base64) */
    @Nullable
    private final String authHeader;
    @Nullable
    private final SSLContext sslContext;

    /** As read from the Content Server. */
    @NonNull
    private final ArrayList<CalibreLibrary> libraries = new ArrayList<>();

    private final Set<CalibreCustomField> calibreCustomFields = new HashSet<>();

    private final int connectTimeoutInMs;
    private final int readTimeoutInMs;

    @Nullable
    private FutureHttpPost<Void> futureHttpPost;
    @Nullable
    private FutureHttpGet<String> futureJsonFetchRequest;
    @Nullable
    private FutureHttpGet<Uri> futureFileFetchRequest;
    @Nullable
    private ImageDownloader imageDownloader;
    /** As read from the Content Server. */
    @Nullable
    private CalibreLibrary defaultLibrary;
    private boolean calibreExtensionInstalled;

    /**
     * Constructor.
     * Uses the configured content server Uri
     *
     * @param context Current context
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    @AnyThread
    public CalibreContentServer(@NonNull final Context context)
            throws CertificateException {
        this(context, Uri.parse(getHostUrl(context)));
    }

    /**
     * Constructor.
     *
     * @param context Current context
     * @param uri     for the content server
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    @AnyThread
    public CalibreContentServer(@NonNull final Context context,
                                @NonNull final Uri uri)
            throws CertificateException {

        serverUri = uri;

        // We're assuming Calibre will be setup with basic-auth as per their SSL recommendations
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String username = prefs.getString(PK_HOST_USER, "");
        if (username.isEmpty()) {
            authHeader = null;
        } else {
            final String password = prefs.getString(PK_HOST_PASS, "");
            authHeader = createBasicAuthHeader(username, password);
        }

        // accommodate the (usually) self-signed CA certificate
        if ("https".equals(serverUri.getScheme())) {
            // *if* a certificate is configured *then*
            // we might get a CertificateException.... which we MUST propagate!
            sslContext = getSslContext(context);
        } else {
            sslContext = null;
        }

        connectTimeoutInMs = Prefs.getTimeoutValueInMs(
                PREF_KEY + "." + Prefs.pk_timeout_connect_in_seconds,
                CONNECT_TIMEOUT_IN_MS);
        readTimeoutInMs = Prefs.getTimeoutValueInMs(
                PREF_KEY + "." + Prefs.pk_timeout_read_in_seconds,
                READ_TIMEOUT_IN_MS);

        calibreCustomFields.addAll(ServiceLocator.getInstance().getCalibreCustomFieldDao()
                                                 .getCustomFields());
    }

    /**
     * Get the default/stored host url.
     *
     * @param context Current context
     *
     * @return url
     */
    @NonNull
    @AnyThread
    public static String getHostUrl(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(PK_HOST_URL, "");
    }

    @AnyThread
    static void setFolderUri(@NonNull final Context context,
                             @NonNull final Uri uri)
            throws SecurityException {
        final ContentResolver contentResolver = context.getContentResolver();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // If the old one is different then the current selection, release the previous Uri
        final String oldFolder = prefs.getString(PK_LOCAL_FOLDER_URI, "");
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

            prefs.edit()
                 .putString(PK_LOCAL_FOLDER_URI, uri.toString())
                 .apply();
        } catch (@NonNull final SecurityException e) {
            LoggerFactory.getLogger().e(TAG, e, "uri=" + uri);
            throw e;
        }
    }

    @NonNull
    @AnyThread
    static Optional<Uri> getFolderUri(@NonNull final Context context) {

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

    public static void setCertificate(@NonNull final Context context,
                                      @Nullable final X509Certificate ca)
            throws IOException, CertificateEncodingException {
        if (ca != null) {
            try (FileOutputStream fos = context.openFileOutput(SERVER_CA, Context.MODE_PRIVATE)) {
                fos.write(ca.getEncoded());
            }
        } else {
            context.deleteFile(SERVER_CA);
        }
    }

    @NonNull
    public static X509Certificate getCertificate(@NonNull final Context context)
            throws CertificateException, IOException {
        try (InputStream is = context.openFileInput(SERVER_CA)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                                                       .generateCertificate(is);
        }
    }

    @AnyThread
    @NonNull
    private String createBasicAuthHeader(@NonNull final String username,
                                         @NonNull final String password) {
        return "Basic " + Base64.encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8), 0);
    }

    /**
     * Create the custom SSLContext if there is a custom CA file configured.
     *
     * @param context Current context
     *
     * @return an SSLContext, or {@code null} if the custom CA file (certificate) was not found.
     *
     * @throws CertificateException on failures related to a user installed CA.
     */
    @Nullable
    private SSLContext getSslContext(@NonNull final Context context)
            throws CertificateException {

        try {
            final X509Certificate ca = getCertificate(context);

            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry(SERVER_CA, ca);

            final TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            return sslContext;

        } catch (@NonNull final KeyManagementException e) {
            // wrap for ease of handling; it is in fact almost certain that
            // we would throw a CertificateException BEFORE we can even
            // get a KeyManagementException
            throw new CertificateException(e);

        } catch (@NonNull final IOException | KeyStoreException | NoSuchAlgorithmException ignore) {
            // All these exceptions, can be ignored and we are assuming
            // that the server does not need a cert, or that the cert is
            // loaded in the Android system keystore.
            return null;
        }
    }

    @NonNull
    private <FRT> FutureHttpGet<FRT> createFutureGetRequest() {
        final FutureHttpGet<FRT> httpGet = FutureHttpGet.createGet(R.string.site_calibre);
        httpGet.setConnectTimeout(connectTimeoutInMs)
               .setReadTimeout(readTimeoutInMs)
               .setRequestProperty(HttpConstants.AUTHORIZATION, authHeader)
               .setSSLContext(sslContext);
        return httpGet;
    }

    @NonNull
    private <FRT> FutureHttpPost<FRT> createFuturePostRequest() {
        final FutureHttpPost<FRT> httpPost = new FutureHttpPost<>(R.string.site_calibre);
        httpPost.setConnectTimeout(connectTimeoutInMs)
                .setReadTimeout(readTimeoutInMs)
                .setRequestProperty(HttpConstants.CONTENT_TYPE, HttpConstants.CONTENT_TYPE_JSON)
                .setRequestProperty(HttpConstants.AUTHORIZATION, authHeader)
                .setSSLContext(sslContext);
        return httpPost;
    }

    @WorkerThread
    @Override
    public boolean validateConnection()
            throws StorageException,
                   IOException {
        return !fetch(serverUri + ULR_AJAX_LIBRARY_INFO, BUFFER_SMALL).isEmpty();
    }

    boolean isMetaDataRead() {
        return defaultLibrary != null;
    }

    /**
     * endpoint('/ajax/library-info', postprocess=json)
     * <p>
     * Return info about available libraries and their meta data from the server.
     * <ul>
     *     <li>number of books in the given library</li>
     *     <li>user custom fields definitions for this library</li>
     * </ul>
     *
     * <pre>
     * {"library_map":
     *      {"Calibre_Library": "Calibre Library"},
     *      "default_library": "Calibre_Library"
     * }
     * </pre>
     * <p>
     * populates {@link #defaultLibrary} + {@link #libraries}
     *
     * @param context Current context
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    public void readMetaData(@NonNull final Context context)
            throws IOException,
                   StorageException,
                   JSONException {

        libraries.clear();
        defaultLibrary = null;

        final CalibreLibraryDao libraryDao = ServiceLocator.getInstance().getCalibreLibraryDao();

        final long currentBookshelfId = Bookshelf.getBookshelf(context, Bookshelf.PREFERRED)
                                                 .map(Bookshelf::getId)
                                                 .orElse((long) Bookshelf.DEFAULT);

        final JSONObject source = new JSONObject(
                fetch(serverUri + ULR_AJAX_LIBRARY_INFO, BUFFER_SMALL));

        final JSONObject libraryMap = source.getJSONObject("library_map");
        final String defaultLibraryId = source.getString("default_library");
        // only present if our extension is installed
        final JSONObject libraryDetails = source.optJSONObject("library_details");
        calibreExtensionInstalled = libraryDetails != null;

        final Iterator<String> it = libraryMap.keys();
        while (it.hasNext()) {
            final String libraryId = it.next();
            // read the standard info
            final String name = libraryMap.getString(libraryId);

            // read the extended info if present
            final String uuid;
            final JSONObject vlibs;
            if (libraryDetails != null && !libraryDetails.isNull(libraryId)) {
                final JSONObject details = libraryDetails.getJSONObject(libraryId);
                uuid = details.getString("uuid");
                if (details.isNull(RESPONSE_TAG_VIRTUAL_LIBRARIES)) {
                    vlibs = null;
                } else {
                    vlibs = details.getJSONObject(RESPONSE_TAG_VIRTUAL_LIBRARIES);
                }
            } else {
                uuid = "";
                vlibs = null;
            }

            @Nullable
            CalibreLibrary library = null;
            if (!uuid.isEmpty()) {
                library = libraryDao.findLibraryByUuid(uuid);
            }
            if (library == null) {
                library = libraryDao.findLibraryByStringId(libraryId);
            }
            if (library == null) {
                // must be a new one.
                library = new CalibreLibrary(uuid, libraryId, name, currentBookshelfId);

            } else {
                // we found it by uuid or id, update it with the server info
                // (even if unchanged... )
                library.setUuid(uuid);
                library.setName(name);
            }

            // If we have vl info, process it
            // If we don't; the library will keep any vl defined previously
            if (vlibs != null) {
                processVirtualLibraries(libraryDao, library, vlibs);
            }

            if (library.getId() > 0) {
                libraryDao.update(library);
            } else {
                libraryDao.insert(library);
            }

            // add to cached list
            libraries.add(library);
            // and set as default if it is.
            if (libraryId.equals(defaultLibraryId)) {
                defaultLibrary = library;
            }

            // read the first book available to get the customs fields (if any)
            final JSONObject result = getBookIds(library.getLibraryStringId(), 1, 0);
            // grab the initial/current total number of books while we have it
            library.setTotalBooks(result.optInt(RESPONSE_TAG_TOTAL_NUM));

            final JSONArray calibreIds = result.optJSONArray(RESPONSE_TAG_BOOK_IDS);
            if (calibreIds != null && !calibreIds.isEmpty()) {
                loadCustomFieldDefinitions(library, calibreIds.getInt(0));
            }
        }

        // Sanity check
        Objects.requireNonNull(defaultLibrary, "defaultLibrary");
    }

    private void processVirtualLibraries(@NonNull final CalibreLibraryDao dao,
                                         @NonNull final CalibreLibrary library,
                                         @NonNull final JSONObject virtualLibraries)
            throws JSONException {

        final List<CalibreVirtualLibrary> vlibs = new ArrayList<>();

        final Iterator<String> it = virtualLibraries.keys();
        while (it.hasNext()) {
            final String name = it.next();
            final String expr = virtualLibraries.getString(name);

            CalibreVirtualLibrary dbVirtLib = dao.getVirtualLibrary(library.getId(), name);
            if (dbVirtLib == null) {
                dbVirtLib = new CalibreVirtualLibrary(library.getId(), name, expr,
                                                      library.getMappedBookshelfId());
            } else {
                dbVirtLib.setName(name);
                dbVirtLib.setExpr(expr);
            }
            vlibs.add(dbVirtLib);
        }

        // hook them up to the library itself; always overwriting the current(previous) list.
        library.setVirtualLibraries(vlibs);
    }

    private void loadCustomFieldDefinitions(@NonNull final CalibreLibrary library,
                                            final int bookId)
            throws StorageException, IOException, JSONException {

        final Set<CalibreCustomField> fields = new HashSet<>();
        final JSONObject calibreBook = getBook(library.getLibraryStringId(), bookId);
        final JSONObject userMetaData = calibreBook.optJSONObject(CalibreBook.USER_METADATA);
        if (userMetaData != null) {
            // check the supported fields
            for (final CalibreCustomField cf : this.calibreCustomFields) {
                final JSONObject data = userMetaData.optJSONObject(cf.getCalibreKey());
                // do we have a match? (this check is needed, it's NOT a sanity check)
                if (data != null && cf.getType().equals(data.getString(
                        CalibreCustomField.METADATA_DATATYPE))) {
                    fields.add(cf);
                }
            }
        }
        // finally, hook them up to the library itself.
        library.setCustomFields(fields);
    }

    @AnyThread
    boolean isExtensionInstalled() {
        return calibreExtensionInstalled;
    }

    /**
     * Get the list of libraries; usually just the one.
     *
     * @return list
     */
    @NonNull
    @AnyThread
    public ArrayList<CalibreLibrary> getLibraries() {
        return libraries;
    }

    /**
     * Get the default library.
     *
     * @return library
     */
    @NonNull
    CalibreLibrary getDefaultLibrary() {
        return Objects.requireNonNull(defaultLibrary, "defaultLibrary");
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
     * @param libraryStringId the Calibre native {@code stringId} for the library to read from
     * @param calibreIds      the list of books (id only)
     *
     * @return see above, or {@code null} if the extension is missing
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @Nullable
    JSONObject getVirtualLibrariesForBooks(@NonNull final String libraryStringId,
                                           @NonNull final JSONArray calibreIds)
            throws IOException,
                   StorageException,
                   JSONException {
        if (!calibreExtensionInstalled) {
            return null;
        }

        final String url = serverUri + "/ntmb/virtual-libraries-for-books/"
                           + getCsvIds(calibreIds) + "/" + libraryStringId;
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
     * @param libraryStringId the Calibre native {@code stringId} for the library to read from
     * @param num             number of books to fetch
     * @param offset          to start fetching from
     *
     * @return see above
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    public JSONObject getBookIds(@NonNull final String libraryStringId,
                                 @SuppressWarnings("SameParameterValue") final int num,
                                 final int offset)
            throws StorageException,
                   IOException,
                   JSONException {

        final String url = serverUri + "/ajax/category/616c6c626f6f6b73/" + libraryStringId
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
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    public JSONObject search(@NonNull final String libraryId,
                             @SuppressWarnings("SameParameterValue") final int num,
                             final int offset,
                             @NonNull final String query)
            throws StorageException,
                   IOException,
                   JSONException {

        final String url = serverUri + "/ajax/search/" + libraryId
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
     * @param libraryStringId the Calibre native {@code stringId} for the library to read from
     * @param calibreIds      the list of books (id only)
     *
     * @return JSONObject with a list of Calibre book objects; NOT an array.
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    JSONObject getBooks(@NonNull final String libraryStringId,
                        @NonNull final JSONArray calibreIds)
            throws StorageException,
                   IOException,
                   JSONException {

        final String url = serverUri + "/ajax/books/" + libraryStringId
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
     * @param libraryStringId the Calibre native {@code stringId} for the library to read from
     * @param calibreUuid     of the book to get
     *
     * @return Calibre book object
     *
     * @throws IOException on generic/other IO failuress
     */
    @WorkerThread
    @NonNull
    public JSONObject getBook(@NonNull final String libraryStringId,
                              @NonNull final String calibreUuid)
            throws StorageException, IOException, JSONException {

        final String url = serverUri + "/ajax/book/" + calibreUuid + '/' + libraryStringId
                           + "?id_is_uuid=true";
        return new JSONObject(fetch(url, BUFFER_BOOK));
    }

    /**
     * See{@link #getBook(String, String)}.
     *
     * @param libraryStringId the Calibre native {@code stringId} for the library to read from
     * @param calibreId       of the book to get
     *
     * @return Calibre book object
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    public JSONObject getBook(@NonNull final String libraryStringId,
                              final int calibreId)
            throws StorageException, IOException, JSONException {

        final String url = serverUri + "/ajax/book/" + calibreId + '/' + libraryStringId;
        return new JSONObject(fetch(url, BUFFER_BOOK));
    }

    @WorkerThread
    @NonNull
    Optional<File> getCover(final int calibreId,
                            @NonNull final String coverUrl)
            throws StorageException {

        synchronized (this) {
            if (imageDownloader == null) {
                imageDownloader = new ImageDownloader(createFutureGetRequest());
            }
        }
        final File tmpFile = imageDownloader
                .getTempFile(FILENAME_SUFFIX, String.valueOf(calibreId), 0, null);

        return imageDownloader.fetch(serverUri + coverUrl, tmpFile);
    }

    /**
     * Fetch the given url content as a single string.
     *
     * @param url    to read
     * @param buffer size for the read
     *
     * @return content
     *
     * @throws CancellationException  if the user cancelled us
     * @throws SocketTimeoutException if the timeout expires before
     *                                the connection can be established
     * @throws IOException            on generic/other IO failures
     * @throws StorageException       on storage related failures
     */
    @NonNull
    private String fetch(@NonNull final String url,
                         final int buffer)
            throws CancellationException,
                   StorageException,
                   SocketTimeoutException,
                   IOException {
        synchronized (this) {
            if (futureJsonFetchRequest == null) {
                futureJsonFetchRequest = createFutureGetRequest();
            }
        }
        return futureJsonFetchRequest.get(url, request -> {
            try (BufferedInputStream bis = new BufferedInputStream(
                    request.getInputStream());
                 InputStreamReader isr = new InputStreamReader(bis, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr, buffer)) {

                return reader.lines().collect(Collectors.joining());
            } catch (@NonNull final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Download the main format file for the given book and store it in the given folder.
     *
     * @param context          Current context
     * @param book             to download
     * @param folder           to store the download in
     * @param progressListener Progress and cancellation interface
     *
     * @return the file
     *
     * @throws CancellationException  if the user cancelled us
     * @throws SocketTimeoutException if the timeout expires before
     *                                the connection can be established
     * @throws StorageException       on storage related failures
     * @throws IOException            on generic/other IO failures
     */
    @NonNull
    Uri fetchFile(@NonNull final Context context,
                  @NonNull final Book book,
                  @NonNull final Uri folder,
                  @NonNull final ProgressListener progressListener)
            throws CancellationException,
                   StorageException,
                   SocketTimeoutException,
                   IOException {

        final DocumentFile destFile = getDocumentFile(context, book, folder, true);

        final int id = book.getInt(DBKey.CALIBRE_BOOK_ID);
        final String format = book.getString(DBKey.CALIBRE_BOOK_MAIN_FORMAT);
        final long libraryId = book.getLong(DBKey.FK_CALIBRE_LIBRARY);

        final CalibreLibrary calibreLibrary = libraries
                .stream()
                .filter(library -> library.getId() == libraryId)
                .findFirst()
                .orElseThrow(() -> new FileNotFoundException(
                        context.getString(R.string.error_file_not_found,
                                          String.valueOf(libraryId))));

        final String url = serverUri + "/get/" + format + "/" + id + "/"
                           + calibreLibrary.getLibraryStringId();

        final Uri destUri = destFile.getUri();

        synchronized (this) {
            if (futureFileFetchRequest == null) {
                futureFileFetchRequest = createFutureGetRequest();
            }
        }
        return futureFileFetchRequest.get(url, request -> {
            try (OutputStream os = context.getContentResolver().openOutputStream(destUri)) {
                if (os != null) {
                    try (BufferedOutputStream bos = new BufferedOutputStream(os);
                         BufferedInputStream bis = new BufferedInputStream(
                                 request.getInputStream(), BUFFER_FILE)) {

                        progressListener.publishProgress(0, context.getString(
                                R.string.progress_msg_loading));
                        FileUtils.copy(bis, bos);
                    }
                }
            } catch (@NonNull final IOException e) {
                if (destFile.exists()) {
                    destFile.delete();
                }
                throw new UncheckedIOException(e);
            }
            // the destFile is now properly closed.
            if (destFile.exists()) {
                return destUri;
            } else {
                throw new UncheckedIOException(
                        new FileNotFoundException(context.getString(
                                R.string.error_file_not_found, destFile.getName())));
            }
        });
    }

    /**
     * Get the DocumentFile for the given book.
     *
     * @param context  Current context
     * @param book     to get
     * @param folder   where the files are
     * @param creating set {@code true} when creating, set {@code false} for checking existence
     *
     * @return the eBook file
     *
     * @throws FileNotFoundException on any failure
     */
    @NonNull
    DocumentFile getDocumentFile(@NonNull final Context context,
                                 @NonNull final Book book,
                                 @NonNull final Uri folder,
                                 final boolean creating)
            throws FileNotFoundException {

        // we're not assuming ANYTHING....
        final DocumentFile root = DocumentFile.fromTreeUri(context, folder);
        if (root == null) {
            throw new FileNotFoundException(folder.toString());
        }

        final String authorDirectory = createAuthorDirectoryName(context, book);

        // FIRST check if it exists
        DocumentFile authorFolder = root.findFile(authorDirectory);
        if (authorFolder == null) {
            if (creating) {
                authorFolder = root.createDirectory(authorDirectory);
            }
            if (authorFolder == null) {
                throw new FileNotFoundException(authorDirectory);
            }
        }

        final String fileName = createFilename(context, book);
        final String fileExt = book.getString(DBKey.CALIBRE_BOOK_MAIN_FORMAT);

        // FIRST check if it exists using the format extension
        DocumentFile bookFile = authorFolder.findFile(fileName + "." + fileExt);
        if (bookFile == null) {
            if (creating) {
                // when creating, we must NOT directly use the extension,
                // but deduce the mime type from the extension.
                final String mimeType = FileUtils.getMimeTypeFromExtension(fileExt);
                bookFile = authorFolder.createFile(mimeType, fileName);
            }
            if (bookFile == null) {
                throw new FileNotFoundException(fileName);
            }
        }

        return bookFile;
    }

    @VisibleForTesting
    @NonNull
    String createAuthorDirectoryName(@NonNull final Context context,
                                     @NonNull final Book book)
            throws FileNotFoundException {
        final Author primaryAuthor = Objects.requireNonNullElseGet(
                book.getPrimaryAuthor(), () -> Author.createUnknownAuthor(context));

        String authorDirectory = primaryAuthor.getFamilyName();
        final String givenNames = primaryAuthor.getGivenNames();
        if (!givenNames.isEmpty()) {
            authorDirectory += ", " + givenNames;
        }

        authorDirectory = FileUtils.buildValidFilename(authorDirectory);

        // A little extra nastiness... if our name ends with a '.'
        // then Android, in its infinite wisdom, will remove it
        // If we escape it, Android will turn it into a '_'
        // Hence, we remove it ourselves, so a subsequent findFile will work.
        while (authorDirectory.endsWith(".") && authorDirectory.length() > 2) {
            authorDirectory = authorDirectory.substring(0, authorDirectory.length() - 1).trim();
        }
        return authorDirectory;
    }

    @VisibleForTesting
    @NonNull
    String createFilename(@NonNull final Context context,
                          @NonNull final Book book)
            throws FileNotFoundException {
        final String name = book.getPrimarySeries()
                                .map(series -> series.getLabel(context) + " - ")
                                .orElse("")
                            + book.getTitle();

        // Combine, and filter all other invalid characters for filenames
        return FileUtils.buildValidFilename(name);
    }

    /**
     * Send updates to the server.
     *
     * @param libraryStringId the Calibre native {@code stringId} for the library to write to
     * @param calibreId       book to update
     * @param changes         to send
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     */
    void pushChanges(@NonNull final String libraryStringId,
                     final int calibreId,
                     @NonNull final JSONObject changes)
            throws IOException, JSONException, StorageException {

        final JSONArray loadedBookIds = new JSONArray()
                .put(calibreId);

        final String url = serverUri + "/cdb/set-fields/" + calibreId + '/' + libraryStringId;
        final String postBody = new JSONObject()
                .put("changes", changes)
                .put("loaded_book_ids", loadedBookIds)
                .toString();

        synchronized (this) {
            if (futureHttpPost == null) {
                futureHttpPost = createFuturePostRequest();
            }
        }
        futureHttpPost.post(url, postBody, null);
    }

    public void cancel() {
        synchronized (this) {
            if (futureJsonFetchRequest != null) {
                futureJsonFetchRequest.cancel();
            }
            if (futureFileFetchRequest != null) {
                futureFileFetchRequest.cancel();
            }
            if (imageDownloader != null) {
                imageDownloader.cancel();
            }
            if (futureHttpPost != null) {
                futureHttpPost.cancel();
            }
        }
    }

}
