/*
 * @Copyright 2018-2023 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;

/**
 * Listener interface for progress messages.
 */
public interface ProgressListener
        extends Cancellable {

    /**
     * Send a progress message.
     *
     * @param message to send
     */
    @WorkerThread
    void publishProgress(@NonNull TaskProgress message);

    /**
     * Advance progress by 'delta'.
     * <p>
     * Convenience method which should build the {@link TaskProgress} based
     * on the current progress counters and the passed data and call
     * {@link #publishProgress(TaskProgress)}.
     * <p>
     * See {@link TaskBase} for the default implementation.
     *
     * @param delta the relative step in the overall progress count.
     * @param text  (optional) text message
     */
    @WorkerThread
    void publishProgress(int delta,
                         @Nullable String text);

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

    /**
     * Optional to override: the interval to send progress updates in milliseconds.
     * <p>
     * Default: 200ms. i.e. 5x a second.
     *
     * @return interval in ms
     */
    default int getUpdateIntervalInMs() {
        return 200;
    }
}
