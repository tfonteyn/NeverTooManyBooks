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

    @Nullable
    public final BufferedInputStream inputStream;
    @NonNull
    private final HttpURLConnection con;
    @Nullable
    private final Thread closingThread;

    private boolean isOpen;

    /**
     * Constructor.
     *
     * @param url               URL to retrieve
     * @param killDelayInMillis delay after which this connection will get killed if not closed yet.
     *
     * @throws IOException on failure
     */
    private TerminatorConnection(@NonNull final URL url,
                                 final int killDelayInMillis)
            throws IOException {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Logger.debugEnter(this, "TerminatorConnection", "url=" + url);
        }

        con = (HttpURLConnection) url.openConnection();
        con.setUseCaches(false);
        con.setConnectTimeout(CONNECT_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        // these are defaults
        //con.setDoInput(true);
        //con.setDoOutput(false);
        //con.setRequestMethod("GET");

        isOpen = true;
        try {
            inputStream = new BufferedInputStream(con.getInputStream());
        } catch (IOException e) {
            close();
            throw e;
        }

        if (con.getResponseCode() >= 300) {
            close();
            throw new IOException("response: " + con.getResponseCode()
                                          + ' ' + con.getResponseMessage());
        }

        // close the connection on a background task after a 'kill' timeout,
        // so that we can cancel any runaway timeouts.
        closingThread = new Thread(new TerminatorThread(this, killDelayInMillis));
        closingThread.start();
    }

    /**
     * Get a ConnectionInfo from a URL.
     *
     * <p>
     * It is assumed we have a network.
     * It is not assumed (will be tested) that the internet works.
     *
     * @param urlStr URL to retrieve
     *
     * @return ConnectionInfo
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @NonNull
    public static TerminatorConnection getConnection(@NonNull final String urlStr)
            throws IOException {
        return getConnection(urlStr, KILL_CONNECT_DELAY);
    }

    /**
     * Get a ConnectionInfo from a URL.
     *
     * <p>
     * It is assumed we have a network.
     * It is not assumed (will be tested) that the internet works.
     *
     * @param urlStr            URL to retrieve
     * @param killDelayInMillis delay after which this connection will get killed if not closed yet.
     *
     * @return ConnectionInfo
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @NonNull
    public static TerminatorConnection getConnection(@NonNull final String urlStr,
                                                     final int killDelayInMillis)
            throws IOException {

        final URL url = new URL(urlStr);

        // lets make sure name resolution and basic site access works.
        if (!NetworkUtils.isAlive(urlStr)) {
            throw new IOException("site cannot be contacted: " + urlStr);
        }

        int nrOfTries = NR_OF_TRIES;
        while (true) {
            try {
                return new TerminatorConnection(url, killDelayInMillis);
                // retry for these exceptions.
            } catch (SocketTimeoutException | FileNotFoundException | UnknownHostException e) {
                // don't log here, we'll log higher up the chain.
                nrOfTries--;
                if (nrOfTries-- == 0) {
                    throw e;
                }
                try {
                    Thread.sleep(RETRY_AFTER_MS);
                } catch (InterruptedException ignored) {
                }
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
            } catch (IOException ignore) {
            }
        }
        con.disconnect();
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
                                             + mConnection.con.getURL());
                    }
                    mConnection.close();
                }
            } catch (InterruptedException ignore) {
            }
        }
    }
}
