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

package com.hardbacknutter.nevertoomanybooks.database.dao;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.TagMapping;

public interface TagMappingDao {

    /**
     * Find a {@link TagMapping} by using the <strong>name</strong> field.
     * If found, updates <strong>ONLY</strong> the id with the one found in the database.
     *
     * @param mapping to update
     */
    void fixId(@NonNull TagMapping mapping);

    /**
     * Get all mappings.
     *
     * @return mappings
     */
    @NonNull
    List<TagMapping> getAll();

    /**
     * Insert a new mapping pair.
     *
     * @param mapping to insert
     *
     * @return the row id of the newly inserted item
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1)
    long insert(@NonNull TagMapping mapping)
            throws DaoWriteException;

    /**
     * Update the given {@link TagMapping}.
     *
     * @param mapping to update
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull TagMapping mapping)
            throws DaoWriteException;

    /**
     * Delete the given {@link TagMapping}.
     *
     * @param mapping to delete
     */
    void delete(@NonNull TagMapping mapping);

    /**
     * Find a {@link TagMapping} by using the <strong>name</strong> fields
     * of the given {@link TagMapping}.
     * The given {@link TagMapping} is <strong>not</strong> modified.
     *
     * @param mapping to find the id of
     *
     * @return the {@link TagMapping}
     */
    @NonNull
    Optional<TagMapping> findByName(@NonNull TagMapping mapping);
}
