/*
 * @Copyright 2018-2025 HardBackNutter
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
     * @param position of the book, {@code 0..}
     *
     * @return book id
     */
    long getBookId(@IntRange(from = 0) int position);

    void close();
}
