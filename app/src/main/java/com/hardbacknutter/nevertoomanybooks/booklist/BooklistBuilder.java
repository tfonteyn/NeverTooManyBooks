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

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_ABSOLUTE_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_BOOK_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_EXPANDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_ROW_KIND;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_SELECTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_VISIBLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_PRIMARY_SERIES_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_REAL_ROW_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_ROOT_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_SERIES_NUM_FLOAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
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
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_SETTINGS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TMP_TBL_BOOK_LIST;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TMP_TBL_ROW_NAVIGATOR;

/**
 * Class used to build and populate temporary tables with details of a flattened book
 * list used to display books in a list control and perform operation like
 * 'expand/collapse' on pseudo nodes in the list.
 *
 * <strong>IMPORTANT:</strong> {@link #rebuild()} was giving to many problems
 * (remember it did not work in BookCatalogue 5.2.2 except in classic mode)
 * The code for it has not been removed here yet, might get revisited later.
 * <p>
 * TODO: [0123456789] replace by [0..9]
 */
public class BooklistBuilder
        implements AutoCloseable {

    /** id values for state preservation property. See {@link ListRebuildMode}. */
    public static final int PREF_LIST_REBUILD_SAVED_STATE = 0;
    public static final int PREF_LIST_REBUILD_ALWAYS_EXPANDED = 1;
    public static final int PREF_LIST_REBUILD_ALWAYS_COLLAPSED = 2;
    @SuppressWarnings("WeakerAccess")
    public static final int PREF_LIST_REBUILD_PREFERRED_STATE = 3;

    /** BookList Compatibility mode property values. See {@link CompatibilityMode}. */
    public static final int PREF_MODE_DEFAULT = 0;
    public static final int PREF_MODE_NESTED_TRIGGERS = 1;
    public static final int PREF_MODE_FLAT_TRIGGERS = 2;
    public static final int PREF_MODE_OLD_STYLE = 3;

    /** Counter for BooklistBuilder ID's. */
    @NonNull
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    /** Counter for 'flattened' book temp tables. */
    @NonNull
    private static final AtomicInteger FLAT_LIST_ID_COUNTER = new AtomicInteger();
    /** DEBUG Instance counter. */
    @NonNull
    private static final AtomicInteger DEBUG_INSTANCE_COUNTER = new AtomicInteger();
    /** static SQL. */
    private static final String DELETE_BOOK_LIST_NODE_SETTINGS_BY_BOOKSHELF =
            "DELETE FROM " + TBL_BOOK_LIST_NODE_SETTINGS + " WHERE " + DOM_FK_BOOKSHELF + "=?";

    /** Statement names for caching. */
    private static final String STMT_DELETE_LIST_NODE_SETTINGS = "DelNodeSettings";
    private static final String STMT_DELETE_LIST_NODE_SETTING = "DelNodeSetting";

    private static final String STMT_SAVE_LIST_NODE_SETTING = "SaveNodeSetting";
    private static final String STMT_SAVE_ALL_LIST_NODE_SETTINGS = "SaveAllNodeSettings";

    private static final String STMT_UPD_NAV_NODES_BETWEEN = "updNNBetween";
    private static final String STMT_UPD_NAV_NODES_FOR_LEVEL = "updNNLevel";
    private static final String STMT_UPD_NAV_NODE = "updNN";

    private static final String STMT_GET_NEXT_NODE_AT_SAME_LEVEL = "GetNextNode";
    private static final String STMT_GET_POSITION_CHECK_VISIBLE = "GetPosCheckVisible";

    private static final String STMT_BASE_BUILD = "BaseBuild";
    private static final String STMT_GET_POSITION = "GetPos";
    private static final String STMT_NAV_TABLE_INSERT = "NavTable.insert";
    private static final String STMT_NAV_IX_1 = "navIx1";
    private static final String STMT_NAV_IX_2 = "navIx2";
    private static final String STMT_IX_1 = "ix1";

    /**
     * Collection of statements created by this Builder.
     * Private to this instance, hence no need to synchronize the statements.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final SqlStatementManager mStatements;
    /** Database Access. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final DAO mDb;
    /** The underlying database. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final SynchronizedDb mSyncedDb;

    // not in use for now
    // List of columns for the group-by clause, including COLLATE clauses. Set by build() method.
    //private String mGroupColumnList;

    /** Internal ID. */
    private final int mBooklistBuilderId;
    /** Collection of 'extra' domains requested by caller. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Map<String, ExtraDomainDetails> mExtraDomains = new HashMap<>();
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Map<String, TableDefinition> mExtraJoins = new HashMap<>();
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
    /** CompatibilityMode - Preferred: true. */
    private boolean mUseTriggers;
    /** CompatibilityMode - Preferred: true. */
    private boolean mUseNestedTriggers;
    /** used in debug. */
    @SuppressWarnings("FieldNotUsedInToString")
    private boolean mDebugReferenceDecremented;

    /** Show only books on this bookshelf. */
    private Bookshelf mBookshelf;

    /** DEBUG: Indicates close() has been called. */
    private boolean mCloseWasCalled;
    /** Object used in constructing the output table. Global for {@link #rebuild()}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private SummaryBuilder mRebuildSummary;

    /**
     * Local copy of {@link DBDefinitions#TMP_TBL_BOOK_LIST}
     * appended with {@link #mBooklistBuilderId}.
     * <p>
     * ENHANCE: speculation.. use a VIEW instead ?
     * <p>
     * Reminder: this is a {@link TableDefinition.TableTypes#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private TableDefinition mListTable;
    /**
     * Local copy of the {@link DBDefinitions#TMP_TBL_ROW_NAVIGATOR},
     * appended with {@link #mBooklistBuilderId}.
     * *
     * Reminder: this is a {@link TableDefinition.TableTypes#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private TableDefinition mNavTable;

    /**
     * Constructor.
     *
     * @param style Booklist style to use
     */
    public BooklistBuilder(@NonNull final BooklistStyle style) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Logger.debugEnter(this, "BooklistBuilder",
                              "instances: " + DEBUG_INSTANCE_COUNTER.incrementAndGet());
        }
        // Allocate ID
        mBooklistBuilderId = ID_COUNTER.incrementAndGet();

        // Save the requested style
        mStyle = style;

        // Get the database and create a statements collection
        mDb = new DAO();
        mSyncedDb = mDb.getUnderlyingDatabase();
        mStatements = new SqlStatementManager(mSyncedDb);
    }

    /**
     * @return the current preferred rebuild state for the list.
     */
    @ListRebuildMode
    public static int getPreferredListRebuildState() {
        return App.getListPreference(Prefs.pk_bob_levels_rebuild_state,
                                     PREF_LIST_REBUILD_SAVED_STATE);
    }

    /**
     * Clones the table definitions and append the id to make new names in case
     * more than one view is open.
     */
    private void buildTableDefinitions() {

        mListTable = TMP_TBL_BOOK_LIST.clone();
        mListTable.setName(mListTable.getName() + '_' + mBooklistBuilderId);

        mNavTable = TMP_TBL_ROW_NAVIGATOR.clone();
        mNavTable.setName(mNavTable.getName() + '_' + mBooklistBuilderId);

        // link the two tables via the row ID
        mNavTable.addReference(mListTable, DOM_BL_REAL_ROW_ID);
    }

    /**
     * Create a flattened table of ordered book ID's based on the underlying list.
     *
     * @return the name of the created table.
     */
    @NonNull
    public String createFlattenedBooklist() {
        return FlattenedBooklist.createTable(mDb, mNavTable, mListTable,
                                             FLAT_LIST_ID_COUNTER.getAndIncrement());
    }

    /**
     * Add a domain to the resulting flattened list based on the details provided.
     *
     * @param domain           Domain to add
     * @param sourceExpression Expression to use in deriving domain value
     * @param isSorted         Indicates if it should be added to the sort key
     *
     * @return The builder (to allow chaining)
     */
    @NonNull
    @SuppressWarnings("UnusedReturnValue")
    public BooklistBuilder addExtraDomain(@NonNull final DomainDefinition domain,
                                          @Nullable final String sourceExpression,
                                          final boolean isSorted) {
        if (!mExtraDomains.containsKey(domain.getName())) {
            int flags = 0;
            if (isSorted) {
                flags |= SummaryBuilder.FLAG_SORTED;
            }
            mExtraDomains.put(domain.getName(),
                              new ExtraDomainDetails(domain, sourceExpression, flags));

        } else {
            // adding a duplicate here is a bug.
            throw new IllegalArgumentException("Domain already added: `" + domain.getName() + '`');
        }

        return this;
    }

    public BooklistBuilder requireJoin(@NonNull final TableDefinition tableDefinition) {
        if (!mExtraJoins.containsKey(tableDefinition.getName())) {
            mExtraJoins.put(tableDefinition.getName(), tableDefinition);
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

    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        String sql = "SELECT " + DBDefinitions.KEY_FK_BOOK
                     + " FROM " + mListTable + " ORDER BY " + DBDefinitions.KEY_FK_BOOK;

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
     * Drop and recreate all the data based on previous criteria.
     */
    public void rebuild() {
        mRebuildSummary.recreateTable();

        mNavTable.drop(mSyncedDb);
        mNavTable.create(mSyncedDb, true, false);

        for (SynchronizedStatement stmt : mRebuildStmts) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Logger.debug(this, "rebuild", "mRebuildStmts|" + stmt + '\n');
            }
            stmt.execute();
        }
    }

    /**
     * Clear and build the temporary list of books.
     * Criteria must be set before calling this method with one or more of the setCriteria calls.
     *
     * @param listState                State to display
     * @param previouslySelectedBookId id of book to remember, so we can scroll back to it
     */
    public void build(@BooklistBuilder.ListRebuildMode final int listState,
                      final long previouslySelectedBookId) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Logger.debugEnter(this, "build",
                              "mBooklistBuilderId=" + mBooklistBuilderId);
        }

        // We can not use triggers to fill in headings in API < 8 since
        // SQLite 3.5.9 (2008-05-14) is broken.
        // Allow for the user preferences to override in case another build is broken.
        // 2019-09-20: targeting API 23, which would be SqLite 3.8
        //ENHANCE: time to remove sqlite pre-trigger use logic ?
        initCompatibilityMode();

        // Build a sort mask based on if triggers are used;
        // we can not reverse sort if they are not used.
        final int sortDescendingMask = mUseTriggers
                                       ? SummaryBuilder.FLAG_SORT_DESCENDING
                                       : SummaryBuilder.FLAG_NONE;

        SummaryBuilder summary = new SummaryBuilder();

        try {
            final long t0 = System.nanoTime();

            // create the mListTable/mNavTable fresh.
            buildTableDefinitions();

            // Add the minimum required domains which will have special handling

            // Will use default value
            mListTable.addDomain(DOM_PK_ID);
            // Will use expression based on first group (rowKindPrefix/rowValue); determined later
            mListTable.addDomain(DOM_BL_ROOT_KEY);

            // Add the domains that have simple pre-determined expressions as sources
            summary.addDomain(DOM_BL_NODE_LEVEL, String.valueOf(mStyle.groupCount() + 1),
                              SummaryBuilder.FLAG_NONE);
            summary.addDomain(DOM_BL_NODE_ROW_KIND, "" + BooklistGroup.RowKind.BOOK,
                              SummaryBuilder.FLAG_NONE);

            summary.addDomain(DOM_FK_BOOK, TBL_BOOKS.dot(DOM_PK_ID),
                              SummaryBuilder.FLAG_NONE);
            summary.addDomain(DOM_BL_BOOK_COUNT, "1",
                              SummaryBuilder.FLAG_NONE);

            final long t1_basic_setup_done = System.nanoTime();

            final BuildInfoHolder buildInfoHolder = new BuildInfoHolder(mExtraJoins);

            for (BooklistGroup group : mStyle.getGroups()) {
                summary.addGroup(group, sortDescendingMask, buildInfoHolder);

                // Copy the current groups to this level item; this effectively accumulates
                // 'group by' domains down each level so that the top has fewest groups and
                // the bottom level has groups for all levels.
                group.setDomains(summary.cloneGroups());
            }

            final long t2_groups_processed = System.nanoTime();

            // Now we know if we have a Bookshelf group, add the Filter on it if we want one.
            if (mBookshelf.isRegularShelf()) {
                mFilters.add(() -> {
                    if (buildInfoHolder.isJoinBookshelves()) {
                        return "EXISTS(SELECT NULL FROM "
                               + TBL_BOOK_BOOKSHELF.ref() + ' '
                               + TBL_BOOK_BOOKSHELF.join(TBL_BOOKS)
                               + " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_FK_BOOKSHELF)
                               + '=' + mBookshelf.getId()
                               + ')';
                    } else {
                        return '(' + TBL_BOOKSHELF.dot(DOM_PK_ID)
                               + '=' + mBookshelf.getId() + ')';
                    }
                });
            }

            // We want the UUID for the book so we can get thumbnails
            summary.addDomain(DOM_BOOK_UUID, TBL_BOOKS.dot(DOM_BOOK_UUID),
                              SummaryBuilder.FLAG_NONE);

            // If we have a book id to remember, then add the DOM_BL_NODE_SELECTED field,
            // and setup the expression.
            if (previouslySelectedBookId != 0) {
                summary.addDomain(DOM_BL_NODE_SELECTED,
                                  TBL_BOOKS.dot(DOM_PK_ID) + '=' + previouslySelectedBookId,
                                  SummaryBuilder.FLAG_NONE);
            }

            summary.addDomain(DOM_BL_NODE_LEVEL, null, SummaryBuilder.FLAG_SORTED);

            // Finished. Ensure any caller-specified extras (e.g. title) are added at the end.
            for (ExtraDomainDetails edd : mExtraDomains.values()) {
                summary.addDomain(edd.domain, edd.sourceExpression, edd.flags);
            }

            final long t3 = System.nanoTime();

            /*
             * Build the initial insert statement components:
             */
            BooklistGroup.CompoundKey compoundKey = mStyle.getGroupAt(0).getCompoundKey();
            BaseBuildSqlComponents baseBuild = summary.buildSqlComponents(compoundKey);

            final long t4_buildSqlComponents = System.nanoTime();

            baseBuild.buildJoinTables(buildInfoHolder, mBookshelf.isRegularShelf());

            final long t5_build_join = System.nanoTime();

            baseBuild.buildWhereClause(mFilters, mStyle);

            final long t6_build_where = System.nanoTime();

            baseBuild.buildOrderBy(summary.getSortedColumns(),
                                   mSyncedDb.isCollationCaseSensitive());

            // unused
            // Process the group-by columns suitable for a group-by statement or index
//            {
//                final StringBuilder groupCols = new StringBuilder();
//                for (DomainDefinition d : summary.cloneGroups()) {
//                    groupCols.append(d.name);
//                    groupCols.append(DAO.COLLATION);
//                    groupCols.append(", ");
//                }
//                groupCols.append(DBDefinitions.DOM_BL_NODE_LEVEL);
//                //mGroupColumnList = groupCols.toString();
//            }

            final long t7_sortColumns_processed = System.nanoTime();

            mRebuildStmts.clear();

            // We are good to go.
            SyncLock txLock = mSyncedDb.beginTransaction(true);
            try {
                if (mUseTriggers) {
                    // If we are using triggers, insert them in order and rely on the
                    // triggers to build the summary rows in the correct place.
                    String tgt;
                    if (mUseNestedTriggers) {
                        // Nested triggers are compatible with Android 2.2+ and fast.
                        // (or at least relatively fast when there are a 'reasonable'
                        // number of headings to be inserted).
                        tgt = makeNestedTriggers(summary.getSortedColumns());
                    } else {
                        // Flat triggers are compatible with Android 1.6+ but slower
                        tgt = makeSingleTrigger(summary.getSortedColumns());
                    }

                    // Build the lowest level summary using our initial insert statement
                    // The triggers will do the rest.
                    String sql = baseBuild.getSql(tgt);

                    //experimental, so debug it
                    if (BuildConfig.DEBUG && !mStyle.useTaskForExtras()) {
                        Logger.debug(this, "not using a task for extras", "sql=" + sql);
                    }

                    SynchronizedStatement baseStmt = mStatements.add(STMT_BASE_BUILD, sql);
                    mRebuildStmts.add(baseStmt);
                    baseStmt.executeInsert();

                } else {
                    // eventually to be removed maybe?
                    baseBuildWithoutTriggers(baseBuild);
                }

                final long t8_BaseBuild_executed = System.nanoTime();

                mSyncedDb.analyze(mListTable);

                final long t9_table_optimized = System.nanoTime();

                // build the lookup aka navigation table
                if (mUseTriggers) {
                    // the table is ordered at insert time, so the row id *is* the order.
                    populateNavigationTable(mListTable.dot(DOM_PK_ID), listState);
                } else {
                    populateNavigationTable(baseBuild.getOrderByColumns(), listState);
                }

                final long t10_nav_table_build = System.nanoTime();

                // Create index on nav table
                SynchronizedStatement ixStmt1 =
                        mStatements.add(STMT_NAV_IX_1,
                                        "CREATE INDEX " + mNavTable + "_IX1"
                                        + " ON " + mNavTable
                                        + '(' + DOM_BL_NODE_LEVEL
                                        + ',' + DOM_BL_NODE_EXPANDED
                                        + ',' + DOM_BL_ROOT_KEY + ')');
                mRebuildStmts.add(ixStmt1);
                ixStmt1.execute();

                final long t11_nav_table_IX1_created = System.nanoTime();

                // Essential for main query! If not present, will make getCount() take
                // ages because main query is a cross without index.
                SynchronizedStatement ixStmt2 =
                        mStatements.add(STMT_NAV_IX_2,
                                        "CREATE UNIQUE INDEX " + mNavTable + "_IX2"
                                        + " ON " + mNavTable
                                        + '(' + DOM_BL_REAL_ROW_ID + ')');
                mRebuildStmts.add(ixStmt2);
                ixStmt2.execute();

                final long t12_nav_table_IX2_created = System.nanoTime();
                mSyncedDb.analyze(mNavTable);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    Logger.debug(this, "build",
                                 "\nT01: " + (t1_basic_setup_done - t0),
                                 "\nT02: " + (t2_groups_processed - t1_basic_setup_done),
                                 "\nT03: " + (t3 - t2_groups_processed),
                                 "\nT04: " + (t4_buildSqlComponents - t3),
                                 "\nT05: " + (t5_build_join - t4_buildSqlComponents),
                                 "\nT06: " + (t6_build_where - t5_build_join),
                                 "\nT07: " + (t7_sortColumns_processed - t6_build_where),
                                 "\nT08: " + (t8_BaseBuild_executed - t7_sortColumns_processed),
                                 "\nT09: " + (t9_table_optimized - t8_BaseBuild_executed),
                                 "\nT10: " + (t10_nav_table_build - t9_table_optimized),
                                 "\nT11: " + (t11_nav_table_IX1_created - t10_nav_table_build),
                                 "\nT12: " + (t12_nav_table_IX2_created
                                              - t11_nav_table_IX1_created),
                                 "\nT13: " + (System.nanoTime() - t12_nav_table_IX2_created),
                                 "\n============================",
                                 "\nTotal time: " + (System.nanoTime() - t0) + "nano");
                }
                mSyncedDb.setTransactionSuccessful();

                // keep around for {@link #rebuild()}
                mRebuildSummary = summary;

            } finally {
                mSyncedDb.endTransaction(txLock);
            }
        } finally {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Logger.debugExit(this, "build", mBooklistBuilderId);
            }
        }
    }

    /**
     * Build a collection of triggers on the list table designed to fill in the summary/header
     * records as the data records are added in sorted order.
     * <p>
     * This approach is both a performance improvement and a means to allow DESCENDING sort orders.
     * <p>
     * It is the preferred option in Android 2.2+, but there is a chance that some
     * vendor implemented a broken or old SQLite version.
     *
     * @return the mListTable table name
     */
    private String makeNestedTriggers(@NonNull final ArrayList<SortedDomainInfo> sortedColumns) {
        // Name of a table to store the snapshot of the most recent/current row headings
        final String currentTableName = mListTable + "_curr";
        // Alias for the table
        final String currentTableAlias = "l";

        // List of cols we sort by
        StringBuilder sortedCols = new StringBuilder();
        // SQL statement to update the 'current' table
        StringBuilder currInsertSql = new StringBuilder();
        // List of domain names for sorting
        Set<String> sortedDomainNames = new HashSet<>();

        // Build the 'current' header table definition and the sort column list
        for (SortedDomainInfo domainInfo : sortedColumns) {
            if (!sortedDomainNames.contains(domainInfo.domain.getName())) {
                sortedDomainNames.add(domainInfo.domain.getName());
                if (sortedCols.length() > 0) {
                    sortedCols.append(",");
                    currInsertSql.append(",");
                }
                sortedCols.append(domainInfo.domain.getName());
                currInsertSql.append("New.").append(domainInfo.domain.getName());
            }
        }

        /*
         * Create a temp table to store the most recent header details from the last row.
         * We use this in determining what needs to be inserted as header records for
         * any given row.
         *
         * This is just a simple technique to provide persistent context to the trigger.
         */
        mSyncedDb.execSQL("CREATE TEMPORARY TABLE " + currentTableName + " (" + sortedCols + ')');

        /*
         * For each grouping, starting with the lowest, build a trigger to update the next
         * level up as necessary
         */
        for (int i = mStyle.groupCount() - 1; i >= 0; i--) {
            // Get the group
            final BooklistGroup group = mStyle.getGroupAt(i);
            // Get the level number for this group
            final int levelId = i + 1;
            // Create an INSERT statement for the next level up
            StringBuilder insertSql = new StringBuilder(
                    "INSERT INTO " + mListTable
                    + " (" + DOM_BL_NODE_LEVEL
                    + ',' + DOM_BL_NODE_ROW_KIND
                    + ',' + DOM_BL_ROOT_KEY);

            // Create the VALUES statement for the next level up
            StringBuilder valuesSql = new StringBuilder(
                    " VALUES(" + levelId
                    + ',' + group.getKind()
                    + ",New." + DOM_BL_ROOT_KEY);

            // Create the conditional to detect if next level up is already defined
            // (by checking the 'current' record/table)
            StringBuilder conditionSql = new StringBuilder();
            // Update the statement components
            //noinspection ConstantConditions
            for (DomainDefinition groupDomain : group.getDomains()) {
                insertSql.append(',').append(groupDomain);
                valuesSql.append(", New.").append(groupDomain);
                // Only update the 'condition' part if it is part of the SORT list
                if (sortedDomainNames.contains(groupDomain.getName())) {
                    if (conditionSql.length() > 0) {
                        conditionSql.append(" AND ");
                    }
                    conditionSql.append("COALESCE(" + currentTableAlias + '.').append(groupDomain)
                                .append(",'')=COALESCE(New.").append(groupDomain).append(",'')")
                                .append(DAO.COLLATION);
                }
            }
            insertSql.append(")\n")
                     .append(valuesSql)
                     .append(')');

            // (re)Create the trigger
            String tgName = "header_A_tg_Level" + i;
            mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + tgName);
            String tgSql = "\nCREATE TEMPORARY TRIGGER " + tgName
                           + " BEFORE INSERT ON " + mListTable + " FOR EACH ROW"
                           + "\n WHEN New." + DOM_BL_NODE_LEVEL + '=' + (levelId + 1)
                           + " AND NOT EXISTS("
                           + /* */ "SELECT 1 FROM " + currentTableName + ' ' + currentTableAlias
                           + /* */ " WHERE " + conditionSql + ')'
                           + "\n BEGIN"
                           + "\n   " + insertSql + ';'
                           + "\n END";
            SynchronizedStatement stmt = mStatements.add(tgName, tgSql);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Logger.debug(this, "makeNestedTriggers", "add",
                             tgName, stmt.toString());
            }
            mRebuildStmts.add(stmt);
            stmt.execute();
        }

        // Create a trigger to maintain the 'current' value -- just delete and insert
        String currentValueTriggerName = mListTable + "_TG_ZZZ";
        mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + currentValueTriggerName);
        String tgSql = "\nCREATE TEMPORARY TRIGGER " + currentValueTriggerName
                       + " AFTER INSERT ON " + mListTable + " FOR EACH ROW"
                       + "\n WHEN New." + DOM_BL_NODE_LEVEL + '=' + mStyle.groupCount()
                       // + " AND NOT EXISTS("
                       // +   "SELECT 1 FROM " + currentTableName + ' ' + currentTableAlias
                       // +   " WHERE " + conditionSql + ')'
                       + "\n BEGIN"
                       + "\n   DELETE FROM " + currentTableName + ';'
                       + "\n   INSERT INTO " + currentTableName + " VALUES (" + currInsertSql + ");"
                       + "\n END";

        SynchronizedStatement stmt = mStatements.add(currentValueTriggerName, tgSql);
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Logger.debug(this, "makeNestedTriggers", "add",
                         currentValueTriggerName, stmt);
        }
        mRebuildStmts.add(stmt);
        stmt.execute();

        return mListTable.getName();
    }

    /**
     * Build a collection of triggers on the list table designed to fill in the summary/header
     * records as the data records are added in sorted order.
     * <p>
     * This approach allows DESCENDING sort orders but is slightly slower than the old-style
     * manually generated lists.
     * <p>
     * 2019-01-27: this is now working:
     * - added instance id to tgForwardName
     * - added TEMP to oneBigTrigger
     *
     * @return the view table name
     */
    @NonNull
    private String makeSingleTrigger(@NonNull final ArrayList<SortedDomainInfo> sortedColumns) {
        // Create a trigger to forward all row details to real table
        // Name of the trigger to create. 2019-01-27: added mBooklistBuilderId.
        final String tgForwardName = mListTable + "_TG_AAA" + '_' + mBooklistBuilderId;
        // Name of a table to store the snapshot of the most recent/current row headings
        final String currTblName = mListTable + "_curr";
        final String viewTblName = mListTable + "_view";

        // Build an INSERT statement to insert the entire row in the real table
        StringBuilder fullInsert = new StringBuilder("INSERT INTO " + mListTable + '(');
        StringBuilder fullValues = new StringBuilder("VALUES (");
        boolean firstCol = true;
        for (DomainDefinition d : mListTable.getDomains()) {
            if (!d.equals(DOM_PK_ID)) {
                if (firstCol) {
                    firstCol = false;
                } else {
                    fullInsert.append(", ");
                    fullValues.append(", ");
                }
                fullInsert.append(d);
                fullValues.append("New.").append(d);
            }
        }
        fullInsert.append(") ").append(fullValues).append(");");


        // We just create one big trigger
        // 2019-01-27: added 'TEMP'
        StringBuilder oneBigTrigger = new StringBuilder(
                "CREATE TEMPORARY TRIGGER " + tgForwardName
                + "\n INSTEAD OF INSERT ON " + viewTblName + " FOR EACH ROW"
                + "\n BEGIN");

        // List of columns we sort by
        StringBuilder sortedCols = new StringBuilder();
        // SQL statement to update the 'current' table
        StringBuilder currInsertSql = new StringBuilder();
        // List of domain names for sorting
        Set<String> sortedDomainNames = new HashSet<>();
        // Build the 'current' header table definition and the sort column list
        for (SortedDomainInfo domainInfo : sortedColumns) {
            if (!sortedDomainNames.contains(domainInfo.domain.getName())) {
                sortedDomainNames.add(domainInfo.domain.getName());
                if (sortedCols.length() > 0) {
                    sortedCols.append(',');
                    currInsertSql.append(',');
                }
                sortedCols.append(domainInfo.domain.getName());
                currInsertSql.append("New.").append(domainInfo.domain.getName());
            }
        }

        /*
         * Create a temp table to store the most recent header details from the last row.
         * We use this in determining what needs to be inserted as header records for any given row.
         *
         * This is just a simple technique to provide persistent context to the trigger.
         */
        mSyncedDb.execSQL("CREATE TEMPORARY TABLE " + currTblName + " (" + sortedCols + ')');
        mSyncedDb.execSQL("CREATE TEMPORARY VIEW " + viewTblName + " AS"
                          + " SELECT * FROM " + mListTable);

        //mSyncedDb.execSQL("Create Unique Index " + mListTable + "_IX_TG1 on " + mListTable +
        // "(" + DOM_BL_NODE_LEVEL + ", " + sortedCols + ", " + DOM_FK_BOOK + ")");

        // For each grouping, starting with the lowest, build a trigger to update
        // the next level up as necessary
        for (int i = 0; i < mStyle.groupCount(); i++) {
            // Get the group
            final BooklistGroup group = mStyle.getGroupAt(i);
            // Get the level number for this group
            final int levelId = i + 1;
            // Create an INSERT statement for the next level up. Not complete, more to be added.
            StringBuilder sql = new StringBuilder("INSERT INTO " + mListTable
                                                  + " (" + DOM_BL_NODE_LEVEL
                                                  + ',' + DOM_BL_NODE_ROW_KIND
                                                  + ',' + DOM_BL_ROOT_KEY);

            // Create the VALUES statement for the next level up. Not complete, more to be added.
            StringBuilder valuesSql = new StringBuilder(
                    " SELECT " + levelId + ',' + group.getKind() + ',' + "New." + DOM_BL_ROOT_KEY);

            // Create the conditional to detect if next level up is already defined
            // (by checking the 'current' record/table)
            StringBuilder conditionSql = new StringBuilder();
            // Update the statement components
            //noinspection ConstantConditions
            for (DomainDefinition d : group.getDomains()) {
                sql.append(',').append(d);
                valuesSql.append(", New.").append(d);
                // Only update the 'condition' part if it is part of the SORT list
                if (sortedDomainNames.contains(d.getName())) {
                    if (conditionSql.length() > 0) {
                        conditionSql.append(" AND ");
                    }
                    conditionSql.append("COALESCE(l.").append(d)
                                .append(",'') = COALESCE(New.").append(d)
                                .append(",'')")
                                .append(DAO.COLLATION).append('\n');
                }
            }
            //insertSql += ")\n SELECT " + valuesSql
            // + " WHERE NOT EXISTS(SELECT 1 FROM " + mListTable + " l WHERE " + conditionSql + ")";
            //tgLines[i] = insertSql;

            sql.append(")\n")
               .append(valuesSql)
               .append(" WHERE NOT EXISTS(SELECT 1 FROM ")
               .append(currTblName).append(" l WHERE ").append(conditionSql).append(")\n");

            oneBigTrigger.append("\n    ").append(sql).append(';');
        }

        // Finalize the main trigger; insert the full row and update the 'current' header
        oneBigTrigger.append("\n    ").append(fullInsert).append('\n')
                     .append("\n    DELETE FROM ").append(currTblName).append(';')
                     .append("\n    INSERT INTO ").append(currTblName)
                     .append(" VALUES (").append(currInsertSql).append(");")
                     .append("\n END");


        SynchronizedStatement stmt = mStatements.add(tgForwardName, oneBigTrigger.toString());
        mRebuildStmts.add(stmt);
        stmt.execute();

        return viewTblName;
    }

    private void baseBuildWithoutTriggers(@NonNull final BaseBuildSqlComponents sqlCmp) {

        final long[] t_style = new long[mStyle.groupCount() + 1];
        t_style[0] = System.nanoTime();

        // Without triggers we just get the base rows and add summary later
        SynchronizedStatement baseStmt =
                mStatements.add(STMT_BASE_BUILD, sqlCmp.getSqlWithoutOrderBy(mListTable.getName()));
        mRebuildStmts.add(baseStmt);
        baseStmt.executeInsert();

        // Now build each summary level query based on the prior level.
        // We build and run from the bottom up.
        int timer = 1;
        // Loop from innermost group to outermost, building summary at each level
        for (int i = mStyle.groupCount() - 1; i >= 0; i--) {
            final BooklistGroup group = mStyle.getGroupAt(i);
            final int levelId = i + 1;
            // cols is the list of column names for the 'Insert' and 'Select' parts
            StringBuilder cols = new StringBuilder();
            // collatedCols is used for the group-by
            StringBuilder collatedCols = new StringBuilder();

            // Build the column lists for this group
            //noinspection ConstantConditions
            for (DomainDefinition domain : group.getDomains()) {
                if (collatedCols.length() > 0) {
                    collatedCols.append(',');
                }
                cols.append(',').append(domain.getName());

                collatedCols.append(' ').append(domain.getName()).append(DAO.COLLATION);
            }

            // Construct the sum statement for this group
            String sql = "INSERT INTO " + mListTable
                         + " (" + DOM_BL_NODE_LEVEL
                         + ',' + DOM_BL_NODE_ROW_KIND + cols
                         + ',' + DOM_BL_ROOT_KEY + ')'
                         + " SELECT " + levelId + " AS " + DOM_BL_NODE_LEVEL
                         + ',' + group.getKind() + " AS " + DOM_BL_NODE_ROW_KIND + cols
                         + ',' + DOM_BL_ROOT_KEY
                         + " FROM " + mListTable
                         + " WHERE " + DOM_BL_NODE_LEVEL + '=' + (levelId + 1)
                         + " GROUP BY " + collatedCols + ',' + DOM_BL_ROOT_KEY
                         + DAO.COLLATION;
            //" GROUP BY " + DOM_BL_NODE_LEVEL + ", " + DOM_BL_NODE_ROW_KIND + collatedCols;

            // Save, compile and run this statement
            SynchronizedStatement stmt = mStatements.add("Level" + i, sql);
            mRebuildStmts.add(stmt);
            stmt.executeInsert();
            t_style[timer++] = System.nanoTime();
        }

        // Build an index if it will help sorting but *If* collation is case-sensitive,
        // don't bother with index, since everything is wrapped in lower().
        // ICS UNICODE: Consider adding a duplicate _OB (lower case) column
        // to the SUMMARY table. Ugh. ==> this whole "without triggers" is outdated.
        if (!mSyncedDb.isCollationCaseSensitive()) {
            String ix1Sql = sqlCmp.getIndexCreateWithoutTriggersSql(mListTable.getName());
            SynchronizedStatement stmt = mStatements.add(STMT_IX_1, ix1Sql);
            mRebuildStmts.add(stmt);
            stmt.execute();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            for (int i = 0; i < mStyle.groupCount(); i++) {
                Logger.debug(this, "baseBuildWithoutTriggers",
                             "t_style[" + i + "]: " + (t_style[i] - t_style[i - 1]));
            }
        }
    }


    /**
     * Build a lookup table to match row sort position to row ID. This is used to
     * match a specific book (or other row in result set) to a position directly
     * without having to scan the database. This is especially useful in
     * expand/collapse operations.
     *
     * @param sortExpression the ORDER BY expression
     * @param listState      Desired list state
     */
    private void populateNavigationTable(@NonNull final String sortExpression,
                                         @BooklistBuilder.ListRebuildMode final int listState) {

        mNavTable.drop(mSyncedDb);
        mNavTable.create(mSyncedDb, true, false);

        String baseSql = "INSERT INTO " + mNavTable
                         + " (" + DOM_BL_REAL_ROW_ID
                         + ',' + DOM_BL_NODE_LEVEL
                         + ',' + DOM_BL_ROOT_KEY
                         + ',' + DOM_BL_NODE_EXPANDED
                         + ',' + DOM_BL_NODE_VISIBLE
                         + ')'
                         + " SELECT " + mListTable.dot(DOM_PK_ID)
                         + ',' + mListTable.dot(DOM_BL_NODE_LEVEL)
                         + ',' + mListTable.dot(DOM_BL_ROOT_KEY)
                         + "\n";

        // SQL to rebuild with state preserved.
        // Reminder: TBL_BOOK_LIST_NODE_SETTINGS consists of:
        //       DOM_PK_ID,
        //       DOM_FK_BOOKSHELF
        //       DOM_BL_ROOT_KEY
        //       DOM_BL_NODE_ROW_KIND
        String sqlPreserved =
                baseSql
                // DOM_BL_NODE_EXPANDED
                + ",CASE"
                // All nodes present in the table will be expanded; others will be collapsed
                + " WHEN " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY)
                + /*  */ " IS NULL THEN 0 ELSE 1 END"
                + "\n"

                // DOM_BL_NODE_VISIBLE
                + ",CASE"
                // always keep level 1 visible even if such a node is not present in the table.
                + " WHEN " + DOM_BL_NODE_LEVEL + "=1 THEN 1"
                // All nodes present in the table will be visible; others will be invisible
                + " WHEN " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY)
                + /*  */ " IS NULL THEN 0 ELSE 1 END"
                + "\n"

                + " FROM " + mListTable.ref()
                + " LEFT OUTER JOIN " + TBL_BOOK_LIST_NODE_SETTINGS.ref()
                + " ON " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY)
                + /*  */ '=' + mListTable.dot(DOM_BL_ROOT_KEY)
                + " AND " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_NODE_ROW_KIND)
                + /*  */ '=' + mStyle.getGroupKindAt(0);

        // for the current bookshelf only, or don't filter if we want all books.
        if (mBookshelf.isRegularShelf()) {
            baseSql += " AND " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_FK_BOOKSHELF)
                       + '=' + mBookshelf.getId();
        }
        baseSql += " ORDER BY " + sortExpression;

        // Always save the state-preserving statement for rebuilds
        SynchronizedStatement navStmt = mStatements.add(STMT_NAV_TABLE_INSERT, sqlPreserved);
        mRebuildStmts.add(navStmt);

        int topLevel = mStyle.getTopLevel();

        // On first-time builds, get the Preferences-based list
        switch (listState) {
            case PREF_LIST_REBUILD_ALWAYS_COLLAPSED: {
                String sqlCollapse =
                        baseSql
                        // DOM_BL_NODE_VISIBLE: level 1 visible and all other nodes invisible.
                        + ",CASE WHEN " + DOM_BL_NODE_LEVEL + "=1 THEN 1 ELSE 0 END"
                        // DOM_BL_NODE_EXPANDED: all nodes collapsed
                        + ",0"
                        + "\n"
                        + " FROM " + mListTable.ref() + " ORDER BY " + sortExpression;
                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sqlCollapse)) {
                    stmt.executeInsert();
                }
                break;
            }
            case PREF_LIST_REBUILD_ALWAYS_EXPANDED: {
                String sqlExpand =
                        baseSql
                        // DOM_BL_NODE_VISIBLE: all visible
                        + ",1"
                        // DOM_BL_NODE_EXPANDED: all expanded
                        + ",1"
                        + " FROM " + mListTable.ref() + " ORDER BY " + sortExpression;
                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sqlExpand)) {
                    stmt.executeInsert();
                }
                break;
            }

            case PREF_LIST_REBUILD_SAVED_STATE:
                // Use already-defined SQL for preserve state.
                navStmt.execute();
                break;

            case PREF_LIST_REBUILD_PREFERRED_STATE:
                // Use already-defined SQL for preserve state.
                navStmt.execute();
                // but override/reset to the desired top-level.
                expandNodes(topLevel, false);
                break;

            default:
                throw new UnexpectedValueException(listState);

        }
    }


    /**
     * @return the style used by this builder.
     */
    @NonNull
    public BooklistStyle getStyle() {
        return mStyle;
    }

    /**
     * @return a list of column names that will be in the list for cursor implementations.
     */
    @NonNull
    String[] getListColumnNames() {
        // Get the domains
        List<DomainDefinition> domains = mListTable.getDomains();
        // Make the array +1 for DOM_BL_ABSOLUTE_POSITION
        String[] names = new String[domains.size() + 1];
        // Copy domains
        for (int i = 0; i < domains.size(); i++) {
            names[i] = domains.get(i).getName();
        }

        names[domains.size()] = DOM_BL_ABSOLUTE_POSITION.getName();
        return names;
    }

    /**
     * Gets a 'window' on the result set, starting at 'position' and 'size' rows.
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
                     + " (" + mNavTable.dot(DOM_PK_ID) + " - 1) AS " + DOM_BL_ABSOLUTE_POSITION
                     + " FROM " + mListTable.ref() + mListTable.join(mNavTable)
                     + " WHERE " + mNavTable.dot(DOM_BL_NODE_VISIBLE) + "=1"
                     + " ORDER BY " + mNavTable.dot(DOM_PK_ID)
                     + " LIMIT " + pageSize + " OFFSET " + position;

        return (BooklistCursor) mSyncedDb.rawQueryWithFactory(mBooklistCursorFactory, sql,
                                                              null, "");
    }

    /**
     * @return a {@link BooklistPseudoCursor} instead of a real cursor.
     */
    @NonNull
    public BooklistPseudoCursor getNewListCursor() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Logger.debugWithStackTrace(this, "getNewListCursor");
        }
        return new BooklistPseudoCursor(this);
    }

    /**
     * All pseudo list cursors work with the static data in the temp table.
     * Get the logical count of rows using a simple query rather than scanning
     * the entire result set.
     */
    int getPseudoCount() {
        return performCount(
                "SELECT COUNT(*) FROM " + mNavTable
                + " WHERE " + DOM_BL_NODE_VISIBLE + "=1");
    }

    /**
     * @return the total number of book records in the list
     */
    public int getBookCount() {
        return performCount(
                "SELECT COUNT(*) FROM " + mListTable
                + " WHERE " + DOM_BL_NODE_LEVEL + '=' + (mStyle.groupCount() + 1));
    }

    /**
     * @return the number of <strong>unique</strong> book records in the list
     */
    public int getUniqueBookCount() {
        return performCount(
                "SELECT COUNT(DISTINCT " + DOM_FK_BOOK + ") FROM " + mListTable
                + " WHERE " + DOM_BL_NODE_LEVEL + '=' + (mStyle.groupCount() + 1));
    }

    /**
     * Perform a single count query.
     *
     * @param countSql the 'count' SQL to execute
     *
     * @return the count
     */
    private int performCount(@NonNull final String countSql) {
        final long t0 = System.nanoTime();
        long count;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(countSql)) {
            count = stmt.count();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            Logger.debug(this, "performCount",
                         "countSql=" + countSql,
                         "count=" + count,
                         "completed in " + (System.nanoTime() - t0) + " nano");
        }
        return (int) count;
    }

    private void initCompatibilityMode() {
        @CompatibilityMode
        int mode = App.getListPreference(Prefs.pk_compat_booklist_mode, PREF_MODE_DEFAULT);
        // we'll always use triggers unless we're in 'ancient' mode
        mUseTriggers = mode != PREF_MODE_OLD_STYLE;

        // mUseNestedTriggers is ignored unless mUseTriggers is set to true.
        mUseNestedTriggers =
                // The default/preferred mode is to use nested triggers
                (mode == PREF_MODE_DEFAULT)
                // or if the user explicitly asked for them
                || (mode == PREF_MODE_NESTED_TRIGGERS);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Logger.debug(this, "initCompatibilityMode",
                         "\nlistMode          : " + mode,
                         "\nmUseTriggers      : " + mUseTriggers,
                         "\nmUseNestedTriggers: " + mUseNestedTriggers);
        }
    }

    /**
     * @return the number of levels in the list, including the 'base' books level
     */
    public int levels() {
        return mStyle.groupCount() + 1;
    }

    /**
     * Given an absolute position, return the actual list position for a row taking into
     * account invisible rows.
     *
     * @param absolutePosition Abs. position to check
     *
     * @return Actual list position.
     */
    public int getPosition(final int absolutePosition) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_POSITION_CHECK_VISIBLE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_POSITION_CHECK_VISIBLE,
                                   "SELECT " + DOM_BL_NODE_VISIBLE + " FROM " + mNavTable
                                   + " WHERE " + DOM_PK_ID + "=?");
        }
        // Check the absolute position is visible
        final long rowId = absolutePosition + 1;
        stmt.bindLong(1, rowId);
        boolean isVisible = stmt.simpleQueryForLongOrZero() == 1;

        stmt = mStatements.get(STMT_GET_POSITION);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_POSITION,
                                   "SELECT COUNT(*) FROM " + mNavTable
                                   + " WHERE " + DOM_BL_NODE_VISIBLE + "=1"
                                   + " AND " + DOM_PK_ID + "<?");
        }
        // Count the number of *visible* rows *before* the specified one.
        stmt.bindLong(1, rowId);
        int newPos = (int) stmt.count();
        // If specified row is visible, then the position is the count,
        // otherwise, count -1 (i.e. the previous visible row).
        if (isVisible) {
            return newPos;
        } else {
            return newPos > 0 ? newPos - 1 : 0;
        }
    }

    /**
     * Get all positions, and the row info for that position, at which the specified book appears.
     *
     * @return Array of row details, including absolute positions, visibility and level.
     * Can be empty, but never {@code null}.
     */
    @NonNull
    private ArrayList<RowInfo> getBookPositions(final long bookId) {
        String sql = "SELECT "
                     + mNavTable.dot(DOM_PK_ID)
                     + ',' + mNavTable.dot(DOM_BL_NODE_VISIBLE)
                     + ',' + mNavTable.dot(DOM_BL_NODE_LEVEL)
                     + " FROM " + mListTable + " bl " + mListTable.join(mNavTable)
                     + " WHERE " + mListTable.dot(DOM_FK_BOOK) + "=?";

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(bookId)})) {
            ArrayList<RowInfo> rows = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                int absPos = cursor.getInt(0) - 1;
                boolean visible = cursor.getInt(1) == 1;
                int level = cursor.getInt(2);
                rows.add(new RowInfo(absPos, getPosition(absPos), visible, level));
            }
            return rows;
        }
    }


    /**
     * Find the visible root node for a given absolute position and ensure it is visible.
     *
     * @param row we want to become visible
     */
    private void ensureAbsolutePositionIsVisible(@NonNull final RowInfo row) {

        // If <0 then no previous node.
        if (row.absolutePosition < 0) {
            return;
        }

        // Get the root node (level==1) 'before' the given row.
        String sql = "SELECT " + DOM_PK_ID + ',' + DOM_BL_NODE_EXPANDED
                     + " FROM " + mNavTable
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
     * @return the target rows, or {@code null} if none.
     */
    @Nullable
    public ArrayList<RowInfo> syncPreviouslySelectedBookId(final long bookId) {
        // no input, no output...
        if (bookId == 0) {
            return null;
        }

        // get all positions of the book
        ArrayList<RowInfo> rows = getBookPositions(bookId);

        if (rows.isEmpty()) {
            return null;
        }

        // First, get the ones that are currently visible...
        ArrayList<RowInfo> visibleRows = new ArrayList<>();
        for (RowInfo row : rows) {
            if (row.visible) {
                visibleRows.add(row);
            }
        }

        // If we have any visible rows, only consider those for the new position
        if (!visibleRows.isEmpty()) {
            rows = visibleRows;

        } else {
            // Make them all visible
            for (RowInfo row : rows) {
                if (!row.visible) {
                    ensureAbsolutePositionIsVisible(row);
                }
            }
            // Recalculate all positions
            for (RowInfo row : rows) {
                row.listPosition = getPosition(row.absolutePosition);
            }
        }

        // This was commented out in the original code.
//            // Find the nearest row to the recorded 'top' row.
//            int targetRow = rows.get(0).absolutePosition;
//            int minDist = Math.abs(mModel.getTopRow() - getPosition(targetRow));
//            for (int i = 1; i < rows.size(); i++) {
//                int pos = getPosition(rows.get(i).absolutePosition);
//                int dist = Math.abs(mModel.getTopRow() - pos);
//                if (dist < minDist) {
//                    targetRow = rows.get(i).absolutePosition;
//                }
//            }
//            // Make sure the target row is visible/expanded.
//            ensureAbsolutePositionIsVisible(targetRow);
//            // Now find the position it will occupy in the view
//            mTargetPos = getPosition(targetRow);

        return rows;
    }

    /**
     * Expand or collapse <strong>all</strong> nodes.
     * <p>
     * For topLevel == 1, we fully expand/collapse <strong>all</strong> rows/levels.
     * For a topLevel > 1, we set levels above the top-level always expanded/visible,
     * and levels below as demanded.
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

            if (topLevel == 1) {
                if (expand) {
                    // expand and show all levels
                    updateNavigationNodes(true, true, ">=", 1);
                } else {
                    // collapse level 1, but keep it visible.
                    updateNavigationNodes(false, true, "=", 1);
                    // collapse and hide all levels above 1
                    updateNavigationNodes(false, false, ">", 1);
                }
            } else /* topLevel > 1 */ {
                // always expand and show levels less then the defined top-level.
                updateNavigationNodes(true, true, "<", topLevel);

                // collapse the specified level, but keep it visible.
                updateNavigationNodes(false, true, "=", topLevel);

                // collapse and hide all levels starting from the topLevel
                updateNavigationNodes(false, false, ">", topLevel);

            }
            // Store the state of all nodes.
            preserveAllNodes();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                Logger.debug(this, "expandNodes",
                             (System.nanoTime() - t0) / 1_000_000);
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
     * @return {@code true} if the new state is expanded,
     * {@code false} if collapsed, or if an error occurred.
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

        //URGENT: re-introduce the desired top-level which must be kept visible
        int topLevel = mStyle.getTopLevel();

        SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            // Get the current state of the node for the given row id.
            String sql = "SELECT " + DOM_BL_NODE_LEVEL + ',' + DOM_BL_NODE_EXPANDED
                         + " FROM " + mNavTable.ref()
                         + " WHERE " + mNavTable.dot(DOM_PK_ID) + "=?";

            try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(rowId)})) {
                if (cursor.moveToFirst()) {
                    rowLevel = cursor.getInt(0);
                    rowIsExpanded = cursor.getInt(1) != 0;
                } else {
                    return false;
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

            // Find the next row at the same level
            long nextRowId = findNextRowId(rowId, rowLevel);

            //TODO: optimize when expand==true -> combine the two updates

            // Set the actual node; this node should always be visible,
            // and expanded/collapsed as demanded.
            updateNavigationNode(rowId, expand, true);

            // Update the intervening nodes; this excludes the start and end row
            // visibility matches expansion state
            updateNavigationNodes(expand, expand, rowId, nextRowId);

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

    /**
     * Find the next row ('after' the given row) at the given level.
     *
     * @param startRowId from where to start looking
     * @param rowLevel   level to look for
     *
     * @return endRowId
     */
    private long findNextRowId(final long startRowId,
                               final int rowLevel) {
        long nextRowId;
        SynchronizedStatement getNextAtSameLevelStmt =
                mStatements.get(STMT_GET_NEXT_NODE_AT_SAME_LEVEL);
        if (getNextAtSameLevelStmt == null) {
            getNextAtSameLevelStmt = mStatements.add(
                    STMT_GET_NEXT_NODE_AT_SAME_LEVEL,
                    // the COALESCE(max(" + DOM_PK_ID + "),-1) prevents a SQLiteDoneException
                    "SELECT COALESCE(Max(" + DOM_PK_ID + "),-1) FROM "
                    + "(SELECT " + DOM_PK_ID + " FROM " + mNavTable.ref()
                    + " WHERE " + mNavTable.dot(DOM_PK_ID) + ">?"
                    + " AND " + mNavTable.dot(DOM_BL_NODE_LEVEL) + "=?"
                    + " ORDER BY " + DOM_PK_ID + " LIMIT 1"
                    + ") zzz");
        }
        getNextAtSameLevelStmt.bindLong(1, startRowId);
        getNextAtSameLevelStmt.bindLong(2, rowLevel);

        nextRowId = getNextAtSameLevelStmt.simpleQueryForLong();
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
    private void updateNavigationNode(final long rowId,
                                      final boolean expand,
                                      @SuppressWarnings("SameParameterValue")
                                      final boolean visible) {
        SynchronizedStatement stmt = mStatements.get(STMT_UPD_NAV_NODE);
        if (stmt == null) {
            stmt = mStatements.add(
                    STMT_UPD_NAV_NODE,
                    "UPDATE " + mNavTable + " SET "
                    + DOM_BL_NODE_EXPANDED + "=?" + ',' + DOM_BL_NODE_VISIBLE + "=?"
                    + " WHERE " + DOM_PK_ID + "=?");
        }

        stmt.bindBoolean(1, expand);
        stmt.bindBoolean(2, visible);
        stmt.bindLong(3, rowId);
        int rowsUpdated = stmt.executeUpdateDelete();
        if (BuildConfig.DEBUG) {
            Logger.debug(this, "updateNavigationNodes",
                         "expand=" + expand,
                         "visible=" + visible,
                         "rowId=" + rowId,
                         "rowsUpdated=" + rowsUpdated);
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
    private void updateNavigationNodes(final boolean expand,
                                       final boolean visible,
                                       final long startRow,
                                       final long endRow) {
        SynchronizedStatement stmt = mStatements.get(STMT_UPD_NAV_NODES_BETWEEN);
        if (stmt == null) {
            stmt = mStatements.add(
                    STMT_UPD_NAV_NODES_BETWEEN,
                    "UPDATE " + mNavTable + " SET "
                    + DOM_BL_NODE_EXPANDED + "=?," + DOM_BL_NODE_VISIBLE + "=?"
                    + " WHERE " + DOM_PK_ID + ">?" + " AND " + DOM_PK_ID + "<?");
        }

        stmt.bindBoolean(1, expand);
        stmt.bindBoolean(2, visible);

        stmt.bindLong(3, startRow);
        stmt.bindLong(4, endRow);

        int rowsUpdated = stmt.executeUpdateDelete();
        if (BuildConfig.DEBUG) {
            Logger.debug(this, "updateNavigationNodes",
                         "expand=" + expand,
                         "visible=" + visible,
                         "startRow=" + startRow,
                         "endRow=" + endRow,
                         "rowsUpdated=" + rowsUpdated);
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
    private void updateNavigationNodes(final boolean expand,
                                       final boolean visible,
                                       @NonNull final String levelOperand,
                                       @IntRange(from = 1) final int level) {
        if (BuildConfig.DEBUG /* always */) {
            // developer sanity check
            if (!"< = > <= >=".contains(levelOperand)) {
                throw new IllegalArgumentException();
            }
        }

        String sql = "UPDATE " + mNavTable + " SET "
                     + DOM_BL_NODE_EXPANDED + "=?," + DOM_BL_NODE_VISIBLE + "=?"
                     + " WHERE " + DOM_BL_NODE_LEVEL + levelOperand + "?";

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
            stmt.bindBoolean(1, expand);
            stmt.bindBoolean(2, visible);
            stmt.bindLong(3, level);
            int rowsUpdated = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG) {
                Logger.debug(this, "updateNavigationNodes",
                             "expand=" + expand,
                             "visible=" + visible,
                             "level" + levelOperand + level,
                             "rowsUpdated=" + rowsUpdated);
            }
        }
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

            // clear all rows for the current bookshelf before we save the new values.
            stmt = mStatements.get(STMT_DELETE_LIST_NODE_SETTINGS);
            if (stmt == null) {
                stmt = mStatements.add(STMT_DELETE_LIST_NODE_SETTINGS,
                                       DELETE_BOOK_LIST_NODE_SETTINGS_BY_BOOKSHELF);
            }
            stmt.bindLong(1, mBookshelf.getId());
            int rowsDeleted = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG) {
                Logger.debug(this, "preserveAllNodes",
                             "rowsDeleted=" + rowsDeleted);
            }

            // Read all expanded nodes, and send them to the permanent table.
            //URGENT: review/test
            // We only store the bookshelfId, top-level group/kind and the composite key.
            stmt = mStatements.get(STMT_SAVE_ALL_LIST_NODE_SETTINGS);
            if (stmt == null) {
                String sql = "INSERT INTO " + TBL_BOOK_LIST_NODE_SETTINGS
                             + " (" + DOM_FK_BOOKSHELF
                             + ',' + DOM_BL_NODE_ROW_KIND
                             + ',' + DOM_BL_ROOT_KEY
                             + ')'
                             + " SELECT DISTINCT ?, ?, " + DOM_BL_ROOT_KEY + " FROM " + mNavTable
                             + " WHERE " + DOM_BL_NODE_EXPANDED + "=1";

                stmt = mStatements.add(STMT_SAVE_ALL_LIST_NODE_SETTINGS, sql);
            }

            stmt.bindLong(1, mBookshelf.getId());
            stmt.bindLong(2, mStyle.getGroupKindAt(0));
            int rowsUpdated = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG) {
                Logger.debug(this, "preserveAllNodes",
                             "rowsUpdated=" + rowsUpdated);
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
     * @param startRow between this row
     * @param endRow   and this row
     */
    private void preserveNodes(final long startRow,
                               final long endRow) {
        SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            // Clear the list of expanded nodes using the root_key from the given rows.
            SynchronizedStatement stmt = mStatements.get(STMT_DELETE_LIST_NODE_SETTING);
            if (stmt == null) {
                stmt = mStatements.add(
                        STMT_DELETE_LIST_NODE_SETTING,
                        DELETE_BOOK_LIST_NODE_SETTINGS_BY_BOOKSHELF
                        + " AND " + DOM_BL_ROOT_KEY + " IN"
                        + " (SELECT DISTINCT " + DOM_BL_ROOT_KEY + " FROM " + mNavTable
                        + " WHERE " + DOM_PK_ID + ">?" + " AND " + DOM_PK_ID + "<?)");
            }
            stmt.bindLong(1, mBookshelf.getId());

            stmt.bindLong(2, startRow);
            stmt.bindLong(3, endRow);

            stmt.executeUpdateDelete();

            // Read all expanded nodes below the given node, and send them to the permanent table.
            //URGENT: review/test
            // We only store the bookshelfId, top-level group/kind and the composite key.
            stmt = mStatements.get(STMT_SAVE_LIST_NODE_SETTING);
            if (stmt == null) {
                String sql = "INSERT INTO " + TBL_BOOK_LIST_NODE_SETTINGS
                             + " (" + DOM_FK_BOOKSHELF
                             + ',' + DOM_BL_NODE_ROW_KIND
                             + ',' + DOM_BL_ROOT_KEY
                             + ')'
                             + " SELECT DISTINCT ?,?," + DOM_BL_ROOT_KEY + " FROM " + mNavTable
                             + " WHERE " + DOM_BL_NODE_EXPANDED + "=1"
                             + " AND " + DOM_PK_ID + ">?" + " AND " + DOM_PK_ID + "<?";
                stmt = mStatements.add(STMT_SAVE_LIST_NODE_SETTING, sql);
            }

            stmt.bindLong(1, mBookshelf.getId());
            stmt.bindLong(2, mStyle.getGroupKindAt(0));

            stmt.bindLong(3, startRow);
            stmt.bindLong(4, endRow);

            int rowsUpdated = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG) {
                Logger.debug(this, "preserveNodes",
                             "startRow=" + startRow,
                             "endRow=" + endRow,
                             "rowsUpdated=" + rowsUpdated);
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
     * Close the builder.
     */
    @Override
    public void close() {
        mCloseWasCalled = true;

        if (!mStatements.isEmpty()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Logger.debug(this, "close",
                             "Has active mStatements (this is not an error): ");
                for (String name : mStatements.getNames()) {
                    Logger.debug(this, "cleanup", name);
                }
            }
            mStatements.close();
        }

        if (mNavTable != null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Logger.debug(this, "close",
                             "Dropping mNavTable (this is not an error)");
            }

            try {
                mNavTable.clear();
                mNavTable.drop(mSyncedDb);
            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e);
            }
        }

        if (mListTable != null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Logger.debug(this, "close",
                             "Dropping mListTable (this is not an error)");
            }

            try {
                mListTable.clear();
                mListTable.drop(mSyncedDb);
            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e);
            }
        }

        mDb.close();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            if (!mDebugReferenceDecremented) {
                // Only de-reference once! Paranoia ... close() might be called twice?
                Logger.debug(this, "close",
                             "instances left: " + DEBUG_INSTANCE_COUNTER.decrementAndGet());
            }
            mDebugReferenceDecremented = true;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBooklistBuilderId);
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
        return mBooklistBuilderId == that.mBooklistBuilderId;
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistBuilder{"
               + "mBooklistBuilderId=" + mBooklistBuilderId
               + ", mUseTriggers=" + mUseTriggers
               + ", mUseNestedTriggers=" + mUseNestedTriggers
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
            Logger.warn(this, "finalize", "Closing unclosed builder");
            close();
        }
        super.finalize();
    }

    public enum NodeState {
        Toggle, Expand, Collapse
    }

    /** Define the list of accepted constants and declare the annotation. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PREF_LIST_REBUILD_SAVED_STATE,
             PREF_LIST_REBUILD_ALWAYS_EXPANDED,
             PREF_LIST_REBUILD_ALWAYS_COLLAPSED,
             PREF_LIST_REBUILD_PREFERRED_STATE})
    public @interface ListRebuildMode {

    }

    /** Define the list of accepted constants and declare the annotation. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PREF_MODE_DEFAULT,
             PREF_MODE_NESTED_TRIGGERS,
             PREF_MODE_FLAT_TRIGGERS,
             PREF_MODE_OLD_STYLE})
    public @interface CompatibilityMode {

    }

    /**
     * Store and process components of the SQL required to build the list.
     */
    private static class BaseBuildSqlComponents {

        /** Columns in the current list table. */
        @NonNull
        private final String mDestinationColumns;
        /** Columns from the original tables. */
        @NonNull
        private final String mSourceColumns;
        /**
         * Expression from the original tables that represent the key for the root level group.
         * Stored in each row and used to determine the expand/collapse results.
         */
        @NonNull
        private final String mRootKeyExpression;

        /** Tables/joins. */
        private String mSourceTables;
        /** WHERE clause; includes the WHERE keyword. Alternatively, an empty string. */
        private String mWhereClause;

        /** [without triggers] List of column names for the 'ORDER BY' clause. */
        private String mOrderByColumns;
        /** [without triggers] List of column names for the 'CREATE INDEX' column list. */
        private String mOrderByColumnsForBaseBuildIndex;

        BaseBuildSqlComponents(@NonNull final String destinationColumns,
                               @NonNull final String sourceColumns,
                               @NonNull final String rootKeyExpression) {
            mDestinationColumns = destinationColumns;
            mSourceColumns = sourceColumns;
            mRootKeyExpression = rootKeyExpression;
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
         * @param buildInfoHolder   info about the joins to be made.
         * @param filterOnBookshelf whether we are filtering on a particular Bookshelf
         */
        void buildJoinTables(@NonNull final BuildInfoHolder buildInfoHolder,
                             final boolean filterOnBookshelf) {

            Joiner join;

            // If there is a bookshelf specified, start the join there.
            // Otherwise, start with the BOOKS table.
            if (buildInfoHolder.isJoinBookshelves() || filterOnBookshelf) {
                join = new Joiner(TBL_BOOKSHELF)
                        .join(TBL_BOOK_BOOKSHELF)
                        .join(TBL_BOOKS);
            } else {
                join = new Joiner(TBL_BOOKS);
            }

            // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
            if (buildInfoHolder.isJoinLoaned() || App.isUsed(DBDefinitions.KEY_LOANEE)) {
                // so get the loanee name, or a {@code null} for available books.
                join.leftOuterJoin(TBL_BOOK_LOANEE);
            }

            // Now join with author; we must specify a parent in the join, because the last table
            // joined was one of BOOKS or LOAN and we don't know which. So we explicitly use books.
            join.join(TBL_BOOKS, TBL_BOOK_AUTHOR);

            // If there is no author group, or the user only wants primary author, get primary only
            if (buildInfoHolder.authorGroup == null || !buildInfoHolder.authorGroup.showAll()) {
                join.append(" AND " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_POSITION) + "==1");
            }

            // Join with Authors to make the names available
            join.join(TBL_AUTHORS);

            // Current table will be authors, so name parent explicitly to join books->book_series.
            join.leftOuterJoin(TBL_BOOKS, TBL_BOOK_SERIES);

            // If there was no Series group, or user requests primary Series only,
            // then just get primary Series.
            if (buildInfoHolder.seriesGroup == null || !buildInfoHolder.seriesGroup.showAll()) {
                join.append(" AND " + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + "==1");
            }

            // Join with Series to get name
            join.leftOuterJoin(TBL_SERIES);

            mSourceTables = join.getSql();
        }

        /**
         * Create the WHERE clause based on all filters.
         */
        void buildWhereClause(@NonNull final ArrayList<Filter> filters,
                              @NonNull final BooklistStyle style) {
            StringBuilder where = new StringBuilder();

            // Add local Filters which are always active
            for (Filter filter : filters) {
                if (where.length() != 0) {
                    where.append(" AND ");
                }
                where.append(' ').append(filter.getExpression());
            }

            // Add BooklistStyle Filters but check if they are active
            for (Filter filter : style.getFilters()) {
                if (filter.isActive()) {
                    if (where.length() != 0) {
                        where.append(" AND ");
                    }
                    where.append(' ').append(filter.getExpression());
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
         * @param sortedColumns the list of sorted domains from the builder
         * @param collationIsCs if {@code true} then we'll adjust the case here
         */
        void buildOrderBy(@NonNull final List<SortedDomainInfo> sortedColumns,
                          final boolean collationIsCs) {
            final StringBuilder sortCols = new StringBuilder();
            final StringBuilder indexCols = new StringBuilder();

            for (SortedDomainInfo sdi : sortedColumns) {
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

            mOrderByColumns = sortCols.toString();
            mOrderByColumnsForBaseBuildIndex = indexCols.toString();
        }

        /**
         * Constructs the INSERT INTO statement for the base build using triggers.
         *
         * @param table destination table
         *
         * @return INSERT INTO statement
         */
        @NonNull
        String getSql(@NonNull final String table) {
            return "INSERT INTO " + table + '(' + mDestinationColumns + ')'
                   + " SELECT " + mSourceColumns + ',' + mRootKeyExpression
                   + " FROM " + mSourceTables + mWhereClause
                   + " ORDER BY " + mOrderByColumns;
        }

        /**
         * Constructs the INSERT INTO statement for the base build without using triggers.
         *
         * @param table destination table
         *
         * @return INSERT INTO statement
         */
        @NonNull
        String getSqlWithoutOrderBy(@NonNull final String table) {
            return "INSERT INTO " + table + '(' + mDestinationColumns + ')'
                   + " SELECT " + mSourceColumns + ',' + mRootKeyExpression
                   + " FROM " + mSourceTables + mWhereClause;
        }

        @NonNull
        String getOrderByColumns() {
            return mOrderByColumns;
        }

        /**
         * Constructs the CREATE INDEX statement for the build without using triggers.
         *
         * @param table destination table
         *
         * @return CREATE INDEX statement
         */
        @NonNull
        String getIndexCreateWithoutTriggersSql(@NonNull final String table) {
            return "CREATE INDEX " + table + "_IX1 ON " + table + '('
                   + mOrderByColumnsForBaseBuildIndex + ')';

            // Indexes that were tried. None had a substantial impact with 800 books.
//            String ix1aSql = "CREATE INDEX " + table + "_IX1a ON " + table
//                    + '(' + DOM_BL_NODE_LEVEL + ',' + mOrderByColumnsForBaseBuildIndex + ')';
//            String ix2Sql = "CREATE UNIQUE INDEX " + table + "_IX2 ON " + table
//                    + '(' + DOM_FK_BOOK + ',' + DOM_PK_ID + ')';
//            String ix3Sql = "CREATE INDEX " + table + "_IX3 ON " + table
//                    + '(' + mOrderByColumnsForBaseBuildIndex + ')';
//            String ix3aSql = "CREATE INDEX " + table + "_IX3 ON " + table
//                    + '(' + DOM_BL_NODE_LEVEL + ',' + mOrderByColumnsForBaseBuildIndex + ')';
//            String ix3bSql = "CREATE INDEX " + table + "_IX3 ON " + table
//                    + '(' + mOrderByColumnsForBaseBuildIndex + ',' + DOM_BL_NODE_LEVEL + ')';
//            String ix3cSql = "CREATE INDEX " + table + "_IX3 ON " + table
//                    + '(' + mOrderByColumnsForBaseBuildIndex
//                    + ',' + DOM_BL_ROOT_KEY + DAO.COLLATION + ')';
//            String ix3dSql = "CREATE INDEX " + table + "_IX3 ON " + table
//                    + '(' + DOM_BL_NODE_LEVEL + ',' + mOrderByColumnsForBaseBuildIndex
//                    + ',' + DOM_BL_ROOT_KEY + ')';
//            String ix3eSql = "CREATE INDEX " + table + "_IX3 ON " + table
//                    + '(' + mOrderByColumnsForBaseBuildIndex + ',' + DOM_BL_ROOT_KEY
//                    + ',' + DOM_BL_NODE_LEVEL + ')';
//            String ix4Sql = "CREATE INDEX " + table + "_IX4 ON " + table
//                    + '(' + DOM_BL_NODE_LEVEL + ',' + DOM_BL_NODE_EXPANDED
//                    + ',' + DOM_BL_ROOT_KEY + ')';
        }
    }

    /**
     * A data class containing details of a single row.
     */
    public static class RowInfo {

        public final int absolutePosition;
        @IntRange(from = 1)
        public final int level;
        public final boolean visible;
        public int listPosition;

        RowInfo(final int absolutePosition,
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
     * A data class used to pass around multiple variables.
     * Allowed us to break up the big 'build' method as it existed in the original code.
     */
    private static class BuildInfoHolder {

        /** Will be set to appropriate Group if an Author group exists in style. */
        BooklistGroup.BooklistAuthorGroup authorGroup;
        /** Will be set to appropriate Group if a Series group exists in style. */
        BooklistGroup.BooklistSeriesGroup seriesGroup;

        /** Will be set to {@code true} if a LOANED group exists in style. */
        private boolean mJoinLoaned;
        /** Will be set to {@code true} if a BOOKSHELF group exists in style. */
        private boolean mJoinBookshelves;

        BuildInfoHolder(@NonNull final Map<String, TableDefinition> extraJoins) {
            mJoinBookshelves = extraJoins.containsKey(TBL_BOOKSHELF.getName());
            mJoinLoaned = extraJoins.containsKey(TBL_BOOK_LOANEE.getName());
        }

        void setJoinBookshelves() {
            mJoinBookshelves = true;
        }

        boolean isJoinBookshelves() {
            return mJoinBookshelves;
        }

        void setJoinLoaned() {
            mJoinLoaned = true;
        }

        boolean isJoinLoaned() {
            return mJoinLoaned;
        }
    }

    /**
     * A data class for domain + desc/asc sorting flag.
     */
    private static class SortedDomainInfo {

        @NonNull
        final DomainDefinition domain;
        final boolean isDescending;

        SortedDomainInfo(@NonNull final DomainDefinition domain,
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
        final int flags;

        ExtraDomainDetails(@NonNull final DomainDefinition domain,
                           @Nullable final String sourceExpression,
                           final int flags) {
            this.domain = domain;
            this.sourceExpression = sourceExpression;
            this.flags = flags;
        }
    }

    /**
     * Accumulate data for the build() method.
     */
    private class SummaryBuilder {

        /** Flag (bitmask) indicating added domain has no special properties. */
        static final int FLAG_NONE = 0;
        /** Flag indicating domain should be added to the GROUP BY clause. */
        static final int FLAG_GROUPED = 1;
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

        /** Domains required in output table. */
        private final ArrayList<DomainDefinition> mDomains = new ArrayList<>();
        /** Mapping from Domain to source Expression. */
        private final Map<DomainDefinition, String> mExpressions = new HashMap<>();

        /** Domains that are GROUPED. */
        private final ArrayList<DomainDefinition> mGroupedDomains = new ArrayList<>();

        // Not currently used.
        ///** Domains that form part of accumulated unique key. */
        //private List<DomainDefinition> mKeys = new ArrayList<DomainDefinition>();

        /**
         * Domains that form part of the sort key.
         * These are typically a reduced set of the GROUP domains since the group domains
         * may contain more than just the key
         */
        private final ArrayList<SortedDomainInfo> mSortedColumns = new ArrayList<>();
        /** The set is used as a simple mechanism to prevent duplicate domains. */
        private final Set<DomainDefinition> mSortedColumnsSet = new HashSet<>();

        private SummaryBuilder() {
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
                       final int flags) {
            // Add to various collections. We use a map to improve lookups and ArrayLists
            // so we can preserve order. Order preservation makes reading the SQL easier
            // but is unimportant for code correctness.

            // Add to table
            boolean added = mListTable.addDomain(domain);
            if (!added) {
                // The domain was already present, do a sanity check!
                String expression = mExpressions.get(domain);
                if (Objects.equals(sourceExpression, expression)) {
                    // same expression, we do NOT want to add it.
                    // This is NOT a bug, although one could argue it's an efficiency issue.
                    if (BuildConfig.DEBUG /* always */) {
                        Logger.warnWithStackTrace(this, "duplicate domain/expression",
                                                  "domain.name=" + domain.getName());
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
            if ((flags & FLAG_GROUPED) != 0) {
                mGroupedDomains.add(domain);
            }

            if ((flags & FLAG_SORTED) != 0 && !mSortedColumnsSet.contains(domain)) {
                boolean isDesc = (flags & FLAG_SORT_DESCENDING) != 0;
                SortedDomainInfo sdi = new SortedDomainInfo(domain, isDesc);
                mSortedColumns.add(sdi);
                mSortedColumnsSet.add(domain);
            }

            // Not currently used
            //if ((flags & FLAG_KEY) != 0) {
            //    mKeys.add(domain);
            //}
        }

        /**
         * Build each kind group.
         * <p>
         * ****************************************************************************************
         * <b>IMPORTANT:</b> for each kind, the FIRST SORTED AND GROUPED domain should be the one
         * that will be displayed at that level in the UI.
         * ****************************************************************************************
         */
        void addGroup(@NonNull final BooklistGroup group,
                      final int sortDescendingMask,
                      @NonNull final BuildInfoHolder /* in/out */ buildInfoHolder) {

            switch (group.getKind()) {

                case BooklistGroup.RowKind.BOOK:
                    // do nothing.
                    break;

                case BooklistGroup.RowKind.AUTHOR: {
                    // Save this for later use
                    buildInfoHolder.authorGroup = (BooklistGroup.BooklistAuthorGroup) group;

                    // Always group & sort by DOM_RK_AUTHOR_SORT and user preference order; see #696
                    // The expression uses the OB column.
                    addDomain(DOM_RK_AUTHOR_SORT,
                              mStyle.sortAuthorByGiven()
                              ? DAO.SqlColumns.EXP_AUTHOR_SORT_FIRST_LAST
                              : DAO.SqlColumns.EXP_AUTHOR_SORT_LAST_FIRST,
                              SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);

                    // Add the 'formatted' field of the requested type for displaying.
                    addDomain(DOM_AUTHOR_FORMATTED,
                              buildInfoHolder.authorGroup.showAuthorGivenNameFirst()
                              ? DAO.SqlColumns.EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY
                              : DAO.SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN,
                              SummaryBuilder.FLAG_GROUPED);

                    // We also want the ID
                    addDomain(DOM_FK_AUTHOR, TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR),
                              SummaryBuilder.FLAG_GROUPED);

                    // we want the isComplete flag
                    addDomain(DOM_AUTHOR_IS_COMPLETE, TBL_AUTHORS.dot(DOM_AUTHOR_IS_COMPLETE),
                              SummaryBuilder.FLAG_GROUPED);

                    break;
                }
                case BooklistGroup.RowKind.SERIES: {
                    // Save this for later use
                    buildInfoHolder.seriesGroup = (BooklistGroup.BooklistSeriesGroup) group;

                    // Add the 'formatted' field of the requested type for displaying,
                    // but do not sort by it
                    addDomain(group.getDisplayDomain(), group.getSourceExpression(),
                              SummaryBuilder.FLAG_GROUPED);

                    // Group and sort uses the OB column.
                    addDomain(DOM_RK_SERIES_SORT, TBL_SERIES.dot(DOM_SERIES_TITLE_OB),
                              SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);

                    // Group by id (we want the id available and there is a *chance* two
                    // series will have the same name...with bad data
                    addDomain(DOM_FK_SERIES, TBL_BOOK_SERIES.dot(DOM_FK_SERIES),
                              SummaryBuilder.FLAG_GROUPED);

                    // We want the series position in the base data
                    addDomain(DOM_BOOK_SERIES_POSITION,
                              TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION),
                              SummaryBuilder.FLAG_NONE);

                    // we want the isComplete flag
                    addDomain(DOM_SERIES_IS_COMPLETE, TBL_SERIES.dot(DOM_SERIES_IS_COMPLETE),
                              SummaryBuilder.FLAG_GROUPED);

                    // We want the series number in the base data in sorted order
                    // Allow for the possibility of 3.1, or even "3.1|Omnibus 3-10" as
                    // a series number. So we convert it to a real (aka float).
                    // This field is not displayed.
                    addDomain(DOM_BL_SERIES_NUM_FLOAT,
                              DAO.SqlColumns.EXP_SERIES_NUMBER_AS_FLOAT,
                              SummaryBuilder.FLAG_SORTED);

                    // We also add the series number as a sorted field for display purposes
                    // and in case of non-numeric data.
                    addDomain(DOM_BOOK_NUM_IN_SERIES, TBL_BOOK_SERIES.dot(DOM_BOOK_NUM_IN_SERIES),
                              SummaryBuilder.FLAG_SORTED);

                    // We want a counter of how many books use the series as a primary series,
                    // so we can skip some series
                    addDomain(DOM_BL_PRIMARY_SERIES_COUNT,
                              DAO.SqlColumns.EXP_PRIMARY_SERIES_COUNT_AS_BOOLEAN,
                              SummaryBuilder.FLAG_NONE);
                    break;
                }
                case BooklistGroup.RowKind.LOANED: {
                    // Saved for later to indicate group was present
                    buildInfoHolder.setJoinLoaned();

                    addDomain(group.getDisplayDomain(), group.getSourceExpression(),
                              SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);

                    addDomain(DOM_LOANEE_AS_BOOLEAN, DAO.SqlColumns.EXP_LOANEE_AS_BOOLEAN,
                              SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    break;
                }
                case BooklistGroup.RowKind.READ_STATUS: {
                    // Define how the new field is retrieved and sorted/grouped
                    addDomain(group.getDisplayDomain(), group.getSourceExpression(),
                              SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);

                    // We want the READ flag at the lowest level only, don't group.
                    addDomain(DOM_BOOK_READ, group.getSourceExpression(),
                              SummaryBuilder.FLAG_NONE);
                    break;
                }
                case BooklistGroup.RowKind.RATING: {
                    // sorting should be descending.
                    addDomain(group.getDisplayDomain(), group.getSourceExpression(),
                              SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                              | sortDescendingMask);
                    break;
                }

                case BooklistGroup.RowKind.BOOKSHELF:
                case BooklistGroup.RowKind.PUBLISHER:
                case BooklistGroup.RowKind.GENRE:
                case BooklistGroup.RowKind.LANGUAGE:
                case BooklistGroup.RowKind.LOCATION:
                case BooklistGroup.RowKind.FORMAT:
                case BooklistGroup.RowKind.TITLE_LETTER: {
                    addDomain(group.getDisplayDomain(), group.getSourceExpression(),
                              SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    break;
                }

                // ----------------------------------
                // Check with original code what this quote really means.
                // It was on most, but not all date rows:
                // TODO: Handle 'DESCENDING'. Requires the navigator construction to use
                // max/min for non-grouped domains that appear in sub-levels based on desc/asc.
                // We don't use DESCENDING sort yet because the 'header' ends up below
                // the detail rows in the flattened table.
                // ----------------------------------

                // Also: sortDescendingMask: original code had it added to "date added" and
                // "date last updated" but not to others.
                case BooklistGroup.RowKind.DATE_PUBLISHED_YEAR:
                case BooklistGroup.RowKind.DATE_PUBLISHED_MONTH:
                case BooklistGroup.RowKind.DATE_FIRST_PUBLICATION_YEAR:
                case BooklistGroup.RowKind.DATE_FIRST_PUBLICATION_MONTH:

                case BooklistGroup.RowKind.DATE_READ_YEAR:
                case BooklistGroup.RowKind.DATE_READ_MONTH:
                case BooklistGroup.RowKind.DATE_READ_DAY:

                case BooklistGroup.RowKind.DATE_ACQUIRED_YEAR:
                case BooklistGroup.RowKind.DATE_ACQUIRED_MONTH:
                case BooklistGroup.RowKind.DATE_ACQUIRED_DAY:

                case BooklistGroup.RowKind.DATE_ADDED_YEAR:
                case BooklistGroup.RowKind.DATE_ADDED_MONTH:
                case BooklistGroup.RowKind.DATE_ADDED_DAY: {
                    addDomain(group.getDisplayDomain(), group.getSourceExpression(),
                              SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    // | sortDescendingMask);
                    break;
                }

                case BooklistGroup.RowKind.DATE_LAST_UPDATE_YEAR:
                case BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH:
                case BooklistGroup.RowKind.DATE_LAST_UPDATE_DAY: {
                    addDomain(group.getDisplayDomain(), group.getSourceExpression(),
                              SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                              | sortDescendingMask);

                    addDomain(DOM_DATE_LAST_UPDATED, null,
                              SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                    break;
                }

                // NEWTHINGS: RowKind.ROW_KIND_x

                default:
                    throw new UnexpectedValueException(group.getKind());
            }
        }

        /**
         * Since BooklistGroup objects are processed in order, this allows us to get
         * the GROUP-BY fields applicable to the currently processed group, including all
         * outer groups.
         * Hence why it is cloned -- subsequent domains will modify this collection.
         *
         * @return a clone of the CURRENT domains.
         */
        @NonNull
        @SuppressWarnings("unchecked")
        ArrayList<DomainDefinition> cloneGroups() {
            //shallow copy, is enough
            return (ArrayList<DomainDefinition>) mGroupedDomains.clone();
        }

        /**
         * Get the list of columns used to sort the output.
         *
         * @return the list
         */
        @NonNull
        ArrayList<SortedDomainInfo> getSortedColumns() {
            return mSortedColumns;
        }

        /**
         * Drop and recreate the underlying temp table.
         */
        void recreateTable() {
            final long t0 = System.nanoTime();
            mListTable.drop(mSyncedDb);
            final long t1 = System.nanoTime();
            //has to be withConstraints==false
            mListTable.create(mSyncedDb, false);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                Logger.debug(this, "recreateTable",
                             "Drop   = " + (t1 - t0) + "nano",
                             "Create = " + (System.nanoTime() - t1) + "nano");
            }
        }

        /**
         * Using the collected domain info, create the various SQL phrases used
         * to build the resulting flat list table and build the portion of the SQL
         * that does the initial table load.
         * <p>
         * 'INSERT INTO [mListTable] (mDomains) SELECT [expressions] FROM'
         * <p>
         * The from-tables, where-clause, order-by is build later.
         *
         * @param rootKey The key for the root level group. Stored in each row and used
         *                to determine the expand/collapse results.
         *
         * @return BaseBuildSqlComponents structure
         */
        @NonNull
        BaseBuildSqlComponents buildSqlComponents(
                @NonNull final BooklistGroup.CompoundKey rootKey) {
            // Rebuild the data table
            recreateTable();
            // List of column names for the INSERT INTO... clause
            StringBuilder destColumns = new StringBuilder();
            // List of expressions for the SELECT... clause.
            StringBuilder sourceColumns = new StringBuilder();

            // Build the lists.
            for (int i = 0; i < mDomains.size(); i++) {
                if (i > 0) {
                    destColumns.append(',');
                    sourceColumns.append(',');
                }

                destColumns.append(mDomains.get(i).getName());
                sourceColumns.append(mExpressions.get(mDomains.get(i)));
                // This is not strictly necessary, but the SQL is more readable and easier to debug.
                sourceColumns.append(" AS ").append(mDomains.get(i).getName());
            }

            // Build the expression for the root key.
            StringBuilder keyColumns = new StringBuilder('\'' + rootKey.getPrefix());
            for (DomainDefinition domain : rootKey.getDomains()) {
                keyColumns.append("/' || "
                                  + "COALESCE(").append(mExpressions.get(domain)).append(",'')");
            }

            return new BaseBuildSqlComponents(destColumns + "," + DOM_BL_ROOT_KEY,
                                              sourceColumns.toString(),
                                              keyColumns.toString());
        }
    }
}
