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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.sync.stripinfo.StripInfoCollectionData;

public interface StripInfoDao {

    /**
     * Delete, update existing or insert the StripInfo data for the given {@link Book}.
     *
     * @param book to process
     */
    void insertOrUpdate(@NonNull Book book);

    /**
     * Store the given {@link StripInfoCollectionData}.
     *
     * @param book to process
     *
     * @return {@code true} if an insert was done.
     *         {@code false} if no insert was <strong>attempted</strong>
     */
    boolean insert(@NonNull Book book);

    /**
     * Delete all data related to StripInfo for the given book.
     * The StripInfo specific fields are left in the {@link Book} object.
     *
     * @param book to process
     */
    void delete(@NonNull Book book);

    /**
     * Get the StripInfo data for the given local book id.
     *
     * @param bookId the local book id
     *
     * @return data
     */
    @NonNull
    Optional<StripInfoCollectionData> findByLocalBookId(@IntRange(from = 1) long bookId);
}
