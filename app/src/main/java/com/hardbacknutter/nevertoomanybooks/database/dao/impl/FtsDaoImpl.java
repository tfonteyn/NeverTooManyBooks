/*
 * @Copyright 2018-2025 HardBackNutter
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

import android.app.SearchManager;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.database.SqlEncode;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.FtsSearchResult;
import com.hardbacknutter.nevertoomanybooks.database.dao.StylesHelper;
import com.hardbacknutter.util.logger.LoggerFactory;

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

/**
 * FTS Strings <strong>PRESERVE single-spaces</strong>.
 */
public class FtsDaoImpl
        extends BaseDaoImpl
        implements FtsDao {

    /** Log tag. */
    private static final String TAG = "FtsDaoImpl";

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

    /** log error string. */
    private static final String ERROR_FAILED_TO_UPDATE_FTS = "Failed to update FTS";

    /** Name of the temporary table used during {@link #rebuild()}. */
    private static final String TMP_TABLE_FOR_REBUILDING = "books_fts_rebuilding";
    private static final String LIST_DELIMITER = "; ";

    @NonNull
    private final Supplier<StylesHelper> stylesHelperSupplier;

    /**
     * Constructor.
     *
     * @param db                   Underlying database
     * @param stylesHelperSupplier deferred supplier for the {@link StylesHelper}
     */
    public FtsDaoImpl(@NonNull final SynchronizedDb db,
                      @NonNull final Supplier<StylesHelper> stylesHelperSupplier) {
        super(db, TAG);
        this.stylesHelperSupplier = stylesHelperSupplier;
    }

    /**
     * Bind a string or {@code null} value to a parameter since binding a {@code null}
     * in bindString produces an error.
     * <p>
     * <strong>Note:</strong> We specifically want to use the default Locale for this.
     *
     * @param stmt     to use
     * @param position to bind
     * @param text     to bind
     */
    private void bindStringOrNull(@NonNull final SynchronizedStatement stmt,
                                  final int position,
                                  @Nullable final String text) {
        if (text == null || text.isBlank()) {
            stmt.bindNull(position);
        } else {
            stmt.bindString(position, SqlEncode.normalize(text));
        }
    }

    private void bindStringOrNull(@NonNull final SynchronizedStatement stmt,
                                  final int position,
                                  @NonNull final Collection<String> list) {
        if (list.isEmpty()) {
            stmt.bindNull(position);
        } else {
            final String normalized = list
                    .stream()
                    .map(SqlEncode::normalize)
                    .collect(Collectors.joining(LIST_DELIMITER));
            if (normalized.isBlank()) {
                stmt.bindNull(position);
            } else {
                stmt.bindString(position, normalized);
            }
        }
    }

    @Override
    @NonNull
    public List<FtsSearchResult> search(@Nullable final String author,
                                        @Nullable final String title,
                                        @Nullable final String seriesTitle,
                                        @Nullable final String publisherName,
                                        @Nullable final String keywords) {

        final List<FtsSearchResult> result = new ArrayList<>();

        FtsDaoHelper.createMatchClause(title, seriesTitle, author, publisherName, keywords)
                    .ifPresent(match -> {
                        try (Cursor cursor = db.rawQuery(Sql.SEARCH_SUGGESTIONS,
                                                         new String[]{match})) {
                            while (cursor.moveToNext()) {
                                result.add(new FtsSearchResult(
                                        cursor.getLong(0),
                                        cursor.getString(1),
                                        cursor.getString(2)));
                            }
                        }
                    });

        return result;
    }

    @Override
    @NonNull
    public List<FtsSearchResult> search(@NonNull final String keywords) {
        return search(null, null, null, null, keywords);
    }

    @Nullable
    @Override
    public Cursor querySearchSuggestions(@NonNull final String searchText) {
        final String query = FtsDaoHelper.prepareSearchText(searchText, null);
        if (!query.isEmpty()) {
            return db.rawQuery(Sql.SEARCH_SUGGESTIONS, new String[]{query});
        }

        return null;
    }

    @Override
    @WorkerThread
    public void rebuild() {
        // This can take several seconds with many books or a slow device.
        long t0 = 0;
        if (BuildConfig.DEBUG /* always */) {
            t0 = System.nanoTime();
        }

        final TableDefinition ftsTemp = DBDefinitions
                .createFtsTableDefinition(TMP_TABLE_FOR_REBUILDING);

        Synchronizer.SyncLock txLock = null;
        //noinspection CheckStyle,OverlyBroadCatchBlock
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            //IMPORTANT: withDomainConstraints MUST BE false
            db.recreate(ftsTemp, false);

            try (Cursor cursor = db.rawQuery(Sql.ALL_BOOKS, null)) {
                processBooks(cursor, INSERT_INTO_ + TMP_TABLE_FOR_REBUILDING + Sql.INSERT_BODY);
            }
            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } catch (@NonNull final RuntimeException e) {
            // we're running as a task thread, just cleanup, and let the task handle the exception
            LoggerFactory.getLogger().e(TAG, e);
            db.drop(TMP_TABLE_FOR_REBUILDING);
            throw e;

        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }

        // FTS tables should only be renamed outside of transactions.
        // http://sqlite.1065341.n5.nabble.com/Bug-in-FTS3-when-trying-to-rename-table-within-a-transaction-td11430.html
        // Delete old table and rename the new table
        db.drop(TBL_FTS_BOOKS.getName());
        db.execSQL("ALTER TABLE " + TMP_TABLE_FOR_REBUILDING
                   + " RENAME TO " + TBL_FTS_BOOKS.getName());

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(TAG, "rebuild",
                                        "completed in "
                                        + (System.nanoTime() - t0) / NANO_TO_MILLIS + " ms");
        }
    }

    @Override
    public void insert(@IntRange(from = 1) final long bookId) {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        //noinspection CheckStyle
        try (Cursor cursor = db.rawQuery(Sql.BOOK_BY_ID, new String[]{String.valueOf(bookId)})) {
            processBooks(cursor, Sql.INSERT);

        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            LoggerFactory.getLogger().e(TAG, e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    @Override
    public void update(@IntRange(from = 1) final long bookId) {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        //noinspection CheckStyle
        try (Cursor cursor = db.rawQuery(Sql.BOOK_BY_ID, new String[]{String.valueOf(bookId)})) {
            processBooks(cursor, Sql.UPDATE);

        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            LoggerFactory.getLogger().e(TAG, e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    /**
     * Process the book details from the cursor using the passed fts query.
     * <p>
     * <strong>Note:</strong> This assumes a specific order for query parameters.
     * If modified, also modify {@link Sql#INSERT_BODY} and {@link Sql#UPDATE}
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param cursor Cursor of books to update
     * @param sql    Statement to execute (insert or update)
     */
    private void processBooks(@NonNull final Cursor cursor,
                              @NonNull final String sql) {

        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                //noinspection CheckStyle
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // Accumulator for author names for each book
        final List<String> authorList = new ArrayList<>();
        // Accumulator for series titles for each book
        final List<String> seriesList = new ArrayList<>();
        // Accumulator for publisher names for each book
        final List<String> publisherList = new ArrayList<>();
        // Accumulator for TOCEntry titles for each book
        final List<String> tocList = new ArrayList<>();

        final boolean givenNameFirst = stylesHelperSupplier.get()
                                                           .getGlobalStyle()
                                                           .isShowAuthorByGivenName();

        // Indexes of fields in the inner-loop cursors, -2 for 'not initialised yet'
        int colGivenNames = -2;
        int colFamilyName = -2;
        int colSeriesTitle = -2;
        int colPublisherName = -2;
        int colTOCEntryTitle = -2;

        final CursorRow rowData = new CursorRow(cursor);
        // Process each book
        while (cursor.moveToNext()) {
            authorList.clear();
            seriesList.clear();
            publisherList.clear();
            tocList.clear();

            final long bookId = rowData.getLong(DBKey.PK_ID);
            // Query Parameter
            final String[] qpBookId = {String.valueOf(bookId)};

            // Get list of authors
            try (Cursor authors = db.rawQuery(Sql.GET_AUTHORS_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colGivenNames < 0) {
                    colGivenNames = authors.getColumnIndexOrThrow(DBKey.AUTHOR.GIVEN_NAMES);
                }
                if (colFamilyName < 0) {
                    colFamilyName = authors.getColumnIndexOrThrow(DBKey.AUTHOR.FAMILY_NAME);
                }

                while (authors.moveToNext()) {
                    final String givenName = authors.getString(colGivenNames);
                    final String familyName = authors.getString(colFamilyName);
                    final String name;
                    if (givenNameFirst) {
                        name = givenName.isBlank() ? familyName : givenName + ' ' + familyName;
                    } else {
                        // don't add comma, it would be removed when normalizing anyhow
                        name = familyName + (givenName.isBlank() ? "" : " " + givenName);
                    }
                    authorList.add(name);
                }
            }

            // Get list of series
            try (Cursor series = db.rawQuery(Sql.GET_SERIES_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colSeriesTitle < 0) {
                    colSeriesTitle = series.getColumnIndexOrThrow(DBKey.SERIES.TITLE);
                }

                while (series.moveToNext()) {
                    seriesList.add(series.getString(colSeriesTitle));
                }
            }

            // Get list of publishers
            try (Cursor publishers = db.rawQuery(Sql.GET_PUBLISHERS_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colPublisherName < 0) {
                    colPublisherName = publishers.getColumnIndexOrThrow(DBKey.PUBLISHER.NAME);
                }

                while (publishers.moveToNext()) {
                    publisherList.add(publishers.getString(colPublisherName));
                }
            }

            // Get list of TOC titles
            try (Cursor toc = db.rawQuery(Sql.GET_TOC_TITLES_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colTOCEntryTitle < 0) {
                    colTOCEntryTitle = toc.getColumnIndexOrThrow(DBKey.TITLE);
                }

                while (toc.moveToNext()) {
                    tocList.add(toc.getString(colTOCEntryTitle));
                }
            }

            try (SynchronizedStatement stmt = db.compileStatement(sql)) {
                bindStringOrNull(stmt, 1, rowData.getString(DBKey.TITLE));
                // FTS_AUTHOR_NAME
                bindStringOrNull(stmt, 2, authorList);
                // SERIES_TITLE
                bindStringOrNull(stmt, 3, seriesList);

                bindStringOrNull(stmt, 4, rowData.getString(DBKey.DESCRIPTION));
                bindStringOrNull(stmt, 5, rowData.getString(DBKey.PERSONAL_NOTES));

                bindStringOrNull(stmt, 6, publisherList);

                bindStringOrNull(stmt, 7, rowData.getString(DBKey.LOCATION));
                bindStringOrNull(stmt, 8, rowData.getString(DBKey.ISBN));

                // FTS_TOC_ENTRY_TITLE
                bindStringOrNull(stmt, 9, tocList);

                // FTS_BOOK_ID : in a where clause, or as insert parameter
                stmt.bindLong(10, bookId);

                stmt.execute();
            }
        }
    }

    private static final class Sql {

        /** the body of an INSERT INTO [table]. Used more than once. */
        static final String INSERT_BODY =
                " (" + DBKey.TITLE
                + ',' + DBKey.FTS.AUTHOR_NAME
                + ',' + DBKey.SERIES.TITLE
                + ',' + DBKey.DESCRIPTION
                + ',' + DBKey.PERSONAL_NOTES
                + ',' + DBKey.PUBLISHER.NAME
                + ',' + DBKey.LOCATION
                + ',' + DBKey.ISBN
                + ',' + DBKey.FTS.TOC_ENTRY_TITLE

                + ',' + DBKey.FTS.PK_BOOK_ID
                + ") VALUES (?,?,?,?,?,?,?,?,?, ?)";

        /**
         * The full INSERT statement.
         * The parameter order MUST match the order expected in UPDATE.
         */
        static final String INSERT =
                INSERT_INTO_ + TBL_FTS_BOOKS.getName() + INSERT_BODY;

        /**
         * The full UPDATE statement.
         * The parameter order MUST match the order expected in INSERT.
         */
        static final String UPDATE =
                UPDATE_ + TBL_FTS_BOOKS.getName()
                + _SET_ + DBKey.TITLE + "=?"
                + ',' + DBKey.FTS.AUTHOR_NAME + "=?"
                + ',' + DBKey.SERIES.TITLE + "=?"
                + ',' + DBKey.DESCRIPTION + "=?"
                + ',' + DBKey.PERSONAL_NOTES + "=?"
                + ',' + DBKey.PUBLISHER.NAME + "=?"
                + ',' + DBKey.LOCATION + "=?"
                + ',' + DBKey.ISBN + "=?"
                + ',' + DBKey.FTS.TOC_ENTRY_TITLE + "=?"

                + _WHERE_ + DBKey.FTS.PK_BOOK_ID + "=?";

        /** Used during a full FTS rebuild. Minimal column list. */
        static final String ALL_BOOKS =
                SELECT_ + DBKey.PK_ID
                + ',' + DBKey.TITLE
                + ',' + DBKey.DESCRIPTION
                + ',' + DBKey.PERSONAL_NOTES
                + ',' + DBKey.LOCATION
                + ',' + DBKey.ISBN
                + _FROM_ + TBL_BOOKS.getName();

        /** Used during insert of a book. Minimal column list. */
        static final String BOOK_BY_ID = ALL_BOOKS + _WHERE_ + DBKey.PK_ID + "=?";

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_AUTHORS_BY_BOOK_ID =
                SELECT_ + TBL_AUTHORS.dotAs(DBKey.AUTHOR.FAMILY_NAME, DBKey.AUTHOR.GIVEN_NAMES)
                + _FROM_ + TBL_BOOK_AUTHOR.startJoin(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_AUTHOR.dot(DBKey.AUTHOR.BOOK_AUTHOR_POSITION);

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_PUBLISHERS_BY_BOOK_ID =
                SELECT_ + TBL_PUBLISHERS.dotAs(DBKey.PUBLISHER.NAME)
                + _FROM_ + TBL_BOOK_PUBLISHER.startJoin(TBL_PUBLISHERS)
                + _WHERE_ + TBL_BOOK_PUBLISHER.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_PUBLISHER.dot(DBKey.PUBLISHER.BOOK_PUBLISHER_POSITION);

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_TOC_TITLES_BY_BOOK_ID =
                SELECT_ + TBL_TOC_ENTRIES.dotAs(DBKey.TITLE)
                + _FROM_ + TBL_TOC_ENTRIES.startJoin(TBL_BOOK_TOC_ENTRIES)
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_TOC_ENTRIES.dot(DBKey.BOOK_TOC_ENTRY_POSITION);

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_SERIES_BY_BOOK_ID =
                SELECT_ + TBL_SERIES.dot(DBKey.SERIES.TITLE) + "||' '||"
                + " COALESCE(" + TBL_BOOK_SERIES.dot(DBKey.SERIES.BOOK_SERIES_NUMBER) + ",'')"
                + _AS_ + DBKey.SERIES.TITLE
                + _FROM_ + TBL_BOOK_SERIES.startJoin(TBL_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(DBKey.FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_SERIES.dot(DBKey.SERIES.BOOK_SERIES_POSITION);

        /** Advanced Local-search. */
        static final String SEARCH =
                // FTS_BOOK_ID is the _id into the books table.
                SELECT_ + DBKey.FTS.PK_BOOK_ID
                + _FROM_ + TBL_FTS_BOOKS.getName()
                + _WHERE_ + TBL_FTS_BOOKS.getName()
                + " MATCH ?";

        /** Standard Local-search. */
        static final String SEARCH_SUGGESTIONS =
                // FTS_BOOK_ID is the _id into the books table.
                "SELECT " + DBKey.FTS.PK_BOOK_ID + _AS_ + DBKey.PK_ID

                + ',' + (TBL_FTS_BOOKS.dot(DBKey.TITLE)
                         + _AS_ + SearchManager.SUGGEST_COLUMN_TEXT_1)
                + ',' + (TBL_FTS_BOOKS.dot(DBKey.FTS.AUTHOR_NAME)
                         + _AS_ + SearchManager.SUGGEST_COLUMN_TEXT_2)

                + ',' + (TBL_FTS_BOOKS.dot(DBKey.TITLE)
                         + _AS_ + SearchManager.SUGGEST_COLUMN_INTENT_DATA)
                + " FROM " + TBL_FTS_BOOKS.getName()
                + " WHERE " + TBL_FTS_BOOKS.getName() + " MATCH ?";
    }
}
