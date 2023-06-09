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
package com.hardbacknutter.nevertoomanybooks.backup.backupbase;

import android.content.Context;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DeletedBooksDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;

public abstract class BaseRecordReader
        implements RecordReader {

    private static final String TAG = "BaseRecordReader";

    /** Database Access. */
    @NonNull
    protected final BookDao bookDao;
    @NonNull
    protected final DeletedBooksDao deletedBooksDao;

    @NonNull
    protected final DateParser dateParser;
    /** cached localized "Books" string. */
    @NonNull
    protected final String booksString;
    /** cached localized progress string. */
    @NonNull
    protected final String progressMessage;

    protected ImportResults results;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param systemLocale to use for ISO date parsing
     */
    protected BaseRecordReader(@NonNull final Context context,
                               @NonNull final Locale systemLocale) {
        bookDao = ServiceLocator.getInstance().getBookDao();
        deletedBooksDao = ServiceLocator.getInstance().getDeletedBooksDao();

        this.dateParser = new ISODateParser(systemLocale);

        booksString = context.getString(R.string.lbl_books);
        progressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
    }

    /**
     * insert or update a single book which has a <strong>valid UUID</strong>.
     *
     * @param context         Current context
     * @param helper          ImportHelper for options etc...
     * @param book            to import
     * @param uuid            the uuid as found in the import record
     * @param importNumericId (optional) the numeric id for the book as found in the import.
     *                        {@code 0} for none
     *
     * @throws StorageException  The covers directory is not available
     * @throws DaoWriteException on failure
     */
    protected void importBookWithUuid(@NonNull final Context context,
                                      @NonNull final ImportHelper helper,
                                      @NonNull final Book book,
                                      @NonNull final String uuid,
                                      final long importNumericId)
            throws StorageException,
                   DaoWriteException {

        // check if the book exists in our database, and fetch it's id.
        final long databaseBookId = bookDao.getBookIdByUuid(uuid);
        if (databaseBookId > 0) {
            // The book exists in our database (matching UUID).
            // We'll use a delta: explicitly set the EXISTING id on the book
            // (the importBookId was removed earlier, and is IGNORED)
            book.putLong(DBKey.PK_ID, databaseBookId);

            // UPDATE the existing book (if allowed).
            final DataReader.Updates updateOption = helper.getUpdateOption();
            switch (updateOption) {
                case Overwrite: {
                    bookDao.update(context, book, Set.of(BookDao.BookFlag.RunInBatch,
                                                         BookDao.BookFlag.UseUpdateDateIfPresent));
                    results.booksUpdated++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                        LoggerFactory.getLogger().d(TAG, "importBookWithUuid", "Overwrite",
                                                    "UUID=" + uuid,
                                                    "databaseBookId=" + databaseBookId,
                                                    book.getTitle());
                    }
                    break;
                }
                case OnlyNewer: {
                    final LocalDateTime localDate = bookDao.getLastUpdateDate(databaseBookId);
                    if (localDate != null) {
                        final LocalDateTime importDate = dateParser.parse(
                                book.getString(DBKey.DATE_LAST_UPDATED__UTC, null));

                        if (importDate != null && importDate.isAfter(localDate)) {

                            bookDao.update(context, book,
                                           Set.of(BookDao.BookFlag.RunInBatch,
                                                  BookDao.BookFlag.UseUpdateDateIfPresent));
                            results.booksUpdated++;
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                                LoggerFactory.getLogger()
                                             .d(TAG, "importBookWithUuid", "OnlyNewer",
                                                "UUID=" + uuid,
                                                "databaseBookId=" + databaseBookId,
                                                book.getTitle());
                            }
                        }
                    }
                    break;
                }
                case Skip: {
                    results.booksSkipped++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                        LoggerFactory.getLogger().d(TAG, "importBookWithUuid", "Skip",
                                                    "UUID=" + uuid,
                                                    "databaseBookId=" + databaseBookId,
                                                    book.getTitle());
                    }
                    break;
                }
            }
        } else {
            // The book does NOT exist in our database (no match for the UUID), insert it.

            // If we have an importBookId, and it does not already exist, we reuse it.
            if (importNumericId > 0 && !bookDao.bookExistsById(importNumericId)) {
                book.putLong(DBKey.PK_ID, importNumericId);
            }

            // the Book object will contain:
            // - a valid book UUID which does not exist in the database
            // - no ID, or an ID which does not exist in the database yet.
            // INSERT, explicitly allowing the id to be reused if present
            bookDao.insert(context, book, Set.of(BookDao.BookFlag.RunInBatch,
                                                 BookDao.BookFlag.UseIdIfPresent));
            results.booksCreated++;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                LoggerFactory.getLogger().d(TAG, "importBookWithUuid", "INSERT",
                                            "UUID=" + book.getString(DBKey.BOOK_UUID, null),
                                            "importNumericId=" + importNumericId,
                                            "book=" + book.getId(),
                                            book.getTitle());
            }
        }
    }
}
