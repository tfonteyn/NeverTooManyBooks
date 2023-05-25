/*
 * @Copyright 2018-2023 HardBackNutter
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

/** @noinspection LongLine */
@SuppressWarnings("WeakerAccess")
public final class HttpConstants {

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
    public static final String CONNECTION_CLOSE = "close";
    public static final String CONNECTION_KEEP_ALIVE = "keep-alive";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control">
     * Cache-Control</a>
     */
    public static final String CACHE_CONTROL = "Cache-Control";
    public static final String CACHE_CONTROL_0 = "max-age=0";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept">
     * Accept</a>
     */
    public static final String ACCEPT = "Accept";
    /** Firefox sends (2023-05-22) this default. */
    public static final String ACCEPT_KITCHEN_SINK =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";

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
    /** Firefox sends (2023-05-22): "gzip, deflate, br". */
    public static final String ACCEPT_ENCODING_GZIP = "gzip";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Upgrade-Insecure-Requests">
     * Upgrade-Insecure-Requests</a>
     */
    public static final String UPGRADE_INSECURE_REQUESTS = "Upgrade-Insecure-Requests";
    public static final String UPGRADE_INSECURE_REQUESTS_TRUE = "1";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Type">
     * Content-Type</a>
     */
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    public static final String CONTENT_TYPE_FORM_URL_ENCODED =
            "application/x-www-form-urlencoded; charset=UTF-8";

    /**
     * HTTP Request Header.
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/User-Agent">
     * User-Agent</a>
     */
    public static final String USER_AGENT = "User-Agent";
    /**
     * RELEASE: 2023-05-26. Continuously update to latest version. Now set to Firefox.
     * Some sites don't return full data unless the user agent is set to a valid browser.
     */
    public static final String USER_AGENT_VALUE =
            // "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36";
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/113.0";


    /** HTTP Response Header. */
    public static final String RESPONSE_HEADER_LOCATION = "location";

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
        return "gzip".equals(response.getHeaderField("content-encoding"));
    }
}
