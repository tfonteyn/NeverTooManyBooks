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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieStore;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.DefaultRequestCacheKeyProvider;
import com.burgstaller.okhttp.DispatchingAuthenticator;
import com.burgstaller.okhttp.basic.BasicAuthenticator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.network.ConnectionValidator;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpCall;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.core.network.RateLimitInterceptor;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.core.network.ThrottlingInterceptor;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.covers.ImageStorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.network.NetworkConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteAuthModule;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.coders.CalibreBookJsonKey;
import com.hardbacknutter.nevertoomanybooks.utils.OkHttpLoggerFactory;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

import okhttp3.Authenticator;
import okhttp3.CookieJar;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

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

    /** Response root tag: Total number of items found in a query. */
    static final String RESPONSE_TAG_TOTAL_NUM = "total_num";
    /** Response root tag: The array of book ids returned in 'this' call. */
    static final String RESPONSE_TAG_BOOK_IDS = "book_ids";

    /** Log tag. */
    private static final String TAG = "CalibreContentServer";

    /** Custom field for {@link SyncReaderMetaData}. */
    public static final String BKEY_LIBRARY = TAG + ":defLib";
    /** Custom field for {@link SyncReaderMetaData}. */
    public static final String BKEY_LIBRARY_LIST = TAG + ":libs";
    static final String BKEY_PLUGIN_INSTALLED = TAG + ":plugin";

    private static final String PK_HOST_URL =
            PREFERENCE_KEY + '.' + SearchEngineConfig.PK_HOST_URL;
    private static final String PK_HOST_USER =
            PREFERENCE_KEY + '.' + SiteAuthModule.PK_HOST_USER;
    private static final String PK_HOST_PASS =
            PREFERENCE_KEY + '.' + SiteAuthModule.PK_HOST_PASSWORD;

    /**
     * Default for the number of books we pull from the server per request.
     * On a RaspberryPi 1b+ (2012) we used 10, although 25..50 should be ok.
     * It seems a Pi 3b (2016) should be able to handle 100.
     * Anything higher should really handle 250.
     * <p>
     * Given its 2026 right now, we'll assume a 10 year old Pi as the minimum as default.
     *
     * @see #PK_BOOKS_PER_PULL_REQUEST
     */
    private static final int BOOKS_PER_PULL_REQUEST_DEFAULT = 100;

    /**
     * Preference key: the number of books which will be pulled from the server in one request.
     * <p>
     * {@code int}
     *
     * @see #BOOKS_PER_PULL_REQUEST_DEFAULT
     */
    private static final String PK_BOOKS_PER_PULL_REQUEST =
            PREFERENCE_KEY + ".request.pull.nr_of_books";

    /**
     * Default for the number of books we push to the server per request.
     *
     * @see #PK_BOOKS_PER_PUSH_REQUEST
     */
    private static final int BOOKS_PER_PUSH_REQUEST_DEFAULT = 100;

    /**
     * Preference key: the number of books which will be pushed to the server in one request.
     * <p>
     * {@code int}
     *
     * @see #BOOKS_PER_PUSH_REQUEST_DEFAULT
     */
    private static final String PK_BOOKS_PER_PUSH_REQUEST =
            PREFERENCE_KEY + ".request.push.nr_of_books";

    /**
     * Preference key: The local download folder.
     * <p>
     * {@code String}
     */
    private static final String PK_LOCAL_FOLDER_URI = PREFERENCE_KEY + ".folder";

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
     * 8K of data for a single book, and we fetch 10 books at a time... hence 128k.
     */
    private static final int BUFFER_BOOK_LIST = 131_072;
    /**
     * And a huge buffer to download the eBook files themselves.
     */
    private static final int BUFFER_FILE = 1_048_576;

    /** Error/bug msg if the default library is null. */
    private static final String ERROR_NULL_DEFAULT_LIBRARY = "defaultLibrary";

    /**
     * Present in the response from {@link Endpoints#GET_LIBRARY_INFO}
     * and {@link Endpoints#NTMB_GET_LIBRARY_INFO}.
     * <p>
     * Contains a list of {@code libraryStringId=name} pairs for all libraries.
     */
    private static final String RESPONSE_AJAX_LIBRARY_MAP = "library_map";

    /**
     * Present in the response from {@link Endpoints#GET_LIBRARY_INFO}
     * and {@link Endpoints#NTMB_GET_LIBRARY_INFO}.
     * <p>
     * Contains the name of the default library.
     */
    private static final String RESPONSE_AJAX_DEFAULT_LIBRARY = "default_library";

    /**
     * Present in the response from {@link Endpoints#NTMB_GET_LIBRARY_INFO}
     * when our calibre plugin is installed on the server.
     * <p>
     * Contain extra information:
     * <ul>
     *     <li>library uuid</li>
     *     <li>library name</li>
     *     <li>virtual libraries</li>
     *     <li>custom field definitions</li>
     * </ul>
     */
    private static final String RESPONSE_NTMB_LIBRARY_DATA = "library_data";

    /**
     * Present in the response from {@link Endpoints#NTMB_GET_LIBRARY_INFO}
     * when our calibre plugin is installed on the server.
     * <p>
     * Contains the Library UUID
     */
    private static final String RESPONSE_NTMB_UUID = "uuid";

    /**
     * Present in the response from {@link Endpoints#NTMB_GET_LIBRARY_INFO}
     * when our calibre plugin is installed on the server.
     * <p>
     * Contains the virtual libraries (if any)
     *
     * @see #RESPONSE_NTMB_LIBRARY_DATA
     */
    private static final String RESPONSE_NTMB_VIRTUAL_LIBRARIES = "virtual_libraries";

    /**
     * Present in the response from {@link Endpoints#NTMB_GET_LIBRARY_INFO}
     * when our calibre ajax plugin is installed on the server.
     * <p>
     * Contains the custom field definitions (if any)
     *
     * @see #RESPONSE_NTMB_LIBRARY_DATA
     */
    private static final String RESPONSE_NTMB_CUSTOM_FIELDS = "custom_fields";

    /** Ignored by the server, but we still need to set one. */
    private static final String ACCEPT_LANGUAGE_HEADER = "en";

    /** The RequestBody media type. */
    private static final MediaType MEDIA_TYPE_JSON = MediaType
            .parse("application/json; charset=utf-8");

    @NonNull
    private final Uri serverUri;
    /** As read from the Content Server. */
    @NonNull
    private final List<CalibreLibrary> libraries = new ArrayList<>();
    private final Set<CalibreCustomField> calibreCustomFields;

    @NonNull
    private final NetworkConfig networkConfig;
    @NonNull
    private final OkHttpClient httpClient;
    @NonNull
    private final CookieStore cookieStore;

    private final BookshelfDao bookshelfDao;
    private final CalibreLibraryDao libraryDao;

    /** Lazy created in {@link #getImageDownloader()}. */
    @Nullable
    private volatile ImageDownloader imageDownloader;
    /** As read from the Content Server. */
    @Nullable
    private CalibreLibrary defaultLibrary;

    /**
     * {@code null} initially. Set to a value after {@link #readMetaData()} is run.
     */
    @Nullable
    private Boolean pluginInstalled;

    @Nullable
    private HttpCall jsonFetchCall;
    @Nullable
    private HttpCall fileFetchCall;
    @Nullable
    private HttpCall postCall;

    /**
     * Constructor.
     *
     * @param uri              for the content server
     * @param username         for the content server
     * @param password         for the content server
     * @param sslContext       (optional) for certificate handling
     * @param x509TrustManager (optional) for certificate handling;
     *                         Must NOT be {@code null} if the {@code sslContext} is set.
     * @param hostnameVerifier (optional) for certificate handling
     */
    private CalibreContentServer(@NonNull final Uri uri,
                                 @NonNull final String username,
                                 @NonNull final String password,
                                 @Nullable final SSLContext sslContext,
                                 @Nullable final X509TrustManager x509TrustManager,
                                 @Nullable final HostnameVerifier hostnameVerifier) {

        this.serverUri = uri;

        final ServiceLocator serviceLocator = ServiceLocator.getInstance();

        bookshelfDao = serviceLocator.getBookshelfDao();
        libraryDao = serviceLocator.getCalibreLibraryDao();

        final List<CalibreCustomField> customFields =
                serviceLocator.getCalibreCustomFieldDao().getCustomFields();
        calibreCustomFields = new HashSet<>(customFields);

        cookieStore = serviceLocator.getCookieManager().getCookieStore();

        networkConfig = new CalibreNetworkConfig();


        final OkHttpClient.Builder builder = serviceLocator
                .getOkHttpClient()
                .newBuilder()
                .connectTimeout(networkConfig.getConnectTimeoutInMs(), TimeUnit.MILLISECONDS)
                .readTimeout(networkConfig.getReadTimeoutInMs(), TimeUnit.MILLISECONDS);

        final Throttler throttler = networkConfig.getThrottler();
        if (throttler != null) {
            builder.addInterceptor(new ThrottlingInterceptor(throttler))
                   .addInterceptor(new RateLimitInterceptor(throttler,
                                                            networkConfig.isHttpLoggingEnabled()));
        }

        if (sslContext != null && x509TrustManager != null) {
            builder.sslSocketFactory(sslContext.getSocketFactory(), x509TrustManager);
            if (hostnameVerifier != null) {
                builder.hostnameVerifier(hostnameVerifier);
            }
        }

        if (!username.isEmpty() && !password.isEmpty()) {
            // https://github.com/rburgst/okhttp-digest
            final Credentials credentials = new Credentials(username, password);

            final Authenticator basicAuthenticator =
                    new BasicAuthenticator(credentials, StandardCharsets.UTF_8);
            final Authenticator digestAuthenticator =
                    new DigestAuthenticator(credentials, StandardCharsets.UTF_8);

            final Authenticator dispatchingAuthenticator =
                    new DispatchingAuthenticator.Builder()
                            .with("digest", digestAuthenticator)
                            .with("basic", basicAuthenticator)
                            .build();

            final Map<String, CachingAuthenticator> authCache = new ConcurrentHashMap<>();

            final Authenticator authenticator =
                    new CachingAuthenticatorDecorator(dispatchingAuthenticator, authCache);
            builder.authenticator(authenticator);

            // Proxy support
            // final Interceptor authCacheProxyInterceptor =
            //      new AuthenticationCacheInterceptor(authCache,
            //                                         new DefaultProxyCacheKeyProvider());
            // builder.addNetworkInterceptor(authCacheProxyInterceptor);

            final Interceptor authCacheInterceptor =
                    new AuthenticationCacheInterceptor(authCache,
                                                       new DefaultRequestCacheKeyProvider());
            builder.addInterceptor(authCacheInterceptor);
        }

        if (networkConfig.isHttpLoggingEnabled()) {
            builder.addNetworkInterceptor(OkHttpLoggerFactory.getLogger(networkConfig.getLogTag()));
        }

        httpClient = builder.build();
    }

    /**
     * Get the default/stored host url for the Calibre Content Server instance.
     *
     * @return url; will be empty if not configured.
     */
    @NonNull
    public static String getHostUrl() {
        return ServiceLocator.getInstance()
                             .getSharedPreferences()
                             .getString(PK_HOST_URL, "");
    }

    /**
     * Set (in preferences) the local folder where (from Calibre Content Server) downloaded
     * books will be stored.
     *
     * @param context Current context
     * @param uri     for the local folder
     */
    static void setFolderUri(@NonNull final Context context,
                             @NonNull final Uri uri) {
        final ContentResolver contentResolver = context.getContentResolver();

        final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();

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
    static Optional<Uri> getFolderUri(@NonNull final Context context) {

        final String folder = ServiceLocator.getInstance().getSharedPreferences()
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
    private static Pair<SSLContext, X509TrustManager> getSslContext(@NonNull final Context context)
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

            for (final TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    return new Pair<>(sslContext, (X509TrustManager) tm);
                }
            }

            return null;

        } catch (@NonNull final KeyManagementException e) {
            // wrap for ease of handling; it is in fact almost certain that
            // we would throw a CertificateException BEFORE we can even
            // get a KeyManagementException
            throw new CertificateException(e);

        } catch (@NonNull final IOException | KeyStoreException | NoSuchAlgorithmException ignore) {
            // All these exceptions, can be ignored, and we are assuming
            // that the server does not need a cert, or that the cert is
            // loaded in the Android system keystore.
            return null;
        }
    }

    @WorkerThread
    @Override
    public boolean validateConnection(@NonNull final Context context)
            throws IOException {
        // Use the default GET_LIBRARY_INFO for validation. It's short and fast.
        final String url = String.format(Endpoints.GET_LIBRARY_INFO, serverUri);
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
     * Return info about available libraries and their metadata from the server.
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
     * and the {@link #pluginInstalled} flag.
     *
     * @throws IOException   on generic/other IO failures
     * @throws JSONException upon any parsing error
     */
    @WorkerThread
    public void readMetaData()
            throws IOException, JSONException {

        libraries.clear();
        defaultLibrary = null;

        // use the current bookshelf (or default if not set)
        final long currentBookshelfId = bookshelfDao.getCurrent()
                                                    .orElseGet(bookshelfDao::getDefault)
                                                    .getId();

        final JSONObject response = fetchLibraryInfo();

        final JSONObject libraryMap = response.getJSONObject(RESPONSE_AJAX_LIBRARY_MAP);
        final String defaultLibraryId = response.getString(RESPONSE_AJAX_DEFAULT_LIBRARY);

        // This data is only present if our plugin is installed
        @Nullable
        final JSONObject libraryData = response.optJSONObject(RESPONSE_NTMB_LIBRARY_DATA);

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

                // read the plugin added info if present
                @NonNull
                String uuid = "";
                int totalBooks = 0;
                @Nullable
                JSONObject vlibs = null;
                @Nullable
                JSONObject customFields = null;
                if (isPluginInstalled()) {
                    // Paranoia... with the 8.0 plugin, we should ALWAYS have these details.
                    if (libraryData != null
                        && !libraryData.isNull(libraryId)) {

                        final JSONObject details = libraryData.getJSONObject(libraryId);

                        uuid = details.getString(RESPONSE_NTMB_UUID);
                        totalBooks = details.optInt(RESPONSE_TAG_TOTAL_NUM);

                        if (!details.isNull(RESPONSE_NTMB_VIRTUAL_LIBRARIES)) {
                            vlibs = details.getJSONObject(RESPONSE_NTMB_VIRTUAL_LIBRARIES);
                        }
                        if (!details.isNull(RESPONSE_NTMB_CUSTOM_FIELDS)) {
                            customFields = details.getJSONObject(RESPONSE_NTMB_CUSTOM_FIELDS);
                        }
                    }
                }

                // Using the UUID, or fallback on the id, and check if we already
                // have the library in our local database
                @Nullable
                CalibreLibrary library = null;
                if (!uuid.isEmpty()) {
                    library = libraryDao.findLibraryByUuid(uuid).orElse(null);
                }
                if (library == null) {
                    library = libraryDao.findLibraryByStringId(libraryId).orElse(null);
                }
                if (library != null) {
                    // we found it above, either by uuid or id, update it with the server info
                    // (even if unchanged... )
                    library.setUuid(uuid);
                    library.setName(name);

                } else {
                    // Not found, must be a new one.
                    library = new CalibreLibrary(uuid, libraryId, name, currentBookshelfId);
                }

                // If we have vl info from our plugin, process it
                // If we don't, the library will keep any vl defined previously
                if (vlibs != null) {
                    processVirtualLibraries(library, vlibs);
                }

                // The Library data is now complete, store it to the local database
                if (library.getId() > 0) {
                    libraryDao.update(library);
                } else {
                    libraryDao.insert(library);
                }

                // Lastly read and handle the in-memory library data.
                if (isPluginInstalled()) {
                    library.setTotalBooks(totalBooks);
                    parseCustomFieldDefinitions(library, customFields);

                } else {
                    // Use standard endpoints as workaround - it's slower :(
                    // Read the first book available to get the customs fields (if any)
                    final JSONObject bookIds = getBookIds(library.getLibraryStringId(), 1, 0);
                    // grab the initial/current total number of books
                    library.setTotalBooks(bookIds.optInt(RESPONSE_TAG_TOTAL_NUM));

                    // The Calibre numeric book ids returned by the server
                    final JSONArray calibreIds = bookIds.optJSONArray(RESPONSE_TAG_BOOK_IDS);
                    // There will either be none in which case we CANNOT get the custom fields,
                    // or a single id and after getting the full book,
                    // we can extract the  custom fields
                    if (calibreIds != null && !calibreIds.isEmpty()) {
                        final JSONObject calibreBook = getBookById(library.getLibraryStringId(),
                                                                   calibreIds.getInt(0));
                        final JSONObject userMetaData =
                                calibreBook.optJSONObject(CalibreBookJsonKey.USER_METADATA);
                        parseCustomFieldDefinitions(library, userMetaData);
                    }
                }

                // Processing is complete, add to the cached list
                libraries.add(library);
                // and set as the default library if applicable
                if (libraryId.equals(defaultLibraryId)) {
                    defaultLibrary = library;
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

    /**
     * Check if the virtual-library support plugin has been installed
     * on the Calibre Content Server.
     * <p>
     * Only valid after an attempt to read the meta-data.
     * Otherwise returns {@code false} by default.
     *
     * @return flag
     *
     * @see #readMetaData()
     * @see #isMetaDataRead()
     */
    boolean isPluginInstalled() {
        return pluginInstalled != null && pluginInstalled;
    }

    /**
     * Get the configured books-per-request for <strong>pulling</strong> book data.
     *
     * @return nr of books
     */
    @IntRange(from = 1)
    int getBooksPerPullRequest() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getInt(PK_BOOKS_PER_PULL_REQUEST, BOOKS_PER_PULL_REQUEST_DEFAULT);
    }

    /**
     * Get the configured books-per-request for <strong>pushing</strong> book data.
     *
     * @return nr of books
     */
    @IntRange(from = 1)
    int getBooksPerPushRequest() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getInt(PK_BOOKS_PER_PUSH_REQUEST, BOOKS_PER_PUSH_REQUEST_DEFAULT);
    }

    /**
     * Get the list of libraries; usually just the one.
     *
     * @return list
     */
    @NonNull
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
     * Get all book ids present in the given library.
     * The ids are fetched in 'pages' of {number of books} starting at {offset}.
     * <p>
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
     * @throws IOException   on generic/other IO failures
     * @throws JSONException upon any parsing error
     */
    @WorkerThread
    @NonNull
    public JSONObject getBookIds(@NonNull final String libraryStringId,
                                 @SuppressWarnings("SameParameterValue") final int num,
                                 final int offset)
            throws IOException, JSONException {

        final String url = String.format(Locale.ROOT, Endpoints.GET_BOOK_IDS, serverUri,
                                         libraryStringId, num, offset);
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
     * Example query:  query=last_modified:">2021-1-10"
     * {@code
     * http://192.168.0.202:8080/ajax/search?num=10&query=last_modified:%22%3E2021-1-10%22
     * }
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
     * @param libraryStringId the Calibre native {@code stringId} for the library to read from
     * @param num             the maximum number of entries to return
     * @param offset          the offset for the next set to return
     * @param query           the search query, see above
     *
     * @return books matching the specified search query.
     *
     * @throws IOException   on generic/other IO failures
     * @throws JSONException upon any parsing error
     */
    @WorkerThread
    @NonNull
    public JSONObject search(@NonNull final String libraryStringId,
                             @SuppressWarnings("SameParameterValue") final int num,
                             final int offset,
                             @NonNull final String query)
            throws IOException,
                   JSONException {

        final String url = String.format(Locale.ROOT, Endpoints.SEARCH, serverUri,
                                         libraryStringId, num, offset, query);
        return new JSONObject(fetch(url, BUFFER_BOOK_LIST));
    }

    /**
     * Return the metadata of a single book as a JSON dictionary.
     * Search for it by numeric id.
     * <p>
     * By preference, use {@link #getBookByUuid(String, String)} if possible.
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
     * @param calibreBookId   numeric book id
     *
     * @return Calibre book object
     *
     * @throws IOException   on generic/other IO failures
     * @throws JSONException upon any parsing error
     */
    @WorkerThread
    @NonNull
    private JSONObject getBookById(@NonNull final String libraryStringId,
                                   final int calibreBookId)
            throws IOException, JSONException {

        final String url = String.format(Endpoints.GET_BOOK_BY_ID, serverUri,
                                         calibreBookId, libraryStringId);
        return new JSONObject(fetch(url, BUFFER_BOOK));
    }

    /**
     * Return the metadata of a single book as a JSON dictionary.
     * Search for it by UUID.
     *
     * @param libraryStringId the Calibre native {@code stringId} for the library to read from
     * @param calibreBookUuid of the book to get
     *
     * @return Calibre book object
     *
     * @throws IOException   on generic/other IO failures
     * @throws JSONException upon any parsing error
     * @see #getBookById(String, int)
     */
    @WorkerThread
    @NonNull
    JSONObject getBookByUuid(@NonNull final String libraryStringId,
                             @NonNull final String calibreBookUuid)
            throws IOException, JSONException {

        final String url = String.format(Endpoints.GET_BOOK_BY_UUID, serverUri,
                                         calibreBookUuid, libraryStringId);
        return new JSONObject(fetch(url, BUFFER_BOOK));
    }

    /**
     * Return the metadata of the books in the given library as a JSON dictionary.
     * Search for them by numeric ids.
     * If our plugin is installed, the virtual-libraries for each will be fetched and added.
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
     * Example response see {@link BookCoder}.
     *
     * @param libraryStringId the Calibre native {@code stringId} for the library to read from
     * @param calibreBookIds  a list of Calibre numeric book ids
     *
     * @return JSONObject with a list of Calibre book objects; NOT an array.
     *
     * @throws IOException   on generic/other IO failures
     * @throws JSONException upon any parsing error
     */
    @WorkerThread
    @NonNull
    JSONObject getBooksById(@NonNull final String libraryStringId,
                            @NonNull final List<Integer> calibreBookIds)
            throws IOException, JSONException {

        final String csv = calibreBookIds.stream()
                                         .map(String::valueOf)
                                         .collect(Collectors.joining(","));

        final String url = String.format(Endpoints.GET_BOOKS_BY_ID, serverUri,
                                         libraryStringId, csv);
        final JSONObject bookList = new JSONObject(fetch(url, BUFFER_BOOK_LIST));

        // if possible, fetch the virtual library data for those same book-ids
        if (isPluginInstalled()) {
            fetchVirtualLibraries(libraryStringId, csv, bookList);
        }

        return bookList;
    }

    /**
     * Return the metadata of the books in the given library as a JSON dictionary.
     * Search for them by numeric ids.
     * If our plugin is installed, the virtual-libraries for each will be fetched and added.
     * <p>
     * See {@link #getBooksById(String, List)} for endpoint docs.
     *
     * @param libraryStringId  the Calibre native {@code stringId} for the library to read from
     * @param calibreBookUuids a list of Calibre book UUIDs
     *
     * @return JSONObject with a list of Calibre book objects; NOT an array.
     *
     * @throws IOException   on generic/other IO failures
     * @throws JSONException upon any parsing error
     */
    @WorkerThread
    @NonNull
    JSONObject getBooksByUuid(@NonNull final String libraryStringId,
                              @NonNull final List<String> calibreBookUuids)
            throws IOException, JSONException {

        final String csv = String.join(",", calibreBookUuids);

        final String url = String.format(Endpoints.GET_BOOKS_BY_UUID, serverUri,
                                         libraryStringId, csv);
        final JSONObject bookList = new JSONObject(fetch(url, BUFFER_BOOK_LIST));

        // if possible, fetch the matching virtual library data
        if (isPluginInstalled()) {
            // Calibre can only use numeric ids for fetching virtual libs.
            // First extract the numeric ids into a csv list.
            final List<String> ids = new ArrayList<>();
            final Iterator<String> it = bookList.keys();
            while (it.hasNext()) {
                ids.add(it.next());
            }
            final String idCsvList = String.join(",", ids);

            fetchVirtualLibraries(libraryStringId, idCsvList, bookList);
        }
        return bookList;
    }

    /**
     * Fetch the virtual library information for the given library and books.
     * Updates the books in the list with their virtual libraries.
     *
     * @param libraryStringId the Calibre native {@code stringId} for the library to read from
     * @param csv             a CSV list of Calibre numeric book ids
     * @param bookList        to update
     *
     * @throws IOException   on generic/other IO failures
     * @throws JSONException upon any parsing error
     */
    private void fetchVirtualLibraries(@NonNull final String libraryStringId,
                                       @NonNull final String csv,
                                       @NonNull final JSONObject bookList)
            throws IOException, JSONException {

        final JSONObject virtualLibs = getVirtualLibrariesForBookIds(libraryStringId, csv);
        // inject the virtual library list into the book objects
        final Iterator<String> it = bookList.keys();
        while (it.hasNext()) {
            final String key = it.next();
            final JSONArray libs = virtualLibs.optJSONArray(key);
            if (libs != null) {
                final JSONObject calibreBook = bookList.getJSONObject(key);
                calibreBook.put(CalibreBookJsonKey.NTMB_VIRTUAL_LIBRARY_LIST, libs);
            }
        }
    }

    /**
     * Return the book ids with their virtual libraries.
     * <pre>
     * {@code
     *      endpoint('/nevertoomanybooks/virtual-libraries-for-books/{library_id=None}',
     *               postprocess=json)
     * }
     * </pre>
     * Query parameters: 'ids' with a simple csv list; example: 271,7,200
     * <p>
     * This method uses a plugin which needs to be installed on the Calibre Content Server.
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
     * @param csv             a CSV list of Calibre numeric book ids
     *
     * @return see above
     *
     * @throws HttpNotFoundException if the plugin is not installed
     * @throws IOException           on generic/other IO failures
     * @throws JSONException         upon any parsing error
     * @throws IllegalStateException (debug) if the plugin is not installed
     * @see #isPluginInstalled()
     */
    @NonNull
    private JSONObject getVirtualLibrariesForBookIds(@NonNull final String libraryStringId,
                                                     @NonNull final String csv)
            throws IOException, JSONException {

        if (BuildConfig.DEBUG /* always */) {
            if (pluginInstalled == null) {
                throw new IllegalStateException("pluginInstalled == null");
            }
            if (!pluginInstalled) {
                throw new IllegalStateException("Plugin not installed");
            }
        }

        final String url = String.format(Endpoints.NTMB_VIRTUAL_LIBRARIES_FOR_BOOKS, serverUri,
                                         libraryStringId, csv);
        return new JSONObject(fetch(url, BUFFER_SMALL));
    }

    /**
     * Similar to {@link #getBooksById(String, List)} but
     * instead of full book objects the result contains only the fields
     * {@link CalibreBookJsonKey#LAST_MODIFIED},
     * {@link CalibreBookJsonKey#IDENTIFIERS}.
     * <pre>
     * {@code
     *      endpoint('/nevertoomanybooks/prep-for-pushing/{library_id=None}',
     *               postprocess=json)
     * }
     * </pre>
     * Query parameters: 'ids' with a simple csv list; example: 271,7,200
     * <p>
     * This method uses a plugin which needs to be installed on the Calibre Content Server.
     *
     * @param libraryStringId the Calibre native {@code stringId} for the library to read from
     * @param calibreBookIds  a list of Calibre numeric book ids
     *
     * @return JSONObject with a list of Calibre book ids with their last_modified/identifiers
     *
     * @throws IOException           on generic/other IO failures
     * @throws JSONException         upon any parsing error
     * @throws IllegalStateException (debug) if the plugin is not installed
     */
    @NonNull
    JSONObject prepForPushing(@NonNull final String libraryStringId,
                              @NonNull final List<Integer> calibreBookIds)
            throws IOException, JSONException {

        if (BuildConfig.DEBUG /* always */) {
            if (!isPluginInstalled()) {
                throw new IllegalStateException("Plugin not installed");
            }
        }

        final String csv = calibreBookIds.stream()
                                         .map(String::valueOf)
                                         .collect(Collectors.joining(","));

        // minimal set of "last_modified" and "identifiers"
        final String url = String.format(Endpoints.NTMB_PREP_FOR_PUSHING, serverUri,
                                         libraryStringId, csv);
        return new JSONObject(fetch(url, BUFFER_BOOK_LIST));
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
     * @param calibreBookId   numeric book id
     * @param changes         (delta) fields to update
     *
     * @throws IOException   on generic/other IO failures
     * @throws JSONException upon any parsing error
     */
    @WorkerThread
    void pushChanges(@NonNull final String libraryStringId,
                     final int calibreBookId,
                     @NonNull final JSONObject changes)
            throws IOException, JSONException {

        final JSONArray loadedBookIds = new JSONArray()
                .put(calibreBookId);

        final String url = String.format(Locale.ROOT, Endpoints.PUSH_CHANGES, serverUri,
                                         calibreBookId, libraryStringId);

        final String jsonBody = new JSONObject()
                .put("changes", changes)
                .put("loaded_book_ids", loadedBookIds)
                .toString();
        if (jsonBody == null || jsonBody.isEmpty()) {
            throw new JSONException("jsonBody invalid?");
        }

        final RequestBody body = RequestBody.create(jsonBody, MEDIA_TYPE_JSON);

        postCall = new HttpCall(httpClient,
                                ACCEPT_LANGUAGE_HEADER,
                                R.string.site_calibre,
                                networkConfig.isHttpLoggingEnabled(),
                                cookieStore);

        postCall.post(createPostRequest(url, body), null);
    }

    /**
     * Download a cover using the direct cover-url.
     * <p>
     * Uploading a cover is done using {@link #pushChanges(String, int, JSONObject)} .
     *
     * @param calibreBookId used for the temp filename only
     * @param coverUrl      to download; will be prefixed with the server url
     *
     * @return cover file
     *
     * @throws IOException           on generic IO failures
     * @throws ImageStorageException on image storage failures
     */
    @WorkerThread
    @NonNull
    public Optional<File> getCover(final int calibreBookId,
                                   @NonNull final String coverUrl)
            throws IOException, ImageStorageException {

        final String tempFilename = ImageFileInfo.getTempFilename(
                PREFERENCE_KEY, String.valueOf(calibreBookId), 0, null);

        final Request imageRequest = createImageRequest(serverUri + coverUrl);
        return getImageDownloader().fetch(imageRequest, tempFilename);
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
     * @throws IOException on generic/other IO failures
     */
    @WorkerThread
    @NonNull
    Uri fetchFile(@NonNull final Context context,
                  @NonNull final Book book,
                  @NonNull final Uri folder,
                  @NonNull final ProgressListener progressListener)
            throws IOException {

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

        final String url = String.format(Locale.ROOT, Endpoints.FETCH_FILE, serverUri, format, id,
                                         calibreLibrary.getLibraryStringId());

        final Uri destUri = destFile.getUri();

        fileFetchCall = new HttpCall(httpClient,
                                     ACCEPT_LANGUAGE_HEADER,
                                     R.string.site_calibre,
                                     networkConfig.isHttpLoggingEnabled(),
                                     cookieStore);

        fileFetchCall.setBufferSize(BUFFER_FILE);
        final Uri uri = fileFetchCall.get(createGetRequest(url), (response, is) -> {
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
        // Should never be null...flw
        return Objects.requireNonNull(uri, "uri==null");
    }

    /**
     * Get the DocumentFile for the given book from the local download folder.
     *
     * @param context         Current context
     * @param book            to get
     * @param folder          where the files are
     * @param createIfMissing set {@code true} when creating,
     *                        set {@code false} for checking existence
     *
     * @return the eBook file
     *
     * @throws FileNotFoundException on any failure
     */
    @NonNull
    DocumentFile getDocumentFile(@NonNull final Context context,
                                 @NonNull final Book book,
                                 @NonNull final Uri folder,
                                 final boolean createIfMissing)
            throws FileNotFoundException {

        // Sanity check that the download folder exists
        final DocumentFile root = DocumentFile.fromTreeUri(context, folder);
        if (root == null) {
            throw new FileNotFoundException(folder.toString());
        }

        final DocumentFile authorFolder = getAuthorFolder(context, root, book, createIfMissing);

        final String fileName = createFilename(context, book);
        final String fileExt = book.getString(DBKey.CALIBRE.BOOK_MAIN_FORMAT);

        DocumentFile bookFile = authorFolder.findFile(fileName + '.' + fileExt);

        if (bookFile == null && createIfMissing) {
            // when creating, we must NOT directly use the extension,
            // but deduce the mime type from the extension.
            final String mimeType = FileUtils.getMimeTypeFromExtension(fileExt);
            bookFile = authorFolder.createFile(mimeType, fileName);
        }

        if (bookFile == null) {
            throw new FileNotFoundException(fileName);
        }
        return bookFile;
    }

    @NonNull
    private DocumentFile getAuthorFolder(@NonNull final Context context,
                                         @NonNull final DocumentFile root,
                                         @NonNull final Book book,
                                         final boolean createIfMissing)
            throws FileNotFoundException {

        final String authorDirectory = createAuthorDirectoryName(context, book);

        DocumentFile authorFolder = root.findFile(authorDirectory);
        if (authorFolder == null && createIfMissing) {
            authorFolder = root.createDirectory(authorDirectory);
        }

        if (authorFolder == null) {
            throw new FileNotFoundException(authorDirectory);
        }
        return authorFolder;
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
     * Fetch the library information.
     * First tries the endpoint for our plugin,
     * and if not found falls back to the default endpoint.
     * <p>
     * Sets {@link #pluginInstalled} accordingly.
     *
     * <pre>
     *   "library_map": {
     *     "Main_Library": "Main Library",
     *     "SciFi": "Sci-Fi Books"
     *   },
     *   "default_library": "Main_Library",
     *
     *   Plugin info:
     *
     *   "library_details": {
     *     "Main_Library": {
     *       "uuid": "a1b2c3d4-...",
     *       "name": "Main Library",
     *       "total_num": 1420,
     *       "custom_fields": {
     *         "#read": {
     *           "datatype": "bool",
     *           "name": "Read",
     *           "colnum": 1,
     *           "is_custom": true
     *         }
     *       },
     *       "virtual_libraries": {
     *         "Favorites": "rating:>=4"
     *       }
     *     },
     *     "SciFi": {
     *       "uuid": "e5f67890-...",
     *       "name": "Sci-Fi Books",
     *       "total_num": 350,
     *       "custom_fields": {},
     *       "virtual_libraries": {}
     *     }
     *   }
     * }
     * </pre>
     *
     * @return the library information as a json object
     *
     * @throws IOException   on generic/other IO failures
     * @throws JSONException upon any parsing error
     */
    @WorkerThread
    @NonNull
    private JSONObject fetchLibraryInfo()
            throws IOException, JSONException {

        String response;

        if (pluginInstalled == null) {
            try {
                // Try our plugin endpoint first
                response = fetch(String.format(Endpoints.NTMB_GET_LIBRARY_INFO, serverUri),
                                 BUFFER_SMALL);
                // if we don't get a 404, our plugin is installed
                pluginInstalled = true;

            } catch (@NonNull final HttpNotFoundException e) {
                // our plugin is not installed
                pluginInstalled = false;
                // use the standard Calibre endpoint
                response = fetch(String.format(Endpoints.GET_LIBRARY_INFO, serverUri),
                                 BUFFER_SMALL);
            }
        } else if (pluginInstalled) {
            response = fetch(String.format(Endpoints.NTMB_GET_LIBRARY_INFO, serverUri),
                             BUFFER_SMALL);
        } else {
            response = fetch(String.format(Endpoints.GET_LIBRARY_INFO, serverUri),
                             BUFFER_SMALL);
        }

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(TAG, "pluginInstalled=" + pluginInstalled);
        }

        return new JSONObject(response);
    }

    /**
     * Helper for {@link #fetchLibraryInfo()}.
     * <p>
     * Parse the virtual libray information and attach them to the library object.
     *
     * @param library          to update
     * @param virtualLibraries to decode
     *
     * @throws JSONException upon any parsing error
     */
    private void processVirtualLibraries(@NonNull final CalibreLibrary library,
                                         @NonNull final JSONObject virtualLibraries)
            throws JSONException {

        final List<CalibreVirtualLibrary> vLibs = new ArrayList<>();

        final Iterator<String> it = virtualLibraries.keys();
        while (it.hasNext()) {
            final String name = it.next();
            final String expr = virtualLibraries.getString(name);

            libraryDao.findVirtualLibrary(library.getId(), name).ifPresentOrElse(vLib -> {
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

    /**
     * Helper for {@link #readMetaData()}.
     * <p>
     * Fetch the given book (which can be a random book) to read the/any
     * custom field definitions and attach them to the library.
     *
     * @param library      to update
     * @param userMetaData containing the custom-columns definitions
     *
     * @throws JSONException upon any parsing error
     */
    private void parseCustomFieldDefinitions(@NonNull final CalibreLibrary library,
                                             @Nullable final JSONObject userMetaData) {
        if (userMetaData == null) {
            return;
        }

        final Set<CalibreCustomField> fields = new HashSet<>();
        // check the supported fields
        for (final CalibreCustomField cf : this.calibreCustomFields) {
            final JSONObject data = userMetaData.optJSONObject(cf.getCalibreKey());
            // do we have a match? (this check is needed, it's NOT a sanity check)
            if (data != null) {
                final String type = data.getString(CalibreCustomField.METADATA_DATATYPE);
                if (cf.getType().equals(type)) {
                    fields.add(cf);
                }
            }
        }
        // finally, hook them up to the library itself.
        library.setCustomFields(fields);
    }

    @NonNull
    private Request createImageRequest(@NonNull final String urlStr) {

        // TODO: check adding http headers with Calibre built-in-http-server
        //  versus Calibre hosted behind an Apache server

        // Host, Connection, Accept-Encoding are added by OkHttp
        return new Request.Builder()
                .url(urlStr)
                .header(HttpConstants.USER_AGENT,
                        HttpConstants.USER_AGENT_FIREFOX)
                .header(HttpConstants.ACCEPT,
                        HttpConstants.ACCEPT_IMAGE).build();
    }

    @NonNull
    private Request createGetRequest(@NonNull final String url) {

        // TODO: check adding http headers with Calibre built-in-http-server
        //  versus Calibre hosted behind an Apache server

        // Host, Connection, Accept-Encoding are added by OkHttp
        return new Request.Builder().url(url).build();
    }

    /**
     * Fetch the given url content as a single string.
     *
     * @param url    to read
     * @param buffer size for the read
     *
     * @return content
     *
     * @throws IOException on generic/other IO failures
     */
    @WorkerThread
    @NonNull
    private String fetch(@NonNull final String url,
                         final int buffer)
            throws IOException {

        jsonFetchCall = new HttpCall(httpClient,
                                     ACCEPT_LANGUAGE_HEADER,
                                     R.string.site_calibre,
                                     networkConfig.isHttpLoggingEnabled(),
                                     cookieStore);
        jsonFetchCall.setBufferSize(buffer);
        return jsonFetchCall.getAsString(createGetRequest(url));
    }

    @NonNull
    private ImageDownloader getImageDownloader() {
        ImageDownloader instance = imageDownloader;
        if (instance == null) {
            synchronized (this) {
                instance = imageDownloader;
                if (instance == null) {
                    // Calibre sends a cookie with each image.
                    // During an 'pull' of 100's of books,
                    // the 100's of requests for images will create
                    // a mess (for lack of a better word) in the cookie handling,
                    // resulting in a steady increase of time spend in the
                    // okhttp call.execute(). From initially 10 millis...
                    // .. it increases to 100's, then 1000's of millis.
                    // Solution: DROP cookies for these requests.
                    final OkHttpClient imageHttpClient =
                            httpClient.newBuilder()
                                      .cookieJar(CookieJar.NO_COOKIES)
                                      .build();
                    instance = new ImageDownloader(imageHttpClient,
                                                   networkConfig.getThrottler(),
                                                   R.string.site_calibre,
                                                   false);
                    imageDownloader = instance;
                }
            }
        }
        return instance;
    }

    @NonNull
    private Request createPostRequest(@NonNull final String url,
                                      @NonNull final RequestBody body) {
        // Host, Connection, Accept-Encoding are added by OkHttp
        final Request.Builder builder = new Request.Builder()
                .url(url)
                .post(body)
                .header(HttpConstants.CONTENT_TYPE,
                        HttpConstants.CONTENT_TYPE_JSON);

        return builder.build();
    }

    @AnyThread
    public void cancel() {
        synchronized (this) {
            if (jsonFetchCall != null) {
                jsonFetchCall.cancel();
            }
            if (fileFetchCall != null) {
                fileFetchCall.cancel();
            }
            final ImageDownloader downloader = imageDownloader;
            if (downloader != null) {
                downloader.cancel();
            }
            if (postCall != null) {
                postCall.cancel();
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
        private X509TrustManager x509TrustManager;

        @Nullable
        private HostnameVerifier hostnameVerifier;

        /**
         * Constructor.
         *
         * @param context Current context
         */
        public Builder(@NonNull final Context context) {
            this.context = context;
        }

        /**
         * Set the url for the connection.
         *
         * @param url to use
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setUrl(@NonNull final String url) {
            this.url = url;
            return this;
        }

        /**
         * Set the username for the connection.
         *
         * @param username to use
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setUser(@NonNull final String username) {
            this.username = username;
            return this;
        }

        /**
         * Set the password for the connection.
         *
         * @param password to use
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setPassword(@NonNull final String password) {
            this.password = password;
            return this;
        }

        /**
         * Set a custom SSL/X509 configuration.
         *
         * @param sslContext   to use
         * @param trustManager to use
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setSSLContext(@NonNull final SSLContext sslContext,
                                     @NonNull final X509TrustManager trustManager) {
            this.sslContext = sslContext;
            this.x509TrustManager = trustManager;
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

        /**
         * Create the server.
         *
         * @return new instance
         *
         * @throws CertificateException on failures related to a user installed CA
         */
        @NonNull
        public CalibreContentServer build()
                throws CertificateException {
            final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();

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
                    final Pair<SSLContext, X509TrustManager> pair = getSslContext(context);
                    if (pair != null) {
                        sslContext = pair.first;
                        x509TrustManager = pair.second;
                    } else {
                        sslContext = null;
                        x509TrustManager = null;
                    }
                } else {
                    sslContext = null;
                    x509TrustManager = null;
                }
            }

            return new CalibreContentServer(uri, username, password,
                                            sslContext, x509TrustManager, hostnameVerifier);
        }
    }
}
