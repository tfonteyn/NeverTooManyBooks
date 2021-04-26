/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.api;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

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
import com.hardbacknutter.nevertoomanybooks.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.network.HttpStatusException;
import com.hardbacknutter.nevertoomanybooks.network.HttpUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteParsingException;
import com.hardbacknutter.nevertoomanybooks.searchengines.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.utils.xml.SAXHelper;

/**
 * Base class for all Goodreads handler classes.
 * <p>
 * The job of a handler is to implement a method to run the Goodreads request
 * and to process the output.
 */
public abstract class ApiHandler {

    /** Log tag. */
    private static final String TAG = "ApiHandler";

    @NonNull
    protected final GoodreadsAuth mGrAuth;
    @NonNull
    final Context mContext;
    @NonNull
    private final SearchEngineConfig mSEConfig;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param grAuth  Authentication handler
     */
    protected ApiHandler(@NonNull final Context context,
                         @NonNull final GoodreadsAuth grAuth) {
        mContext = context;
        mGrAuth = grAuth;

        mSEConfig = SearchEngineRegistry.getInstance().getByEngineId(SearchSites.GOODREADS);
    }

    /**
     * Sign a request and GET it; then pass it off to a parser.
     *
     * @param url               to GET
     * @param parameterMap      (optional) parameters to add to the url
     * @param requiresSignature Flag to optionally sign the request
     * @param requestHandler    (optional) handler for the parser
     *
     * @throws SiteParsingException on a decoding/parsing of data issue
     * @throws IOException          on failures
     */
    @WorkerThread
    void executeGet(@NonNull final String url,
                    @Nullable final Map<String, String> parameterMap,
                    final boolean requiresSignature,
                    @Nullable final DefaultHandler requestHandler)
            throws SiteParsingException, IOException {

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
        request.setConnectTimeout(mSEConfig.getConnectTimeoutInMs());
        request.setReadTimeout(mSEConfig.getReadTimeoutInMs());

        if (requiresSignature) {
            mGrAuth.signGetRequest(request);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsSearchEngine.THROTTLER.waitUntilRequestAllowed();
        // explicit connect for clarity
        request.connect();

        parseResponse(request, requestHandler);
    }

    /**
     * Sign a request and POST it; then pass it off to a parser.
     *
     * @param url               to POST
     * @param parameterMap      (optional) parameters to add to the POST
     * @param requiresSignature Flag to optionally sign the request
     * @param requestHandler    (optional) handler for the parser
     *
     * @throws SiteParsingException on a decoding/parsing of data issue
     * @throws IOException             on failures
     */
    @WorkerThread
    void executePost(@NonNull final String url,
                     @Nullable final Map<String, String> parameterMap,
                     @SuppressWarnings("SameParameterValue") final boolean requiresSignature,
                     @Nullable final DefaultHandler requestHandler)
            throws SiteParsingException, IOException {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "executePost|url=\"" + url + '\"');
        }

        final HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setRequestMethod(HttpUtils.POST);
        request.setDoOutput(true);
        request.setConnectTimeout(mSEConfig.getConnectTimeoutInMs());
        request.setReadTimeout(mSEConfig.getReadTimeoutInMs());

        if (requiresSignature) {
            mGrAuth.signPostRequest(request, parameterMap);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsSearchEngine.THROTTLER.waitUntilRequestAllowed();
        // explicit connect for clarity
        request.connect();

        // Now the actual POST payload consisting of the parameters (FORM data)
        if (parameterMap != null) {
            final Uri.Builder builder = new Uri.Builder();
            for (final Map.Entry<String, String> entry : parameterMap.entrySet()) {
                builder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
            final String query = builder.build().getEncodedQuery();
            if (query != null) {
                try (OutputStream os = request.getOutputStream();
                     Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                     Writer writer = new BufferedWriter(osw)) {
                    writer.write(query);
                    writer.flush();
                }
            }
        }

        parseResponse(request, requestHandler);
    }

    /**
     * Check the response code; then pass a successful response off to a parser.
     *
     * @param request        to execute
     * @param requestHandler (optional) handler for the parser
     *
     * @throws CredentialsException  on login failure
     * @throws HttpNotFoundException the URL was not found
     * @throws HttpStatusException   on other HTTP failures
     * @throws IOException           on other failures
     */
    @WorkerThread
    private void parseResponse(@NonNull final HttpURLConnection request,
                               @Nullable final DefaultHandler requestHandler)
            throws CredentialsException, HttpNotFoundException, HttpStatusException, IOException,
                   SiteParsingException {

        try {
            HttpUtils.checkResponseCode(request, R.string.site_goodreads);

            try (InputStream is = request.getInputStream()) {
                final SAXParserFactory factory = SAXParserFactory.newInstance();
                final SAXParser parser = factory.newSAXParser();
                parser.parse(is, requestHandler);
            }

        } catch (@NonNull final CredentialsException e) {
            GoodreadsAuth.invalidateCredentials();
            throw e;

        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "parseResponse", e);
            }
            // unwrap SAXException which are really IOExceptions
            SAXHelper.unwrapIOException(e);
            // wrap parser exceptions in an IOException /
            throw new SiteParsingException(SearchSites.GOODREADS, e);

        } finally {
            request.disconnect();
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
     * @throws CredentialsException  on login failure
     * @throws HttpNotFoundException the URL was not found
     * @throws HttpStatusException   on other HTTP failures
     * @throws IOException           on other failures
     */
    @NonNull
    @WorkerThread
    String executeRawGet(@NonNull final String url,
                         @SuppressWarnings("SameParameterValue") final boolean requiresSignature)
            throws CredentialsException, HttpNotFoundException, HttpStatusException, IOException {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "executeRawGet|url=\"" + url + '\"');
        }

        final HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        request.setConnectTimeout(mSEConfig.getConnectTimeoutInMs());
        request.setReadTimeout(mSEConfig.getReadTimeoutInMs());

        if (requiresSignature) {
            mGrAuth.signGetRequest(request);
        }

        // Make sure we follow Goodreads ToS (no more than 1 request/second).
        GoodreadsSearchEngine.THROTTLER.waitUntilRequestAllowed();
        // explicit connect for clarity
        request.connect();

        try {
            HttpUtils.checkResponseCode(request, R.string.site_goodreads);

            // Read the response into a single String.
            final StringBuilder content = new StringBuilder();
            final InputStream is = request.getInputStream();
            if (is != null) {
                while (true) {
                    final int i = is.read();
                    if (i == -1) {
                        break;
                    }
                    content.append((char) i);
                }
            }
            return content.toString();

        } catch (@NonNull final CredentialsException e) {
            GoodreadsAuth.invalidateCredentials();
            throw e;

        } finally {
            request.disconnect();
        }
    }
}
