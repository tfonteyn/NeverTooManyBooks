/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.tasks;

import android.content.Context;
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
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;

/**
 * Wrapping a HttpURLConnection and BufferedInputStream with timeout close() support.
 * <p>
 * There is a problem with failed timeouts:
 * http://thushw.blogspot.hu/2010/10/java-urlconnection-provides-no-fail.html
 * <p>
 * So...we will use a background thread to kill the connection after a set timeout.
 * <p>
 * Note that the Goodreads classes and image download use the standard HttpURLConnection directly.
 * TODO: either make all places use this class, or perhaps remove this class?
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

    /** if at first we don't succeed... */
    private static final int NR_OF_TRIES = 2;
    /** milliseconds to wait between retries. This is in ADDITION to the Throttler. */
    private static final int RETRY_AFTER_MS = 1_000;

    @NonNull
    private final HttpURLConnection mCon;

    @Nullable
    private final Throttler mThrottler;
    private final int mConnectTimeout;
    private final int mReadTimeout;
    private final int mKillDelayInMillis;

    @Nullable
    private BufferedInputStream mInputStream;
    @Nullable
    private Thread mClosingThread;

    /** DEBUG: Indicates close() has been called. Also see {@link Closeable#close()}. */
    private boolean mCloseWasCalled;
    private int mNrOfTries;

    /**
     * Constructor.
     *
     * @param context Application context
     * @param urlStr  URL to retrieve
     *
     * @throws IOException on failure
     */
    @WorkerThread
    public TerminatorConnection(@NonNull final Context context,
                                @NonNull final String urlStr,
                                @IntRange(from = 0) final int connectTimeout,
                                @IntRange(from = 0) final int readTimeout,
                                @Nullable final Throttler throttler)
            throws IOException {

        final URL url = new URL(urlStr);

        // can we reach the site at all ?
        NetworkUtils.ping(context, urlStr);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "Constructor|url=\"" + url + '\"');
        }

        try {
            mCon = (HttpURLConnection) url.openConnection();
        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                Logger.error(context, TAG, e, "url=" + urlStr);
            }
            throw e;
        }

        mKillDelayInMillis = KILL_TIMEOUT_MS;

        mConnectTimeout = connectTimeout;
        mReadTimeout = readTimeout;
        mThrottler = throttler;

        // redirect MUST BE SET TO TRUE here.
        mCon.setInstanceFollowRedirects(true);
        mCon.setUseCaches(false);
    }

    /**
     * Convenience constructor.
     *
     * @param context      Application context
     * @param urlStr       URL to retrieve
     * @param searchEngine to use
     *
     * @throws IOException on failure
     */
    @WorkerThread
    public TerminatorConnection(@NonNull final Context context,
                                @NonNull final String urlStr,
                                @NonNull final SearchEngine searchEngine)
            throws IOException {
        this(context, urlStr,
             searchEngine.getConnectTimeoutMs(),
             searchEngine.getReadTimeoutMs(),
             searchEngine.getThrottler());
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
            mInputStream = open();
        }
        return mInputStream;
    }

    /**
     * Perform the actual opening of the connection, initiate the InputStream
     * and setup the killer-thread.
     * <p>
     * Called from {@link #getInputStream()}.
     *
     * @throws IOException on failure
     */
    @NonNull
    private BufferedInputStream open()
            throws IOException {

        if (mConnectTimeout > 0) {
            mCon.setConnectTimeout(mConnectTimeout);
        } else {
            mCon.setConnectTimeout(CONNECT_TIMEOUT_MS);
        }

        if (mReadTimeout > 0) {
            mCon.setReadTimeout(mReadTimeout);
        } else {
            mCon.setReadTimeout(READ_TIMEOUT_MS);
        }

        // If the site drops connection, we retry.
        int retry;
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
                final BufferedInputStream is = new BufferedInputStream(mCon.getInputStream());
                if (mCon.getResponseCode() < 400) {
                    // we'll close the connection on a background task after a 'kill' timeout,
                    // so that we can cancel any runaway timeouts.
                    mClosingThread = new Thread(new TerminatorThread(this, mKillDelayInMillis));
                    mClosingThread.start();
                    return is;

                } else {
                    // throw any real error code without retrying.
                    close();
                    throw new IOException("response: " + mCon.getResponseCode()
                                          + ' ' + mCon.getResponseMessage());
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
                               + "|url=`" + mCon.getURL() + '`'
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
            Log.d(TAG, "open|giving up|url=`" + mCon.getURL() + '`');
        }
        throw new IOException("Giving up");
    }

    @SuppressWarnings("unused")
    public void setNrOfTries(@IntRange(from = 0) final int nrOfTries) {
        mNrOfTries = nrOfTries;
    }

    /** wrapper to {@link HttpURLConnection}. */
    public void setInstanceFollowRedirects(final boolean followRedirects) {
        mCon.setInstanceFollowRedirects(followRedirects);
    }

    /** wrapper to {@link HttpURLConnection}. */
    public void setRequestProperty(@NonNull final String key,
                                   @NonNull final String value) {
        mCon.setRequestProperty(key, value);
    }

    /** wrapper to {@link HttpURLConnection}. */
    @Nullable
    public String getHeaderField(@NonNull final String name) {
        return mCon.getHeaderField(name);
    }

    /** wrapper to {@link HttpURLConnection}. */
    @Nullable
    public URL getURL() {
        return mCon.getURL();
    }

    /**
     * Close the inputStream/connection.
     * <p>
     * Will send an interrupt to the 'terminator' thread.
     */
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
        mCon.disconnect();
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
        if (!mCloseWasCalled) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.w(TAG, "finalize|" + mCon.getURL().toString());
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
                        Log.d(TAG, "run|Closing TerminatorConnection: "
                                   + mConnection.mCon.getURL());
                    }
                    mConnection.close();
                }
            } catch (@NonNull final InterruptedException ignore) {
                // ignore
            }
        }
    }
}
