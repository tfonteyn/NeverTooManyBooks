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
package com.hardbacknutter.nevertoomanybooks.network;

import android.util.Base64;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public final class HttpUtils {

    /** HTTP Request method. */
    public static final String POST = "POST";

    /** HTTP authentication header. */
    static final String AUTHORIZATION = "Authorization";

    /** HTTP Request Header. */
    static final String CONNECTION = "Connection";
    static final String CONNECTION_CLOSE = "close";

    /** HTTP Request Header. */
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    public static final String CONTENT_TYPE_FORM_URL_ENCODED =
            "application/x-www-form-urlencoded; charset=UTF-8";

    /** HTTP Request Header. */
    static final String USER_AGENT = "User-Agent";
    /**
     * RELEASE: Chrome 2021-05-01. Continuously update to latest version.
     * Some sites don't return full data unless the user agent is set to a valid browser.
     */
    static final String USER_AGENT_VALUE =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
            + " AppleWebKit/537.36 (KHTML, like Gecko)"
            + " Chrome/90.0.4430.93 Safari/537.36";


    /** HTTP Response Header. */
    static final String LOCATION = "location";

    private HttpUtils() {
    }

    @AnyThread
    @NonNull
    public static String createBasicAuthHeader(@NonNull final String username,
                                               @NonNull final String password) {
        return "Basic " + Base64.encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8), 0);
    }

    /**
     * If already connected, simply check the response code.
     * Otherwise implicitly connect by getting the response code.
     * <p>
     * {@link TerminatorConnection} will call this internally.
     *
     * @param request   to check
     * @param siteResId site identifier
     *
     * @throws IOException               on connect
     * @throws HttpUnauthorizedException 401: Unauthorized.
     * @throws HttpNotFoundException     404: Not Found.
     * @throws SocketTimeoutException    408: Request Time-Out.
     * @throws HttpStatusException       on any other HTTP failures
     */
    @WorkerThread
    public static void checkResponseCode(@NonNull final HttpURLConnection request,
                                         @StringRes final int siteResId)
            throws IOException,
                   HttpUnauthorizedException,
                   HttpNotFoundException,
                   SocketTimeoutException,
                   HttpStatusException {

        final int responseCode = request.getResponseCode();

        if (responseCode < 400) {
            return;
        }

        switch (responseCode) {
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                throw new HttpUnauthorizedException(siteResId,
                                                    request.getResponseMessage(),
                                                    request.getURL());

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new HttpNotFoundException(siteResId,
                                                request.getResponseMessage(),
                                                request.getURL());

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                // for easier reporting issues to the user, map a 408 to an STE
                throw new SocketTimeoutException("408 " + request.getResponseMessage());

            default:
                throw new HttpStatusException(siteResId,
                                              responseCode,
                                              request.getResponseMessage(),
                                              request.getURL());
        }
    }

}
