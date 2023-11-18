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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomField;

public interface CalibreCustomFieldDao {

    /**
     * Find a {@link CalibreCustomField} by using the <strong>name</strong> fields.
     * If found, updates <strong>ONLY</strong> the id with the one found in the database.
     * <p>
     * If the item has 'sub' items, then implementations must propagate the call.
     *
     * @param calibreCustomField to update
     */
    void fixId(@NonNull CalibreCustomField calibreCustomField);

    /**
     * Creates a new {@link CalibreCustomField} in the database.
     *
     * @param calibreCustomField object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1, to = Integer.MAX_VALUE)
    long insert(@NonNull CalibreCustomField calibreCustomField)
            throws DaoWriteException;

    /**
     * Update a {@link CalibreCustomField}.
     *
     * @param calibreCustomField to update
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull CalibreCustomField calibreCustomField)
            throws DaoWriteException;

    /**
     * Delete the passed {@link CalibreCustomField}.
     *
     * @param calibreCustomField to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull CalibreCustomField calibreCustomField);

    /**
     * Get a list of all the custom fields we have local knowledge of.
     *
     * @return list
     */
    @NonNull
    List<CalibreCustomField> getCustomFields();


}
