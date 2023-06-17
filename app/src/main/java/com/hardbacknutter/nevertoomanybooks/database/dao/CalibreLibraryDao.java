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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreVirtualLibrary;

public interface CalibreLibraryDao {

    /**
     * Get the <strong>physical</strong> {@link CalibreLibrary} for the given row id.
     *
     * @param id to lookup
     *
     * @return physical library
     */
    @Nullable
    CalibreLibrary getLibraryById(long id);

    /**
     * Get the <strong>physical</strong> {@link CalibreLibrary} for the given uuid.
     *
     * @param uuid to lookup
     *
     * @return physical library
     */
    @Nullable
    CalibreLibrary findLibraryByUuid(@NonNull String uuid);

    /**
     * Get the <strong>physical</strong> {@link CalibreLibrary} for the given libraryStringId.
     *
     * @param libraryStringId to lookup
     *
     * @return physical library
     */
    @Nullable
    CalibreLibrary findLibraryByStringId(@NonNull String libraryStringId);

    @NonNull
    List<CalibreLibrary> getAllLibraries();

    /**
     * Get the <strong>virtual</strong> {@link CalibreLibrary} for the given library + name.
     * The mapped {@link Bookshelf} will have been resolved.
     *
     * @param libraryId row id for the physical library
     * @param name      of the virtual library to lookup
     *
     * @return virtual library
     */
    @Nullable
    CalibreVirtualLibrary getVirtualLibrary(long libraryId,
                                            @NonNull String name);

    /**
     * Update a {@link CalibreVirtualLibrary}.
     *
     * @param library to update
     *
     * @return {@code true} for success.
     */
    boolean update(@NonNull CalibreVirtualLibrary library);

    /**
     * Creates a new {@link CalibreLibrary} in the database.
     *
     * @param library object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    long insert(@NonNull CalibreLibrary library);

    /**
     * Find a {@link CalibreLibrary} by using the <strong>name</strong> fields.
     * If found, updates <strong>ONLY</strong> the id with the one found in the database.
     * <p>
     * If the item has 'sub' items, then implementations must propagate the call.
     *
     * @param context Current context
     * @param library to update
     */
    void fixId(@NonNull Context context,
               @NonNull CalibreLibrary library);

    /**
     * Update a {@link CalibreLibrary}.
     *
     * @param library to update
     *
     * @return {@code true} for success.
     */
    boolean update(@NonNull CalibreLibrary library);

    /**
     * Delete the passed {@link CalibreLibrary}.
     *
     * @param library to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull CalibreLibrary library);

    /**
     * Check that a book with the passed Calibre UUID exists and return the id of the book, or zero.
     *
     * @param uuid Calibre UUID
     *
     * @return id of the book, or 0 'new' if not found
     */
    @IntRange(from = 0)
    long getBookIdFromCalibreUuid(@NonNull String uuid);
}
