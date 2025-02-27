/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.localsearch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class TimerDelegate {

    /** create timer to tick every 1/4 of a second. */
    private static final int TIMER_TICK_NS = 250_000_000;
    /** 1 second idle trigger. */
    private static final int TRIGGER_IN_NS = 1_000_000_000;
    @NonNull
    private final Runnable search;
    private final ScheduledExecutorService executor;
    /** Indicates user has typed something since the last search. */
    private boolean searchIsDirty;
    /** Timer reset each time the user clicks, in order to detect an idle time. */
    private long idleStartInNs;
    /** Timer object for background idle searches. */
    @Nullable
    private ScheduledFuture<?> timer;

    TimerDelegate(@NonNull final Runnable search) {
        this.search = search;

        executor = Executors.newSingleThreadScheduledExecutor();
    }

    void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Called when a UI element detects the user doing something.
     *
     * @param dirty Indicates the user action made the last search invalid
     */
    void userIsActive(final boolean dirty) {
        synchronized (this) {
            // Mark search dirty if necessary
            searchIsDirty = searchIsDirty || dirty;
            // Reset the idle timer since the user did something
            idleStartInNs = System.nanoTime();
            // If the search is dirty, start the idle timer if we haven't done so yet
            if (searchIsDirty && timer == null) {
                try {
                    timer = executor.scheduleWithFixedDelay(new SearchUpdateTimer(),
                                                            0, TIMER_TICK_NS,
                                                            TimeUnit.NANOSECONDS);
                } catch (@NonNull final RejectedExecutionException ignore) {
                    // ignore, we're likely shutting down
                }
            }
        }
    }

    void stopIdleTimer() {
        synchronized (this) {
            if (timer != null) {
                // Stop this scheduled task (but don't interrupt!),
                // it will be rescheduled when the user types something
                timer.cancel(false);
                timer = null;
            }
        }
    }

    /**
     * Start a search when the user is idle.
     */
    private final class SearchUpdateTimer
            implements Runnable {

        @Override
        public void run() {
            boolean doSearch = false;
            // Synchronize to access the delegate member variables,
            // as we might have more than one timer running
            synchronized (TimerDelegate.this) {
                final boolean idle = (System.nanoTime() - idleStartInNs) > TRIGGER_IN_NS;
                if (idle) {
                    stopIdleTimer();
                    if (searchIsDirty) {
                        doSearch = true;
                        searchIsDirty = false;
                    }
                }
            }

            if (doSearch) {
                search.run();
            }
        }
    }
}
