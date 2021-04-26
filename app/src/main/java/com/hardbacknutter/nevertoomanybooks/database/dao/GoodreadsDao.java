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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

public interface GoodreadsDao {

    /**
     * Query to get the relevant columns for sending a single Book to Goodreads.
     *
     * @param bookId to retrieve
     *
     * @return Cursor containing a single book, or none
     */
    @NonNull
    Cursor fetchBookForExport(@IntRange(from = 1) long bookId);

    /**
     * Query to get the relevant columns for sending a set of Book 's to Goodreads.
     *
     * @param startId     the 'first' (e.g. 'oldest') bookId to get
     *                    since the last sync with Goodreads
     * @param updatesOnly true, if we only want the updated records
     *                    since the last sync with Goodreads
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    Cursor fetchBooksForExport(long startId,
                               boolean updatesOnly);

    /**
     * Set the Goodreads book id for this book.
     *
     * @param bookId          the/our book id
     * @param goodreadsBookId the Goodreads book id
     */
    void setGoodreadsBookId(@IntRange(from = 1) long bookId,
                            long goodreadsBookId);

    /**
     * Set the Goodreads sync date to the current time.
     *
     * @param bookId the book
     */
    void setSyncDate(@IntRange(from = 1) long bookId);
}
