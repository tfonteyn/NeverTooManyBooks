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
import java.util.Deque;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
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
    private static final String STMT_GET_NEXT_NODE_AT_SAME_LEVEL = "next";
    /** Statement cache name. */
    private static final String STMT_COUNT_VIS_ROWS_BEFORE = "cntVisRowsBefore";
    /** Statement cache name. */
    private static final String STMT_COUNT_VIS_ROWS = "cntVisRows";

    /** Maintenance/debug usage. Simple clear all state data. */
    private static final String PURGE_ALL_SQL =
            "DELETE FROM " + DBDefinitions.TBL_BOOK_LIST_NODE_STATE;

    private static final String _AND_ = " AND ";
    private static final String _ORDER_BY_ = " ORDER BY ";
    private static final String _FROM_ = " FROM ";
    private static final String SELECT_ = "SELECT ";
    private static final String _WHERE_ = " WHERE ";
    private static final String UPDATE_ = "UPDATE ";
    private static final String _SET_ = " SET ";
    /** This is a reference only. Cleanup is done by the owner of this object. */
    @NonNull
    private final SynchronizedDb mSyncedDb;
    /** Collection of statements pre-compiled for this object. */
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

    /** Constructed during {@link #build} for {@link #getOffsetCursor}. */
    private String mSqlGetOffsetCursor;
    /** Constructed during {@link #build} for {@link #ensureNodeIsVisible}. */
    private String mSqlEnsureNodeIsVisible;
    /** Constructed during {@link #build} for {@link #getNextBookWithoutCover}. */
    private String mSqlGetNextBookWithoutCover;
    /** Constructed during {@link #build} for {@link #getNodeByNodeId}. */
    private String mSqlGetNodeByNodeId;
    /** Constructed during {@link #build} for {@link #getBookNodes}. */
    private String mSqlGetBookNodes;
    /**
     * Constructed during {@link #build}:
     * Base insert statement for {@link #saveAllNodes} and {@link #saveNodesBetween}.
     */
    private String mSqlNodesInsertBase;


    /**
     * Constructor. Defines, but does not create the table. See {@link #build}.
     *
     * <strong>Note:</strong> the style/bookshelf the booklist is displaying, determines
     * the list-table and hence the RowStateDAO.
     *
     * @param syncedDb   Books database
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
        SynchronizedDb.getInstance(DAO.SYNCHRONIZER).execSQL(PURGE_ALL_SQL);
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
               @BooklistBuilder.ListRebuildMode final int listState) {

        // we drop the newly defined table just in case there is a leftover physical table
        // with the same name (due to previous crash maybe).
        if (mTable.exists(mSyncedDb)) {
            mSyncedDb.drop(mTable.getName());
        }

        // Link with the list table using the actual row ID
        // We need this for joins, but it's not enforced with constraints.
        mTable.addReference(listTable, DBDefinitions.DOM_FK_BL_ROW_ID);

        // indices will be created after the table is populated.
        mTable.create(mSyncedDb, true, false);

        // build a set of SQL statements which will be reused.
        buildReusableSqlStrings(listTable);

        // Populate the table according to the user preferred rebuild-state
        int rowsUpdated;

        // the table is ordered at insert time, so the row id *is* the order.
        String sql = "INSERT INTO " + mTable.getName()
                     + " (" + KEY_FK_BL_ROW_ID
                     + ',' + KEY_BL_NODE_KEY
                     + ',' + KEY_BL_NODE_LEVEL
                     + ',' + KEY_BL_NODE_GROUP

                     + ',' + KEY_BL_NODE_VISIBLE
                     + ',' + KEY_BL_NODE_EXPANDED
                     + ')'
                     + "\nSELECT DISTINCT "
                     + listTable.dot(KEY_PK_ID)
                     + ',' + listTable.dot(KEY_BL_NODE_KEY)
                     + ',' + listTable.dot(KEY_BL_NODE_LEVEL)
                     + ',' + listTable.dot(KEY_BL_NODE_GROUP)
                     + "\n";

        switch (listState) {
            case BooklistBuilder.PREF_REBUILD_ALWAYS_EXPANDED: {
                // KEY_BL_NODE_VISIBLE: all visible
                // KEY_BL_NODE_EXPANDED: all expanded
                sql += ",1,1 FROM " + listTable.ref()
                       + _ORDER_BY_ + listTable.dot(KEY_PK_ID);

                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                        Log.d(TAG, "PREF_REBUILD_ALWAYS_EXPANDED"
                                   + "|rowsUpdated=" + rowsUpdated
                                   + "|sql=" + sql);
                    }
                }
                break;
            }
            case BooklistBuilder.PREF_REBUILD_ALWAYS_COLLAPSED: {
                sql +=
                        // KEY_BL_NODE_VISIBLE:
                        // level 1 visible and all other levels invisible.
                        ",CASE WHEN " + KEY_BL_NODE_LEVEL + "=1 THEN 1 ELSE 0 END"
                        // 'AS' for SQL readability/debug only
                        + " AS " + KEY_BL_NODE_VISIBLE

                        // KEY_BL_NODE_EXPANDED: all nodes collapsed
                        + ",0 FROM " + listTable.ref()
                        + _ORDER_BY_ + listTable.dot(KEY_PK_ID);

                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                        Log.d(TAG, "PREF_REBUILD_ALWAYS_COLLAPSED"
                                   + "|rowsUpdated=" + rowsUpdated
                                   + "|sql=" + sql);
                    }
                }
                break;
            }
            case BooklistBuilder.PREF_REBUILD_PREFERRED_STATE: {
                // Run the INSERT first, state does not matter, for simplicity visible/expand all
                sql += ",1,1 FROM " + listTable.ref()
                       + _ORDER_BY_ + listTable.dot(KEY_PK_ID);
                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                        Log.d(TAG, "PREF_REBUILD_PREFERRED_STATE"
                                   + "|rowsUpdated=" + rowsUpdated
                                   + "|sql=" + sql);
                    }
                }

                // Now set the preferred expand/visibility status.
                expandAllNodes(mStyle.getTopLevel(context), false);
                break;
            }
            case BooklistBuilder.PREF_REBUILD_SAVED_STATE: {
                // The more nodes expanded, the slower this is.
                // **ad-hoc** measurements: 31 top-level nodes, 9260 rows running in emulator.
                // fully collapsed: 117 ms
                // fully expanded: 20.000 ms
                sql +=
                        // KEY_BL_NODE_VISIBLE
                        ",CASE"
                        // Level 1 is always visible (whether present or not).
                        + " WHEN " + listTable.dot(KEY_BL_NODE_LEVEL) + "=1 THEN 1"
                        // If the row is not present (check on NODE_KEY!), hide it.
                        + " WHEN " + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_KEY) + " IS NULL"
                        + /* */ " THEN 0"
                        // All other rows are visible (whether present or not).
                        + /* */ " ELSE 1"
                        + " END"
                        // 'AS' for SQL readability/debug only
                        + " AS " + KEY_BL_NODE_VISIBLE
                        + "\n"

                        // KEY_BL_NODE_EXPANDED
                        // If the row is not present, collapse it. Otherwise use stored state.
                        + ",COALESCE(" + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_EXPANDED) + ",0)"
                        // 'AS' for SQL readability/debug only
                        + " AS " + KEY_BL_NODE_EXPANDED
                        + "\n"

                        // note this is a pure OUTER JOIN and not a where-clause
                        // We need to get *ALL* rows from listTable.
                        + _FROM_ + listTable.ref()
                        + " LEFT OUTER JOIN " + TBL_BOOK_LIST_NODE_STATE.ref() + " ON "
                        + listTable.dot(KEY_BL_NODE_LEVEL)
                        + '=' + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_LEVEL)
                        + _AND_
                        + listTable.dot(KEY_BL_NODE_KEY)
                        + '=' + TBL_BOOK_LIST_NODE_STATE.dot(KEY_BL_NODE_KEY)
                        + "\n"
                        + _AND_ + TBL_BOOK_LIST_NODE_STATE.dot(KEY_FK_BOOKSHELF) + "=?"
                        + _AND_ + TBL_BOOK_LIST_NODE_STATE.dot(KEY_FK_STYLE) + "=?"
                        + "\n"
                        + _ORDER_BY_ + listTable.dot(KEY_PK_ID);

                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    stmt.bindLong(1, mBookshelfId);
                    stmt.bindLong(2, mStyle.getId());
                    rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                        Log.d(TAG, "PREF_REBUILD_SAVED_STATE"
                                   + "|rowsUpdated=" + rowsUpdated
                                   + "|sql=" + sql);
                    }
                }
                break;
            }

            default:
                throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + listState);
        }

        // Create the indexes AFTER the data got inserted. This has proven to be somewhat faster.
        mTable.createIndices(mSyncedDb);

        mSyncedDb.analyze(mTable);
    }

    /**
     * Build a set of SQL statements which will be reused.
     *
     * @param listTable the list-table to be referenced
     */
    private void buildReusableSqlStrings(@NonNull final TableDefinition listTable) {
        // Build the SQL for #getOffsetCursor,
        // adding an alias for TMP_TBL_BOOK_LIST_ROW_STATE#KEY_PK_ID
        mSqlGetOffsetCursor =
                SELECT_
                + Csv.join(listTable.getDomains(), domain -> listTable.dot(domain.getName()))
                + ',' + (mTable.dot(KEY_PK_ID) + " AS " + KEY_BL_LIST_VIEW_NODE_ROW_ID)
                + _FROM_ + listTable.ref() + listTable.join(mTable)
                + _WHERE_ + mTable.dot(KEY_BL_NODE_VISIBLE) + "=1"
                + _ORDER_BY_ + mTable.dot(KEY_PK_ID);

        mSqlEnsureNodeIsVisible =
                SELECT_ + KEY_PK_ID + _FROM_ + mTable.getName()
                // follow the node hierarchy
                + _WHERE_ + KEY_BL_NODE_KEY + " LIKE ?"
                // we'll loop for all levels
                + _AND_ + KEY_BL_NODE_LEVEL + "=?";

        mSqlGetNextBookWithoutCover =
                SELECT_ + Node.getColumns(mTable)
                + ',' + listTable.dot(DBDefinitions.KEY_BOOK_UUID)
                + _FROM_ + listTable.ref() + listTable.join(mTable)
                + _WHERE_ + listTable.dot(KEY_BL_NODE_GROUP) + "=?"
                + _AND_ + listTable.dot(KEY_PK_ID) + ">?";

        mSqlGetNodeByNodeId =
                SELECT_ + Node.getColumns(mTable)
                + _FROM_ + mTable.ref()
                + _WHERE_ + mTable.dot(KEY_PK_ID) + "=?";

        mSqlGetBookNodes =
                SELECT_ + Node.getColumns(mTable)
                + _FROM_ + listTable.ref() + listTable.join(mTable)
                + _WHERE_ + listTable.dot(KEY_FK_BOOK) + "=?";

        mSqlNodesInsertBase =
                "INSERT INTO " + TBL_BOOK_LIST_NODE_STATE + " ("
                + KEY_BL_NODE_KEY
                + ',' + KEY_BL_NODE_LEVEL
                + ',' + KEY_BL_NODE_GROUP
                + ',' + KEY_BL_NODE_EXPANDED

                + ',' + KEY_FK_BOOKSHELF
                + ',' + KEY_FK_STYLE
                + ')'
                + " SELECT DISTINCT "
                + KEY_BL_NODE_KEY
                + ',' + KEY_BL_NODE_LEVEL
                + ',' + KEY_BL_NODE_GROUP
                + ',' + KEY_BL_NODE_EXPANDED

                + ",?,?"

                + _FROM_ + mTable.getName()
                // only store visible rows; all others will be
                // considered non-visible and collapsed.
                + _WHERE_ + KEY_BL_NODE_VISIBLE + "=1";
    }

    @NonNull
    public TableDefinition getTable() {
        return mTable;
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
        SynchronizedStatement stmt = mStatementManager.get(STMT_COUNT_VIS_ROWS);
        if (stmt == null) {
            stmt = mStatementManager.add(STMT_COUNT_VIS_ROWS,
                                         "SELECT COUNT(*) FROM " + mTable.getName()
                                         + _WHERE_ + KEY_BL_NODE_VISIBLE + "=1");
        }
        final int count;
        // no params here, so don't need synchronized... but leave as reminder
        //        synchronized (stmt) {
        count = (int) stmt.simpleQueryForLongOrZero();
        //        }
        return count;
    }

    /**
     * Update the passed node with the actual list position,
     * <strong>taking into account invisible rows</strong>.
     *
     * @param node to set
     */
    void findAndSetListPosition(@NonNull final Node node) {
        SynchronizedStatement stmt = mStatementManager.get(STMT_COUNT_VIS_ROWS_BEFORE);
        if (stmt == null) {
            stmt = mStatementManager.add(STMT_COUNT_VIS_ROWS_BEFORE,
                                         "SELECT COUNT(*) FROM " + mTable.getName()
                                         + _WHERE_ + KEY_BL_NODE_VISIBLE + "=1"
                                         + _AND_ + KEY_PK_ID + "<?");
        }
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
                    // do this for only ONE level below the current node
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
                    .filter(node -> node.isVisible() && node.getRowId() >= 0)
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
            // developer sanity check
            if (!"< = > <= >=".contains(levelOperand)) {
                throw new IllegalArgumentException("levelOperand=`" + levelOperand + '`');
            }
        }

        //Note to self: levelOperand is concatenated, so do not cache this stmt.
        final String sql = UPDATE_ + mTable.getName() + _SET_
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

            SynchronizedStatement stmt = mStatementManager.get(STMT_DEL_ALL_NODES);
            if (stmt == null) {
                stmt = mStatementManager.add(
                        STMT_DEL_ALL_NODES,
                        // delete all rows for the current bookshelf/style
                        "DELETE FROM " + TBL_BOOK_LIST_NODE_STATE.getName()
                        + _WHERE_ + KEY_FK_BOOKSHELF + "=?"
                        + _AND_ + KEY_FK_STYLE + "=?");
            }

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, mBookshelfId);
                stmt.bindLong(2, mStyle.getId());

                final int rowsDeleted = stmt.executeUpdateDelete();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                    Log.d(TAG, "saveAllNodes"
                               + "|rowsDeleted=" + rowsDeleted);
                }
            }

            stmt = mStatementManager.get(STMT_SAVE_ALL_NODES);
            if (stmt == null) {
                stmt = mStatementManager.add(STMT_SAVE_ALL_NODES, mSqlNodesInsertBase);
            }

            // Read all visible nodes, and send them to the permanent table.
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, mBookshelfId);
                stmt.bindLong(2, mStyle.getId());

                final int rowsUpdated = stmt.executeUpdateDelete();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
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
     * {@link #setNode} 1. Update a single node.
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
                    UPDATE_ + mTable.getName() + _SET_
                    + KEY_BL_NODE_EXPANDED + "=?" + ',' + KEY_BL_NODE_VISIBLE + "=?"
                    + _WHERE_ + KEY_PK_ID + "=?");
        }

        final int rowsUpdated;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindBoolean(1, expand);
            stmt.bindBoolean(2, visible);
            stmt.bindLong(3, rowId);
            rowsUpdated = stmt.executeUpdateDelete();
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            Log.d(TAG, "updateNode"
                       + "|rowId=" + rowId
                       + "|expand=" + expand
                       + "|visible=" + visible
                       + "|rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * {@link #setNode} 2. Find the next row ('after' the given row) at the given level (or lower).
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
                    SELECT_ + KEY_PK_ID + _FROM_ + mTable.getName()
                    + _WHERE_ + KEY_PK_ID + ">?" + _AND_ + KEY_BL_NODE_LEVEL + "<=?"
                    + _ORDER_BY_ + KEY_PK_ID + " LIMIT 1");
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
     * {@link #setNode} 3. Update the nodes <strong>between</strong> the two given rows;
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
                    UPDATE_ + mTable.getName() + _SET_
                    + KEY_BL_NODE_VISIBLE + "=?,"
                    + KEY_BL_NODE_EXPANDED + "=?"
                    + _WHERE_ + KEY_PK_ID + ">?" + _AND_ + KEY_PK_ID + "<?"
                    + _AND_ + KEY_BL_NODE_LEVEL + "<?");
        }

        final int rowsUpdated;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindBoolean(1, expandAndVisible);
            stmt.bindBoolean(2, expandAndVisible);

            stmt.bindLong(3, startRowExcl);
            stmt.bindLong(4, endRowExcl);

            stmt.bindLong(5, maxLevel);

            rowsUpdated = stmt.executeUpdateDelete();
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
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
                        UPDATE_ + mTable.getName() + _SET_
                        + KEY_BL_NODE_VISIBLE + "=?,"
                        + KEY_BL_NODE_EXPANDED + "=?"
                        + _WHERE_ + KEY_PK_ID + ">?" + _AND_ + KEY_PK_ID + "<?"
                        + _AND_ + KEY_BL_NODE_LEVEL + "=?");
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
     * {@link #setNode} 4. Save the state for a single node to permanent storage.
     *
     * <strong>Note:</strong> always uses the current bookshelf/style
     *
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param fromLevel   from this level onwards
     * @param fromRowIncl between this row (inclusive)
     * @param endRowExcl  and this row (exclusive)
     */
    private void saveNodesBetween(final long fromLevel,
                                  final long fromRowIncl,
                                  final long endRowExcl) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
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

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                TBL_BOOK_LIST_NODE_STATE
                        .dumpTable(mSyncedDb, "saveNodesBetween", "before delete", 10,
                                   KEY_PK_ID);
            }

            SynchronizedStatement stmt = mStatementManager.get(STMT_DEL_NODES_BETWEEN);
            if (stmt == null) {
                stmt = mStatementManager.add(
                        STMT_DEL_NODES_BETWEEN,
                        "DELETE FROM " + TBL_BOOK_LIST_NODE_STATE.getName()
                        + _WHERE_ + KEY_FK_BOOKSHELF + "=?"
                        + _AND_ + KEY_FK_STYLE + "=?"

                        // leave the parent levels untouched
                        + _AND_ + KEY_BL_NODE_LEVEL + ">=?"

                        + _AND_ + KEY_BL_NODE_KEY + " IN ("
                        + " SELECT DISTINCT " + KEY_BL_NODE_KEY + _FROM_ + mTable.getName()
                        + _WHERE_ + KEY_PK_ID + ">=? AND " + KEY_PK_ID + "<? )");
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

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                    Log.d(TAG, "saveNodesBetween|rowsDeleted=" + rowsDeleted);
                }
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                TBL_BOOK_LIST_NODE_STATE
                        .dumpTable(mSyncedDb, "saveNodesBetween", "delete done", 10,
                                   KEY_PK_ID);
            }

            stmt = mStatementManager.get(STMT_SAVE_NODES_BETWEEN);
            if (stmt == null) {
                stmt = mStatementManager.add(STMT_SAVE_NODES_BETWEEN,
                                             mSqlNodesInsertBase
                                             + _AND_ + KEY_PK_ID + ">=? AND " + KEY_PK_ID + "<?");
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

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                    Log.d(TAG, "saveNodesBetween|rowsInserted=" + rowsInserted);
                }
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
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
     * Cleanup.
     */
    @Override
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
            mRowId = cursor.getInt(0);
            mKey = cursor.getString(1);
            mLevel = cursor.getInt(2);
            mIsExpanded = cursor.getInt(3) != 0;
            mIsVisible = cursor.getInt(4) != 0;
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
