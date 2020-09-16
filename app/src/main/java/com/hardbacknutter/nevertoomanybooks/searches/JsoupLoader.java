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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import javax.net.ssl.SSLProtocolException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;

/**
 * Provide a more or less robust base to load a url and parse the html with Jsoup.
 * The extra safeguards are needed due to the size of some html pages we're parsing.
 */
public class JsoupLoader {


    /**
     * RELEASE: Chrome 2020-09-16. Continuously update to latest version.
     * - KBNL site does not return full data unless the user agent is set to a valid browser.
     * <p>
     * Set by default. Call {@link #setUserAgent(String)} to override.
     */
    private static final String USER_AGENT_VALUE =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
            + " AppleWebKit/537.36 (KHTML, like Gecko)"
            + " Chrome/86.0.4209.0 Safari/537.36";
    /** Log tag. */
    private static final String TAG = "JsoupLoader";

    /** HTTP Request Header. */
    private static final String HEADER_CONNECTION = "Connection";
    /** HTTP Request Header. */
    private static final String HEADER_USER_AGENT = "User-Agent";
    /** HTTP Response Header. */
    private static final String HEADER_LOCATION = "location";


    /** The downloaded and parsed web page. */
    @Nullable
    private Document mDoc;
    /** The <strong>request</strong> url for the web page. */
    @Nullable
    private String mDocRequestUrl;

    /** The user agent to send. */
    @Nullable
    private String mUserAgent = USER_AGENT_VALUE;

    /** {@code null} by default: for Jsoup to figure it out. */
    @Nullable
    private String mCharSetName;

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
    void setCharSetName(@SuppressWarnings("SameParameterValue")
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
     * @param context      Current context
     * @param url          to fetch
     * @param searchEngine to make the connection for
     *
     * @return the parsed Document, or {@code null} on failure to load.
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @Nullable
    Document loadDocument(@NonNull final Context context,
                          @NonNull final String url,
                          @NonNull final SearchEngine searchEngine)
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
        int attempts = 2;
        while (attempts > 0) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                Logger.d(TAG, "loadDocument|REQUESTED|url=\"" + url + '\"');
            }

            try (TerminatorConnection con = searchEngine.createConnection(url, true)) {
                // Don't retry if the initial connection fails...
                // We use our own retry mechanism here.
                con.setRetryCount(0);

                // added due to https://github.com/square/okhttp/issues/1517
                // it's a server issue, this is a workaround.
                con.setRequestProperty(HEADER_CONNECTION, "close");

                if (mUserAgent != null) {
                    con.setRequestProperty(HEADER_USER_AGENT, mUserAgent);
                }

                // GO!
                final InputStream inputStream = con.getInputStream();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                    Logger.d(TAG, "loadDocument|AFTER open"
                                  + "\ncon.getURL=" + con.getURL()
                                  + "\nlocation  =" + con.getHeaderField(HEADER_LOCATION));
                }

                // the original url will change after a redirect.
                // We need the actual url for further processing.
                String locationHeader = con.getHeaderField(HEADER_LOCATION);
                if (locationHeader == null || locationHeader.isEmpty()) {
                    //noinspection ConstantConditions
                    locationHeader = con.getURL().toString();

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                        Logger.d(TAG, "loadDocument|location header not set, using url");
                    }
                }

                /*
                 VERY IMPORTANT: Explicitly set the baseUri to the location header.
                 JSoup by default uses the absolute path from the inputStream
                 and sets that as the document 'location'
                 From JSoup docs:

                * Get the URL this Document was parsed from. If the starting URL is a redirect,
                * this will return the final URL from which the document was served from.
                * @return location
                public String location() {
                    return location;
                }

                However that is WRONG (org.jsoup:jsoup:1.11.3)
                It will NOT resolve the redirect itself and 'location' == 'baseUri'
                */
                mDoc = Jsoup.parse(inputStream, mCharSetName, locationHeader);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                    Logger.d(TAG, "loadDocument|AFTER parsing|mDoc.location()=" + mDoc.location());
                }

                // Success
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
                // at com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection.open
                // at com.hardbacknutter.nevertoomanybooks.searches.JsoupLoader.loadPage

                // Log it as WARN, so at least we can get to know the frequency of these issues.
                Logger.warn(context, TAG, "loadDocument"
                                          + "|e=" + e.getLocalizedMessage()
                                          + "|url=\"" + url + '\"');
                // we'll retry.
                attempts--;
                if (attempts == 0) {
                    throw e;
                }

            } catch (@NonNull final IOException e) {
                mDoc = null;

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                    Logger.error(context, TAG, e, "url=" + url);
                }
                throw e;
            }
        }

        // Shouldn't get here ... flw
        return null;
    }
}
