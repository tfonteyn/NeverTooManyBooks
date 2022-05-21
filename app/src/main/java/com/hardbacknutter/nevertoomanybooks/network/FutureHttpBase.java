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

import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UncheckedSAXException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UncheckedStorageException;

public abstract class FutureHttpBase<T> {

    /** The default number of times we try to connect; i.e. one RETRY. */
    static final int NR_OF_TRIES = 2;
    /**
     * Milliseconds to wait between retries. This is in ADDITION to the Throttler.
     * Reminder: not all sites have/need a throttler.
     */
    static final int RETRY_AFTER_MS = 1_000;

    private static final String TAG = "FutureHttpBase";

    /** timeout for opening a connection to a website. */
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** timeout for requests to website. */
    private static final int READ_TIMEOUT_MS = 10_000;

    @StringRes
    private final int mSiteResId;

    private final Map<String, String> mRequestProperties = new HashMap<>();

    /** see {@link #setRetryCount(int)}. */
    int mNrOfTries = NR_OF_TRIES;

    @Nullable
    Throttler mThrottler;

    @Nullable
    private Future<T> mFuture;
    @Nullable
    private SSLContext mSslContext;
    @Nullable
    private Boolean mFollowRedirects;
    /** -1: use the static default. */
    private int mConnectTimeoutInMs = -1;
    /** -1: use the static default. */
    private int mReadTimeoutInMs = -1;

    FutureHttpBase(@StringRes final int siteResId) {
        mSiteResId = siteResId;
    }

    public static void dumpSSLException(@NonNull final HttpsURLConnection request,
                                        @NonNull final SSLException e) {
        try {
            Logger.w("dumpSSLException", request.getURL().toString());
            final Certificate[] serverCertificates = request.getServerCertificates();
            if (serverCertificates != null && serverCertificates.length > 0) {
                for (final Certificate c : serverCertificates) {
                    Logger.w("dumpSSLException", c.toString());
                }
            }
        } catch (@NonNull final Exception ex) {
            Logger.d("dumpSSLException", ex, "");
        }

    }

    /**
     * If already connected, simply check the response code.
     * Otherwise implicitly connect by getting the response code.
     *
     * @param request to check
     *
     * @throws IOException               on connect
     * @throws HttpUnauthorizedException 401: Unauthorized.
     * @throws HttpNotFoundException     404: Not Found.
     * @throws SocketTimeoutException    408: Request Time-Out.
     * @throws HttpStatusException       on any other HTTP failures
     */
    @WorkerThread
    void checkResponseCode(@NonNull final HttpURLConnection request)
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
                throw new HttpUnauthorizedException(mSiteResId,
                                                    request.getResponseMessage(),
                                                    request.getURL());

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new HttpNotFoundException(mSiteResId,
                                                request.getResponseMessage(),
                                                request.getURL());

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                // for easier reporting issues to the user, map a 408 to an STE
                throw new SocketTimeoutException("408 " + request.getResponseMessage());

            default:
                throw new HttpStatusException(mSiteResId,
                                              responseCode,
                                              request.getResponseMessage(),
                                              request.getURL());
        }
    }

    /**
     * Set the optional connect-timeout.
     *
     * @param timeoutInMs in millis, use {@code 0} for infinite timeout
     */
    @NonNull
    public FutureHttpBase<T> setConnectTimeout(@IntRange(from = 0) final int timeoutInMs) {
        mConnectTimeoutInMs = timeoutInMs;
        return this;
    }

    /**
     * Set the optional read-timeout.
     *
     * @param timeoutInMs in millis, use {@code 0} for infinite timeout
     */
    @NonNull
    public FutureHttpBase<T> setReadTimeout(@IntRange(from = 0) final int timeoutInMs) {
        mReadTimeoutInMs = timeoutInMs;
        return this;
    }

    /**
     * Set a throttler to obey site usage rules.
     *
     * @param throttler (optional) to use
     */
    @NonNull
    public FutureHttpBase<T> setThrottler(@Nullable final Throttler throttler) {
        mThrottler = throttler;
        return this;
    }

    @NonNull
    public FutureHttpBase<T> setInstanceFollowRedirects(final boolean followRedirects) {
        mFollowRedirects = followRedirects;
        return this;
    }

    /**
     * Override the default retry count {@link #NR_OF_TRIES}.
     *
     * @param retryCount to use, should be {@code 0} for no retries.
     */
    @NonNull
    public FutureHttpBase<T> setRetryCount(@IntRange(from = 0) final int retryCount) {
        mNrOfTries = retryCount + 1;
        return this;
    }

    /**
     * For secure connections.
     *
     * @param sslContext (optional) SSL context to use instead of the system one.
     */
    @NonNull
    public FutureHttpBase<T> setSSLContext(@Nullable final SSLContext sslContext) {
        mSslContext = sslContext;
        return this;
    }

    @NonNull
    public FutureHttpBase<T> setRequestProperty(@NonNull final String key,
                                                @Nullable final String value) {
        if (value != null) {
            mRequestProperties.put(key, value);
        } else {
            mRequestProperties.remove(key);
        }
        return this;
    }

    private int getFutureTimeout() {
        return mConnectTimeoutInMs + mReadTimeoutInMs + 10;
    }

    @Nullable
    T execute(@NonNull final String url,
              @NonNull final String method,
              final boolean doOutput,
              @NonNull final Function<HttpURLConnection, T> action)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {
        try {
            mFuture = ASyncExecutor.SERVICE.submit(() -> {
                HttpURLConnection request = null;
                try {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                        Log.d(TAG, "url=\"" + url + '\"');
                    }

                    request = (HttpURLConnection) new URL(url).openConnection();
                    request.setRequestMethod(method);
                    request.setDoOutput(doOutput);

                    // Don't trust the caches; they have proven to be cumbersome.
                    request.setUseCaches(false);

                    if (mFollowRedirects != null) {
                        request.setInstanceFollowRedirects(mFollowRedirects);
                    }

                    for (final Map.Entry<String, String> entry : mRequestProperties.entrySet()) {
                        request.setRequestProperty(entry.getKey(), entry.getValue());
                    }

                    if (mConnectTimeoutInMs >= 0) {
                        request.setConnectTimeout(mConnectTimeoutInMs);
                    } else {
                        request.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    }

                    if (mReadTimeoutInMs >= 0) {
                        request.setReadTimeout(mReadTimeoutInMs);
                    } else {
                        request.setReadTimeout(READ_TIMEOUT_MS);
                    }

                    if (mSslContext != null) {
                        ((HttpsURLConnection) request).setSSLSocketFactory(
                                mSslContext.getSocketFactory());
                    }

                    // The request is now ready to be connected/used,
                    // pass control to the specific method
                    return action.apply(request);

                } finally {
                    if (request != null) {
                        request.disconnect();
                    }
                }
            });
            return mFuture.get(getFutureTimeout(), TimeUnit.MILLISECONDS);

        } catch (@NonNull final ExecutionException e) {
            final Throwable cause = e.getCause();

            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof UncheckedStorageException) {
                //noinspection ConstantConditions
                throw (StorageException) cause.getCause();
            }
            if (cause instanceof UncheckedIOException) {
                //noinspection ConstantConditions
                throw (IOException) cause.getCause();
            }
            if (cause instanceof UncheckedSAXException) {
                final SAXException saxException = ((UncheckedSAXException) cause).getCause();
                // unwrap SAXException using getException() !
                final Exception saxCause = Objects.requireNonNull(saxException).getException();
                if (saxCause instanceof IOException) {
                    throw (IOException) saxCause;
                }
                if (saxCause instanceof StorageException) {
                    throw (StorageException) saxCause;
                }
                throw new IOException(saxCause);
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
