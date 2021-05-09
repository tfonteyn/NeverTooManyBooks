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

import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;

/**
 * A minimalistic interface for a Task (or similar, e.g. {@link SearchCoordinator})
 * which can be passed to another class.
 */
public interface Canceller {

    /**
     * Check if the task is or should be cancelled.
     *
     * @return {@code true} if the task was cancelled before it completed
     */
    boolean isCancelled();
}
