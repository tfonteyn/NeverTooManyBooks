/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

public final class HttpConstants {

    /** HTTP Request method. */
    public static final String POST = "POST";

    /** HTTP authentication header. */
    public static final String AUTHORIZATION = "Authorization";

    /** HTTP Request Header. */
    public static final String CONNECTION = "Connection";
    public static final String CONNECTION_CLOSE = "close";

    /** HTTP Request Header. */
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";


    /** HTTP Request Header. */
    public static final String USER_AGENT = "User-Agent";
    /**
     * RELEASE: Chrome 2021-03-13. Continuously update to latest version.
     * Some sites don't return full data unless the user agent is set to a valid browser.
     */
    public static final String USER_AGENT_VALUE =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
            + " AppleWebKit/537.36 (KHTML, like Gecko)"
            + " Chrome/88.0.4324.190 Safari/537.36";


    /** HTTP Response Header. */
    public static final String LOCATION = "location";

    private HttpConstants() {
    }
}
