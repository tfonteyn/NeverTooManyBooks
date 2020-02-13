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
import com.hardbacknutter.nevertoomanybooks.database.Joiner;
import com.hardbacknutter.nevertoomanybooks.database.SqlStatementManager;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_KIND;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_PRIMARY_SERIES_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_SERIES_NUM_FLOAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_DATE_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_LOANEE_AS_BOOLEAN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_AUTHOR_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_RK_SERIES_SORT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_LIST_VIEW_ROW_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_KIND;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_UUID;
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
    public static final int PREF_REBUILD_ALWAYS_COLLAPSED = 2;
    @SuppressWarnings("WeakerAccess")
    public static final int PREF_REBUILD_PREFERRED_STATE = 3;
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
    private final Map<String, ExtraDomainDetails> mExtraDomains = new HashMap<>();

    /** the list of Filters. */
    private final Collection<Filter> mFilters = new ArrayList<>();
    /** Show only books on this bookshelf. */
    private final Bookshelf mBookshelf;
    /** Style to use while building the list. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final BooklistStyle mStyle;
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
     * @param bookshelf to use
     * @param style     Booklist style to use;
     *                  this is the resolved style as used by the passed bookshelf
     */
    public BooklistBuilder(@NonNull final Bookshelf bookshelf,
                           @NonNull final BooklistStyle style) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "ENTER|BooklistBuilder"
                       + "|bookshelf=" + bookshelf.getName()
                       + "|style=" + style.getUuid()
                       + "|instances: " + DEBUG_INSTANCE_COUNTER.incrementAndGet());
        }
        // Allocate ID
        mInstanceId = ID_COUNTER.incrementAndGet();

        mBookshelf = bookshelf;
        mStyle = style;

        // Get the database and create a statements collection
        mDb = new DAO(TAG);
        mSyncedDb = mDb.getUnderlyingDatabase();
        mStatementManager = new SqlStatementManager(mSyncedDb, TAG + "|" + mInstanceId);
    }

    /**
     * Get the current preferred rebuild state for the list.
     *
     * @return ListRebuildMode
     */
    @ListRebuildMode
    public static int getPreferredListRebuildState() {
        return PIntString.getListPreference(Prefs.pk_bob_levels_rebuild_state,
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
     * @param domainDetails Domain to add
     *
     */
    public void addDomain(@NonNull final ExtraDomainDetails domainDetails) {
        if (!mExtraDomains.containsKey(domainDetails.domain.getName())) {
            mExtraDomains.put(domainDetails.domain.getName(), domainDetails);

        } else {
            // adding a duplicate here is a bug.
            throw new IllegalArgumentException("Duplicate domain=`"
                                               + domainDetails.domain.getName() + '`');
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

        // Start building the table domains

        // Will use default increment
        mListTable.addDomain(DOM_PK_ID);
        // Will use expression based on groups (rowKindPrefix/rowValue); determined later
        mListTable.addDomain(DOM_BL_NODE_KEY);

        final BuildHelper helper = new BuildHelper(mListTable, mStyle);
        // not sorted
        helper.addDomain(DOM_BL_NODE_LEVEL, String.valueOf(mStyle.groupCount() + 1),
                         BuildHelper.FLAG_NONE);
        //sorted
        helper.addDomain(DOM_BL_NODE_LEVEL, null, BuildHelper.FLAG_SORTED);

        helper.addDomain(DOM_BL_NODE_KIND, String.valueOf(BooklistGroup.RowKind.BOOK),
                         BuildHelper.FLAG_NONE);

        helper.addDomain(DOM_FK_BOOK, TBL_BOOKS.dot(KEY_PK_ID), BuildHelper.FLAG_NONE);
        // each row has a book count, for books this is obviously always == 1.
        helper.addDomain(DOM_BOOK_COUNT, "1", BuildHelper.FLAG_NONE);

        // We want the UUID for the book so we can get thumbnails
        helper.addDomain(DOM_BOOK_UUID, TBL_BOOKS.dot(KEY_BOOK_UUID), BuildHelper.FLAG_NONE);

        for (BooklistGroup group : mStyle.getGroups()) {
            helper.addGroup(group);

            // Copy the current groups to this level item; this effectively accumulates
            // 'GROUP BY' domains down each level so that the top has fewest groups and
            // the bottom level has groups for all levels.
            group.setDomains(helper.getDomainsForCurrentGroup());
        }
        // After adding the groups, we now know if we have a Bookshelf group or not.

        // If we want a specific Bookshelf, make sure to filter on it.
        if (!mBookshelf.isAllBooks()) {
            helper.ensureBookshelfGroup(mBookshelf.getId());
        }

        // add any caller-specified extras (e.g. title).
        for (ExtraDomainDetails edd : mExtraDomains.values()) {
            helper.addDomain(edd.domain, edd.sourceExpression, edd.flags);
        }

        // and finally, add the filters to be used for the WHERE clause
        helper.addFilters(mFilters);

        final long t01_domains_done = System.nanoTime();

        // Construct the initial insert statement components.
        String initialInsertSql = helper.buildSql(mSyncedDb.isCollationCaseSensitive());

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
            if (!mSyncedDb.isCollationCaseSensitive()) {
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

            mRowStateDAO = new RowStateDAO(mSyncedDb, mInstanceId,
                                           mBookshelf, mStyle);
            mRowStateDAO.build(mListTable, listState);

            t06_stateTable_created = System.nanoTime();

            mSyncedDb.setTransactionSuccessful();

        } finally {
            mSyncedDb.endTransaction(txLock);

            // we don't catch exceptions but we do want to log the time it took here.
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                Log.d(TAG, "build|"
                           + String.format(Locale.UK, ""
                                                      + "\ndomains setup        : %5d"
                                                      + "\nbuild insert         : %5d"

                                                      + "\nlist table created   : %5d"
                                                      + "\nbase insert executed : %5d"
                                                      + "\nlist table analyzed  : %5d"

                                                      + "\nstate table build    : %5d"
                                                      + "\n============================"
                                                      + "\nTotal time in ms     : %5d",

                                           (t01_domains_done - t00)
                                           / NANO_TO_MILLIS,
                                           (t02_base_insert_prepared - t01_domains_done)
                                           / NANO_TO_MILLIS,
                                           (t03_list_table_created - t02_base_insert_prepared)
                                           / NANO_TO_MILLIS,
                                           (t04_base_insert_executed - t03_list_table_created)
                                           / NANO_TO_MILLIS,
                                           (t05_listTable_analyzed - t04_base_insert_executed)
                                           / NANO_TO_MILLIS,
                                           (t06_stateTable_created - t05_listTable_analyzed)
                                           / NANO_TO_MILLIS,
                                           (System.nanoTime() - t00) / NANO_TO_MILLIS
                                          )
                     );
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
    private void createTriggers(@NonNull final Iterable<SortedDomains> sortedDomains) {

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
        //IMPORTANT: withConstraints MUST BE false
        mTriggerHelperTable.recreate(mSyncedDb, false);

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
                    .append(KEY_BL_NODE_LEVEL)
                    .append(',').append(KEY_BL_NODE_KIND)
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
                + "\n WHEN New." + KEY_BL_NODE_LEVEL + '=' + mStyle.groupCount()
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
                     + " WHERE " + KEY_BL_NODE_KIND + "=?"
                     + " ORDER BY " + KEY_FK_BOOK;

        try (Cursor cursor = mSyncedDb
                .rawQuery(sql, new String[]{String.valueOf(BooklistGroup.RowKind.BOOK)})) {
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
                + " WHERE " + KEY_BL_NODE_KIND + "=?")) {
            stmt.bindLong(1, BooklistGroup.RowKind.BOOK);
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
                + " WHERE " + KEY_BL_NODE_KIND + "=?")) {
            stmt.bindLong(1, BooklistGroup.RowKind.BOOK);
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
                Logger.w(TAG, "finalize|" + mInstanceId);
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
     * A value class for domain + desc/asc sorting flag.
     */
    static class SortedDomains {

        @NonNull
        final Domain domain;
        final boolean isDescending;

        SortedDomains(@NonNull final Domain domain,
                      final boolean isDescending) {
            this.domain = domain;
            this.isDescending = isDescending;
        }
    }

    /**
     * A value class of extra domains requested by caller before the build() method is called.
     */
    public static class ExtraDomainDetails {

        /** Domain to add. */
        @NonNull
        final Domain domain;
        /** Expression to use in deriving domain value. */
        @Nullable
        final String sourceExpression;
        /** Flags indicating attributes of new domain. */
        @BuildHelper.Flags
        final int flags;

        public ExtraDomainDetails(@NonNull final Domain domain,
                                  @Nullable final String sourceExpression,
                                  @BuildHelper.Flags final int flags) {
            this.domain = domain;
            this.sourceExpression = sourceExpression;
            this.flags = flags;
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

        @NonNull
        private final TableDefinition mDestinationTable;
        @NonNull
        private final BooklistStyle mStyle;
        /** Domains required in output table. */
        private final Collection<Domain> mDomains = new ArrayList<>();
        /** Mapping from Domain to source Expression. */
        private final Map<Domain, String> mExpressions = new HashMap<>();
        /** Domains belonging the current group including its outer groups. */
        private final ArrayList<Domain> mGroupDomains = new ArrayList<>();
        /** the list of Filters. */
        private final Collection<Filter> mFilters = new ArrayList<>();

        /**
         * Domains that form part of the sort key.
         * These are typically a reduced set of the GROUP domains since the group domains
         * may contain more than just the key
         */
        private final ArrayList<SortedDomains> mSortedDomains = new ArrayList<>();
        /** The set is used as a simple mechanism to prevent duplicate domains. */
        private final Collection<Domain> mSortedColumnsSet = new HashSet<>();

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
            // copy filter references, but not the actual style filter list
            // as we'll be adding to the local list
            mFilters.addAll(mStyle.getFilters());
        }

        /**
         * Add a domain and source expression to the summary.
         *
         * @param domain           Domain to add
         * @param sourceExpression Source Expression
         * @param flags            Flags indicating attributes of new domain
         */
        void addDomain(@NonNull final Domain domain,
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
//                    if (BuildConfig.DEBUG /* always */) {
//                        Log.d(TAG, "duplicate domain/expression"
//                                   + "|domain.name=" + domain.getName());
//                    }
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
        }

        /**
         * Add the domains for the given group.
         *
         * @param group to add
         */
        void addGroup(@NonNull final BooklistGroup group) {

            switch (group.getId()) {

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

                    addDomain(DOM_FK_AUTHOR, TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR),
                              BuildHelper.FLAG_GROUP);

                    addDomain(DOM_AUTHOR_IS_COMPLETE, TBL_AUTHORS.dot(KEY_AUTHOR_IS_COMPLETE),
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
                    addDomain(DOM_RK_SERIES_SORT, TBL_SERIES.dot(KEY_SERIES_TITLE_OB),
                              BuildHelper.FLAG_GROUP
                              | BuildHelper.FLAG_SORTED);

                    // Group by id (we want the id available and there is a *chance* two
                    // series will have the same name...with bad data
                    addDomain(DOM_FK_SERIES, TBL_BOOK_SERIES.dot(KEY_FK_SERIES),
                              BuildHelper.FLAG_GROUP);

                    // The series position at the lowest level for use with the Book (not grouped)
                    addDomain(DOM_BOOK_SERIES_POSITION,
                              TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION),
                              BuildHelper.FLAG_NONE);

                    addDomain(DOM_SERIES_IS_COMPLETE,
                              TBL_SERIES.dot(KEY_SERIES_IS_COMPLETE),
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
                              TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES),
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
                case BooklistGroup.RowKind.TITLE_LETTER:
                case BooklistGroup.RowKind.SERIES_TITLE_LETTER: {
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

                    addDomain(DOM_DATE_PUBLISHED, null,
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

                    addDomain(DOM_DATE_FIRST_PUBLICATION, null,
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
         * Ensure we have a Bookshelf group; but if not, then add a filter with the given
         * bookshelf id.
         *
         * @param bookshelfId to filter on if there is no Bookshelf group.
         */
        void ensureBookshelfGroup(final long bookshelfId) {
            if (!mHasBookshelfGroup) {
                mHasBookshelfGroup = true;
                // add a specific filter to get only the requested shelf.
                mFilters.add(() -> '(' + TBL_BOOKSHELF.dot(KEY_PK_ID) + '=' + bookshelfId + ')');
            }
        }

        /**
         * Since BooklistGroup objects are processed in order, this allows us to get
         * the fields applicable to the currently processed group, including its outer groups.
         * Hence why the list is copied -- subsequent domains will modify this collection.
         *
         * @return a shallow copy of the current-group domains.
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
        ArrayList<SortedDomains> getSortedDomains() {
            return mSortedDomains;
        }

        /**
         * Using the collected domain info, create the various SQL phrases used to build
         * the resulting flat list table and build the SQL that does the initial table load.
         *
         * @return initial insert statement
         */
        @NonNull
        String buildSql(final boolean collationIsCs) {

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

            // add the root key column
            destColumns.append(',').append(KEY_BL_NODE_KEY);
            sourceColumns.append(',').append(buildRootKeyColumn());

//            return new BaseSql(mDestinationTable.getName(),
//                               destColumns.toString(),
//                               sourceColumns.toString())
//                    .from(mAuthorGroup,
//                          mSeriesGroup,
//                          mHasBookshelfGroup,
//                          mHasLoaneeGroup)
//                    .where(mFilters)
//                    .orderBy(mSortedDomains, collationIsCs)
//                    .build();

            String sql = "INSERT INTO " + mDestinationTable.getName() + '(' + destColumns + ')'
                         + " SELECT " + sourceColumns
                         + " FROM " + buildFrom()
                         + buildWhere()
                         + " ORDER BY " + buildOrderBy(collationIsCs);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Log.d(TAG, "build|sql=" + sql);
            }
            return sql;
        }

        /**
         * Create the expression for the key column.
         *
         * @return column expression
         */
        private String buildRootKeyColumn() {
            StringBuilder keyColumn = new StringBuilder();

            for (BooklistGroup group : mStyle.getGroups()) {
                BooklistGroup.CompoundKey key = group.getCompoundKey();
                keyColumn.append("'/").append(key.getPrefix())
                         // null becomes an empty String
                         .append("/'||COALESCE(").append(key.getExpression()).append(",'')||");
            }
            int len = keyColumn.length();
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
            Joiner mJoiner;
            // If there is a bookshelf specified, start the join there.
            // Otherwise, start with the BOOKS table.
            if (mHasBookshelfGroup) {
                mJoiner = new Joiner(TBL_BOOKSHELF)
                        .join(TBL_BOOKSHELF, TBL_BOOK_BOOKSHELF)
                        .join(TBL_BOOK_BOOKSHELF, TBL_BOOKS);
            } else {
                mJoiner = new Joiner(TBL_BOOKS);
            }

            // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
            if (mHasLoaneeGroup || App.isUsed(KEY_LOANEE)) {
                // so get the loanee name, or a {@code null} for available books.
                mJoiner.leftOuterJoin(TBL_BOOKS, TBL_BOOK_LOANEE);
            }


            // Join with the link table between Book and Author.
            mJoiner.join(TBL_BOOKS, TBL_BOOK_AUTHOR);
            // Join with the primary Author (i.e. position 1) if needed.
            if (mAuthorGroup == null || !mAuthorGroup.showAll()) {
                mJoiner.append(" AND " + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION) + "=1");
            }
            // Join with Authors to make the names available
            mJoiner.join(TBL_BOOK_AUTHOR, TBL_AUTHORS);


            // Join with the link table between Book and Series.
            mJoiner.leftOuterJoin(TBL_BOOKS, TBL_BOOK_SERIES);
            // Join with the primary Series (i.e. position 1) if needed.
            if (mSeriesGroup == null || !mSeriesGroup.showAll()) {
                mJoiner.append(" AND " + TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION) + "=1");
            }
            // Join with Series to make the titles available
            mJoiner.leftOuterJoin(TBL_BOOK_SERIES, TBL_SERIES);

            return mJoiner.build();
        }

        /**
         * Create the WHERE clause based on all filters.
         */
        private String buildWhere() {
            StringBuilder where = new StringBuilder();

            for (Filter filter : mFilters) {
                if (filter.isActive()) {
                    if (where.length() != 0) {
                        where.append(" AND ");
                    }
                    where.append(' ').append(filter.getExpression());
                }
            }

            if (where.length() > 0) {
                return where.insert(0, " WHERE ").toString();
            } else {
                return "";
            }
        }

        /**
         * Process the 'sort-by' columns into a list suitable for an ORDER-BY statement.
         * <p>
         * If the {@link DAO#COLLATION} is case-sensitive, we wrap the columns in "lower()"
         *
         * @param collationIsCs if {@code true} then we'll adjust the case here
         */
        private String buildOrderBy(final boolean collationIsCs) {
            // List of column names appropriate for 'ORDER BY' clause
            final StringBuilder sortCols = new StringBuilder();
            for (SortedDomains sdi : mSortedDomains) {
                if (sdi.domain.isText()) {
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
                    sortCols.append(" DESC");
                }
                sortCols.append(',');
            }

            sortCols.append(KEY_BL_NODE_LEVEL);

            return sortCols.toString();
        }

        /**
         * Process the 'sort-by' columns into a list suitable for an CREATE-INDEX statement.
         */
        private String getSortedDomainsIndexColumns() {
            final StringBuilder indexCols = new StringBuilder();

            for (SortedDomains sdi : mSortedDomains) {
                indexCols.append(sdi.domain.getName());
                if (sdi.domain.isText()) {
                    indexCols.append(DAO.COLLATION);
                }
                if (sdi.isDescending) {
                    indexCols.append(" DESC");
                }
                indexCols.append(',');
            }

            return indexCols.append(KEY_BL_NODE_LEVEL).toString();
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
        })
        @interface Flags {

        }
    }

}
