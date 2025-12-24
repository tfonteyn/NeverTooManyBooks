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

import androidx.annotation.EmptySuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
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
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import javax.xml.parsers.SAXParser;

import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

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

    private static final int HTTP_TEMPORARY_REDIRECT = 307;
    private static final int HTTP_PERMANENT_REDIRECT = 308;

    @NonNull
    private final OkHttpClient httpClient;
    @Nullable
    private final Throttler throttler;
    private final boolean logEnabled;
    private final int labelResId;

    @NonNull
    private final CookieStore store;

    /** InputStream buffer size. Used by {@code GET} and {@code POST}. */
    private int bufferSize = DEFAULT_BUFFER_SIZE;

    @Nullable
    private Call call;

    /**
     * Constructor.
     *
     * @param httpClient  the one
     * @param cookieStore for logging <strong>all</strong> cookies as desired
     * @param labelResId  string resource representing the caller
     * @param throttler   to use
     * @param logEnabled  flag
     */
    public HttpCall(@NonNull final OkHttpClient httpClient,
                    @NonNull final CookieStore cookieStore,
                    @StringRes final int labelResId,
                    @Nullable final Throttler throttler,
                    final boolean logEnabled) {

        this.httpClient = httpClient;
        this.store = cookieStore;
        this.labelResId = labelResId;

        this.throttler = throttler;
        this.logEnabled = logEnabled;
    }

    /**
     * Create a suitable "Accept-Language" with site and user language.
     * The site locale is send first.
     * The priorities (q) will be a little randomized to help prevent fingerprinting.
     *
     * @param siteLocale for the primary language tag
     * @param userLocale for the secondary language tag
     *
     * @return header string
     *
     * @see HttpConstants#ACCEPT_LANGUAGE
     */
    @NonNull
    public static String createAcceptLanguageHeader(@NonNull final Locale siteLocale,
                                                    @NonNull final Locale userLocale) {
        final Set<String> noDups = new HashSet<>();
        final int offset = RANDOM.nextInt(2);

        final StringJoiner accept = new StringJoiner(",");

        // use 0.8 or 0.7
        accept.add(addLangTag(siteLocale.toLanguageTag(), siteLocale.getLanguage(),
                              8 + offset, noDups));
        // use 0.5 or 0.4
        // Always add english if not there already.
        accept.add(addLangTag(userLocale.toLanguageTag(), userLocale.getLanguage(),
                              4 + offset, noDups));
        // use 0.3 or 0.2
        accept.add(addLangTag("en", "en-GB", 2 + offset, noDups));

        return accept.toString();
    }

    @NonNull
    private static CharSequence addLangTag(@NonNull final String languageTag,
                                           @NonNull final String language,
                                           final int q,
                                           @NonNull final Set<String> noDups) {

        final StringJoiner accept = new StringJoiner(",");
        boolean addQ = false;
        if (!noDups.contains(languageTag)) {
            accept.add(languageTag);
            noDups.add(languageTag);
            addQ = true;
        }
        if (!noDups.contains(language)) {
            accept.add(language);
            noDups.add(language);
            addQ = true;
        }

        // only add q if we actually added a value.
        if (addQ) {
            return accept + ";q=0." + q;
        } else {
            return accept.toString();
        }
    }

    /**
     * Check if the response headers indicate the encoding is gzip.
     *
     * @param response connection to check
     *
     * @return {@code true} if the content-encoding was "gzip"
     */
    private static boolean isZipped(@NonNull final Response response) {
        return HttpConstants.ACCEPT_ENCODING_GZIP.equalsIgnoreCase(
                response.header(HttpConstants.RESPONSE_HEADER_CONTENT_ENCODING));
    }

    private void logRequest(@NonNull final Request request) {
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

    private void logResponse(@NonNull final Response response) {
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
    private boolean isRedirect(final int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
               || code == HttpURLConnection.HTTP_MOVED_TEMP
               || code == HttpURLConnection.HTTP_SEE_OTHER
               || code == HTTP_TEMPORARY_REDIRECT
               || code == HTTP_PERMANENT_REDIRECT;
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
     * @param url        to fetch
     * @param siteLocale for the primary language tag
     * @param userLocale for the secondary language tag
     *
     * @return new {@link Request} instance
     *
     * @throws MalformedURLException on url errors
     */
    @NonNull
    @EmptySuper
    public Request createHeadRequest(@NonNull final String url,
                                     @NonNull final Locale siteLocale,
                                     @NonNull final Locale userLocale)
            throws MalformedURLException {
        return createDocumentRequest(HEAD, new URL(url), siteLocale, userLocale, null, null
        );
    }

    /**
     * Create a suitable {@code GET} request.
     *
     * @param url        to fetch
     * @param siteLocale for the primary language tag
     * @param userLocale for the secondary language tag
     * @param headers    (optional) extra headers to add/override
     *
     * @return new {@link Request} instance
     *
     * @throws MalformedURLException on url errors
     */
    @NonNull
    @EmptySuper
    public Request createGetRequest(@NonNull final String url,
                                    @NonNull final Locale siteLocale,
                                    @NonNull final Locale userLocale,
                                    @Nullable final Map<String, String> headers)
            throws MalformedURLException {
        return createDocumentRequest(GET, new URL(url), siteLocale, userLocale, headers, null);
    }

    /**
     * Create a suitable {@code POST} request.
     * Redirects are automatic and might miss cookies.
     * Use {@link #postWithRedirectHandling(Request, ResponseProcessor)} for authentication
     * or other cookie/redirect issues.
     *
     * @param url        to fetch
     * @param siteLocale for the primary language tag
     * @param userLocale for the secondary language tag
     * @param headers    (optional) extra headers to add/override
     * @param body       (optional) to post
     *
     * @return new {@link Request} instance
     *
     * @throws MalformedURLException on url errors
     */
    public Request createPostRequest(@NonNull final String url,
                                     @NonNull final Locale siteLocale,
                                     @NonNull final Locale userLocale,
                                     @Nullable final Map<String, String> headers,
                                     @Nullable final RequestBody body)
            throws MalformedURLException {
        return createDocumentRequest(POST, new URL(url), siteLocale, userLocale, headers, body);
    }

    /**
     * Convenience method to create a suitable {@code GET} {@link Request}.
     * <p>
     * The headers are set to the defaults as used by Firefox to request a "document"
     *
     * @param method     "GET", "HEAD", "POST"
     * @param url        to use
     * @param siteLocale for the primary language tag
     * @param userLocale for the secondary language tag
     * @param headers    (optional) extra headers to add/override
     * @param body       (optional) the body to send when using a "POST"
     *
     * @return new {@code GET} request instance
     */
    @NonNull
    private Request createDocumentRequest(@NonNull final String method,
                                          @NonNull final URL url,
                                          @NonNull final Locale siteLocale,
                                          @NonNull final Locale userLocale,
                                          @Nullable final Map<String, String> headers,
                                          @Nullable final RequestBody body) {

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
                        createAcceptLanguageHeader(siteLocale, userLocale))
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
     * @param url        to fetch
     * @param siteLocale for the primary language tag
     * @param userLocale for the secondary language tag
     *
     * @throws IOException on generic/other IO failures
     */
    public void head(@NonNull final String url,
                     @NonNull final Locale siteLocale,
                     @NonNull final Locale userLocale)
            throws IOException {
        final Request request = createHeadRequest(url, siteLocale, userLocale);
        head(request);
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
     * @param url               to fetch
     * @param siteLocale        for the primary language tag
     * @param userLocale        for the secondary language tag
     * @param headers           (optional) extra headers to add/override
     * @param responseProcessor (optional) receives the response/InputStream
     * @param <R>               type of the result
     *
     * @return the processed response
     *
     * @throws IOException on generic/other IO failures
     */
    @Nullable
    public <R> R get(@NonNull final String url,
                     @NonNull final Locale siteLocale,
                     @NonNull final Locale userLocale,
                     @Nullable final Map<String, String> headers,
                     @Nullable final ResponseProcessor<R> responseProcessor)
            throws IOException {
        final Request request = createGetRequest(url, siteLocale, userLocale, headers);
        return get(request, responseProcessor);
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
        try (Response response = getResponse(request)) {
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
        try (Response response = getResponse(request)) {
            return readBody(response, responseProcessor);
        }
    }


    @Nullable
    public <R> R getWithRedirectHandling(@NonNull final String url,
                                         @NonNull final Locale siteLocale,
                                         @NonNull final Locale userLocale,
                                         @Nullable final Map<String, String> headers,
                                         @Nullable final ResponseProcessor<R> responseProcessor)
            throws IOException {
        final Request request = createGetRequest(url, siteLocale, userLocale, headers);
        return getWithRedirectHandling(request, responseProcessor);
    }

    @Nullable
    public <R> R getWithRedirectHandling(@NonNull final Request request,
                                         @Nullable final ResponseProcessor<R> responseProcessor)
            throws IOException {

        call = httpClient.newBuilder()
                         .followRedirects(false)
                         .build()
                         .newCall(request);

        Response response = getResponse(request);

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

                    response = getResponse(request);
                }
            }
        }

        return readBody(response, responseProcessor);
    }

    /**
     * Create a request, and {@code POST} it; handles redirects manually capturing cookies
     * as needed.
     *
     * @param url               to fetch
     * @param siteLocale        for the primary language tag
     * @param userLocale        for the secondary language tag
     * @param headers           (optional) extra headers to add/override
     * @param body              (optional) to post
     * @param responseProcessor (optional) receives the response/InputStream
     * @param <R>               type of the result
     *
     * @return the processed response; can be {@code null} if there was no response body.
     *
     * @throws IOException on generic/other IO failures
     * @see #createPostRequest(String, Locale, Locale, Map, RequestBody)
     * @see #postWithRedirectHandling(Request, ResponseProcessor)
     */
    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    public <R> R postAuthenticationForm(@NonNull final String url,
                                        @NonNull final Locale siteLocale,
                                        @NonNull final Locale userLocale,
                                        @Nullable final Map<String, String> headers,
                                        @Nullable final RequestBody body,
                                        @Nullable final ResponseProcessor<R> responseProcessor)
            throws IOException {
        final Request request = createPostRequest(url, siteLocale, userLocale, headers, body);
        return postWithRedirectHandling(request, responseProcessor);
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
     *
     * @see #postAuthenticationForm(String, Locale, Locale, Map, RequestBody, ResponseProcessor)
     */
    @Nullable
    public <R> R postWithRedirectHandling(@NonNull final Request request,
                                          @Nullable final ResponseProcessor<R> responseProcessor)
            throws IOException {

        call = httpClient.newBuilder()
                         .followRedirects(false)
                         .build()
                         .newCall(request);

        HttpUrl redirectedUrl = null;
        Headers headers = null;
        final int code;
        try (Response response = getResponse(request)) {
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
            try (Response response = getResponse(request)) {
                return readBody(response, responseProcessor);
            }
        }

        return null;
    }


    /**
     * Send a {@code GET} request and return the response as a single string.
     *
     * @param url        to fetch
     * @param siteLocale for the primary language tag
     * @param userLocale for the secondary language tag
     * @param headers    (optional) extra headers to add/override
     *
     * @return the response page as a single {@code String}
     *
     * @throws IOException on generic/other IO failures
     */
    @NonNull
    public String getAsString(@NonNull final String url,
                              @NonNull final Locale siteLocale,
                              @NonNull final Locale userLocale,
                              @Nullable final Map<String, String> headers)
            throws IOException {
        final Request request = createGetRequest(url, siteLocale, userLocale, headers);
        return getAsString(request);
    }

    /**
     * Send a {@code GET} request and return the response as a single string.
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
        try (Response response = getResponse(request)) {
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

    /**
     * Send a {@code GET} request and process the response using the
     * given SAX parser/handler.
     * <p>
     * The handler should provide the result.
     *
     * @param url        to fetch
     * @param siteLocale for the primary language tag
     * @param userLocale for the secondary language tag
     * @param headers    (optional) extra headers to add/override
     * @param parser     SAX parser
     * @param handler    SAX handler
     *
     * @throws IOException on generic/other IO failures
     */
    public void get(@NonNull final String url,
                    @NonNull final Locale siteLocale,
                    @NonNull final Locale userLocale,
                    @Nullable final Map<String, String> headers,
                    @NonNull final SAXParser parser,
                    @NonNull final DefaultHandler handler)
            throws IOException {
        get(url, siteLocale, userLocale, headers, (response, is) -> {
            // The InputStream is already unzipped as needed.
            try {
                parser.parse(is, handler);
                return true;
            } catch (@NonNull final SAXException e) {
                // always unwrap SAXException using getException() !
                final Exception cause = e.getException();
                if (cause instanceof EOFException) {
                    // not an error; we're done.
                    return true;
                }
                if (cause != null) {
                    throw new IOException(cause);
                }
                // a raw SAXException? Should never happen... flw
                throw new IOException(e);
            }
        });
    }

    /**
     * Read the response body, and unzip as required.
     *
     * @param response          to read/unzip
     * @param responseProcessor to process the resulting stream
     * @param <R>               type of the result
     *
     * @return the processed response
     *
     * @throws IOException on generic/other IO failures
     */
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
     * Prepare and execute the request. The response will be checked
     * for errors before returning.
     *
     * @param request to execute
     *
     * @return response
     *
     * @throws IOException on generic/other IO failures
     */
    @NonNull
    private Response getResponse(@NonNull final Request request)
            throws IOException {
        if (throttler != null) {
            throttler.waitUntilRequestAllowed();
        }

        if (logEnabled) {
            logRequest(request);
        }
        //noinspection DataFlowIssue
        final Response response = call.execute();
        checkResponse(response);
        return response;
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

    public void cancel() {
        synchronized (this) {
            if (call != null) {
                call.cancel();
            }
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
