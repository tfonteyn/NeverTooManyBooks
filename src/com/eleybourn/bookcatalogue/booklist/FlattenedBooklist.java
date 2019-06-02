package com.eleybourn.bookcatalogue.booklist;

import android.database.sqlite.SQLiteDoneException;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.SqlStatementManager;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition.TableTypes;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * Class to provide a simple interface into a temporary table containing a list of book IDs in
 * the same order as an underlying book list.
 *
 * @author pjw
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

    /** Underlying temporary table definition. */
    private TableDefinition mTable;
    /** The underlying database. We need this to keep the table alive. */
    private SynchronizedDb mSyncedDb;
    /** Default position (before start). */
    private long mPosition = -1;
    /** Book ID from the currently selected row. */
    private long mBookId;
    /** Collection of statements compiled for this object. */
    private SqlStatementManager mStatements;
    /** DEBUG: Indicates close() has been called. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     *
     * @param db    Database connection
     * @param table Table definition
     */
    FlattenedBooklist(@NonNull final SynchronizedDb db,
                      @NonNull final TableDefinition table) {
        init(db, table.clone());
    }

    /**
     * Constructor.
     *
     * @param db        Database connection
     * @param tableName Name of underlying table
     */
    public FlattenedBooklist(@NonNull final DAO db,
                             @NonNull final String tableName) {
        TableDefinition flat = DBDefinitions.TBL_ROW_NAVIGATOR_FLATTENED.clone();
        flat.setName(tableName);
        //RELEASE MUST use TableTypes.Temporary
        flat.setType(TableTypes.Temporary);

        init(db.getUnderlyingDatabase(), flat);
    }

    /**
     * Shared constructor.
     *
     * @param db    Database connection
     * @param table Table definition
     */
    private void init(@NonNull final SynchronizedDb db,
                      @NonNull final TableDefinition table) {
        mSyncedDb = db;
        mTable = table;
        mStatements = new SqlStatementManager(mSyncedDb);
    }

    @NonNull
    public TableDefinition getTable() {
        return mTable;
    }

    public long getBookId() {
        return mBookId;
    }

    /**
     * Passed a statement update the 'current' row details based on the columns returned.
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
        } catch (SQLiteDoneException ignore) {
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
                    + mTable.dot(DBDefinitions.DOM_PK_ID) + "|| '/' || "
                    + mTable.dot(DBDefinitions.DOM_FK_BOOK_ID)
                    + " FROM " + mTable.ref()
                    + " WHERE " + mTable.dot(DBDefinitions.DOM_PK_ID) + ">?"
                    + " AND " + mTable.dot(DBDefinitions.DOM_FK_BOOK_ID) + "<>Coalesce(?,-1)"
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
                    + mTable.dot(DBDefinitions.DOM_PK_ID) + "|| '/' || "
                    + mTable.dot(DBDefinitions.DOM_FK_BOOK_ID)
                    + " FROM " + mTable.ref()
                    + " WHERE " + mTable.dot(DBDefinitions.DOM_PK_ID) + "<?"
                    + " AND " + mTable.dot(DBDefinitions.DOM_FK_BOOK_ID) + "<>Coalesce(?,-1)"
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
     * Move to the specified book row, based on the row ID, not the book ID or row number.
     * The row ID should be the row number in the table, including header-related rows.
     *
     * @return {@code true}if successful
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean moveTo(final int position) {
        SynchronizedStatement stmt = mStatements.get(STMT_MOVE);
        if (stmt == null) {
            String sql = "SELECT "
                    + mTable.dot(DBDefinitions.DOM_PK_ID) + "|| '/' || "
                    + mTable.dot(DBDefinitions.DOM_FK_BOOK_ID)
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
     * @return the underlying row position (row ID).
     */
    public long getPosition() {
        return mPosition;
    }

    /**
     * @return the position of the current record in the table
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
     * It's a temp table, but cleaning up properly is a good thing.
     */
    public void deleteData() {
        if (mTable != null) {
            mTable.drop(mSyncedDb);
            mTable.clear();
            mTable = null;
        }
    }

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
