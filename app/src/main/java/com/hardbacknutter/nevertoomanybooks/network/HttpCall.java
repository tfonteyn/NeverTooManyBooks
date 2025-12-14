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

package com.hardbacknutter.nevertoomanybooks.network;

import android.content.Context;

import androidx.annotation.EmptySuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpForbiddenException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpStatusException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpUnauthorizedException;
import com.hardbacknutter.nevertoomanybooks.core.network.Throttler;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;

public class HttpCall {

    private static final String TAG = "HttpCall";

    /** InputStream buffer size. Used by {@code GET} and {@code POST}. */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    /** Helper to randomize some urls to avoid fingerprinting by the servers. */
    @SuppressWarnings("TypeMayBeWeakened")
    @NonNull
    private static final Random RANDOM = new Random();

    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String HEAD = "HEAD";

    @NonNull
    private final OkHttpClient httpClient;
    @Nullable
    private final Throttler throttler;
    private final boolean logEnabled;
    private final int labelResId;

    /** InputStream buffer size. Used by {@code GET} and {@code POST}. */
    private int bufferSize = DEFAULT_BUFFER_SIZE;

    @Nullable
    private Call call;

    public HttpCall(@NonNull final OkHttpClient httpClient,
                    @NonNull final EngineId engineId) {
        this.httpClient = httpClient;
        this.labelResId = engineId.getLabelResId();

        final SearchEngineConfig config = engineId.getConfig();
        //noinspection DataFlowIssue
        this.throttler = config.getThrottler();
        this.logEnabled = config.isLogHttpGetRequests();
    }

    public HttpCall(@NonNull final OkHttpClient httpClient,
                    @Nullable final Throttler throttler,
                    @StringRes final int labelResId,
                    final boolean logEnabled) {

        this.httpClient = httpClient;
        this.labelResId = labelResId;

        this.throttler = throttler;
        this.logEnabled = logEnabled;
    }

    /**
     * Create a suitable "Accept-Language" with user and site language.
     * The priorities will be a little randomized to help prevent fingerprinting
     *
     * @param context Current context
     * @param locale  for the primary language
     *
     * @return header string
     */
    @NonNull
    public static String createAcceptLanguageHeader(@NonNull final Context context,
                                                    @NonNull final Locale locale) {
        final Set<String> noDups = new HashSet<>();
        boolean addQ;

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        final String userLanguage = userLocale.getLanguage();
        final String languageTag = userLocale.toLanguageTag();

        final String siteLanguageTag = locale.toLanguageTag();
        final String siteLanguage = locale.getLanguage();

        final StringBuilder accept = new StringBuilder(languageTag);
        noDups.add(languageTag);

        if (!noDups.contains(userLanguage)) {
            accept.append(',').append(userLanguage);
            noDups.add(userLanguage);
        }

        final int offset = RANDOM.nextInt(2);

        // use 0.8 or 0.7
        //noinspection CheckStyle
        accept.append(";q=0.").append(8 + offset);

        addQ = false;
        if (!noDups.contains(siteLanguageTag)) {
            accept.append(',').append(siteLanguageTag);
            noDups.add(siteLanguageTag);
            addQ = true;
        }
        if (!noDups.contains(siteLanguage)) {
            accept.append(',').append(siteLanguage);
            noDups.add(siteLanguage);
            addQ = true;
        }
        // only add q if we actually added a value.
        if (addQ) {
            // use 0.5 or 0.4
            accept.append(";q=0.").append(4 + offset);
        }

        // Always add english if not there already.
        //noinspection CheckStyle
        if (!noDups.contains("en")) {
            accept.append(',').append("en");
            // use 0.3 or 0.2
            accept.append(";q=0.").append(2 + offset);
        }

        return accept.toString();
    }

    /**
     * Check if the response headers indicate the encoding is gzip.
     *
     * @param response connection to check
     *
     * @return {@code true} if the content-encoding was "gzip"
     */
    private static boolean isZipped(@NonNull final Response response) {
        return "gzip".equalsIgnoreCase(
                response.header(HttpConstants.RESPONSE_HEADER_CONTENT_ENCODING));
    }

    private static void logRequest(@NonNull final Request request) {
        final String headers = request
                .headers()
                .toMultimap()
                .entrySet()
                .stream()
                .map(es -> "Request Header: " + es.getKey() + "="
                           + String.join("|", es.getValue()))
                .collect(Collectors.joining("\n"));

        final Logger logger = LoggerFactory.getLogger();
        logger.d(TAG, "url: " + request.url().url());
        logger.d(TAG, "headers", "\n" + headers);
        logger.d(TAG, "body", "\n" + request.body());
    }

    private static void logResponse(@NonNull final Response response) {
        final String headers = response
                .headers()
                .toMultimap()
                .entrySet()
                .stream()
                .map(es -> "Response Header: " + es.getKey() + "="
                           + String.join("|", es.getValue()))
                .collect(Collectors.joining("\n"));

        final Logger logger = LoggerFactory.getLogger();
        logger.d(TAG, "url: ", response.request().url().url());
        logger.d(TAG, "responseCode: " + response.code());
        logger.d(TAG, "responseMsg: " + response.message());
        logger.d(TAG, "headers", "\n" + headers);

        final CookieStore store = ServiceLocator.getInstance()
                                                .getCookieManager()
                                                .getCookieStore();
        for (final URI uri : store.getURIs()) {
            for (final HttpCookie cookie : store.get(uri)) {
                logger.d(TAG, "Stored cookie → URI: " + uri + " | " + cookie);
            }
        }
    }

    /**
     * See <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-redirection-3xx">
     * Redirect codes</a>.
     *
     * @param code to check
     *
     * @return flag
     */
    private static boolean isRedirect(final int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
               || code == HttpURLConnection.HTTP_MOVED_TEMP
               || code == HttpURLConnection.HTTP_SEE_OTHER
               // Temporary Redirect
               || code == 307
               // Permanent Redirect
               || code == 308;
    }

    /**
     * Set the buffer size to use for the input stream.
     *
     * @param bufferSize in bytes
     */
    public void setBufferSize(final int bufferSize) {
        this.bufferSize = bufferSize;
    }

    /**
     * Create a suitable {@code HEAD} request.
     *
     * @param context Current context
     * @param locale  to use for the language header
     * @param url     to fetch
     *
     * @return new {@link Request} instance
     *
     * @throws MalformedURLException on url errors
     */
    @NonNull
    @EmptySuper
    public Request createHeadRequest(@NonNull final Context context,
                                     @NonNull final Locale locale,
                                     @NonNull final String url)
            throws MalformedURLException {
        return createDocumentRequest(context, locale, HEAD, new URL(url), null, null);
    }

    /**
     * Create a suitable {@code GET} request.
     *
     * @param context Current context
     * @param locale  to use for the language header
     * @param url     to fetch
     * @param headers (optional) extra headers to add/override
     *
     * @return new {@link Request} instance
     *
     * @throws MalformedURLException on url errors
     */
    @NonNull
    @EmptySuper
    public Request createGetRequest(@NonNull final Context context,
                                    @NonNull final Locale locale,
                                    @NonNull final String url,
                                    @Nullable final Map<String, String> headers)
            throws MalformedURLException {
        return createDocumentRequest(context, locale, GET, new URL(url), null, headers);
    }

    /**
     * Create a suitable {@code POST} request.
     * Redirects are automatic and might miss cookies.
     * Use {@link #postWithRedirectHandling(Request, ResponseProcessor)} for authentication
     * or other cookie/redirect issues.
     *
     * @param context Current context
     * @param locale  to use for the language header
     * @param url     to fetch
     * @param body    (optional) to post
     * @param headers (optional) extra headers to add/override
     *
     * @return new {@link Request} instance
     *
     * @throws MalformedURLException on url errors
     */
    public Request createPostRequest(@NonNull final Context context,
                                     @NonNull final Locale locale,
                                     @NonNull final String url,
                                     @Nullable final RequestBody body,
                                     @Nullable final Map<String, String> headers)
            throws MalformedURLException {
        return createDocumentRequest(context, locale, POST, new URL(url), body, headers);
    }

    /**
     * Convenience method to create a suitable {@code GET} {@link Request}.
     * <p>
     * The headers are set to the defaults as used by Firefox to request a "document"
     *
     * @param context Current context
     * @param locale  to use
     * @param method  "GET", "HEAD", "POST"
     * @param url     to use
     * @param body    (optional) the body to send when using a "POST"
     * @param headers (optional) extra headers to add/override
     *
     * @return new {@code GET} request instance
     */
    @NonNull
    private Request createDocumentRequest(@NonNull final Context context,
                                          @NonNull final Locale locale,
                                          @NonNull final String method,
                                          @NonNull final URL url,
                                          @Nullable final RequestBody body,
                                          @Nullable final Map<String, String> headers) {

        // Example of a Firefox request to https://developer.android.com

        //Host: developer.android.com
        //User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0
        //Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
        //Accept-Language: en-GB,en;q=0.8,nl-BE;q=0.5,de-DE;q=0.3
        //Accept-Encoding: gzip, deflate, br, zstd
        //DNT: 1
        //Sec-GPC: 1
        //Upgrade-Insecure-Requests: 1
        //Sec-Fetch-Dest: document
        //Sec-Fetch-Mode: navigate
        //Sec-Fetch-Site: none
        //Sec-Fetch-User: ?1
        //Connection: keep-alive

        // The "Sec-GPC" header above is documented as EXPERIMENTAL at
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-GPC
        // It seems only firefox is sending it and it's not used by any other browser.
        // We're not sending it for now.

        // TODO: could add Platform in combo with the Randomizer
        // "Android", "Chrome OS", "Chromium OS", "iOS", "Linux", "macOS", "Windows",
        // or "Unknown".
        // .header("Sec-CH-UA-Platform", "Windows");

        final Request.Builder builder = new Request.Builder()
                .url(url)
                .method(method, body)
                .header(HttpConstants.HOST, url.getHost())
                .header(HttpConstants.USER_AGENT,
                        HttpConstants.BROWSER_USER_AGENT)
                .header(HttpConstants.ACCEPT,
                        HttpConstants.ACCEPT_KITCHEN_SINK)
                .header(HttpConstants.ACCEPT_LANGUAGE,
                        createAcceptLanguageHeader(context, locale))
                .header(HttpConstants.ACCEPT_ENCODING,
                        HttpConstants.ACCEPT_ENCODING_GZIP)

                .header(HttpConstants.CONNECTION,
                        HttpConstants.CONNECTION_KEEP_ALIVE)

                // Deprecated but Firefox/Chrome are still sending it by default.
                .header(HttpConstants.DNT, "1")

                .header(HttpConstants.UPGRADE_INSECURE_REQUESTS,
                        HttpConstants.UPGRADE_INSECURE_REQUESTS_TRUE)
                // We want a generic document, e.g. html, xml, json, ...
                .header(HttpConstants.SEC_FETCH_DEST,
                        HttpConstants.SEC_FETCH_DEST_DOCUMENT)
                // The request is initiated by navigation between HTML documents.
                .header(HttpConstants.SEC_FETCH_MODE,
                        HttpConstants.SEC_FETCH_MODE_NAVIGATE)

                // The request was send by a "user" (our app) and not some auto/link/etc...
                .header(HttpConstants.SEC_FETCH_SITE,
                        HttpConstants.SEC_FETCH_SITE_NONE)
                .header(HttpConstants.SEC_FETCH_USER, "?1");

        // add or override
        if (headers != null) {
            headers.forEach(builder::header);
        }

        return builder.build();
    }

    /**
     * Send a {@code HEAD} request.
     *
     * @param request to execute
     *
     * @throws IOException on generic/other IO failures
     */
    public void head(@NonNull final Request request)
            throws IOException {
        // silly, sure...  but lets keep the calls clear by calling this method "head"
        get(request, null);
    }

    /**
     * Send a {@code GET} request.
     *
     * @param request           execute
     * @param responseProcessor (optional) receives the response/InputStream
     * @param <R>               type of the result
     *
     * @return the processed response
     *
     * @throws IOException on generic/other IO failures
     */
    @Nullable
    public <R> R get(@NonNull final Request request,
                     @Nullable final ResponseProcessor<R> responseProcessor)
            throws IOException {

        call = httpClient.newCall(request);
        preExecute(request);
        try (Response response = call.execute()) {
            checkResponse(response);
            return readBody(response, responseProcessor);
        }
    }

    /**
     * Send a {@code POST} request.
     *
     * @param request           to execute
     * @param responseProcessor (optional) receives the response/InputStream
     * @param <R>               type of the result
     *
     * @return the processed response; can be {@code null} if there was no response body.
     *
     * @throws IOException on generic/other IO failures
     */
    @Nullable
    public <R> R post(@NonNull final Request request,
                      @Nullable final ResponseProcessor<R> responseProcessor)
            throws IOException {

        call = httpClient.newCall(request);
        preExecute(request);
        try (Response response = call.execute()) {
            checkResponse(response);
            return readBody(response, responseProcessor);
        }
    }


    @Nullable
    public <R> R getWithRedirectHandling(@NonNull final Request request,
                                         @Nullable final ResponseProcessor<R> responseProcessor)
            throws IOException {

        call = httpClient.newBuilder()
                         .followRedirects(false)
                         .build()
                         .newCall(request);

        preExecute(request);
        Response response = call.execute();
        checkResponse(response);

        final int MAX_REDIRECTS = 50;
        final Set<String> visited = new HashSet<>();
        int redirectCount = 0;
        Request redirectedRequest;
        HttpUrl redirectedUrl;
        Headers headers;

        while (response.isRedirect()) {
            if (redirectCount > MAX_REDIRECTS) {
                throw new IOException("Too many redirects: " + redirectCount);
            }
            final String location = response.header(HttpConstants.RESPONSE_HEADER_LOCATION);
            if (location != null) {
                redirectedUrl = response.request().url().resolve(location);
                if (redirectedUrl != null) {
                    if (visited.contains(redirectedUrl.toString())) {
                        throw new IOException("Invalid or looping redirect to: " + location);
                    }

                    visited.add(redirectedUrl.toString());
                    redirectCount++;

                    headers = response.request().headers();
                    response.close();

                    final Request.Builder requestBuilder = new Request.Builder()
                            .url(redirectedUrl)
                            .method(GET, null);

                    // Copy headers safely
                    for (final String name : headers.names()) {
                        if (!name.equalsIgnoreCase(HttpConstants.RESPONSE_HEADER_CONTENT_LENGTH)) {
                            final String value = headers.get(name);
                            if (value != null) {
                                requestBuilder.header(name, value);
                            }
                        }
                    }
                    redirectedRequest = requestBuilder.build();
                    call = httpClient.newBuilder()
                                     .followRedirects(false)
                                     .build()
                                     .newCall(redirectedRequest);

                    preExecute(request);
                    response = call.execute();
                    checkResponse(response);
                }
            }
        }

        return readBody(response, responseProcessor);
    }

    /**
     * Send a {@code POST} request.
     * <p>
     * This method will manually follow redirects enabling it to capture
     * cookies set on the 30x response, but not on the eventual 200 response.
     * Commonly used for authentication requests.
     *
     * @param request           to execute
     * @param responseProcessor (optional) receives the response/InputStream
     * @param <R>               type of the result
     *
     * @return the processed response; can be {@code null} if there was no response body.
     *
     * @throws IOException on generic/other IO failures
     */
    @Nullable
    public <R> R postWithRedirectHandling(@NonNull final Request request,
                                          @Nullable final ResponseProcessor<R> responseProcessor)
            throws IOException {

        final HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        call = httpClient.newBuilder()
                         .followRedirects(false)
                         .build()
                         .newCall(request);

        HttpUrl redirectedUrl = null;
        Headers headers = null;
        final int code;
        preExecute(request);
        try (Response response = call.execute()) {
            checkResponse(response);

            code = response.code();
            if (!isRedirect(code)) {
                return readBody(response, responseProcessor);
            }

            // manual redirect, so we get the cookies from it
            final String location = response.header(HttpConstants.RESPONSE_HEADER_LOCATION);
            if (location != null) {
                redirectedUrl = response.request().url().resolve(location);
                headers = response.request().headers();
            }
        }

        if (redirectedUrl != null) {
            final Request.Builder requestBuilder = new Request.Builder()
                    .url(redirectedUrl)
                    // POST + redirect => GET
                    // Sidenote: read the specs on 301/302/307/308
                    // and sigh deeply...
                    // but in reality, this method swap works.
                    .method(GET, null);

            // Copy headers safely
            for (final String name : headers.names()) {
                if (!name.equalsIgnoreCase(HttpConstants.RESPONSE_HEADER_CONTENT_LENGTH)) {
                    final String value = headers.get(name);
                    if (value != null) {
                        requestBuilder.header(name, value);
                    }
                }
            }
            final Request redirectedRequest = requestBuilder.build();
            call = httpClient.newCall(redirectedRequest);
            preExecute(redirectedRequest);
            try (Response response = call.execute()) {
                checkResponse(response);
                return readBody(response, responseProcessor);
            }
        }

        return null;
    }

    private void preExecute(@NonNull final Request request) {
        if (throttler != null) {
            throttler.waitUntilRequestAllowed();
        }

        if (logEnabled) {
            logRequest(request);
        }
    }

    @Nullable
    private <R> R readBody(@NonNull final Response response,
                           @Nullable final ResponseProcessor<R> responseProcessor)
            throws IOException {
        if (responseProcessor != null) {
            final ResponseBody body = response.body();
            if (body != null) {
                try (BufferedInputStream bis = new BufferedInputStream(
                        body.byteStream(), bufferSize)) {
                    if (isZipped(response)) {
                        try (GZIPInputStream gzs = new GZIPInputStream(bis)) {
                            return responseProcessor.apply(response, gzs);
                        }
                    } else {
                        return responseProcessor.apply(response, bis);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Send the GET and use the given {@link ResponseProcessor} to handle the response.
     * <p>
     * This method handles gzip encoding automatically.
     *
     * @param request execute
     *
     * @return the response page as a single {@code String}
     *
     * @throws IOException on generic/other IO failures
     */
    @NonNull
    public String getAsString(@NonNull final Request request)
            throws IOException {

        call = httpClient.newCall(request);
        preExecute(request);
        try (Response response = call.execute()) {
            checkResponse(response);
            // This code SHOULD have worked but it does not unzip correctly!?
            // response.body().string();
            //noinspection DataFlowIssue
            return readBody(response, (r, is) -> {
                try (Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    try (BufferedReader reader = new BufferedReader(isr, bufferSize)) {
                        return reader.lines().collect(Collectors.joining());
                    }
                }
            });
        }
    }

    public void cancel() {
        synchronized (this) {
            if (call != null) {
                call.cancel();
            }
        }
    }

    /**
     * Check the response code and throw exceptions as appropriate.
     *
     * @param response to check
     *
     * @throws HttpUnauthorizedException 401: Unauthorized.
     * @throws HttpForbiddenException    403: Forbidden
     * @throws HttpNotFoundException     404: Not Found.
     * @throws SocketTimeoutException    408: Request Time-Out.
     * @throws HttpStatusException       on any other HTTP failures
     */
    @WorkerThread
    private void checkResponse(@NonNull final Response response)
            throws HttpUnauthorizedException,
                   HttpForbiddenException,
                   HttpNotFoundException,
                   SocketTimeoutException,
                   HttpStatusException {

        final int responseCode = response.code();

        if (logEnabled) {
            logResponse(response);
        }

        if (responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {
            return;
        }

        @Nullable
        final String location = response.header(HttpConstants.RESPONSE_HEADER_LOCATION);

        switch (responseCode) {
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                throw new HttpUnauthorizedException(labelResId,
                                                    response.message(),
                                                    response.request().url().url(),
                                                    location);

            case HttpURLConnection.HTTP_FORBIDDEN:
                throw new HttpForbiddenException(labelResId,
                                                 response.message(),
                                                 response.request().url().url(),
                                                 location);

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new HttpNotFoundException(labelResId,
                                                response.message(),
                                                response.request().url().url(),
                                                location);

            case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
                // for easier reporting issues to the user, map a 408 to an STE
                throw new SocketTimeoutException("408 " + response.message());

            default:
                throw new HttpStatusException(labelResId,
                                              responseCode,
                                              response.message(),
                                              response.request().url().url(),
                                              location);
        }
    }

    /**
     * Process the response to a GET method.
     *
     * @param <R> the resulting/parsed vale
     */
    @FunctionalInterface
    public interface ResponseProcessor<R> {

        /**
         * Applies this function to the given arguments.
         *
         * @param response for getting headers, url,..
         *                 This is (2025-04-20) only really needed by JSoup as
         *                 we need to access the final url and response headers.
         * @param is       to read and parse
         *
         * @return the resulting {@code R}
         *
         * @throws IOException on generic/other IO failures
         *                     Can contain wrapped exceptions.
         */
        R apply(@NonNull Response response,
                @NonNull InputStream is)
                throws IOException;
    }
}
