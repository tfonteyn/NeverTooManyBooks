/*
 * @Copyright 2018-2021 HardBackNutter
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
import android.util.Log;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordReader;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;

public abstract class BaseRecordReader
        implements RecordReader {

    private static final String TAG = "BaseRecordReader";

    /** Database Access. */
    @NonNull
    protected final BookDao mBookDao;

    @NonNull
    protected final DateParser mDateParser;
    /** cached localized "Books" string. */
    @NonNull
    protected final String mBooksString;
    /** cached localized progress string. */
    @NonNull
    protected final String mProgressMessage;

    protected ImportResults mResults;

    protected BaseRecordReader(@NonNull final Context context) {
        mBookDao = ServiceLocator.getInstance().getBookDao();
        mDateParser = new ISODateParser();

        mBooksString = context.getString(R.string.lbl_books);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
    }

    /**
     * insert or update a single book which has a <strong>valid UUID</strong>.
     *
     * @param context Current context
     * @param book    to import
     *
     * @throws CoverStorageException The covers directory is not available
     * @throws DaoWriteException     on failure
     */
    protected void importBookWithUuid(@NonNull final Context context,
                                      @NonNull final ImportHelper helper,
                                      @NonNull final Book book,
                                      final long importNumericId)
            throws CoverStorageException, DaoWriteException {
        // Verified to be valid earlier.
        final String uuid = book.getString(DBKey.KEY_BOOK_UUID);

        // check if the book exists in our database, and fetch it's id.
        final long databaseBookId = mBookDao.getBookIdByUuid(uuid);
        if (databaseBookId > 0) {
            // The book exists in our database (matching UUID).
            // We'll use a delta: explicitly set the EXISTING id on the book
            // (the importBookId was removed earlier, and is IGNORED)
            book.putLong(DBKey.PK_ID, databaseBookId);

            // UPDATE the existing book (if allowed).
            final ImportHelper.Updates updateOption = helper.getUpdateOption();
            switch (updateOption) {
                case Overwrite: {
                    mBookDao.update(context, book, BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                                   | BookDao.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                    mResults.booksUpdated++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                        Log.d(TAG, "UUID=" + uuid
                                   + "|databaseBookId=" + databaseBookId
                                   + "|Overwrite|" + book.getTitle());
                    }
                    break;
                }
                case OnlyNewer: {
                    final LocalDateTime localDate = mBookDao.getLastUpdateDate(databaseBookId);
                    final LocalDateTime importDate = mDateParser.parse(
                            book.getString(DBKey.UTC_DATE_LAST_UPDATED));

                    if (localDate != null && importDate != null
                        && importDate.isAfter(localDate)) {

                        mBookDao.update(context, book,
                                        BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                        | BookDao.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                        mResults.booksUpdated++;
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                            Log.d(TAG, "UUID=" + uuid
                                       + "|databaseBookId=" + databaseBookId
                                       + "|OnlyNewer|" + book.getTitle());
                        }
                    }
                    break;
                }
                case Skip: {
                    mResults.booksSkipped++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                        Log.d(TAG, "UUID=" + uuid
                                   + "|databaseBookId=" + databaseBookId
                                   + "|Skip|" + book.getTitle());
                    }
                    break;
                }
            }
        } else {
            // The book does NOT exist in our database (no match for the UUID), insert it.

            // If we have an importBookId, and it does not already exist, we reuse it.
            if (importNumericId > 0 && !mBookDao.bookExistsById(importNumericId)) {
                book.putLong(DBKey.PK_ID, importNumericId);
            }

            // the Book object will contain:
            // - valid DBDefinitions.KEY_BOOK_UUID not existent in the database
            // - NO id, OR an id which does not exist in the database yet.
            // INSERT, explicitly allowing the id to be reused if present
            mBookDao.insert(context, book, BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                           | BookDao.BOOK_FLAG_USE_ID_IF_PRESENT);
            mResults.booksCreated++;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                Log.d(TAG, "UUID=" + book.getString(DBKey.KEY_BOOK_UUID)
                           + "|importNumericId=" + importNumericId
                           + "|INSERT|book=" + book.getId() + "|" + book.getTitle());
            }
        }
    }
}
