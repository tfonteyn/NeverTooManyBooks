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

import java.util.Timer;
import java.util.TimerTask;

class TimerDelegate {

    /** create timer to tick every 250ms. */
    private static final int TIMER_TICK_MS = 250;
    /** 0.5 second idle trigger. */
    private static final int NANO_TO_SECONDS = 500_000_000;
    @NonNull
    private final Runnable search;
    /** Indicates user has changed something since the last search. */
    private boolean searchIsDirty;
    /** Timer reset each time the user clicks, in order to detect an idle time. */
    private long idleStart;
    /** Timer object for background idle searches. */
    @Nullable
    private Timer timer;

    TimerDelegate(@NonNull final Runnable search) {
        this.search = search;
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
            idleStart = System.nanoTime();
            // If the search is dirty, start the idle timer
            if (searchIsDirty) {
                startIdleTimer();
            }
        }
    }

    /**
     * start the idle timer.
     */
    private void startIdleTimer() {
        // Synchronize since this is relevant to more than 1 thread.
        synchronized (this) {
            if (timer != null) {
                return;
            }
            timer = new Timer();
            idleStart = System.nanoTime();
        }

        timer.schedule(new SearchUpdateTimer(), 0, TIMER_TICK_MS);
    }

    /**
     * Stop the timer.
     */
    void stopIdleTimer() {
        final Timer tmpTimer;
        // Synchronize since this is relevant to more than 1 thread.
        synchronized (this) {
            tmpTimer = this.timer;
            this.timer = null;
        }
        if (tmpTimer != null) {
            tmpTimer.cancel();
        }
    }

    /**
     * Implements a timer task (Runnable) and start a search when the user is idle.
     */
    private final class SearchUpdateTimer
            extends TimerTask {

        @Override
        public void run() {
            boolean doSearch = false;
            // Synchronize as we might have more than one timer running (but shouldn't)
            synchronized (this) {
                final boolean idle = (System.nanoTime() - idleStart) > NANO_TO_SECONDS;
                if (idle) {
                    // Stop the timer, it will be restarted when the user changes something
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
