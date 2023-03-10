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
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.core.database.TypedCursor;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

public interface BookDao {


    /**
     * Update the 'last updated' of the given book.
     * If successful, the book itself will also be updated with
     * the current date-time (which will be very slightly 'later' then what we store).
     *
     * @param book to update
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean touch(@NonNull Book book);

    @SuppressWarnings("UnusedReturnValue")
    boolean touch(@IntRange(from = 1) long bookId);

    /**
     * Create a new Book using the details provided.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set. May contain extra data.
     *                The id will be updated.
     * @param flags   See {@link BookFlag} for flag definitions
     *
     * @return the row id of the newly inserted row
     *
     * @throws StorageException  The covers directory is not available
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1, to = Integer.MAX_VALUE)
    long insert(@NonNull Context context,
                @NonNull Book book,
                @NonNull Set<BookFlag> flags)
            throws StorageException, DaoWriteException;

    @IntRange(from = 1, to = Integer.MAX_VALUE)
    default long insert(@NonNull final Context context,
                        @NonNull final Book /* in/out */ book)
            throws StorageException, DaoWriteException {
        return insert(context, book, Set.of());
    }

    /**
     * Update the given {@link Book}.
     * This will update <strong>ONLY</strong> the fields present in the passed in Book.
     * Non-present fields will not be touched. i.e. this is a delta operation.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set.
     *                May contain extra data which will be ignored.
     * @param flags   See {@link BookFlag} for flag definitions
     *
     * @throws StorageException  The covers directory is not available
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Context context,
                @NonNull Book book,
                @NonNull Set<BookFlag> flags)
            throws StorageException, DaoWriteException;

    default void update(@NonNull final Context context,
                        @NonNull final Book book)
            throws StorageException, DaoWriteException {
        update(context, book, Set.of());
    }

    /**
     * Delete the given book (and its covers).
     *
     * @param book to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Book book);

    boolean delete(@NonNull BookLight bookLight);

    /**
     * Delete the given book (and its covers).
     *
     * @param id of the book.
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@IntRange(from = 1) long id);

    /**
     * Create the link between {@link Book} and {@link Author}.
     * {@link DBDefinitions#TBL_BOOK_AUTHOR}
     * <p>
     * The list is pruned before storage.
     * New authors are added to the Author table, existing ones are NOT updated.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context   Current context
     * @param bookId    of the book
     * @param doUpdates set to {@code true} to force each Author to be updated.
     *                  <strong>ONLY</strong> set this when actually needed.
     *                  Do not set this during for example an import.
     * @param list      the list of authors
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertAuthors(@NonNull Context context,
                       @IntRange(from = 1) long bookId,
                       boolean doUpdates,
                       @NonNull Collection<Author> list,
                       boolean lookupLocale,
                       @NonNull Locale bookLocale)
            throws DaoWriteException;

    /**
     * Create the link between {@link Book} and {@link Series}.
     * {@link DBDefinitions#TBL_BOOK_SERIES}
     * <p>
     * The list is pruned before storage.
     * New series are added to the Series table, existing ones are NOT updated.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context      Current context
     * @param bookId       of the book
     * @param doUpdates    set to {@code true} to force each Author to be updated.
     *                     <strong>ONLY</strong> set this when actually needed.
     *                     Do not set this during for example an import.
     * @param list         the list of Series
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertSeries(@NonNull Context context,
                      @IntRange(from = 1) long bookId,
                      boolean doUpdates,
                      @NonNull Collection<Series> list,
                      boolean lookupLocale,
                      @NonNull Locale bookLocale)
            throws DaoWriteException;

    /**
     * Create the link between {@link Book} and {@link Publisher}.
     * {@link DBDefinitions#TBL_BOOK_PUBLISHER}
     * <p>
     * The list is pruned before storage.
     * New Publishers are added to the Publisher table, existing ones are NOT updated.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context      Current context
     * @param bookId       of the book
     * @param doUpdates    set to {@code true} to force each Author to be updated.
     *                     <strong>ONLY</strong> set this when actually needed.
     *                     Do not set this during for example an import.
     * @param list         the list of Publishers
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertPublishers(@NonNull Context context,
                          @IntRange(from = 1) long bookId,
                          boolean doUpdates,
                          @NonNull Collection<Publisher> list,
                          boolean lookupLocale,
                          @NonNull Locale bookLocale)
            throws DaoWriteException;

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
     * @param context      Current context
     * @param bookId       of the book
     * @param list         the list of {@link TocEntry}
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertOrUpdateToc(@NonNull Context context,
                           @IntRange(from = 1) long bookId,
                           @NonNull Collection<TocEntry> list,
                           boolean lookupLocale,
                           @NonNull Locale bookLocale)
            throws DaoWriteException;

    /**
     * Update the 'read' status and the 'read_end' date of the book.
     * This method should only be called from places where only the book id is available.
     * If the full Book is available, use {@link #setRead(Book, boolean)} instead.
     *
     * @param id   id of the book to update
     * @param read the status to set
     *
     * @return {@code true} for success.
     */
    boolean setRead(@IntRange(from = 1) long id,
                    boolean read);

    /**
     * Update the 'read' status and the 'read_end' date of the book.
     * <p>
     * If successful, the book object will have been updated with the new status.
     *
     * @param book   to update
     * @param read the status to set
     *
     * @return {@code true} for success.
     */
    boolean setRead(@NonNull Book book,
                    boolean read);

    /**
     * Count all books.
     *
     * @return number of books
     */
    long count();

    /**
     * Return a Cursor with the Book for the given {@link Book} id.
     *
     * @param id to retrieve
     *
     * @return A Book Cursor with 0..1 row
     */
    @NonNull
    TypedCursor fetchById(@IntRange(from = 1) long id);

    /**
     * Return an Cursor with all Books for the given external book ID.
     * <strong>Note:</strong> MAY RETURN MORE THAN ONE BOOK
     *
     * @param key        to use
     * @param externalId to retrieve
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    TypedCursor fetchByKey(@NonNull String key,
                           @NonNull String externalId);

    /**
     * Return an Cursor with all Books for the given list of {@link Book} ID's.
     *
     * @param idList List of book ID's to retrieve; should not be empty!
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     *         Only books with {@link DBKey#AUTO_UPDATE} set will be returned.
     *
     * @throws IllegalArgumentException if the list is empty
     */
    @NonNull
    TypedCursor fetchForAutoUpdate(@NonNull List<Long> idList);

    /**
     * Return an Cursor with all Books for the given list of ISBN numbers.
     *
     * @param isbnList list of ISBN numbers; should not be empty!
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     *
     * @throws IllegalArgumentException if the list is empty
     */
    @NonNull
    TypedCursor fetchByIsbn(@NonNull List<String> isbnList);


    /**
     * Return an Cursor with all Books where the {@link Book} id > the given id.
     * Pass in {@code 0} for all books.
     *
     * @param id the lowest book id to start from.
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     *         Only books with {@link DBKey#AUTO_UPDATE} set will be returned.
     */
    @NonNull
    TypedCursor fetchForAutoUpdateFromIdOnwards(long id);


    /**
     * Can be called before {@link #fetchBooksForExport(LocalDateTime)} to count
     * the number of books before starting the actual export.
     *
     * @param sinceDateTime (optional) select all books modified or added since that
     *                      date/time (UTC based). Set to {@code null} for *all* books.
     *
     * @return number of books that would be exported
     */
    int countBooksForExport(@Nullable LocalDateTime sinceDateTime);

    /**
     * Return an Cursor with all Books, or with all updated Books since the given date/time.
     *
     * @param sinceDateTime (optional) select all books modified or added since that
     *                      date/time (UTC based). Set to {@code null} for *all* books.
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    TypedCursor fetchBooksForExport(@Nullable LocalDateTime sinceDateTime);

    /**
     * Same as {@link #fetchBooksForExport(LocalDateTime)} but for a specific Calibre library.
     *
     * @param libraryId     row id for the physical library
     * @param sinceDateTime (optional) select all books modified or added since that
     *                      date/time (UTC based). Set to {@code null} for *all* books.
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    TypedCursor fetchBooksForExportToCalibre(long libraryId,
                                             @Nullable LocalDateTime sinceDateTime);

    /**
     * Same as {@link #fetchBooksForExport(LocalDateTime)} but specific to the stripinfo.be website.
     *
     * @param sinceDateTime (optional) select all books modified or added since that
     *                      date/time (UTC based). Set to {@code null} for *all* books.
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    TypedCursor fetchBooksForExportToStripInfo(@Nullable LocalDateTime sinceDateTime);

    /**
     * Fetch all book UUID, and return them as a List.
     *
     * @return a list of all book UUID in the database.
     */
    @NonNull
    ArrayList<String> getBookUuidList();

    /**
     * Check that a book with the passed UUID exists and return the id of the book, or zero.
     *
     * @param uuid UUID of the book
     *
     * @return id of the book, or 0 'new' if not found
     */
    @IntRange(from = 0)
    long getBookIdByUuid(@NonNull String uuid);

    /**
     * Get a list of book id/title's (most often just the one) for the given ISBN.
     *
     * @param isbn to search for; can be generic/non-valid
     *
     * @return list with book id/title
     */
    @NonNull
    ArrayList<Pair<Long, String>> getBookIdAndTitleByIsbn(@NonNull ISBN isbn);

    /**
     * Check that a book with the passed id exists.
     *
     * @param id of the book
     *
     * @return {@code true} if exists
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean bookExistsById(@IntRange(from = 1) long id);

    /**
     * Check that a book with the passed isbn exists.
     *
     * @param isbnStr of the book
     *
     * @return {@code true} if exists
     */
    boolean bookExistsByIsbn(@NonNull String isbnStr);

    /**
     * Get a unique list of all currencies for the specified domain (from the Books table).
     *
     * @param key for which to collect the used currency codes
     *
     * @return The list; values are always in uppercase.
     */
    @NonNull
    ArrayList<String> getCurrencyCodes(@NonNull String key);


    /**
     * Get the date the given book was last updated.
     *
     * @param id of the book
     *
     * @return date
     */
    @Nullable
    LocalDateTime getLastUpdateDate(@IntRange(from = 1) long id);

    /**
     * Flags used during {@link #insert(Context, Book, Set)}
     * and {@link #update(Context, Book, Set)} operations.
     */
    enum BookFlag {
        /**
         * If set, relax some rules which would affect performance otherwise.
         * This is/should only be used during imports.
         */
        RunInBatch,
        /**
         * If set, and the book bundle has an id !=0, force the id to be used.
         * This is/should only be used during imports of new books
         * i.e. during import of a backup archive/csv
         */
        UseIdIfPresent,
        /**
         * If set, the {@link DBKey#DATE_LAST_UPDATED__UTC} field from the bundle should be trusted.
         * If this flag is not set, the current date/time will be used.
         */
        UseUpdateDateIfPresent
    }
}
