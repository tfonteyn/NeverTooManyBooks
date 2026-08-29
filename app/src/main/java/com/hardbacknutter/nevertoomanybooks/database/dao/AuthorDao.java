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
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.Positional;
import com.hardbacknutter.nevertoomanybooks.database.Purgeable;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

@SuppressWarnings("UnusedReturnValue")
public interface AuthorDao
        extends Purgeable, Positional {

    /**
     * Get a unique list of {@link Author} names in the specified format.
     *
     * @param key type of name wanted, one of
     *            {@link DBKey.AUTHOR#FAMILY_NAME},
     *            {@link DBKey.AUTHOR#GIVEN_NAMES},
     *            {@link DBKey.AUTHOR#FORMATTED_FULL_NAME},
     *            {@link DBKey.AUTHOR#FORMATTED_FULL_NAME_GIVEN_FIRST}
     *
     * @return list of all author names.
     */
    @NonNull
    List<String> getNames(@NonNull String key);

    /**
     * Return all the {@link AuthorWork} for the given {@link Author}.
     *
     * @param author         to retrieve
     * @param bookshelfId    limit the list to books on this shelf (pass -1 for all shelves)
     * @param withTocEntries add the toc entries
     * @param withBooks      add books without TOC as well; i.e. the toc of a book without a toc,
     *                       is the book title itself. (makes sense?)
     * @param orderBy        {@code null} for the default, or one of {@link WorksOrderBy}
     *
     * @return List of {@link AuthorWork} for this {@link Author}
     */
    @WorkerThread
    @NonNull
    List<AuthorWork> getAuthorWorks(@NonNull Author author,
                                    long bookshelfId,
                                    boolean withTocEntries,
                                    boolean withBooks,
                                    @WorksOrderBy @Nullable String orderBy);

    /**
     * Update the 'complete' status for the given {@link Author}.
     * <p>
     * If successful, the author object will have been updated with the new status.
     *
     * @param author   to update
     * @param complete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    boolean setComplete(@NonNull Author author,
                        boolean complete);

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
                              @NonNull final Collection<Author> list,
                              @NonNull final Function<Author, Locale> localeSupplier) {
        return pruneList(context, list, localeSupplier,
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
     * @param localeSupplier deferred supplier for a {@link Locale}.
     * @param idFixer        how to call {@link #fixId(Context, Author, Locale)}
     *
     * @return {@code true} if the list was modified.
     */
    @VisibleForTesting
    boolean pruneList(@NonNull Context context,
                      @NonNull Collection<Author> list,
                      @NonNull Function<Author, Locale> localeSupplier,
                      @NonNull BiConsumer<Author, Locale> idFixer);

    /**
     * Rebuild the OB columns for the table(s) of this dao.
     *
     * @param locale Current Locale
     *
     * @return the number of rows actually updated
     */
    @WorkerThread
    int rebuildOrderByColumns(@NonNull Locale locale);

    /**
     * Count the books for the given {@link Author}.
     *
     * @param author to count the books of
     *
     * @return the number of books
     */
    int countBooks(@NonNull Author author);

    /**
     * Get a list of book ID's for the given {@link Author}.
     *
     * @param authorId id of the Author
     *
     * @return list with book ID's linked to this Author
     */
    @NonNull
    List<Long> getBookIds(long authorId);

    /**
     * Fetch all the author picture UUIDs, and return them as a List.
     *
     * @return a list of all author UUID in the database.
     */
    @NonNull
    List<String> getImageUuidList();

    /**
     * Get a list of the {@link Author} for a book.
     *
     * @param bookId of the book
     *
     * @return list
     */
    @NonNull
    List<Author> getByBookId(@IntRange(from = 1) long bookId);

    /**
     * Insert or update a list of {@link Author}'s linked to a single {@link Book}.
     * <p>
     * The list is pruned before storage.
     * New {@link Author}'s are added to the {@link Author} table, existing ones are NOT updated
     * unless explicitly allowed by the {@code doUpdates} parameter.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context        Current context
     * @param bookId         of the book
     * @param doUpdates      set to {@code true} to force each {@link Author} to be updated.
     *                       <strong>ONLY</strong> set this when actually needed.
     * @param list           the list of {@link Author}'s
     * @param localeSupplier a supplier to get the Locale; called for each item in the list
     *
     * @throws DaoWriteException on failure
     */
    void insertOrUpdate(@NonNull Context context,
                        @IntRange(from = 1) long bookId,
                        boolean doUpdates,
                        @NonNull Collection<Author> list,
                        @NonNull Function<Author, Locale> localeSupplier)
            throws DaoWriteException;

    /**
     * Moves all books from the 'source' {@link Author}, to the 'target' {@link Author}.
     * The (now unused) 'source' {@link Author} is deleted.
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
                  @NonNull Author source,
                  @NonNull Author target)
            throws DaoWriteException;

    /**
     * Find a {@link Author} based on the given id.
     *
     * @param id of {@link Author} to find
     *
     * @return the {@link Author}
     */
    @NonNull
    Optional<Author> findById(@IntRange(from = 1) long id);

    /**
     * Find a {@link Author} by using the <strong>name</strong> fields of the given {@link Author}.
     * The given {@link Author} is <strong>not</strong> modified.
     *
     * @param context Current context
     * @param item    to find the id of
     * @param locale  Current Locale
     *
     * @return the {@link Author}
     */
    @NonNull
    Optional<Author> findByName(@NonNull Context context,
                                @NonNull Author item,
                                @NonNull Locale locale);

    /**
     * Get a simple/total count of the items.
     *
     * @return count
     */
    int count();

    /**
     * Find a {@link Author} by using the <strong>name</strong> fields.
     * If found, updates <strong>ONLY</strong> the id with the one found in the database.
     * <p>
     * If the item has child items, then implementations must propagate the call.
     * <p>
     * URGENT: fixId should be able to merge the given item with the database data
     *
     * @param context Current context
     * @param item    to update
     * @param locale  Current Locale
     */
    void fixId(@NonNull Context context,
               @NonNull Author item,
               @NonNull Locale locale);

    /**
     * Refresh the passed {@link Author} from the database, if present.
     * Used to ensure that the current record matches the content of the database
     * should some other task have changed the {@link Author}.
     * <p>
     * Will <strong>NOT</strong> insert a new {@link Author} if not found;
     * instead the id of the item will be set to {@code 0}, i.e. 'new'.
     *
     * @param context Current context
     * @param item    to refresh
     * @param locale  Current Locale
     */
    void refresh(@NonNull Context context,
                 @NonNull Author item,
                 @NonNull Locale locale);

    /**
     * Insert a new {@link Author}.
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
                @NonNull Author item,
                @NonNull Locale locale)
            throws DaoWriteException;

    /**
     * Update the given {@link Author}.
     *
     * @param context Current context
     * @param item    to update
     * @param locale  The Locale of the item
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Context context,
                @NonNull Author item,
                @NonNull Locale locale)
            throws DaoWriteException;

    /**
     * Delete the given {@link Author}.
     *
     * @param context Current context
     * @param author  to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Context context,
                   @NonNull Author author);

    @StringDef({
            DBKey.TITLE_OB,
            DBKey.FIRST_PUBLICATION_DATE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface WorksOrderBy {

    }
}
