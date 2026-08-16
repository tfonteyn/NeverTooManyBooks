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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;

public class HttpFutureFactory {

    @NonNull
    private final SearchEngineConfig config;
    private final boolean enableLog;

    @Nullable
    private final SSLContext sslContext;
    @NonNull
    private final String acceptLanguageHeader;

    public HttpFutureFactory(@NonNull final SearchEngineConfig config,
                             @Nullable final SSLContext sslContext,
                             @NonNull final String acceptLanguageHeader) {
        this.config = config;
        enableLog = config.isLogHttpGetRequests();
        this.sslContext = sslContext;
        this.acceptLanguageHeader = acceptLanguageHeader;
    }

    /**
     * Create a {@link FutureHttp}.
     * <p>
     * <strong>NO</strong> headers added.
     *
     * @param <R> the type of the return value for the request
     *
     * @return new instance
     */
    @NonNull
    public <R> FutureHttp<R> createRequest() {
        final EngineId engineId = config.getEngineId();
        final Throttler throttler = config.getThrottler();

        final FutureHttp<R> request = new FutureHttp<>(engineId.getLabelResId(),
                                                       throttler, enableLog);
        request.setConnectTimeout(config.getConnectTimeoutInMs())
               .setReadTimeout(config.getReadTimeoutInMs())
               .setSSLContext(sslContext);

        return request;
    }

    @NonNull
    private Map<String, String> createHeadersForGETorHEAD() {
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
     * Convenience method to create a suitable {@code HEAD} request.
     *
     * @param <T> return type
     *
     * @return new {@code HEAD} request instance
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public <T> FutureHttp<T> createHeadRequest() {
        final FutureHttp<T> request = createRequest();
        request.setHeaders(createHeadersForGETorHEAD());
        return request;
    }


    /**
     * Convenience method to create a suitable {@code GET} request.
     * <p>
     * The headers are set to the defaults as used by Firefox to request a "document"
     *
     * @param <T> return type
     *
     * @return new {@code GET} request instance
     */
    @NonNull
    public <T> FutureHttp<T> createGetDocumentRequest() {
        final FutureHttp<T> request = createRequest();
        request.setHeaders(createHeadersForGETorHEAD());
        return request;
    }
}
