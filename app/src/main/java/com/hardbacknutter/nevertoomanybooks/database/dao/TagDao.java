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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.cleaning.Purger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.settings.tags.TagMapperTask;

public interface TagDao {

    /**
     * Find the {@link Tag} for the given id.
     *
     * @param id of the {@link Tag}
     *
     * @return {@link Tag}
     */
    @NonNull
    Optional<Tag> findById(@IntRange(from = 1) long id);

    /**
     * Find a {@link Tag} by using the <strong>name</strong> fields of the given {@link Tag}.
     * The given {@link Tag} is <strong>not</strong> modified.
     *
     * @param tag to find the id of
     *
     * @return the {@link Tag}
     */
    @NonNull
    Optional<Tag> findByName(@NonNull Tag tag);

    /**
     * Find a {@link Tag} by using the <strong>name</strong> field.
     * If found, updates <strong>ONLY</strong> the id with the one found in the database.
     *
     * @param tag to update
     */
    void fixId(@NonNull Tag tag);

    /**
     * Refresh the passed {@link Tag} from the database, if present.
     * Used to ensure that the current record matches the content of the database
     * should some other task have changed the {@link Tag}.
     * <p>
     * Will <strong>NOT</strong> insert a new {@link Tag} if not found;
     * instead the id of the item will be set to {@code 0}, i.e. 'new'.
     *
     * @param tag to refresh
     */
    void refresh(@NonNull Tag tag);

    /**
     * Insert a new {@link Tag}.
     *
     * @param tag to insert. Will be updated with the id
     *
     * @return the row id of the newly inserted item
     *
     * @throws DaoWriteException on failure
     */

    @IntRange(from = 1)
    long insert(@NonNull Tag tag)
            throws DaoWriteException;

    /**
     * Update the given {@link Tag}.
     *
     * @param tag to update
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Tag tag)
            throws DaoWriteException;

    /**
     * Delete the given {@link Tag}.
     *
     * @param tag to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Tag tag);

    /**
     * Get the list of all tags, ordered by name.
     *
     * @return list
     */
    @NonNull
    List<Tag> getAll();

    /**
     * Count the books for the given {@link Tag}.
     *
     * @param tag to count the books of
     *
     * @return the number of books
     */
    int countBooks(@NonNull Tag tag);

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
                              @NonNull final Collection<Tag> list,
                              @NonNull final Function<Tag, Locale> localeSupplier) {
        return pruneList(context, list, localeSupplier,
                // Don't look up the locale a 2nd time.
                         (current, locale) -> fixId(current));
    }

    /**
     * Remove duplicates. We keep the first occurrence.
     * <p>
     * <strong>Tests only.</strong>
     * Allows overriding the normalisation flag and use a custom (nop) id-fixeer.
     *
     * @param context        Current context
     * @param list           List to clean up
     * @param localeSupplier deferred supplier for a {@link Locale}.
     * @param idFixer        how to call {@link #fixId(Tag)}
     *
     * @return {@code true} if the list was modified.
     */
    @VisibleForTesting
    boolean pruneList(@NonNull Context context,
                      @NonNull Collection<Tag> list,
                      @NonNull Function<Tag, Locale> localeSupplier,
                      @NonNull BiConsumer<Tag, Locale> idFixer);

    /**
     * Delete orphaned records.
     * <p>
     * Note this is <strong>not</strong> called from {@link Purger#purge()}
     * as we will always seek the users permission to purge the dao/table.
     *
     * @return the number of rows deleted,
     *         or {@code -1} if an error occurred
     */
    @WorkerThread
    @IntRange(from = 0)
    int purge();

    /**
     * Insert or update a list of {@link Tag}s linked to a single {@link Book}.
     * <p>
     * The list is pruned before storage.
     * New {@link Tag}s are added to the {@link Tag} table,
     * existing ones are NOT updated (nothing to do).
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context        Current context
     * @param bookId         of the book
     * @param list           the list of {@link Tag}s
     * @param localeSupplier a supplier to get the Locale; called for each item in the list
     *
     * @throws DaoWriteException on failure
     */
    void insertOrUpdate(@NonNull Context context,
                        @IntRange(from = 1) long bookId,
                        @NonNull Collection<Tag> list,
                        @NonNull Function<Tag, Locale> localeSupplier)
            throws DaoWriteException;

    /**
     * Get a list of the {@link Tag}s for a book.
     *
     * @param bookId of the book
     *
     * @return list
     */
    @NonNull
    List<Tag> getByBookId(@IntRange(from = 1) long bookId);

    /**
     * Get a list of book ID's for the given {@link Tag}.
     *
     * @param tagId id of the Tag
     *
     * @return list with book ID's linked to this Tag
     */
    @NonNull
    List<Long> getBookIds(@IntRange(from = 1) long tagId);

    /**
     * Find all books with tags, and apply the currently configured
     * tag-mapping rules.
     *
     * @param context Current context
     * @param locale  Current Locale
     * @param options a set of {@link TagMapperTask.Options}
     *
     * @return options + number of books modified
     *
     * @throws DaoWriteException on any failure
     */
    @NonNull
    Map<TagMapperTask.Options, Integer> applyTagMappings(
            @NonNull Context context,
            @NonNull Locale locale,
            @NonNull Set<TagMapperTask.Options> options)
            throws DaoWriteException;

    /**
     * Bulk import the given list of {@link Tag}s.
     * Entries already present are simply skipped.
     *
     * @param list to import.
     *
     * @return the number of entries actually inserted; can be {@code 0}.
     */
    @WorkerThread
    int importRecords(@NonNull Collection<Tag> list);

    /**
     * Moves all books from the 'source' {@link Tag}, to the 'target' {@link Tag}.
     * The (now unused) 'source' {@link Tag} is deleted.
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
                  @NonNull Tag source,
                  @NonNull Tag target)
            throws DaoWriteException;
}
