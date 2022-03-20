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
package com.hardbacknutter.nevertoomanybooks.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.net.ssl.SSLProtocolException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Provide a more or less robust base to load a url and parse the html with Jsoup.
 * The extra safeguards are needed due to the size of some html pages we're parsing.
 */
public class JsoupLoader {

    /** Log tag. */
    private static final String TAG = "JsoupLoader";
    @NonNull
    private final FutureHttpGet<Document> mFutureHttpGet;
    /** The downloaded and parsed web page. */
    @Nullable
    private Document mDoc;
    /** The <strong>request</strong> url for the web page. */
    @Nullable
    private String mDocRequestUrl;
    /** The user agent to send. Call {@link #setUserAgent(String)} to override. */
    @Nullable
    private String mUserAgent = HttpUtils.USER_AGENT_VALUE;
    /** {@code null} by default: for Jsoup to figure it out. */
    @Nullable
    private String mCharSetName;

    public JsoupLoader(@NonNull final FutureHttpGet<Document> futureHttpGet) {
        mFutureHttpGet = futureHttpGet;
    }

    /**
     * Optionally override the user agent; can be set to {@code null} to revert to JSoup default.
     *
     * @param userAgent string to use
     */
    @SuppressWarnings("unused")
    public void setUserAgent(@Nullable final String userAgent) {
        mUserAgent = userAgent;
    }

    /**
     * Optionally override the website character set.
     * Used when the html page contains an incorrect header charset.
     *
     * @param charSetName to use
     */
    public void setCharSetName(@SuppressWarnings("SameParameterValue")
                               @NonNull final String charSetName) {
        mCharSetName = charSetName;
    }

    /**
     * Reset the loader.
     */
    public void reset() {
        mDoc = null;
    }

    /**
     * Fetch the URL and parse it into {@link #mDoc}.
     * Will silently return if it has downloaded the document before.
     * Call {@link #reset()} before to force a clean/new download.
     * <p>
     * The content encoding is: "Accept-Encoding", "gzip"
     *
     * @param url to fetch
     *
     * @return the parsed Document
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @NonNull
    public Document loadDocument(@NonNull final String url)
            throws IOException {

        // are we requesting the same url again ?
        if (mDoc != null && url.equals(mDocRequestUrl)) {
            // return the previously parsed doc
            return mDoc;
        }

        // new download
        mDoc = null;
        mDocRequestUrl = url;

        // If the site drops connection, we retry once
        // Note this is NOT the same as the retry mechanism of TerminatorConnection.
        // The latter is a retry for the initial connect only.
        // This retry is for when a successful connection gets dropped mid-read
        // specifically due to SSLProtocolException | EOFException
        int attemptsLeft = 2;

        while (attemptsLeft > 0) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                Logger.d(TAG, "loadDocument",
                         "REQUESTED|mDocRequestUrl=\"" + mDocRequestUrl + '\"');
            }

            try {
                // Don't retry if the initial connection fails...
                mFutureHttpGet.setRetryCount(0);

                mDoc = mFutureHttpGet.get(mDocRequestUrl, request -> {
                    // added due to https://github.com/square/okhttp/issues/1517
                    // it's a server issue, this is a workaround.
                    request.setRequestProperty(HttpUtils.CONNECTION, HttpUtils.CONNECTION_CLOSE);
                    // some sites refuse to return content if they don't like the user-agent
                    if (mUserAgent != null) {
                        request.setRequestProperty(HttpUtils.USER_AGENT, mUserAgent);
                    }

                    //GO!
                    try (BufferedInputStream is = new BufferedInputStream(
                            request.getInputStream())) {

                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                            Logger.d(TAG, "loadDocument",
                                     "AFTER open"
                                     + "\ncon.getURL=" + request.getURL()
                                     + "\nlocation  =" + request.getHeaderField(
                                             HttpUtils.LOCATION));
                        }

                        // the original url will change after a redirect.
                        // We need the actual url for further processing.
                        String locationHeader = request.getHeaderField(HttpUtils.LOCATION);
                        if (locationHeader == null || locationHeader.isEmpty()) {
                            locationHeader = request.getURL().toString();
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                                Logger.d(TAG, "loadDocument", "location header not set, using url");
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

                        However that is WRONG (org.jsoup:jsoup:1.11.3)
                        It will NOT resolve the redirect itself and 'location' == 'baseUri'
                        */
                        final Document document = Jsoup.parse(is, mCharSetName, locationHeader);
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                            Logger.d(TAG, "loadDocument",
                                     "AFTER parsing|mDoc.location()=" + document.location());
                        }

                        return document;

                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                return mDoc;

            } catch (@NonNull final SSLProtocolException | EOFException e) {
                mDoc = null;

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
                // ...
                // Log it as WARN, so at least we can get to know the frequency of these issues.
                Logger.warn(TAG, "loadDocument"
                                 + "|e=" + e.getMessage()
                                 + "|mDocRequestUrl=\"" + mDocRequestUrl + '\"');
                // we'll retry.
                attemptsLeft--;
                if (attemptsLeft == 0) {
                    // IOException
                    throw e;
                }

            } catch (@NonNull final StorageException e) {
                // This is only here due to FutureHttpGet declaring StorageException;
                // which her will never be thrown.
                throw new IOException(e);

            } catch (@NonNull final IOException e) {
                mDoc = null;

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                    Logger.error(TAG, e, "mDocRequestUrl=" + mDocRequestUrl);
                }
                throw e;
            }
        }

        // Shouldn't get here ... flw
        throw new IOException("Failed to get: " + mDocRequestUrl);
    }

    public void cancel() {
        synchronized (mFutureHttpGet) {
            mFutureHttpGet.cancel();
        }
    }
}
