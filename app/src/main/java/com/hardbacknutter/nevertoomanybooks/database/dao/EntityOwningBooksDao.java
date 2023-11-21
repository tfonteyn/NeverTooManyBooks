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

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

/**
 * DAO interface for entities which have a 1:N relation with a book.
 *
 * @param <T> Entity managed by the DAO (e.g. Author, Publisher,..)
 */
public interface EntityOwningBooksDao<T extends Entity> {

    /**
     * Check for books which do not have a {@link T} at position 1.
     * For those that don't, read their list, and re-save them.
     *
     * @param context Current context
     *
     * @return the number of books processed
     */
    int fixPositions(@NonNull Context context);

    /**
     * Get a simple/total count of the items.
     *
     * @return count
     */
    long count();

    /**
     * Count the books for the given {@link T}.
     *
     * @param context    Current context
     * @param item       to count the books of
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of books
     */
    long countBooks(@NonNull Context context,
                    @NonNull T item,
                    @NonNull Locale bookLocale);

    /**
     * Get a list of book ID's for the given {@link T}.
     *
     * @param itemId id of the item
     *
     * @return list with book ID's linked to this item
     */
    @NonNull
    List<Long> getBookIds(long itemId);

    /**
     * Get a list of book ID's for the given {@link T} and {@link Bookshelf}.
     *
     * @param itemId      id of the {@link T}
     * @param bookshelfId id of the {@link Bookshelf}
     *
     * @return list with book ID's linked to this item
     *         which are present on the given {@link Bookshelf}
     */
    @NonNull
    List<Long> getBookIds(long itemId,
                          long bookshelfId);

    /**
     * Get a list of the {@link T} for a book.
     *
     * @param bookId of the book
     *
     * @return list
     */
    @NonNull
    List<T> getByBookId(@IntRange(from = 1) long bookId);

    /**
     * Insert or update a list of {@link T}'s linked to a single {@link Book}.
     * <p>
     * The list is pruned before storage.
     * New {@link T}'s are added to the {@link T} table, existing ones are NOT updated
     * unless explicitly allowed by the {@code doUpdates} parameter.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context      Current context
     * @param bookId       of the book
     * @param doUpdates    set to {@code true} to force each {@link T} to be updated.
     *                     <strong>ONLY</strong> set this when actually needed.
     *                     Do not set this during for example an import.
     * @param list         the list of {@link T}'s
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertOrUpdate(@NonNull Context context,
                        @IntRange(from = 1) long bookId,
                        boolean doUpdates,
                        @NonNull Collection<T> list,
                        boolean lookupLocale,
                        @NonNull Locale bookLocale)
            throws DaoWriteException;

    /**
     * Insert a new {@link T}.
     *
     * @param context    Current context
     * @param item       to insert. Will be updated with the id
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the row id of the newly inserted item
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1, to = Integer.MAX_VALUE)
    long insert(@NonNull Context context,
                @NonNull T item,
                @NonNull Locale bookLocale)
            throws DaoWriteException;

    /**
     * Update the given {@link T}.
     *
     * @param context    Current context
     * @param item       to update
     * @param bookLocale Locale to use if the item has none set
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Context context,
                @NonNull T item,
                @NonNull Locale bookLocale)
            throws DaoWriteException;

    /**
     * Delete the given {@link T}.
     *
     * @param context Current context
     * @param item    to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Context context,
                   @NonNull T item);

    /**
     * Refresh the passed {@link T} from the database, if present.
     * Used to ensure that the current record matches the content of the database
     * should some other task have changed the {@link T}.
     * <p>
     * Will <strong>NOT</strong> insert a new {@link T} if not found;
     * instead the id of the item will be set to {@code 0}, i.e. 'new'.
     *
     * @param context Current context
     * @param item    to refresh
     * @param locale  to use
     */
    void refresh(@NonNull Context context,
                 @NonNull T item,
                 @NonNull Locale locale);

    /**
     * Delete orphaned records.
     */
    void purge();
}
