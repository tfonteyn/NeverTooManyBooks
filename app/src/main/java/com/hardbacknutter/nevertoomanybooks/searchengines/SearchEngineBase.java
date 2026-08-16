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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.EmptySuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpLanguageHeader;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.covers.ImageDownloader;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.covers.ImageWebSize;
import com.hardbacknutter.nevertoomanybooks.network.HttpCallFactory;
import com.hardbacknutter.util.logger.LoggerFactory;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public abstract class SearchEngineBase
        implements SearchEngine {

    @NonNull
    private final SearchEngineConfig config;
    /**
     * Set by a client or from within the task.
     * It's a <strong>request</strong> to cancel while running.
     */
    private final AtomicBoolean cancelRequested = new AtomicBoolean();

    @NonNull
    protected final BookParserHelper bookParserHelper;

    @Nullable
    private SSLContext sslContext;
    /** Lazy created in {@link #getImageDownloader()}. */
    @Nullable
    private volatile ImageDownloader imageDownloader;
    /**
     * Either set from a child constructor with
     * {@link #setImageRequestFactory(ImageRequestFactory)}
     * or lazy created in {@link #getImageRequestFactory(Context)}.
     */
    @Nullable
    private volatile ImageRequestFactory imageRequestFactory;

    @Nullable
    private Cancellable caller;

    /**
     * Constructor.
     *
     * @param context Current context. NOT stored.
     * @param config  the search engine configuration
     *
     * @see EngineId#createSearchEngine(Context)
     */
    protected SearchEngineBase(@NonNull final Context context,
                               @NonNull final SearchEngineConfig config) {
        this.config = config;
        this.bookParserHelper = new BookParserHelper(config);
    }

    @NonNull
    private static Map<String, String> createHeadersForGET(@NonNull final Locale siteLocale,
                                                           @NonNull final Locale userLocale) {
        final String acceptLanguageHeader = HttpLanguageHeader.create(siteLocale, userLocale);
        // Improve compatibility by sending standard headers.

        // Example of a Firefox request to https://developer.android.com

        // GET / HTTP/1.1
        // Host: developer.android.com
        // User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:152.0) Gecko/20100101 Firefox/152.0
        // Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
        // Accept-Language: en-GB,en;q=0.9,nl-BE;q=0.8,de-DE;q=0.7
        // Accept-Encoding: gzip, deflate, br, zstd
        // DNT: 1
        // Sec-GPC: 1
        // Upgrade-Insecure-Requests: 1
        // Sec-Fetch-Dest: document
        // Sec-Fetch-Mode: navigate
        // Sec-Fetch-Site: none
        // Sec-Fetch-User: ?1
        // Connection: keep-alive

        // ordered list.
        final Map<String, String> headers = new LinkedHashMap<>();
        // Host & User-Agent are prefixed to the below in {@link FutureHttp#execute}

        headers.put(HttpConstants.ACCEPT,
                    HttpConstants.ACCEPT_KITCHEN_SINK);
        headers.put(HttpConstants.ACCEPT_LANGUAGE,
                    acceptLanguageHeader);
        headers.put(HttpConstants.ACCEPT_ENCODING,
                    HttpConstants.ACCEPT_ENCODING_GZIP);

        headers.put(HttpConstants.DNT, "1");
        headers.put(HttpConstants.SEC_GPC, "1");

        headers.put(HttpConstants.UPGRADE_INSECURE_REQUESTS,
                    HttpConstants.UPGRADE_INSECURE_REQUESTS_TRUE);

        // We want a generic document, e.g. html, xml, json, ...
        headers.put(HttpConstants.SEC_FETCH_DEST,
                    HttpConstants.SEC_FETCH_DEST_DOCUMENT);
        // The request is initiated by navigation between HTML documents.
        headers.put(HttpConstants.SEC_FETCH_MODE,
                    HttpConstants.SEC_FETCH_MODE_NAVIGATE);

        // The request was sent by a "user" (our app) and not some auto/link/etc...
        headers.put(HttpConstants.SEC_FETCH_SITE,
                    HttpConstants.SEC_FETCH_SITE_NONE);
        headers.put(HttpConstants.SEC_FETCH_USER, "?1");

        headers.put(HttpConstants.CONNECTION,
                    HttpConstants.CONNECTION_KEEP_ALIVE);
        return headers;
    }

    /**
     * Override the default.
     * This method <strong>must</strong> be called from the child constructor.
     *
     * @param imageRequestFactory to use
     */
    protected void setImageRequestFactory(@NonNull final ImageRequestFactory imageRequestFactory) {
        this.imageRequestFactory = imageRequestFactory;
    }

    @NonNull
    @Override
    public EngineId getEngineId() {
        return config.getEngineId();
    }

    @Override
    public void ping()
            throws UnknownHostException,
                   IOException,
                   SocketTimeoutException,
                   MalformedURLException {
        ServiceLocator.getInstance().getNetworkChecker().ping(
                config.getHostUrl(), config.getConnectTimeoutInMs());
    }

    /**
     * Get the <strong>standard</strong> Locale for this engine.
     * <p>
     * Override to derive the locale from the host-url instead
     *
     * @return site locale
     *
     * @see #getLocale(Context, String)
     */
    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        return config.getEngineId().getDefaultLocale();
    }

    /**
     * Derive the Locale from the actual url.
     * <p>
     * Sites which support multiple countries, should overwrite {@link #getLocale(Context)} with
     * {@code getLocale(context, getHostUrl()); }
     *
     * @param context Current context
     * @param baseUrl to digest
     *
     * @return Locale matching the url root domain
     */
    @NonNull
    protected Locale getLocale(@NonNull final Context context,
                               @NonNull final String baseUrl) {

        final String root = baseUrl.substring(baseUrl.lastIndexOf('.') + 1);
        switch (root) {
            case "com":
                return Locale.US;

            case "uk":
                // country code is GB
                return Locale.UK;

            default:
                // other sites are (should be ?) just the country code.
                final Locale userLocale = context.getResources().getConfiguration().getLocales()
                                                 .get(0);
                final Optional<Locale> locale = ServiceLocator.getInstance().getAppLocale()
                                                              .getLocale(root, userLocale);
                if (BuildConfig.DEBUG /* always */) {
                    LoggerFactory.getLogger().d(config.getEngineId().name(),
                                                "baseUrl=" + baseUrl, "getLocale=" + locale);
                }
                return locale.orElse(Locale.US);
        }
    }

    @NonNull
    public SearchEngineConfig getConfig() {
        return config;
    }

    @VisibleForTesting
    @NonNull
    public BookParserHelper getParserHelper() {
        return bookParserHelper;
    }

    @Override
    @AnyThread
    @CallSuper
    public void cancel() {
        cancelRequested.set(true);
        synchronized (this) {
            final ImageDownloader downloader = imageDownloader;
            if (downloader != null) {
                downloader.cancel();
            }
        }
    }

    @Override
    public void setCaller(@Nullable final Cancellable caller) {
        this.caller = caller;
        cancelRequested.set(false);
    }

    @Override
    public boolean isCancelled() {
        // caller being null should only happen when we check if we're cancelled
        // before a search was started.
        return cancelRequested.get() || caller == null || caller.isCancelled();
    }

    @Nullable
    protected SSLContext getSslContext() {
        return sslContext;
    }

    protected void setSslContext(@Nullable final SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Convenience method which uses the engines specific network configuration
     * to create a suitable {@code HEAD} request.
     *
     * @param <T> return type
     *
     * @return new {@code HEAD} request instance
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public <T> FutureHttp<T> createHeadRequest() {
        final FutureHttp<T> httpHead = HttpCallFactory.create(config.getEngineId());
        httpHead.setSSLContext(sslContext);
        return httpHead;
    }

    /**
     * Convenience method which uses the engines specific network configuration
     * to create a suitable {@code GET} request.
     * <p>
     * The headers are set to the defaults as used by Firefox to request a "document"
     *
     * @param context Current context
     * @param <T>     return type
     *
     * @return new {@code GET} request instance
     */
    @NonNull
    public <T> FutureHttp<T> createGetDocumentRequest(@NonNull final Context context) {

        final Locale siteLocale = getLocale(context);
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);

        final Map<String, String> headers = createHeadersForGET(siteLocale, userLocale);

        final FutureHttp<T> httpGet = HttpCallFactory.create(config.getEngineId());
        httpGet.setSSLContext(sslContext);
        httpGet.setHeaders(headers);

        return httpGet;
    }

    /**
     * Convenience method which uses the engines specific network configuration
     * to create a suitable {@link OkHttpClient}.
     * <p>
     * Overridable for sites requiring quirks...
     *
     * @return new {@link OkHttpClient} instance
     *
     * @see #createGetDocumentRequest(Context)
     */
    @NonNull
    @EmptySuper
    public OkHttpClient createHttpClient() {
        return HttpCallFactory.createHttpClient(config.getEngineId(), sslContext);
    }

    /**
     * Convenience method to save an image using the engines specific network configuration.
     *
     * @param context           Current context
     * @param url               Image file URL
     * @param requestProperties optional map
     * @param bookId            more or less unique id; e.g. isbn or website native id, etc...
     * @param cIdx              0..n image index
     * @param size              (optional) size parameter for engines/sites which support one
     *
     * @return File fileSpec, or {@code Optional.empty()} on failure
     *
     * @throws CoverStorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    public Optional<String> saveImage(@NonNull final Context context,
                                      @NonNull final String url,
                                      @Nullable final Map<String, String> requestProperties,
                                      @Nullable final String bookId,
                                      @IntRange(from = 0, to = 3) final int cIdx,
                                      @Nullable final ImageWebSize size)
            throws CoverStorageException {

        final String tempFilename = ImageFileInfo.getTempFilename(
                config.getEngineId().getPreferenceKey(), bookId, cIdx, size);

        try {
            final Request imageRequest = getImageRequestFactory(context)
                    .createRequest(url, requestProperties);

            return getImageDownloader().fetch(imageRequest, tempFilename)
                                       .map(File::getAbsolutePath);

        } catch (@NonNull final IOException e) {
            // we swallow IOExceptions, even when the disk is full.
            // We're counting on that condition to be caught elsewhere...
            // as handling it in each call here would become [bleep] fast.
            return Optional.empty();
        }
    }

    @NonNull
    private ImageRequestFactory getImageRequestFactory(@NonNull final Context context) {
        ImageRequestFactory instance = imageRequestFactory;
        if (instance == null) {
            synchronized (this) {
                instance = imageRequestFactory;
                if (instance == null) {
                    final Locale siteLocale = getLocale(context);
                    final Locale userLocale = context.getResources()
                                                     .getConfiguration()
                                                     .getLocales().get(0);
                    instance = new ImageRequestFactoryDefault(siteLocale, userLocale);
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
                    final OkHttpClient httpClient = createHttpClient();
                    instance = new ImageDownloader(httpClient,
                                                   config.getThrottler(),
                                                   config.getEngineId().getLabelResId(),
                                                   config.isLogHttpGetRequests());
                    imageDownloader = instance;
                }
            }
        }
        return instance;
    }
}
