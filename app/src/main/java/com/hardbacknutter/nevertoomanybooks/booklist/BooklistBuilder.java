/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PSEUDONYM_AUTHOR;
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

    /** Foreign key between the list and navigation table. */
    static final String FK_BL_ROW_ID = "bl_row_id";

    /** Log tag. */
    private static final String TAG = "BooklistBuilder";

    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";

    /**
     * Foreign key between the {@link Booklist} list table
     * and the {@link Booklist} navigator table
     * as used in the ViewPager displaying individual books.
     */
    private static final Domain DOM_FK_BL_ROW_ID;

    /**
     * Counter for generating ID's. Only increments.
     * Used to create unique names for the temporary tables.
     */
    @NonNull
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    private static final String _AND_ = " AND ";

    static {
        DOM_FK_BL_ROW_ID =
                new Domain.Builder(FK_BL_ROW_ID, SqLiteDataType.Integer)
                        .notNull()
                        .build();
    }

    /** Style to use while building the list. */
    @NonNull
    private final Style style;

    /** Show only books on this bookshelf. */
    @NonNull
    private final Bookshelf bookshelf;

    @NonNull
    private final Map<String, TableDefinition> leftOuterJoins = new HashMap<>();

    /** Collection of 'extra' book level domains requested by caller. */
    @NonNull
    private final Map<String, DomainExpression> bookDomains = new HashMap<>();

    /** the list of Filters. */
    private final Collection<Filter> filters = new ArrayList<>();
    @NonNull
    private final SynchronizedDb db;

    @NonNull
    private RebuildBooklist rebuildMode;

    /**
     * Constructor.
     *
     * @param db          Underlying database
     * @param style       Style reference.
     *                    This is the resolved style as used by the passed bookshelf
     * @param bookshelf   the current bookshelf
     * @param rebuildMode booklist mode to use in next rebuild.
     */
    BooklistBuilder(@NonNull final SynchronizedDb db,
                    @NonNull final Style style,
                    @NonNull final Bookshelf bookshelf,
                    @NonNull final RebuildBooklist rebuildMode) {
        this.db = db;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            LoggerFactory.getLogger().d(TAG, "BooklistBuilder",
                                        "style=" + style.getUuid(),
                                        "bookshelf=`" + bookshelf.getName() + '`',
                                        "instances: "
                                        + Booklist.DEBUG_INSTANCE_COUNTER.incrementAndGet(),
                                        new Throwable());
        }

        this.style = style;
        this.bookshelf = bookshelf;
        this.rebuildMode = rebuildMode;
    }

    /**
     * Add a table to be added as a LEFT OUTER JOIN.
     *
     * @param tableDefinition TableDefinition to add
     *
     * @throws IllegalArgumentException if the table was already added
     */
    void addLeftOuterJoin(@SuppressWarnings("SameParameterValue")
                          @NonNull final TableDefinition tableDefinition) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            if (leftOuterJoins.containsKey(tableDefinition.getName())) {
                // adding a duplicate here is a bug.
                throw new IllegalArgumentException("Duplicate table=" + tableDefinition.getName());
            }
        }
        leftOuterJoins.put(tableDefinition.getName(), tableDefinition);
    }

    /**
     * Add a domain to the resulting flattened list based on the details provided.
     *
     * @param domainExpression Domain to add
     *
     * @throws IllegalArgumentException if the domain was already added
     */
    void addDomain(@NonNull final DomainExpression domainExpression) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            final String key = domainExpression.getDomain().getName();
            if (bookDomains.containsKey(key)) {
                // adding a duplicate here is a bug.
                throw new IllegalArgumentException("Duplicate domain=" + key);
            }
        }
        bookDomains.put(domainExpression.getDomain().getName(), domainExpression);
    }

    /**
     * Add a filter to use in the WHERE clause.
     *
     * @param filter to add
     */
    void addFilter(@NonNull final Filter filter) {
        filters.add(filter);
    }

    /**
     * Add a list of filter to use in the WHERE clause.
     *
     * @param filters to add
     */
    void addFilter(@NonNull final List<Filter> filters) {
        this.filters.addAll(filters);
    }

    /**
     * Allows to override the build state set in the constructor.
     *
     * @param rebuildMode booklist state to use in next rebuild.
     */
    void setRebuildMode(@SuppressWarnings("SameParameterValue")
                        @NonNull final RebuildBooklist rebuildMode) {
        this.rebuildMode = rebuildMode;
    }

    /**
     * Clear and build the temporary list of books.
     *
     * @param context Current context
     *
     * @return the Booklist ready to use
     *
     * @throws IllegalArgumentException if the rebuild-mode is unknown
     */
    @NonNull
    public Booklist build(@NonNull final Context context) {
        final int instanceId = ID_COUNTER.incrementAndGet();

        final TableBuilder tableBuilder = new TableBuilder(
                instanceId, style, bookshelf, rebuildMode);

        final Synchronizer.SyncLock txLock = db.beginTransaction(true);
        try {
            // Construct the list table and all needed structures.
            final Pair<TableDefinition, TableDefinition> tables = tableBuilder
                    .build(context, db, leftOuterJoins.values(), bookDomains.values(), filters);

            final TableDefinition listTable = tables.first;
            final TableDefinition navTable = tables.second;
            final BooklistNodeDao rowStateDAO =
                    new BooklistNodeDao(db, listTable, style, bookshelf);

            switch (rebuildMode) {
                case FromSaved:
                    // all rows will be collapsed/hidden; restore the saved state
                    rowStateDAO.restoreSavedState();
                    break;

                case Preferred:
                    // all rows will be collapsed/hidden; now adjust as required.
                    rowStateDAO.setAllNodes(style.getExpansionLevel(), false);
                    break;

                case Expanded:
                case Collapsed:
                    // handled during table creation
                    break;

                default:
                    throw new IllegalArgumentException(String.valueOf(rebuildMode));
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

        private static final String DROP_TRIGGER_IF_EXISTS_ = "DROP TRIGGER IF EXISTS ";

        /** divider to convert nanoseconds to milliseconds. */
        private static final int NANO_TO_MILLIS = 1_000_000;

        @NonNull
        private final Style style;

        /** Set to {@code true} if we're filtering on a specific {@link Bookshelf}. */
        private final boolean filteredOnBookshelf;

        /** The table we'll be generating. */
        @NonNull
        private final TableDefinition listTable;

        /**
         * The navigation table we'll be generating.
         * We need this due to SQLite in Android lacking the 3.25 window functions.
         */
        @NonNull
        private final TableDefinition navTable;

        /**
         * Domains required in output table.
         * During the build, the {@link Domain} is added to the {@link #listTable}
         * where they will be used to <strong>create</strong> the table.
         * <p>
         * The full {@link DomainExpression} is added to this collection to build the
         * SQL column string for both the INSERT and the SELECT statement.
         */
        private final Collection<DomainExpression> domainExpressions = new ArrayList<>();

        /** Domains belonging the current group including its outer groups. */
        private final List<Domain> accumulatedDomains = new ArrayList<>();

        /**
         * Domains that form part of the sort key.
         * These are typically a reduced set of the group domains since the group domains
         * may contain more than just the key
         */
        private final List<DomainExpression> orderByDomainExpressions = new ArrayList<>();

        /** Guards from adding duplicates. */
        private final Map<Domain, String> expressionsDupCheck = new HashMap<>();

        /** Guards from adding duplicates. */
        private final Collection<String> orderByDupCheck = new HashSet<>();

        @NonNull
        private final RebuildBooklist rebuildMode;

        /** Table used by the triggers to track the most recent/current row headings. */
        private TableDefinition triggerHelperTable;
        /** Trigger name - inserts headers for each level during the initial insert. */
        private String[] triggerHelperLevelTriggerName;
        /** Trigger name - maintain the 'current' value during the initial insert. */
        private String triggerHelperCurrentValueTriggerName;

        /**
         * Constructor.
         *
         * @param instanceId  to create a unique table name
         * @param style       to apply to the list
         * @param bookshelf   to display
         * @param rebuildMode the mode to use for restoring the saved state.
         */
        TableBuilder(final int instanceId,
                     @NonNull final Style style,
                     @NonNull final Bookshelf bookshelf,
                     @NonNull final RebuildBooklist rebuildMode) {

            this.style = style;
            // whether we're filtering on a specific Bookshelf,
            // or if we're using the 'all books'
            filteredOnBookshelf = bookshelf.getId() != Bookshelf.ALL_BOOKS;
            this.rebuildMode = rebuildMode;

            /*
             * Temporary table used to store a flattened booklist tree structure.
             * This table should always be created without column constraints applied,
             * with the exception of the "_id" primary key autoincrement
             *
             * Domains are added at runtime depending on how the list is build.
             *
             * We setup the tables here, but don't create them yet.
             */
            listTable = new TableDefinition("tmp_book_list_" + instanceId, "bl")
                    .addDomains(DBDefinitions.DOM_PK_ID)
                    .setPrimaryKey(DBDefinitions.DOM_PK_ID);

            navTable = new TableDefinition("tmp_book_nav_" + instanceId, "nav")
                    .addDomains(DBDefinitions.DOM_PK_ID, DBDefinitions.DOM_FK_BOOK,
                                DOM_FK_BL_ROW_ID)
                    .setPrimaryKey(DBDefinitions.DOM_PK_ID);

            // Allow debug mode to use a standard table so we can export and inspect the content.
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_USES_STANDARD_TABLE) {
                listTable.setType(TableDefinition.TableType.Standard);
                navTable.setType(TableDefinition.TableType.Standard);
            } else {
                listTable.setType(TableDefinition.TableType.Temporary);
                navTable.setType(TableDefinition.TableType.Temporary);
            }
        }

        /**
         * Using the collected domain info, create the various SQL phrases used to build
         * the resulting flat list table and build the SQL that does the initial table load.
         *
         * @param context        Current context
         * @param db             Underlying database
         * @param leftOuterJoins tables to be added as a LEFT OUTER JOIN
         * @param bookDomains    list of domains to add on the book level
         * @param filters        to use for the WHERE clause
         *
         * @return a Pair with the fully populated list-table and the navigation-table
         */
        @NonNull
        Pair<TableDefinition, TableDefinition> build(
                @NonNull final Context context,
                @NonNull final SynchronizedDb db,
                @NonNull final Collection<TableDefinition> leftOuterJoins,
                @NonNull final Collection<DomainExpression> bookDomains,
                @NonNull final Collection<Filter> filters) {

            // {@link BooklistGroup#GroupKey}.
            // The actual value is set on a by-group/book basis.
            listTable.addDomains(DBDefinitions.DOM_BL_NODE_KEY)
                     .addIndex(DBKey.BL_NODE_KEY, false, DBDefinitions.DOM_BL_NODE_KEY);

            // flags used by {@link BooklistNodeDao}.
            listTable.addDomains(DBDefinitions.DOM_BL_NODE_EXPANDED)
                     .addIndex(DBKey.BL_NODE_EXPANDED, false,
                               DBDefinitions.DOM_BL_NODE_EXPANDED)
                     .addDomains(DBDefinitions.DOM_BL_NODE_VISIBLE)
                     .addIndex(DBKey.BL_NODE_VISIBLE, false,
                               DBDefinitions.DOM_BL_NODE_VISIBLE);

            // Always sort by level first; no expression, as this does not represent a value.
            addDomainExpression(new DomainExpression(DBDefinitions.DOM_BL_NODE_LEVEL, Sort.Asc));

            // The level expression; for a book this is always 1 below the #groups obviously
            addDomainExpression(new DomainExpression(DBDefinitions.DOM_BL_NODE_LEVEL,
                                                     String.valueOf(style.getGroupCount() + 1),
                                                     Sort.Unsorted));

            // The BooklistGroup for a book is always BooklistGroup.BOOK (duh)
            addDomainExpression(new DomainExpression(DBDefinitions.DOM_BL_NODE_GROUP,
                                                     String.valueOf(BooklistGroup.BOOK),
                                                     Sort.Unsorted));
            listTable.addIndex(DBKey.BL_NODE_GROUP, false, DBDefinitions.DOM_BL_NODE_GROUP);

            // The book id itself
            addDomainExpression(new DomainExpression(DBDefinitions.DOM_FK_BOOK,
                                                     TBL_BOOKS.dot(DBKey.PK_ID),
                                                     Sort.Unsorted));

            // Add style-specified groups
            style.getGroupList().forEach(this::addGroup);

            // Add caller-specified domains
            bookDomains.forEach(this::addDomainExpression);

            // List of column names for the INSERT INTO... clause
            final StringJoiner destColumns = new StringJoiner(",");
            // List of expressions for the SELECT... clause.
            final StringJoiner sourceColumns = new StringJoiner(",");

            // Add the domain expressions
            domainExpressions.forEach(expression -> {
                destColumns.add(expression.getDomain().getName());
                sourceColumns.add(expression.getExpression()
                                  + _AS_ + expression.getDomain().getName());
            });

            // Add the node key column
            destColumns.add(DBDefinitions.DOM_BL_NODE_KEY.getName());
            sourceColumns.add(buildNodeKey() + _AS_ + DBDefinitions.DOM_BL_NODE_KEY.getName());

            // Add the node state columns
            destColumns.add(DBDefinitions.DOM_BL_NODE_EXPANDED.getName());
            destColumns.add(DBDefinitions.DOM_BL_NODE_VISIBLE.getName());
            if (rebuildMode == RebuildBooklist.Expanded) {
                // Expanded nodes must explicitly be set to 1/1
                sourceColumns.add("1" + _AS_ + DBDefinitions.DOM_BL_NODE_EXPANDED.getName())
                             .add("1" + _AS_ + DBDefinitions.DOM_BL_NODE_VISIBLE.getName());

            } else {
                // All others must be set to 0/0. The actual state will be set afterwards.
                sourceColumns.add("0" + _AS_ + DBDefinitions.DOM_BL_NODE_EXPANDED.getName())
                             .add("0" + _AS_ + DBDefinitions.DOM_BL_NODE_VISIBLE.getName());
            }

            final String sqlForInitialInsert =
                    INSERT_INTO_ + listTable.getName() + " (" + destColumns + ") "
                    + SELECT_ + sourceColumns
                    + _FROM_ + buildFrom(leftOuterJoins) + buildWhere(context, filters)
                    + _ORDER_BY_ + buildOrderBy(db.isCollationCaseSensitive());

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                LoggerFactory.getLogger()
                             .d(TAG, "build", "sqlForInitialInsert=" + sqlForInitialInsert);

                if (!db.inTransaction()) {
                    throw new TransactionException(TransactionException.REQUIRED);
                }
            }

            // All structures are in place now
            // Create the list table and populate it.

            //IMPORTANT: withDomainConstraints MUST BE false
            db.recreate(listTable, false);

            // get the triggers in place, ready to act on our upcoming initial insert.
            createTriggers(db);

            // Build the lowest level (i.e. books) using our initial insert statement
            // The triggers will do the other levels.
            final int initialInsertCount;

            final long t0 = System.nanoTime();

            try (SynchronizedStatement stmt = db.compileStatement(sqlForInitialInsert)) {
                initialInsertCount = stmt.executeUpdateDelete();
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                // only measure the insert... all other operations are very fast compared to it
                LoggerFactory.getLogger().d(TAG, "build",
                                            "insert(" + initialInsertCount + "): "
                                            + ((System.nanoTime() - t0) / NANO_TO_MILLIS) + " ms");
            }

            if (!db.isCollationCaseSensitive()) {
                // can't use IndexDefinition class as it does not support sorting clause for now.
                final String indexCols = orderByDomainExpressions
                        .stream()
                        .map(de -> de.getDomain().getOrderByString(
                                de.getSort(), db.isCollationCaseSensitive()))
                        .collect(Collectors.joining(",", "(", ")"));

                db.execSQL("CREATE INDEX " + listTable.getName() + "_SDI ON "
                           + listTable.getName() + indexCols);
            }

            // The list table is now fully populated.
            db.analyze(listTable);

            // remove the no longer needed triggers
            cleanupTriggers(db);

            // Create the navigation table.
            // This is a mapping table between row-id + book-id and a plain sequential id.
            // The latter is needed for a RecyclerView adapter.
            // Don't apply constraints (no need)
            db.recreate(navTable, false);
            db.execSQL(INSERT_INTO_ + navTable.getName()
                       + " (" + DBKey.FK_BOOK + ',' + FK_BL_ROW_ID + ") "
                       + SELECT_ + DBKey.FK_BOOK + ',' + DBKey.PK_ID
                       + _FROM_ + listTable.getName()
                       + _WHERE_ + DBKey.BL_NODE_GROUP + "=" + BooklistGroup.BOOK
                       + _ORDER_BY_ + DBKey.PK_ID);

            return new Pair<>(listTable, navTable);
        }

        /**
         * Build a collection of triggers on the list table designed to fill in the summary/header
         * records as the data records are added in sorted order.
         * <p>
         * <strong>IMPORTANT</strong>: when creating the VALUES clause for the next level up,
         * the node key for the level 'up' will contain ALL levels.
         * This is of course <strong>INCORRECT</strong>... but SQLite does not have
         * enough string functions to split "NEW.node_key" and only store the actual
         * amount of key=value pairs we need.
         * When we need to get a list of books for a specific node, we manually split the
         * node key down to the required level and use a trailing '%' to get what we need.
         * <p>
         * Example of actual list table content:
         * <pre>{@code
         *     key                          level   group
         *     /a=58/s=/p=32/yrp=1990/          1   1 AUTHOR
         *     /a=58/s=/p=32/yrp=1990/          2   2 SERIES (no series)
         *     /a=58/s=/p=32/yrp=1990/          3   4 PUBLISHER
         *     /a=58/s=/p=32/yrp=1990/          4   7 DATE_PUBLISHED_YEAR
         *     /a=58/s=/p=32/yrp=1990/          5   0 434 book
         * }
         * </pre>
         * <p>
         * Ideally, it should be:
         * <pre>{@code
         *     key                          level   group
         *     /a=58/                           1   1 AUTHOR
         *     /a=58/s=/                        2   2 SERIES (no series)
         *     /a=58/s=/p=32/                   3   4 PUBLISHER
         *     /a=58/s=/p=32/yrp=1990/          4   7 DATE_PUBLISHED_YEAR
         *     /a=58/s=/p=32/yrp=1990/b=434/    5   0 434 book
         * }
         * </pre>
         * In other words, books should have their own key=value pair added when building
         * the table content and the trigger for each level-up should cut off
         * the last key=value pair.
         *
         * @param db Database Access
         *
         * @see Booklist#getBookIdsForNodeKey
         * @see Booklist#ensureNodeIsVisible(BooklistNode)
         */
        @SuppressWarnings("JavadocReference")
        private void createTriggers(@NonNull final SynchronizedDb db) {

            triggerHelperTable = new TableDefinition(listTable + "_th", "tht")
                    .setType(TableDefinition.TableType.Temporary);

            // VALUES clause to update the 'current' table
            final StringJoiner currentValues = new StringJoiner(",", "(", ")");
            // List of domain names for sorting
            final Collection<String> sortedDomainNames = new HashSet<>();

            // Build the 'current' header table definition and the sort column list
            orderByDomainExpressions.stream()
                                    .map(DomainExpression::getDomain)
                                    // don't add duplicate domains
                                    .filter(domain -> !sortedDomainNames.contains(domain.getName()))
                                    .forEachOrdered(domain -> {
                                        sortedDomainNames.add(domain.getName());
                                        currentValues.add("NEW." + domain.getName());

                                        triggerHelperTable.addDomains(domain);
                                    });

            /*
             * Create a temp table to store the most recent header details from the last row.
             * We use this in determining what needs to be inserted as header records for
             * any given row.
             *
             * This is just a simple technique to provide persistent context to the trigger.
             */
            //IMPORTANT: withDomainConstraints MUST BE false
            db.recreate(triggerHelperTable, false);

            final int groupCount = style.getGroupCount();

            triggerHelperLevelTriggerName = new String[groupCount];

            /*
             * For each grouping, starting with the lowest, build a trigger to update the next
             * level up as necessary. i.o.w. each level has a dedicated trigger.
             */
            for (int index = groupCount - 1; index >= 0; index--) {
                // Get the level number for this group
                final int level = index + 1;

                // Get the group
                final BooklistGroup group = style.getGroupByLevel(level);

                // Create the INSERT columns clause for the next level up
                final StringJoiner listColumns = new StringJoiner(",", "(", ")")
                        .add(DBKey.BL_NODE_LEVEL)
                        .add(DBKey.BL_NODE_GROUP)
                        .add(DBKey.BL_NODE_KEY)
                        .add(DBKey.BL_NODE_EXPANDED)
                        .add(DBKey.BL_NODE_VISIBLE);

                // RebuildBooklist.Expanded must explicitly be set to 1/1
                // All others must be set to 0/0. The actual state will be set afterwards.
                final String expVis = (rebuildMode == RebuildBooklist.Expanded) ? "1" : "0";

                // Create the VALUES clause for the next level up
                final StringJoiner listValues = new StringJoiner(",", "(", ")")
                        .add(String.valueOf(level))
                        .add(String.valueOf(group.getId()))
                        .add("NEW." + DBKey.BL_NODE_KEY)
                        .add(expVis)
                        // level 1 is always visible. THIS IS CRITICAL!
                        .add(level == 1 ? "1" : expVis);

                // Create the where-clause to detect if the next level up is already defined
                // (by checking the 'current' record/table)
                final StringJoiner whereClause = new StringJoiner(_AND_);

                group.getAccumulatedDomains()
                     .forEach(domain -> {
                         final String domainName = domain.getName();
                         listColumns.add(domainName);
                         listValues.add("NEW." + domainName);

                         // Only add to the where-clause if the group is part of the SORT list
                         if (sortedDomainNames.contains(domainName)) {
                             whereClause.add(
                                     "COALESCE(" + triggerHelperTable.dot(domainName) + ",'')"
                                     + "=COALESCE(NEW." + domainName + ",'')"
                                     + domain.getCollationClause());
                         }
                     });

                // (re)Create the trigger
                triggerHelperLevelTriggerName[index] = listTable.getName() + "_TG_LEVEL_" + level;
                db.execSQL(DROP_TRIGGER_IF_EXISTS_ + triggerHelperLevelTriggerName[index]);
                final String levelTgSql =
                        "CREATE TEMPORARY TRIGGER " + triggerHelperLevelTriggerName[index]
                        + " BEFORE INSERT ON " + listTable.getName() + " FOR EACH ROW"
                        + " WHEN NEW." + DBKey.BL_NODE_LEVEL + '=' + (level + 1)
                        + " AND NOT EXISTS("
                        + /* */ "SELECT 1 FROM " + triggerHelperTable.ref() + _WHERE_ + whereClause
                        + /* */ ')'
                        + " BEGIN"
                        + /* */ " INSERT INTO " + listTable.getName() + ' ' + listColumns
                        + /*        */ " VALUES " + listValues + ';'
                        + " END";

                db.execSQL(levelTgSql);
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                    LoggerFactory.getLogger()
                                 .d(TAG, "build", "level=" + level
                                                  + "|TgSql=" + levelTgSql);
                }
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
            triggerHelperCurrentValueTriggerName = listTable.getName() + "_TG_CURRENT";
            db.execSQL(DROP_TRIGGER_IF_EXISTS_ + triggerHelperCurrentValueTriggerName);
            // This is a single row only, so delete the previous value, and insert the current one
            final String currentValueTgSql =
                    "CREATE TEMPORARY TRIGGER " + triggerHelperCurrentValueTriggerName
                    + " AFTER INSERT ON " + listTable.getName() + " FOR EACH ROW"
                    + " WHEN NEW." + DBKey.BL_NODE_LEVEL + '=' + groupCount
                    + " BEGIN"
                    + "  DELETE FROM " + triggerHelperTable.getName() + ';'
                    + "  INSERT INTO " + triggerHelperTable.getName()
                    + /*   */ " VALUES " + currentValues + ";"
                    + " END";
            db.execSQL(currentValueTgSql);
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                LoggerFactory.getLogger()
                             .d(TAG, "build", "currentValueTgSql=" + currentValueTgSql);
            }
        }

        /**
         * Drop the triggers and related table.
         *
         * @param db Database Access
         */
        private void cleanupTriggers(@NonNull final SynchronizedDb db) {
            if (triggerHelperCurrentValueTriggerName != null) {
                db.execSQL(DROP_TRIGGER_IF_EXISTS_ + triggerHelperCurrentValueTriggerName);
            }
            if (triggerHelperLevelTriggerName != null) {
                Arrays.stream(triggerHelperLevelTriggerName)
                      .map(name -> DROP_TRIGGER_IF_EXISTS_ + name)
                      .forEach(db::execSQL);
            }
            if (triggerHelperTable != null) {
                db.drop(triggerHelperTable.getName());
            }
        }

        /**
         * Add a {@link DomainExpression}.
         *
         * @param domainExpression to add
         */
        private void addDomainExpression(@NonNull final DomainExpression domainExpression) {

            final Domain domain = domainExpression.getDomain();
            // Add the domain itself to the table, if it's not already there
            final boolean domainAlreadyPresent = listTable.contains(domain);
            if (!domainAlreadyPresent) {
                listTable.addDomains(domain);
            }

            // If the expression is {@code null},
            // then the domain is just meant for the lowest level; i.e. the book.
            // otherwise, we check and add it here
            final String expression = domainExpression.getExpression();
            if (expression != null) {
                // If the domain was already present, check if the expression is different
                // from the stored expression
                if (domainAlreadyPresent && expression.equals(expressionsDupCheck.get(domain))) {
                    // If it's the same expression, we do NOT want to add it again.
                    // This is NOT a bug, although one could argue it's an efficiency issue.
                    return;
                }
                expressionsDupCheck.put(domain, expression);

                // add it
                domainExpressions.add(domainExpression);
            }

            // If required, add the domainExpression to the order-by domains, if not already there
            if (domainExpression.getSort() != Sort.Unsorted
                && !orderByDupCheck.contains(domain.getName())) {

                orderByDomainExpressions.add(domainExpression);
                orderByDupCheck.add(domain.getName());
            }

            // TODO: We experimented with this replacing the above code
            //  when fixing the issue described in {@link DateReadHolder}
            // If required, add the domainExpression to the order-by domains
//            if (domainExpression.getSort() != Sort.Unsorted) {
//                if (orderByDupCheck.contains(domain.getName())) {
//                    // remove any previous copy
//                    orderByDomainExpressions.removeIf(
//                            de -> de.getDomain().getName().equals(domain.getName()));
//                } else {
//                    // remember we added it
//                    orderByDupCheck.add(domain.getName());
//                }
//                orderByDomainExpressions.add(domainExpression);
//            }
        }

        /**
         * Add the domains for the given group.
         *
         * @param group to add
         *
         * @throws IllegalArgumentException when trying to group by book
         */
        private void addGroup(@NonNull final BooklistGroup group) {
            // dev sanity check
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                if (group.getId() == BooklistGroup.BOOK) {
                    throw new IllegalArgumentException("Cannot group by Book");
                }
            }

            // Do this in 3 steps, allowing groups to override their parent (if any) group

            // display domain first
            final DomainExpression displayDomainExpression = group.getDisplayDomainExpression();
            addDomainExpression(displayDomainExpression);
            accumulatedDomains.add(displayDomainExpression.getDomain());

            // then how we group
            group.getGroupDomainExpressions().forEach(domainExpression -> {
                addDomainExpression(domainExpression);
                accumulatedDomains.add(domainExpression.getDomain());
            });

            // the base domains we always need/have
            group.getBaseDomainExpressions().forEach(this::addDomainExpression);

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
            group.setAccumulatedDomains(new ArrayList<>(accumulatedDomains));
        }

        /**
         * Create the expression for the {@link DBKey#BL_NODE_KEY} column of a Book.
         * <p>
         * This string value contains one key-value pair for each each group level, and
         * ALWAYS ends with a '/'.
         * <p>
         * i.e: "/key=value/key=value/[key=value/]"
         *
         * @return column expression
         */
        @NonNull
        private String buildNodeKey() {
            return style.getGroupList()
                        .stream()
                        .map(BooklistGroup::getNodeKeyExpression)
                        .collect(Collectors.joining("||"))
                   + "||'/'";
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
         *      <li>{@link #leftOuterJoins}</li>
         * </ul>
         *
         * @param leftOuterJoins tables to be added as a LEFT OUTER JOIN
         *
         * @return FROM clause
         */
        @NonNull
        private String buildFrom(@NonNull final Collection<TableDefinition> leftOuterJoins) {
            final StringBuilder sb = new StringBuilder();

            // If there is a bookshelf specified (either as group or as a filter),
            // we start the join there.
            if (style.hasGroup(BooklistGroup.BOOKSHELF) || filteredOnBookshelf) {
                sb.append(TBL_BOOKSHELF.startJoin(TBL_BOOK_BOOKSHELF, TBL_BOOKS));
            } else {
                // Otherwise, we start with the BOOKS table.
                sb.append(TBL_BOOKS.ref());
            }

            joinWithAuthors(sb);

            if (style.hasGroup(BooklistGroup.SERIES)
                || style.isShowField(FieldVisibility.Screen.List, DBKey.FK_SERIES)) {
                joinWithSeries(sb);
            }

            if (style.hasGroup(BooklistGroup.PUBLISHER)
                || style.isShowField(FieldVisibility.Screen.List, DBKey.FK_PUBLISHER)) {
                joinWithPublishers(sb);
            }

            // Add LEFT OUTER JOIN tables as needed
            leftOuterJoins.forEach(table -> sb.append(TBL_BOOKS.leftOuterJoin(table)));

            return sb.toString();
        }

        private void joinWithAuthors(@NonNull final StringBuilder sb) {
            // Join with the link table between Book and Author.
            sb.append(TBL_BOOKS.join(TBL_BOOK_AUTHOR));
            // Extend the join filtering on the primary Author unless
            // the user wants the book to show under all its Authors
            if (!style.isShowBooksUnderEachGroup(Style.UnderEach.Author.getGroupId())) {
                @Author.Type
                final int primaryAuthorType = style.getPrimaryAuthorType();
                if (primaryAuthorType == Author.TYPE_UNKNOWN) {
                    // don't care about Author type, so just grab the first one (i.e. pos==1)
                    sb.append(_AND_)
                      .append(TBL_BOOK_AUTHOR.dot(DBKey.BOOK_AUTHOR_POSITION))
                      .append("=1");
                } else {
                    // grab the desired type, or if no such type, grab the first one
                    //   AND (((type & TYPE)<>0) OR (((type &~ TYPE)=0) AND pos=1))
                    sb.append(" AND (((")
                      .append(TBL_BOOK_AUTHOR.dot(DBKey.AUTHOR_TYPE__BITMASK))
                      .append(" & ").append(primaryAuthorType).append(")<>0)")
                      .append(" OR (((")
                      .append(TBL_BOOK_AUTHOR.dot(DBKey.AUTHOR_TYPE__BITMASK))
                      .append(" &~ ").append(primaryAuthorType).append(")=0)")
                      .append(_AND_)
                      .append(TBL_BOOK_AUTHOR.dot(DBKey.BOOK_AUTHOR_POSITION))
                      .append("=1))");
                }
            }
            // Join with Authors to make the names available
            sb.append(TBL_BOOK_AUTHOR.join(TBL_AUTHORS));
            // and potential 'real' names if this one is a pseudonym
            sb.append(TBL_AUTHORS.leftOuterJoin(TBL_PSEUDONYM_AUTHOR));
        }

        private void joinWithSeries(@NonNull final StringBuilder sb) {
            // Join with the link table between Book and Series.
            sb.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_SERIES));
            // Extend the join filtering on the primary Series unless
            // the user wants the book to show under all its Series
            if (!style.isShowBooksUnderEachGroup(Style.UnderEach.Series.getGroupId())) {
                sb.append(_AND_)
                  .append(TBL_BOOK_SERIES.dot(DBKey.BOOK_SERIES_POSITION))
                  .append("=1");
            }
            // Join with Series to make the titles available
            sb.append(TBL_BOOK_SERIES.leftOuterJoin(TBL_SERIES));
        }

        private void joinWithPublishers(@NonNull final StringBuilder sb) {
            // Join with the link table between Book and Publishers.
            sb.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_PUBLISHER));
            // Extend the join filtering on the primary Publisher unless
            // the user wants the book to show under all its Publishers
            if (!style.isShowBooksUnderEachGroup(Style.UnderEach.Publisher.getGroupId())) {
                sb.append(_AND_)
                  .append(TBL_BOOK_PUBLISHER.dot(DBKey.BOOK_PUBLISHER_POSITION))
                  .append("=1");
            }
            // Join with Publishers to make the names available
            sb.append(TBL_BOOK_PUBLISHER.leftOuterJoin(TBL_PUBLISHERS));
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
            //noinspection DataFlowIssue
            final String where = filters
                    .stream()
                    // ONLY APPLY ACTIVE FILTERS!
                    .filter(filter -> filter.isActive(context))
                    .map(filter -> filter.getExpression(context))
                    // sanity checks
                    .filter(Objects::nonNull)
                    .filter(expression -> !expression.isEmpty())
                    .collect(Collectors.joining(_AND_));

            return where.isEmpty() ? "" : _WHERE_ + where;
        }

        /**
         * Create the ORDER BY clause.
         *
         * @param collationCaseSensitive flag; whether the database uses case-sensitive collation
         *
         * @return ORDER BY clause
         */
        @NonNull
        private String buildOrderBy(final boolean collationCaseSensitive) {
            return orderByDomainExpressions
                    .stream()
                    .map(de -> de.getDomain()
                                 .getOrderByString(de.getSort(), collationCaseSensitive))
                    .collect(Collectors.joining(","));
        }
    }
}
