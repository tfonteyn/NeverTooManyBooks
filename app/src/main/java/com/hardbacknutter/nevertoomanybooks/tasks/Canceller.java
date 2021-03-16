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
package com.hardbacknutter.nevertoomanybooks.tasks;

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
     *
     * @param mayInterruptIfRunning {@code true} if the thread executing this task
     *                              should be interrupted; otherwise, in-progress tasks
     *                              are allowed to complete.
     *                              The task implementation is free to ignore this flag though.
     *
     * @return {@code false} if the task could not be cancelled,
     * typically because it has already completed normally;
     * {@code true} otherwise
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Check if the task is or should be cancelled.
     *
     * @return {@code true} if task was cancelled before it completed
     */
    boolean isCancelled();
}
