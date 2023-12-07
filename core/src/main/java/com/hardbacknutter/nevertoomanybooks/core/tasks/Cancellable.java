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
package com.hardbacknutter.nevertoomanybooks.core.tasks;

import androidx.annotation.AnyThread;

/**
 * A minimalistic interface for a Task, SearchEngine, DataReader, DataWriter, ...
 * which can be passed to another class.
 */
public interface Cancellable {

    /**
     * Attempt to cancel execution of the current task.
     * This is a <strong>request</strong>. Implementations might ignore this request.
     */
    @AnyThread
    void cancel();

    /**
     * Check if the task is or should be cancelled.
     *
     * @return {@code true} if a request was previously made to cancel this task.
     */
    @AnyThread
    boolean isCancelled();
}
