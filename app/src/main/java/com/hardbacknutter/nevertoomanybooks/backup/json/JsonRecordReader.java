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
package com.hardbacknutter.nevertoomanybooks.backup.json;

import android.content.Context;
import android.database.sqlite.SQLiteDoneException;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.backupbase.ArchiveWriterAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.backupbase.BaseRecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BookshelfCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.BundleCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.CalibreCustomFieldCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.CalibreLibraryCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.CertificateCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.DeletedBooksCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.JsonCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.SharedPreferencesCoder;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.StyleCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StylesHelper;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.UncheckedDaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreCustomFieldDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.org.json.JSONTokener;

/**
 * Supports two levels of Archive records.
 * <ol>
 *     <li>Single level (one record == one json file): a record will contain ONE of
 *     <ul>
 *         <li>{@link RecordType#MetaData}</li>
 *         <li>{@link RecordType#Styles}</li>
 *         <li>{@link RecordType#Preferences}</li>
 *         <li>{@link RecordType#Certificates}</li>
 *         <li>{@link RecordType#Bookshelves}</li>
 *         <li>{@link RecordType#CalibreLibraries}</li>
 *         <li>{@link RecordType#CalibreCustomFields}</li>
 *         <li>{@link RecordType#DeletedBooks}</li>
 *         <li>{@link RecordType#Books}</li>
 *     </ul>
 *     </li>
 *     <li>All-in-one (single json file): top level: {@link JsonCoder#TAG_APPLICATION_ROOT},
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
 *              <li>{@link RecordType#CalibreCustomFields}</li>
 *              <li>{@link RecordType#DeletedBooks}</li>
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
    private final Set<RecordType> allowedTypes;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param systemLocale to use for ISO date parsing
     * @param allowedTypes the record types we're allowed to read
     */
    @AnyThread
    public JsonRecordReader(@NonNull final Context context,
                            @NonNull final Locale systemLocale,
                            @NonNull final Set<RecordType> allowedTypes) {
        super(context, systemLocale);
        this.allowedTypes = allowedTypes;
    }

    @Override
    @NonNull
    public Optional<ArchiveMetaData> readMetaData(@NonNull final Context context,
                                                  @NonNull final ArchiveReaderRecord record)
            throws DataReaderException,
                   IOException {
        try {
            // Don't close this stream
            final InputStream is = record.getInputStream();
            final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            final JSONTokener tokener = new JSONTokener(isr);
            JSONObject root = new JSONObject(tokener);

            // If it's a JsonArchiveWriter: descend into the container object.
            if (root.has(JsonCoder.TAG_APPLICATION_ROOT)) {
                root = root.getJSONObject(JsonCoder.TAG_APPLICATION_ROOT);
                // we should now have "data" and "info"
            }

            // If we have MetaData on the current level, descend into it
            if (root.has(RecordType.MetaData.getName())) {
                root = root.optJSONObject(RecordType.MetaData.getName());
            }

            // Sanity check before we try decoding
            if (root == null) {
                return Optional.empty();
            }

            // We should now be 'in' the MetaData object, but we need to do another
            // sanity check by explicitly checking for the INFO_ARCHIVER_VERSION field,
            // as we might be inside a generic json file instead and we do not
            // want the BundleCoder to crash!
            if (root.has(ArchiveMetaData.INFO_ARCHIVER_VERSION)) {
                final Bundle data = new BundleCoder(context).decode(root);
                if (data.isEmpty()) {
                    return Optional.empty();
                } else {
                    return Optional.of(new ArchiveMetaData(data));
                }
            } else {
                // This is a gamble...
                // Suppose the user took our standard zip archive, and extracted
                // the "books.json" file and then tries to import that file...
                // Then theoretically we *could* now be inside that file at a point
                // were we *could* find "books" at the current root level.
                if (root.has(RecordType.Books.getName())) {
                    // If we do, then we should be able to reconstruct the meta-data
                    // by simply counting the elements under "books"
                    final int nrOfBooks = root.getJSONArray(RecordType.Books.getName())
                                              .length();
                    if (nrOfBooks > 1) {
                        // The next gamble is that each element is formatted
                        // in the current archiver version format.
                        // If it's not... then the import will fail at a later stage...
                        final ArchiveMetaData metaData = new ArchiveMetaData(
                                ArchiveWriterAbstract.VERSION,
                                ServiceLocator.getInstance().newBundle());
                        metaData.setBookCount(nrOfBooks);
                        // and now let's hope for the best...
                        return Optional.of(metaData);
                    }
                }

                // We have an unknown file...
                return Optional.empty();
            }

        } catch (@NonNull final JSONException e) {
            throw new DataReaderException(e);
        }
    }

    @Override
    @NonNull
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ArchiveReaderRecord record,
                              @NonNull final ImportHelper helper,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException,
                   StorageException,
                   IOException {

        results = new ImportResults();

        if (record.getType().isPresent()) {
            final RecordType recordType = record.getType().get();

            final StylesHelper stylesHelper = ServiceLocator.getInstance().getStyles();
            final Style defaultStyle = stylesHelper.getDefault();

            try {
                // Don't close this stream
                final InputStream is = record.getInputStream();
                final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                final JSONTokener tokener = new JSONTokener(isr);

                // REMINDER: this can take some time when reading a large amount of book data.
                JSONObject root = new JSONObject(tokener);

                // Is this a JsonArchiveWriter format ?
                // Then descend to the container and into the data object.
                if (root.has(JsonCoder.TAG_APPLICATION_ROOT)) {
                    root = root.getJSONObject(JsonCoder.TAG_APPLICATION_ROOT)
                               .getJSONObject(RecordType.AutoDetect.getName());
                }

                if (allowedTypes.contains(recordType)
                    || recordType == RecordType.AutoDetect) {

                    if (recordType == RecordType.Styles
                        || recordType == RecordType.AutoDetect) {
                        readStyles(root, stylesHelper);
                    }

                    if (recordType == RecordType.Preferences
                        || recordType == RecordType.AutoDetect) {
                        readPreferences(context, root);
                    }

                    if (recordType == RecordType.Certificates
                        || recordType == RecordType.AutoDetect) {
                        readCertificates(context, root);
                    }

                    if (recordType == RecordType.Bookshelves
                        || recordType == RecordType.AutoDetect) {
                        readBookshelves(context, root, helper, defaultStyle);
                    }

                    if (recordType == RecordType.CalibreLibraries
                        || recordType == RecordType.AutoDetect) {
                        readCalibreLibraries(context, root, helper, defaultStyle);
                    }

                    if (recordType == RecordType.CalibreCustomFields
                        || recordType == RecordType.AutoDetect) {
                        readCalibreCustomFields(root, helper);
                    }

                    if (recordType == RecordType.DeletedBooks
                        || recordType == RecordType.AutoDetect) {
                        readDeletedBooks(root);
                    }

                    if (recordType == RecordType.Books
                        || recordType == RecordType.AutoDetect) {
                        readBooks(context, root, helper, defaultStyle,
                                  progressListener);
                    }
                }
            } catch (@NonNull final JSONException e) {
                // Unpack if possible
                if (e.getCause() instanceof DaoWriteException) {
                    throw new DataReaderException(context.getString(
                            R.string.error_import_failed_for_record, recordType.getName()),
                                                  e.getCause());
                }
                throw new DataReaderException(context.getString(
                        R.string.error_import_failed_for_record, recordType.getName()), e);

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
        return results;
    }

    private void readStyles(@NonNull final JSONObject root,
                            @NonNull final StylesHelper stylesHelper)
            throws JSONException {
        final JSONArray jsonRoot = root.optJSONArray(RecordType.Styles.getName());
        if (jsonRoot != null) {
            new StyleCoder()
                    .decode(jsonRoot)
                    .forEach(stylesHelper::updateOrInsert);
            results.styles = jsonRoot.length();
        }
    }

    private void readPreferences(@NonNull final Context context,
                                 @NonNull final JSONObject root)
            throws JSONException {
        final JSONObject jsonRoot = root.optJSONObject(RecordType.Preferences.getName());
        if (jsonRoot != null) {
            // The coder itself will set/update the values directly.
            new SharedPreferencesCoder(PreferenceManager.getDefaultSharedPreferences(context))
                    .decode(jsonRoot);
            // Migrate/remove any obsolete keys
            DBHelper.migratePreferenceKeys(context);
            results.preferences = 1;
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
                    results.certificates++;
                } catch (@NonNull final CertificateEncodingException e) {
                    // log but don't quit
                    LoggerFactory.getLogger().e(TAG, e);
                }
            }
        }
    }

    private void readBookshelves(@NonNull final Context context,
                                 @NonNull final JSONObject root,
                                 @NonNull final ImportHelper helper,
                                 @NonNull final Style defaultStyle)
            throws JSONException,
                   UncheckedDaoWriteException {
        final JSONArray jsonRoot = root.optJSONArray(RecordType.Bookshelves.getName());
        if (jsonRoot != null) {
            final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();

            new BookshelfCoder(context, defaultStyle)
                    .decode(jsonRoot)
                    .forEach(bookshelf -> {
                        bookshelfDao.fixId(bookshelf);
                        if (bookshelf.getId() > 0) {
                            // The shelf already exists
                            final DataReader.Updates updateOption = helper.getUpdateOption();
                            switch (updateOption) {
                                case Overwrite: {
                                    try {
                                        bookshelfDao.update(context, bookshelf);
                                    } catch (@NonNull final DaoWriteException e) {
                                        throw new UncheckedDaoWriteException(e);
                                    }
                                    break;
                                }
                                case OnlyNewer:
                                case Skip:
                                    break;
                            }
                        } else {
                            try {
                                bookshelfDao.insert(context, bookshelf);
                                results.bookshelves++;
                            } catch (@NonNull final DaoWriteException e) {
                                throw new UncheckedDaoWriteException(e);
                            }
                        }
                    });
        }
    }

    private void readCalibreLibraries(@NonNull final Context context,
                                      @NonNull final JSONObject root,
                                      @NonNull final ImportHelper helper,
                                      @NonNull final Style defaultStyle)
            throws JSONException {
        final JSONArray jsonRoot = root.optJSONArray(RecordType.CalibreLibraries.getName());
        if (jsonRoot != null) {
            final CalibreLibraryDao libraryDao = ServiceLocator.getInstance()
                                                               .getCalibreLibraryDao();

            new CalibreLibraryCoder(context, defaultStyle)
                    .decode(jsonRoot)
                    .forEach(library -> {
                        libraryDao.fixId(context, library);
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

    private void readCalibreCustomFields(@NonNull final JSONObject root,
                                         @NonNull final ImportHelper helper)
            throws JSONException {
        final JSONArray jsonRoot = root.optJSONArray(RecordType.CalibreCustomFields.getName());
        if (jsonRoot != null) {
            final CalibreCustomFieldDao dao =
                    ServiceLocator.getInstance().getCalibreCustomFieldDao();

            new CalibreCustomFieldCoder()
                    .decode(jsonRoot)
                    .forEach(calibreCustomField -> {
                        dao.fixId(calibreCustomField);
                        if (calibreCustomField.getId() > 0) {
                            // The field already exists
                            final DataReader.Updates updateOption = helper.getUpdateOption();
                            switch (updateOption) {
                                case Overwrite: {
                                    dao.update(calibreCustomField);
                                    break;
                                }
                                case OnlyNewer:
                                case Skip:
                                    break;
                            }
                        } else {
                            dao.insert(calibreCustomField);
                        }
                    });
        }
    }

    private void readDeletedBooks(@NonNull final JSONObject root) {
        final JSONArray jsonRoot = root.optJSONArray(RecordType.DeletedBooks.getName());
        if (jsonRoot != null) {
            final List<Pair<String, String>> list = new DeletedBooksCoder().decode(jsonRoot);
            if (!list.isEmpty()) {
                results.deletedBooks = deletedBooksDao.importRecords(list);
            }
        }
    }

    private void readBooks(@NonNull final Context context,
                           @NonNull final JSONObject root,
                           @NonNull final ImportHelper helper,
                           @NonNull final Style defaultStyle,
                           @NonNull final ProgressListener progressListener)
            throws StorageException,
                   JSONException {

        progressListener.publishProgress(0, context.getString(R.string.lbl_books));

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

        final JsonCoder<Book> bookCoder = new BookCoder(context, defaultStyle);

        for (int i = 0; i < books.length() && !progressListener.isCancelled(); i++) {

            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }
            try {
                final Book book = bookCoder.decode(books.getJSONObject(i));
                final String importUuid = book.getString(DBKey.BOOK_UUID, null);
                if (importUuid == null || importUuid.isEmpty()) {
                    throw new DaoWriteException(context.getString(
                            R.string.error_record_must_contain_column, DBKey.BOOK_UUID));
                }

                final long importNumericId = book.getLong(DBKey.PK_ID);
                book.remove(DBKey.PK_ID);

                importBookWithUuid(context, helper, book, importUuid, importNumericId);

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
            } catch (@NonNull final DaoWriteException | SQLiteDoneException e) {
                results.handleRowException(context, row, e, null);

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
                final String msg = String.format(progressMessage,
                                                 booksString,
                                                 results.booksCreated,
                                                 results.booksUpdated,
                                                 results.booksSkipped);
                progressListener.publishProgress(delta, msg);
                lastUpdateTime = now;
                delta = 0;
            }
        }
        // minus 1 to compensate for the last increment
        results.booksProcessed = row - 1;
    }
}
