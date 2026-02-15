/*
 * @Copyright 2018-2026 HardBackNutter
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

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Throttler is created once in each {@code EngineId/SearchEngineConfig}.
 * The actual {@code SearchEngine} which can be multi-instance/multi-thread
 * will always refer to that single Throttler instance.
 */
public class Throttler {

    /**
     * Even if there are no specific terms of usage,
     * we're only going to send maximum one request a second by default
     * as a courtesy/precaution.
     * <p>
     * This is the default used throughout the app.
     * Different classes may/will override as needed.
     */
    public static final int THROTTLER_DEFAULT_MS = 1_000;

    /** divider to convert nanoseconds to milliseconds. */
    private static final long NANO_TO_MILLIS = 1_000_000L;

    /** For quick use in {@link #getDelayInMillis()}. */
    private final int delayInMillis;
    /** For internal use with {@code System.nanoTime()}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private final long intervalNanos;

    @NonNull
    private final AtomicLong nextTimeInNanos;
    // lint: java.util.random.RandomGenerator is JDK 17 and not available on Android.
    @SuppressWarnings({"FieldNotUsedInToString", "TypeMayBeWeakened"})
    private final Random random = new Random();

    /**
     * Constructor.
     *
     * @param delayInMillis the delay time between requests.
     */
    public Throttler(final int delayInMillis) {
        this.delayInMillis = Math.max(THROTTLER_DEFAULT_MS, delayInMillis);
        intervalNanos = (long) this.delayInMillis * NANO_TO_MILLIS;
        nextTimeInNanos = new AtomicLong(System.nanoTime());
    }

    /**
     * Get the currently defined delay in milliseconds.
     *
     * @return ms
     */
    public int getDelayInMillis() {
        return delayInMillis;
    }


    /**
     * Forces the throttler to wait for a specific duration.
     * Call this when receiving a {@code 429 Too Many Requests} error.
     *
     * @param delayInMillis value as calculated from the {@code Retry-After} header
     */
    @SuppressWarnings("WeakerAccess")
    public void onTooManyRequests(final int delayInMillis) {
        final long targetTime = System.nanoTime() + delayInMillis * NANO_TO_MILLIS;

        // Move the time forward.
        // If another thread already set a longer delay, we don't shorten it.
        nextTimeInNanos.updateAndGet(last -> Math.max(last, targetTime));
    }

    /**
     * Wait for the default interval.
     */
    public void waitUntilRequestAllowed() {
        waitUntilRequestAllowed(intervalNanos);
    }

    /**
     * Wait for the given interval.
     *
     * @param delayInMillis Thread delay time
     */
    public void waitUntilRequestAllowed(final int delayInMillis) {
        waitUntilRequestAllowed(delayInMillis * NANO_TO_MILLIS);
    }

    /**
     * Uses {@link #nextTimeInNanos} to determine when the next request is allowed.
     * Callers to this routine effectively allocate time slots.
     *
     * @param delayInNano Thread delay time
     */
    private void waitUntilRequestAllowed(final long delayInNano) {
        final long now = System.nanoTime();

        // Add 0 to 500ms of extra random delay
        final long jitterNanos = random.nextInt(500) * NANO_TO_MILLIS;

        final long next = nextTimeInNanos.getAndUpdate(
                last -> Math.max(now, last) + delayInNano + jitterNanos);

        final long waitNanos = next - now;

        if (waitNanos > 0) {
            // Rounded up
            final long waitMillis = (waitNanos + 999_999L) / NANO_TO_MILLIS;
            try {
                Thread.sleep(waitMillis);
            } catch (@NonNull final InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        final long currentWaitMs = Math.max(0, (nextTimeInNanos.get() - System.nanoTime())
                                               / NANO_TO_MILLIS);
        return "Throttler{"
               + "delayInMillis=" + delayInMillis
               + ", currentWaitMs=" + currentWaitMs
               + '}';
    }
}
