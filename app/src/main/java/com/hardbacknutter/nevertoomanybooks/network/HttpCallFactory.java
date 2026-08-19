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

package com.hardbacknutter.nevertoomanybooks.network;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLProtocolException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpCall;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.core.network.RateLimitInterceptor;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.core.network.ThrottlingInterceptor;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.covers.ImageWebSize;
import com.hardbacknutter.nevertoomanybooks.searchengines.ImageRequestFactoryDefault;
import com.hardbacknutter.nevertoomanybooks.searchengines.RequestFactory;
import com.hardbacknutter.nevertoomanybooks.utils.OkHttpLoggerFactory;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class HttpCallFactory {

    @NonNull
    private final NetworkConfig config;
    @Nullable
    private final SSLContext sslContext;
    @NonNull
    private final CookieStore cookieStore;
    @NonNull
    private final String acceptLanguageHeader;

    @Nullable
    private final String charSetName;
    @NonNull
    private final String imageFilenamePrefix;

    /** Lazy created in {@link #getHttpClient()}. */
    @Nullable
    private volatile OkHttpClient httpClient;

    /** Lazy created in {@link #getJsoupLoader()}. */
    @Nullable
    private volatile JsoupLoader jsoupLoader;

    /** Lazy created in {@link #getImageDownloader()}. */
    @Nullable
    private volatile ImageDownloader imageDownloader;

    /**
     * Either set from a child constructor with
     * {@link #setImageRequestFactory(RequestFactory)}
     * or lazy created in {@link #getImageRequestFactory()}.
     */
    @Nullable
    private volatile RequestFactory imageRequestFactory;

    public HttpCallFactory(@NonNull final NetworkConfig config,
                           @Nullable final SSLContext sslContext,
                           @NonNull final CookieStore cookieStore,
                           @NonNull final String acceptLanguageHeader,
                           @Nullable final String charSetName,
                           @NonNull final String imageFilenamePrefix) {
        this.config = config;
        this.sslContext = sslContext;
        this.cookieStore = cookieStore;
        this.acceptLanguageHeader = acceptLanguageHeader;
        this.charSetName = charSetName;

        this.imageFilenamePrefix = imageFilenamePrefix;
    }

    /**
     * Override the default {@link ImageRequestFactoryDefault}.
     *
     * @param imageRequestFactory to use
     */
    public void setImageRequestFactory(@NonNull final RequestFactory imageRequestFactory) {
        this.imageRequestFactory = imageRequestFactory;
    }

    public boolean isLogEnabled() {
        return config.isHttpLoggingEnabled();
    }

    /**
     * Create an {@link HttpCall}.
     *
     * @return new instance
     */
    @NonNull
    public HttpCall createCall() {
        return createCall(getHttpClient());
    }

    /**
     * Create an {@link HttpCall}.
     *
     * @param httpClient the client
     *
     * @return new instance
     */
    @NonNull
    public HttpCall createCall(@NonNull final OkHttpClient httpClient) {
        return new HttpCall(httpClient,
                            acceptLanguageHeader,
                            config.getLogStringRes(),
                            config.isHttpLoggingEnabled(), cookieStore
        );
    }

    /**
     * Convenience method to save an image using the engines specific network configuration.
     *
     * @param url     Image file URL
     * @param headers (optional) extra headers to add/override
     * @param bookId  more or less unique id; e.g. isbn or website native id, etc...
     * @param cIdx    0..n image index
     * @param size    (optional) size parameter for engines/sites which support one
     *
     * @return File fileSpec, or {@code Optional.empty()} on failure
     *
     * @throws CoverStorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    public Optional<String> saveImage(@NonNull final String url,
                                      @Nullable final Map<String, String> headers,
                                      @Nullable final String bookId,
                                      @IntRange(from = 0, to = 3) final int cIdx,
                                      @Nullable final ImageWebSize size)
            throws CoverStorageException {

        final String tempFilename = ImageFileInfo
                .getTempFilename(imageFilenamePrefix, bookId, cIdx, size);

        try {
            final Request imageRequest = getImageRequestFactory().createRequest(url, headers);

            return getImageDownloader().fetch(imageRequest, tempFilename)
                                       .map(File::getAbsolutePath);

        } catch (@NonNull final IOException e) {
            // we swallow IOExceptions, even when the disk is full.
            // We're counting on that condition to be caught elsewhere...
            // as handling it in each call here would become [bleep] fast.
            return Optional.empty();
        }
    }


    /**
     * Load the url into a parsed {@link org.jsoup.nodes.Document} using an HTML parser.
     *
     * @param context Current context
     * @param url     to load
     * @param headers (optional) extra headers to add/override
     *
     * @return the document
     *
     * @throws IOException on generic IO failures
     */
    @WorkerThread
    @NonNull
    public Document loadHtml(@NonNull final Context context,
                             @NonNull final String url,
                             @Nullable final Map<String, String> headers)
            throws IOException {
        return getJsoupLoader().loadDocument(context, Parser.htmlParser(), url, headers);

    }

    /**
     * Load the url into a parsed {@link org.jsoup.nodes.Document} using an XML parser.
     *
     * @param context Current context
     * @param url     to load
     * @param headers (optional) extra headers to add/override
     *
     * @return the document
     *
     * @throws IOException on generic IO failures
     */
    @WorkerThread
    @NonNull
    public Document loadXml(@NonNull final Context context,
                            @NonNull final String url,
                            @Nullable final Map<String, String> headers)
            throws IOException {

        return getJsoupLoader().loadDocument(context, Parser.xmlParser(), url, headers);
    }

    @AnyThread
    @CallSuper
    public void cancel() {
        synchronized (this) {
            final ImageDownloader tmpImageDownloader = imageDownloader;
            if (tmpImageDownloader != null) {
                tmpImageDownloader.cancel();
            }
            final JsoupLoader tmpJsoupLoader = jsoupLoader;
            if (tmpJsoupLoader != null) {
                tmpJsoupLoader.cancel();
            }
        }
    }

    /**
     * Get/create the {@link OkHttpClient}.
     *
     * @return new instance
     */
    @NonNull
    public OkHttpClient getHttpClient() {
        OkHttpClient instance = httpClient;
        if (instance == null) {
            synchronized (this) {
                instance = httpClient;
                if (instance == null) {
                    instance = createOkHttpClient();
                    httpClient = instance;
                }
            }
        }
        return instance;
    }

    @NonNull
    private OkHttpClient createOkHttpClient() {

        final OkHttpClient.Builder builder = ServiceLocator
                .getInstance()
                .getOkHttpClient()
                .newBuilder()
                .connectTimeout(config.getConnectTimeoutInMs(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getReadTimeoutInMs(), TimeUnit.MILLISECONDS);

        // For SearchEngines this will never be null.
        // But for Calibre, it can be null.
        final Throttler throttler = config.getThrottler();
        if (throttler != null) {
            builder.addInterceptor(new ThrottlingInterceptor(throttler))
                   .addInterceptor(new RateLimitInterceptor(
                           throttler, config.isHttpLoggingEnabled()));
        }

        if (sslContext != null) {
            builder.setSocketFactory$okhttp(sslContext.getSocketFactory());
        }

        if (config.isHttpLoggingEnabled()) {
            builder.addNetworkInterceptor(OkHttpLoggerFactory.getLogger(config.getLogTag()));
        }

        return builder.build();
    }

    @NonNull
    private JsoupLoader getJsoupLoader() {
        JsoupLoader instance = jsoupLoader;
        if (instance == null) {
            synchronized (this) {
                instance = jsoupLoader;
                if (instance == null) {
                    instance = new JsoupLoader(this, charSetName);
                    jsoupLoader = instance;
                }
            }
        }
        return instance;
    }

    @NonNull
    private RequestFactory getImageRequestFactory() {
        RequestFactory instance = imageRequestFactory;
        if (instance == null) {
            synchronized (this) {
                instance = imageRequestFactory;
                if (instance == null) {
                    instance = new ImageRequestFactoryDefault(acceptLanguageHeader);
                    imageRequestFactory = instance;
                }
            }
        }
        return instance;
    }

    @NonNull
    private ImageDownloader getImageDownloader() {
        ImageDownloader instance = imageDownloader;
        if (instance == null) {
            synchronized (this) {
                instance = imageDownloader;
                if (instance == null) {
                    instance = new ImageDownloader(getHttpClient(),
                                                   config.getThrottler(),
                                                   config.getLogStringRes(),
                                                   config.isHttpLoggingEnabled());
                    imageDownloader = instance;
                }
            }
        }
        return instance;
    }

    /**
     * Provide a more or less robust base to load a url and parse the HTML with Jsoup.
     * The extra safeguards are needed due to the size of some HTML pages we're parsing.
     */
    public static class JsoupLoader {

        /** Log tag. */
        private static final String TAG = "JsoupLoader";

        @NonNull
        private final HttpCallFactory httpCallFactory;
        @Nullable
        private final String charSetName;

        @Nullable
        private HttpCall httpCall;

        /** The downloaded and parsed web page. */
        @Nullable
        private Document document;
        /** The <strong>request</strong> url for the web page. */
        @Nullable
        private String requestUrl;

        /**
         * Constructor.
         *
         * @param httpCallFactory to use
         * @param charSetName (optional) for the parser to use; or {@code null} to auto-select.
         */
        JsoupLoader(@NonNull final HttpCallFactory httpCallFactory,
                    @Nullable final String charSetName) {
            this.httpCallFactory = httpCallFactory;
            this.charSetName = charSetName;
        }

        /**
         * Reset the loader.
         */
        public void reset() {
            document = null;
            requestUrl = null;
        }

        /**
         * Fetch the URL and parse it into {@link #document}.
         * Will silently return if it has downloaded the document before.
         * Call {@link #reset()} before to force a clean/new download.
         *
         * @param context     Current context
         * @param parser      to use
         * @param url         to fetch
         * @param headers     optional
         *
         * @return the parsed Document
         *
         * @throws IOException on generic/other IO failures
         */
        @WorkerThread
        @NonNull
        public Document loadDocument(@NonNull final Context context,
                                     @NonNull final Parser parser,
                                     @NonNull final String url,
                                     @Nullable final Map<String, String> headers)
                throws IOException {

            // are we requesting the same url again ?
            if (document != null && url.equals(requestUrl)) {
                // return the previously parsed doc
                return document;
            }

            // new download
            document = null;
            requestUrl = url;

            // If the site drops connection while we're fetching the page, we retry once.
            //
            // This retry tries to solve the situation when a successful connection gets
            // dropped mid-read specifically due to SSLProtocolException | EOFException
            int attemptsLeft = 2;

            while (attemptsLeft > 0) {
                if (httpCallFactory.isLogEnabled()) {
                    LoggerFactory.getLogger().d(TAG, "loadDocument|get",
                                                "attemptsLeft=" + attemptsLeft,
                                                "requestUrl=`" + requestUrl + '`');
                }

                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException();
                }

                try {
                    httpCall = httpCallFactory.createCall();
                    document = httpCall.get(url, headers, (response, is)
                            -> processResponse(response, is, parser));
                    //noinspection DataFlowIssue
                    return document;

                } catch (@NonNull final SSLProtocolException | EOFException e) {
                    document = null;

                    // EOFException: happens often with ISFDB...
                    // This is after a successful connection was made.
                    // Google search says it's a server issue.
                    // Not so sure that Google search is correct thought but what do I know...

                    // SSLProtocolException: happens often with stripinfo.be;
                    // ... at least while running in the emulator:
                    // javax.net.ssl.SSLProtocolException:
                    //  Read error: ssl=0x91461c80: Failure in SSL library, usually a protocol error
                    //  error:1e000065:Cipher functions:OPENSSL_internal:BAD_DECRYPT
                    //  external/boringssl/src/crypto/cipher/e_aes.c:1143 0xa0d78e9f:0x00000000)
                    //  error:1000008b:SSL routines:OPENSSL_internal:
                    //       DECRYPTION_FAILED_OR_BAD_RECORD_MAC
                    //  external/boringssl/src/ssl/tls_record.c:277 0xa0d78e9f:0x00000000)
                    // at com.android.org.conscrypt.NativeCrypto.SSL_read(Native Method)
                    // 2025-04-13: not seen for quite some time now.
                    // ...
                    if (httpCallFactory.isLogEnabled()) {
                        LoggerFactory.getLogger().w(TAG, "loadDocument",
                                                    "e=" + e.getMessage(),
                                                    "requestUrl=\"" + requestUrl + '\"');
                    }
                    // we'll retry.
                    attemptsLeft--;
                    if (attemptsLeft == 0) {
                        // IOException
                        throw e;
                    }

                } catch (@NonNull final HttpNotFoundException e) {
                    // Getting a 404 here is usually NOT an actual problem.
                    // We don't want to change the http response checker for debugging reason,
                    // but we DO change the message here:
                    e.setLocalizedMessage(context.getString(R.string.warning_book_not_found));
                    throw e;

                } catch (@NonNull final IOException e) {
                    document = null;

                    if (httpCallFactory.isLogEnabled()) {
                        LoggerFactory.getLogger().e(TAG, e, "loadDocument",
                                                    "requestUrl=" + requestUrl);
                    }
                    throw e;
                } finally {
                    httpCall = null;
                }
            }

            throw new IOException("Failed to get: " + requestUrl);
        }

        @NonNull
        private Document processResponse(@NonNull final Response response,
                                         @NonNull final InputStream is,
                                         @NonNull final Parser parser)
                throws IOException {
            // the original url will change after a redirect.
            // We need the actual url for further processing.
            String locationHeader = response.header(HttpConstants.RESPONSE_HEADER_LOCATION);
            final String url = response.request().url().toString();

            if (httpCallFactory.isLogEnabled()) {
                LoggerFactory.getLogger().d(TAG, "processResponse",
                                            "response.getURL()=" + url
                                            + "\nlocation  =" + locationHeader);
            }

            if (locationHeader == null || locationHeader.isEmpty()) {
                locationHeader = url;
                if (httpCallFactory.isLogEnabled()) {
                    LoggerFactory.getLogger().d(TAG, "processResponse",
                                                "location header not set, using url");
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException();
            }

            /*
             VERY IMPORTANT: Explicitly set the baseUri to the location header.
             JSoup by default uses the absolute path from the inputStream
             and sets that as the document 'location'
             From JSoup docs:

             Get the URL this Document was parsed from.
             If the starting URL is a redirect, this will return the
             final URL from which the document was served from.

             @return location
             public String location() {
                return location;
             }

            However, that is WRONG (org.jsoup:jsoup:1.11.3)
            It will NOT resolve the redirect itself and 'location' == 'baseUri'
            */
            final Document parsedDocument = Jsoup.parse(is, charSetName, locationHeader, parser);
            if (httpCallFactory.isLogEnabled()) {
                LoggerFactory.getLogger()
                             .d(TAG, "processResponse|disconnect",
                                "AFTER parsing|document.location()="
                                + parsedDocument.location());
            }

            return parsedDocument;
        }

        public void cancel() {
            synchronized (this) {
                if (httpCall != null) {
                    httpCall.cancel();
                }
            }
        }
    }
}
