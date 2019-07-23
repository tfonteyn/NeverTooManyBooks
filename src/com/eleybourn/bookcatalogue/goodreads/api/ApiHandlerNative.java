/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.goodreads.api;

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

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.BookNotFoundException;
import com.eleybourn.bookcatalogue.utils.CredentialsException;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter;

/**
 * Base class for all Goodreads API handler classes.
 * <p>
 * The job of an API handler is to implement a method to run the API (e.g. 'search' in
 * {@link SearchBooksApiHandler} and to process the output.
 *
 * @author Philip Warner
 */
abstract class ApiHandlerNative {

    /** initial connection time to websites timeout. */
    private static final int CONNECT_TIMEOUT = 10_000;
    /** timeout for requests to website. */
    private static final int READ_TIMEOUT = 10_000;

    /** error string. */
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
        if (BuildConfig.DEBUG) {
            Logger.debug(this, "ApiHandler", "NATIVE");
        }
        mManager = grManager;
    }

    /**
     * Sign a request and GET it; then pass it off to a parser.
     *
     * @param url               to POST
     * @param requiresSignature Flag to optionally sigh the request
     * @param requestHandler    (optional) handler for the parser
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    void executeGet(@NonNull final String url,
                    @SuppressWarnings("SameParameterValue") final boolean requiresSignature,
                    @Nullable final DefaultHandler requestHandler)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.NETWORK || DEBUG_SWITCHES.DUMP_HTTP_URL)) {
            Logger.debug(this, "executeGet", "url=" + url);
        }

        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();

        execute(request, requiresSignature, requestHandler);
    }

    /**
     * Sign a request and POST it; then pass it off to a parser.
     *
     * @param url               to POST
     * @param parameterMap      (optional) parameters to add to the POST
     * @param requiresSignature Flag to optionally sigh the request
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
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.NETWORK || DEBUG_SWITCHES.DUMP_HTTP_URL)) {
            Logger.debug(this, "executeGet", "url=" + url);
        }

        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setRequestMethod("POST");
        request.setDoInput(true);
        request.setDoOutput(true);

        if (parameterMap != null) {
            Uri.Builder builder = new Uri.Builder();
            for (Map.Entry<String, String> entry : parameterMap.entrySet()) {
                builder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
            String query = builder.build().getEncodedQuery();

            try (OutputStream os = request.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                writer.write(query);
                writer.flush();
            }
        }

        execute(request, requiresSignature, requestHandler);
    }

    /**
     * Sign a request and submit it; then pass it off to a parser.
     *
     * @param request           to execute
     * @param requiresSignature Flag to optionally sigh the request
     * @param requestHandler    (optional) handler for the parser
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    private void execute(@NonNull final HttpURLConnection request,
                         final boolean requiresSignature,
                         @Nullable final DefaultHandler requestHandler)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (requiresSignature) {
            mManager.sign(request);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsManager.waitUntilRequestAllowed();

        request.setConnectTimeout(CONNECT_TIMEOUT);
        request.setReadTimeout(READ_TIMEOUT);
        request.connect();

        int code = request.getResponseCode();
        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                parseResponse(request, requestHandler);
                break;

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                GoodreadsManager.sHasValidCredentials = false;
                throw new CredentialsException(R.string.goodreads);

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new BookNotFoundException();

            default:
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
     * @param requiresSignature Flag to optionally sigh the request
     *
     * @return the raw text output.
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    String executeRaw(@NonNull final String url,
                      @SuppressWarnings("SameParameterValue") final boolean requiresSignature)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.NETWORK || DEBUG_SWITCHES.DUMP_HTTP_URL)) {
            Logger.debug(this, "executeRaw", "url=" + url);
        }

        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();

        if (requiresSignature) {
            mManager.sign(request);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsManager.waitUntilRequestAllowed();

        request.setConnectTimeout(CONNECT_TIMEOUT);
        request.setReadTimeout(READ_TIMEOUT);
        request.connect();

        int code = request.getResponseCode();
        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                return getContent(request);

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                GoodreadsManager.sHasValidCredentials = false;
                throw new CredentialsException(R.string.goodreads);

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new BookNotFoundException();

            default:
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
        InputStream in = request.getInputStream();
        if (in != null) {
            while (true) {
                int i = in.read();
                if (i == -1) {
                    break;
                }
                html.append((char) i);
            }
        }
        return html.toString();
    }
}
