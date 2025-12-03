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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import java.time.LocalDateTime;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.database.cleaning.Purger;

/**
 * Note that there is no individual 'insert' method.
 * New records are created by a database trigger when books are deleted.
 */
public interface DeletedBooksDao {

    /**
     * Get the deleted-book records.
     * Each record (i.e. a Pair) consists of the UUID and the date-added as an iso utc timestamp.
     *
     * @param sinceDateTime (optional) cutoff timestamp
     *
     * @return list of records
     *
     * @see #importRecords(Collection)
     * @see #sync()
     */
    @NonNull
    Collection<Pair<String, String>> getAll(@Nullable LocalDateTime sinceDateTime);

    /**
     * Bulk import of the list of deleted books (uuid + date deleted).
     * Entries already present are simply skipped.
     *
     * @param list to import.
     *
     * @return the number of entries actually inserted; can be {@code 0}.
     *
     * @see #getAll(LocalDateTime)
     * @see #sync()
     */
    @WorkerThread
    int importRecords(@NonNull Collection<Pair<String, String>> list);

    /**
     * Delete the known deleted books; i.e. checks which books have been
     * flagged as having been deleted on another of the user's devices
     * and deleted the actual books on this device.
     *
     * @return number of actual books deleted
     *
     * @see #getAll(LocalDateTime)
     * @see #importRecords(Collection)
     */
    int sync();

    /**
     * Purge/clear the entire deleted books table.
     * This does not affect/delete the actual books.
     * <p>
     * Note this is <strong>not</strong> called from {@link Purger#purge()}
     * as we will always seek the users permission to purge the dao/table.
     *
     * @return the number of rows deleted,
     *         or {@code -1} if an error occurred
     */
    @WorkerThread
    int purge();
}
