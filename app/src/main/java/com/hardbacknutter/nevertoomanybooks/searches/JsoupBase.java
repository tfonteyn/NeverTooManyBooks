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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.EOFException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLProtocolException;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;

/**
 * Provide a more or less robust base to load a url and parse the html with Jsoup.
 * The extra safeguards are needed due to the size of some html pages we're parsing.
 */
public abstract class JsoupBase {

    private static final String TAG = "JsoupBase";

//    /** Not needed for now, but handy for testing. */
//    protected static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
//                                             + " AppleWebKit/537.36 (KHTML, like Gecko)"
//                                             + " Chrome/76.0.3809.100 Safari/537.36";

    /** The parsed downloaded web page. */
    protected Document mDoc;

    /** If the site drops connection, we retry once. */
    private boolean mRetry = true;

    private int mConnectTimeout;
    private int mReadTimeout;
    /** {@code null} by default: for Jsoup to figure it out. */
    private String mCharSetName;

    /**
     * Constructor.
     */
    protected JsoupBase() {
    }

    /**
     * Constructor for mocking.
     *
     * @param doc the pre-loaded Jsoup document.
     */
    @VisibleForTesting
    public JsoupBase(@NonNull final Document doc) {
        mDoc = doc;
    }

    /**
     * Optionally override the connection timeout.
     *
     * @param connectTimeout to use
     */
    protected void setConnectTimeout(
            @SuppressWarnings("SameParameterValue") final int connectTimeout) {
        mConnectTimeout = connectTimeout;
    }

    /**
     * Optionally override the read timeout.
     *
     * @param readTimeout to use
     */
    protected void setReadTimeout(
            @SuppressWarnings("SameParameterValue") final int readTimeout) {
        mReadTimeout = readTimeout;
    }

    /**
     * Optionally override the website character set.
     * Used when the html page contains an incorrect header charset.
     *
     * @param charSetName to use
     */
    protected void setCharSetName(
            @SuppressWarnings("SameParameterValue") final String charSetName) {
        mCharSetName = charSetName;
    }

    /**
     * Fetch the URL and parse it into {@link #mDoc}.
     * <p>
     * the connect call uses a set of defaults. For example the user-agent:
     * {@link HttpConnection#DEFAULT_UA}
     * <p>
     * The content encoding by default is: "Accept-Encoding", "gzip"
     *
     * @param appContext Application context
     * @param url        to fetch
     *
     * @return the actual URL for the page we got (after redirects etc), or {@code null}
     * on failure to load.
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @WorkerThread
    @Nullable
    public String loadPage(@NonNull final Context appContext,
                           @NonNull final String url)
            throws SocketTimeoutException {

        if (mDoc == null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                Log.d(TAG, "loadPage|REQUESTED|url=\"" + url + '\"');
            }

            try (TerminatorConnection tCon = new TerminatorConnection(appContext, url)) {
                HttpURLConnection con = tCon.getHttpURLConnection();

                // added due to https://github.com/square/okhttp/issues/1517
                // it's a server issue, this is a workaround.
                con.setRequestProperty("Connection", "close");

                if (mConnectTimeout > 0) {
                    con.setConnectTimeout(mConnectTimeout);
                }
                if (mReadTimeout > 0) {
                    con.setReadTimeout(mReadTimeout);
                }

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                    Log.d(TAG, "loadPage|BEFORE open|con.getURL()=" + con.getURL().toString());
                }
                // GO!
                tCon.open();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                    Log.d(TAG, "loadPage|AFTER open|con.getURL()=" + con.getURL());
                    Log.d(TAG, "loadPage|AFTER open|con.getHeaderField(\"location\")="
                               + con.getHeaderField("location"));
                }

                // the original url will change after a redirect.
                // We need the actual url for further processing.
                String locationHeader = con.getHeaderField("location");
                if (locationHeader == null || locationHeader.isEmpty()) {
                    locationHeader = con.getURL().toString();

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                        Log.d(TAG, "loadPage|location header not set, using url");
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
                mDoc = Jsoup.parse(tCon.getInputStream(), mCharSetName, locationHeader);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.JSOUP) {
                    Log.d(TAG, "loadPage|AFTER parsing|mDoc.location()=" + mDoc.location());
                }

            } catch (@NonNull final HttpStatusException e) {
                if (BuildConfig.DEBUG) {
                    Logger.error(appContext, TAG, e, "url=" + url);
                }
                return null;

            } catch (@NonNull final EOFException | SSLProtocolException e) {
                // EOFException: happens often with ISFDB...
                // Google search says it's a server issue.
                // Not so sure that Google search is correct thought but what do I know...
                //
                //
                // SSLProtocolException: happens often with stripinfo.be; at least while running
                // in the emulator.
                // javax.net.ssl.SSLProtocolException:
                //  Read error: ssl=0x91461c80: Failure in SSL library, usually a protocol error
                //  error:1e000065:Cipher functions:OPENSSL_internal:BAD_DECRYPT
                //  external/boringssl/src/crypto/cipher/e_aes.c:1143 0xa0d78e9f:0x00000000)
                //  error:1000008b:SSL routines:OPENSSL_internal:DECRYPTION_FAILED_OR_BAD_RECORD_MAC
                //  external/boringssl/src/ssl/tls_record.c:277 0xa0d78e9f:0x00000000)
                // at com.android.org.conscrypt.NativeCrypto.SSL_read(Native Method)
                // ...
                // at com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection.open
                // at com.hardbacknutter.nevertoomanybooks.searches.JsoupBase.loadPage

                Logger.warn(appContext, TAG, "loadPage"
                                             + "|e=" + e.getLocalizedMessage()
                                             + "|url=\"" + url + '\"');
                // retry once.
                if (mRetry) {
                    mRetry = false;
                    mDoc = null;
                    return loadPage(appContext, url);
                } else {
                    return null;
                }

            } catch (@NonNull final SocketTimeoutException e) {
                throw e;

            } catch (@NonNull final IOException e) {
                Logger.error(appContext, TAG, e, url);
                return null;
            }
            // reset the flag.
            mRetry = true;
        }

        return mDoc.location();
    }
}
