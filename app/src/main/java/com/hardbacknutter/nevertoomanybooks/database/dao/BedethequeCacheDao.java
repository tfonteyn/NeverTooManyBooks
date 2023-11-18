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

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BdtAuthor;

public interface BedethequeCacheDao {

    /**
     * Find a {@link BdtAuthor} with the given name.
     *
     * @param name   to find
     * @param locale to use
     *
     * @return the {@link BdtAuthor}, or {@code null} if not found
     */
    @NonNull
    Optional<BdtAuthor> findByName(@NonNull String name,
                                   @NonNull Locale locale);

    /**
     * Find a {@link BdtAuthor} by using the <strong>name</strong> fields.
     * If found, updates <strong>ONLY</strong> the id with the one found in the database.
     * <p>
     * If the item has 'sub' items, then implementations must propagate the call.
     *
     * @param bdtAuthor to update
     * @param locale    to use
     */
    void fixId(@NonNull BdtAuthor bdtAuthor,
               @NonNull Locale locale);

    /**
     * Create the {@link BdtAuthor}s as supplied into the database.
     * <p>
     * There can be 100's or even 1000's of Authors. Pass an Supplier
     * to the insert method so we can read/insert one-by-one.
     * <p>
     * Because this method deals with a list of items to insert, it returns a simple boolean.
     *
     * @param locale         to use
     * @param recordSupplier a supplier which delivers a {@link BdtAuthor} to insert,
     *                       or {@code null} when done.
     *
     * @return {@code true} if at least one row was inserted
     *
     * @throws DaoWriteException on failure
     */
    boolean insert(@NonNull Locale locale,
                   @NonNull Supplier<BdtAuthor> recordSupplier)
            throws DaoWriteException;

    /**
     * Update the given {@link BdtAuthor}.
     *
     * @param bdtAuthor to update
     * @param locale    to use
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull BdtAuthor bdtAuthor,
                @NonNull Locale locale)
            throws DaoWriteException;

    /**
     * Check if there is at least one {@link BdtAuthor} in the database whose name
     * starts with the given character.
     * <p>
     * This makes the assumption if there is one, then the whole list-page for that character
     * has been downloaded and cached.
     *
     * @param c1 to lookup
     *
     * @return {@code true} if there is
     */
    boolean isAuthorPageCached(char c1);

    /**
     * Get the total amount of authors cached.
     *
     * @return amount
     */
    int countAuthors();

    /**
     * Clear the entire cache.
     */
    void clearCache();
}
