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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.Groups;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_EXPANDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_VISIBLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BL_ROW_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;

/**
 * Build and populate temporary tables with details of "flattened" books.
 * The generated list is used to display books in a list control and perform operation like
 * 'expand/collapse' on nodes in the list.
 * <p>
 * The {@link Booklist} "owns" the temporary database tables.
 * They get deleted when {@link Booklist#close()} is called.
 * A {@link Booklist} has a 1:1 relation to {@link BooklistCursor} objects.
 * The BooklistCursor holds a reference to the {@link Booklist}.
 * <p>
 * The main class and the inner table class could be (re)merged into one,
 * but keeping the two separate should make the API easier to handle.
 */
class BooklistBuilder {

    /** Log tag. */
    private static final String TAG = "BooklistBuilder";
    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";

    /**
     * Expression for the domain {@link DBDefinitions#DOM_BOOKSHELF_NAME_CSV}.
     * <p>
     * The order of the returned names will be arbitrary.
     * We could add an ORDER BY GROUP_CONCAT(... if we GROUP BY
     */
    static final String EXP_BOOKSHELF_NAME_CSV =
            "(SELECT GROUP_CONCAT(" + TBL_BOOKSHELF.dot(DBKey.KEY_BOOKSHELF_NAME) + ",', ')"
            + _FROM_ + TBL_BOOKSHELF.startJoin(TBL_BOOK_BOOKSHELF)
            + _WHERE_
            + TBL_BOOKS.dot(DBKey.PK_ID) + "=" + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOK)
            + ")";

    /**
     * Expression for the domain {@link DBDefinitions#DOM_PUBLISHER_NAME_CSV}.
     * <p>
     * The order of the returned names will be arbitrary.
     * We could add an ORDER BY GROUP_CONCAT(... if we GROUP BY
     */
    static final String EXP_PUBLISHER_NAME_CSV =
            "(SELECT GROUP_CONCAT(" + TBL_PUBLISHERS.dot(DBKey.KEY_PUBLISHER_NAME) + ",', ')"
            + _FROM_ + TBL_PUBLISHERS.startJoin(TBL_BOOK_PUBLISHER)
            + _WHERE_
            + TBL_BOOKS.dot(DBKey.PK_ID) + "=" + TBL_BOOK_PUBLISHER.dot(DBKey.FK_BOOK)
            + ")";
    /**
     * Counter for generating ID's. Only increments.
     * Used to create unique names for the temporary tables.
     */
    @NonNull
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    private static final String _AND_ = " AND ";

    /** Style to use while building the list. */
    @NonNull
    private final ListStyle mStyle;

    /** Show only books on this bookshelf. */
    @NonNull
    private final Bookshelf mBookshelf;

    @NonNull
    private final Map<String, TableDefinition> mLeftOuterJoins = new HashMap<>();

    /** Collection of 'extra' book level domains requested by caller. */
    @NonNull
    private final Map<String, DomainExpression> mBookDomains = new HashMap<>();

    /** the list of Filters. */
    private final Collection<Filter> mFilters = new ArrayList<>();

    @NonNull
    private RebuildBooklist mRebuildMode;

    /**
     * Constructor.
     *
     * @param style       Style reference.
     *                    This is the resolved style as used by the passed bookshelf
     * @param bookshelf   the current bookshelf
     * @param rebuildMode booklist mode to use in next rebuild.
     */
    BooklistBuilder(@NonNull final ListStyle style,
                    @NonNull final Bookshelf bookshelf,
                    @NonNull final RebuildBooklist rebuildMode) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "ENTER|BooklistBuilder"
                       + "|style=" + style.getUuid()
                       + "|bookshelf=`" + bookshelf.getName() + '`'
                       + "|instances: " + Booklist.DEBUG_INSTANCE_COUNTER.incrementAndGet(),
                  new Throwable());
        }

        mStyle = style;
        mBookshelf = bookshelf;
        mRebuildMode = rebuildMode;
    }

    /**
     * Add a table to be added as a LEFT OUTER JOIN.
     *
     * @param tableDefinition TableDefinition to add
     */
    void addLeftOuterJoin(@SuppressWarnings("SameParameterValue")
                          @NonNull final TableDefinition tableDefinition) {
        if (mLeftOuterJoins.containsKey(tableDefinition.getName())) {
            // adding a duplicate here is a bug.
            throw new IllegalArgumentException("Duplicate table=" + tableDefinition.getName());
        }
        mLeftOuterJoins.put(tableDefinition.getName(), tableDefinition);
    }

    /**
     * Add a domain to the resulting flattened list based on the details provided.
     *
     * @param domainExpression Domain to add
     */
    void addDomain(@NonNull final DomainExpression domainExpression) {
        final String key = domainExpression.getDomain().getName();
        if (mBookDomains.containsKey(key)) {
            // adding a duplicate here is a bug.
            throw new IllegalArgumentException("Duplicate domain=" + key);
        }
        mBookDomains.put(key, domainExpression);
    }

    /**
     * Add a filter to use in the WHERE clause.
     *
     * @param filter to add
     */
    void addFilter(@NonNull final Filter filter) {
        mFilters.add(filter);
    }

    /**
     * Add a list of filter to use in the WHERE clause.
     *
     * @param filters to add
     */
    void addFilter(@NonNull final List<Filter> filters) {
        mFilters.addAll(filters);
    }

    /**
     * Allows to override the build state set in the constructor.
     *
     * @param rebuildMode booklist state to use in next rebuild.
     */
    void setRebuildMode(@SuppressWarnings("SameParameterValue")
                        @NonNull final RebuildBooklist rebuildMode) {
        mRebuildMode = rebuildMode;
    }

    /**
     * Clear and build the temporary list of books.
     *
     * @param context Current context
     *
     * @return the Booklist ready to use
     */
    @NonNull
    public Booklist build(@NonNull final Context context) {
        final int instanceId = ID_COUNTER.incrementAndGet();

        // Construct the list table and all needed structures.
        final TableBuilder tableBuilder = new TableBuilder(
                instanceId, mStyle, !mBookshelf.isAllBooks(), mRebuildMode);
        tableBuilder.preBuild(context, mLeftOuterJoins.values(), mBookDomains.values(), mFilters);

        final SynchronizedDb db = ServiceLocator.getInstance().getDb();

        final Synchronizer.SyncLock txLock = db.beginTransaction(true);
        try {
            // create the tables and populate them
            final Pair<TableDefinition, TableDefinition> tables = tableBuilder.build(db);
            final TableDefinition listTable = tables.first;
            final TableDefinition navTable = tables.second;
            final BooklistNodeDao rowStateDAO =
                    new BooklistNodeDao(db, listTable, mStyle, mBookshelf);

            switch (mRebuildMode) {
                case FromSaved:
                    // all rows will be collapsed/hidden; restore the saved state
                    rowStateDAO.restoreSavedState();
                    break;

                case Preferred:
                    // all rows will be collapsed/hidden; now adjust as required.
                    rowStateDAO.setAllNodes(mStyle.getTopLevel(), false);
                    break;

                case Expanded:
                case Collapsed:
                    // handled during table creation
                    break;

                default:
                    throw new IllegalArgumentException(String.valueOf(mRebuildMode));
            }

            db.setTransactionSuccessful();

            return new Booklist(instanceId, db, listTable, navTable, rowStateDAO);

        } finally {
            db.endTransaction(txLock);
        }
    }


    /**
     * A Builder to accumulates data while building the list table
     * and produce the initial SQL insert statement.
     * <p>
     * While building, the triggers will make sure that the top-level(==1) nodes
     * are always visible. This is critical for some parts of the code which rely on this.
     */
    private static class TableBuilder {

        private static final String INSERT_INTO_ = "INSERT INTO ";
        private static final String _ORDER_BY_ = " ORDER BY ";
        private static final String _AS_ = " AS ";
        private static final String _COLLATION = " COLLATE LOCALIZED";

        private static final String DROP_TRIGGER_IF_EXISTS_ = "DROP TRIGGER IF EXISTS ";

        /** divider to convert nanoseconds to milliseconds. */
        private static final int NANO_TO_MILLIS = 1_000_000;

        /** Supposed to be {@code false}. */
        private final boolean mCollationIsCaseSensitive;

        @NonNull
        private final ListStyle mStyle;

        /** Set to {@code true} if we're filtering on a specific {@link Bookshelf}. */
        private final boolean mFilteredOnBookshelf;

        /** The table we'll be generating. */
        @NonNull
        private final TableDefinition mListTable;

        /**
         * The navigation table we'll be generating.
         * We need this due to SQLite in Android lacking the 3.25 window functions.
         */
        @NonNull
        private final TableDefinition mNavTable;

        /**
         * Domains required in output table.
         * During the build, the {@link Domain} is added to the {@link #mListTable}
         * where they will be used to create the table.
         * <p>
         * The full {@link DomainExpression} is kept this collection to build the SQL column string
         * for both the INSERT and the SELECT statement.
         */
        private final Collection<DomainExpression> mDomainExpressions = new ArrayList<>();

        /** Domains belonging the current group including its outer groups. */
        private final List<Domain> mAccumulatedDomains = new ArrayList<>();

        /**
         * Domains that form part of the sort key.
         * These are typically a reduced set of the group domains since the group domains
         * may contain more than just the key
         */
        private final List<DomainExpression> mOrderByDomains = new ArrayList<>();

        /** Guards from adding duplicates. */
        private final Map<Domain, String> mExpressionsDupCheck = new HashMap<>();

        /** Guards from adding duplicates. */
        private final Collection<String> mOrderByDupCheck = new HashSet<>();

        @NonNull
        private final RebuildBooklist mRebuildMode;

        private String mSqlForInitialInsert;

        /** Table used by the triggers to track the most recent/current row headings. */
        private TableDefinition mTriggerHelperTable;
        /** Trigger name - inserts headers for each level during the initial insert. */
        private String[] mTriggerHelperLevelTriggerName;
        /** Trigger name - maintain the 'current' value during the initial insert. */
        private String mTriggerHelperCurrentValueTriggerName;

        private Collection<TableDefinition> mLeftOuterJoins;

        /**
         * Constructor.
         *
         * @param instanceId            to create a unique table name
         * @param style                 to apply to the list
         * @param isFilteredOnBookshelf whether we're filtering on a specific Bookshelf,
         *                              or if we're using the 'all books'
         * @param rebuildMode           the mode to use for restoring the saved state.
         */
        TableBuilder(final int instanceId,
                     @NonNull final ListStyle style,
                     final boolean isFilteredOnBookshelf,
                     @NonNull final RebuildBooklist rebuildMode) {

            mStyle = style;
            mFilteredOnBookshelf = isFilteredOnBookshelf;
            mRebuildMode = rebuildMode;

            mCollationIsCaseSensitive = ServiceLocator.getInstance().isCollationCaseSensitive();

            /*
             * Temporary table used to store a flattened booklist tree structure.
             * This table should always be created without column constraints applied,
             * with the exception of the "_id" primary key autoincrement
             *
             * Domains are added at runtime depending on how the list is build.
             *
             * We setup the tables here, but don't create them yet.
             */
            mListTable = new TableDefinition("tmp_book_list_" + instanceId)
                    .setAlias("bl")
                    .addDomains(DOM_PK_ID)
                    .setPrimaryKey(DOM_PK_ID);

            mNavTable = new TableDefinition("tmp_book_nav_" + instanceId)
                    .setAlias("nav")
                    .addDomains(DOM_PK_ID, DOM_FK_BOOK, DOM_FK_BL_ROW_ID)
                    .setPrimaryKey(DOM_PK_ID);

            // Allow debug mode to use a standard table so we can export and inspect the content.
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_USES_STANDARD_TABLE) {
                mListTable.setType(TableDefinition.TableType.Standard);
                mNavTable.setType(TableDefinition.TableType.Standard);
            } else {
                mListTable.setType(TableDefinition.TableType.Temporary);
                mNavTable.setType(TableDefinition.TableType.Temporary);
            }
        }

        /**
         * Using the collected domain info, create the various SQL phrases used to build
         * the resulting flat list table and build the SQL that does the initial table load.
         * <p>
         * This method does not access the database.
         *
         * @param context        Current context
         * @param leftOuterJoins tables to be added as a LEFT OUTER JOIN
         * @param bookDomains    list of domains to add on the book level
         * @param filters        to use for the WHERE clause
         **/
        void preBuild(@NonNull final Context context,
                      @NonNull final Collection<TableDefinition> leftOuterJoins,
                      @NonNull final Collection<DomainExpression> bookDomains,
                      @NonNull final Collection<Filter> filters) {

            // Store for later use in #buildFrom
            mLeftOuterJoins = leftOuterJoins;

            // {@link BooklistGroup#GroupKey}.
            // The actual value is set on a by-group/book basis.
            addDomain(DOM_BL_NODE_KEY);
            addIndex(DBKey.KEY_BL_NODE_KEY, false, DOM_BL_NODE_KEY);

            // flags used by {@link BooklistNodeDao}.
            addDomain(DOM_BL_NODE_EXPANDED);
            addIndex(DBKey.KEY_BL_NODE_EXPANDED, false, DOM_BL_NODE_EXPANDED);
            addDomain(DOM_BL_NODE_VISIBLE);
            addIndex(DBKey.KEY_BL_NODE_VISIBLE, false, DOM_BL_NODE_VISIBLE);

            // Always sort by level first; no expression, as this does not represent a value.
            addDomain(new DomainExpression(DOM_BL_NODE_LEVEL, null, DomainExpression.SORT_ASC));

            // The level expression; for a book this is always 1 below the #groups obviously
            addDomain(new DomainExpression(
                    DOM_BL_NODE_LEVEL, String.valueOf(mStyle.getGroups().size() + 1)));

            // The BooklistGroup for a book is always BooklistGroup.BOOK (duh)
            addDomain(new DomainExpression(DOM_BL_NODE_GROUP, String.valueOf(BooklistGroup.BOOK)));
            addIndex(DBKey.KEY_BL_NODE_GROUP, false, DOM_BL_NODE_GROUP);

            // The book id itself
            addDomain(new DomainExpression(DOM_FK_BOOK, TBL_BOOKS.dot(DBKey.PK_ID)));

            // Add style-specified groups
            mStyle.getGroups().getGroupList().forEach(this::addGroup);

            // Add caller-specified domains
            bookDomains.forEach(this::addDomain);

            // List of column names for the INSERT INTO... clause
            final StringBuilder destColumns = new StringBuilder();
            // List of expressions for the SELECT... clause.
            final StringBuilder sourceColumns = new StringBuilder();

            // The 'AS' is for SQL readability/debug only

            boolean first = true;
            for (final DomainExpression domainExpression : mDomainExpressions) {
                if (first) {
                    first = false;
                } else {
                    destColumns.append(',');
                    sourceColumns.append(',');
                }

                destColumns.append(domainExpression.getDomain().getName());
                sourceColumns.append(domainExpression.getExpression())
                             .append(_AS_)
                             .append(domainExpression.getDomain().getName());
            }

            // add the node key column
            destColumns.append(',').append(DBKey.KEY_BL_NODE_KEY);
            sourceColumns.append(',').append(buildNodeKey()).append(_AS_).append(DOM_BL_NODE_KEY);

            // and the node state columns
            destColumns.append(',').append(DOM_BL_NODE_EXPANDED);
            destColumns.append(',').append(DOM_BL_NODE_VISIBLE);

            // PREF_REBUILD_EXPANDED must explicitly be set to 1/1
            // All others must be set to 0/0. The actual state will be set afterwards.
            switch (mRebuildMode) {
                case Collapsed:
                case Preferred:
                case FromSaved:
                    sourceColumns.append(",0 AS ").append(DOM_BL_NODE_EXPANDED);
                    sourceColumns.append(",0 AS ").append(DOM_BL_NODE_VISIBLE);
                    break;

                case Expanded:
                    sourceColumns.append(",1 AS ").append(DOM_BL_NODE_EXPANDED);
                    sourceColumns.append(",1 AS ").append(DOM_BL_NODE_VISIBLE);
                    break;

                default:
                    throw new IllegalArgumentException(String.valueOf(mRebuildMode));
            }

            mSqlForInitialInsert =
                    INSERT_INTO_ + mListTable.getName() + " (" + destColumns + ") "
                    + SELECT_ + sourceColumns
                    + _FROM_ + buildFrom() + buildWhere(context, filters)
                    + _ORDER_BY_ + buildOrderBy();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Log.d(TAG, "build|sql=" + mSqlForInitialInsert);
            }
        }

        /**
         * Create the table and data.
         *
         * @param db Underlying database
         *
         * @return the fully populated list table.
         */
        @NonNull
        Pair<TableDefinition, TableDefinition> build(@NonNull final SynchronizedDb db) {
            if (BuildConfig.DEBUG /* always */) {
                SanityCheck.requireValue(mSqlForInitialInsert, "preBuild() must be called first");

                if (!db.inTransaction()) {
                    throw new TransactionException(TransactionException.REQUIRED);
                }
            }

            //IMPORTANT: withDomainConstraints MUST BE false
            db.recreate(mListTable, false);

            // get the triggers in place, ready to act on our upcoming initial insert.
            createTriggers(db);

            // Build the lowest level (i.e. books) using our initial insert statement
            // The triggers will do the other levels.
            final int initialInsertCount;

            final long t0 = System.nanoTime();

            try (SynchronizedStatement stmt = db.compileStatement(mSqlForInitialInsert)) {
                initialInsertCount = stmt.executeUpdateDelete();
            }
            final long t1_insert = System.nanoTime();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                Log.d(TAG, "build|insert(" + initialInsertCount + "): "
                           + ((t1_insert - t0) / NANO_TO_MILLIS) + " ms");
            }

            if (!mCollationIsCaseSensitive) {
                // can't do this, IndexDefinition class does not support DESC columns for now.
                // mListTable.addIndex("SDI", false, helper.getSortedDomains());
                db.execSQL(
                        "CREATE INDEX " + mListTable.getName() + "_SDI ON " + mListTable.getName()
                        + "(" + getSortedDomainsIndexColumns() + ")");
            }

            // The list table is now fully populated.
            db.analyze(mListTable);

            // remove the no longer needed triggers
            cleanupTriggers(db);

            // Create the navigation table.
            // This is a mapping table between row-id + book-id and a plain sequential id.
            // The latter is needed for a RecyclerView adapter.
            // Don't apply constraints (no need)
            db.recreate(mNavTable, false);
            db.execSQL(INSERT_INTO_ + mNavTable.getName()
                       + " (" + DBKey.FK_BOOK + ',' + DBKey.FK_BL_ROW_ID + ") "
                       + SELECT_ + DBKey.FK_BOOK + ',' + DBKey.PK_ID
                       + _FROM_ + mListTable.getName()
                       + _WHERE_ + DBKey.KEY_BL_NODE_GROUP + "=" + BooklistGroup.BOOK
                       + _ORDER_BY_ + DBKey.PK_ID
                      );

            return new Pair<>(mListTable, mNavTable);
        }

        /**
         * Build a collection of triggers on the list table designed to fill in the summary/header
         * records as the data records are added in sorted order.
         *
         * @param db Database Access
         */
        private void createTriggers(@NonNull final SynchronizedDb db) {

            mTriggerHelperTable = new TableDefinition(mListTable + "_th")
                    .setAlias("tht")
                    .setType(TableDefinition.TableType.Temporary);

            // SQL statement to update the 'current' table
            final StringBuilder valuesColumns = new StringBuilder();
            // List of domain names for sorting
            final Collection<String> sortedDomainNames = new HashSet<>();

            // Build the 'current' header table definition and the sort column list
            mOrderByDomains.stream()
                           .map(DomainExpression::getDomain)
                           // don't add duplicate domains
                           .filter(domain -> !sortedDomainNames.contains(domain.getName()))
                           .forEachOrdered(domain -> {
                               sortedDomainNames.add(domain.getName());
                               if (valuesColumns.length() > 0) {
                                   valuesColumns.append(",");
                               }
                               valuesColumns.append("NEW.").append(domain.getName());

                               mTriggerHelperTable.addDomain(domain);
                           });

            /*
             * Create a temp table to store the most recent header details from the last row.
             * We use this in determining what needs to be inserted as header records for
             * any given row.
             *
             * This is just a simple technique to provide persistent context to the trigger.
             */
            //IMPORTANT: withDomainConstraints MUST BE false
            db.recreate(mTriggerHelperTable, false);

            final int groupCount = mStyle.getGroups().size();

            mTriggerHelperLevelTriggerName = new String[groupCount];

            /*
             * For each grouping, starting with the lowest, build a trigger to update the next
             * level up as necessary. i.o.w. each level has a dedicated trigger.
             */
            for (int index = groupCount - 1; index >= 0; index--) {
                // Get the level number for this group
                final int level = index + 1;

                // Get the group
                final BooklistGroup group = mStyle.getGroups().getGroupByLevel(level);

                // Create the INSERT columns clause for the next level up
                final StringBuilder listColumns = new StringBuilder()
                        .append(DBKey.KEY_BL_NODE_LEVEL)
                        .append(',').append(DBKey.KEY_BL_NODE_GROUP)
                        .append(',').append(DBKey.KEY_BL_NODE_KEY)
                        .append(',').append(DBKey.KEY_BL_NODE_EXPANDED)
                        .append(',').append(DBKey.KEY_BL_NODE_VISIBLE);

                // PREF_REBUILD_EXPANDED must explicitly be set to 1/1
                // All others must be set to 0/0. The actual state will be set afterwards.
                final int expVis = (mRebuildMode == RebuildBooklist.Expanded) ? 1 : 0;

                // Create the VALUES clause for the next level up
                final StringBuilder listValues = new StringBuilder()
                        .append(level)
                        .append(',').append(group.getId())
                        .append(",NEW.").append(DBKey.KEY_BL_NODE_KEY)
                        .append(",").append(expVis)
                        // level 1 is always visible. THIS IS CRITICAL!
                        .append(",").append(level == 1 ? 1 : expVis);

                // Create the where-clause to detect if the next level up is already defined
                // (by checking the 'current' record/table)
                final StringBuilder whereClause = new StringBuilder();

                group.getAccumulatedDomains().stream()
                     .map(Domain::getName)
                     .forEach(domainName -> {
                         listColumns.append(',').append(domainName);
                         listValues.append(",NEW.").append(domainName);

                         // Only add to the where-clause if the group is part of the SORT list
                         if (sortedDomainNames.contains(domainName)) {
                             if (whereClause.length() > 0) {
                                 whereClause.append(_AND_);
                             }
                             whereClause.append("COALESCE(")
                                        .append(mTriggerHelperTable.dot(domainName))
                                        .append(",'')=COALESCE(NEW.")
                                        .append(domainName)
                                        .append(",'')")
                                        .append(_COLLATION);
                         }
                     });

                // (re)Create the trigger
                mTriggerHelperLevelTriggerName[index] = mListTable.getName() + "_TG_LEVEL_" + level;
                db.execSQL(DROP_TRIGGER_IF_EXISTS_ + mTriggerHelperLevelTriggerName[index]);
                final String levelTgSql =
                        "\nCREATE TEMPORARY TRIGGER " + mTriggerHelperLevelTriggerName[index]
                        + " BEFORE INSERT ON " + mListTable.getName() + " FOR EACH ROW"
                        + "\n WHEN NEW." + DBKey.KEY_BL_NODE_LEVEL + '=' + (level + 1)
                        + " AND NOT EXISTS("
                        + /* */ "SELECT 1 FROM " + mTriggerHelperTable.ref() + _WHERE_ + whereClause
                        + /* */ ')'
                        + "\n BEGIN"
                        + "\n  INSERT INTO " + mListTable.getName() + " (" + listColumns + ")"
                        + /*             */ " VALUES(" + listValues + ");"
                        + "\n END";

                db.execSQL(levelTgSql);
                // for references, these look somewhat like this:
                // Level 2 is a "Series", level 1 uses "Title 1st letter"
                //
                // CREATE TEMPORARY TRIGGER tmp_book_list_1_TG_LEVEL_2
                //          BEFORE INSERT ON tmp_book_list_1 FOR EACH ROW
                //     WHEN NEW.node_level=3 AND NOT EXISTS(
                //          SELECT 1 FROM tmp_book_list_1_th AS tht
                //          WHERE COALESCE(tht.blg_tit_let,'')=COALESCE(NEW.blg_tit_let,'')
                //              COLLATE LOCALIZED
                //          AND COALESCE(tht.bl_ser_sort,'')=COALESCE(NEW.bl_ser_sort,'')
                //              COLLATE LOCALIZED
                //          )
                //     BEGIN
                //      INSERT INTO tmp_book_list_1
                //          (node_level,node_group,node_key,node_expanded,node_visible,
                //              blg_tit_let,
                //              series_name,bl_ser_sort,series_id,series_complete)
                //          VALUES(2,2,NEW.node_key,0,0,
                //              NEW.blg_tit_let,
                //              NEW.series_name,NEW.bl_ser_sort,NEW.series_id,NEW.series_complete);
                //     END
                //
                // CREATE TEMPORARY TRIGGER tmp_book_list_1_TG_LEVEL_1
                //          BEFORE INSERT ON tmp_book_list_1 FOR EACH ROW
                //     WHEN NEW.node_level=2 AND NOT EXISTS(
                //          SELECT 1 FROM tmp_book_list_1_th AS tht
                //          WHERE COALESCE(tht.blg_tit_let,'')=COALESCE(NEW.blg_tit_let,'')
                //              COLLATE LOCALIZED
                //          )
                //     BEGIN
                //      INSERT INTO tmp_book_list_1
                //          (node_level,node_group,node_key,node_expanded,node_visible,
                //              blg_tit_let)
                //          VALUES(1,9,NEW.node_key,0,1,
                //              NEW.blg_tit_let);
                //     END
            }

            // Create a trigger to maintain the 'current' value
            mTriggerHelperCurrentValueTriggerName = mListTable.getName() + "_TG_CURRENT";
            db.execSQL(DROP_TRIGGER_IF_EXISTS_ + mTriggerHelperCurrentValueTriggerName);
            // This is a single row only, so delete the previous value, and insert the current one
            final String currentValueTgSql =
                    "\nCREATE TEMPORARY TRIGGER " + mTriggerHelperCurrentValueTriggerName
                    + " AFTER INSERT ON " + mListTable.getName() + " FOR EACH ROW"
                    + "\n WHEN NEW." + DBKey.KEY_BL_NODE_LEVEL + '=' + groupCount
                    + "\n BEGIN"
                    + "\n  DELETE FROM " + mTriggerHelperTable.getName() + ';'
                    + "\n  INSERT INTO " + mTriggerHelperTable.getName()
                    + /*              */ " VALUES (" + valuesColumns + ");"
                    + "\n END";

            db.execSQL(currentValueTgSql);
        }

        /**
         * Drop the triggers and related table.
         *
         * @param db Database Access
         */
        private void cleanupTriggers(@NonNull final SynchronizedDb db) {
            if (mTriggerHelperCurrentValueTriggerName != null) {
                db.execSQL(DROP_TRIGGER_IF_EXISTS_ + mTriggerHelperCurrentValueTriggerName);
            }
            if (mTriggerHelperLevelTriggerName != null) {
                Arrays.stream(mTriggerHelperLevelTriggerName)
                      .map(name -> DROP_TRIGGER_IF_EXISTS_ + name)
                      .forEach(db::execSQL);
            }
            if (mTriggerHelperTable != null) {
                db.drop(mTriggerHelperTable.getName());
            }
        }

        /**
         * Add a simple domain for the lowest level; i.e. the book.
         *
         * @param domain to add
         */
        @SuppressWarnings("UnusedReturnValue")
        private boolean addDomain(@NonNull final Domain domain) {
            // Add to the table, if not already there
            return mListTable.addDomain(domain);
        }

        private void addIndex(@NonNull final String nameSuffix,
                              @SuppressWarnings("SameParameterValue") final boolean unique,
                              @NonNull final Domain... domains) {
            mListTable.addIndex(nameSuffix, unique, domains);
        }

        /**
         * Add a DomainExpression.
         * This encapsulates the actual Domain, the expression, and the (optional) sort flag.
         *
         * @param domainExpression DomainExpression to add
         */
        private void addDomain(@NonNull final DomainExpression domainExpression) {
            final Domain domain = domainExpression.getDomain();
            @Nullable
            final String expression = domainExpression.getExpression();

            // Add to the table, if not already there
            final boolean added = mListTable.addDomain(domain);

            // If the expression is {@code null},
            // then the domain is just meant for the lowest level; i.e. the book.
            // otherwise, we check and add it here
            if (expression != null) {
                // If the domain was already present, and it has an expression,
                // check the expression being different (or not) from the stored expression
                if (!added && expression.equals(mExpressionsDupCheck.get(domain))) {
                    // same expression, we do NOT want to add it.
                    // This is NOT a bug, although one could argue it's an efficiency issue.
                    return;
                }

                mDomainExpressions.add(domainExpression);
                mExpressionsDupCheck.put(domain, expression);
            }

            // If required, add to the order-by domains, if not already there
            if (domainExpression.isSorted() && !mOrderByDupCheck.contains(domain.getName())) {
                mOrderByDomains.add(domainExpression);
                mOrderByDupCheck.add(domain.getName());
            }
        }

        /**
         * Add the domains for the given group.
         *
         * @param group to add
         */
        private void addGroup(@NonNull final BooklistGroup group) {
            // dev sanity check
            if (BuildConfig.DEBUG /* always */) {
                if (group.getId() == BooklistGroup.BOOK) {
                    throw new IllegalArgumentException("Cannot group by Book");
                }
            }

            // Do this in 3 steps, allowing groups to override their parent (if any) group

            // display domain first
            final DomainExpression displayDomainExpression = group.getDisplayDomainExpression();
            addDomain(displayDomainExpression);
            mAccumulatedDomains.add(displayDomainExpression.getDomain());

            // then how we group
            group.getGroupDomainExpressions().forEach(domainExpression -> {
                addDomain(domainExpression);
                mAccumulatedDomains.add(domainExpression.getDomain());
            });

            // the base domains we always need/have
            group.getBaseDomainExpressions().forEach(this::addDomain);

            /*
             * Copy all current groups to this group; this effectively accumulates
             * 'GROUP BY' domains down each level so that the top has fewest groups and
             * the bottom level has groups for all levels.
             *
             * Since BooklistGroup objects are processed in order, this allows us to get
             * the fields applicable to the currently processed group, including its outer groups.
             *
             * As subsequent addGroup calls will modify this collection,
             * we make a (shallow) copy of the list.
             */
            group.setAccumulatedDomains(new ArrayList<>(mAccumulatedDomains));
        }

        /**
         * Create the expression for the key column.
         * Will contain one key-value pair for each each group level.
         * "/key=value/key=value/key=value/..."
         * A {@code null} value is reformatted as an empty string
         * <p>
         * Dev. Note: this is an SQL expression, the "||" operator being a string 'concat'
         * during the SQL execution.
         *
         * @return column expression
         */
        @NonNull
        private String buildNodeKey() {
            return mStyle.getGroups().getGroupList()
                         .stream()
                         .map(BooklistGroup::getNodeKeyExpression)
                         .collect(Collectors.joining("||"));
        }

        /**
         * Create the FROM clause based on the {@link BooklistGroup}s and extra criteria.
         * <ul>Always joined are:
         *      <li>{@link DBDefinitions#TBL_BOOK_AUTHOR}
         *      + {@link DBDefinitions#TBL_AUTHORS}</li>
         * </ul>
         * <ul>Optionally joined with:
         *      <li>{@link DBDefinitions#TBL_BOOK_SERIES}
         *      + {@link DBDefinitions#TBL_SERIES}</li>
         *      <li>{@link DBDefinitions#TBL_BOOK_PUBLISHER}
         *      + {@link DBDefinitions#TBL_PUBLISHERS}</li>
         *      <li>{@link DBDefinitions#TBL_BOOK_BOOKSHELF}
         *      + {@link DBDefinitions#TBL_BOOKSHELF}</li>
         *      <li>{@link #mLeftOuterJoins}</li>
         * </ul>
         *
         * @return FROM clause
         */
        @NonNull
        private String buildFrom() {
            final SharedPreferences global = ServiceLocator.getGlobalPreferences();
            // Text of join statement
            final StringBuilder sql = new StringBuilder();

            final Groups styleGroups = mStyle.getGroups();

            // If there is a bookshelf specified (either as group or as a filter),
            // we start the join there.
            if (styleGroups.contains(BooklistGroup.BOOKSHELF) || mFilteredOnBookshelf) {
                sql.append(TBL_BOOKSHELF.startJoin(TBL_BOOK_BOOKSHELF, TBL_BOOKS));
            } else {
                // Otherwise, we start with the BOOKS table.
                sql.append(TBL_BOOKS.ref());
            }

            joinWithAuthors(sql);

            if (styleGroups.contains(BooklistGroup.SERIES)
                || DBKey.isUsed(global, DBKey.KEY_SERIES_TITLE)) {
                joinWithSeries(sql);
            }

            if (styleGroups.contains(BooklistGroup.PUBLISHER)
                || DBKey.isUsed(global, DBKey.KEY_PUBLISHER_NAME)) {
                joinWithPublishers(sql);
            }

            // Add LEFT OUTER JOIN tables as needed
            mLeftOuterJoins.forEach(table -> sql.append(TBL_BOOKS.leftOuterJoin(table)));

            return sql.toString();
        }

        private void joinWithAuthors(@NonNull final StringBuilder sql) {
            // Join with the link table between Book and Author.
            sql.append(TBL_BOOKS.join(TBL_BOOK_AUTHOR));
            // Extend the join filtering on the primary Author unless
            // the user wants the book to show under all its Authors
            if (!mStyle.isShowBooksUnderEachAuthor()) {
                @Author.Type
                final int primaryAuthorType = mStyle.getPrimaryAuthorType();
                if (primaryAuthorType == Author.TYPE_UNKNOWN) {
                    // don't care about Author type, so just grab the first one (i.e. pos==1)
                    sql.append(_AND_)
                       .append(TBL_BOOK_AUTHOR.dot(DBKey.KEY_BOOK_AUTHOR_POSITION))
                       .append("=1");
                } else {
                    // grab the desired type, or if no such type, grab the first one
                    //   AND (((type & TYPE)<>0) OR (((type &~ TYPE)=0) AND pos=1))
                    sql.append(" AND (((")
                       .append(TBL_BOOK_AUTHOR.dot(DBKey.KEY_BOOK_AUTHOR_TYPE_BITMASK))
                       .append(" & ").append(primaryAuthorType).append(")<>0)")
                       .append(" OR (((")
                       .append(TBL_BOOK_AUTHOR.dot(DBKey.KEY_BOOK_AUTHOR_TYPE_BITMASK))
                       .append(" &~ ").append(primaryAuthorType).append(")=0)")
                       .append(_AND_)
                       .append(TBL_BOOK_AUTHOR.dot(DBKey.KEY_BOOK_AUTHOR_POSITION))
                       .append("=1))");
                }
            }
            // Join with Authors to make the names available
            sql.append(TBL_BOOK_AUTHOR.join(TBL_AUTHORS));
        }

        private void joinWithSeries(@NonNull final StringBuilder sql) {
            // Join with the link table between Book and Series.
            sql.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_SERIES));
            // Extend the join filtering on the primary Series unless
            // the user wants the book to show under all its Series
            if (!mStyle.isShowBooksUnderEachSeries()) {
                sql.append(_AND_)
                   .append(TBL_BOOK_SERIES.dot(DBKey.KEY_BOOK_SERIES_POSITION))
                   .append("=1");
            }
            // Join with Series to make the titles available
            sql.append(TBL_BOOK_SERIES.leftOuterJoin(TBL_SERIES));
        }

        private void joinWithPublishers(@NonNull final StringBuilder sql) {
            // Join with the link table between Book and Publishers.
            sql.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_PUBLISHER));
            // Extend the join filtering on the primary Publisher unless
            // the user wants the book to show under all its Publishers
            if (!mStyle.isShowBooksUnderEachPublisher()) {
                sql.append(_AND_)
                   .append(TBL_BOOK_PUBLISHER.dot(DBKey.KEY_BOOK_PUBLISHER_POSITION))
                   .append("=1");
            }
            // Join with Publishers to make the names available
            sql.append(TBL_BOOK_PUBLISHER.leftOuterJoin(TBL_PUBLISHERS));
        }

        /**
         * Create the WHERE clause based on all active filters (for in-use domains).
         *
         * @param context Current context
         * @param filters to apply
         *
         * @return WHERE clause, can be empty
         */
        @NonNull
        private String buildWhere(@NonNull final Context context,
                                  @NonNull final Collection<Filter> filters) {
            final StringBuilder where = new StringBuilder();

            filters.stream()
                   // ONLY APPLY ACTIVE FILTERS!
                   .filter(filter -> filter.isActive(context))
                   .map(filter -> filter.getExpression(context))
                   // sanity checks
                   .filter(Objects::nonNull)
                   .filter(expression -> !expression.isEmpty())
                   .forEach(expression -> {
                       if (where.length() != 0) {
                           where.append(_AND_);
                       }
                       where.append(' ').append(expression);
                   });

            if (where.length() > 0) {
                return _WHERE_ + where;
            } else {
                return "";
            }
        }

        /**
         * Process the 'sort-by' columns into a list suitable for an ORDER-BY statement.
         *
         * @return ORDER BY clause
         */
        @NonNull
        private String buildOrderBy() {
            // List of column names appropriate for 'ORDER BY' clause
            final StringBuilder orderBy = new StringBuilder();
            mOrderByDomains.forEach(sd -> {
                final Domain domain = sd.getDomain();
                if (domain.isText()) {
                    // The order of this if/elseif/else is important, don't merge branch 1 and 3!
                    if (domain.isPrePreparedOrderBy()) {
                        // always use a pre-prepared order-by column as-is
                        orderBy.append(domain.getName());

                    } else if (mCollationIsCaseSensitive) {
                        // If {@link DAO#COLLATION} is case-sensitive, lowercase it.
                        // This should never happen, but see the DAO method docs.
                        orderBy.append("lower(").append(domain.getName()).append(')');

                    } else {
                        // hope for the best. This case might not handle non-[A..Z0..9] as expected
                        orderBy.append(domain.getName());
                    }

                    orderBy.append(_COLLATION);
                } else {
                    orderBy.append(domain.getName());
                }
                orderBy.append(sd.getSortedExpression()).append(',');
            });

            final int len = orderBy.length();
            return orderBy.delete(len - 1, len).toString();
        }

        /**
         * Process the 'sort-by' columns into a list suitable for an CREATE-INDEX statement.
         *
         * @return column list clause
         */
        @NonNull
        private String getSortedDomainsIndexColumns() {
            final StringBuilder indexCols = new StringBuilder();

            mOrderByDomains.forEach(sd -> {
                final Domain domain = sd.getDomain();
                indexCols.append(domain.getName());
                if (domain.isText()) {
                    indexCols.append(_COLLATION);
                }
                indexCols.append(sd.getSortedExpression()).append(',');
            });
            final int len = indexCols.length();
            return indexCols.delete(len - 1, len).toString();
        }
    }
}
