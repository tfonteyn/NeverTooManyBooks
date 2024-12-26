/*
 * @Copyright 2018-2024 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.core.database.DaoInsertException;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoUpdateException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;

public interface IdentifierDao {

    @NonNull
    Optional<Identifier> findById(@IntRange(from = 1) long id);

    @NonNull
    Optional<Identifier> findByName(@NonNull Identifier identifier);

    @NonNull
    Optional<Identifier> findByName(@NonNull String name);

    /**
     * Convenience method, fetch all identifiers, and return them as a List
     * ordered by Identifier name.
     * <p>
     * <strong>Note:</strong> we do not include the 'ISBN'.
     *
     * @return a list of all Identifiers in the database.
     */
    @NonNull
    List<Identifier> getAll();

    @IntRange(from = 1)
    long insert(@NonNull Identifier identifier)
            throws DaoInsertException;

    void update(@NonNull Identifier identifier)
            throws DaoUpdateException;

    boolean delete(@NonNull Identifier identifier);

    void fixId(@NonNull Identifier identifier);

    void insertOrUpdate(@NonNull Book book,
                        boolean doUpdates)
            throws DaoInsertException, DaoUpdateException;

    @NonNull
    List<Identifier.Value> getByBookId(@IntRange(from = 1) long bookId);

    /**
     * Find the book id for the given SID and name.
     *
     * @param identifier to use
     * @param sid        value
     *
     * @return book id, or {@code 0} if none found
     */
    @IntRange(from = 0)
    long getBookId(@NonNull Identifier identifier,
                   @NonNull String sid);

    /**
     * Find the book id for the given SID and name.
     *
     * @param identifierName one of the {@link Identifier} SID constants
     * @param sid            value
     *
     * @return book id, or {@code 0} if none found
     */
    @IntRange(from = 0)
    long getBookId(@NonNull String identifierName,
                   @NonNull String sid);
}
