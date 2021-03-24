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
package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.content.Context;
import android.database.sqlite.SQLiteDoneException;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BundleCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.CertificateCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.JsonCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.ListStyleCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.SharedPreferencesCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.org.json.JSONTokener;

/**
 * Supports two levels of Archive records.
 * <ol>
 *     <li>Single level: a record will contain ONE of
 *     <ul>
 *         <li>{@link RecordType#MetaData}</li>
 *         <li>{@link RecordType#Styles}</li>
 *         <li>{@link RecordType#Preferences}</li>
 *         <li>{@link RecordType#Certificates}</li>
 *         <li>{@link RecordType#Books}</li>
 *     </ul>
 *     </li>
 *     <li>EXPERIMENTAL: All-in-one: top level: {@link JsonCoder#TAG_APPLICATION_ROOT},
 *     which contains:
 *     <ul>
 *         <li>{@link RecordType#MetaData}</li>
 *         <li>{@link RecordType#AutoDetect}, which in turn contains:
 *         <ul>
 *              <li>{@link RecordType#Styles}</li>
 *              <li>{@link RecordType#Preferences}</li>
 *              <li>{@link RecordType#Certificates}</li>
 *              <li>{@link RecordType#Books}</li>
 *         </ul>
 *         </li>
 *     </ul>
 *
 *     </li>
 * </ol>
 */
public class JsonRecordReader
        implements RecordReader {

    /** Log tag. */
    private static final String TAG = "JsonRecordReader";
    /** log error string. */
    private static final String ERROR_IMPORT_FAILED_AT_ROW = "Import failed at row ";
    /** Database Access. */
    @NonNull
    private final BookDao mBookDao;
    /** cached localized "Books" string. */
    @NonNull
    private final String mBooksString;
    @NonNull
    private final String mProgressMessage;
    @NonNull
    private final JsonCoder<Book> mBookCoder;
    @NonNull
    private final Set<RecordType> mImportEntriesAllowed;
    private ImportResults mResults;

    /**
     * Constructor.
     * <p>
     * Only supports {@link RecordType#Books}.
     *
     * @param context              Current context
     * @param bookDao              Database Access;
     * @param importEntriesAllowed the record types we're allowed to read
     */
    @AnyThread
    public JsonRecordReader(@NonNull final Context context,
                            @NonNull final BookDao bookDao,
                            @NonNull final Set<RecordType> importEntriesAllowed) {
        mBookDao = bookDao;
        mImportEntriesAllowed = importEntriesAllowed;

        mBookCoder = new BookCoder(context);

        mBooksString = context.getString(R.string.lbl_books);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
    }

    @Override
    @NonNull
    public ArchiveMetaData readMetaData(@NonNull final ArchiveReaderRecord record)
            throws GeneralParsingException, IOException {
        try {
            // Don't close this stream
            final InputStream is = record.getInputStream();
            final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            final BufferedReader reader = new BufferedReader(isr, RecordReader.BUFFER_SIZE);
            // read the entire record into a single String
            final String content = reader.lines().collect(Collectors.joining());
            return new ArchiveMetaData(new BundleCoder().decode(new JSONObject(content)));

        } catch (@NonNull final JSONException e) {
            throw new GeneralParsingException(e);

        } catch (@NonNull final UncheckedIOException e) {
            //noinspection ConstantConditions
            throw e.getCause();
        }
    }

    @Override
    @NonNull
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ArchiveReaderRecord record,
                              @ImportHelper.Options final int options,
                              @NonNull final ProgressListener progressListener)
            throws IOException, ImportException {

        mResults = new ImportResults();

        if (record.getType().isPresent()) {
            final RecordType recordType = record.getType().get();

            try {
                // Don't close this stream
                final InputStream is = record.getInputStream();
                final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                final BufferedReader reader = new BufferedReader(isr, RecordReader.BUFFER_SIZE);

                // read the entire record into a single String
                // OutOfMemoryError when the data starts to exceed 8mb (9000+ books)
//                final String content = reader.lines().collect(Collectors.joining());
//                if (!content.isEmpty()) {

//                    JSONObject root = new JSONObject(content);

                // 2021-03-01: now using an updated/repacked org.json, version 20201115
                JSONObject root = new JSONObject(new JSONTokener(reader));

                // Is this a JsonArchiveWriter format ?
                // Then descend to the container (data) object.
                if (root.has(JsonCoder.TAG_APPLICATION_ROOT)) {
                    root = root.getJSONObject(JsonCoder.TAG_APPLICATION_ROOT)
                               .getJSONObject(RecordType.AutoDetect.getName());
                }

                if (mImportEntriesAllowed.contains(recordType)
                    || recordType == RecordType.AutoDetect) {

                    if (recordType == RecordType.Styles
                        || recordType == RecordType.AutoDetect) {
                        readStyles(context, root);
                    }

                    if (recordType == RecordType.Preferences
                        || recordType == RecordType.AutoDetect) {
                        readPreferences(root);
                    }

                    if (recordType == RecordType.Certificates
                        || recordType == RecordType.AutoDetect) {
                        readCertificates(context, root);
                    }

                    if (recordType == RecordType.Books
                        || recordType == RecordType.AutoDetect) {
                        readBooks(context, root, options, progressListener);
                    }
                }
//                }
            } catch (@NonNull final JSONException e) {
                throw new ImportException(context.getString(R.string.error_import_failed), e);

            } catch (@NonNull final UncheckedIOException e) {
                //noinspection ConstantConditions
                throw e.getCause();

            } catch (@NonNull final OutOfMemoryError e) {
                // wrap it. Note this is not foolproof... the app might still crash!
                // 2021-03-01: now using an updated/repacked org.json, version 20201115
                // which uses the Reader directly. Theoretically this should now be fixed.
                throw new IOException(e);
            }
        }
        return mResults;
    }

    private void readStyles(@NonNull final Context context,
                            @NonNull final JSONObject root)
            throws JSONException {
        final JSONArray jsonRoot = root.optJSONArray(RecordType.Styles.getName());
        if (jsonRoot != null) {
            //noinspection SimplifyStreamApiCallChains
            new ListStyleCoder(context)
                    .decode(jsonRoot)
                    .stream()
                    .forEach(StyleUtils::updateOrInsert);
            mResults.styles = jsonRoot.length();
        }
    }

    private void readPreferences(@NonNull final JSONObject root)
            throws JSONException {
        final JSONObject jsonRoot = root.optJSONObject(RecordType.Preferences.getName());
        if (jsonRoot != null) {
            new SharedPreferencesCoder(ServiceLocator.getGlobalPreferences())
                    .decode(jsonRoot);
            mResults.preferences = 1;
        }
    }

    private void readCertificates(@NonNull final Context context,
                                  @NonNull final JSONObject root)
            throws IOException, JSONException {
        final JSONObject jsonRoot = root.optJSONObject(RecordType.Certificates.getName());
        if (jsonRoot != null) {
            final CertificateCoder coder = new CertificateCoder();

            final JSONObject calibreCA = jsonRoot.optJSONObject(CalibreContentServer.SERVER_CA);
            if (calibreCA != null) {
                try {
                    CalibreContentServer.setCertificate(context, coder.decode(calibreCA));
                    mResults.certificates++;
                } catch (@NonNull final CertificateEncodingException e) {
                    // log but don't quit
                    Logger.error(context, TAG, e);
                }
            }
        }
    }

    private void readBooks(@NonNull final Context context,
                           @NonNull final JSONObject root,
                           @ImportHelper.Options final int options,
                           @NonNull final ProgressListener progressListener)
            throws JSONException {

        final JSONArray books = root.optJSONArray(RecordType.Books.getName());
        if (books == null || books.isEmpty()) {
            return;
        }

        final boolean updatesMustSync =
                (options & ImportHelper.OPTION_UPDATES_MUST_SYNC) != 0;
        final boolean updatesMayOverwrite =
                (options & ImportHelper.OPTION_UPDATES_MAY_OVERWRITE) != 0;

        int row = 1;
        // Count the number of rows between committing a transaction
        int txRowCount = 0;
        // Instance in time when we last send a progress message
        long lastUpdateTime = 0;
        // Count the nr of books in between progress updates.
        int delta = 0;

        // not perfect, but good enough
        if (progressListener.getMaxPos() < books.length()) {
            progressListener.setMaxPos(books.length());
        }

        final SynchronizedDb db = ServiceLocator.getDb();

        Synchronizer.SyncLock txLock = null;
        try {
            // Iterate through each imported element
            for (int i = 0; i < books.length() && !progressListener.isCancelled(); i++) {
                // every 10 inserted, we commit the transaction
                if (db.inTransaction() && txRowCount > 10) {
                    db.setTransactionSuccessful();
                    db.endTransaction(txLock);
                }
                if (!db.inTransaction()) {
                    txLock = db.beginTransaction(true);
                    txRowCount = 0;
                }
                txRowCount++;

                try {
                    final Book book = mBookCoder.decode(books.getJSONObject(i));
                    Objects.requireNonNull(book.getString(DBKeys.KEY_BOOK_UUID),
                                           "KEY_BOOK_UUID");

                    final long importNumericId = book.getLong(DBKeys.KEY_PK_ID);
                    book.remove(DBKeys.KEY_PK_ID);
                    importBookWithUuid(context, updatesMustSync, updatesMayOverwrite,
                                       book, importNumericId);

                } catch (@NonNull final DaoWriteException | SQLiteDoneException e) {
                    //TODO: use a meaningful user-displaying string.
                    mResults.booksSkipped++;
                    mResults.failedLinesMessage.add(
                            context.getString(R.string.error_import_csv_line, row));
                    mResults.failedLinesNr.add(row);

                    Logger.warn(context, TAG, "e=" + e.getMessage(),
                                ERROR_IMPORT_FAILED_AT_ROW + row);

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS_EXT) {
                        // logging with the full exception is VERY HEAVY
                        Logger.error(context, TAG, e, ERROR_IMPORT_FAILED_AT_ROW + row);
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
                    progressListener.publishProgress(delta, msg);
                    lastUpdateTime = now;
                    delta = 0;
                }
            }
        } finally {
            if (db.inTransaction()) {
                db.setTransactionSuccessful();
                db.endTransaction(txLock);
            }
        }
        // minus 1 to compensate for the last increment
        mResults.booksProcessed = row - 1;
    }

    /**
     * insert or update a single book which has a <strong>valid UUID</strong>.
     *
     * @param context Current context
     * @param book    to import
     *
     * @throws DaoWriteException on failure
     */
    private void importBookWithUuid(@NonNull final Context context,
                                    final boolean updatesMustSync,
                                    final boolean updatesMayOverwrite,
                                    @NonNull final Book book,
                                    final long importNumericId)
            throws DaoWriteException {
        // Verified to be valid earlier.
        final String uuid = book.getString(DBKeys.KEY_BOOK_UUID);

        // check if the book exists in our database, and fetch it's id.
        final long databaseBookId = mBookDao.getBookIdFromUuid(uuid);
        if (databaseBookId > 0) {
            // The book exists in our database (matching UUID).

            // Explicitly set the EXISTING id on the book
            // (the importBookId was removed earlier, and is IGNORED)
            book.putLong(DBKeys.KEY_PK_ID, databaseBookId);

            // UPDATE the existing book (if allowed). Check the sync option FIRST!
            if ((updatesMustSync
                 && isImportNewer(context, databaseBookId, book.getLastUpdateUtcDate(context)))
                || updatesMayOverwrite) {

                mBookDao.update(context, book, BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                               | BookDao.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
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
            if (importNumericId > 0 && !mBookDao.bookExistsById(importNumericId)) {
                book.putLong(DBKeys.KEY_PK_ID, importNumericId);
            }

            // the Book object will contain:
            // - valid DBDefinitions.KEY_BOOK_UUID not existent in the database
            // - NO id, OR an id which does not exist in the database yet.
            // INSERT, explicitly allowing the id to be reused if present
            final long insId = mBookDao.insert(context, book,
                                               BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                               | BookDao.BOOK_FLAG_USE_ID_IF_PRESENT);
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
     * @param context              Current context
     * @param bookId               the local book id to lookup in our database
     * @param importLastUpdateDate to check
     *
     * @return {@code true} if the imported data is newer then the local data.
     */
    private boolean isImportNewer(@NonNull final Context context,
                                  @IntRange(from = 1) final long bookId,
                                  @Nullable final LocalDateTime importLastUpdateDate) {
        if (importLastUpdateDate == null) {
            return false;
        }

        final LocalDateTime utcLastUpdated = mBookDao.getBookLastUpdateUtcDate(context, bookId);
        return utcLastUpdated == null || importLastUpdateDate.isAfter(utcLastUpdated);
    }
}
