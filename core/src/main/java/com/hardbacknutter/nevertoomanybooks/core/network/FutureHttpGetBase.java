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
import androidx.annotation.StringRes;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;

import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * @param <T> the type of the return value for the request
 */
public class FutureHttpGetBase<T>
        extends FutureHttpBase<T> {

    private static final String TAG = "FutureHttpHead";

    private static final int MAX_404_REDIRECTS = 5;

    private boolean enable404Redirect;
    private int redirect404Count;

    /**
     * Private constructor.
     *
     * @param siteResId string resource for the site name
     */
    FutureHttpGetBase(@StringRes final int siteResId) {
        super(siteResId);
    }

    /**
     * <a href="https://developer.android.com/reference/java/net/HttpURLConnection.html#response-handling">HttpURLConnection</a>
     * HttpURLConnection will follow up to five HTTP redirects. It will follow redirects
     * from one origin server to another. This implementation doesn't follow redirects
     * from HTTPS to HTTP or vice versa.
     * <p>
     * <strong>This does not always work, for some requests it responds with a 404</strong>: .
     * <p>
     * Example:
     * <br>connecting to: {@code https://covers.openlibrary.org/b/id/13414586-M.jpg?default=false}
     * <br>404 {@code https://archive.org/download/m_covers_0013/m_covers_0013_41.zip/0013414586-M.jpg}
     *
     * @param enable404Redirect flag
     */
    public void setEnable404Redirect(final boolean enable404Redirect) {
        this.enable404Redirect = enable404Redirect;
        redirect404Count = 0;
    }

    /**
     * Perform the actual opening of the connection.
     * <p>
     * If the site fails to connect, we will retry up to {@link #NR_OF_TRIES}.
     * This is always enabled.
     * <p>
     * If the site sends a redirect which Android (in it's mysterious ways...) interprets
     * as a {@code 404}, we will try to follow it manually up to {@link #MAX_404_REDIRECTS}.
     * To enable this, set {@link #setEnable404Redirect(boolean)} to {@code true}.
     * The default is {@code false}.
     *
     * @param initialRequest to connect; <strong>MUST</strong>> be discarded after
     *                       this method returns. Use the returned request instead.
     *
     * @return the request which was successful
     *
     * @throws IOException      on generic/other IO failures
     * @throws NetworkException on fatal error / giving up
     */
    @NonNull
    protected HttpURLConnection connect(@NonNull final HttpURLConnection initialRequest)
            throws IOException {

        int retry;
        // sanity check
        if (nrOfTries > 0) {
            retry = nrOfTries;
        } else {
            retry = NR_OF_TRIES;
        }

        while (retry > 0) {
            //noinspection OverlyBroadCatchBlock
            try {
                if (throttler != null) {
                    throttler.waitUntilRequestAllowed();
                }

                // Preserve for potential enable404Redirect
                String requestUrlStr = initialRequest.getURL().toString();

                if (isLoggingEnabled()) {
                    LoggerFactory.getLogger().d(TAG, "connect", "url=" + requestUrlStr);
                }

                HttpURLConnection req = initialRequest;
                // Initial try
                req.connect();

                redirect404Count = 0;
                while (enable404Redirect && redirect404Count < MAX_404_REDIRECTS
                       && req.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {

                    final URL responseUrl = req.getURL();
                    final String responseUrlStr = responseUrl.toString();

                    if (requestUrlStr.equals(responseUrlStr)) {
                        // request and response URL are the same, it's a genuine 404
                        this.enable404Redirect = false;
                    } else {
                        // follow the redirect
                        redirect404Count++;

                        req = createRequest(responseUrl, req.getRequestMethod(),
                                            req.getDoOutput());
                        // Preserve for potential retry
                        requestUrlStr = responseUrlStr;

                        if (isLoggingEnabled()) {
                            LoggerFactory.getLogger().d(
                                    TAG, "connect",
                                    "redirect404Count=" + redirect404Count,
                                    "url=" + requestUrlStr);
                        }
                        // Note we are NOT using the throttler here,
                        // Android was SUPPOSED to redirect immediately.
                        req.connect();
                    }
                }

                checkResponseCode(req);
                // all fine, we're connected
                return req;

            } catch (@NonNull final InterruptedIOException
                                    | FileNotFoundException
                                    | UnknownHostException e) {
                // These exceptions CAN be retried:
                // InterruptedIOException / SocketTimeoutException: connection timeout
                // UnknownHostException: DNS or other low-level network issue
                // FileNotFoundException: seen on some sites. A retry and the site was ok.
                if (isLoggingEnabled()) {
                    LoggerFactory.getLogger().d(TAG, "connect", "retry=" + retry,
                                                "url=`" + initialRequest.getURL() + '`', "e=" + e);
                }

                retry--;
                if (retry == 0) {
                    initialRequest.disconnect();
                    throw e;
                }
            }

            try {
                Thread.sleep(RETRY_AFTER_MS);
            } catch (@NonNull final InterruptedException ignore) {
                // ignore
            }
        }

        final String message = "Giving up|url=`" + initialRequest.getURL() + '`';
        if (isLoggingEnabled()) {
            LoggerFactory.getLogger().d(TAG, message);
        }
        throw new NetworkException(message);
    }
}
