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
import androidx.annotation.WorkerThread;
import androidx.core.util.Pair;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.bookreadstatus.ReadingProgress;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.TypedCursor;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookDaoHelper;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLite;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

public interface BookDao {

    /**
     * Update the 'last updated' of the given book.
     * If successful, the book itself will also be updated with
     * the current date-time (which will be very slightly 'later' than when we stored).
     *
     * @param book to update
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean touch(@NonNull Book book);

    /**
     * Update the 'last updated' of the given book.
     * If successful, the book itself will also be updated with
     * the current date-time (which will be very slightly 'later' than when we stored).
     *
     * @param bookId to update
     *
     * @return {@code true} on success
     */
    boolean touch(long bookId);

    /**
     * Create a new {@link Book}.
     * <p>
     * ENHANCE: pass in {@link DataReader.Updates} option to propagate to Authors
     *  and eventually to other linked objects.
     *
     * @param context       Current context
     * @param userLocale    to use
     * @param bookDaoHelper to use
     * @param book          object to insert. Will be updated with the id.
     * @param flags         See {@link ImportFlag} for flag definitions
     *
     * @return the row id of the newly inserted row
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1)
    long insert(@NonNull Context context,
                @NonNull Locale userLocale,
                @NonNull BookDaoHelper bookDaoHelper,
                @NonNull Book book,
                @NonNull Set<ImportFlag> flags)
            throws DaoWriteException;

    /**
     * Update the given {@link Book}.
     * <p>
     * ENHANCE: pass in {@link DataReader.Updates} option to propagate to Authors
     *  and eventually to other linked objects.
     * <p>
     * This will update <strong>ONLY</strong> the fields present in the given Book.
     * Non-present fields will not be touched. i.e. this is a delta operation.
     * <p>
     * TRIGGERS:
     * - If the Code of a {@link Book} is changed, reset external ID's and sync dates.
     *
     * @param context       Current context
     * @param userLocale    to use
     * @param bookDaoHelper to use
     * @param book          A collection with the columns to be set.
     *                      May contain extra data which will be ignored.
     * @param flags         See {@link ImportFlag} for flag definitions
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Context context,
                @NonNull Locale userLocale,
                @NonNull BookDaoHelper bookDaoHelper,
                @NonNull Book book,
                @NonNull Set<ImportFlag> flags)
            throws DaoWriteException;

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
     * @param bookLite to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull BookLite bookLite);

    /**
     * Delete the given {@link Book} (and its covers).
     *
     * @param id of the book to delete.
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
    int deleteByUuid(@NonNull Collection<String> uuids);

    /**
     * Bulk operation to set the Bookshelves.
     * <p>
     * Assign the given list of Bookshelves to the given list of Books.
     *
     * @param context     Current context
     * @param bookIds     to update
     * @param bookshelves to add/move the books to
     *
     * @return {@code true} for success.
     */
    boolean setBookshelves(@NonNull Context context,
                           @NonNull Collection<Long> bookIds,
                           @NonNull Collection<Bookshelf> bookshelves);

    /**
     * Bulk operation to set the {@link DBKey#LOCATION} field.
     * <p>
     * Assign the given Location to the given list of Books.
     *
     * @param bookIds  to update
     * @param location to set
     *
     * @return {@code true} for success.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean setLocation(@NonNull Collection<Long> bookIds,
                        @NonNull String location);

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
     * @param book            to update
     * @param readingProgress the progress data to set
     *
     * @return {@code true} for success.
     */
    boolean setReadingProgress(@NonNull Book book,
                               @NonNull ReadingProgress readingProgress);

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
     * Return a Cursor with all Books for the given list of {@link Book} ID's.
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
     * Return a Cursor with all Books for the given list of Codes.
     *
     * @param list list of Code; should not be empty!
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     *
     * @throws IllegalArgumentException if the list is empty
     */
    @NonNull
    TypedCursor fetch(@NonNull List<ProductCode> list);

    /**
     * Return a Cursor with all Books where the {@link Book} id > the given id.
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
     * Return a Cursor with all Books, or with all updated Books since the given date/time.
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
     * Get a list of book id/title's (most often just the one) for the given Code.
     *
     * @param productCode to search for
     *
     * @return list with book id/title
     */
    @NonNull
    List<Pair<Long, String>> getBookIdAndTitle(@NonNull ProductCode productCode);

    /**
     * Check that a book with the passed id exists.
     *
     * @param id of the book
     *
     * @return {@code true} if it exists
     */
    boolean bookExists(@IntRange(from = 1) long id);

    /**
     * Check that a book with the passed code exists.
     *
     * @param productCode of the book
     *
     * @return {@code true} if it exists
     */
    boolean bookExists(@NonNull ProductCode productCode);

    /**
     * Get a unique list of all currencies for the specified domain (from the Books table).
     *
     * @param key for which to collect the used currency codes
     *
     * @return The list; values are always in uppercase.
     */
    @NonNull
    List<String> getCurrencies(@NonNull String key);


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
     * Flags used during {@link BookDao#insert} and {@link BookDao#update}.
     */
    enum ImportFlag {
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
         * If set, the {@link DBKey#DATE_LAST_UPDATED__UTC} field from the bundle
         * should be trusted.
         * If this flag is not set, the current date/time will be used.
         */
        UseUpdateDateIfPresent
    }
}
