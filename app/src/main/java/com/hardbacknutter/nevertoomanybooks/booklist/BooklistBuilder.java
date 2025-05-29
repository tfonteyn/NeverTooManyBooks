/*
 * @Copyright 2018-2025 HardBackNutter
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.NumberListFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PEntityListFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.grouping.GroupKey;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ScreenLayout;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.core.database.Domain;
import com.hardbacknutter.nevertoomanybooks.core.database.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.core.database.Sort;
import com.hardbacknutter.nevertoomanybooks.core.database.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.database.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_EXPANDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_VISIBLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_IDENTIFIER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TAG;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_IDENTIFIERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_LANG_MAPPINGS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PSEUDONYM_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TAGS;

/**
 * Build and populate temporary tables with details of "flattened" books.
 * The generated list is used to display books in a list control and perform operation like
 * 'expand/collapse' on nodes in the list.
 * <p>
 * As a general remark: the list-table is a compromise between data-availability
 * (i.e. data must explicitly be copied) and performance during scrolling
 * through a large amount of books.
 * <p>
 * The {@link Booklist} "owns" the temporary database tables.
 * They get deleted when {@link Booklist#close()} is called.
 * A {@link Booklist} has a 1:1 relation to {@link BooklistCursor} objects.
 * The BooklistCursor holds a reference to the {@link Booklist}.
 * <p>
 * While building, the triggers will make sure that the top-level(==1) nodes
 * are always visible. This is critical for some parts of the code which rely on this.
 */
class BooklistBuilder {

    /** Foreign key between the list and navigation table. */
    static final String FK_ROW_ID = "bl_row_id";
    /** Log tag. */
    private static final String TAG = "BooklistBuilder";
    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

    private static final String DELETE_FROM_ = "DELETE FROM ";
    private static final String INSERT_INTO_ = "INSERT INTO ";
    private static final String SELECT_ = "SELECT ";
    private static final String SELECT_COUNT_ = "SELECT COUNT(*) ";
    private static final String SELECT_DISTINCT_ = "SELECT DISTINCT ";
    private static final String UPDATE_ = "UPDATE ";
    private static final String WITH_ = "WITH ";
    private static final String _AND_ = " AND ";
    private static final String _AS_ = " AS ";
    private static final String _FROM_ = " FROM ";
    private static final String _GROUP_BY_ = " GROUP BY ";
    private static final String _ORDER_BY_ = " ORDER BY ";
    private static final String _SET_ = " SET ";
    private static final String _VALUES_ = " VALUES ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _JOIN_ = " JOIN ";
    private static final String _ON_ = " ON ";

    private static final String CREATE_TEMPORARY_TRIGGER_ = "CREATE TEMPORARY TRIGGER ";
    private static final String _BEGIN_ = " BEGIN ";
    private static final String END = "END";
    private static final String DROP_TRIGGER_IF_EXISTS_ = "DROP TRIGGER IF EXISTS ";

    /**
     * Foreign key between the {@link Booklist} list table
     * and the {@link Booklist} navigator table
     * as used in the ViewPager displaying individual books.
     */
    private static final Domain DOM_FK_BL_ROW_ID;

    /** Table alias. */
    private static final String TA_WITH = "ws";
    /** Column alias. */
    private static final String CA_SUM = "cnt";

    static {
        DOM_FK_BL_ROW_ID =
                new Domain.Builder(FK_ROW_ID, SqLiteDataType.Integer)
                        .notNull()
                        .build();
    }

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

    /** Any LEFT OUTER JOIN's that needs adding. */
    @NonNull
    private final Set<TableDefinition> leftOuterJoins = new HashSet<>();

    /** the list of Filters. */
    private final Collection<Filter> filters = new ArrayList<>();

    /**
     * Domains belonging the current group including its outer groups.
     * <p>
     * LinkedHashSet: to eliminate duplicates, but keep the insertion order.
     */
    private final Set<Domain> accumulatedDomains = new LinkedHashSet<>();

    /**
     * Domains that form part of the sort key.
     * These are typically a reduced set of the group domains since the group domains
     * may contain more than just the key
     * <p>
     * LinkedHashSet: to eliminate duplicates, but keep the insertion order.
     */
    private final Set<DomainExpression> orderByDomainExpressions = new LinkedHashSet<>();

    /** Guards from adding duplicates. */
    private final Map<Domain, String> expressionsDupCheck = new HashMap<>();

    @NonNull
    private final Bookshelf bookshelf;
    @NonNull
    private RebuildBooklist rebuildMode = RebuildBooklist.FromSaved;

    /** Table used by the triggers to track the most recent/current row headings. */
    private TableDefinition triggerHelperTable;
    /** Trigger name - inserts headers for each level during the initial insert. */
    private String[] triggerHelperLevelTriggerName;
    /** Trigger name - maintain the 'current' value during the initial insert. */
    private String triggerHelperCurrentValueTriggerName;

    /**
     * Constructor.
     *
     * @param instanceId to create a unique table name
     * @param style      to apply to the list
     * @param bookshelf  to display
     */
    BooklistBuilder(final int instanceId,
                    @NonNull final Style style,
                    @NonNull final Bookshelf bookshelf) {

        this.style = style;
        // whether we're filtering on a specific Bookshelf,
        // or if we're using the 'all books'
        this.filteredOnBookshelf = bookshelf.getId() != Bookshelf.ALL_BOOKS;
        this.bookshelf = bookshelf;

        /*
         * Temporary table used to store a flattened booklist tree structure.
         * This table should always be created without column constraints applied,
         * with the exception of the "_id" primary key autoincrement
         *
         * We setup the tables here with the primary key only.
         * Other domains will be added as needed.
         */
        listTable = new TableDefinition("tmp_book_list_" + instanceId, "bl")
                .addDomains(DOM_PK_ID)
                .setPrimaryKey(DOM_PK_ID);

        navTable = new TableDefinition("tmp_book_nav_" + instanceId, "nav")
                .addDomains(DOM_PK_ID, DOM_FK_BOOK, DOM_FK_BL_ROW_ID)
                .setPrimaryKey(DOM_PK_ID);

        // Allow debug mode to use a standard table so we can export and inspect the content.
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_USES_STANDARD_TABLE) {
            listTable.setType(TableDefinition.TableType.Standard);
            navTable.setType(TableDefinition.TableType.Standard);
        } else {
            listTable.setType(TableDefinition.TableType.Temporary);
            navTable.setType(TableDefinition.TableType.Temporary);
        }
    }

    /**
     * Add a {@link DomainExpression}.
     *
     * @param domainExpression to add
     */
    private void addDomainExpression(@NonNull final DomainExpression domainExpression) {

        final Domain domain = domainExpression.getDomain();
        // Add the domain itself to the table if it's not already there
        final boolean domainAlreadyPresent = listTable.contains(domain);
        if (!domainAlreadyPresent) {
            listTable.addDomains(domain);
        }

        // If the expression is {@code null},
        // then the domain is just meant for the book level.
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

        // If required, add the domainExpression to the order-by domains
        if (domainExpression.getSort() != Sort.Unsorted) {
            orderByDomainExpressions.add(domainExpression);
        }
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
         * As subsequent addGroup calls will modify the {@code #accumulatedDomains} collection,
         * hence we must make a (shallow) copy of the list.
         */
        group.setAccumulatedDomains(new ArrayList<>(accumulatedDomains));
    }

    /**
     * build.
     */
    private void setupNodeDomains() {
        // {@link BooklistGroup#GroupKey}.
        // The actual value is set on a by-group/book basis.
        listTable.addDomains(DOM_BL_NODE_KEY)
                 .addIndex(DBKey.BL_NODE.KEY, false, DOM_BL_NODE_KEY);

        // flags used by {@link BooklistNodeDao}.
        listTable.addDomains(DOM_BL_NODE_EXPANDED)
                 .addIndex(DBKey.BL_NODE.EXPANDED, false, DOM_BL_NODE_EXPANDED)
                 .addDomains(DOM_BL_NODE_VISIBLE)
                 .addIndex(DBKey.BL_NODE.VISIBLE, false, DOM_BL_NODE_VISIBLE);

        // Always SORT by level first; for a book this is always 1 below the #groups obviously
        addDomainExpression(new DomainExpression(DOM_BL_NODE_LEVEL,
                                                 String.valueOf(style.getGroupCount() + 1),
                                                 Sort.Asc));

        // The BooklistGroup for a book is always {@link BooklistGroup#BOOK} (duh)
        // The group levels will have {@code null} in this column.
        addDomainExpression(DBExpr.BOOK_NODE_GROUP);
        listTable.addIndex(DBKey.BL_NODE.GROUP, false, DOM_BL_NODE_GROUP);
    }

    /**
     * build.
     *
     * @param context Current context
     */
    private void addBookLevelDomains(@NonNull final Context context) {
        // Always get the book id
        addDomainExpression(DBExpr.BOOK_ID);
        // Always get the UUID.
        addDomainExpression(DBExpr.UUID);
        // Always get the ISBN.
        addDomainExpression(DBExpr.ISBN);

        // Always get the read flag.
        addDomainExpression(DBExpr.READ);

        // Always get the book language.
        // It is needed for reordering titles in BooklistGroup rows.
        addDomainExpression(DBExpr.LANGUAGE);

        // Always get the Author ID.
        // We need the Author id to show the "Search on" menu allowing
        // to search for "other books by the same author"
        // This DOES force a mandatory join with the authors even if
        // we're not grouping by them (or not displaying them on the book-level)
        // Note: we COULD do the same for series, but we don't:
        // Joining with authors sure... all books have authors,
        // but books with series... don't know how common, but a lot less.
        // If a user in interested in searching for "other books in the same series"
        // then surely they will either group by them, or display them
        // at the book level
        addDomainExpression(DBExpr.AUTHOR_ID);

        // The domains for the book level, visibility and ordering according to style.
        if (style.getLayout() == ScreenLayout.Grid) {
            //TODO: define a FieldVisibility.Screen.Grid, and do this in style.isShowField(...)
            // or do all in style.isShowField(...) where we can check the layout in use ?
            style.getBookLevelFieldsOrderBy().entrySet()
                 .stream()
                 .filter(field -> {
                     final String key = field.getKey();
                     if (key.equals(DBKey.TITLE) || key.equals(DBKey.COVER[0])) {
                         return true;
                     } else if (key.equals(DBKey.FK_AUTHOR)) {
                         return style.isShowField(FieldVisibility.Screen.List, field.getKey());
                     }
                     return false;
                 })
                 .map(field -> DBExpr.forBookLevelField(field.getKey(),
                                                        field.getValue(),
                                                        style))
                 .flatMap(List::stream)
                 .forEach(this::addDomainExpression);
        } else {
            // Style.Layout.List
            style.getBookLevelFieldsOrderBy().entrySet()
                 .stream()
                 .filter(field -> style.isShowField(FieldVisibility.Screen.List, field.getKey()))
                 .map(field -> DBExpr.forBookLevelField(field.getKey(),
                                                        field.getValue(),
                                                        style))
                 .flatMap(List::stream)
                 .forEach(this::addDomainExpression);
        }

        // If we're showing {@link DBKey#LOANEE_NAME} on the book level, we require
        // a {@code LEFT JOIN} {@link DBDefinitions#TBL_BOOK_LOANEE}.
        if (style.isShowField(FieldVisibility.Screen.List, DBKey.LOANEE_NAME)) {
            leftOuterJoins.add(TBL_BOOK_LOANEE);
        }

        if (CalibreHandler.isSyncEnabled(context)) {
            DBExpr.CALIBRE.forEach(this::addDomainExpression);
            leftOuterJoins.add(TBL_CALIBRE_BOOKS);
        }
    }

    /**
     * Combine filters from the bookshelf with Style grouping requirements.
     *
     * @param context Current context
     *
     * @return filters
     */
    @NonNull
    private List<PFilter<?>> createBookshelfFilters(@NonNull final Context context) {
        // Prepare the Bookshelf filters; paranoia: make sure we only get the active ones
        final List<PFilter<?>> bookshelfFilters = bookshelf.pruneFilters();

        // Add a filter on the current Bookshelf?
        // Only consider doing this if this is NOT the "All books" Bookshelf
        if (bookshelf.getId() != Bookshelf.ALL_BOOKS) {
            // and only if the current style does NOT contain the Bookshelf group.
            if (!style.hasGroup(BooklistGroup.BOOKSHELF)) {
                // do we already have a Bookshelf based filter?
                final Optional<PFilter<?>> bookshelfFilter = bookshelfFilters
                        .stream()
                        .filter(pFilter -> DBKey.FK_BOOKSHELF.equals(pFilter.getDBKey()))
                        .findFirst();

                if (bookshelfFilter.isPresent()) {
                    // Add the current Bookshelf to the existing filter.
                    final PEntityListFilter<?> pFilter = (PEntityListFilter<?>)
                            bookshelfFilter.get();

                    final Set<Long> list = new HashSet<>(pFilter.getValue());
                    list.add(bookshelf.getId());
                    pFilter.setValue(context, list);

                } else {
                    // Filter on the current one only
                    this.filters.add(new NumberListFilter<>(TBL_BOOKSHELF, DOM_PK_ID,
                                                            List.of(bookshelf.getId())));
                }
            }
        }

        return bookshelfFilters;
    }

    /**
     * Using the collected domain info, create the various SQL phrases used to build
     * the resulting flat list table and build the SQL that does the initial table load.
     *
     * @param context        Current context
     * @param db             Underlying database
     * @param rebuildMode    the mode to use for restoring the saved state.
     * @param searchCriteria filters to add
     *
     * @return a Pair with the fully populated list-table and the navigation-table
     *
     * @throws TransactionException (debug)
     */
    @NonNull
    Pair<TableDefinition, TableDefinition> build(@NonNull final Context context,
                                                 @NonNull final SynchronizedDb db,
                                                 @NonNull final RebuildBooklist rebuildMode,
                                                 @NonNull final Collection<Filter> searchCriteria) {
        if (BuildConfig.DEBUG /* always */) {
            if (!db.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        this.rebuildMode = rebuildMode;

        final boolean collationCaseSensitive = db.isCollationCaseSensitive();

        // first step!
        setupNodeDomains();

        style.getGroupList().forEach(this::addGroup);

        addBookLevelDomains(context);

        this.filters.addAll(searchCriteria);
        this.filters.addAll(createBookshelfFilters(context));

        // All structures are in place now
        // Construct the INSERT INTO ... SELECT
        // to populate the list-table
        final String sqlBulkInsert = createSqlBulkInsert(context, collationCaseSensitive);

        // Create the list table
        //IMPORTANT: withDomainConstraints MUST BE false
        db.recreate(listTable, false);

        // 2025-03-11: add indexes on the group-keys for use by the "sums" sql.
        // Not entirely sure how much impact, or lack-of, this has.
        style.getGroupList().stream()
             .map(BooklistGroup::getGroupKey)
             .map(GroupKey::getKeyDomainExpression)
             .map(DomainExpression::getDomain)
             .forEach(domain -> listTable.addIndex(domain.getName(), false, domain));

        // get the triggers in place, ready to act on our upcoming initial insert.
        createTriggers(db);

        final long t0 = System.nanoTime();

        // Build the lowest level (i.e. books) using our initial insert statement
        // The triggers will do the grouping levels.
        final int initialInsertCount;
        try (SynchronizedStatement stmt = db.compileStatement(sqlBulkInsert)) {
            initialInsertCount = stmt.executeUpdateDelete();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
            LoggerFactory.getLogger()
                         .d(TAG, "build",
                            "insert(" + initialInsertCount + ")"
                            + "|" + ((System.nanoTime() - t0) / NANO_TO_MILLIS) + " ms"
                            + "|" + sqlBulkInsert);
        }

        if (!collationCaseSensitive) {
            // can't use IndexDefinition class as it does not support sorting clause for now.
            final String indexCols = orderByDomainExpressions
                    .stream()
                    .map(domainExpression -> domainExpression.getDomain()
                                                             .getOrderByString(
                                                                     domainExpression.getSort(),
                                                                     false))
                    .collect(Collectors.joining(",", "(", ")"));

            db.execSQL("CREATE INDEX " + listTable.getName() + "_SDI ON "
                       + listTable.getName() + indexCols);
        }

        // The list table is now fully populated.
        db.analyze(listTable);

        // remove the no longer needed triggers
        cleanupTriggers(db);

        if (style.isShowGroupBookCount()) {
            createLevelSums(db);
        }

        // Create the navigation table.
        // This is a mapping table between row-id + book-id and a plain sequential id.
        // The latter is needed for a RecyclerView adapter.
        // Don't apply constraints (no need)
        db.recreate(navTable, false);
        db.execSQL(INSERT_INTO_ + navTable.getName()
                   + " (" + DBKey.FK_BOOK + ',' + FK_ROW_ID + ") "
                   + SELECT_ + DBKey.FK_BOOK + ',' + DBKey.PK_ID
                   + _FROM_ + listTable.getName()
                   // MUST be limited to BooklistGroup.BOOK;
                   // the FK_BOOK fields on other groups represents the book-count for that group
                   + _WHERE_ + DBKey.BL_NODE.GROUP + "=" + BooklistGroup.BOOK
                   + _ORDER_BY_ + DBKey.PK_ID);

        return new Pair<>(listTable, navTable);
    }

    @NonNull
    private String createSqlBulkInsert(@NonNull final Context context,
                                       final boolean collationCaseSensitive) {
        // List of column names for the INSERT INTO... clause
        final StringJoiner destColumns = new StringJoiner(",");
        // List of expressions for the SELECT... clause.
        final StringJoiner sourceColumns = new StringJoiner(",");

        // Add the domain expressions
        domainExpressions.forEach(domainExpression -> {
            destColumns.add(domainExpression.getDomain().getName());
            sourceColumns.add(domainExpression.getExpression()
                              + _AS_ + domainExpression.getDomain().getName());
        });

        // Add the node key column
        destColumns.add(DOM_BL_NODE_KEY.getName());
        sourceColumns.add(buildNodeKey() + _AS_ + DOM_BL_NODE_KEY.getName());

        // Add the node state columns
        destColumns.add(DOM_BL_NODE_EXPANDED.getName());
        destColumns.add(DOM_BL_NODE_VISIBLE.getName());
        if (rebuildMode == RebuildBooklist.Expanded) {
            // Expanded nodes must explicitly be set to 1/1
            sourceColumns.add("1" + _AS_ + DOM_BL_NODE_EXPANDED.getName())
                         .add("1" + _AS_ + DOM_BL_NODE_VISIBLE.getName());

        } else {
            // All others must be set to 0/0. The actual state will be set afterwards.
            sourceColumns.add("0" + _AS_ + DOM_BL_NODE_EXPANDED.getName())
                         .add("0" + _AS_ + DOM_BL_NODE_VISIBLE.getName());
        }

        return INSERT_INTO_ + listTable.getName() + " (" + destColumns + ") "
               + SELECT_ + sourceColumns + _FROM_ + buildFrom(context) + buildWhere()
               + _ORDER_BY_ + buildOrderBy(collationCaseSensitive);
    }

    /**
     * Create the expression for the {@link DBKey.BL_NODE#KEY} column of a Book.
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
                    .collect(Collectors.joining("||", "", "||'/'"));
    }

    /**
     * Create the FROM clause based on the
     * {@link BooklistGroup}s,
     * {@link Filter}s
     * and any pre-defined leftOuterJoin's
     * and {@link Style#getBookLevelFieldsOrderBy()} fields.
     *
     * @param context Current context
     *
     * @return FROM clause
     */
    @NonNull
    private String buildFrom(@NonNull final Context context) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            LoggerFactory.getLogger().d(TAG, "build|leftOuterJoins=" + leftOuterJoins);
            LoggerFactory.getLogger().d(TAG, "build|filters=" + filters);
        }

        // Add any LEFT OUTER JOINS the filters ask for, but leave the BOOKS table out.
        // Others will be removed if/when we're already using them for grouping,
        // see below.
        filters.stream()
               .map(Filter::getLeftOuterJoinTable)
               .flatMap(Optional::stream)
               .filter(tableDefinition -> !TBL_BOOKS.equals(tableDefinition))
               .forEach(leftOuterJoins::add);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            LoggerFactory.getLogger().d(TAG, "build|leftOuterJoins+filters=" + leftOuterJoins);
        }

        final StringBuilder sb = new StringBuilder();

        // If there is a bookshelf specified (either as group or as a filter),
        // we start the join there.
        if (style.hasGroup(BooklistGroup.BOOKSHELF) || filteredOnBookshelf) {
            sb.append(TBL_BOOKSHELF.startJoin(TBL_BOOK_BOOKSHELF, TBL_BOOKS));
            leftOuterJoins.remove(TBL_BOOK_BOOKSHELF);
        } else {
            // Otherwise, we start with the BOOKS table.
            sb.append(TBL_BOOKS.ref());
        }

        // We always want the primary author id in the cursor.
        // We add that id in {@link #addBookLevelDomains} see comments there
        joinWithAuthors(sb);
        leftOuterJoins.remove(TBL_BOOK_AUTHOR);

        // URGENT: BooklistGroup joins need to be done via callbacks.
        //  i.e. config the join on the BooklistGroup so it won't drop through
        //  the cracks when creating a new group.
        if (style.hasGroup(BooklistGroup.SERIES)
            || style.isShowField(FieldVisibility.Screen.List, DBKey.FK_SERIES)) {
            joinWithSeries(sb);
            leftOuterJoins.remove(TBL_BOOK_SERIES);
        }

        if (style.hasGroup(BooklistGroup.PUBLISHER)
            || style.isShowField(FieldVisibility.Screen.List, DBKey.FK_PUBLISHER)) {
            joinWithPublishers(sb);
            leftOuterJoins.remove(TBL_BOOK_PUBLISHER);
        }

        if (style.hasGroup(BooklistGroup.LANGUAGE)
            || style.isShowField(FieldVisibility.Screen.List, DBKey.LANGUAGE)) {
            joinWithLanguageMappings(context,
                                     DBDefinitions.ALIAS_LANG_MAPPINGS_LANGUAGE,
                                     DBKey.LANGUAGE,
                                     sb);
        }
        if (style.hasGroup(BooklistGroup.ORIGINAL_LANGUAGE)
            || style.isShowField(FieldVisibility.Screen.List,
                                 DBKey.TRANSLATION_ORIGINAL_LANGUAGE)) {
            joinWithLanguageMappings(context,
                                     DBDefinitions.ALIAS_LANG_MAPPINGS_ORIGINAL_LANGUAGE,
                                     DBKey.TRANSLATION_ORIGINAL_LANGUAGE,
                                     sb);
        }

        if (style.hasGroup(BooklistGroup.TAGS_GENRE)) {
            // book-level not supported
            // || style.isShowField(FieldVisibility.Screen.List, DBKey.FK_TAG)
            joinWithTags(sb);
            leftOuterJoins.remove(TBL_BOOK_TAG);
        }

        if (style.hasGroup(BooklistGroup.IDENTIFIER)) {
            // book-level not supported
            // || style.isShowField(FieldVisibility.Screen.List, DBKey.FK_IDENTIFIER)
            joinWithIdentifiers(sb);
            leftOuterJoins.remove(TBL_BOOK_IDENTIFIER);
        }

        // NEWTHINGS: BooklistGroup: add joins if needed

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            LoggerFactory.getLogger().d(TAG, "build|leftOuterJoins final=" + leftOuterJoins);
        }
        // Add LEFT OUTER JOIN tables as needed
        // (it's a Set, no duplicates to check)
        leftOuterJoins.forEach(table -> sb.append(TBL_BOOKS.leftOuterJoin(table)));

        return sb.toString();
    }

    private void joinWithAuthors(@NonNull final StringBuilder sb) {
        // Join with the link table between Book and Author.
        sb.append(TBL_BOOKS.join(TBL_BOOK_AUTHOR));
        // If the user wants the book to show ONLY under its primary Author...
        if (!style.isShowBooksUnderEachGroup(Style.UnderEach.Author.getGroupId())) {
            // then extend the join filtering on the primary Author
            sb.append(_AND_);

            @Author.Type
            final int primaryAuthorType = style.getPrimaryAuthorType();
            if (primaryAuthorType == Author.TYPE_UNKNOWN) {
                // The user has no specific type set, so just grab the first one (i.e. pos==1)
                sb.append(TBL_BOOK_AUTHOR.dot(DBKey.AUTHOR.BOOK_AUTHOR_POSITION)).append("=1");
            } else {
                // FIXME: https://github.com/tfonteyn/NeverTooManyBooks/issues/82
                // grab the desired type, or if no such type, grab the first one anyway
                //   (
                //      ((type & TYPE)<>0)
                //   OR
                //      (((type &~ TYPE)=0) AND pos=1)
                //   )
                sb.append("(((")
                  // the type is an exact match
                  .append(TBL_BOOK_AUTHOR.dot(DBKey.AUTHOR.BOOK_AUTHOR_TYPE))
                  .append(" & ").append(primaryAuthorType).append(")<>0)")
                  .append(" OR (((")
                  // grab the first one
                  .append(TBL_BOOK_AUTHOR.dot(DBKey.AUTHOR.BOOK_AUTHOR_TYPE))
                  .append(" &~ ").append(primaryAuthorType).append(")=0)")
                  .append(_AND_)
                  .append(TBL_BOOK_AUTHOR.dot(DBKey.AUTHOR.BOOK_AUTHOR_POSITION)).append("=1))");
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
              .append(TBL_BOOK_SERIES.dot(DBKey.SERIES.BOOK_SERIES_POSITION))
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
              .append(TBL_BOOK_PUBLISHER.dot(DBKey.PUBLISHER.BOOK_PUBLISHER_POSITION))
              .append("=1");
        }
        // Join with Publishers to make the names available
        sb.append(TBL_BOOK_PUBLISHER.leftOuterJoin(TBL_PUBLISHERS));
    }

    private void joinWithLanguageMappings(@NonNull final Context context,
                                          @NonNull final String tableAlias,
                                          @NonNull final String bookTableColumn,
                                          @NonNull final StringBuilder sb) {
        final String userIso3 = context.getResources().getConfiguration().getLocales().get(0)
                                       .getISO3Language();

        // This is using a non-enforced reference, build the JOIN manually.
        final String join =
                " LEFT OUTER JOIN " + TBL_LANG_MAPPINGS.getName() + _AS_ + tableAlias
                + _ON_
                + TBL_BOOKS.dot(bookTableColumn) + '=' + tableAlias + "." + DBKey.LANG_MAPPING.ISO3
                + _AND_
                + tableAlias + "." + DBKey.LANG_MAPPING.ISO3_USER + "='" + userIso3 + '\'';
        sb.append(join);
    }

    private void joinWithTags(@NonNull final StringBuilder sb) {
        // Join with the link table between Book and Tags.
        sb.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_TAG));
        // Note we're ALWAYS showing books under all it's tags because:
        // 1) that's IMHO always desired
        // 2) there is no 'primary tag' concept
        // Join with Tags to make the names available
        sb.append(TBL_BOOK_TAG.leftOuterJoin(TBL_TAGS));
    }

    private void joinWithIdentifiers(@NonNull final StringBuilder sb) {
        // Join with the link table between Book and Identifiers.
        sb.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_IDENTIFIER));
        // Note we're ALWAYS showing books under all it's identifier because:
        // 1) that's IMHO always desired
        // 2) there is no 'primary identifier' concept
        // Join with Identifiers to make the names available
        sb.append(TBL_BOOK_IDENTIFIER.leftOuterJoin(TBL_IDENTIFIERS));
    }

    /**
     * Create the WHERE clause based on all active filters (for in-use domains).
     *
     * @return WHERE clause, can be empty
     */
    @NonNull
    private String buildWhere() {
        //noinspection DataFlowIssue
        final String where = filters
                .stream()
                // ONLY APPLY ACTIVE FILTERS!
                .filter(Filter::isActive)
                .map(Filter::getExpression)
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
                .map(domainExpression -> domainExpression.getDomain()
                                                         .getOrderByString(
                                                                 domainExpression.getSort(),
                                                                 collationCaseSensitive))
                .collect(Collectors.joining(","));
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
     * <p>
     * For references, the triggers/helper example with "Author/Series/Book" grouping.
     * <pre>
     *    CREATE TEMPORARY TABLE tmp_book_list_1_th
     *          (node_level integer NOT NULL,blg_sort_aut text,blg_sort_ser text,
     *           blg_sort_ser_num_f real,
     *           series_num text COLLATE LOCALIZED,
     *           title_ob text NOT NULL DEFAULT '' COLLATE LOCALIZED)
     *
     *    -- before inserting a book...
     *    CREATE TEMPORARY TRIGGER tmp_book_list_1_TG_LEVEL_2 BEFORE INSERT ON tmp_book_list_1
     *    FOR EACH ROW WHEN NEW.node_level=3 AND
     *       -- is the current author+series different from the one inserted previously?
     *       NOT EXISTS(SELECT 1 FROM tmp_book_list_1_th AS tht WHERE COALESCE(tht.blg_sort_aut,'')=COALESCE(NEW.blg_sort_aut,'') AND COALESCE(tht.blg_sort_ser,'')=COALESCE(NEW.blg_sort_ser,''))
     *         BEGIN
     *         -- insert the series
     *            INSERT INTO tmp_book_list_1 (node_level,node_group,node_key,node_expanded,node_visible,
     *                        author_formatted,blg_sort_aut,author,author_complete,real_author,
     *                        series_name,blg_sort_ser,series_id,series_complete)
     *                   VALUES (2,2,NEW.node_key,0,0,
     *                       NEW.author_formatted,NEW.blg_sort_aut,NEW.author,NEW.author_complete,NEW.real_author,
     *                       NEW.series_name,NEW.blg_sort_ser,NEW.series_id,NEW.series_complete);
     *         END
     *
     *    -- before inserting a series
     *    CREATE TEMPORARY TRIGGER tmp_book_list_1_TG_LEVEL_1 BEFORE INSERT ON tmp_book_list_1
     *    FOR EACH ROW WHEN NEW.node_level=2 AND
     *        -- is the current author different from the one inserted previously?
     *        NOT EXISTS(SELECT 1 FROM tmp_book_list_1_th AS tht WHERE COALESCE(tht.blg_sort_aut,'')=COALESCE(NEW.blg_sort_aut,''))
     *          BEGIN
     *          -- insert an author
     *             INSERT INTO tmp_book_list_1 (node_level,node_group,node_key,node_expanded,node_visible,
     *                         author_formatted,blg_sort_aut,author,author_complete,real_author)
     *                    VALUES (1,1,NEW.node_key,0,1,
     *                         NEW.author_formatted,NEW.blg_sort_aut,NEW.author,NEW.author_complete,NEW.real_author);
     *         END
     *
     *    -- track the last inserted full group-hierarchy used
     *    CREATE TEMPORARY TRIGGER tmp_book_list_1_TG_CURRENT AFTER INSERT ON tmp_book_list_1
     *    FOR EACH ROW WHEN NEW.node_level=2 BEGIN
     *        DELETE FROM tmp_book_list_1_th;
     *        INSERT INTO tmp_book_list_1_th VALUES (NEW.node_level,NEW.blg_sort_aut,NEW.blg_sort_ser,NEW.blg_sort_ser_num_f,NEW.series_num,NEW.title_ob);
     *    END
     * </pre>
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

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            LoggerFactory.getLogger()
                         .d(TAG, "build", "triggerHelperTable="
                                          + triggerHelperTable.getCreateStatement(
                                 triggerHelperTable.getName(), true)
                         );
        }

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
                    .add(DBKey.BL_NODE.LEVEL)
                    .add(DBKey.BL_NODE.GROUP)
                    .add(DBKey.BL_NODE.KEY)
                    .add(DBKey.BL_NODE.EXPANDED)
                    .add(DBKey.BL_NODE.VISIBLE);

            // RebuildBooklist.Expanded must explicitly be set to 1/1
            // All others must be set to 0/0. The actual state will be set afterwards.
            final String expVis = (rebuildMode == RebuildBooklist.Expanded) ? "1" : "0";

            // Create the VALUES clause for the next level up
            final StringJoiner listValues = new StringJoiner(",", "(", ")")
                    .add(String.valueOf(level))
                    .add(String.valueOf(group.getId()))
                    .add("NEW." + DBKey.BL_NODE.KEY)
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
                    CREATE_TEMPORARY_TRIGGER_ + triggerHelperLevelTriggerName[index]
                    + " BEFORE INSERT ON " + listTable.getName() + " FOR EACH ROW"
                    + " WHEN NEW." + DBKey.BL_NODE.LEVEL + '=' + (level + 1)
                    + " AND NOT EXISTS("
                    + "SELECT 1 FROM " + triggerHelperTable.ref() + _WHERE_ + whereClause + ')'
                    + _BEGIN_
                    + INSERT_INTO_ + listTable.getName()
                    + ' ' + listColumns + _VALUES_ + listValues + ';'
                    + END;

            db.execSQL(levelTgSql);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                LoggerFactory.getLogger()
                             .d(TAG, "build", "level=" + level
                                              + "|TgSql=" + levelTgSql);
            }
        }

        // Create a trigger to maintain the 'current' value
        triggerHelperCurrentValueTriggerName = listTable.getName() + "_TG_CURRENT";
        db.execSQL(DROP_TRIGGER_IF_EXISTS_ + triggerHelperCurrentValueTriggerName);
        // This is a single row only, so delete the previous value, and insert the current one
        final String currentValueTgSql =
                CREATE_TEMPORARY_TRIGGER_ + triggerHelperCurrentValueTriggerName
                + " AFTER INSERT ON " + listTable.getName() + " FOR EACH ROW"
                + " WHEN NEW." + DBKey.BL_NODE.LEVEL + '=' + groupCount
                + _BEGIN_
                + DELETE_FROM_ + triggerHelperTable.getName() + ';'
                + INSERT_INTO_ + triggerHelperTable.getName() + _VALUES_ + currentValues + ";"
                + END;
        db.execSQL(currentValueTgSql);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            LoggerFactory.getLogger()
                         .d(TAG, "build", "currentValue|TgSql=" + currentValueTgSql);
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

    private void createLevelSums(@NonNull final SynchronizedDb db) {

        // the first level above the books == number of groups
        final int groupCount = style.getGroupCount();

        // count the number of books for each lowest-level group
        // and store the result on that group
        final String sqlBookCount = createLevelSumsBooks(groupCount + 1);
        db.execSQL(sqlBookCount);

        final List<String> keyColumns = style
                .getGroupList().stream()
                .map(BooklistGroup::getGroupKey)
                .map(GroupKey::getKeyDomainExpression)
                .map(DomainExpression::getDomain)
                .map(Domain::getName)
                .collect(Collectors.toList());

        for (int level = groupCount; level > 1; level--) {
            final String sqlGroupSum =
                    createLevelSumsGroups(keyColumns.subList(0, level - 1), level);

            // for each group, sum the book-counts, and store them in the next group up.
            db.execSQL(sqlGroupSum);
        }
    }

    @NonNull
    private String createLevelSumsBooks(final int level) {

        final String nodeLevel = listTable.getName() + "." + DBKey.BL_NODE.LEVEL;
        // Note we use the single concatenated/string key column.
        final String keys = listTable.getName() + "." + DBKey.BL_NODE.KEY;

        // Create a temp view for use with a WITH-clause
        // collecting the [node-key, count] pairs for the books.
        // This count will be stored on the level just above the books.
        final String withSelectClause =
                "("
                + SELECT_ + keys + ", COUNT(*)" + _AS_ + CA_SUM
                + _FROM_ + listTable.getName()
                + _WHERE_ + nodeLevel + "=" + level
                + _GROUP_BY_ + keys
                + ")";

        final String bookSetter =
                "("
                + SELECT_ + TA_WITH + "." + CA_SUM
                + _FROM_ + TA_WITH
                + _WHERE_ + TA_WITH + "." + DBKey.BL_NODE.KEY + "=" + keys
                + ")";

        return WITH_ + TA_WITH + _AS_ + withSelectClause
               + UPDATE_ + listTable.getName()
               + _SET_ + DBKey.FK_BOOK + "=" + bookSetter
               + _WHERE_ + nodeLevel + "=" + (level - 1)
               + _AND_ + keys + " IN (SELECT " + DBKey.BL_NODE.KEY + _FROM_ + TA_WITH + ")";
    }

    @NonNull
    private String createLevelSumsGroups(@NonNull final List<String> keyColumns,
                                         final int level) {

        final String nodeLevel = listTable.getName() + "." + DBKey.BL_NODE.LEVEL;
        // Note we use the list of individual key (id) columns
        final String keys = keyColumns.stream()
                                      .map(key -> listTable.getName() + "." + key)
                                      .collect(Collectors.joining(","));

        final String withSelectClause =
                "("
                + SELECT_ + keys + ", SUM(" + DBKey.FK_BOOK + ")" + _AS_ + CA_SUM
                + _FROM_ + listTable.getName()
                + _WHERE_ + nodeLevel + "=" + level
                + _GROUP_BY_ + keys
                + ")";

        final String bookSetter =
                "("
                + SELECT_ + TA_WITH + "." + CA_SUM
                + _FROM_ + TA_WITH
                + _WHERE_ + createKeyEquality(keyColumns, TA_WITH)
                + ")";

        return WITH_ + TA_WITH + _AS_ + withSelectClause
               + UPDATE_ + listTable.getName()
               + _SET_ + DBKey.FK_BOOK + "=" + bookSetter
               + _WHERE_ + nodeLevel + "=" + (level - 1)
               + _AND_ + "EXISTS "
               + "(SELECT 1 FROM " + TA_WITH + _WHERE_ + createKeyEquality(keyColumns,
                                                                           TA_WITH) + ")";
    }

    @NonNull
    private String createKeyEquality(@NonNull final List<String> keyColumns,
                                     @SuppressWarnings("SameParameterValue")
                                     @NonNull final String table) {
        return keyColumns.stream()
                         .map(k -> listTable.getName() + "." + k + "=" + table + "." + k)
                         .collect(Collectors.joining(_AND_));
    }
}
