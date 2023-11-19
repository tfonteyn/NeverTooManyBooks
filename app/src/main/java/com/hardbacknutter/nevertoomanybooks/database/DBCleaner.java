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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * Cleanup routines for some columns/tables which can be run at upgrades, import, startup
 * <p>
 * Work in progress.
 * <p>
 * TODO: add a loop check for the covers cache database:
 * read all book uuid's and clean the covers.db removing non-existing uuid rows.
 */
public class DBCleaner {

    /** Log tag. */
    private static final String TAG = "DBCleaner";

    private static final String SELECT_ = "SELECT ";
    private static final String SELECT_DISTINCT_ = "SELECT DISTINCT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _IS_NULL = " IS NULL";
    private static final String UPDATE_ = "UPDATE ";
    private static final String _SET_ = " SET ";
    private static final String DELETE_FROM_ = "DELETE FROM ";

    /** Database Access. */
    @NonNull
    private final SynchronizedDb db;

    /**
     * Constructor.
     */
    public DBCleaner() {
        this.db = ServiceLocator.getInstance().getDb();
    }

    public void clean(@NonNull final Context context) {
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();

        // do a mass update of any languages not yet converted to ISO 639-2 codes
        serviceLocator.getLanguageDao().bulkUpdate(context);

        // make sure there are no 'T' separators in datetime fields
        datetimeFormat();

        // validate booleans to have 0/1 content (could do just ALL_TABLES)
        booleanColumns(DBDefinitions.TBL_BOOKS,
                       DBDefinitions.TBL_AUTHORS,
                       DBDefinitions.TBL_SERIES);

        // clean/correct style UUID's on Bookshelves for deleted styles.
        bookshelves(context);

        //TEST: we only check & log for now, but don't update yet...
        // we need to test with bad data
        bookBookshelf(true);

        // re-sort positional links - theoretically this should never be needed... flw.
        int modified;
        modified = serviceLocator.getAuthorDao().fixPositions(context);
        modified += serviceLocator.getSeriesDao().fixPositions(context);
        modified += serviceLocator.getPublisherDao().fixPositions(context);
        modified += serviceLocator.getTocEntryDao().fixPositions(context);
        if (modified > 0) {
            LoggerFactory.getLogger().w(TAG, "reposition modified=" + modified);
        }
    }


    /**
     * Replace 'T' occurrences with ' '.
     * See package-info docs for
     * {@link FullDateParser}
     */
    private void datetimeFormat() {
        final Pattern T = Pattern.compile("T");

        final Collection<Pair<Long, String>> rows = new ArrayList<>();

        for (final String key : DBKey.DATETIME_KEYS) {
            try (Cursor cursor = db.rawQuery(
                    SELECT_ + DBKey.PK_ID + ',' + key
                    + _FROM_ + DBDefinitions.TBL_BOOKS.getName()
                    + _WHERE_ + key + " LIKE '%T%'", null)) {
                while (cursor.moveToNext()) {
                    rows.add(new Pair<>(cursor.getLong(0), cursor.getString(1)));
                }
            }

            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger().d(TAG, "dates",
                                            "key=" + key
                                            + "|rows.size()=" + rows.size());
            }
            try (SynchronizedStatement stmt = db.compileStatement(
                    UPDATE_ + DBDefinitions.TBL_BOOKS.getName()
                    + _SET_ + key + "=?" + _WHERE_ + DBKey.PK_ID + "=?")) {

                for (final Pair<Long, String> row : rows) {
                    stmt.bindString(1, T.matcher(row.second).replaceFirst(" "));
                    stmt.bindLong(2, row.first);
                    stmt.executeUpdateDelete();
                }
            }
            // reuse for next column
            rows.clear();
        }
    }

    /**
     * Validates {@link Bookshelf} being set to a valid {@link Style}.
     *
     * @param context Current context
     */
    public void bookshelves(@NonNull final Context context) {
        ServiceLocator.getInstance().getBookshelfDao().getAll().forEach(
                bookshelf -> bookshelf.validateStyle(context));
    }


    /**
     * Validates all boolean columns to contain '0' or '1'.
     *
     * @param tables list of tables
     */
    private void booleanColumns(@NonNull final TableDefinition... tables) {
        for (final TableDefinition table : tables) {
            table.getDomains()
                 .stream()
                 .filter(domain -> domain.getSqLiteDataType() == SqLiteDataType.Boolean)
                 .forEach(domain -> booleanCleanup(table.getName(), domain.getName()));
        }
    }

    /**
     * Enforce boolean columns to 0,1.
     *
     * @param table  to check
     * @param column to check
     */
    private void booleanCleanup(@NonNull final String table,
                                @NonNull final String column) {
        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(TAG, "booleanCleanup",
                                        "table=" + table,
                                        "column=" + column);
        }

        final String select = SELECT_DISTINCT_ + column + _FROM_ + table
                              + _WHERE_ + column + " NOT IN ('0','1')";
        toLog("booleanCleanup", select);

        final String update = UPDATE_ + table + _SET_ + column + "=?"
                              + _WHERE_ + "LOWER(" + column + ") IN ";
        String sql;
        sql = update + "('true','t','yes')";
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.bindLong(1, 1);
            final int count = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG /* always */) {
                if (count > 0) {
                    LoggerFactory.getLogger().d(TAG, "booleanCleanup", "true=" + count);
                }
            }
        }

        sql = update + "('false','f','no')";
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.bindLong(1, 0);
            final int count = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG /* always */) {
                if (count > 0) {
                    LoggerFactory.getLogger().d(TAG, "booleanCleanup", "false=" + count);
                }
            }
        }
    }

    /* ****************************************************************************************** */

    /**
     * Remove rows where books are sitting on a {@code null} bookshelf.
     *
     * @param dryRun {@code true} to run the update.
     */
    private void bookBookshelf(@SuppressWarnings("SameParameterValue") final boolean dryRun) {
        final String select = SELECT_DISTINCT_ + DBKey.FK_BOOK
                              + _FROM_ + DBDefinitions.TBL_BOOK_BOOKSHELF
                              + _WHERE_ + DBKey.FK_BOOKSHELF + _IS_NULL;

        toLog("bookBookshelf|ENTER", select);
        if (!dryRun) {
            final String sql = DELETE_FROM_ + DBDefinitions.TBL_BOOK_BOOKSHELF
                               + _WHERE_ + DBKey.FK_BOOKSHELF + _IS_NULL;
            try (SynchronizedStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog("bookBookshelf|EXIT", select);
        }
    }

    /**
     * Convert any {@code null} values to an empty string.
     * <p>
     * Used to correct data in columns which have "string default ''"
     *
     * @param table  to check
     * @param column to check
     * @param dryRun {@code true} to run the update.
     */
    @SuppressWarnings("unused")
    public void nullString2empty(@NonNull final String table,
                                 @NonNull final String column,
                                 final boolean dryRun) {
        final String select =
                SELECT_DISTINCT_ + column + _FROM_ + table + _WHERE_ + column + _IS_NULL;
        toLog("nullString2empty|ENTER", select);
        if (!dryRun) {
            final String sql =
                    UPDATE_ + table + _SET_ + column + "=''" + _WHERE_ + column + _IS_NULL;
            try (SynchronizedStatement stmt = db.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog("nullString2empty|EXIT", select);
        }
    }

    /**
     * WIP... debug
     * Execute the query and log the results.
     *
     * @param state Enter/Exit
     * @param query to execute
     */
    private void toLog(@NonNull final String state,
                       @NonNull final String query) {
        if (BuildConfig.DEBUG /* always */) {
            try (SynchronizedCursor cursor = db.rawQuery(query, null)) {
                LoggerFactory.getLogger().d(TAG, state, "row count=" + cursor.getCount());
                while (cursor.moveToNext()) {
                    final String field = cursor.getColumnName(0);
                    final String value = cursor.getString(0);

                    LoggerFactory.getLogger().d(TAG, state, field + '=' + value);
                }
            }
        }
    }
}
