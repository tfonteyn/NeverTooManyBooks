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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.io.Closeable;
import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_EXPANDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_VISIBLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;

/**
 * Provides an isolation of the logic and data needed to handle the tree structure
 * and a backend table to store enough information in the database to rebuild after an app restart.
 *
 * <ul>Naming:
 *      <li>expandABC: entry points into this class to expand/collapse node</li>
 *      <li>updateABC: private methods that write to the in-memory table</li>
 *      <li>saveABC: private methods that write to the database</li>
 * </ul>
 */
public class RowStateDAO
        implements AutoCloseable {

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
    private static final String STMT_COLLAPSE_NODES_BETWEEN = "cNSBetween";

    /** Statement cache name. */
    private static final String STMT_GET_NEXT_NODE_AT_SAME_LEVEL = "next";
    /** Statement cache name. */
    private static final String STMT_COUNT_VIS_ROWS_BEFORE = "cntVisRowsBefore";
    /** Statement cache name. */
    private static final String STMT_COUNT_VIS_ROWS = "cntVisRows";

    private static final String SELECT_DISTINCT_ = "SELECT DISTINCT ";
    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _AND_ = " AND ";
    private static final String _ORDER_BY_ = " ORDER BY ";

    private static final String UPDATE_ = "UPDATE ";
    private static final String _SET_ = " SET ";
    private static final String DELETE_FROM_ = "DELETE FROM ";

    /** Maintenance/debug usage. Simple clear all state data. */
    private static final String PURGE_ALL_SQL = DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE;

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
    private final BooklistStyle mStyle;

    /** The current list table. */
    @NonNull
    private final TableDefinition mListTable;

    /** {@link #getOffsetCursor}. */
    private final String mSqlGetOffsetCursor;
    /** {@link #ensureNodeIsVisible}. */
    private final String mSqlEnsureNodeIsVisible;
    /** {@link #getNextBookWithoutCover}. */
    private final String mSqlGetNextBookWithoutCover;
    /** {@link #getNodeByNodeId}. */
    private final String mSqlGetNodeByNodeId;
    /** {@link #getBookNodes}. */
    private final String mSqlGetBookNodes;
    /** Insert statement for {@link #saveAllNodes}. */
    private final String mSqlSaveAllNodes;
    /** Insert statement for {@link #saveNodesBetween}. */
    private final String mSqlSaveNodesBetween;

    /** DEBUG: Indicates close() has been called. Also see {@link Closeable#close()}. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     *
     * @param syncedDb  Underlying database
     * @param style     Booklist style to use;
     * @param bookshelf to use
     */
    RowStateDAO(@NonNull final SynchronizedDb syncedDb,
                @NonNull final TableDefinition listTable,
                @NonNull final BooklistStyle style,
                @NonNull final Bookshelf bookshelf) {

        mSyncedDb = syncedDb;
        mListTable = listTable;
        mBookshelfId = bookshelf.getId();
        mStyle = style;

        mStmtManager = new SqlStatementManager(mSyncedDb, TAG + "|RowStateDAO");

        // build a set of SQL statements which will be reused.

        // Build the SQL for #getOffsetCursor,
        // adding an alias for TMP_TBL_BOOK_LIST_ROW_STATE#KEY_PK_ID
        mSqlGetOffsetCursor =
                SELECT_
                + Csv.join(mListTable.getDomains(), domain -> mListTable.dot(domain.getName()))
                + ',' + (mListTable.dot(KEY_PK_ID) + " AS " + KEY_BL_LIST_VIEW_NODE_ROW_ID)
                + _FROM_ + mListTable.ref()
                + _WHERE_ + mListTable.dot(KEY_BL_NODE_VISIBLE) + "=1"
                + _ORDER_BY_ + mListTable.dot(KEY_PK_ID);

        mSqlEnsureNodeIsVisible =
                SELECT_ + KEY_PK_ID + _FROM_ + mListTable.getName()
                // follow the node hierarchy
                + _WHERE_ + KEY_BL_NODE_KEY + " LIKE ?"
                // we'll loop for all levels
                + _AND_ + KEY_BL_NODE_LEVEL + "=?";

        mSqlGetNextBookWithoutCover =
                SELECT_ + Node.getColumns(mListTable)
                + ',' + mListTable.dot(DBDefinitions.KEY_BOOK_UUID)
                + _FROM_ + mListTable.ref()
                + _WHERE_ + mListTable.dot(KEY_BL_NODE_GROUP) + "=?"
                + _AND_ + mListTable.dot(KEY_PK_ID) + ">?";

        mSqlGetNodeByNodeId =
                SELECT_ + Node.getColumns(mListTable)
                + _FROM_ + mListTable.ref()
                + _WHERE_ + mListTable.dot(KEY_PK_ID) + "=?";

        mSqlGetBookNodes =
                SELECT_ + Node.getColumns(mListTable)
                + _FROM_ + mListTable.ref()
                + _WHERE_ + mListTable.dot(KEY_FK_BOOK) + "=?";

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

        mSqlSaveNodesBetween =
                mSqlSaveAllNodes + _AND_ + KEY_PK_ID + ">=? AND " + KEY_PK_ID + "<?";
    }

    /**
     * Purge <strong>all</strong> Booklist node state data.
     */
    public static void clearAll() {
        SynchronizedDb.getInstance(DAO.SYNCHRONIZER).execSQL(PURGE_ALL_SQL);
    }

    /**
     * Gets a 'window' on the result set, starting at 'offset' and 'pageSize' rows.
     * We only retrieve visible rows.
     *
     * @param offset   the offset position (SQL OFFSET clause)
     * @param pageSize the amount of results maximum to return (SQL LIMIT clause)
     *
     * @return a list cursor starting at a given offset, using a given limit.
     */
    @NonNull
    Cursor getOffsetCursor(final int offset,
                           @SuppressWarnings("SameParameterValue") final int pageSize) {

        return mSyncedDb.rawQuery(mSqlGetOffsetCursor
                                  + " LIMIT " + pageSize + " OFFSET " + offset,
                                  null);
    }

    /**
     * Count the number of visible rows.
     *
     * @return count
     */
    int countVisibleRows() {
        final SynchronizedStatement stmt = mStmtManager.get(STMT_COUNT_VIS_ROWS, () ->
                "SELECT COUNT(*) FROM " + mListTable.getName()
                + _WHERE_ + KEY_BL_NODE_VISIBLE + "=1");

        return (int) stmt.simpleQueryForLongOrZero();
    }

    /**
     * Update the passed node with the actual list position,
     * <strong>taking into account invisible rows</strong>.
     *
     * @param node to set
     */
    void findAndSetListPosition(@NonNull final Node node) {
        final SynchronizedStatement stmt = mStmtManager.get(STMT_COUNT_VIS_ROWS_BEFORE, () ->
                "SELECT COUNT(*) FROM " + mListTable.getName()
                + _WHERE_ + KEY_BL_NODE_VISIBLE + "=1"
                + _AND_ + KEY_PK_ID + "<?");

        // We need to count the visible rows between the start of the list,
        // and the given row, to determine the ACTUAL row we want.
        // Remember that a position == rowId -1
        final int count;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, node.getRowId());
            count = (int) stmt.simpleQueryForLongOrZero();
        }
        final int pos;
        if (node.isVisible()) {
            // If the specified row is visible, then the count _is_ the position.
            pos = count;
        } else {
            // otherwise it's the previous visible row == count-1 (or the top row)
            pos = count > 0 ? count - 1 : 0;
        }

        node.setListPosition(pos);
    }

    /**
     * Ensure al nodes up to the root node for the given (book) node (inclusive) are visible.
     *
     * @param node we want to become visible
     */
    private void ensureNodeIsVisible(@NonNull final Node node) {

        node.setExpanded(true);
        node.setVisible(true);

        String nodeKey = node.getKey();

        // levels are 1.. based; start with lowest level above books, working up to root.
        // Pair: rowId/Level
        final Deque<Pair<Long, Integer>> nodes = new ArrayDeque<>();
        for (int level = node.getLevel() - 1; level >= 1; level--) {
            try (Cursor cursor = mSyncedDb.rawQuery(mSqlEnsureNodeIsVisible,
                                                    new String[]{nodeKey + "%",
                                                                 String.valueOf(level)})) {
                while (cursor.moveToNext()) {
                    final long rowId = cursor.getLong(0);
                    nodes.push(new Pair<>(rowId, level));
                }
            }
            nodeKey = nodeKey.substring(0, nodeKey.lastIndexOf('/'));
        }

        // Now process the collected nodes from the root downwards.
        for (Pair<Long, Integer> n : nodes) {
            setNode(n.first, n.second,
                    // Expand (and make visible) the given node
                    true,
                    // do this for only ONE level
                    1);
        }
    }

    @Nullable
    Node getNextBookWithoutCover(@NonNull final Context context,
                                 final long rowId) {

        try (Cursor cursor = mSyncedDb.rawQuery(mSqlGetNextBookWithoutCover,
                                                new String[]{String.valueOf(BooklistGroup.BOOK),
                                                             String.valueOf(rowId)})) {
            final Node node = new Node();
            while (cursor.moveToNext()) {
                node.from(cursor);
                final String uuid = cursor.getString(Node.COLS);
                final File file = AppDir.getCoverFile(context, uuid, 0);
                if (!ImageUtils.isFileGood(file, false)) {
                    // FIRST make the node visible
                    ensureNodeIsVisible(node);
                    // only now calculate the list position
                    findAndSetListPosition(node);
                    return node;
                }
            }
        }

        return null;
    }

    /**
     * Get the node for the the given row id (any row, not limited to books).
     * It will contain the current state of the node as stored in the database.
     *
     * @param nodeId to get
     *
     * @return the node
     */
    @NonNull
    Node getNodeByNodeId(final long nodeId) {
        try (Cursor cursor = mSyncedDb.rawQuery(mSqlGetNodeByNodeId,
                                                new String[]{String.valueOf(nodeId)})) {
            if (cursor.moveToFirst()) {
                final Node node = new Node(cursor);
                findAndSetListPosition(node);
                return node;
            } else {
                throw new IllegalStateException("rowId not found: " + nodeId);
            }
        }
    }

    /**
     * Find the nodes that show the given book.
     * Either returns a list of <strong>already visible</strong> nodes,
     * or all nodes (after making them visible).
     *
     * @param bookId the book to find
     *
     * @return the node(s), or {@code null} if none
     */
    @Nullable
    ArrayList<Node> getBookNodes(final long bookId) {
        // sanity check
        if (bookId == 0) {
            return null;
        }

        // get all positions of the book
        final ArrayList<Node> nodeList = new ArrayList<>();

        try (Cursor cursor = mSyncedDb.rawQuery(mSqlGetBookNodes,
                                                new String[]{String.valueOf(bookId)})) {
            while (cursor.moveToNext()) {
                final Node node = new Node(cursor);
                findAndSetListPosition(node);
                nodeList.add(node);
            }
        }
        if (nodeList.isEmpty()) {
            return null;
        }

        // We have nodes; first get the ones that are currently visible
        final ArrayList<Node> visibleNodes =
                nodeList.stream()
                        .filter(Node::isVisible)
                        .collect(Collectors.toCollection(ArrayList::new));


        // If we have nodes already visible, return them
        if (!visibleNodes.isEmpty()) {
            return visibleNodes;

        } else {
            // Make them all visible
            nodeList.stream()
                    .filter(node -> !node.isVisible() && node.getRowId() >= 0)
                    .forEach(this::ensureNodeIsVisible);

            // Recalculate all positions
            //noinspection SimplifyStreamApiCallChains
            nodeList.stream()
                    .forEach(this::findAndSetListPosition);

            return nodeList;
        }
    }

    /**
     * Expand or collapse <strong>all</strong> nodes.
     *
     * <strong>Transaction:</strong> participate, or runs in new.
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
     * {@link #expandAllNodes} 1. Update the status/visibility of all nodes for the given level.
     *
     * <strong>Transaction:</strong> required
     *
     * @param levelOperand one of "<", "=", ">", "<=" or ">="
     * @param level        the level
     * @param expand       state to set
     * @param visible      visibility to set
     */
    private void updateAllNodesForLevel(@NonNull final CharSequence levelOperand,
                                        @IntRange(from = 1) final int level,
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
            stmt.bindLong(3, level);
            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "updateNodesForLevel"
                       + "|level" + levelOperand + level
                       + "|expand=" + expand
                       + "|visible=" + visible
                       + "|rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * {@link #expandAllNodes} 2. Save the state for all nodes to permanent storage.
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
                // This protects the developer when changing the structure
                // somewhat irresponsibly...
                mSyncedDb.execSQL(PURGE_ALL_SQL);
                Logger.error(App.getAppContext(), TAG, e);
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
     * @param rowId from where to start looking
     * @param level level to look for
     *
     * @return row id
     */
    private long findNextNode(final long rowId,
                              final int level) {
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
                                           final int nodeLevel,
                                           final int relativeChildLevel) {
        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final int level = Math.min(nodeLevel + relativeChildLevel, mStyle.getGroupCount() + 1);

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
     * @param fromLevel   from this level onwards
     * @param fromRowIncl between this row (inclusive)
     * @param endRowExcl  and this row (exclusive)
     */
    private void saveNodesBetween(final long fromLevel,
                                  final long fromRowIncl,
                                  final long endRowExcl) {
        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "saveNodesBetween"
                       + "|fromRowId=" + fromRowIncl
                       + "|fromLevel=" + fromLevel
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

            stmt.bindLong(3, fromLevel);

            stmt.bindLong(4, fromRowIncl);
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

            stmt.bindLong(3, fromRowIncl);
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
        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // don't cache, only used once for the given table.
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

    private void adjustVisibility() {
        if (BuildConfig.DEBUG /* always */) {
            if (!mSyncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // key + level
        final Collection<Pair<String, Integer>> keyPrefixes = new ArrayList<>();

        // find all branches (level 2+) with visible nodes
        try (Cursor cursor = mSyncedDb.rawQuery(
                SELECT_DISTINCT_ + KEY_BL_NODE_KEY + ',' + KEY_BL_NODE_LEVEL
                + _FROM_ + mListTable.getName()
                + _WHERE_ + KEY_BL_NODE_VISIBLE + "=1" + _AND_ + KEY_BL_NODE_LEVEL + ">1"
                , null)) {

            while (cursor.moveToNext()) {
                final String key = cursor.getString(0);
                final int level = cursor.getInt(1);

                final String[] parts = key.split("=");
                final StringBuilder prefix = new StringBuilder(parts[0] + '=');
                for (int p = 1; p < level; p++) {
                    prefix.append(parts[p]).append('=');
                }
                prefix.append('%');
                keyPrefixes.add(new Pair<>(prefix.toString(), level));
            }
        }

        // update the branches we found
        int rows = 0;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(
                UPDATE_ + mListTable.getName() + _SET_ + KEY_BL_NODE_VISIBLE + "=1"
                + _WHERE_ + KEY_BL_NODE_KEY + " LIKE ?"
                + _AND_ + KEY_BL_NODE_LEVEL + "=?"
                + _AND_ + KEY_BL_NODE_VISIBLE + "=0")) {

            for (Pair<String, Integer> entry : keyPrefixes) {
                stmt.bindString(1, entry.first);
                stmt.bindLong(2, entry.second);
                rows += stmt.executeUpdateDelete();
            }
        }

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "rows=" + rows);
        }
    }

    /**
     * Cleanup.
     */
    @Override
    public void close() {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "|close|" + mListTable.getName());
        }
        mStmtManager.close();
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
                Logger.w(TAG, "finalize|" + mListTable.getName());
            }
            close();
        }
        super.finalize();
    }

    /**
     * A value class containing details of a single row as used in the list-view.
     */
    public static class Node {

        /** The state we want a node to <strong>become</strong>. */
        public static final int NEXT_STATE_TOGGLE = 0;
        /** The state we want a node to <strong>become</strong>. */
        public static final int NEXT_STATE_EXPANDED = 1;
        /** The state we want a node to <strong>become</strong>. */
        static final int NEXT_STATE_COLLAPSED = 2;
        /** Number of columns used in {@link #getColumns(TableDefinition)}. */
        static final int COLS = 5;

        private long mRowId;
        private String mKey;
        private int mLevel;
        private boolean mIsExpanded;
        private boolean mIsVisible;

        /**
         * Will be calculated/set if the list changed.
         * {@code row.listPosition = RowStateDAO#getListPosition(row.rowId); }
         * <p>
         * (it's an Integer, so we can detect when it's not set at all which would be a bug)
         */
        private Integer mListPosition;

        /**
         * Constructor. Intended to be used with loops without creating new objects over and over.
         * Use {@link #from(Cursor)} to populate the current values.
         */
        Node() {
        }

        /**
         * Constructor which expects a full cursor row.
         *
         * @param cursor to read from
         */
        Node(@NonNull final Cursor cursor) {
            from(cursor);
        }

        /**
         * Get a select clause (column list) with all the fields for a node.
         *
         * @param table to use
         *
         * @return CSV list of node columns
         */
        @NonNull
        static String getColumns(@NonNull final TableDefinition table) {
            return table.dot(KEY_PK_ID)
                   + ',' + table.dot(KEY_BL_NODE_KEY)
                   + ',' + table.dot(KEY_BL_NODE_LEVEL)
                   + ',' + table.dot(KEY_BL_NODE_EXPANDED)
                   + ',' + table.dot(KEY_BL_NODE_VISIBLE);
        }

        /**
         * Set the node based on the given cursor row.
         * <p>
         * Allows a single node to be used in a loop without creating new objects over and over.
         *
         * @param cursor to read from
         */
        void from(@NonNull final Cursor cursor) {
            mRowId = cursor.getInt(0);
            mKey = cursor.getString(1);
            mLevel = cursor.getInt(2);
            mIsExpanded = cursor.getInt(3) != 0;
            mIsVisible = cursor.getInt(4) != 0;
        }

        public boolean isExpanded() {
            return mIsExpanded;
        }

        public void setExpanded(final boolean expanded) {
            mIsExpanded = expanded;
        }

        public boolean isVisible() {
            return mIsVisible;
        }

        public void setVisible(final boolean visible) {
            mIsVisible = visible;
        }

        @SuppressWarnings("WeakerAccess")
        public long getRowId() {
            return mRowId;
        }

        @NonNull
        public String getKey() {
            return mKey;
        }

        public int getLevel() {
            return mLevel;
        }

        /**
         * Update the current state.
         *
         * @param nextState the state to set the node to
         */
        void setNextState(@NodeNextState final int nextState) {
            switch (nextState) {
                case Node.NEXT_STATE_COLLAPSED:
                    mIsExpanded = false;
                    break;

                case Node.NEXT_STATE_EXPANDED:
                    mIsExpanded = true;
                    break;

                case Node.NEXT_STATE_TOGGLE:
                default:
                    mIsExpanded = !mIsExpanded;
                    break;
            }
        }

        public int getListPosition() {
            return Objects.requireNonNull(mListPosition);
        }

        void setListPosition(final int listPosition) {
            mListPosition = listPosition;
        }

        @Override
        @NonNull
        public String toString() {
            return "Node{"
                   + "rowId=" + mRowId
                   + ", key=" + mKey
                   + ", level=" + mLevel
                   + ", isExpanded=" + mIsExpanded
                   + ", isVisible=" + mIsVisible
                   + ", mListPosition=" + mListPosition
                   + '}';
        }

        @IntDef({NEXT_STATE_EXPANDED, NEXT_STATE_COLLAPSED, NEXT_STATE_TOGGLE})
        @Retention(RetentionPolicy.SOURCE)
        public @interface NodeNextState {

        }
    }
}
