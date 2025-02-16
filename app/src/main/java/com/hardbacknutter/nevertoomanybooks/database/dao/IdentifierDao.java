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

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

/**
 * <strong>External-id</strong> or <strong>sid</strong>:
 * a book-id as defined by an external (to this app) source,
 * usually a website. Hence <strong>sid</strong>: site-id.
 * <p>
 * <strong>{@link Identifier}</strong>: a NAME for an external/site book-id.
 * <p>
 * <strong>Note:</strong>'ISBN' has dedicated handling and is NOT included here.
 */
public interface IdentifierDao {

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
     * Find the {@link Identifier} for the given name.
     *
     * @param key of the {@link Identifier}
     *
     * @return {@link Identifier}
     */
    @NonNull
    Optional<Identifier> findByKey(@NonNull String key);

    /**
     * Convenience method, fetch all {@link Identifier}s, and return them as a List
     * ordered by {@link Identifier} name.
     *
     * @return a list of all {@link Identifier}s in the database.
     */
    @NonNull
    List<Identifier> getAll();

    /**
     * Insert a new {@link Identifier}.
     *
     * @param context Current context
     * @param identifier to insert. Will be updated with the id
     *
     * @return the row id of the newly inserted item
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1)
    long insert(@NonNull Context context,
                @NonNull Identifier identifier)
            throws DaoWriteException;

    /**
     * Update the given {@link Identifier}.
     *
     * @param context Current context
     * @param identifier to update
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Context context,
                @NonNull Identifier identifier)
            throws DaoWriteException;

    /**
     * Delete the given {@link Identifier}.
     *
     * @param identifier to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Identifier identifier);

    boolean pruneList(@NonNull Collection<Identifier.Value> list);

    /**
     * Find a {@link Identifier} by using the <strong>name</strong> field.
     * If found, updates <strong>ONLY</strong> the id with the one found in the database.
     *
     * @param identifier to update
     */
    void fixId(@NonNull Identifier identifier);

    /**
     * Insert or update a list of {@link Identifier.Value}s linked to a single {@link Book}.
     * New {@link Identifier}s are added to the {@link Identifier} table,
     * existing ones are NOT updated (nothing to do).
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context Current context
     * @param bookId  of the book
     * @param list    the list of {@link Identifier.Value}s
     *
     * @throws DaoWriteException on failure
     */
    void insertOrUpdate(@NonNull Context context,
                        @IntRange(from = 1) long bookId,
                        @NonNull Collection<Identifier.Value> list)
            throws DaoWriteException;

    /**
     * Get a list of all {@link Identifier.Value}s for the given book id.
     *
     * @param bookId to get
     *
     * @return list
     */
    @NonNull
    List<Identifier.Value> getByBookId(@IntRange(from = 1) long bookId);

    /**
     * Count the books for the given {@link Identifier}.
     *
     * @param identifier to count the books of
     *
     * @return the number of books
     */
    int countBooks(@NonNull Identifier identifier);

    /**
     * Get the SID value for the given {@link Identifier} of the given book id.
     *
     * @param key to get
     * @param bookId         for this book id
     *
     * @return sid value
     */
    @NonNull
    Optional<String> findSid(@NonNull String key,
                             @IntRange(from = 1) long bookId);

    /**
     * Find the book id for the given SID and name.
     *
     * @param key one of the {@link Identifier} SID constants
     * @param sid            value
     *
     * @return book id, or {@code 0} if none found
     */
    @IntRange(from = 0)
    long findBookId(@NonNull String key,
                    @NonNull String sid);
}
