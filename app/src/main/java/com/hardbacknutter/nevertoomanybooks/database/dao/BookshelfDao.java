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
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

@SuppressWarnings("UnusedReturnValue")
public interface BookshelfDao
        extends MoveBooksDao<Bookshelf> {

    /**
     * Find a {@link Bookshelf} by using the <strong>name</strong> fields
     * of the passed {@link Bookshelf}. The incoming object is not modified.
     *
     * @param bookshelf to find the id of
     *
     * @return the Bookshelf
     */
    @NonNull
    Optional<Bookshelf> findByName(@NonNull Bookshelf bookshelf);

    /**
     * Find a {@link Bookshelf} with the given name.
     *
     * @param name of bookshelf to find
     *
     * @return the Bookshelf
     */
    @NonNull
    Optional<Bookshelf> findByName(@NonNull String name);

    /**
     * Get the filters defined for the given bookshelf.
     *
     * @param bookshelfId to fetch
     *
     * @return filters
     */
    @NonNull
    List<PFilter<?>> getFilters(long bookshelfId);

    /**
     * Convenience method, fetch all shelves, and return them as a List.
     * <p>
     * <strong>Note:</strong> we do not include the 'All Books' shelf.
     *
     * @return a list of all bookshelves in the database.
     */
    @NonNull
    List<Bookshelf> getAll();

    /**
     * Get all Bookshelves; mainly for the purpose of exports.
     * <strong>Note:</strong> we do not include the 'All Books' shelf.
     *
     * @return Cursor over all Bookshelves
     */
    @NonNull
    Cursor fetchAllUserShelves();

    /**
     * Passed a list of Objects, remove duplicates. We keep the first occurrence.
     *
     * @param context Current context
     * @param list    List to clean up
     *
     * @return {@code true} if the list was modified.
     */
    boolean pruneList(@NonNull Context context,
                      @NonNull Collection<Bookshelf> list);

    /**
     * Tries to find the item in the database using all or some of its fields (except the id).
     * If found, sets the item's id with the id found in the database.
     *
     * @param bookshelf to update
     */
    void fixId(@NonNull Bookshelf bookshelf);

    /**
     * Insert or update a list of {@link Bookshelf}'s linked to a single {@link Book}.
     * <p>
     * The list is pruned before storage.
     * New shelves are added, existing ones are NOT updated.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context Current context
     * @param bookId  of the book
     * @param list    the list of bookshelves
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertOrUpdate(@NonNull Context context,
                        @IntRange(from = 1) long bookId,
                        @NonNull Collection<Bookshelf> list)
            throws DaoWriteException;

    /**
     * Creates a new bookshelf in the database.
     *
     * @param context   Current context
     * @param bookshelf object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted item
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1, to = Integer.MAX_VALUE)
    long insert(@NonNull Context context,
                @NonNull Bookshelf bookshelf)
            throws DaoWriteException;

    /**
     * Update a bookshelf.
     *
     * @param context   Current context
     * @param bookshelf to update
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Context context,
                @NonNull Bookshelf bookshelf)
            throws DaoWriteException;


    /**
     * Delete the passed {@link Bookshelf}.
     * Cleans up {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE} as well.
     *
     * @param bookshelf to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Bookshelf bookshelf);

    /**
     * Purge book list node state data for the given {@link Bookshelf}.
     * <p>
     * Called when a {@link Bookshelf} is deleted or manually from the
     * {@link Bookshelf} management context menu.
     *
     * @param bookshelfId to purge
     */
    void purgeNodeStates(long bookshelfId);

    /**
     * Get a list of book ID's for the given {@link Bookshelf}.
     *
     * @param itemId id of the item
     *
     * @return list with book ID's linked to this item
     */
    @NonNull
    List<Long> getBookIds(long itemId);

    /**
     * Get a list of all the bookshelves this book is on.
     *
     * @param bookId to use
     *
     * @return the list
     */
    @NonNull
    List<Bookshelf> getByBookId(@IntRange(from = 1) long bookId);
}
