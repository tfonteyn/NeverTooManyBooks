/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.tasks;

import androidx.annotation.Nullable;

/**
 * Listener interface for progress messages.
 * One of the publishProgress should be implemented.
 */
public interface ProgressListener {

    /**
     * Advance progress by 'delta'.
     *
     * @param delta   increment/decrement value for the progress counter
     * @param message optional message to display
     */
    default void publishProgressStep(final int delta,
                                     @Nullable final String message) {
        throw new UnsupportedOperationException();
    }

    /**
     * Advance progress to absolute position.
     *
     * @param position absolute position for the progress counter
     * @param message  optional message to display
     */
    default void publishPosition(final int position,
                                 @Nullable final String message) {
        throw new UnsupportedOperationException();
    }

    /**
     * Optional to use/override: the interval to send progress updates in milliseconds.
     * <p>
     * Default: 200ms. i.e. 5x a second.
     *
     * @return interval in ms
     */
    default int getUpdateIntervalInMs() {
        return 200;
    }

    /**
     * Check if the user wants to cancel the operation.
     *
     * @return {@code true} if operation was cancelled.
     */
    boolean isCancelled();

    /**
     * Change the indeterminate mode for the progress bar.
     *
     * @param indeterminate true/false to enable/disable the indeterminate mode
     *                      or {@code null} to tell the receiver to revert back to its initial mode.
     */
    void setIndeterminate(@Nullable Boolean indeterminate);

    /**
     * Get the max position. Useful if a routine wants to adjust the max only if the
     * new value it intents to use is larger than the current max.
     *
     * @return max position
     */
    int getMaxPos();

    /**
     * Set the max value for the progress counter.
     *
     * @param maxPosition value
     */
    void setMaxPos(int maxPosition);
}
