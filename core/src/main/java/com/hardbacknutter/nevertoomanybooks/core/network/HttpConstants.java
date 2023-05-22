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

@SuppressWarnings("WeakerAccess")
public final class HttpConstants {

    /** HTTP Request Header. */
    public static final String AUTHORIZATION = "Authorization";

    /** HTTP Request Header. */
    public static final String REFERER = "Referer";

    /** HTTP Request Header. */
    public static final String CONNECTION = "Connection";
    public static final String CONNECTION_CLOSE = "close";
    public static final String CONNECTION_KEEP_ALIVE = "keep-alive";

    /** HTTP Request Header. */
    public static final String ACCEPT = "Accept";
    public static final String ACCEPT_KITCHEN_SINK =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";

    /** HTTP Request Header. */
    public static final String ACCEPT_LANGUAGE = "Accept-Language";
    /** HTTP Request Header. */
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    /**
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Accept-Encoding">
     * Accept-Encoding</a>
     * Firefox sends (2023-05-22): "gzip, deflate, br"
     */
    public static final String ACCEPT_ENCODING_GZIP = "gzip";
    /** HTTP Request Header. */
    public static final String UPGRADE_INSECURE_REQUESTS = "Upgrade-Insecure-Requests";
    public static final String UPGRADE_INSECURE_REQUESTS_TRUE = "1";


    /** HTTP Request Header. */
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    public static final String CONTENT_TYPE_FORM_URL_ENCODED =
            "application/x-www-form-urlencoded; charset=UTF-8";

    /** HTTP Request Header. */
    public static final String USER_AGENT = "User-Agent";
    /**
     * RELEASE: 2023-03-26. Continuously update to latest version. Now set to Firefox.
     * Some sites don't return full data unless the user agent is set to a valid browser.
     */
    public static final String USER_AGENT_VALUE =
//            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
//            + " AppleWebKit/537.36 (KHTML, like Gecko)"
//            + " Chrome/108.0.0.0 Safari/537.36";
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/111.0";


    /** HTTP Response Header. */
    public static final String LOCATION = "location";

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
