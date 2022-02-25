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
import android.os.Bundle;

import androidx.annotation.AnyThread;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.backupbase.BaseRecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.backup.common.DataReader;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookshelfCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BundleCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.CalibreLibraryCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.CertificateCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.JsonCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.ListStyleCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.SharedPreferencesCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Styles;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
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
 *         <li>{@link RecordType#Bookshelves}</li>
 *         <li>{@link RecordType#CalibreLibraries}</li>
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
 *              <li>{@link RecordType#Bookshelves}</li>
 *              <li>{@link RecordType#CalibreLibraries}</li>
 *              <li>{@link RecordType#Books}</li>
 *         </ul>
 *         </li>
 *     </ul>
 *
 *     </li>
 * </ol>
 */
public class JsonRecordReader
        extends BaseRecordReader {

    /** Log tag. */
    private static final String TAG = "JsonRecordReader";
    @NonNull
    private final JsonCoder<Book> mBookCoder;
    @NonNull
    private final Set<RecordType> mImportEntriesAllowed;
    @Nullable
    private JsonCoder<Bookshelf> mBookshelfCoder;
    @Nullable
    private JsonCoder<CalibreLibrary> mCalibreLibraryCoder;

    /**
     * Constructor.
     * <p>
     * Only supports {@link RecordType#Books}.
     *
     * @param context              Current context
     * @param importEntriesAllowed the record types we're allowed to read
     */
    @AnyThread
    public JsonRecordReader(@NonNull final Context context,
                            @NonNull final Set<RecordType> importEntriesAllowed) {
        super(context);
        mImportEntriesAllowed = importEntriesAllowed;

        mBookCoder = new BookCoder(context, getBookshelfCoder(context),
                                   getCalibreLibraryCoder(context));
    }

    @NonNull
    private JsonCoder<Bookshelf> getBookshelfCoder(@NonNull final Context context) {
        if (mBookshelfCoder == null) {
            mBookshelfCoder = new BookshelfCoder(context);
        }
        return mBookshelfCoder;
    }

    @NonNull
    private JsonCoder<CalibreLibrary> getCalibreLibraryCoder(@NonNull final Context context) {
        if (mCalibreLibraryCoder == null) {
            mCalibreLibraryCoder = new CalibreLibraryCoder(context, getBookshelfCoder(context));
        }
        return mCalibreLibraryCoder;
    }

    @Override
    @NonNull
    public Optional<ArchiveMetaData> readMetaData(@NonNull final ArchiveReaderRecord record)
            throws ImportException, IOException {
        try {
            // Don't close this stream
            final InputStream is = record.getInputStream();
            final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            final BufferedReader reader = new BufferedReader(isr, RecordReader.BUFFER_SIZE);
            // read the entire record into a single String
            final String content = reader.lines().collect(Collectors.joining());
            final Bundle data = new BundleCoder().decode(new JSONObject(content));
            if (data.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(new ArchiveMetaData(data));
            }

        } catch (@NonNull final JSONException e) {
            throw new ImportException(e);

        } catch (@NonNull final UncheckedIOException e) {
            //noinspection ConstantConditions
            throw e.getCause();
        }
    }

    @Override
    @NonNull
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ArchiveReaderRecord record,
                              @NonNull final ImportHelper helper,
                              @NonNull final ProgressListener progressListener)
            throws CoverStorageException, ImportException, IOException {

        mResults = new ImportResults();

        if (record.getType().isPresent()) {
            final RecordType recordType = record.getType().get();

            try {
                // Don't close this stream
                final InputStream is = record.getInputStream();
                final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                final JSONTokener tokener = new JSONTokener(isr);

                JSONObject root = new JSONObject(tokener);

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

                    if (recordType == RecordType.Bookshelves
                        || recordType == RecordType.AutoDetect) {
                        readBookshelves(context, root, helper);
                    }

                    if (recordType == RecordType.CalibreLibraries
                        || recordType == RecordType.AutoDetect) {
                        readCalibreLibraries(context, root, helper);
                    }

                    if (recordType == RecordType.Books
                        || recordType == RecordType.AutoDetect) {
                        readBooks(context, root, helper, progressListener);
                    }
                }
            } catch (@NonNull final JSONException e) {
                throw new ImportException(context.getString(R.string.error_import_failed_for_record,
                                                            recordType.getName()), e);

            } catch (@NonNull final UncheckedIOException e) {
                //noinspection ConstantConditions
                throw e.getCause();

            } catch (@NonNull final Error e) {
                // wrap it. Note this is not foolproof... the app might still crash!

                // StackOverflowError
                // 2022-02-22 Never seen in live, but occasionally seen during a junit test
                // while running with a debugger attached.

                // OutOfMemoryError
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
            final Styles styles = ServiceLocator.getInstance().getStyles();
            new ListStyleCoder(context)
                    .decode(jsonRoot)
                    .forEach(styles::updateOrInsert);
            mResults.styles = jsonRoot.length();
        }
    }

    private void readPreferences(@NonNull final JSONObject root)
            throws JSONException {
        final JSONObject jsonRoot = root.optJSONObject(RecordType.Preferences.getName());
        if (jsonRoot != null) {
            // The coder itself will set/update the values directly.
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

            // We only have a single cert for now
            final JSONObject calibreCA = jsonRoot.optJSONObject(CalibreContentServer.SERVER_CA);
            if (calibreCA != null) {
                try {
                    CalibreContentServer.setCertificate(context, coder.decode(calibreCA));
                    mResults.certificates++;
                } catch (@NonNull final CertificateEncodingException e) {
                    // log but don't quit
                    Logger.error(TAG, e);
                }
            }
        }
    }

    private void readBookshelves(@NonNull final Context context,
                                 @NonNull final JSONObject root,
                                 @NonNull final ImportHelper helper)
            throws JSONException {
        final JSONArray jsonRoot = root.optJSONArray(RecordType.Bookshelves.getName());
        if (jsonRoot != null) {
            final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
            new BookshelfCoder(context)
                    .decode(jsonRoot)
                    .forEach(bookshelf -> {
                        bookshelfDao.fixId(bookshelf);
                        if (bookshelf.getId() > 0) {
                            // The shelf already exists
                            final DataReader.Updates updateOption = helper.getUpdateOption();
                            switch (updateOption) {
                                case Overwrite: {
                                    bookshelfDao.update(context, bookshelf);
                                    break;
                                }
                                case OnlyNewer:
                                case Skip:
                                    break;
                            }
                        } else {
                            bookshelfDao.insert(context, bookshelf);
                        }
                    });
        }
    }

    private void readCalibreLibraries(@NonNull final Context context,
                                      @NonNull final JSONObject root,
                                      @NonNull final ImportHelper helper)
            throws JSONException {
        final JSONArray jsonRoot = root.optJSONArray(RecordType.CalibreLibraries.getName());
        if (jsonRoot != null) {
            final CalibreLibraryDao libraryDao =
                    ServiceLocator.getInstance().getCalibreLibraryDao();

            getCalibreLibraryCoder(context)
                    .decode(jsonRoot)
                    .forEach(library -> {
                        libraryDao.fixId(library);
                        if (library.getId() > 0) {
                            // The library already exists
                            final DataReader.Updates updateOption = helper.getUpdateOption();
                            switch (updateOption) {
                                case Overwrite: {
                                    libraryDao.update(library);
                                    break;
                                }
                                case OnlyNewer:
                                case Skip:
                                    break;
                            }
                        } else {
                            libraryDao.insert(library);
                        }
                    });
        }
    }

    private void readBooks(@NonNull final Context context,
                           @NonNull final JSONObject root,
                           @NonNull final ImportHelper helper,
                           @NonNull final ProgressListener progressListener)
            throws CoverStorageException, JSONException {

        final JSONArray books = root.optJSONArray(RecordType.Books.getName());
        if (books == null || books.isEmpty()) {
            return;
        }

        int row = 1;
        // Instance in time when we last send a progress message
        long lastUpdateTime = 0;
        // Count the nr of books in between progress updates.
        int delta = 0;

        // not perfect, but good enough
        if (progressListener.getMaxPos() < books.length()) {
            progressListener.setMaxPos(books.length());
        }

        final SynchronizedDb db = ServiceLocator.getInstance().getDb();

        Synchronizer.SyncLock txLock = null;

        // Iterate through each imported element
        for (int i = 0; i < books.length() && !progressListener.isCancelled(); i++) {

            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }
            try {
                final Book book = mBookCoder.decode(books.getJSONObject(i));
                Objects.requireNonNull(book.getString(DBKey.KEY_BOOK_UUID), DBKey.KEY_BOOK_UUID);

                final long importNumericId = book.getLong(DBKey.PK_ID);
                book.remove(DBKey.PK_ID);
                importBookWithUuid(context, helper, book, importNumericId);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
            } catch (@NonNull final DaoWriteException | SQLiteDoneException e) {
                //TODO: use a meaningful user-displaying string.
                handleRowException(row, e, context.getString(R.string.error_import_csv_line, row));

            } finally {
                if (txLock != null) {
                    db.endTransaction(txLock);
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
        // minus 1 to compensate for the last increment
        mResults.booksProcessed = row - 1;
    }

    private void handleRowException(final int row,
                                    @NonNull final Exception e,
                                    @NonNull final String msg) {
        mResults.booksFailed++;
        mResults.failedLinesMessage.add(msg);
        mResults.failedLinesNr.add(row);

        if (BuildConfig.DEBUG /* always */) {
            if (DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                Logger.w(TAG, "Import failed at row " + row + "|e=" + e.getMessage());
            } else if (DEBUG_SWITCHES.IMPORT_CSV_BOOKS_EXT) {
                // logging with the full exception is VERY HEAVY
                Logger.e(TAG, "Import failed at row " + row, e);
            }
        }
    }
}
