/*
 * @Copyright 2019 HardBackNutter
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

import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Class to provide a simple interface into a temporary table containing a list of book ID's in
 * the same order as an underlying booklist.
 * <p>
 * Construction is done in two steps:
 * <ol>
 * <li>Create the table, fill it with data<br>
 * String tableName = {@link #createTable(DAO, TableDefinition, TableDefinition, int)}</li>
 * <li>Use the normal constructor with the table name from step 1 to start using the table</li>
 * </ol>
 * Reminder: moveNext/Prev SQL concatenates/splits two columns.
 * This is to get around the limitation of {@link SQLiteStatement}
 * only able to return a single column.
 * Alternative is to use a raw query, but those cannot be re-used (pre-compiled).
 */
public class FlattenedBooklist
        implements AutoCloseable {

    /** Name for the 'next' statement. */
    private static final String STMT_NEXT = "next";
    /** Name for the 'prev' statement. */
    private static final String STMT_PREV = "prev";
    /** Name for the 'move-to' statement. */
    private static final String STMT_MOVE = "move";
    /** Name for the 'absolute-position' statement. */
    private static final String STMT_POSITION = "position";
    /** The underlying database. We need this to keep the table alive. */
    @NonNull
    private final SynchronizedDb mSyncedDb;
    /** Collection of statements compiled for this object. */
    @NonNull
    private final SqlStatementManager mStatements;
    /**
     * Underlying table definition.
     * Reminder: this is a {@link TableDefinition.TableTypes#Temporary}.
     */
    @NonNull
    private TableDefinition mTable;
    /** Default position (before first element). */
    private long mPosition = -1;
    /** Book id from the currently selected row. */
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
        TableDefinition table = DBDefinitions.TMP_TBL_ROW_NAVIGATOR_FLATTENED.clone();
        table.setName(tableName);

        mSyncedDb = db.getUnderlyingDatabase();
        mTable = table;
        mStatements = new SqlStatementManager(mSyncedDb);
    }

    /**
     * Create a flattened table of ordered book ID's based on the underlying list.
     *
     * @param db Database Access
     * @param id counter which will be used to create the table name
     *
     * @return the name of the created table.
     */
    @NonNull
    static String createTable(@NonNull final DAO db,
                              @NonNull final TableDefinition navTable,
                              @NonNull final TableDefinition listTable,
                              final int id) {

        SynchronizedDb syncedDb = db.getUnderlyingDatabase();

        TableDefinition table = DBDefinitions.TMP_TBL_ROW_NAVIGATOR_FLATTENED.clone();
        table.setName(table.getName() + '_' + id);
        // no indexes, no constraints!
        table.create(syncedDb, false);

        String sql = table.getInsertInto(DBDefinitions.DOM_PK_ID, DBDefinitions.DOM_FK_BOOK)
                     + " SELECT " + navTable.dot(DBDefinitions.DOM_PK_ID)
                     + ',' + listTable.dot(DBDefinitions.DOM_FK_BOOK)
                     + " FROM " + listTable.ref() + listTable.join(navTable)
                     + " WHERE " + listTable.dot(DBDefinitions.DOM_FK_BOOK) + " NOT NULL"
                     + " ORDER BY " + navTable.dot(DBDefinitions.DOM_PK_ID);

        try (SynchronizedStatement stmt = syncedDb.compileStatement(sql)) {
            stmt.executeInsert();
        }

        return table.getName();
    }

    public long getBookId() {
        return mBookId;
    }

    /**
     * Passed a statement update the 'current' row details based on the columns returned.
     *
     * @param stmt to execute
     *
     * @return {@code true} if we have set position and bookId successfully.
     */
    private boolean fetchBookIdAndPosition(@NonNull final SynchronizedStatement stmt) {
        try {
            // Get a pair of ID's separated by a '/'
            final String[] data = stmt.simpleQueryForString().split("/");
            mPosition = Long.parseLong(data[0]);
            mBookId = Long.parseLong(data[1]);
            return true;
        } catch (@NonNull final SQLiteDoneException ignore) {
            return false;
        }
    }

    /**
     * Check that the referenced table exists. This is important for resumed activities
     * where the underlying database connection may have closed and the table been deleted
     * as a result.
     *
     * @return {@code true} if the table exists.
     */
    public boolean exists() {
        return mTable.exists(mSyncedDb);
    }

    /**
     * Move to the next book row.
     *
     * @return {@code true}if successful
     */
    public boolean moveNext() {
        SynchronizedStatement stmt = mStatements.get(STMT_NEXT);
        if (stmt == null) {
            String sql = "SELECT "
                         + mTable.dot(DBDefinitions.DOM_PK_ID)
                         + " || '/' || " + mTable.dot(DBDefinitions.DOM_FK_BOOK)
                         + " FROM " + mTable.ref()
                         + " WHERE " + mTable.dot(DBDefinitions.DOM_PK_ID) + ">?"
                         + " AND " + mTable.dot(DBDefinitions.DOM_FK_BOOK) + "<>COALESCE(?,-1)"
                         + " ORDER BY " + mTable.dot(DBDefinitions.DOM_PK_ID) + " ASC LIMIT 1";
            stmt = mStatements.add(STMT_NEXT, sql);
        }
        stmt.bindLong(1, mPosition);
        if (mBookId != 0) {
            stmt.bindLong(2, mBookId);
        } else {
            stmt.bindNull(2);
        }
        // Get a pair of ID's separated by a '/'
        return fetchBookIdAndPosition(stmt);
    }

    /**
     * Move to the previous book row.
     *
     * @return {@code true}if successful
     */
    public boolean movePrev() {
        SynchronizedStatement stmt = mStatements.get(STMT_PREV);
        if (stmt == null) {
            String sql = "SELECT "
                         + mTable.dot(DBDefinitions.DOM_PK_ID)
                         + " || '/' || " + mTable.dot(DBDefinitions.DOM_FK_BOOK)
                         + " FROM " + mTable.ref()
                         + " WHERE " + mTable.dot(DBDefinitions.DOM_PK_ID) + "<?"
                         + " AND " + mTable.dot(DBDefinitions.DOM_FK_BOOK) + "<>COALESCE(?,-1)"
                         + " ORDER BY " + mTable.dot(DBDefinitions.DOM_PK_ID) + " DESC LIMIT 1";
            stmt = mStatements.add(STMT_PREV, sql);
        }
        stmt.bindLong(1, mPosition);
        if (mBookId != 0) {
            stmt.bindLong(2, mBookId);
        } else {
            stmt.bindNull(2);
        }
        // Get a pair of ID's separated by a '/'
        return fetchBookIdAndPosition(stmt);
    }

    /**
     * Move to the specified book row, based on the row ID, not the book id or row number.
     * The row id should be the row number in the table, including header-related rows.
     *
     * @param position to move to
     *
     * @return {@code true} if successful
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean moveTo(final int position) {
        SynchronizedStatement stmt = mStatements.get(STMT_MOVE);
        if (stmt == null) {
            String sql = "SELECT "
                         + mTable.dot(DBDefinitions.DOM_PK_ID)
                         + " || '/' || " + mTable.dot(DBDefinitions.DOM_FK_BOOK)
                         + " FROM " + mTable.ref()
                         + " WHERE " + mTable.dot(DBDefinitions.DOM_PK_ID) + "=?";
            stmt = mStatements.add(STMT_MOVE, sql);
        }
        stmt.bindLong(1, position);
        // Get a pair of ID's separated by a '/'
        if (fetchBookIdAndPosition(stmt)) {
            return true;
        } else {
            long posSav = mPosition;
            mPosition = position;
            if (moveNext() || movePrev()) {
                return true;
            } else {
                mPosition = posSav;
                return false;
            }
        }
    }

    /**
     * Move to the first row.
     *
     * @return {@code true}if successful
     */
    @SuppressWarnings("unused")
    public boolean moveFirst() {
        mPosition = -1;
        mBookId = 0;
        return moveNext();
    }

    /**
     * Move to the last row.
     *
     * @return {@code true}if successful
     */
    @SuppressWarnings("unused")
    public boolean moveLast() {
        mPosition = Long.MAX_VALUE;
        mBookId = 0;
        return movePrev();
    }

    /**
     * Get the underlying row position (row ID).
     *
     * @return position
     */
    @SuppressWarnings("unused")
    public long getPosition() {
        return mPosition;
    }

    /**
     * Get the position of the current record in the table.
     *
     * @return position
     */
    @SuppressWarnings("unused")
    public long getAbsolutePosition() {
        SynchronizedStatement stmt = mStatements.get(STMT_POSITION);
        if (stmt == null) {
            String sql = "SELECT COUNT(*) FROM " + mTable.ref()
                         + " WHERE " + mTable.dot(DBDefinitions.DOM_PK_ID) + "<=?";
            stmt = mStatements.add(STMT_POSITION, sql);
        }
        stmt.bindLong(1, mPosition);
        return stmt.count();
    }

    /**
     * Release resource-consuming stuff.
     */
    @Override
    public void close() {
        mCloseWasCalled = true;
        mStatements.close();
    }

    /**
     * Cleanup the underlying table.
     * It's a temp table, but cleaning up prevents 'old' tables hanging around far to long.
     */
    public void deleteData() {
        mTable.drop(mSyncedDb);
        mTable.clear();
        // force to null, so if we access the table again, we'll crash on purpose!
        //noinspection ConstantConditions
        mTable = null;
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
            Logger.warn(this, "finalize", "mCloseWasCalled=false; calling close() now");
            close();
        }
        super.finalize();
    }
}
