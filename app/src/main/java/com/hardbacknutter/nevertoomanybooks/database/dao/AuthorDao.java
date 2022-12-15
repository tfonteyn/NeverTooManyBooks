/*
 * @Copyright 2018-2022 HardBackNutter
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
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public interface AuthorDao {

    /**
     * Get the {@link Author} based on the given id.
     *
     * @param id of {@link Author} to find
     *
     * @return the {@link Author}, or {@code null} if not found
     */
    @Nullable
    Author getById(long id);

    /**
     * Find an {@link Author} by using the <strong>name</strong> fields
     * of the passed {@link Author}. The incoming object is not modified.
     * <p>
     * <strong>IMPORTANT:</strong> the query can return more than one row if the
     * given-name of the author is empty. e.g. "Asimov" and "Asimov"+"Isaac"
     * We only return the id of the  <strong>first row found</strong>.
     *
     * @param context      Current context
     * @param author       to find the id of
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    long find(@NonNull Context context,
              @NonNull Author author,
              boolean lookupLocale,
              @NonNull Locale bookLocale);

    /**
     * Get a unique list of {@link Author} names in the specified format.
     *
     * @param key type of name wanted, one of
     *            {@link DBKey#AUTHOR_FAMILY_NAME},
     *            {@link DBKey#AUTHOR_GIVEN_NAMES},
     *            {@link DBKey#AUTHOR_FORMATTED},
     *            {@link DBKey#AUTHOR_FORMATTED_GIVEN_FIRST}
     *
     * @return list of all author names.
     */
    @NonNull
    ArrayList<String> getNames(@NonNull String key);

    /**
     * Get a list of the authors for a book.
     *
     * @param bookId of the book
     *
     * @return list of authors
     */
    @NonNull
    ArrayList<Author> getAuthorsByBookId(@IntRange(from = 1) long bookId);

    /**
     * Get a list of book ID's for the given {@link Author}.
     *
     * @param authorId id of the author
     *
     * @return list with book ID's
     */
    @NonNull
    ArrayList<Long> getBookIds(long authorId);

    /**
     * Get a list of book ID's for the given {@link Author} and {@link Bookshelf}.
     *
     * @param authorId    id of the {@link Author}
     * @param bookshelfId id of the {@link Bookshelf}
     *
     * @return list with book ID's
     */
    @NonNull
    ArrayList<Long> getBookIds(long authorId,
                               long bookshelfId);

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
    @NonNull
    ArrayList<AuthorWork> getAuthorWorks(@NonNull Author author,
                                         long bookshelfId,
                                         boolean withTocEntries,
                                         boolean withBooks,
                                         @WorksOrderBy @Nullable String orderBy);

    /**
     * Get all Authors; mainly for the purpose of exports.
     *
     * @return Cursor over all Authors
     */
    @NonNull
    Cursor fetchAll();

    long count();

    /**
     * Count the books for the given {@link Author}.
     *
     * @param context    Current context
     * @param author     to retrieve
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of books
     */
    long countBooks(@NonNull Context context,
                    @NonNull Author author,
                    @NonNull Locale bookLocale);

    /**
     * Count the {@link TocEntry}'s for the given {@link Author}.
     *
     * @param context    Current context
     * @param author     to count the TocEntries of
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of {@link TocEntry} this {@link Author} has
     */
    long countTocEntries(@NonNull Context context,
                         @NonNull Author author,
                         @NonNull Locale bookLocale);

    /**
     * Update the 'complete' status of an {@link Author}.
     *
     * @param authorId   to update
     * @param isComplete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    boolean setComplete(long authorId,
                        boolean isComplete);

    /**
     * Passed a list of Author, remove duplicates.
     * Consolidates author/- and author/type.
     * <p>
     * ENHANCE: Add aliases table to allow further pruning
     * (e.g. Joe Haldeman == Joe W Haldeman).
     *
     * @param context      Current context
     * @param list         List to clean up
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @return {@code true} if the list was modified.
     */
    boolean pruneList(@NonNull Context context,
                      @NonNull Collection<Author> list,
                      boolean lookupLocale,
                      @NonNull Locale bookLocale);

    /**
     * Find an {@link Author} by using the <strong>name</strong> fields.
     * If found, update the id with the id as found in the database.
     *
     * @param context      Current context
     * @param author       to update
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     */
    void fixId(@NonNull Context context,
               @NonNull Author author,
               boolean lookupLocale,
               @NonNull Locale bookLocale);

    /**
     * Refresh the passed {@link Author} from the database, if present.
     * Used to ensure that the current record matches the current DB if some
     * other task may have changed the {@link Author}.
     * <p>
     * Will NOT insert a new {@link Author} if not found.
     *
     * @param context    Current context
     * @param author     to refresh
     * @param bookLocale Locale to use if the item has none set
     */
    void refresh(@NonNull Context context,
                 @NonNull Author author,
                 @NonNull Locale bookLocale);

    /**
     * Insert a new {@link Author}.
     *
     * @param context Current context
     * @param author  object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted Author
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1, to = Integer.MAX_VALUE)
    long insert(@NonNull Context context,
                @NonNull Author author)
            throws DaoWriteException;

    /**
     * Update an {@link Author}.
     *
     * @param context Current context
     * @param author  to update
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Context context,
                @NonNull Author author)
            throws DaoWriteException;

    /**
     * Delete the given {@link Author}.
     *
     * @param context Current context
     * @param author  to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull Context context,
                   @NonNull Author author);

    /**
     * Moves all books from the 'source' {@link Author}, to the 'target' {@link Author}.
     * The (now unused) 'source' {@link Author} is deleted.
     * <p>
     * Note that TOC entries also get updated with the new author id.
     *
     * @param context Current context
     * @param source  from where to move
     * @param target  to move to
     *
     * @throws DaoWriteException on failure
     */
    void moveBooks(@NonNull Context context,
                   @NonNull Author source,
                   @NonNull Author target)
            throws DaoWriteException;

    /**
     * Delete orphaned records.
     */
    void purge();

    /**
     * Check for books which do not have an {@link Author} at position 1.
     * For those that don't, read their list, and re-save them.
     *
     * @param context Current context
     *
     * @return the number of books processed
     */
    int repositionAuthor(@NonNull Context context);

    @StringDef({
            DBKey.TITLE_OB,
            DBKey.FIRST_PUBLICATION__DATE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface WorksOrderBy {

    }
}
