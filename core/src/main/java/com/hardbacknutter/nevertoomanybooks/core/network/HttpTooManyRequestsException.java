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

package com.hardbacknutter.nevertoomanybooks.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.net.URL;

public class HttpTooManyRequestsException
        extends HttpStatusException {

    /**
     * HTTP Status-Code 400: Too Many Requests.
     */
    public static final int HTTP_TOO_MANY_REQUESTS = 429;

    private static final long serialVersionUID = -3443703752368041392L;
    /**
     * Either a positive integer with the number of seconds, or a String with a
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Date">Date</a>.
     */
    @Nullable
    private final String retryAfter;

    /**
     * Constructor.
     *
     * @param siteResId     the site string res; which will be embedded in a default user message
     * @param retryAfter    (optional) the content of the "Retry-After" header
     * @param statusMessage the original status message from the HTTP request
     * @param url           (optional) The full url, for debugging
     * @param location      (optional) the content of the "Location" header
     */
    public HttpTooManyRequestsException(@StringRes final int siteResId,
                                        @Nullable final String retryAfter,
                                        @NonNull final String statusMessage,
                                        @Nullable final URL url,
                                        @Nullable final String location) {
        super(siteResId, HTTP_TOO_MANY_REQUESTS, statusMessage, url, location);
        this.retryAfter = retryAfter;
    }

    /**
     * Get the raw "Retry-After" header.
     *
     * @return header
     */
    @Nullable
    String getRetryAfter() {
        return retryAfter;
    }
}
