/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.BookNotFoundException;
import com.hardbacknutter.nevertoomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter;

/**
 * Base class for all Goodreads handler classes.
 * <p>
 * The job of  handler is to implement a method to run the Goodreads request (e.g. 'search' in
 * {@link SearchBooksApiHandler} and to process the output.
 */
abstract class ApiHandlerNative {

    /** initial connection time to websites timeout. */
    private static final int CONNECT_TIMEOUT = 10_000;
    /** timeout for requests to website. */
    private static final int READ_TIMEOUT = 10_000;

    /** log error string. */
    private static final String ERROR_UNEXPECTED_STATUS_CODE_FROM_API =
            "Unexpected status code from API: ";

    @NonNull
    final GoodreadsManager mManager;

    /** XmlFilter root object. Used in extracting data file XML results. */
    @NonNull
    final XmlFilter mRootFilter = new XmlFilter("");

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     */
    ApiHandlerNative(@NonNull final GoodreadsManager grManager) {
        mManager = grManager;
    }

    /**
     * Sign a request and GET it; then pass it off to a parser.
     *
     * @param url               to GET
     * @param parameterMap      (optional) parameters to add to the url
     * @param requiresSignature Flag to optionally sign the request
     * @param requestHandler    (optional) handler for the parser
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    void executeGet(@NonNull final String url,
                    @SuppressWarnings({"SameParameterValue", "unused"})
                    @Nullable final Map<String, String> parameterMap,
                    @SuppressWarnings("SameParameterValue") final boolean requiresSignature,
                    @Nullable final DefaultHandler requestHandler)
            throws CredentialsException, BookNotFoundException, IOException {

        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.NETWORK || DEBUG_SWITCHES.DUMP_HTTP_URL)) {
            Logger.debug(this, "executeGet", "url=" + url);
        }

        String fullUrl = url;
        if (parameterMap != null) {
            Uri.Builder builder = new Uri.Builder();
            for (Map.Entry<String, String> entry : parameterMap.entrySet()) {
                builder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
            String query = builder.build().getEncodedQuery();
            if (query != null) {
                // add or append query string.
                fullUrl += (url.indexOf('?') < 0 ? '?' : '&') + query;
            }
        }

        HttpURLConnection request = (HttpURLConnection) new URL(fullUrl).openConnection();

        if (requiresSignature) {
            mManager.signGetRequest(request);
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
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    void executePost(@NonNull final String url,
                     @Nullable final Map<String, String> parameterMap,
                     final boolean requiresSignature,
                     @Nullable final DefaultHandler requestHandler)
            throws CredentialsException, BookNotFoundException, IOException {

        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.NETWORK || DEBUG_SWITCHES.DUMP_HTTP_URL)) {
            Logger.debug(this, "executePost", "url=" + url);
        }

        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setRequestMethod("POST");
        request.setDoOutput(true);

        if (requiresSignature) {
            mManager.signPostRequest(request, parameterMap);
        }

        // Now the actual POST payload
        if (parameterMap != null) {
            // encode using JDK
            Uri.Builder builder = new Uri.Builder();
            for (Map.Entry<String, String> entry : parameterMap.entrySet()) {
                builder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
            String query = builder.build().getEncodedQuery();

//            // encode using signpost. Leaving this code as a reference for now.
//            StringBuilder sb = new StringBuilder();
//            boolean first = true;
//            for (Map.Entry<String, String> entry : parameterMap.entrySet()) {
//                if (!first) {
//                    sb.append("&");
//                }
//                first = false;
//                // note we need to encode both key and value.
//                sb.append(OAuth.percentEncode(entry.getKey()));
//                sb.append("=");
//                sb.append(OAuth.percentEncode(entry.getValue()));
//            }
//            String oauth_query = sb.toString();
//
//            Logger.debug(this,"SIGN","native_query=" + query);
//            Logger.debug(this,"SIGN","query_oath=" + oauth_query);
//            Logger.debug(this,"SIGN","oauth_query.equals(native_query)= "
//                                        + oauth_query.equals(query));

            try (OutputStream os = request.getOutputStream();
                 OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(osw)) {
                writer.write(query);
                writer.flush();
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
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    private void execute(@NonNull final HttpURLConnection request,
                         @Nullable final DefaultHandler requestHandler)
            throws CredentialsException, BookNotFoundException, IOException {

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsManager.THROTTLER.waitUntilRequestAllowed();

        request.setConnectTimeout(CONNECT_TIMEOUT);
        request.setReadTimeout(READ_TIMEOUT);
        request.connect();

        int code = request.getResponseCode();
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS) {
            Logger.debug(this, "execute",
                         "\nrequest: " + request.getURL(),
                         "\nresponse: " + code + ' ' + request.getResponseMessage());
        }

        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                parseResponse(request, requestHandler);
                request.disconnect();
                break;

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                request.disconnect();
                GoodreadsManager.sHasValidCredentials = false;
                throw new CredentialsException(R.string.goodreads);

            case HttpURLConnection.HTTP_NOT_FOUND:
                request.disconnect();
                throw new BookNotFoundException();

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
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            parser.parse(is, requestHandler);

        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(this, e);
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
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    String executeRawGet(@NonNull final String url,
                         @SuppressWarnings("SameParameterValue") final boolean requiresSignature)
            throws CredentialsException, BookNotFoundException, IOException {

        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.NETWORK || DEBUG_SWITCHES.DUMP_HTTP_URL)) {
            Logger.debug(this, "executeRawGet", "url=" + url);
        }

        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();

        if (requiresSignature) {
            mManager.signGetRequest(request);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsManager.THROTTLER.waitUntilRequestAllowed();

        request.setConnectTimeout(CONNECT_TIMEOUT);
        request.setReadTimeout(READ_TIMEOUT);
        request.connect();

        int code = request.getResponseCode();
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS) {
            Logger.debug(this, "execute",
                         "\nrequest: " + request.getURL(),
                         "\nresponse: " + code + ' ' + request.getResponseMessage());
        }
        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                String content = getContent(request);
                request.disconnect();
                return content;

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                request.disconnect();
                GoodreadsManager.sHasValidCredentials = false;
                throw new CredentialsException(R.string.goodreads);

            case HttpURLConnection.HTTP_NOT_FOUND:
                request.disconnect();
                throw new BookNotFoundException();

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
    private String getContent(@NonNull final HttpURLConnection request)
            throws IOException {
        StringBuilder html = new StringBuilder();
        InputStream is = request.getInputStream();
        if (is != null) {
            while (true) {
                int i = is.read();
                if (i == -1) {
                    break;
                }
                html.append((char) i);
            }
        }
        return html.toString();
    }
}
