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
package com.eleybourn.bookcatalogue.tasks;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;

import java.io.Serializable;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Class to execute Runnable objects in a separate thread after a predetermined delay.
 *
 * @author pjw
 */
public class Terminator {

    /** Task queue to get book lists in background */
    private static final SimpleTaskQueue mTaskQueue = new SimpleTaskQueue("Terminator", 1);
    /** Object used in synchronization */
    private static final Object mWaitObject = new Object();
    /** Queue of Event objects currently awaiting execution */
    private static final PriorityQueue<Event> mEvents = new PriorityQueue<>(10, new EventComparator());
    /**
     * Flag indicating the main thread process is still running and waiting for
     * a timer to elapse.
     */
    private static boolean mIsRunning = false;

    /**
     * Dummy method to make sure static initialization is done. Need to be
     * called from main thread (usually at app startup).
     */
    public static void init() {
        System.out.println("Nice night for a walk");
    }

    /**
     * Enqueue the passed runnable to be run after the specified delay.
     *
     * @param r     Runnable to execute
     * @param delay Delay before execution
     */
    public static void enqueue(Runnable r, long delay) {
        // Compute actual time
        long time = System.currentTimeMillis() + delay;
        // Create Event and add to queue.
        Event e = new Event(r, time);
        synchronized (mTaskQueue) {
            mEvents.add(e);
            // Make sure task is actually running
            if (!mIsRunning) {
                mTaskQueue.enqueue(new TerminatorTask());
                mIsRunning = true;
            } else {
                // Wake up task in case this object has a shorter timer
                synchronized (mWaitObject) {
                    mWaitObject.notify();
                }
            }
        }
    }

    /** Details of the runnable to run */
    private static class Event {
        public final Runnable runnable;
        final long time;

        public Event(Runnable r, long time) {
            runnable = r;
            this.time = time;
        }
    }

    /** Comparator to ensure Event objects are returned in the correct order */
    private static class EventComparator implements Comparator<Event>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(Event lhs, Event rhs) {
            return Long.compare(lhs.time, rhs.time);
        }
    }

    /**
     * Background task to process the queue and schedule appropriate delays
     *
     * @author pjw
     */
    private static class TerminatorTask implements SimpleTask {

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext) {
            if (BuildConfig.DEBUG) {
                System.out.println("Terminator starting");
            }
            do {
                Event e;
                long delay;
                // Check when next task due
                synchronized (mTaskQueue) {
                    // Lok for a task; if exception or none found, abort.
                    try {
                        e = mEvents.peek();
                    } catch (Exception ex) {
                        e = null;
                    }
                    if (e == null) {
                        mIsRunning = false;
                        return;
                    }
                    // Check how long until it should run
                    delay = e.time - System.currentTimeMillis();
                    // If it's due now, then remove it from the queue.
                    if (delay <= 0)
                        mEvents.remove(e);
                }

                if (delay > 0) {
                    // If we have nothing to run, wait for first
                    synchronized (mWaitObject) {
                        try {
                            mWaitObject.wait(delay);
                        } catch (Exception ex) {
                            Logger.logError(ex);
                        }
                    }
                } else {
                    // Run the available event
                    // TODO: if 'run' blocks, then our Terminator stops terminating!
                    // Should probably be another thread.
                    // But...not for now.
                    try {
                        e.runnable.run();
                    } catch (Exception ex) {
                        Logger.logError(ex);
                    }
                }
            } while (true);
        }

        @Override
        public void onFinish(Exception e) {
            System.out.println("Terminator terminating. I'll be back.");
            if (e != null) {
                Logger.logError(e);
            }
        }
    }
}
