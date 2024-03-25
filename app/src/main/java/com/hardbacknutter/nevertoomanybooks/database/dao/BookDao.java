/*
 * @Copyright 2018-2024 HardBackNutter
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.bookdetails.ReadProgress;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.TypedCursor;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;

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

    /**
     * Create a new {@link Book}.
     *
     * @param context Current context
     * @param book    object to insert. Will be updated with the id.
     * @param flags   See {@link BookFlag} for flag definitions
     *
     * @return the row id of the newly inserted row
     *
     * @throws StorageException  The covers directory is not available
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1)
    long insert(@NonNull Context context,
                @NonNull Book book,
                @NonNull Set<BookFlag> flags)
            throws StorageException, DaoWriteException;

    @IntRange(from = 1)
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
     * Delete the given {@link Book} (and its covers).
     *
     * @param book to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Book book);

    /**
     * Delete the given {@link Book} (and its covers).
     *
     * @param bookLight to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull BookLight bookLight);

    /**
     * Delete the given {@link Book} (and its covers).
     *
     * @param id of the book.
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@IntRange(from = 1) long id);

    /**
     * Delete the given list of {@link Book}s (and their covers).
     *
     * @param uuids list of book UUIDs
     *
     * @return the number of rows deleted
     */
    int deleteByUuid(@NonNull List<String> uuids);

    /**
     * Update the 'read' status of a book.
     * The 'read end' date is updated as needed.
     * Any 'progress' data is erased.
     * <p>
     * If successful, the book object will have been updated with the new status.
     *
     * @param book to update
     * @param read the status to set
     *
     * @return {@code true} for success.
     */
    boolean setRead(@NonNull Book book,
                    boolean read);

    /**
     * Update the 'read-progress' status of a book.
     * The 'read end' date is updated as needed.
     * <p>
     * If successful, the book object will have been updated with the new status.
     *
     * @param book         to update
     * @param readProgress the progress data to set
     *
     * @return {@code true} for success.
     */
    boolean setReadProgress(@NonNull Book book,
                            @NonNull ReadProgress readProgress);

    /**
     * Count all books.
     *
     * @return number of books
     */
    int count();

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
    TypedCursor fetchByIsbn(@NonNull List<ISBN> isbnList);


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
    TypedCursor fetchForAutoUpdateFromIdOnwards(@IntRange(from = 1) long id);


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
    TypedCursor fetchBooksForExportToCalibre(@IntRange(from = 1) long libraryId,
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
    List<String> getBookUuidList();

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
    List<Pair<Long, String>> getBookIdAndTitleByIsbn(@NonNull ISBN isbn);

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
    List<String> getCurrencyCodes(@NonNull String key);


    /**
     * Get the date the given book was last updated.
     *
     * @param id of the book
     *
     * @return date
     */
    @NonNull
    Optional<LocalDateTime> getLastUpdateDate(@IntRange(from = 1) long id);

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
