package com.hardbacknutter.nevertomanybooks.utils;

import android.util.Log;

import androidx.annotation.NonNull;

public class Throttler {

    /**
     * Stores the last time an API request was made to avoid breaking API rules.
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
            //
            // mLastRequestTime must be updated while synchronized. As soon as this
            // block is left, another block may perform another update.
            //
            if (wait < 0) {
                wait = 0;
            }
            mLastRequestTime = now + wait;
            Log.d("Throttler", "mLastRequestTime=" + mLastRequestTime);
        }

        if (wait > 0) {
            try {
                Log.d("Throttler", "wait=" + wait);
                Thread.sleep(wait);
            } catch (@NonNull final InterruptedException ignored) {
            }
        }
    }
}
