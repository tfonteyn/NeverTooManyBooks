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

import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Wrapping a HttpURLConnection and BufferedInputStream with timeout close() support.
 * <p>
 * <p>
 * To be clear: <strong>this class is for GET requests only</strong>.
 * It supports http/https connections and by setting the standard "Authorization" header
 * basic/digest authentication.
 * It does <strong>NOT</strong> support signed requests.
 * <p>
 * <p>
 * The use of this class is due to a potential problem with failed timeouts as nicely explained
 * <a href="http://thushw.blogspot.hu/2010/10/java-urlconnection-provides-no-fail.html">
 * here</a>
 * <p>
 * So...we will use a background thread to kill the connection after a set timeout.
 * <p>
 * Note that the Goodreads classes and image download use the standard HttpURLConnection directly.
 * <p>
 * TODO: add support for POST and signing
 */
@WorkerThread
public final class TerminatorConnection
        implements AutoCloseable {

    /** Log tag. */
    private static final String TAG = "TerminatorConnection";
    /** timeout for opening a connection to a website. */
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** timeout for requests to website. */
    private static final int READ_TIMEOUT_MS = 10_000;
    /** kill connections after this delay. */
    private static final int KILL_TIMEOUT_MS = 60_000;
    /** The default number of times we try to connect; i.e. one RETRY. */
    private static final int NR_OF_TRIES = 2;
    /** milliseconds to wait between retries. This is in ADDITION to the Throttler. */
    private static final int RETRY_AFTER_MS = 1_000;
    @Nullable
    private Throttler mThrottler;
    @Nullable
    private HttpURLConnection mRequest;
    @Nullable
    private BufferedInputStream mInputStream;
    @Nullable
    private Thread mClosingThread;
    /** DEBUG: Indicates close() has been called. Also see {@link Closeable#close()}. */
    private boolean mCloseWasCalled;
    /** see {@link #setRetryCount(int)}. */
    private int mNrOfTries = NR_OF_TRIES;

    /**
     * Constructor.
     *
     * @param urlStr URL to retrieve
     *
     * @throws IOException on failure
     */
    @WorkerThread
    public TerminatorConnection(@NonNull final String urlStr)
            throws IOException {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "Constructor|url=\"" + urlStr + '\"');
        }

        try {
            // Creates the connection, but does not connect to the remote server.
            // We'll do that in getInputStream() / open()
            mRequest = (HttpURLConnection) new URL(urlStr).openConnection();

        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                Logger.e(TAG, "url=" + urlStr, e);
            }
            throw e;
        }

        // Don't trust the caches; they have proven to be cumbersome.
        mRequest.setUseCaches(false);
    }

    /**
     * Set the optional connect-timeout.
     *
     * @param timeoutInMs in millis, use {@code 0} for system default
     */
    @NonNull
    public TerminatorConnection setConnectTimeout(@IntRange(from = 0) final int timeoutInMs) {
        Objects.requireNonNull(mRequest, "mRequest");

        if (timeoutInMs > 0) {
            mRequest.setConnectTimeout(timeoutInMs);
        } else {
            mRequest.setConnectTimeout(CONNECT_TIMEOUT_MS);
        }
        return this;
    }

    /**
     * Set the optional read-timeout.
     *
     * @param timeoutInMs in millis, use {@code 0} for system default
     */
    @NonNull
    public TerminatorConnection setReadTimeout(@IntRange(from = 0) final int timeoutInMs) {
        Objects.requireNonNull(mRequest, "mRequest");

        if (timeoutInMs > 0) {
            mRequest.setReadTimeout(timeoutInMs);
        } else {
            mRequest.setReadTimeout(READ_TIMEOUT_MS);
        }

        return this;
    }

    /**
     * Set a throttler to obey site usage rules.
     *
     * @param throttler (optional) to use
     */
    @NonNull
    public TerminatorConnection setThrottler(@Nullable final Throttler throttler) {
        mThrottler = throttler;
        return this;
    }

    /**
     * For secure connections.
     *
     * @param sslContext (optional) SSL context to use instead of the system one.
     */
    public TerminatorConnection setSSLContext(@Nullable final SSLContext sslContext) {
        Objects.requireNonNull(mRequest, "mRequest");

        if (sslContext != null) {
            ((HttpsURLConnection) mRequest).setSSLSocketFactory(sslContext.getSocketFactory());
        }
        return this;
    }

    /**
     * Override the default retry count {@link #NR_OF_TRIES}.
     *
     * @param retryCount to use, should be {@code 0} for no retries.
     */
    public void setRetryCount(@IntRange(from = 0) final int retryCount) {
        mNrOfTries = retryCount + 1;
    }

    /** wrapper to {@link HttpURLConnection}. */
    public void setInstanceFollowRedirects(final boolean followRedirects) {
        Objects.requireNonNull(mRequest, "mRequest").setInstanceFollowRedirects(followRedirects);
    }

    /** wrapper to {@link HttpURLConnection}. */
    public void setRequestProperty(@NonNull final String key,
                                   @NonNull final String value) {
        Objects.requireNonNull(mRequest, "mRequest").setRequestProperty(key, value);
    }


    /** Get the underlying connection/request object. <strong>use with care</strong>. */
    @NonNull
    public HttpURLConnection getRequest() {
        return Objects.requireNonNull(mRequest, "mRequest");
    }

    /**
     * Open the actual connection and return the input stream.
     *
     * @return input stream
     *
     * @throws IOException on failure
     */
    @NonNull
    public BufferedInputStream getInputStream()
            throws IOException {
        if (mInputStream == null) {
            mInputStream = new BufferedInputStream(openInputStream());
        }
        return mInputStream;
    }

    /**
     * Perform the actual opening of the connection: initiate the InputStream
     * and setup the killer-thread.
     * <p>
     * Called from {@link #getInputStream()}.
     *
     * @return an input stream that reads from this open connection.
     *
     * @throws IOException on failure
     */
    @NonNull
    private InputStream openInputStream()
            throws IOException {
        Objects.requireNonNull(mRequest, "mRequest");

        // If the site drops connection, we retry.
        int retry;
        // sanity check
        if (mNrOfTries > 0) {
            retry = mNrOfTries;
        } else {
            retry = NR_OF_TRIES;
        }

        while (retry > 0) {
            try {
                if (mThrottler != null) {
                    mThrottler.waitUntilRequestAllowed();
                }

                // make the actual connection
                final InputStream is = mRequest.getInputStream();
                if (mRequest.getResponseCode() < 400) {
                    // we'll close the connection on a background task after a 'kill' timeout,
                    // so that we can cancel any runaway timeouts.
                    mClosingThread = new Thread(new TerminatorThread(this, KILL_TIMEOUT_MS));
                    mClosingThread.start();
                    return is;

                } else {
                    // throw any real error code without retrying.
                    close();
                    throw new IOException("response: " + mRequest.getResponseCode()
                                          + ' ' + mRequest.getResponseMessage());
                }

                // these exceptions CAN be retried
            } catch (@NonNull final InterruptedIOException
                    | FileNotFoundException
                    | UnknownHostException e) {
                // InterruptedIOException / SocketTimeoutException: connection timeout
                // UnknownHostException: DNS or other low-level network issue
                // FileNotFoundException: seen on some sites. A retry and the site was ok.
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "open"
                               + "|retry=" + retry
                               + "|url=`" + mRequest.getURL() + '`'
                               + "|e=" + e);
                }

                retry--;
                if (retry == 0) {
                    close();
                    throw e;
                }
            }

            try {
                Thread.sleep(RETRY_AFTER_MS);
            } catch (@NonNull final InterruptedException ignore) {
            }
        }

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "open|giving up|url=`" + mRequest.getURL() + '`');
        }
        throw new IOException("Giving up");
    }


    /** wrapper to {@link HttpURLConnection}. */
    @Nullable
    public String getHeaderField(@NonNull final String name) {
        return Objects.requireNonNull(mRequest, "mRequest").getHeaderField(name);
    }

    /** wrapper to {@link HttpURLConnection}. */
    @Nullable
    public URL getURL() {
        return Objects.requireNonNull(mRequest, "mRequest").getURL();
    }

    /**
     * Close the inputStream/connection.
     * <p>
     * Will send an interrupt to the 'terminator' thread.
     */
    @Override
    public void close() {
        mCloseWasCalled = true;
        if (mInputStream != null) {
            try {
                mInputStream.close();
            } catch (@NonNull final IOException ignore) {
                // ignore
            }
            mInputStream = null;
        }
        if (mRequest != null) {
            mRequest.disconnect();
            mRequest = null;
        }
        if (mClosingThread != null) {
            // dismiss the unneeded closing thread.
            mClosingThread.interrupt();
        }
    }

    /**
     * DEBUG: if we see the warn in the logs, we know we have an issue to fix.
     */
    @SuppressWarnings("FinalizeDeclaration")
    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        if (mRequest != null && !mCloseWasCalled) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.w(TAG, "finalize|mRequest.getURL()=" + mRequest.getURL().toString());
            }
            close();
        }
        super.finalize();
    }

    /**
     * A Runnable which will close a potential run-away connection after a set timeout.
     */
    static class TerminatorThread
            implements Runnable {

        /** Connection to kill. */
        @NonNull
        private final TerminatorConnection mConnection;
        /** Delay before killing. */
        private final int mKillDelayInMillis;

        /**
         * Constructor.
         *
         * @param con               the underlying connection
         * @param killDelayInMillis delay after which the connection should be closed.
         */
        TerminatorThread(@NonNull final TerminatorConnection con,
                         final int killDelayInMillis) {
            mConnection = con;
            mKillDelayInMillis = killDelayInMillis;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(mKillDelayInMillis);
                if (!mConnection.mCloseWasCalled) {
                    if (BuildConfig.DEBUG /* always */) {
                        if (mConnection.mRequest != null) {
                            Log.d(TAG, "run|Closing TerminatorConnection: "
                                       + mConnection.mRequest.getURL());
                        }
                    }
                    mConnection.close();
                }
            } catch (@NonNull final InterruptedException ignore) {
                // ignore
            }
        }
    }
}
