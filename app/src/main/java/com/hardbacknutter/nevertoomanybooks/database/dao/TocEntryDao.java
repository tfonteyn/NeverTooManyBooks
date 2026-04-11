/*
 * @Copyright 2018-2026 HardBackNutter
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
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.Positional;
import com.hardbacknutter.nevertoomanybooks.database.Purgeable;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLite;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

public interface TocEntryDao
        extends Purgeable, Positional {

    /**
     * Preference key: whether to normalise the title during pruning.
     * <p>
     * Type: {@code boolean}
     *
     * @see #pruneList(Context, Collection, Function)
     * @see #pruneList(Context, Collection, boolean, Function)
     * @see #pruneList(Context, Collection, boolean, Function, BiConsumer)
     */
    String PK_NORMALISE_TOC_TITLE = "normalize.toc.title";

    /**
     * Get a list of book ID's (most often just the one) in which this {@link TocEntry}
     * (story) is present.
     *
     * @param tocId id of the entry (story)
     *
     * @return list with book ID's, can be empty if the TOCEntry is orphaned
     */
    @NonNull
    List<Long> getBookIds(long tocId);

    /**
     * Return a list of paired book-id and book-title 's for the given {@link TocEntry}.
     * The primary author is used as the author for the returned {@link BookLite} objects.
     * <p>
     * The titles are returned "as-is". If re-ordering is needed, the caller must do this
     * after getting the list.
     *
     * @param tocEntry to use
     *
     * @return list of id/titles/language of books.
     */
    @NonNull
    List<BookLite> getBookTitles(@NonNull TocEntry tocEntry);

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
    int count(@NonNull Author author);

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
        final boolean normalise = ServiceLocator.getInstance().getSharedPreferences()
                                                .getBoolean(PK_NORMALISE_TOC_TITLE, false);
        return pruneList(context, list, normalise, localeSupplier);
    }

    /**
     * Remove duplicates. We keep the first occurrence.
     *
     * @param context        Current context
     * @param list           List to clean up
     * @param normalise      flag, whether to normalise the title
     * @param localeSupplier deferred supplier for a {@link Locale}.
     *
     * @return {@code true} if the list was modified.
     */
    default boolean pruneList(@NonNull final Context context,
                              @NonNull final Collection<TocEntry> list,
                              final boolean normalise,
                              @NonNull final Function<TocEntry, Locale> localeSupplier) {
        return pruneList(context, list, normalise, localeSupplier,
                // Don't look up the locale a 2nd time.
                         (current, locale) -> fixId(context, current, locale));
    }

    /**
     * Remove duplicates. We keep the first occurrence.
     * <p>
     * <strong>Tests only.</strong>
     * Allows overriding the normalisation flag and use a custom (nop) id-fixeer.
     *
     * @param context        Current context
     * @param list           List to clean up
     * @param normalise      flag, whether to normalise the title
     * @param localeSupplier deferred supplier for a {@link Locale}.
     * @param idFixer        how to call {@link #fixId(Context, TocEntry, Locale)}
     *
     * @return {@code true} if the list was modified.
     */
    @VisibleForTesting
    boolean pruneList(@NonNull Context context,
                      @NonNull Collection<TocEntry> list,
                      boolean normalise,
                      @NonNull Function<TocEntry, Locale> localeSupplier,
                      @NonNull BiConsumer<TocEntry, Locale> idFixer);
    /**
     * Rebuild the OB columns for the table(s) of this dao.
     *
     * @param context       Current context
     * @param locale        Current Locale
     * @param reorderHelper helper
     *
     * @return the number of rows actually updated
     */
    @WorkerThread
    int rebuildOrderByColumns(@NonNull Context context,
                              @NonNull Locale locale,
                              @NonNull ReorderHelper reorderHelper);

    /**
     * Find a {@link TocEntry} based on the given id.
     *
     * @param id of {@link TocEntry} to find
     *
     * @return the {@link TocEntry}
     */
    @NonNull
    Optional<TocEntry> findById(@IntRange(from = 1) long id);

    /**
     * Find a {@link TocEntry} by using the <strong>name</strong> fields
     * of the given {@link TocEntry}.
     * The given {@link TocEntry} is <strong>not</strong> modified.
     *
     * @param context Current context
     * @param item    to find the id of
     * @param locale  Current Locale
     *
     * @return the {@link TocEntry}
     */
    @NonNull
    Optional<TocEntry> findByName(@NonNull Context context,
                                  @NonNull TocEntry item,
                                  @NonNull Locale locale);

    /**
     * Get a simple/total count of the items.
     *
     * @return count
     */
    int count();

    /**
     * Count the books for the given {@link TocEntry}.
     *
     * @param tocEntry to count the books of
     *
     * @return the number of books
     */
    int countBooks(@NonNull TocEntry tocEntry);

    /**
     * Find a {@link TocEntry} by using the <strong>name</strong> fields.
     * If found, updates <strong>ONLY</strong> the id with the one found in the database.
     * <p>
     * If the item has child items, then implementations must propagate the call.
     *
     * @param context Current context
     * @param item    to update
     * @param locale  Current Locale
     */
    void fixId(@NonNull Context context,
               @NonNull TocEntry item,
               @NonNull Locale locale);

    /**
     * Refresh the passed {@link TocEntry} from the database, if present.
     * Used to ensure that the current record matches the content of the database
     * should some other task have changed the {@link TocEntry}.
     * <p>
     * Will <strong>NOT</strong> insert a new {@link TocEntry} if not found;
     * instead the id of the item will be set to {@code 0}, i.e. 'new'.
     *
     * @param context Current context
     * @param item    to refresh
     * @param locale  Current Locale
     */
    void refresh(@NonNull Context context,
                 @NonNull TocEntry item,
                 @NonNull Locale locale);

    /**
     * Insert a new {@link TocEntry}.
     *
     * @param context Current context
     * @param item    to insert. Will be updated with the id
     * @param locale  The Locale of the item
     *
     * @return the row id of the newly inserted item
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1)
    long insert(@NonNull Context context,
                @NonNull TocEntry item,
                @NonNull Locale locale)
            throws DaoWriteException;

    /**
     * Update the given {@link TocEntry}.
     *
     * @param context Current context
     * @param item    to update
     * @param locale  The Locale of the item
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Context context,
                @NonNull TocEntry item,
                @NonNull Locale locale)
            throws DaoWriteException;

    /**
     * Delete the given {@link TocEntry}.
     *
     * @param context Current context
     * @param item    to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Context context,
                   @NonNull TocEntry item);
}
