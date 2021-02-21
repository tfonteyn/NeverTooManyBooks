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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.NumberListFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.dao.BaseDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;

class BooklistBuilder {

    /** Log tag. */
    private static final String TAG = "BooklistBuilder";

    private static final String SELECT_COUNT = "SELECT COUNT(*)";
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

    /** Database Access. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final SynchronizedDb mSyncedDb;

    /** Internal ID. Used to create unique names for the temporary tables. */
    private final int mInstanceId;

    /** Style to use while building the list. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final ListStyle mStyle;

    /** Show only books on this bookshelf. */
    @NonNull
    private final List<Bookshelf> mBookshelves = new ArrayList<>();
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Map<String, TableDefinition> mLeftOuterJoins = new HashMap<>();
    /** Collection of 'extra' book level domains requested by caller. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Map<String, DomainExpression> mBookDomains = new HashMap<>();
    /** the list of Filters. */
    private final Collection<Filter> mFilters = new ArrayList<>();
    @SuppressWarnings("FieldNotUsedInToString")
    @Booklist.ListRebuildMode
    private int mRebuildState;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param style        Style reference.
     *                     This is the resolved style as used by the passed bookshelf
     * @param bookshelf    the current bookshelf
     * @param rebuildState booklist state to use in next rebuild.
     */
    public BooklistBuilder(@NonNull final Context context,
                           @NonNull final ListStyle style,
                           @NonNull final Bookshelf bookshelf,
                           @Booklist.ListRebuildMode final int rebuildState) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "ENTER|Booklist"
                       + "|style=" + style.getUuid()
                       + "|instances: " + DEBUG_INSTANCE_COUNTER.incrementAndGet(),
                  new Throwable());
        }

        mInstanceId = ID_COUNTER.incrementAndGet();

        mSyncedDb = DBHelper.getSyncDb(context);
        mStyle = style;
        mBookshelves.add(bookshelf);
        mRebuildState = rebuildState;
    }

    /**
     * Add a table to be added as a LEFT OUTER JOIN.
     *
     * @param tableDefinition TableDefinition to add
     */
    public void addLeftOuterJoin(@NonNull final TableDefinition tableDefinition) {
        if (!mLeftOuterJoins.containsKey(tableDefinition.getName())) {
            mLeftOuterJoins.put(tableDefinition.getName(), tableDefinition);

        } else {
            // adding a duplicate here is a bug.
            throw new IllegalArgumentException("Duplicate table=" + tableDefinition.getName());
        }
    }

    /**
     * Add a domain to the resulting flattened list based on the details provided.
     *
     * @param domainExpression Domain to add
     */
    public void addDomain(@NonNull final DomainExpression domainExpression) {
        if (!mBookDomains.containsKey(domainExpression.getName())) {
            mBookDomains.put(domainExpression.getName(), domainExpression);

        } else {
            // adding a duplicate here is a bug.
            throw new IllegalArgumentException("Duplicate domain=" + domainExpression.getName());
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
    public void addFilterOnBookIdList(@Nullable final List<Long> filter) {
        if (filter != null && !filter.isEmpty()) {
            mFilters.add(new NumberListFilter(TBL_BOOKS, KEY_PK_ID, filter));
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
    public void addFilterOnKeywords(@Nullable final String author,
                                    @Nullable final String title,
                                    @Nullable final String seriesTitle,
                                    @Nullable final String publisherName,
                                    @Nullable final String keywords) {

        final String query = BookDao.FtsSql.createMatchString(author, title, seriesTitle,
                                                              publisherName, keywords);
        if (!query.isEmpty()) {
            mFilters.add(context ->
                                 '(' + TBL_BOOKS.dot(KEY_PK_ID) + " IN ("
                                 // fetch the ID's only
                                 + SELECT_ + DBDefinitions.KEY_FTS_BOOK_ID
                                 + _FROM_ + DBDefinitions.TBL_FTS_BOOKS.getName()
                                 + _WHERE_ + DBDefinitions.TBL_FTS_BOOKS.getName()
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
    public void addFilterOnLoanee(@Nullable final String filter) {
        if (filter != null && !filter.trim().isEmpty()) {
            mFilters.add(context ->
                                 "EXISTS(SELECT NULL FROM " + TBL_BOOK_LOANEE.ref()
                                 + _WHERE_ + TBL_BOOK_LOANEE.dot(KEY_LOANEE)
                                 + "=`" + BaseDao.encodeString(filter) + '`'
                                 + _AND_ + TBL_BOOK_LOANEE.fkMatch(TBL_BOOKS)
                                 + ')');
        }
    }

    /**
     * Allows to override the build state set in the constructor.
     *
     * @param rebuildState booklist state to use in next rebuild.
     */
    public void setRebuildState(@Booklist.ListRebuildMode final int rebuildState) {
        mRebuildState = rebuildState;
    }

    /**
     * Clear and build the temporary list of books.
     * Criteria must be set before calling this method with one or more of the setCriteria calls.
     *
     * @param context Current context
     */
    public BL2 build(@NonNull final Context context) {

        final boolean isFilteredOnBookshelves = !mBookshelves.get(0).isAllBooks();

        // Filter on the specified Bookshelves.
        // The filter will only be added if the current style does not contain the Bookshelf group.
        if (isFilteredOnBookshelves && !mStyle.getGroups().contains(BooklistGroup.BOOKSHELF)) {
            if (mBookshelves.size() == 1) {
                mFilters.add(c -> '(' + TBL_BOOKSHELF.dot(KEY_PK_ID)
                                  + '=' + mBookshelves.get(0).getId()
                                  + ')');
            } else {
                mFilters.add(c -> '(' + TBL_BOOKSHELF.dot(KEY_PK_ID)
                                  + " IN (" + mBookshelves.stream()
                                                          .map(Bookshelf::getId)
                                                          .map(String::valueOf)
                                                          .collect(Collectors.joining(","))
                                  + "))");
            }
        }

        // Construct the list table and all needed structures.
        final BooklistTableBuilder tableBuilder = new BooklistTableBuilder(
                mInstanceId, mStyle, isFilteredOnBookshelves, mRebuildState);

        tableBuilder.preBuild(context, mLeftOuterJoins.values(), mBookDomains.values(), mFilters);

        final Synchronizer.SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            // create the tables and populate them
            final Pair<TableDefinition, TableDefinition> tables = tableBuilder.build(mSyncedDb);
            TableDefinition mListTable = tables.first;
            TableDefinition mNavTable = tables.second;

            BooklistNodeDAO mRowStateDAO = new BooklistNodeDAO(mSyncedDb, mListTable,
                                                               mStyle, mBookshelves.get(0));

            switch (mRebuildState) {
                case Booklist.PREF_REBUILD_SAVED_STATE:
                    // all rows will be collapsed/hidden; restore the saved state
                    mRowStateDAO.restoreSavedState();
                    break;

                case Booklist.PREF_REBUILD_PREFERRED_STATE:
                    // all rows will be collapsed/hidden; now adjust as required.
                    mRowStateDAO.setAllNodes(mStyle.getTopLevel(), false);
                    break;

                case Booklist.PREF_REBUILD_EXPANDED:
                case Booklist.PREF_REBUILD_COLLAPSED:
                    // handled during table creation
                    break;

                default:
                    throw new IllegalArgumentException(String.valueOf(mRebuildState));
            }

            mSyncedDb.setTransactionSuccessful();

            return new BL2(mListTable, mNavTable, mRowStateDAO);

        } finally {
            mSyncedDb.endTransaction(txLock);
        }
    }
}
