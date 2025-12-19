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

package com.hardbacknutter.nevertoomanybooks.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.net.CookieStore;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpImpl;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpCall;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;

import okhttp3.OkHttpClient;

public final class HttpCallFactory {

    private HttpCallFactory() {
    }

    /**
     * Create a basic {@link FutureHttp} instance.
     *
     * @param siteResId string resource for the site name; used for logging/messages.
     * @param <R>       the type of the return value for the request
     *
     * @return new instance
     */
    public static <R> FutureHttp<R> create(@StringRes final int siteResId) {
        return new FutureHttpImpl<>(siteResId);
    }

    /**
     * Create a {@link FutureHttp} based on the given engine configuration.
     *
     * @param engineId to use
     * @param <R>      the type of the return value for the request
     *
     * @return new instance
     */
    public static <R> FutureHttp<R> create(@NonNull final EngineId engineId) {
        final FutureHttp<R> request = create(engineId.getLabelResId());

        final SearchEngineConfig config = Objects.requireNonNull(engineId.getConfig());
        request.setConnectTimeout(config.getConnectTimeoutInMs())
               .setReadTimeout(config.getReadTimeoutInMs())
               .setThrottler(config.getThrottler())
               .enableLogging(config.isLogHttpGetRequests());

        return request;
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
                            config.getThrottler(),
                            config.isLogHttpGetRequests());
    }

    /**
     * Create a basic {@link HttpCall}.
     *
     * @param httpClient the client
     * @param labelResId string resource representing the caller
     * @param throttler  to use
     * @param logEnabled flag
     *
     * @return new instance
     */
    public static HttpCall create(@NonNull final OkHttpClient httpClient,
                                  @Nullable final Throttler throttler,
                                  @StringRes final int labelResId,
                                  final boolean logEnabled) {
        final CookieStore cookieStore = ServiceLocator.getInstance()
                                                      .getCookieManager()
                                                      .getCookieStore();
        return new HttpCall(httpClient, cookieStore, labelResId, throttler, logEnabled);
    }
}
