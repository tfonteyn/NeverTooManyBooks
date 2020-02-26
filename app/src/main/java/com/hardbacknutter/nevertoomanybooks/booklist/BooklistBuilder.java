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

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
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
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_AUTHOR_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_BOOK_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_SERIES_NUM_FLOAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_SERIES_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_LIST_VIEW_ROW_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TMP_TBL_BOOK_LIST;

/**
 * Class used to build and populate temporary tables with details of a flattened book
 * list used to display books in a list control and perform operation like
 * 'expand/collapse' on nodes in the list.
 * <p>
 * A BooklistBuilder has a 1:n relation to {@link BooklistCursor} objects.
 * Right now (2020-01-16) it's a 1:2 relation,
 * i.e. two cursors with the same builder can exist: the current cursor, and a new cursor
 * which is going to replace the old cursor. See {@link #closeCursor}.
 * Cursors hold a reference to the BooklistBuilder.
 */
public class BooklistBuilder
        implements AutoCloseable {

    /** id values for state preservation property. See {@link ListRebuildMode}. */
    public static final int PREF_REBUILD_SAVED_STATE = 0;
    public static final int PREF_REBUILD_ALWAYS_EXPANDED = 1;
    @SuppressWarnings("WeakerAccess")
    public static final int PREF_REBUILD_ALWAYS_COLLAPSED = 2;
    @SuppressWarnings("WeakerAccess")
    public static final int PREF_REBUILD_PREFERRED_STATE = 3;
    /** Flag (bitmask) indicating added domain is for Books. */
    static final int FLAG_BOOK = 0;
    /** Flag indicating domain is part of the current group. */
    static final int FLAG_GROUP = 1;
    /** Flag indicating domain should be added to the ORDER BY clause. */
    static final int FLAG_SORT = 1 << 1;
    /** Used in combination with FLAG_SORT. */
    @SuppressWarnings("WeakerAccess")
    static final int FLAG_DESC = 1 << 2;
    /** Convenience combination flag. */
    static final int FLAG_SORT_DESC = FLAG_SORT | FLAG_DESC;
    /** Log tag. */
    private static final String TAG = "BooklistBuilder";
    /** Counter for BooklistBuilder ID's. Used to create unique table names etc... */
    @NonNull
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();

    /** DEBUG Instance counter. */
    @NonNull
    private static final AtomicInteger DEBUG_INSTANCE_COUNTER = new AtomicInteger();

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

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
    private final Map<String, BookDomain> mBookDomains = new HashMap<>();

    /** the list of Filters. */
    private final Collection<Filter> mFilters = new ArrayList<>();
    /** Style to use while building the list. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final BooklistStyle mStyle;
    /** Show only books on this bookshelf. */
    @NonNull
    private final Bookshelf mBookshelf;
    /**
     * The temp table representing the booklist for the current bookshelf/style.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private TableDefinition mListTable;

    /**
     * The temp table holding the state (expand,visibility,...) for all rows in the list-table.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private RowStateDAO mRowStateDAO;
    /** Table used by the triggers to store the snapshot of the most recent/current row headings. */
    @SuppressWarnings("FieldNotUsedInToString")
    private TableDefinition mTriggerHelperTable;
    /** used in debug. */
    @SuppressWarnings("FieldNotUsedInToString")
    private boolean mDebugReferenceDecremented;
    /** DEBUG: Indicates close() has been called. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     *
     * @param style Booklist style to use;
     *              this is the resolved style as used by the passed bookshelf
     */
    public BooklistBuilder(@NonNull final BooklistStyle style,
                           @NonNull final Bookshelf bookshelf) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "ENTER|BooklistBuilder"
                       + "|style=" + style.getUuid()
                       + "|instances: " + DEBUG_INSTANCE_COUNTER.incrementAndGet());
        }
        // Allocate ID
        mInstanceId = ID_COUNTER.incrementAndGet();

        mStyle = style;
        // Filter on the specified Bookshelf.
        mBookshelf = bookshelf;
        // The filter will only be added if the current style does not contain the Bookshelf group.
        if (!bookshelf.isAllBooks() && !mStyle.containsGroup(BooklistGroup.BOOKSHELF)) {
            mFilters.add(() -> '(' + TBL_BOOKSHELF.dot(KEY_PK_ID) + '=' + bookshelf.getId() + ')');
        }

        // Get the database and create a statements collection
        mDb = new DAO(TAG);
        mSyncedDb = mDb.getSyncDb();
        mStatementManager = new SqlStatementManager(mSyncedDb, TAG + "|" + mInstanceId);
    }

    /**
     * Get the current preferred rebuild state for the list.
     *
     * @return ListRebuildMode
     */
    @ListRebuildMode
    public static int getPreferredListRebuildState() {
        return PIntString.getListPreference(Prefs.pk_booklist_rebuild_state,
                                            PREF_REBUILD_SAVED_STATE);
    }

    /**
     * Close the 'cursorToClose' in a safe way, checking the BooklistBuilder is not used
     * by the 'currentCursor'.
     *
     * @param cursorToClose the one to close
     * @param currentCursor the one to check
     */
    public static void closeCursor(@Nullable final BooklistCursor cursorToClose,
                                   @Nullable final BooklistCursor currentCursor) {
        if (cursorToClose != null && cursorToClose != currentCursor) {
            BooklistBuilder blbToClose = cursorToClose.getBooklistBuilder();
            if (currentCursor == null || !blbToClose.equals(currentCursor.getBooklistBuilder())) {
                blbToClose.close();
            }
            cursorToClose.close();
        }
    }

    /**
     * Create a flattened table (a snapshot!) of ordered book ID's based on the underlying list.
     * Any old data is removed before the new table is created.
     *
     * @return the name of the created table.
     */
    @NonNull
    public String createFlattenedBooklist() {
        // reminder: do not drop this table. It needs to survive beyond the booklist screen.
        return FlattenedBooklist.createTable(mSyncedDb, mInstanceId, mRowStateDAO, mListTable);
    }

    /**
     * Add a domain to the resulting flattened list based on the details provided.
     *
     * @param bookDomain Domain to add
     */
    public void addDomain(@NonNull final BookDomain bookDomain) {
        if (!mBookDomains.containsKey(bookDomain.domain.getName())) {
            mBookDomains.put(bookDomain.domain.getName(), bookDomain);

        } else {
            // adding a duplicate here is a bug.
            throw new IllegalArgumentException("Duplicate domain=`"
                                               + bookDomain.domain.getName() + '`');
        }
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

            mFilters.add(() -> '(' + TBL_BOOKS.dot(KEY_PK_ID)
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
                               + TBL_BOOK_LOANEE.dot(KEY_LOANEE)
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
            mFilters.add(new WildcardFilter(TBL_SERIES, KEY_SERIES_TITLE, filter));
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
            mFilters.add(new ListOfValuesFilter<>(TBL_BOOKS, KEY_PK_ID, filter));
        }
    }

    /**
     * Clear and build the temporary list of books.
     * Criteria must be set before calling this method with one or more of the setCriteria calls.
     *
     * @param listState State to display
     */
    public void build(@ListRebuildMode final int listState) {
        final long t00 = System.nanoTime();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "ENTER|build|mInstanceId=" + mInstanceId);
        }

        // Setup the tables (but don't create them yet)
        mListTable = new TableDefinition(TMP_TBL_BOOK_LIST);
        mListTable.setName(mListTable.getName() + mInstanceId);
        mListTable.addDomain(DOM_PK_ID);
        // {@link BooklistGroup#GroupKey}. The actual value is set on a by-group/book basis.
        mListTable.addDomain(DOM_BL_NODE_KEY);

        // Start building the table domains
        final BuildHelper helper = new BuildHelper(mListTable, mStyle, mBookshelf);

        // always sort by level first
        helper.addOrderBy(DOM_BL_NODE_LEVEL, false);

        // add the basic book domains
        // The book id itself
        helper.addDomain(DOM_FK_BOOK, TBL_BOOKS.dot(KEY_PK_ID), FLAG_BOOK);
        // The level for the book, always 1 below the #groups obviously
        helper.addDomain(DOM_BL_NODE_LEVEL, String.valueOf(mStyle.getGroupCount() + 1), FLAG_BOOK);
        // The group is the book group (duh)
        helper.addDomain(DOM_BL_NODE_GROUP, String.valueOf(BooklistGroup.BOOK), FLAG_BOOK);
        // each row has a book count, for books this is obviously always == 1.
        helper.addDomain(DOM_BL_BOOK_COUNT, "1", FLAG_BOOK);

        // add style-specified groups
        for (BooklistGroup group : mStyle.getGroups()) {
            // add the domains for the group
            helper.addGroup(group);
            // Copy all current groups to this level; this effectively accumulates
            // 'GROUP BY' domains down each level so that the top has fewest groups and
            // the bottom level has groups for all levels.
            group.setDomains(helper.getDomainsForCurrentGroup());
        }

        // add caller-specified book domains
        for (BookDomain bookDomain : mBookDomains.values()) {
            helper.addDomain(bookDomain.domain, bookDomain.expression, FLAG_BOOK);
        }

        // and finally, add all filters to be used for the WHERE clause
        helper.addFilters(mFilters);

        final long t01_domains_done = System.nanoTime();

        // Construct the initial insert statement components.
        String initialInsertSql = helper.build();

        final long t02_base_insert_prepared = System.nanoTime();

        // We are good to go.
        long t03_list_table_created = 0;
        long t04_base_insert_executed = 0;
        long t05_listTable_analyzed = 0;
        long t06_stateTable_created = 0;

        final SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            //IMPORTANT: withConstraints MUST BE false
            mListTable.recreate(mSyncedDb, false)
                      .createIndices(mSyncedDb);

            //TEST: index on list table.
            if (!DBHelper.isCollationCaseSensitive()) {
                // can't do this, IndexDefinition class does not support DESC columns for now.
                // mListTable.addIndex("SDI", false, helper.getSortedDomains());
                mSyncedDb.execSQL("CREATE INDEX " + mListTable + "_SDI ON " + mListTable
                                  + "(" + helper.getSortedDomainsIndexColumns() + ")");
            }

            t03_list_table_created = System.nanoTime();

            // get the triggers in place, ready to act on our upcoming initial insert.
            createTriggers(helper.getSortedDomains());

            // Build the lowest level summary using our initial insert statement
            // The triggers will do the rest.
            mSyncedDb.execSQL(initialInsertSql);

            t04_base_insert_executed = System.nanoTime();

            // The list table is now fully populated.
            mSyncedDb.analyze(mListTable);

            t05_listTable_analyzed = System.nanoTime();

            // build the row-state table for expansion/visibility tracking
            // clean up previously used table (if any)
            if (mRowStateDAO != null) {
                mRowStateDAO.close();
            }

            mRowStateDAO = new RowStateDAO(mSyncedDb, mInstanceId, mStyle, mBookshelf);
            mRowStateDAO.build(mListTable, listState);

            t06_stateTable_created = System.nanoTime();

            mSyncedDb.setTransactionSuccessful();

        } finally {
            mSyncedDb.endTransaction(txLock);

            // we don't catch exceptions but we do want to log the time it took here.
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                Log.d(TAG, "build|" + String.format(
                        Locale.UK, ""
                                   + "\ndomain setup        : %5d"
                                   + "\nbuild insert        : %5d"
                                   + "\nlist table created  : %5d"
                                   + "\nbase insert executed: %5d"
                                   + "\nlist table analyzed : %5d"
                                   + "\nstate table build   : %5d"
                                   + "\n============================"
                                   + "\nTotal time in ms    : %5d",

                        (t01_domains_done - t00) / NANO_TO_MILLIS,
                        (t02_base_insert_prepared - t01_domains_done) / NANO_TO_MILLIS,
                        (t03_list_table_created - t02_base_insert_prepared) / NANO_TO_MILLIS,
                        (t04_base_insert_executed - t03_list_table_created) / NANO_TO_MILLIS,
                        (t05_listTable_analyzed - t04_base_insert_executed) / NANO_TO_MILLIS,
                        (t06_stateTable_created - t05_listTable_analyzed) / NANO_TO_MILLIS,
                        (System.nanoTime() - t00) / NANO_TO_MILLIS));
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Log.d(TAG, "EXIT|build|mInstanceId=" + mInstanceId);
            }
        }
    }

    /**
     * Build a collection of triggers on the list table designed to fill in the summary/header
     * records as the data records are added in sorted order.
     *
     * @param sortedDomains the domains we need to sort by, in order.
     */
    private void createTriggers(@NonNull final Iterable<SortedDomain> sortedDomains) {

        mTriggerHelperTable = new TableDefinition(mListTable + "_th")
                .setAlias("tht");
        // Allow debug mode to use standard tables so we can export and inspect the content.
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_USES_STANDARD_TABLES) {
            mTriggerHelperTable.setType(TableDefinition.TableType.Standard);
        } else {
            mTriggerHelperTable.setType(TableDefinition.TableType.Temporary);
        }

        // SQL statement to update the 'current' table
        StringBuilder valuesColumns = new StringBuilder();
        // List of domain names for sorting
        Collection<String> sortedDomainNames = new HashSet<>();

        // Build the 'current' header table definition and the sort column list
        for (SortedDomain sdi : sortedDomains) {
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
        //IMPORTANT: withConstraints MUST BE false
        mTriggerHelperTable.recreate(mSyncedDb, false);

        /*
         * For each grouping, starting with the lowest, build a trigger to update the next
         * level up as necessary
         */
        for (int index = mStyle.getGroupCount() - 1; index >= 0; index--) {
            // Get the level number for this group
            final int level = index + 1;

            // Get the group
            final BooklistGroup group = mStyle.getGroupForLevel(level);

            // Create the INSERT columns clause for the next level up
            StringBuilder listColumns = new StringBuilder()
                    .append(KEY_BL_NODE_LEVEL)
                    .append(',').append(KEY_BL_NODE_GROUP)
                    .append(',').append(KEY_BL_NODE_KEY);

            // Create the VALUES clause for the next level up
            StringBuilder listValues = new StringBuilder()
                    .append(level)
                    .append(',').append(group.getId())
                    .append(",New.").append(KEY_BL_NODE_KEY);

            // Create the where-clause to detect if the next level up is already defined
            // (by checking the 'current' record/table)
            StringBuilder whereClause = new StringBuilder();

            //noinspection ConstantConditions
            for (Domain groupDomain : group.getDomains()) {
                listColumns.append(',').append(groupDomain);
                listValues.append(", New.").append(groupDomain);

                // Only add to the where-clause if the group is part of the SORT list
                if (sortedDomainNames.contains(groupDomain.getName())) {
                    if (whereClause.length() > 0) {
                        whereClause.append(" AND ");
                    }
                    whereClause.append("COALESCE(")
                               .append(mTriggerHelperTable.dot(groupDomain.getName()))
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
                    + "\n WHEN New." + KEY_BL_NODE_LEVEL + '=' + (level + 1)
                    + " AND NOT EXISTS("
                    + /* */ "SELECT 1 FROM " + mTriggerHelperTable.ref() + " WHERE " + whereClause
                    + /* */ ')'
                    + "\n BEGIN"
                    + "\n   INSERT INTO " + mListTable.getName() + " (" + listColumns + ")"
                    + /*               */ " VALUES(" + listValues + ");"
                    + "\n END";

            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(levelTgSql)) {
                stmt.execute();
            }
        }

        // Create a trigger to maintain the 'current' value -- just delete and insert
        String currentValueTgName = mListTable.getName() + "_TG_CURRENT";
        mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + currentValueTgName);

        String currentValueTgSql =
                "\nCREATE TEMPORARY TRIGGER " + currentValueTgName
                + " AFTER INSERT ON " + mListTable.getName() + " FOR EACH ROW"
                + "\n WHEN New." + KEY_BL_NODE_LEVEL + '=' + mStyle.getGroupCount()
                + "\n BEGIN"
                + "\n   DELETE FROM " + mTriggerHelperTable.getName() + ';'
                + "\n   INSERT INTO " + mTriggerHelperTable.getName()
                + /*               */ " VALUES (" + valuesColumns + ");"
                + "\n END";

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(currentValueTgSql)) {
            stmt.execute();
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

    /** Wrapper for {@link RowStateDAO#getOffsetCursor}. */
    @NonNull
    Cursor getOffsetCursor(final int position,
                           @SuppressWarnings("SameParameterValue") final int pageSize) {

        return mRowStateDAO.getOffsetCursor(mListTable, position, pageSize);
    }

    /**
     * Get a new list cursor.
     *
     * @return a {@link BooklistCursor} instead of a real cursor.
     */
    @NonNull
    public BooklistCursor getNewListCursor() {
        return new BooklistCursor(this);
    }


    /**
     * Try to sync the previously selected book ID.
     *
     * @param bookId the book to find
     *
     * @return the target rows, or {@code null} if none.
     */
    @Nullable
    public ArrayList<RowStateDAO.ListRowDetails> getTargetRows(final long bookId) {
        // no input, no output...
        if (bookId == 0) {
            return null;
        }

        // get all positions of the book
        ArrayList<RowStateDAO.ListRowDetails> rows =
                mRowStateDAO.getBookPositions(mListTable, bookId);

        if (rows.isEmpty()) {
            return null;
        }

        // First, get the ones that are currently visible...
        ArrayList<RowStateDAO.ListRowDetails> visibleRows = new ArrayList<>();
        for (RowStateDAO.ListRowDetails row : rows) {
            if (row.visible) {
                visibleRows.add(row);
            }
        }

        // If we have any visible rows, only consider those for the new position
        if (!visibleRows.isEmpty()) {
            rows = visibleRows;

        } else {
            // Make them all visible
            for (RowStateDAO.ListRowDetails row : rows) {
                if (!row.visible && row.rowId >= 0) {
                    mRowStateDAO.ensureRowIsVisible(row.rowId, row.level);
                }
            }
            // Recalculate all positions
            for (RowStateDAO.ListRowDetails row : rows) {
                row.listPosition = mRowStateDAO.getListPosition(row.rowId);
            }
        }

        return rows;
    }

    /**
     * Get the full list of all Books (their id only) which are currently in the list table.
     *
     * @return list of book ids
     */
    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        String sql = "SELECT " + KEY_FK_BOOK
                     + " FROM " + mListTable.getName()
                     + " WHERE " + KEY_BL_NODE_GROUP + "=?"
                     + " ORDER BY " + KEY_FK_BOOK;

        try (Cursor cursor = mSyncedDb
                .rawQuery(sql, new String[]{String.valueOf(BooklistGroup.BOOK)})) {
            ArrayList<Long> rows = new ArrayList<>(cursor.getCount());
            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getInt(0);
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
        List<Domain> domains = mListTable.getDomains();
        // Make the array +1 so we can add KEY_BL_LIST_VIEW_ROW_ID
        String[] names = new String[domains.size() + 1];
        for (int i = 0; i < domains.size(); i++) {
            names[i] = domains.get(i).getName();
        }

        names[domains.size()] = KEY_BL_LIST_VIEW_ROW_ID;
        return names;
    }

    /**
     * Count the number of <strong>distinct</strong> book records in the list.
     *
     * @return count
     */
    public int getDistinctBookCount() {
        final long t0 = System.nanoTime();
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(
                "SELECT COUNT(DISTINCT " + KEY_FK_BOOK + ")"
                + " FROM " + mListTable.getName()
                + " WHERE " + KEY_BL_NODE_GROUP + "=?")) {
            stmt.bindLong(1, BooklistGroup.BOOK);
            long count = stmt.count();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                Log.d(TAG, "getDistinctBookCount"
                           + "|count=" + count
                           + "|completed in " + (System.nanoTime() - t0) / NANO_TO_MILLIS + " ms");
            }
            return (int) count;
        }
    }

    /**
     * Count the total number of book records in the list.
     *
     * @return count
     */
    public int getBookCount() {
        final long t0 = System.nanoTime();
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(
                "SELECT COUNT(*)"
                + " FROM " + mListTable.getName()
                + " WHERE " + KEY_BL_NODE_GROUP + "=?")) {
            stmt.bindLong(1, BooklistGroup.BOOK);
            long count = stmt.count();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                Log.d(TAG, "getBookCount"
                           + "|count=" + count
                           + "|completed in " + (System.nanoTime() - t0) / NANO_TO_MILLIS + " ms");
            }
            return (int) count;
        }
    }

    /**
     * Wrapper for {@link RowStateDAO#countVisibleRows()}.
     *
     * @return count
     */
    int countVisibleRows() {
        return mRowStateDAO.countVisibleRows();
    }

    /**
     * Wrapper for {@link RowStateDAO#getListPosition}.
     *
     * @param rowId to check
     *
     * @return Actual list position.
     */
    public int getListPosition(final long rowId) {
        return mRowStateDAO.getListPosition(rowId);
    }

    /** Wrapper for {@link RowStateDAO}. */
    public void saveAllNodes() {
        mRowStateDAO.saveAllNodes();
    }

    /** Wrapper for {@link RowStateDAO}. */
    public void expandAllNodes(final int topLevel,
                               final boolean expand) {
        mRowStateDAO.expandAllNodes(topLevel, expand);
    }

    /**
     * Wrapper for {@link RowStateDAO}.
     *
     * @param leafRowId of the node in the list
     */
    public boolean toggleNode(final long leafRowId) {
        // Yes, we could just take the hard coded arguments one level down,
        // but keeping it this way allows future enhancements.
        return mRowStateDAO.setNode(leafRowId,
                                    RowStateDAO.DesiredNodeState.Toggle,
                                    // if the new state is expand, also expand all children
                                    true, true,
                                    // for all child levels
                                    Integer.MAX_VALUE);
    }

    /**
     * Close the builder.
     * <p>
     * RuntimeException are caught and ignored.
     * <p>
     * We cleanup temporary tables simply to free up no longer needed resources NOW.
     */
    @Override
    public void close() {
        mCloseWasCalled = true;

        mStatementManager.close();

        if (mRowStateDAO != null) {
            mRowStateDAO.close();
        }
        if (mListTable != null) {
            mSyncedDb.drop(mListTable.getName());
        }
        if (mTriggerHelperTable != null) {
            mSyncedDb.drop(mTriggerHelperTable.getName());
        }

        mDb.close();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            if (!mDebugReferenceDecremented) {
                int inst = DEBUG_INSTANCE_COUNTER.decrementAndGet();
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
        BooklistBuilder that = (BooklistBuilder) obj;
        return mInstanceId == that.mInstanceId;
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistBuilder{"
               + "mInstanceId=" + mInstanceId
               + ", mCloseWasCalled=" + mCloseWasCalled
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
             PREF_REBUILD_ALWAYS_EXPANDED,
             PREF_REBUILD_ALWAYS_COLLAPSED,
             PREF_REBUILD_PREFERRED_STATE})
    public @interface ListRebuildMode {

    }

    /**
     * Should really use:.
     * enum Sort { No, Asc, Desc}
     * boolean group   (true fo group, false for book)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {FLAG_BOOK,
                                  FLAG_GROUP,
                                  FLAG_SORT,
                                  FLAG_SORT_DESC
    })
    @interface Flags {

    }

    /**
     * A value class for domain + desc/asc sorting flag.
     */
    static class SortedDomain {

        @NonNull
        final Domain domain;
        final boolean isDescending;

        SortedDomain(@NonNull final Domain domain,
                     final boolean isDescending) {
            this.domain = domain;
            this.isDescending = isDescending;
        }
    }

    /**
     * A value class of Book level domains requested by caller before the build() method is called.
     */
    public static class BookDomain {

        /** Domain to add. */
        @NonNull
        final Domain domain;
        /** Expression to use to fetch the domain value. */
        @NonNull
        final String expression;

        public BookDomain(@NonNull final Domain domain,
                          @NonNull final String expression) {
            this.domain = domain;
            this.expression = expression;
        }
    }

    /**
     * A Builder to accumulates data while building the list table
     * and produce the initial SQL insert statement.
     */
    private static final class BuildHelper {

        @NonNull
        private final TableDefinition mDestinationTable;
        @NonNull
        private final BooklistStyle mStyle;
        /** Domains required in output table. */
        private final Collection<Domain> mDomains = new ArrayList<>();
        /** Mapping from Domain to source Expression. */
        private final Map<Domain, String> mExpressions = new HashMap<>();
        /** Domains belonging the current group including its outer groups. */
        private final List<Domain> mGroupDomains = new ArrayList<>();
        /** the list of Filters. */
        private final Collection<Filter> mFilters = new ArrayList<>();

        /**
         * Domains that form part of the sort key.
         * These are typically a reduced set of the GROUP domains since the group domains
         * may contain more than just the key
         */
        private final List<SortedDomain> mSortedDomains = new ArrayList<>();
        /** The set is used as a simple mechanism to prevent duplicate domains. */
        private final Collection<Domain> mSortedColumnsSet = new HashSet<>();

        /** Set to {@code true} if we're filtering on a specific bookshelf. */
        private final boolean mFilteredOnBookshelf;

        /**
         * Constructor.
         *
         * @param destinationTable       the list table to build
         * @param style                  to use
         * @param bookshelf              to use
         */
        private BuildHelper(@NonNull final TableDefinition destinationTable,
                            @NonNull final BooklistStyle style,
                            @NonNull final Bookshelf bookshelf) {
            mDestinationTable = destinationTable;
            mStyle = style;
            mFilteredOnBookshelf = !bookshelf.isAllBooks();
            // copy filter references, but not the actual style filter list
            // as we'll be adding to the local list
            mFilters.addAll(mStyle.getActiveFilters());
        }

        /**
         * Add an ORDER BY domain.
         *
         * @param domain Domain to add
         * @param isDesc Flags
         */
        void addOrderBy(@NonNull final Domain domain,
                        final boolean isDesc) {
            // Add to the table, if not already there
            boolean added = mDestinationTable.addDomain(domain);
            // The domain was already present, check the expression being different (or not)
            if (!added && mExpressions.get(domain) == null) {
                // same expression, we do NOT want to add it.
                // This is NOT a bug, although one could argue it's an efficiency issue.
                return;
            }

            // Add to the order-by domains, if not already there
            if (!mSortedColumnsSet.contains(domain)) {
                mSortedDomains.add(new SortedDomain(domain, isDesc));
                mSortedColumnsSet.add(domain);
            }
        }

        /**
         * Add a domain and source expression to the summary.
         *
         * @param domain     Domain to add
         * @param expression Source Expression
         * @param flags      Flags indicating attributes of new domain
         */
        void addDomain(@NonNull final Domain domain,
                       @NonNull final String expression,
                       @Flags final int flags) {
            // Add to various collections. We use a map to improve lookups and ArrayLists
            // so we can preserve order. Order preservation makes reading the SQL easier
            // but is unimportant for code correctness.

            // Add to the table, if not already there
            boolean added = mDestinationTable.addDomain(domain);
            // The domain was already present, check the expression being different (or not)
            if (!added && expression.equals(mExpressions.get(domain))) {
                // same expression, we do NOT want to add it.
                // This is NOT a bug, although one could argue it's an efficiency issue.
                return;
            }

            mDomains.add(domain);
            mExpressions.put(domain, expression);

            if ((flags & FLAG_GROUP) != 0) {
                mGroupDomains.add(domain);
            }

            // Add to the order-by domains, if not already there
            if ((flags & FLAG_SORT) != 0 && !mSortedColumnsSet.contains(domain)) {
                boolean isDesc = (flags & FLAG_DESC) != 0;
                mSortedDomains.add(new SortedDomain(domain, isDesc));
                mSortedColumnsSet.add(domain);
            }
        }

        /**
         * Add the domains for the given group.
         *
         * @param group to add
         */
        void addGroup(@NonNull final BooklistGroup group) {

            // NEWTHINGS: Group.ROW_KIND_x
            switch (group.getId()) {

                case BooklistGroup.BOOK:
                    // satisfy lint.
                    break;

                case BooklistGroup.AUTHOR: {
                    // Do not sort by it as we'll use the OB column instead
                    addDomain(group.getDisplayDomain(), group.getDisplayDomainExpression(),
                              FLAG_GROUP);

                    // Always sort by DOM_BL_AUTHOR_SORT and user preference order.
                    // Sort uses the OB column.
                    addDomain(DOM_BL_AUTHOR_SORT,
                              mStyle.isSortAuthorByGivenNameFirst()
                              ? DAO.SqlColumns.EXP_AUTHOR_SORT_FIRST_LAST
                              : DAO.SqlColumns.EXP_AUTHOR_SORT_LAST_FIRST,
                              FLAG_GROUP | FLAG_SORT);

                    // Group by id (we want the id available and there is a *chance* two
                    // Authors will have the same name...if there is bad data
                    addDomain(DOM_FK_AUTHOR, TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR),
                              FLAG_GROUP);

                    addDomain(DOM_AUTHOR_IS_COMPLETE, TBL_AUTHORS.dot(KEY_AUTHOR_IS_COMPLETE),
                              FLAG_GROUP);

                    break;
                }
                case BooklistGroup.SERIES: {
                    // Do not sort by it as we'll use the OB column instead
                    addDomain(group.getDisplayDomain(), group.getDisplayDomainExpression(),
                              FLAG_GROUP);

                    // Always sort by DOM_BL_SERIES_SORT.
                    // Sort uses the OB column.
                    addDomain(DOM_BL_SERIES_SORT, TBL_SERIES.dot(KEY_SERIES_TITLE_OB),
                              FLAG_GROUP | FLAG_SORT);

                    // Group by id (we want the id available and there is a *chance* two
                    // Series will have the same name...if there is bad data
                    addDomain(DOM_FK_SERIES, TBL_BOOK_SERIES.dot(KEY_FK_SERIES),
                              FLAG_GROUP);

                    addDomain(DOM_SERIES_IS_COMPLETE,
                              TBL_SERIES.dot(KEY_SERIES_IS_COMPLETE),
                              FLAG_GROUP);

                    // The series number in the base data in sorted order
                    // Allow for the possibility of 3.1, or even "3.1|Omnibus 3-10" as
                    // a series number. So we convert it to a real (aka float).
                    // This field is not displayed.
                    addDomain(DOM_BL_SERIES_NUM_FLOAT,
                              DAO.SqlColumns.EXP_SERIES_NUMBER_AS_FLOAT,
                              FLAG_BOOK | FLAG_SORT);

                    // The series number as a sorted field for display purposes
                    // and in case of non-numeric data (where the above float would fail)
                    addDomain(DOM_BOOK_NUM_IN_SERIES,
                              TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES),
                              FLAG_BOOK | FLAG_SORT);


                    // The series position at the lowest level for use with the Book (not grouped)
//                    addDomain(DOM_BOOK_SERIES_POSITION,
//                              TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION),
//                              FLAG_BOOK);

                    // A counter of how many books use the series as a primary series,
                    // so we can skip some series
//                    addDomain(DOM_BL_PRIMARY_SERIES_COUNT,
//                              DAO.SqlColumns.EXP_PRIMARY_SERIES_COUNT_AS_BOOLEAN,
//                              FLAG_BOOK);

                    break;
                }

                case BooklistGroup.RATING: {
                    addDomain(group.getDisplayDomain(), group.getDisplayDomainExpression(),
                              // sort with highest rated first
                              FLAG_GROUP | FLAG_SORT_DESC);
                    break;
                }

                case BooklistGroup.BOOKSHELF:
                case BooklistGroup.LOANED:
                case BooklistGroup.READ_STATUS:
                case BooklistGroup.PUBLISHER:
                case BooklistGroup.GENRE:
                case BooklistGroup.LANGUAGE:
                case BooklistGroup.LOCATION:
                case BooklistGroup.FORMAT:
                case BooklistGroup.COLOR:
                case BooklistGroup.BOOK_TITLE_LETTER:
                case BooklistGroup.SERIES_TITLE_LETTER: {
                    addDomain(group.getDisplayDomain(), group.getDisplayDomainExpression(),
                              FLAG_GROUP | FLAG_SORT);
                    break;
                }

                case BooklistGroup.DATE_PUBLISHED_YEAR:
                case BooklistGroup.DATE_PUBLISHED_MONTH: {
                    addDomain(group.getDisplayDomain(), group.getDisplayDomainExpression(),
                              FLAG_GROUP | FLAG_SORT_DESC);
                    // sort on the full date for cases where we don't have all 3 separate groups
                    addOrderBy(DOM_DATE_PUBLISHED, true);
                    break;
                }

                case BooklistGroup.DATE_FIRST_PUBLICATION_YEAR:
                case BooklistGroup.DATE_FIRST_PUBLICATION_MONTH: {
                    addDomain(group.getDisplayDomain(), group.getDisplayDomainExpression(),
                              FLAG_GROUP | FLAG_SORT_DESC);
                    // sort on the full date for cases where we don't have all 3 separate groups
                    addOrderBy(DOM_DATE_FIRST_PUBLICATION, true);
                    break;
                }

                case BooklistGroup.DATE_READ_YEAR:
                case BooklistGroup.DATE_READ_MONTH:
                case BooklistGroup.DATE_READ_DAY: {
                    addDomain(group.getDisplayDomain(), group.getDisplayDomainExpression(),
                              FLAG_GROUP | FLAG_SORT_DESC);
                    // sort on the full date for cases where we don't have all 3 separate groups
                    addOrderBy(DOM_BOOK_DATE_READ_END, true);
                    // read books first
                    addOrderBy(DOM_BOOK_READ, true);
                    break;
                }

                case BooklistGroup.DATE_ACQUIRED_YEAR:
                case BooklistGroup.DATE_ACQUIRED_MONTH:
                case BooklistGroup.DATE_ACQUIRED_DAY: {
                    addDomain(group.getDisplayDomain(), group.getDisplayDomainExpression(),
                              FLAG_GROUP | FLAG_SORT_DESC);
                    // sort on the full date for cases where we don't have all 3 separate groups
                    addOrderBy(DOM_BOOK_DATE_ACQUIRED, true);
                    break;
                }

                case BooklistGroup.DATE_ADDED_YEAR:
                case BooklistGroup.DATE_ADDED_MONTH:
                case BooklistGroup.DATE_ADDED_DAY: {
                    addDomain(group.getDisplayDomain(), group.getDisplayDomainExpression(),
                              FLAG_GROUP | FLAG_SORT_DESC);
                    // sort on the full date for cases where we don't have all 3 separate groups
                    addOrderBy(DOM_BOOK_DATE_ADDED, true);
                    break;
                }

                case BooklistGroup.DATE_LAST_UPDATE_YEAR:
                case BooklistGroup.DATE_LAST_UPDATE_MONTH:
                case BooklistGroup.DATE_LAST_UPDATE_DAY: {
                    addDomain(group.getDisplayDomain(), group.getDisplayDomainExpression(),
                              FLAG_GROUP | FLAG_SORT_DESC);
                    // sort on the full date for cases where we don't have all 3 separate groups
                    addOrderBy(DOM_DATE_LAST_UPDATED, true);
                    break;
                }

                default:
                    throw new UnexpectedValueException(group.getId());
            }
        }

        /**
         * Add Filters.
         *
         * @param filters list of filters; only active filters will be added
         */
        void addFilters(@NonNull final Collection<Filter> filters) {
            mFilters.addAll(filters);
        }

        /**
         * Since BooklistGroup objects are processed in order, this allows us to get
         * the fields applicable to the currently processed group, including its outer groups.
         * Hence why the list is copied -- subsequent domains will modify this collection.
         *
         * @return a shallow copy of the current-group domains
         * (ArrayList as we'll need to parcel it)
         */
        @NonNull
        ArrayList<Domain> getDomainsForCurrentGroup() {
            return new ArrayList<>(mGroupDomains);
        }

        /**
         * Get the list of domains used to sort the output.
         *
         * @return the list
         */
        @NonNull
        List<SortedDomain> getSortedDomains() {
            return mSortedDomains;
        }

        /**
         * Using the collected domain info, create the various SQL phrases used to build
         * the resulting flat list table and build the SQL that does the initial table load.
         *
         * @return initial insert statement
         */
        @NonNull
        String build() {

            // List of column names for the INSERT INTO... clause
            StringBuilder destColumns = new StringBuilder();
            // List of expressions for the SELECT... clause.
            StringBuilder sourceColumns = new StringBuilder();

            boolean first = true;
            for (Domain domain : mDomains) {
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

            // add the node key column
            destColumns.append(',').append(KEY_BL_NODE_KEY);
            sourceColumns.append(',').append(buildNodeKey());

            String sql = "INSERT INTO " + mDestinationTable.getName() + " (" + destColumns + ')'
                         + " SELECT " + sourceColumns
                         + " FROM " + buildFrom()
                         + buildWhere()
                         + " ORDER BY " + buildOrderBy();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Log.d(TAG, "build|sql=" + sql);
            }
            return sql;
        }

        /**
         * Create the expression for the key column.
         * Will contain one key=value pair for each each group level.
         * "/key=value/key=value/key=value/..."
         *
         * @return column expression
         */
        private String buildNodeKey() {
            StringBuilder keyColumn = new StringBuilder();

            for (BooklistGroup group : mStyle.getGroups()) {
                BooklistGroup.GroupKey key = group.getCompoundKey();
                keyColumn.append("'/").append(key.getKeyPrefix())
                         // null becomes an empty String
                         .append("/'||COALESCE(").append(key.getExpression()).append(",'')||");
            }
            int len = keyColumn.length();
            // remove the trailing "||"
            return keyColumn.delete(len - 2, len).toString();
        }

        /**
         * Create the FROM clause based on the groups and extra criteria.
         * <ul>Always joined are:
         * <li>{@link DBDefinitions#TBL_BOOK_AUTHOR}<br>{@link DBDefinitions#TBL_AUTHORS}</li>
         * <li>{@link DBDefinitions#TBL_BOOK_SERIES}<br>{@link DBDefinitions#TBL_SERIES}</li>
         * </ul>
         * <ul>Optionally joined with:
         * <li>{@link DBDefinitions#TBL_BOOK_BOOKSHELF}<br>{@link DBDefinitions#TBL_BOOKSHELF}</li>
         * <li>{@link DBDefinitions#TBL_BOOK_LOANEE}</li>
         * </ul>
         */
        private String buildFrom() {
            Context context = App.getAppContext();

            // Text of join statement
            final StringBuilder sql = new StringBuilder();

            // If there is a bookshelf specified (either as group or as a filter),
            // start the join there.
            if (mStyle.containsGroup(BooklistGroup.BOOKSHELF) || mFilteredOnBookshelf) {
                sql.append(TBL_BOOKSHELF.ref())
                   .append(TBL_BOOKSHELF.join(TBL_BOOK_BOOKSHELF))
                   .append(TBL_BOOK_BOOKSHELF.join(TBL_BOOKS));
            } else {
                // Otherwise, start with the BOOKS table.
                sql.append(TBL_BOOKS.ref());
            }

            if (App.isUsed(KEY_LOANEE)) {
                // get the loanee name, or a {@code null} for available books.
                sql.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_LOANEE));
            }

            // Join with the link table between Book and Author.
            sql.append(TBL_BOOKS.join(TBL_BOOK_AUTHOR));

            // Extend the join filtering on the primary Author unless
            // the user wants the book to show under all its Authors
            if (!mStyle.isShowBooksUnderEachAuthor(context)) {
                @Author.Type
                int primaryAuthorType = mStyle.getPrimaryAuthorType(context);
                if (primaryAuthorType == Author.TYPE_UNKNOWN) {
                    // don't care about Author type, so just grab the primary (i.e. pos==1)
                    sql.append(" AND ")
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
                       .append(" AND ")
                       .append(TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION)).append("=1))");
                }
            }
            // Join with Authors to make the names available
            sql.append(TBL_BOOK_AUTHOR.join(TBL_AUTHORS));

            // Join with the link table between Book and Series.
            sql.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_SERIES));
            // Extend the join filtering on the primary Series unless
            // the user wants the book to show under all its Series
            if (!mStyle.isShowBooksUnderEachSeries(context)) {
                sql.append(" AND ")
                   .append(TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION)).append("=1");
            }
            // Join with Series to make the titles available
            sql.append(TBL_BOOK_SERIES.leftOuterJoin(TBL_SERIES));

            return sql.toString();
        }

        /**
         * Create the WHERE clause based on all active filters (for in-use domains).
         */
        private String buildWhere() {
            StringBuilder where = new StringBuilder();

            for (Filter filter : mFilters) {
                // Theoretically all filters should be active here, but paranoia...
                if (filter.isActive()) {
                    if (where.length() != 0) {
                        where.append(" AND ");
                    }
                    where.append(' ').append(filter.getExpression());
                }
            }

            if (where.length() > 0) {
                return " WHERE " + where;
            } else {
                return "";
            }
        }

        /**
         * Process the 'sort-by' columns into a list suitable for an ORDER-BY statement.
         */
        private String buildOrderBy() {
            // List of column names appropriate for 'ORDER BY' clause
            final StringBuilder orderBy = new StringBuilder();
            for (SortedDomain sd : mSortedDomains) {
                if (sd.domain.isText()) {
                    // The order of this if/elseif/else is important, don't merge branch 1 and 3!
                    if (sd.domain.isPrePreparedOrderBy()) {
                        // always use a pre-prepared order-by column as-is
                        orderBy.append(sd.domain.getName());

                    } else if (DBHelper.isCollationCaseSensitive()) {
                        // If {@link DAO#COLLATION} is case-sensitive, lowercase it.
                        // This should never happen, but see the DAO method docs.
                        orderBy.append("lower(").append(sd.domain.getName()).append(')');

                    } else {
                        // hope for the best. This case might not handle non-[A..Z0..9] as expected
                        orderBy.append(sd.domain.getName());
                    }

                    orderBy.append(DAO.COLLATION);
                } else {
                    orderBy.append(sd.domain.getName());
                }

                if (sd.isDescending) {
                    orderBy.append(" DESC");
                }
                orderBy.append(',');
            }

            return orderBy.append(KEY_BL_NODE_LEVEL).toString();
        }

        /**
         * Process the 'sort-by' columns into a list suitable for an CREATE-INDEX statement.
         */
        private String getSortedDomainsIndexColumns() {
            final StringBuilder indexCols = new StringBuilder();

            for (SortedDomain sd : mSortedDomains) {
                indexCols.append(sd.domain.getName());
                if (sd.domain.isText()) {
                    indexCols.append(DAO.COLLATION);
                }

                if (sd.isDescending) {
                    indexCols.append(" DESC");
                }
                indexCols.append(',');
            }

            return indexCols.append(KEY_BL_NODE_LEVEL).toString();
        }
    }
}
