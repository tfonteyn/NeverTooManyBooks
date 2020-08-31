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
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.DateParser;

class CalibreArchiveReader
        implements ArchiveReader {

    /** Log tag. */
    private static final String TAG = "CalibreArchiveReader";

    /** Only send progress updates every 200ms. */
    private static final int PROGRESS_UPDATE_INTERVAL = 200;

    /**
     * The main fields from the books table.
     * <p>
     * Note we're not taking "books.pubdate"; most metadata downloaded by Calibre contains
     * bad/incorrect dates (at least the ones we've seen)
     * <p>
     * Not copying "ratings" either. The source is unclear.
     * <p>
     * ENHANCE: tags... this would require implementing a full tag system in our own database.
     * ENHANCE: custom fields ? Maybe detect/guess fields like "read" etc ?
     */
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

        mForceUpdate = (helper.getOptions() & ImportManager.IMPORT_ONLY_NEW_OR_UPDATED) == 0;

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

        int colId = -2;
        int colUuid = -2;
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

        // The default Bookshelf to which all books will be added.
        final Bookshelf bookshelf = Bookshelf.getBookshelf(context, mDb, Bookshelf.PREFERRED,
                                                           Bookshelf.DEFAULT);

        int txRowCount = 0;
        long lastUpdate = 0;
        // we only update progress every PROGRESS_UPDATE_INTERVAL ms.
        // Count the nr of books in between.
        int delta = 0;

        Synchronizer.SyncLock txLock = null;

        try (Cursor source = mCalibreDb.rawQuery(SQL_SELECT_BOOKS, null)) {
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
                book.putString(DBDefinitions.KEY_EID_CALIBRE_UUID, calibreUuid);

                book.putString(DBDefinitions.KEY_TITLE, source.getString(colTitle));
                book.putString(DBDefinitions.KEY_LANGUAGE, source.getString(colLang));
                book.putString(DBDefinitions.KEY_DESCRIPTION, source.getString(colDescription));
                book.putString(DBDefinitions.KEY_UTC_LAST_UPDATED,
                               source.getString(colLastModified));

                if (!source.isNull(colSeriesName)) {
                    final String seriesNr = source.getString(colSeriesIndex);
                    final String seriesName = source.getString(colSeriesName);
                    final Series series = Series.from(seriesName);
                    if (!seriesNr.isEmpty() && !"0.0".equals(seriesNr)) {
                        series.setNumber(seriesNr);
                    }
                    mSeries.add(series);
                }

                final String[] calibreParam = new String[]{calibreId};

                try (Cursor cursor = mCalibreDb.rawQuery(SQL_SELECT_AUTHORS, calibreParam)) {
                    while (cursor.moveToNext()) {
                        final String name = cursor.getString(0);
                        mAuthors.add(Author.from(name));
                    }
                }

                try (Cursor cursor = mCalibreDb.rawQuery(SQL_SELECT_PUBLISHERS, calibreParam)) {
                    while (cursor.moveToNext()) {
                        final String name = cursor.getString(0);
                        mPublishers.add(Publisher.from(name));
                    }
                }

                try (Cursor cursor = mCalibreDb.rawQuery(SQL_SELECT_IDENTIFIERS, calibreParam)) {
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

                if (!mAuthors.isEmpty()) {
                    book.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, mAuthors);
                }
                if (!mSeries.isEmpty()) {
                    book.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, mSeries);
                }
                if (!mPublishers.isEmpty()) {
                    book.putParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY, mPublishers);
                }

                book.putString(DBDefinitions.KEY_FORMAT, mEBookString);

                book.getParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY).add(bookshelf);

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

                // limit the amount of progress updates, otherwise this will cause a slowdown.
                final long now = System.currentTimeMillis();
                if ((now - lastUpdate) > PROGRESS_UPDATE_INTERVAL
                    && !progressListener.isCancelled()) {
                    final String msg = String.format(mProgressMessage,
                                                     mBooksString,
                                                     importResults.booksCreated,
                                                     importResults.booksUpdated,
                                                     importResults.booksSkipped);
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

        return importResults;
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

    @Override
    public void close() {
        mDb.purge();
        mDb.close();
    }
}
