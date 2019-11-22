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
package com.hardbacknutter.nevertoomanybooks.tasks;

import android.content.Context;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
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

    private static final String TAG = "TerminatorConnection";

    /** initial connection time to websites timeout. */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    /** timeout for requests to  website. */
    private static final int READ_TIMEOUT_MS = 10_000;
    /** kill connections after this delay. */
    private static final int KILL_CONNECT_DELAY_MS = 60_000;
    /** if at first we don't succeed... */
    private static final int NR_OF_TRIES = 2;
    /** milliseconds to wait between retries. */
    private static final int RETRY_AFTER_MS = 500;

    @NonNull
    private final HttpURLConnection mCon;
    private final int mKillDelayInMillis;
    @Nullable
    private BufferedInputStream inputStream;
    @Nullable
    private Thread closingThread;
    private boolean isOpen;
    /** DEBUG: Indicates close() has been called. */
    private boolean mCloseWasCalled;

    /**
     * Constructor. Get an open TerminatorConnection from a URL.
     *
     * @param appContext Application context
     * @param urlStr     URL to retrieve
     *
     * @throws IOException on failure
     */
    @WorkerThread
    public TerminatorConnection(@NonNull final Context appContext,
                                @NonNull final String urlStr)
            throws IOException {
        // redirect MUST BE SET TO TRUE here.
        this(appContext, urlStr, true);
    }

    /**
     * Constructor. Get an open TerminatorConnection from a URL.
     *
     * @param appContext Application context
     * @param urlStr     URL to retrieve
     * @param redirect   whether redirects should be followed or not
     *
     * @throws IOException on failure
     */
    @WorkerThread
    private TerminatorConnection(@NonNull final Context appContext,
                                 @NonNull final String urlStr,
                                 final boolean redirect)
            throws IOException {

        final URL url = new URL(urlStr);

        // lets make sure name resolution and basic site access works.
        NetworkUtils.poke(appContext, urlStr);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Log.d(TAG, "Constructor|url=\"" + url + '\"');
        }

        mKillDelayInMillis = KILL_CONNECT_DELAY_MS;

        try {
            mCon = (HttpURLConnection) url.openConnection();
        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                Logger.error(appContext, TAG, e, "url=" + urlStr);
            }
            throw e;
        }

        mCon.setInstanceFollowRedirects(redirect);
        mCon.setUseCaches(false);
        mCon.setConnectTimeout(CONNECT_TIMEOUT_MS);
        mCon.setReadTimeout(READ_TIMEOUT_MS);
    }

    /**
     * Convenience function. Get an open TerminatorConnection from a URL.
     *
     * @param appContext Application context
     * @param urlStr     URL to retrieve
     *
     * @return the open connection
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @NonNull
    public static TerminatorConnection openConnection(@NonNull final Context appContext,
                                                      @NonNull final String urlStr)
            throws IOException {
        try {
            TerminatorConnection tCon = new TerminatorConnection(appContext, urlStr);
            tCon.open();
            return tCon;

        } catch (@NonNull final IOException e) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                Logger.error(appContext, TAG, e, "url=" + urlStr);
            }
            throw e;
        }
    }

    @NonNull
    public HttpURLConnection getHttpURLConnection() {
        return mCon;
    }

    @Nullable
    public BufferedInputStream getInputStream() {
        return inputStream;
    }

    /**
     * Perform the actual opening of the connection, initiate the InputStream
     * and setup the killer-thread.
     *
     * @throws IOException on failure
     */
    public void open()
            throws IOException {

        // If the site drops connection, we retry.
        int retry = NR_OF_TRIES;

        while (retry > 0) {
            try {
                // make the actual connection
                inputStream = new BufferedInputStream(mCon.getInputStream());

                // throw any real error code after connect.
                if (mCon.getResponseCode() >= 400) {
                    close();
                    throw new IOException("response: " + mCon.getResponseCode()
                                          + ' ' + mCon.getResponseMessage());
                }

                // close the connection on a background task after a 'kill' timeout,
                // so that we can cancel any runaway timeouts.
                closingThread = new Thread(new TerminatorThread(this, mKillDelayInMillis));
                closingThread.start();

                isOpen = true;
                return;

            } catch (@NonNull final InterruptedIOException
                    | FileNotFoundException
                    | UnknownHostException e) {
                // retry for these exceptions.
                // InterruptedIOException / SocketTimeoutException: connection timeout
                // UnknownHostException: DNS or other low-level network issue
                // FileNotFoundException: seen on some sites. Simply retrying and the site was ok.
                retry--;
                if (retry == 0) {
                    throw e;
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "open"
                               + "|e=" + e.getLocalizedMessage()
                               + "|will retry=" + (retry > 0)
                               + "|url=\"" + mCon.getURL() + '\"');
                }

                try {
                    Thread.sleep(RETRY_AFTER_MS);
                } catch (@NonNull final InterruptedException ignored) {
                }

            } catch (@NonNull final IOException e) {
                // give up .
                close();
                throw e;
            }
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
            }
        }
        mCon.disconnect();
        isOpen = false;
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
            Logger.warn(TAG, "finalize|calling close()");
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
                if (mConnection.isOpen) {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "run|Closing TerminatorConnection: "
                                   + mConnection.mCon.getURL());
                    }
                    mConnection.close();
                }
            } catch (@NonNull final InterruptedException ignore) {
            }
        }
    }
}
