/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.booklist;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDoneException;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertomanybooks.booklist.filters.ListOfValuesFilter;
import com.hardbacknutter.nevertomanybooks.booklist.filters.WildcardFilter;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertomanybooks.database.Joiner;
import com.hardbacknutter.nevertomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertomanybooks.database.cursors.BooklistCursor;
import com.hardbacknutter.nevertomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertomanybooks.database.definitions.TableInfo;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.settings.Prefs;
import com.hardbacknutter.nevertomanybooks.utils.IllegalTypeException;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertomanybooks.viewmodels.BooksOnBookshelfModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_AUTHOR_SORT;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BL_ABSOLUTE_POSITION;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BL_BOOK_COUNT;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BL_NODE_EXPANDED;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BL_NODE_ROW_KIND;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BL_NODE_SELECTED;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BL_NODE_VISIBLE;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BL_PRIMARY_SERIES_COUNT;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BL_REAL_ROW_ID;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BL_ROOT_KEY;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BL_SERIES_NUM_FLOAT;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOKSHELF;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ADDED;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_RATING;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_READ_END;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_BOOK_UUID;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_DATE_LAST_UPDATED;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_FK_BOOKSHELF;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_LOANEE;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_LOANEE_AS_BOOLEAN;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_SERIES_SORT;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_SERIES_TITLE;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.DOM_TITLE_OB;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.TBL_BOOK_LIST;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_SETTINGS;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.TBL_ROW_NAVIGATOR;
import static com.hardbacknutter.nevertomanybooks.database.DBDefinitions.TBL_SERIES;

/**
 * Class used to build and populate temporary tables with details of a flattened book
 * list used to display books in a list control and perform operation like
 * 'expand/collapse' on pseudo nodes in the list.
 * <p>
 * TODO: [0123456789] replace by [0..9]
 *
 * @author Philip Warner
 */
public class BooklistBuilder
        implements AutoCloseable {

    /** ID values for state preservation property. */
    public static final int PREF_LIST_REBUILD_STATE_PRESERVED = 0;
    public static final int PREF_LIST_REBUILD_ALWAYS_EXPANDED = 1;
    public static final int PREF_LIST_REBUILD_ALWAYS_COLLAPSED = 2;

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
    private static final String DELETE_BOOK_LIST_NODE_SETTINGS_BY_KIND =
            "DELETE FROM " + TBL_BOOK_LIST_NODE_SETTINGS + " WHERE " + DOM_BL_NODE_ROW_KIND + "=?";

    /** Statement names for caching. */
    private static final String STMT_GET_NODE_LEVEL = "GetNodeLevel";
    private static final String STMT_GET_NEXT_AT_SAME_LEVEL = "GetNextAtSameLevel";
    private static final String STMT_SHOW = "Show";
    private static final String STMT_EXPAND = "Expand";
    private static final String STMT_GET_NODE_ROOT = "GetNodeRoot";
    private static final String STMT_GET_POSITION_CHECK_VISIBLE = "GetPosCheckVisible";
    private static final String STMT_SAVE_LIST_NODE_SETTING = "SaveNodeSetting";
    private static final String STMT_SAVE_ALL_LIST_NODE_SETTINGS = "SaveAllNodeSettings";
    private static final String STMT_DELETE_LIST_NODE_SETTING = "DelNodeSetting";
    private static final String STMT_BASE_BUILD = "BaseBuild";
    private static final String STMT_DELETE_LIST_NODE_SETTINGS = "DelNodeSettings";
    private static final String STMT_GET_POSITION = "GetPos";
    private static final String STMT_NAV_TABLE_INSERT = "NavTable.insert";
    private static final String STMT_NAV_IX_1 = "navIx1";
    private static final String STMT_NAV_IX_2 = "navIx2";
    private static final String STMT_IX_1 = "ix1";

    // not in use for now
    // List of columns for the group-by clause, including COLLATE clauses. Set by build() method.
    //private String mGroupColumnList;

    /**
     * Collection of statements created by this Builder.
     * Private to this instance, hence no need to synchronize the statements.
     */
    @NonNull
    private final SqlStatementManager mStatements;

    /** Database access. */
    @NonNull
    private final DAO mDb;

    /** The underlying database. */
    @NonNull
    private final SynchronizedDb mSyncedDb;

    /** Internal ID. */
    private final int mBooklistBuilderId;
    /** Collection of 'extra' domains requested by caller. */
    @NonNull
    private final Map<String, ExtraDomainDetails> mExtraDomains = new HashMap<>();
    @NonNull
    private final Map<String, TableDefinition> mExtraJoins = new HashMap<>();

    /** Style to use in building the list. */
    @NonNull
    private final BooklistStyle mStyle;

    /**
     * Instance-based cursor factory so that the builder can be associated with the cursor
     * and the rowView. We could probably send less context, but in the first instance this
     * guarantees we get all the info we need downstream.
     */
    private final CursorFactory mBooklistCursorFactory = (db, masterQuery, editTable, query) ->
            new BooklistCursor(masterQuery, editTable, query, DAO.getSynchronizer(),
                    BooklistBuilder.this);

    /** The word 'UNKNOWN', used for year/month if those are (doh) unknown. */
    private final String mUnknown;
    /** the list of Filters. */
    private final ArrayList<Filter> mFilters = new ArrayList<>();
    /**
     * Collection of statements used to build the data tables.
     * <p>
     * Needs to be re-usable WITHOUT parameter binding.
     */
    private final ArrayList<SynchronizedStatement> mRebuildStmts = new ArrayList<>();
    /** used in debug. */
    private boolean mDebugReferenceDecremented;
    /**
     * Local copy of the {@link DBDefinitions#TBL_BOOK_LIST} table definition,
     * 'book_list_tmp' but renamed by adding the instance number to the end to match this instance.
     * <p>
     * The builder will create the needed columns (domain) on the fly
     * and create INSERT statements to build the content of this table.
     * <p>
     * ENHANCE: speculation.. use a VIEW instead ?
     */
    private TableDefinition mListTable;
    /**
     * Local copy of the {@link DBDefinitions#TBL_ROW_NAVIGATOR},
     * renamed to match this instance.
     */
    private TableDefinition mNavTable;
    /** Object used in constructing the output table. */
    private SummaryBuilder mSummary;

    /** A bookshelf id to use as a filter; i.e. show only books on this bookshelf. */
    private long mFilterOnBookshelfId;
    /** DEBUG: Indicates close() has been called. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     *
     * @param context Current context for accessing resources.
     * @param style     Book list style to use
     */
    public BooklistBuilder(@NonNull final Context context,
                           @NonNull final BooklistStyle style) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
            Logger.debugEnter(this, "BooklistBuilder",
                    "instances: " + DEBUG_INSTANCE_COUNTER.incrementAndGet());
        }
        // Allocate ID
        mBooklistBuilderId = ID_COUNTER.incrementAndGet();

        mUnknown = context.getString(R.string.unknown).toUpperCase(LocaleUtils.from(context));

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
    public static int getPreferredListRebuildState() {
        return App.getListPreference(Prefs.pk_bob_list_state, PREF_LIST_REBUILD_STATE_PRESERVED);
    }

    public static boolean imagesAreGeneratedInBackground(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Prefs.pk_bob_thumbnails_generating_mode, false);
    }

    /**
     * Only valid if {@link #imagesAreGeneratedInBackground} returns {@code true}.
     */
    public static boolean imagesAreCached(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Prefs.pk_bob_thumbnails_cache_resized, false);
    }

    /**
     * Clones the table definitions and append the ID to make new names in case
     * more than one view is open.
     */
    private void buildTableDefinitions() {

        mListTable = TBL_BOOK_LIST.clone();
        mListTable.setName(mListTable.getName() + '_' + mBooklistBuilderId);

        mNavTable = TBL_ROW_NAVIGATOR.clone();
        mNavTable.setName(mNavTable.getName() + '_' + mBooklistBuilderId);

        // link the two tables via the row id
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
    public BooklistBuilder requireDomain(@NonNull final DomainDefinition domain,
                                         @Nullable final String sourceExpression,
                                         final boolean isSorted) {
        if (!mExtraDomains.containsKey(domain.name)) {
            int flags = 0;
            if (isSorted) {
                flags |= SummaryBuilder.FLAG_SORTED;
            }
            mExtraDomains.put(domain.name, new ExtraDomainDetails(domain, sourceExpression, flags));

        } else {
            Logger.warnWithStackTrace(this, "requireDomain",
                    "Domain already added: `" + domain.name + '`');

//            throw new IllegalStateException("Domain already added: `" + domain.name + '`');
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
     * @param author   Author-related keywords to find
     * @param title    Title-related keywords to find
     * @param keywords Keywords to find anywhere in book
     */
    public void setFilter(@Nullable final String author,
                          @Nullable final String title,
                          @Nullable final String keywords) {
        if (keywords != null && !keywords.trim().isEmpty()) {
            mFilters.add(() -> '(' + TBL_BOOKS.dot(DOM_PK_ID)
                    + " IN (" + DAO.getFtsSearchSQL(author, title, keywords) + ")");
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
     * Set the filter for only books in named series with added wildcards.
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param filter the series to limit the search for.
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
     * @param bookshelfId only books on this shelf, or 0 for all shelves.
     */
    public void setFilterOnBookshelfId(final long bookshelfId) {
        mFilterOnBookshelfId = bookshelfId;
    }

    /**
     * The where clause will add a "AND books._id IN (list)".
     * Be careful when combining with other criteria as you might get less then expected
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
        mSummary.recreateTable();

        mNavTable.drop(mSyncedDb);
        mNavTable.create(mSyncedDb, true, false);

        for (SynchronizedStatement stmt : mRebuildStmts) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                Logger.debug(this, "rebuild", "mRebuildStmts|" + stmt + '\n');
            }
            stmt.execute();
        }
    }

    /**
     * Clear and then build the temporary list of books.
     * Criteria must be set before calling this method with one or more of the setCriteria calls.
     *
     * @param preferredState           State to display: expanded, collapsed or remembered
     * @param previouslySelectedBookId ID of book to remember, so we can scroll back to it
     */
    public void build(final int preferredState,
                      final long previouslySelectedBookId) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
            Logger.debugEnter(this, "build",
                    "mBooklistBuilderId=" + mBooklistBuilderId);
        }

        // We can not use triggers to fill in headings in API < 8 since
        // SQLite 3.5.9 (2008-05-14) is broken.
        // Allow for the user preferences to override in case another build is broken.
        // 2019-01-20: v200 is targeting API 21, which would be SqLite 3.8 (2013-08-26)
        //ENHANCE: time to remove sqlite pre-trigger use logic ?
        final CompatibilityMode listMode = CompatibilityMode.get();

        // Build a sort mask based on if triggers are used;
        // we can not reverse sort if they are not used.
        final int sortDescendingMask = listMode.useTriggers
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
            // Will use expression based on first group; determined later
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
            // process each group/RowKind
            for (BooklistGroup group : mStyle.getGroups()) {
                summary.addGroup(group, sortDescendingMask, buildInfoHolder);

                // Copy the current groups to this level item; this effectively accumulates
                // 'group by' domains down each level so that the top has fewest groups and
                // the bottom level has groups for all levels.
                group.setDomains(summary.cloneGroups());
            }

            final long t2_groups_processed = System.nanoTime();

            // Now we know if we have a Bookshelf group, add the Filter on it if we want one.
            if (mFilterOnBookshelfId > 0) {
                mFilters.add(() -> {
                    if (buildInfoHolder.isJoinBookshelves()) {
                        return "EXISTS(SELECT NULL FROM "
                                + TBL_BOOK_BOOKSHELF.ref() + ' '
                                + TBL_BOOK_BOOKSHELF.join(TBL_BOOKS)
                                + " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_FK_BOOKSHELF)
                                + '=' + mFilterOnBookshelfId
                                + ')';
                    } else {
                        return '(' + TBL_BOOKSHELF.dot(DOM_PK_ID)
                                + '=' + mFilterOnBookshelfId + ')';
                    }
                });
            }

            // We want the UUID for the book so we can get thumbnails
            summary.addDomain(DOM_BOOK_UUID, TBL_BOOKS.dot(DOM_BOOK_UUID),
                    SummaryBuilder.FLAG_NONE);

            // If we have a book ID to remember, then add the DOM_BL_NODE_SELECTED field,
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

            baseBuild.buildJoinTables(buildInfoHolder, mFilterOnBookshelfId > 0);

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
                if (listMode.useTriggers) {
                    // If we are using triggers, then we insert them in order and rely on the
                    // triggers to build the summary rows in the correct place.
                    String tgt;
                    if (listMode.nestedTriggers) {
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
                    if (!BooksOnBookshelfModel.TMP_USE_BOB_EXTRAS_TASK) {
                        Logger.debug(this, "", "sql=" + sql);
                    }

                    SynchronizedStatement baseStmt = mStatements.add(STMT_BASE_BUILD, sql);
                    mRebuildStmts.add(baseStmt);
                    baseStmt.executeInsert();

                } else {
                    // eventually to be removed maybe?
                    baseBuildWithoutTriggers(baseBuild, mSyncedDb.isCollationCaseSensitive());
                }

                final long t8_BaseBuild_executed = System.nanoTime();

                mSyncedDb.analyze(mListTable);

                final long t9_table_optimized = System.nanoTime();

                // build the lookup aka navigation table
                if (listMode.useTriggers) {
                    // the table is ordered at insert time, so the row id *is* the order.
                    populateNavigationTable(mListTable.dot(DOM_PK_ID), preferredState);
                } else {
                    populateNavigationTable(baseBuild.getOrderByColumns(), preferredState);
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

                mSummary = summary;

            } finally {
                mSyncedDb.endTransaction(txLock);
            }
        } finally {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                Logger.debugExit(this, "build", mBooklistBuilderId);
            }
        }
    }

    /**
     * Now build a lookup table to match row sort position to row ID. This is used to
     * match a specific book (or other row in result set) to a position directly
     * without having to scan the database. This is especially useful in
     * expand/collapse operations.
     *
     * @param preferredState State to display: expanded, collapsed or remembered
     */
    private void populateNavigationTable(@NonNull final String sortExpression,
                                         final int preferredState) {

        mNavTable.drop(mSyncedDb);
        mNavTable.create(mSyncedDb, true, false);

        // TODO: Rebuild with state preserved is SLOWEST option
        // Need a better way to preserve state.
        String insSql = mNavTable.getInsertInto(DOM_BL_REAL_ROW_ID,
                DOM_BL_NODE_LEVEL,
                DOM_BL_ROOT_KEY,
                DOM_BL_NODE_VISIBLE,
                DOM_BL_NODE_EXPANDED)
                + " SELECT " + mListTable.dot(DOM_PK_ID)
                + ',' + mListTable.dot(DOM_BL_NODE_LEVEL)
                + ',' + mListTable.dot(DOM_BL_ROOT_KEY)
                + ',' + '\n'
                + "CASE WHEN " + DOM_BL_NODE_LEVEL + "=1"
                + " THEN 1"
                + " WHEN " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY) + " IS NULL"
                + " THEN 0 ELSE 1"
                + " END"
                + ',' + '\n'
                + "CASE WHEN " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY) + " IS NULL"
                + " THEN 0 ELSE 1"
                + " END"
                + '\n'
                + " FROM " + mListTable.ref()
                + " LEFT OUTER JOIN " + TBL_BOOK_LIST_NODE_SETTINGS.ref()
                + " ON " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY)
                + /*  */ '=' + mListTable.dot(DOM_BL_ROOT_KEY)
                + " AND " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_NODE_ROW_KIND)
                + /*  */ '=' + mStyle.getGroupKindAt(0)
                + " ORDER BY " + sortExpression;

        // Always save the state-preserving navigator for rebuilds
        SynchronizedStatement navStmt = mStatements.add(STMT_NAV_TABLE_INSERT, insSql);
        mRebuildStmts.add(navStmt);

        // On first-time builds, get the Preferences-based list
        switch (preferredState) {
            case PREF_LIST_REBUILD_ALWAYS_COLLAPSED:
                String sqlc = mNavTable.getInsertInto(DOM_BL_REAL_ROW_ID,
                        DOM_BL_NODE_LEVEL,
                        DOM_BL_ROOT_KEY,
                        DOM_BL_NODE_VISIBLE,
                        DOM_BL_NODE_EXPANDED)
                        + " SELECT " + mListTable.dot(DOM_PK_ID)
                        + ',' + mListTable.dot(DOM_BL_NODE_LEVEL)
                        + ',' + mListTable.dot(DOM_BL_ROOT_KEY)
                        + ',' + '\n'
                        + "CASE WHEN " + DOM_BL_NODE_LEVEL + "=1"
                        + " THEN 1 ELSE 0"
                        + " END"
                        + ",0"
                        + " FROM " + mListTable.ref()
                        + " ORDER BY " + sortExpression;
                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sqlc)) {
                    stmt.executeInsert();
                }
                break;

            case PREF_LIST_REBUILD_ALWAYS_EXPANDED:
                String sqle = mNavTable.getInsertInto(DOM_BL_REAL_ROW_ID,
                        DOM_BL_NODE_LEVEL,
                        DOM_BL_ROOT_KEY,
                        DOM_BL_NODE_VISIBLE,
                        DOM_BL_NODE_EXPANDED)
                        + " SELECT " + mListTable.dot(DOM_PK_ID)
                        + ',' + mListTable.dot(DOM_BL_NODE_LEVEL)
                        + ',' + mListTable.dot(DOM_BL_ROOT_KEY)
                        + ",1"
                        + ",1"
                        + " FROM " + mListTable.ref()
                        + " ORDER BY " + sortExpression;
                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sqle)) {
                    stmt.executeInsert();
                }
                break;

            default:
                // Use already-defined SQL for preserve state.
                navStmt.execute();
                break;
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
            if (!sortedDomainNames.contains(domainInfo.domain.name)) {
                sortedDomainNames.add(domainInfo.domain.name);
                if (sortedCols.length() > 0) {
                    sortedCols.append(",");
                    currInsertSql.append(",");
                }
                sortedCols.append(domainInfo.domain.name);
                currInsertSql.append("New.").append(domainInfo.domain.name);
            }
        }

        /*
         * Create a temp table to store the most recent header details from the last row.
         * We use this in determining what needs to be inserted as header records for
         * any given row.
         *
         * This is just a simple technique to provide persistent context to the trigger.
         */
        mSyncedDb.execSQL("CREATE TEMP TABLE " + currentTableName + " (" + sortedCols + ')');

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
                if (sortedDomainNames.contains(groupDomain.name)) {
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
            String tgSql = "\nCREATE TEMP TRIGGER " + tgName
                    + " BEFORE INSERT ON " + mListTable + " FOR EACH ROW"
                    + "\n WHEN New." + DOM_BL_NODE_LEVEL + '=' + (levelId + 1)
                    + " AND NOT EXISTS("
                    + /* */ "SELECT 1 FROM " + currentTableName + ' ' + currentTableAlias
                    + /* */ " WHERE " + conditionSql + ')'
                    + "\n BEGIN"
                    + "\n   " + insertSql + ';'
                    + "\n END";
            SynchronizedStatement stmt = mStatements.add(tgName, tgSql);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                Logger.debug(this, "makeNestedTriggers", "add",
                        tgName, stmt.toString());
            }
            mRebuildStmts.add(stmt);
            stmt.execute();
        }

        // Create a trigger to maintain the 'current' value -- just delete and insert
        String currentValueTriggerName = mListTable + "_TG_ZZZ";
        mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + currentValueTriggerName);
        String tgSql = "\nCREATE TEMP TRIGGER " + currentValueTriggerName
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
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
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
                "CREATE TEMP TRIGGER " + tgForwardName
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
            if (!sortedDomainNames.contains(domainInfo.domain.name)) {
                sortedDomainNames.add(domainInfo.domain.name);
                if (sortedCols.length() > 0) {
                    sortedCols.append(',');
                    currInsertSql.append(',');
                }
                sortedCols.append(domainInfo.domain.name);
                currInsertSql.append("New.").append(domainInfo.domain.name);
            }
        }

        /*
         * Create a temp table to store the most recent header details from the last row.
         * We use this in determining what needs to be inserted as header records for any given row.
         *
         * This is just a simple technique to provide persistent context to the trigger.
         */
        mSyncedDb.execSQL("CREATE TEMP TABLE " + currTblName + " (" + sortedCols + ')');
        mSyncedDb.execSQL("CREATE TEMP VIEW " + viewTblName + " AS SELECT * FROM " + mListTable);

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
            StringBuilder insertSql = new StringBuilder(
                    "INSERT INTO " + mListTable
                            + " (" + DOM_BL_NODE_LEVEL
                            + ',' + DOM_BL_NODE_ROW_KIND
                            + ',' + DOM_BL_ROOT_KEY);

            // Create the VALUES statement for the next level up. Not complete, more to be added.
            StringBuilder valuesSql = new StringBuilder(
                    " SELECT "
                            + levelId
                            + ',' + group.getKind()
                            + ',' + "New." + DOM_BL_ROOT_KEY);

            // Create the conditional to detect if next level up is already defined
            // (by checking the 'current' record/table)
            StringBuilder conditionSql = new StringBuilder();
            // Update the statement components
            //noinspection ConstantConditions
            for (DomainDefinition d : group.getDomains()) {
                insertSql.append(',').append(d);
                valuesSql.append(", New.").append(d);
                // Only update the 'condition' part if it is part of the SORT list
                if (sortedDomainNames.contains(d.name)) {
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

            insertSql.append(")\n")
                    .append(valuesSql)
                    .append(" WHERE NOT EXISTS(SELECT 1 FROM ")
                    .append(currTblName).append(" l WHERE ").append(conditionSql).append(")\n");

            oneBigTrigger.append("\n    ").append(insertSql).append(';');
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

    private void baseBuildWithoutTriggers(@NonNull final BaseBuildSqlComponents sqlCmp,
                                          final boolean collationIsCs) {

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
            for (DomainDefinition d : group.getDomains()) {
                if (collatedCols.length() > 0) {
                    collatedCols.append(',');
                }
                cols.append(',').append(d.name);

                collatedCols.append(' ').append(d.name).append(DAO.COLLATION);
            }

            // Construct the sum statement for this group
            String summarySql = "INSERT INTO " + mListTable
                    + " (" + DOM_BL_NODE_LEVEL
                    + ',' + DOM_BL_NODE_ROW_KIND + cols
                    + ',' + DOM_BL_ROOT_KEY + ')'
                    + " SELECT " + levelId + " AS " + DOM_BL_NODE_LEVEL
                    + ',' + group.getKind() + " AS " + DOM_BL_NODE_ROW_KIND + cols
                    + ',' + DOM_BL_ROOT_KEY
                    + " FROM " + mListTable + " WHERE " + DOM_BL_NODE_LEVEL + '=' + (levelId + 1)
                    + " GROUP BY " + collatedCols + ',' + DOM_BL_ROOT_KEY + DAO.COLLATION;
            //" GROUP BY " + DOM_BL_NODE_LEVEL + ", " + DOM_BL_NODE_ROW_KIND + collatedCols;

            // Save, compile and run this statement
            SynchronizedStatement stmt = mStatements.add("Level" + i, summarySql);
            mRebuildStmts.add(stmt);
            stmt.executeInsert();
            t_style[timer++] = System.nanoTime();
        }

        // Build an index if it will help sorting but *If* collation is case-sensitive,
        // don't bother with index, since everything is wrapped in lower().
        // ICS UNICODE: Consider adding a duplicate _OB (lower case) column
        // to the SUMMARY table. Ugh. ==> this whole "without triggers" is outdated.
        if (!collationIsCs) {
            String ix1Sql = sqlCmp.getIndexCreateWithoutTriggersSql(mListTable.getName());
            SynchronizedStatement stmt = mStatements.add(STMT_IX_1, ix1Sql);
            mRebuildStmts.add(stmt);
            stmt.execute();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            for (
                    int i = 0; i < mStyle.groupCount();
                    i++) {
                Logger.debug(this, "baseBuildWithoutTriggers",
                        "t_style[" + i + "]: " + (t_style[i] - t_style[i - 1]));
            }
        }
    }

    /**
     * Save the currently expanded top level nodes, and the top level group kind, to the database
     * so that the next time this view is opened, the user will see the same opened/closed nodes.
     */
    private void saveListNodeSettings() {
        SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            // clear all before we save the new values.
            deleteListNodeSettings();

            SynchronizedStatement stmt = mStatements.get(STMT_SAVE_ALL_LIST_NODE_SETTINGS);
            if (stmt == null) {
                String sql = TBL_BOOK_LIST_NODE_SETTINGS
                        .getInsertInto(DOM_BL_NODE_ROW_KIND, DOM_BL_ROOT_KEY)
                        + " SELECT DISTINCT ?," + DOM_BL_ROOT_KEY + " FROM " + mNavTable
                        + " WHERE " + DOM_BL_NODE_EXPANDED + "=1"
                        + " AND " + DOM_BL_NODE_LEVEL + "=1";

                stmt = mStatements.add(STMT_SAVE_ALL_LIST_NODE_SETTINGS, sql);
            }

            stmt.bindLong(1, mStyle.getGroupKindAt(0));
            stmt.execute();

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
     * Save the specified node state.
     */
    private void saveListNodeSetting(final long rowId) {
        SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            // Clear the list of expanded nodes in the current view.
            SynchronizedStatement stmt = mStatements.get(STMT_DELETE_LIST_NODE_SETTING);
            if (stmt == null) {
                stmt = mStatements.add(
                        STMT_DELETE_LIST_NODE_SETTING,
                        "DELETE FROM " + TBL_BOOK_LIST_NODE_SETTINGS
                                + " WHERE "
                                + DOM_BL_NODE_ROW_KIND + "=? AND " + DOM_BL_ROOT_KEY + " IN"
                                + " (SELECT DISTINCT " + DOM_BL_ROOT_KEY
                                + " FROM " + mNavTable + " WHERE " + DOM_PK_ID + "=?)");
            }
            stmt.bindLong(1, mStyle.getGroupKindAt(0));
            stmt.bindLong(2, rowId);
            stmt.executeUpdateDelete();

            // recreate them.
            stmt = mStatements.get(STMT_SAVE_LIST_NODE_SETTING);
            if (stmt == null) {
                String sql = TBL_BOOK_LIST_NODE_SETTINGS
                        .getInsertInto(DOM_BL_NODE_ROW_KIND, DOM_BL_ROOT_KEY)
                        + " SELECT ?," + DOM_BL_ROOT_KEY + " FROM " + mNavTable
                        + " WHERE " + DOM_BL_NODE_EXPANDED + "=1"
                        + " AND " + DOM_BL_NODE_LEVEL + "=1"
                        + " AND " + DOM_PK_ID + "=?";
                stmt = mStatements.add(STMT_SAVE_LIST_NODE_SETTING, sql);
            }

            stmt.bindLong(1, mStyle.getGroupKindAt(0));
            stmt.bindLong(2, rowId);
            stmt.execute();

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
     * Clear the list of expanded nodes in the current view.
     */
    private void deleteListNodeSettings() {

        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_LIST_NODE_SETTINGS);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_LIST_NODE_SETTINGS,
                    DELETE_BOOK_LIST_NODE_SETTINGS_BY_KIND);
        }
        // delete all rows for the top level group kind of the style
        stmt.bindLong(1, mStyle.getGroupKindAt(0));
        stmt.executeUpdateDelete();
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
            names[i] = domains.get(i).name;
        }

        names[domains.size()] = DOM_BL_ABSOLUTE_POSITION.name;
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
            domains.append(prefix).append(domain.name)
                    .append(" AS ").append(domain.name).append(',');
        }

        // Build the SQL, adding DOM_BL_ABSOLUTE_POSITION.
        final String sql = "SELECT " + domains
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
        return new BooklistPseudoCursor(this);
    }

    /**
     * All pseudo list cursors work with the static data in the temp table.
     * Get the logical count of rows using a simple query rather than scanning
     * the entire result set.
     */
    int getPseudoCount() {
        return pseudoCount("NavTable",
                "SELECT COUNT(*) FROM " + mNavTable
                        + " WHERE " + DOM_BL_NODE_VISIBLE + "=1");
    }

    /**
     * @return the total number of book records in the list
     */
    public int getBookCount() {
        return pseudoCount("ListTableBooks",
                "SELECT COUNT(*)"
                        + " FROM " + mListTable
                        + " WHERE "
                        + DOM_BL_NODE_LEVEL + '=' + (mStyle.groupCount() + 1));
    }

    /**
     * @return the number of <strong>unique</strong> book records in the list
     */
    public int getUniqueBookCount() {
        return pseudoCount("ListTableUniqueBooks",
                "SELECT COUNT(DISTINCT " + DOM_FK_BOOK + ')'
                        + " FROM " + mListTable
                        + " WHERE " + DOM_BL_NODE_LEVEL
                        + '=' + (mStyle.groupCount() + 1));
    }

    /**
     * Perform a single count query.
     *
     * @param name     a name only used for logging.
     * @param countSql the 'count' SQL to execute
     *
     * @return the count
     */
    private int pseudoCount(@NonNull final String name,
                            @NonNull final String countSql) {
        final long t0 = System.nanoTime();
        int count;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(countSql)) {
            count = (int) stmt.count();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            Logger.debug(this, "pseudoCount",
                    name + '=' + count + " completed in "
                            + (System.nanoTime() - t0) + "nano");
        }
        return count;
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
     * Get all positions at which the specified book appears.
     *
     * @return Array of row details, including absolute positions and visibility or
     * {@code null} if not present.
     */
    @Nullable
    public ArrayList<BookRowInfo> getBookAbsolutePositions(final long bookId) {
        String sql = "SELECT "
                + mNavTable.dot(DOM_PK_ID) + ',' + mNavTable.dot(DOM_BL_NODE_VISIBLE)
                + " FROM " + mListTable + " bl " + mListTable.join(mNavTable)
                + " WHERE " + mListTable.dot(DOM_FK_BOOK) + "=?";

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(bookId)})) {
            ArrayList<BookRowInfo> rows = new ArrayList<>(cursor.getCount());
            if (cursor.moveToFirst()) {
                do {
                    int absPos = cursor.getInt(0) - 1;
                    rows.add(new BookRowInfo(absPos, getPosition(absPos),
                            cursor.getInt(1) == 1));
                } while (cursor.moveToNext());
                return rows;
            } else {
                return null;
            }
        }
    }

    /**
     * Find the visible root node for a given absolute position and ensure it is visible.
     */
    public void ensureAbsolutePositionVisible(final long absPos) {
        // If <0 then no previous node.
        if (absPos < 0) {
            return;
        }

        SynchronizedStatement stmt = mStatements.get(STMT_GET_NODE_ROOT);
        if (stmt == null) {
            stmt = mStatements.add(
                    STMT_GET_NODE_ROOT,
                    "SELECT " + DOM_PK_ID + " || '/' || " + DOM_BL_NODE_EXPANDED
                            + " FROM " + mNavTable
                            + " WHERE " + DOM_BL_NODE_LEVEL + "=1 AND " + DOM_PK_ID + "<=?"
                            + " ORDER BY " + DOM_PK_ID + " DESC LIMIT 1");
        }

        final long rowId = absPos + 1;

        // Get the root node, and expanded flag
        stmt.bindLong(1, rowId);
        try {
            String[] info = stmt.simpleQueryForString().split("/");
            long rootId = Long.parseLong(info[0]);
            long isExpanded = Long.parseLong(info[1]);
            // If root node is not the node we are checking,
            // and root node is not expanded, expand it.
            if (rootId != rowId && isExpanded == 0) {
                toggleExpandNode(rootId - 1);
            }
        } catch (@NonNull final SQLiteDoneException ignore) {
            // query returned zero rows
        }
    }

    /**
     * For EXPAND: Set all rows as visible/expanded.
     * For COLLAPSE: Set all non-root rows as invisible/unexpanded and mark all root
     * nodes as visible/unexpanded.
     */
    public void expandAll(final boolean expand) {
        SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            final long t0 = System.nanoTime();
            if (expand) {
                String sql = "UPDATE " + mNavTable + " SET "
                        + DOM_BL_NODE_EXPANDED + "=1,"
                        + DOM_BL_NODE_VISIBLE + "=1";
                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    stmt.executeUpdateDelete();
                }
                saveListNodeSettings();

            } else {
                String sql = "UPDATE " + mNavTable + " SET "
                        + DOM_BL_NODE_EXPANDED + "=0,"
                        + DOM_BL_NODE_VISIBLE + "=0"
                        + " WHERE " + DOM_BL_NODE_LEVEL + ">1";
                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    stmt.executeUpdateDelete();
                }
                sql = "UPDATE " + mNavTable + " SET "
                        + DOM_BL_NODE_EXPANDED + "=0"
                        + " WHERE " + DOM_BL_NODE_LEVEL + "=1";
                try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                    stmt.executeUpdateDelete();
                }
                deleteListNodeSettings();
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                Logger.debug(this, "expandAll",
                        (System.nanoTime() - t0) + "nano");
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
     * Toggle the expand/collapse status of the node at the specified absolute position.
     */
    public void toggleExpandNode(final long absPos) {
        SyncLock txLock = null;
        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            // row position starts at 0, ID's start at 1...
            final long rowId = absPos + 1;

            // Get the details of the passed row position.
            SynchronizedStatement getNodeLevelStmt = mStatements.get(STMT_GET_NODE_LEVEL);
            if (getNodeLevelStmt == null) {
                getNodeLevelStmt = mStatements.add(
                        STMT_GET_NODE_LEVEL,
                        "SELECT " + DOM_BL_NODE_LEVEL + " || '/' || " + DOM_BL_NODE_EXPANDED
                                + " FROM " + mNavTable.ref()
                                + " WHERE " + mNavTable.dot(DOM_PK_ID) + "=?");
            }
            getNodeLevelStmt.bindLong(1, rowId);

            String[] info;
            try {
                info = getNodeLevelStmt.simpleQueryForString().split("/");
            } catch (@NonNull final SQLiteDoneException ignore) {
                return;
            }
            long level = Long.parseLong(info[0]);
            int isExpanded = (Integer.parseInt(info[1]) == 1) ? 0 : 1;

            // Find the next row at the same level
            SynchronizedStatement getNextAtSameLevelStmt =
                    mStatements.get(STMT_GET_NEXT_AT_SAME_LEVEL);
            if (getNextAtSameLevelStmt == null) {
                getNextAtSameLevelStmt = mStatements.add(
                        STMT_GET_NEXT_AT_SAME_LEVEL,
                        "SELECT COALESCE(Max(" + DOM_PK_ID + "),-1) FROM "
                                + "(SELECT " + DOM_PK_ID + " FROM " + mNavTable.ref()
                                + " WHERE " + mNavTable.dot(DOM_PK_ID) + ">?"
                                + " AND " + mNavTable.dot(DOM_BL_NODE_LEVEL) + "=?"
                                + " ORDER BY " + DOM_PK_ID + " LIMIT 1"
                                + ") zzz");
            }
            getNextAtSameLevelStmt.bindLong(1, rowId);
            getNextAtSameLevelStmt.bindLong(2, level);
            // the COALESCE(max(" + DOM_PK_ID + "),-1) prevents SQLiteDoneException
            long next = getNextAtSameLevelStmt.simpleQueryForLong();
            if (next < 0) {
                next = Long.MAX_VALUE;
            }

            // Set intervening nodes as visible/invisible
            SynchronizedStatement showStmt = mStatements.get(STMT_SHOW);
            if (showStmt == null) {
                showStmt = mStatements.add(
                        STMT_SHOW,
                        "UPDATE " + mNavTable + " SET "
                                + DOM_BL_NODE_VISIBLE + "=?"
                                + ',' + DOM_BL_NODE_EXPANDED + "=?"
                                + " WHERE " + DOM_PK_ID + ">?"
                                + " AND " + DOM_BL_NODE_LEVEL + ">? AND " + DOM_PK_ID + "<?");
            }
            // visible
            showStmt.bindLong(1, isExpanded);
            // expanded
            showStmt.bindLong(2, isExpanded);
            showStmt.bindLong(3, rowId);
            showStmt.bindLong(4, level);
            showStmt.bindLong(5, next);

            showStmt.execute();

            // Set this node as expanded.
            SynchronizedStatement expandStmt = mStatements.get(STMT_EXPAND);
            if (expandStmt == null) {
                expandStmt = mStatements.add(STMT_EXPAND,
                        "UPDATE " + mNavTable
                                + " SET " + DOM_BL_NODE_EXPANDED + "=?"
                                + " WHERE " + DOM_PK_ID + "=?");
            }
            expandStmt.bindLong(1, isExpanded);
            expandStmt.bindLong(2, rowId);
            expandStmt.execute();

            // Store the state of this node.
            saveListNodeSetting(rowId);

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
     * @return the style used by this builder.
     */
    @NonNull
    public BooklistStyle getStyle() {
        return mStyle;
    }

    /**
     * If the field has a time part, then convert to local time.
     * This deals with legacy 'date-only' dates.
     * The logic being that IF they had a time part then it would be UTC.
     * Without a time part, we assume the zone is local (or irrelevant).
     */
    @NonNull
    private String localDateExpression(@NonNull final String fieldSpec) {
        return "CASE WHEN " + fieldSpec + " glob '*-*-* *' "
                + " THEN datetime(" + fieldSpec + ", 'localtime')"
                + " ELSE " + fieldSpec
                + " END";
    }

    /**
     * Return a glob expression to get the 'year' from a text date field in a standard way.
     * <p>
     * Just look for 4 leading numbers. We don't care about anything else.
     *
     * @param fieldSpec fully qualified field name
     * @param toLocal   convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    @NonNull
    private String yearGlob(@NonNull String fieldSpec,
                            final boolean toLocal) {
        if (toLocal) {
            fieldSpec = localDateExpression(fieldSpec);
        }
        return "CASE WHEN " + fieldSpec + " glob '[0123456789][01234567890][01234567890][01234567890]*'"
                + " THEN substr(" + fieldSpec + ",1,4)"
                + " ELSE '" + mUnknown + '\''
                + " END";
    }

    /**
     * Returns a glob expression to get the 'month' from a text date field in a standard way.
     * <p>
     * Just look for 4 leading numbers followed by 2 or 1 digit.
     * We don't care about anything else.
     *
     * @param fieldSpec fully qualified field name
     * @param toLocal   convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    @NonNull
    private String monthGlob(@NonNull String fieldSpec,
                             final boolean toLocal) {
        if (toLocal) {
            fieldSpec = localDateExpression(fieldSpec);
        }
        return "CASE WHEN " + fieldSpec + " glob '[0123456789][01234567890][01234567890][01234567890]-[0123456789][01234567890]*'"
                + " THEN substr(" + fieldSpec + ",6,2)"
                + " WHEN " + fieldSpec + " glob '[0123456789][01234567890][01234567890][01234567890]-[0123456789]*'"
                + " THEN substr(" + fieldSpec + ",6,1)"
                + " ELSE '" + mUnknown + '\''
                + " END";
    }

    /**
     * Returns a glob expression to get the 'day' from a text date field in a standard way.
     * <p>
     * Just look for 4 leading numbers followed by 2 or 1 digit, and then 1 or two digits.
     * We don't care about anything else.
     *
     * @param fieldSpec fully qualified field name
     * @param toLocal   convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    @NonNull
    private String dayGlob(@NonNull String fieldSpec,
                           final boolean toLocal) {
        if (toLocal) {
            fieldSpec = localDateExpression(fieldSpec);
        }
        // Just look for 4 leading numbers followed by 2 or 1 digit then another 2 or 1 digit.
        // We don't care about anything else.
        return "CASE WHEN " + fieldSpec + " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789][0123456789]-[0123456789][0123456789]*'"
                + " THEN substr(" + fieldSpec + ",9,2)"
                + " WHEN " + fieldSpec + " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789]-[0123456789][0123456789]*'"
                + " THEN substr(" + fieldSpec + ",8,2)"
                + " WHEN " + fieldSpec + " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789][0123456789]-[0123456789]*'"
                + " THEN substr(" + fieldSpec + ",9,1)"
                + " WHEN " + fieldSpec + " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789]-[0123456789]*'"
                + " THEN substr(" + fieldSpec + ",8,1)"
                + " ELSE " + fieldSpec
                + " END";
    }

    /**
     * Close the builder.
     */
    @Override
    public void close() {
        mCloseWasCalled = true;

        if (!mStatements.isEmpty()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                Logger.debug(this, "close",
                        "Has active mStatements (this is not an error): ");
                for (String name : mStatements.getNames()) {
                    Logger.debug(this, "cleanup", name);
                }
            }

            mStatements.close();
        }

        if (mNavTable != null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
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
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
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

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
            if (!mDebugReferenceDecremented) {
                // Only de-reference once! Paranoia ... close() might be called twice?
                Logger.debug(this, "close",
                        "instances left: " + DEBUG_INSTANCE_COUNTER.decrementAndGet());
            }
            mDebugReferenceDecremented = true;
        }
    }

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

    /**
     * Simple equality: two builders are equal if their ID's are the same.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BooklistBuilder that = (BooklistBuilder) o;
        return mBooklistBuilderId == that.mBooklistBuilderId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBooklistBuilderId);
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistBuilder{"
                + "mBooklistBuilderId=" + mBooklistBuilderId
                + ", mCloseWasCalled=" + mCloseWasCalled
                + '}';
    }

    public String debugInfoForTables() {
        // not calling mListTable.getTableInfo, so we don't cache this data in debug.
        TableInfo listTableInfo = new TableInfo(mSyncedDb, mListTable.getName());
        TableInfo navTableInfo = new TableInfo(mSyncedDb, mNavTable.getName());

        return "ListTable\n" + listTableInfo.toString() + '\n' + navTableInfo.toString();
    }

    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        String sql = "SELECT " + DBDefinitions.KEY_FK_BOOK
                + " FROM " + mListTable + " ORDER BY " + DBDefinitions.KEY_FK_BOOK;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            ArrayList<Long> rows = new ArrayList<>(cursor.getCount());
            if (cursor.moveToFirst()) {
                do {
                    Long id = (long) cursor.getInt(0);
                    rows.add(id);
                } while (cursor.moveToNext());
                return rows;
            } else {
                return new ArrayList<>();
            }
        }
    }

    public static class CompatibilityMode {

        /** BookList Compatibility mode property values. */
        public static final int PREF_MODE_DEFAULT = 0;
        public static final int PREF_MODE_NESTED_TRIGGERS = 1;
        public static final int PREF_MODE_FLAT_TRIGGERS = 2;
        public static final int PREF_MODE_OLD_STYLE = 3;
        /** singleton. */
        private static CompatibilityMode instance;
        /** Preferred: true. */
        final boolean useTriggers;
        /** Preferred: true. */
        final boolean nestedTriggers;

        /**
         * Constructor.
         */
        private CompatibilityMode() {
            int mode = App.getListPreference(Prefs.pk_bob_list_generation, PREF_MODE_DEFAULT);
            // we'll always use triggers unless we're in 'ancient' mode
            useTriggers = mode != PREF_MODE_OLD_STYLE;

            // nestedTriggers is ignored unless useTriggers is set to true.
            nestedTriggers =
                    // The default/preferred mode is to use nested triggers
                    (mode == PREF_MODE_DEFAULT)
                            // or if the user explicitly asked for them
                            || (mode == PREF_MODE_NESTED_TRIGGERS);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                Logger.debug(this, "CompatibilityMode",
                        "\nlistMode          : " + mode,
                        "\nuseTriggers       : " + useTriggers,
                        "\nnestedTriggers    : " + nestedTriggers);
            }
        }

        static CompatibilityMode get() {
            if (instance == null) {
                instance = new CompatibilityMode();
            }
            return instance;
        }
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
         * <p>
         * Always joined are:
         * <ul>
         * <li>{@link DBDefinitions#TBL_BOOK_AUTHOR}<br>{@link DBDefinitions#TBL_AUTHORS}</li>
         * <li>{@link DBDefinitions#TBL_BOOK_SERIES}<br>{@link DBDefinitions#TBL_SERIES}</li>
         * </ul>
         * Optionally joined with:
         * <ul>
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
                indexCols.append(sdi.domain.name);
                if (sdi.domain.isText()) {
                    indexCols.append(DAO.COLLATION);
                    // The order of this if/elseif/else is important, don't merge branch 1 and 3!
                    if (sdi.domain.isPrePreparedOrderBy()) {
                        // always use a pre-prepared order-by column as-is
                        sortCols.append(sdi.domain.name);

                    } else if (collationIsCs) {
                        // *If* collations is case-sensitive, lowercase it.
                        sortCols.append("lower(").append(sdi.domain.name).append(')');

                    } else {
                        // hope for the best. This case might not handle non-[A..Z0..9] as expected
                        sortCols.append(sdi.domain.name);
                    }

                    sortCols.append(DAO.COLLATION);
                } else {
                    sortCols.append(sdi.domain.name);
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
            return "CREATE INDEX " + table + "_IX1 ON " + table + '(' + mOrderByColumnsForBaseBuildIndex + ')';

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
     * A data class containing details of the positions of all instances of a single book.
     */
    public static class BookRowInfo {

        public final int absolutePosition;
        public final boolean visible;
        public int listPosition;

        BookRowInfo(final int absolutePosition,
                    final int listPosition,
                    final boolean visible) {
            this.absolutePosition = absolutePosition;
            this.listPosition = listPosition;
            this.visible = visible;
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
    static class SortedDomainInfo {

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
                    // same expression, we do NOT want to add it. This is fine.
                    if (BuildConfig.DEBUG) {
                        Logger.warnWithStackTrace(this,
                                "duplicate domain/expression",
                                "domain.name=" + domain.name);
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
            //if ((flags & FLAG_KEY) != 0)
            //	mKeys.add(domain);
        }

        /**
         * Build each kind group.
         * <p>
         * ****************************************************************************************
         * <b>IMPORTANT:</b> for each kind, the FIRST SORTED AND GROUPED domain should be the one
         * that will be displayed at that level in the UI.
         * ****************************************************************************************
         */
        void addGroup(@NonNull final BooklistGroup booklistGroup,
                      final int sortDescendingMask,
                      @NonNull final BuildInfoHolder /* in/out */ buildInfoHolder) {

            switch (booklistGroup.getKind()) {

                case BooklistGroup.RowKind.AUTHOR:
                    // Save this for later use
                    buildInfoHolder.authorGroup = (BooklistGroup.BooklistAuthorGroup) booklistGroup;

                    // Always group & sort by DOM_AUTHOR_SORT and user preference order; see #696
                    // The expression uses the OB column.
                    addDomain(DOM_AUTHOR_SORT,
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
                    addDomain(DOM_FK_AUTHOR,
                            TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR),
                            SummaryBuilder.FLAG_GROUPED);

                    // we want the isComplete flag
                    addDomain(DOM_AUTHOR_IS_COMPLETE,
                            TBL_AUTHORS.dot(DOM_AUTHOR_IS_COMPLETE),
                            SummaryBuilder.FLAG_GROUPED);

                    break;

                case BooklistGroup.RowKind.SERIES:
                    // Save this for later use
                    buildInfoHolder.seriesGroup = (BooklistGroup.BooklistSeriesGroup) booklistGroup;

                    // Group and sort by name
                    // The expression uses the OB column.
                    addDomain(DOM_SERIES_SORT,
                            TBL_SERIES.dot(DOM_SERIES_TITLE_OB),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);

                    // Add the 'formatted' field of the requested type for displaying.
                    addDomain(DOM_SERIES_TITLE,
                            TBL_SERIES.dot(DOM_SERIES_TITLE),
                            SummaryBuilder.FLAG_GROUPED);

                    // Group by ID (we want the ID available and there is a *chance* two
                    // series will have the same name...with bad data
                    addDomain(DOM_FK_SERIES,
                            TBL_BOOK_SERIES.dot(DOM_FK_SERIES),
                            SummaryBuilder.FLAG_GROUPED);

                    // We want the series position in the base data
                    addDomain(DOM_BOOK_SERIES_POSITION,
                            TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION),
                            SummaryBuilder.FLAG_NONE);

                    // we want the isComplete flag
                    addDomain(DOM_SERIES_IS_COMPLETE,
                            TBL_SERIES.dot(DOM_SERIES_IS_COMPLETE),
                            SummaryBuilder.FLAG_GROUPED);

                    // We want the series number in the base data in sorted order
                    // Allow for the possibility of 3.1, or even "3.1|Omnibus 3-10" as
                    // a series number. So we convert it to a real (aka float).
                    addDomain(DOM_BL_SERIES_NUM_FLOAT,
                            "CAST("
                                    + TBL_BOOK_SERIES.dot(DOM_BOOK_NUM_IN_SERIES) + " AS REAL)",
                            SummaryBuilder.FLAG_SORTED);

                    // We also add the base name as a sorted field for display purposes
                    // and in case of non-numeric data.
                    addDomain(DOM_BOOK_NUM_IN_SERIES,
                            TBL_BOOK_SERIES.dot(DOM_BOOK_NUM_IN_SERIES),
                            SummaryBuilder.FLAG_SORTED);

                    // We want a counter of how many books use the series as a primary series,
                    // so we can skip some series
                    addDomain(DOM_BL_PRIMARY_SERIES_COUNT,
                            DAO.SqlColumns.EXP_PRIMARY_SERIES_COUNT_AS_BOOLEAN,
                            SummaryBuilder.FLAG_NONE);
                    break;

                case BooklistGroup.RowKind.LOANED:
                    // Saved for later to indicate group was present
                    buildInfoHolder.setJoinLoaned();
                    addDomain(DOM_LOANEE_AS_BOOLEAN,
                            DAO.SqlColumns.EXP_LOANEE_AS_BOOLEAN,
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);

                    addDomain(DOM_LOANEE,
                            DAO.SqlColumns.EXP_BOOK_LOANEE_OR_EMPTY,
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    break;

                case BooklistGroup.RowKind.BOOKSHELF:
                    buildInfoHolder.setJoinBookshelves();
                    addDomain(booklistGroup.getDisplayDomain(),
                            TBL_BOOKSHELF.dot(DOM_BOOKSHELF),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    break;

                case BooklistGroup.RowKind.READ_STATUS:
                    // Define how the new field is retrieved and sorted/grouped
                    addDomain(booklistGroup.getDisplayDomain(),
                            TBL_BOOKS.dot(DOM_BOOK_READ),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);

                    addDomain(DOM_BOOK_READ,
                            TBL_BOOKS.dot(DOM_BOOK_READ),
                            //FIXME: run a cleanup of data in db upgrades.
                            // We want the READ flag at the lowest level only.
                            // Some bad data means that it may be 0 or 'f',
                            // so we don't group by it.
                            SummaryBuilder.FLAG_NONE);
                    break;

                case BooklistGroup.RowKind.PUBLISHER:
                    addDomain(booklistGroup.getDisplayDomain(),
                            TBL_BOOKS.dot(DOM_BOOK_PUBLISHER),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    break;

                case BooklistGroup.RowKind.GENRE:
                    addDomain(booklistGroup.getDisplayDomain(),
                            TBL_BOOKS.dot(DOM_BOOK_GENRE),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    break;

                case BooklistGroup.RowKind.LANGUAGE:
                    addDomain(booklistGroup.getDisplayDomain(),
                            TBL_BOOKS.dot(DOM_BOOK_LANGUAGE),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    break;

                case BooklistGroup.RowKind.LOCATION:
                    addDomain(booklistGroup.getDisplayDomain(),
                            TBL_BOOKS.dot(DOM_BOOK_LOCATION),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    break;

                case BooklistGroup.RowKind.FORMAT:
                    addDomain(booklistGroup.getDisplayDomain(),
                            TBL_BOOKS.dot(DOM_BOOK_FORMAT),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    break;

                case BooklistGroup.RowKind.TITLE_LETTER:
                    addDomain(booklistGroup.getDisplayDomain(),
                            // use the OrderBy column!
                            "SUBSTR(" + TBL_BOOKS.dot(DOM_TITLE_OB) + ",1,1)",
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    break;

                case BooklistGroup.RowKind.RATING:
                    // sorting should be descending.
                    addDomain(booklistGroup.getDisplayDomain(),
                            "CAST(" + TBL_BOOKS.dot(DOM_BOOK_RATING) + " AS INTEGER)",
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                    | sortDescendingMask);
                    break;


                //URGENT: check with original code what this quote really means.
                // It was on most, but not all date rows:
                // ----------------------------------
                // TODO: Handle 'DESCENDING'. Requires the navigator construction to use
                // max/min for non-grouped domains that appear in sub-levels based on desc/asc.
                // We don't use DESCENDING sort yet because the 'header' ends up below
                // the detail rows in the flattened table.
                // ----------------------------------

                // Also: sortDescendingMask: original code had it added to "date added" and
                // "date last updated" but not to others.
                // Commented ones mine, added as reminder)
                case BooklistGroup.RowKind.DATE_PUBLISHED_YEAR:
                    addDomain(booklistGroup.getDisplayDomain(),
                            yearGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_PUBLISHED), false),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    // | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_PUBLISHED_MONTH:
                    addDomain(booklistGroup.getDisplayDomain(),
                            monthGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_PUBLISHED), false),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    // | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_FIRST_PUBLICATION_YEAR:
                    addDomain(booklistGroup.getDisplayDomain(),
                            yearGlob(TBL_BOOKS.dot(DOM_DATE_FIRST_PUBLICATION), false),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    // | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_FIRST_PUBLICATION_MONTH:
                    addDomain(booklistGroup.getDisplayDomain(),
                            monthGlob(TBL_BOOKS.dot(DOM_DATE_FIRST_PUBLICATION), false),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    // | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_READ_YEAR:
                    addDomain(booklistGroup.getDisplayDomain(),
                            yearGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    // | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_READ_MONTH:
                    addDomain(booklistGroup.getDisplayDomain(),
                            monthGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    // | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_READ_DAY:
                    addDomain(booklistGroup.getDisplayDomain(),
                            dayGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    // | sortDescendingMask);
                    break;


                case BooklistGroup.RowKind.DATE_ACQUIRED_YEAR:
                    addDomain(booklistGroup.getDisplayDomain(),
                            yearGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    // | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_ACQUIRED_MONTH:
                    addDomain(booklistGroup.getDisplayDomain(),
                            monthGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    // | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_ACQUIRED_DAY:
                    addDomain(booklistGroup.getDisplayDomain(),
                            dayGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                    // | sortDescendingMask);
                    break;


                case BooklistGroup.RowKind.DATE_ADDED_YEAR:
                    addDomain(booklistGroup.getDisplayDomain(),
                            yearGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                    | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_ADDED_MONTH:
                    addDomain(booklistGroup.getDisplayDomain(),
                            monthGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                    | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_ADDED_DAY:
                    addDomain(booklistGroup.getDisplayDomain(),
                            dayGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                    | sortDescendingMask);
                    break;


                case BooklistGroup.RowKind.DATE_LAST_UPDATE_YEAR:
                    addDomain(booklistGroup.getDisplayDomain(),
                            yearGlob(TBL_BOOKS.dot(DOM_DATE_LAST_UPDATED), true),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                    | sortDescendingMask);

                    addDomain(DOM_DATE_LAST_UPDATED,
                            null,
                            SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH:
                    addDomain(booklistGroup.getDisplayDomain(),
                            monthGlob(TBL_BOOKS.dot(DOM_DATE_LAST_UPDATED), true),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                    | sortDescendingMask);

                    addDomain(DOM_DATE_LAST_UPDATED,
                            null,
                            SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                    break;

                case BooklistGroup.RowKind.DATE_LAST_UPDATE_DAY:
                    addDomain(booklistGroup.getDisplayDomain(),
                            dayGlob(TBL_BOOKS.dot(DOM_DATE_LAST_UPDATED), true),
                            SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                    | sortDescendingMask);

                    addDomain(DOM_DATE_LAST_UPDATED,
                            null,
                            SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                    break;


                // NEWKIND: RowKind.ROW_KIND_x

                case BooklistGroup.RowKind.BOOK:
                    // nothing to do.
                    break;

                default:
                    throw new IllegalTypeException(String.valueOf(booklistGroup.getKind()));
            }
        }

        /**
         * @return a clone of the CURRENT domains.
         * <p>
         * Since BooklistGroup objects are processed in order, this allows us to get
         * the GROUP-BY fields applicable to the currently processed group, including all
         * outer groups.
         * Hence why it is cloned -- subsequent domains will modify this collection.
         */
        @NonNull
        @SuppressWarnings("unchecked")
        ArrayList<DomainDefinition> cloneGroups() {
            //shallow copy, is enough
            return (ArrayList<DomainDefinition>) mGroupedDomains.clone();
        }

        /**
         * @return the collection of columns used to sort the output.
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
        BaseBuildSqlComponents buildSqlComponents(@NonNull final BooklistGroup.CompoundKey rootKey) {
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

                destColumns.append(mDomains.get(i).name);
                sourceColumns.append(mExpressions.get(mDomains.get(i)));
                // This is not strictly necessary, but the SQL is more readable and easier to debug.
                sourceColumns.append(" AS ").append(mDomains.get(i).name);
            }

            // Build the expression for the root key.
            StringBuilder keyColumns = new StringBuilder('\'' + rootKey.getPrefix());
            for (DomainDefinition domain : rootKey.getDomains()) {
                keyColumns.append("/' || COALESCE(").append(mExpressions.get(domain)).append(
                        ",'')");
            }

            return new BaseBuildSqlComponents(destColumns + "," + DOM_BL_ROOT_KEY,
                    sourceColumns.toString(),
                    keyColumns.toString());
        }
    }
}
