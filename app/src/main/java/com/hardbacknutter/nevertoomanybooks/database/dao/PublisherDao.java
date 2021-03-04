/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

public interface PublisherDao
        extends BaseDao {

    /**
     * Get the {@link Publisher} based on the given id.
     *
     * @param id of {@link Publisher} to find
     *
     * @return the {@link Publisher}, or {@code null} if not found
     */
    @Nullable
    Publisher getById(long id);

    /**
     * Find a {@link Publisher} by using the appropriate fields of the passed {@link Publisher}.
     * The incoming object is not modified.
     *
     * @param context      Current context
     * @param publisher    to find the id of
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    long find(@NonNull Context context,
              @NonNull Publisher publisher,
              boolean lookupLocale,
              @NonNull Locale bookLocale);

    /**
     * Get a unique list of all publisher names.
     *
     * @return The list
     */
    @NonNull
    ArrayList<String> getNames();

    /**
     * Get a list of book ID's for the given {@link Publisher}.
     *
     * @param publisherId id of the {@link Publisher}
     *
     * @return list with book ID's
     */
    @NonNull
    ArrayList<Long> getBookIds(long publisherId);

    /**
     * Get a list of book ID's for the given {@link Publisher} and {@link Bookshelf}.
     *
     * @param publisherId id of the {@link Publisher}
     * @param bookshelfId id of the {@link Bookshelf}
     *
     * @return list with book ID's
     */
    @NonNull
    ArrayList<Long> getBookIds(long publisherId,
                               long bookshelfId);

    /**
     * Get all publishers; mainly for the purpose of exports.
     *
     * @return Cursor over all publishers
     */
    @NonNull
    Cursor fetchAll();

    long count();

    /**
     * Count the books for the given {@link Publisher}.
     *
     * @param context    Current context
     * @param publisher  to retrieve
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of books
     */
    long countBooks(@NonNull Context context,
                    @NonNull Publisher publisher,
                    @NonNull Locale bookLocale);

    /**
     * Try to find the {@link Publisher}.
     * If found, update the id with the id as found in the database.
     *
     * @param context      Current context
     * @param publisher    to update
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @return the item id (also set on the item).
     */
    long fixId(@NonNull Context context,
               @NonNull Publisher publisher,
               boolean lookupLocale,
               @NonNull Locale bookLocale);

    /**
     * Refresh the passed {@link Publisher} from the database, if present.
     * Used to ensure that the current record matches the current DB if some
     * other task may have changed the {@link Publisher}.
     * <p>
     * Will NOT insert a new {@link Publisher} if not found.
     *
     * @param context    Current context
     * @param publisher  to refresh
     * @param bookLocale Locale to use if the item has none set
     */
    void refresh(@NonNull Context context,
                 @NonNull Publisher publisher,
                 @NonNull Locale bookLocale);

    /**
     * Insert a new {@link Publisher}.
     *
     * @param context    Current context
     * @param publisher  object to insert. Will be updated with the id.
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    long insert(@NonNull Context context,
                @NonNull Publisher publisher,
                @NonNull Locale bookLocale);

    /**
     * Update a {@link Publisher}.
     *
     * @param context    Current context
     * @param publisher  to update
     * @param bookLocale Locale to use if the item has none set
     *
     * @return {@code true} for success.
     */
    boolean update(@NonNull Context context,
                   @NonNull Publisher publisher,
                   @NonNull Locale bookLocale);

    /**
     * Delete the given {@link Publisher}.
     *
     * @param context   Current context
     * @param publisher to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull Context context,
                   @NonNull Publisher publisher);

    /**
     * Moves all books from the 'source' {@link Publisher}, to the 'destId' {@link Publisher}.
     * The (now unused) 'source' {@link Publisher} is deleted.
     *
     * @param context Current context
     * @param source  from where to move
     * @param destId  to move to
     *
     * @throws DaoWriteException on failure
     */
    void merge(@NonNull Context context,
               @NonNull Publisher source,
               long destId)
            throws DaoWriteException;

    /**
     * Delete orphaned records.
     */
    void purge();
}
