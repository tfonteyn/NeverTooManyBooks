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
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;

@SuppressWarnings("UnusedReturnValue")
public interface BookshelfDao
        extends EntityDao<Bookshelf>,
                MoveBooksDao<Bookshelf> {

    /**
     * Set the given {@link Bookshelf} as the current/preferred.
     *
     * @param context   Current context
     * @param bookshelf to set
     */
    void setAsPreferred(@NonNull Context context,
                        @NonNull Bookshelf bookshelf);

    /**
     * Get the specified bookshelf.
     *
     * @param context Current context
     * @param id      of bookshelf to get
     *
     * @return the bookshelf
     */
    @NonNull
    Optional<Bookshelf> getBookshelf(@NonNull Context context,
                                     long id);

    /**
     * Get the first of the specified bookshelf id's found.
     *
     * @param context Current context
     * @param ids     list of bookshelves to get in order of preference
     *
     * @return the bookshelf
     */
    @NonNull
    Optional<Bookshelf> getBookshelf(@NonNull Context context,
                                     @NonNull long... ids);

    /**
     * Find a {@link Bookshelf} with the given name.
     * <p>
     * Convenience method for {@link EntityDao#findByName(Context, Entity, Locale)}.
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
     * Convenience method, fetch all shelves, and return them as a List
     * ordered by Bookshelf name.
     * <p>
     * <strong>Note:</strong> we do not include the 'All Books' shelf.
     *
     * @return a list of all bookshelves in the database.
     */
    @NonNull
    List<Bookshelf> getAll();

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
     * Purge book list node state data for the given {@link Bookshelf}.
     * <p>
     * Called when a {@link Bookshelf} is deleted or manually from the
     * {@link Bookshelf} management context menu.
     *
     * @param bookshelf to purge
     *
     * @throws DaoWriteException on failure
     */
    void purgeNodeStates(@NonNull Bookshelf bookshelf)
            throws DaoWriteException;

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
     * @throws DaoWriteException on failure
     */
    void insertOrUpdate(@NonNull Context context,
                        @IntRange(from = 1) long bookId,
                        @NonNull Collection<Bookshelf> list)
            throws DaoWriteException;
}
