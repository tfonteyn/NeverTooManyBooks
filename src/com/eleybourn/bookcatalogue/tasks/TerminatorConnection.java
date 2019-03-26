package com.eleybourn.bookcatalogue.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
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
    private static final int CONNECT_TIMEOUT = 30_000;
    /** timeout for requests to  website. */
    private static final int READ_TIMEOUT = 30_000;
    /** kill connections after this delay. */
    private static final int KILL_CONNECT_DELAY = 30_000;
    /** if at first we don't succeed... */
    private static final int RETRIES = 3;
    /** milliseconds to wait between retries. */
    private static final int RETRY_AFTER_MS = 500;

    /** for synchronization. */
    private static final Object INPUT_STREAM_LOCK = new Object();
    @NonNull
    public final BufferedInputStream inputStream;
    @NonNull
    private final HttpURLConnection con;

    /** Constructor. */
    private TerminatorConnection(@NonNull final URL url)
            throws IOException {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Logger.info(this, Tracker.State.Enter,
                        "TerminatorConnection", "url=" + url);
        }

        con = (HttpURLConnection) url.openConnection();
        con.setUseCaches(false);
        con.setConnectTimeout(CONNECT_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        // these are defaults
        //con.setDoInput(true);
        //con.setDoOutput(false);
        //con.setRequestMethod("GET");

        inputStream = new BufferedInputStream(con.getInputStream());

        if (con.getResponseCode() >= 300) {
            throw new IOException("response: " + con.getResponseCode()
                                          + ' ' + con.getResponseMessage());
        }

        // close the connection on a background task after a 'kill' timeout,
        // so that we can cancel any runaway timeouts.
        new Thread(new InterruptThread(Thread.currentThread(), this)).start();
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
     */
    @WorkerThread
    @NonNull
    public static TerminatorConnection getConnection(@NonNull final String urlStr)
            throws IOException {

        final URL url = new URL(urlStr);

        // lets make sure name resolution and basic site access works.
        // Uses a low-level socket, if that already fails, no point to continue.
        if (!NetworkUtils.isAlive(urlStr)) {
            throw new IOException("site cannot be contacted");
        }

        // only allow one request at a time to get an InputStream.
        synchronized (INPUT_STREAM_LOCK) {
            int retries = RETRIES;
            while (true) {
                try {

                    return new TerminatorConnection(url);

                } catch (UnknownHostException e) {
                    Logger.info(TerminatorConnection.class, "getConnection",
                                e.getLocalizedMessage());
                    retries--;
                    if (retries-- == 0) {
                        throw e;
                    }
                    try {
                        Thread.sleep(RETRY_AFTER_MS);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
    }

    public void close() {
        try {
            inputStream.close();
        } catch (IOException ignore) {
        }
        con.disconnect();
    }

    /**
     * This is the replacement of the old SimpleTask to close a potential run-away connection.
     * {@link TerminatorConnection} is always called from a background task, so we cannot
     * start another background task from there. An old-fashioned Thread/Runnable is fine though.
     */
    static class InterruptThread
            implements Runnable {

        // needed to keep the reference alive?
        private final Thread parent;
        private final TerminatorConnection con;

        InterruptThread(@NonNull final Thread parent,
                        @NonNull final TerminatorConnection con) {
            this.parent = parent;
            this.con = con;
        }

        public void run() {
            try {
                Thread.sleep(KILL_CONNECT_DELAY);
            } catch (InterruptedException ignore) {
            }
//            if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Logger.info(this, "run", "Closing WrappedConnection.");
//            }
            con.close();
        }
    }
}
