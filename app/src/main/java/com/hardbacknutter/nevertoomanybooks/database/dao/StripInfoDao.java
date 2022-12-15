/*
 * @Copyright 2018-2022 HardBackNutter
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

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.entities.Book;

public interface StripInfoDao {

    void updateOrInsert(@NonNull Book book)
            throws DaoWriteException;

    /**
     * Store the StripInfo data for the given {@link Book}.
     *
     * @param book to process
     *
     * @throws DaoWriteException on failure
     */
    void insert(@NonNull Book book)
            throws DaoWriteException;

    /**
     * Delete all data related to StripInfo from the database (but leaves them in the book object).
     *
     * @param book to process
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Book book);
}
