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
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.NumberListFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.FtsDefinition;
import com.hardbacknutter.nevertoomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.VirtualDomain;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_VISIBLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;

/**
 * Build and populate temporary tables with details of "flattened" books.
 * The generated list is used to display books in a list control and perform operation like
 * 'expand/collapse' on nodes in the list.
 * <p>
 * The Booklist "owns" the temporary database tables.
 * They get deleted when {@link #close()} is called.
 * A Booklist has a 1:1 relation to {@link BooklistCursor} objects.
 * The BooklistCursor holds a reference to the Booklist.
 */
public class Booklist
        implements AutoCloseable {

    /** id values for state preservation property. See {@link ListRebuildMode}. */
    public static final int PREF_REBUILD_SAVED_STATE = 0;
    public static final int PREF_REBUILD_EXPANDED = 1;
    @SuppressWarnings("WeakerAccess")
    public static final int PREF_REBUILD_COLLAPSED = 2;
    @SuppressWarnings("WeakerAccess")
    public static final int PREF_REBUILD_PREFERRED_STATE = 3;
    /** Log tag. */
    public static final String TAG = "Booklist";
    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _AND_ = " AND ";
    private static final String _ORDER_BY_ = " ORDER BY ";
    /**
     * Counter for Booklist ID's. Only increment.
     * Used to create unique table names etc... see {@link #mInstanceId}.
     */
    @NonNull
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    /** DEBUG Instance counter. Increment and decrements to check leaks. */
    @NonNull
    private static final AtomicInteger DEBUG_INSTANCE_COUNTER = new AtomicInteger();


    // not in use for now
    // List of columns for the group-by clause, including COLLATE clauses. Set by build() method.
    //private String mGroupColumnList;

    /** Statement cache name. */
    private static final String STMT_COUNT_BOOKS = "cntBooks";
    /** Statement cache name. */
    private static final String STMT_COUNT_DISTINCT_BOOKS = "cntDistBooks";
    /** Statement cache name. */
    private static final String STMT_COUNT_VISIBLE_ROWS = "cntVisRows";
    /** Statement cache name. */
    private static final String STMT_COUNT_VIS_ROWS_BEFORE = "cntVisRowsBefore";

    /** Database Access. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final SynchronizedDb mSyncedDb;
    /** Collection of statements pre-compiled for this object. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final SqlStatementManager mStmtManager;
    /** Internal ID. Used to create unique names for the temporary tables. */
    private final int mInstanceId;
    /** Collection of 'extra' book level domains requested by caller. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Map<String, VirtualDomain> mBookDomains = new HashMap<>();
    /** the list of Filters. */
    private final Collection<Filter> mFilters = new ArrayList<>();
    /** Style to use while building the list. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final ListStyle mStyle;
    /** Show only books on this bookshelf. */
    @NonNull
    private final Bookshelf mBookshelf;
    @SuppressWarnings("FieldNotUsedInToString")
    @ListRebuildMode
    private int mRebuildState;

    /**
     * The temp table representing the booklist for the current bookshelf/style.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private TableDefinition mListTable;

    /**
     * The navigation table for next/prev moving between books.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private TableDefinition mNavTable;

    /**
     * The temp table holding the state (expand,visibility,...) for all rows in the list-table.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private BooklistNodeDAO mRowStateDAO;

    /** DEBUG: double check on mCloseWasCalled to control {@link #DEBUG_INSTANCE_COUNTER}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private boolean mDebugReferenceDecremented;
    /** DEBUG: Indicates close() has been called. See {@link Closeable#close()}. */
    private boolean mCloseWasCalled;
    /** Total number of books in current list. e.g. a book can be listed under 2 authors. */
    private int mTotalBooks = -1;
    /** Total number of unique books in current list. */
    private int mDistinctBooks = -1;
    /** The current list cursor. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private BooklistCursor mCursor;
    /** {@link #getOffsetCursor}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlGetOffsetCursor;
    /** {@link #getNextBookWithoutCover}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlGetNextBookWithoutCover;
    /** {@link #getCurrentBookIdList}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlGetCurrentBookIdList;
    /** {@link #getNodeByRowId}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlGetNodeByRowId;
    /** {@link #getBookNodes}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlGetBookNodes;
    /** {@link #ensureNodeIsVisible}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String mSqlEnsureNodeIsVisible;

    /**
     * Constructor.
     *
     * @param db           Database Access
     * @param style        Style reference.
     *                     This is the resolved style as used by the passed bookshelf
     * @param bookshelf    the current bookshelf
     * @param rebuildState booklist state to use in next rebuild.
     */
    public Booklist(@NonNull final SynchronizedDb db,
                    @NonNull final ListStyle style,
                    @NonNull final Bookshelf bookshelf,
                    @ListRebuildMode final int rebuildState) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "ENTER|Booklist"
                       + "|style=" + style.getUuid()
                       + "|instances: " + DEBUG_INSTANCE_COUNTER.incrementAndGet(),
                  new Throwable());
        }

        // Allocate ID
        mInstanceId = ID_COUNTER.incrementAndGet();
        mSyncedDb = db;
        mStyle = style;
        mBookshelf = bookshelf;
        mRebuildState = rebuildState;

        mStmtManager = new SqlStatementManager(mSyncedDb, TAG + "|Booklist");

        // Filter on the specified Bookshelf.
        // The filter will only be added if the current style does not contain the Bookshelf group.
        if (!mBookshelf.isAllBooks()
            && !mStyle.getGroups().contains(BooklistGroup.BOOKSHELF)) {
            mFilters.add(c -> '(' + TBL_BOOKSHELF.dot(KEY_PK_ID) + '=' + mBookshelf.getId() + ')');
        }
    }

    /**
     * Allows to override the build state set in the constructor.
     *
     * @param rebuildState booklist state to use in next rebuild.
     */
    public void setRebuildState(@ListRebuildMode final int rebuildState) {
        mRebuildState = rebuildState;
    }

    /**
     * Add a domain to the resulting flattened list based on the details provided.
     *
     * @param vDomain Domain to add
     */
    public void addDomain(@NonNull final VirtualDomain vDomain) {
        if (!mBookDomains.containsKey(vDomain.getName())) {
            mBookDomains.put(vDomain.getName(), vDomain);

        } else {
            // adding a duplicate here is a bug.
            throw new IllegalArgumentException("Duplicate domain=" + vDomain.getName());
        }
    }

    /**
     * Adds the FTS book table for a keyword match.
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param author        Author related keywords to find
     * @param title         Title related keywords to find
     * @param seriesTitle   Series title related keywords to find
     * @param publisherName Publisher name related keywords to find
     * @param keywords      Keywords to find anywhere in book; this includes all above fields
     */
    public void setFilter(@Nullable final String author,
                          @Nullable final String title,
                          @Nullable final String seriesTitle,
                          @Nullable final String publisherName,
                          @Nullable final String keywords) {

        final String query = FtsDefinition.createMatchString(author, title, seriesTitle,
                                                             publisherName, keywords);
        if (!query.isEmpty()) {
            mFilters.add(context ->
                                 '(' + TBL_BOOKS.dot(KEY_PK_ID) + " IN ("
                                 // fetch the ID's only
                                 + SELECT_ + FtsDefinition.KEY_FTS_BOOK_ID
                                 + _FROM_ + FtsDefinition.TBL_FTS_BOOKS.getName()
                                 + _WHERE_ + FtsDefinition.TBL_FTS_BOOKS.getName()
                                 + " MATCH '" + query + "')"
                                 + ')');
        }
    }

    /**
     * Set the filter for only books lend to the named person (exact name).
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param filter the exact name of the person we lend books to.
     */
    public void setFilterOnLoanee(@Nullable final String filter) {
        if (filter != null && !filter.trim().isEmpty()) {
            mFilters.add(context ->
                                 "EXISTS(SELECT NULL FROM " + TBL_BOOK_LOANEE.ref()
                                 + _WHERE_ + TBL_BOOK_LOANEE.dot(KEY_LOANEE)
                                 + "=`" + DAO.encodeString(filter) + '`'
                                 + _AND_ + TBL_BOOK_LOANEE.fkMatch(TBL_BOOKS)
                                 + ')');
        }
    }

    /**
     * The where clause will add a "AND books._id IN (list)".
     * Be careful when combining with other criteria as you might get less than expected
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param filter a list of book ID's.
     */
    public void setFilterOnBookIdList(@Nullable final List<Long> filter) {
        if (filter != null && !filter.isEmpty()) {
            mFilters.add(new NumberListFilter(TBL_BOOKS, KEY_PK_ID, filter));
        }
    }

    /**
     * Clear and build the temporary list of books.
     * Criteria must be set before calling this method with one or more of the setCriteria calls.
     *
     * @param context Current context
     */
    public void build(@NonNull final Context context) {

        // Construct the list table and all needed structures.
        final BooklistBuilder helper = new BooklistBuilder(mInstanceId,
                                                           mStyle, mBookshelf,
                                                           mRebuildState);
        helper.preBuild(context, mBookDomains.values(), mFilters);

        final SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            // create the tables and populate them
            final Pair<TableDefinition, TableDefinition> tables = helper.build(mSyncedDb);
            mListTable = tables.first;
            mNavTable = tables.second;

            mRowStateDAO = new BooklistNodeDAO(mSyncedDb, mStmtManager, mListTable,
                                               mStyle, mBookshelf);

            switch (mRebuildState) {
                case PREF_REBUILD_SAVED_STATE:
                    // all rows will be collapsed/hidden; restore the saved state
                    mRowStateDAO.restoreSavedState();
                    break;

                case PREF_REBUILD_PREFERRED_STATE:
                    // all rows will be collapsed/hidden; now adjust as required.
                    mRowStateDAO.setAllNodes(mStyle.getTopLevel(), false);
                    break;

                case PREF_REBUILD_EXPANDED:
                case PREF_REBUILD_COLLAPSED:
                    // handled during table creation
                    break;

                default:
                    throw new IllegalArgumentException(String.valueOf(mRebuildState));
            }

            mSyncedDb.setTransactionSuccessful();

        } finally {
            mSyncedDb.endTransaction(txLock);
        }
    }

    /**
     * Count the number of <strong>distinct</strong> book records in the list.
     *
     * @return count
     */
    public int countDistinctBooks() {
        if (mDistinctBooks == -1) {
            final SynchronizedStatement stmt = mStmtManager.get(STMT_COUNT_DISTINCT_BOOKS, () ->
                    "SELECT COUNT(DISTINCT " + KEY_FK_BOOK + ")"
                    + _FROM_ + mListTable.getName()
                    + _WHERE_ + KEY_BL_NODE_GROUP + "=?");

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, BooklistGroup.BOOK);
                mDistinctBooks = (int) stmt.simpleQueryForLongOrZero();
            }
        }
        return mDistinctBooks;
    }

    /**
     * Count the total number of book records in the list.
     *
     * @return count
     */
    public int countBooks() {
        if (mTotalBooks == -1) {
            final SynchronizedStatement stmt = mStmtManager.get(STMT_COUNT_BOOKS, () ->
                    "SELECT COUNT(*)"
                    + _FROM_ + mListTable.getName()
                    + _WHERE_ + KEY_BL_NODE_GROUP + "=?");

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, BooklistGroup.BOOK);
                mTotalBooks = (int) stmt.simpleQueryForLongOrZero();
            }
        }
        return mTotalBooks;
    }

    /**
     * Count the number of visible rows.
     *
     * @return count
     */
    int countVisibleRows() {
        final SynchronizedStatement stmt = mStmtManager.get(STMT_COUNT_VISIBLE_ROWS, () ->
                "SELECT COUNT(*)"
                + _FROM_ + mListTable.getName()
                + _WHERE_ + KEY_BL_NODE_VISIBLE + "=1");

        return (int) stmt.simpleQueryForLongOrZero();
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

        names[domains.size()] = KEY_BL_LIST_VIEW_NODE_ROW_ID;
        return names;
    }

    @NonNull
    public String getNavigationTableName() {
        return mNavTable.getName();
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
                    SELECT_ + mListTable.getDomains().stream()
                                        .map(domain -> mListTable.dot(domain.getName()))
                                        .collect(Collectors.joining(","))
                    + ',' + (mListTable.dot(KEY_PK_ID) + " AS " + KEY_BL_LIST_VIEW_NODE_ROW_ID)
                    + _FROM_ + mListTable.ref()
                    + _WHERE_ + mListTable.dot(KEY_BL_NODE_VISIBLE) + "=1"
                    + _ORDER_BY_ + mListTable.dot(KEY_PK_ID)
                    + " LIMIT ? OFFSET ?";
        }

        return mSyncedDb.rawQuery(mSqlGetOffsetCursor, new String[]{
                String.valueOf(pageSize),
                String.valueOf(offset)});
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
     * Find the nodes that show the given book.
     * Either returns a list of <strong>already visible</strong> nodes,
     * or all nodes (after making them visible).
     *
     * @param bookId the book to find
     *
     * @return the node(s), or {@code null} if none
     */
    @Nullable
    public ArrayList<BooklistNode> getBookNodes(@IntRange(from = 0) final long bookId) {
        // sanity check
        if (bookId == 0) {
            return null;
        }

        if (mSqlGetBookNodes == null) {
            mSqlGetBookNodes =
                    SELECT_ + BooklistNode.getColumns(mListTable)
                    + _FROM_ + mListTable.ref()
                    + _WHERE_ + mListTable.dot(KEY_FK_BOOK) + "=?";
        }

        // get all positions the book is on
        final ArrayList<BooklistNode> nodeList = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(mSqlGetBookNodes, new String[]{
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
        final ArrayList<BooklistNode> visibleNodes =
                nodeList.stream()
                        .filter(BooklistNode::isVisible)
                        .collect(Collectors.toCollection(ArrayList::new));

        // If we have nodes already visible, return those
        if (!visibleNodes.isEmpty()) {
            return visibleNodes;

        } else {
            // Make them all visible
            //noinspection SimplifyStreamApiCallChains
            nodeList.stream().forEach(this::ensureNodeIsVisible);

            // Recalculate all positions
            //noinspection SimplifyStreamApiCallChains
            nodeList.stream().forEach(this::findAndSetListPosition);

            return nodeList;
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
                    SELECT_ + KEY_FK_BOOK
                    + _FROM_ + mListTable.getName()
                    + _WHERE_ + KEY_BL_NODE_GROUP + "=?"
                    + _ORDER_BY_ + KEY_FK_BOOK;
        }

        try (Cursor cursor = mSyncedDb.rawQuery(mSqlGetCurrentBookIdList, new String[]{
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
    public BooklistNode getNextBookWithoutCover(@NonNull final Context context,
                                                final long rowId) {

        if (mSqlGetNextBookWithoutCover == null) {
            mSqlGetNextBookWithoutCover =
                    SELECT_ + BooklistNode.getColumns(mListTable)
                    + ',' + mListTable.dot(DBDefinitions.KEY_BOOK_UUID)
                    + _FROM_ + mListTable.ref()
                    + _WHERE_ + mListTable.dot(KEY_BL_NODE_GROUP) + "=?"
                    + _AND_ + mListTable.dot(KEY_PK_ID) + ">?";
        }

        try (Cursor cursor = mSyncedDb.rawQuery(mSqlGetNextBookWithoutCover, new String[]{
                String.valueOf(BooklistGroup.BOOK),
                String.valueOf(rowId)})) {

            final BooklistNode node = new BooklistNode();
            while (cursor.moveToNext()) {
                node.from(cursor);
                final String uuid = cursor.getString(BooklistNode.COLS);
                final File file = Book.getUuidCoverFile(context, uuid, 0);
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
     * Expand or collapse <strong>all</strong> nodes.
     * The internal cursor will be null'd but it's still the clients responsibility
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
                                 + _WHERE_ + mListTable.dot(KEY_PK_ID) + "=?";
        }

        try (Cursor cursor = mSyncedDb.rawQuery(mSqlGetNodeByRowId, new String[]{
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
     * Update the passed node with the actual list position,
     * <strong>taking into account invisible rows</strong>.
     *
     * @param node to set
     */
    private void findAndSetListPosition(@NonNull final BooklistNode node) {
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
    private void ensureNodeIsVisible(@NonNull final BooklistNode node) {

        if (mSqlEnsureNodeIsVisible == null) {
            mSqlEnsureNodeIsVisible =
                    SELECT_ + KEY_PK_ID + _FROM_ + mListTable.getName()
                    // follow the node hierarchy
                    + _WHERE_ + KEY_BL_NODE_KEY + " LIKE ?"
                    // we'll loop for all levels
                    + _AND_ + KEY_BL_NODE_LEVEL + "=?";
        }

        node.setExpanded(true);
        node.setVisible(true);

        String nodeKey = node.getKey();

        // levels are 1.. based; start with lowest level above books, working up to root.
        // Pair: rowId/Level
        final Deque<Pair<Long, Integer>> nodes = new ArrayDeque<>();
        for (int level = node.getLevel() - 1; level >= 1; level--) {
            try (Cursor cursor = mSyncedDb.rawQuery(mSqlEnsureNodeIsVisible, new String[]{
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

        mStmtManager.close();

        if (mCursor != null) {
            mCursor.close();
        }
        if (mListTable != null) {
            mSyncedDb.drop(mListTable.getName());
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            if (!mDebugReferenceDecremented) {
                final int inst = DEBUG_INSTANCE_COUNTER.decrementAndGet();
                // Only de-reference once! Paranoia ... close() might be called twice?
                Log.d(TAG, "close|instances left=" + inst);
            }
            mDebugReferenceDecremented = true;
        }
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
               + ", mFilters=" + mFilters
               + ", mBookshelf=" + mBookshelf
               + '}';
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

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PREF_REBUILD_SAVED_STATE,
             PREF_REBUILD_EXPANDED,
             PREF_REBUILD_COLLAPSED,
             PREF_REBUILD_PREFERRED_STATE})
    public @interface ListRebuildMode {

    }

}
