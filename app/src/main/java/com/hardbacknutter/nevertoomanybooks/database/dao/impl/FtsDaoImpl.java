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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.AUTHOR_GIVEN_NAMES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_PUBLISHER_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOK_TOC_ENTRY_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.DESCRIPTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FTS_AUTHOR_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FTS_BOOK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FTS_TOC_ENTRY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PERSONAL_NOTES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.SERIES_BOOK_NUMBER;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.TITLE;

public class FtsDaoImpl
        extends BaseDaoImpl
        implements FtsDao {

    /** Log tag. */
    private static final String TAG = "FtsDaoImpl";

    /** the body of an INSERT INTO [table]. Used more than once. */
    private static final String INSERT_BODY =
            " (" + TITLE
            + ',' + FTS_AUTHOR_NAME
            + ',' + SERIES_TITLE
            + ',' + DESCRIPTION
            + ',' + PERSONAL_NOTES
            + ',' + PUBLISHER_NAME
            + ',' + GENRE
            + ',' + LOCATION
            + ',' + BOOK_ISBN
            + ',' + FTS_TOC_ENTRY_TITLE

            + ',' + FTS_BOOK_ID
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)";

    /**
     * The full UPDATE statement.
     * The parameter order MUST match the order expected in INSERT.
     */
    private static final String UPDATE =
            UPDATE_ + TBL_FTS_BOOKS.getName()
            + _SET_ + TITLE + "=?"
            + ',' + FTS_AUTHOR_NAME + "=?"
            + ',' + SERIES_TITLE + "=?"
            + ',' + DESCRIPTION + "=?"
            + ',' + PERSONAL_NOTES + "=?"
            + ',' + PUBLISHER_NAME + "=?"
            + ',' + GENRE + "=?"
            + ',' + LOCATION + "=?"
            + ',' + BOOK_ISBN + "=?"
            + ',' + FTS_TOC_ENTRY_TITLE + "=?"

            + _WHERE_ + FTS_BOOK_ID + "=?";

    /**
     * The full INSERT statement.
     * The parameter order MUST match the order expected in UPDATE.
     */
    private static final String INSERT =
            INSERT_INTO_ + TBL_FTS_BOOKS.getName() + INSERT_BODY;


    /** Used during a full FTS rebuild. Minimal column list. */
    private static final String ALL_BOOKS =
            SELECT_ + PK_ID
            + ',' + TITLE
            + ',' + DESCRIPTION
            + ',' + PERSONAL_NOTES
            + ',' + GENRE
            + ',' + LOCATION
            + ',' + BOOK_ISBN
            + _FROM_ + TBL_BOOKS.getName();

    /** Used during insert of a book. Minimal column list. */
    private static final String BOOK_BY_ID = ALL_BOOKS + _WHERE_ + PK_ID + "=?";

    /** Used during insert of a book. Minimal column list. Ordered by position. */
    private static final String GET_AUTHORS_BY_BOOK_ID =
            SELECT_ + TBL_AUTHORS.dotAs(AUTHOR_FAMILY_NAME, AUTHOR_GIVEN_NAMES)
            + _FROM_ + TBL_BOOK_AUTHOR.startJoin(TBL_AUTHORS)
            + _WHERE_ + TBL_BOOK_AUTHOR.dot(FK_BOOK) + "=?"
            + _ORDER_BY_ + TBL_BOOK_AUTHOR.dot(BOOK_AUTHOR_POSITION);

    /** Used during insert of a book. Minimal column list. Ordered by position. */
    private static final String GET_PUBLISHERS_BY_BOOK_ID =
            SELECT_ + TBL_PUBLISHERS.dotAs(PUBLISHER_NAME)
            + _FROM_ + TBL_BOOK_PUBLISHER.startJoin(TBL_PUBLISHERS)
            + _WHERE_ + TBL_BOOK_PUBLISHER.dot(FK_BOOK) + "=?"
            + _ORDER_BY_ + TBL_BOOK_PUBLISHER.dot(BOOK_PUBLISHER_POSITION);

    /** Used during insert of a book. Minimal column list. Ordered by position. */
    private static final String GET_TOC_TITLES_BY_BOOK_ID =
            SELECT_ + TBL_TOC_ENTRIES.dotAs(TITLE)
            + _FROM_ + TBL_TOC_ENTRIES.startJoin(TBL_BOOK_TOC_ENTRIES)
            + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(FK_BOOK) + "=?"
            + _ORDER_BY_ + TBL_BOOK_TOC_ENTRIES.dot(BOOK_TOC_ENTRY_POSITION);

    /** Used during insert of a book. Minimal column list. Ordered by position. */
    private static final String GET_SERIES_BY_BOOK_ID =
            SELECT_ + TBL_SERIES.dot(SERIES_TITLE) + "||' '||"
            + " COALESCE(" + TBL_BOOK_SERIES.dot(SERIES_BOOK_NUMBER) + ",'')"
            + _AS_ + SERIES_TITLE
            + _FROM_ + TBL_BOOK_SERIES.startJoin(TBL_SERIES)
            + _WHERE_ + TBL_BOOK_SERIES.dot(FK_BOOK) + "=?"
            + _ORDER_BY_ + TBL_BOOK_SERIES.dot(BOOK_SERIES_POSITION);

    /** Advanced Local-search. */
    private static final String SEARCH =
            // FTS_BOOK_ID is the _id into the books table.
            SELECT_ + DBKey.FTS_BOOK_ID
            + _FROM_ + TBL_FTS_BOOKS.getName()
            + _WHERE_ + TBL_FTS_BOOKS.getName()
            + " MATCH ? LIMIT ?";
    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;
    /** log error string. */
    private static final String ERROR_FAILED_TO_UPDATE_FTS = "Failed to update FTS";

    /**
     * Constructor.
     */
    public FtsDaoImpl() {
        super(TAG);
    }

    /**
     * Bind a string or {@code null} value to a parameter since binding a {@code null}
     * in bindString produces an error.
     * <p>
     * <strong>Note:</strong> We specifically want to use the default Locale for this.
     */
    private static void bindStringOrNull(@NonNull final SynchronizedStatement stmt,
                                         final int position,
                                         @Nullable final CharSequence text) {
        if (text == null) {
            stmt.bindNull(position);
        } else {
            stmt.bindString(position, ParseUtils.toAscii(text));
        }
    }

    @Override
    @NonNull
    public List<Long> search(@Nullable final String author,
                             @Nullable final String title,
                             @Nullable final String seriesTitle,
                             @Nullable final String publisherName,
                             @Nullable final String keywords,
                             final int limit) {

        final List<Long> result = new ArrayList<>();

        FtsDao.createMatchString(title, seriesTitle, author, publisherName, keywords)
              .ifPresent(query -> {
                  try (Cursor cursor = mDb.rawQuery(SEARCH, new String[]
                          {query, String.valueOf(limit)})) {
                      while (cursor.moveToNext()) {
                          result.add(cursor.getLong(0));
                      }
                  }
              });

        return result;
    }

    @Override
    public void rebuild() {
        // This can take several seconds with many books or a slow device.
        long t0 = 0;
        if (BuildConfig.DEBUG /* always */) {
            t0 = System.nanoTime();
        }
        boolean gotError = false;

        final String tmpTableName = "books_fts_rebuilding";
        final TableDefinition ftsTemp = DBDefinitions.createFtsTableDefinition(tmpTableName);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            //IMPORTANT: withDomainConstraints MUST BE false
            mDb.recreate(ftsTemp, false);

            try (Cursor cursor = mDb.rawQuery(ALL_BOOKS, null)) {
                processBooks(cursor, INSERT_INTO_ + tmpTableName + INSERT_BODY);
            }
            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(TAG, e);
            gotError = true;
            mDb.drop(tmpTableName);

        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }

            /*
            http://sqlite.1065341.n5.nabble.com/Bug-in-FTS3-when-trying-to-rename-table-within-a-transaction-td11430.html
            FTS tables should only be renamed outside of transactions.
            */
            //  Delete old table and rename the new table
            if (!gotError) {
                // Drop old table, ready for rename
                mDb.drop(TBL_FTS_BOOKS.getName());
                mDb.execSQL("ALTER TABLE " + tmpTableName
                            + " RENAME TO " + TBL_FTS_BOOKS.getName());
            }
        }

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "|rebuildFts|completed in "
                       + (System.nanoTime() - t0) / NANO_TO_MILLIS + " ms");
        }
    }

    @Override
    public void insert(@IntRange(from = 1) final long bookId)
            throws TransactionException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        try (Cursor cursor = mDb.rawQuery(BOOK_BY_ID, new String[]{String.valueOf(bookId)})) {
            processBooks(cursor, INSERT);

        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(TAG, e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    @Override
    public void update(@IntRange(from = 1) final long bookId)
            throws TransactionException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        try (Cursor cursor = mDb.rawQuery(BOOK_BY_ID, new String[]{String.valueOf(bookId)})) {
            processBooks(cursor, UPDATE);

        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(TAG, e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    /**
     * Process the book details from the cursor using the passed fts query.
     * <p>
     * <strong>Note:</strong> This assumes a specific order for query parameters.
     * If modified, also modify {@link FtsDaoImpl#INSERT_BODY}
     * and {@link FtsDaoImpl#UPDATE}
     *
     * <strong>Transaction:</strong> required
     *
     * @param cursor Cursor of books to update
     * @param sql    Statement to execute (insert or update)
     *
     * @throws TransactionException a transaction must be started before calling this method
     */
    @SuppressLint("Range")
    private void processBooks(@NonNull final Cursor cursor,
                              @NonNull final String sql)
            throws TransactionException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // Accumulator for author names for each book
        final StringBuilder authorText = new StringBuilder();
        // Accumulator for series titles for each book
        final StringBuilder seriesText = new StringBuilder();
        // Accumulator for publisher names for each book
        final StringBuilder publisherText = new StringBuilder();
        // Accumulator for TOCEntry titles for each book
        final StringBuilder tocTitles = new StringBuilder();

        // Indexes of fields in the inner-loop cursors, -2 for 'not initialised yet'
        int colGivenNames = -2;
        int colFamilyName = -2;
        int colSeriesTitle = -2;
        int colPublisherName = -2;
        int colTOCEntryTitle = -2;

        final DataHolder rowData = new CursorRow(cursor);
        // Process each book
        while (cursor.moveToNext()) {
            authorText.setLength(0);
            seriesText.setLength(0);
            publisherText.setLength(0);
            tocTitles.setLength(0);

            final long bookId = rowData.getLong(PK_ID);
            // Query Parameter
            final String[] qpBookId = {String.valueOf(bookId)};

            // Get list of authors
            try (Cursor authors = mDb.rawQuery(GET_AUTHORS_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colGivenNames < 0) {
                    colGivenNames = authors.getColumnIndexOrThrow(AUTHOR_GIVEN_NAMES);
                }
                if (colFamilyName < 0) {
                    colFamilyName = authors.getColumnIndexOrThrow(AUTHOR_FAMILY_NAME);
                }

                while (authors.moveToNext()) {
                    authorText.append(authors.getString(colGivenNames))
                              .append(' ')
                              .append(authors.getString(colFamilyName))
                              .append(';');
                }
            }

            // Get list of series
            try (Cursor series = mDb.rawQuery(GET_SERIES_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colSeriesTitle < 0) {
                    colSeriesTitle = series.getColumnIndexOrThrow(SERIES_TITLE);
                }

                while (series.moveToNext()) {
                    seriesText.append(series.getString(colSeriesTitle)).append(';');
                }
            }

            // Get list of publishers
            try (Cursor publishers = mDb
                    .rawQuery(GET_PUBLISHERS_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colPublisherName < 0) {
                    colPublisherName = publishers.getColumnIndexOrThrow(PUBLISHER_NAME);
                }

                while (publishers.moveToNext()) {
                    publisherText.append(publishers.getString(colPublisherName)).append(';');
                }
            }

            // Get list of TOC titles
            try (Cursor toc = mDb.rawQuery(GET_TOC_TITLES_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colTOCEntryTitle < 0) {
                    colTOCEntryTitle = toc.getColumnIndexOrThrow(TITLE);
                }

                while (toc.moveToNext()) {
                    tocTitles.append(toc.getString(colTOCEntryTitle)).append(';');
                }
            }

            try (final SynchronizedStatement stmt = mDb.compileStatement(sql)) {
                bindStringOrNull(stmt, 1, rowData.getString(TITLE));
                // FTS_AUTHOR_NAME
                bindStringOrNull(stmt, 2, authorText.toString());
                // SERIES_TITLE
                bindStringOrNull(stmt, 3, seriesText.toString());
                bindStringOrNull(stmt, 4, rowData.getString(DESCRIPTION));
                bindStringOrNull(stmt, 5, rowData.getString(PERSONAL_NOTES));
                bindStringOrNull(stmt, 6, publisherText.toString());
                bindStringOrNull(stmt, 7, rowData.getString(GENRE));
                bindStringOrNull(stmt, 8, rowData.getString(LOCATION));
                bindStringOrNull(stmt, 9, rowData.getString(BOOK_ISBN));
                // FTS_TOC_ENTRY_TITLE
                bindStringOrNull(stmt, 10, tocTitles.toString());

                // FTS_BOOK_ID : in a where clause, or as insert parameter
                stmt.bindLong(11, bookId);

                stmt.execute();
            }
        }
    }
}
