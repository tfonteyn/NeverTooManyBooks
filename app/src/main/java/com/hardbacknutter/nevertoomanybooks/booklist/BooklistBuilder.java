/*
 * @Copyright 2019 HardBackNutter
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
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.ListOfValuesFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.WildcardFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntString;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.Joiner;
import com.hardbacknutter.nevertoomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.UnexpectedValueException;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_ABSOLUTE_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_BOOK_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_EXPANDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_KIND;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_SELECTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_VISIBLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_PRIMARY_SERIES_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_ROOT_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_SERIES_NUM_FLOAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_FIRST_PUB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK_BL_ROW_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE_AS_BOOLEAN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_AUTHOR_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_SERIES_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TMP_TBL_BOOK_LIST;

/**
 * Class used to build and populate temporary tables with details of a flattened book
 * list used to display books in a list control and perform operation like
 * 'expand/collapse' on pseudo nodes in the list.
 *
 * <strong>IMPORTANT:</strong> {@link #rebuild()} was giving to many problems
 * (remember it did not work in BookCatalogue 5.2.2 except in classic mode)
 * The code for it has not been removed here yet, might get revisited later.
 * (particular issue: the triggers)
 * <p>
 */
public class BooklistBuilder
        implements AutoCloseable {

    /** id values for state preservation property. See {@link ListRebuildMode}. */
    public static final int PREF_LIST_REBUILD_SAVED_STATE = 0;
    public static final int PREF_LIST_REBUILD_ALWAYS_EXPANDED = 1;
    public static final int PREF_LIST_REBUILD_ALWAYS_COLLAPSED = 2;
    @SuppressWarnings("WeakerAccess")
    public static final int PREF_LIST_REBUILD_PREFERRED_STATE = 3;
    private static final String TAG = "BooklistBuilder";
    /** Counter for BooklistBuilder ID's. Used to create unique table names etc... */
    @NonNull
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    /** DEBUG Instance counter. */
    @NonNull
    private static final AtomicInteger DEBUG_INSTANCE_COUNTER = new AtomicInteger();

    /** Statement names for caching. */
    private static final String STMT_DELETE_ALL_NODES = "DelAllNodes";
    private static final String STMT_DELETE_SOME_NODES = "DelSomeNodes";

    private static final String STMT_SAVE_ALL_NODES = "SaveNodeSetting";
    private static final String STMT_SAVE_SOME_NODES = "SaveAllNodeSettings";

    private static final String STMT_BUILD_ROW_STATE_TABLE = "BuildRowStateTable";

    private static final String STMT_BL_BOOK_COUNT = "bl:bookCount";
    private static final String STMT_BL_DISTINCT_BOOK_COUNT = "bl:distinctBookCount";


    /** Divider nano seconds to milli seconds. */
    private static final int TO_MILLIS = 1_000_000;

    /**
     * The base DELETE SQL for {@link #preserveAllNodes()} / {@link #preserveNodes}.
     */
    private static final String mPreserveNodesBaseDeleteSql =
            "DELETE FROM " + TBL_BOOK_LIST_NODE_STATE + " WHERE " + DOM_FK_BOOKSHELF + "=?";

    // not in use for now
    // List of columns for the group-by clause, including COLLATE clauses. Set by build() method.
    //private String mGroupColumnList;
    /**
     * Collection of statements created by this Builder.
     * Private and single thread usage, hence no need to synchronize the statements.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final SqlStatementManager mStatementManager;
    /** Database Access. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final DAO mDb;
    /** The underlying database. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final SynchronizedDb mSyncedDb;
    /** Internal ID. Used to create unique names for the temporary tables. */
    private final int mInstanceId;
    /** Collection of 'extra' domains requested by caller. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Map<String, ExtraDomainDetails> mExtraDomains = new HashMap<>();
    /** Style to use while building the list. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final BooklistStyle mStyle;
    /**
     * Instance-based cursor factory so that the builder can be associated with the cursor
     * and the rowView. We could probably send less context, but in the first instance this
     * guarantees we get all the info we need downstream.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private final CursorFactory mBooklistCursorFactory =
            (db, masterQuery, editTable, query) ->
                    new BooklistCursor(masterQuery, editTable, query,
                                       DAO.getSynchronizer(), BooklistBuilder.this);
    /** the list of Filters. */
    private final ArrayList<Filter> mFilters = new ArrayList<>();
    /**
     * Collection of statements used to {@link #rebuild()}.
     * <p>
     * Needs to be re-usable WITHOUT parameter binding.
     */
    private final ArrayList<SynchronizedStatement> mRebuildStmts = new ArrayList<>();
    /**
     * The temp table representing the booklist for the current bookshelf/style.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private TableDefinition mListTable;
    @SuppressWarnings("FieldNotUsedInToString")
    private TableDefinition mFlatNavigationTable;

    /**
     * The temp table holding the state (expand,visibility,...) for all rows in the list-table.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private
    RowStateTable mRowStateTable;


    /** Table used by the triggers to store the snapshot of the most recent/current row headings. */
    @SuppressWarnings("FieldNotUsedInToString")
    private TableDefinition mTriggerHelperTable;
    /** used in debug. */
    @SuppressWarnings("FieldNotUsedInToString")
    private boolean mDebugReferenceDecremented;
    /** Show only books on this bookshelf. */
    private Bookshelf mBookshelf;
    /** DEBUG: Indicates close() has been called. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     *
     * @param style Booklist style to use
     */
    public BooklistBuilder(@NonNull final BooklistStyle style) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "ENTER|BooklistBuilder"
                       + "|instances: " + DEBUG_INSTANCE_COUNTER.incrementAndGet());
        }
        // Allocate ID
        mInstanceId = ID_COUNTER.incrementAndGet();

        // Save the requested style
        mStyle = style;

        // Get the database and create a statements collection
        mDb = new DAO();
        mSyncedDb = mDb.getUnderlyingDatabase();
        mStatementManager = new SqlStatementManager(mSyncedDb);
    }

    /**
     * Get the current preferred rebuild state for the list.
     *
     * @return ListRebuildMode
     */
    @ListRebuildMode
    public static int getPreferredListRebuildState() {
        return PIntString.getListPreference(Prefs.pk_bob_levels_rebuild_state,
                                            PREF_LIST_REBUILD_SAVED_STATE);
    }

    /**
     * Create a flattened table (a snapshot!) of ordered book ID's based on the underlying list.
     *
     * @return the name of the created table.
     */
    @NonNull
    public String createFlattenedBooklist() {

        mFlatNavigationTable = FlattenedBooklist.createTable(mSyncedDb, mInstanceId,
                                                             mListTable, mRowStateTable);

        return mFlatNavigationTable.getName();
    }

    /**
     * Add a domain to the resulting flattened list based on the details provided.
     *
     * @param domain           Domain to add
     * @param sourceExpression Expression to use in deriving domain value
     * @param isSorted         Indicates if it should be added to the sort key
     *
     * @return this for chaining
     */
    @NonNull
    @SuppressWarnings("UnusedReturnValue")
    public BooklistBuilder addExtraDomain(@NonNull final DomainDefinition domain,
                                          @Nullable final String sourceExpression,
                                          final boolean isSorted) {
        if (!mExtraDomains.containsKey(domain.getName())) {
            @BuildHelper.Flags
            int flags = 0;
            if (isSorted) {
                flags |= BuildHelper.FLAG_SORTED;
            }
            mExtraDomains.put(domain.getName(),
                              new ExtraDomainDetails(domain, sourceExpression, flags));

        } else {
            // adding a duplicate here is a bug.
            throw new IllegalArgumentException("Domain already added: `" + domain.getName() + '`');
        }

        return this;
    }

    /**
     * Adds the FTS book table for a keyword match.
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param author      Author related keywords to find
     * @param title       Title related keywords to find
     * @param seriesTitle Series title related keywords to find
     * @param keywords    Keywords to find anywhere in book; this includes titles and authors
     */
    public void setFilter(@Nullable final String author,
                          @Nullable final String title,
                          @Nullable final String seriesTitle,
                          @Nullable final String keywords) {

        // need at least one.
        if ((author != null && !author.trim().isEmpty())
            || (title != null && !title.trim().isEmpty())
            || (seriesTitle != null && !seriesTitle.trim().isEmpty())
            || (keywords != null && !keywords.trim().isEmpty())) {

            mFilters.add(() -> '(' + TBL_BOOKS.dot(DOM_PK_ID)
                               + " IN ("
                               + mDb.getFtsSearchSQL(author, title, seriesTitle, keywords)
                               + ')'
                               + ')');
        }
    }

    /**
     * Set the filter for only books loaned to the named person (exact name).
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param filter the exact name of the person we loaned books to.
     */
    public void setFilterOnLoanedToPerson(@Nullable final String filter) {
        if (filter != null && !filter.trim().isEmpty()) {
            mFilters.add(() -> "EXISTS(SELECT NULL FROM " + TBL_BOOK_LOANEE.ref()
                               + " WHERE "
                               + TBL_BOOK_LOANEE.dot(DOM_LOANEE)
                               + "='" + DAO.encodeString(filter) + '\''
                               + " AND " + TBL_BOOK_LOANEE.fkMatch(TBL_BOOKS) + ')');
        }
    }

    /**
     * Set the filter for only books in named Series with added wildcards.
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param filter the Series to limit the search for.
     */
    public void setFilterOnSeriesName(@Nullable final String filter) {
        if (filter != null && !filter.trim().isEmpty()) {
            mFilters.add(new WildcardFilter(TBL_SERIES, DOM_SERIES_TITLE, filter));
        }
    }

    /**
     * Set the filter to return only books on this bookshelf.
     * If set to 0, return books from all shelves.
     *
     * @param bookshelf only books on this shelf.
     */
    public void setBookshelf(@NonNull final Bookshelf bookshelf) {
        mBookshelf = bookshelf;
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
            mFilters.add(new ListOfValuesFilter<>(TBL_BOOKS, DOM_PK_ID, filter));
        }
    }

    /**
     * Drop and recreate all the data based on previous criteria.
     */
    public void rebuild() {
        // Drop the table in case there is an orphaned instance.
        mListTable.drop(mSyncedDb);
        //IMPORTANT: withConstraints MUST BE false
        mListTable.create(mSyncedDb, false);

        mRowStateTable.drop(mSyncedDb);
        // indices are created after the table is populated.
        mRowStateTable.create(mSyncedDb, true, false);

        for (SynchronizedStatement stmt : mRebuildStmts) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Log.d(TAG, "rebuild|mRebuildStmts" + stmt);
            }
            stmt.execute();
        }
    }

    /**
     * Clear and build the temporary list of books.
     * Criteria must be set before calling this method with one or more of the setCriteria calls.
     *
     * @param listState        State to display
     * @param currentPosBookId The book id around which the list is positioned.
     *                         Will be remembered so we can scroll back to it.
     */
    public void build(@BooklistBuilder.ListRebuildMode final int listState,
                      final long currentPosBookId) {
        final long t00 = System.nanoTime();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "ENTER|build|mInstanceId=" + mInstanceId);
        }

        // Setup the tables (but don't create them yet)
        mListTable = TMP_TBL_BOOK_LIST.clone();
        mListTable.setName(mListTable.getName() + mInstanceId);

        mRowStateTable = new RowStateTable(mSyncedDb, mStatementManager, mInstanceId);

        // link the two tables via the actual row ID
        // We use this for joins, but it's not enforced with constraints.
        mRowStateTable.addReference(mListTable, DOM_FK_BOOK_BL_ROW_ID);

        // Will be created on first usage.
        mFlatNavigationTable = null;

        mTriggerHelperTable = new TableDefinition(mListTable + "_th")
                .setAlias("tht");
        // Allow debug mode to use standard tables so we can export and inspect the content.
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_USES_STANDARD_TABLES) {
            mTriggerHelperTable.setType(TableDefinition.TableType.Standard);
        } else {
            mTriggerHelperTable.setType(TableDefinition.TableType.Temporary);
        }

        // Start building the table domains
        final BuildHelper helper = new BuildHelper(mListTable, mStyle);

        // Will use default increment
        mListTable.addDomain(DOM_PK_ID);
        // Will use expression based on first group (rowKindPrefix/rowValue); determined later
        mListTable.addDomain(DOM_BL_ROOT_KEY);

        // not sorted
        helper.addDomain(DOM_BL_NODE_LEVEL, String.valueOf(mStyle.groupCount() + 1),
                         BuildHelper.FLAG_NONE);
        //sorted
        helper.addDomain(DOM_BL_NODE_LEVEL, null, BuildHelper.FLAG_SORTED);

        helper.addDomain(DOM_BL_NODE_KIND, String.valueOf(BooklistGroup.RowKind.BOOK),
                         BuildHelper.FLAG_NONE);

        helper.addDomain(DOM_FK_BOOK, TBL_BOOKS.dot(DOM_PK_ID), BuildHelper.FLAG_NONE);
        helper.addDomain(DOM_BL_BOOK_COUNT, "1", BuildHelper.FLAG_NONE);

        // We want the UUID for the book so we can get thumbnails
        helper.addDomain(DOM_BOOK_UUID, TBL_BOOKS.dot(DOM_BOOK_UUID), BuildHelper.FLAG_NONE);

        // If we have a book id to remember, then add the DOM_BL_NODE_SELECTED field,
        // and setup the expression.
        if (currentPosBookId != 0) {
            helper.addDomain(DOM_BL_NODE_SELECTED,
                             TBL_BOOKS.dot(DOM_PK_ID) + '=' + currentPosBookId,
                             BuildHelper.FLAG_NONE);
        }

        for (BooklistGroup group : mStyle.getGroups()) {
            helper.addGroup(group);

            // Copy the current groups to this level item; this effectively accumulates
            // 'GROUP BY' domains down each level so that the top has fewest groups and
            // the bottom level has groups for all levels.
            group.setDomains(helper.getDomainsForCurrentGroup());
        }

        // After adding the groups, we now know if we have a Bookshelf group.
        // If we want a specific shelf, we''ll need to filter on it.
        if (!mBookshelf.isAllBooks()) {
            if (helper.hasBookshelfGroup()) {
                // we have the group, and we want a specific shelf, just add an 'exists' filter.
                mFilters.add(() -> "EXISTS(SELECT NULL FROM "
                                   + TBL_BOOK_BOOKSHELF.ref() + TBL_BOOK_BOOKSHELF.join(TBL_BOOKS)
                                   + " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_FK_BOOKSHELF)
                                   + '=' + mBookshelf.getId()
                                   + ')');
            } else {
                // we do not have the group but we do want a specific shelf.
                // So tell the helper we'll need to join and add the 'exact' filter.
                helper.setJoinWithBookshelves();
                mFilters.add(() -> '(' + TBL_BOOKSHELF.dot(DOM_PK_ID)
                                   + '=' + mBookshelf.getId() + ')');
            }
        }

        // Last step in preparing the domains: add any caller-specified extras (e.g. title).
        for (ExtraDomainDetails edd : mExtraDomains.values()) {
            helper.addDomain(edd.domain, edd.sourceExpression, edd.flags);
        }

        // With all domains setup, we can now (re)create the list-table

        // Drop the table in case there is an orphaned instance.
        mListTable.drop(mSyncedDb);
        //IMPORTANT: withConstraints MUST BE false
        mListTable.create(mSyncedDb, false);

        final long t01_table_created = System.nanoTime();

        // Construct the initial insert statement components.
        final BaseSql baseSql = helper.build();

        final long t02_build_base_sql = System.nanoTime();

        baseSql.addJoins(helper);

        final long t03_build_join = System.nanoTime();

        baseSql.addWhere(mFilters, mStyle.getFilters());

        final long t04_build_where = System.nanoTime();

        baseSql.addOrderBy(helper.getSortedDomains(), mSyncedDb.isCollationCaseSensitive());

        final long t05_build_order_by = System.nanoTime();

        mRebuildStmts.clear();

        // We are good to go.
        final SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            // get the triggers in place, ready to act on our upcoming initial insert.
            createTriggers(helper.getSortedDomains());

            // Build the lowest level summary using our initial insert statement
            // The triggers will do the rest.
            try (SynchronizedStatement baseStmt = mSyncedDb.compileStatement(baseSql.build())) {
                mRebuildStmts.add(baseStmt);
                baseStmt.executeInsert();
            }

            final long t06_base_insert_done = System.nanoTime();

            mSyncedDb.analyze(mListTable);

            final long t07_listTable_analyzed = System.nanoTime();

            // build the row-state aka navigation table
            // the table is ordered at insert time, so the row id *is* the order.
            buildRowStateTable(listState, mListTable.dot(DOM_PK_ID));

            final long t08_stateTable_build = System.nanoTime();

            // now we have all data inserted, create the indexes we need
            mRowStateTable.createIndices(mSyncedDb);

            final long t09_stateTable_idx_created = System.nanoTime();

            mSyncedDb.analyze(mRowStateTable);

            final long t10_stateTable_analyzed = System.nanoTime();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                Log.d(TAG, "build|" +
                           String.format(Locale.UK, ""
                                                    + "\ntable created         : %.10d"
                                                    + "\nbase sql              : %.10d"
                                                    + "\njoins                 : %.10d"
                                                    + "\nwhere clause          : %.10d"
                                                    + "\norder-by clause       : %.10d"
                                                    + "\nbase insert executed  : %.10d"
                                                    + "\ntable analyzed        : %.10d"
                                                    + "\nstateTable build      : %.10d"
                                                    + "\nstateTable idx created: %.10d"
                                                    + "\nstateTable_analyzed   : %.10d",

                                         t01_table_created - t00,
                                         t02_build_base_sql - t01_table_created,
                                         t03_build_join - t02_build_base_sql,
                                         t04_build_where - t03_build_join,
                                         t05_build_order_by - t04_build_where,
                                         t06_base_insert_done - t05_build_order_by,
                                         t07_listTable_analyzed - t06_base_insert_done,

                                         t08_stateTable_build - t07_listTable_analyzed,
                                         t09_stateTable_idx_created - t08_stateTable_build,
                                         t10_stateTable_analyzed - t09_stateTable_idx_created));
            }
            mSyncedDb.setTransactionSuccessful();

        } finally {
            mSyncedDb.endTransaction(txLock);

            // we don't catch exceptions but we do want to log the time it took here.
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Log.d(TAG, "EXIT|build|mInstanceId=" + mInstanceId
                           + "|Total time in ms: " + ((System.nanoTime() - t00) / TO_MILLIS));
            }
        }
    }

    /**
     * Build a collection of triggers on the list table designed to fill in the summary/header
     * records as the data records are added in sorted order.
     *
     * @param sortedDomains the domains we need to sort by, in order.
     */
    private void createTriggers(@NonNull final ArrayList<SortedDomains> sortedDomains) {

        // SQL statement to update the 'current' table
        StringBuilder valuesColumns = new StringBuilder();
        // List of domain names for sorting
        Set<String> sortedDomainNames = new HashSet<>();

        // Build the 'current' header table definition and the sort column list
        for (SortedDomains sdi : sortedDomains) {
            // don't add duplicate domains
            if (!sortedDomainNames.contains(sdi.domain.getName())) {
                sortedDomainNames.add(sdi.domain.getName());

                if (valuesColumns.length() > 0) {
                    valuesColumns.append(",");
                }
                valuesColumns.append("New.").append(sdi.domain.getName());
                mTriggerHelperTable.addDomain(sdi.domain);
            }
        }

        /*
         * Create a temp table to store the most recent header details from the last row.
         * We use this in determining what needs to be inserted as header records for
         * any given row.
         *
         * This is just a simple technique to provide persistent context to the trigger.
         */
        // Drop the table in case there is an orphaned instance.
        mTriggerHelperTable.drop(mSyncedDb);
        //IMPORTANT: withConstraints MUST BE false
        mTriggerHelperTable.create(mSyncedDb, false);

        /*
         * For each grouping, starting with the lowest, build a trigger to update the next
         * level up as necessary
         */
        for (int index = mStyle.groupCount() - 1; index >= 0; index--) {
            // Get the group
            final BooklistGroup group = mStyle.getGroupAt(index);
            // Get the level number for this group
            final int level = index + 1;

            // Create the INSERT columns clause for the next level up
            StringBuilder listColumns = new StringBuilder()
                    .append(DOM_BL_NODE_LEVEL)
                    .append(',').append(DOM_BL_NODE_KIND)
                    .append(',').append(DOM_BL_ROOT_KEY);

            // Create the VALUES clause for the next level up
            StringBuilder listValues = new StringBuilder()
                    .append(level)
                    .append(',').append(group.getKind())
                    .append(",New.").append(DOM_BL_ROOT_KEY);

            // Create the where-clause to detect if the next level up is already defined
            // (by checking the 'current' record/table)
            StringBuilder whereClause = new StringBuilder();

            //noinspection ConstantConditions
            for (DomainDefinition groupDomain : group.getDomains()) {
                listColumns.append(',').append(groupDomain);
                listValues.append(", New.").append(groupDomain);

                // Only add to the where-clause if the group is part of the SORT list
                if (sortedDomainNames.contains(groupDomain.getName())) {
                    if (whereClause.length() > 0) {
                        whereClause.append(" AND ");
                    }
                    whereClause.append("COALESCE(")
                               .append(mTriggerHelperTable.dot(groupDomain))
                               .append(",'')=COALESCE(New.").append(groupDomain).append(",'')")
                               .append(DAO.COLLATION);
                }
            }

            // (re)Create the trigger
            String levelTgName = mListTable.getName() + "_TG_LEVEL_" + level;
            mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + levelTgName);
            String levelTgSql =
                    "\nCREATE TEMPORARY TRIGGER " + levelTgName
                    + " BEFORE INSERT ON " + mListTable.getName() + " FOR EACH ROW"
                    + "\n WHEN New." + DOM_BL_NODE_LEVEL + '=' + (level + 1)
                    + " AND NOT EXISTS("
                    + /* */ "SELECT 1 FROM " + mTriggerHelperTable.ref() + " WHERE " + whereClause
                    + /* */ ')'
                    + "\n BEGIN"
                    + "\n   INSERT INTO " + mListTable.getName() + " (" + listColumns + ")"
                    + /*               */ " VALUES(" + listValues + ");"
                    + "\n END";
            SynchronizedStatement stmt = mStatementManager.add(levelTgName, levelTgSql);
            stmt.execute();
        }

        // Create a trigger to maintain the 'current' value -- just delete and insert
        String currentValueTgName = mListTable.getName() + "_TG_CURRENT";
        mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + currentValueTgName);
        String currentValueTgSql =
                "\nCREATE TEMPORARY TRIGGER " + currentValueTgName
                + " AFTER INSERT ON " + mListTable.getName() + " FOR EACH ROW"
                + "\n WHEN New." + DOM_BL_NODE_LEVEL + '=' + mStyle.groupCount()
                + "\n BEGIN"
                + "\n   DELETE FROM " + mTriggerHelperTable.getName() + ';'
                + "\n   INSERT INTO " + mTriggerHelperTable.getName()
                + /*               */ " VALUES (" + valuesColumns + ");"
                + "\n END";

        SynchronizedStatement stmt = mStatementManager.add(currentValueTgName, currentValueTgSql);
        stmt.execute();

    }

    /**
     * Build a lookup table to match row sort position to row ID. This is used to
     * match a specific book (or other row in result set) to a position directly
     * without having to scan the database. This is especially useful in
     * expand/collapse operations.
     *
     * @param listState         Desired list state
     * @param orderByExpression the ORDER BY expression
     */
    private void buildRowStateTable(@ListRebuildMode final int listState,
                                    @NonNull final String orderByExpression) {

        // we drop the table just in case there is a leftover table with the same name.
        // (due to previous crash maybe)
        mRowStateTable.drop(mSyncedDb);
        // indices are created after the table is populated.
        mRowStateTable.create(mSyncedDb, true, false);

        String baseSql = "INSERT INTO " + mRowStateTable
                         + " (" + DOM_FK_BOOK_BL_ROW_ID
                         + ',' + DOM_BL_ROOT_KEY

                         + ',' + DOM_BL_NODE_LEVEL
                         + ',' + DOM_BL_NODE_KIND

                         + ',' + DOM_BL_NODE_VISIBLE
                         + ',' + DOM_BL_NODE_EXPANDED
                         + ')'
                         + " SELECT DISTINCT " + mListTable.dot(DOM_PK_ID)
                         + ',' + mListTable.dot(DOM_BL_ROOT_KEY)

                         + ',' + mListTable.dot(DOM_BL_NODE_LEVEL)
                         + ',' + mListTable.dot(DOM_BL_NODE_KIND)
                         + "\n";

        // SQL to rebuild with state preserved.
        // We create this 'outside' of the switch() so we can  save this statement for rebuilds
        // IMPORTANT: we concat the sql with the bookshelf and style id's.
        // should bind those obviously, but current (and non-functional) mRebuildStmts
        // requires statements without parameters.
        String sqlPreserved =
                baseSql
                // SELECT DISTINCT bl._id, bl.root_key, bl.level,bl.kind
                // ,CASE
                // WHEN bl.level = 1 THEN 1
                // WHEN bl_ns.root_key IS NULL THEN 0
                // ELSE 1
                // END AS visible
                //
                // ,CASE WHEN bl_ns.root_key IS NULL THEN 0
                // ELSE bl_ns.expanded END AS expanded
                // FROM book_list_tmp_1 bl LEFT OUTER JOIN book_list_node_settings bl_ns
                // ON bl_ns.bookshelf=2 AND bl_ns.style=-1
                // AND bl.level=bl_ns.level
                // AND bl.root_key=bl_ns.root_key
                // ORDER BY bl._id

                // *all* rows in TBL_BOOK_LIST_NODE_STATE are to be considered visible!
                // ergo, all others are invisible but level 1 is always visible.

                // DOM_BL_NODE_VISIBLE
                + ",CASE"
                // level 1 is always visible
                + " WHEN " + mListTable.dot(DOM_BL_NODE_LEVEL) + "=1 THEN 1"
                // if the row is not present, hide it.
                + " WHEN " + TBL_BOOK_LIST_NODE_STATE.dot(DOM_BL_ROOT_KEY) + " IS NULL THEN 0"
                // all others are visible
                + " ELSE 1"
                + " END"
                // 'AS' for SQL readability/debug only
                + " AS " + DOM_BL_NODE_VISIBLE
                + "\n"

                // DOM_BL_NODE_EXPANDED
                + ",CASE"
                // if the row is not present, collapse it.
                + " WHEN " + TBL_BOOK_LIST_NODE_STATE.dot(DOM_BL_ROOT_KEY) + " IS NULL THEN 0"
                //  Otherwise use the stored state
                + " ELSE " + TBL_BOOK_LIST_NODE_STATE.dot(DOM_BL_NODE_EXPANDED)
                + " END"
                // 'AS' for SQL readability/debug only
                + " AS " + DOM_BL_NODE_EXPANDED
                + "\n"

                + " FROM " + mListTable.ref()
                // note this is a pure OUTER JOIN.
                + " LEFT OUTER JOIN " + TBL_BOOK_LIST_NODE_STATE.ref()
                + " ON "
                + TBL_BOOK_LIST_NODE_STATE.dot(DOM_FK_BOOKSHELF) + '=' + mBookshelf.getId()
                + " AND "
                + TBL_BOOK_LIST_NODE_STATE.dot(DOM_FK_STYLE) + '=' + mStyle.getId()
                + " AND "
                + TBL_BOOK_LIST_NODE_STATE.dot(DOM_BL_NODE_LEVEL)
                + '=' + mListTable.dot(DOM_BL_NODE_LEVEL)
                + " AND "
                + TBL_BOOK_LIST_NODE_STATE.dot(DOM_BL_ROOT_KEY)
                + '=' + mListTable.dot(DOM_BL_ROOT_KEY)
                + " ORDER BY " + orderByExpression;


        SynchronizedStatement rebuildSavedStateStmt =
                mStatementManager.add(STMT_BUILD_ROW_STATE_TABLE, sqlPreserved);
        // once rebuild works again, we should find a solution to bind shelf/style id's
        mRebuildStmts.add(rebuildSavedStateStmt);

        int topLevel = mStyle.getTopLevel();

        // On first-time builds, get the Preferences-based list
        switch (listState) {
            case PREF_LIST_REBUILD_ALWAYS_EXPANDED: {
                String sql = baseSql
                             // DOM_BL_NODE_VISIBLE: all visible
                             + ",1"
                             // DOM_BL_NODE_EXPANDED: all expanded
                             + ",1"
                             + " FROM " + mListTable.ref() + " ORDER BY " + orderByExpression;

                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    int rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                        Log.d(TAG, "PREF_LIST_REBUILD_ALWAYS_EXPANDED"
                                   + "|rowsUpdated=" + rowsUpdated + "|sql=" + sql);
                    }
                }
                break;
            }
            case PREF_LIST_REBUILD_ALWAYS_COLLAPSED: {
                String sql = baseSql
                             // DOM_BL_NODE_VISIBLE: level 1 visible and all others invisible.
                             + ",CASE"
                             + " WHEN " + DOM_BL_NODE_LEVEL + "=1 THEN 1"
                             + " ELSE 0"
                             + " END"
                             // 'AS' for SQL readability/debug only
                             + " AS " + DOM_BL_NODE_VISIBLE

                             // DOM_BL_NODE_EXPANDED: all nodes collapsed
                             + ",0"
                             + "\n"
                             + " FROM " + mListTable.ref() + " ORDER BY " + orderByExpression;

                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    int rowsUpdated = stmt.executeUpdateDelete();
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                        Log.d(TAG, "PREF_LIST_REBUILD_ALWAYS_COLLAPSED"
                                   + "|rowsUpdated=" + rowsUpdated
                                   + "|sql=" + sql);
                    }
                }
                break;
            }
            case PREF_LIST_REBUILD_SAVED_STATE: {
                // Use already-defined SQL for preserve state.
                int rowsUpdated = rebuildSavedStateStmt.executeUpdateDelete();
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                    Log.d(TAG, "PREF_LIST_REBUILD_SAVED_STATE"
                               + "|rowsUpdated=" + rowsUpdated
                               + "|sql=" + sqlPreserved);
                }

//                TBL_BOOK_LIST_NODE_STATE
//                        .dumpTable(mSyncedDb, "PREF_LIST_REBUILD_SAVED_STATE", 0, 1000, "");
//                mRowStateTable
//                        .dumpTable(mSyncedDb, "PREF_LIST_REBUILD_SAVED_STATE", 71, 75, "");
                break;
            }
            case PREF_LIST_REBUILD_PREFERRED_STATE: {
                expandNodes(topLevel, false);
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                    Log.d(TAG, "PREF_LIST_REBUILD_PREFERRED_STATE");
                }
                break;
            }


            default:
                throw new UnexpectedValueException(listState);

        }
    }

    /**
     * Get the style used by this builder.
     *
     * @return the style
     */
    @NonNull
    public BooklistStyle getStyle() {
        return mStyle;
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
    BooklistCursor getOffsetCursor(final int position,
                                   @SuppressWarnings("SameParameterValue") final int pageSize) {
        // Get the domains
        StringBuilder domains = new StringBuilder();
        final String prefix = mListTable.getAlias() + '.';

        for (DomainDefinition domain : mListTable.getDomains()) {
            domains.append(prefix).append(domain.getName())
                   .append(" AS ").append(domain.getName()).append(',');
        }

        // Build the SQL, adding DOM_BL_ABSOLUTE_POSITION.
        String sql = "SELECT " + domains
                     + " (" + mRowStateTable.dot(DOM_PK_ID) + " -1) AS " + DOM_BL_ABSOLUTE_POSITION

                     + " FROM " + mListTable.ref() + mListTable.join(mRowStateTable)
                     + " WHERE " + mRowStateTable.dot(DOM_BL_NODE_VISIBLE) + "=1"
                     + " ORDER BY " + mRowStateTable.dot(DOM_PK_ID)
                     + " LIMIT " + pageSize + " OFFSET " + position;

        return (BooklistCursor) mSyncedDb.rawQueryWithFactory(mBooklistCursorFactory, sql,
                                                              null, "");
    }

    /**
     * Get a new list cursor.
     *
     * @return a {@link BooklistPseudoCursor} instead of a real cursor.
     */
    @NonNull
    public BooklistPseudoCursor getNewListCursor() {
        return new BooklistPseudoCursor(this);
    }

    /**
     * Get all positions, and the row info for that position,
     * at which the specified book appears (and is visible).
     *
     * @param bookId the book to find
     *
     * @return Array of row details, including absolute positions, visibility and level.
     * Can be empty, but never {@code null}.
     */
    @NonNull
    private ArrayList<RowDetails> getBookPositions(final long bookId) {
        String sql = "SELECT "
                     + mRowStateTable.dot(DOM_PK_ID)
                     + ',' + mRowStateTable.dot(DOM_BL_NODE_VISIBLE)
                     + ',' + mRowStateTable.dot(DOM_BL_NODE_LEVEL)
                     + " FROM " + mListTable.ref() + mListTable.join(mRowStateTable)
                     + " WHERE " + mListTable.dot(DOM_FK_BOOK) + "=?";

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(bookId)})) {
            ArrayList<RowDetails> rows = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                int absPos = cursor.getInt(0) - 1;
                boolean visible = cursor.getInt(1) == 1;
                int level = cursor.getInt(2);
                rows.add(new RowDetails(absPos, mRowStateTable.getListPosition(absPos),
                                        visible, level));
            }
            return rows;
        }
    }

    /**
     * Find the visible root node for a given absolute position and ensure it is visible.
     *
     * @param row we want to become visible
     */
    private void ensureAbsolutePositionIsVisible(@NonNull final RowDetails row) {

        // If <0 then no previous node.
        if (row.absolutePosition < 0) {
            return;
        }

        // Get the root node (level==1) 'before' the given row.
        String sql = "SELECT " + DOM_PK_ID + ',' + DOM_BL_NODE_EXPANDED
                     + " FROM " + mRowStateTable
                     + " WHERE " + DOM_BL_NODE_LEVEL + "=1" + " AND " + DOM_PK_ID + "<=?"
                     + " ORDER BY " + DOM_PK_ID + " DESC LIMIT 1";

        // rows start at 1, but positions at 0
        long rowId = row.absolutePosition + 1;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(rowId)})) {
            if (cursor.moveToFirst()) {
                // Get the root node and expanded flag
                long rootId = cursor.getLong(0);
                boolean rootIsExpanded = cursor.getLong(1) != 0;

                // If root node is not the node we are checking,
                // and root node is not expanded, expand it.
                if (rootId != rowId && !rootIsExpanded) {
                    // rows start at 1, but positions at 0
                    expandNode(rootId - 1, NodeState.Expand);
                }
            }
        }
    }

    /**
     * Try to sync the previously selected book ID.
     *
     * @param bookId the book to find
     *
     * @return the target rows, or {@code null} if none.
     */
    @Nullable
    public ArrayList<RowDetails> syncPreviouslySelectedBookId(final long bookId) {
        // no input, no output...
        if (bookId == 0) {
            return null;
        }

        // get all positions of the book
        ArrayList<RowDetails> rows = getBookPositions(bookId);

        if (rows.isEmpty()) {
            return null;
        }

        // First, get the ones that are currently visible...
        ArrayList<RowDetails> visibleRows = new ArrayList<>();
        for (RowDetails row : rows) {
            if (row.visible) {
                visibleRows.add(row);
            }
        }

        // If we have any visible rows, only consider those for the new position
        if (!visibleRows.isEmpty()) {
            rows = visibleRows;

        } else {
            // Make them all visible
            for (RowDetails row : rows) {
                if (!row.visible) {
                    ensureAbsolutePositionIsVisible(row);
                }
            }
            // Recalculate all positions
            for (RowDetails row : rows) {
                row.listPosition = mRowStateTable.getListPosition(row.absolutePosition);
            }
        }

        // This was commented out in the original code.
//            // Find the nearest row to the recorded 'top' row.
//            int targetRow = rows.get(0).absolutePosition;
//            int minDist = Math.abs(mModel.getTopRow()
//                          - mRowStateTable.getListPosition(targetRow));
//            for (int i = 1; i < rows.size(); i++) {
//                int pos = mRowStateTable.getListPosition(rows.get(i).absolutePosition);
//                int dist = Math.abs(mModel.getTopRow() - pos);
//                if (dist < minDist) {
//                    targetRow = rows.get(i).absolutePosition;
//                }
//            }
//            // Make sure the target row is visible/expanded.
//            ensureAbsolutePositionIsVisible(targetRow);
//            // Now find the position it will occupy in the view
//            mTargetPos = mRowStateTable.getListPosition(targetRow);

        return rows;
    }

    /**
     * Expand or collapse <strong>all</strong> nodes.
     *
     * @param topLevel the desired top-level which must be kept visible
     * @param expand   the state to apply to levels 'below' the top-level
     */
    public void expandNodes(@IntRange(from = 1) final int topLevel,
                            final boolean expand) {

        SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            final long t0 = System.nanoTime();

            // in-memory expansion
            mRowStateTable.expandNodes(expand, topLevel);

            // Store the state of all nodes.
            preserveAllNodes();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                Log.d(TAG, "expandNodes|" + (System.nanoTime() - t0) / TO_MILLIS);
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
     * Expand, collapse or toggle the status of the node at the specified absolute position.
     *
     * @param position   of the node in the list
     * @param nodeStatus one of {@link NodeState}
     *
     * @return {@code true} if the new state is expanded, {@code false} if collapsed.
     */
    public boolean expandNode(final long position,
                              @NonNull final NodeState nodeStatus) {

        // row position starts at 0, ID's start at 1...
        final long rowId = position + 1;
        // current state of the row
        boolean rowIsExpanded;
        // level of the row
        int rowLevel;

        // future state of the affected rows.
        boolean expand;

        SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            // Get the current state of the node for the given row id.
            String sql = "SELECT " + DOM_BL_NODE_LEVEL + ',' + DOM_BL_NODE_EXPANDED
                         + " FROM " + mRowStateTable.ref()
                         + " WHERE " + mRowStateTable.dot(DOM_PK_ID) + "=?";

            try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(rowId)})) {
                if (cursor.moveToFirst()) {
                    rowLevel = cursor.getInt(0);
                    rowIsExpanded = cursor.getInt(1) != 0;
                } else {
                    throw new IllegalStateException("no row found for position=" + position);
                }
            }

            // Decide what the new state for the nodes should be.
            switch (nodeStatus) {
                case Collapse:
                    expand = false;
                    break;
                case Expand:
                    expand = true;
                    break;

                case Toggle:
                default:
                    expand = !rowIsExpanded;
                    break;
            }

            // expand/collapse until we get to a node on the same level.
            long nextRowId = mRowStateTable.expandNodes(expand, rowId, rowLevel);

            // Store the state of these nodes.
            preserveNodes(rowId, nextRowId);

            if (txLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }

        return expand;
    }

    private String getPreserveNodesInsertSql() {
        return ("INSERT INTO " + TBL_BOOK_LIST_NODE_STATE
                + " (" + DOM_FK_BOOKSHELF
                + ',' + DOM_FK_STYLE

                + ',' + DOM_BL_ROOT_KEY
                + ',' + DOM_BL_NODE_LEVEL
                + ',' + DOM_BL_NODE_KIND
                + ',' + DOM_BL_NODE_EXPANDED
                + ')'
                + " SELECT DISTINCT "
                + "?"
                + ",?"
                + ',' + DOM_BL_ROOT_KEY
                + ',' + DOM_BL_NODE_LEVEL
                + ',' + DOM_BL_NODE_KIND
                + ',' + DOM_BL_NODE_EXPANDED)

               + " FROM " + mRowStateTable
               // only store visible rows; all others will be considered non-visible and collapsed.
               + " WHERE " + DOM_BL_NODE_VISIBLE + "=1";
    }

    /**
     * Save the state for all nodes to permanent storage.
     * We only store expanded/visible nodes.
     */
    private void preserveAllNodes() {
        SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }
            SynchronizedStatement stmt;

            // delete *all* rows for the current bookshelf
            stmt = mStatementManager.get(STMT_DELETE_ALL_NODES);
            if (stmt == null) {
                stmt = mStatementManager.add(STMT_DELETE_ALL_NODES,
                                             mPreserveNodesBaseDeleteSql);
            }
            stmt.bindLong(1, mBookshelf.getId());

            int rowsDeleted = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                Log.d(TAG, "preserveAllNodes"
                           + "|bookshelfId=" + mBookshelf.getId()
                           + "|rowsDeleted=" + rowsDeleted);
            }

            // Read all nodes, and send them to the permanent table.
            stmt = mStatementManager.get(STMT_SAVE_SOME_NODES);
            if (stmt == null) {
                String sql = getPreserveNodesInsertSql();
                stmt = mStatementManager.add(STMT_SAVE_SOME_NODES, sql);
            }

            stmt.bindLong(1, mBookshelf.getId());
            stmt.bindLong(2, mStyle.getId());

            int rowsUpdated = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                Log.d(TAG, "preserveAllNodes"
                           + "|bookshelfId=" + mBookshelf.getId()
                           + "|styleId=" + mStyle.getId()
                           + "|rowsUpdated=" + rowsUpdated);
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
     * Save the state for a single node to permanent storage.
     *
     * @param startRow between this row (inclusive)
     * @param endRow   and this row (exclusive)
     */
    private void preserveNodes(final long startRow,
                               final long endRow) {
        SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }
            SynchronizedStatement stmt;

//            mRowStateTable
//                    .dumpTable(mSyncedDb, "preserveNodes", 71, 75, "before delete");
//            TBL_BOOK_LIST_NODE_STATE
//                    .dumpTable(mSyncedDb, "preserveNodes", 0, 1000, "before delete");

            // delete the given rows for the current bookshelf and root-key
            stmt = mStatementManager.get(STMT_DELETE_SOME_NODES);
            if (stmt == null) {
                stmt = mStatementManager.add(STMT_DELETE_SOME_NODES,
                                             mPreserveNodesBaseDeleteSql
                                             + " AND " + DOM_BL_ROOT_KEY + " IN ("
                                             + " SELECT DISTINCT " + DOM_BL_ROOT_KEY
                                             + " FROM " + mRowStateTable
                                             + " WHERE " + DOM_PK_ID + ">=?" + " AND " + DOM_PK_ID
                                             + "<?"
                                             + ")");
            }
            stmt.bindLong(1, mBookshelf.getId());

            stmt.bindLong(2, startRow);
            stmt.bindLong(3, endRow);

            int rowsDeleted = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                Log.d(TAG, "preserveNodes"
                           + "|bookshelfId=" + mBookshelf.getId()
                           + "|startRow=" + startRow
                           + "|endRow=" + endRow
                           + "|rowsDeleted=" + rowsDeleted);
            }

//            TBL_BOOK_LIST_NODE_STATE
//                    .dumpTable(mSyncedDb, "preserveNodes", 0, 1000, "delete done");

            // Read all nodes below the given node, and send them to the permanent table.
            stmt = mStatementManager.get(STMT_SAVE_ALL_NODES);
            if (stmt == null) {
                String sql = getPreserveNodesInsertSql()
                             + " AND " + DOM_PK_ID + ">=?" + " AND " + DOM_PK_ID + "<?";
                stmt = mStatementManager.add(STMT_SAVE_ALL_NODES, sql);
            }

            int p = 0;
            stmt.bindLong(++p, mBookshelf.getId());
            stmt.bindLong(++p, mStyle.getId());

            stmt.bindLong(++p, startRow);
            stmt.bindLong(++p, endRow);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                Log.d(TAG, "preserveNodes"
                           + "|bookshelfId=" + mBookshelf.getId()
                           + "|styleId=" + mStyle.getId()
                           + "|startRow=" + startRow
                           + "|endRow=" + endRow);
            }

            int rowsUpdated = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                Log.d(TAG, "preserveNodes"
                           + "|rowsUpdated=" + rowsUpdated);
            }

//            TBL_BOOK_LIST_NODE_STATE
//                    .dumpTable(mSyncedDb, "preserveNodes", 0, 1000, "insert done");

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
     * Close the builder.
     * <p>
     * RuntimeException are caught and ignored.
     */
    @Override
    public void close() {
        mCloseWasCalled = true;

        if (!mStatementManager.isEmpty()) {
            mStatementManager.close();
        }

        if (mRowStateTable != null) {
            mRowStateTable.drop(mSyncedDb);
        }

        if (mListTable != null) {
            mListTable.drop(mSyncedDb);
        }

        mDb.close();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            if (!mDebugReferenceDecremented) {
                int inst = DEBUG_INSTANCE_COUNTER.decrementAndGet();
                // Only de-reference once! Paranoia ... close() might be called twice?
                Log.d(TAG, "close|instances left: " + inst);
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
        BooklistBuilder that = (BooklistBuilder) obj;
        return mInstanceId == that.mInstanceId;
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistBuilder{"
               + "mInstanceId=" + mInstanceId
               + ", mCloseWasCalled=" + mCloseWasCalled
               //+ ", mRebuildStmts=" + mRebuildStmts
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
            Logger.warn(App.getAppContext(), TAG, "finalize|calling close()");
            close();
        }
        super.finalize();
    }

    /**
     * Get the full list of all Books (their id only) which are currently in the list table.
     *
     * @return list of book ids
     */
    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        String sql = "SELECT " + DBDefinitions.KEY_FK_BOOK
                     + " FROM " + mListTable
                     + " WHERE " + DOM_BL_NODE_KIND + '=' + BooklistGroup.RowKind.BOOK
                     + " ORDER BY " + DBDefinitions.KEY_FK_BOOK;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            ArrayList<Long> rows = new ArrayList<>(cursor.getCount());
            if (cursor.moveToFirst()) {
                do {
                    long id = (long) cursor.getInt(0);
                    rows.add(id);
                } while (cursor.moveToNext());
                return rows;
            } else {
                return new ArrayList<>();
            }
        }
    }

    /**
     * Get the list of column names that will be in the list for cursor implementations.
     *
     * @return array with column names
     */
    @NonNull
    String[] getListColumnNames() {
        // Get the domains
        List<DomainDefinition> domains = mListTable.getDomains();
        // Make the array +1 so we can add DOM_BL_ABSOLUTE_POSITION
        String[] names = new String[domains.size() + 1];
        // Copy domains
        for (int i = 0; i < domains.size(); i++) {
            names[i] = domains.get(i).getName();
        }

        names[domains.size()] = DOM_BL_ABSOLUTE_POSITION.getName();
        return names;
    }

    /**
     * Wrapper for {@link RowStateTable#getListPosition(int)}.
     *
     * @param absolutePosition Abs. position to check
     *
     * @return Actual list position.
     */
    public int getListPosition(final int absolutePosition) {
        return mRowStateTable.getListPosition(absolutePosition);
    }

    /**
     * Count the number of <strong>distinct</strong> book records in the list.
     *
     * @return count
     */
    public int getDistinctBookCount() {
        SynchronizedStatement stmt = mStatementManager.get(STMT_BL_DISTINCT_BOOK_COUNT);
        if (stmt == null) {
            stmt = mStatementManager.add(STMT_BL_DISTINCT_BOOK_COUNT,
                                         "SELECT COUNT(DISTINCT " + DOM_FK_BOOK + ")"
                                         + " FROM " + mListTable
//                                         + " WHERE " + DOM_BL_NODE_LEVEL + "=?");
                                         + " WHERE " + DOM_BL_NODE_KIND
                                         + '=' + BooklistGroup.RowKind.BOOK);
        }

        final long t0 = System.nanoTime();
        long count;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
//            stmt.bindLong(1, mStyle.groupCount() + 1);
            count = stmt.count();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            Log.d(TAG, "getDistinctBookCount"
                       + "|count=" + count
                       + "|completed in " + (System.nanoTime() - t0) + " nano");
        }
        return (int) count;
    }

    /**
     * Count the total number of book records in the list.
     *
     * @return count
     */
    public int getBookCount() {
        SynchronizedStatement stmt = mStatementManager.get(STMT_BL_BOOK_COUNT);
        if (stmt == null) {
            stmt = mStatementManager.add(STMT_BL_BOOK_COUNT,
                                         "SELECT COUNT(*) FROM " + mListTable
//                                         + " WHERE " + DOM_BL_NODE_LEVEL + "=?");
                                         + " WHERE " + DOM_BL_NODE_KIND
                                         + '=' + BooklistGroup.RowKind.BOOK);
        }

        final long t0 = System.nanoTime();
        long count;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
//            stmt.bindLong(1, mStyle.groupCount() + 1);
            count = stmt.count();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            Log.d(TAG, "getBookCount"
                       + "|count=" + count
                       + "|completed in " + (System.nanoTime() - t0) + " nano");
        }
        return (int) count;
    }

    /**
     * Wrapper for {@link RowStateTable#countVisibleRows()}.
     *
     * @return count
     */
    int countVisibleRows() {
        return mRowStateTable.countVisibleRows();
    }

    public enum NodeState {
        Toggle, Expand, Collapse
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PREF_LIST_REBUILD_SAVED_STATE,
             PREF_LIST_REBUILD_ALWAYS_EXPANDED,
             PREF_LIST_REBUILD_ALWAYS_COLLAPSED,
             PREF_LIST_REBUILD_PREFERRED_STATE})
    public @interface ListRebuildMode {

    }

    /**
     * A data class containing details of a single row.
     */
    public static class RowDetails {

        @SuppressWarnings("WeakerAccess")
        public final int absolutePosition;
        @IntRange(from = 1)
        public final int level;
        public final boolean visible;
        public int listPosition;

        RowDetails(final int absolutePosition,
                   final int listPosition,
                   final boolean visible,
                   @IntRange(from = 1) final int level) {
            this.absolutePosition = absolutePosition;
            this.listPosition = listPosition;
            this.visible = visible;
            this.level = level;
        }
    }

    /**
     * A data class for domain + desc/asc sorting flag.
     */
    private static class SortedDomains {

        @NonNull
        final DomainDefinition domain;
        final boolean isDescending;

        SortedDomains(@NonNull final DomainDefinition domain,
                      final boolean isDescending) {
            this.domain = domain;
            this.isDescending = isDescending;
        }
    }

    /**
     * A data class of extra domains requested by caller before the build() method is called.
     */
    private static class ExtraDomainDetails {

        /** Domain definition of domain to add. */
        @NonNull
        final DomainDefinition domain;
        /** Expression to use in deriving domain value. */
        @Nullable
        final String sourceExpression;
        /** Flags indicating attributes of new domain. */
        @BuildHelper.Flags
        final int flags;

        ExtraDomainDetails(@NonNull final DomainDefinition domain,
                           @Nullable final String sourceExpression,
                           @BuildHelper.Flags final int flags) {
            this.domain = domain;
            this.sourceExpression = sourceExpression;
            this.flags = flags;
        }
    }

    /**
     * Store and process components of the SQL required to build the list.
     */
    private static final class BaseSql {

        /** destination table. */
        private final String mDestinationTableName;
        /** Columns in the current list table. */
        @NonNull
        private final String mDestinationColumns;
        /** Columns from the original tables. */
        @NonNull
        private final String mSourceColumns;

        /** FROM clause; Tables and joins. */
        private String mFromClause;
        /** WHERE clause; includes the WHERE keyword. Alternatively, an empty string. */
        private String mWhereClause;
        /** ORDER BY clause; list of column names. */
        private String mOrderByClause;

        BaseSql(@NonNull final String destinationTableName,
                @NonNull final String destinationColumns,
                @NonNull final String sourceColumns) {
            mDestinationTableName = destinationTableName;
            mDestinationColumns = destinationColumns;
            mSourceColumns = sourceColumns;
        }

        /**
         * Build the 'join' statement based on the groups and extra criteria.
         * <ul>Always joined are:
         * <li>{@link DBDefinitions#TBL_BOOK_AUTHOR}<br>{@link DBDefinitions#TBL_AUTHORS}</li>
         * <li>{@link DBDefinitions#TBL_BOOK_SERIES}<br>{@link DBDefinitions#TBL_SERIES}</li>
         * </ul>
         * <ul>Optionally joined with:
         * <li>{@link DBDefinitions#TBL_BOOK_BOOKSHELF}<br>{@link DBDefinitions#TBL_BOOKSHELF}</li>
         * <li>{@link DBDefinitions#TBL_BOOK_LOANEE}</li>
         * </ul>
         *
         * @param buildHelper info about the joins to be made.
         */
        void addJoins(@NonNull final BuildHelper buildHelper) {

            Joiner joiner;

            // If there is a bookshelf specified, start the join there.
            // Otherwise, start with the BOOKS table.
            if (buildHelper.hasBookshelfGroup()) {
                joiner = new Joiner(TBL_BOOKSHELF)
                        .join(TBL_BOOK_BOOKSHELF)
                        .join(TBL_BOOKS);
            } else {
                joiner = new Joiner(TBL_BOOKS);
            }

            // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
            if (buildHelper.hasLoaneeGroup() || App.isUsed(DBDefinitions.KEY_LOANEE)) {
                // so get the loanee name, or a {@code null} for available books.
                joiner.leftOuterJoin(TBL_BOOK_LOANEE);
            }

            // Now join with author; we must specify a parent in the join, because the last table
            // joined was one of BOOKS or LOAN and we don't know which. So we explicitly use books.
            joiner.join(TBL_BOOKS, TBL_BOOK_AUTHOR);

            // Join with the primary Author if needed.
            BooklistGroup.BooklistAuthorGroup authorGroup = buildHelper.getAuthorGroup();
            if (authorGroup == null || !authorGroup.showAll()) {
                joiner.append(" AND " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_POSITION) + "=1");
            }

            // Join with Authors to make the names available
            joiner.join(TBL_AUTHORS);

            // Current table will be authors, so name parent explicitly to join books->book_series.
            joiner.leftOuterJoin(TBL_BOOKS, TBL_BOOK_SERIES);

            // Join with the primary Series if needed.
            BooklistGroup.BooklistSeriesGroup seriesGroupGroup = buildHelper.getSeriesGroup();
            if (seriesGroupGroup == null || !seriesGroupGroup.showAll()) {
                joiner.append(" AND " + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + "=1");
            }

            // Join with Series to make the titles available
            joiner.leftOuterJoin(TBL_SERIES);

            mFromClause = joiner.build();
        }

        /**
         * Create the WHERE clause based on all filters.
         *
         * @param activeFilters   filter which are assumed to be active; will always be added
         * @param optionalFilters optional filters which will only be added if they are active
         */
        void addWhere(@NonNull final ArrayList<Filter> activeFilters,
                      @Nullable final Collection<Filter> optionalFilters) {
            StringBuilder where = new StringBuilder();

            for (Filter filter : activeFilters) {
                if (where.length() != 0) {
                    where.append(" AND ");
                }
                where.append(' ').append(filter.getExpression());
            }

            if (optionalFilters != null) {
                for (Filter filter : optionalFilters) {
                    if (filter.isActive()) {
                        if (where.length() != 0) {
                            where.append(" AND ");
                        }
                        where.append(' ').append(filter.getExpression());
                    }
                }
            }
            // all done
            if (where.length() > 0) {
                mWhereClause = where.insert(0, " WHERE ").toString();
            } else {
                mWhereClause = "";
            }
        }

        /**
         * Process the 'sort-by' columns into a list suitable for an ORDER-BY statement, or index.
         * <p>
         * If the {@link DAO#COLLATION} is case-sensitive, we wrap the columns in "lower()"
         *
         * @param sortedDomains the list of sorted domains from the builder
         * @param collationIsCs if {@code true} then we'll adjust the case here
         */
        void addOrderBy(@NonNull final List<SortedDomains> sortedDomains,
                        final boolean collationIsCs) {
            final StringBuilder sortCols = new StringBuilder();
            final StringBuilder indexCols = new StringBuilder();

            for (SortedDomains sdi : sortedDomains) {
                indexCols.append(sdi.domain.getName());
                if (sdi.domain.isText()) {
                    indexCols.append(DAO.COLLATION);
                    // The order of this if/elseif/else is important, don't merge branch 1 and 3!
                    if (sdi.domain.isPrePreparedOrderBy()) {
                        // always use a pre-prepared order-by column as-is
                        sortCols.append(sdi.domain.getName());

                    } else if (collationIsCs) {
                        // *If* collations is case-sensitive, lowercase it.
                        sortCols.append("lower(").append(sdi.domain.getName()).append(')');

                    } else {
                        // hope for the best. This case might not handle non-[A..Z0..9] as expected
                        sortCols.append(sdi.domain.getName());
                    }

                    sortCols.append(DAO.COLLATION);
                } else {
                    sortCols.append(sdi.domain.getName());
                }
                if (sdi.isDescending) {
                    indexCols.append(" DESC");
                    sortCols.append(" DESC");
                }
                sortCols.append(',');
                indexCols.append(',');
            }

            sortCols.append(DOM_BL_NODE_LEVEL);
            indexCols.append(DOM_BL_NODE_LEVEL);

            mOrderByClause = sortCols.toString();
        }

        /**
         * Constructs the INSERT INTO statement for the base build.
         *
         * @return INSERT INTO statement
         */
        @NonNull
        String build() {
            String sql = "INSERT INTO " + mDestinationTableName + '(' + mDestinationColumns + ')'
                         + " SELECT " + mSourceColumns
                         + " FROM " + mFromClause + mWhereClause
                         + " ORDER BY " + mOrderByClause;

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Log.d(TAG, "build|sql=" + sql);
            }
            return sql;
        }
    }

    /**
     * Accumulates data while building the list table.
     */
    private static final class BuildHelper {

        /** Flag (bitmask) indicating added domain has no special needs. */
        static final int FLAG_NONE = 0;
        /** Flag indicating domain is part of the current group. */
        static final int FLAG_GROUP = 1;
        /** Flag indicating domain should be added to the ORDER BY clause. */
        static final int FLAG_SORTED = 1 << 1;
        /**
         * Flag indicating added domain should be sorted in DESC order.
         * DO NOT USE FOR GROUPED DATA. See notes below.
         */
        static final int FLAG_SORT_DESCENDING = 1 << 2;

        // Not currently used.
        ///** Flag indicating added domain is part of the unique key. */
        //static final int FLAG_KEY = 1 << 3;
        ///** Domains that form part of accumulated unique key. */
        //private List<DomainDefinition> mKeys = new ArrayList<DomainDefinition>();
        @NonNull
        private final TableDefinition mDestinationTable;
        @NonNull
        private final BooklistStyle mStyle;
        /** Domains required in output table. */
        private final ArrayList<DomainDefinition> mDomains = new ArrayList<>();
        /** Mapping from Domain to source Expression. */
        private final Map<DomainDefinition, String> mExpressions = new HashMap<>();
        /** Domains belonging the current group including its outer groups. */
        private final ArrayList<DomainDefinition> mGroupDomains = new ArrayList<>();
        /**
         * Domains that form part of the sort key.
         * These are typically a reduced set of the GROUP domains since the group domains
         * may contain more than just the key
         */
        private final ArrayList<SortedDomains> mSortedDomains = new ArrayList<>();
        /** The set is used as a simple mechanism to prevent duplicate domains. */
        private final Set<DomainDefinition> mSortedColumnsSet = new HashSet<>();
        /** Will be set to appropriate Group if an Author group exists in style. */
        private BooklistGroup.BooklistAuthorGroup mAuthorGroup;
        /** Will be set to appropriate Group if a Series group exists in style. */
        private BooklistGroup.BooklistSeriesGroup mSeriesGroup;
        /** Will be set to {@code true} if a LOANED group exists in style. */
        private boolean mHasLoaneeGroup;
        /** Will be set to {@code true} if a BOOKSHELF group exists in style. */
        private boolean mHasBookshelfGroup;

        /**
         * Constructor.
         *
         * @param destinationTable the list table to build
         * @param style            to use
         */
        private BuildHelper(@NonNull final TableDefinition destinationTable,
                            @NonNull final BooklistStyle style) {
            mDestinationTable = destinationTable;
            mStyle = style;
        }

        /**
         * Add a domain and source expression to the summary.
         *
         * @param domain           Domain to add
         * @param sourceExpression Source Expression
         * @param flags            Flags indicating attributes of new domain
         */
        void addDomain(@NonNull final DomainDefinition domain,
                       @Nullable final String sourceExpression,
                       @Flags final int flags) {
            // Add to various collections. We use a map to improve lookups and ArrayLists
            // so we can preserve order. Order preservation makes reading the SQL easier
            // but is unimportant for code correctness.

            // Add to table
            boolean added = mDestinationTable.addDomain(domain);
            if (!added) {
                // The domain was already present, do a sanity check!
                String expression = mExpressions.get(domain);
                if (Objects.equals(sourceExpression, expression)) {
                    // same expression, we do NOT want to add it.
                    // This is NOT a bug, although one could argue it's an efficiency issue.
                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "duplicate domain/expression"
                                   + "|domain.name=" + domain.getName(),
                              new Throwable());
                    }
                    return;
                }
            }

            // If the sourceExpression is {@code null},
            // then the domain is just meant for the lowest level of the hierarchy.
            if (sourceExpression != null) {
                mDomains.add(domain);
                mExpressions.put(domain, sourceExpression.trim());
            }

            // Based on the flags, add the domain to other lists.
            if ((flags & FLAG_GROUP) != 0) {
                mGroupDomains.add(domain);
            }

            if ((flags & FLAG_SORTED) != 0 && !mSortedColumnsSet.contains(domain)) {
                boolean isDesc = (flags & FLAG_SORT_DESCENDING) != 0;
                SortedDomains sdi = new SortedDomains(domain, isDesc);
                mSortedDomains.add(sdi);
                mSortedColumnsSet.add(domain);
            }

            // Not currently used
            //if ((flags & FLAG_KEY) != 0) {
            //    mKeys.add(domain);
            //}
        }

        /**
         * Add the domains for the given group.
         *
         * @param group to add
         */
        void addGroup(@NonNull final BooklistGroup group) {

            switch (group.getKind()) {

                case BooklistGroup.RowKind.BOOK:
                    // do nothing.
                    break;

                case BooklistGroup.RowKind.AUTHOR: {
                    // Save for later use
                    mAuthorGroup = (BooklistGroup.BooklistAuthorGroup) group;

                    // Do not sort by it as we'll use the OB column instead
                    addDomain(group.getFormattedDomain(),
                              // not using getFormattedDomainExpression() as it depends on pref.
                              mAuthorGroup.showAuthorGivenNameFirst()
                              ? DAO.SqlColumns.EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY
                              : DAO.SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN,
                              BuildHelper.FLAG_GROUP);

                    // Always sort by DOM_RK_AUTHOR_SORT and user preference order.
                    // Sort uses the OB column.
                    addDomain(DOM_RK_AUTHOR_SORT,
                              mStyle.sortAuthorByGiven()
                              ? DAO.SqlColumns.EXP_AUTHOR_SORT_FIRST_LAST
                              : DAO.SqlColumns.EXP_AUTHOR_SORT_LAST_FIRST,
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED);

                    addDomain(DOM_FK_AUTHOR, TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR),
                              BuildHelper.FLAG_GROUP);

                    addDomain(DOM_AUTHOR_IS_COMPLETE, TBL_AUTHORS.dot(DOM_AUTHOR_IS_COMPLETE),
                              BuildHelper.FLAG_GROUP);

                    break;
                }
                case BooklistGroup.RowKind.SERIES: {
                    // Save for later use
                    mSeriesGroup = (BooklistGroup.BooklistSeriesGroup) group;

                    // Do not sort by it as we'll use the OB column instead
                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP);

                    // Always sort by DOM_RK_SERIES_SORT.
                    // Sort uses the OB column.
                    addDomain(DOM_RK_SERIES_SORT, TBL_SERIES.dot(DOM_SERIES_TITLE_OB),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED);

                    // Group by id (we want the id available and there is a *chance* two
                    // series will have the same name...with bad data
                    addDomain(DOM_FK_SERIES, TBL_BOOK_SERIES.dot(DOM_FK_SERIES),
                              BuildHelper.FLAG_GROUP);

                    // The series position at the lowest level for use with the Book (not grouped)
                    addDomain(DOM_BOOK_SERIES_POSITION,
                              TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION),
                              BuildHelper.FLAG_NONE);

                    addDomain(DOM_SERIES_IS_COMPLETE,
                              TBL_SERIES.dot(DOM_SERIES_IS_COMPLETE),
                              BuildHelper.FLAG_GROUP);

                    // The series number in the base data in sorted order
                    // Allow for the possibility of 3.1, or even "3.1|Omnibus 3-10" as
                    // a series number. So we convert it to a real (aka float).
                    // This field is not displayed.
                    addDomain(DOM_BL_SERIES_NUM_FLOAT,
                              DAO.SqlColumns.EXP_SERIES_NUMBER_AS_FLOAT,
                              BuildHelper.FLAG_SORTED);

                    // The series number as a sorted field for display purposes
                    // and in case of non-numeric data.
                    addDomain(DOM_BOOK_NUM_IN_SERIES,
                              TBL_BOOK_SERIES.dot(DOM_BOOK_NUM_IN_SERIES),
                              BuildHelper.FLAG_SORTED);

                    // A counter of how many books use the series as a primary series,
                    // so we can skip some series
                    addDomain(DOM_BL_PRIMARY_SERIES_COUNT,
                              DAO.SqlColumns.EXP_PRIMARY_SERIES_COUNT_AS_BOOLEAN,
                              BuildHelper.FLAG_NONE);

                    break;
                }
                case BooklistGroup.RowKind.LOANED: {
                    // Saved for later to indicate this group was present
                    mHasLoaneeGroup = true;

                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED);

                    // A simple boolean whether a book is lend out or not.
                    addDomain(DOM_LOANEE_AS_BOOLEAN, DAO.SqlColumns.EXP_LOANEE_AS_BOOLEAN,
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED);

                    break;
                }
                case BooklistGroup.RowKind.READ_STATUS: {
                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED);

                    // The READ flag at the lowest level for use with the Book (not grouped)
                    addDomain(DOM_BOOK_READ, group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_NONE);
                    break;
                }

                case BooklistGroup.RowKind.BOOKSHELF: {
                    // Saved for later to indicate this group was present
                    mHasBookshelfGroup = true;

                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED);

                    break;
                }

                case BooklistGroup.RowKind.PUBLISHER:
                case BooklistGroup.RowKind.GENRE:
                case BooklistGroup.RowKind.LANGUAGE:
                case BooklistGroup.RowKind.LOCATION:
                case BooklistGroup.RowKind.FORMAT:
                case BooklistGroup.RowKind.COLOR:
                case BooklistGroup.RowKind.TITLE_LETTER: {
                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED);
                    break;
                }

                case BooklistGroup.RowKind.RATING: {
                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED
                              // sort with highest rated first
                              | BuildHelper.FLAG_SORT_DESCENDING);
                    break;
                }

                case BooklistGroup.RowKind.DATE_PUBLISHED_YEAR:
                case BooklistGroup.RowKind.DATE_PUBLISHED_MONTH: {
                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);

                    addDomain(DOM_BOOK_DATE_PUBLISHED, null,
                              BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);
                    break;
                }

                case BooklistGroup.RowKind.DATE_FIRST_PUB_YEAR:
                case BooklistGroup.RowKind.DATE_FIRST_PUB_MONTH: {
                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);

                    addDomain(DOM_DATE_FIRST_PUB, null,
                              BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);
                    break;
                }

                case BooklistGroup.RowKind.DATE_READ_YEAR:
                case BooklistGroup.RowKind.DATE_READ_MONTH:
                case BooklistGroup.RowKind.DATE_READ_DAY: {
                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);

                    addDomain(DOM_BOOK_READ, null,
                              BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);
                    break;
                }

                case BooklistGroup.RowKind.DATE_ACQUIRED_YEAR:
                case BooklistGroup.RowKind.DATE_ACQUIRED_MONTH:
                case BooklistGroup.RowKind.DATE_ACQUIRED_DAY: {
                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);

                    addDomain(DOM_BOOK_DATE_ACQUIRED, null,
                              BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);
                    break;
                }

                case BooklistGroup.RowKind.DATE_ADDED_YEAR:
                case BooklistGroup.RowKind.DATE_ADDED_MONTH:
                case BooklistGroup.RowKind.DATE_ADDED_DAY: {
                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);

                    addDomain(DOM_BOOK_DATE_ADDED, null,
                              BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);
                    break;
                }

                case BooklistGroup.RowKind.DATE_LAST_UPDATE_YEAR:
                case BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH:
                case BooklistGroup.RowKind.DATE_LAST_UPDATE_DAY: {
                    addDomain(group.getFormattedDomain(), group.getFormattedDomainExpression(),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);

                    addDomain(DOM_DATE_LAST_UPDATED, null,
                              BuildHelper.FLAG_SORTED
                              | BuildHelper.FLAG_SORT_DESCENDING);
                    break;
                }

                // NEWTHINGS: RowKind.ROW_KIND_x

                default:
                    throw new UnexpectedValueException(group.getKind());
            }
        }

        /**
         * Since BooklistGroup objects are processed in order, this allows us to get
         * the fields applicable to the currently processed group, including its outer groups.
         * Hence why it is cloned -- subsequent domains will modify this collection.
         *
         * @return a clone of the current-group domains.
         */
        @NonNull
        @SuppressWarnings("unchecked")
        ArrayList<DomainDefinition> getDomainsForCurrentGroup() {
            //shallow copy, is enough
            return (ArrayList<DomainDefinition>) mGroupDomains.clone();
        }

        /**
         * Get the list of domains used to sort the output.
         *
         * @return the list
         */
        @NonNull
        ArrayList<SortedDomains> getSortedDomains() {
            return mSortedDomains;
        }

        /**
         * Using the collected domain info, create the various SQL phrases used
         * to build the resulting flat list table and build the portion of the SQL
         * that does the initial table load.
         * <p>
         * 'INSERT INTO [mListTable] (mDomains) SELECT [expressions] FROM'
         * <p>
         * The from-clause, where-clause and order-by-clause are build later.
         *
         * @return BaseSql structure
         */
        @NonNull
        BaseSql build() {

            // List of column names for the INSERT INTO... clause
            StringBuilder destColumns = new StringBuilder();
            // List of expressions for the SELECT... clause.
            StringBuilder sourceColumns = new StringBuilder();

            boolean first = true;
            for (DomainDefinition domain : mDomains) {
                if (first) {
                    first = false;
                } else {
                    destColumns.append(',');
                    sourceColumns.append(',');
                }

                destColumns.append(domain.getName());
                sourceColumns.append(mExpressions.get(domain))
                             // 'AS' for SQL readability/debug only
                             .append(" AS ").append(domain.getName());
            }

            // add the root key column
            destColumns.append(',').append(DOM_BL_ROOT_KEY.getName());
            sourceColumns.append(',').append(buildRootKeyColumn());

            return new BaseSql(mDestinationTable.getName(),
                               destColumns.toString(),
                               sourceColumns.toString());
        }

        /**
         * Build the expression for the key column.
         *
         * @return column expression
         */
        private String buildRootKeyColumn() {
            StringBuilder keyColumn = new StringBuilder();

            for (BooklistGroup group : mStyle.getGroups()) {
                BooklistGroup.CompoundKey key = group.getCompoundKey();
                keyColumn.append("'/")
                         .append(key.getPrefix())
                         .append("/'||COALESCE(")
                         .append(key.getExpression())
                         // null becomes an empty String
                         .append(",'')||");

            }
            int len = keyColumn.length();
            return keyColumn.delete(len - 2, len).toString();
        }


        @Nullable
        BooklistGroup.BooklistAuthorGroup getAuthorGroup() {
            return mAuthorGroup;
        }

        @Nullable
        BooklistGroup.BooklistSeriesGroup getSeriesGroup() {
            return mSeriesGroup;
        }

        boolean hasBookshelfGroup() {
            return mHasBookshelfGroup;
        }

        boolean hasLoaneeGroup() {
            return mHasLoaneeGroup;
        }

        void setJoinWithBookshelves() {
            mHasBookshelfGroup = true;
        }

        /**
         * Should really use:.
         * enum Sort { No, Asc, Desc}
         * boolean group
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(flag = true, value = {FLAG_NONE,
                                      FLAG_GROUP,
                                      FLAG_SORTED,
                                      FLAG_SORT_DESCENDING
//                                      FLAG_KEY
        })
        @interface Flags {

        }
    }

    private static class RowStateTable
            extends TableDefinition {

        private static final String BASE_NAME =
                DBDefinitions.DB_TN_BOOK_LIST_PREFIX + "_row_state_tmp_";

        private static final String ALIAS = "bl_rs";

        private static final String STMT_UPD_NODE = ALIAS + ":updNN";
        private static final String STMT_UPD_NODES_BETWEEN = ALIAS + ":updNNBetween";
        private static final String STMT_GET_NEXT_NODE_AT_SAME_LEVEL = ALIAS + ":nextNode";

        private static final String STMT_GET_POSITION_VISIBILITY = ALIAS + ":getPosCheckVisible";
        private static final String STMT_GET_POSITION = ALIAS + ":getPos";

        private static final String STMT_COUNT_VIS_ROWS = ALIAS + ":countVisRows";

        @NonNull
        private final SynchronizedDb mSyncedDb;

        /** This is a reference only. Cleanup is done by the owner of this object. */
        @NonNull
        private final SqlStatementManager mStatementManager;

        /**
         * Constructor.
         *
         * @param id unique id to be used as suffix on the table name.
         */
        RowStateTable(@NonNull final SynchronizedDb syncedDb,
                      @NonNull final SqlStatementManager statementManager,
                      final int id) {

            super(BASE_NAME + id);
            setAlias(ALIAS);

            mSyncedDb = syncedDb;
            mStatementManager = statementManager;

            // Allow debug mode to use standard tables so we can export and inspect the content.
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_USES_STANDARD_TABLES) {
                setType(TableType.Standard);
            } else {
                setType(TableType.Temporary);
            }

            addDomains(DOM_PK_ID,
                       // FK to TMP_TBL_BOOK_LIST
                       DOM_FK_BOOK_BL_ROW_ID,
                       DOM_BL_ROOT_KEY,
                       // Node data
                       DOM_BL_NODE_LEVEL,
                       DOM_BL_NODE_KIND,
                       DOM_BL_NODE_VISIBLE,
                       DOM_BL_NODE_EXPANDED
                      );

            setPrimaryKey(DOM_PK_ID);

            // Essential for main query! If not present, will make getCount()
            // take ages because main query is a cross without index.
            addIndex(DOM_FK_BOOK_BL_ROW_ID, true, DOM_FK_BOOK_BL_ROW_ID);

            // BooklistBuilder#getPreserveNodesInsertSql()
            addIndex(DOM_BL_NODE_VISIBLE, false, DOM_BL_NODE_VISIBLE);

            addIndex("NODE_DATA",
                     false,
                     DOM_BL_ROOT_KEY,
                     DOM_BL_NODE_LEVEL,
                     DOM_BL_NODE_EXPANDED
                    );
        }

        /**
         * Given an absolute position, return the actual list position for a row taking into
         * account invisible rows.
         *
         * @param absolutePosition Abs. position to check
         *
         * @return Actual list position.
         */
        int getListPosition(final int absolutePosition) {

            final long rowId = absolutePosition + 1;

            // Check the absolute position is visible
            SynchronizedStatement stmt = mStatementManager.get(STMT_GET_POSITION_VISIBILITY);
            if (stmt == null) {
                stmt = mStatementManager.add(STMT_GET_POSITION_VISIBILITY,
                                             "SELECT " + DOM_BL_NODE_VISIBLE + " FROM " + getName()
                                             + " WHERE " + DOM_PK_ID + "=?");
            }

            boolean isVisible;
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, rowId);
                isVisible = stmt.simpleQueryForLongOrZero() == 1;
            }

            // Count the number of *visible* rows *before* the specified one.
            stmt = mStatementManager.get(STMT_GET_POSITION);
            if (stmt == null) {
                stmt = mStatementManager.add(STMT_GET_POSITION,
                                             "SELECT COUNT(*) FROM " + getName()
                                             + " WHERE " + DOM_BL_NODE_VISIBLE + "=1"
                                             + " AND " + DOM_PK_ID + "<?");
            }
            int newPos;
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, rowId);
                newPos = (int) stmt.count();
            }

            // If specified row is visible, then the position is the count,
            // otherwise the previous visible row, i.e. count-1 (or the top row)
            if (isVisible) {
                return newPos;
            } else {
                return newPos > 0 ? newPos - 1 : 0;
            }
        }

        /**
         * Get the logical count of rows.
         * <p>
         * All pseudo list cursors work with the static data in the temp table.
         * So we can use a simple query rather than scanning the entire result set.
         *
         * @return count
         */
        int countVisibleRows() {
            SynchronizedStatement stmt = mStatementManager.get(STMT_COUNT_VIS_ROWS);
            if (stmt == null) {
                stmt = mStatementManager.add(STMT_COUNT_VIS_ROWS,
                                             "SELECT COUNT(*) FROM " + getName()
                                             + " WHERE " + DOM_BL_NODE_VISIBLE + "=1");
            }

            final long t0 = System.nanoTime();
            long count;

            // no params here, so don't need synchronized... but leave as reminder
            //        synchronized (stmt) {
            count = stmt.count();
            //        }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                Log.d(TAG, "countVisibleRows"
                           + "|count=" + count
                           + "|completed in " + (System.nanoTime() - t0) + " nano");
            }
            return (int) count;
        }

        /**
         * Expand or collapse <strong>all</strong> nodes.
         * <p>
         * For topLevel == 1, we fully expand/collapse <strong>all</strong> rows/levels.
         * For a topLevel > 1, we set levels above the top-level always expanded/visible,
         * and levels below as demanded.
         *
         * @param expand   the state to apply to levels 'below' the top-level
         * @param topLevel the desired top-level which must be kept visible
         */
        void expandNodes(final boolean expand,
                         @IntRange(from = 1) final int topLevel) {
            if (topLevel == 1) {
                if (expand) {
                    // expand and show all levels
                    updateNodes(true, true, ">=", 1);
                } else {
                    // collapse level 1, but keep it visible.
                    updateNodes(false, true, "=", 1);
                    // collapse and hide all levels above 1
                    updateNodes(false, false, ">", 1);
                }
            } else /* topLevel > 1 */ {
                // always expand and show levels less then the defined top-level.
                updateNodes(true, true, "<", topLevel);

                // collapse the specified level, but keep it visible.
                updateNodes(false, true, "=", topLevel);

                // collapse and hide all levels starting from the topLevel
                updateNodes(false, false, ">", topLevel);

            }
        }

        /**
         * Expand or collapse all nodes <strong>between</strong> given rows and
         * the next row at the same level.
         *
         * @param expand   state to set
         * @param rowId    to expand/collapse
         * @param rowLevel level of the row
         */
        long expandNodes(final boolean expand,
                         final long rowId,
                         final int rowLevel) {

            // Find the next row at the same level. We need to update all nodes in between
            long endRow = findNextRowId(rowId, rowLevel);

            //TODO: optimize when expand==true -> combine the two updates

            // expand/collapse the specified row, but keep it visible.
            updateNode(rowId, expand, true);

            // Update the intervening nodes; this excludes the start and end row
            // visibility matches expansion state
            updateNodes(expand, expand, rowId, endRow);

            return endRow;
        }

        /**
         * Find the next row ('after' the given row) at the given level (or lower)
         *
         * @param startRowId from where to start looking
         * @param rowLevel   level to look for
         *
         * @return endRowId
         */
        private long findNextRowId(final long startRowId,
                                   final int rowLevel) {

            SynchronizedStatement stmt = mStatementManager.get(STMT_GET_NEXT_NODE_AT_SAME_LEVEL);
            if (stmt == null) {
                stmt = mStatementManager.add(
                        STMT_GET_NEXT_NODE_AT_SAME_LEVEL,
                        // the COALESCE(max(" + DOM_PK_ID + "),-1) prevents a SQLiteDoneException
                        "SELECT COALESCE(Max(" + DOM_PK_ID + "),-1) FROM "
                        + "(SELECT " + DOM_PK_ID + " FROM " + this.ref()
                        + " WHERE " + this.dot(DOM_PK_ID) + ">?"
                        + " AND " + this.dot(DOM_BL_NODE_LEVEL) + "<=?"
                        + " ORDER BY " + DOM_PK_ID + " LIMIT 1"
                        + ") zzz");
            }

            long nextRowId;
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, startRowId);
                stmt.bindLong(2, rowLevel);
                nextRowId = stmt.simpleQueryForLong();
            }

            // if there was no next node, use the end of the list.
            if (nextRowId < 0) {
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
            SynchronizedStatement stmt = mStatementManager.get(STMT_UPD_NODE);
            if (stmt == null) {
                stmt = mStatementManager.add(
                        STMT_UPD_NODE,
                        "UPDATE " + getName() + " SET "
                        + DOM_BL_NODE_EXPANDED + "=?" + ',' + DOM_BL_NODE_VISIBLE + "=?"
                        + " WHERE " + DOM_PK_ID + "=?");
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
                Log.d(TAG, "updateNodes"
                           + "|expand=" + expand
                           + "|visible=" + visible
                           + "|rowId=" + rowId
                           + "|rowsUpdated=" + rowsUpdated);
            }
        }

        /**
         * Update the nodes between the two given rows; excluding the start and end row.
         *
         * @param expand   state to set
         * @param visible  visibility to set
         * @param startRow between this row
         * @param endRow   and this row
         */
        private void updateNodes(final boolean expand,
                                 final boolean visible,
                                 final long startRow,
                                 final long endRow) {
            SynchronizedStatement stmt = mStatementManager.get(STMT_UPD_NODES_BETWEEN);
            if (stmt == null) {
                stmt = mStatementManager.add(
                        STMT_UPD_NODES_BETWEEN,
                        "UPDATE " + getName() + " SET "
                        + DOM_BL_NODE_EXPANDED + "=?," + DOM_BL_NODE_VISIBLE + "=?"
                        + " WHERE " + DOM_PK_ID + ">?" + " AND " + DOM_PK_ID + "<?");
            }

            int rowsUpdated;
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindBoolean(1, expand);
                stmt.bindBoolean(2, visible);

                stmt.bindLong(3, startRow);
                stmt.bindLong(4, endRow);

                rowsUpdated = stmt.executeUpdateDelete();
            }
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                Log.d(TAG, "updateNodes"
                           + "|expand=" + expand
                           + "|visible=" + visible
                           + "|startRow=" + startRow
                           + "|endRow=" + endRow
                           + "|rowsUpdated=" + rowsUpdated);
            }
        }

        /**
         * Update the nodes for the given status and level.
         *
         * @param expand       state to set
         * @param visible      visibility to set
         * @param levelOperand one of "<", "=", ">", "<=" or ">="
         * @param level        the level
         */
        private void updateNodes(final boolean expand,
                                 final boolean visible,
                                 @NonNull final String levelOperand,
                                 @IntRange(from = 1) final int level) {
            if (BuildConfig.DEBUG /* always */) {
                // developer sanity check
                if (!"< = > <= >=".contains(levelOperand)) {
                    throw new IllegalArgumentException();
                }
            }

            String sql = "UPDATE " + getName() + " SET "
                         + DOM_BL_NODE_EXPANDED + "=?," + DOM_BL_NODE_VISIBLE + "=?"
                         + " WHERE " + DOM_BL_NODE_LEVEL + levelOperand + "?";

            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                stmt.bindBoolean(1, expand);
                stmt.bindBoolean(2, visible);
                stmt.bindLong(3, level);
                int rowsUpdated = stmt.executeUpdateDelete();
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_NODE_STATE) {
                    Log.d(TAG, "updateNodes"
                               + "|expand=" + expand
                               + "|visible=" + visible
                               + "|level" + levelOperand + level
                               + "|rowsUpdated=" + rowsUpdated);
                }
            }
        }
    }
}
