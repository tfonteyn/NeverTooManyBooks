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
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpImpl;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpCall;
import com.hardbacknutter.nevertoomanybooks.core.network.RateLimitInterceptor;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.core.network.ThrottlingInterceptor;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.utils.OkHttpLoggerFactory;

import okhttp3.OkHttpClient;

public final class HttpCallFactory {

    private HttpCallFactory() {
    }

    /**
     * Create a {@link FutureHttp} based on the given engine configuration.
     *
     * @param engineId to use
     * @param <R>      the type of the return value for the request
     *
     * @return new instance
     */
    @NonNull
    public static <R> FutureHttp<R> create(@NonNull final EngineId engineId) {
        final SearchEngineConfig config = engineId.getConfig();
        @SuppressWarnings("DataFlowIssue")
        final Throttler throttler = config.getThrottler();
        final boolean enableLog = config.isLogHttpGetRequests();

        final FutureHttp<R> request = new FutureHttpImpl<>(engineId.getLabelResId(),
                                                           throttler, enableLog);
        request.setConnectTimeout(config.getConnectTimeoutInMs())
               .setReadTimeout(config.getReadTimeoutInMs());

        return request;
    }

    /**
     * Create an {@link OkHttpClient} based on the given engine configuration.
     *
     * @param engineId   to use
     * @param sslContext (optional) to use
     *
     * @return new {@link OkHttpClient} instance
     */
    @NonNull
    public static OkHttpClient createHttpClient(@NonNull final EngineId engineId,
                                                @Nullable final SSLContext sslContext) {
        final SearchEngineConfig config = engineId.getConfig();
        //noinspection DataFlowIssue
        final Throttler throttler = config.getThrottler();
        final boolean enableLog = config.isLogHttpGetRequests();

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
            // use the app context, it's the non-translatable name used as a log tag
            final String tag = engineId.getName(ServiceLocator.getInstance().getAppContext());
            builder.addNetworkInterceptor(OkHttpLoggerFactory.getLogger(tag));
        }

        return builder.build();
    }

    /**
     * Create an {@link HttpCall} based on the given engine configuration.
     *
     * @param httpClient the client
     * @param engineId   to use
     *
     * @return new instance
     */
    @NonNull
    public static HttpCall create(@NonNull final OkHttpClient httpClient,
                                  @NonNull final EngineId engineId) {
        final CookieStore cookieStore = ServiceLocator.getInstance()
                                                      .getCookieManager()
                                                      .getCookieStore();
        final SearchEngineConfig config = engineId.getConfig();

        //noinspection DataFlowIssue
        return new HttpCall(httpClient, cookieStore,
                            engineId.getLabelResId(),
                            config.isLogHttpGetRequests());
    }
}
