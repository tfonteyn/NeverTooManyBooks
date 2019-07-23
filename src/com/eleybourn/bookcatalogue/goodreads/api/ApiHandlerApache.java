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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
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
 * Replaced by {@link ApiHandler} using native HttpURLConnection.
 * <p>
 * Keeping this class around as-is for a while longer during testing.
 * <p>
 * Gradle:
 * // pull apache http explicitly. Signpost would get an older version otherwise.
 * implementation 'org.apache.httpcomponents:httpcore:4.4.11'
 * implementation 'org.apache.httpcomponents:httpmime:4.5.9'
 * implementation 'oauth.signpost:signpost-commonshttp4:1.2.1.2'
 * <p>
 * Base class for all Goodreads API handler classes using Apache Commons HTTP.
 * <p>
 * The job of an API handler is to implement a method to run the API (e.g. 'search' in
 * {@link SearchBooksApiHandler} and to process the output.
 *
 * @author Philip Warner
 */
@Deprecated
abstract class ApiHandlerApache {

    /** initial connection time to websites timeout. */
    private static final int CONNECT_TIMEOUT = 30_000;
    /** timeout for requests to website. */
//    private static final int READ_TIMEOUT = 10_000;

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
    ApiHandlerApache(@NonNull final GoodreadsManager grManager) {
        if (BuildConfig.DEBUG) {
            Logger.debug(this, "ApiHandler", "APACHE");
        }
        mManager = grManager;
    }

    /**
     * Create an HttpClient with specifically set buffer sizes to deal with
     * potentially exorbitant settings on some HTC handsets.
     */
    @NonNull
    private HttpClient newHttpClient() {

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, CONNECT_TIMEOUT);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpConnectionParams.setLinger(params, 0);
        HttpConnectionParams.setTcpNoDelay(params, false);
        return new DefaultHttpClient(params);
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

        HttpGet request = new HttpGet(url);
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
            Logger.debug(this, "executePost", "url=" + url);
        }

        HttpPost request = new HttpPost(url);

        if (parameterMap != null) {
            List<NameValuePair> parameters = new ArrayList<>();
            for (Map.Entry<String, String> entry : parameterMap.entrySet()) {
                parameters.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
            // TEST: both were used in separate call. Unifying to UTF-8 here.
            request.setEntity(new UrlEncodedFormEntity(parameters, "UTF-8"));
//            request.setEntity(new UrlEncodedFormEntity(parameters));
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
    private void execute(@NonNull final HttpUriRequest request,
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

        // Get a new client
        HttpClient httpClient = newHttpClient();

        // Submit the request then process result.
        HttpResponse response = httpClient.execute(request);

        HttpEntity entity = response.getEntity();
        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.NETWORK || DEBUG_SWITCHES.DUMP_HTTP_RESPONSE)) {
            entity.writeTo(System.out);
            if (!entity.isRepeatable()) {
                Logger.debug(this, "execute",
                             "dumped response not repeatable, ABORTING");
                return;
            }
        }

        int code = response.getStatusLine().getStatusCode();
        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                parseResponse(entity, requestHandler);
                break;

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                GoodreadsManager.sHasValidCredentials = false;
                throw new CredentialsException(R.string.goodreads);

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new BookNotFoundException();

            default:
                throw new IOException(ERROR_UNEXPECTED_STATUS_CODE_FROM_API
                                              + response.getStatusLine().getStatusCode()
                                              + '/' + response.getStatusLine().getReasonPhrase());
        }
    }


    /**
     * Pass a response off to a parser.
     *
     * @param entity         the executed request from which to read
     * @param requestHandler (optional) handler for the parser
     *
     * @throws IOException on failures (any parser exceptions are wrapped)
     */
    private void parseResponse(@NonNull final HttpEntity entity,
                               @Nullable final DefaultHandler requestHandler)
            throws IOException {


        try (InputStream is = entity.getContent()) {
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

        HttpUriRequest request = new HttpGet(url);

        if (requiresSignature) {
            mManager.sign(request);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsManager.waitUntilRequestAllowed();

        // Get a new client
        HttpClient httpClient = newHttpClient();

        // Submit the request then process result.
        HttpResponse response = httpClient.execute(request);

        int code = response.getStatusLine().getStatusCode();
        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                return getContent(response.getEntity());

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                GoodreadsManager.sHasValidCredentials = false;
                throw new CredentialsException(R.string.goodreads);

            case HttpURLConnection.HTTP_NOT_FOUND:
                throw new BookNotFoundException();

            default:
                throw new IOException(ERROR_UNEXPECTED_STATUS_CODE_FROM_API
                                              + response.getStatusLine().getStatusCode()
                                              + '/' + response.getStatusLine().getReasonPhrase());
        }
    }

    /**
     * Read the request into a single String.
     *
     * @param entity the executed request from which to read
     *
     * @return the content as a single string
     *
     * @throws IOException on failures
     */
    private String getContent(@NonNull final HttpEntity entity)
            throws IOException {
        StringBuilder html = new StringBuilder();
        InputStream in = entity.getContent();
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
