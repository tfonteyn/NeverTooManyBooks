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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.storage.UncheckedStorageException;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.xml.sax.SAXException;

public abstract class FutureHttpBase<R> {

    static final String HEAD = "HEAD";
    static final String GET = "GET";
    static final String POST = "POST";

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

    /** LinkedHashMap so the order we use is preserved. */
    private final Map<String, String> requestProperties = new LinkedHashMap<>();
    @Nullable
    Future<R> futureHttp;
    private int nrOfTries = NR_OF_TRIES;
    @Nullable
    private Throttler throttler;
    @Nullable
    private SSLContext sslContext;
    @Nullable
    private HostnameVerifier hostnameVerifier;
    @Nullable
    private Boolean followRedirects;
    /** -1: use the static default. */
    private int connectTimeoutInMs = -1;
    /** -1: use the static default. */
    private int readTimeoutInMs = -1;
    /** Log {@code GET} and {@code HEAD} related url,responseCode and redirects. */
    private boolean logHttpGetRequests;

    /**
     * Constructor.
     *
     * @param siteResId string resource for the site name
     */
    FutureHttpBase(@StringRes final int siteResId) {
        this.siteResId = siteResId;
    }

    /**
     * If already connected, simply check the response code.
     * Otherwise implicitly connect by getting the response code.
     *
     * @param request to check
     *
     * @throws IOException               on connect
     * @throws HttpUnauthorizedException 401: Unauthorized.
     * @throws HttpForbiddenException    403: Forbidden
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

        if (isLoggingEnabled()) {
            LoggerFactory.getLogger().d(TAG, "checkResponseCode",
                                        responseCode + " " + request.getURL().toString());
        }

        if (responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {
            return;
        }

        @Nullable
        final String location = request.getHeaderField(HttpConstants.RESPONSE_HEADER_LOCATION);

        switch (responseCode) {
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                throw new HttpUnauthorizedException(siteResId,
                                                    request.getResponseMessage(),
                                                    request.getURL(),
                                                    location);

            case HttpURLConnection.HTTP_FORBIDDEN:
                throw new HttpForbiddenException(siteResId,
                                                 request.getResponseMessage(),
                                                 request.getURL(),
                                                 location);

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new HttpNotFoundException(siteResId,
                                                request.getResponseMessage(),
                                                request.getURL(),
                                                location);

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                // for easier reporting issues to the user, map a 408 to an STE
                throw new SocketTimeoutException("408 " + request.getResponseMessage());

            default:
                throw new HttpStatusException(siteResId,
                                              responseCode,
                                              request.getResponseMessage(),
                                              request.getURL(),
                                              location);
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
    public FutureHttpBase<R> setConnectTimeout(@IntRange(from = 0) final int timeoutInMs) {
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
    public FutureHttpBase<R> setReadTimeout(@IntRange(from = 0) final int timeoutInMs) {
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
    public FutureHttpBase<R> setThrottler(@Nullable final Throttler throttler) {
        this.throttler = throttler;
        return this;
    }

    /**
     * Set whether redirects should be followed.
     * <p>
     * The default is unset, i.e. use the OS default.
     *
     * @param followRedirects flag
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public FutureHttpBase<R> setInstanceFollowRedirects(final boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    public int getRetryCount() {
        // sanity check
        return nrOfTries > 0 ? nrOfTries : NR_OF_TRIES;
    }

    /**
     * Override the default retry count {@link #NR_OF_TRIES}.
     *
     * @param retryCount to use, should be {@code 0} for no retries.
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public FutureHttpBase<R> setRetryCount(@IntRange(from = 0) final int retryCount) {
        nrOfTries = retryCount + 1;
        return this;
    }

    /**
     * For secure connections.
     *
     * @param sslContext (optional) SSL context to use instead of the system default.
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public FutureHttpBase<R> setSSLContext(@Nullable final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * For secure connections.
     *
     * @param verifier (optional) for custom checking of hostnames in for
     *                 example certificate handling with self-signed certificates.
     *                 {@code null} to use the system default.
     *
     * @return {@code this} (for chaining)
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public FutureHttpBase<R> setHostnameVerifier(@Nullable final HostnameVerifier verifier) {
        this.hostnameVerifier = verifier;
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public FutureHttpBase<R> enableLogging(final boolean enable) {
        this.logHttpGetRequests = enable;
        return this;
    }

    public boolean isLoggingEnabled() {
        return logHttpGetRequests;
    }

    /**
     * Add a connection request property.
     *
     * @param key   to set
     * @param value to set; use {@code null} to remove instead of add the property
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public FutureHttpBase<R> setRequestProperty(@NonNull final String key,
                                                @Nullable final String value) {
        if (value != null) {
            requestProperties.put(key, value);
        } else {
            requestProperties.remove(key);
        }
        return this;
    }

    int getFutureTimeout() {
        return connectTimeoutInMs + readTimeoutInMs + 10;
    }

    void waitUntilRequestAllowed() {
        if (throttler != null) {
            throttler.waitUntilRequestAllowed();
        }
    }

    /**
     * Create a new unconnected {@link HttpURLConnection}.
     *
     * @param url    to connect to
     * @param method one of {@link #GET} or {@link #HEAD}.
     *
     * @return request
     *
     * @throws IOException on generic/other IO failures
     */
    @NonNull
    HttpURLConnection createRequest(@NonNull final URL url,
                                    @NonNull final String method)
            throws IOException {

        final HttpURLConnection request = (HttpURLConnection) url.openConnection();

        request.setRequestMethod(method);
        request.setDoOutput(POST.equals(method));

        // Don't trust the caches; they have proven to be cumbersome.
        request.setUseCaches(false);

        if (followRedirects != null) {
            request.setInstanceFollowRedirects(followRedirects);
        }

        request.setRequestProperty(HttpConstants.HOST, url.getHost());
        request.setRequestProperty(HttpConstants.USER_AGENT,
                                   HttpConstants.BROWSER_USER_AGENT);

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
            final HttpsURLConnection con = (HttpsURLConnection) request;
            con.setSSLSocketFactory(sslContext.getSocketFactory());
            // the hostnameVerifier is normally only set from tests
            if (hostnameVerifier != null) {
                con.setHostnameVerifier(hostnameVerifier);
            }
        }

        return request;
    }

    void unpackExecutionException(@NonNull final ExecutionException e)
            throws StorageException, IOException {
        // TODO: maybe move away from this early interception? and let the ExecutionException
        //  go all the way up and decode it in ExMsg ?

        // TODO: in theory we no longer receive the Unchecked variants
        //  due to using ActionFunction

        final Throwable cause = e.getCause();

        if (cause instanceof UncheckedStorageException) {
            //noinspection DataFlowIssue
            throw (StorageException) cause.getCause();

        } else if (cause instanceof StorageException) {
            throw (StorageException) cause;

        } else if (cause instanceof UncheckedIOException) {
            //noinspection DataFlowIssue
            throw (IOException) cause.getCause();

        } else if (cause instanceof IOException) {
            throw (IOException) cause;

        } else if (cause instanceof UncheckedSAXException) {
            final SAXException saxException = Objects.requireNonNull(
                    ((UncheckedSAXException) cause).getCause());
            unpackSAXException(saxException);

        } else if (cause instanceof SAXException) {
            final SAXException saxException = (SAXException) cause;
            unpackSAXException(saxException);
        }

        // An unexpected exception, let the caller deal with it.
        throw new IOException(cause);
    }

    private void unpackSAXException(@NonNull final SAXException saxException)
            throws IOException, StorageException {
        // First try unwrapping with SAXException#getException() !
        Throwable saxCause = saxException.getException();
        if (saxCause == null) {
            // try the standard getCause() instead
            saxCause = saxException.getCause();
        }
        if (saxCause == null) {
            // We have an actual SAXException which is not wrapping anything.
            // This indicates a parser failure somewhere usually caused
            // by the server response not matching the expectations.
            // Wrap it into an IOException and let the caller deal with it.
            throw new IOException(saxException);
        }

        // The SAXException was caused by a wrapped exception. Unwrap if we can.
        if (saxCause instanceof IOException) {
            throw (IOException) saxCause;
        } else if (saxCause instanceof StorageException) {
            throw (StorageException) saxCause;
        }
        // Some other wrapped exception, re-wrap and let the caller deal with it.
        throw new IOException(saxCause);
    }

    /**
     * Request to cancel an ongoing http request.
     */
    public void cancel() {
        synchronized (this) {
            if (futureHttp != null) {
                futureHttp.cancel(true);
            }
        }
    }

    /**
     * Same as {@code java.util.function.Function} but with checked exceptions
     * thus avoiding packing/unpacking.
     *
     * @param <T> input
     *            Typically the actual {@link HttpURLConnection}
     *            or a preprocessed {@link java.io.InputStream} from that connection
     * @param <R> output
     */
    @FunctionalInterface
    public interface ActionFunction<T, R> {
        /**
         * Applies this function to the given argument.
         *
         * @param t the function argument
         *
         * @return the function result
         *
         * @throws IOException      on generic/other IO failures
         * @throws StorageException The covers directory is not available
         * @throws SAXException     on parser problems if a SAX parser was used
         */
        R apply(T t)
                throws IOException,
                       StorageException,
                       SAXException;
    }
}
