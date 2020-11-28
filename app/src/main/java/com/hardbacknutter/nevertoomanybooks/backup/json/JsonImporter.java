/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.content.Context;
import android.database.sqlite.SQLiteDoneException;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveContainerEntry;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.base.ReaderEntity;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

/**
 * TEST: Experimental - NEEDS TESTING
 */
public class JsonImporter
        implements Importer {

    /** Log tag. */
    private static final String TAG = "JsonImporter";

    /** Buffer for the Reader. */
    private static final int BUFFER_SIZE = 65535;
    /** log error string. */
    private static final String ERROR_IMPORT_FAILED_AT_ROW = "Import failed at row ";
    /** Database Access. */
    @NonNull
    private final DAO mDb;
    /** cached localized "Books" string. */
    @NonNull
    private final String mBooksString;
    @NonNull
    private final String mProgressMessage;
    @NonNull
    private final String mUnknownString;
    private final BookCoder mBookCoder;
    private final ImportResults mResults = new ImportResults();
    /** import configuration. */
    private boolean mSyncBooks;
    private boolean mOverwriteBooks;

    /**
     * Constructor.
     * <p>
     * Only supports {@link ArchiveContainerEntry#BooksJson}.
     *
     * @param context Current context
     */
    @AnyThread
    public JsonImporter(@NonNull final Context context) {

        mDb = new DAO(TAG);

        mBooksString = context.getString(R.string.lbl_books);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
        mUnknownString = context.getString(R.string.unknown);

        mBookCoder = new BookCoder(StyleDAO.getDefault(context, mDb));
    }

    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ReaderEntity entity,
                              final int options,
                              @NonNull final ProgressListener progressListener)
            throws IOException, ImportException {

        mSyncBooks = (options & ImportHelper.OPTIONS_UPDATES_MUST_SYNC) != 0;
        mOverwriteBooks = (options & ImportHelper.OPTIONS_UPDATES_MAY_OVERWRITE) != 0;

        // we only support books, return empty results
        if (entity.getType() != ArchiveContainerEntry.BooksJson) {
            return mResults;
        }

        // Don't close this stream!
        final InputStream is = entity.getInputStream();
        final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        final BufferedReader reader = new BufferedReader(isr, BUFFER_SIZE);

        // We read the whole file/list into memory.
        final StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        if (sb.length() == 0) {
            return mResults;
        }

        int row = 1;
        // Count the number of rows between committing a transaction
        int txRowCount = 0;
        // Instance in time when we last send a progress message
        long lastUpdateTime = 0;
        // Count the nr of books in between progress updates.
        int delta = 0;

        Synchronizer.SyncLock txLock = null;
        try {
            final JSONArray books = new JSONArray(sb.toString());

            // not perfect, but good enough
            if (progressListener.getMaxPos() < books.length()) {
                progressListener.setMaxPos(books.length());
            }

            // Iterate through each imported row
            for (int i = 0; i < books.length() && !progressListener.isCancelled(); i++) {
                // every 10 inserted, we commit the transaction
                if (mDb.inTransaction() && txRowCount > 10) {
                    mDb.setTransactionSuccessful();
                    mDb.endTransaction(txLock);
                }
                if (!mDb.inTransaction()) {
                    txLock = mDb.beginTransaction(true);
                    txRowCount = 0;
                }
                txRowCount++;

                try {
                    final Book book = mBookCoder.decode(books.getJSONObject(i));
                    Objects.requireNonNull(book.getString(DBDefinitions.KEY_BOOK_UUID),
                                           "KEY_BOOK_UUID");

                    final long importNumericId = book.getLong(DBDefinitions.KEY_PK_ID);
                    book.remove(DBDefinitions.KEY_PK_ID);
                    importBookWithUuid(context, book, importNumericId);

                } catch (@NonNull final DAO.DaoWriteException | SQLiteDoneException e) {
                    //TODO: use a meaningful user-displaying string.
                    mResults.booksSkipped++;
                    mResults.failedLinesMessage.add(mUnknownString);
                    mResults.failedLinesNr.add(row);

                    if (BuildConfig.DEBUG /* always */) {
                        if (DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                            Logger.warn(context, TAG, "e=" + e.getMessage(),
                                        ERROR_IMPORT_FAILED_AT_ROW + row);
                        } else if (DEBUG_SWITCHES.IMPORT_CSV_BOOKS_EXT) {
                            // logging with the full exception is VERY HEAVY
                            Logger.error(context, TAG, e, ERROR_IMPORT_FAILED_AT_ROW + row);
                        }
                    }
                }

                row++;

                delta++;
                final long now = System.currentTimeMillis();
                if ((now - lastUpdateTime) > progressListener.getUpdateIntervalInMs()
                    && !progressListener.isCancelled()) {
                    final String msg = String.format(mProgressMessage,
                                                     mBooksString,
                                                     mResults.booksCreated,
                                                     mResults.booksUpdated,
                                                     mResults.booksSkipped);
                    progressListener.publishProgressStep(delta, msg);
                    lastUpdateTime = now;
                    delta = 0;
                }
            }

        } catch (@NonNull final JSONException e) {
            throw new ImportException(context.getString(R.string.error_import_failed), e);

        } finally {
            if (mDb.inTransaction()) {
                mDb.setTransactionSuccessful();
                mDb.endTransaction(txLock);
            }
        }

        // minus 1 to compensate for the last increment
        mResults.booksProcessed = row - 1;

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "read|mResults=" + mResults);
        }
        return mResults;
    }

    /**
     * insert or update a single book which has a <strong>valid UUID</strong>.
     *
     * @param context Current context
     * @param book    to import
     *
     * @throws DAO.DaoWriteException on failure
     */
    private void importBookWithUuid(@NonNull final Context context,
                                    @NonNull final Book book,
                                    final long importNumericId)
            throws DAO.DaoWriteException {
        // Verified to be valid earlier.
        final String uuid = book.getString(DBDefinitions.KEY_BOOK_UUID);

        // check if the book exists in our database, and fetch it's id.
        final long databaseBookId = mDb.getBookIdFromUuid(uuid);
        if (databaseBookId > 0) {
            // The book exists in our database (matching UUID).

            // Explicitly set the EXISTING id on the book
            // (the importBookId was removed earlier, and is IGNORED)
            book.putLong(DBDefinitions.KEY_PK_ID, databaseBookId);

            // UPDATE the existing book (if allowed). Check the sync option FIRST!
            if ((mSyncBooks && isImportNewer(context, mDb, book, databaseBookId))
                || mOverwriteBooks) {

                mDb.update(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                          | DAO.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                mResults.booksUpdated++;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                    Log.d(TAG, "UUID=" + uuid
                               + "|databaseBookId=" + databaseBookId
                               + "|update|" + book.getTitle());
                }

            } else {
                mResults.booksSkipped++;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                    Log.d(TAG, "UUID=" + uuid
                               + "|databaseBookId=" + databaseBookId
                               + "|skipped|" + book.getTitle());
                }
            }

        } else {
            // The book does NOT exist in our database (no match for the UUID), insert it.

            // If we have an importBookId, and it does not already exist, we reuse it.
            if (importNumericId > 0 && !mDb.bookExistsById(importNumericId)) {
                book.putLong(DBDefinitions.KEY_PK_ID, importNumericId);
            }

            // the Book object will contain:
            // - valid DBDefinitions.KEY_BOOK_UUID not existent in the database
            // - NO id, OR an id which does not exist in the database yet.
            // INSERT, explicitly allowing the id to be reused if present
            final long insId = mDb.insert(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                                         | DAO.BOOK_FLAG_USE_ID_IF_PRESENT);
            mResults.booksCreated++;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                Log.d(TAG, "UUID=" + uuid
                           + "|importNumericId=" + importNumericId
                           + "|insert=" + insId
                           + "|" + book.getTitle());
            }
        }
    }

    /**
     * Check if the incoming book is newer than the stored book data.
     *
     * @param context Current context
     * @param db      Database Access
     * @param book    the book we're updating
     * @param bookId  the book id to lookup in our database
     */
    private boolean isImportNewer(@NonNull final Context context,
                                  @NonNull final DAO db,
                                  @NonNull final Book book,
                                  @IntRange(from = 1) final long bookId) {
        final LocalDateTime utcImportDate =
                DateParser.getInstance(context)
                          .parseISO(book.getString(DBDefinitions.KEY_UTC_LAST_UPDATED));
        if (utcImportDate == null) {
            return false;
        }

        final LocalDateTime utcLastUpdated = db.getBookLastUpdateUtcDate(context, bookId);

        return utcLastUpdated == null || utcImportDate.isAfter(utcLastUpdated);
    }

    @Override
    public void close() {
        mDb.purge();
        mDb.close();
    }
}
