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
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;

/**
 * Wrapping a HttpURLConnection and BufferedInputStream with timeout close() support.
 * <p>
 * There is a problem with failed timeouts:
 * http://thushw.blogspot.hu/2010/10/java-urlconnection-provides-no-fail.html
 * <p>
 * So...we are forced to use a background thread to be able to kill it.
 * <p>
 * This is the replacement for the old Terminator class. Uses a simple Thread/Runnable for closing
 * connections instead of the full-blown queue based system with 'SimpleTask'.
 * <p>
 * Note that the Goodreads classes and image download used to use the Apache Commons Http classes
 * instead. Apache is now removed, and they use the standard HttpURLConnection directly.
 * TODO: either make all places use this class, or perhaps remove this class?
 */
@WorkerThread
public final class TerminatorConnection
        implements AutoCloseable {

    /** Log tag. */
    private static final String TAG = "TerminatorConnection";

    /** timeout for requests to  website. */
    private static final int READ_TIMEOUT_MS = 10_000;
    /** kill connections after this delay. */
    private static final int KILL_CONNECT_DELAY_MS = 60_000;
    /** if at first we don't succeed... */
    private static final int NR_OF_TRIES = 2;
    /** milliseconds to wait between retries. */
    private static final int RETRY_AFTER_MS = 1_000;

    @NonNull
    private final HttpURLConnection mCon;
    private final int mKillDelayInMillis;
    @Nullable
    private BufferedInputStream inputStream;
    @Nullable
    private Thread closingThread;
    /** DEBUG: Indicates close() has been called. Also see {@link Closeable#close()}. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     *
     * @param context        Application context
     * @param urlStr         URL to retrieve
     * @param connectTimeout in milliseconds
     *
     * @throws IOException on failure
     */
    @WorkerThread
    public TerminatorConnection(@NonNull final Context context,
                                @NonNull final String urlStr,
                                final int connectTimeout)
            throws IOException {

        final URL url = new URL(urlStr);

        // can we reach the site at all ?
        NetworkUtils.ping(context, urlStr);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "Constructor|url=\"" + url + '\"');
        }

        mKillDelayInMillis = KILL_CONNECT_DELAY_MS;

        try {
            mCon = (HttpURLConnection) url.openConnection();
        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                Logger.error(context, TAG, e, "url=" + urlStr);
            }
            throw e;
        }


        // redirect MUST BE SET TO TRUE here.
        mCon.setInstanceFollowRedirects(true);
        mCon.setUseCaches(false);

        mCon.setConnectTimeout(connectTimeout);
        mCon.setReadTimeout(READ_TIMEOUT_MS);
    }

    /**
     * Convenience function. Get an open TerminatorConnection from a URL.
     *
     * @param context        Application context
     * @param urlStr         URL to retrieve
     * @param connectTimeout in milliseconds
     *
     * @return the open connection
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @NonNull
    public static TerminatorConnection open(@NonNull final Context context,
                                            @NonNull final String urlStr,
                                            final int connectTimeout)
            throws IOException {
        return open(context, urlStr, connectTimeout, NR_OF_TRIES, null);
    }

    /**
     * Convenience function. Get an open TerminatorConnection from a URL.
     *
     * @param context        Application context
     * @param urlStr         URL to retrieve
     * @param connectTimeout in milliseconds
     * @param retries        If the site drops connection, we retry 'retries' times
     * @param throttler      (optional) to use
     *
     * @return the open connection
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @NonNull
    public static TerminatorConnection open(@NonNull final Context context,
                                            @NonNull final String urlStr,
                                            final int connectTimeout,
                                            final int retries,
                                            @Nullable final Throttler throttler)
            throws IOException {
        TerminatorConnection con = new TerminatorConnection(context, urlStr, connectTimeout);
        con.open(retries, throttler);
        return con;
    }

    /** wrapper to {@link HttpURLConnection}. */
    public void setInstanceFollowRedirects(final boolean followRedirects) {
        mCon.setInstanceFollowRedirects(followRedirects);
    }

    /** wrapper to {@link HttpURLConnection}. */
    public void setReadTimeout(final int timeout) {
        mCon.setReadTimeout(timeout);
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

    @Nullable
    public BufferedInputStream getInputStream() {
        return inputStream;
    }

    public void open()
            throws IOException {
        open(NR_OF_TRIES, null);
    }

    /**
     * Perform the actual opening of the connection, initiate the InputStream
     * and setup the killer-thread.
     * <p>
     * The optional {@link Throttler} will be used before the first connect
     * and before each retry.
     * If no Throttler is set, then the first request is send immediately, and retries
     * use a fixed {@link #RETRY_AFTER_MS} delay.
     *
     * @param retries   If the site drops connection, we retry 'retries' times
     * @param throttler (optional) to use
     *
     * @throws IOException on failure
     */
    public void open(final int retries,
                     @Nullable final Throttler throttler)
            throws IOException {

        try {

            if (throttler != null) {
                throttler.waitUntilRequestAllowed();
            }

            // If the site drops connection, we retry.
            int retry = retries;

            while (retry > 0) {
                try {
                    // make the actual connection
                    inputStream = new BufferedInputStream(mCon.getInputStream());

                    if (mCon.getResponseCode() < 400) {
                        // we'll close the connection on a background task after a 'kill' timeout,
                        // so that we can cancel any runaway timeouts.
                        closingThread = new Thread(new TerminatorThread(this, mKillDelayInMillis));
                        closingThread.start();
                        return;

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

                if (throttler != null) {
                    throttler.waitUntilRequestAllowed();
                } else {
                    try {
                        Thread.sleep(RETRY_AFTER_MS);
                    } catch (@NonNull final InterruptedException ignored) {
                    }
                }
            }

        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "open|giving up"
                           + "|url=`" + mCon.getURL() + '`'
                           + "|e=" + e);
            }
            throw e;
        }
    }

    /**
     * Close the inputStream/connection.
     * <p>
     * Will send an interrupt to the 'terminator' thread.
     */
    public void close() {
        mCloseWasCalled = true;
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (@NonNull final IOException ignore) {
                // ignore
            }
        }
        mCon.disconnect();
        if (closingThread != null) {
            // dismiss the unneeded closing thread.
            closingThread.interrupt();
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
     * This is the replacement of the old SimpleTask to close a potential run-away connection.
     * {@link TerminatorConnection} is always called from a background task, so we cannot
     * start another background task from there. An old-fashioned Thread/Runnable is fine though.
     */
    static class TerminatorThread
            implements Runnable {

        private final TerminatorConnection mConnection;
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
