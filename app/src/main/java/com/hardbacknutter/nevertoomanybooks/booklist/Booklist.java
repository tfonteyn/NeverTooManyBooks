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
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class Booklist
        implements AutoCloseable {

    /**
     * DEBUG Instance counter. Increment and decrements to check leaks.
     * This is probably no longer needed.
     */
    @NonNull
    static final AtomicInteger DEBUG_INSTANCE_COUNTER = new AtomicInteger();

    private static final String SELECT_COUNT_FROM_ = "SELECT COUNT(*) FROM ";
    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _AS_ = " AS ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _AND_ = " AND ";
    private static final String _ORDER_BY_ = " ORDER BY ";

    /** Log tag. */
    private static final String TAG = "Booklist";

    /** Database Access. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final SynchronizedDb mDb;

    /**
     * Internal ID. Used to create unique names for the temporary tables.
     * Used in this class for logging and {@link #equals}.
     */
    private final int mInstanceId;

    /**
     * The temp table representing the booklist for the current bookshelf/style.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private final TableDefinition mListTable;

    /**
     * The navigation table for next/prev moving between books.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private final TableDefinition mNavTable;

    /**
     * The temp table holding the state (expand,visibility,...) for all rows in the list-table.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private final BooklistNodeDao mRowStateDAO;

    /** Total number of books in current list. e.g. a book can be listed under 2 authors. */
    private int mTotalBooks = -1;

    /** Total number of unique books in current list. */
    private int mDistinctBooks = -1;

    /** {@link #getBookNodes}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlGetBookNodes;

    /** {@link #ensureNodeIsVisible}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlEnsureNodeIsVisible;

    /**
     * DEBUG: double check on mCloseWasCalled to control
     * {@link Booklist#DEBUG_INSTANCE_COUNTER}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private boolean mDebugReferenceDecremented;

    /** DEBUG: Indicates close() has been called. See {@link Closeable#close()}. */
    private boolean mCloseWasCalled;

    /** The current list cursor. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private BooklistCursor mCursor;

    /** {@link #getNodeByRowId}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlGetNodeByRowId;

    /** {@link #getCurrentBookIdList}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlGetCurrentBookIdList;

    /** {@link #getNextBookWithoutCover}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlGetNextBookWithoutCover;

    /** {@link #getOffsetCursor}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlGetOffsetCursor;

    Booklist(final int instanceId,
             @NonNull final SynchronizedDb db,
             @NonNull final TableDefinition listTable,
             @NonNull final TableDefinition navTable,
             @NonNull final BooklistNodeDao rowStateDAO) {

        mInstanceId = instanceId;
        mDb = db;
        mListTable = listTable;
        mNavTable = navTable;
        mRowStateDAO = rowStateDAO;
    }

    @NonNull
    public String getNavigationTableName() {
        return mNavTable.getName();
    }

    /**
     * Count the total number of book records in the list.
     *
     * @return count
     */
    public int countBooks() {
        if (mTotalBooks == -1) {
            try (SynchronizedStatement stmt = mDb.compileStatement(
                    SELECT_COUNT_FROM_ + mListTable.getName()
                    + _WHERE_ + DBKey.KEY_BL_NODE_GROUP + "=?")) {
                stmt.bindLong(1, BooklistGroup.BOOK);
                mTotalBooks = (int) stmt.simpleQueryForLongOrZero();
            }
        }
        return mTotalBooks;
    }

    /**
     * Count the number of <strong>distinct</strong> book records in the list.
     *
     * @return count
     */
    public int countDistinctBooks() {
        if (mDistinctBooks == -1) {
            try (SynchronizedStatement stmt = mDb.compileStatement(
                    "SELECT COUNT(DISTINCT " + DBKey.FK_BOOK + ")"
                    + _FROM_ + mListTable.getName()
                    + _WHERE_ + DBKey.KEY_BL_NODE_GROUP + "=?")) {

                stmt.bindLong(1, BooklistGroup.BOOK);
                mDistinctBooks = (int) stmt.simpleQueryForLongOrZero();
            }
        }
        return mDistinctBooks;
    }

    /**
     * Count the number of visible rows.
     *
     * @return count
     */
    int countVisibleRows() {
        try (SynchronizedStatement stmt = mDb.compileStatement(
                SELECT_COUNT_FROM_ + mListTable.getName()
                + _WHERE_ + DBKey.KEY_BL_NODE_VISIBLE + "=1")) {
            return (int) stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get the list cursor.
     * <p>
     * Note this is a {@link BooklistCursor}
     *
     * @return cursor
     */
    @NonNull
    public BooklistCursor getNewListCursor() {
        if (mCursor != null) {
            mCursor.close();
        }

        mCursor = new BooklistCursor(this);
        return mCursor;
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

        if (mSqlGetOffsetCursor == null) {
            mSqlGetOffsetCursor =
                    SELECT_ + mListTable.getDomains()
                                        .stream()
                                        .map(domain -> mListTable.dot(domain.getName()))
                                        .collect(Collectors.joining(","))
                    + ',' + (mListTable.dot(DBKey.PK_ID)
                             + _AS_ + DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID)
                    + _FROM_ + mListTable.ref()
                    + _WHERE_ + mListTable.dot(DBKey.KEY_BL_NODE_VISIBLE) + "=1"
                    + _ORDER_BY_ + mListTable.dot(DBKey.PK_ID)
                    + " LIMIT ? OFFSET ?";
        }

        return mDb.rawQuery(mSqlGetOffsetCursor, new String[]{
                String.valueOf(pageSize),
                String.valueOf(offset)});
    }

    /**
     * Get the list of column names that will be in the list for cursor implementations.
     *
     * @return array with column names
     */
    @NonNull
    String[] getListColumnNames() {
        // Get the domains
        final List<Domain> domains = mListTable.getDomains();
        // Make the array +1 so we can add KEY_BL_LIST_VIEW_ROW_ID
        final String[] names = new String[domains.size() + 1];
        for (int i = 0; i < domains.size(); i++) {
            names[i] = domains.get(i).getName();
        }

        names[domains.size()] = DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID;
        return names;
    }

    /**
     * Expand or collapse <strong>all</strong> nodes.
     * The internal cursor will be set to {@code null} but it's still the clients responsibility
     * to refresh their adapter.
     *
     * @param topLevel the desired top-level which must be kept visible
     * @param expand   the state to apply to levels 'below' the topLevel (level > topLevel),
     */
    public void setAllNodes(@IntRange(from = 1) final int topLevel,
                            final boolean expand) {
        mRowStateDAO.setAllNodes(topLevel, expand);
        mCursor = null;
    }

    /**
     * Toggle (expand/collapse) the given node.
     *
     * @param rowId              list-view row id of the node in the list
     * @param nextState          the state to set the node to
     * @param relativeChildLevel up to and including this (relative to the node) child level;
     *
     * @return the node
     */
    @NonNull
    public BooklistNode setNode(final long rowId,
                                @BooklistNode.NextState final int nextState,
                                final int relativeChildLevel) {
        final BooklistNode node = getNodeByRowId(rowId);
        node.setNextState(nextState);
        mRowStateDAO.setNode(node.getRowId(), node.getLevel(),
                             node.isExpanded(), relativeChildLevel);
        findAndSetListPosition(node);
        return node;
    }

    boolean isNodeExpanded(final long rowId) {
        return getNodeByRowId(rowId).isExpanded();
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
    private BooklistNode getNodeByRowId(final long rowId) {
        if (mSqlGetNodeByRowId == null) {
            mSqlGetNodeByRowId = SELECT_ + BooklistNode.getColumns(mListTable)
                                 + _FROM_ + mListTable.ref()
                                 + _WHERE_ + mListTable.dot(DBKey.PK_ID) + "=?";
        }

        try (Cursor cursor = mDb.rawQuery(mSqlGetNodeByRowId, new String[]{
                String.valueOf(rowId)})) {

            if (cursor.moveToFirst()) {
                final BooklistNode node = new BooklistNode(cursor);
                findAndSetListPosition(node);
                return node;
            } else {
                throw new IllegalArgumentException("rowId not found: " + rowId);
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
    public List<BooklistNode> getBookNodes(@IntRange(from = 0) final long bookId) {
        // sanity check
        if (bookId == 0) {
            return null;
        }

        if (mSqlGetBookNodes == null) {
            mSqlGetBookNodes =
                    SELECT_ + BooklistNode.getColumns(mListTable)
                    + _FROM_ + mListTable.ref()
                    + _WHERE_ + mListTable.dot(DBKey.FK_BOOK) + "=?";
        }

        // get all positions the book is on
        final List<BooklistNode> nodeList = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(mSqlGetBookNodes, new String[]{
                String.valueOf(bookId)})) {

            while (cursor.moveToNext()) {
                final BooklistNode node = new BooklistNode(cursor);
                findAndSetListPosition(node);
                nodeList.add(node);
            }
        }

        if (nodeList.isEmpty()) {
            // the book is not present
            return null;
        }

        // We have nodes; first get the ones that are currently visible
        final List<BooklistNode> visibleNodes =
                nodeList.stream()
                        .filter(BooklistNode::isVisible)
                        .collect(Collectors.toList());

        // If we have nodes already visible, return those
        if (!visibleNodes.isEmpty()) {
            return visibleNodes;

        } else {
            // Make them all visible
            nodeList.forEach(this::ensureNodeIsVisible);

            // Recalculate all positions
            nodeList.forEach(this::findAndSetListPosition);

            return nodeList;
        }
    }

    /**
     * Update the passed node with the actual list position,
     * <strong>taking into account invisible rows</strong>.
     *
     * @param node to set
     */
    private void findAndSetListPosition(@NonNull final BooklistNode node) {
        // We need to count the visible rows between the start of the list,
        // and the given row, to determine the ACTUAL row we want.
        final int count;
        try (SynchronizedStatement stmt = mDb.compileStatement(
                SELECT_COUNT_FROM_ + mListTable.getName()
                + _WHERE_ + DBKey.KEY_BL_NODE_VISIBLE + "=1" + _AND_ + DBKey.PK_ID + "<?")) {

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

        node.setAdapterPosition(pos);
    }

    /**
     * Ensure al nodes up to the root node for the given (book) node (inclusive) are visible.
     *
     * @param node we want to become visible
     */
    private void ensureNodeIsVisible(@NonNull final BooklistNode node) {

        if (mSqlEnsureNodeIsVisible == null) {
            mSqlEnsureNodeIsVisible =
                    SELECT_ + DBKey.PK_ID + _FROM_ + mListTable.getName()
                    // follow the node hierarchy
                    + _WHERE_ + DBKey.KEY_BL_NODE_KEY + " LIKE ?"
                    // we'll loop for all levels
                    + _AND_ + DBKey.KEY_BL_NODE_LEVEL + "=?";
        }

        node.setExpanded(true);
        node.setVisible(true);

        String nodeKey = node.getKey();

        // levels are 1.. based; start with lowest level above books, working up to root.
        // Pair: rowId/Level
        final Deque<Pair<Long, Integer>> nodes = new ArrayDeque<>();
        for (int level = node.getLevel() - 1; level >= 1; level--) {
            try (Cursor cursor = mDb.rawQuery(mSqlEnsureNodeIsVisible, new String[]{
                    nodeKey + "%",
                    String.valueOf(level)})) {

                while (cursor.moveToNext()) {
                    final long rowId = cursor.getLong(0);
                    nodes.push(new Pair<>(rowId, level));
                }
            }
            nodeKey = nodeKey.substring(0, nodeKey.lastIndexOf('/'));
        }

        // Now process the collected nodes from the root downwards.
        for (final Pair<Long, Integer> n : nodes) {
            mRowStateDAO.setNode(n.first, n.second,
                                 // Expand (and make visible) the given node
                                 true,
                                 // do this for only ONE level
                                 1);
        }
    }

    /**
     * Get the full list of all Books (their id only) which are currently in the list table.
     *
     * @return list of book ID's
     */
    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        if (mSqlGetCurrentBookIdList == null) {
            mSqlGetCurrentBookIdList =
                    SELECT_ + DBKey.FK_BOOK
                    + _FROM_ + mListTable.getName()
                    + _WHERE_ + DBKey.KEY_BL_NODE_GROUP + "=?"
                    + _ORDER_BY_ + DBKey.FK_BOOK;
        }

        try (Cursor cursor = mDb.rawQuery(mSqlGetCurrentBookIdList, new String[]{
                String.valueOf(BooklistGroup.BOOK)})) {

            if (cursor.moveToFirst()) {
                final ArrayList<Long> rows = new ArrayList<>(cursor.getCount());
                do {
                    final long id = cursor.getInt(0);
                    rows.add(id);
                } while (cursor.moveToNext());
                return rows;
            } else {
                return new ArrayList<>();
            }
        }
    }

    @Nullable
    public BooklistNode getNextBookWithoutCover(final long rowId) {

        if (mSqlGetNextBookWithoutCover == null) {
            mSqlGetNextBookWithoutCover =
                    SELECT_ + BooklistNode.getColumns(mListTable)
                    + ',' + mListTable.dot(DBKey.KEY_BOOK_UUID)
                    + _FROM_ + mListTable.ref()
                    + _WHERE_ + mListTable.dot(DBKey.KEY_BL_NODE_GROUP) + "=?"
                    + _AND_ + mListTable.dot(DBKey.PK_ID) + ">?";
        }

        try (Cursor cursor = mDb.rawQuery(mSqlGetNextBookWithoutCover, new String[]{
                String.valueOf(BooklistGroup.BOOK),
                String.valueOf(rowId)})) {

            final BooklistNode node = new BooklistNode();
            while (cursor.moveToNext()) {
                node.from(cursor);
                final String uuid = cursor.getString(BooklistNode.COLS);
                final File file = Book.getPersistedCoverFile(uuid, 0);
                if (file == null || !file.exists()) {
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
     * Cleanup.
     * <p>
     * RuntimeException are caught and ignored.
     * <p>
     * We cleanup temporary tables to free up no longer needed resources NOW.
     */
    @Override
    public void close() {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "|close|mInstanceId=" + mInstanceId);
        }
        mCloseWasCalled = true;

        if (mCursor != null) {
            mCursor.close();
        }
        mDb.drop(mListTable.getName());

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            if (!mDebugReferenceDecremented) {
                final int inst = DEBUG_INSTANCE_COUNTER.decrementAndGet();
                // Only de-reference once! Paranoia ... close() might be called twice?
                Log.d(TAG, "close|instances left=" + inst);
            }
            mDebugReferenceDecremented = true;
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
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "finalize|" + mInstanceId);
            }
            close();
        }
        super.finalize();
    }

    @Override
    public int hashCode() {
        return Objects.hash(mInstanceId);
    }

    /**
     * Simple equality: two builders are equal if their ID's are the same.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Booklist that = (Booklist) obj;
        return mInstanceId == that.mInstanceId;
    }

    @Override
    @NonNull
    public String toString() {
        return "Booklist{"
               + "mInstanceId=" + mInstanceId
               + ", mCloseWasCalled=" + mCloseWasCalled
               + ", mTotalBooks=" + mTotalBooks
               + ", mDistinctBooks=" + mDistinctBooks
               + '}';
    }

}
