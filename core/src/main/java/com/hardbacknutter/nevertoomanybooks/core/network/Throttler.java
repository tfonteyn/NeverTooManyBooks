/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.core.network;

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

    /** Thread delay time. */
    private final int delayInMillis;

    /** Stores the last time a request was made to avoid breaking site usage rules. */
    private long lastRequestTime;

    /**
     * Constructor.
     * <p>
     * IMPORTANT: Must be a static member variable to be meaningful.
     *
     * @param delayInMillis the delay time between requests.
     */
    public Throttler(final int delayInMillis) {
        this.delayInMillis = delayInMillis;
    }


    /**
     * The default wait as used by the network code.
     *
     * @see #waitUntilRequestAllowed(int)
     */
    @SuppressWarnings("WeakerAccess")
    public void waitUntilRequestAllowed() {
        waitUntilRequestAllowed(delayInMillis);
    }

    /**
     * Uses {@link #lastRequestTime} to determine how long until the next request is allowed;
     * and update {@link #lastRequestTime}; this needs to be synchronized across threads.
     * <p>
     * Note that as a result of this approach {@link #lastRequestTime} may in fact be
     * in the future; callers to this routine effectively allocate time slots.
     * <p>
     * This method will sleep() until it can make a request; if 10 threads call this
     * simultaneously, one will return immediately, one will return 1 second later,
     * another two seconds etc.
     * <p>
     * This method may be called in special circumstances if the site needs
     * extra throttling for certain APIs.
     *
     * @param delayInMillis Thread delay time
     */
    public void waitUntilRequestAllowed(final int delayInMillis) {
        long wait;
        synchronized (this) {
            final long now = System.currentTimeMillis();
            wait = delayInMillis - (now - lastRequestTime);
            // lastRequestTime must be updated while synchronized. As soon as this
            // block is left, another block may perform another update.
            if (wait < 0) {
                wait = 0;
            }
            lastRequestTime = now + wait;
        }

        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (@NonNull final InterruptedException ignore) {
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "Throttler{"
               + "delayInMillis=" + delayInMillis
               + ", lastRequestTime=" + lastRequestTime
               + '}';
    }
}
