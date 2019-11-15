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
package com.hardbacknutter.nevertoomanybooks.utils;

import androidx.annotation.NonNull;

public class Throttler {

//    private static final String TAG = "Throttler";

    /**
     * Stores the last time a request was made to avoid breaking site usage rules.
     * Only modify this value from inside a synchronized (LAST_REQUEST_TIME_LOCK)
     */
    private long mLastRequestTime;

    /** Default thread delay time; 1 second as per LibraryThing/GoodReads. */
    private long mDelayInMillis = 1_000L;

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
    public Throttler(final long delayInMillis) {
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
        long now = System.currentTimeMillis();
        long wait;
        synchronized (this) {
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
            } catch (@NonNull final InterruptedException ignored) {
            }
        }
    }
}
