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

package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.SQLException;

import androidx.annotation.IntRange;

public interface Navigator {

    /**
     * Get the total number of rows (i.e. books).
     *
     * @return row count
     */
    @IntRange(from = 1)
    int getRowCount();

    /**
     * Get the book id to load for the given position.
     *
     * @param position of the book, {@code 0} based
     *
     * @return book id
     *
     * @throws SQLException        on unexpected failures
     */
    @IntRange(from = 1)
    long getBookId(@IntRange(from = 0) int position)
            throws SQLException;

    /**
     * Clean up / close.
     */
    void close();
}
