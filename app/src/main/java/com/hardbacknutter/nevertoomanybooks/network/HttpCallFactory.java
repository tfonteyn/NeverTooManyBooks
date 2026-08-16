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
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.utils.OkHttpLoggerFactory;

import okhttp3.OkHttpClient;

public final class HttpCallFactory {

    @NonNull
    private final SearchEngineConfig config;
    private final boolean enableLog;

    @NonNull
    private final CookieStore cookieStore;
    @Nullable
    private final SSLContext sslContext;
    @NonNull
    private final String acceptLanguageHeader;

    /** Lazy created in {@link #getHttpClient()}. */
    @Nullable
    private volatile OkHttpClient httpClient;

    public HttpCallFactory(@NonNull final SearchEngineConfig config,
                           @Nullable final SSLContext sslContext,
                           @NonNull final String acceptLanguageHeader) {
        this.config = config;
        this.enableLog = config.isLogHttpGetRequests();
        this.sslContext = sslContext;
        this.acceptLanguageHeader = acceptLanguageHeader;

        cookieStore = ServiceLocator.getInstance()
                                    .getCookieManager()
                                    .getCookieStore();
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
        return new HttpCall(getHttpClient(), cookieStore, acceptLanguageHeader,
                            config.getEngineId().getLabelResId(),
                            config.isLogHttpGetRequests());
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
        return new HttpCall(httpClient, cookieStore, acceptLanguageHeader,
                            config.getEngineId().getLabelResId(),
                            config.isLogHttpGetRequests());
    }

    @NonNull
    private OkHttpClient createOkHttpClient() {
        final Throttler throttler = config.getThrottler();

        final OkHttpClient.Builder builder = ServiceLocator
                .getInstance()
                .getOkHttpClient()
                .newBuilder()
                .connectTimeout(config.getConnectTimeoutInMs(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getReadTimeoutInMs(), TimeUnit.MILLISECONDS)
                .addInterceptor(new ThrottlingInterceptor(throttler))
                .addInterceptor(new RateLimitInterceptor(throttler, enableLog));

        if (sslContext != null) {
            builder.setSocketFactory$okhttp(sslContext.getSocketFactory());
        }

        if (enableLog) {
            builder.addNetworkInterceptor(
                    OkHttpLoggerFactory.getLogger(config.getEngineId().name()));
        }

        return builder.build();
    }
}
