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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;

public abstract class BaseRecordReader
        implements RecordReader {

    private static final String TAG = "BaseRecordReader";

    @NonNull
    private final BookDao bookDao;
    @NonNull
    private final DateParser dateParser;
    @NonNull
    private final DataReader.Updates updateOption;

    protected ImportResults results;

    /**
     * Constructor.
     *
     * @param systemLocale to use for ISO date parsing
     * @param updateOption options
     */
    protected BaseRecordReader(@NonNull final Locale systemLocale,
                               @NonNull final DataReader.Updates updateOption) {
        this.updateOption = updateOption;
        this.bookDao = ServiceLocator.getInstance().getBookDao();

        this.dateParser = new ISODateParser(systemLocale);
    }

    @NonNull
    protected DataReader.Updates getUpdateOption() {
        return updateOption;
    }

    /**
     * Import a single book.
     * <p>
     * Try to in order of importance:
     * <ol>
     *     <li>If there is a UUID, either update an existing book by looking up the UUID,
     *         or insert a new book.</li>
     *     <li>If there is a no UUID, but there is an ID, either update an existing book
     *         by looking up the ID,
     *         or insert a new book.</li>
     *     <li>Neither UUID or ID, just insert a new book</li>
     * </ol>
     *
     * @param context Current context
     * @param book    to import
     *
     * @throws StorageException  The covers directory is not available
     * @throws DaoWriteException on failure
     */
    protected void importBook(@NonNull final Context context,
                              @NonNull final Book book)
            throws StorageException,
                   DaoWriteException {

        final String importedUuid = book.getString(DBKey.BOOK_UUID);

        // ALWAYS let the UUID trump the ID; we may be importing someone else's list
        if (!importedUuid.isEmpty()) {
            // We have a UUID.
            // Check if the book exists in our database by searching on UUID.
            final long localId = bookDao.getBookIdByUuid(importedUuid);
            if (localId > 0) {
                // The book UUID exists in our database.
                // Explicitly set the EXISTING id on the book.
                book.putLong(DBKey.PK_ID, localId);
                // and update/skip using the DataReader.Updates#updateOptions.
                // As we have both a matching UUID and ID, this is 100% safe to do.
                updateOrSkipExistingBook(context, book);

            } else {
                // The book UUID does NOT exist in our database.
                // The UUID as present in the Book will be used to insert the book.
                // If the book contains an ID, and it already exists, REMOVE that ID.
                // Otherwise, we'll be reuse it.
                final long importedId = book.getId();
                if (importedId <= 0 || bookDao.bookExistsById(importedId)) {
                    book.remove(DBKey.PK_ID);
                }
                insertBook(context, book);
            }
        } else {
            // We do NOT have a UUID.
            // Check if the book exists in our database by searching on ID.
            final long importedId = book.getId();
            if (importedId > 0 && bookDao.bookExistsById(importedId)) {
                // The book ID already exists in our database.
                // We will update/skip using the DataReader.Updates#updateOptions.

                // This is risky as we might overwrite a different book which happens
                // to have the same id, but other than skipping there is no other
                // option for now.
                // ENHANCE: callback to ask the user whether to "skip/overwrite" this book.
                updateOrSkipExistingBook(context, book);

            } else {
                // The book ID is not in use, just insert the book reusing the id.
                insertBook(context, book);
            }
        }
    }

    /**
     * Insert the given Book.
     * <ul>
     *     <li>If the Book contains a {@link DBKey#BOOK_UUID}, it WILL be used</li>
     *     <li>If the Book contains a {@link DBKey#PK_ID}, it WILL be used</li>
     * </ul>
     *
     * @param context Current context
     * @param book    to import
     *
     * @throws StorageException  The covers directory is not available
     * @throws DaoWriteException on failure
     */
    private void insertBook(@NonNull final Context context,
                            @NonNull final Book book)
            throws StorageException, DaoWriteException {

        final String preImportUuid = book.getString(DBKey.BOOK_UUID, null);
        final long preImportId = book.getId();

        // explicitly allow the id to be reused if present
        bookDao.insert(context, book, Set.of(BookDao.BookFlag.RunInBatch,
                                             BookDao.BookFlag.UseIdIfPresent));
        results.booksCreated++;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
            LoggerFactory.getLogger().d(TAG, "insertBook",
                                        "preImport=" + preImportId, preImportUuid,
                                        "postImport=" + book.getId(),
                                        book.getString(DBKey.BOOK_UUID, null),
                                        book.getTitle());
        }
    }

    /**
     * Update (or skip) the given Book according to the user options and the respective
     * last-updated dates.
     * <p>
     * The Book must contain both a valid/existing {@link DBKey#PK_ID} and {@link DBKey#BOOK_UUID}.
     *
     * @param context Current context
     * @param book    to update
     *
     * @throws StorageException  The covers directory is not available
     * @throws DaoWriteException on failure
     */
    private void updateOrSkipExistingBook(@NonNull final Context context,
                                          @NonNull final Book book)
            throws StorageException, DaoWriteException {
        switch (updateOption) {
            case Overwrite: {
                updateBook(context, book);
                break;
            }
            case OnlyNewer: {
                final Optional<LocalDateTime> localDate = bookDao.getLastUpdateDate(book.getId());
                final Optional<LocalDateTime> importDate = book.getLastModified(dateParser);

                // Both should always be present, but paranoia...
                final boolean isNewer = localDate.isPresent() && importDate.isPresent()
                                        // is the imported data newer then our data ?
                                        && importDate.get().isAfter(localDate.get());
                if (isNewer) {
                    updateBook(context, book);
                } else {
                    skipBook(book);
                }
                break;
            }
            case Skip: {
                skipBook(book);
                break;
            }
        }
    }

    private void skipBook(@NonNull final Book book) {
        results.booksSkipped++;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
            LoggerFactory.getLogger().d(TAG, "skipBook",
                                        updateOption,
                                        "UUID=" + book.getString(DBKey.BOOK_UUID),
                                        "id=" + book.getId(),
                                        book.getTitle());
        }
    }

    private void updateBook(@NonNull final Context context,
                            @NonNull final Book book)
            throws StorageException, DaoWriteException {
        bookDao.update(context, book, Set.of(BookDao.BookFlag.RunInBatch,
                                             BookDao.BookFlag.UseUpdateDateIfPresent));
        results.booksUpdated++;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
            LoggerFactory.getLogger()
                         .d(TAG, "updateBook",
                            updateOption,
                            "UUID=" + book.getString(DBKey.BOOK_UUID),
                            "id=" + book.getId(),
                            book.getTitle());
        }
    }
}
