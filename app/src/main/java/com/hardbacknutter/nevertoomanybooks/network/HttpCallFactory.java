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

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.net.CookieStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpCall;
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

import okhttp3.OkHttpClient;
import okhttp3.Request;

public final class HttpCallFactory {

    @NonNull
    private final NetworkConfig config;
    @Nullable
    private final SSLContext sslContext;
    @NonNull
    private final CookieStore cookieStore;
    @NonNull
    private final String acceptLanguageHeader;

    private final boolean enableLog;
    @NonNull
    private final String imageFilenamePrefix;

    /** Lazy created in {@link #getHttpClient()}. */
    @Nullable
    private volatile OkHttpClient httpClient;

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
                           @NonNull final String imageFilenamePrefix) {
        this.config = config;
        this.sslContext = sslContext;
        this.cookieStore = cookieStore;
        this.acceptLanguageHeader = acceptLanguageHeader;

        this.enableLog = config.isHttpLoggingEnabled();
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

    @AnyThread
    @CallSuper
    public void cancel() {
        synchronized (this) {
            final ImageDownloader downloader = imageDownloader;
            if (downloader != null) {
                downloader.cancel();
            }
        }
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
                   .addInterceptor(new RateLimitInterceptor(throttler, enableLog));
        }

        if (sslContext != null) {
            builder.setSocketFactory$okhttp(sslContext.getSocketFactory());
        }

        if (enableLog) {
            builder.addNetworkInterceptor(OkHttpLoggerFactory.getLogger(config.getLogTag()));
        }

        return builder.build();
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
}
