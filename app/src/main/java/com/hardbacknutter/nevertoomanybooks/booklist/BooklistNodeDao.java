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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.core.debug.DbDebugUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;

/**
 * Provides an isolation of the logic and data needed to save and restore the tree structure
 * and a backend table to store enough information in the database to rebuild after an app restart.
 * <p>
 * Entry points:
 * <ul>
 *      <li>{@link #setAllNodes(int, boolean)}: update ALL nodes starting from a set level.</li>
 *      <li>{@link #setNode(long, int, boolean, int)}: update a single node (and it's branch).</li>
 *      <li>{@link #restoreSavedState()}: restore the tree view for all nodes from storage.</li>
 * </ul>
 */
public class BooklistNodeDao {

    /** Log tag. */
    private static final String TAG = "BooklistNodeDao";

    /** Reference to singleton. */
    @NonNull
    private final SynchronizedDb db;

    /** The current shelf. */
    private final long bookshelfId;
    /** The current style. */
    @NonNull
    private final Style style;
    /** The current list table. */
    @NonNull
    private final TableDefinition listTable;

    /**
     * Constructor.
     *
     * @param db        Database Access
     * @param listTable the {@link Booklist} table
     * @param style     Style reference.
     * @param bookshelf to use
     */
    BooklistNodeDao(@NonNull final SynchronizedDb db,
                    @NonNull final TableDefinition listTable,
                    @NonNull final Style style,
                    @NonNull final Bookshelf bookshelf) {

        this.db = db;
        this.listTable = listTable;
        bookshelfId = bookshelf.getId();
        this.style = style;
    }

    /**
     * Purge <strong>all</strong> Booklist node state data.
     *
     * @param db Database Access
     */
    public static void clearAll(@NonNull final SynchronizedDb db) {
        db.execSQL(Sql.DELETE_ALL);
    }

    /**
     * Expand or collapse <strong>all</strong> nodes.
     *
     * @param topLevel the desired top-level which must be kept visible
     * @param expand   the state to apply to levels 'below' the topLevel (level > topLevel),
     */
    void setAllNodes(@IntRange(from = 1) final int topLevel,
                     final boolean expand) {

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
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
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * {@link #setAllNodes} 1. Update the status/visibility of all nodes for the given level.
     * <p>
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
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
            // developer sanity check
            if (!"< = > <= >=".contains(levelOperand)) {
                throw new IllegalArgumentException("levelOperand=`" + levelOperand + '`');
            }
        }

        // levelOperand is concatenated!!!
        final String sql =
                Sql.UPDATE_ + listTable.getName()
                + Sql._SET_ + DBKey.BL_NODE_EXPANDED + "=?," + DBKey.BL_NODE_VISIBLE
                + "=?"
                + Sql._WHERE_ + DBKey.BL_NODE_LEVEL + levelOperand + "?";

        final int rowsUpdated;
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.bindBoolean(1, expand);
            stmt.bindBoolean(2, visible);
            stmt.bindLong(3, nodeLevel);
            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            LoggerFactory.getLogger().d(TAG, "updateNodesForLevel"
                                             + "|level=" + levelOperand + nodeLevel
                                             + "|expand=" + expand
                                             + "|visible=" + visible
                                             + "|rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * {@link #setAllNodes} 2. Save the state for all nodes to permanent storage.
     * We only store visible nodes and their expansion state.
     * <p>
     * <strong>Note:</strong> always use the current bookshelf/style
     */
    private void saveAllNodes() {
        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }

            int rowsUpdated;

            try (SynchronizedStatement stmt = db.compileStatement(
                    Sql.DELETE_ALL_FOR_CURRENT_SHELF)) {
                stmt.bindLong(1, bookshelfId);
                stmt.bindLong(2, style.getId());
                rowsUpdated = stmt.executeUpdateDelete();
            }
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                LoggerFactory.getLogger().d(TAG, "saveAllNodes", "delete",
                                            "rowsUpdated=" + rowsUpdated);
            }

            // Read all visible nodes, and send them to the permanent table.
            try (SynchronizedStatement stmt = db.compileStatement(
                    String.format(Sql.SAVE_ALL_NODES, listTable.getName()))) {
                stmt.bindLong(1, bookshelfId);
                stmt.bindLong(2, style.getId());
                rowsUpdated = stmt.executeUpdateDelete();
            }
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
                LoggerFactory.getLogger().d(TAG, "saveAllNodes", "insert",
                                            "rowsUpdated=" + rowsUpdated);
            }

            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } catch (@NonNull final SQLiteConstraintException e) {
            if (BuildConfig.DEBUG /* always */) {
                // DEBUG ONLY!
                // This protects the developer when changing the structure irresponsibly...
                clearAll(db);
                throw new IllegalStateException(e);
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
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
     * @param nodeRowId          of the node in the list
     * @param nodeLevel          the level of the node
     * @param expandNode         the state to set for this node
     * @param relativeChildLevel up to and including this (relative to nodeLevel!) child level.
     */
    void setNode(final long nodeRowId,
                 @IntRange(from = 1) final int nodeLevel,
                 final boolean expandNode,
                 @IntRange(from = 0) final int relativeChildLevel) {

        final int groupCount = style.getGroupCount();

        if (relativeChildLevel > groupCount) {
            throw new IllegalArgumentException("groupCount=" + groupCount
                                               + "|relativeChildLevel=" + relativeChildLevel);
        }

        Synchronizer.SyncLock txLock = null;
        try {
            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
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
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * {@link #setNode} 1. Update a single node.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param rowId  the row/node to update
     * @param expand state to set
     */
    private void updateNode(final long rowId,
                            final boolean expand) {
        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final int rowsUpdated;
        try (SynchronizedStatement stmt = db.compileStatement(
                String.format(Sql.UPDATE_NODE, listTable.getName()))) {
            stmt.bindBoolean(1, expand);
            stmt.bindLong(2, rowId);
            rowsUpdated = stmt.executeUpdateDelete();
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            LoggerFactory.getLogger().d(TAG, "updateNode",
                                        "rowId=" + rowId,
                                        "expand=" + expand,
                                        "rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * {@link #setNode} 2. Find the next row ('after' the given row) at the given level (or lower).
     * <p>
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
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final long nextRowId;
        try (SynchronizedStatement stmt = db.compileStatement(
                String.format(Sql.FIND_NEXT_NODE, listTable.getName()))) {
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
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param startRowExcl       between this row
     * @param endRowExcl         and this row
     * @param nodeLevel          the level which was clicked
     * @param relativeChildLevel up to and including this (relative to nodeLevel!) child level.
     */
    private void showAndExpandNodesBetween(final long startRowExcl,
                                           final long endRowExcl,
                                           @IntRange(from = 1) final int nodeLevel,
                                           final int relativeChildLevel) {
        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final int level = Math.min(nodeLevel + relativeChildLevel, style.getGroupCount() + 1);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            LoggerFactory.getLogger().d(TAG, "showAndExpandNodesBetween",
                                        "nodeLevel=" + nodeLevel,
                                        "relativeChildLevel=" + relativeChildLevel,
                                        "level=" + level);
        }

        int rowsUpdated;

        try (SynchronizedStatement stmt = db.compileStatement(
                String.format(Sql.UPDATE_NODES_BETWEEN_1, listTable.getName()))) {
            stmt.bindLong(1, startRowExcl);
            stmt.bindLong(2, endRowExcl);
            stmt.bindLong(3, level);
            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            LoggerFactory.getLogger().d(TAG, "showAndExpandNodesBetween|step1",
                                        "startRowExcl=" + startRowExcl,
                                        "endRowExcl=" + endRowExcl,
                                        "rowsUpdated=" + rowsUpdated);
        }

        try (SynchronizedStatement stmt = db.compileStatement(
                String.format(Sql.UPDATE_NODES_BETWEEN_2, listTable.getName()))) {
            stmt.bindLong(1, startRowExcl);
            stmt.bindLong(2, endRowExcl);
            stmt.bindLong(3, level);
            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            LoggerFactory.getLogger().d(TAG, "showAndExpandNodesBetween|step2",
                                        "startRowExcl=" + startRowExcl,
                                        "endRowExcl=" + endRowExcl,
                                        "rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * {@link #setNode} 3. Collapse/hide the nodes <strong>between</strong> the two given rows;
     * i.e. <strong>excluding</strong> the start and end row.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param startRowExcl between this row
     * @param endRowExcl   and this row
     */
    private void collapseAndHideNodesBetween(final long startRowExcl,
                                             final long endRowExcl) {
        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final int rowsUpdated;
        try (SynchronizedStatement stmt = db.compileStatement(
                String.format(Sql.COLLAPSE_AND_HIDE_NODES_BETWEEN, listTable.getName()))) {
            stmt.bindLong(1, startRowExcl);
            stmt.bindLong(2, endRowExcl);
            rowsUpdated = stmt.executeUpdateDelete();
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            LoggerFactory.getLogger().d(TAG, "collapseAndHideNodesBetween",
                                        "startRowExcl=" + startRowExcl,
                                        "endRowExcl=" + endRowExcl,
                                        "rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * {@link #setNode} 4. Save the state for a single node (and below) to permanent storage.
     * <p>
     * <strong>Note:</strong> always uses the current bookshelf/style
     * <p>
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
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            LoggerFactory.getLogger().d(TAG, "saveNodesBetween",
                                        "fromRowId=" + startRowIncl,
                                        "fromLevel=" + nodeLevel,
                                        "endRowExcl=" + endRowExcl);

            DbDebugUtils.dumpTable(db, TBL_BOOK_LIST_NODE_STATE, 10, DBKey.PK_ID,
                                   "saveNodesBetween", "before delete");
        }

        int rowsUpdated;

        // delete the given rows (inc. start, excl. end) and level
        // for the current bookshelf/style
        try (SynchronizedStatement stmt = db.compileStatement(
                String.format(Sql.DELETE_NODES_BETWEEN, listTable.getName()))) {
            stmt.bindLong(1, bookshelfId);
            stmt.bindLong(2, style.getId());

            stmt.bindLong(3, nodeLevel);

            stmt.bindLong(4, startRowIncl);
            stmt.bindLong(5, endRowExcl);

            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            LoggerFactory.getLogger().d(TAG, "saveNodesBetween", "rowsDeleted=" + rowsUpdated);
            DbDebugUtils.dumpTable(db, TBL_BOOK_LIST_NODE_STATE, 10, DBKey.PK_ID,
                                   "saveNodesBetween", "delete done");
        }

        // Read all nodes below the given node (again inc. start, excl. end),
        // and send them to the permanent table.
        try (SynchronizedStatement stmt = db.compileStatement(
                String.format(Sql.SAVE_NODES_BETWEEN, listTable.getName()))) {
            stmt.bindLong(1, bookshelfId);
            stmt.bindLong(2, style.getId());

            stmt.bindLong(3, startRowIncl);
            stmt.bindLong(4, endRowExcl);

            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            LoggerFactory.getLogger().d(TAG, "saveNodesBetween", "rowsInserted=" + rowsUpdated);
            DbDebugUtils.dumpTable(db, TBL_BOOK_LIST_NODE_STATE, 10, DBKey.PK_ID,
                                   "saveNodesBetween", "insert done");
        }
    }


    /**
     * Restore the expanded and/or visible node status.
     * The logic here assumes the target table {@link #listTable} will have ALL rows
     * set to "0/0". It will update ONLY rows from storage that have "-/1" and/or "1/-"
     * <p>
     * <strong>Transaction:</strong> required
     */
    void restoreSavedState() {
        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final String sqlTemplate =
                Sql.UPDATE_ + listTable.getName() + Sql._SET_ + "%s=1"
                + Sql._WHERE_ + DBKey.PK_ID + " IN ("
                + Sql.SELECT_ + listTable.dot(DBKey.PK_ID) + Sql._FROM_ + listTable.ref()
                + "," + TBL_BOOK_LIST_NODE_STATE.ref()
                + Sql._WHERE_
                + TBL_BOOK_LIST_NODE_STATE.dot(DBKey.FK_BOOKSHELF) + "=?"
                + Sql._AND_
                + TBL_BOOK_LIST_NODE_STATE.dot(DBKey.FK_STYLE) + "=?"
                + Sql._AND_
                + listTable.dot(DBKey.BL_NODE_KEY) + "="
                + TBL_BOOK_LIST_NODE_STATE.dot(DBKey.BL_NODE_KEY)
                + Sql._AND_
                + listTable.dot(DBKey.BL_NODE_LEVEL) + "="
                + TBL_BOOK_LIST_NODE_STATE.dot(DBKey.BL_NODE_LEVEL)
                + Sql._AND_
                + listTable.dot(DBKey.BL_NODE_GROUP) + "="
                + TBL_BOOK_LIST_NODE_STATE.dot(DBKey.BL_NODE_GROUP)
                + Sql._AND_
                + TBL_BOOK_LIST_NODE_STATE.dot("%s") + "=1"
                + ")";

        restoreSavedState(sqlTemplate, DBKey.BL_NODE_EXPANDED);
        restoreSavedState(sqlTemplate, DBKey.BL_NODE_VISIBLE);

        adjustVisibility();
    }

    /**
     * {@link #restoreSavedState()} 1. Update visibility/expanded flag (single column at a time)
     */
    private void restoreSavedState(@NonNull final String sqlTemplate,
                                   @NonNull final String columnName) {
        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        final String sql = String.format(sqlTemplate, columnName, columnName);

        final int rowsUpdated;
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.bindLong(1, bookshelfId);
            stmt.bindLong(2, style.getId());
            rowsUpdated = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_NODE_STATE) {
            LoggerFactory.getLogger().d(TAG, "restoreSavedState",
                                        "bookshelfId=" + bookshelfId,
                                        "style.getId()=" + style.getId(),
                                        "columnName=" + columnName,
                                        "rowsUpdated=" + rowsUpdated,
                                        "sql=" + sql);
        }
    }

    /**
     * {@link #restoreSavedState()} 2. Update branches which were not in the saved data.
     */
    private void adjustVisibility() {
        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // level + key
        final Collection<Pair<Integer, String>> keyPrefixes = new ArrayList<>();

        // Find all branches (groups on level 2+) with visible nodes
        try (Cursor cursor = db.rawQuery(
                String.format(Sql.ADJUST_VISIBILITY_1, listTable.getName()),
                new String[]{String.valueOf(style.getGroupCount())})) {

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
        try (SynchronizedStatement stmt = db.compileStatement(
                String.format(Sql.ADJUST_VISIBILITY_2, listTable.getName()))) {

            for (final Pair<Integer, String> entry : keyPrefixes) {
                stmt.bindLong(1, entry.first);
                stmt.bindString(2, entry.second);
                rows += stmt.executeUpdateDelete();
            }
        }

        if (BuildConfig.DEBUG /* always */) {
            LoggerFactory.getLogger().d(TAG, "adjustVisibility", "rows=" + rows);
        }
    }

    private static final class Sql {

        private static final String SELECT_DISTINCT_ = "SELECT DISTINCT ";
        private static final String SELECT_ = "SELECT ";
        private static final String _FROM_ = " FROM ";
        private static final String _WHERE_ = " WHERE ";
        private static final String _AND_ = " AND ";
        private static final String _ORDER_BY_ = " ORDER BY ";

        private static final String UPDATE_ = "UPDATE ";
        private static final String _SET_ = " SET ";

        private static final String DELETE_FROM_ = "DELETE FROM ";

        /**
         * {@link #saveAllNodes}.
         * Delete all rows for the current bookshelf/style.
         */
        private static final String DELETE_ALL_FOR_CURRENT_SHELF =
                DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE.getName()
                + _WHERE_ + DBKey.FK_BOOKSHELF + "=?" + _AND_ + DBKey.FK_STYLE + "=?";

        /**
         * Insert statement for {@link #saveAllNodes}.
         * <p>
         * We save only nodes which are expanded and/or visible.
         * This does mean we always store the state of level 1 even when not needed.
         * but oh well... no big deal.
         */
        private static final String SAVE_ALL_NODES =
                "INSERT INTO " + TBL_BOOK_LIST_NODE_STATE
                + " (" + DBKey.FK_BOOKSHELF + ',' + DBKey.FK_STYLE
                + ',' + DBKey.BL_NODE_KEY
                + ',' + DBKey.BL_NODE_LEVEL
                + ',' + DBKey.BL_NODE_GROUP
                + ',' + DBKey.BL_NODE_EXPANDED
                + ',' + DBKey.BL_NODE_VISIBLE
                + ") "
                + SELECT_DISTINCT_ + "?,?"
                + ',' + DBKey.BL_NODE_KEY
                + ',' + DBKey.BL_NODE_LEVEL
                + ',' + DBKey.BL_NODE_GROUP
                + ',' + DBKey.BL_NODE_EXPANDED
                + ',' + DBKey.BL_NODE_VISIBLE

                + _FROM_ + /* listTable.getName() */ "%s"
                + _WHERE_ + "(" + DBKey.BL_NODE_EXPANDED + "=1 OR "
                + DBKey.BL_NODE_VISIBLE + "=1)";
        /** Insert statement for {@link #saveNodesBetween}. */
        private static final String SAVE_NODES_BETWEEN =
                SAVE_ALL_NODES + _AND_ + DBKey.PK_ID + ">=? AND " + DBKey.PK_ID + "<?";
        /** {@link #saveNodesBetween}. */
        private static final String DELETE_NODES_BETWEEN =
                // delete the rows for the current bookshelf/style
                DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE.getName()
                + _WHERE_ + DBKey.FK_BOOKSHELF + "=?" + _AND_ + DBKey.FK_STYLE + "=?"
                // but leave the parent levels untouched
                + _AND_ + DBKey.BL_NODE_LEVEL + ">=?"
                // and only between the given nodes
                + _AND_ + DBKey.BL_NODE_KEY + " IN ("
                + SELECT_DISTINCT_ + DBKey.BL_NODE_KEY + _FROM_
                + /* listTable.getName() */ "%s"
                + _WHERE_ + DBKey.PK_ID + ">=? AND " + DBKey.PK_ID + "<? )";
        /** {@link #findNextNode}. */
        private static final String FIND_NEXT_NODE =
                SELECT_ + DBKey.PK_ID + _FROM_ + /* listTable.getName() */ "%s"
                + _WHERE_ + DBKey.PK_ID + ">?" + _AND_ + DBKey.BL_NODE_LEVEL + "<=?"
                + _ORDER_BY_ + DBKey.PK_ID + " LIMIT 1";

        /**
         * {@link #collapseAndHideNodesBetween}.
         * Simply set all of them to invisible/unexpanded.
         */
        private static final String COLLAPSE_AND_HIDE_NODES_BETWEEN =
                UPDATE_ + /* listTable.getName() */ "%s"
                + _SET_ + DBKey.BL_NODE_VISIBLE + "=0," + DBKey.BL_NODE_EXPANDED + "=0"
                + _WHERE_ + DBKey.PK_ID + ">?" + _AND_ + DBKey.PK_ID + "<?";

        /**
         * {@link #showAndExpandNodesBetween}.
         * Handle all levels except the lowest level.
         */
        private static final String UPDATE_NODES_BETWEEN_1 =
                UPDATE_ + /* listTable.getName() */ "%s"
                + _SET_ + DBKey.BL_NODE_VISIBLE + "=1," + DBKey.BL_NODE_EXPANDED + "=1"
                + _WHERE_ + DBKey.PK_ID + ">?" + _AND_ + DBKey.PK_ID + "<?"
                + _AND_ + DBKey.BL_NODE_LEVEL + "<?";

        /**
         * {@link #showAndExpandNodesBetween}.
         * The lowest level we want visible but not expanded.
         */
        private static final String UPDATE_NODES_BETWEEN_2 =
                UPDATE_ + /* listTable.getName() */ "%s"
                + _SET_ + DBKey.BL_NODE_VISIBLE + "=1," + DBKey.BL_NODE_EXPANDED + "=0"
                + _WHERE_ + DBKey.PK_ID + ">?" + _AND_ + DBKey.PK_ID + "<?"
                + _AND_ + DBKey.BL_NODE_LEVEL + "=?";


        /** {@link #updateNode}. */
        private static final String UPDATE_NODE =
                UPDATE_ + /* listTable.getName() */ "%s"
                + _SET_ + DBKey.BL_NODE_EXPANDED + "=?"
                + ',' + DBKey.BL_NODE_VISIBLE + "=1"
                + _WHERE_ + DBKey.PK_ID + "=?";

        /** {@link #adjustVisibility()}. */
        private static final String ADJUST_VISIBILITY_1 =
                SELECT_DISTINCT_ + DBKey.BL_NODE_KEY + ',' + DBKey.BL_NODE_LEVEL
                + _FROM_ + /* listTable.getName() */ "%s"
                + _WHERE_ + DBKey.BL_NODE_VISIBLE + "=1"
                // Groups only - Don't do books
                + _AND_ + DBKey.BL_NODE_LEVEL + " BETWEEN 2 AND ?";

        /** {@link #adjustVisibility()}. */
        private static final String ADJUST_VISIBILITY_2 =
                UPDATE_ + /* listTable.getName() */ "%s"
                + _SET_ + DBKey.BL_NODE_VISIBLE + "=1"
                + _WHERE_ + DBKey.BL_NODE_VISIBLE + "=0"
                + _AND_ + DBKey.BL_NODE_LEVEL + "=?"
                + _AND_ + DBKey.BL_NODE_KEY + " LIKE ?";

        /** Maintenance/debug usage. Simple clear all state data. */
        private static final String DELETE_ALL = DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE;
    }
}
