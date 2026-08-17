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

package com.hardbacknutter.nevertoomanybooks.searchengines;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;

import okhttp3.Request;

public class ImageRequestFactoryDefault
        implements RequestFactory {

    @NonNull
    private final String acceptLanguageHeader;

    /**
     * Constructor.
     *
     * @param acceptLanguageHeader to use
     */
    ImageRequestFactoryDefault(@NonNull final String acceptLanguageHeader) {
        this.acceptLanguageHeader = acceptLanguageHeader;
    }

    /**
     * Create a suitable {@code GET} {@link Request}.
     *
     * @param urlStr            to use
     * @param requestProperties (optional) extra headers to add/override

     *
     * @return new {@code GET} request instance
     *
     * @throws MalformedURLException on url errors
     */
    @NonNull
    public Request createRequest(@NonNull final String urlStr,
                                 @Nullable final Map<String, String> requestProperties)
            throws MalformedURLException {

        // Host: www.gstatic.com
        // User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:152.0) Gecko/20100101 Firefox/152.0
        // Accept: image/avif,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5
        // Accept-Language: en-GB,en;q=0.9,nl-BE;q=0.8,de-DE;q=0.7
        // Accept-Encoding: gzip, deflate, br, zstd
        // Referer: https://developer.android.com/
        // Sec-Fetch-Storage-Access: none
        // DNT: 1
        // Sec-GPC: 1
        // Sec-Fetch-Dest: image
        // Sec-Fetch-Mode: no-cors
        // Sec-Fetch-Site: cross-site
        // Connection: keep-alive

        // Host, Connection, Accept-Encoding are added by OkHttp
        final Request.Builder builder = new Request.Builder()
                .url(urlStr)
                .header(HttpConstants.USER_AGENT,
                        HttpConstants.USER_AGENT_FIREFOX)
                .header(HttpConstants.ACCEPT,
                        HttpConstants.ACCEPT_IMAGE)
                .header(HttpConstants.ACCEPT_LANGUAGE,
                        acceptLanguageHeader)

                .header(HttpConstants.SEC_FETCH_STORAGE_ACCESS,
                        HttpConstants.SEC_FETCH_STORAGE_ACCESS_NONE)

                .header(HttpConstants.DNT, "1")
                .header(HttpConstants.SEC_GPC, "1")

                //We want a generic image
                .header(HttpConstants.SEC_FETCH_DEST,
                        HttpConstants.SEC_FETCH_DEST_IMAGE)
                .header(HttpConstants.SEC_FETCH_MODE,
                        HttpConstants.SEC_FETCH_MODE_NO_CORS)
                // same site... might need to use SEC_FETCH_SITE_CROSS_SITE ?
                .header(HttpConstants.SEC_FETCH_SITE,
                        HttpConstants.SEC_FETCH_SITE_NONE);

        // add or override
        if (requestProperties != null) {
            requestProperties.forEach(builder::header);
        }

        return builder.build();
    }
}
