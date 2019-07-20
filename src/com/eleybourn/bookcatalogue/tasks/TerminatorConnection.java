package com.eleybourn.bookcatalogue.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * Wrapping a HttpURLConnection and BufferedInputStream with timeout close() support.
 *
 * <p>
 * There is a problem with failed timeouts:
 * http://thushw.blogspot.hu/2010/10/java-urlconnection-provides-no-fail.html
 * <p>
 * So...we are forced to use a background thread to be able to kill it.
 * <p>
 * This is the replacement for the old Terminator class. Uses a simple Thread/Runnable for closing
 * connections instead of the full-blown queue based system with 'SimpleTask'.
 */
@WorkerThread
public final class TerminatorConnection
        implements AutoCloseable {

    /** initial connection time to websites timeout. */
    private static final int CONNECT_TIMEOUT = 5_000;
    /** timeout for requests to  website. */
    private static final int READ_TIMEOUT = 10_000;
    /** kill connections after this delay. */
    private static final int KILL_CONNECT_DELAY = 20_000;
    /** if at first we don't succeed... */
    private static final int NR_OF_TRIES = 1;
    /** milliseconds to wait between retries. */
    private static final int RETRY_AFTER_MS = 500;


    @NonNull
    private final HttpURLConnection mCon;
    private final int mKillDelayInMillis;
    @Nullable
    public BufferedInputStream inputStream;
    @Nullable
    private Thread closingThread;
    private boolean isOpen;

    /**
     * Constructor.
     *
     * @param urlStr URL to retrieve
     *
     * @throws IOException on failure
     */
    public TerminatorConnection(@NonNull final String urlStr)
            throws IOException {

        final URL url = new URL(urlStr);

        // lets make sure name resolution and basic site access works.
        if (!NetworkUtils.isAlive(urlStr)) {
            throw new IOException("site cannot be contacted: " + urlStr);
        }

        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.NETWORK || DEBUG_SWITCHES.DUMP_HTTP_URL)) {
            Logger.debugEnter(this, "TerminatorConnection", "url=" + url);
        }

        mKillDelayInMillis = KILL_CONNECT_DELAY;

        mCon = (HttpURLConnection) url.openConnection();
        mCon.setUseCaches(false);
        mCon.setConnectTimeout(CONNECT_TIMEOUT);
        mCon.setReadTimeout(READ_TIMEOUT);
    }

    /**
     * Convenience function. Get an *open* TerminatorConnection from a URL
     *
     * @param urlStr URL to retrieve
     *
     * @return the open connection
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @NonNull
    public static TerminatorConnection openConnection(@NonNull final String urlStr)
            throws IOException {
        TerminatorConnection tCon = new TerminatorConnection(urlStr);
        tCon.open();
        return tCon;
    }

    @NonNull
    public HttpURLConnection getHttpURLConnection() {
        return mCon;
    }

    /**
     * Perform the actual opening of the connection, initiate the InputStream
     * and setup the killer-thread.
     *
     * @throws IOException on failure
     */
    public void open()
            throws IOException {

        int nrOfTries = NR_OF_TRIES;
        while (true) {
            try {
                // make the actual connection
                inputStream = new BufferedInputStream(mCon.getInputStream());

                // throw any error code after connect.
                if (mCon.getResponseCode() >= 300) {
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

            } catch (@NonNull final SocketTimeoutException
                    | FileNotFoundException
                    | UnknownHostException e) {
                // retry for these exceptions.
                nrOfTries--;
                if (nrOfTries-- == 0) {
                    throw e;
                }
                try {
                    Thread.sleep(RETRY_AFTER_MS);
                } catch (@NonNull final InterruptedException ignored) {
                }

            } catch (@NonNull final IOException e) {
                // give up for this exception.
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
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                        Logger.debug(this, "run",
                                     "Closing TerminatorConnection: "
                                             + mConnection.mCon.getURL());
                    }
                    mConnection.close();
                }
            } catch (@NonNull final InterruptedException ignore) {
            }
        }
    }
}
