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

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.util.logger.LoggerFactory;

public class FutureHttpPost<R>
        extends FutureHttpBase<R> {

    private static final String TAG = "FutureHttpPost";

    /**
     * Constructor.
     *
     * @param siteResId string resource for the site name
     */
    public FutureHttpPost(@StringRes final int siteResId) {
        super(siteResId);
    }

    /**
     * Send the POST.
     *
     * @param urlStr            to use
     * @param postBody          to send
     * @param responseProcessor which will receive the response InputStream
     *
     * @return the processed response; can be {@code null} if there was no response body.
     *
     * @throws CancellationException  if the user cancelled us
     * @throws SocketTimeoutException if the timeout expires before
     *                                the connection can be established
     * @throws IOException            on generic/other IO failures
     * @throws StorageException       The covers directory is not available
     */
    @Nullable
    public R post(@NonNull final String urlStr,
                  @NonNull final String postBody,
                  @Nullable final ActionFunction<InputStream, R> responseProcessor)
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
                        LoggerFactory.getLogger().d(TAG, "post|createRequest");
                    }
                    request = createRequest(url, POST);

                    waitUntilRequestAllowed();
                    try (OutputStream os = request.getOutputStream();
                         Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                         Writer writer = new BufferedWriter(osw)) {
                        writer.write(postBody);
                        writer.flush();
                    }

                    checkResponseCode(request);

                    if (responseProcessor != null) {
                        try (InputStream is = request.getInputStream();
                             BufferedInputStream bis = new BufferedInputStream(is)) {
                            if (HttpConstants.isZipped(request)) {
                                try (GZIPInputStream gzs = new GZIPInputStream(bis)) {
                                    return responseProcessor.apply(gzs);
                                }
                            } else {
                                return responseProcessor.apply(bis);
                            }
                        }
                    }
                    //noinspection ReturnOfNull
                    return null;
                } finally {
                    if (request != null) {
                        if (isLoggingEnabled()) {
                            LoggerFactory.getLogger().d(TAG, "post|disconnect");
                        }
                        request.disconnect();
                    }
                }
            });
            return futureHttp.get(getFutureTimeout(), TimeUnit.MILLISECONDS);

        } catch (@NonNull final ExecutionException e) {
            if (isLoggingEnabled()) {
                LoggerFactory.getLogger().d(TAG, "post: " + e);
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
}
