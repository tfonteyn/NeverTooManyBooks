/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.api;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * Base class for all Goodreads handler classes.
 * <p>
 * The job of a handler is to implement a method to run the Goodreads request
 * and to process the output.
 */
public abstract class ApiHandler {

    /** Log tag. */
    private static final String TAG = "ApiHandler";

    /** log error string. */
    private static final String ERROR_UNEXPECTED_STATUS_CODE_FROM_API =
            "Unexpected status code from API: ";
    @NonNull
    protected final GoodreadsAuth mGrAuth;
    @NonNull
    final Context mAppContext;
    @NonNull
    private final SearchEngineRegistry.Config mConfig;

    /**
     * Constructor.
     *
     * @param appContext Application context
     * @param grAuth     Authentication handler
     */
    protected ApiHandler(@NonNull final Context appContext,
                         @NonNull final GoodreadsAuth grAuth) {
        mAppContext = appContext;
        mGrAuth = grAuth;

        mConfig = SearchEngineRegistry.getByEngineId(SearchSites.GOODREADS);
    }

    /**
     * Sign a request and GET it; then pass it off to a parser.
     *
     * @param url               to GET
     * @param parameterMap      (optional) parameters to add to the url
     * @param requiresSignature Flag to optionally sign the request
     * @param requestHandler    (optional) handler for the parser
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the URL was not found
     * @throws IOException          on other failures
     */
    protected void executeGet(@NonNull final String url,
                              @Nullable final Map<String, String> parameterMap,
                              final boolean requiresSignature,
                              @Nullable final DefaultHandler requestHandler)
            throws CredentialsException, Http404Exception, IOException {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "executeGet|url=\"" + url + '\"');
        }

        String fullUrl = url;
        if (parameterMap != null) {
            final Uri.Builder builder = new Uri.Builder();
            for (final Map.Entry<String, String> entry : parameterMap.entrySet()) {
                builder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
            final String query = builder.build().getEncodedQuery();
            if (query != null) {
                // add or append query string.
                fullUrl += (url.indexOf('?') < 0 ? '?' : '&') + query;
            }
        }

        final HttpURLConnection request = (HttpURLConnection) new URL(fullUrl).openConnection();

        if (requiresSignature) {
            mGrAuth.signGetRequest(request);
        }

        execute(request, requestHandler);
    }

    /**
     * Sign a request and POST it; then pass it off to a parser.
     *
     * @param url               to POST
     * @param parameterMap      (optional) parameters to add to the POST
     * @param requiresSignature Flag to optionally sign the request
     * @param requestHandler    (optional) handler for the parser
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the URL was not found
     * @throws IOException          on other failures
     */
    void executePost(@NonNull final String url,
                     @Nullable final Map<String, String> parameterMap,
                     @SuppressWarnings("SameParameterValue") final boolean requiresSignature,
                     @Nullable final DefaultHandler requestHandler)
            throws CredentialsException, Http404Exception, IOException {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "executePost|url=\"" + url + '\"');
        }

        final HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setRequestMethod("POST");
        request.setDoOutput(true);

        if (requiresSignature) {
            mGrAuth.signPostRequest(request, parameterMap);
        }

        // Now the actual POST payload
        if (parameterMap != null) {
            // encode using JDK
            final Uri.Builder builder = new Uri.Builder();
            for (final Map.Entry<String, String> entry : parameterMap.entrySet()) {
                builder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
            final String query = builder.build().getEncodedQuery();

//            // encode using signpost. Leaving this code as a reference for now.
//            StringBuilder sb = new StringBuilder();
//            boolean first = true;
//            for (Map.Entry<String, String> entry : parameterMap.entrySet()) {
//                if (first) {
//                    first = false;
//                } else {
//                    sb.append("&");
//                }
//
//                // note we need to encode both key and value.
//                sb.append(OAuth.percentEncode(entry.getKey()));
//                sb.append("=");
//                sb.append(OAuth.percentEncode(entry.getValue()));
//            }
//            String oauth_query = sb.toString();
//
//            Log.d(TAG,"SIGN|native_query=" + query);
//            Log.d(TAG,"SIGN|query_oath=" + oauth_query);
//            Log.d(TAG,"SIGN|oauth_query.equals(native_query)= " + oauth_query.equals(query));

            if (query != null) {
                try (OutputStream os = request.getOutputStream();
                     Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                     Writer writer = new BufferedWriter(osw)) {
                    writer.write(query);
                    writer.flush();
                }
            }
        }

        execute(request, requestHandler);
    }

    /**
     * Submit a request; then pass it off to a parser.
     *
     * @param request        to execute
     * @param requestHandler (optional) handler for the parser
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the URL was not found
     * @throws IOException          on other failures
     */
    private void execute(@NonNull final HttpURLConnection request,
                         @Nullable final DefaultHandler requestHandler)
            throws CredentialsException, Http404Exception, IOException {

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsSearchEngine.THROTTLER.waitUntilRequestAllowed();

        request.setConnectTimeout(mConfig.getConnectTimeoutMs());
        request.setReadTimeout(mConfig.getReadTimeoutMs());
        request.connect();

        final int code = request.getResponseCode();
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_HTTP_XML) {
            Log.d(TAG, "execute"
                       + "\nrequest: " + request.getURL()
                       + "\nresponse: " + code + ' ' + request.getResponseMessage());
        }

        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                parseResponse(request, requestHandler);
                request.disconnect();
                break;

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                request.disconnect();
                GoodreadsAuth.invalidateCredentials();
                throw new CredentialsException(mAppContext.getString(R.string.site_goodreads));

            case HttpURLConnection.HTTP_NOT_FOUND:
                request.disconnect();
                throw new Http404Exception(
                        mAppContext.getString(R.string.error_network_site_access_failed),
                        request.getURL());

            default:
                request.disconnect();
                throw new IOException(ERROR_UNEXPECTED_STATUS_CODE_FROM_API
                                      + request.getResponseCode()
                                      + '/' + request.getResponseMessage());
        }
    }

    /**
     * Pass a response off to a parser.
     *
     * @param request        the executed request from which to read
     * @param requestHandler (optional) handler for the parser
     *
     * @throws IOException on failures (any parser exceptions are wrapped)
     */
    private void parseResponse(@NonNull final HttpURLConnection request,
                               @Nullable final DefaultHandler requestHandler)
            throws IOException {

        try (InputStream is = request.getInputStream()) {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            final SAXParser parser = factory.newSAXParser();
            parser.parse(is, requestHandler);

        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "parseResponse", e);
            }
            throw new IOException(e);
        }
    }

    /**
     * Sign a request and submit it. Return the raw text output.
     *
     * @param url               to GET
     * @param requiresSignature Flag to optionally sign the request
     *
     * @return the raw text output.
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the URL was not found
     * @throws IOException          on other failures
     */
    @NonNull
    String executeRawGet(@NonNull final String url,
                         @SuppressWarnings("SameParameterValue") final boolean requiresSignature)
            throws CredentialsException, Http404Exception, IOException {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "executeRawGet|url=\"" + url + '\"');
        }

        final HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();

        if (requiresSignature) {
            mGrAuth.signGetRequest(request);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsSearchEngine.THROTTLER.waitUntilRequestAllowed();

        request.setConnectTimeout(mConfig.getConnectTimeoutMs());
        request.setReadTimeout(mConfig.getReadTimeoutMs());
        request.connect();

        final int code = request.getResponseCode();
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_HTTP_XML) {
            Log.d(TAG, "execute"
                       + "\nrequest: " + request.getURL()
                       + "\nresponse: " + code + ' ' + request.getResponseMessage());
        }
        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                final String content = getContent(request);
                request.disconnect();
                return content;

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                request.disconnect();
                GoodreadsAuth.invalidateCredentials();
                throw new CredentialsException(mAppContext.getString(R.string.site_goodreads));

            case HttpURLConnection.HTTP_NOT_FOUND:
                request.disconnect();
                throw new Http404Exception(
                        mAppContext.getString(R.string.error_network_site_access_failed),
                        request.getURL());

            default:
                request.disconnect();
                throw new IOException(ERROR_UNEXPECTED_STATUS_CODE_FROM_API
                                      + request.getResponseCode()
                                      + '/' + request.getResponseMessage());
        }
    }

    /**
     * Read the request into a single String.
     *
     * @param request the executed request from which to read
     *
     * @return the content as a single string
     *
     * @throws IOException on failures
     */
    @NonNull
    private String getContent(@NonNull final HttpURLConnection request)
            throws IOException {
        final StringBuilder html = new StringBuilder();
        final InputStream is = request.getInputStream();
        if (is != null) {
            while (true) {
                final int i = is.read();
                if (i == -1) {
                    break;
                }
                html.append((char) i);
            }
        }
        return html.toString();
    }
}
