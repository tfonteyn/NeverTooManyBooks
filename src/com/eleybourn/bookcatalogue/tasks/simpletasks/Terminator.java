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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Class to execute Runnable objects in a separate thread after a predetermined delay.
 *
 * @author pjw
 */
public final class Terminator {

    /** Task queue to get book lists in background. */
    private static final SimpleTaskQueue TASK_QUEUE =
            new SimpleTaskQueue("Terminator", 1);

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
    /** for synchronization. */
    private static final Object INPUT_STREAM_LOCK = new Object();

    /**
     * Flag indicating the main thread process is still running and waiting
     * for a timer to elapse.
     */
    private static boolean mIsRunning;

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

    /**
     * https://developer.android.com/about/versions/marshmallow/android-6.0-changes
     * The Apache HTTP client was removed from 6.0 (although you can use a legacy lib)
     * Recommended is to use the java.net.HttpURLConnection
     * This means com.android.okhttp
     * https://square.github.io/okhttp/
     * <p>
     * 2018-11-22: removal of apache started....
     * <p>
     * Get data from a URL. Makes sure timeout is set to avoid application stalling.
     *
     * @param url URL to retrieve
     *
     * @return InputStream
     */
    @Nullable
    public static InputStream getInputStream(@NonNull final URL url)
            throws IOException {

        synchronized (INPUT_STREAM_LOCK) {

            int retries = 3;
            while (true) {

                final ConnectionInfo connInfo = new ConnectionInfo();

                try {
                    /*
                     * There is a problem with failed timeouts:
                     *   http://thushw.blogspot.hu/2010/10/java-urlconnection-provides-no-fail.html
                     *
                     * So...we are forced to use a background thread to be able to kill it.
                     */

                    // paranoid sanity check
                    URLConnection urlConnection = url.openConnection();
                    if (!(urlConnection instanceof HttpURLConnection)) {
                        return null;
                    }

                    connInfo.connection = (HttpURLConnection) urlConnection;
                    connInfo.connection.setUseCaches(false);
                    connInfo.connection.setDoInput(true);
                    connInfo.connection.setDoOutput(false);
                    connInfo.connection.setConnectTimeout(CONNECT_TIMEOUT);
                    connInfo.connection.setReadTimeout(READ_TIMEOUT);
                    connInfo.connection.setRequestMethod("GET");

                    // close the connection on a background task,
                    // so that we can cancel any runaway timeouts.
                    enqueue(new Runnable() {
                        @Override
                        public void run() {
                            if (connInfo.inputStream != null) {
                                if (connInfo.inputStream.isOpen()) {
                                    try {
                                        connInfo.inputStream.close();
                                        connInfo.connection.disconnect();
                                    } catch (IOException e) {
                                        Logger.error(e);
                                    }
                                }
                            } else {
                                connInfo.connection.disconnect();
                            }
                        }
                    }, KILL_CONNECT_DELAY);

                    connInfo.inputStream =
                            new StatefulBufferedInputStream(connInfo.connection.getInputStream());

                    if (connInfo.connection != null
                            && connInfo.connection.getResponseCode() >= 300) {
                        Logger.error("URL lookup failed: "
                                             + connInfo.connection.getResponseCode()
                                             + ' ' + connInfo.connection.getResponseMessage()
                                             + ", URL: " + url);
                        return null;
                    }

                    return connInfo.inputStream;

                } catch (java.net.UnknownHostException e) {
                    Logger.error(e);
                    retries--;
                    if (retries-- == 0) {
                        throw e;
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                    }
                    if (connInfo.connection != null) {
                        connInfo.connection.disconnect();
                    }
                } catch (InterruptedIOException e) {
                    Logger.info(Terminator.class,
                                "InterruptedIOException: " + e.getLocalizedMessage());
                    if (connInfo.connection != null) {
                        connInfo.connection.disconnect();
                    }
                } catch (@SuppressWarnings("OverlyBroadCatchBlock") IOException e) {
                    Logger.error(e);
                    if (connInfo.connection != null) {
                        connInfo.connection.disconnect();
                    }
                    throw e;
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

    public static class ConnectionInfo {

        @Nullable
        HttpURLConnection connection;
        @Nullable
        StatefulBufferedInputStream inputStream;
    }

    public static class StatefulBufferedInputStream
            extends BufferedInputStream
            implements Closeable {

        private boolean mIsOpen = true;

        StatefulBufferedInputStream(@NonNull final InputStream in) {
            super(in);
        }

        @Override
        @CallSuper
        public void close()
                throws IOException {
            try {
                super.close();
            } finally {
                mIsOpen = false;
            }
        }

        public boolean isOpen() {
            return mIsOpen;
        }
    }
}
