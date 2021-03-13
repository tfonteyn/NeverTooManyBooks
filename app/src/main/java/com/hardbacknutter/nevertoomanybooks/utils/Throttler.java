/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils;

import androidx.annotation.NonNull;

/**
 * Define as a static.
 * <pre>
 * {@code
 *     @NonNull
 *     private static final Throttler THROTTLER = new Throttler();
 * }
 * </pre>
 * Use where needed: {@code THROTTLER.waitUntilRequestAllowed(); }
 */
public class Throttler {

    //    /** Log tag. */
//    private static final String TAG = "Throttler";

    /**
     * Stores the last time a request was made to avoid breaking site usage rules.
     * Only modify this value from inside a synchronized (LAST_REQUEST_TIME_LOCK)
     */
    private long mLastRequestTime;

    /** Default thread delay time; 1 second as per LibraryThing/GoodReads. */
    private int mDelayInMillis = 1_000;

    /**
     * Constructor.
     * <p>
     * IMPORTANT: Must be a static member variable to be meaningful.
     * <p>
     * Uses the default delay of 1 second.
     */
    public Throttler() {
    }

    /**
     * Constructor.
     * <p>
     * IMPORTANT: Must be a static member variable to be meaningful.
     *
     * @param delayInMillis the delay time between requests.
     */
    @SuppressWarnings("unused")
    public Throttler(final int delayInMillis) {
        mDelayInMillis = delayInMillis;
    }

    /**
     * Uses mLastRequestTime to determine how long until the next request is allowed;
     * and update mLastRequestTime this needs to be synchronized across threads.
     * <p>
     * Note that as a result of this approach mLastRequestTime may in fact be
     * in the future; callers to this routine effectively allocate time slots.
     * <p>
     * This method will sleep() until it can make a request; if 10 threads call this
     * simultaneously, one will return immediately, one will return 1 second later,
     * another two seconds etc.
     */
    public void waitUntilRequestAllowed() {
        long wait;
        synchronized (this) {
            final long now = System.currentTimeMillis();
            wait = mDelayInMillis - (now - mLastRequestTime);
            // mLastRequestTime must be updated while synchronized. As soon as this
            // block is left, another block may perform another update.
            if (wait < 0) {
                wait = 0;
            }
            mLastRequestTime = now + wait;
            //Log.d(TAG, "mLastRequestTime=" + mLastRequestTime);
        }

        if (wait > 0) {
            try {
                //Log.d(TAG, "wait=" + wait);
                Thread.sleep(wait);
            } catch (@NonNull final InterruptedException ignore) {
            }
        }
    }
}
