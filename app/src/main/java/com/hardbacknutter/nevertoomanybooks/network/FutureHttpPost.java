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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;

public class FutureHttpPost<T> {

    @StringRes
    private final int mSiteResId;
    private final int mTimeoutInMs;
    @Nullable
    private String mContentType;
    @Nullable
    private String mAuthHeader;
    @Nullable
    private SSLContext mSslContext;
    @Nullable
    private Future<T> mFuture;
    @Nullable
    private Throttler mThrottler;

    public FutureHttpPost(@StringRes final int siteResId,
                          final int timeoutInMs) {
        mSiteResId = siteResId;
        mTimeoutInMs = timeoutInMs;
    }

    @NonNull
    public FutureHttpPost<T> setThrottler(@Nullable final Throttler throttler) {
        mThrottler = throttler;
        return this;
    }

    @NonNull
    public FutureHttpPost<T> setContentType(@Nullable final String contentType) {
        mContentType = contentType;
        return this;
    }

    @NonNull
    public FutureHttpPost<T> setAuthHeader(@Nullable final String authHeader) {
        mAuthHeader = authHeader;
        return this;
    }

    @NonNull
    public FutureHttpPost<T> setSslContext(@Nullable final SSLContext sslContext) {
        mSslContext = sslContext;
        return this;
    }

    @Nullable
    public T post(@NonNull final String url,
                  @NonNull final String postBody,
                  @Nullable final Function<BufferedInputStream, T> processResponse)
            throws CancellationException,
                   SocketTimeoutException,
                   IOException {

        if (mThrottler != null) {
            mThrottler.waitUntilRequestAllowed();
        }

        try {
            mFuture = ASyncExecutor.SERVICE.submit(() -> {
                HttpURLConnection request = null;
                try {
                    request = (HttpURLConnection) new URL(url).openConnection();
                    request.setRequestMethod(HttpUtils.POST);
                    request.setDoOutput(true);

                    if (mContentType != null) {
                        request.setRequestProperty(HttpUtils.CONTENT_TYPE, mContentType);
                    }
                    if (mAuthHeader != null) {
                        request.setRequestProperty(HttpUtils.AUTHORIZATION, mAuthHeader);
                    }
                    if (mSslContext != null) {
                        ((HttpsURLConnection) request).setSSLSocketFactory(
                                mSslContext.getSocketFactory());
                    }

                    try (OutputStream os = request.getOutputStream();
                         Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                         Writer writer = new BufferedWriter(osw)) {
                        writer.write(postBody);
                        writer.flush();
                    }

                    HttpUtils.checkResponseCode(request, mSiteResId);

                    if (processResponse != null) {
                        try (InputStream is = request.getInputStream();
                             BufferedInputStream bis = new BufferedInputStream(is)) {
                            return processResponse.apply(bis);
                        }
                    }
                } finally {
                    if (request != null) {
                        request.disconnect();
                    }
                }

                return null;
            });

            return mFuture.get(mTimeoutInMs, TimeUnit.MILLISECONDS);

        } catch (@NonNull final ExecutionException e) {
            final Throwable cause = e.getCause();

            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof UncheckedIOException) {
                //noinspection ConstantConditions
                throw (IOException) cause.getCause();
            }
            throw new IOException(cause);

        } catch (@NonNull final RejectedExecutionException | InterruptedException e) {
            throw new IOException(e);

        } catch (@NonNull final TimeoutException e) {
            // re-throw as if it's coming from the network call.
            throw new SocketTimeoutException(e.getMessage());

        } finally {
            mFuture = null;
        }
    }

    public void cancel() {
        synchronized (this) {
            if (mFuture != null) {
                mFuture.cancel(true);
            }
        }
    }
}
