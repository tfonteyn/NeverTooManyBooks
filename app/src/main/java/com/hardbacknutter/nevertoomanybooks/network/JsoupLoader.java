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
package com.hardbacknutter.nevertoomanybooks.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLProtocolException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

/**
 * Provide a more or less robust base to load a url and parse the HTML with Jsoup.
 * The extra safeguards are needed due to the size of some HTML pages we're parsing.
 */
public class JsoupLoader {

    /** Log tag. */
    private static final String TAG = "JsoupLoader";

    @NonNull
    private final FutureHttp<Document> httpGet;
    @NonNull
    private final EngineId engineId;
    private final boolean logEnabled;
    /** The downloaded and parsed web page. */
    @Nullable
    private Document document;
    /** The <strong>request</strong> url for the web page. */
    @Nullable
    private String requestUrl;
    /** {@code null} by default: for Jsoup to figure it out. */
    @Nullable
    private String charSetName;
    @Nullable
    private SSLContext sslContext;

    /**
     * Constructor.
     *
     * @param httpGet  to use
     * @param engineId to use
     */
    public JsoupLoader(@NonNull final FutureHttp<Document> httpGet,
                       @NonNull final EngineId engineId) {
        this.httpGet = httpGet;
        this.engineId = engineId;
        //noinspection DataFlowIssue
        this.logEnabled = engineId.getConfig().isLogHttpGetRequests();
    }

    /**
     * Optionally override the website character set.
     * Used when the HTML page contains an incorrect header charset.
     *
     * @param charSetName to use; or {@code null} to auto-select.
     */
    public void setCharSetName(@Nullable final String charSetName) {
        this.charSetName = charSetName;
    }

    /**
     * Set a custom SSLContext.
     *
     * @param sslContext to use
     */
    public void setSslContext(@Nullable final SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Reset the loader.
     */
    public void reset() {
        document = null;
    }

    @WorkerThread
    @NonNull
    public Document loadDocument(@NonNull final Context context,
                                 @NonNull final String url,
                                 @Nullable final Map<String, String> headers)
            throws IOException {

        // are we requesting the same url again ?
        if (document != null && url.equals(requestUrl)) {
            // return the previously parsed doc
            return document;
        }

        return loadDocument(context, Parser.htmlParser(), url, headers);
    }

    /**
     * Fetch the URL and parse it into {@link #document}.
     * Will silently return if it has downloaded the document before.
     * Call {@link #reset()} before to force a clean/new download.
     * <p>
     * The content encoding is: "Accept-Encoding", "gzip"
     *
     * @param context Current context
     * @param parser  to use
     * @param url     to fetch
     * @param headers optional
     *
     * @return the parsed Document
     *
     * @throws IOException on generic/other IO failures
     */
    @WorkerThread
    @NonNull
    public Document loadDocument(@NonNull final Context context,
                                 @NonNull final Parser parser,
                                 @NonNull final String url,
                                 @Nullable final Map<String, String> headers)
            throws IOException {

        // are we requesting the same url again ?
        if (document != null && url.equals(requestUrl)) {
            // return the previously parsed doc
            return document;
        }

        // new download
        document = null;
        requestUrl = url;

        // If the site drops connection, we retry once.
        // Note this is NOT the same as the retry mechanism of FutureHttp.
        // The latter is a retry for the initial connect only.
        //
        // This retry tries to solve the situation when a successful connection gets
        // dropped mid-read specifically due to SSLProtocolException | EOFException
        int attemptsLeft = 2;

        while (attemptsLeft > 0) {
            if (logEnabled) {
                LoggerFactory.getLogger().d(TAG, "loadDocument|get",
                                            "attemptsLeft=" + attemptsLeft,
                                            "requestUrl=`" + requestUrl + '`');
            }

            try {
                httpGet.setSSLContext(sslContext);

                if (headers != null) {
                    httpGet.setHeaders(headers);
                }

                document = httpGet.get(requestUrl, (response, is) ->
                        processResponse(response, is, parser));
                //noinspection DataFlowIssue
                return document;

            } catch (@NonNull final SSLProtocolException | EOFException e) {
                document = null;

                // EOFException: happens often with ISFDB...
                // This is after a successful connection was made.
                // Google search says it's a server issue.
                // Not so sure that Google search is correct thought but what do I know...

                // SSLProtocolException: happens often with stripinfo.be;
                // ... at least while running in the emulator:
                // javax.net.ssl.SSLProtocolException:
                //  Read error: ssl=0x91461c80: Failure in SSL library, usually a protocol error
                //  error:1e000065:Cipher functions:OPENSSL_internal:BAD_DECRYPT
                //  external/boringssl/src/crypto/cipher/e_aes.c:1143 0xa0d78e9f:0x00000000)
                //  error:1000008b:SSL routines:OPENSSL_internal:DECRYPTION_FAILED_OR_BAD_RECORD_MAC
                //  external/boringssl/src/ssl/tls_record.c:277 0xa0d78e9f:0x00000000)
                // at com.android.org.conscrypt.NativeCrypto.SSL_read(Native Method)
                // 2025-04-13: not seen for quite some time now.
                // ...
                if (logEnabled) {
                    LoggerFactory.getLogger().w(TAG, "loadDocument",
                                                "e=" + e.getMessage(),
                                                "requestUrl=\"" + requestUrl + '\"');
                }
                // we'll retry.
                attemptsLeft--;
                if (attemptsLeft == 0) {
                    // IOException
                    throw e;
                }

            } catch (@NonNull final HttpNotFoundException e) {
                // Getting a 404 here is usually NOT an actual problem.
                // We don't want to change the http response checker for debugging reason,
                // but we DO change the message here:
                e.setLocalizedMessage(context.getString(R.string.warning_book_not_found));
                throw e;

            } catch (@NonNull final StorageException e) {
                // This is only here due to FutureHttp declaring StorageException;
                // which here will never be thrown.
                throw new IOException(e);

            } catch (@NonNull final IOException e) {
                document = null;

                if (logEnabled) {
                    LoggerFactory.getLogger().e(TAG, e, "loadDocument",
                                                "requestUrl=" + requestUrl);
                }
                throw e;
            }
        }

        throw new IOException("Failed to get: " + requestUrl);
    }

    @NonNull
    private Document processResponse(@NonNull final HttpURLConnection response,
                                     @NonNull final InputStream is,
                                     @NonNull final Parser parser)
            throws IOException {
        // the original url will change after a redirect.
        // We need the actual url for further processing.
        String locationHeader = response.getHeaderField(HttpConstants.RESPONSE_HEADER_LOCATION);
        final String url = response.getURL().toString();

        if (logEnabled) {
            LoggerFactory.getLogger().d(TAG, "processResponse",
                                        "response.getURL()=" + url
                                        + "\nlocation  =" + locationHeader);
        }

        if (locationHeader == null || locationHeader.isEmpty()) {
            locationHeader = url;
            if (logEnabled) {
                LoggerFactory.getLogger().d(TAG, "processResponse",
                                            "location header not set, using url");
            }
        }

        /*
         VERY IMPORTANT: Explicitly set the baseUri to the location header.
         JSoup by default uses the absolute path from the inputStream
         and sets that as the document 'location'
         From JSoup docs:

         Get the URL this Document was parsed from.
         If the starting URL is a redirect, this will return the
         final URL from which the document was served from.

         @return location
         public String location() {
            return location;
         }

        However, that is WRONG (org.jsoup:jsoup:1.11.3)
        It will NOT resolve the redirect itself and 'location' == 'baseUri'
        */
        final Document parsedDocument = Jsoup.parse(is, charSetName, locationHeader, parser);
        if (logEnabled) {
            LoggerFactory.getLogger()
                         .d(TAG, "processResponse|disconnect",
                            "AFTER parsing|document.location()="
                            + parsedDocument.location());
        }

        return parsedDocument;
    }

    public void cancel() {
        synchronized (httpGet) {
            httpGet.cancel();
        }
    }
}
