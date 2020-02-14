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

import android.database.Cursor;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
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
 * <li>expandABC: entry points into this class to expand/collapse node</li>
 * <li>updateABC: private methods that write to the in-memory table</li>
 * <li>saveABC: private methods that write to the database</li>
 * </ul>
 */
public class RowStateDAO {

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
    private static final String STMT_UPDATE_NODES_BETWEEN = "uNSBetween";


    /** Statement cache name. */
    private static final String STMT_GET_NEXT_NODE_AT_SAME_LEVEL = "next";
    /** Statement cache name. */
    private static final String STMT_GET_VISIBILITY = "vis";
    /** Statement cache name. */
    private static final String STMT_COUNT_VIS_ROWS_BEFORE = "cntVisRowsBefore";
    /** Statement cache name. */
    private static final String STMT_COUNT_VIS_ROWS = "cntVisRows";

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;
    /** This is a reference only. Cleanup is done by the owner of this object. */
    @NonNull
    private final SynchronizedDb mSyncedDb;
    @NonNull
    private final SqlStatementManager mStatementManager;
    /** The table represented by this class. */
    @NonNull
    private final TableDefinition mTable;
    @NonNull
    private final Bookshelf mBookshelf;
    @NonNull
    private final BooklistStyle mStyle;
    /** used by finalize so close does not get called twice. */
    private boolean mCloseWasCalled;

    /**
     * Constructor. Defines, but does not create the table. See {@link #build}.
     *
     * <strong>Note:</strong> the style/bookshelf the booklist is displaying, determines
     * the list-table and hence the RowStateDAO.
     *
     * @param syncedDb   database
     * @param instanceId unique id to be used as suffix on the table name.
     * @param bookshelf  to use
     * @param style      Booklist style to use;
     *                   this must be the resolved style as used by the passed bookshelf
     */
    RowStateDAO(@NonNull final SynchronizedDb syncedDb,
                final int instanceId,
                @NonNull final Bookshelf bookshelf,
                @NonNull final BooklistStyle style) {

        mSyncedDb = syncedDb;
        mBookshelf = bookshelf;
        mStyle = style;

        mTable = new TableDefinition(TMP_TBL_BOOK_LIST_ROW_STATE);
        mTable.setName(mTable.getName() + instanceId);
        mStatementManager = new SqlStatementManager(mSyncedDb, TAG + "|" + instanceId);
    }

    /**
     * Build the table according to the desired listState.
     *
     * <strong>Note:</strong> called once at creation time, hence the statements used
     * here are not cached.
     *
     * @param listTable the list-table to be referenced
     * @param listState to use
     */
    void build(@NonNull final TableDefinition listTable,
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
        String baseSql = "INSERT INTO " + mTable.getName()
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
                String sql = baseSql
                             // KEY_BL_NODE_VISIBLE: all visible
                             + ",1"
                             // KEY_BL_NODE_EXPANDED: all expanded
                             + ",1"
                             + " FROM " + listTable.ref()
                             + " ORDER BY " + listTable.dot(KEY_PK_ID);

                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    int rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                        Log.d(TAG, "PREF_REBUILD_ALWAYS_EXPANDED"
                                   + "|rowsUpdated=" + rowsUpdated
                                   + "|sql=" + sql);
                    }
                }
                break;
            }
            case PREF_REBUILD_ALWAYS_COLLAPSED: {
                String sql = baseSql
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
                    int rowsUpdated = stmt.executeUpdateDelete();
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
                String sql =
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
                    stmt.bindLong(1, mBookshelf.getId());
                    stmt.bindLong(2, mStyle.getId());
                    int rowsUpdated = stmt.executeUpdateDelete();
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
                String sql = baseSql + ",1,1 FROM " + listTable.ref()
                             + " ORDER BY " + listTable.dot(KEY_PK_ID);
                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    int rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                        Log.d(TAG, "PREF_REBUILD_PREFERRED_STATE"
                                   + "|rowsUpdated=" + rowsUpdated
                                   + "|sql=" + sql);
                    }
                }

                // Now set the preferred expand/visibility status.
                expandAllNodes(mStyle.getTopLevel(), false);
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
        String sql = "SELECT "
                     + Csv.join(",", listTable.getDomains(),
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
     * @param rowId to check
     *
     * @return Actual (and visible) list position.
     */
    int getListPosition(final long rowId) {

        boolean isVisible = isVisible(rowId);
        int count = countVisibleRowsBefore(rowId);
        if (isVisible) {
            // If the specified row is visible, then the count is the position.
            return count;
        } else {
            // otherwise it's the previous visible row == count-1 (or the top row)
            return count > 0 ? count - 1 : 0;
        }
    }

    /**
     * Check the row is visible.
     *
     * @param rowId to check
     *
     * @return visibility
     */
    private boolean isVisible(final long rowId) {

        SynchronizedStatement stmt = mStatementManager.get(STMT_GET_VISIBILITY);
        if (stmt == null) {
            stmt = mStatementManager.add(
                    STMT_GET_VISIBILITY,
                    "SELECT " + KEY_BL_NODE_VISIBLE + " FROM " + mTable.getName()
                    + " WHERE " + KEY_PK_ID + "=?");
        }

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, rowId);
            return stmt.simpleQueryForLongOrZero() == 1;
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
        int count;
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
        int count;
        // no params here, so don't need synchronized... but leave as reminder
        //        synchronized (stmt) {
        count = (int) stmt.count();
        //        }
        return count;
    }

    /**
     * Ensure al leaf nodes up to the root node for the given row are visible.
     *
     * @param rowId we want to become visible
     * @param level of that row
     */
    void ensureRowIsVisible(final long rowId,
                            final int level) {

        String sql = "SELECT " + KEY_PK_ID
                     + ',' + KEY_BL_NODE_EXPANDED
                     + ',' + KEY_BL_NODE_VISIBLE
                     + " FROM " + mTable.getName()
                     // we'll loop for all levels
                     + " WHERE " + KEY_BL_NODE_LEVEL + "=?"
                     // before the given row
                     + " AND " + KEY_PK_ID + "<=?"
                     // DESC so we 'walk' upwards
                     + " ORDER BY " + KEY_PK_ID + " DESC LIMIT 1";

        for (int leafLevel = 1; leafLevel < level; leafLevel++) {

            try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{
                    String.valueOf(leafLevel),
                    String.valueOf(rowId)})) {

                if (cursor.moveToFirst()) {
                    long leafRowId = cursor.getLong(0);
                    boolean leafIsExpanded = cursor.getLong(1) != 0;
                    boolean leafIsVisible = cursor.getLong(2) != 0;

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                        Log.d(TAG, "ensureRowIsVisible"
                                   + "|rowId=" + rowId
                                   + "|leafLevel=" + leafLevel
                                   + "|leafRowId=" + leafRowId
                                   + "|leafIsExpanded=" + leafIsExpanded
                                   + "|leafIsVisible=" + leafIsVisible
                                   + "|");
                    }
                    // If leaf is not the node we are checking, and not currently expanded
                    if (leafRowId != rowId && !leafIsExpanded) {
                        // Expand (and make visible) the given node
                        setNode(leafRowId, true, leafLevel,
                                // but keep the children collapsed/visible.
                                false, true,
                                // do this for only ONE level below the current leaf
                                leafLevel + 1);
                    }
                } else {
                    return;
                }
            }
        }
    }

    /**
     * Get all positions, and the row info for that position,
     * at which the specified book appears.
     *
     * @param bookId the book to find
     *
     * @return Array of {@link ListRowDetails}. Can be empty, but never {@code null}.
     */
    @NonNull
    ArrayList<ListRowDetails> getBookPositions(@NonNull final TableDefinition listTable,
                                               final long bookId) {
        String sql = "SELECT "
                     + mTable.dot(KEY_PK_ID)
                     + ',' + mTable.dot(KEY_BL_NODE_VISIBLE)
                     + ',' + mTable.dot(KEY_BL_NODE_LEVEL)
                     + " FROM " + listTable.ref() + listTable.join(mTable)
                     + " WHERE " + listTable.dot(KEY_FK_BOOK) + "=?";

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(bookId)})) {
            ArrayList<ListRowDetails> rows = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                int rowId = cursor.getInt(0);
                boolean visible = cursor.getInt(1) == 1;
                int level = cursor.getInt(2);
                ListRowDetails rowDetails = new ListRowDetails(rowId, visible, level);
                rowDetails.listPosition = getListPosition(rowDetails.rowId);
                rows.add(rowDetails);
            }
            return rows;
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
        String sql = "UPDATE " + mTable.getName() + " SET "
                     + KEY_BL_NODE_EXPANDED + "=?," + KEY_BL_NODE_VISIBLE + "=?"
                     + " WHERE " + KEY_BL_NODE_LEVEL + levelOperand + "?";

        int rowsUpdated;
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
                stmt.bindLong(1, mBookshelf.getId());
                stmt.bindLong(2, mStyle.getId());

                int rowsDeleted = stmt.executeUpdateDelete();

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
                stmt.bindLong(1, mBookshelf.getId());
                stmt.bindLong(2, mStyle.getId());

                int rowsUpdated = stmt.executeUpdateDelete();

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                    Log.d(TAG, "saveAllNodes"
                               + "|rowsUpdated=" + rowsUpdated);
                }
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
     * Expand, collapse or toggle the state of passed leaf node.
     * <br><br>
     * The leaf node will always be kept visible.<br>
     * If the resulting state of the leaf node is:<br>
     * 'Expand': we set the children to the desired child state/visibility
     * (recurse up to the desired max child level)<br>
     * 'Collapse': all children will be set to Collapse/hidden (recurse ALL child levels)
     *
     * @param leafRowId of the node in the list
     * @param leafState one of {@link DesiredNodeState}
     *
     * @return {@code true} if the new leaf node state is expanded, {@code false} if collapsed.
     */
    boolean setNode(final long leafRowId,
                    @NonNull final DesiredNodeState leafState,
                    final boolean childExpand,
                    final boolean childVisible,
                    final int maxChildLevel) {

        // Get the current state of the node for the given row id.
        String sql = "SELECT " + KEY_BL_NODE_LEVEL + ',' + KEY_BL_NODE_EXPANDED
                     + " FROM " + mTable.getName()
                     + " WHERE " + KEY_PK_ID + "=?";

        int leafLevel;
        boolean leafIsExpanded;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(leafRowId)})) {
            if (cursor.moveToFirst()) {
                leafLevel = cursor.getInt(0);
                leafIsExpanded = cursor.getInt(1) != 0;
            } else {
                throw new IllegalStateException("rowId not found: " + leafRowId);
            }
        }

        // Decide what the new state for the leaf node should be.
        boolean expandLeaf;
        switch (leafState) {
            case Collapse:
                expandLeaf = false;
                break;
            case Expand:
                expandLeaf = true;
                break;

            case Toggle:
            default:
                expandLeaf = !leafIsExpanded;
                break;
        }

        setNode(leafRowId, expandLeaf, leafLevel,
                childExpand, childVisible, maxChildLevel);

        return expandLeaf;
    }

    /**
     * Expand/collapse the passed leaf node.
     * <br><br>
     * The leaf node will always be kept visible.<br>
     * If the resulting state of the leaf node is:<br>
     * 'Expand': we set the children to the desired child state/visibility
     * (recurse up to the desired max child level)<br>
     * 'Collapse': all children will be set to Collapse/hidden (recurse ALL child levels)
     *
     * <strong>Note:</strong> If the leafLevel is not known, or the state must be toggled,
     * then call {@link #setNode(long, DesiredNodeState, boolean, boolean, int)} instead.
     *
     * @param leafRowId of the node in the list
     */
    private void setNode(final long leafRowId,
                         final boolean leafExpand,
                         final int leafLevel,

                         final boolean childExpand,
                         final boolean childVisible,
                         final int maxChildLevel) {
        Synchronizer.SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            // expand/collapse the specified row, but always keep it visible.
            updateNode(leafRowId, leafExpand, true);

            // Find the next row ('after' the given row) at the given level (or lower).
            long endRowExcl = findNextNode(leafRowId, leafLevel);

            // and update the rows between the leaf, and the endRow
            if (leafExpand) {
                // use the desired child state/level
                updateNodesBetween(leafRowId, endRowExcl, childExpand, childVisible, maxChildLevel);
            } else {
                // collapsing the leaf, so just collapse/hide all children.
                updateNodesBetween(leafRowId, endRowExcl, false, false, Integer.MAX_VALUE);
            }

            // Store the state of these nodes.
            saveNodesBetween(leafRowId, leafLevel, endRowExcl);

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

        long nextRowId;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, rowId);
            stmt.bindLong(2, level);
            nextRowId = stmt.simpleQueryForLongOrZero();
        }

        // if there was no next node, use the end of the list.
        if (nextRowId <= 0) {
            nextRowId = Long.MAX_VALUE;
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

        int rowsUpdated;
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
     * @param startRowExcl between this row
     * @param endRowExcl   and this row
     * @param expand       state to set
     * @param visible      visibility to set
     * @param maxLevel     up to and including this level;
     *                     pass in {@code Integer.MAX_VALUE} for all levels.
     */
    private void updateNodesBetween(final long startRowExcl,
                                    final long endRowExcl,
                                    final boolean expand,
                                    final boolean visible,
                                    final int maxLevel) {

        SynchronizedStatement stmt = mStatementManager.get(STMT_UPDATE_NODES_BETWEEN);
        if (stmt == null) {
            stmt = mStatementManager.add(
                    STMT_UPDATE_NODES_BETWEEN,
                    "UPDATE " + mTable.getName() + " SET "
                    + KEY_BL_NODE_EXPANDED + "=?," + KEY_BL_NODE_VISIBLE + "=?"
                    + " WHERE " + KEY_PK_ID + ">?" + " AND " + KEY_PK_ID + "<?"
                    + " AND " + KEY_BL_NODE_LEVEL + "<=?");
        }

        int rowsUpdated;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindBoolean(1, expand);
            stmt.bindBoolean(2, visible);

            stmt.bindLong(3, startRowExcl);
            stmt.bindLong(4, endRowExcl);

            stmt.bindLong(5, maxLevel);

            rowsUpdated = stmt.executeUpdateDelete();
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
            Log.d(TAG, "updateNodesBetween"
                       + "|startRowExcl=" + startRowExcl
                       + "|endRowExcl=" + endRowExcl
                       + "|expand=" + expand
                       + "|visible=" + visible
                       + "|maxLevel=" + maxLevel
                       + "|rowsUpdated=" + rowsUpdated);
        }
    }

    /**
     * Save the state for a single node to permanent storage.
     *
     * <strong>Note:</strong> always uses the current bookshelf/style
     *
     * @param leafRowId    between this row (inclusive)
     * @param leafLevel    level
     * @param nextLeafExcl and this row (exclusive)
     */
    private void saveNodesBetween(final long leafRowId,
                                  final long leafLevel,
                                  final long nextLeafExcl) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
            Log.d(TAG, "saveNodesBetween"
                       + "|leafRowId=" + leafRowId
                       + "|leafLevel=" + leafLevel
                       + "|endRowExcl=" + nextLeafExcl);
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
                stmt.bindLong(1, mBookshelf.getId());
                stmt.bindLong(2, mStyle.getId());

                stmt.bindLong(3, leafLevel);

                stmt.bindLong(4, leafRowId);
                stmt.bindLong(5, nextLeafExcl);

                int rowsDeleted = stmt.executeUpdateDelete();

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
                stmt.bindLong(1, mBookshelf.getId());
                stmt.bindLong(2, mStyle.getId());

                stmt.bindLong(3, leafRowId);
                stmt.bindLong(4, nextLeafExcl);

                int rowsInserted = stmt.executeUpdateDelete();

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
     * The state we want a node to become; as used by
     * {@link #setNode(long, DesiredNodeState, boolean, boolean, int)}.
     */
    public enum DesiredNodeState {
        Expand,
        Collapse,
        Toggle
    }

    /**
     * A value class containing details of a single row as used in the list-view.
     */
    public static class ListRowDetails {

        @IntRange(from = 1)
        public final int level;
        public final boolean visible;
        /** The row this object represents. */
        @IntRange(from = 1)
        final int rowId;
        /**
         * Set at creation time, but can be recalculated/set if the list changed.
         * {@code row.listPosition = RowStateDAO#getListPosition(row.rowId); }
         */
        public Integer listPosition;

        ListRowDetails(final int rowId,
                       final boolean visible,
                       @IntRange(from = 1) final int level) {
            this.rowId = rowId;
            this.visible = visible;
            this.level = level;
        }
    }
}
