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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_EXPANDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_VISIBLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;

/**
 * Provides an isolation of the logic and data needed to save and restore the tree structure
 * and a backend table to store enough information in the database to rebuild after an app restart.
 *
 * <ul>entry points:
 *      <li>{@link #setAllNodes(int, boolean)}: update ALL nodes starting from a set level.</li>
 *      <li>{@link #setNode(long, int, boolean, int)}: update a single node (and it's branch).</li>
 *      <li>{@link #restoreSavedState()}: restore the tree view for all nodes from storage.</li>
 * </ul>
 */
class BooklistNodeDAO {

    /** Log tag. */
    private static final String TAG = "BooklistNodeDAO";

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
    private static final String STMT_COLLAPSE_NODES_BETWEEN = "cNSBetween";

    /** Statement cache name. */
    private static final String STMT_GET_NEXT_NODE_AT_SAME_LEVEL = "next";

    private static final String SELECT_DISTINCT_ = "SELECT DISTINCT ";
    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _AND_ = " AND ";
    private static final String _ORDER_BY_ = " ORDER BY ";

    private static final String UPDATE_ = "UPDATE ";
    private static final String _SET_ = " SET ";

    private static final String DELETE_FROM_ = "DELETE FROM ";

    /** This is a reference only. Cleanup is done by the owner of this object. */
    @NonNull
    private final SynchronizedDb mSyncedDb;

    /** Collection of statements pre-compiled for this object. */
    @NonNull
    private final SqlStatementManager mStmtManager;

    /** The current shelf. */
    private final long mBookshelfId;
    /** The current style. */
    @NonNull
    private final ListStyle mStyle;

    /** The current list table. */
    @NonNull
    private final TableDefinition mListTable;

    /** Insert statement for {@link #saveAllNodes}. */
    private final String mSqlSaveAllNodes;
    /** Insert statement for {@link #saveNodesBetween}. */
    private final String mSqlSaveNodesBetween;

    /**
     * Constructor.
     *
     * @param db          Database Access
     * @param stmtManager to use
     * @param style       Style reference.
     * @param bookshelf   to use
     */
    BooklistNodeDAO(@NonNull final SynchronizedDb db,
                    @NonNull final SqlStatementManager stmtManager,
                    @NonNull final TableDefinition listTable,
                    @NonNull final ListStyle style,
                    @NonNull final Bookshelf bookshelf) {

        mSyncedDb = db;
        mListTable = listTable;
        mBookshelfId = bookshelf.getId();
        mStyle = style;

        mStmtManager = stmtManager;

        // We save only nodes which are expanded and/or visible.
        // This does mean we always store the state of level 1 even when not needed.
        // but oh well... no big deal.
        mSqlSaveAllNodes =
                "INSERT INTO " + TBL_BOOK_LIST_NODE_STATE
                + " (" + KEY_FK_BOOKSHELF + ',' + KEY_FK_STYLE
                + ',' + KEY_BL_NODE_KEY
                + ',' + KEY_BL_NODE_LEVEL
                + ',' + KEY_BL_NODE_GROUP
                + ',' + KEY_BL_NODE_EXPANDED
                + ',' + KEY_BL_NODE_VISIBLE
                + ") "
                + SELECT_DISTINCT_ + "?,?"
                + ',' + KEY_BL_NODE_KEY
                + ',' + KEY_BL_NODE_LEVEL
                + ',' + KEY_BL_NODE_GROUP
                + ',' + KEY_BL_NODE_EXPANDED
                + ',' + KEY_BL_NODE_VISIBLE

                + _FROM_ + mListTable.getName()
                + _WHERE_ + "(" + KEY_BL_NODE_EXPANDED + "=1 OR " + KEY_BL_NODE_VISIBLE + "=1)";

        // Same, but limited to a single node (and it's branch)
        mSqlSaveNodesBetween =
                mSqlSaveAllNodes + _AND_ + KEY_PK_ID + ">=? AND " + KEY_PK_ID + "<?";
    }

    /**
     * Expand or collapse <strong>all</strong> nodes.
     *
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param topLevel the desired top-level which must be kept visible
     * @param expand   the state to apply to levels 'below' the topLevel (level > topLevel),
     */
    void setAllNodes(@IntRange(from = 1) final int topLevel,
                     final boolean expand) {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            // code could be condensed somewhat, but leaving as-is making it easier to read.
            if (topLevel == 1) {
                if (expand) {
                    // expand and show all levels
                    updateAllNodesForLevel(">=", topLevel, true, true);
                } else {
                    // collapse level 1, but keep it visible.
                    updateAllNodesForLevel("=", topLevel, false, true);
                    // collapse and hide all other levels
                    updateAllNodesForLevel(">", topLevel, false, false);
                }

            } else /* topLevel > 1 */ {
                // always expand and show levels less than the defined top-level.
                updateAllNodesForLevel("<", topLevel, true, true);

                // collapse the specified level, but keep it visible.
                updateAllNodesForLevel("=", topLevel, false, true);

                // set the desired state to all other levels
                updateAllNodesForLevel(">", topLevel, expand, expand);
            }

            // Store the state of all nodes.
            saveAllNodes();

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
     * {@link #setAllNodes} 1. Update the status/visibility of all nodes for the given level.
     *
     * <strong>Transaction:</strong> required
     *
     * @param levelOperand one of "<", "=", ">", "<=" or ">="
     * @param nodeLevel    the level
     * @param expand       state to set
     * @param visible      visibility to set
     */
    private void updateAllNodesForLevel(@NonNull final CharSequence levelOperand,
                                        @IntRange(from = 1) final int nodeLevel,
                                        final boolean expand,
                                        final boolean visible) {
        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
            // developer sanity check
            if (!"< = > <= >=".contains(levelOperand)) {
                throw new IllegalArgumentException("levelOperand=`" + levelOperand + '`');
            }
        }

        //Note to self: levelOperand is concatenated, so do not cache this stmt.
        final String sql = UPDATE_ + mListTable.getName() + _SET_
                           + KEY_BL_NODE_EXPANDED + "=?," + KEY_BL_NODE_VISIBLE + "=?"
                           + _WHERE_ + KEY_BL_NODE_LEVEL + levelOperand + "?";

        final int rowsUpdated;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
            stmt.bindBoolean(1, expand);
            stmt.bindBoolean(2, visible);
            stmt.bindLong(3, nodeLevel);
            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "updateNodesForLevel"
                       + "|level=" + levelOperand + nodeLevel
                       + "|expand=" + expand
                       + "|visible=" + visible
                       + "|rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * {@link #setAllNodes} 2. Save the state for all nodes to permanent storage.
     * We only store visible nodes and their expansion state.
     *
     * <strong>Note:</strong> always use the current bookshelf/style
     *
     * <strong>Transaction:</strong> participate, or runs in new.
     */
    private void saveAllNodes() {
        Synchronizer.SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            SynchronizedStatement stmt;
            int rowsUpdated;

            stmt = mStmtManager.get(STMT_DEL_ALL_NODES, () ->
                    // delete all rows for the current bookshelf/style
                    DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE.getName()
                    + _WHERE_ + KEY_FK_BOOKSHELF + "=?" + _AND_ + KEY_FK_STYLE + "=?");

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, mBookshelfId);
                stmt.bindLong(2, mStyle.getId());
                rowsUpdated = stmt.executeUpdateDelete();
            }
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                Log.d(TAG, "saveAllNodes|rowsDeleted=" + rowsUpdated);
            }

            stmt = mStmtManager.get(STMT_SAVE_ALL_NODES, () -> mSqlSaveAllNodes);

            // Read all visible nodes, and send them to the permanent table.
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, mBookshelfId);
                stmt.bindLong(2, mStyle.getId());
                rowsUpdated = stmt.executeUpdateDelete();
            }
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                Log.d(TAG, "saveAllNodes|rowsUpdated=" + rowsUpdated);
            }

            if (txLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
        } catch (@NonNull final SQLiteConstraintException e) {
            if (BuildConfig.DEBUG /* always */) {
                // DEBUG ONLY!
                // This protects the developer when changing the structure irresponsibly...
                DAO.clearNodeStateData(App.getAppContext());
                Logger.error(TAG, e);
            }
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
    }


    /**
     * Expand/collapse the passed node.
     * <p>
     * The node will always be kept visible.
     * <p>
     * If the resulting state of the node is:<br>
     * 'Expand': we set the children to the desired child state/visibility
     * (recurse up to the desired max child level)<br>
     * 'Collapse': all children will be set to Collapse/hidden
     *
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param nodeRowId          of the node in the list
     * @param nodeLevel          the level of the node
     * @param expandNode         the state to set for this node
     * @param relativeChildLevel up to and including this (relative to nodeLevel!) child level.
     */
    void setNode(final long nodeRowId,
                 @IntRange(from = 1) final int nodeLevel,
                 final boolean expandNode,
                 @IntRange(from = 0) final int relativeChildLevel) {

        final int groupCount = mStyle.getGroups().size();

        if (relativeChildLevel > groupCount) {
            throw new IllegalArgumentException("groupCount=" + groupCount
                                               + "|relativeChildLevel=" + relativeChildLevel);
        }

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            // expand/collapse the specified row, but always keep it visible.
            updateNode(nodeRowId, expandNode);

            // Find the next row ('after' the given row) at the given level (or lower).
            final long endRowExcl = findNextNode(nodeRowId, nodeLevel);
            // are there actually any rows in between?
            if ((endRowExcl - nodeRowId) > 1) {
                // update the rows between the node row itself, and the endRow
                if (expandNode) {
                    // use the desired child state/level
                    showAndExpandNodesBetween(nodeRowId, endRowExcl, nodeLevel, relativeChildLevel);
                } else {
                    // collapsing the node, so just collapse/hide all children.
                    collapseAndHideNodesBetween(nodeRowId, endRowExcl);
                }
            }

            // Store the state of these nodes.
            saveNodesBetween(nodeRowId, endRowExcl, nodeLevel);

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
     * {@link #setNode} 1. Update a single node.
     *
     * <strong>Transaction:</strong> required
     *
     * @param rowId  the row/node to update
     * @param expand state to set
     */
    private void updateNode(final long rowId,
                            final boolean expand) {
        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final SynchronizedStatement stmt = mStmtManager.get(STMT_UPDATE_NODE, () ->
                UPDATE_ + mListTable.getName() + _SET_
                + KEY_BL_NODE_EXPANDED + "=?" + ',' + KEY_BL_NODE_VISIBLE + "=1"
                + _WHERE_ + KEY_PK_ID + "=?");

        final int rowsUpdated;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindBoolean(1, expand);
            stmt.bindLong(2, rowId);
            rowsUpdated = stmt.executeUpdateDelete();
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "updateNode"
                       + "|rowId=" + rowId
                       + "|expand=" + expand
                       + "|rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * {@link #setNode} 2. Find the next row ('after' the given row) at the given level (or lower).
     *
     * <strong>Transaction:</strong> required
     *
     * @param rowId     from where to start looking
     * @param nodeLevel level to look for
     *
     * @return row id
     */
    private long findNextNode(final long rowId,
                              @IntRange(from = 1) final int nodeLevel) {
        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final SynchronizedStatement stmt = mStmtManager.get(
                STMT_GET_NEXT_NODE_AT_SAME_LEVEL, () ->
                        SELECT_ + KEY_PK_ID + _FROM_ + mListTable.getName()
                        + _WHERE_ + KEY_PK_ID + ">?" + _AND_ + KEY_BL_NODE_LEVEL + "<=?"
                        + _ORDER_BY_ + KEY_PK_ID + " LIMIT 1");

        final long nextRowId;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, rowId);
            stmt.bindLong(2, nodeLevel);
            nextRowId = stmt.simpleQueryForLongOrZero();
        }

        // if there was no next node, use the end of the list.
        if (nextRowId <= 0) {
            return Long.MAX_VALUE;
        }
        return nextRowId;
    }

    /**
     * {@link #setNode} 3. Update the nodes <strong>between</strong> the two given rows;
     * i.e. <strong>excluding</strong> the start and end row.
     *
     * <strong>Transaction:</strong> required
     *
     * @param startRowExcl between this row
     * @param endRowExcl   and this row
     * @param nodeLevel    the level which was clicked
     */
    private void showAndExpandNodesBetween(final long startRowExcl,
                                           final long endRowExcl,
                                           @IntRange(from = 1) final int nodeLevel,
                                           final int relativeChildLevel) {
        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final int level = Math.min(nodeLevel + relativeChildLevel, mStyle.getGroups().size() + 1);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "updateNodesBetween"
                       + "|nodeLevel=" + nodeLevel
                       + "|relativeChildLevel=" + relativeChildLevel
                       + "|level=" + level);
        }

        SynchronizedStatement stmt;
        int rowsUpdated;

        // handle all levels except the lowest level.
        stmt = mStmtManager.get(STMT_UPDATE_NODES_BETWEEN_1, () ->
                UPDATE_ + mListTable.getName() + _SET_
                + KEY_BL_NODE_VISIBLE + "=1,"
                + KEY_BL_NODE_EXPANDED + "=1"
                + _WHERE_ + KEY_PK_ID + ">?" + _AND_ + KEY_PK_ID + "<?"
                + _AND_ + KEY_BL_NODE_LEVEL + "<?");

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, startRowExcl);
            stmt.bindLong(2, endRowExcl);
            stmt.bindLong(3, level);
            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "updateNodesBetween|step1"
                       + "|startRowExcl=" + startRowExcl
                       + "|endRowExcl=" + endRowExcl
                       + "|rowsUpdated=" + rowsUpdated);
        }

        // the lowest level we want visible but not expanded.
        stmt = mStmtManager.get(STMT_UPDATE_NODES_BETWEEN_2, () ->
                UPDATE_ + mListTable.getName() + _SET_
                + KEY_BL_NODE_VISIBLE + "=1,"
                + KEY_BL_NODE_EXPANDED + "=0"
                + _WHERE_ + KEY_PK_ID + ">?" + _AND_ + KEY_PK_ID + "<?"
                + _AND_ + KEY_BL_NODE_LEVEL + "=?");

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, startRowExcl);
            stmt.bindLong(2, endRowExcl);
            stmt.bindLong(3, level);
            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "updateNodesBetween|step2"
                       + "|startRowExcl=" + startRowExcl
                       + "|endRowExcl=" + endRowExcl
                       + "|rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * {@link #setNode} 3. Collapse/hide the nodes <strong>between</strong> the two given rows;
     * i.e. <strong>excluding</strong> the start and end row.
     *
     * <strong>Transaction:</strong> required
     *
     * @param startRowExcl between this row
     * @param endRowExcl   and this row
     */
    private void collapseAndHideNodesBetween(final long startRowExcl,
                                             final long endRowExcl) {
        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // simply set all of them to invisible/unexpanded.
        final SynchronizedStatement stmt = mStmtManager.get(STMT_COLLAPSE_NODES_BETWEEN, () ->
                UPDATE_ + mListTable.getName() + _SET_
                + KEY_BL_NODE_VISIBLE + "=0,"
                + KEY_BL_NODE_EXPANDED + "=0"
                + _WHERE_ + KEY_PK_ID + ">?" + _AND_ + KEY_PK_ID + "<?");

        final int rowsUpdated;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, startRowExcl);
            stmt.bindLong(2, endRowExcl);
            rowsUpdated = stmt.executeUpdateDelete();
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "collapseAndHideNodesBetween"
                       + "|startRowExcl=" + startRowExcl
                       + "|endRowExcl=" + endRowExcl
                       + "|rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * {@link #setNode} 4. Save the state for a single node (and below) to permanent storage.
     *
     * <strong>Note:</strong> always uses the current bookshelf/style
     *
     * <strong>Transaction:</strong> required
     *
     * @param startRowIncl between this row (inclusive)
     * @param endRowExcl   and this row (exclusive)
     * @param nodeLevel    from this level onwards
     */
    private void saveNodesBetween(final long startRowIncl,
                                  final long endRowExcl,
                                  @IntRange(from = 1) final long nodeLevel) {
        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "saveNodesBetween"
                       + "|fromRowId=" + startRowIncl
                       + "|fromLevel=" + nodeLevel
                       + "|endRowExcl=" + endRowExcl);

            TBL_BOOK_LIST_NODE_STATE.dumpTable(
                    mSyncedDb, "saveNodesBetween", "before delete", 10, KEY_PK_ID);
        }

        SynchronizedStatement stmt;
        int rowsUpdated;

        stmt = mStmtManager.get(STMT_DEL_NODES_BETWEEN, () ->
                // delete the rows for the current bookshelf/style
                DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE.getName()
                + _WHERE_ + KEY_FK_BOOKSHELF + "=?" + _AND_ + KEY_FK_STYLE + "=?"
                // but leave the parent levels untouched
                + _AND_ + KEY_BL_NODE_LEVEL + ">=?"
                // and only between the given nodes
                + _AND_ + KEY_BL_NODE_KEY + " IN ("
                + SELECT_DISTINCT_ + KEY_BL_NODE_KEY + _FROM_ + mListTable.getName()
                + _WHERE_ + KEY_PK_ID + ">=? AND " + KEY_PK_ID + "<? )");

        // delete the given rows (inc. start, excl. end) and level
        // for the current bookshelf/style
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, mBookshelfId);
            stmt.bindLong(2, mStyle.getId());

            stmt.bindLong(3, nodeLevel);

            stmt.bindLong(4, startRowIncl);
            stmt.bindLong(5, endRowExcl);

            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "saveNodesBetween|rowsDeleted=" + rowsUpdated);
            TBL_BOOK_LIST_NODE_STATE.dumpTable(
                    mSyncedDb, "saveNodesBetween", "delete done", 10, KEY_PK_ID);
        }

        stmt = mStmtManager.get(STMT_SAVE_NODES_BETWEEN, () -> mSqlSaveNodesBetween);

        // Read all nodes below the given node (again inc. start, excl. end),
        // and send them to the permanent table.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, mBookshelfId);
            stmt.bindLong(2, mStyle.getId());

            stmt.bindLong(3, startRowIncl);
            stmt.bindLong(4, endRowExcl);

            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "saveNodesBetween|rowsInserted=" + rowsUpdated);
            TBL_BOOK_LIST_NODE_STATE.dumpTable(
                    mSyncedDb, "saveNodesBetween", "insert done", 10, KEY_PK_ID);
        }
    }


    /**
     * Restore the expanded and/or visible node status.
     * The logic here assumes the target table {@link #mListTable} will have ALL rows
     * set to "0/0". It will update ONLY rows from storage that have "-/1" and/or "1/-"
     *
     * <strong>Transaction:</strong> required
     */
    void restoreSavedState() {
        // don't cache the sql here, only used once for the given table.

        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final String sqlTemplate =
                UPDATE_ + mListTable.getName() + _SET_ + "%s=1"
                + _WHERE_ + KEY_PK_ID + " IN ("
                + SELECT_ + mListTable.dot(KEY_PK_ID) + _FROM_ + mListTable.ref()
                + "," + TBL_BOOK_LIST_NODE_STATE.ref()
                + _WHERE_
                + TBL_BOOK_LIST_NODE_STATE.dot(KEY_FK_BOOKSHELF) + "=?"
                + _AND_
                + TBL_BOOK_LIST_NODE_STATE.dot(KEY_FK_STYLE) + "=?"
                + _AND_
                + mListTable.dot(KEY_BL_NODE_KEY) + "="
                + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_KEY)
                + _AND_
                + mListTable.dot(KEY_BL_NODE_LEVEL) + "="
                + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_LEVEL)
                + _AND_
                + mListTable.dot(KEY_BL_NODE_GROUP) + "="
                + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_GROUP)
                + _AND_
                + TBL_BOOK_LIST_NODE_STATE.dot("%s") + "=1"
                + ")";

        restoreSavedState(sqlTemplate, KEY_BL_NODE_EXPANDED);
        restoreSavedState(sqlTemplate, KEY_BL_NODE_VISIBLE);

        adjustVisibility();
    }

    /**
     * {@link #restoreSavedState()} 1. Update visibility/expanded flag (single column at a time)
     */
    private void restoreSavedState(@NonNull final String sqlTemplate,
                                   @NonNull final String columnName) {
        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final String sql = String.format(sqlTemplate, columnName, columnName);

        final int rowsUpdated;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
            stmt.bindLong(1, mBookshelfId);
            stmt.bindLong(2, mStyle.getId());
            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "restoreSavedState"
                       + "|mBookshelfId=" + mBookshelfId
                       + "|mStyle.getId()=" + mStyle.getId()
                       + "|columnName=" + columnName
                       + "|rowsUpdated=" + rowsUpdated
                       + "|sql=" + sql);
        }
    }

    /**
     * {@link #restoreSavedState()} 2. Update branches which were not in the saved data.
     */
    private void adjustVisibility() {
        // don't cache the sql here, only used once for the given table.

        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // level + key
        final Collection<Pair<Integer, String>> keyPrefixes = new ArrayList<>();

        // find all branches (groups on level 2+) with visible nodes
        try (Cursor cursor = mSyncedDb.rawQuery(
                SELECT_DISTINCT_ + KEY_BL_NODE_KEY + ',' + KEY_BL_NODE_LEVEL
                + _FROM_ + mListTable.getName()
                + _WHERE_ + KEY_BL_NODE_VISIBLE + "=1"
                // Groups only - Don't do books
                + _AND_ + KEY_BL_NODE_LEVEL + " BETWEEN 2 AND ?",
                new String[]{String.valueOf(mStyle.getGroups().size())})) {

            while (cursor.moveToNext()) {
                final String key = cursor.getString(0);
                final int level = cursor.getInt(1);
                final String[] parts = key.split("=");
                final StringBuilder prefix = new StringBuilder(parts[0]);
                for (int p = 1; p < level; p++) {
                    prefix.append('=').append(parts[p]);
                }
                prefix.append('%');
                keyPrefixes.add(new Pair<>(level, prefix.toString()));
            }
        }

        // update the branches we found
        int rows = 0;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(
                UPDATE_ + mListTable.getName() + _SET_ + KEY_BL_NODE_VISIBLE + "=1"
                + _WHERE_ + KEY_BL_NODE_VISIBLE + "=0"
                + _AND_ + KEY_BL_NODE_LEVEL + "=?"
                + _AND_ + KEY_BL_NODE_KEY + " LIKE ?")) {

            for (final Pair<Integer, String> entry : keyPrefixes) {
                stmt.bindLong(1, entry.first);
                stmt.bindString(2, entry.second);
                rows += stmt.executeUpdateDelete();
            }
        }

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "adjustVisibility|rows=" + rows);
        }
    }
}
