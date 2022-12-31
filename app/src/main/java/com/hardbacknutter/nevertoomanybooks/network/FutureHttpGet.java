/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public final class FutureHttpGet<T>
        extends FutureHttpBase<T> {

    private static final String TAG = "FutureHttpGet";
    @NonNull
    private final String command;

    /**
     * Constructor.
     *
     * @param siteResId string resource for the site name
     */
    private FutureHttpGet(@StringRes final int siteResId,
                          @NonNull final String command) {
        super(siteResId);
        this.command = command;
    }

    @NonNull
    public static <T> FutureHttpGet<T> createGet(@StringRes final int siteResId) {
        return new FutureHttpGet<>(siteResId, "GET");
    }

    @NonNull
    public static <T> FutureHttpGet<T> createHead(@StringRes final int siteResId) {
        return new FutureHttpGet<>(siteResId, "HEAD");
    }

    /**
     * Send the GET/HEAD.
     *
     * @param url               to use
     * @param responseProcessor which will receive the response InputStream
     *
     * @return the processed response
     */
    @NonNull
    public T get(@NonNull final String url,
                 @NonNull final Function<HttpURLConnection, T> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {

        return Objects.requireNonNull(execute(url, command, false, request -> {
            try {
                connect(request);
                return responseProcessor.apply(request);

            } catch (@NonNull final IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }

    /**
     * Perform the actual opening of the connection.
     *
     * @throws IOException on generic/other IO failures
     */
    private void connect(@NonNull final HttpURLConnection request)
            throws IOException {

        // If the site fails to connect, we retry.
        int retry;
        // sanity check
        if (nrOfTries > 0) {
            retry = nrOfTries;
        } else {
            retry = NR_OF_TRIES;
        }

        while (retry > 0) {
            try {
                if (throttler != null) {
                    throttler.waitUntilRequestAllowed();
                }

                request.connect();
                checkResponseCode(request);
                // all fine, we're connected
                return;

                // these exceptions CAN be retried
            } catch (@NonNull final InterruptedIOException
                                    | FileNotFoundException
                                    | UnknownHostException e) {
                // InterruptedIOException / SocketTimeoutException: connection timeout
                // UnknownHostException: DNS or other low-level network issue
                // FileNotFoundException: seen on some sites. A retry and the site was ok.
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "open"
                               + "|retry=" + retry
                               + "|url=`" + request.getURL() + '`'
                               + "|e=" + e);
                }

                retry--;
                if (retry == 0) {
                    request.disconnect();
                    throw e;
                }
            }

            try {
                Thread.sleep(RETRY_AFTER_MS);
            } catch (@NonNull final InterruptedException ignore) {
            }
        }

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "open|giving up|url=`" + request.getURL() + '`');
        }
        throw new NetworkException("Giving up");
    }
}
