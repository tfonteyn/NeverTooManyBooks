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
import com.eleybourn.bookcatalogue.utils.CredentialsException;
import com.eleybourn.bookcatalogue.utils.BookNotFoundException;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter;

/**
 * Base class for all Goodreads API handler classes.
 * <p>
 * The job of an API handler is to implement a method to run the API (e.g. 'search' in
 * {@link SearchBooksApiHandler} and to process the output.
 *
 * @author Philip Warner
 */
abstract class ApiHandler {

    private static final String ERROR_UNEXPECTED_STATUS_CODE_FROM_API =
            "Unexpected status code from API: ";
    /** XML tags/attrs we look for. */
    static final String XML_GOODREADS_RESPONSE = "GoodreadsResponse";
    static final String XML_NAME = "name";

    static final String XML_ID = "id";
    static final String XML_WORK = "work";
    static final String XML_BODY = "body";
    static final String XML_SEARCH = "search";

    static final String XML_RESULT = "results";
    static final String XML_TOTAL_RESULTS = "total-results";
    static final String XML_RESULTS_END = "results-end";
    static final String XML_RESULTS_START = "results-start";

    static final String XML_REVIEWS = "reviews";
    static final String XML_REVIEW = "review";
    static final String XML_MY_REVIEW = "my_review";

    static final String XML_AUTHORS = "authors";
    static final String XML_AUTHOR = "author";

    static final String XML_TITLE = "title";
    static final String XML_TITLE_WITHOUT_SERIES = "title_without_series";
    static final String XML_ORIGINAL_TITLE = "original_title";

    /** <language_code>en-GB</language_code> */
    static final String XML_LANGUAGE = "language_code";

    static final String XML_BOOK = "book";
    static final String XML_BEST_BOOK = "best_book";
    /** <isbn13><![CDATA[9780340198278]]></isbn13> */
    static final String XML_ISBN_13 = "isbn13";
    /** <isbn><![CDATA[0340198273]]></isbn> */
    static final String XML_ISBN = "isbn";
    /**
     * <num_pages><![CDATA[206]]></num_pages>
     * <num_pages>448</num_pages>
     */
    static final String XML_NUM_PAGES = "num_pages";
    /** <format><![CDATA[Mass Market Paperback]]></format> */
    static final String XML_FORMAT = "format";
    /** <is_ebook>false</is_ebook> */
    static final String XML_IS_EBOOK = "is_ebook";
    static final String XML_DESCRIPTION = "description";

    static final String XML_SERIES = "series";
    static final String XML_SERIES_WORK = "series_work";
    static final String XML_SERIES_WORKS = "series_works";

    static final String XML_SHELVES = "shelves";
    static final String XML_SHELF = "shelf";
    static final String XML_USER_SHELF = "user_shelf";

    /** <publisher>Coronet</publisher> */
    static final String XML_PUBLISHER = "publisher";
    /** <country_code><![CDATA[GB]]></country_code> */
    static final String XML_COUNTRY_CODE = "country_code";

    /** <publication_year>1977</publication_year> */
    static final String XML_PUBLICATION_YEAR = "publication_year";
    /** <publication_month>10</publication_month> */
    static final String XML_PUBLICATION_MONTH = "publication_month";
    /** <publication_day></publication_day> */
    static final String XML_PUBLICATION_DAY = "publication_day";

    /** <original_publication_year>1977</original_publication_year> */
    static final String XML_ORIGINAL_PUBLICATION_YEAR = "original_publication_year";
    /** <original_publication_month>10</original_publication_month> */
    static final String XML_ORIGINAL_PUBLICATION_MONTH = "original_publication_month";
    /** <original_publication_day></original_publication_day> */
    static final String XML_ORIGINAL_PUBLICATION_DAY = "original_publication_day";

    static final String XML_DATE_ADDED = "date_added";
    static final String XML_DATE_UPDATED = "date_updated";

    static final String XML_RATING = "rating";
    static final String XML_AVERAGE_RATING = "average_rating";

    static final String XML_URL = "url";
    static final String XML_USER_POSITION = "user_position";
    static final String XML_SMALL_IMAGE_URL = "small_image_url";
    static final String XML_IMAGE_URL = "image_url";
    static final String XML_EXCLUSIVE_FLAG = "exclusive_flag";
    static final String XML_START = "start";
    static final String XML_END = "end";
    static final String XML_TOTAL = "total";
    static final String XML_STARTED_AT = "started_at";
    static final String XML_READ_AT = "read_at";
    static final String XML_REVIEW_ID = "review-id";


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
    ApiHandler(@NonNull final GoodreadsManager grManager) {

        mManager = grManager;
    }

    /**
     * Create an HttpClient with specifically set buffer sizes to deal with
     * potentially exorbitant settings on some HTC handsets.
     */
    @NonNull
    private HttpClient newHttpClient() {

        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 30_000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpConnectionParams.setLinger(params, 0);
        HttpConnectionParams.setTcpNoDelay(params, false);
        return new DefaultHttpClient(params);
    }

    /**
     * Sign a request and submit it; then pass it off to a parser.
     * Wrapper for {@link #execute(HttpUriRequest, DefaultHandler, boolean)}}
     * to get all Apache API calls centralised.
     *
     * @throws CredentialsException with GoodReads
     * @throws BookNotFoundException  GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException            on other failures
     */
    void executeGet(@NonNull final String url,
                    @Nullable final DefaultHandler requestHandler,
                    @SuppressWarnings("SameParameterValue") final boolean requiresSignature)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        HttpGet request = new HttpGet(url);
        execute(request, requestHandler, requiresSignature);
    }

    /**
     * Sign a request and submit it; then pass it off to a parser.
     * Wrapper for {@link #execute(HttpUriRequest, DefaultHandler, boolean)}}
     * to get all Apache API calls centralised.
     *
     * @throws CredentialsException with GoodReads
     * @throws BookNotFoundException  GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException            on other failures
     */
    void executePost(@NonNull final String url,
                     @Nullable final Map<String, String> parameterMap,
                     @Nullable final DefaultHandler requestHandler,
                     final boolean requiresSignature)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

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

        execute(request, requestHandler, requiresSignature);
    }

    /**
     * Sign a request and submit it; then pass it off to a parser.
     *
     * @throws CredentialsException with GoodReads
     * @throws BookNotFoundException  GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException            on other failures
     */
    private void execute(@NonNull final HttpUriRequest request,
                        @Nullable final DefaultHandler requestHandler,
                        final boolean requiresSignature)
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

        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.NETWORK || DEBUG_SWITCHES.DUMP_HTTP_URL)) {
            Logger.debug(this, "execute",
                         "url=" + request.getURI());
        }

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
                InputStream is = entity.getContent();
                parseResponse(is, requestHandler);
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
     * @author Philip Warner
     */
    private void parseResponse(@NonNull final InputStream is,
                               @Nullable final DefaultHandler handler)
            throws IOException {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            SAXParser parser = factory.newSAXParser();
            parser.parse(is, handler);
            // wrap parser exceptions in an IOException
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.debugWithStackTrace(this, e);
            }
            throw new IOException(e);
        }
    }

    /**
     * Sign a request and submit it then return the raw text output.
     * Wrapper for {@link #executeRaw(HttpUriRequest, boolean)}
     * to get all Apache API calls centralised.
     *
     * @return the raw text output.
     *
     * @throws CredentialsException with GoodReads
     * @throws BookNotFoundException  GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException            on other failures
     */
    @NonNull
    String executeRaw(@NonNull final String url,
                      @SuppressWarnings("SameParameterValue") final boolean requiresSignature)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        return executeRaw(new HttpGet(url), requiresSignature);
    }

    /**
     * Sign a request and submit it then return the raw text output.
     *
     * @return the raw text output.
     *
     * @throws CredentialsException with GoodReads
     * @throws BookNotFoundException  GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException            on other failures
     */
    @NonNull
    private String executeRaw(@NonNull final HttpUriRequest request,
                              final boolean requiresSignature)
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

        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.NETWORK || DEBUG_SWITCHES.DUMP_HTTP_URL)) {
            Logger.debug(this, "executeRaw",
                         "url=" + request.getURI());
        }
        // Submit the request then process result.
        HttpResponse response = httpClient.execute(request);

        StringBuilder html = new StringBuilder();
        HttpEntity entity = response.getEntity();
        if (entity != null) {
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
        }

        int code = response.getStatusLine().getStatusCode();
        switch (code) {
            case HttpURLConnection.HTTP_OK:
            case HttpURLConnection.HTTP_CREATED:
                return html.toString();

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
}
