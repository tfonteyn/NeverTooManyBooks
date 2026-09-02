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

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

public interface IdentifierDao {

    /**
     * Find a {@link Identifier} by using the <strong>name</strong> field.
     * If found, updates <strong>ONLY</strong> the id with the one found in the database.
     *
     * @param identifier to update
     */
    void fixId(@NonNull Identifier identifier);

    /**
     * Remove duplicates. We keep the first occurrence.
     * <p>
     * Dev note: the {@code sid} value is actually handled here, but in a pure-data way.
     * i.o.w. not as a linked piece of key.
     *
     * @param list List to clean up
     *
     * @return {@code true} if the list was modified.
     */
    boolean pruneList(@NonNull Collection<Identifier.Value> list);

    /**
     * Find the {@link Identifier} for the given id.
     *
     * @param id of the {@link Identifier}
     *
     * @return {@link Identifier}
     */
    @NonNull
    Optional<Identifier> findById(@IntRange(from = 1) long id);

    /**
     * Find the {@link Identifier} for the given key/EntityType.
     *
     * @param key        of the {@link Identifier}
     * @param entityType of the {@link Identifier}
     *
     * @return {@link Identifier}
     */
    @NonNull
    Optional<Identifier> find(@NonNull String key,
                              @NonNull Identifier.EntityType entityType);

    /**
     * Convenience method, fetch all {@link Identifier}s, and return them as a List
     * ordered by {@link Identifier} name.
     *
     * @return a list of all {@link Identifier}s in the database.
     */
    @NonNull
    List<Identifier> getAll();

    /**
     * Convenience method, fetch all {@link Identifier}s, and return them as a List
     * ordered by {@link Identifier} name.
     *
     * @param entityType to get
     *
     * @return a list of all {@link Identifier}s in the database.
     */
    @NonNull
    List<Identifier> getAll(@NonNull Identifier.EntityType entityType);

    /**
     * Insert a new {@link Identifier}.
     *
     * @param identifier to insert. Will be updated with the id
     *
     * @return the row id of the newly inserted item
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1)
    long insert(@NonNull Identifier identifier)
            throws DaoWriteException;

    /**
     * Update the given {@link Identifier}.
     *
     * @param identifier to update
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Identifier identifier)
            throws DaoWriteException;

    /**
     * Delete the given {@link Identifier}.
     *
     * @param identifier to delete
     */
    void delete(@NonNull Identifier identifier);

    /**
     * Re-insert, or update the Identifiers which were set up
     * when the app was installed.
     * <p>
     * This is a repair/restore method for when users need to undo
     * any modifications they did to the preinstalled list of Identifiers.
     * <p>
     * The {@code key} of the identifier must not have been changed
     * for this to work successfully. The app's UI prevents key changes,
     * but users can edit JSON imports... in which case ... "oops, to bad".
     *
     * @param context Current context
     */
    void restore(@NonNull Context context);
}
