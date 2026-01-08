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
package com.hardbacknutter.nevertoomanybooks.core.network;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.storage.UncheckedStorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.xml.sax.SAXException;

/**
 * {@code HEAD}, {@code GET} and {@code POST} support.
 *
 * @param <R> the type of the return value for the request
 */
public class FutureHttpImpl<R>
        implements FutureHttp<R> {

    private static final String HEAD = "HEAD";
    private static final String GET = "GET";
    private static final String POST = "POST";

    /** The default number of times we try to connect; i.e. one RETRY. */
    private static final int NR_OF_TRIES = 2;

    private static final String TAG = "FutureHttpImpl";
    private static final String LOG_ATTEMPTS_LEFT = "attemptsLeft=";
    private static final String LOG_REQUEST_URL = "requestUrlStr=";
    private static final String LOG_REDIRECT_COUNT = "redirectCount=";

    /** timeout for opening a connection to a website. */
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** timeout for requests to website. */
    private static final int READ_TIMEOUT_MS = 10_000;
    /** {@code GET}. */
    private static final int MAX_REDIRECTS = 5;

    /** InputStream buffer size. Used by {@code GET} and {@code POST}. */
    private static final int DEFAULT_BUFFER_SIZE = 8192;
    /**
     * Milliseconds to wait between retries. This is in ADDITION to the Throttler.
     * Reminder: not all sites have/need a throttler.
     */
    private static final int RETRY_AFTER_MS = 1_000;

    @StringRes
    private final int siteResId;

    /** LinkedHashMap so the order we use is preserved. */
    private final Map<String, String> requestProperties = new LinkedHashMap<>();

    /** InputStream buffer size. Used by {@code GET} and {@code POST}. */
    private int bufferSize = DEFAULT_BUFFER_SIZE;
    /** {@code GET}. */
    private boolean enable404Redirect;
    /** {@code GET}. */
    private int redirectCount;
    @Nullable
    private Future<R> futureHttp;
    /** {@code GET}. */
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

    public FutureHttpImpl(@StringRes final int siteResId) {
        this.siteResId = siteResId;
    }

    /**
     * Check if the response headers indicate the encoding is gzip.
     *
     * @param response connection to check
     *
     * @return {@code true} if the content-encoding was "gzip"
     */
    private static boolean isZipped(@NonNull final HttpURLConnection response) {
        return HttpConstants.ACCEPT_ENCODING_GZIP.equalsIgnoreCase(
                response.getHeaderField(HttpConstants.RESPONSE_HEADER_CONTENT_ENCODING));
    }

    /**
     * If already connected, simply check the response code.
     * Otherwise, implicitly connect by getting the response code.
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
    private void checkResponseCode(@NonNull final HttpURLConnection request)
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

        if (isLoggingEnabled()) {
            final String msg = request
                    .getHeaderFields()
                    .entrySet()
                    .stream()
                    .map(es -> "Response Header: " + es.getKey() + "="
                               + String.join("|", es.getValue()))
                    .collect(Collectors.joining("\n"));

            LoggerFactory.getLogger().d(TAG, "checkResponseCode", "\n" + msg);
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

    @NonNull
    @Override
    public FutureHttp<R> setConnectTimeout(@IntRange(from = 0) final int timeoutInMs) {
        connectTimeoutInMs = timeoutInMs;
        return this;
    }

    @NonNull
    @Override
    public FutureHttp<R> setReadTimeout(@IntRange(from = 0) final int timeoutInMs) {
        readTimeoutInMs = timeoutInMs;
        return this;
    }

    @NonNull
    @Override
    public FutureHttp<R> setThrottler(@Nullable final Throttler throttler) {
        this.throttler = throttler;
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    @Override
    public FutureHttp<R> setInstanceFollowRedirects(final boolean followRedirects) {
        this.followRedirects = followRedirects;
        return this;
    }

    @Override
    public void setEnable404Redirect(final boolean enable404Redirect) {
        this.enable404Redirect = enable404Redirect;
        redirectCount = 0;
    }

    @Override
    public void setBufferSize(final int bufferSize) {
        this.bufferSize = bufferSize;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    @Override
    public FutureHttp<R> setRetryCount(@IntRange(from = 0) final int retryCount) {
        nrOfTries = retryCount + 1;
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    @Override
    public FutureHttp<R> setSSLContext(@Nullable final SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    @Override
    public FutureHttp<R> setHostnameVerifier(@Nullable final HostnameVerifier verifier) {
        this.hostnameVerifier = verifier;
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    @Override
    public FutureHttp<R> enableLogging(final boolean enable) {
        this.logHttpGetRequests = enable;
        return this;
    }

    @Override
    public boolean isLoggingEnabled() {
        return logHttpGetRequests;
    }

    @NonNull
    @Override
    public FutureHttp<R> setRequestProperty(@NonNull final String key,
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

    private void waitUntilRequestAllowed() {
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
    private HttpURLConnection createRequest(@NonNull final URL url,
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

        requestProperties.forEach(request::setRequestProperty);

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

        if (isLoggingEnabled()) {
            final String msg = request
                    .getRequestProperties()
                    .entrySet()
                    .stream()
                    .map(es -> "Request Header: " + es.getKey() + "="
                               + String.join("|", es.getValue()))
                    .collect(Collectors.joining("\n"));

            LoggerFactory.getLogger().d(TAG, "createRequest", "\n" + msg);
        }

        return request;
    }

    private void unpackExecutionException(@NonNull final ExecutionException e)
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

    @NonNull
    @Override
    public R head(@NonNull final String url,
                  @NonNull final ActionFunction<HttpURLConnection, R> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {

        return Objects.requireNonNull(doGetExecute(url, HEAD, responseProcessor));
    }

    @NonNull
    @Override
    public R get(@NonNull final String url,
                 @NonNull final ResponseProcessor<InputStream, R> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {

        return Objects.requireNonNull(doGetExecute(url, GET, connection -> {
            try (BufferedInputStream bis = new BufferedInputStream(
                    connection.getInputStream(), bufferSize)) {
                if (isZipped(connection)) {
                    try (GZIPInputStream gzs = new GZIPInputStream(bis)) {
                        return responseProcessor.apply(connection, gzs);
                    }
                } else {
                    return responseProcessor.apply(connection, bis);
                }
            }
        }));
    }

    @NonNull
    @Override
    public R getAsString(@NonNull final String url,
                         @NonNull final ResponseProcessor<String, R> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {

        return Objects.requireNonNull(doGetExecute(url, GET, connection -> {

            try (InputStream is = connection.getInputStream()) {
                final String page;
                if (isZipped(connection)) {
                    try (GZIPInputStream gzs = new GZIPInputStream(is)) {
                        try (Reader isr = new InputStreamReader(gzs, StandardCharsets.UTF_8)) {
                            try (BufferedReader reader = new BufferedReader(isr, bufferSize)) {
                                page = reader.lines().collect(Collectors.joining());
                            }
                        }
                        return responseProcessor.apply(connection, page);
                    }
                } else {
                    try (Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        try (BufferedReader reader = new BufferedReader(isr, bufferSize)) {
                            page = reader.lines().collect(Collectors.joining());
                        }
                    }
                    return responseProcessor.apply(connection, page);
                }
            }
        }));
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
    private R doGetExecute(@NonNull final String urlStr,
                           @NonNull final String method,
                           @NonNull final ActionFunction<HttpURLConnection, R> action)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {
        try {
            futureHttp = ASyncExecutor.NETWORK.submit(() -> {
                HttpURLConnection request = null;
                try {
                    final URL url = new URL(urlStr);
                    if (isLoggingEnabled()) {
                        LoggerFactory.getLogger().d(TAG, "doGetExecute|doGetConnect");
                    }
                    request = doGetConnect(url, method);
                    return action.apply(request);
                } finally {
                    if (request != null) {
                        if (isLoggingEnabled()) {
                            LoggerFactory.getLogger().d(TAG, "doGetExecute|disconnect");
                        }
                        request.disconnect();
                    }
                }
            });
            return futureHttp.get(getFutureTimeout(), TimeUnit.MILLISECONDS);

        } catch (@NonNull final ExecutionException e) {
            if (isLoggingEnabled()) {
                LoggerFactory.getLogger().d(TAG, "doGetExecute: " + e);
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
     * If the site sends a redirect which Android (in its mysterious ways...) interprets
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
    private HttpURLConnection doGetConnect(@NonNull final URL url,
                                           @NonNull final String method)
            throws IOException {

        // sanity check
        int attemptsLeft = nrOfTries > 0 ? nrOfTries : NR_OF_TRIES;

        final HttpURLConnection initialRequest = createRequest(url, method);
        // Preserve for a potential manual redirect
        String requestUrlStr = initialRequest.getURL().toString();

        HttpURLConnection req = initialRequest;

        while (attemptsLeft > 0) {
            if (isLoggingEnabled()) {
                LoggerFactory.getLogger().d(TAG, "doGetConnect|connect",
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
                                     .d(TAG, "doGetConnect|response",
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
                                         .d(TAG, "doGetConnect|redirect",
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
                    LoggerFactory.getLogger().e(TAG, e, "doGetConnect|disconnecting",
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
                // FileNotFoundException: seen on some sites. A retry and the site was OK.
                if (isLoggingEnabled()) {
                    LoggerFactory.getLogger()
                                 .e(TAG, e, "doGetConnect|recoverable error",
                                    LOG_ATTEMPTS_LEFT + attemptsLeft,
                                    "requestUrlStr=`" + requestUrlStr + '`');
                }

                attemptsLeft--;
                if (attemptsLeft == 0) {
                    if (isLoggingEnabled()) {
                        LoggerFactory.getLogger()
                                     .d(TAG, "doGetConnect|all attempts failed|disconnecting");
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

        final String message = "doGetConnect|Giving up|initialRequestUrl=`"
                               + initialRequest.getURL() + '`';
        if (isLoggingEnabled()) {
            LoggerFactory.getLogger().d(TAG, message);
        }
        throw new NetworkException(message);
    }


    @Nullable
    @Override
    public R post(@NonNull final String urlStr,
                  @NonNull final String postBody,
                  @Nullable final ActionFunction<InputStream, R> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {

        try {
            futureHttp = ASyncExecutor.NETWORK.submit(() -> {
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
                             BufferedInputStream bis = new BufferedInputStream(is, bufferSize)) {
                            if (isZipped(request)) {
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

    @Override
    public void cancel() {
        synchronized (this) {
            if (futureHttp != null) {
                futureHttp.cancel(true);
            }
        }
    }
}
