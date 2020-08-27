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
package com.hardbacknutter.nevertoomanybooks.backup.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

public class DbArchiveReader
        implements ArchiveReader {

    private static final String TAG = "DbArchiveReader";

    /** Only send progress updates every 200ms. */
    private static final int PROGRESS_UPDATE_INTERVAL = 200;

    private static final String SQL_LIST_TABLES =
            "SELECT tbl_name FROM sqlite_master WHERE type='table'";
    private static final String SQL_CALIBRE_SELECT_BOOKS =
            "SELECT books.id AS id,"
            + " books.uuid AS uuid,"
            + " identifiers.val AS isbn,"
            + " languages.lang_code AS lang,"
            + " books.last_modified AS last_modified,"
            + " books.series_index AS nr_in_series,"
            + " series.name AS series_title,"
            + " books.title AS book_title,"
            + " comments.text AS description"
            + " FROM books"
            + " LEFT JOIN identifiers ON books.id=identifiers.book AND identifiers.type='isbn'"
            + " LEFT JOIN comments ON books.id = comments.book"

            + " LEFT JOIN books_series_link ON books.id=books_series_link.book"
            + " LEFT JOIN series ON books_series_link.series=series.id"

            + " LEFT JOIN books_languages_link ON books.id = books_languages_link.book"
            + " LEFT JOIN languages ON books_languages_link.lang_code = languages.id";
    private static final String SQL_CALIBRE_SELECT_PUBLISHERS =
            " SELECT publishers.name FROM books_publishers_link JOIN publishers "
            + "ON books_publishers_link.publisher = publishers.id"
            + " WHERE books_publishers_link.book=?";
    private static final String SQL_CALIBRE_SELECT_AUTHORS =
            "  SELECT authors.name FROM books_authors_link JOIN authors"
            + " ON books_authors_link.author = authors.id WHERE books_authors_link.book=?";

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    @NonNull
    private final ImportManager mHelper;

    private final ImportResults mResults = new ImportResults();

    private final boolean mForceUpdate;

    /** cached localized "Books" string. */
    @NonNull
    private final String mBooksString;
    @NonNull
    private final String mEBookString;
    @NonNull
    private final String mProgressMessage;


    public DbArchiveReader(@NonNull final Context context,
                           @NonNull final ImportManager helper) {
        mHelper = helper;
        mDb = new DAO(TAG);

        mForceUpdate = (mHelper.getOptions() & ImportManager.IMPORT_ONLY_NEW_OR_UPDATED) == 0;

        mEBookString = context.getString(R.string.book_format_ebook);
        mBooksString = context.getString(R.string.lbl_books);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
    }

    @Override
    public void close() {
        mDb.purge();
        mDb.close();
    }

    @NonNull
    @Override
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws IOException, InvalidArchiveException {

        @Nullable
        final InputStream is = context.getContentResolver().openInputStream(mHelper.getUri());
        if (is == null) {
            // openInputStream can return null, just pretend we couldn't find the file.
            // Should never happen - flw
            throw new FileNotFoundException(mHelper.getUri().toString());
        }

        // First step is to copy the file from the uri to a place where
        // we can access it as a database.
        // Second step is to recognize which database we're handling,
        // and then hand it over to one of the database-specific handler.
        final String tmpFilename = System.currentTimeMillis() + ".db";
        File tmpDb = null;
        try {
            tmpDb = AppDir.Cache.getFile(context, tmpFilename);
            tmpDb = FileUtils.copyInputStream(context, is, tmpDb);
            if (tmpDb != null) {
                try (SQLiteDatabase sqLiteDatabase =
                             SQLiteDatabase.openDatabase(tmpDb.getAbsolutePath(), null,
                                                         SQLiteDatabase.OPEN_READONLY)) {

                    // checking 6 tables should be sufficient (and likely excessive)
                    int calibreTables = 6;
                    try (Cursor cursor = sqLiteDatabase.rawQuery(SQL_LIST_TABLES, null)) {
                        while (cursor.moveToNext()) {
                            final String tableName = cursor.getString(0);
                            if ("library_id".equals(tableName)
                                || "books".equals(tableName)
                                || "authors".equals(tableName)
                                || "books_authors_link".equals(tableName)
                                || "series".equals(tableName)
                                || "books_series_link".equals(tableName)) {
                                calibreTables--;
                            }
                        }
                    }

                    // we think we recognised a Calibre database.
                    if (calibreTables == 0) {
                        // Older versions might work, but no testing was done so we reject them.
                        if (sqLiteDatabase.getVersion() < 23) {
                            throw new InvalidArchiveException(ErrorMsg.IMPORT_NOT_SUPPORTED);
                        }
                        return readCalibre(context, sqLiteDatabase, progressListener);
                    }
                }

                // unknown database
                throw new InvalidArchiveException(ErrorMsg.IMPORT_NOT_SUPPORTED);
            }

        } finally {
            // no need to keep the import database file.
            FileUtils.delete(tmpDb);
            is.close();
        }

        return new ImportResults();
    }

    @NonNull
    private ImportResults readCalibre(@NonNull final Context context,
                                      @NonNull final SQLiteDatabase calibreDb,
                                      @NonNull final ProgressListener progressListener) {
        int colId = -2;
        int colUuid = -2;
        int colIsbn = -2;
        int colLang = -2;
        int colTitle = -2;
        int colDescription = -2;
        int colLastModified = -2;
        int colSeriesIndex = -2;
        int colSeriesName = -2;

        final Book book = new Book();
        final ArrayList<Author> mAuthors = new ArrayList<>();
        final ArrayList<Series> mSeries = new ArrayList<>();
        final ArrayList<Publisher> mPublishers = new ArrayList<>();

        int txRowCount = 0;
        long lastUpdate = 0;
        // we only update progress every PROGRESS_UPDATE_INTERVAL ms.
        // Count the nr of books in between.
        int delta = 0;

        Synchronizer.SyncLock txLock = null;

        try (Cursor source = calibreDb.rawQuery(SQL_CALIBRE_SELECT_BOOKS, null)) {
            final int count = source.getCount();
            // not perfect, but good enough
            if (count > 0 && progressListener.getProgressMaxPos() < count) {
                progressListener.setProgressMaxPos(count);
            }

            while (source.moveToNext() && !progressListener.isCancelled()) {
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

                if (colId == -2) {
                    colId = source.getColumnIndex("id");
                    colUuid = source.getColumnIndex("uuid");
                    colIsbn = source.getColumnIndex("isbn");
                    colLang = source.getColumnIndex("lang");
                    colTitle = source.getColumnIndex("book_title");
                    colDescription = source.getColumnIndex("description");
                    colLastModified = source.getColumnIndex("last_modified");
                    colSeriesIndex = source.getColumnIndex("nr_in_series");
                    colSeriesName = source.getColumnIndex("series_title");
                }
                book.clear();
                mAuthors.clear();
                mSeries.clear();
                mPublishers.clear();

                // We're not keeping the Calibre id other then for lookups during import
                final String calibreId = source.getString(colId);
                // keep the Calibre UUID
                final String calibreUuid = source.getString(colUuid);
                book.put(DBDefinitions.KEY_EID_CALIBRE_UUID, calibreUuid);

                book.put(DBDefinitions.KEY_ISBN, source.getString(colIsbn));
                book.put(DBDefinitions.KEY_TITLE, source.getString(colTitle));
                book.put(DBDefinitions.KEY_LANGUAGE, source.getString(colLang));
                book.put(DBDefinitions.KEY_DESCRIPTION, source.getString(colDescription));
                book.put(DBDefinitions.KEY_UTC_LAST_UPDATED, source.getString(colLastModified));

                if (!source.isNull(colSeriesName)) {
                    String seriesNr = source.getString(colSeriesIndex);
                    String seriesName = source.getString(colSeriesName);
                    Series series = Series.from(seriesName);
                    if (!seriesNr.isEmpty() && !"0.0".equals(seriesNr)) {
                        series.setNumber(seriesNr);
                    }
                    mSeries.add(series);
                }

                try (Cursor authCursor = calibreDb.rawQuery(SQL_CALIBRE_SELECT_AUTHORS,
                                                            new String[]{calibreId})) {
                    while (authCursor.moveToNext()) {
                        String name = authCursor.getString(0);
                        mAuthors.add(Author.from(name));
                    }
                }

                try (Cursor pubCursor = calibreDb.rawQuery(SQL_CALIBRE_SELECT_PUBLISHERS,
                                                           new String[]{calibreId})) {
                    while (pubCursor.moveToNext()) {
                        String name = pubCursor.getString(0);
                        mPublishers.add(Publisher.from(name));
                    }
                }

                if (!mAuthors.isEmpty()) {
                    book.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, mAuthors);
                }
                if (!mSeries.isEmpty()) {
                    book.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, mSeries);
                }
                if (!mPublishers.isEmpty()) {
                    book.putParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY, mPublishers);
                }

                book.put(DBDefinitions.KEY_FORMAT, mEBookString);

                try {
                    // check if the book exists in our database, and fetch it's id.
                    long databaseBookId = mDb.getBookIdFromKey(DBDefinitions.KEY_EID_CALIBRE_UUID,
                                                               calibreUuid);

                    //    if (databaseBookId == 0) {
                    //        // try to find it using the ISBN
                    //        final String isbn = book.getString(DBDefinitions.KEY_ISBN);
                    //        if (!isbn.isEmpty()) {
                    //            databaseBookId = mDb.getBookIdFromIsbn(new ISBN(isbn));
                    //        }
                    //    }

                    if (databaseBookId > 0) {
                        // The book exists in our database

                        // Explicitly set the EXISTING id on the book
                        book.putLong(DBDefinitions.KEY_PK_ID, databaseBookId);

                        // and UPDATE the existing book (if allowed)
                        if (mForceUpdate || isImportNewer(context, mDb, book, databaseBookId)) {
                            mDb.update(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION
                                                      | DAO.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                            mResults.booksUpdated++;
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                                Log.d(TAG, "calibreUuid=" + calibreUuid
                                           + "|databaseBookId=" + databaseBookId
                                           + "|update|" + book.getTitle());
                            }
                        } else {
                            mResults.booksSkipped++;
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                                Log.d(TAG, "calibreUuid=" + calibreUuid
                                           + "|databaseBookId=" + databaseBookId
                                           + "|skipped|" + book.getTitle());
                            }
                        }

                    } else {
                        // The book does NOT exist in our database
                        long insId = mDb.insert(context, book, DAO.BOOK_FLAG_IS_BATCH_OPERATION);
                        mResults.booksCreated++;
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                            Log.d(TAG, "calibreUuid=" + calibreUuid
                                       + "|insert=" + insId
                                       + "|" + book.getTitle());
                        }
                    }
                } catch (@NonNull final DAO.DaoWriteException
                        | SQLiteDoneException
                        | IndexOutOfBoundsException e) {
                    mResults.booksSkipped++;
                }

                // limit the amount of progress updates, otherwise this will cause a slowdown.
                final long now = System.currentTimeMillis();
                if ((now - lastUpdate) > PROGRESS_UPDATE_INTERVAL
                    && !progressListener.isCancelled()) {
                    final String msg = String.format(mProgressMessage,
                                                     mBooksString,
                                                     mResults.booksCreated,
                                                     mResults.booksUpdated,
                                                     mResults.booksSkipped);
                    progressListener.publishProgressStep(delta, msg);
                    delta = 0;
                    lastUpdate = now;
                }
                delta++;
            }
        } finally {
            if (mDb.inTransaction()) {
                mDb.setTransactionSuccessful();
                mDb.endTransaction(txLock);
            }
        }

        return mResults;
    }

    /**
     * Check if the incoming book is newer then the stored book data.
     *
     * @param context Current context
     * @param db      Database Access
     * @param book    the book we're updating
     * @param bookId  the book id to lookup in our database
     */
    private boolean isImportNewer(@NonNull final Context context,
                                  @NonNull final DAO db,
                                  @SuppressWarnings("TypeMayBeWeakened")
                                  @NonNull final Book book,
                                  final long bookId) {
        final LocalDateTime utcImportDate =
                DateParser.getInstance(context)
                          .parseISO(book.getString(DBDefinitions.KEY_UTC_LAST_UPDATED));
        if (utcImportDate == null) {
            return false;
        }

        final LocalDateTime utcLastUpdated = db.getBookLastUpdateUtcDate(context, bookId);

        return utcLastUpdated == null || utcImportDate.isAfter(utcLastUpdated);
    }
}
