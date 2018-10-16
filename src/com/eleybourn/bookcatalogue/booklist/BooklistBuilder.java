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
package com.eleybourn.bookcatalogue.booklist;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteQuery;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.BooklistAuthorGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistGroup.BooklistSeriesGroup;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle.CompoundKey;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.CollationCaseSensitive;
import com.eleybourn.bookcatalogue.database.DatabaseHelper;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.database.JoinContext;
import com.eleybourn.bookcatalogue.database.SqlStatementManager;
import com.eleybourn.bookcatalogue.database.cursors.BooklistCursor;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition.TableTypes;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_AUTHOR;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_BOOK;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_BOOKSHELF;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_DAY_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_DAY_READ;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_FORMAT;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_GENRE;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LANGUAGE;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LOANED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_LOCATION;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_PUBLISHED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_MONTH_READ;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_PUBLISHER;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_RATING;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_READ_AND_UNREAD;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_SERIES;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_TITLE_LETTER;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_UPDATE_DAY;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_UPDATE_MONTH;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_UPDATE_YEAR;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_ADDED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_PUBLISHED;
import static com.eleybourn.bookcatalogue.booklist.BooklistGroup.RowKinds.ROW_KIND_YEAR_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ABSOLUTE_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ADDED_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ADDED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ADDED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_SORT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_COUNT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DOCID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_EXPANDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LEVEL;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO_SORT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_MARK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PRIMARY_SERIES_COUNT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLICATION_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLICATION_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_STATUS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_REAL_ROW_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ROOT_KEY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ROW_KIND;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NUM_FLOAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE_LETTER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_UPDATE_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_UPDATE_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_UPDATE_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_VISIBLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS_FTS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_LIST;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_LOAN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_ROW_NAVIGATOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_ROW_NAVIGATOR_FLATTENED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_SERIES;

/**
 * Class used to build and populate temporary tables with details of a flattened book list used to
 * display books in a ListView control and perform operation like 'expand/collapse' on pseudo nodes
 * in the list.
 *
 * TODO: [0123456789] replace by [0..9]
 *
 * @author Philip Warner
 */
public class BooklistBuilder implements AutoCloseable {

    /** Force list construction to compatible mode (compatible with Android 1.6) */
    public static final String PREF_BOOKLIST_GENERATION_MODE = "App.BooklistGenerationMode";
    /** BookList Compatibility mode property values */
    public static final int BOOKLIST_GENERATE_OLD_STYLE = 1;
    public static final int BOOKLIST_GENERATE_FLAT_TRIGGER = 2;
    public static final int BOOKLIST_GENERATE_NESTED_TRIGGER = 3;
    public static final int BOOKLIST_GENERATE_AUTOMATIC = 4;

    /**
     * Convenience expression for the SQL which gets formatted author names in 'Last, Given' form
     */
    private static final String AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION = "Case "
            + "When " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " = '' Then " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
            + " Else " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + "|| ', ' || " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES)
            + " End";
    /**
     * Convenience expression for the SQL which gets formatted author names in 'Given Last' form
     */
    private static final String AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION = "Case "
            + "When " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " = '' Then " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
            + " Else " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "|| ' ' || " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
            + " End";

    /** Counter for BooklistBuilder IDs */
    @NonNull
    private static Integer mBooklistBuilderIdCounter = 0;
    /** Debug counter */
    @NonNull
    private static Integer mInstanceCount = 0;
    /** Counter for 'flattened' book temp tables */
    @NonNull
    private static Integer mFlatNavCounter = 0;

    // List of columns for the group-by clause, including COLLATE clauses. Set by build() method.
    //private String mGroupColumnList;
    /** Collection of statements created by this Builder */
    @NonNull
    private final SqlStatementManager mStatements;
    /** Database to use */
    @NonNull
    private final SynchronizedDb mSyncedDb;
    /** Internal ID */
    private final int mBooklistBuilderId;
    /** Collection of 'extra' domains requested by caller */
    private final Map<String, ExtraDomainDetails> mExtraDomains = new HashMap<>();
    /** Style to use in building the list */
    @NonNull
    private final BooklistStyle mStyle;
    /**
     * Instance-based cursor factory so that the builder can be associated with the cursor
     * and the rowView. We could probably send less context, but in the first instance this
     * guarantees we get all the info we need downstream.
     */
    private final CursorFactory mBooklistCursorFactory = new CursorFactory() {
        @Override
        public Cursor newCursor(
                SQLiteDatabase db,
                @NonNull SQLiteCursorDriver masterQuery,
                @NonNull String editTable,
                @NonNull SQLiteQuery query) {
            return new BooklistCursor(masterQuery, editTable, query, BooklistBuilder.this, CatalogueDBAdapter.getSynchronizer());
        }
    };
    private final String mUNKNOWNText = BookCatalogueApp.getResourceString(R.string.unknown_uc);
    /** Local copy of the BOOK_LIST table definition, renamed to match this instance */
    private TableDefinition mListTable;
    /** Local copy of the navigation table definition, renamed to match this instance */
    private TableDefinition mNavTable;
    /** Object used in constructing the output table */
    @Nullable
    private SummaryBuilder mSummary = null;
    /** Statement used to perform initial insert */
    @Nullable
    private SynchronizedStatement mBaseBuildStmt = null;
    /** Collection of statements used to build remaining data */
    @Nullable
    private ArrayList<SynchronizedStatement> mLevelBuildStmts = null;
    @Nullable
    private SynchronizedStatement mDeleteListNodeSettingsStmt = null;
    @Nullable
    private SynchronizedStatement mSaveListNodeSettingsStmt = null;
    @Nullable
    private SynchronizedStatement mDeleteListNodeSettingStmt = null;
    @Nullable
    private SynchronizedStatement mSaveListNodeSettingStmt = null;
    @Nullable
    private SynchronizedStatement mGetPositionCheckVisibleStmt = null;
    @Nullable
    private SynchronizedStatement mGetPositionStmt = null;

    ///** Convenience expression for the SQL which gets the name of the person to whom a book has been loaned, if any
    // *  We do not initialize it here because it needs the app context to be setup for R.string.available */
    //private static String LOANED_TO_SQL = null;
    //private static String getLoanedToSql() {
    //	if (LOANED_TO_SQL == null) {
    //		LOANED_TO_SQL = "Coalesce( (Select " + TBL_LOAN.dot(DOM_LOANED_TO) + " From " + TBL_LOAN.ref() +
    //				" Where " + TBL_LOAN.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS.dot(DOM_ID) + "), '" + BookCatalogueApp.getResourceString(R.string.available) + ")";
    //	}
    //	return LOANED_TO_SQL;
    //}
    @Nullable
    private SynchronizedStatement mGetNodeRootStmt = null;
    @Nullable
    private SynchronizedStatement mGetNodeLevelStmt = null;
    @Nullable
    private SynchronizedStatement mGetNextAtSameLevelStmt = null;
    @Nullable
    private SynchronizedStatement mShowStmt = null;
    @Nullable
    private SynchronizedStatement mExpandStmt = null;
    private boolean mReferenceDecremented = false;

    /**
     * Constructor
     *
     * @param db    Database Adapter to use
     * @param style Book list style to use
     */
    public BooklistBuilder(@NonNull final CatalogueDBAdapter db, @NonNull final BooklistStyle style) {
        if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG) {
            synchronized (mInstanceCount) {
                mInstanceCount++;
                Logger.info("BB instances: " + mInstanceCount);
            }
        }

        // Allocate ID
        synchronized (mBooklistBuilderIdCounter) {
            mBooklistBuilderId = ++mBooklistBuilderIdCounter;
        }
        // Get the database and create a statements collection
        mSyncedDb = db.getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing();
        mStatements = new SqlStatementManager(mSyncedDb);
        // Save the requested style
        mStyle = style;

        // Clone the temp. table definitions and append the ID to make new names in case
        // more than one view is open.
        mListTable = TBL_BOOK_LIST.clone();
        mListTable.setName(mListTable.getName() + "_" + getId());
        mListTable.setType(TableTypes.Temporary); //RELEASE Make sure is TEMPORARY

        mNavTable = TBL_ROW_NAVIGATOR.clone()
                .addReference(mListTable, DOM_REAL_ROW_ID)
        ;
        mNavTable.setName(mNavTable.getName() + "_" + getId());
        mNavTable.setType(TableTypes.Temporary); //RELEASE Make sure is TEMPORARY
    }

    /**
     * Construct a flattened table of ordered book IDs based on the underlying list
     */
    @NonNull
    public FlattenedBooklist createFlattenedBooklist() {
        int flatId;
        synchronized (mFlatNavCounter) {
            flatId = mFlatNavCounter++;
        }
        TableDefinition flat = TBL_ROW_NAVIGATOR_FLATTENED.clone();
        flat.setName(flat.getName() + "_" + flatId);
        flat.setType(TableTypes.Temporary); //RELEASE Make sure is TEMPORARY
        flat.create(mSyncedDb, true);
        String sql = flat.getInsert(DOM_ID, DOM_BOOK_ID) +
                " SELECT " + mNavTable.dot(DOM_ID) + "," + mListTable.dot(DOM_BOOK_ID) +
                " FROM " + mListTable.ref() + mListTable.join(mNavTable) +
                " WHERE " + mListTable.dot(DOM_BOOK_ID) + " NOT NULL " +
                " ORDER BY " + mNavTable.dot(DOM_ID);
        mSyncedDb.execSQL(sql);
        return new FlattenedBooklist(mSyncedDb, flat);
    }

    private int getId() {
        return mBooklistBuilderId;
    }

    /**
     * Add a domain to the resulting flattened list based on the details provided.
     *
     * @param domain           Domain to add (used for name)
     * @param sourceExpression Expression to generate date for this column
     * @param isSorted         Indicates if it should be added to the sort key
     *
     * @return The builder (to allow chaining)
     */
    @NonNull
    @SuppressWarnings("UnusedReturnValue")
    public BooklistBuilder requireDomain(@NonNull final DomainDefinition domain, @NonNull final String sourceExpression, final boolean isSorted) {
        // Save the details
        ExtraDomainDetails info = new ExtraDomainDetails();
        info.domain = domain;
        info.sourceExpression = sourceExpression;
        info.isSorted = isSorted;

        // Check if it already exists
        if (mExtraDomains.containsKey(domain.name)) {
            // Make sure it has the same definition.
            boolean ok;
            ExtraDomainDetails oldInfo = mExtraDomains.get(domain.name);
            if (oldInfo.sourceExpression == null) {
                ok = info.sourceExpression == null || info.sourceExpression.isEmpty();
            } else {
                if (info.sourceExpression == null) {
                    ok = oldInfo.sourceExpression.isEmpty();
                } else {
                    ok = oldInfo.sourceExpression.equalsIgnoreCase(info.sourceExpression);
                }
            }
            if (!ok)
                throw new IllegalStateException("Required domain '" + domain.name + "' added with differing source expression");
        } else
            mExtraDomains.put(domain.name, info);

        return this;
    }

    /**
     * Drop and recreate all the data based on previous criteria
     */
    public void rebuild() {
        mSummary.recreateTable();

        mNavTable.drop(mSyncedDb);
        mNavTable.create(mSyncedDb, true);

        // Build base data
        mBaseBuildStmt.execute();
        // Rebuild all the rest
        for (SynchronizedStatement s : mLevelBuildStmts) {
            s.execute();
        }
    }

    private String localDateExpression(@NonNull final String fieldSpec) {
        // IF the field has a time part, then convert to local time. This deals with legacy 'date-only' dates.
        // The logic being that IF they had a time part then it would be UTC. Without a time part, we assume the
        // zone is local (or irrelevant).
        return "case when " + fieldSpec + " glob '*-*-* *' "
                + " then datetime(" + fieldSpec + ", 'localtime')"
                + " else " + fieldSpec + " end";
    }

    /**
     * Utility function to return a glob expression to get the 'year' from a text date field in a standard way.
     *
     * Just look for 4 leading numbers. We don't care about anything else.
     *
     * @param fieldSpec fully qualified field name
     * @param toLocal   convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    private String yearGlob(@NonNull String fieldSpec, final boolean toLocal) {
        if (toLocal) {
            fieldSpec = localDateExpression(fieldSpec);
        }

        return "case when " + fieldSpec + " glob '[0123456789][01234567890][01234567890][01234567890]*'\n" +
                "	Then substr(" + fieldSpec + ", 1, 4) \n" +
                " else '" + mUNKNOWNText + "' end";
    }

    /**
     * Utility function to return a glob expression to get the 'month' from a text date field in a standard way.
     *
     * Just look for 4 leading numbers followed by 2 or 1 digit. We don't care about anything else.
     *
     * @param fieldSpec fully qualified field name
     * @param toLocal   convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    private String monthGlob(@NonNull String fieldSpec, final boolean toLocal) {
        if (toLocal) {
            fieldSpec = localDateExpression(fieldSpec);
        }
        return "case when " + fieldSpec +
                " glob '[0123456789][01234567890][01234567890][01234567890]-[0123456789][01234567890]*'\n" +
                "	Then substr(" + fieldSpec + ", 6, 2) \n" +
                " when " + fieldSpec +
                " glob '[0123456789][01234567890][01234567890][01234567890]-[0123456789]*'\n" +
                "	Then substr(" + fieldSpec + ", 6, 1) \n" +
                " else '" + mUNKNOWNText + "' end";
    }

    /**
     * Utility function to return a glob expression to get the 'day' from a text date field in a standard way.
     *
     * Just look for 4 leading numbers followed by 2 or 1 digit, and then 1 or two digits.
     * We don't care about anything else.
     *
     * @param fieldSpec fully qualified field name
     * @param toLocal   convert the fieldSpec to local time from UTC
     *
     * @return expression
     */
    private String dayGlob(@NonNull String fieldSpec, final boolean toLocal) {
        if (toLocal) {
            fieldSpec = localDateExpression(fieldSpec);
        }

        // Just look for 4 leading numbers followed by 2 or 1 digit then another 2 or 1 digit. We don't care about anything else.
        return "case " +
                " when " + fieldSpec +
                " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789][0123456789]-[0123456789][0123456789]*'\n" +
                "	Then substr(" + fieldSpec + ", 9, 2) \n" +
                " when " + fieldSpec +
                " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789]-[0123456789][0123456789]*'\n" +
                "	Then substr(" + fieldSpec + ", 8, 2) \n" +
                " when " + fieldSpec +
                " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789][0123456789]-[0123456789]*'\n" +
                "	Then substr(" + fieldSpec + ", 9, 1) \n" +
                " when " + fieldSpec +
                " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789]-[0123456789]*'\n" +
                "	Then substr(" + fieldSpec + ", 8, 1) \n" +
                " else " + fieldSpec + " end";
    }

    /**
     * Clear and the build the temporary list of books based on the passed criteria.
     *
     * @param preferredState State to display: expanded, collapsed or remembered
     * @param markId         TODO: ID of book to 'mark'. DEPRECATED?
     * @param bookshelf      Search criteria: limit to shelf
     * @param authorWhere    Search criteria: additional conditions that apply to authors table
     * @param bookWhere      Search criteria: additional conditions that apply to book table
     * @param loaned_to      Search criteria: only books loaned to named person
     * @param seriesName     Search criteria: only books in named series
     * @param searchText     Search criteria: book details must in some way contain the passed text
     */
    public void build(final int preferredState,
                      final long markId,
                      @NonNull final String bookshelf,
                      @NonNull final String authorWhere,
                      @NonNull final String bookWhere,
                      @NonNull final String loaned_to,
                      @NonNull final String seriesName,
                      @NonNull String searchText) {
        Tracker.handleEvent(this, "build-" + getId(), Tracker.States.Enter);
        try {
            @SuppressWarnings("UnusedAssignment")
            long t0 = System.currentTimeMillis();

            // Cleanup searchText
            //
            // Because FTS does not understand locales in all android up to 4.2,
            // we do case folding here using the default locale.
            //
            if (searchText != null) {
                searchText = searchText.toLowerCase(Locale.getDefault());
            }

            // Rebuild the main table definition
            mListTable = TBL_BOOK_LIST.clone();
            mListTable.setName(mListTable.getName() + "_" + getId());
            mListTable.setType(TableTypes.Temporary); // RELEASE Make sure is TEMPORARY

            // Rebuild the navigation table definition
            mNavTable = TBL_ROW_NAVIGATOR.clone()
                    .addReference(mListTable, DOM_REAL_ROW_ID)
            ;
            mNavTable.setName(mNavTable.getName() + "_" + getId());
            mNavTable.setType(TableTypes.Temporary); //RELEASE Make sure is TEMPORARY

            // Get a new summary builder utility object
            SummaryBuilder summary = new SummaryBuilder();

            // Add the minimum required domains which will have special handling
            mListTable.addDomain(DOM_ID); // Will use default value
            mListTable.addDomain(DOM_ROOT_KEY);    // Will use expression based on first group; determined later

            // Add the domains that have simple pre-determined expressions as sources
            summary.addDomain(DOM_LEVEL, Integer.toString(mStyle.size() + 1), SummaryBuilder.FLAG_NONE);
            summary.addDomain(DOM_ROW_KIND, "" + ROW_KIND_BOOK, SummaryBuilder.FLAG_NONE);
            summary.addDomain(DOM_BOOK_ID, TBL_BOOKS.dot(DOM_ID), SummaryBuilder.FLAG_NONE);
            summary.addDomain(DOM_BOOK_COUNT, "1", SummaryBuilder.FLAG_NONE);

            // Will be set to appropriate Group if a Series group exists in style
            BooklistSeriesGroup seriesGroup = null;
            // Will be set to appropriate Group if an Author group exists in style
            BooklistAuthorGroup authorGroup = null;
            // Will be set to TRUE if a LOANED group exists in style
            boolean hasGroupLOANED = false;
            // Will be set to TRUE if a BOOKSHELF group exists in style
            boolean hasGroupBOOKSHELF = false;

            // We can not use triggers to fill in headings in API < 8 since SQLite 3.5.9 is broken
            // Allow for the user preferences to override in case another build is broken.
            final int listMode = BookCatalogueApp.Prefs.getInt(
                    PREF_BOOKLIST_GENERATION_MODE,
                    BooklistBuilder.BOOKLIST_GENERATE_AUTOMATIC);
            boolean useTriggers;
            boolean flatTriggers = false;
            // Based on the users choice, decide how the list will be generated.
            switch (listMode) {

                case BOOKLIST_GENERATE_OLD_STYLE:
                    useTriggers = false;
                    break;
                case BOOKLIST_GENERATE_AUTOMATIC:
                    useTriggers = true;
                    break;
                case BOOKLIST_GENERATE_FLAT_TRIGGER:
                    useTriggers = true;
                    flatTriggers = true;
                    break;

                case BOOKLIST_GENERATE_NESTED_TRIGGER:
                    useTriggers = true;
                    flatTriggers = false;
                    break;

                default:
                    useTriggers = true;
                    break;
            }
            // Build a sort mask based on if triggers are used; we can not
            // reverse sort if they are not used.
            final int sortDescendingMask = (useTriggers ? SummaryBuilder.FLAG_SORT_DESCENDING : 0);

            @SuppressWarnings("UnusedAssignment")
            long t0a = System.currentTimeMillis();

            for (BooklistGroup booklistGroup : mStyle) {
                //<editor-fold desc="Process each group in the style">

                //
                //	Build each kind group.
                //
                //  ****************************************************************************************
                //  IMPORTANT NOTE: for each kind, then FIRST SORTED AND GROUPED domain should be the one
                //					that will be displayed and that level in the UI.
                //  ****************************************************************************************
                //
                switch (booklistGroup.kind) {

                    // NEWKIND: Add new kinds to this list

                    case ROW_KIND_SERIES:
                        booklistGroup.displayDomain = DOM_SERIES_NAME;
                        // Save this for later use
                        seriesGroup = (BooklistSeriesGroup) booklistGroup;
                        // Group and sort by name
                        summary.addDomain(DOM_SERIES_NAME,
                                TBL_SERIES.dot(DOM_SERIES_NAME),
                                SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);

                        // Group by ID (we want the ID available and there is a *chance* two series will have the same name...with bad data */
                        summary.addDomain(DOM_SERIES_ID,
                                TBL_BOOK_SERIES.dot(DOM_SERIES_ID),
                                SummaryBuilder.FLAG_GROUPED);

                        // We want the series position in the base data
                        summary.addDomain(DOM_BOOK_SERIES_POSITION,
                                TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION),
                                SummaryBuilder.FLAG_NONE);

                        // We want a counter of how many books use the series as a primary series, so we can skip some series
                        summary.addDomain(DOM_PRIMARY_SERIES_COUNT,
                                "case when Coalesce(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + ",1) == 1 then 1 else 0 end",
                                SummaryBuilder.FLAG_NONE);

                        // This group can be given a name of the form 's/<n>' where <n> is the series id, eg. 's/18'.
                        booklistGroup.setKeyComponents("s", DOM_SERIES_ID);
                        break;

                    case ROW_KIND_AUTHOR:
                        booklistGroup.displayDomain = DOM_AUTHOR_FORMATTED;
                        // Save this for later use
                        authorGroup = (BooklistAuthorGroup) booklistGroup;
                        // Always group & sort by 'Last, Given' expression
                        summary.addDomain(DOM_AUTHOR_SORT, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);
                        // Add the 'formatted' field of the requested type
                        if (authorGroup.showGivenName())
                            summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);
                        else
                            summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);
                        // We also want the ID
                        summary.addDomain(DOM_AUTHOR_ID, TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID), SummaryBuilder.FLAG_GROUPED);

                        // This group can be given a name of the form 'a/<n>' where <n> is the author id, eg. 's/18'.
                        booklistGroup.setKeyComponents("a", DOM_AUTHOR_ID);

                        break;

                    case ROW_KIND_GENRE:
                        booklistGroup.displayDomain = DOM_BOOK_GENRE;
                        summary.addDomain(DOM_BOOK_GENRE, TBL_BOOKS.dot(DOM_BOOK_GENRE), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        booklistGroup.setKeyComponents("g", DOM_BOOK_GENRE);
                        break;

                    case ROW_KIND_LANGUAGE:
                        // The domain used to display the data on the screen (not always the underlying domain)
                        booklistGroup.displayDomain = DOM_BOOK_LANGUAGE;
                        // Define how the new field is retrieved and sorted/grouped
                        summary.addDomain(DOM_BOOK_LANGUAGE, TBL_BOOKS.dot(DOM_BOOK_LANGUAGE), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        // Unique name for this field and the source data field
                        booklistGroup.setKeyComponents("lang", DOM_BOOK_LANGUAGE);
                        break;

                    case ROW_KIND_LOCATION:
                        booklistGroup.displayDomain = DOM_BOOK_LOCATION;
                        summary.addDomain(DOM_BOOK_LOCATION, "Coalesce(" + TBL_BOOKS.dot(DOM_BOOK_LOCATION) + ", '')", SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        booklistGroup.setKeyComponents("loc", DOM_BOOK_LOCATION);
                        break;

                    case ROW_KIND_BOOKSHELF:
                        booklistGroup.displayDomain = DOM_BOOKSHELF;
                        summary.addDomain(DOM_BOOKSHELF, "Coalesce(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + ", '')", SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        booklistGroup.setKeyComponents("shelf", DOM_BOOKSHELF);
                        hasGroupBOOKSHELF = true;
                        break;

                    case ROW_KIND_PUBLISHER:
                        booklistGroup.displayDomain = DOM_BOOK_PUBLISHER;
                        summary.addDomain(DOM_BOOK_PUBLISHER, TBL_BOOKS.dot(DOM_BOOK_PUBLISHER), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        booklistGroup.setKeyComponents("p", DOM_BOOK_PUBLISHER);
                        break;

                    case ROW_KIND_RATING:
                        booklistGroup.displayDomain = DOM_BOOK_RATING;
                        summary.addDomain(DOM_BOOK_RATING, "Cast(" + TBL_BOOKS.dot(DOM_BOOK_RATING) + " AS Integer)", SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                        booklistGroup.setKeyComponents("rat", DOM_BOOK_RATING);
                        break;

                    case ROW_KIND_FORMAT:
                        booklistGroup.displayDomain = DOM_BOOK_FORMAT;
                        summary.addDomain(DOM_BOOK_FORMAT, "Coalesce(" + TBL_BOOKS.dot(DOM_BOOK_FORMAT) + ", '')", SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        booklistGroup.setKeyComponents("fmt", DOM_BOOK_FORMAT);
                        break;

                    case ROW_KIND_READ_AND_UNREAD:
                        booklistGroup.displayDomain = DOM_READ_STATUS;
                        String unreadExpr = "Case When " + TBL_BOOKS.dot(DOM_BOOK_READ) + " = 1\n" +
                                "	Then '" + BookCatalogueApp.getResourceString(R.string.booklist_read) + "'\n" +
                                " Else '" + BookCatalogueApp.getResourceString(R.string.booklist_unread) + "' end";
                        summary.addDomain(DOM_READ_STATUS, unreadExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        // We want the READ flag at the lowest level only. Some bad data means that it may be 0 or 'f', so we don't group by it.
                        summary.addDomain(DOM_BOOK_READ, TBL_BOOKS.dot(DOM_BOOK_READ), SummaryBuilder.FLAG_NONE);
                        booklistGroup.setKeyComponents("r", DOM_READ_STATUS);
                        break;

                    case ROW_KIND_LOANED:
                        // Saved for later to indicate group was present
                        hasGroupLOANED = true;
                        booklistGroup.displayDomain = DOM_LOANED_TO;
                        summary.addDomain(DOM_LOANED_TO_SORT, "Case When " + TBL_LOAN.dot(DOM_LOANED_TO) + " is null then 1 else 0 end", SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        summary.addDomain(DOM_LOANED_TO, "Case When " + TBL_LOAN.dot(DOM_LOANED_TO) + " is null then '" + BookCatalogueApp.getResourceString(R.string.available) + "'" +
                                        " else '" + BookCatalogueApp.getResourceString(R.string.loaned_to_2) + "' || " + TBL_LOAN.dot(DOM_LOANED_TO) + " end",
                                SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        booklistGroup.setKeyComponents("l", DOM_LOANED_TO);
                        break;

                    case ROW_KIND_TITLE_LETTER:
                        booklistGroup.displayDomain = DOM_TITLE_LETTER;
                        String titleLetterExpr = "substr(" + TBL_BOOKS.dot(DOM_TITLE) + ",1,1)";
                        summary.addDomain(DOM_TITLE_LETTER, titleLetterExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        booklistGroup.setKeyComponents("t", DOM_TITLE_LETTER);
                        break;

                    case ROW_KIND_YEAR_PUBLISHED:
                        booklistGroup.displayDomain = DOM_PUBLICATION_YEAR;
                        // Use our standard glob expression
                        String yearPubExpr = yearGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_PUBLISHED), false);
                        summary.addDomain(DOM_PUBLICATION_YEAR, yearPubExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        booklistGroup.setKeyComponents("yrp", DOM_PUBLICATION_YEAR);
                        break;

                    case ROW_KIND_MONTH_PUBLISHED:
                        booklistGroup.displayDomain = DOM_PUBLICATION_MONTH;
                        // Use our standard glob expression
                        String monthPubExpr = monthGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_PUBLISHED), false);
                        summary.addDomain(DOM_PUBLICATION_MONTH, monthPubExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                        booklistGroup.setKeyComponents("mnp", DOM_PUBLICATION_MONTH);
                        break;

                    case ROW_KIND_YEAR_ADDED:
                        booklistGroup.displayDomain = DOM_ADDED_YEAR;
                        // Use our standard glob expression
                        String yearAddedExpr = yearGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true);
                        // TODO: Handle 'DESCENDING'. Requires the navigator construction to use max/min for non-grouped domains that appear in sublevels based on desc/asc.
                        // We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
                        summary.addDomain(DOM_ADDED_YEAR, yearAddedExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                        booklistGroup.setKeyComponents("yra", DOM_ADDED_YEAR);
                        break;

                    case ROW_KIND_MONTH_ADDED:
                        booklistGroup.displayDomain = DOM_ADDED_MONTH;
                        // Use our standard glob expression
                        String monthAddedExpr = monthGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true);
                        // We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
                        summary.addDomain(DOM_ADDED_MONTH, monthAddedExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                        booklistGroup.setKeyComponents("mna", DOM_ADDED_MONTH);
                        break;

                    case ROW_KIND_DAY_ADDED:
                        booklistGroup.displayDomain = DOM_ADDED_DAY;
                        // Use our standard glob expression
                        String dayAddedExpr = dayGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true);
                        // We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
                        summary.addDomain(DOM_ADDED_DAY, dayAddedExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                        booklistGroup.setKeyComponents("dya", DOM_ADDED_DAY);
                        break;


                    case ROW_KIND_UPDATE_YEAR:
                        booklistGroup.displayDomain = DOM_UPDATE_YEAR;
                        // Use our standard glob expression
                        String yearUpdatedExpr = yearGlob(TBL_BOOKS.dot(DOM_LAST_UPDATE_DATE), true);
                        // TODO: Handle 'DESCENDING'. Requires the navigator construction to use max/min for non-grouped domains that appear in sublevels based on desc/asc.
                        // We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
                        summary.addDomain(DOM_UPDATE_YEAR, yearUpdatedExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                        booklistGroup.setKeyComponents("yru", DOM_UPDATE_YEAR);
                        summary.addDomain(DOM_LAST_UPDATE_DATE, null, SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                        break;

                    case ROW_KIND_UPDATE_MONTH:
                        booklistGroup.displayDomain = DOM_UPDATE_MONTH;
                        // Use our standard glob expression
                        String monthUpdatedExpr = monthGlob(TBL_BOOKS.dot(DOM_LAST_UPDATE_DATE), true);
                        // We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
                        summary.addDomain(DOM_UPDATE_MONTH, monthUpdatedExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                        booklistGroup.setKeyComponents("mnu", DOM_UPDATE_MONTH);
                        summary.addDomain(DOM_LAST_UPDATE_DATE, null, SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                        break;

                    case ROW_KIND_UPDATE_DAY:
                        booklistGroup.displayDomain = DOM_UPDATE_DAY;
                        // Use our standard glob expression
                        String dayUpdatedExpr = dayGlob(TBL_BOOKS.dot(DOM_LAST_UPDATE_DATE), true);
                        // We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
                        summary.addDomain(DOM_UPDATE_DAY, dayUpdatedExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                        booklistGroup.setKeyComponents("dyu", DOM_UPDATE_DAY);
                        summary.addDomain(DOM_LAST_UPDATE_DATE, null, SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                        break;


                    case ROW_KIND_YEAR_READ:
                        booklistGroup.displayDomain = DOM_READ_YEAR;
                        // TODO: Handle 'DESCENDING'. Requires the navigator construction to use max/min for non-grouped domains that appear in sublevels based on desc/asc.
                        // We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
                        summary.addDomain(DOM_READ_YEAR, yearGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | SummaryBuilder.FLAG_SORT_DESCENDING);
                        booklistGroup.setKeyComponents("yrr", DOM_READ_YEAR);
                        break;

                    case ROW_KIND_MONTH_READ:
                        booklistGroup.displayDomain = DOM_READ_MONTH;
                        // We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
                        summary.addDomain(DOM_READ_MONTH, monthGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | SummaryBuilder.FLAG_SORT_DESCENDING);
                        booklistGroup.setKeyComponents("mnr", DOM_READ_MONTH);
                        break;

                    case ROW_KIND_DAY_READ:
                        booklistGroup.displayDomain = DOM_READ_DAY;
                        // We don't use DESCENDING sort yet because the 'header' ends up below the detail rows in the flattened table.
                        summary.addDomain(DOM_READ_DAY, dayGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | SummaryBuilder.FLAG_SORT_DESCENDING);
                        booklistGroup.setKeyComponents("dyr", DOM_READ_DAY);
                        break;

                    default:
                        throw new RTE.IllegalTypeException("" + booklistGroup.kind);

                }
                // Copy the current groups to this level item; this effectively accumulates 'group by' domains
                // down each level so that the top has fewest groups and the bottom level has groups for all levels.
                booklistGroup.groupDomains = summary.cloneGroups();

                //</editor-fold>
            }

            @SuppressWarnings("UnusedAssignment")
            long t0b = System.currentTimeMillis();

            // Want the UUID for the book so we can get thumbs
            summary.addDomain(DOM_BOOK_UUID, TBL_BOOKS.dot(DOM_BOOK_UUID), SummaryBuilder.FLAG_NONE);

            // If we have a book ID to mark, then add the MARK field, and setup the expression.
            if (markId != 0) {
                summary.addDomain(DOM_MARK, TBL_BOOKS.dot(DOM_ID) + "=" + markId, SummaryBuilder.FLAG_NONE);
            }

            if (seriesGroup != null) {
                // We want the series number in the base data in sorted order

                // Allow for the possibility of 3.1, or even "3.1|Omnibus 3-10" as a series name.
                // so we convert it to a float.
                summary.addDomain(DOM_SERIES_NUM_FLOAT, "cast(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM) + " AS float)", SummaryBuilder.FLAG_SORTED);
                // We also add the base name as a sorted field for display purposes and in case of non-numeric data.
                summary.addDomain(DOM_BOOK_SERIES_NUM, TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM), SummaryBuilder.FLAG_SORTED);
            }
            summary.addDomain(DOM_LEVEL, null, SummaryBuilder.FLAG_SORTED);

            // Ensure any caller-specified extras (eg. title) are added at the end.
            for (Entry<String, ExtraDomainDetails> d : mExtraDomains.entrySet()) {
                ExtraDomainDetails info = d.getValue();
                int flags;
                if (info.isSorted)
                    flags = SummaryBuilder.FLAG_SORTED;
                else
                    flags = SummaryBuilder.FLAG_NONE;
                summary.addDomain(info.domain, info.sourceExpression, flags);
            }
            @SuppressWarnings("UnusedAssignment")
            long t0c = System.currentTimeMillis();


            /*
             Build the initial insert statement: 'insert into <tbl> (col-list) select (expr-list) from'.
             We just need to add the 'from' tables. It is a fairly static list, for the most part we just
             add extra criteria as needed.

             The seriesLevel and authorLevel fields will influenced the nature of the join. If at a later
             stage some row kinds introduce more table dependencies, a flag (or object) can be set
             when processing the level to inform the joining code (below) which tables need to be added.
            */
            SqlComponents sqlCmp = summary.buildSqlComponents(mStyle.getGroupAt(0).getCompoundKey());

            @SuppressWarnings("UnusedAssignment")
            long t0d = System.currentTimeMillis();

            /*
             * Now build the 'join' statement based on the groups and extra criteria
             * The sql used prior to using {@link JoinContext} is included as comments below
             * the code that replaced it.
             */
            JoinContext join;

            // If there is a bookshelf specified, start the join there. Otherwise, start with the BOOKS table.
            if (hasGroupBOOKSHELF || !bookshelf.isEmpty()) {
                join = new JoinContext(TBL_BOOKSHELF)
                        .start()
                        .join(TBL_BOOK_BOOKSHELF)
                        .join(TBL_BOOKS);
            } else {
                join = new JoinContext(TBL_BOOKS).start();
            }
				/*
				if (!bookshelf.isEmpty()) {
					sql += "	" + DB_TB_BOOKSHELF_AND_ALIAS + " join " + DB_TB_BOOK_BOOKSHELF_AND_ALIAS +
							" On " + ALIAS_BOOK_BOOKSHELF + "." + KEY_BOOKSHELF_NAME + " = " + ALIAS_BOOKSHELF + "." + KEY_ID ;
					sql +=	"    join " + DB_TB_BOOKS_AND_ALIAS + " on " + ALIAS_BOOKS + "." + KEY_ID + " = " + ALIAS_BOOK_BOOKSHELF + "." + KEY_BOOK_ID + "\n";
				} else {
					sql +=	"    " + DB_TB_BOOKS_AND_ALIAS + "\n";
				}
				*/

            // If a LOANED level is present, we are ONLY interested in loaned books. So cross it here.
            if (hasGroupLOANED) {
                join.leftOuterJoin(TBL_LOAN);
            }

            // Now join with author; we must specify a parent in the join, because the last table
            // joined was one of BOOKS or LOAN and we don't know which. So we explicitly use books.
            join.join(TBL_BOOKS, TBL_BOOK_AUTHOR);
            // If there is no author group, or the user only wants primary author, get primary only
            if (authorGroup == null || !authorGroup.showAllAuthors()) {
                join.append("		AND " + TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_POSITION) + " == 1\n");
            }
            // Join with authors to make the names available
            join.join(TBL_AUTHORS);

            // Current table will be authors, so name parent explicitly to join books->book_series.
            join.leftOuterJoin(TBL_BOOKS, TBL_BOOK_SERIES);
				/*
				sql +=	"    join " + DB_TB_BOOK_AUTHOR_AND_ALIAS + " on " + ALIAS_BOOK_AUTHOR + "." + KEY_BOOK_ID + " = " + ALIAS_BOOKS + "." + KEY_ID + "\n" +
						"    join " + DB_TB_AUTHORS_AND_ALIAS + " on " + ALIAS_AUTHORS + "." + KEY_ID + " = " + ALIAS_BOOK_AUTHOR + "." + KEY_AUTHOR_ID + "\n";
				sql +=	"    left outer join " + DB_TB_BOOK_SERIES_AND_ALIAS + " on " + ALIAS_BOOK_SERIES + "." + KEY_BOOK_ID + " = " + ALIAS_BOOKS + "." + KEY_ID + "\n";
				*/

            // If there was no series group, or user requests primary series only, then just get primary series.
            if (seriesGroup == null || !seriesGroup.showAllSeries()) {
                join.append("		AND " + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + " == 1\n");
            }
            // Join with series to get name
            join.leftOuterJoin(TBL_SERIES);
				/*
				if (seriesLevel == null || !seriesLevel.allSeries) {
					sql += "		and " + ALIAS_BOOK_SERIES + "." + KEY_SERIES_POSITION + " == 1\n";
				}
				sql +=	"    left outer join " + DB_TB_SERIES_AND_ALIAS + " on " + ALIAS_SERIES + "." + KEY_ID + " = " + ALIAS_BOOK_SERIES + "." + KEY_SERIES_ID;
				*/

            // Append the resulting join tables to our initial insert statement
            sqlCmp.join = join.toString();

            @SuppressWarnings("UnusedAssignment")
            long t0e = System.currentTimeMillis();
            // Now build the 'where' clause.
            StringBuilder where = build_whereClause(bookshelf, authorWhere, bookWhere, loaned_to, seriesName, searchText, hasGroupBOOKSHELF);

            // If we got any conditions, add them to the initial insert statement
            if (where.length() != 0) {
                sqlCmp.where = " WHERE " + where;
            } else {
                sqlCmp.where = "";
            }

            @SuppressWarnings("UnusedAssignment")
            long t1 = System.currentTimeMillis();
            // Check if the collation we use is case sensitive; bug introduced in ICS was to make UNICODE not CI.
            // Due to bugs in other language sorting, we are now forced to use a different collation  anyway, but
            // we still check if it is CI.
            boolean collationIsCs = CollationCaseSensitive.isCaseSensitive(mSyncedDb.getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing());

            // List of column names appropriate for 'Order By' clause
            String sortColNameList;
            // List of column names appropriate for 'Create Index' column list
            String sortIndexColumnList;

            // Process the 'sort-by' columns into a list suitable for a sort-by statement, or index
            {
                final ArrayList<SortedDomainInfo> sort = summary.getSortedColumns();
                final StringBuilder sortCols = new StringBuilder();
                final StringBuilder indexCols = new StringBuilder();
                for (SortedDomainInfo sdi : sort) {
                    indexCols.append(sdi.domain.name);
                    if (sdi.domain.isText()) {
                        indexCols.append(DatabaseHelper.COLLATION);

                        // *If* collations is case-sensitive, handle it.
                        if (collationIsCs)
                            sortCols.append("lower(");
                        sortCols.append(sdi.domain.name);
                        if (collationIsCs)
                            sortCols.append(")");
                        sortCols.append(DatabaseHelper.COLLATION);
                    } else {
                        sortCols.append(sdi.domain.name);
                    }
                    if (sdi.isDescending) {
                        indexCols.append(" DESC");
                        sortCols.append(" DESC");
                    }
                    sortCols.append(", ");
                    indexCols.append(", ");
                }
                sortCols.append(DOM_LEVEL.name);
                indexCols.append(DOM_LEVEL.name);
                sortColNameList = sortCols.toString();
                sortIndexColumnList = indexCols.toString();
            }

            // unused
            // Process the group-by columns suitable for a group-by statement or index
//			{
//				final StringBuilder groupCols = new StringBuilder();
//                for (DomainDefinition d: summary.cloneGroups()) {
//					groupCols.append(d.name);
//					groupCols.append(CatalogueDBAdapter.COLLATION);
//					groupCols.append(", ");
//				}
//				groupCols.append( DOM_LEVEL.name );
//				//mGroupColumnList = groupCols.toString();
//			}

            String ix1Sql = "CREATE INDEX " + mListTable + "_IX1 ON " + mListTable + "(" + sortIndexColumnList + ")";
			/* Indexes that were tried. None had a substantial impact with 800 books.
			String ix1aSql = "Create Index " + mListTable + "_IX1a on " + mListTable + "(" + DOM_LEVEL + ", " + mSortColumnList + ")";
			String ix2Sql = "Create Unique Index " + mListTable + "_IX2 on " + mListTable + "(" + DOM_BOOK_ID + ", " + DOM_ID + ")";

			String ix3Sql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList + ")";
			String ix3aSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + DOM_LEVEL + ", " + mGroupColumnList + ")";
			String ix3bSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList +  ", " + DOM_LEVEL + ")";
			String ix3cSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList +  ", " + DOM_ROOT_KEY + CatalogueDBAdapter.COLLATION + ")";
			String ix3dSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + DOM_LEVEL + ", " + mGroupColumnList +  ", " + DOM_ROOT_KEY + ")";
			String ix3eSql = "Create Index " + mListTable + "_IX3 on " + mListTable + "(" + mGroupColumnList +  ", " + DOM_ROOT_KEY + "," + DOM_LEVEL + ")";
			String ix4Sql = "Create Index " + mListTable + "_IX4 on " + mListTable + "(" + DOM_LEVEL + "," + DOM_EXPANDED + "," + DOM_ROOT_KEY + ")";
			*/

            // We are good to go.
            @SuppressWarnings("UnusedAssignment")
            long t1a = System.currentTimeMillis();
            //mSyncedDb.execSQL("PRAGMA synchronous = OFF"); -- Has very little effect
            SyncLock txLock = mSyncedDb.beginTransaction(true);
            @SuppressWarnings("UnusedAssignment")
            long t1b = System.currentTimeMillis();
            try {
                //
                // This is experimental code that replaced the INSERT...SELECT with a cursor
                // and an INSERT statement. It was literally 10x slower, largely due to the
                // Android implementation of SQLiteStatement and the way it handles parameter
                // binding. Kept for future experiments
                //
                ////
                //// Code to manually insert each row
                ////
                //@SuppressWarnings("UnusedAssignment")
                //long TM0 = System.currentTimeMillis();
                //String selStmt = sqlCmp.select + " from " + sqlCmp.join + " " + sqlCmp.where + " Order by " + sortIndexColumnList;
                //final Cursor selCsr = mSyncedDb.rawQuery(selStmt);
                //final SynchronizedStatement insStmt = mSyncedDb.compileStatement(sqlCmp.insertValues);
                ////final String baseIns = sqlCmp.insert + " Values (";
                ////SQLiteStatement insStmt = insSyncStmt.getUnderlyingStatement();
                //
                //final int cnt = selCsr.getColumnCount();
                ////StringBuilder insBuilder = new StringBuilder();
                //while(selCsr.moveToNext()) {
                //	//insBuilder.append(baseIns);
                //	for(int i = 0; i < cnt; i++) {
                //		//if (i > 0) {
                //		//	insBuilder.append(", ");
                //		//}
                //		// THIS IS SLOWER than using Strings!!!!
                //		//switch(selCsr.getType(i)) {
                //		//case android.database.Cursor.FIELD_TYPE_NULL:
                //		//	insStmt.bindNull(i+1);
                //		//	break;
                //		//case android.database.Cursor.FIELD_TYPE_FLOAT:
                //		//	insStmt.bindDouble(i+1, selCsr.getDouble(i));
                //		//	break;
                //		//case android.database.Cursor.FIELD_TYPE_INTEGER:
                //		//	insStmt.bindLong(i+1, selCsr.getLong(i));
                //		//	break;
                //		//case android.database.Cursor.FIELD_TYPE_STRING:
                //		//	insStmt.bindString(i+1, selCsr.getString(i));
                //		//	break;
                //		//case android.database.Cursor.FIELD_TYPE_BLOB:
                //		//	insStmt.bindNull(i+1);
                //		//	break;
                //		//}
                //		final String v = selCsr.getString(i);
                //		if (v == null) {
                //			//insBuilder.append("NULL");
                //			insStmt.bindNull(i+1);
                //		} else {
                //			//insBuilder.append("'");
                //			//insBuilder.append(CatalogueDBAdapter.encodeString(v));
                //			//insBuilder.append("'");
                //			insStmt.bindString(i+1, v);
                //		}
                //	}
                //	//insBuilder.append(")");
                //	//mSyncedDb.execSQL(insBuilder.toString());
                //	//insBuilder.setLength(0);
                //	insStmt.execute();
                //}
                //selCsr.close();
                //@SuppressWarnings("UnusedAssignment")
                //long TM1 = System.currentTimeMillis();
                //Logger.info("Time to MANUALLY INSERT: " + (TM1-TM0));

                mLevelBuildStmts = new ArrayList<>();

                // Build the lowest level summary using our initial insert statement
                long t2;
                long t2a[] = new long[mStyle.size()];
                long t3;

                if (useTriggers) {
                    // If we are using triggers, then we insert them in order and rely on the
                    // triggers to build the summary rows in the correct place.
                    String tgt = makeTriggers(summary, flatTriggers);
                    mBaseBuildStmt = mStatements.add("mBaseBuildStmt",
                            "INSERT INTO " + tgt + "(" + sqlCmp.destinationColumns + ") " + sqlCmp.select +
                                    "\n FROM\n" + sqlCmp.join + sqlCmp.where + " ORDER BY " + sortColNameList);
                    mBaseBuildStmt.execute();
                    t2 = System.currentTimeMillis();
                    //noinspection UnusedAssignment
                    t3 = t2;
                } else {
                    // Without triggers we just get the base rows and add summary later
                    mBaseBuildStmt = mStatements.add("mBaseBuildStmt",
                            sqlCmp.insertSelect + sqlCmp.join + sqlCmp.where);
                    //Logger.info("Base Build:\n" + sql);
                    mBaseBuildStmt.execute();
                    //noinspection UnusedAssignment
                    t2 = System.currentTimeMillis();

                    // Now build each summary level query based on the prior level.
                    // We build and run from the bottom up.

                    int pos = 0;
                    // Loop from innermost group to outermost, building summary at each level
                    for (int i = mStyle.size() - 1; i >= 0; i--) {
                        final BooklistGroup g = mStyle.getGroupAt(i);
                        final int levelId = i + 1;
                        // cols is the list of column names for the 'Insert' and 'Select' parts
                        StringBuilder cols = new StringBuilder();
                        // collatedCols is used for the group-by
                        StringBuilder collatedCols = new StringBuilder();

                        // Build the column lists for this group
                        for (DomainDefinition d : g.groupDomains) {
                            if (collatedCols.length() > 0)
                                collatedCols.append(",");
                            cols.append(",\n	").append(d.name);
                            collatedCols.append("\n	").append(d.name).append(DatabaseHelper.COLLATION);
                        }
                        // Construct the sum statement for this group
                        String sql = "Insert INTO " + mListTable + "(\n	" + DOM_LEVEL + ",\n	" + DOM_ROW_KIND +
                                cols + "," + DOM_ROOT_KEY +
                                ")" +
                                "\n SELECT " + levelId + " AS " + DOM_LEVEL + ",\n	" + g.kind + " AS " + DOM_ROW_KIND +
                                cols + "," + DOM_ROOT_KEY +
                                "\n FROM " + mListTable + "\n " + " WHERE level = " + (levelId + 1) +
                                "\n Group by " + collatedCols + "," + DOM_ROOT_KEY + DatabaseHelper.COLLATION;
                        //"\n Group by " + DOM_LEVEL + ", " + DOM_ROW_KIND + collatedCols;

                        // Save, compile and run this statement
                        SynchronizedStatement stmt = mStatements.add("L" + i, sql);
                        mLevelBuildStmts.add(stmt);
                        stmt.execute();
                        t2a[pos++] = System.currentTimeMillis();
                    }

                    // Build an index
                    //noinspection UnusedAssignment
                    t3 = System.currentTimeMillis();
                    // Build an index if it will help sorting
                    // - *If* collation is case-sensitive, don't bother with index, since everything is wrapped in lower().
                    // ENHANCE: ICS UNICODE: Consider adding a duplicate _lc (lower case) column to the SUMMARY table. Ugh.
                    if (!collationIsCs) {
                        SynchronizedStatement stmt = mStatements.add("ix1", ix1Sql);
                        mLevelBuildStmts.add(stmt);
                        stmt.execute();
                    }
                }

                @SuppressWarnings("UnusedAssignment")
                long t3a = System.currentTimeMillis();
                // Analyze the table
                mSyncedDb.execSQL("analyze " + mListTable);
                @SuppressWarnings("UnusedAssignment")
                long t3b = System.currentTimeMillis();

                // Now build a lookup table to match row sort position to row ID. This is used to match a specific
                // book (or other row in result set) to a position directly without having to scan the database. This
                // is especially useful in expand/collapse operations.
                mNavTable.drop(mSyncedDb);
                mNavTable.create(mSyncedDb, true);

                String sortExpression;
                if (useTriggers) {
                    sortExpression = mListTable.dot(DOM_ID);
                } else {
                    sortExpression = sortColNameList;
                }

                // TODO: Rebuild with state preserved is SLOWEST option. Need a better way to preserve state.
                String insSql = mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_LEVEL, DOM_ROOT_KEY, DOM_VISIBLE, DOM_EXPANDED) +
                        " SELECT " + mListTable.dot(DOM_ID) + "," + mListTable.dot(DOM_LEVEL) + "," + mListTable.dot(DOM_ROOT_KEY) +
                        " ,\n	Case When " + DOM_LEVEL + " = 1 Then 1 \n" +
                        "	When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0\n	Else 1 end,\n " +
                        "	Case When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0 Else 1 end\n" +
                        " FROM " + mListTable.ref() + "\n	LEFT OUTER JOIN " + TBL_BOOK_LIST_NODE_SETTINGS.ref() +
                        "\n		ON " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " = " + mListTable.dot(DOM_ROOT_KEY) +
                        "\n			AND " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROW_KIND) + " = " + mStyle.getGroupAt(0).kind +
                        "\n	ORDER BY " + sortExpression;
                // Always save the state-preserving navigator for rebuilds
                SynchronizedStatement navStmt = mStatements.add("InsNav", insSql);
                mLevelBuildStmts.add(navStmt);

                // On first-time builds, get the pref-based list
                switch (preferredState) {
                    case BooklistPreferencesActivity.BOOK_LIST_ALWAYS_COLLAPSED: {
                        String sql = mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_LEVEL, DOM_ROOT_KEY, DOM_VISIBLE, DOM_EXPANDED) +
                                " SELECT " + mListTable.dot(DOM_ID) + "," + mListTable.dot(DOM_LEVEL) + "," + mListTable.dot(DOM_ROOT_KEY) +
                                " ,\n	Case When " + DOM_LEVEL + " = 1 Then 1 Else 0 End, 0\n" +
                                " FROM " + mListTable.ref() +
                                "\n	ORDER BY " + sortExpression;
                        mSyncedDb.execSQL(sql);
                        break;
                    }
                    case BooklistPreferencesActivity.BOOK_LIST_ALWAYS_EXPANDED: {
                        String sql = mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_LEVEL, DOM_ROOT_KEY, DOM_VISIBLE, DOM_EXPANDED) +
                                " SELECT " + mListTable.dot(DOM_ID) + "," + mListTable.dot(DOM_LEVEL) + "," + mListTable.dot(DOM_ROOT_KEY) +
                                " , 1, 1 \n" +
                                " FROM " + mListTable.ref() +
                                "\n	ORDER BY " + sortExpression;
                        mSyncedDb.execSQL(sql);
                        break;
                    }
                    default:
                        // Use already-defined SQL
                        navStmt.execute();
                        break;
                }

                @SuppressWarnings("UnusedAssignment")
                long t4 = System.currentTimeMillis();
                // Create index on nav table
                {
                    String sql = "CREATE INDEX " + mNavTable + "_IX1" + " ON " + mNavTable + "(" + DOM_LEVEL + "," + DOM_EXPANDED + "," + DOM_ROOT_KEY + ")";
                    SynchronizedStatement ixStmt = mStatements.add("navIx1", sql);
                    mLevelBuildStmts.add(ixStmt);
                    ixStmt.execute();
                }

                @SuppressWarnings("UnusedAssignment")
                long t4a = System.currentTimeMillis();
                {
                    // Essential for main query! If not present, will make getCount() take ages because main query is a cross with no index.
                    String sql = "CREATE UNIQUE INDEX " + mNavTable + "_IX2" + " ON " + mNavTable + "(" + DOM_REAL_ROW_ID + ")";
                    SynchronizedStatement ixStmt = mStatements.add("navIx2", sql);
                    mLevelBuildStmts.add(ixStmt);
                    ixStmt.execute();
                }

                @SuppressWarnings("UnusedAssignment")
                long t4b = System.currentTimeMillis();
                mSyncedDb.execSQL("analyze " + mNavTable);
                @SuppressWarnings("UnusedAssignment")
                long t4c = System.currentTimeMillis();
                @SuppressWarnings("UnusedAssignment")
                long t8 = System.currentTimeMillis();
                //stmt = makeStatement(ix1Sql);
                //mLevelBuildStmts.add(stmt);
                //stmt.execute();
                @SuppressWarnings("UnusedAssignment")
                long t9 = System.currentTimeMillis();
                //mSyncedDb.execSQL(ix2Sql);
                @SuppressWarnings("UnusedAssignment")
                long t10 = System.currentTimeMillis();
                //mSyncedDb.execSQL("analyze " + mTableName);

                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    Logger.info("T0a: " + (t0a - t0));
                    Logger.info("T0b: " + (t0b - t0a));
                    Logger.info("T0c: " + (t0c - t0b));
                    Logger.info("T0d: " + (t0d - t0c));
                    Logger.info("T0e: " + (t0e - t0d));
                    Logger.info("T1: " + (t1 - t0));
                    Logger.info("T1a: " + (t1a - t1));
                    Logger.info("T1b: " + (t1b - t1a));
                    Logger.info("T1c: " + (t2 - t1b));
                    Logger.info("T2a[0]: " + (t2a[0] - t2));
                    for (int i = 1; i < mStyle.size(); i++) {
                        Logger.info("T2a[" + i + "]: " + (t2a[i] - t2a[i - 1]));
                    }
                    Logger.info("T3: " + (t3 - t2a[mStyle.size() - 1]));
                    Logger.info("T3a: " + (t3a - t3));
                    Logger.info("T3b: " + (t3b - t3a));
                    Logger.info("T4: " + (t4 - t3b));
                    Logger.info("T4a: " + (t4a - t4));
                    Logger.info("T4b: " + (t4b - t4a));
                    Logger.info("T4c: " + (t4c - t4b));
                    //Logger.info("T5: " + (t5-t4));
                    //Logger.info("T6: " + (t6-t5));
                    //Logger.info("T7: " + (t7-t6));
                    Logger.info("T8: " + (t8 - t4c));
                    Logger.info("T9: " + (t9 - t8));
                    Logger.info("T10: " + (t10 - t9));
                    Logger.info("T11: " + (System.currentTimeMillis() - t10));
                }
                mSyncedDb.setTransactionSuccessful();

                mSummary = summary;

                //if (markId > 0)
                //	ensureBookVisible(markId);

                // Get the final result
                //return getList();
                //sql = "select * from " + mTableName + " Order by " + mSortColumnList;

                //return (BooklistCursor) mSyncedDb.rawQueryWithFactory(mBooklistCursorFactory, sql, EMPTY_STRING_ARRAY, "");

            } finally {
                mSyncedDb.endTransaction(txLock);
                //mSyncedDb.execSQL("PRAGMA synchronous = FULL");

            }
        } finally {
            Tracker.handleEvent(this, "build-" + getId(), Tracker.States.Exit);
        }
    }

    @NonNull
    private StringBuilder build_whereClause(@NonNull final String bookshelf,
                                            @NonNull final String authorWhere,
                                            @NonNull final String bookWhere,
                                            @NonNull final String loaned_to,
                                            @NonNull final String seriesName,
                                            @NonNull final String searchText,
                                            final boolean hasGroupBOOKSHELF) {
        StringBuilder where = new StringBuilder();

        if (!bookshelf.isEmpty()) {
            if (where.length() != 0) {
                where.append(" AND ");
            }
            if (hasGroupBOOKSHELF) {
                where.append("Exists(SELECT NULL FROM ")
                        .append(TBL_BOOK_BOOKSHELF)
                        .append(" z1 JOIN ").append(TBL_BOOKSHELF)
                        .append(" z2 ON (z2.").append(DOM_ID).append(" = z1.").append(DOM_BOOKSHELF_ID).append(")")
                        .append(" WHERE z2.")
                        .append(DOM_BOOKSHELF).append(" = '").append(CatalogueDBAdapter.encodeString(bookshelf)).append("'")
                        .append(" AND z1.").append(DOM_BOOK_ID).append(" = ").append(TBL_BOOKS.dot(DOM_ID)).append(")");
            } else {
                where.append("(").append(TBL_BOOKSHELF.dot(DOM_BOOKSHELF)).append(" = '").append(CatalogueDBAdapter.encodeString(bookshelf)).append("')");
            }
        }
        if (!authorWhere.isEmpty()) {
            if (where.length() != 0) {
                where.append(" AND ");
            }
            where.append("(").append(authorWhere).append(")");
        }

        if (!bookWhere.isEmpty()) {
            if (where.length() != 0) {
                where.append(" AND ");
            }
            where.append("(").append(bookWhere).append(")");
        }

        if (!loaned_to.isEmpty()) {
            if (where.length() != 0) {
                where.append(" AND ");
            }
            where.append("Exists(SELECT NULL FROM ").append(TBL_LOAN.ref())
                    .append(" WHERE ").append(TBL_LOAN.dot(DOM_LOANED_TO)).append(" = '").append(CatalogueDBAdapter.encodeString(loaned_to)).append("'")
                    .append(" AND ").append(TBL_LOAN.fkMatch(TBL_BOOKS)).append(")");
            // .and()    .op(TBL_LOAN.dot(DOM_BOOK_ID), "=", TBL_BOOKS.dot(DOM_ID)) + ")";
        }
        if (!seriesName.isEmpty()) {
            if (where.length() != 0) {
                where.append(" AND ");
            }
            where.append("(").append(TBL_SERIES.dot(DOM_SERIES_NAME)).append(" = '").append(CatalogueDBAdapter.encodeString(seriesName)).append("')");
        }
        if (!searchText.isEmpty()) {
            if (where.length() != 0) {
                where.append(" AND ");
            }
            where.append("(").append(TBL_BOOKS.dot(DOM_ID)).append(" in (SELECT ").append(DOM_DOCID).append(" FROM ").append(TBL_BOOKS_FTS)
                    .append(" WHERE ").append(TBL_BOOKS_FTS).append(" match '")
                    .append(CatalogueDBAdapter.encodeString(CatalogueDBAdapter.cleanupFtsCriterion(searchText))).append("'))");
        }


        build_AddFilters(where);

        return where;
    }

    /**
     * Add all possible/enabled Filters
     */
    private void build_AddFilters(@NonNull final StringBuilder /* in/out */ where) {
        // Add support for book filter: READ
        {
            String extra = null;
            switch (mStyle.getReadFilter()) {
                case BooklistStyle.FILTER_YES:
                    extra = TBL_BOOKS.dot(DOM_BOOK_READ) + " = 1\n";
                    break;
                case BooklistStyle.FILTER_NO:
                    extra = TBL_BOOKS.dot(DOM_BOOK_READ) + " = 0\n";
                    break;
                default:
                    break;
            }
            if (extra != null) {
                if (where.length() != 0) {
                    where.append(" AND ");
                }
                where.append(" ").append(extra);
            }
        }
        // Add support for book filter: SIGNED
        {
            String extra = null;
            switch (mStyle.getSignedFilter()) {
                case BooklistStyle.FILTER_YES:
                    extra = TBL_BOOKS.dot(DOM_BOOK_SIGNED) + " = 1\n";
                    break;
                case BooklistStyle.FILTER_NO:
                    extra = TBL_BOOKS.dot(DOM_BOOK_SIGNED) + " = 0\n";
                    break;
                default:
                    break;
            }
            if (extra != null) {
                if (where.length() != 0) {
                    where.append(" AND ");
                }
                where.append(" ").append(extra);
            }
        }
        // Add support for book filter: ANTHOLOGY
        {
            String extra = null;
            switch (mStyle.getAnthologyFilter()) {
                case BooklistStyle.FILTER_YES:
                    extra = TBL_BOOKS.dot(DOM_BOOK_ANTHOLOGY_BITMASK) + " > 0\n";
                    break;
                case BooklistStyle.FILTER_NO:
                    extra = TBL_BOOKS.dot(DOM_BOOK_ANTHOLOGY_BITMASK) + " = 0\n";
                    break;
                default:
                    break;
            }
            if (extra != null) {
                if (where.length() != 0) {
                    where.append(" AND ");
                }
                where.append(" ").append(extra);
            }
        }
        // Add support for book filter: LOANED
        {
            String extra = null;
            switch (mStyle.getLoanedFilter()) {
                case BooklistStyle.FILTER_YES:
                    extra = TBL_BOOKS.dot(DOM_LOANED_TO) + " = 1\n";
                    break;
                case BooklistStyle.FILTER_NO:
                    extra = TBL_BOOKS.dot(DOM_LOANED_TO) + " = 0\n";
                    break;
                default:
                    break;
            }
            if (extra != null) {
                if (where.length() != 0) {
                    where.append(" AND ");
                }
                where.append(" ").append(extra);
            }
        }
    }

    /**
     * Clear the list of expanded nodes in the current view
     */
    private void deleteListNodeSettings() {
        SyncLock l = null;

        try {
            if (!mSyncedDb.inTransaction())
                l = mSyncedDb.beginTransaction(true);

            int kind = mStyle.getGroupAt(0).kind;
            if (mDeleteListNodeSettingsStmt == null) {
                mDeleteListNodeSettingsStmt = mStatements.add("mDeleteListNodeSettingsStmt",
                        "DELETE FROM " + TBL_BOOK_LIST_NODE_SETTINGS + " WHERE " + DOM_ROW_KIND + "=?");
            }
            mDeleteListNodeSettingsStmt.bindLong(1, kind);
            mDeleteListNodeSettingsStmt.execute();
            if (l != null)
                mSyncedDb.setTransactionSuccessful();
        } finally {
            if (l != null)
                mSyncedDb.endTransaction(l);
        }
    }

    /**
     * Build a collection of triggers on the list table designed to fill in the summary/header records
     * as the data records are added in sorted order.
     * <p>
     * This approach means to allow DESCENDING sort orders.
     */
    @NonNull
    private String makeTriggers(@NonNull final SummaryBuilder summary, final boolean flatTriggers) {
        if (flatTriggers) {
            // Flat triggers are compatible with Android 1.6+ but slower
            return makeSingleTrigger(summary);
        } else {
            // Nested triggers are compatible with Android 2.2+ and fast
            // (or at least relatively fast when there are a 'reasonable'
            // number of headings to be inserted).
            makeNestedTriggers(summary);
            return mListTable.getName();
        }
    }

    /**
     * Build a collection of triggers on the list table designed to fill in the summary/header records
     * as the data records are added in sorted order.
     * <p>
     * This approach is allows DESCENDING sort orders but is slightly slower than the old-style
     * manually generated lists.
     */
    @NonNull
    private String makeSingleTrigger(@NonNull final SummaryBuilder summary) {
        // Name of a table to store the snapshot of the most recent/current row headings
        final String currTblName = mListTable + "_curr";
        final String viewTblName = mListTable + "_view";

        /*
         * Create a trigger to forward all row details to real table
         */

        // Name of the trigger to create.
        final String tgForwardName = mListTable + "_TG_AAA";
        // Build an INSERT statement to insert the entire row in the real table
        StringBuilder fullInsert = new StringBuilder("INSERT INTO " + mListTable + "(");
        {
            StringBuilder fullValues = new StringBuilder("Values (");
            boolean firstCol = true;
            for (DomainDefinition d : mListTable.getDomains()) {
                if (!d.equals(DOM_ID)) {
                    if (firstCol)
                        firstCol = false;
                    else {
                        fullInsert.append(", ");
                        fullValues.append(", ");
                    }
                    fullInsert.append(d);
                    fullValues.append("new.").append(d);
                }
            }
            fullInsert.append(") ").append(fullValues).append(");");
        }

        // We just create one big trigger
        StringBuilder trigger = new StringBuilder("CREATE TRIGGER " + tgForwardName + " instead of insert ON " + viewTblName + " for each row \n" +
                "	Begin\n");

        // List of cols we sort by
        StringBuilder sortedCols = new StringBuilder();
        // SQL statement to update the 'current' table
        StringBuilder currInsertSql = new StringBuilder();
        // List of domain names for sorting
        Set<String> sortedDomainNames = new HashSet<>();
        // Build the 'current' header table definition and the sort column list
        for (SortedDomainInfo i : summary.getSortedColumns()) {
            if (!sortedDomainNames.contains(i.domain.name)) {
                sortedDomainNames.add(i.domain.name);
                if (sortedCols.length() > 0) {
                    sortedCols.append(", ");
                    currInsertSql.append(", ");
                }
                sortedCols.append(i.domain.name);
                currInsertSql.append("new.").append(i.domain.name);
            }
        }

        //
        // Create a temp table to store the most recent header details from the last row.
        // We use this in determining what needs to be inserted as header records for
        // any given row.
        //
        // This is just a simple technique to provide persistent context to the trigger.
        //
        mSyncedDb.execSQL("CREATE Temp TABLE " + currTblName + " (" + sortedCols + ")");
        mSyncedDb.execSQL("CREATE Temp View " + viewTblName + " AS SELECT * FROM " + mListTable);

        //mSyncedDb.execSQL("Create Unique Index " + mListTable + "_IX_TG1 on " + mListTable + "(" + DOM_LEVEL + ", " + sortedCols + ", " + DOM_BOOK_ID + ")");

        // For each grouping, starting with the lowest, build a trigger to update the next level up as necessary
        for (int i = 0; i < mStyle.size(); i++) {
            // Get the group
            final BooklistGroup booklistGroup = mStyle.getGroupAt(i);
            // Get the level number for this group
            final int levelId = i + 1;
            // Create an INSERT statement for the next level up
            StringBuilder insertSql = new StringBuilder("INSERT INTO " + mListTable + "( " + DOM_LEVEL + "," + DOM_ROW_KIND + ", " + DOM_ROOT_KEY + "\n");

            // Create the VALUES statement for the next level up
            StringBuilder valuesSql = new StringBuilder(" SELECT " + levelId + ", " + booklistGroup.kind + ", " + "new." + DOM_ROOT_KEY + "\n");
            // Create the conditional to detect if next level up is already defined (by checking the 'current' record/table)
            StringBuilder conditionSql = new StringBuilder();// "booklistGroup." + DOM_LEVEL + " = " + levelId + "\n";
            // Update the statement components
            for (DomainDefinition d : booklistGroup.groupDomains) {
                insertSql.append(", ").append(d);
                valuesSql.append(", new.").append(d);
                // Only update the 'condition' part if it is part of the SORT list
                if (sortedDomainNames.contains(d.name)) {
                    if (conditionSql.length() > 0)
                        conditionSql.append("	AND ");
                    conditionSql.append("Coalesce(l.").append(d).append(", '') = Coalesce(new.").append(d).append(",'') ").append(DatabaseHelper.COLLATION).append("\n");
                }
            }
            //insertSql += ")\n	Select " + valuesSql + " Where not exists(Select 1 From " + mListTable + " l where " + conditionSql + ")";
            //tgLines[i] = insertSql;

            insertSql.append(")\n").append(valuesSql).append(" WHERE not Exists(SELECT 1 FROM ").append(currTblName).append(" l WHERE ").append(conditionSql).append(")\n");
            trigger.append("		").append(insertSql).append(";\n");
        }

        // Finalize the main trigger; insert the full row and update the 'current' header
        trigger.append("		").append(fullInsert).append("\n")
                .append("		DELETE FROM ").append(currTblName).append(";\n")
                .append("		INSERT INTO ").append(currTblName).append(" values (").append(currInsertSql).append(");\n")
                .append("	End");

        {
            mSyncedDb.execSQL("DROP TRIGGER if exists " + tgForwardName);
            SynchronizedStatement stmt = mStatements.add(tgForwardName, trigger.toString());
            mLevelBuildStmts.add(stmt);
            stmt.execute();
        }

        return viewTblName;
    }

    /**
     * Build a collection of triggers on the list table designed to fill in the summary/header records
     * as the data records are added in sorted order.
     *
     * This approach is both a performance improvement and a means to allow DESCENDING sort orders.
     *
     * It is the preferred option in Android 2.2+, but there is a chance that some vendor implemented
     * a broken or old SQLite version.
     */
    private void makeNestedTriggers(@NonNull final SummaryBuilder summary) {
        // Name of a table to store the snapshot of the most recent/current row headings
        final String currTblName = mListTable + "_curr";
        // List of cols we sort by
        StringBuilder sortedCols = new StringBuilder();
        // SQL statement to update the 'current' table
        StringBuilder currInsertSql = new StringBuilder();
        // List of domain names for sorting
        Set<String> sortedDomainNames = new HashSet<>();
        // Build the 'current' header table definition and the sort column list
        for (SortedDomainInfo i : summary.getSortedColumns()) {
            if (!sortedDomainNames.contains(i.domain.name)) {
                sortedDomainNames.add(i.domain.name);
                if (sortedCols.length() > 0) {
                    sortedCols.append(", ");
                    currInsertSql.append(", ");
                }
                sortedCols.append(i.domain.name);
                currInsertSql.append("new.").append(i.domain.name);
            }
        }

        //
        // Create a temp table to store the most recent header details from the last row.
        // We use this in determining what needs to be inserted as header records for
        // any given row.
        //
        // This is just a simple technique to provide persistent context to the trigger.
        //
        mSyncedDb.execSQL("CREATE TEMP TABLE " + currTblName + " (" + sortedCols + ")");

        // For each grouping, starting with the lowest, build a trigger to update the next level up as necessary
        for (int i = mStyle.size() - 1; i >= 0; i--) {
            // Get the group
            final BooklistGroup l = mStyle.getGroupAt(i);
            // Get the level number for this group
            final int levelId = i + 1;
            // Create an INSERT statement for the next level up
            StringBuilder insertSql = new StringBuilder("INSERT INTO " + mListTable + "( " + DOM_LEVEL + "," + DOM_ROW_KIND + ", " + DOM_ROOT_KEY + "\n");

            // Create the VALUES statement for the next level up
            StringBuilder valuesSql = new StringBuilder("Values (" + levelId + ", " + l.kind + ", " + "new." + DOM_ROOT_KEY + "\n");
            // Create the conditional to detect if next level up is already defined (by checking the 'current' record/table)
            StringBuilder conditionSql = new StringBuilder();// "l." + DOM_LEVEL + " = " + levelId + "\n";
            // Update the statement components
            for (DomainDefinition d : l.groupDomains) {
                insertSql.append(", ").append(d);
                valuesSql.append(", new.").append(d);
                // Only update the 'condition' part if it is part of the SORT list
                if (sortedDomainNames.contains(d.name)) {
                    if (conditionSql.length() > 0)
                        conditionSql.append("	AND ");
                    conditionSql.append("Coalesce(l.").append(d).append(",'') = Coalesce(new.").append(d).append(",'') ").append(DatabaseHelper.COLLATION).append("\n");
                }
            }

            insertSql.append(")\n").append(valuesSql).append(")");
            String tgName = "header_A_tgL" + i;
            // Drop trigger if necessary
            mSyncedDb.execSQL("DROP TRIGGER if exists " + tgName);

            // Create the trigger
            String tgSql = "CREATE TEMP TRIGGER " + tgName + " before INSERT ON " + mListTable + " for each row when new.level = " + (levelId + 1) +
                    " AND NOT exists(SELECT 1 FROM " + currTblName + " l WHERE " + conditionSql + ")\n" +
                    "	Begin\n" +
                    "		" + insertSql + ";\n" +
                    "	End";
            SynchronizedStatement stmt = mStatements.add("TG " + tgName, tgSql);
            mLevelBuildStmts.add(stmt);
            stmt.execute();
        }

        // Create a trigger to maintain the 'current' value -- just delete and insert
        String currTgName = mListTable + "_TG_ZZZ";
        mSyncedDb.execSQL("DROP TRIGGER if exists " + currTgName);
        String tgSql = "CREATE TEMP TRIGGER " + currTgName + " after INSERT ON " + mListTable + " for each row when new.level = " + mStyle.size() +
                //" and not exists(Select 1 From " + currTblName + " l where " + conditionSql + ")\n" +
                "	Begin\n" +
                "		DELETE FROM " + currTblName + ";\n" +
                "		INSERT INTO " + currTblName + " values (" + currInsertSql + ");\n" +
                "	End";

        SynchronizedStatement stmt = mStatements.add(currTgName, tgSql);
        mLevelBuildStmts.add(stmt);
        stmt.execute();
    }

    /**
     * Save the currently expanded top level nodes, and the top level group kind, to the database
     * so that the next time this view is opened, the user will see the same opened/closed nodes.
     */
    private void saveListNodeSettings() {
        SyncLock l = null;
        try {
            if (!mSyncedDb.inTransaction())
                l = mSyncedDb.beginTransaction(true);

            deleteListNodeSettings();

            if (mSaveListNodeSettingsStmt == null) {
                String sql = TBL_BOOK_LIST_NODE_SETTINGS.getInsert(DOM_ROW_KIND, DOM_ROOT_KEY) +
                        " SELECT DISTINCT ?, " + DOM_ROOT_KEY + " FROM " + mNavTable + " WHERE expanded = 1 AND level = 1";
                mSaveListNodeSettingsStmt = mStatements.add("mSaveListNodeSettingsStmt", sql);
            }
            int kind = mStyle.getGroupAt(0).kind;

            mSaveListNodeSettingsStmt.bindLong(1, kind);
            mSaveListNodeSettingsStmt.execute();
            if (l != null)
                mSyncedDb.setTransactionSuccessful();
        } finally {
            if (l != null)
                mSyncedDb.endTransaction(l);
        }
    }

    /**
     * Clear the list of expanded nodes in the current view
     */
    private void deleteListNodeSetting(final long rowId) {
        SyncLock l = null;

        try {
            if (!mSyncedDb.inTransaction())
                l = mSyncedDb.beginTransaction(true);

            int kind = mStyle.getGroupAt(0).kind;
            if (mDeleteListNodeSettingStmt == null) {
                mDeleteListNodeSettingStmt = mStatements.add("mDeleteSettingsStmt",
                        "DELETE FROM " + TBL_BOOK_LIST_NODE_SETTINGS + " WHERE " + DOM_ROW_KIND + "=? AND " +
                                DOM_ROOT_KEY + " IN (SELECT DISTINCT " + DOM_ROOT_KEY + " FROM " + mNavTable + " WHERE " + DOM_ID + "=?)");
            }
            mDeleteListNodeSettingStmt.bindLong(1, kind);
            mDeleteListNodeSettingStmt.bindLong(2, rowId);
            mDeleteListNodeSettingStmt.execute();
        } finally {
            if (l != null)
                mSyncedDb.endTransaction(l);
        }
    }

    /**
     * Save the specified node state.
     */
    private void saveListNodeSetting(final long rowId) {
        SyncLock l = null;
        try {
            if (!mSyncedDb.inTransaction())
                l = mSyncedDb.beginTransaction(true);

            deleteListNodeSetting(rowId);

            if (mSaveListNodeSettingStmt == null) {
                String sql = TBL_BOOK_LIST_NODE_SETTINGS.getInsert(DOM_ROW_KIND, DOM_ROOT_KEY) +
                        " SELECT ?, " + DOM_ROOT_KEY + " FROM " + mNavTable + " WHERE expanded=1 AND level=1 AND " + DOM_ID + "=?";
                mSaveListNodeSettingStmt = mStatements.add("mSaveListNodeSettingStmt", sql);
            }
            int kind = mStyle.getGroupAt(0).kind;

            mSaveListNodeSettingStmt.bindLong(1, kind);
            mSaveListNodeSettingStmt.bindLong(2, rowId);
            mSaveListNodeSettingStmt.execute();
            mSyncedDb.setTransactionSuccessful();
        } finally {
            if (l != null)
                mSyncedDb.endTransaction(l);
        }
    }


//	private void pseudoCursor(String sql, int pos) {
//		long tc0 = System.currentTimeMillis();
//		final BooklistCursor cFoo = (BooklistCursor) mSyncedDb.rawQueryWithFactory(mBooklistCursorFactory, sql + " Limit 80 offset " + pos, EMPTY_STRING_ARRAY, "");
//		long tc1 = System.currentTimeMillis();
//		long cCnt = cFoo.getCount();
//		long tc2 = System.currentTimeMillis();
//		cFoo.close();
//
//		Logger.info("Limit cursor @" + pos + " create in " + (tc1 - tc0) + "ms, count (" + cCnt + ") in " + (tc2-tc1) + "ms");
//	}

//	private void pseudoCount(TableDefinition table, String condition) {
//		String foo = "Select count(*) from " + table + (condition == null ? "" : condition);
//		pseudoCount(table.getName(), foo);
//	}

    /**
     * Get all positions at which the specified book appears.
     *
     * @return Array of row details, including absolute positions and visibility. Null if not present
     */
    @Nullable
    public ArrayList<BookRowInfo> getBookAbsolutePositions(final long bookId) {
        String sql = "SELECT " + mNavTable.dot(DOM_ID) + ", " + mNavTable.dot(DOM_VISIBLE) + " FROM " + mListTable + " bl "
                + mListTable.join(mNavTable) + " WHERE " + mListTable.dot(DOM_BOOK_ID) + "=" + bookId;

        try (Cursor c = mSyncedDb.rawQuery(sql, new String[]{})) {
            ArrayList<BookRowInfo> rows = new ArrayList<>();
            if (c.moveToFirst()) {
                do {
                    int absPos = c.getInt(0) - 1;
                    rows.add(new BookRowInfo(absPos, getPosition(absPos), c.getInt(1)));
                } while (c.moveToNext());
                return rows;
            } else {
                return null;
            }
        }
    }

    /**
     * Utility routine to return a list of column names that will be in the list
     * for cursor implementations.
     */
    @NonNull
    String[] getListColumnNames() {
        // Get the domains
        List<DomainDefinition> domains = mListTable.getDomains();
        // Make the array and allow for ABSOLUTE_POSITION
        String[] names = new String[domains.size() + 1];
        // Copy domains
        for (int i = 0; i < domains.size(); i++)
            names[i] = domains.get(i).name;
        // Add ABSOLUTE_POSITION
        names[domains.size()] = DOM_ABSOLUTE_POSITION.name;
        return names;
    }

    /**
     * Return a list cursor starting at a given offset, using a given limit.
     */
    @NonNull
    BooklistCursor getOffsetCursor(final int position, @SuppressWarnings("SameParameterValue") final int size) {
        // Get the domains
        StringBuilder domains = new StringBuilder();
        final String prefix = mListTable.getAlias() + ".";
        for (DomainDefinition d : mListTable.getDomains()) {
            domains.append(prefix);
            domains.append(d.name);
            domains.append(" AS ");
            domains.append(d.name);
            domains.append(", ");
        }

        // Build the SQL, adding ABS POS.
        final String sql = "SELECT " + domains + " (" + mNavTable.dot(DOM_ID) + " - 1) AS " + DOM_ABSOLUTE_POSITION +
                " FROM " + mListTable.ref() + mListTable.join(mNavTable) +
                " WHERE " + mNavTable.dot(DOM_VISIBLE) + " = 1 ORDER BY " + mNavTable.dot(DOM_ID) +
                " LIMIT " + size + " Offset " + position;

        // Get and return the cursor
        return (BooklistCursor) mSyncedDb.rawQueryWithFactory(mBooklistCursorFactory, sql, new String[]{}, "");
    }

    /**
     * Return a BooklistPseudoCursor instead of a real cursor.
     */
    @NonNull
    public BooklistPseudoCursor getList() {
        return new BooklistPseudoCursor(this);
    }

    /**
     * All pseudo list cursors work with the static data in the temp table. Get the
     * logical count of rows using a simple query rather than scanning the entire result set.
     */
    int getPseudoCount() {
        return pseudoCount("NavTable",
                "SELECT COUNT(*) FROM " + mNavTable + " WHERE " + DOM_VISIBLE + "=1");
    }

    /**
     * Get the number of book records in the list
     */
    int getBookCount() {
        return pseudoCount("ListTableBooks",
                "SELECT COUNT(*) FROM " + mListTable + " WHERE " + DOM_LEVEL + "=" + (mStyle.size() + 1));
    }

    /**
     * Get the number of unique book records in the list
     */
    int getUniqueBookCount() {
        return pseudoCount("ListTableUniqueBooks",
                "SELECT COUNT(DISTINCT " + DOM_BOOK_ID + ") FROM " + mListTable + " WHERE " + DOM_LEVEL + "=" + (mStyle.size() + 1));
    }

    /**
     * Utility routine to perform a single count query.
     */
    private int pseudoCount(@NonNull final String name, @NonNull final String countSql) {
        @SuppressWarnings("UnusedAssignment")
        long tc0 = System.currentTimeMillis();
        int cnt;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(countSql)) {
            cnt = (int) stmt.count();
        }

        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            Logger.info("Pseudo-count (" + name + ") = " + cnt + " completed in " + (System.currentTimeMillis() - tc0) + "ms");
        }
        return cnt;
    }

    /**
     * Using a 1-based level index, retrieve the domain that is displayed in the summary for the specified level.
     *
     * @param level 1-based level to check
     *
     * @return Name of the display field for this level
     */
    public DomainDefinition getDisplayDomain(final int level) {
        return mStyle.getGroupAt(level - 1).displayDomain;
    }

    /**
     * @return the number of levels in the list, including the 'base' books level
     */
    int numLevels() {
        return mStyle.size() + 1;
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
        if (mGetPositionCheckVisibleStmt == null) {
            mGetPositionCheckVisibleStmt = mStatements.add("mGetPositionCheckVisibleStmt",
                    "SELECT visible FROM " + mNavTable + " WHERE " + DOM_ID + "=?");
        }
        // Check the absolute position is visible
        final long rowId = absolutePosition + 1;
        mGetPositionCheckVisibleStmt.bindLong(1, rowId);
        boolean isVisible = (1 == mGetPositionCheckVisibleStmt.simpleQueryForLongOrZero());

        if (mGetPositionStmt == null) {
            mGetPositionStmt = mStatements.add("mGetPositionStmt",
                    "SELECT COUNT(*) FROM " + mNavTable + " WHERE visible=1 AND " + DOM_ID + " < ?");
        }
        // Count the number of *visible* rows *before* the specified one.
        mGetPositionStmt.bindLong(1, rowId);
        int newPos = (int) mGetPositionStmt.count();
        // If specified row is visible, then the position is the count, otherwise, count -1 (ie. the
        // previous visible row).
        if (isVisible) {
            return newPos;
        } else {
            return newPos > 0 ? newPos - 1 : 0;
        }
    }

    /**
     * Find the visible root node for a given absolute position and ensure it is visible.
     */
    public void ensureAbsolutePositionVisible(final long absPos) {
        // If <0 then no previous node.
        if (absPos < 0)
            return;

        final long rowId = absPos + 1;

        if (mGetNodeRootStmt == null) {
            String sql = "SELECT " + DOM_ID + "||'/'||" + DOM_EXPANDED + " FROM " + mNavTable +
                    " WHERE " + DOM_LEVEL + " = 1 AND " + DOM_ID + " <= ? ORDER BY " + DOM_ID + " DESC LIMIT 1";
            mGetNodeRootStmt = mStatements.add("mGetNodeRootStmt", sql);
        }

        // Get the root node, and expanded flag
        mGetNodeRootStmt.bindLong(1, rowId);
        try {
            String info[] = mGetNodeRootStmt.simpleQueryForString().split("/");
            long rootId = Long.parseLong(info[0]);
            long isExpanded = Long.parseLong(info[1]);
            // If root node is not the node we are checking, and root node is not expanded, expand it.
            if (rootId != rowId && isExpanded == 0) {
                toggleExpandNode(rootId - 1);
            }
        } catch (SQLiteDoneException ignore) {
            // query returned zero rows
        }
    }

    /**
     * Build statements used by expand/collapse code.
     */
    private void buildExpandNodeStatements() {
        if (mGetNodeLevelStmt == null) {
            mGetNodeLevelStmt = mStatements.add("mGetNodeLevelStmt",
                    "SELECT " + DOM_LEVEL + "||'/'||" + DOM_EXPANDED +
                            " FROM " + mNavTable.ref() + " WHERE " + mNavTable.dot(DOM_ID) + "=?");
        }
        if (mGetNextAtSameLevelStmt == null) {
            mGetNextAtSameLevelStmt = mStatements.add("mGetNextAtSameLevelStmt",
                    "SELECT Coalesce( max(" + DOM_ID + "), -1) FROM " +
                            "(SELECT " + DOM_ID + " FROM " + mNavTable.ref() +
                            " WHERE " + mNavTable.dot(DOM_ID) + " > ? AND " + mNavTable.dot(DOM_LEVEL) + "=?" +
                            " ORDER BY " + DOM_ID + " LIMIT 1" +
                            ")" +
                            " zzz");
        }
        if (mShowStmt == null) {
            mShowStmt = mStatements.add("mShowStmt",
                    "UPDATE " + mNavTable +
                            " SET " + DOM_VISIBLE + " = ?," + DOM_EXPANDED + "=?" +
                            " WHERE " + DOM_ID + " > ? AND " + DOM_LEVEL + " > ? AND " + DOM_ID + " < ?");
        }
        if (mExpandStmt == null) {
            mExpandStmt = mStatements.add("mExpandStmt",
                    "UPDATE " + mNavTable + " SET " + DOM_EXPANDED + " = ? WHERE " + DOM_ID + "=?");
        }
    }

    /**
     * For EXPAND: Mark all rows as visible/expanded
     * For COLLAPSE: Mark all non-root rows as invisible/unexpanded and mark all root nodes as visible/unexpanded.
     */
    public void expandAll(final boolean expand) {
        @SuppressWarnings("UnusedAssignment")
        long t0 = System.currentTimeMillis();
        if (expand) {
            String sql = "UPDATE " + mNavTable + " SET expanded=1, visible=1";
            mSyncedDb.execSQL(sql);
            saveListNodeSettings();
        } else {
            String sql = "UPDATE " + mNavTable + " SET expanded=0, visible=0 WHERE level > 1";
            mSyncedDb.execSQL(sql);
            sql = "UPDATE " + mNavTable + " SET expanded=0 WHERE level=1";
            mSyncedDb.execSQL(sql);
            deleteListNodeSettings();
        }

        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            Logger.info("Expand All: " + (System.currentTimeMillis() - t0));
        }
    }

    /**
     * Toggle the expand/collapse status of the node as the specified absolute position
     */
    public void toggleExpandNode(final long absPos) {
        // This seems to get called sometimes after the database is closed...
        // RELEASE: remove statements as members, and look them up in mStatements via static keys

        buildExpandNodeStatements();

        // row position starts at 0, id's start at 1...
        final long rowId = absPos + 1;

        // Get the details of the passed row position.
        mGetNodeLevelStmt.bindLong(1, rowId);

        String[] info;
        try {
            info = mGetNodeLevelStmt.simpleQueryForString().split("/");
        } catch (SQLiteDoneException ignore) {
            return;
        }
        long level = Long.parseLong(info[0]);
        int isExpanded = (Integer.parseInt(info[1]) == 1) ? 0 : 1;

        // Find the next row at the same level
        mGetNextAtSameLevelStmt.bindLong(1, rowId);
        mGetNextAtSameLevelStmt.bindLong(2, level);
        long next = mGetNextAtSameLevelStmt.simpleQueryForLong();
        if (next < 0) {
            next = Long.MAX_VALUE;
        }
        // Mark intervening nodes as visible/invisible
        mShowStmt.bindLong(1, isExpanded);
        mShowStmt.bindLong(2, isExpanded);
        mShowStmt.bindLong(3, rowId);
        mShowStmt.bindLong(4, level);
        mShowStmt.bindLong(5, next);

        mShowStmt.execute();

        // Mark this node as expanded.
        mExpandStmt.bindLong(1, isExpanded);
        mExpandStmt.bindLong(2, rowId);
        mExpandStmt.execute();

        // Update settings
        saveListNodeSetting(rowId);
    }

    /**
     * @return the style used by this builder.
     */
    @NonNull
    public BooklistStyle getStyle() {
        return mStyle;
    }

    /**
     * General cleanup routine called by both 'close()' and 'finalize()'
     *
     * @param isFinalize set in the finalize[] method call
     */
    private void cleanup(final boolean isFinalize) {
        if (mStatements.size() != 0) {
            if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG && isFinalize) {
                Logger.info("BooklistBuilder Finalizing with active statements (this is not an error): ");
                for (String name : mStatements.getNames()) {
                    Logger.info(name);
                }
            }
            try {
                mStatements.close();
            } catch (Exception e) {
                Logger.error(e);
            }
        }

        if (mNavTable != null) {
            if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG && isFinalize) {
                Logger.info("BooklistBuilder Finalizing wth mNavTable (this is not an error)");
            }
            try {
                mNavTable.close();
                mNavTable.drop(mSyncedDb);
            } catch (Exception e) {
                Logger.error(e);
            }
        }
        if (mListTable != null) {
            if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG && isFinalize) {
                Logger.info("BooklistBuilder Finalizing  with list table (this is not an error)");
            }
            try {
                mListTable.close();
                mListTable.drop(mSyncedDb);
            } catch (Exception e) {
                Logger.error(e);
            }
        }

        if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG) {
            if (!mReferenceDecremented) {
                // Only de-reference once!
                synchronized (mInstanceCount) {
                    mInstanceCount--;
                    Logger.info("BB instances: " + mInstanceCount);
                }
            }
            mReferenceDecremented = true;
        }
    }

    /**
     * Close the builder.
     */
    @Override
    public void close() {
        cleanup(false);
    }

    public void finalize() {
        cleanup(true);
    }

    public static class SortedDomainInfo {
        @NonNull
        final DomainDefinition domain;
        final boolean isDescending;

        SortedDomainInfo(@NonNull final DomainDefinition domain, final boolean isDescending) {
            this.domain = domain;
            this.isDescending = isDescending;
        }
    }

    /**
     * Structure used to store components of the SQL required to build the list.
     * We use this for experimenting with alternate means of construction.
     */
    private static class SqlComponents {
        public String destinationColumns;
        public String select;
        public String insert;
        public String insertSelect;
        public String insertValues;
        public String rootKeyExpression;
        public String join;
        public String where;
    }

    /**
     * Record containing details of the positions of all instances of a single book.
     *
     * @author Philip Warner
     */
    public static class BookRowInfo {
        public final int absolutePosition;
        public final boolean visible;
        public int listPosition;

        BookRowInfo(final int absPos, final int listPos, final int vis) {
            absolutePosition = absPos;
            listPosition = listPos;
            visible = (vis == 1);
        }
    }

    /**
     * Details of extra domain requested by caller before the build() method is called.
     *
     * @author Philip Warner
     */
    private class ExtraDomainDetails {
        /**
         * Domain definition of domain to add
         */
        DomainDefinition domain;
        /**
         * Expression to use in deriving domain value
         */
        String sourceExpression;
        /**
         * Indicates if domain is to be part of the list sort key
         */
        boolean isSorted;
    }

    /**
     * Utility class to accumulate date for the build() method.
     *
     * @author Philip Warner
     */
    private class SummaryBuilder {

        /** Options indicating added domain has no special properties */
        static final int FLAG_NONE = 0;
        /** Options indicating added domain is SORTED */
        static final int FLAG_SORTED = 1;
        /** Options indicating added domain is GROUPED */
        static final int FLAG_GROUPED = 2;
        // Not currently used.
        ///** Options indicating added domain is part of the unique key */
        //static final int FLAG_KEY = 4;
        /**
         * Options indicating added domain should be SORTED in descending order. DO NOT USE FOR GROUPED DATA. See notes below.
         */
        static final int FLAG_SORT_DESCENDING = 8;

        /** Domains required in output table */
        private final ArrayList<DomainDefinition> mDomains = new ArrayList<>();
        /** Source expressions for output domains */
        private final ArrayList<String> mExpressions = new ArrayList<>();
        /** Mapping from Domain to source Expression */
        private final Map<DomainDefinition, String> mExpressionMap = new HashMap<>();

        /** Domains that are GROUPED */
        private final ArrayList<DomainDefinition> mGroups = new ArrayList<>();
        // Not currently used.
        ///** Domains that form part of accumulated unique key */
        //private List<DomainDefinition> mKeys = new ArrayList<DomainDefinition>();
        /**
         * Domains that form part of the sort key. These are typically a reduced set of the GROUP domains since
         * the group domains may contain more than just the key
         */
        private final ArrayList<SortedDomainInfo> mSortedColumns = new ArrayList<>();
        private final Set<DomainDefinition> mSortedColumnsSet = new HashSet<>();

        /**
         * Add a domain and source expression to the summary.
         *
         * @param domain     Domain to add
         * @param expression Source Expression
         * @param flags      Flags indicating attributes of new domain
         */
        void addDomain(@NonNull final DomainDefinition domain, @Nullable final String expression, final int flags) {
            // Add to various collections. We use a map to improve lookups and ArrayLists
            // so we can preserve order. Order preservation makes reading the SQL easier
            // but is unimportant for code correctness.

            // Add to table
            mListTable.addDomain(domain);

            // Domains and Expressions must be synchronized; we should probably use a map.
            // For now, just check if mExpression is null. If it IS null, it means that
            // the domain is just for the lowest level of the hierarchy.
            if (expression != null) {
                mDomains.add(domain);
                mExpressions.add(expression);
                mExpressionMap.put(domain, expression);
            }

            // Based on the flags, add the domain to other lists.
            if ((flags & FLAG_GROUPED) != 0)
                mGroups.add(domain);

            if ((flags & FLAG_SORTED) != 0 && !mSortedColumnsSet.contains(domain)) {
                mSortedColumns.add(new SortedDomainInfo(domain, (flags & FLAG_SORT_DESCENDING) != 0));
                mSortedColumnsSet.add(domain);
            }

            // Not currently used
            //if ((flags & FLAG_KEY) != 0)
            //	mKeys.add(domain);
        }

        /**
         * Return a clone of the CURRENT groups. Since BooklistGroup objects are processed in order, this
         * allows us to get the GROUP-BY fields applicable to the currently processed group, including all
         * outer groups. Hence why it is cloned -- subsequent domains will modify this collection.
         */
        @NonNull
        @SuppressWarnings("unchecked")
        ArrayList<DomainDefinition> cloneGroups() {
            //TOMF: shallow copy, is that enough ? TODO: check calling code
            //      * Returns a shallow copy of this <tt>ArrayList</tt> instance.  (The
            //     * elements themselves are not copied.)
            return (ArrayList<DomainDefinition>) mGroups.clone();
        }

        /**
         * Return the collection of columns used to sort the output.
         */
        @NonNull
        ArrayList<SortedDomainInfo> getSortedColumns() {
            return mSortedColumns;
        }

        /**
         * Drop and recreate the underlying temp table
         */
        void recreateTable() {
            //mListTable.setIsTemporary(true); commented in original code

            @SuppressWarnings("UnusedAssignment")
            long t0 = System.currentTimeMillis();
            mListTable.drop(mSyncedDb);
            @SuppressWarnings("UnusedAssignment")
            long t1 = System.currentTimeMillis();
            mListTable.create(mSyncedDb, false);

            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                Logger.info("Drop   = " + (t1 - t0));
                Logger.info("Create = " + (System.currentTimeMillis() - t1));
            }
        }

        /**
         * Using the collected domain info, create the various SQL phrases used to build the resulting
         * flat list table and build the 'INSERT...SELECT...From'  portion of the SQL that does the
         * initial table load.
         *
         * @param rootKey The key for the root level group. Stored in each row and used to determine the
         *                expand/collapse results.
         *
         * @return SqlComponents structure
         */
        @NonNull
        SqlComponents buildSqlComponents(@NonNull final CompoundKey rootKey) {
            SqlComponents cmp = new SqlComponents();

            // Rebuild the data table
            recreateTable();
            // List of column names for the INSERT... part
            StringBuilder columns = new StringBuilder();
            // List of expressions for the SELECT... part.
            StringBuilder expressions = new StringBuilder();
            // List of ?'s for the VALUES... part.
            StringBuilder values = new StringBuilder();

            // Build the lists. mDomains and mExpressions were built in sync with each other.
            for (int i = 0; i < mDomains.size(); i++) {
                DomainDefinition d = mDomains.get(i);
                String e = mExpressions.get(i);
                if (i > 0) {
                    columns.append(",\n	");
                    expressions.append(",\n	");
                    values.append(", ");
                }
                columns.append(d.name);
                values.append("?");
                expressions.append(e);
                // This is not strictly necessary, but makes SQL more readable and debugging easier.
                expressions.append(" AS ");
                expressions.append(d.name);
            }

            // Build the expression for the root key.
            StringBuilder keyExpression = new StringBuilder("'" + rootKey.prefix);
            for (DomainDefinition d : rootKey.domains) {
                keyExpression.append("/'||Coalesce(").append(mExpressionMap.get(d)).append(",'')");
            }

            // Setup the SQL phrases.
            cmp.rootKeyExpression = keyExpression.toString();
            cmp.destinationColumns = columns + ",\n	" + DOM_ROOT_KEY;
            cmp.insert = "Insert INTO " + mListTable + " (\n	" + cmp.destinationColumns + ")";
            cmp.select = "SELECT\n	" + expressions + ",\n	" + keyExpression;
            cmp.insertSelect = cmp.insert + "\n " + cmp.select + "\n FROM\n";
            cmp.insertValues = cmp.insert + "\n    Values (" + values + ", ?)";

            return cmp;
        }
    }
}


/*
 * Below was an interesting experiment, kept because it may one day get resurrected.
 *
 * The basic idea was to create the list in sorted order to start with by using a BEFORE INSERT trigger on the
 * book_list table. The problems were two-fold:
 *
 * - Android 1.6 has a buggy SQLite that does not like insert triggers that insert on the same table (this is fixed by
 *   android 2.0). So the old code would have to be kept anyway, to deal with old Android.
 *
 * - it did not speed things up, probably because of index maintenance. It took about 5% longer.
 *
 * Tests were performed on Android 2.2 using approx. 800 books.
 *
 * Circumstances under which this may be resurrected include:
 *
 * - Huge libraries (so that index build times are compensated for by huge tables). 800 books is close to break-even
 * - Stored procedures in SQLite become available to avoid multiple nested trigger calls.
 *
 */
//
//	/**
//	 * Clear and the build the temporary list of books based on the passed criteria.
//	 * 
//	 * @param primarySeriesOnly		Only fetch books primary series
//	 * @param showSeries			If false, will not cross with series at al
//	 * @param bookshelf				Search criteria: limit to shelf
//	 * @param authorWhere			Search criteria: additional conditions that apply to authors table
//	 * @param bookWhere				Search criteria: additional conditions that apply to book table
//	 * @param loaned_to				Search criteria: only books loaned to named person
//	 * @param seriesName			Search criteria: only books in named series
//	 * @param searchText			Search criteria: book details must in some way contain the passed text
//	 * 
//	 */
//	public BooklistCursor build(long markId, String bookshelf, String authorWhere, String bookWhere, String loaned_to, String seriesName, String searchText) {
//		long t0 = System.currentTimeMillis();
//		SummaryBuilder summary = new SummaryBuilder();
//		// Add the minimum required domains
//
//		summary.addDomain(DOM_ID);
//		summary.addDomain(DOM_LEVEL, Integer.toString(mLevels.size()+1), SummaryBuilder.FLAG_NONE);
//		summary.addDomain(DOM_ROW_KIND, "" + ROW_KIND_BOOK, SummaryBuilder.FLAG_NONE);
//		summary.addDomain(DOM_BOOK_ID, TBL_BOOKS.dot(DOM_ID), SummaryBuilder.FLAG_NONE);
//		summary.addDomain(DOM_BOOK_COUNT, "1", SummaryBuilder.FLAG_NONE);
//		summary.addDomain(DOM_ROOT_KEY);
//		//summary.addDomain(DOM_PARENT_KEY);		
//		
//		BooklistSeriesLevel seriesLevel = null;
//		BooklistAuthorLevel authorLevel = null;
//		boolean hasLevelLOANED = false;
//		
//		long t0a = System.currentTimeMillis();
//	
//		for (BooklistLevel l : mLevels) {
//			//
//			//	Build each row-kind group.
//			//
//			//  ****************************************************************************************
//			//  IMPORTANT NOTE: for each row kind, then FIRST SORTED AND GROUPED domain should be the one
//			//					that will be displayed and that level in the UI.
//			//  ****************************************************************************************
//			//
//			switch (l.kind) {
//			
//			case ROW_KIND_SERIES:
//				l.displayDomain = DOM_SERIES_NAME;
//				seriesLevel = (BooklistSeriesLevel) l; // getLevel(ROW_KIND_SERIES);
//				summary.addDomain(DOM_SERIES_NAME, TBL_SERIES.dot(DOM_SERIES_NAME), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
//				summary.addDomain(DOM_SERIES_ID, TBL_BOOK_SERIES.dot(DOM_SERIES_ID), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_KEY);
//				summary.addDomain(DOM_BOOK_SERIES_NUM, TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM), SummaryBuilder.FLAG_NONE);
//				summary.addDomain(DOM_BOOK_SERIES_POSITION, TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION), SummaryBuilder.FLAG_NONE);
//				summary.addDomain(DOM_PRIMARY_SERIES_COUNT,"case when Coalesce(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + ",1) == 1 then 1 else 0 end", SummaryBuilder.FLAG_NONE);
//				summary.addDomain(DOM_BOOK_SERIES_NUM, TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM), SummaryBuilder.FLAG_SORTED);
//				//summary.addKeyComponents("'s'", TBL_BOOK_SERIES.dot(DOM_SERIES_ID));
//				l.setKeyComponents("s", DOM_SERIES_ID);
//				break;
//
//			case ROW_KIND_AUTHOR:
//				l.displayDomain = DOM_AUTHOR_FORMATTED;
//				authorLevel = (BooklistAuthorLevel) l; //getLevel(ROW_KIND_AUTHOR);
//				summary.addDomain(DOM_AUTHOR_SORT, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
//				if (authorLevel.givenName)
//					summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);
//				else
//					summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);
//
//				summary.addDomain(DOM_AUTHOR_ID, TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_KEY);
//
//				//summary.addKeyComponents("'a'", TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID));
//				l.setKeyComponents("a", DOM_AUTHOR_ID);
//
//				break;
//
//			case ROW_KIND_GENRE:
//				l.displayDomain = DOM_BOOK_GENRE;
//				summary.addDomain(DOM_BOOK_GENRE, TBL_BOOKS.dot(DOM_BOOK_GENRE), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'g'", TBL_BOOKS.dot(DOM_BOOK_GENRE));
//				l.setKeyComponents("g", DOM_BOOK_GENRE);
//				break;
//
//			case ROW_KIND_PUBLISHER:
//				l.displayDomain = DOM_BOOK_PUBLISHER;
//				summary.addDomain(DOM_BOOK_PUBLISHER, TBL_BOOKS.dot(DOM_BOOK_PUBLISHER), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'p'", TBL_BOOKS.dot(DOM_BOOK_PUBLISHER));
//				l.setKeyComponents("p", DOM_BOOK_PUBLISHER);
//				break;
//
//			case ROW_KIND_UNREAD:
//				l.displayDomain = DOM_READ_STATUS;
//				String unreadExpr = "Case When " + TBL_BOOKS.dot(DOM_BOOK_READ) + " = 1 " +
//						" Then '" + BookCatalogueApp.getResourceString(R.string.booklist_read) + "'" +
//						" Else '" + BookCatalogueApp.getResourceString(R.string.booklist_unread) + "' end";
//				summary.addDomain(DOM_READ_STATUS, unreadExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
//				summary.addDomain(DOM_BOOK_READ, TBL_BOOKS.dot(DOM_BOOK_READ), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'r'", TBL_BOOKS.dot(DOM_BOOK_READ));
//				l.setKeyComponents("r", DOM_BOOK_READ);
//				break;
//
//			case ROW_KIND_LOANED:
//				hasLevelLOANED = true;
//				l.displayDomain = DOM_LOANED_TO;
//				summary.addDomain(DOM_LOANED_TO, LOANED_TO_EXPRESSION, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'l'", DatabaseDefinitions.TBL_LOAN.dot(DOM_LOANED_TO));
//				l.setKeyComponents("l", DOM_LOANED_TO);
//				break;
//
//			case ROW_KIND_TITLE_LETTER:
//				l.displayDomain = DOM_TITLE_LETTER;
//				String titleLetterExpr = "substr(" + TBL_BOOKS.dot(DOM_TITLE) + ",1,1)";
//				summary.addDomain(DOM_TITLE_LETTER, titleLetterExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'t'", titleLetterExpr);
//				l.setKeyComponents("t", DOM_TITLE_LETTER);
//				break;
//
//			default:
//				throw new IllegalTypeException(l.kind.toString());
//
//			}
//			// Copy the current groups to this level item; this effectively accumulates 'group by' domains 
//			// down each level so that the top has fewest groups and the bottom level has groups for all levels.
//			l.groupDomains = (ArrayList<DomainDefinition>) summary.cloneGroups();
//		}
//		long t0b = System.currentTimeMillis();
//
//		if (markId != 0) {
//			summary.addDomain(DOM_MARK, TBL_BOOKS.dot(DOM_ID) + " = " + markId, SummaryBuilder.FLAG_NONE);
//		}
//
//		// Ensure any caller-specified extras (eg. title) are added at the end.
//		for(Entry<String,ExtraDomainInfo> d : mExtraDomains.entrySet()) {
//			ExtraDomainInfo info = d.getValue();
//			int flags;
//			if (info.isSorted)
//				flags = SummaryBuilder.FLAG_SORTED;
//			else
//				flags = SummaryBuilder.FLAG_NONE;
//			summary.addDomain(info.domain, info.sourceExpression, flags);
//		}
//		long t0c = System.currentTimeMillis();
//
//		//
//		// Build the initial insert statement: 'insert into <tbl> (col-list) select (expr-list) from'.
//		// We just need to add the 'from' tables. It is a fairly static list, for the most part we just
//		// add extra criteria as needed.
//		//
//		// The seriesLevel and authorLevel fields will influenced the nature of the join. If at a later
//		// stage some row kinds introduce more table dependencies, a flag (or object) can be set
//		// when processing the level to inform the joining code (below) which tables need to be added.
//		// 
//		// Aside: The sql used prior to using DbUtils is included as comments below the call that replaced it.
//		//
//		String sql = summary.buildBaseInsert(mLevels.get(0).getCompoundKey());
//
//		long t0d = System.currentTimeMillis();
//
//		JoinContext join;
//
//		if (!bookshelf.isEmpty()) {
//			join = new JoinContext(TBL_BOOKSHELF)
//				.start()
//				.join(TBL_BOOK_BOOKSHELF)
//				.join(TBL_BOOKS);
//		} else {
//			join = new JoinContext(TBL_BOOKS).start();
//		}
//			/*
//			if (!bookshelf.isEmpty()) {
//				sql += "	" + DB_TB_BOOKSHELF_AND_ALIAS + " join " + DB_TB_BOOK_BOOKSHELF_AND_ALIAS + 
//						" On " + ALIAS_BOOK_BOOKSHELF + "." + KEY_BOOKSHELF_NAME + " = " + ALIAS_BOOKSHELF + "." + KEY_ID ;
//				sql +=	"    join " + DB_TB_BOOKS_AND_ALIAS + " on " + ALIAS_BOOKS + "." + KEY_ID + " = " + ALIAS_BOOK_BOOKSHELF + "." + KEY_BOOK_ID + "\n";
//			} else {
//				sql +=	"    " + DB_TB_BOOKS_AND_ALIAS + "\n";
//			}
//			*/
//
//		// If a LOANED level is present, we are ONLY interested in loaned books. So cross it here.
//		if (hasLevelLOANED) {
//			join.join(TBL_LOAN);
//		}
//		
//		// Specify a parent in the join, because the last table joined was one of BOOKS or LOAN.
//		join.join(TBL_BOOKS, TBL_BOOK_AUTHOR);
//		if (authorLevel == null || !authorLevel.allAuthors) {
//			join.append( "		and " + TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_POSITION) + " == 1\n");
//		}
//		join.join(TBL_AUTHORS);
//		join.leftOuterJoin(TBL_BOOKS, TBL_BOOK_SERIES);
//			/*
//			sql +=	"    join " + DB_TB_BOOK_AUTHOR_AND_ALIAS + " on " + ALIAS_BOOK_AUTHOR + "." + KEY_BOOK_ID + " = " + ALIAS_BOOKS + "." + KEY_ID + "\n" +
//					"    join " + DB_TB_AUTHORS_AND_ALIAS + " on " + ALIAS_AUTHORS + "." + KEY_ID + " = " + ALIAS_BOOK_AUTHOR + "." + KEY_AUTHOR_ID + "\n";
//			sql +=	"    left outer join " + DB_TB_BOOK_SERIES_AND_ALIAS + " on " + ALIAS_BOOK_SERIES + "." + KEY_BOOK_ID + " = " + ALIAS_BOOKS + "." + KEY_ID + "\n";
//			*/
//
//		if (seriesLevel == null || !seriesLevel.allSeries) {
//			join.append( "		and " + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + " == 1\n");
//		}
//		join.leftOuterJoin(TBL_SERIES);
//			/*
//			if (seriesLevel == null || !seriesLevel.allSeries) {
//				sql += "		and " + ALIAS_BOOK_SERIES + "." + KEY_SERIES_POSITION + " == 1\n";
//			}
//			sql +=	"    left outer join " + DB_TB_SERIES_AND_ALIAS + " on " + ALIAS_SERIES + "." + KEY_ID + " = " + ALIAS_BOOK_SERIES + "." + KEY_SERIES_ID;
//			*/
//
//		// Append the joined tables to our initial insert statement
//		sql += join.toString();
//
//		//
//		// Now build the 'where' clause.
//		//
//		long t0e = System.currentTimeMillis();
//		String where = "";
//
//		if (!bookshelf.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + " = '" + CatalogueDBAdapter.encodeString(bookshelf) + "')";
//		}
//		if (!authorWhere.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + authorWhere + ")";
//		}
//		if (!bookWhere.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + bookWhere + ")";
//		}
//		if (!loaned_to.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "Exists(Select NULL From " + TBL_LOAN.ref() + " Where " + TBL_LOAN.dot(DOM_LOANED_TO) + " = '" + encodeString(loaned_to) + "'" +
//					" and " + TBL_LOAN.fkMatch(TBL_BOOKS) + ")";
//					// .and()    .op(TBL_LOAN.dot(DOM_BOOK_ID), "=", TBL_BOOKS.dot(DOM_ID)) + ")";
//		}
//		if (!seriesName.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + TBL_SERIES.dot(DOM_SERIES_NAME) + " = '" + encodeString(seriesName) + "')";
//		}
//		if(!searchText.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + TBL_BOOKS.dot(DOM_ID) + " in (select docid from " + DB_TB_BOOKS_FTS + " where " + DB_TB_BOOKS_FTS + " match '" + encodeString(searchText) + "'))";
//		}
//
//		// If we got any conditions, add them to the initial insert statement
//		if (!where.equals(""))
//			sql += " where " + where.toString();
//
//		long t1 = System.currentTimeMillis();
//
//		{
//			final ArrayList<DomainDefinition> sort = summary.getExtraSort();
//			final StringBuilder sortCols = new StringBuilder();
//			for (DomainDefinition d: sort) {
//				sortCols.append(d.name);
//				sortCols.append(CatalogueDBAdapter.COLLATION + ", ");
//			}
//			sortCols.append(DOM_LEVEL.name);
//			mSortColumnList = sortCols.toString();
//		}
//
//		{
//			final ArrayList<DomainDefinition> group = summary.cloneGroups();
//			final StringBuilder groupCols = new StringBuilder();;
//			for (DomainDefinition d: group) {
//				groupCols.append(d.name);
//				groupCols.append(CatalogueDBAdapter.COLLATION + ", ");
//			}
//			groupCols.append( DOM_LEVEL.name );
//			mGroupColumnList = groupCols.toString();
//		}
//
//		{
//			final ArrayList<DomainDefinition> keys = summary.getKeys();
//			final StringBuilder keyCols = new StringBuilder();;
//			for (DomainDefinition d: keys) {
//				keyCols.append(d.name);
//				keyCols.append(CatalogueDBAdapter.COLLATION + ", ");
//			}
//			keyCols.append( DOM_LEVEL.name );
//			mKeyColumnList = keyCols.toString();
//		}
//
//		mLevelBuildStmts = new ArrayList<SQLiteStatement>();
//
//		String listNameSave = mListTable.getName();
//		try {
//			mListTable.setName(TBL_BOOK_LIST.getName());
//			mListTable.drop(mSyncedDb);
//			mListTable.create(mSyncedDb, false);
//		} finally {
//			mListTable.setName(listNameSave);			
//		}
//		//mSyncedDb.execSQL("Create View " + TBL_BOOK_LIST + " as select * from " + mListTable);
//
//		// Make sure triggers can check easily
//		//mSyncedDb.execSQL("Create Index " + mListTable + "_IX_TG on " + mListTable + "(" + DOM_LEVEL + ", " + mGroupColumnList + ")");
//		mSyncedDb.execSQL("Create Unique Index " + mListTable + "_IX_TG1 on " + mListTable + "(" + DOM_LEVEL + ", " + mKeyColumnList + ", " + DOM_BOOK_ID + ")");
//
//		/*
//		 * Create a trigger to forward all row details to real table
//		 */
//		/*
//		String fullInsert = "Insert into " + mListTable + "(";
//		{
//			String fullValues = "Values (";
//			boolean firstCol = true;
//			for (DomainDefinition d: mListTable.domains) {
//				if (!d.equals(DOM_ID)) {
//					if (firstCol)
//						firstCol = false;
//					else {
//						fullInsert+=", ";
//						fullValues += ", ";
//					}
//					fullInsert += d; 
//					fullValues += "new." + d; 
//				}
//			}
//			fullInsert += ") " + fullValues + ");";			
//
//			String tgForwardName = "header_Z_F";
//			mSyncedDb.execSQL("Drop Trigger if exists " + tgForwardName);
//			String tgForwardSql = "Create Trigger " + tgForwardName + " instead of  insert on " + TBL_BOOK_LIST + " for each row \n" +
//					"	Begin\n" +
//					"		" + fullInsert + "\n" +
//					"	End";
//			SQLiteStatement stmt = mStatements.add("TG " + tgForwardName, tgForwardSql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();			
//		}
//		*/
//
//		// Now make some BEFORE INSERT triggers to build hierarchy; no trigger on root level (index = 0).
//		//String[] tgLines = new String[mLevels.size()];
//		
//		for (int i = mLevels.size()-1; i >= 0; i--) {
//			final BooklistLevel l = mLevels.get(i);
//			final int levelId = i + 1;
//			String insertSql = "Insert into " + mListTable + "( " + DOM_LEVEL + "," + DOM_ROW_KIND + ", " + DOM_ROOT_KEY + "\n";
//			// If inserting with forwarding table: String insertSql = "Insert into " + TBL_BOOK_LIST + "( " + DOM_LEVEL + "," + DOM_ROW_KIND + ", " + DOM_ROOT_KEY + "\n";
//			// If inserting in one trigger using multiple 'exists': String valuesSql = levelId + ", " + l.kind + ", " + "new." + DOM_ROOT_KEY + "\n";
//			String valuesSql = "Values (" + levelId + ", " + l.kind + ", " + "new." + DOM_ROOT_KEY + "\n";
//			String conditionSql = "l." + DOM_LEVEL + " = " + levelId + "\n";
//			for(DomainDefinition d  : l.groupDomains) {
//				insertSql += ", " + d;
//				valuesSql += ", new." + d;
//				if (summary.getKeys().contains(d))
//					conditionSql += "	and l." + d + " = new." + d + CatalogueDBAdapter.COLLATION + "\n";
//			}
//			//insertSql += ")\n	Select " + valuesSql + " Where not exists(Select 1 From " + mListTable + " l where " + conditionSql + ")";
//			//tgLines[i] = insertSql;
//
//			insertSql += ")\n" + valuesSql + ")";
//			String tgName = "header_A_tgL" + i;
//			mSyncedDb.execSQL("Drop Trigger if exists " + tgName);
//			// If using forwarding table: String tgSql = "Create Trigger " + tgName + " instead of  insert on " + TBL_BOOK_LIST + " for each row when new.level = " + (levelId+1) +
//			String tgSql = "Create Temp Trigger " + tgName + " before insert on " + mListTable + " for each row when new.level = " + (levelId+1) +
//					" and not exists(Select 1 From " + mListTable + " l where " + conditionSql + ")\n" +
//					"	Begin\n" +
//					"		" + insertSql + ";\n" +
//					"	End";
//			SQLiteStatement stmt = mStatements.add("TG " + tgName, tgSql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();
//		}
//		/*
//		String tgName = "header_tg";
//		mSyncedDb.execSQL("Drop Trigger if exists " + tgName);
//		String tgSql = "Create Trigger " + tgName + " instead of  insert on " + TBL_BOOK_LIST + " for each row when new.level = " + (mLevels.size()+1) +
//				"	Begin\n";
//		for(String s: tgLines) {
//			tgSql += "		" + s + ";\n";			
//		}
//
//		tgSql += "\n	" + fullIns + ") " + vals + ");\n";
//		tgSql += "	End";
//		
//		SQLiteStatement tgStmt = mStatements.add("TG " + tgName, tgSql);
//		mLevelBuildStmts.add(tgStmt);
//		tgStmt.execute();
//		*/
//
//		// We are good to go.
//		long t1a = System.currentTimeMillis();
//		mSyncedDb.beginTransaction();
//		long t1b = System.currentTimeMillis();
//		try {
//			// Build the lowest level summary using our initial insert statement
//			//mBaseBuildStmt = mStatements.add("mBaseBuildStmt", sql + ") zzz Order by " + mGroupColumnList);
//			mBaseBuildStmt = mStatements.add("mBaseBuildStmt", sql );
//			mBaseBuildStmt.execute();
//
//			/*
//			 Create table foo(level int, g1 text, g2 text, g3 text);
//			 Create Temp Trigger foo_tgL2 before insert on foo for each row when new.level = 3 and not exists(Select * From foo where level = 2 and g1=new.g1 and g2= foo.g2)
//			 Begin
//			    Insert into foo(level, g1, g2) values (2, new.g1, new.g2);
//			   End;
//			 Create Temp Trigger foo_tgL1 before insert on foo for each row when new.level = 2 and not exists(Select * From foo where level = 1 and g1=new.g1)
//			 Begin
//			    Insert into foo(level, g1) values (1, new.g1);
//			   End;
//
//			 Create Temp Trigger L3 After Insert On mListTable For Each Row When LEVEL = 3 Begin
//			 Insert into mListTable (level, kind, root_key, <cols>) values (new.level-1, new.<cols>)
//			 Where not exists
//			 */
//			long t1c = System.currentTimeMillis();
//			//mSyncedDb.execSQL(ix3cSql);
//			long t1d = System.currentTimeMillis();
//			//mSyncedDb.execSQL("analyze " + mListTable);
//			
//			long t2 = System.currentTimeMillis();
//
//			// Now build each summary level query based on the prior level.
//			// We build and run from the bottom up.
//
//			/*
//			long t2a[] = new long[mLevels.size()];
//			int pos=0;
//			for (int i = mLevels.size()-1; i >= 0; i--) {
//				final BooklistLevel l = mLevels.get(i);
//				final int levelId = i + 1;
//				String cols = "";
//				String collatedCols = "";
//				for(DomainDefinition d  : l.groupDomains) {
//					if (!collatedCols.equals(""))
//						collatedCols += ",";
//					cols += ",\n	" + d.name;
//					//collatedCols += ",\n	" + d.name + CatalogueDBAdapter.COLLATION;
//					collatedCols += "\n	" + d.name + CatalogueDBAdapter.COLLATION;
//				}
//				sql = "Insert Into " + mListTable + "(\n	" + DOM_LEVEL + ",\n	" + DOM_ROW_KIND +
//						//",\n	" + DOM_PARENT_KEY +
//						cols + "," + DOM_ROOT_KEY +
//						")" +
//						"\n select " + levelId + " as " + DOM_LEVEL + ",\n	" + l.kind + " as " + DOM_ROW_KIND +
//						//l.getKeyExpression() +
//						cols + "," + DOM_ROOT_KEY +
//						"\n from " + mListTable + "\n " + " where level = " + (levelId+1) +
//						"\n Group by " + collatedCols + "," + DOM_ROOT_KEY + CatalogueDBAdapter.COLLATION;
//						//"\n Group by " + DOM_LEVEL + ", " + DOM_ROW_KIND + collatedCols;
//
//				SQLiteStatement stmt = mStatements.add("L" + i, sql);
//				mLevelBuildStmts.add(stmt);
//				stmt.execute();
//				t2a[pos++] = System.currentTimeMillis();
//			}
//			*/
//
//			String ix1Sql = "Create Index " + mListTable + "_IX1 on " + mListTable + "(" + mSortColumnList + ")";
//
//			SQLiteStatement stmt;
//			long t3 = System.currentTimeMillis();
//			stmt = mStatements.add("ix1", ix1Sql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();
//			long t3a = System.currentTimeMillis();
//			mSyncedDb.execSQL("analyze " + mListTable);
//			long t3b = System.currentTimeMillis();
//			
//			// Now build a lookup table to match row sort position to row ID. This is used to match a specific
//			// book (or other row in result set) to a position directly.
//			mNavTable.drop(mSyncedDb);
//			mNavTable.create(mSyncedDb, true);
//			sql = mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_LEVEL, DOM_ROOT_KEY, DOM_VISIBLE, DOM_EXPANDED) + 
//					" Select " + mListTable.dot(DOM_ID) + "," + mListTable.dot(DOM_LEVEL) + "," + mListTable.dot(DOM_ROOT_KEY) +
//					" ,\n	Case When " + DOM_LEVEL + " = 1 Then 1 \n" +
//					"	When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0\n	Else 1 end,\n "+ 
//					"	Case When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0 Else 1 end\n"+
//					" From " + mListTable.ref() + "\n	left outer join " + TBL_BOOK_LIST_NODE_SETTINGS.ref() + 
//					"\n		On " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " = " + mListTable.dot(DOM_ROOT_KEY) +
//					"\n			And " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROW_KIND) + " = " + mLevels.get(0).kind +
//					"\n	Order by " + mSortColumnList;
//
//			stmt = mStatements.add("InsNav", sql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();
//
//			long t4 = System.currentTimeMillis();
//			mSyncedDb.execSQL("Create Index " + mNavTable + "_IX1" + " On " + mNavTable + "(" + DOM_LEVEL + "," + DOM_EXPANDED + "," + DOM_ROOT_KEY + ")");
//			long t4a = System.currentTimeMillis();
//			// Essential for main query! If not present, will make getCount() take ages because main query is a cross with no index.
//			mSyncedDb.execSQL("Create Unique Index " + mNavTable + "_IX2" + " On " + mNavTable + "(" + DOM_REAL_ROW_ID + ")");
//			long t4b = System.currentTimeMillis();
//			mSyncedDb.execSQL("analyze " + mNavTable);
//			long t4c = System.currentTimeMillis();
//			
//			/*
//			// Create Index book_list_tmp_row_pos_1_ix2 on book_list_tmp_row_pos_1(level, expanded, root_key);
//			long t5 = System.currentTimeMillis();
//			sql = "Update " + navName + " set expanded = 1 where level = 1 and " +
//					"exists(Select _id From " + TBL_BOOK_LIST_NODE_SETTINGS + " x2 Where x2.kind = " + mLevels.get(0).kind + " and x2.root_key = " + navName + ".root_key)";
//			mSyncedDb.execSQL(sql);
//			long t6 = System.currentTimeMillis();
//			sql = "Update " + navName + " set visible = 1, expanded = 1 where level > 1 and " +
//					"exists(Select _id From " + navName + " x2 Where x2.level = 1 and x2.root_key = " + navName + ".root_key and x2.expanded=1)";
//			mSyncedDb.execSQL(sql);
//			long t7 = System.currentTimeMillis();
//			sql = "Update " + navName + " set visible = 1 where level = 1";
//			mSyncedDb.execSQL(sql);
//			*/
//
//			long t8 = System.currentTimeMillis();
//			//stmt = makeStatement(ix1Sql);
//			//mLevelBuildStmts.add(stmt);
//			//stmt.execute();
//			long t9 = System.currentTimeMillis();
//			//mSyncedDb.execSQL(ix2Sql);
//			long t10 = System.currentTimeMillis();
//			//mSyncedDb.execSQL("analyze " + mTableName);
//			long t11 = System.currentTimeMillis();
//			
//			Logger.info("T0a: " + (t0a-t0));
//			Logger.info("T0b: " + (t0b-t0a));
//			Logger.info("T0c: " + (t0c-t0b));
//			Logger.info("T0d: " + (t0d-t0c));
//			Logger.info("T0e: " + (t0e-t0d));
//			Logger.info("T1: " + (t1-t0));
//			Logger.info("T1a: " + (t1a-t1));
//			Logger.info("T1b: " + (t1b-t1a));
//			Logger.info("T1c: " + (t1c-t1b));
//			Logger.info("T1d: " + (t1d-t1c));
//			Logger.info("T2: " + (t2-t1d));
//			//Logger.info("T2a[0]: " + (t2a[0]-t2));
//			//for(int i = 1; i < mLevels.size(); i++) {
//			//	Logger.info("T2a[" + i + "]: " + (t2a[i]-t2a[i-1]));
//			//}
//			//Logger.info("T3: " + (t3-t2a[mLevels.size()-1]));
//			Logger.info("T3a: " + (t3a-t3));
//			Logger.info("T3b: " + (t3b-t3a));
//			Logger.info("T4: " + (t4-t3b));
//			Logger.info("T4a: " + (t4a-t4));
//			Logger.info("T4b: " + (t4b-t4a));
//			Logger.info("T4c: " + (t4c-t4b));
//			//Logger.info("T5: " + (t5-t4));
//			//Logger.info("T6: " + (t6-t5));
//			//Logger.info("T7: " + (t7-t6));
//			Logger.info("T8: " + (t8-t4c));
//			Logger.info("T9: " + (t9-t8));
//			Logger.info("T10: " + (t10-t9));
//			Logger.info("T10: " + (t11-t10));
//
//			mSyncedDb.setTransactionSuccessful();
//
//			mSummary = summary;
//
//			
//			// Get the final result			
//			return getList();
//			//sql = "select * from " + mTableName + " Order by " + mSortColumnList;
//
//			//return (BooklistCursor) mSyncedDb.rawQueryWithFactory(mBooklistCursorFactory, sql, EMPTY_STRING_ARRAY, "");
//
//		} finally {
//			mSyncedDb.endTransaction();
//			//mSyncedDb.execSQL("PRAGMA synchronous = FULL");
//
//		}
//	}

