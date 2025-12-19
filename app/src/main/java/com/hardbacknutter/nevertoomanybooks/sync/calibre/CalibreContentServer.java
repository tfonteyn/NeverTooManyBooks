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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.annotation.SuppressLint;
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.network.ConnectionValidator;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.network.HttpCallFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

import okhttp3.OkHttpClient;
import okhttp3.Request;

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
public final class CalibreContentServer
        implements ConnectionValidator {

    /** CA certificate identifier. */
    public static final String SERVER_CA = "CalibreContentServer.ca";

    /** Preferences prefix. */
    public static final String PREFERENCE_KEY = "calibre";

    /** Type: {@code String}. Matches "res/xml/preferences_calibre.xml". */
    static final String PK_HOST_URL = PREFERENCE_KEY + '.' + SearchEngineConfig.PK_HOST_URL;
    static final String PK_HOST_USER = PREFERENCE_KEY + '.' + SearchEngineConfig.PK_HOST_USER;
    static final String PK_HOST_PASS = PREFERENCE_KEY + '.' + SearchEngineConfig.PK_HOST_PASSWORD;
    /**
     * Calibre treats the ISBN as just another identifier.
     * "isbn_10", "isbn_13" are also used, in particular by the ISFDB plugin for Calibre
     * We're ignoring them as the "isbn" should take precedence really
     */
    static final String IDENTIFIER_ISBN = "isbn";
    /** Response root tag: Total number of items found in a query. */
    static final String RESPONSE_TAG_TOTAL_NUM = "total_num";
    /** Response root tag: The array of book ids returned in 'this' call. */
    static final String RESPONSE_TAG_BOOK_IDS = "book_ids";
    private static final String PK_LOCAL_FOLDER_URI = PREFERENCE_KEY + ".folder";

    private static final String AMAZON = "amazon";
    /**
     * Key's that map 1:1 are not listed.
     * This list only maps <strong>known</strong> keys
     * from the predefined list at app install time.
     * <p>
     * Other keys we've seen now and then:
     * "epl"
     * "kobo"
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static final Map<String, String> IDENTIFIER_MAPPING_READER = Map.ofEntries(
            // I'm not clear on why calibre prefers 'amazon' above 'asin'
            // but heck, just convert it.
            Map.entry(AMAZON, Identifier.SID_ASIN),
            // mobi is obsolete so we always map it to pure 'asin'
            Map.entry("mobi-asin", Identifier.SID_ASIN),
            // Calibre typically uses 'uri' but sometimes we see 'url
            Map.entry("url", Identifier.SID_URI)
    );

    /**
     * Key's that map 1:1 are not listed.
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    static final Map<String, String> IDENTIFIER_MAPPING_WRITER = Map.ofEntries(
            // I'm not clear on why calibre prefers 'amazon' above 'asin'
            // but heck, just convert it.
            Map.entry(Identifier.SID_ASIN, AMAZON)
    );
    /** Log tag. */
    private static final String TAG = "CalibreContentServer";

    /** Custom field for {@link SyncReaderMetaData}. */
    public static final String BKEY_LIBRARY = TAG + ":defLib";
    /** Custom field for {@link SyncReaderMetaData}. */
    public static final String BKEY_LIBRARY_LIST = TAG + ":libs";
    static final String BKEY_EXT_INSTALLED = TAG + ":extInst";

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

    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "CL";

    /** Error/bug msg if the default library is null. */
    private static final String ERROR_NULL_DEFAULT_LIBRARY = "defaultLibrary";

    /**
     * The standard request to get information about the libraries available
     * on the server.
     * <p>
     * We're also calling this for connection validation.
     * <p>
     * Param 1: serverUri
     */
    private static final String GET_LIBRARY_INFO = "%1$s/ajax/library-info";

    /**
     * Request the list of virtual libraries.
     * <p>
     * Param 1: serverUri
     * Param 2: csv list of book ids
     * Param 3: libraryStringId
     */
    private static final String NTMB_VIRTUAL_LIBRARIES_FOR_BOOKS =
            "%1$s/ntmb/virtual-libraries-for-books/%2$s/%3$s";

    private static final String SEARCH = "%1$s/ajax/search/%2$s?num=%3$d&offset=%4$d&query=%5$s";

    /**
     * Request a file download.
     * <p>
     * Param 1: serverUri
     * Param 2: file format
     * Param 3: calibre book id
     * Param 4: libraryStringId
     */
    private static final String FETCH_FILE = "%1$s/get/%2$s/%3$d/%4$s";

    private static final String GET_BOOKS = "%1$s/ajax/books/%2$s?category_urls=false&ids=%3$s";

    /**
     * Fetch all book.
     * <p>
     * {@code "616c6c626f6f6b73" == "allbooks"}
     * <p>
     * Param 1: serverUri
     * Param 2: libraryStringId
     * Param 3: number of books
     * Param 4: offset to start fetching from
     *
     * @see #getBookIds(String, int, int)
     */
    private static final String GET_BOOK_IDS =
            "%1$s/ajax/category/616c6c626f6f6b73/%2$s?num=%3$d&offset=%4$d";

    /**
     * Fetch a book by its Calibre uuid.
     * <p>
     * Param 1: serverUri
     * Param 2: book UUID
     * Param 3: libraryStringId
     */
    private static final String GET_BOOK_BY_UUID = "%1$s/ajax/book/%2$s/%3$s?id_is_uuid=true";

    /**
     * Fetch a book by its Calibre numeric id.
     * <p>
     * Param 1: serverUri
     * Param 2: book id
     * Param 3: libraryStringId
     */
    private static final String GET_BOOK_BY_ID = "%1$s/ajax/book/%2$d/%3$s";

    /** Response root tag. */
    private static final String RESPONSE_TAG_VIRTUAL_LIBRARIES = "virtual_libraries";
    /**
     * Present in the response from {@link #GET_LIBRARY_INFO}.
     * Contains the {@link #RESPONSE_TAG_DEFAULT_LIBRARY} and a list of key=value
     * pairs with the libraries.
     */
    private static final String RESPONSE_TAG_LIBRARY_MAP = "library_map";
    /**
     * Present in {@link #RESPONSE_TAG_LIBRARY_MAP} containing the name of
     * the default library.
     */
    private static final String RESPONSE_TAG_DEFAULT_LIBRARY = "default_library";
    /**
     * Potentially present in the response from {@link #GET_LIBRARY_INFO}
     * when our calibre ajax extension is installed on the server.
     * This JSONObject will contain extra information:
     * - library uuid.
     * - virtual libraries.
     */
    private static final String RESPONSE_TAG_LIBRARY_DETAILS = "library_details";

    @NonNull
    private final Uri serverUri;
    @Nullable
    private final SSLContext sslContext;
    @Nullable
    private final HostnameVerifier hostnameVerifier;
    /** As read from the Content Server. */
    @NonNull
    private final List<CalibreLibrary> libraries = new ArrayList<>();
    private final Set<CalibreCustomField> calibreCustomFields;
    private final int connectTimeoutInMs;
    private final int readTimeoutInMs;
    /** The header string: "Basic user:password". (in base64) */
    @Nullable
    private final String authHeader;

    private final BookshelfDao bookshelfDao;
    private final CalibreLibraryDao calibreLibraryDao;

    @Nullable
    private FutureHttp<Void> httpPost;
    @Nullable
    private FutureHttp<String> jsonFetchRequest;
    @Nullable
    private FutureHttp<Uri> fileFetchRequest;
    @Nullable
    private ImageDownloader imageDownloader;
    /** As read from the Content Server. */
    @Nullable
    private CalibreLibrary defaultLibrary;
    private boolean calibreExtensionInstalled;

    /**
     * Constructor.
     *
     * @param uri              for the content server
     * @param username         for the content server
     * @param password         for the content server
     * @param sslContext       (optional) for certificate handling
     * @param hostnameVerifier (optional) for certificate handling
     */
    private CalibreContentServer(@NonNull final Uri uri,
                                 @NonNull final String username,
                                 @NonNull final String password,
                                 @Nullable final SSLContext sslContext,
                                 @Nullable final HostnameVerifier hostnameVerifier) {

        this.serverUri = uri;
        this.sslContext = sslContext;
        this.hostnameVerifier = hostnameVerifier;

        // We're assuming Calibre will be setup with basic-auth as per their SSL recommendations
        if (username.isEmpty()) {
            authHeader = null;
        } else {
            authHeader = "Basic " + Base64.encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8), 0);
        }

        connectTimeoutInMs = SearchEngineConfig.getTimeoutValueInMs(
                PREFERENCE_KEY + '.' + SearchEngineConfig.PK_TIMEOUT_CONNECT_IN_SECONDS,
                CONNECT_TIMEOUT_IN_MS);
        readTimeoutInMs = SearchEngineConfig.getTimeoutValueInMs(
                PREFERENCE_KEY + '.' + SearchEngineConfig.PK_TIMEOUT_READ_IN_SECONDS,
                READ_TIMEOUT_IN_MS);

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        bookshelfDao = serviceLocator.getBookshelfDao();
        calibreLibraryDao = serviceLocator.getCalibreLibraryDao();

        final List<CalibreCustomField> customFields =
                serviceLocator.getCalibreCustomFieldDao().getCustomFields();
        calibreCustomFields = new HashSet<>(customFields);
    }

    /**
     * Get the default/stored host url for the Calibre Content Server instance.
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

    /**
     * Set (in preferences) the local folder where (from Calibre Content Server) downloaded
     * books will be stored.
     *
     * @param context Current context
     * @param uri     for the local folder
     */
    @AnyThread
    static void setFolderUri(@NonNull final Context context,
                             @NonNull final Uri uri) {
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
            // SecurityException is never thrown as the
            // System.getSecurityManager() always return null
            LoggerFactory.getLogger().e(TAG, e, "uri=" + uri);
            throw e;
        }
    }

    /**
     * Get (from preferences) the local folder where (from Calibre Content Server) downloaded
     * books are stored.
     *
     * @param context Current context
     *
     * @return uri for the local folder
     */
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

    /**
     * Set the self-signed CA certificate.
     *
     * @param context Current context
     * @param ca      the certificate
     *
     * @throws CertificateEncodingException on failures related to a user installed CA
     * @throws IOException                  on generic/other IO failures
     */
    public static void setCertificate(@NonNull final Context context,
                                      @Nullable final X509Certificate ca)
            throws CertificateEncodingException, IOException {
        if (ca != null) {
            try (FileOutputStream fos = context.openFileOutput(SERVER_CA, Context.MODE_PRIVATE)) {
                fos.write(ca.getEncoded());
            }
        } else {
            context.deleteFile(SERVER_CA);
        }
    }

    /**
     * Get the self-signed CA certificate.
     *
     * @param context Current context
     *
     * @return the certificate
     *
     * @throws CertificateException on failures related to a user installed CA
     * @throws IOException          on generic/other IO failures
     */
    @NonNull
    public static X509Certificate getCertificate(@NonNull final Context context)
            throws CertificateException, IOException {
        try (InputStream is = context.openFileInput(SERVER_CA)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                                                       .generateCertificate(is);
        }
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
    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    @Nullable
    private static SSLContext getSslContext(@NonNull final Context context)
            throws CertificateException {

        try {
            final X509Certificate ca = getCertificate(context);

            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry(SERVER_CA, ca);

            final TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            final SSLContext tls = SSLContext.getInstance("TLS");
            tls.init(null, tmf.getTrustManagers(), null);
            return tls;

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
    private OkHttpClient createHttpClient() {
        final OkHttpClient.Builder builder = ServiceLocator
                .getInstance()
                .getOkHttpClient()
                .newBuilder()
                .connectTimeout(connectTimeoutInMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutInMs, TimeUnit.MILLISECONDS);

        if (sslContext != null) {
            builder.setSocketFactory$okhttp(sslContext.getSocketFactory());
            if (hostnameVerifier != null) {
                builder.setHostnameVerifier$okhttp(hostnameVerifier);
            }
        }

        return builder.build();
    }

    @NonNull
    private Request createImageRequest(@NonNull final String urlStr)
            throws MalformedURLException {

        // TODO: check adding http headers with Calibre built-in-http-server
        //  versus Calibre hosted behind an Apache server

        final Request.Builder builder = new Request.Builder()
                .url(urlStr)
                .header(HttpConstants.HOST, new URL(urlStr).getHost())
                .header(HttpConstants.USER_AGENT,
                        HttpConstants.BROWSER_USER_AGENT)

                .header(HttpConstants.ACCEPT,
                        HttpConstants.ACCEPT_IMAGE)
                .header(HttpConstants.ACCEPT_ENCODING,
                        HttpConstants.ACCEPT_ENCODING_GZIP)

                .header(HttpConstants.CONNECTION,
                        HttpConstants.CONNECTION_KEEP_ALIVE);

        if (authHeader != null) {
            builder.header(HttpConstants.AUTHORIZATION, authHeader);
        }

        return builder.build();
    }

    @NonNull
    private <FRT> FutureHttp<FRT> createGetRequest() {
        final FutureHttp<FRT> request = HttpCallFactory.create(R.string.site_calibre);

        // TODO: check adding http headers with Calibre built-in-http-server
        //  versus Calibre hosted behind an Apache server

        request.setConnectTimeout(connectTimeoutInMs)
               .setReadTimeout(readTimeoutInMs)
               .setRequestProperty(HttpConstants.AUTHORIZATION, authHeader)
               .setRequestProperty(HttpConstants.ACCEPT_ENCODING,
                                   HttpConstants.ACCEPT_ENCODING_GZIP)
               .setRequestProperty(HttpConstants.CONNECTION,
                                   HttpConstants.CONNECTION_KEEP_ALIVE)
               .setSSLContext(sslContext)
               .setHostnameVerifier(hostnameVerifier);
        return request;
    }

    @NonNull
    private <FRT> FutureHttp<FRT> createPostRequest() {
        final FutureHttp<FRT> request = HttpCallFactory.create(R.string.site_calibre);
        request.setConnectTimeout(connectTimeoutInMs)
               .setReadTimeout(readTimeoutInMs)
               .setRequestProperty(HttpConstants.CONTENT_TYPE, HttpConstants.CONTENT_TYPE_JSON)
               .setRequestProperty(HttpConstants.AUTHORIZATION, authHeader)
               .setSSLContext(sslContext)
               .setHostnameVerifier(hostnameVerifier);
        return request;
    }

    @WorkerThread
    @Override
    public boolean validateConnection(@NonNull final Context context)
            throws StorageException,
                   IOException {
        final String url = String.format(GET_LIBRARY_INFO, serverUri);
        return !fetch(url, BUFFER_SMALL).isEmpty();
    }

    /**
     * Check if {@link #readMetaData()} has been successfully called.
     *
     * @return flag
     */
    boolean isMetaDataRead() {
        return defaultLibrary != null;
    }

    /**
     * Return info about available libraries and their meta data from the server.
     * <pre>
     * {@code
     *      endpoint('/ajax/library-info', postprocess=json)
     * }
     * </pre>
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
     * Populates {@link #defaultLibrary}, {@link #libraries}
     * and the {@link #calibreExtensionInstalled} flag.
     *
     * @throws IOException       on generic/other IO failures
     * @throws StorageException  on storage related failures
     * @throws JSONException     upon any parsing error
     * @throws DaoWriteException on failure to update the database
     */
    @WorkerThread
    public void readMetaData()
            throws IOException,
                   StorageException,
                   JSONException,
                   DaoWriteException {

        libraries.clear();
        defaultLibrary = null;

        // use the current bookshelf (or default if not set)
        final long currentBookshelfId = bookshelfDao.getCurrent()
                                                    .orElseGet(bookshelfDao::getDefault)
                                                    .getId();

        final String url = String.format(GET_LIBRARY_INFO, serverUri);
        final JSONObject source = new JSONObject(fetch(url, BUFFER_SMALL));

        final JSONObject libraryMap = source.getJSONObject(RESPONSE_TAG_LIBRARY_MAP);
        final String defaultLibraryId = source.getString(RESPONSE_TAG_DEFAULT_LIBRARY);
        // only present if our extension is installed
        final JSONObject libraryDetails = source.optJSONObject(RESPONSE_TAG_LIBRARY_DETAILS);
        calibreExtensionInstalled = libraryDetails != null;

        final SynchronizedDb db = ServiceLocator.getInstance().getDb();

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }
            final Iterator<String> it = libraryMap.keys();
            while (it.hasNext()) {
                final String libraryId = it.next();
                // read the standard info
                final String name = libraryMap.getString(libraryId);

                // read the extended info if present
                final String uuid;
                @Nullable
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
                    library = calibreLibraryDao.findLibraryByUuid(uuid).orElse(null);
                }
                if (library == null) {
                    library = calibreLibraryDao.findLibraryByStringId(libraryId).orElse(null);
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
                    processVirtualLibraries(calibreLibraryDao, library, vlibs);
                }

                if (library.getId() > 0) {
                    calibreLibraryDao.update(library);
                } else {
                    calibreLibraryDao.insert(library);
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

            if (txLock != null) {
                db.setTransactionSuccessful();
            }

        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
        // Sanity check
        Objects.requireNonNull(defaultLibrary, ERROR_NULL_DEFAULT_LIBRARY);
    }

    private void processVirtualLibraries(@NonNull final CalibreLibraryDao dao,
                                         @NonNull final CalibreLibrary library,
                                         @NonNull final JSONObject virtualLibraries)
            throws JSONException {

        final List<CalibreVirtualLibrary> vLibs = new ArrayList<>();

        final Iterator<String> it = virtualLibraries.keys();
        while (it.hasNext()) {
            final String name = it.next();
            final String expr = virtualLibraries.getString(name);

            dao.findVirtualLibrary(library.getId(), name).ifPresentOrElse(vLib -> {
                // Update existing
                vLib.setName(name);
                vLib.setExpr(expr);
                vLibs.add(vLib);
            }, () -> {
                // create new
                vLibs.add(new CalibreVirtualLibrary(library.getId(), name, expr,
                                                    library.getMappedBookshelfId()));
            });
        }

        // hook them up to the library itself; always overwriting the current(previous) list.
        library.setVirtualLibraries(vLibs);
    }

    private void loadCustomFieldDefinitions(@NonNull final CalibreLibrary library,
                                            final int bookId)
            throws StorageException, IOException, JSONException {

        final Set<CalibreCustomField> fields = new HashSet<>();
        final JSONObject calibreBook = getBook(library.getLibraryStringId(), bookId);
        final JSONObject userMetaData = calibreBook.optJSONObject(CalibreBookJsonKey.USER_METADATA);
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

    /**
     * Check if the virtual-library support extension has been installed
     * on the Calibre Content Server.
     * <p>
     * Only valid if the meta-data has been read.
     *
     * @return flag
     *
     * @see #readMetaData()
     * @see #isMetaDataRead()
     */
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
    public List<CalibreLibrary> getLibraries() {
        return libraries;
    }

    /**
     * Get the default library.
     *
     * @return library
     */
    @NonNull
    CalibreLibrary getDefaultLibrary() {
        return Objects.requireNonNull(defaultLibrary, ERROR_NULL_DEFAULT_LIBRARY);
    }

    /**
     * Return the book ids with their virtual libraries.
     * <pre>
     * {@code
     *      endpoint('/ntmb/virtual-libraries-for-books/{library_id=None}', postprocess=json)
     * }
     * </pre>
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
     * @throws JSONException    upon any parsing error
     * @see #isExtensionInstalled()
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

        final String url = String.format(NTMB_VIRTUAL_LIBRARIES_FOR_BOOKS, serverUri,
                                         getCsvIds(calibreIds), libraryStringId);
        return new JSONObject(fetch(url, BUFFER_SMALL));
    }

    /**
     * Return a dictionary describing the category specified by name.
     * <pre>
     * {@code
     *      endpoint('/ajax/category/{encoded_name}/{library_id=None}', postprocess=json)
     * }
     * </pre>
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
     * @throws JSONException    upon any parsing error
     */
    @WorkerThread
    @NonNull
    public JSONObject getBookIds(@NonNull final String libraryStringId,
                                 @SuppressWarnings("SameParameterValue") final int num,
                                 final int offset)
            throws StorageException,
                   IOException,
                   JSONException {

        @SuppressLint("DefaultLocale")
        final String url = String.format(GET_BOOK_IDS, serverUri, libraryStringId, num, offset);
        return new JSONObject(fetch(url, BUFFER_SMALL));
    }

    /**
     * Return the books matching the specified search query.
     * <pre>
     * {@code
     *      endpoint('/ajax/search/{library_id=None}', postprocess=json)
     * }
     * </pre>
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
     * @param libraryId to search in
     * @param num       the maximum number of entries to return
     * @param offset    the offset for the next set to return
     * @param query     the search query, see above
     *
     * @return books matching the specified search query.
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     * @throws JSONException    upon any parsing error
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

        @SuppressLint("DefaultLocale")
        final String url = String.format(SEARCH, serverUri, libraryId, num, offset, query);
        return new JSONObject(fetch(url, BUFFER_BOOK_LIST));
    }

    /**
     * Return the metadata of the books in the given library as a JSON dictionary.
     * <pre>
     * {@code
     *      endpoint('/ajax/books/{library_id=None}', postprocess=json)
     * }
     * </pre>
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
     * @throws JSONException    upon any parsing error
     */
    @WorkerThread
    @NonNull
    JSONObject getBooks(@NonNull final String libraryStringId,
                        @NonNull final JSONArray calibreIds)
            throws StorageException,
                   IOException,
                   JSONException {

        final String url = String.format(GET_BOOKS, serverUri, libraryStringId,
                                         getCsvIds(calibreIds));
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
     * Return the metadata of a single book as a JSON dictionary.
     * <pre>
     * {@code
     *      endpoint('/ajax/book/{book_id}/{library_id=None}', postprocess=json)
     * }
     * </pre>
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
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     * @throws JSONException    upon any parsing error
     */
    @WorkerThread
    @NonNull
    public JSONObject getBook(@NonNull final String libraryStringId,
                              @NonNull final String calibreUuid)
            throws StorageException, IOException, JSONException {

        final String url = String.format(GET_BOOK_BY_UUID, serverUri, calibreUuid, libraryStringId);
        return new JSONObject(fetch(url, BUFFER_BOOK));
    }

    /**
     * Same as {@link #getBook(String, String)} but using the {@code calibreId} instead
     * of the {@code calibreUuid}.
     *
     * @param libraryStringId the Calibre native {@code stringId} for the library to read from
     * @param calibreId       of the book to get
     *
     * @return Calibre book object
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     * @throws JSONException    upon any parsing error
     */
    @WorkerThread
    @NonNull
    public JSONObject getBook(@NonNull final String libraryStringId,
                              final int calibreId)
            throws StorageException, IOException, JSONException {

        @SuppressLint("DefaultLocale")
        final String url = String.format(GET_BOOK_BY_ID, serverUri, calibreId, libraryStringId);
        return new JSONObject(fetch(url, BUFFER_BOOK));
    }

    @WorkerThread
    @NonNull
    Optional<File> getCover(final int calibreId,
                            @NonNull final String coverUrl)
            throws IOException, CoverStorageException {

        synchronized (this) {
            if (imageDownloader == null) {
                final OkHttpClient httpClient = createHttpClient();
                imageDownloader = new ImageDownloader(httpClient,
                                                      null,
                                                      R.string.site_calibre,
                                                      false);
            }
        }

        final String tempFilename = ImageFileInfo.getTempFilename(
                FILENAME_SUFFIX, String.valueOf(calibreId), 0, null);

        final Request imageRequest = createImageRequest(serverUri + coverUrl);
        return imageDownloader.fetch(imageRequest, tempFilename);
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

        jsonFetchRequest = createGetRequest();
        jsonFetchRequest.setBufferSize(buffer);
        return jsonFetchRequest.getAsString(url, (con, s) -> s);
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
    @WorkerThread
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

        final int id = book.getInt(DBKey.CALIBRE.BOOK_ID);
        final String format = book.getString(DBKey.CALIBRE.BOOK_MAIN_FORMAT);
        final long libraryId = book.getLong(DBKey.FK_CALIBRE_LIBRARY);

        final CalibreLibrary calibreLibrary = libraries
                .stream()
                .filter(library -> library.getId() == libraryId)
                .findFirst()
                .orElseThrow(() -> new FileNotFoundException(
                        context.getString(R.string.error_file_not_found,
                                          String.valueOf(libraryId))));

        @SuppressLint("DefaultLocale")
        final String url = String.format(FETCH_FILE, serverUri, format, id,
                                         calibreLibrary.getLibraryStringId());

        final Uri destUri = destFile.getUri();

        fileFetchRequest = createGetRequest();
        fileFetchRequest.setBufferSize(BUFFER_FILE);
        return fileFetchRequest.get(url, (con, is) -> {
            try (OutputStream os = context.getContentResolver().openOutputStream(destUri)) {
                if (os != null) {
                    progressListener.publishProgress(0, context.getString(
                            R.string.progress_msg_loading));
                    try (BufferedOutputStream bos = new BufferedOutputStream(os)) {
                        FileUtils.copy(is, bos);
                    }
                }
            } catch (@NonNull final IOException e) {
                if (destFile.exists()) {
                    destFile.delete();
                }
                throw e;
            }
            // the destFile is now properly closed.
            if (destFile.exists()) {
                return destUri;
            } else {
                throw new FileNotFoundException(context.getString(
                        R.string.error_file_not_found, destFile.getName()));
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
        final String fileExt = book.getString(DBKey.CALIBRE.BOOK_MAIN_FORMAT);

        // FIRST check if it exists using the format extension
        DocumentFile bookFile = authorFolder.findFile(fileName + '.' + fileExt);
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
            authorDirectory = authorDirectory.substring(0, authorDirectory.length() - 1).strip();
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
     * <pre>
     * {@code
     *     endpoint('/cdb/set-fields/{book_id}/{library_id=None}',
     *              types={'book_id': int},
     *              needs_db_write=True,
     *              postprocess=msgpack_or_json,
     *              methods=receive_data_methods,
     *              cache_control='no-cache')
     * }
     * </pre>
     *
     * @param libraryStringId the Calibre native {@code stringId} for the library to write to
     * @param calibreId       book to update
     * @param changes         to send
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     * @throws JSONException    upon any parsing error
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
        if (postBody == null) {
            throw new JSONException("postBody was null");
        }

        httpPost = createPostRequest();
        httpPost.post(url, postBody, null);
    }

    public void cancel() {
        synchronized (this) {
            if (jsonFetchRequest != null) {
                jsonFetchRequest.cancel();
            }
            if (fileFetchRequest != null) {
                fileFetchRequest.cancel();
            }
            if (imageDownloader != null) {
                imageDownloader.cancel();
            }
            if (httpPost != null) {
                httpPost.cancel();
            }
        }
    }


    public static class Builder {

        @NonNull
        private final Context context;

        @Nullable
        private String url;
        @Nullable
        private String username;
        @Nullable
        private String password;

        @Nullable
        private SSLContext sslContext;

        @Nullable
        private HostnameVerifier hostnameVerifier;

        public Builder(@NonNull final Context context) {
            this.context = context;
        }

        @NonNull
        public Builder setUrl(@NonNull final String url) {
            this.url = url;
            return this;
        }

        @NonNull
        public Builder setUser(@NonNull final String username) {
            this.username = username;
            return this;
        }

        @NonNull
        public Builder setPassword(@NonNull final String password) {
            this.password = password;
            return this;
        }

        @NonNull
        public Builder setSSLContext(@NonNull final SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * Use for testing only, to bypass the host name verification.
         *
         * @param hostnameVerifier to use
         *
         * @return {@code this} (for chaining)
         */
        @VisibleForTesting
        @NonNull
        public Builder setHostnameVerifier(@NonNull final HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        @NonNull
        public CalibreContentServer build()
                throws CertificateException {
            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(context);

            if (url == null) {
                url = prefs.getString(PK_HOST_URL, "");
            }
            final Uri uri = Uri.parse(url);

            if (username == null) {
                username = prefs.getString(PK_HOST_USER, "");
                password = prefs.getString(PK_HOST_PASS, "");
            }
            if (password == null) {
                password = "";
            }

            if (sslContext == null) {
                if ("https".equals(uri.getScheme())) {
                    // *if* a certificate is configured *then*
                    // we might get a CertificateException.... which we MUST propagate!
                    sslContext = getSslContext(context);
                } else {
                    sslContext = null;
                }
            }

            return new CalibreContentServer(uri, username, password,
                                            sslContext, hostnameVerifier);
        }
    }
}
