/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.tasks;

import androidx.annotation.Nullable;

/**
 * Listener interface for progress messages.
 * One of the onProgress should be implemented.
 */
public interface ProgressListener {

    /**
     * Advance progress to absolute position.
     *
     * @param pos     absolute position for the progress counter
     * @param message optional message to display
     */
    default void onProgress(final int pos,
                            @Nullable final String message) {
        throw new UnsupportedOperationException();
    }

    /**
     * Advance progress by 'delta'.
     *
     * @param delta   increment/decrement value for the progress counter
     * @param message optional message to display
     */
    default void onProgressStep(final int delta,
                                @Nullable final String message) {
        throw new UnsupportedOperationException();
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
     * @param indeterminate true/false to enable the indeterminate mode,
     *                      or {@code null} to tell the receiver to use its initial mode.
     */
    void setIndeterminate(@Nullable Boolean indeterminate);

    /**
     * Get the max position. Useful if a routine wants to adjust the max only if the
     * new value it intents to use is larger than the current max.
     *
     * @return max position
     */
    int getMax();

    /**
     * Set the max value for the progress counter.
     *
     * @param maxPosition value
     */
    void setMax(int maxPosition);
}
