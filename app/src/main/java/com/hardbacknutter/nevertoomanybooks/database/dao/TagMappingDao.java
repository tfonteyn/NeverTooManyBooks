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

import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

/**
 * Note that the external-tag names MUST/are always be lowercase.
 */
public interface TagMappingDao {

    /**
     * Get all mapping.
     * <p>
     * <strong>The key in the map, i.e. the external tag name</strong> will be all-lowercase.
     *
     * @return mappings
     */
    @NonNull
    Map<String, Set<String>> getAll();

    /**
     * Insert a new mapping pair.
     *
     * @param extTag   the <strong>external/site</strong> tag name
     *                 MUST be all-lowercase.
     * @param mappings the set of <strong>internal/local</strong> tags to replace the above with.
     *
     * @return the row id of the newly inserted item
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1)
    long insert(@NonNull String extTag,
                @NonNull Set<String> mappings)
            throws DaoWriteException;

    /**
     * Delete the given {@link Identifier}.
     *
     * @param extTag <strong>external/site</strong> tag name to delete
     *               MUST be all-lowercase.
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull String extTag);
}
