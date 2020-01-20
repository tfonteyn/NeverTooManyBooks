/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TMP_TBL_BOOK_LIST_NAVIGATOR;

/**
 * Class to provide a simple interface into a temporary table containing a list of book ID's in
 * the same order as an underlying booklist.
 * <p>
 * Construction is done in two steps:
 * <ol>
 * <li>Create the table, fill it with data<br>
 * {@link #createTable(SynchronizedDb, int, TableDefinition)}</li>
 * <li>Use the normal constructor with the table name from step 1 to start using the table</li>
 * </ol>
 * Reminder: moveNext/Prev SQL concatenates/splits two columns.
 * This is to get around the limitation of {@link SQLiteStatement}
 * only able to return a single column.
 * Alternative is to use a raw query, but those cannot be re-used (pre-compiled).
 */
public class FlattenedBooklist {

    /** Log tag. */
    private static final String TAG = "FlattenedBooklist";

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

    /** Name for the 'next' statement. */
    private static final String STMT_NEXT = "next";
    /** Name for the 'prev' statement. */
    private static final String STMT_PREV = "prev";
    /** Name for the 'move-to' statement. */
    private static final String STMT_MOVE = "move";
    /** The underlying database. We need this to keep the table alive. */
    @NonNull
    private final SynchronizedDb mSyncedDb;
    /** Collection of statements compiled for this object. */
    @NonNull
    private final SqlStatementManager mSqlStatementManager;
    /** Underlying table definition. */
    @NonNull
    private TableDefinition mTable;

    /** Current position; used to navigate around with next/prev. */
    private long mRowId = -1;
    /** Book id at the currently selected row. */
    private long mBookId;
    /** DEBUG: Indicates close() has been called. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     *
     * @param db        Database Access
     * @param tableName Name of underlying and <strong>existing</strong> table
     */
    public FlattenedBooklist(@NonNull final DAO db,
                             @NonNull final String tableName) {
        mSyncedDb = db.getUnderlyingDatabase();
        mTable = new TableDefinition(TMP_TBL_BOOK_LIST_NAVIGATOR);
        mTable.setName(tableName);

        mSqlStatementManager = new SqlStatementManager(mSyncedDb, TAG + "|" + tableName);
    }

    /**
     * Create a flattened table of ordered book ID's based on the underlying list.
     * Any old data is removed before the new table is created.
     *
     * @param syncedDb   the database
     * @param instanceId counter which will be used to create the table name
     *
     * @return the created table name.
     */
    @NonNull
    static String createTable(@NonNull final SynchronizedDb syncedDb,
                              final int instanceId,
                              @NonNull final TableDefinition listTable) {

        TableDefinition navTable = new TableDefinition(TMP_TBL_BOOK_LIST_NAVIGATOR);
        navTable.setName(navTable.getName() + instanceId);

        //IMPORTANT: withConstraints MUST BE false
        navTable.recreate(syncedDb, false);

        final long t00 = System.nanoTime();

        String sql = "INSERT INTO " + navTable.getName()
                     + " (" + KEY_PK_ID + ',' + KEY_FK_BOOK + ")"
                     + " SELECT " + KEY_PK_ID + ',' + KEY_FK_BOOK
                     + " FROM " + listTable.getName()
                     // all rows which are NOT a book will contain null
                     + " WHERE " + KEY_FK_BOOK + " NOT NULL"
                     + " ORDER BY " + KEY_PK_ID;

        syncedDb.execSQL(sql);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            Log.d(TAG, "createTable"
                       + "|" + navTable.getName()
                       + "|completed in "
                       + (System.nanoTime() - t00) / NANO_TO_MILLIS + " ms");
        }

        return navTable.getName();
    }

    /**
     * Get the current book.
     *
     * @return bookId, or {@code 0} if the last move operation failed.
     */
    public long getBookId() {
        return mBookId;
    }

    /**
     * Move to the specified book row, based on the row ID, not the book id or row number.
     * The row id should be the row number in the table, including header-related rows.
     *
     * @param bookId to move to
     *
     * @return {@code true} on success
     */
    public boolean moveTo(final long bookId) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_MOVE);
        if (stmt == null) {
            String sql = "SELECT " + KEY_PK_ID + " || '/' || " + KEY_FK_BOOK
                         + " FROM " + mTable
                         + " WHERE " + KEY_FK_BOOK + "=?";
            stmt = mSqlStatementManager.add(STMT_MOVE, sql);
        }
        stmt.bindLong(1, bookId);
        return execAndParse(stmt);
    }

    /**
     * Move to the next, or previous book depending on the given direction.
     *
     * @param forward {@code true} to move to the next book, {@code false} for the previous book.
     *
     * @return {@code true} on success
     */
    public boolean move(final boolean forward) {
        if (forward) {
            return moveNext();
        } else {
            return movePrev();
        }
    }

    /**
     * Move to the next book row.
     *
     * @return {@code true} on success
     */
    private boolean moveNext() {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_NEXT);
        if (stmt == null) {
            String sql = "SELECT " + KEY_PK_ID + " || '/' || " + KEY_FK_BOOK
                         + " FROM " + mTable
                         + " WHERE " + KEY_PK_ID + ">?"
                         + " ORDER BY " + KEY_PK_ID + " ASC LIMIT 1";
            stmt = mSqlStatementManager.add(STMT_NEXT, sql);
        }
        stmt.bindLong(1, mRowId);
        return execAndParse(stmt);
    }

    /**
     * Move to the previous book row.
     *
     * @return {@code true} on success
     */
    private boolean movePrev() {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_PREV);
        if (stmt == null) {
            String sql = "SELECT " + KEY_PK_ID + " || '/' || " + KEY_FK_BOOK
                         + " FROM " + mTable
                         + " WHERE " + KEY_PK_ID + "<?"
                         + " ORDER BY " + KEY_PK_ID + " DESC LIMIT 1";
            stmt = mSqlStatementManager.add(STMT_PREV, sql);
        }
        stmt.bindLong(1, mRowId);
        return execAndParse(stmt);
    }

    /**
     * Move to the first row.
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("unused")
    public boolean moveFirst() {
        mRowId = -1;
        return moveNext();
    }

    /**
     * Move to the last row.
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("unused")
    public boolean moveLast() {
        mRowId = Long.MAX_VALUE;
        return movePrev();
    }

    /**
     * Execute the passed statement and update the current row details.
     *
     * @param stmt to execute
     *
     * @return {@code true} on success
     */
    private boolean execAndParse(@NonNull final SynchronizedStatement stmt) {
        final String result = stmt.simpleQueryForStringOrNull();
        if (result != null) {
            final String[] data = result.split("/");
            mRowId = Long.parseLong(data[0]);
            mBookId = Long.parseLong(data[1]);
            return true;

        } else {
            mBookId = 0;
            return false;
        }
    }

    /**
     * Release resource-consuming stuff.
     * Optionally drop the table.
     *
     * @param drop Flag
     */
    public void close(final boolean drop) {
        String tableName = mTable.getName();
        mCloseWasCalled = true;
        mSqlStatementManager.close();
        //noinspection ConstantConditions
        mTable = null;
        if (drop) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "close|dropping table=" + tableName);
            }
            mSyncedDb.drop(tableName);
        }
    }

    /**
     * DEBUG: if we see the warn in the logs, we know we have an issue to fix.
     */
    @SuppressWarnings("FinalizeDeclaration")
    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        if (!mCloseWasCalled) {
            Logger.warn(TAG, "finalize|calling close() on " + mTable.getName());
            // Warning: do NOT drop the table here. It needs to survive beyond this object.
            close(false);
        }
        super.finalize();
    }
}
