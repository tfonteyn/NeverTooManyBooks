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
import com.hardbacknutter.nevertoomanybooks.entities.Series;

public interface SeriesDao
        extends BaseDao {

    /**
     * Get the {@link Series} based on the given id.
     *
     * @param id of {@link Series} to find
     *
     * @return the {@link Series}, or {@code null} if not found
     */
    @Nullable
    Series getById(long id);

    /**
     * Find a {@link Series} by using the appropriate fields of the passed {@link Series}.
     * The incoming object is not modified.
     *
     * @param context      Current context
     * @param series       to find the id of
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    long find(@NonNull Context context,
              @NonNull Series series,
              boolean lookupLocale,
              @NonNull Locale bookLocale);

    /**
     * Get a unique list of all {@link Series} titles.
     *
     * @return The list
     */
    @NonNull
    ArrayList<String> getNames();

    /**
     * Get the language (ISO3) code for a {@link Series}.
     * This is defined as the language code for the first book in the {@link Series}.
     *
     * @param id series to get
     *
     * @return the ISO3 code, or the empty String when none found.
     */
    @NonNull
    String getLanguage(long id);

    /**
     * Get a list of book ID's for the given {@link Series}.
     *
     * @param seriesId id of the {@link Series}
     *
     * @return list with book ID's
     */
    @NonNull
    ArrayList<Long> getBookIds(long seriesId);

    /**
     * Get a list of book ID's for the given {@link Series} and {@link Bookshelf}.
     *
     * @param seriesId    id of the {@link Series}
     * @param bookshelfId id of the {@link Bookshelf}
     *
     * @return list with book ID's
     */
    @NonNull
    ArrayList<Long> getBookIds(long seriesId,
                               long bookshelfId);

    /**
     * Get all series; mainly for the purpose of exports.
     *
     * @return Cursor over all series
     */
    @NonNull
    Cursor fetchAll();

    long count();

    /**
     * Count the books for the given {@link Series}.
     *
     * @param context    Current context
     * @param series     to count the books in
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of books
     */
    long countBooks(@NonNull Context context,
                    @NonNull Series series,
                    @NonNull Locale bookLocale);

    /**
     * Update the 'complete' status of a {@link Series}.
     *
     * @param seriesId   to update
     * @param isComplete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    boolean setComplete(long seriesId,
                        boolean isComplete);

    /**
     * Try to find the {@link Series}.
     * If found, update the id with the id as found in the database.
     *
     * @param context      Current context
     * @param series       to update
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @return the item id (also set on the item).
     */
    long fixId(@NonNull Context context,
               @NonNull Series series,
               boolean lookupLocale,
               @NonNull Locale bookLocale);

    /**
     * Refresh the passed {@link Series} from the database, if present.
     * Used to ensure that the current record matches the current DB if some
     * other task may have changed the {@link Series}.
     * <p>
     * Will NOT insert a new {@link Series} if not found.
     *
     * @param context    Current context
     * @param series     to refresh
     * @param bookLocale Locale to use if the item has none set
     */
    void refresh(@NonNull Context context,
                 @NonNull Series series,
                 @NonNull Locale bookLocale);

    /**
     * Insert a new {@link Series}.
     *
     * @param context    Current context
     * @param series     object to insert. Will be updated with the id.
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    long insert(@NonNull Context context,
                @NonNull Series series,
                @NonNull Locale bookLocale);

    /**
     * Update a {@link Series}.
     *
     * @param context    Current context
     * @param series     to update
     * @param bookLocale Locale to use if the item has none set
     *
     * @return {@code true} for success.
     */
    boolean update(@NonNull Context context,
                   @NonNull Series series,
                   @NonNull Locale bookLocale);

    /**
     * Delete the given {@link Series}.
     *
     * @param context Current context
     * @param series  to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull Context context,
                   @NonNull Series series);

    /**
     * Moves all books from the 'source' {@link Series}, to the 'destId' {@link Series}.
     * The (now unused) 'source' {@link Series} is deleted.
     *
     * @param context Current context
     * @param source  from where to move
     * @param destId  to move to
     *
     * @throws DaoWriteException on failure
     */
    void merge(@NonNull Context context,
               @NonNull Series source,
               long destId)
            throws DaoWriteException;

    /**
     * Delete orphaned records.
     */
    void purge();
}
