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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

/**
 * <strong>External-id</strong> or <strong>sid</strong>:
 * a book-id, author-id, ... as defined by an external (to this app) source,
 * usually a website. Hence, <strong>sid</strong>: site-id.
 * <p>
 * <strong>{@link Identifier}</strong>: a NAME for an external/site-id.
 * <p>
 * <strong>Note:</strong>'ISBN' has dedicated handling and is NOT included here.
 */
public interface IdentifierValueDao {

    /**
     * Insert or update a list of {@link Identifier.Value}s linked to a single item.
     * New {@link Identifier}s are added to the {@link Identifier} table,
     * existing ones are NOT updated (nothing to do).
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param entityType type of the foreign key entity
     * @param fkId       foreign-key id
     * @param list       the list of {@link Identifier.Value}s
     *
     * @throws DaoWriteException on failure
     */
    void insertOrUpdate(@NonNull Identifier.EntityType entityType,
                        @IntRange(from = 1) long fkId,
                        @NonNull Collection<Identifier.Value> list)
            throws DaoWriteException;

    /**
     * Get a list of all {@link Identifier.Value}s for the given foreign-key id.
     *
     * @param fkId foreign-key id
     *
     * @return list
     */
    @NonNull
    List<Identifier.Value> getByFkId(@IntRange(from = 1) long fkId);

    /**
     * Count the foreign-key ids for the given {@link Identifier}.
     *
     * @param identifier to count the foreign-key ids of
     *
     * @return the number of links
     */
    int countLinks(@NonNull Identifier identifier);

    /**
     * Get the SID value for the given {@link Identifier} of the given foreign-key id.
     *
     * @param key  to get
     * @param fkId foreign-key id
     *
     * @return sid value
     */
    @NonNull
    Optional<String> findSid(@NonNull String key,
                             @IntRange(from = 1) long fkId);

    /**
     * Find the foreign-key id for the given SID and name.
     *
     * @param key one of the {@link Identifier} SID constants
     * @param sid value
     *
     * @return foreign-key id
     */
    @NonNull
    Optional<Long> findFkId(@NonNull String key,
                            @NonNull String sid);
}
