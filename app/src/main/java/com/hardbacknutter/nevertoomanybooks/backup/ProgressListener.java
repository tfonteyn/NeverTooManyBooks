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
package com.hardbacknutter.nevertoomanybooks.backup;

import androidx.annotation.Nullable;

/**
 * Listener interface to get progress messages from the backup routines back to the caller.
 * Replaces all (5!) old backup related listeners.
 */
public interface ProgressListener {

    /**
     * Get the max position. Useful if a routine wants to adjust the max only if the
     * new value it intents to use if larger than the current max.
     *
     * @return max position
     */
    int getMax();

    /**
     * Set the max value (can be estimated) for the progress counter.
     *
     * @param maxPosition value
     */
    void setMax(int maxPosition);

    /**
     * Advance progress by 'delta'.
     * <p>
     * Optional to implement.
     *
     * @param delta   increment/decrement value for the progress counter
     * @param message to display, either a String or a StringRes
     */
    default void onProgressStep(int delta,
                                @Nullable Object message) {
        throw new UnsupportedOperationException();
    }

    /**
     * Report progress in absolute position.
     *
     * @param pos     absolute position for the progress counter
     * @param message to display, either a String or a StringRes
     */
    void onProgress(int pos,
                    @Nullable Object message);

    /**
     * @return {@code true} if operation is cancelled.
     */
    boolean isCancelled();
}
