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
import androidx.annotation.WorkerThread;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public interface TocEntryDao
        extends EntityDao<TocEntry> {

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
     * Get a list of book ID's (most often just the one) in which this {@link TocEntry}
     * (story) is present.
     *
     * @param tocId id of the entry (story)
     *
     * @return list with book ID's
     */
    @NonNull
    List<Long> getBookIds(long tocId);

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
    List<TocEntry> getByBookId(@IntRange(from = 1) long bookId);

    /**
     * Count the {@link TocEntry}'s for the given {@link Author}.
     *
     * @param author to count the TocEntries of
     *
     * @return the number of {@link TocEntry} this {@link Author} has
     */
    long count(@NonNull Author author);

    /**
     * Saves a list of {@link TocEntry} items.
     * <ol>
     *     <li>The list is pruned first.</li>
     *     <li>New authors will be inserted. No updates.</li>
     *     <li>TocEntry's existing in the database will be updated, new ones inserted.</li>
     *     <li>Creates the links between {@link Book} and {@link TocEntry}
     *         in {@link DBDefinitions#TBL_BOOK_TOC_ENTRIES}</li>
     * </ol>
     * <strong>Transaction:</strong> required
     *
     * @param context        Current context
     * @param bookId         of the book
     * @param list           the list of {@link TocEntry}
     * @param localeSupplier a supplier to get the Locale; called for each item in the list
     *
     * @throws DaoWriteException on failure
     * @throws RuntimeException  the caller <strong>MUST</strong> handle these
     */
    void insertOrUpdate(@NonNull Context context,
                        @IntRange(from = 1) long bookId,
                        @NonNull Collection<TocEntry> list,
                        @NonNull Function<TocEntry, Locale> localeSupplier)
            throws DaoWriteException;

    /**
     * Passed a list of Objects, remove duplicates.
     *
     * @param context        Current context
     * @param list           List to clean up
     * @param localeSupplier deferred supplier for a {@link Locale}
     *
     * @return {@code true} if the list was modified.
     */
    default boolean pruneList(@NonNull final Context context,
                              @NonNull final Collection<TocEntry> list,
                              @NonNull final Function<TocEntry, Locale> localeSupplier) {
        return pruneList(context, list, Prefs.normalizeTocEntryName(context), localeSupplier);
    }

    /**
     * Remove duplicates. We keep the first occurrence.
     *
     * @param context        Current context
     * @param list           List to clean up
     * @param normalize      flag, whether to normalize the title
     * @param localeSupplier deferred supplier for a {@link Locale}.
     *
     * @return {@code true} if the list was modified.
     */
    boolean pruneList(@NonNull Context context,
                      @NonNull Collection<TocEntry> list,
                      boolean normalize,
                      @NonNull Function<TocEntry, Locale> localeSupplier);

    /**
     * Delete orphaned records.
     */
    @WorkerThread
    void purge();
}
