/*
 * @Copyright 2018-2025 HardBackNutter
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * The base class for a {@code HEAD} and {@code GET} request.
 *
 * @param <R> the type of the return value for the request
 */
public class FutureHttpGetBase<R>
        extends FutureHttpBase<R> {

    private static final String TAG = "FutureHttpGetBase";
    private static final String LOG_ATTEMPTS_LEFT = "attemptsLeft=";
    private static final String LOG_REQUEST_URL = "requestUrlStr=";
    private static final String LOG_REDIRECT_COUNT = "redirectCount=";

    private static final int MAX_REDIRECTS = 5;

    private boolean enable404Redirect;
    private int redirectCount;

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
        redirectCount = 0;
    }

    /**
     * Create a request and execute it using a {@link Future} so we can use a timeout.
     *
     * @param urlStr to connect to
     * @param method {@code GET}, {@code POST}, {@code HEAD}
     * @param action callback to give the request to
     *
     * @return result of the callback method
     *
     * @throws CancellationException  if the user cancelled us
     * @throws SocketTimeoutException if the timeout expires before
     *                                the connection can be established
     * @throws IOException            on generic/other IO failures
     * @throws StorageException       The covers directory is not available
     */
    @Nullable
    R execute(@NonNull final String urlStr,
              @NonNull final String method,
              @NonNull final ActionFunction<HttpURLConnection, R> action)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {
        try {
            futureHttp = ASyncExecutor.SERVICE.submit(() -> {
                HttpURLConnection request = null;
                try {
                    final URL url = new URL(urlStr);
                    if (isLoggingEnabled()) {
                        LoggerFactory.getLogger().d(TAG, "execute|createRequest");
                    }
                    request = connect(url, method);
                    return action.apply(request);
                } finally {
                    if (request != null) {
                        if (isLoggingEnabled()) {
                            LoggerFactory.getLogger().d(TAG, "execute|disconnect");
                        }
                        request.disconnect();
                    }
                }
            });
            return futureHttp.get(getFutureTimeout(), TimeUnit.MILLISECONDS);

        } catch (@NonNull final ExecutionException e) {
            if (isLoggingEnabled()) {
                LoggerFactory.getLogger().d(TAG, "execute: " + e);
            }
            unpackExecutionException(e);
            return null;

        } catch (@NonNull final RejectedExecutionException | InterruptedException e) {
            throw new IOException(e);

        } catch (@NonNull final TimeoutException e) {
            // re-throw as if it's coming from the network call.
            throw new SocketTimeoutException(e.getMessage());

        } finally {
            futureHttp = null;
        }
    }

    /**
     * Perform the actual opening of the connection.
     * <p>
     * If the site fails to connect, we will attempt up to {@link #NR_OF_TRIES}.
     * This is always enabled.
     * <p>
     * If the site sends a redirect which Android (in it's mysterious ways...) interprets
     * as a {@code 404}, we will try to follow it manually up to {@link #MAX_REDIRECTS}.
     * To enable this, set {@link #setEnable404Redirect(boolean)} to {@code true}.
     * The default is {@code false}.
     *
     * @param url    to connect to
     * @param method one of {@link #GET} or {@link #HEAD}.
     *
     * @return the request which was successful
     *
     * @throws IOException      on generic/other IO failures
     * @throws NetworkException on fatal error / giving up
     */
    @NonNull
    private HttpURLConnection connect(@NonNull final URL url,
                                      @NonNull final String method)
            throws IOException {

        int attemptsLeft = getRetryCount();

        final HttpURLConnection initialRequest = createRequest(url, method);
        // Preserve for a potential manual redirect
        String requestUrlStr = initialRequest.getURL().toString();

        HttpURLConnection req = initialRequest;

        while (attemptsLeft > 0) {
            if (isLoggingEnabled()) {
                LoggerFactory.getLogger().d(TAG, "connect",
                                            LOG_ATTEMPTS_LEFT + attemptsLeft,
                                            LOG_REQUEST_URL + requestUrlStr);
            }

            //noinspection OverlyBroadCatchBlock
            try {
                waitUntilRequestAllowed();
                req.connect();

                redirectCount = 0;
                while (enable404Redirect && redirectCount < MAX_REDIRECTS
                       && req.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {

                    final URL responseUrl = req.getURL();
                    final String responseUrlStr = responseUrl.toString();

                    if (isLoggingEnabled()) {
                        LoggerFactory.getLogger()
                                     .d(TAG, "connect|response",
                                        LOG_ATTEMPTS_LEFT + attemptsLeft,
                                        LOG_REQUEST_URL + requestUrlStr,
                                        LOG_REDIRECT_COUNT + redirectCount,
                                        "responseCode=" + req.getResponseCode(),
                                        "responseUrlStr=" + responseUrlStr);
                    }

                    if (requestUrlStr.equals(responseUrlStr)) {
                        // Request and response URL are the same, it's a genuine 404
                        // Force-quit the loop.
                        redirectCount = MAX_REDIRECTS;
                    } else {
                        // follow the redirect
                        redirectCount++;
                        req.disconnect();
                        req = createRequest(responseUrl, req.getRequestMethod());
                        // Preserve for potential retry
                        requestUrlStr = responseUrlStr;

                        if (isLoggingEnabled()) {
                            LoggerFactory.getLogger()
                                         .d(TAG, "connect|redirect",
                                            LOG_ATTEMPTS_LEFT + attemptsLeft,
                                            LOG_REDIRECT_COUNT + redirectCount,
                                            "new requestUrlStr=" + requestUrlStr);
                        }
                        // Note we are NOT using the throttler here,
                        // Android was SUPPOSED to redirect immediately.
                        req.connect();
                    }
                }

                checkResponseCode(req);
                // all fine, we're connected
                return req;

            } catch (@NonNull final HttpForbiddenException e) {
                // 2024-11-07: There are ongoing issues with OpenLibrary fetching cover images.
                // The issues seem to be limited to running in AndroidTest
                // (i.e. the ParseTest class) and do not seem to happen when doing a manual
                // search in the emulator or on a real device.
                //
                // This "catch" code is mainly meant for those test cases:
                // OpenLibrary is returning a 403 upon the first request to the cover api url:
                //
                // example:  ISBN: 9780141346830 => OL28508809M
                // https://openlibrary.org/books/OL28508809M.json
                // contains:
                //   "covers": [
                //    14615097,
                //    14615096,
                //    13011694
                //  ],
                // and using the API:
                // https://openlibrary.org/dev/docs/api/covers
                // we access:
                // https://covers.openlibrary.org/b/id/14615097-L.jpg?default=false
                // ==> IMMEDIATELY a 403....
                // but using that last url in a browser or with wget will return a 302
                if (isLoggingEnabled()) {
                    LoggerFactory.getLogger().e(TAG, e, "connect|disconnecting",
                                                "e.url=" + e.getUrl(),
                                                "e.location=" + e.getLocation());
                }

                req.disconnect();
                // Cannot recover from this, just quit
                throw e;

            } catch (@NonNull final InterruptedIOException
                                    | FileNotFoundException
                                    | UnknownHostException e) {
                // These exceptions CAN be retried:
                // InterruptedIOException / SocketTimeoutException: connection timeout
                // UnknownHostException: DNS or other low-level network issue
                // FileNotFoundException: seen on some sites. A retry and the site was ok.
                if (isLoggingEnabled()) {
                    LoggerFactory.getLogger()
                                 .e(TAG, e, "connect|recoverable error",
                                    LOG_ATTEMPTS_LEFT + attemptsLeft,
                                    "requestUrlStr=`" + requestUrlStr + '`');
                }

                attemptsLeft--;
                if (attemptsLeft == 0) {
                    if (isLoggingEnabled()) {
                        LoggerFactory.getLogger()
                                     .d(TAG, "connect|all attempts failed|disconnecting");
                    }
                    req.disconnect();
                    throw e;
                }
            }

            try {
                Thread.sleep(RETRY_AFTER_MS);
            } catch (@NonNull final InterruptedException ignore) {
                // ignore
            }
        }

        final String message = "Giving up|initialRequestUrl=`" + initialRequest.getURL() + '`';
        if (isLoggingEnabled()) {
            LoggerFactory.getLogger().d(TAG, message);
        }
        throw new NetworkException(message);
    }
}
