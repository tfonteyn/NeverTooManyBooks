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

import android.os.AsyncTask;

import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;

/**
 * A minimalistic interface for a Task (or similar, e.g. {@link SearchCoordinator})
 * which can be passed to another class.
 * <p>
 * The latter can then check if it should quit (if the caller was cancelled,
 * or e.g. upon an error, tell the caller it wants to cancel.
 */
public interface Canceller {

    /**
     * Request cancellation.
     * Mimics {@link AsyncTask#cancel(boolean)}.
     *
     * @param mayInterruptIfRunning {@code true} if the thread executing this
     *                              task should be interrupted; otherwise, in-progress tasks
     *                              are allowed to complete.
     *                              The task implementation is free to ignore this flag though.
     *
     * @return {@code false} if the task could not be cancelled,
     * typically because it has already completed normally;
     * {@code true} otherwise
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Check if the user wants to cancel the operation.
     * Mimics {@link AsyncTask#isCancelled()}.
     *
     * @return {@code true} if task was cancelled before it completed
     */
    boolean isCancelled();
}
