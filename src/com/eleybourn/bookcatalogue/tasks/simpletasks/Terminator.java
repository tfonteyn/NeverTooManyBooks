/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.tasks.simpletasks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Class to execute Runnable objects in a separate thread after a predetermined delay.
 * <p>
 * KILL_CONNECT_DELAY is 30 seconds. This means reading an http response MUST be complete in 30 secs.
 *
 * @author pjw
 */
public final class Terminator {

    /** Task queue to kill run-away connections. */
    private static final SimpleTaskQueue TASK_QUEUE;
    /** Object used in synchronization. */
    private static final Object TERMINATOR_LOCK = new Object();
    /** Queue of Event objects currently awaiting execution. */
    private static final PriorityQueue<Event> EVENTS =
            new PriorityQueue<>(10, new EventComparator());
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
    /**
     * Flag indicating the main thread process is still running and waiting
     * for a timer to elapse.
     */
    private static boolean mIsRunning;

    //2019-02-11:  replaced TASK_QUEUE = new SimpleTaskQueue("Terminator", 1);
    static {
        int maxTasks = 1;
        int nr = Runtime.getRuntime().availableProcessors();
        if (nr > 4) {
            // just a poke in the dark TODO: experiment more
            maxTasks = 3;
        }
        if (DEBUG_SWITCHES.TASK_MANAGER && BuildConfig.DEBUG) {
            Logger.info(Terminator.class, "#cpu: " + nr + ", #maxTasks: " + maxTasks);
        }
        TASK_QUEUE = new SimpleTaskQueue("Terminator", maxTasks);
    }

    private Terminator() {
    }

    /**
     * Dummy method to make sure static initialization is done. Needs to be
     * called from main thread (usually at app startup).
     */
    @SuppressWarnings("EmptyMethod")
    public static void init() {
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
    public static WrappedConnection getConnection(@NonNull final String urlStr)
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
                    return new WrappedConnection(url);

                } catch (UnknownHostException e) {
                    Logger.info(Terminator.class, e.getLocalizedMessage());
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

    /**
     * Enqueue the passed runnable to be run after the specified delay.
     *
     * @param runnable Runnable to execute
     * @param delay    Delay in milliseconds before execution
     */
    public static void enqueue(@NonNull final Runnable runnable,
                               final long delay) {
        // Compute actual time
        long time = System.currentTimeMillis() + delay;
        // Create Event and add to queue.
        Event event = new Event(runnable, time);
        synchronized (TASK_QUEUE) {
            EVENTS.add(event);
            // Make sure task is actually running
            if (!mIsRunning) {
                TASK_QUEUE.enqueue(new TerminatorTask());
                mIsRunning = true;
            } else {
                // Wake up task in case this object has a shorter timer
                synchronized (TERMINATOR_LOCK) {
                    TERMINATOR_LOCK.notify();
                }
            }
        }
    }

    /** Details of the runnable to run. */
    private static class Event {

        @NonNull
        public final Runnable runnable;
        final long time;

        Event(@NonNull final Runnable runnable,
              final long time) {
            this.runnable = runnable;
            this.time = time;
        }
    }

    /** Comparator to ensure Event objects are returned in the correct order. */
    private static class EventComparator
            implements Comparator<Event>, Serializable {

        private static final long serialVersionUID = 1835857521140326924L;

        @Override
        public int compare(@NonNull final Event o1,
                           @NonNull final Event o2) {
            return Long.compare(o1.time, o2.time);
        }
    }

    /**
     * Background task to process the queue and schedule appropriate delays.
     */
    private static class TerminatorTask
            implements SimpleTaskQueue.SimpleTask {

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext) {
            Logger.info(this, "Terminator", "Nice night for a walk.");
            do {
                Event event;
                long delay;
                // Check when next task due
                synchronized (TASK_QUEUE) {
                    // Look for a task
                    event = EVENTS.peek();
                    // none ? quit running task. Will be restarted if/when needed.
                    if (event == null) {
                        mIsRunning = false;
                        return;
                    }
                    // Check how long until it should run
                    delay = event.time - System.currentTimeMillis();
                    // If it's due now, then remove it from the queue.
                    if (delay <= 0) {
                        EVENTS.remove(event);
                    }
                }

                if (delay > 0) {
                    // If we have nothing to run, wait for first
                    synchronized (TERMINATOR_LOCK) {
                        try {
                            TERMINATOR_LOCK.wait(delay);
                        } catch (InterruptedException e) {
                            Logger.error(e);
                        }
                    }
                } else {
                    // Run the available event
                    // TODO: if 'run' blocks, then our Terminator stops terminating!
                    // Should probably be another thread.
                    // But...not for now.
                    try {
                        event.runnable.run();
                    } catch (RuntimeException e) {
                        Logger.error(e);
                    }
                }
            } while (true);
        }

        @Override
        public void onFinish(@Nullable final Exception e) {
            Logger.info(this, "Terminator", "I'll be back.");
            if (e != null) {
                Logger.error(e);
            }
        }
    }

    /**
     * Wrapping a HttpURLConnection and BufferedInputStream with timeout close() support.
     *
     * <p>
     * There is a problem with failed timeouts:
     * http://thushw.blogspot.hu/2010/10/java-urlconnection-provides-no-fail.html
     * <p>
     * So...we are forced to use a background thread to be able to kill it.
     * <p>
     * Get data from a URL. Makes sure timeout is set to avoid application stalling.
     */
    public static class WrappedConnection
            implements AutoCloseable {

        @NonNull
        public final HttpURLConnection con;
        @NonNull
        public final BufferedInputStream inputStream;

        /** Constructor. */
        WrappedConnection(@NonNull final URL url)
                throws IOException {
            con = (HttpURLConnection) url.openConnection();
            con.setUseCaches(false);
            con.setConnectTimeout(CONNECT_TIMEOUT);
            con.setReadTimeout(READ_TIMEOUT);
            // these are defaults
            //con.setDoInput(true);
            //con.setDoOutput(false);
            //con.setRequestMethod("GET");

            // close the connection on a background task after a 'kill' timeout,
            // so that we can cancel any runaway timeouts.
            enqueue(new Runnable() {
                @Override
                public void run() {
                    if (BuildConfig.DEBUG) {
                        Logger.info(Terminator.class,"Killing connection: " + url);
                    }
                    close();
                }
            }, KILL_CONNECT_DELAY);

            inputStream = new BufferedInputStream(con.getInputStream());

            if (con.getResponseCode() >= 300) {
                throw new IOException("response: " + con.getResponseCode()
                                              + ' ' + con.getResponseMessage());
            }
        }

        public void close() {
            try {
                inputStream.close();
            } catch (IOException e) {
                Logger.error(e);
            }
            con.disconnect();
        }
    }
}
