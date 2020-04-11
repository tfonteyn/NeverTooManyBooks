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

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

import static com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder.ListRebuildMode;
import static com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder.PREF_REBUILD_ALWAYS_COLLAPSED;
import static com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder.PREF_REBUILD_ALWAYS_EXPANDED;
import static com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder.PREF_REBUILD_PREFERRED_STATE;
import static com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder.PREF_REBUILD_SAVED_STATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_LIST_VIEW_ROW_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_EXPANDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_VISIBLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BL_ROW_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TMP_TBL_BOOK_LIST_ROW_STATE;

/**
 * Handles an in-memory lookup table to match rows in the book list to the status of the row,
 * and a backend table to store enough information in the database to rebuild after an app restart.
 * <p>
 * This is used to match a specific book (or other row in result set) to a position directly
 * without having to scan the database.
 * i.o.w. this table is a 1:1 mapping with the list presented on screen to the user.
 * i.o.w. each row in the list-view is mapped to a row in this table, in the exact same order.
 * One caveat: rowId's are 1..n based while the list-view rows are 0..n-1 based.
 * We use rowId everywhere <strong>except where directly interacting with the view</strong>
 *
 * <ul>Naming:
 *      <li>expandABC: entry points into this class to expand/collapse node</li>
 *      <li>updateABC: private methods that write to the in-memory table</li>
 *      <li>saveABC: private methods that write to the database</li>
 * </ul>
 */
public class RowStateDAO {

    /** Log tag. */
    private static final String TAG = "RowStateDAO";

    /** Statement cache name. */
    private static final String STMT_DEL_ALL_NODES = "dNS";
    /** Statement cache name. */
    private static final String STMT_SAVE_ALL_NODES = "sNS";
    /** Statement cache name. */
    private static final String STMT_DEL_NODES_BETWEEN = "dNSBetween";
    /** Statement cache name. */
    private static final String STMT_SAVE_NODES_BETWEEN = "sNSBetween";

    /** Statement cache name. */
    private static final String STMT_UPDATE_NODE = "uNS";
    /** Statement cache name. */
    private static final String STMT_UPDATE_NODES_BETWEEN_1 = "uNSBetween1";
    /** Statement cache name. */
    private static final String STMT_UPDATE_NODES_BETWEEN_2 = "uNSBetween2";

    /** Statement cache name. */
    private static final String STMT_GET_NEXT_NODE_AT_SAME_LEVEL = "next";
    /** Statement cache name. */
    private static final String STMT_COUNT_VIS_ROWS_BEFORE = "cntVisRowsBefore";
    /** Statement cache name. */
    private static final String STMT_COUNT_VIS_ROWS = "cntVisRows";

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;
    private static final String PURGE_ALL_SQL =
            "DELETE FROM " + DBDefinitions.TBL_BOOK_LIST_NODE_STATE;
    /** This is a reference only. Cleanup is done by the owner of this object. */
    @NonNull
    private final SynchronizedDb mSyncedDb;
    @NonNull
    private final SqlStatementManager mStatementManager;
    /** The table represented by this class. */
    @NonNull
    private final TableDefinition mTable;

    private final long mBookshelfId;
    @NonNull
    private final BooklistStyle mStyle;
    /** DEBUG: Indicates close() has been called. Also see {@link Closeable#close()}. */
    private boolean mCloseWasCalled;

    /**
     * Constructor. Defines, but does not create the table. See {@link #build}.
     *
     * <strong>Note:</strong> the style/bookshelf the booklist is displaying, determines
     * the list-table and hence the RowStateDAO.
     *
     * @param syncedDb   database
     * @param instanceId unique id to be used as suffix on the table name.
     * @param style      Booklist style to use;
     * @param bookshelf  to use
     */
    RowStateDAO(@NonNull final SynchronizedDb syncedDb,
                final int instanceId,
                @NonNull final BooklistStyle style,
                @NonNull final Bookshelf bookshelf) {

        mSyncedDb = syncedDb;
        mBookshelfId = bookshelf.getId();
        mStyle = style;

        mTable = new TableDefinition(TMP_TBL_BOOK_LIST_ROW_STATE);
        mTable.setName(mTable.getName() + instanceId);
        mStatementManager = new SqlStatementManager(mSyncedDb, TAG + "|" + instanceId);
    }

    /**
     * Purge <strong>all</strong> Booklist node state data.
     */
    public static void clearAll() {
        try (DAO db = new DAO(TAG)) {
            db.getSyncDb().execSQL(PURGE_ALL_SQL);
        }
    }

    /**
     * Build the table according to the desired listState.
     *
     * <strong>Note:</strong> called once at creation time, hence the statements used
     * here are not cached.
     *
     * @param context   Current context
     * @param listTable the list-table to be referenced
     * @param listState to use
     */
    void build(@NonNull final Context context,
               @NonNull final TableDefinition listTable,
               @ListRebuildMode final int listState) {

        // we drop the newly defined table just in case there is a leftover physical table
        // with the same name (due to previous crash maybe).
        mSyncedDb.drop(mTable.getName());

        // Link with the list table using the actual row ID
        // We need this for joins, but it's not enforced with constraints.
        mTable.addReference(listTable, DBDefinitions.DOM_FK_BL_ROW_ID);

        // indices will be created after the table is populated.
        mTable.create(mSyncedDb, true, false);

        // the table is ordered at insert time, so the row id *is* the order.
        final String baseSql = "INSERT INTO " + mTable.getName()
                               + " (" + KEY_FK_BL_ROW_ID
                               + ',' + KEY_BL_NODE_KEY

                               + ',' + KEY_BL_NODE_LEVEL
                               + ',' + KEY_BL_NODE_GROUP

                               + ',' + KEY_BL_NODE_VISIBLE
                               + ',' + KEY_BL_NODE_EXPANDED
                               + ')'
                               + " SELECT DISTINCT "
                               + listTable.dot(KEY_PK_ID)
                               + ',' + listTable.dot(KEY_BL_NODE_KEY)

                               + ',' + listTable.dot(KEY_BL_NODE_LEVEL)
                               + ',' + listTable.dot(KEY_BL_NODE_GROUP)
                               + "\n";

        // On first-time builds, get the Preferences-based list
        switch (listState) {
            case PREF_REBUILD_ALWAYS_EXPANDED: {
                final String sql = baseSql
                                   // KEY_BL_NODE_VISIBLE: all visible
                                   + ",1"
                                   // KEY_BL_NODE_EXPANDED: all expanded
                                   + ",1"
                                   + " FROM " + listTable.ref()
                                   + " ORDER BY " + listTable.dot(KEY_PK_ID);

                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    final int rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                        Log.d(TAG, "PREF_REBUILD_ALWAYS_EXPANDED"
                                   + "|rowsUpdated=" + rowsUpdated
                                   + "|sql=" + sql);
                    }
                }
                break;
            }
            case PREF_REBUILD_ALWAYS_COLLAPSED: {
                final String sql = baseSql
                                   // KEY_BL_NODE_VISIBLE:
                                   + ",CASE"
                                   // level 1 visible
                                   + " WHEN " + KEY_BL_NODE_LEVEL + "=1 THEN 1"
                                   // and all other levels invisible.
                                   + " ELSE 0"
                                   + " END"
                                   // 'AS' for SQL readability/debug only
                                   + " AS " + KEY_BL_NODE_VISIBLE

                                   // KEY_BL_NODE_EXPANDED: all nodes collapsed
                                   + ",0"
                                   + "\n"
                                   + " FROM " + listTable.ref()
                                   + " ORDER BY " + listTable.dot(KEY_PK_ID);

                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    final int rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                        Log.d(TAG, "PREF_REBUILD_ALWAYS_COLLAPSED"
                                   + "|rowsUpdated=" + rowsUpdated
                                   + "|sql=" + sql);
                    }
                }
                break;
            }
            case PREF_REBUILD_SAVED_STATE: {
                // SQL to rebuild with state preserved.
                // IMPORTANT: we concat the sql with the bookshelf and style id's.
                // should bind those obviously, but current (and non-functional) mRebuildStmts
                // requires statements without parameters.
                final String sql =
                        baseSql
                        // KEY_BL_NODE_VISIBLE
                        + ",CASE"
                        // Level 1 is always visible
                        + " WHEN " + listTable.dot(KEY_BL_NODE_LEVEL) + "=1 THEN 1"
                        // if the row is not present, hide it.
                        + " WHEN " + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_KEY) + " IS NULL"
                        + /* */ " THEN 0"
                        // All rows present are visible.
                        + /* */ " ELSE 1"
                        + " END"
                        // 'AS' for SQL readability/debug only
                        + " AS " + KEY_BL_NODE_VISIBLE
                        + "\n"

                        // KEY_BL_NODE_EXPANDED
                        + ",CASE"
                        // if the row is not present, collapse it.
                        + " WHEN " + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_KEY) + " IS NULL"
                        + /* */ " THEN 0"
                        //  Otherwise use the stored state
                        + /* */ " ELSE " + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_EXPANDED)
                        + " END"
                        // 'AS' for SQL readability/debug only
                        + " AS " + KEY_BL_NODE_EXPANDED
                        + "\n"

                        + " FROM " + listTable.ref()
                        // note this is a pure OUTER JOIN and not a where-clause
                        // We need to get *ALL* rows from listTable.
                        + " LEFT OUTER JOIN " + TBL_BOOK_LIST_NODE_STATE.ref()
                        + " ON "
                        + listTable.dot(KEY_BL_NODE_LEVEL)
                        + '=' + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_LEVEL)
                        + " AND "
                        + listTable.dot(KEY_BL_NODE_KEY)
                        + '=' + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_KEY)

                        + " AND " + TBL_BOOK_LIST_NODE_STATE.dot(KEY_FK_BOOKSHELF) + "=?"
                        + " AND " + TBL_BOOK_LIST_NODE_STATE.dot(KEY_FK_STYLE) + "=?"

                        + " ORDER BY " + listTable.dot(KEY_PK_ID);

                // Use already-defined SQL for preserve state.
                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    stmt.bindLong(1, mBookshelfId);
                    stmt.bindLong(2, mStyle.getId());
                    final int rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                        Log.d(TAG, "PREF_REBUILD_SAVED_STATE"
                                   + "|rowsUpdated=" + rowsUpdated
                                   + "|sql=" + sql);
                    }
                }
                break;
            }
            case PREF_REBUILD_PREFERRED_STATE: {
                // Run the INSERT first, the state does not matter, for simplicity expand all
                final String sql = baseSql + ",1,1 FROM " + listTable.ref()
                                   + " ORDER BY " + listTable.dot(KEY_PK_ID);
                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    final int rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                        Log.d(TAG, "PREF_REBUILD_PREFERRED_STATE"
                                   + "|rowsUpdated=" + rowsUpdated
                                   + "|sql=" + sql);
                    }
                }

                // Now set the preferred expand/visibility status.
                expandAllNodes(mStyle.getTopLevel(context), false);
                break;
            }

            default:
                throw new UnexpectedValueException(listState);
        }

        // Create the indexes we need.
        // Doing this AFTER the data got inserted has proven to be somewhat faster.
        mTable.createIndices(mSyncedDb);

        mSyncedDb.analyze(mTable);
    }

    @NonNull
    public TableDefinition getTable() {
        return mTable;
    }

    /**
     * Gets a 'window' on the result set, starting at 'position' and 'size' rows.
     * We only retrieve visible rows.
     *
     * @param position the offset
     * @param pageSize the amount of results maximum to return (SQL LIMIT clause)
     *
     * @return a list cursor starting at a given offset, using a given limit.
     */
    @NonNull
    Cursor getOffsetCursor(@NonNull final TableDefinition listTable,
                           final int position,
                           @SuppressWarnings("SameParameterValue") final int pageSize) {

        // Build the SQL, adding an alias for TMP_TBL_BOOK_LIST_ROW_STATE#KEY_PK_ID
        final String sql = "SELECT "
                           + Csv.join(listTable.getDomains(),
                                      domain -> listTable.dot(domain.getName()))
                           + ',' + mTable.dotAs(KEY_PK_ID, KEY_BL_LIST_VIEW_ROW_ID)
                           + " FROM " + listTable.ref() + listTable.join(mTable)
                           + " WHERE " + mTable.dot(KEY_BL_NODE_VISIBLE) + "=1"
                           + " ORDER BY " + mTable.dot(KEY_PK_ID)
                           + " LIMIT " + pageSize + " OFFSET " + position;

        return mSyncedDb.rawQuery(sql, null);
    }

    /**
     * Given the rowId, return the actual list position for a row,
     * <strong>taking into account invisible rows</strong> and the fact that a position == rowId -1
     * <p>
     * Developer note: we need to count the visible rows between the start of the list,
     * and the given row, to determine the ACTUAL row we want.
     *
     * @param node to check
     *
     * @return Actual (and visible) list position.
     */
    int getListPosition(@NonNull final Node node) {
        if (node.isVisible) {
            // If the specified row is visible, then the count _is_ the position.
            return countVisibleRowsBefore(node.rowId);
        } else {
            // otherwise it's the previous visible row == count-1 (or the top row)
            final int count = countVisibleRowsBefore(node.rowId);
            return count > 0 ? count - 1 : 0;
        }
    }

    /**
     * Count the number of visible rows <strong>before</strong> the specified one.
     *
     * @return count
     */
    private int countVisibleRowsBefore(final long rowId) {
        SynchronizedStatement stmt = mStatementManager.get(STMT_COUNT_VIS_ROWS_BEFORE);
        if (stmt == null) {
            stmt = mStatementManager.add(STMT_COUNT_VIS_ROWS_BEFORE,
                                         "SELECT COUNT(*) FROM " + mTable.getName()
                                         + " WHERE " + KEY_BL_NODE_VISIBLE + "=1"
                                         + " AND " + KEY_PK_ID + "<?");
        }
        final int count;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, rowId);
            count = (int) stmt.count();
        }
        return count;
    }

    /**
     * Count the number of visible rows.
     *
     * @return count
     */
    int countVisibleRows() {
        SynchronizedStatement stmt = mStatementManager.get(STMT_COUNT_VIS_ROWS);
        if (stmt == null) {
            stmt = mStatementManager.add(STMT_COUNT_VIS_ROWS,
                                         "SELECT COUNT(*) FROM " + mTable.getName()
                                         + " WHERE " + KEY_BL_NODE_VISIBLE + "=1");
        }
        final int count;
        // no params here, so don't need synchronized... but leave as reminder
        //        synchronized (stmt) {
        count = (int) stmt.count();
        //        }
        return count;
    }

    /**
     * Ensure al nodes up to the root node for the given (book) node (inclusive) are visible.
     *
     * @param bookNode we want to become visible
     */
    void ensureNodeIsVisible(@NonNull final Node bookNode) {

        final String sql = "SELECT " + KEY_PK_ID + " FROM " + mTable.getName()
                           // follow the node hierarchy
                           + " WHERE " + KEY_BL_NODE_KEY + " LIKE ?"
                           // we'll loop for all levels
                           + " AND " + KEY_BL_NODE_LEVEL + "=?";

        String nodeKey = bookNode.key;

        // levels are 1.. based; start with lowest level above books, working up to root.
        final Deque<Pair<Long, Integer>> nodes = new ArrayDeque<>();
        for (int level = bookNode.level - 1; level >= 1; level--) {
            try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{
                    nodeKey + "%", String.valueOf(level)})) {
                while (cursor.moveToNext()) {
                    final long rowId = cursor.getLong(0);
                    nodes.push(new Pair<>(rowId, level));
                }
            }
            nodeKey = nodeKey.substring(0, nodeKey.lastIndexOf('/'));
        }

        // Now process the collected nodes from the root downwards.
        for (Pair<Long, Integer> node : nodes) {
            // setNode(nodeRowId, level,
            //noinspection ConstantConditions
            setNode(node.first, node.second,
                    // Expand (and make visible) the given node
                    true,
                    // do this for only ONE level below the current node
                    1);
        }
    }

    /**
     * Get all positions, and the row info for that position,
     * at which the specified book appears.
     *
     * @param bookId the book to find
     *
     * @return Array of {@link Node}. Can be empty, but never {@code null}.
     */
    @NonNull
    ArrayList<Node> getNodesForBookId(@NonNull final TableDefinition listTable,
                                      final long bookId) {
        final String sql = "SELECT " + Node.getColumns(mTable)
                           + " FROM " + listTable.ref() + listTable.join(mTable)
                           + " WHERE " + listTable.dot(KEY_FK_BOOK) + "=?";

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(bookId)})) {
            ArrayList<Node> rows = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                final Node node = new Node(cursor);
                node.setListPosition(getListPosition(node));
                rows.add(node);
            }
            return rows;
        }
    }

    /**
     * Get the node for the the given row id (any row, not limited to books).
     * It will contain the current state of the node as stored in the database.
     *
     * @param rowId to get
     *
     * @return the node
     */
    @NonNull
    Node getNodeByNodeId(final long rowId) {
        final String sql = "SELECT " + Node.getColumns(mTable)
                           + " FROM " + mTable.ref()
                           + " WHERE " + mTable.dot(KEY_PK_ID) + "=?";

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(rowId)})) {
            if (cursor.moveToFirst()) {
                return new Node(cursor);
            } else {
                throw new IllegalStateException("rowId not found: " + rowId);
            }
        }
    }

    /**
     * Expand or collapse <strong>all</strong> nodes.
     *
     * @param topLevel the desired top-level which must be kept visible
     * @param expand   the state to apply to levels 'below' the topLevel (level > topLevel),
     */
    void expandAllNodes(@IntRange(from = 1) final int topLevel,
                        final boolean expand) {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            final long t0 = System.nanoTime();

            // code could be condensed somewhat, but leaving as-is making it easier to read.
            if (topLevel == 1) {
                if (expand) {
                    // expand and show all levels
                    updateNodesForLevel(">=", topLevel, true, true);
                } else {
                    // collapse level 1, but keep it visible.
                    updateNodesForLevel("=", topLevel, false, true);
                    // collapse and hide all other levels
                    updateNodesForLevel(">", topLevel, false, false);
                }

            } else /* topLevel > 1 */ {
                // always expand and show levels less than the defined top-level.
                updateNodesForLevel("<", topLevel, true, true);

                // collapse the specified level, but keep it visible.
                updateNodesForLevel("=", topLevel, false, true);

                // set the desired state to all other levels
                updateNodesForLevel(">", topLevel, expand, expand);
            }

            // Store the state of all nodes.
            saveAllNodes();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                Log.d(TAG, "expandAllNodes"
                           + "|completed in " + (System.nanoTime() - t0) / NANO_TO_MILLIS + " ms");
            }

            if (txLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * Update the status/visibility of all nodes for the given level.
     *
     * @param levelOperand one of "<", "=", ">", "<=" or ">="
     * @param level        the level
     * @param expand       state to set
     * @param visible      visibility to set
     */
    private void updateNodesForLevel(@NonNull final CharSequence levelOperand,
                                     @IntRange(from = 1) final int level,
                                     final boolean expand,
                                     final boolean visible) {
        if (BuildConfig.DEBUG /* always */) {
            // developer sanity check
            if (!"< = > <= >=".contains(levelOperand)) {
                throw new IllegalArgumentException();
            }
        }

        //Note to self: levelOperand is concatenated, so do not cache this stmt.
        final String sql = "UPDATE " + mTable.getName() + " SET "
                           + KEY_BL_NODE_EXPANDED + "=?," + KEY_BL_NODE_VISIBLE + "=?"
                           + " WHERE " + KEY_BL_NODE_LEVEL + levelOperand + "?";

        final int rowsUpdated;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
            stmt.bindBoolean(1, expand);
            stmt.bindBoolean(2, visible);
            stmt.bindLong(3, level);
            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
            Log.d(TAG, "updateNodesForLevel"
                       + "|level" + levelOperand + level
                       + "|expand=" + expand
                       + "|visible=" + visible
                       + "|rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * Save the state for all nodes to permanent storage.
     * We only store visible nodes and their expansion state.
     *
     * <strong>Note:</strong> always use the current bookshelf/style
     */
    void saveAllNodes() {
        Synchronizer.SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            SynchronizedStatement stmt = mStatementManager.get(STMT_DEL_ALL_NODES);
            if (stmt == null) {
                stmt = mStatementManager.add(
                        STMT_DEL_ALL_NODES,
                        // delete all rows for the current bookshelf/style
                        "DELETE FROM " + TBL_BOOK_LIST_NODE_STATE.getName()
                        + " WHERE " + KEY_FK_BOOKSHELF + "=?"
                        + " AND " + KEY_FK_STYLE + "=?");
            }

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, mBookshelfId);
                stmt.bindLong(2, mStyle.getId());

                final int rowsDeleted = stmt.executeUpdateDelete();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                    Log.d(TAG, "saveAllNodes"
                               + "|rowsDeleted=" + rowsDeleted);
                }
            }

            stmt = mStatementManager.get(STMT_SAVE_ALL_NODES);
            if (stmt == null) {
                stmt = mStatementManager.add(STMT_SAVE_ALL_NODES, getNodesInsertSql());
            }

            // Read all visible nodes, and send them to the permanent table.
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, mBookshelfId);
                stmt.bindLong(2, mStyle.getId());

                final int rowsUpdated = stmt.executeUpdateDelete();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                    Log.d(TAG, "saveAllNodes"
                               + "|rowsUpdated=" + rowsUpdated);
                }
            }
            if (txLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
//        } catch (@NonNull final SQLiteConstraintException e) {
//            if (BuildConfig.DEBUG /* always */) {
//                // DEBUG ONLY!
//                // This protects the developer when changing the structure
//                // somewhat irresponsibly...
//                mSyncedDb.execSQL(PURGE_ALL_SQL);
//                Logger.error(App.getAppContext(), TAG, e);
//            }
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * Expand/collapse the passed node.
     * <br><br>
     * The node will always be kept visible.
     * <p>
     * If the resulting state of the node is:<br>
     * 'Expand': we set the children to the desired child state/visibility
     * (recurse up to the desired max child level)<br>
     * 'Collapse': all children will be set to Collapse/hidden (recurse ALL child levels)
     *
     * @param nodeRowId          of the node in the list
     * @param nodeLevel          the level of the node
     * @param expandNode         the state to set for this node
     * @param relativeChildLevel up to and including this (relative to nodeLevel!) child level.
     */
    void setNode(final long nodeRowId,
                 final int nodeLevel,
                 final boolean expandNode,
                 @IntRange(from = 0) final int relativeChildLevel) {

        if (relativeChildLevel > mStyle.getGroupCount()) {
            throw new IllegalArgumentException(
                    "mStyle.getGroupCount()=" + mStyle.getGroupCount()
                    + "|relativeChildLevel=" + relativeChildLevel);
        }

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            // expand/collapse the specified row, but always keep it visible.
            updateNode(nodeRowId, expandNode, true);

            // Find the next row ('after' the given row) at the given level (or lower).
            long endRowExcl = findNextNode(nodeRowId, nodeLevel);
            // are there actually any rows in between?
            if ((endRowExcl - nodeRowId) > 1) {
                // update the rows between the node row itself, and the endRow
                if (expandNode) {
                    // use the desired child state/level
                    updateNodesBetween(nodeRowId, endRowExcl, true,
                                       nodeLevel + relativeChildLevel);
                } else {
                    // collapsing the node, so just collapse/hide all children.
                    updateNodesBetween(nodeRowId, endRowExcl, false,
                                       Integer.MAX_VALUE);
                }
            }

            // Store the state of these nodes.
            saveNodesBetween(nodeLevel, nodeRowId, endRowExcl);

            if (txLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * Find the next row ('after' the given row) at the given level (or lower).
     *
     * @param rowId from where to start looking
     * @param level level to look for
     *
     * @return row id
     */
    private long findNextNode(final long rowId,
                              final int level) {

        SynchronizedStatement stmt = mStatementManager.get(STMT_GET_NEXT_NODE_AT_SAME_LEVEL);
        if (stmt == null) {
            stmt = mStatementManager.add(
                    STMT_GET_NEXT_NODE_AT_SAME_LEVEL,
                    "SELECT " + KEY_PK_ID + " FROM " + mTable.getName()
                    + " WHERE " + KEY_PK_ID + ">?" + " AND " + KEY_BL_NODE_LEVEL + "<=?"
                    + " ORDER BY " + KEY_PK_ID + " LIMIT 1");
        }

        final long nextRowId;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, rowId);
            stmt.bindLong(2, level);
            nextRowId = stmt.simpleQueryForLongOrZero();
        }

        // if there was no next node, use the end of the list.
        if (nextRowId <= 0) {
            return Long.MAX_VALUE;
        }
        return nextRowId;
    }

    /**
     * Update a single node.
     *
     * @param rowId   the row/node to update
     * @param expand  state to set
     * @param visible visibility to set
     */
    private void updateNode(final long rowId,
                            final boolean expand,
                            @SuppressWarnings("SameParameterValue") final boolean visible) {
        SynchronizedStatement stmt = mStatementManager.get(STMT_UPDATE_NODE);
        if (stmt == null) {
            stmt = mStatementManager.add(
                    STMT_UPDATE_NODE,
                    "UPDATE " + mTable.getName() + " SET "
                    + KEY_BL_NODE_EXPANDED + "=?" + ',' + KEY_BL_NODE_VISIBLE + "=?"
                    + " WHERE " + KEY_PK_ID + "=?");
        }

        final int rowsUpdated;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindBoolean(1, expand);
            stmt.bindBoolean(2, visible);
            stmt.bindLong(3, rowId);
            rowsUpdated = stmt.executeUpdateDelete();
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
            Log.d(TAG, "updateNode"
                       + "|rowId=" + rowId
                       + "|expand=" + expand
                       + "|visible=" + visible
                       + "|rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * Update the nodes <strong>between</strong> the two given rows;
     * i.e. <strong>excluding</strong> the start and end row.
     *
     * @param startRowExcl     between this row
     * @param endRowExcl       and this row
     * @param expandAndVisible expand/visibility to set
     * @param level            up to and including this level.
     */
    private void updateNodesBetween(final long startRowExcl,
                                    final long endRowExcl,
                                    final boolean expandAndVisible,
                                    final int level) {
        // sanity check
        final int maxLevel = Math.max(level, mStyle.getGroupCount() - 1);

        // handle all levels except the lowest level.
        SynchronizedStatement stmt = mStatementManager.get(STMT_UPDATE_NODES_BETWEEN_1);
        if (stmt == null) {
            stmt = mStatementManager.add(
                    STMT_UPDATE_NODES_BETWEEN_1,
                    "UPDATE " + mTable.getName() + " SET "
                    + KEY_BL_NODE_VISIBLE + "=?,"
                    + KEY_BL_NODE_EXPANDED + "=?"
                    + " WHERE " + KEY_PK_ID + ">?" + " AND " + KEY_PK_ID + "<?"
                    + " AND " + KEY_BL_NODE_LEVEL + "<?");
        }

        int rowsUpdated;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindBoolean(1, expandAndVisible);
            stmt.bindBoolean(2, expandAndVisible);

            stmt.bindLong(3, startRowExcl);
            stmt.bindLong(4, endRowExcl);

            stmt.bindLong(5, maxLevel);

            rowsUpdated = stmt.executeUpdateDelete();
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
            Log.d(TAG, "updateNodesBetween"
                       + "|startRowExcl=" + startRowExcl
                       + "|endRowExcl=" + endRowExcl
                       + "|expandAndVisible=" + expandAndVisible
                       + "|maxLevel=" + maxLevel
                       + "|rowsUpdated=" + rowsUpdated);
        }

        if (expandAndVisible) {
            // the lowest level we want visible should never be expanded.
            stmt = mStatementManager.get(STMT_UPDATE_NODES_BETWEEN_2);
            if (stmt == null) {
                stmt = mStatementManager.add(
                        STMT_UPDATE_NODES_BETWEEN_2,
                        "UPDATE " + mTable.getName() + " SET "
                        + KEY_BL_NODE_VISIBLE + "=?,"
                        + KEY_BL_NODE_EXPANDED + "=?"
                        + " WHERE " + KEY_PK_ID + ">?" + " AND " + KEY_PK_ID + "<?"
                        + " AND " + KEY_BL_NODE_LEVEL + "=?");
            }

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindBoolean(1, true);
                stmt.bindBoolean(2, false);

                stmt.bindLong(3, startRowExcl);
                stmt.bindLong(4, endRowExcl);

                stmt.bindLong(5, maxLevel);

                stmt.executeUpdateDelete();
            }
        }
    }

    /**
     * Save the state for a single node to permanent storage.
     *
     * <strong>Note:</strong> always uses the current bookshelf/style
     *
     * @param fromLevel   from this level onwards
     * @param fromRowIncl between this row (inclusive)
     * @param endRowExcl  and this row (exclusive)
     */
    private void saveNodesBetween(final long fromLevel,
                                  final long fromRowIncl,
                                  final long endRowExcl) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
            Log.d(TAG, "saveNodesBetween"
                       + "|fromRowId=" + fromRowIncl
                       + "|fromLevel=" + fromLevel
                       + "|endRowExcl=" + endRowExcl);
        }

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                TBL_BOOK_LIST_NODE_STATE
                        .dumpTable(mSyncedDb, "saveNodesBetween", "before delete", 10,
                                   KEY_PK_ID);
            }

            SynchronizedStatement stmt = mStatementManager.get(STMT_DEL_NODES_BETWEEN);
            if (stmt == null) {
                stmt = mStatementManager.add(
                        STMT_DEL_NODES_BETWEEN,
                        "DELETE FROM " + TBL_BOOK_LIST_NODE_STATE.getName()
                        + " WHERE " + KEY_FK_BOOKSHELF + "=?"
                        + " AND " + KEY_FK_STYLE + "=?"

                        // leave the parent levels untouched
                        + " AND " + KEY_BL_NODE_LEVEL + ">=?"

                        + " AND " + KEY_BL_NODE_KEY + " IN ("
                        + " SELECT DISTINCT " + KEY_BL_NODE_KEY + " FROM " + mTable.getName()
                        + " WHERE " + KEY_PK_ID + ">=? AND " + KEY_PK_ID + "<? )");
            }

            // delete the given rows (inc. start, excl. end) and level
            // for the current bookshelf/style
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, mBookshelfId);
                stmt.bindLong(2, mStyle.getId());

                stmt.bindLong(3, fromLevel);

                stmt.bindLong(4, fromRowIncl);
                stmt.bindLong(5, endRowExcl);

                final int rowsDeleted = stmt.executeUpdateDelete();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                    Log.d(TAG, "saveNodesBetween|rowsDeleted=" + rowsDeleted);
                }
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                TBL_BOOK_LIST_NODE_STATE
                        .dumpTable(mSyncedDb, "saveNodesBetween", "delete done", 10,
                                   KEY_PK_ID);
            }

            stmt = mStatementManager.get(STMT_SAVE_NODES_BETWEEN);
            if (stmt == null) {
                stmt = mStatementManager.add(STMT_SAVE_NODES_BETWEEN,
                                             getNodesInsertSql()
                                             + " AND " + KEY_PK_ID + ">=? AND " + KEY_PK_ID + "<?");
            }

            // Read all nodes below the given node (again inc. start, excl. end),
            // and send them to the permanent table.
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, mBookshelfId);
                stmt.bindLong(2, mStyle.getId());

                stmt.bindLong(3, fromRowIncl);
                stmt.bindLong(4, endRowExcl);

                final int rowsInserted = stmt.executeUpdateDelete();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                    Log.d(TAG, "saveNodesBetween|rowsInserted=" + rowsInserted);
                }
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                TBL_BOOK_LIST_NODE_STATE
                        .dumpTable(mSyncedDb, "saveNodesBetween", "insert done", 10,
                                   KEY_PK_ID);
            }

            if (txLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * Get the base insert statement for
     * {@link #saveAllNodes} and {@link #saveNodesBetween}.
     *
     * @return sql insert
     */
    private String getNodesInsertSql() {
        return ("INSERT INTO " + TBL_BOOK_LIST_NODE_STATE
                + " (" + KEY_FK_BOOKSHELF
                + ',' + KEY_FK_STYLE

                + ',' + KEY_BL_NODE_KEY
                + ',' + KEY_BL_NODE_LEVEL
                + ',' + KEY_BL_NODE_GROUP
                + ',' + KEY_BL_NODE_EXPANDED
                + ')'
                + " SELECT DISTINCT "
                + "?"
                + ",?"
                + ',' + KEY_BL_NODE_KEY
                + ',' + KEY_BL_NODE_LEVEL
                + ',' + KEY_BL_NODE_GROUP
                + ',' + KEY_BL_NODE_EXPANDED)

               + " FROM " + mTable.getName()
               // only store visible rows; all others will be
               // considered non-visible and collapsed.
               + " WHERE " + KEY_BL_NODE_VISIBLE + "=1";
    }

    /**
     * Cleanup.
     */
    public void close() {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "|close|" + mTable.getName());
        }
        mStatementManager.close();
        mSyncedDb.drop(mTable.getName());
        mCloseWasCalled = true;
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
            if (BuildConfig.DEBUG /* always */) {
                Logger.w(TAG, "finalize|" + mTable.getName());
            }
            close();
        }
        super.finalize();
    }


    /**
     * The state we want a node to become.
     */
    public enum DesiredNodeState {
        Expand,
        Collapse,
        Toggle
    }

    /**
     * A value class containing details of a single row as used in the list-view.
     */
    public static class Node {

        final long rowId;
        @NonNull
        final String key;
        final int level;
        boolean isExpanded;
        boolean isVisible;

        /**
         * Will be calculated/set if the list changed.
         * {@code row.listPosition = RowStateDAO#getListPosition(row.rowId); }
         * <p>
         * (it's an Integer, so we can detect when it's not set at all which would be a bug)
         */
        private Integer mListPosition;

        /**
         * Constructor which expects a full cursor row.
         *
         * @param cursor to read from
         */
        Node(@NonNull final Cursor cursor) {
            rowId = cursor.getInt(0);
            key = cursor.getString(1);
            level = cursor.getInt(2);
            isExpanded = cursor.getInt(3) != 0;
            isVisible = cursor.getInt(4) != 0;
        }

        /**
         * Get a select clause (column list) with all the fields for a node.
         *
         * @param table to use
         *
         * @return CSV list of node columns
         */
        static String getColumns(@NonNull final TableDefinition table) {
            return table.dot(KEY_PK_ID)
                   + ',' + table.dot(KEY_BL_NODE_KEY)
                   + ',' + table.dot(KEY_BL_NODE_LEVEL)
                   + ',' + table.dot(KEY_BL_NODE_EXPANDED)
                   + ',' + table.dot(KEY_BL_NODE_VISIBLE);
        }

        public void setExpanded(@NonNull final DesiredNodeState nodeState) {
            // Decide what the new state for the node should be.
            switch (nodeState) {
                case Collapse:
                    isExpanded = false;
                    break;
                case Expand:
                    isExpanded = true;
                    break;

                case Toggle:
                default:
                    isExpanded = !isExpanded;
                    break;
            }
        }

        public int getListPosition() {
            Objects.requireNonNull(mListPosition);
            return mListPosition;
        }

        void setListPosition(final int listPosition) {
            this.mListPosition = listPosition;
        }

        @Override
        @NonNull
        public String toString() {
            return "Node{"
                   + "rowId=" + rowId
                   + ", key=" + key
                   + ", level=" + level
                   + ", isExpanded=" + isExpanded
                   + ", isVisible=" + isVisible
                   + ", mListPosition=" + mListPosition
                   + '}';
        }
    }
}
