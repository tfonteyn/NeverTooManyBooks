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
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.IOException;
import java.net.URL;

/**
 * Signals that a HTTP request resulted in a not OK HTTP response.
 */
public class HttpStatusException
        extends IOException {

    private static final long serialVersionUID = 7064030911654231924L;

    private final int statusCode;
    @NonNull
    private final String statusMessage;

    @Nullable
    private final URL url;

    /** The site that caused the issue. */
    @StringRes
    private final int siteResId;

    /**
     * Constructor.
     *
     * @param siteResId     the site string res; which will be embedded in a default user message
     * @param statusCode    the original status code from the HTTP request
     * @param statusMessage the original status message from the HTTP request
     * @param url           (optional) The full url, for debugging
     */
    public HttpStatusException(@StringRes final int siteResId,
                        final int statusCode,
                        @NonNull final String statusMessage,
                        @Nullable final URL url) {
        super(statusMessage);
        this.siteResId = siteResId;
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.url = url;
    }

    @StringRes
    public int getSiteResId() {
        return siteResId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @NonNull
    public String getStatusMessage() {
        return statusMessage;
    }

    @Nullable
    public URL getUrl() {
        return url;
    }
}
