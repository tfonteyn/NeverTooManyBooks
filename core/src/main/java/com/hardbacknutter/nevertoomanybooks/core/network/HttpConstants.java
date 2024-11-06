/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.core.network;

import androidx.annotation.NonNull;

import java.net.HttpURLConnection;

@SuppressWarnings("WeakerAccess")
public final class HttpConstants {

    /**
     * RELEASE: BROWSER_USER_AGENT: Last updated: 2024-11-06.
     * Some sites don't return full data unless the user agent is set to a valid browser.
     */
    public static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Authorization">
     * Authorization</a>
     */
    public static final String AUTHORIZATION = "Authorization";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referer">
     * Referer</a>
     */
    public static final String REFERER = "Referer";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Connection">
     * Connection</a>
     */
    public static final String CONNECTION = "Connection";
    /** Value for {@link #CONNECTION}. */
    public static final String CONNECTION_CLOSE = "close";
    /** Value for {@link #CONNECTION}. */
    public static final String CONNECTION_KEEP_ALIVE = "keep-alive";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control">
     * Cache-Control</a>
     */
    public static final String CACHE_CONTROL = "Cache-Control";
    /** Value for {@link #CACHE_CONTROL}. */
    public static final String CACHE_CONTROL_0 = "max-age=0";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept">
     * Accept</a>
     */
    public static final String ACCEPT = "Accept";
    /**
     * RELEASE: update with the current Firefox default "Accept" header. Last updated: 2024-11-06.
     */
    public static final String ACCEPT_KITCHEN_SINK =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    /** Specify images only. */
    public static final String ACCEPT_IMAGE = "image/avif,image/webp,*/*";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Language">
     * Accept-Language</a>
     * <p>
     * Example values:
     * "en-GB,en;q=0.8,nl;q=0.6,de;q=0.3"
     * "en-GB,en;q=0.9,nl-BE;q=0.8,nl;q=0.7,de-DE;q=0.6,de;q=0.5,fr-BE;q=0.4,fr;q=0.3,en-US;q=0.2"
     */
    public static final String ACCEPT_LANGUAGE = "Accept-Language";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Encoding">
     * Accept-Encoding</a>
     */
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    /** The Firefox default on 2024-11-06: "gzip, deflate, br, zstd". */
    public static final String ACCEPT_ENCODING_GZIP = "gzip";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Upgrade-Insecure-Requests">
     * Upgrade-Insecure-Requests</a>
     */
    public static final String UPGRADE_INSECURE_REQUESTS = "Upgrade-Insecure-Requests";
    /** Value for {@link #UPGRADE_INSECURE_REQUESTS}. */
    public static final String UPGRADE_INSECURE_REQUESTS_TRUE = "1";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type">
     * Content-Type</a>
     */
    public static final String CONTENT_TYPE = "Content-Type";
    /** Value for {@link #CONTENT_TYPE}. */
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    /** Value for {@link #CONTENT_TYPE}. */
    public static final String CONTENT_TYPE_FORM_URL_ENCODED =
            "application/x-www-form-urlencoded; charset=UTF-8";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-Fetch-Dest">
     * Sec-Fetch-Dest</a>
     * <p>
     * "document" or "image"
     */
    public static final String SEC_FETCH_DEST = "Sec-Fetch-Dest";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-Fetch-Mode">
     * Sec-Fetch-Mode</a>
     * <p>
     * "navigate" or "no-cors"
     */
    public static final String SEC_FETCH_MODE = "Sec-Fetch-Mode";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-Fetch-Site">
     * Sec-Fetch-Site</a>
     * <p>
     * "none" or "same-origin"
     */
    public static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-Fetch-User">
     * Sec-Fetch-User</a>
     * <p>
     * "?1"
     */
    public static final String SEC_FETCH_USER = "Sec-Fetch-User";
    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Host">
     * Host</a>
     */
    public static final String HOST = "Host";
    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/User-Agent">
     * User-Agent</a>
     */
    public static final String USER_AGENT = "User-Agent";
    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/DNT">
     * DNT</a>
     */
    public static final String DNT = "DNT";

    /**
     * HTTP Response Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Location">
     * Location</a>
     */
    public static final String RESPONSE_HEADER_LOCATION = "Location";

    /**
     * HTTP Response Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Encoding">
     * Content-Encoding</a>
     */
    public static final String RESPONSE_HEADER_CONTENT_ENCODING = "Content-Encoding";

    private HttpConstants() {
    }

    /**
     * Check if the response headers indicate the encoding is gzip.
     *
     * @param response connection to check
     *
     * @return {@code true} if the content-encoding was "gzip"
     */
    public static boolean isZipped(@NonNull final HttpURLConnection response) {
        return "gzip".equalsIgnoreCase(response.getHeaderField(RESPONSE_HEADER_CONTENT_ENCODING));
    }
}
