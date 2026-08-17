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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.CookieStore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpCall;
import com.hardbacknutter.nevertoomanybooks.core.network.RateLimitInterceptor;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.core.network.ThrottlingInterceptor;
import com.hardbacknutter.nevertoomanybooks.utils.OkHttpLoggerFactory;

import okhttp3.OkHttpClient;

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

    /** Lazy created in {@link #getHttpClient()}. */
    @Nullable
    private volatile OkHttpClient httpClient;

    public HttpCallFactory(@NonNull final NetworkConfig config,
                           @Nullable final SSLContext sslContext,
                           @NonNull final CookieStore cookieStore,
                           @NonNull final String acceptLanguageHeader) {
        this.config = config;
        this.sslContext = sslContext;
        this.cookieStore = cookieStore;
        this.acceptLanguageHeader = acceptLanguageHeader;

        this.enableLog = config.isHttpLoggingEnabled();
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
}
