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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public interface TocEntryDao {

    /**
     * Passed a list of Objects, remove duplicates.
     *
     * @param context Current context
     * @param list    List to clean up
     *
     * @return {@code true} if the list was modified.
     */
    boolean pruneList(@NonNull Context context,
                      @NonNull Collection<TocEntry> list,
                      @NonNull Function<TocEntry, Locale> localeSupplier);

    /**
     * Tries to find the item in the database using all or some of its fields (except the id).
     * If found, sets the item's id with the id found in the database.
     * <p>
     * If the item has 'sub' items, then it should call those as well.
     *
     * @param context        Current context
     * @param tocEntry       to update
     * @param localeSupplier deferred supplier for a {@link Locale}.
     */
    void fixId(@NonNull Context context,
               @NonNull TocEntry tocEntry,
               @NonNull Supplier<Locale> localeSupplier);

    /**
     * Return the {@link TocEntry} id. The incoming object is not modified.
     * Note that the publication year is NOT used for comparing, under the assumption that
     * two search-sources can give different dates by mistake.
     *
     * @param context  Current context
     * @param tocEntry to search for
     * @param localeSupplier deferred supplier for a {@link Locale}.
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    long find(@NonNull Context context,
              @NonNull TocEntry tocEntry,
              @NonNull Supplier<Locale> localeSupplier);

    /**
     * Get all TOC entries; mainly for the purpose of backups.
     *
     * @return Cursor over all TOC entries
     */
    @NonNull
    Cursor fetchAll();

    /**
     * Return a list of paired book-id and book-title 's for the given TOC id.
     * <p>
     * The titles are returned "as-is". If re-ordering is needed, the caller must do this
     * after getting the list.
     *
     * @param id     TOC id
     * @param author the Author will be used when creating the BookLight objects.
     *
     * @return list of id/titles/language of books.
     */
    @NonNull
    List<BookLight> getBookTitles(@IntRange(from = 1) long id,
                                  @NonNull Author author);

    /**
     * Get the list of {@link TocEntry}'s for this book.
     *
     * @param bookId of the book
     *
     * @return list
     */
    @NonNull
    ArrayList<TocEntry> getByBookId(@IntRange(from = 1) long bookId);

    /**
     * Get a list of book ID's (most often just the one) in which this {@link TocEntry}
     * (story) is present.
     *
     * @param tocId id of the entry (story)
     *
     * @return list with book ID's
     */
    @NonNull
    ArrayList<Long> getBookIds(long tocId);

    /**
     * Delete the passed {@link TocEntry}.
     *
     * @param context  Current context
     * @param tocEntry to delete.
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Context context,
                   @NonNull TocEntry tocEntry);

    /**
     * Check for books which do not have a {@link TocEntry} at position 1.
     * For those that don't, read their list, and re-save them.
     *
     * @param context Current context
     *
     * @return the number of books processed
     */
    int fixPositions(@NonNull Context context);

    /**
     * Delete orphaned records.
     */
    void purge();
}
