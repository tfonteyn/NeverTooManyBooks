package com.eleybourn.bookcatalogue.booklist;

import android.database.sqlite.SQLiteDoneException;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.SqlStatementManager;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition.TableTypes;
import com.eleybourn.bookcatalogue.debug.Tracker;

/**
 * Class to provide a simple interface into a temporary table containing a list of book IDs on
 * the same order as an underlying book list.
 *
 * @author pjw
 */
public class FlattenedBooklist implements AutoCloseable {
    /** Name for the 'next' statement */
    private static final String NEXT_STMT_NAME = "next";
    /** Name for the 'prev' statement */
    private static final String PREV_STMT_NAME = "prev";
    /** Name for the 'move-to' statement */
    private static final String MOVE_STMT_NAME = "move";
    /** Name for the 'absolute-position' statement */
    private static final String POSITION_STMT_NAME = "position";

    /** Underlying temporary table definition */
    private TableDefinition mTable;
    /** Connection to db; we need this to keep the table alive */
    private SynchronizedDb mSyncedDb;
    /** Default position (before start) */
    private long mPosition = -1;
    /** Book ID from the currently selected row */
    private long mBookId = 0;
    /** Collection of statements compiled for this object */
    private SqlStatementManager mStatements;

    /**
     * Constructor
     *
     * @param db    Database connection
     * @param table Table definition
     */
    FlattenedBooklist(final @NonNull SynchronizedDb db, final @NonNull TableDefinition table) {
        Tracker.enterFunction(this, "FlattenedBooklist(final @NonNull SynchronizedDb db [not logged], final @NonNull TableDefinition table)", table);
        init(db, table.clone());
        Tracker.exitFunction(this, "FlattenedBooklist(final @NonNull SynchronizedDb db, final @NonNull TableDefinition table)");
    }

    /**
     * Constructor
     *
     * @param db        Database connection
     * @param tableName Name of underlying table
     */
    public FlattenedBooklist(final @NonNull CatalogueDBAdapter db, final @NonNull String tableName) {
        Tracker.enterFunction(this, "FlattenedBooklist(final @NonNull CatalogueDBAdapter db [not logged], final @NonNull String tableName)", tableName);

        TableDefinition flat = DatabaseDefinitions.TBL_ROW_NAVIGATOR_FLATTENED.clone();
        flat.setName(tableName);
        flat.setType(TableTypes.Temporary); //RELEASE Make sure is TEMPORARY

        init(db.getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing(), flat);
        Tracker.exitFunction(this, "FlattenedBooklist(final @NonNull CatalogueDBAdapter db, final @NonNull String tableName)");
    }

    /**
     * Shared constructor utility routine.Save the passed values.
     *
     * @param db    Database connection
     * @param table Table definition
     */
    private void init(final @NonNull SynchronizedDb db, final @NonNull TableDefinition table) {
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
     * Release resource-consuming stuff
     */
    @Override
    public void close() {
        mStatements.close();
    }

    /**
     * Cleanup the underlying table
     */
    public void deleteData() {
        mTable.drop(mSyncedDb);
        mTable.close();
    }

    /**
     * Passed a statement update the 'current' row details based on the columns returned
     */
    private boolean fetchBookIdAndPosition(final @NonNull SynchronizedStatement stmt) {
        // Get a pair of ID's separated by a '/'
        try {
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
     */
    public boolean exists() {
        return mTable.exists(mSyncedDb);
    }

    /**
     * Move to the next book row
     *
     * @return <tt>true</tt>if successful
     */
    public boolean moveNext() {
        SynchronizedStatement stmt = mStatements.get(NEXT_STMT_NAME);
        if (stmt == null) {
            String sql = "SELECT " + mTable.dot(DatabaseDefinitions.DOM_PK_ID) + "|| '/' || " + mTable.dot(DatabaseDefinitions.DOM_FK_BOOK_ID)
                    + " FROM " + mTable.ref()
                    + " WHERE " + mTable.dot(DatabaseDefinitions.DOM_PK_ID) + " > ? AND " + mTable.dot(DatabaseDefinitions.DOM_FK_BOOK_ID) + " <> Coalesce(?,-1)"
                    + " ORDER BY " + mTable.dot(DatabaseDefinitions.DOM_PK_ID) + " ASC LIMIT 1";
            stmt = mStatements.add(NEXT_STMT_NAME, sql);
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
     * Move to the previous book row
     *
     * @return <tt>true</tt>if successful
     */
    public boolean movePrev() {
        SynchronizedStatement stmt = mStatements.get(PREV_STMT_NAME);
        if (stmt == null) {
            String sql = "SELECT " + mTable.dot(DatabaseDefinitions.DOM_PK_ID) + "|| '/' || " + mTable.dot(DatabaseDefinitions.DOM_FK_BOOK_ID)
                    + " FROM " + mTable.ref()
                    + " WHERE " + mTable.dot(DatabaseDefinitions.DOM_PK_ID) + " < ? AND " + mTable.dot(DatabaseDefinitions.DOM_FK_BOOK_ID) + " <> Coalesce(?,-1)"
                    + " ORDER BY " + mTable.dot(DatabaseDefinitions.DOM_PK_ID) + " DESC LIMIT 1";
            stmt = mStatements.add(PREV_STMT_NAME, sql);
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
     * @return <tt>true</tt>if successful
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean moveTo(final int pos) {
        SynchronizedStatement stmt = mStatements.get(MOVE_STMT_NAME);
        if (stmt == null) {
            String sql = "SELECT " + mTable.dot(DatabaseDefinitions.DOM_PK_ID) + "|| '/' || " + mTable.dot(DatabaseDefinitions.DOM_FK_BOOK_ID) +
                    " FROM " + mTable.ref() + " WHERE " + mTable.dot(DatabaseDefinitions.DOM_PK_ID) + "=?";
            stmt = mStatements.add(MOVE_STMT_NAME, sql);
        }
        stmt.bindLong(1, pos);
        // Get a pair of ID's separated by a '/'
        if (fetchBookIdAndPosition(stmt)) {
            return true;
        } else {
            long posSav = mPosition;
            mPosition = pos;
            if (moveNext() || movePrev()) {
                return true;
            } else {
                mPosition = posSav;
                return false;
            }
        }
    }

    /**
     * Move to the first row
     *
     * @return <tt>true</tt>if successful
     */
    @SuppressWarnings("unused")
    public boolean moveFirst() {
        mPosition = -1;
        mBookId = 0;
        return moveNext();
    }

    /**
     * Move to the last row
     *
     * @return <tt>true</tt>if successful
     */
    @SuppressWarnings("unused")
    public boolean moveLast() {
        mPosition = Long.MAX_VALUE;
        mBookId = 0;
        return movePrev();
    }

    /**
     * Get the underlying row position (row ID)
     */
    public long getPosition() {
        return mPosition;
    }

    /**
     * Get the position of the current record in the table
     *
     * @return position
     */
    @SuppressWarnings("unused")
    public long getAbsolutePosition() {
        SynchronizedStatement stmt = mStatements.get(POSITION_STMT_NAME);
        if (stmt == null) {
            String sql = "SELECT COUNT(*) FROM " + mTable.ref() +
                    " WHERE " + mTable.dot(DatabaseDefinitions.DOM_PK_ID) + " <= ?";
            stmt = mStatements.add(POSITION_STMT_NAME, sql);
        }
        stmt.bindLong(1, mPosition);
        return stmt.count();
    }

    /**
     * Cleanup the statements
     */
    public void finalize() {
        close();
    }
}