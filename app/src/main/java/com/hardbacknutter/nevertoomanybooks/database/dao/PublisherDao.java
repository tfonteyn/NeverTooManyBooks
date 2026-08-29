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

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.Positional;
import com.hardbacknutter.nevertoomanybooks.database.Purgeable;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

@SuppressWarnings("UnusedReturnValue")
public interface PublisherDao
        extends Purgeable, Positional {

    /**
     * Get a unique list of all publisher names.
     *
     * @return The list
     */
    @NonNull
    List<String> getNames();

    /**
     * Remove duplicates. We keep the first occurrence.
     *
     * @param context        Current context
     * @param list           List to clean up
     * @param localeSupplier deferred supplier for a {@link Locale}.
     *
     * @return {@code true} if the list was modified.
     */
    default boolean pruneList(@NonNull final Context context,
                              @NonNull final Collection<Publisher> list,
                              @NonNull final Function<Publisher, Locale> localeSupplier) {
        final boolean normalise = ReorderHelper.isTryReorderingDuringDeduplication();
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
                              @NonNull final Collection<Publisher> list,
                              final boolean normalise,
                              @NonNull final Function<Publisher, Locale> localeSupplier) {
        return pruneList(context, list, normalise, localeSupplier,
                         (publisher, locale) -> fixId(context, publisher, locale));
    }

    /**
     * Remove duplicates. We keep the first occurrence.
     * <p>
     * <strong>Tests only.</strong>
     * Allows overriding the normalisation flag and use a custom (nop) id-fixeer.
     *
     * @param context        Current context
     * @param list           List to clean up
     * @param normalise      flag, whether to normalise the name
     * @param localeSupplier deferred supplier for a {@link Locale}.
     * @param idFixer        how to call {@link #fixId(Context, Publisher, Locale)}
     *
     * @return {@code true} if the list was modified.
     */
    @VisibleForTesting
    boolean pruneList(@NonNull Context context,
                      @NonNull Collection<Publisher> list,
                      boolean normalise,
                      @NonNull Function<Publisher, Locale> localeSupplier,
                      @NonNull BiConsumer<Publisher, Locale> idFixer);

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
     * Count the books for the given {@link Publisher}.
     *
     * @param publisher to count the books of
     *
     * @return the number of books
     */
    int countBooks(@NonNull Publisher publisher);

    /**
     * Get a list of book ID's for the given {@link Publisher}.
     *
     * @param publisherId id of the Publisher
     *
     * @return list with book ID's linked to this Publisher
     */
    @NonNull
    List<Long> getBookIds(long publisherId);

    /**
     * Get a list of the {@link Publisher} for a book.
     *
     * @param bookId of the book
     *
     * @return list
     */
    @NonNull
    List<Publisher> getByBookId(@IntRange(from = 1) long bookId);

    /**
     * Insert or update a list of {@link Publisher}'s linked to a single {@link Book}.
     * <p>
     * The list is pruned before storage.
     * New {@link Publisher}'s are added to the {@link Publisher} table,
     * existing ones are NOT updated unless explicitly allowed by the {@code doUpdates} parameter.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context        Current context
     * @param bookId         of the book
     * @param doUpdates      set to {@code true} to force each {@link Publisher} to be updated.
     *                       <strong>ONLY</strong> set this when actually needed.
     *                       Do not set this during for example an import.
     * @param list           the list of {@link Publisher}'s
     * @param localeSupplier a supplier to get the Locale; called for each item in the list
     *
     * @throws DaoWriteException on failure
     */
    void insertOrUpdate(@NonNull Context context,
                        @IntRange(from = 1) long bookId,
                        boolean doUpdates,
                        @NonNull Collection<Publisher> list,
                        @NonNull Function<Publisher, Locale> localeSupplier)
            throws DaoWriteException;

    /**
     * Moves all books from the 'source' {@link Publisher}, to the 'target' {@link Publisher}.
     * The (now unused) 'source' {@link Publisher} is deleted.
     *
     * @param context Current context
     * @param source  from where to move
     * @param target  to move to
     *
     * @return amount of books moved
     *
     * @throws DaoWriteException on failure
     */
    int moveBooks(@NonNull Context context,
                  @NonNull Publisher source,
                  @NonNull Publisher target)
            throws DaoWriteException;

    /**
     * Find a {@link Publisher} based on the given id.
     *
     * @param id of {@link Publisher} to find
     *
     * @return the {@link Publisher}
     */
    @NonNull
    Optional<Publisher> findById(@IntRange(from = 1) long id);

    /**
     * Find a {@link Publisher} by using the <strong>name</strong> fields
     * of the given {@link Publisher}.
     * The given {@link Publisher} is <strong>not</strong> modified.
     * <p>
     * Searches on both original and (potentially) reordered name.
     *
     * @param context Current context
     * @param item    to find the id of
     * @param locale  Current Locale
     *
     * @return the {@link Publisher}
     */
    @NonNull
    Optional<Publisher> findByName(@NonNull Context context,
                                   @NonNull Publisher item,
                                   @NonNull Locale locale);

    /**
     * Get a simple/total count of the items.
     *
     * @return count
     */
    int count();

    /**
     * Find a {@link Publisher} by using the <strong>name</strong> fields.
     * If found, updates <strong>ONLY</strong> the id with the one found in the database.
     * <p>
     * If the item has child items, then implementations must propagate the call.
     *
     * @param context Current context
     * @param item    to update
     * @param locale  Current Locale
     */
    void fixId(@NonNull Context context,
               @NonNull Publisher item,
               @NonNull Locale locale);

    /**
     * Refresh the passed {@link Publisher} from the database, if present.
     * Used to ensure that the current record matches the content of the database
     * should some other task have changed the {@link Publisher}.
     * <p>
     * Will <strong>NOT</strong> insert a new {@link Publisher} if not found;
     * instead the id of the item will be set to {@code 0}, i.e. 'new'.
     *
     * @param context Current context
     * @param item    to refresh
     * @param locale  Current Locale
     */
    void refresh(@NonNull Context context,
                 @NonNull Publisher item,
                 @NonNull Locale locale);

    /**
     * Insert a new {@link Publisher}.
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
                @NonNull Publisher item,
                @NonNull Locale locale)
            throws DaoWriteException;

    /**
     * Update the given {@link Publisher}.
     *
     * @param context Current context
     * @param item    to update
     * @param locale  The Locale of the item
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Context context,
                @NonNull Publisher item,
                @NonNull Locale locale)
            throws DaoWriteException;

    /**
     * Delete the given {@link Publisher}.
     *
     * @param context   Current context
     * @param publisher to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Context context,
                   @NonNull Publisher publisher);
}
