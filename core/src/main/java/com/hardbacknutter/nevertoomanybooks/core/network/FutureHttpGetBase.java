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
import androidx.annotation.StringRes;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;

/**
 * @param <T> the type of the return value for the request
 */
public class FutureHttpGetBase<T>
        extends FutureHttpBase<T> {

    private static final String TAG = "FutureHttpHead";

    /**
     * Private constructor.
     *
     * @param siteResId string resource for the site name
     */
    public FutureHttpGetBase(@StringRes final int siteResId) {
        super(siteResId);
    }


    /**
     * Perform the actual opening of the connection.
     *
     * @param request to connect
     *
     * @throws IOException      on generic/other IO failures
     * @throws NetworkException on fatal error / giving up
     */
    @SuppressWarnings({"OverlyBroadThrowsClause", "OverlyBroadCatchBlock"})
    protected void connect(@NonNull final HttpURLConnection request)
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
                    LoggerFactory.getLogger().d(TAG, "open", "retry=" + retry,
                                                "url=`" + request.getURL() + '`', "e=" + e);
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
                // ignore
            }
        }

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger()
                         .d(TAG, "open|giving up", "url=`" + request.getURL() + '`');
        }
        throw new NetworkException("Giving up");
    }
}
