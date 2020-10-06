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
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Groups;
import com.hardbacknutter.nevertoomanybooks.database.DAOSql;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.VirtualDomain;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_EXPANDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_VISIBLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_EXPANDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_VISIBLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_PUBLISHER_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;

/**
 * A Builder to accumulates data while building the list table
 * and produce the initial SQL insert statement.
 * <p>
 * While building, the triggers will make sure that the top-level(==1) nodes
 * are always visible. This is critical for some parts of te code which rely on this.
 */
final class BooklistBuilder {

    /** Log tag. */
    private static final String TAG = "BooklistBuilder";

    private static final String INSERT_INTO_ = "INSERT INTO ";
    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _AND_ = " AND ";
    private static final String _ORDER_BY_ = " ORDER BY ";
    private static final String _AS_ = " AS ";

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

    @NonNull
    private final BooklistStyle mStyle;

    /** Set to {@code true} if we're filtering on a specific bookshelf. */
    private final boolean mFilteredOnBookshelf;

    /** the list of Filters. */
    private final Collection<Filter<?>> mFilters = new ArrayList<>();

    /** The table we'll be generating. */
    @NonNull
    private final TableDefinition mListTable;

    /**
     * Domains required in output table.
     * During the build, the {@link Domain} is added to the {@link #mListTable}
     * where they will be used to create the table.
     * <p>
     * The full {@link VirtualDomain} is kept this collection to build the SQL column string
     * for both the INSERT and the SELECT statement.
     */
    private final Collection<VirtualDomain> mDomains = new ArrayList<>();

    /** Domains belonging the current group including its outer groups. */
    private final List<Domain> mAccumulatedDomains = new ArrayList<>();

    /**
     * Domains that form part of the sort key.
     * These are typically a reduced set of the group domains since the group domains
     * may contain more than just the key
     */
    @SuppressWarnings("TypeMayBeWeakened")
    private final List<VirtualDomain> mOrderByDomains = new ArrayList<>();

    /** Guards from adding duplicates. */
    private final Map<Domain, String> mExpressionsDupCheck = new HashMap<>();

    /** Guards from adding duplicates. */
    private final Collection<String> mOrderByDupCheck = new HashSet<>();

    @Booklist.ListRebuildMode
    private final int mRebuildState;

    private String mSqlForInitialInsert;

    /** Table used by the triggers to store the snapshot of the most recent/current row headings. */
    private TableDefinition mTriggerHelperTable;
    private String mTriggerHelperLevelTriggerName;
    private String mTriggerHelperCurrentValueTriggerName;

    /**
     * Constructor.
     *
     * @param instanceId   of the {@link Booklist} to create a unique table name
     * @param style        to apply to the list
     * @param bookshelf    to use
     * @param rebuildState the mode to use for restoring the saved state.
     */
    BooklistBuilder(final int instanceId,
                    @NonNull final BooklistStyle style,
                    @NonNull final Bookshelf bookshelf,
                    @Booklist.ListRebuildMode final int rebuildState) {
        mStyle = style;
        mFilteredOnBookshelf = !bookshelf.isAllBooks();
        mRebuildState = rebuildState;

        // Setup the table (but don't create it yet)
        // The base definition comes with a small set of essential domains.
        mListTable = new TableDefinition(DBDefinitions.TMP_TBL_BOOK_LIST);
        mListTable.setName(mListTable.getName() + instanceId);
    }

    /**
     * Using the collected domain info, create the various SQL phrases used to build
     * the resulting flat list table and build the SQL that does the initial table load.
     * <p>
     * This method does not access the database.
     *
     * @param context     Current context
     * @param bookDomains list of domains to add on the book level
     * @param filters     to use for the WHERE clause
     **/
    void preBuild(@NonNull final Context context,
                  @NonNull final Collection<VirtualDomain> bookDomains,
                  @NonNull final Collection<Filter<?>> filters) {

        // Always sort by level first; no expression, as this does not represent a value.
        addDomain(new VirtualDomain(
                DOM_BL_NODE_LEVEL, null, VirtualDomain.SORT_ASC), false);

        // The level expression; for a book this is always 1 below the #groups obviously
        addDomain(new VirtualDomain(
                DOM_BL_NODE_LEVEL, String.valueOf(mStyle.getGroups().size() + 1)), false);

        // The group is the book group (duh)
        addDomain(new VirtualDomain(
                DOM_BL_NODE_GROUP, String.valueOf(BooklistGroup.BOOK)), false);

        // The book id itself
        addDomain(new VirtualDomain(DOM_FK_BOOK, TBL_BOOKS.dot(KEY_PK_ID)), false);

        // Add style-specified groups
        for (BooklistGroup group : mStyle.getGroups().getGroupList()) {
            addGroup(group);
        }

        // Add caller-specified book data
        for (VirtualDomain bookDomain : bookDomains) {
            addDomain(bookDomain, false);
        }

        // Add the active filters from the style
        mFilters.addAll(mStyle.getFilters().getActiveFilters(context));
        // And finally, add all filters to be used for the WHERE clause
        mFilters.addAll(filters);


        // List of column names for the INSERT INTO... clause
        final StringBuilder destColumns = new StringBuilder();
        // List of expressions for the SELECT... clause.
        final StringBuilder sourceColumns = new StringBuilder();

        // The 'AS' is for SQL readability/debug only

        boolean first = true;
        for (VirtualDomain domain : mDomains) {
            if (first) {
                first = false;
            } else {
                destColumns.append(',');
                sourceColumns.append(',');
            }

            destColumns.append(domain.getName());
            sourceColumns.append(domain.getExpression())
                         .append(_AS_)
                         .append(domain.getName());
        }

        // add the node key column
        destColumns.append(',')
                   .append(KEY_BL_NODE_KEY);
        sourceColumns.append(',')
                     .append(buildNodeKey())
                     .append(_AS_)
                     .append(DOM_BL_NODE_KEY);

        // and the node state columns
        destColumns.append(',').append(DOM_BL_NODE_EXPANDED);
        destColumns.append(',').append(DOM_BL_NODE_VISIBLE);

        // PREF_REBUILD_EXPANDED must explicitly be set to 1/1
        // All others must be set to 0/0. The actual state will be set afterwards.
        switch (mRebuildState) {
            case Booklist.PREF_REBUILD_COLLAPSED:
            case Booklist.PREF_REBUILD_PREFERRED_STATE:
            case Booklist.PREF_REBUILD_SAVED_STATE:
                sourceColumns.append(",0 AS ").append(DOM_BL_NODE_EXPANDED);
                sourceColumns.append(",0 AS ").append(DOM_BL_NODE_VISIBLE);
                break;

            case Booklist.PREF_REBUILD_EXPANDED:
                sourceColumns.append(",1 AS ").append(DOM_BL_NODE_EXPANDED);
                sourceColumns.append(",1 AS ").append(DOM_BL_NODE_VISIBLE);
                break;

            default:
                throw new UnexpectedValueException(mRebuildState);
        }

        mSqlForInitialInsert =
                INSERT_INTO_ + mListTable.getName() + " (" + destColumns + ") "
                + SELECT_ + sourceColumns
                + _FROM_ + buildFrom(context) + buildWhere(context)
                + _ORDER_BY_ + buildOrderBy();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "build|sql=" + mSqlForInitialInsert);
        }
    }

    /**
     * Create the table and data.
     *
     * @param syncedDb Underlying database
     *
     * @return the fully populated list table.
     */
    @NonNull
    TableDefinition build(final SynchronizedDb syncedDb) {
        if (BuildConfig.DEBUG /* always */) {
            SanityCheck.requireValue(mSqlForInitialInsert, "preBuild() must be called first");

            if (!syncedDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        //IMPORTANT: withConstraints MUST BE false
        mListTable.recreate(syncedDb, false);

        // get the triggers in place, ready to act on our upcoming initial insert.
        createTriggers(syncedDb);

        // Build the lowest level (i.e. books) using our initial insert statement
        // The triggers will do the other levels.
        int initialInsertCount;

        long t0 = System.nanoTime();

        try (SynchronizedStatement stmt = syncedDb.compileStatement(mSqlForInitialInsert)) {
            initialInsertCount = stmt.executeUpdateDelete();
        }
        long t1_insert = System.nanoTime();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
            Log.d(TAG, "build|insert(" + initialInsertCount + "): "
                       + ((t1_insert - t0) / NANO_TO_MILLIS) + " ms");
        }
        if (!DBHelper.isCollationCaseSensitive()) {
            // can't do this, IndexDefinition class does not support DESC columns for now.
            // mListTable.addIndex("SDI", false, helper.getSortedDomains());
            syncedDb.execSQL(
                    "CREATE INDEX " + mListTable.getName() + "_SDI ON " + mListTable.getName()
                    + "(" + getSortedDomainsIndexColumns() + ")");
        }

        // The list table is now fully populated.
        syncedDb.analyze(mListTable);

        // remove the no longer needed triggers
        cleanupTriggers(syncedDb);

        return mListTable;
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
                       // don't add duplicate domains
                       .filter(sdi -> !sortedDomainNames.contains(sdi.getName()))
                       .forEach(sdi -> {
                           sortedDomainNames.add(sdi.getName());
                           if (valuesColumns.length() > 0) {
                               valuesColumns.append(",");
                           }
                           valuesColumns.append("NEW.").append(sdi.getName());
                           mTriggerHelperTable.addDomain(sdi.getDomain());
                       });

        /*
         * Create a temp table to store the most recent header details from the last row.
         * We use this in determining what needs to be inserted as header records for
         * any given row.
         *
         * This is just a simple technique to provide persistent context to the trigger.
         */
        //IMPORTANT: withConstraints MUST BE false
        mTriggerHelperTable.recreate(db, false);

        final int groupCount = mStyle.getGroups().size();

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
                    .append(KEY_BL_NODE_LEVEL)
                    .append(',').append(KEY_BL_NODE_GROUP)
                    .append(',').append(KEY_BL_NODE_KEY)
                    .append(',').append(KEY_BL_NODE_EXPANDED)
                    .append(',').append(KEY_BL_NODE_VISIBLE);

            // PREF_REBUILD_EXPANDED must explicitly be set to 1/1
            // All others must be set to 0/0. The actual state will be set afterwards.
            final int expVis = (mRebuildState == Booklist.PREF_REBUILD_EXPANDED) ? 1 : 0;

            // Create the VALUES clause for the next level up
            final StringBuilder listValues = new StringBuilder()
                    .append(level)
                    .append(',').append(group.getId())
                    .append(",NEW.").append(KEY_BL_NODE_KEY)
                    .append(",").append(expVis)
                    // level 1 is always visible. THIS IS CRITICAL!
                    .append(",").append(level == 1 ? 1 : expVis);

            // Create the where-clause to detect if the next level up is already defined
            // (by checking the 'current' record/table)
            final StringBuilder whereClause = new StringBuilder();

            //noinspection ConstantConditions
            for (Domain groupDomain : group.getAccumulatedDomains()) {
                listColumns.append(',').append(groupDomain.getName());
                listValues.append(", NEW.").append(groupDomain.getName());

                // Only add to the where-clause if the group is part of the SORT list
                if (sortedDomainNames.contains(groupDomain.getName())) {
                    if (whereClause.length() > 0) {
                        whereClause.append(_AND_);
                    }
                    whereClause.append("COALESCE(")
                               .append(mTriggerHelperTable.dot(groupDomain.getName()))
                               .append(",'')=COALESCE(NEW.")
                               .append(groupDomain.getName())
                               .append(",'')")
                               .append(DAOSql._COLLATION);
                }
            }

            // (re)Create the trigger
            mTriggerHelperLevelTriggerName = mListTable.getName() + "_TG_LEVEL_" + level;
            db.execSQL("DROP TRIGGER IF EXISTS " + mTriggerHelperLevelTriggerName);
            final String levelTgSql =
                    "\nCREATE TEMPORARY TRIGGER " + mTriggerHelperLevelTriggerName
                    + " BEFORE INSERT ON " + mListTable.getName() + " FOR EACH ROW"
                    + "\n WHEN NEW." + KEY_BL_NODE_LEVEL + '=' + (level + 1)
                    + " AND NOT EXISTS("
                    + /* */ "SELECT 1 FROM " + mTriggerHelperTable.ref() + _WHERE_ + whereClause
                    + /* */ ')'
                    + "\n BEGIN"
                    + "\n  INSERT INTO " + mListTable.getName() + " (" + listColumns + ")"
                    + /*             */ " VALUES(" + listValues + ");"
                    + "\n END";

            db.execSQL(levelTgSql);
        }

        // Create a trigger to maintain the 'current' value
        mTriggerHelperCurrentValueTriggerName = mListTable.getName() + "_TG_CURRENT";
        db.execSQL("DROP TRIGGER IF EXISTS " + mTriggerHelperCurrentValueTriggerName);
        // This is a single row only, so delete the previous value, and insert the current one
        final String currentValueTgSql =
                "\nCREATE TEMPORARY TRIGGER " + mTriggerHelperCurrentValueTriggerName
                + " AFTER INSERT ON " + mListTable.getName() + " FOR EACH ROW"
                + "\n WHEN NEW." + KEY_BL_NODE_LEVEL + '=' + groupCount
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
            db.execSQL("DROP TRIGGER IF EXISTS " + mTriggerHelperCurrentValueTriggerName);
        }
        if (mTriggerHelperLevelTriggerName != null) {
            db.execSQL("DROP TRIGGER IF EXISTS " + mTriggerHelperLevelTriggerName);
        }
        if (mTriggerHelperTable != null) {
            db.drop(mTriggerHelperTable.getName());
        }
    }

    /**
     * Add a VirtualDomain.
     * This encapsulates the actual Domain, the expression, and the (optional) sort flag.
     *
     * @param vDomain VirtualDomain to add
     * @param isGroup flag: the added VirtualDomain is a group level domain.
     */
    private void addDomain(@NonNull final VirtualDomain vDomain,
                           final boolean isGroup) {

        // Add to the table, if not already there
        final boolean added = mListTable.addDomain(vDomain.getDomain());
        final String expression = vDomain.getExpression();

        // If the domain was already present, and it has an expression,
        // check the expression being different (or not) from the stored expression
        if (!added
            && expression != null
            && expression.equals(mExpressionsDupCheck.get(vDomain.getDomain()))) {
            // same expression, we do NOT want to add it.
            // This is NOT a bug, although one could argue it's an efficiency issue.
            return;
        }

        // If the expression is {@code null},
        // then the domain is just meant for the lowest level; i.e. the book.
        if (expression != null) {
            mDomains.add(vDomain);
            mExpressionsDupCheck.put(vDomain.getDomain(), expression);
        }

        // If needed, add to the order-by domains, if not already there
        if (vDomain.isSorted() && !mOrderByDupCheck.contains(vDomain.getDomain().getName())) {
            mOrderByDomains.add(vDomain);
            mOrderByDupCheck.add(vDomain.getDomain().getName());
        }

        // Accumulate the group domains.
        if (isGroup) {
            mAccumulatedDomains.add(vDomain.getDomain());
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
        addDomain(group.getDisplayDomain(), true);
        // then how we group
        for (VirtualDomain domain : group.getGroupDomains()) {
            addDomain(domain, true);
        }
        // the base domains we always need/have
        for (VirtualDomain domain : group.getBaseDomains()) {
            addDomain(domain, false);
        }

        /*
         * Copy all current groups to this group; this effectively accumulates
         * 'GROUP BY' domains down each level so that the top has fewest groups and
         * the bottom level has groups for all levels.
         *
         * Since BooklistGroup objects are processed in order, this allows us to get
         * the fields applicable to the currently processed group, including its outer groups.
         *
         * As subsequent addGroup calls will modify this collection,
         * we make a (shallow) copy of the list. (ArrayList as we'll need to parcel it)
         */
        group.setAccumulatedDomains(new ArrayList<>(mAccumulatedDomains));
    }

    /**
     * Create the expression for the key column.
     * Will contain one key-value pair for each each group level.
     * "/key=value/key=value/key=value/..."
     * A {@code null} value is reformatted as an empty string
     *
     * @return column expression
     */
    private String buildNodeKey() {
        final StringBuilder keyColumn = new StringBuilder();
        for (BooklistGroup group : mStyle.getGroups().getGroupList()) {
            keyColumn.append(group.getNodeKeyExpression()).append("||");
        }
        int len = keyColumn.length();
        // remove the trailing "||"
        return keyColumn.delete(len - 2, len).toString();
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
     *      <li>{@link DBDefinitions#TBL_BOOK_LOANEE}</li>
     * </ul>
     *
     * @param context Current context
     *
     * @return FROM clause
     */
    private String buildFrom(@NonNull final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Text of join statement
        final StringBuilder sql = new StringBuilder();

        final Groups styleGroups = mStyle.getGroups();

        // If there is a bookshelf specified (either as group or as a filter),
        // start the join there.
        if (styleGroups.contains(BooklistGroup.BOOKSHELF) || mFilteredOnBookshelf) {
            sql.append(TBL_BOOKSHELF.ref())
               .append(TBL_BOOKSHELF.join(TBL_BOOK_BOOKSHELF))
               .append(TBL_BOOK_BOOKSHELF.join(TBL_BOOKS));
        } else {
            // Otherwise, start with the BOOKS table.
            sql.append(TBL_BOOKS.ref());
        }

        if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_LOANEE)) {
            // get the loanee name, or a {@code null} for available books.
            sql.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_LOANEE));
        }

        // Join with the link table between Book and Author.
        sql.append(TBL_BOOKS.join(TBL_BOOK_AUTHOR));

        // Extend the join filtering on the primary Author unless
        // the user wants the book to show under all its Authors
        if (!mStyle.isShowBooksUnderEachAuthor(context)) {
            @Author.Type
            final int primaryAuthorType = mStyle.getPrimaryAuthorType(context);
            if (primaryAuthorType == Author.TYPE_UNKNOWN) {
                // don't care about Author type, so just grab the primary (i.e. pos==1)
                sql.append(_AND_)
                   .append(TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION)).append("=1");
            } else {
                // grab the desired type, or if no such type, grab the 1st
                //   AND (((type & TYPE)<>0) OR (((type &~ TYPE)=0) AND pos=1))
                sql.append(" AND (((")
                   .append(TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_TYPE_BITMASK))
                   .append(" & ").append(primaryAuthorType).append(")<>0)")
                   .append(" OR (((")
                   .append(TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_TYPE_BITMASK))
                   .append(" &~ ").append(primaryAuthorType).append(")=0)")
                   .append(_AND_)
                   .append(TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION)).append("=1))");
            }
        }
        // Join with Authors to make the names available
        sql.append(TBL_BOOK_AUTHOR.join(TBL_AUTHORS));

        if (styleGroups.contains(BooklistGroup.SERIES)
            || DBDefinitions.isUsed(prefs, DBDefinitions.KEY_SERIES_TITLE)) {
            // Join with the link table between Book and Series.
            sql.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_SERIES));
            // Extend the join filtering on the primary Series unless
            // the user wants the book to show under all its Series
            if (!mStyle.isShowBooksUnderEachSeries(context)) {
                sql.append(_AND_)
                   .append(TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION)).append("=1");
            }
            // Join with Series to make the titles available
            sql.append(TBL_BOOK_SERIES.leftOuterJoin(TBL_SERIES));
        }

        if (styleGroups.contains(BooklistGroup.PUBLISHER)
            || DBDefinitions.isUsed(prefs, DBDefinitions.KEY_PUBLISHER_NAME)) {
            // Join with the link table between Book and Publishers.
            sql.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_PUBLISHER));
            // Extend the join filtering on the primary Publisher unless
            // the user wants the book to show under all its Publishers
            if (!mStyle.isShowBooksUnderEachPublisher(context)) {
                sql.append(_AND_)
                   .append(TBL_BOOK_PUBLISHER.dot(KEY_BOOK_PUBLISHER_POSITION)).append("=1");
            }
            // Join with Publishers to make the names available
            sql.append(TBL_BOOK_PUBLISHER.leftOuterJoin(TBL_PUBLISHERS));
        }
        return sql.toString();
    }

    /**
     * Create the WHERE clause based on all active filters (for in-use domains).
     *
     * @param context Current context
     *
     * @return WHERE clause, can be empty
     */
    private String buildWhere(@NonNull final Context context) {
        final StringBuilder where = new StringBuilder();

        mFilters.stream()
                // Theoretically all filters should be active here, but paranoia...
                .filter(filter -> filter.isActive(context))
                .forEach(filter -> {
                    if (where.length() != 0) {
                        where.append(_AND_);
                    }
                    where.append(' ').append(filter.getExpression(context));
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
    private String buildOrderBy() {
        // List of column names appropriate for 'ORDER BY' clause
        final StringBuilder orderBy = new StringBuilder();
        for (VirtualDomain sd : mOrderByDomains) {
            if (sd.getDomain().isText()) {
                // The order of this if/elseif/else is important, don't merge branch 1 and 3!
                if (sd.getDomain().isPrePreparedOrderBy()) {
                    // always use a pre-prepared order-by column as-is
                    orderBy.append(sd.getName());

                } else if (DBHelper.isCollationCaseSensitive()) {
                    // If {@link DAO#COLLATION} is case-sensitive, lowercase it.
                    // This should never happen, but see the DAO method docs.
                    orderBy.append("lower(").append(sd.getName()).append(')');

                } else {
                    // hope for the best. This case might not handle non-[A..Z0..9] as expected
                    orderBy.append(sd.getName());
                }

                orderBy.append(DAOSql._COLLATION);
            } else {
                orderBy.append(sd.getName());
            }

            orderBy.append(sd.getSortedExpression()).append(',');
        }

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

        for (VirtualDomain sd : mOrderByDomains) {
            indexCols.append(sd.getName());
            if (sd.getDomain().isText()) {
                indexCols.append(DAOSql._COLLATION);
            }
            indexCols.append(sd.getSortedExpression()).append(',');
        }
        final int len = indexCols.length();
        return indexCols.delete(len - 1, len).toString();
    }
}
