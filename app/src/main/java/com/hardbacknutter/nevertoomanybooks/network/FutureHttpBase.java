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

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UncheckedSAXException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UncheckedStorageException;

import org.xml.sax.SAXException;

@SuppressWarnings({"UnusedReturnValue", "WeakerAccess", "unused"})
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
    private final int siteResId;

    private final Map<String, String> requestProperties = new HashMap<>();

    /** see {@link #setRetryCount(int)}. */
    int nrOfTries = NR_OF_TRIES;

    @Nullable
    Throttler throttler;

    @Nullable
    private Future<T> futureHttp;
    @Nullable
    private SSLContext sslContext;
    @Nullable
    private Boolean followRedirects;
    /** -1: use the static default. */
    private int connectTimeoutInMs = -1;
    /** -1: use the static default. */
    private int readTimeoutInMs = -1;

    /**
     * Constructor.
     *
     * @param siteResId string resource for the site name
     */
    FutureHttpBase(@StringRes final int siteResId) {
        this.siteResId = siteResId;

        // Set the default user agent.
        // Potentially overridden by calling #setRequestProperty.
        requestProperties.put(HttpUtils.USER_AGENT, HttpUtils.USER_AGENT_VALUE);
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
                throw new HttpUnauthorizedException(siteResId,
                                                    request.getResponseMessage(),
                                                    request.getURL());

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new HttpNotFoundException(siteResId,
                                                request.getResponseMessage(),
                                                request.getURL());

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                // for easier reporting issues to the user, map a 408 to an STE
                throw new SocketTimeoutException("408 " + request.getResponseMessage());

            default:
                throw new HttpStatusException(siteResId,
                                              responseCode,
                                              request.getResponseMessage(),
                                              request.getURL());
        }
    }

    /**
     * Set the optional connect-timeout.
     *
     * @param timeoutInMs in millis, use {@code 0} for infinite timeout
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FutureHttpBase<T> setConnectTimeout(@IntRange(from = 0) final int timeoutInMs) {
        connectTimeoutInMs = timeoutInMs;
        return this;
    }

    /**
     * Set the optional read-timeout.
     *
     * @param timeoutInMs in millis, use {@code 0} for infinite timeout
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FutureHttpBase<T> setReadTimeout(@IntRange(from = 0) final int timeoutInMs) {
        readTimeoutInMs = timeoutInMs;
        return this;
    }

    /**
     * Set a throttler to obey site usage rules.
     *
     * @param throttler (optional) to use
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FutureHttpBase<T> setThrottler(@Nullable final Throttler throttler) {
        this.throttler = throttler;
        return this;
    }

    @NonNull
    public FutureHttpBase<T> setInstanceFollowRedirects(final boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    /**
     * Override the default retry count {@link #NR_OF_TRIES}.
     *
     * @param retryCount to use, should be {@code 0} for no retries.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FutureHttpBase<T> setRetryCount(@IntRange(from = 0) final int retryCount) {
        nrOfTries = retryCount + 1;
        return this;
    }

    /**
     * For secure connections.
     *
     * @param sslContext (optional) SSL context to use instead of the system one.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FutureHttpBase<T> setSSLContext(@Nullable final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    @NonNull
    public FutureHttpBase<T> setRequestProperty(@NonNull final String key,
                                                @Nullable final String value) {
        if (value != null) {
            requestProperties.put(key, value);
        } else {
            requestProperties.remove(key);
        }
        return this;
    }

    private int getFutureTimeout() {
        return connectTimeoutInMs + readTimeoutInMs + 10;
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
            futureHttp = ASyncExecutor.SERVICE.submit(() -> {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                    Log.d(TAG, "url=\"" + url + '\"');
                }

                HttpURLConnection request = null;
                try {
                    request = (HttpURLConnection) new URL(url).openConnection();
                    request.setRequestMethod(method);
                    request.setDoOutput(doOutput);

                    // Don't trust the caches; they have proven to be cumbersome.
                    request.setUseCaches(false);

                    if (followRedirects != null) {
                        request.setInstanceFollowRedirects(followRedirects);
                    }

                    for (final Map.Entry<String, String> entry : requestProperties.entrySet()) {
                        request.setRequestProperty(entry.getKey(), entry.getValue());
                    }

                    if (connectTimeoutInMs >= 0) {
                        request.setConnectTimeout(connectTimeoutInMs);
                    } else {
                        request.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    }

                    if (readTimeoutInMs >= 0) {
                        request.setReadTimeout(readTimeoutInMs);
                    } else {
                        request.setReadTimeout(READ_TIMEOUT_MS);
                    }

                    if (sslContext != null) {
                        ((HttpsURLConnection) request).setSSLSocketFactory(
                                sslContext.getSocketFactory());
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
            return futureHttp.get(getFutureTimeout(), TimeUnit.MILLISECONDS);

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
            futureHttp = null;
        }
    }

    public void cancel() {
        synchronized (this) {
            if (futureHttp != null) {
                futureHttp.cancel(true);
            }
        }
    }
}
