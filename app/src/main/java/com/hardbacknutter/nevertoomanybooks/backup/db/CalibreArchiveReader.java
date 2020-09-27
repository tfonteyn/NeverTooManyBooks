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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

/**
 * Copies the standard fields (book, author, publisher, series).
 * Supports custom columns:
 * <ul>
 *     <li>read (boolean)</li>
 *     <li>read_start (datetime)</li>
 *     <li>read_end (datetime)</li>
 *     <li>date_read (datetime) -> read_end</li>
 * </ul>
 *
 * <p>
 * Note we're not taking "books.pubdate"; most metadata downloaded by Calibre contains
 * bad/incorrect dates (at least the ones we've seen)
 * <p>
 * Not copying "ratings" either. The source is rather unclear. Amazon or Goodreads? or?
 * <p>
 * ENHANCE: tags... this would require implementing a full tag system in our own database.
 */
class CalibreArchiveReader
        implements ArchiveReader {

    /** Log tag. */
    private static final String TAG = "CalibreArchiveReader";

    /** The main fields from the books table. */
    private static final String SQL_SELECT_BOOKS =
            "SELECT books.id AS id,"
            + " books.uuid AS uuid,"
            + " languages.lang_code AS lang,"
            + " books.last_modified AS last_modified,"
            + " books.series_index AS nr_in_series,"
            + " series.name AS series_title,"
            + " books.title AS book_title,"
            + " comments.text AS description"
            + " FROM books"
            + " LEFT JOIN comments ON books.id = comments.book"

            + " LEFT JOIN books_series_link ON books.id=books_series_link.book"
            + " LEFT JOIN series ON books_series_link.series=series.id"

            + " LEFT JOIN books_languages_link ON books.id = books_languages_link.book"
            + " LEFT JOIN languages ON books_languages_link.lang_code = languages.id";

    /** Read the authors for the given book id. */
    private static final String SQL_SELECT_AUTHORS =
            "SELECT authors.name FROM books_authors_link JOIN authors"
            + " ON books_authors_link.author = authors.id"
            + " WHERE books_authors_link.book=?";

    /** Read the publishers for the given book id. */
    private static final String SQL_SELECT_PUBLISHERS =
            "SELECT publishers.name FROM books_publishers_link JOIN publishers "
            + "ON books_publishers_link.publisher = publishers.id"
            + " WHERE books_publishers_link.book=?";

    /** Read the external identifiers for the given book id. */
    private static final String SQL_SELECT_IDENTIFIERS =
            "SELECT identifiers.type, identifiers.val FROM identifiers "
            + " WHERE identifiers.book=?";

    /** Read all custom column definitions. */
    private static final String SQL_SELECT_CUSTOM_COLUMNS =
            "SELECT id, label, datatype FROM custom_columns";

    /** Read a single custom column. The index must be string-substituted. */
    private static final String SQL_SELECT_CUSTOM_COLUMN_X =
            "SELECT value FROM custom_column_%s WHERE book=?";

    private static final String CALIBRE_BOOL = "bool";
    private static final String CALIBRE_DATETIME = "datetime";
    private static final String CALIBRE_COMMENTS = "comments";
    private static final String CALIBRE_TEXT = "text";

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    private final SQLiteDatabase mCalibreDb;

    /** cached localized "Books" string. */
    @NonNull
    private final String mBooksString;
    @NonNull
    private final String mEBookString;
    @NonNull
    private final String mProgressMessage;

    private final boolean mForceUpdate;

    private Collection<CustomColumn> mCustomColumns;

    /**
     * Constructor.
     *
     * @param context   Current context
     * @param helper    import configuration
     * @param calibreDb <strong>OPEN</strong> (read-only) database
     */
    CalibreArchiveReader(@NonNull final Context context,
                         @NonNull final ImportManager helper,
                         @NonNull final SQLiteDatabase calibreDb) {
        mCalibreDb = calibreDb;
        mDb = new DAO(TAG);

        mForceUpdate = (helper.getOptions() & Options.IS_SYNC) == 0;

        mEBookString = context.getString(R.string.book_format_ebook);
        mBooksString = context.getString(R.string.lbl_books);
        mProgressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
    }

    @Override
    public void validate(@NonNull final Context context)
            throws InvalidArchiveException {
        // Older versions might work, but no testing was done so we reject them.
        if (mCalibreDb.getVersion() < 23) {
            throw new InvalidArchiveException(ErrorMsg.IMPORT_NOT_SUPPORTED);
        }
    }

    @NonNull
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener) {

        final ImportResults importResults = new ImportResults();
        final Book book = new Book();

        // The default Bookshelf to which all books will be added.
        final Bookshelf bookshelf = Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED,
                                                           Bookshelf.DEFAULT);

        int colId = -2;
        int colUuid = -2;
        int colLang = -2;
        int colTitle = -2;
        int colDescription = -2;
        int colLastModified = -2;
        int colSeriesIndex = -2;
        int colSeriesName = -2;

        int txRowCount = 0;
        long lastUpdate = 0;
        // Count the nr of books in between progress updates.
        int delta = 0;

        mCustomColumns = readCustomColumns();

        Synchronizer.SyncLock txLock = null;

        try (Cursor source = mCalibreDb.rawQuery(SQL_SELECT_BOOKS, null)) {
            final int count = source.getCount();
            // not perfect, but good enough
            if (count > 0 && progressListener.getMaxPos() < count) {
                progressListener.setMaxPos(count);
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
                    colLang = source.getColumnIndex("lang");
                    colTitle = source.getColumnIndex("book_title");
                    colDescription = source.getColumnIndex("description");
                    colLastModified = source.getColumnIndex("last_modified");
                    colSeriesIndex = source.getColumnIndex("nr_in_series");
                    colSeriesName = source.getColumnIndex("series_title");
                }

                book.clear();

                // We're not keeping the Calibre id other than for lookups during import
                final int calibreId = source.getInt(colId);
                // keep the Calibre UUID
                final String calibreUuid = source.getString(colUuid);
                book.putString(DBDefinitions.KEY_EID_CALIBRE_UUID, calibreUuid);

                book.putString(DBDefinitions.KEY_TITLE, source.getString(colTitle));
                book.putString(DBDefinitions.KEY_LANGUAGE, source.getString(colLang));
                book.putString(DBDefinitions.KEY_DESCRIPTION, source.getString(colDescription));
                book.putString(DBDefinitions.KEY_UTC_LAST_UPDATED,
                               source.getString(colLastModified));

                // it's an eBook - duh!
                book.putString(DBDefinitions.KEY_FORMAT, mEBookString);
                // assign to current shelf.
                book.getParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY).add(bookshelf);

                // There is a "books_series_link" table which indicates you could have a book
                // belong to multiple series, BUT the Calibre UI does not support this and
                // the book number in the series is stored in the books table.
                // (2020-09-01) hence (for now)... we only read a single Series.
                if (!source.isNull(colSeriesName)) {
                    final String seriesNr = source.getString(colSeriesIndex);
                    final String seriesName = source.getString(colSeriesName);
                    final Series series = Series.from(seriesName);
                    if (!seriesNr.isEmpty() && !"0.0".equals(seriesNr)) {
                        series.setNumber(seriesNr);
                    }
                    final ArrayList<Series> seriesList = new ArrayList<>();
                    seriesList.add(series);
                    book.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, seriesList);
                }

                handleAuthor(book, calibreId);
                handlePublishers(book, calibreId);
                handleIdentifiers(book, calibreId);
                handleCustomColumns(book, calibreId);

                // process the book
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
                            importResults.booksUpdated++;
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                                Log.d(TAG, "calibreUuid=" + calibreUuid
                                           + "|databaseBookId=" + databaseBookId
                                           + "|update|" + book.getTitle());
                            }
                        } else {
                            importResults.booksSkipped++;
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                                Log.d(TAG, "calibreUuid=" + calibreUuid
                                           + "|databaseBookId=" + databaseBookId
                                           + "|skipped|" + book.getTitle());
                            }
                        }

                    } else {
                        // The book does NOT exist in our database
                        final long insId = mDb.insert(context, book,
                                                      DAO.BOOK_FLAG_IS_BATCH_OPERATION);
                        importResults.booksCreated++;
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CALIBRE_BOOKS) {
                            Log.d(TAG, "calibreUuid=" + calibreUuid
                                       + "|insert=" + insId
                                       + "|" + book.getTitle());
                        }
                    }
                } catch (@NonNull final DAO.DaoWriteException
                        | SQLiteDoneException
                        | IndexOutOfBoundsException e) {
                    importResults.booksSkipped++;
                }

                final long now = System.currentTimeMillis();
                if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()
                    && !progressListener.isCancelled()) {
                    final String msg = String.format(mProgressMessage,
                                                     mBooksString,
                                                     importResults.booksCreated,
                                                     importResults.booksUpdated,
                                                     importResults.booksSkipped);
                    progressListener.publishProgressStep(delta, msg);
                    lastUpdate = now;
                    delta = 0;
                }
                delta++;
            }
        } finally {
            if (mDb.inTransaction()) {
                mDb.setTransactionSuccessful();
                mDb.endTransaction(txLock);
            }
        }

        return importResults;
    }

    private void handleAuthor(@NonNull final Book book,
                              final int calibreId) {
        final ArrayList<Author> mAuthors = new ArrayList<>();
        try (Cursor cursor = mCalibreDb.rawQuery(SQL_SELECT_AUTHORS,
                                                 new String[]{String.valueOf(calibreId)})) {
            while (cursor.moveToNext()) {
                final String name = cursor.getString(0);
                mAuthors.add(Author.from(name));
            }
        }
        if (!mAuthors.isEmpty()) {
            book.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, mAuthors);
        }
    }

    private void handlePublishers(@NonNull final Book book,
                                  final int calibreId) {
        final ArrayList<Publisher> mPublishers = new ArrayList<>();
        try (Cursor cursor = mCalibreDb.rawQuery(SQL_SELECT_PUBLISHERS,
                                                 new String[]{String.valueOf(calibreId)})) {
            while (cursor.moveToNext()) {
                final String name = cursor.getString(0);
                mPublishers.add(Publisher.from(name));
            }
        }
        if (!mPublishers.isEmpty()) {
            book.putParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY, mPublishers);
        }
    }

    private void handleIdentifiers(@NonNull final Book book,
                                   final int calibreId) {
        try (Cursor cursor = mCalibreDb.rawQuery(SQL_SELECT_IDENTIFIERS,
                                                 new String[]{String.valueOf(calibreId)})) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                if (name != null) {
                    name = name.trim();
                    switch (name) {
                        case "isbn":
                            book.putString(DBDefinitions.KEY_ISBN,
                                           cursor.getString(1));
                            break;
                        case "amazon":
                            book.putString(DBDefinitions.KEY_EID_ASIN,
                                           cursor.getString(1));
                            break;
                        case "goodreads":
                            book.putLong(DBDefinitions.KEY_EID_GOODREADS_BOOK,
                                         cursor.getLong(1));
                            break;
                        case "google":
                            book.putString(DBDefinitions.KEY_EID_GOOGLE,
                                           cursor.getString(1));
                            break;

                        // There are many more (free-form) ...
                        default:
                            // Other than strict "amazon", there are variants
                            // for local sites; e.g. "amazon_nl", "amazon_fr",...
                            if (name.startsWith("amazon")) {
                                book.putString(DBDefinitions.KEY_EID_ASIN,
                                               cursor.getString(1));
                            }
                            break;
                    }
                }
            }
        }
    }

    private void handleCustomColumns(@NonNull final Book book,
                                     final int calibreId) {
        for (CustomColumn cc : mCustomColumns) {
            switch (cc.label) {
                case DBDefinitions.KEY_READ:
                    customBoolean(cc.id, calibreId).ifPresent(
                            read -> book.putBoolean(DBDefinitions.KEY_READ, read));
                    break;

                case DBDefinitions.KEY_READ_START:
                    customString(cc.id, calibreId).ifPresent(
                            date -> book.putString(DBDefinitions.KEY_READ_START, date));
                    break;

                case DBDefinitions.KEY_READ_END:
                case "date_read":
                    customString(cc.id, calibreId).ifPresent(
                            date -> book.putString(DBDefinitions.KEY_READ_END, date));
                    break;

                case DBDefinitions.KEY_PRIVATE_NOTES:
                    customString(cc.id, calibreId).ifPresent(
                            text -> book.putString(DBDefinitions.KEY_PRIVATE_NOTES, text));
                    break;

                default:
                    break;
            }
        }
    }

    @NonNull
    private Collection<CustomColumn> readCustomColumns() {
        final Collection<CustomColumn> customColumns = new ArrayList<>();
        try (Cursor cursor = mCalibreDb.rawQuery(SQL_SELECT_CUSTOM_COLUMNS, null)) {
            int id;
            String label;
            // The datatype as used in Calibre.
            String datatype;
            while (cursor.moveToNext()) {
                id = cursor.getInt(0);
                label = cursor.getString(1);
                datatype = cursor.getString(2);

                final CustomColumn cc = new CustomColumn(id, label);
                switch (cc.label) {
                    case DBDefinitions.KEY_READ:
                        if (CALIBRE_BOOL.equals(datatype)) {
                            customColumns.add(cc);
                        }
                        break;

                    case DBDefinitions.KEY_READ_START:
                    case DBDefinitions.KEY_READ_END:
                    case "date_read":
                        if (CALIBRE_DATETIME.equals(datatype)) {
                            customColumns.add(cc);
                        }
                        break;

                    case DBDefinitions.KEY_PRIVATE_NOTES:
                        if (CALIBRE_COMMENTS.equals(datatype)
                            || CALIBRE_TEXT.equals(datatype)) {
                            customColumns.add(cc);
                        }
                        break;
                    default:
                        // skip others
                        break;
                }
            }
        } catch (@NonNull final RuntimeException ignore) {
            // ignore, maybe the table is not there
        }

        return customColumns;
    }

    @NonNull
    private Optional<String> customString(final int customColumnId,
                                          final int calibreId) {
        try (Cursor cursor = mCalibreDb.rawQuery(
                String.format(SQL_SELECT_CUSTOM_COLUMN_X, customColumnId),
                new String[]{String.valueOf(calibreId)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(cursor.getString(0));
            }
        }
        return Optional.empty();
    }

    @NonNull
    private Optional<Boolean> customBoolean(final int customColumnId,
                                            final int calibreId) {
        try (Cursor cursor = mCalibreDb.rawQuery(
                String.format(SQL_SELECT_CUSTOM_COLUMN_X, customColumnId),
                new String[]{String.valueOf(calibreId)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(cursor.getInt(0) == 1);
            }
        }
        return Optional.empty();
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

    @Override
    public void close() {
        mDb.purge();
        mDb.close();
    }

    /** Value class. */
    private static class CustomColumn {

        final int id;
        final String label;

        CustomColumn(final int id,
                     final String label) {
            this.id = id;
            this.label = label;
        }
    }
}
