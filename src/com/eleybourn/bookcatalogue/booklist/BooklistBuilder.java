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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDoneException;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.filters.Filter;
import com.eleybourn.bookcatalogue.booklist.filters.ListOfValuesFilter;
import com.eleybourn.bookcatalogue.booklist.filters.WildcardFilter;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.database.JoinContext;
import com.eleybourn.bookcatalogue.database.SqlStatementManager;
import com.eleybourn.bookcatalogue.database.cursors.BooklistCursor;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.IllegalTypeException;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;

import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_AUTHOR_SORT;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_ABSOLUTE_POSITION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_BOOK_COUNT;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_NODE_EXPANDED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_NODE_LEVEL;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_NODE_ROW_KIND;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_NODE_VISIBLE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_PRIMARY_SERIES_COUNT;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_REAL_ROW_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_ROOT_KEY;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_SELECTED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BL_SERIES_NUM_FLOAT;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LOANEE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_LOANED_TO_SORT;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_PK_DOCID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_SERIES_TITLE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOKS_FTS;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_LIST;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_LIST_NODE_SETTINGS;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_ROW_NAVIGATOR;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_ROW_NAVIGATOR_FLATTENED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_SERIES;

/**
 * Class used to build and populate temporary tables with details of a flattened book
 * list used to display books in a ListView control and perform operation like
 * 'expand/collapse' on pseudo nodes in the list.
 * <p>
 * TODO: [0123456789] replace by [0..9]
 *
 * @author Philip Warner
 */
public class BooklistBuilder
        implements AutoCloseable {

    /** ID values for state preservation property. */
    public static final int PREF_LIST_REBUILD_ALWAYS_EXPANDED = 0;
    public static final int PREF_LIST_REBUILD_ALWAYS_COLLAPSED = 1;
    public static final int PREF_LIST_REBUILD_STATE_PRESERVED = 2;

    /**
     * SQL column: which gets formatted author names in 'Last, Given' form.
     */
    private static final String X_AUTHOR_FORMATTED_LAST_FIRST = "CASE"
            + " WHEN " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "=''"
            + " THEN " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
            + " ELSE " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + "|| ',' || "
            + /*       */ TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES)
            + " END";
    /**
     * SQL column: formatted author names in 'Given Last' form.
     */
    private static final String X_AUTHOR_FORMATTED_FIRST_LAST = "CASE"
            + " WHEN " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "=''"
            + " THEN " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
            + " ELSE " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "|| ' ' || "
            + /*       */ TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
            + " END";

    /**
     * SQL column: return 1 if the book is available, 0 if not.
     */
    private static final String X_BOOK_IS_AVAILABLE_AS_BOOLEAN = "CASE"
            + " WHEN " + TBL_BOOK_LOANEE.dot(DOM_BOOK_LOANEE) + " IS NULL"
            + " THEN 1 ELSE 0"
            + " END";

    /**
     * SQL column: return "" if the book is available, "loanee name" if not.
     */
    private static final String X_BOOK_IS_AVAILABLE_AS_TEXT = "CASE"
            + " WHEN " + TBL_BOOK_LOANEE.dot(DOM_BOOK_LOANEE) + " IS NULL"
            + "  THEN '' ELSE '" + TBL_BOOK_LOANEE.dot(DOM_BOOK_LOANEE)
            + " END";

    private static final String X_PRIMARY_SERIES_COUNT_AS_BOOLEAN = "CASE"
            + " WHEN Coalesce(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + ",1)==1"
            + " THEN 1 ELSE 0"
            + " END";

    /** Counter for BooklistBuilder IDs. */
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


    @NonNull
    private final Context mContext;
    // not in use for now
    // List of columns for the group-by clause, including COLLATE clauses. Set by build() method.
    //private String mGroupColumnList;

    /**
     * Collection of statements created by this Builder.
     * Private to this instance, hence no need to synchronize the statements.
     */
    @NonNull
    private final SqlStatementManager mStatements;

    /** Database to use. */
    @NonNull
    private final DBA mDb;
    /** Database to use. */
    @NonNull
    private final SynchronizedDb mSyncedDb;
    /** Internal ID. */
    private final int mBooklistBuilderId;
    /** Collection of 'extra' domains requested by caller. */
    @NonNull
    private final Map<String, ExtraDomainDetails> mExtraDomains = new HashMap<>();
    /** Style to use in building the list. */
    @NonNull
    private final BooklistStyle mStyle;
    /**
     * Instance-based cursor factory so that the builder can be associated with the cursor
     * and the rowView. We could probably send less context, but in the first instance this
     * guarantees we get all the info we need downstream.
     */
    private final CursorFactory mBooklistCursorFactory = (db, masterQuery, editTable, query) ->
            new BooklistCursor(masterQuery, editTable, query, DBA.getSynchronizer(),
                               BooklistBuilder.this);
    /** The word 'UNKNOWN', used for year/month if those are (guess what...) unknown. */
    private final String mUnknown;
    /** the list of Filters; both active and non-active. */
    private final transient ArrayList<Filter> mFilters = new ArrayList<>();
    /** used in debug. */
    private boolean mDebugReferenceDecremented;
    /**
     * Local copy of the {@link DBDefinitions#TBL_BOOK_LIST} table definition,
     * 'book_list_tmp' but renamed by adding the instance number to the end to match this instance.
     */
    private TableDefinition mListTable;
    /**
     * Local copy of the {@link DBDefinitions#TBL_ROW_NAVIGATOR},
     * renamed to match this instance.
     */
    private TableDefinition mNavTable;
    /** Object used in constructing the output table. */
    private SummaryBuilder mSummary;
    /**
     * Statement used to perform initial insert.
     * <p>
     * Needs to be re-usable WITHOUT parameter binding.
     */
    private SynchronizedStatement mBaseBuildStmt;
    /**
     * Collection of statements used to build remaining data.
     * <p>
     * Needs to be re-usable WITHOUT parameter binding.
     */
    private ArrayList<SynchronizedStatement> mLevelBuildStmts;

    /** A bookshelf id to use as a filter; i.e. show only books on this bookshelf. */
    private long mFilterOnBookshelfId;

    /**
     * Constructor.
     *
     * @param context the caller context
     * @param style   Book list style to use
     */
    public BooklistBuilder(@NonNull final Context context,
                           @NonNull final BooklistStyle style) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
            Logger.debugEnter(this, "BooklistBuilder",
                              "instances: " + DEBUG_INSTANCE_COUNTER.incrementAndGet());
        }
        // Allocate ID
        mBooklistBuilderId = ID_COUNTER.incrementAndGet();

        mContext = context;

        mUnknown = mContext.getString(R.string.unknown).toUpperCase();

        // Get the database and create a statements collection
        mDb = new DBA(mContext);
        mSyncedDb = mDb.getUnderlyingDatabase();
        mStatements = new SqlStatementManager(mSyncedDb);
        // Save the requested style
        mStyle = style;
    }

    /**
     * @return the current preferred rebuild state for the list.
     */
    public static int getListRebuildState() {
        return App.getListPreference(Prefs.pk_bob_list_state, PREF_LIST_REBUILD_ALWAYS_EXPANDED);
    }

    public static boolean imagesAreCached() {
        return App.getPrefs().getBoolean(Prefs.pk_bob_thumbnails_cache_resized, false);
    }

    public static boolean imagesAreGeneratedInBackground() {
        return App.getPrefs().getBoolean(Prefs.pk_bob_thumbnails_generating_mode, false);
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
        mNavTable.addReference(mListTable, DOM_BL_REAL_ROW_ID);
    }

    /**
     * @return a flattened table of ordered book IDs based on the underlying list.
     */
    @NonNull
    public FlattenedBooklist createFlattenedBooklist() {
        int flatId = FLAT_LIST_ID_COUNTER.getAndIncrement();

        TableDefinition flat = TBL_ROW_NAVIGATOR_FLATTENED.clone();
        flat.setName(flat.getName() + '_' + flatId);
        // no indexes, no constraints!
        flat.create(mSyncedDb, false);

        String sql = flat.getInsert(false, DOM_PK_ID, DOM_FK_BOOK_ID)
                + " SELECT " + mNavTable.dot(DOM_PK_ID)
                + ',' + mListTable.dot(DOM_FK_BOOK_ID)
                + " FROM " + mListTable.ref() + mListTable.join(mNavTable)
                + " WHERE " + mListTable.dot(DOM_FK_BOOK_ID) + " NOT NULL"
                + " ORDER BY " + mNavTable.dot(DOM_PK_ID);
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
            stmt.executeInsert();
        }
        return new FlattenedBooklist(mSyncedDb, flat);
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
    public BooklistBuilder requireDomain(@NonNull final DomainDefinition domain,
                                         @NonNull final String sourceExpression,
                                         final boolean isSorted) {
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
            //noinspection ConstantConditions
            if (oldInfo.sourceExpression == null) {
                ok = info.sourceExpression == null || info.sourceExpression.isEmpty();
            } else {
                if (info.sourceExpression == null) {
                    ok = oldInfo.sourceExpression.isEmpty();
                } else {
                    ok = oldInfo.sourceExpression.equalsIgnoreCase(info.sourceExpression);
                }
            }
            if (!ok) {
                throw new IllegalStateException(
                        "Required domain `" + domain.name + '`'
                                + " added with differing source expression");
            }
        } else {
            mExtraDomains.put(domain.name, info);
        }

        return this;
    }

    /**
     * Drop and recreate all the data based on previous criteria.
     */
    public void rebuild() {
        mSummary.recreateTable();

        mNavTable.drop(mSyncedDb);
        mNavTable.create(mSyncedDb, true, false);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
            Logger.debug(this, "rebuild", "mBaseBuildStmt|" + mBaseBuildStmt.toString() + '\n');
        }

        // Build base data
        mBaseBuildStmt.executeInsert();

        // Rebuild all the rest
        for (SynchronizedStatement stmt : mLevelBuildStmts) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                Logger.debug(this, "rebuild", "mLevelBuildStmts|" + stmt.toString() + '\n');
            }
            stmt.execute();
        }
    }

    private boolean isNonBlank(@Nullable final String s) {
        return s != null && !s.trim().isEmpty();
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
                + " THEN substr(" + fieldSpec + ", 1, 4)"
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
                + " THEN substr(" + fieldSpec + ", 6, 2)"
                + " WHEN " + fieldSpec + " glob '[0123456789][01234567890][01234567890][01234567890]-[0123456789]*'"
                + " THEN substr(" + fieldSpec + ", 6, 1)"
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
                + " THEN substr(" + fieldSpec + ", 9, 2)"
                + " WHEN " + fieldSpec + " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789]-[0123456789][0123456789]*'"
                + " THEN substr(" + fieldSpec + ", 8, 2)"
                + " WHEN " + fieldSpec + " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789][0123456789]-[0123456789]*'"
                + " THEN substr(" + fieldSpec + ", 9, 1)"
                + " WHEN " + fieldSpec + " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789]-[0123456789]*'"
                + " THEN substr(" + fieldSpec + ", 8, 1)"
                + " ELSE " + fieldSpec
                + " END";
    }

    /**
     * expects a full WHERE condition.
     *
     * @param filter additional conditions that apply
     */
    public void setGenericCriteria(@Nullable final String filter) {
        if (isNonBlank(filter)) {
            mFilters.add(() -> '(' + filter + ')');
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
        if (isNonBlank(filter)) {
            mFilters.add(() -> "EXISTS(SELECT NULL FROM " + TBL_BOOK_LOANEE.ref()
                    + " WHERE "
                    + TBL_BOOK_LOANEE.dot(DOM_BOOK_LOANEE)
                    + "='" + DBA.encodeString(filter) + '\''
                    + " AND " + TBL_BOOK_LOANEE.fkMatch(TBL_BOOKS) + ')');
        }
    }

    /**
     * Set the filter for only books with named author (family or given) with added wildcards.
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param filter the author (family or given) to limit the search for.
     */
    public void setFilterOnAuthorName(@Nullable final String filter) {
        if (isNonBlank(filter)) {
            mFilters.add(() -> '(' + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                    + " LIKE '%" + DBA.encodeString(filter) + "%'"
                    + " OR "
                    + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES)
                    + " LIKE '%" + DBA.encodeString(filter) + "%')");
        }
    }

    /**
     * Set the filter for only books with named title with added wildcards.
     * <p>
     * An empty filter will silently be rejected.
     * {@link WildcardFilter} is case insensitive.
     *
     * @param filter the title to limit the search for.
     */
    public void setFilterOnTitle(@Nullable final String filter) {
        if (isNonBlank(filter)) {
            mFilters.add(new WildcardFilter(TBL_BOOKS, DOM_TITLE, filter));
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
        if (isNonBlank(filter)) {
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
     * Adds the FTS book table for a keyword match.
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param filter book details must in some way contain the passed text
     */
    public void setFilterOnText(@Nullable final String filter) {
        if (isNonBlank(filter)) {
            // Cleanup searchText
            // Because FTS does not understand locales in all android up to 4.2,
            // we do case folding here using the user preferred locale.
            final String cleanCriteria = filter.toLowerCase(LocaleUtils.getPreferredLocal());

            mFilters.add(() -> '(' + TBL_BOOKS.dot(DOM_PK_ID)
                    + " IN (SELECT " + DOM_PK_DOCID + " FROM " + TBL_BOOKS_FTS
                    + " WHERE " + TBL_BOOKS_FTS + " match '"
                    + DBA.encodeString(DBA.cleanupFtsCriterion(cleanCriteria)) + "'))");
        }
    }

    /**
     * The where clause will add a "AND books._id IN (list)".
     * Be careful when combining with other criteria as you might get less then expected
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param filter a list of book id's.
     */
    public void setFilterOnBookIdList(@Nullable final List<Integer> filter) {
        if (filter != null && !filter.isEmpty()) {
            mFilters.add(new ListOfValuesFilter<>(TBL_BOOKS, DOM_PK_ID, filter));
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
            Logger.debugEnter(this, "build", mBooklistBuilderId);
        }

        try {
            @SuppressWarnings("UnusedAssignment")
            final long t0 = System.nanoTime();

            // create the mListTable/mNavTable fresh.
            buildTableDefinitions();

            SummaryBuilder summary = new SummaryBuilder();

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
            summary.addDomain(DOM_FK_BOOK_ID, TBL_BOOKS.dot(DOM_PK_ID),
                              SummaryBuilder.FLAG_NONE);
            summary.addDomain(DOM_BL_BOOK_COUNT, "1",
                              SummaryBuilder.FLAG_NONE);

            // We can not use triggers to fill in headings in API < 8 since
            // SQLite 3.5.9 (2008-05-14) is broken.
            // Allow for the user preferences to override in case another build is broken.
            // 2019-01-20: v200 is targeting API 21, which would be SqLite 3.8 (2013-08-26)
            //ENHANCE: time to remove sqlite pre-trigger use logic ? -> keep it till the bug of rebuild (using triggers) is resolved.
            final CompatibilityMode listMode = CompatibilityMode.get();

            // Build a sort mask based on if triggers are used;
            // we can not reverse sort if they are not used.
            final int sortDescendingMask = listMode.useTriggers
                                           ? SummaryBuilder.FLAG_SORT_DESCENDING : 0;

            @SuppressWarnings("UnusedAssignment")
            final long t1_basic_setup_done = System.nanoTime();

            final BuildInfoHolder buildInfoHolder = new BuildInfoHolder();

            for (BooklistGroup group : mStyle.getGroups()) {
                // process each group/RowKind
                buildSummaryForGroup(summary, buildInfoHolder, group, sortDescendingMask);

                // Copy the current groups to this level item; this effectively accumulates
                // 'group by' domains down each level so that the top has fewest groups and
                // the bottom level has groups for all levels.
                group.setDomains(summary.cloneGroups());
            }

            @SuppressWarnings("UnusedAssignment")
            final long t2_groups_processed = System.nanoTime();

            // Now we know if we have a Bookshelf group, add the Filter on it if we want one.
            if (mFilterOnBookshelfId > 0) {
                mFilters.add(() -> {
                    if (buildInfoHolder.hasGroupBOOKSHELF) {
                        return "EXISTS(SELECT NULL FROM "
                                + TBL_BOOK_BOOKSHELF.ref() + ' '
                                + TBL_BOOK_BOOKSHELF.join(TBL_BOOKS)
                                + " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_FK_BOOKSHELF_ID)
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

            // If we have a book ID to remember, then add the DOM_BL_SELECTED field,
            // and setup the expression.
            if (previouslySelectedBookId != 0) {
                summary.addDomain(DOM_BL_SELECTED,
                                  TBL_BOOKS.dot(DOM_PK_ID) + '=' + previouslySelectedBookId,
                                  SummaryBuilder.FLAG_NONE);
            }

            summary.addDomain(DOM_BL_NODE_LEVEL, null, SummaryBuilder.FLAG_SORTED);

            // Finished. Ensure any caller-specified extras (eg. title) are added at the end.
            for (Entry<String, ExtraDomainDetails> d : mExtraDomains.entrySet()) {
                ExtraDomainDetails info = d.getValue();
                int flags = info.isSorted ? SummaryBuilder.FLAG_SORTED
                                          : SummaryBuilder.FLAG_NONE;
                summary.addDomain(info.domain, info.sourceExpression, flags);
            }

            @SuppressWarnings("UnusedAssignment")
            final long t3 = System.nanoTime();

            ////////////////////////////////////////////////////////////////////////////////////////

            /*
             * Build the initial insert statement:
             *      'insert into <tbl> (col-list) select (expr-list) from'.
             *
             * We just need to add the 'from' tables. It is a fairly static list,
             * for the most part we just add extra criteria as needed.
             *
             * The seriesLevel and authorLevel fields will influence the nature of the join.
             * If at a later stage some row kinds introduce more table dependencies, a flag
             * (or object) can be set when processing the level to inform the joining code
             * (below) which tables need to be added.
             */
            SqlComponents sqlCmp = summary.buildSqlComponents(mStyle.getGroupAt(0)
                                                                    .getCompoundKey());

            @SuppressWarnings("UnusedAssignment")
            final long t4_buildSqlComponents = System.nanoTime();
            // Build the join tables
            sqlCmp.join = buildJoin(buildInfoHolder);

            @SuppressWarnings("UnusedAssignment")
            final long t5_build_join = System.nanoTime();

            // Build the 'where' clause
            sqlCmp.where = buildWhereClause();

            @SuppressWarnings("UnusedAssignment")
            final long t6_build_where = System.nanoTime();

            // Check if the collation we use is case sensitive.
            boolean collationIsCs = mSyncedDb.isCollationCaseSensitive();

            processSortColumns(summary.getSortedColumns(), collationIsCs, sqlCmp);

            // unused
            // Process the group-by columns suitable for a group-by statement or index
//            {
//                final StringBuilder groupCols = new StringBuilder();
//                for (DomainDefinition d : summary.cloneGroups()) {
//                    groupCols.append(d.name);
//                    groupCols.append(DBA.COLLATION);
//                    groupCols.append(", ");
//                }
//                groupCols.append(DOM_BL_NODE_LEVEL.name);
//                //mGroupColumnList = groupCols.toString();
//            }

            @SuppressWarnings("UnusedAssignment")
            final long t7_sortColumns_processed = System.nanoTime();

            // We are good to go.

            //mSyncedDb.execSQL("PRAGMA synchronous = OFF"); -- Has very little effect
            SyncLock txLock = mSyncedDb.beginTransaction(true);
            try {
                mLevelBuildStmts = new ArrayList<>();
                // Build the lowest level summary using our initial insert statement
                if (listMode.useTriggers) {
                    // If we are using triggers, then we insert them in order and rely on the
                    // triggers to build the summary rows in the correct place.
                    String tgt;
                    if (listMode.nestedTriggers) {
                        // Nested triggers are compatible with Android 2.2+ and fast.
                        // (or at least relatively fast when there are a 'reasonable'
                        // number of headings to be inserted).
                        tgt = makeNestedTriggers(summary);
                    } else {
                        // Flat triggers are compatible with Android 1.6+ but slower
                        tgt = makeSingleTrigger(summary);
                    }
                    mBaseBuildStmt = mStatements.add(
                            STMT_BASE_BUILD,
                            "INSERT INTO " + tgt + '(' + sqlCmp.destinationColumns + ") "
                                    + sqlCmp.select
                                    + " FROM " + sqlCmp.join + sqlCmp.where
                                    + " ORDER BY " + sqlCmp.orderByColumns);

                    mBaseBuildStmt.executeInsert();
                } else {
                    // just moved out of the way... eventually to be removed maybe?
                    baseBuildWithoutTriggers(sqlCmp, collationIsCs);
                }

                @SuppressWarnings("UnusedAssignment")
                final long t8_BaseBuild_executed = System.nanoTime();

                mSyncedDb.analyze(mListTable);

                @SuppressWarnings("UnusedAssignment")
                final long t9_table_optimized = System.nanoTime();

                // build the lookup aka navigation table
                populateNavigationTable(sqlCmp, preferredState, listMode);

                @SuppressWarnings("UnusedAssignment")
                final long t10_nav_table_build = System.nanoTime();

                // Create index on nav table
                SynchronizedStatement ixStmt1 =
                        mStatements.add(STMT_NAV_IX_1,
                                        "CREATE INDEX " + mNavTable + "_IX1"
                                                + " ON " + mNavTable
                                                + '(' + DOM_BL_NODE_LEVEL
                                                + ',' + DOM_BL_NODE_EXPANDED
                                                + ',' + DOM_BL_ROOT_KEY + ')');

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                    Logger.debug(this,
                                 "build", "add|" + STMT_NAV_IX_1
                                         + '|' + ixStmt1.toString());
                }
                mLevelBuildStmts.add(ixStmt1);
                ixStmt1.execute();

                @SuppressWarnings("UnusedAssignment")
                final long t11_nav_table_index_IX1_created = System.nanoTime();

                // Essential for main query! If not present, will make getCount() take
                // ages because main query is a cross with no index.
                SynchronizedStatement ixStmt2 =
                        mStatements.add(STMT_NAV_IX_2,
                                        "CREATE UNIQUE INDEX " + mNavTable + "_IX2"
                                                + " ON " + mNavTable
                                                + '(' + DOM_BL_REAL_ROW_ID + ')');

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                    Logger.debug(this,
                                 "build", "add|" + STMT_NAV_IX_2
                                         + '|' + ixStmt2.toString());
                }
                mLevelBuildStmts.add(ixStmt2);
                ixStmt2.execute();


                @SuppressWarnings("UnusedAssignment")
                final long t12_nav_table_index_IX2_created = System.nanoTime();
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
                                 "\nT11: " + (t11_nav_table_index_IX1_created - t10_nav_table_build),
                                 "\nT12: " + (t12_nav_table_index_IX2_created
                                         - t11_nav_table_index_IX1_created),
                                 "\nT13: " + (System.nanoTime()
                                         - t12_nav_table_index_IX2_created),
                                 "\n============================",
                                 "\nTotal time: " + (System.nanoTime() - t0) + "nano");
                }
                mSyncedDb.setTransactionSuccessful();

                mSummary = summary;

            } finally {
                mSyncedDb.endTransaction(txLock);
                //mSyncedDb.execSQL("PRAGMA synchronous = FULL");

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
     */
    private void populateNavigationTable(@NonNull final SqlComponents sqlCmp,
                                         final int preferredState,
                                         @NonNull final CompatibilityMode listMode) {

        mNavTable.drop(mSyncedDb);
        mNavTable.create(mSyncedDb, true, false);

        String sortExpression;
        if (listMode.useTriggers) {
            sortExpression = mListTable.dot(DOM_PK_ID);
        } else {
            sortExpression = sqlCmp.orderByColumns;
        }

        // TODO: Rebuild with state preserved is SLOWEST option
        // Need a better way to preserve state.
        String insSql = mNavTable.getInsert(false,
                                            DOM_BL_REAL_ROW_ID,
                                            DOM_BL_NODE_LEVEL,
                                            DOM_BL_ROOT_KEY,
                                            DOM_BL_NODE_VISIBLE,
                                            DOM_BL_NODE_EXPANDED)
                + " SELECT " + mListTable.dot(DOM_PK_ID)
                + ',' + mListTable.dot(DOM_BL_NODE_LEVEL)
                + ',' + mListTable.dot(DOM_BL_ROOT_KEY)
                + ',' + '\n'
                + " CASE WHEN " + DOM_BL_NODE_LEVEL + "=1"
                + " THEN 1"
                + " WHEN " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY) + " IS NULL"
                + " THEN 0 ELSE 1"
                + " END"
                + ',' + '\n'
                + " CASE WHEN " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY) + " IS NULL"
                + " THEN 0 ELSE 1"
                + " END"
                + '\n'
                + " FROM " + mListTable.ref()
                + " LEFT OUTER JOIN " + TBL_BOOK_LIST_NODE_SETTINGS.ref()
                + " ON " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY)
                + /*     */ '=' + mListTable.dot(DOM_BL_ROOT_KEY)
                + "	AND " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_NODE_ROW_KIND)
                + /*     */ '=' + mStyle.getGroupKindAt(0)
                + " ORDER BY " + sortExpression;

        // Always save the state-preserving navigator for rebuilds
        SynchronizedStatement navStmt = mStatements.add(STMT_NAV_TABLE_INSERT, insSql);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
            Logger.debug(this,
                         "populateNavigationTable", "add|" + STMT_NAV_TABLE_INSERT
                                 + '|' + navStmt.toString());
        }
        mLevelBuildStmts.add(navStmt);

        // On first-time builds, get the Preferences-based list
        switch (preferredState) {
            case PREF_LIST_REBUILD_ALWAYS_COLLAPSED:
                String sqlc = mNavTable.getInsert(false,
                                                  DOM_BL_REAL_ROW_ID,
                                                  DOM_BL_NODE_LEVEL,
                                                  DOM_BL_ROOT_KEY,
                                                  DOM_BL_NODE_VISIBLE,
                                                  DOM_BL_NODE_EXPANDED)
                        + " SELECT " + mListTable.dot(DOM_PK_ID)
                        + ',' + mListTable.dot(DOM_BL_NODE_LEVEL)
                        + ',' + mListTable.dot(DOM_BL_ROOT_KEY)
                        + ',' + '\n'
                        + " CASE WHEN " + DOM_BL_NODE_LEVEL + "=1"
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
                String sqle = mNavTable.getInsert(false,
                                                  DOM_BL_REAL_ROW_ID,
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
     * Process the 'sort-by' columns into a list suitable for a sort-by statement, or index.
     *
     * @param sortedColumns the list of sorted domains from the builder
     * @param collationIsCs if <tt>true</tt> then we'll adjust the case ourselves
     * @param sqlCmp        will be updated with the sorting information
     */
    private void processSortColumns(@NonNull final List<SortedDomainInfo> sortedColumns,
                                    final boolean collationIsCs,
                                    final SqlComponents /* in/out */ sqlCmp) {
        final StringBuilder sortCols = new StringBuilder();
        final StringBuilder indexCols = new StringBuilder();

        for (SortedDomainInfo sdi : sortedColumns) {
            indexCols.append(sdi.domain.name);
            if (sdi.domain.isText()) {
                indexCols.append(DBA.COLLATION);

                // *If* collations is case-sensitive, handle it.
                if (collationIsCs) {
                    sortCols.append("lower(").append(sdi.domain.name).append(')');
                } else {
                    sortCols.append(sdi.domain.name);
                }
                sortCols.append(DBA.COLLATION);
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

        sortCols.append(DOM_BL_NODE_LEVEL.name);
        indexCols.append(DOM_BL_NODE_LEVEL.name);

        sqlCmp.orderByColumns = sortCols.toString();
        sqlCmp.sortIndexColumnList = indexCols.toString();
    }

    private void baseBuildWithoutTriggers(@NonNull final SqlComponents /* in/out */ sqlCmp,
                                          final boolean collationIsCs) {

        final long[] t_style = new long[mStyle.groupCount() + 1];
        t_style[0] = System.nanoTime();

        // Without triggers we just get the base rows and add summary later
        mBaseBuildStmt = mStatements.add(STMT_BASE_BUILD,
                                         sqlCmp.insertSelect + sqlCmp.join + sqlCmp.where);
        mBaseBuildStmt.executeInsert();

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

                collatedCols.append(' ').append(d.name).append(DBA.COLLATION);
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
                    + " GROUP BY " + collatedCols + ',' + DOM_BL_ROOT_KEY + DBA.COLLATION;
            //" GROUP BY " + DOM_BL_NODE_LEVEL + ", " + DOM_BL_NODE_ROW_KIND + collatedCols;

            // Save, compile and run this statement
            SynchronizedStatement stmt = mStatements.add("Level" + i, summarySql);
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                Logger.debug(this,
                             "baseBuildWithoutTriggers",
                             "add|" + "Level" + i + '|' + stmt.toString());
            }
            mLevelBuildStmts.add(stmt);
            stmt.executeInsert();
            t_style[timer++] = System.nanoTime();
        }

        // Build an index if it will help sorting but *If* collation is case-sensitive,
        // don't bother with index, since everything is wrapped in lower().
        // ENHANCE: ICS UNICODE: Consider adding a duplicate _lc (lower case) column
        // to the SUMMARY table. Ugh.
        if (!collationIsCs) {
            String ix1Sql = "CREATE INDEX " + mListTable + "_IX1 ON " + mListTable
                    + '(' + sqlCmp.sortIndexColumnList + ')';

            // Indexes that were tried. None had a substantial impact with 800 books.
//            String ix1aSql = "CREATE INDEX " + mListTable + "_IX1a ON " + mListTable
//                    + '(' + DOM_BL_NODE_LEVEL + ", " + sqlCmp.sortIndexColumnList + ')';
//            String ix2Sql = "CREATE UNIQUE INDEX " + mListTable + "_IX2 ON " + mListTable
//                    + '(' + DOM_FK_BOOK_ID + ", " + DOM_PK_ID + ')';
//            String ix3Sql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable
//                    + '(' + sqlCmp.sortIndexColumnList + ')';
//            String ix3aSql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable
//                    + '(' + DOM_BL_NODE_LEVEL + ',' + sqlCmp.sortIndexColumnList + ')';
//            String ix3bSql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable
//                    + '(' + sqlCmp.sortIndexColumnList + ',' + DOM_BL_NODE_LEVEL + ')';
//            String ix3cSql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable
//                    + '(' + sqlCmp.sortIndexColumnList
//                    + ',' + DOM_BL_ROOT_KEY + DBA.COLLATION + ')';
//            String ix3dSql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable
//                    + '(' + DOM_BL_NODE_LEVEL + ',' + sqlCmp.sortIndexColumnList
//                    + ',' + DOM_BL_ROOT_KEY + ')';
//            String ix3eSql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable
//                    + '(' + sqlCmp.sortIndexColumnList + ',' + DOM_BL_ROOT_KEY
//                    + ',' + DOM_BL_NODE_LEVEL + ')';
//            String ix4Sql = "CREATE INDEX " + mListTable + "_IX4 ON " + mListTable
//                    + '(' + DOM_BL_NODE_LEVEL + ',' + DOM_BL_NODE_EXPANDED
//                    + ',' + DOM_BL_ROOT_KEY + ')';
            SynchronizedStatement stmt = mStatements.add(STMT_IX_1, ix1Sql);
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                Logger.debug(this,
                             "baseBuildWithoutTriggers",
                             "add|" + STMT_IX_1 + '|' + stmt.toString());
            }
            mLevelBuildStmts.add(stmt);
            stmt.execute();
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            for (
                //noinspection UnusedAssignment
                    int i = 0; i < mStyle.groupCount();
                //noinspection UnusedAssignment
                    i++) {
                Logger.debug(this,
                             "baseBuildWithoutTriggers",
                             "t_style[" + i + "]: " + (t_style[i] - t_style[i - 1]));
            }
        }
    }

    /**
     * Build each kind group.
     * <p>
     * ****************************************************************************************
     * IMPORTANT NOTE: for each kind, the FIRST SORTED AND GROUPED domain should be the one
     * that will be displayed at that level in the UI.
     * <p>
     * -> 2018-12-07: due to moving some things to {@link BooklistGroup.RowKind}
     * the requirement is semi-reverse: the displayed domain is normally decided by the RowKind
     * and this should be set as the FIRST SORTED AND GROUPED domain
     * ****************************************************************************************
     */
    private void buildSummaryForGroup(@NonNull final SummaryBuilder /* in/out */ summary,
                                      @NonNull final BuildInfoHolder /* in/out */ buildInfoHolder,
                                      @NonNull final BooklistGroup /* in/out */ booklistGroup,
                                      final int sortDescendingMask) {

        switch (booklistGroup.getKind()) {

            case BooklistGroup.RowKind.AUTHOR:
                // Save this for later use
                buildInfoHolder.authorGroup = (BooklistGroup.BooklistAuthorGroup) booklistGroup;

                // Always group & sort by DOM_AUTHOR_SORT and user preference order; see #696
                summary.addDomain(DOM_AUTHOR_SORT,
                                  mStyle.sortAuthorByGiven()
                                  ? X_AUTHOR_FORMATTED_FIRST_LAST
                                  : X_AUTHOR_FORMATTED_LAST_FIRST,
                                  SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);

                // Add the 'formatted' field of the requested type
                summary.addDomain(DOM_AUTHOR_FORMATTED,
                                  buildInfoHolder.authorGroup.showGivenNameFirst()
                                  ? X_AUTHOR_FORMATTED_FIRST_LAST
                                  : X_AUTHOR_FORMATTED_LAST_FIRST,
                                  SummaryBuilder.FLAG_GROUPED);

                // We also want the ID
                summary.addDomain(DOM_FK_AUTHOR_ID,
                                  TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR_ID),
                                  SummaryBuilder.FLAG_GROUPED);

                // we want the isComplete flag
                summary.addDomain(DOM_AUTHOR_IS_COMPLETE,
                                  TBL_AUTHORS.dot(DOM_AUTHOR_IS_COMPLETE),
                                  SummaryBuilder.FLAG_GROUPED);

                break;

            case BooklistGroup.RowKind.SERIES:
                // Save this for later use
                buildInfoHolder.seriesGroup = (BooklistGroup.BooklistSeriesGroup) booklistGroup;

                // Group and sort by name
                summary.addDomain(DOM_SERIES_TITLE,
                                  TBL_SERIES.dot(DOM_SERIES_TITLE),
                                  SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);

                // Group by ID (we want the ID available and there is a *chance* two
                // series will have the same name...with bad data
                summary.addDomain(DOM_FK_SERIES_ID,
                                  TBL_BOOK_SERIES.dot(DOM_FK_SERIES_ID),
                                  SummaryBuilder.FLAG_GROUPED);

                // We want the series position in the base data
                summary.addDomain(DOM_BOOK_SERIES_POSITION,
                                  TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION),
                                  SummaryBuilder.FLAG_NONE);

                // we want the isComplete flag
                summary.addDomain(DOM_SERIES_IS_COMPLETE,
                                  TBL_SERIES.dot(DOM_SERIES_IS_COMPLETE),
                                  SummaryBuilder.FLAG_GROUPED);

                // We want the series number in the base data in sorted order
                // Allow for the possibility of 3.1, or even "3.1|Omnibus 3-10" as a series number.
                // so we convert it to a real (aka float).
                summary.addDomain(DOM_BL_SERIES_NUM_FLOAT,
                                  "CAST(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM) + " AS REAL)",
                                  SummaryBuilder.FLAG_SORTED);

                // We also add the base name as a sorted field for display purposes
                // and in case of non-numeric data.
                summary.addDomain(DOM_BOOK_SERIES_NUM,
                                  TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM),
                                  SummaryBuilder.FLAG_SORTED);

                // We want a counter of how many books use the series as a primary series,
                // so we can skip some series
                summary.addDomain(DOM_BL_PRIMARY_SERIES_COUNT,
                                  X_PRIMARY_SERIES_COUNT_AS_BOOLEAN,
                                  SummaryBuilder.FLAG_NONE);
                break;

            case BooklistGroup.RowKind.LOANED:
                // Saved for later to indicate group was present
                buildInfoHolder.hasGroupLOANED = true;
                summary.addDomain(DOM_LOANED_TO_SORT,
                                  X_BOOK_IS_AVAILABLE_AS_BOOLEAN,
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);

                summary.addDomain(DOM_BOOK_LOANEE,
                                  X_BOOK_IS_AVAILABLE_AS_TEXT,
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                break;

            case BooklistGroup.RowKind.BOOKSHELF:
                buildInfoHolder.hasGroupBOOKSHELF = true;
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  TBL_BOOKSHELF.dot(DOM_BOOKSHELF),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                break;

            case BooklistGroup.RowKind.READ_STATUS:
                // Define how the new field is retrieved and sorted/grouped
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  TBL_BOOKS.dot(DOM_BOOK_READ),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);

                summary.addDomain(DOM_BOOK_READ,
                                  TBL_BOOKS.dot(DOM_BOOK_READ),
                                  //FIXME: run a cleanup of data in db upgrades.
                                  // We want the READ flag at the lowest level only.
                                  // Some bad data means that it may be 0 or 'f',
                                  // so we don't group by it.
                                  SummaryBuilder.FLAG_NONE);
                break;

            case BooklistGroup.RowKind.PUBLISHER:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  TBL_BOOKS.dot(DOM_BOOK_PUBLISHER),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                break;

            case BooklistGroup.RowKind.GENRE:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  TBL_BOOKS.dot(DOM_BOOK_GENRE),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                break;

            case BooklistGroup.RowKind.LANGUAGE:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  TBL_BOOKS.dot(DOM_BOOK_LANGUAGE),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                break;

            case BooklistGroup.RowKind.LOCATION:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  TBL_BOOKS.dot(DOM_BOOK_LOCATION),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                break;

            case BooklistGroup.RowKind.FORMAT:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  TBL_BOOKS.dot(DOM_BOOK_FORMAT),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                break;

            case BooklistGroup.RowKind.TITLE_LETTER:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  "SUBSTR(" + TBL_BOOKS.dot(DOM_TITLE) + ",1,1)",
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                break;

            case BooklistGroup.RowKind.RATING:
                // sorting should be descending.
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  "CAST(" + TBL_BOOKS.dot(DOM_BOOK_RATING) + " AS INTEGER)",
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                          | sortDescendingMask);
                break;


            //TOMF: check with original code what this quote really means.
            // It was on most, but not all date rows:
            // ----------------------------------
            // TODO: Handle 'DESCENDING'. Requires the navigator construction to use
            // max/min for non-grouped domains that appear in sublevels based on desc/asc.
            // We don't use DESCENDING sort yet because the 'header' ends up below
            // the detail rows in the flattened table.
            // ----------------------------------

            // Also: sortDescendingMask: original code had it added to "date added" and
            // "date last updated" but not to others.
            // Commented ones mine, added as reminder)
            case BooklistGroup.RowKind.DATE_PUBLISHED_YEAR:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  yearGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_PUBLISHED), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_PUBLISHED_MONTH:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  monthGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_PUBLISHED), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_FIRST_PUBLICATION_YEAR:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  yearGlob(TBL_BOOKS.dot(DOM_FIRST_PUBLICATION), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_FIRST_PUBLICATION_MONTH:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  monthGlob(TBL_BOOKS.dot(DOM_FIRST_PUBLICATION), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_READ_YEAR:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  yearGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_READ_MONTH:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  monthGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_READ_DAY:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  dayGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                // | sortDescendingMask);
                break;


            case BooklistGroup.RowKind.DATE_ACQUIRED_YEAR:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  yearGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_ACQUIRED_MONTH:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  monthGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_ACQUIRED_DAY:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  dayGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                // | sortDescendingMask);
                break;


            case BooklistGroup.RowKind.DATE_ADDED_YEAR:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  yearGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                          | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_ADDED_MONTH:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  monthGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                          | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_ADDED_DAY:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  dayGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ADDED), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                          | sortDescendingMask);
                break;


            case BooklistGroup.RowKind.DATE_LAST_UPDATE_YEAR:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  yearGlob(TBL_BOOKS.dot(DOM_LAST_UPDATE_DATE), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                          | sortDescendingMask);

                summary.addDomain(DOM_LAST_UPDATE_DATE,
                                  null,
                                  SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_LAST_UPDATE_MONTH:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  monthGlob(TBL_BOOKS.dot(DOM_LAST_UPDATE_DATE), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                          | sortDescendingMask);

                summary.addDomain(DOM_LAST_UPDATE_DATE,
                                  null,
                                  SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_LAST_UPDATE_DAY:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  dayGlob(TBL_BOOKS.dot(DOM_LAST_UPDATE_DATE), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                          | sortDescendingMask);

                summary.addDomain(DOM_LAST_UPDATE_DATE,
                                  null,
                                  SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                break;


            // NEWKIND: RowKind.ROW_KIND_x

            case BooklistGroup.RowKind.BOOK:
                // nothing to do.
                break;

            default:
                throw new IllegalTypeException("" + booklistGroup.getKind());
        }
    }

    /**
     * Build the 'join' statement based on the groups and extra criteria.
     */
    @NonNull
    private String buildJoin(@NonNull final BuildInfoHolder buildInfoHolder) {

        JoinContext join;

        // If there is a bookshelf specified, start the join there.
        // Otherwise, start with the BOOKS table.
        if (buildInfoHolder.hasGroupBOOKSHELF || (mFilterOnBookshelfId > 0)) {
            join = new JoinContext(TBL_BOOKSHELF)
                    .join(TBL_BOOK_BOOKSHELF)
                    .join(TBL_BOOKS);
        } else {
            join = new JoinContext(TBL_BOOKS);
        }

        // If a LOANED level is present, we are ONLY interested in loaned books. So cross it here.
        if (buildInfoHolder.hasGroupLOANED) {
            join.leftOuterJoin(TBL_BOOK_LOANEE);
        }

        // Now join with author; we must specify a parent in the join, because the last table
        // joined was one of BOOKS or LOAN and we don't know which. So we explicitly use books.
        join.join(TBL_BOOKS, TBL_BOOK_AUTHOR);

        // If there is no author group, or the user only wants primary author, get primary only
        if (buildInfoHolder.authorGroup == null || !buildInfoHolder.authorGroup.showAllAuthors()) {
            join.append(" AND " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_POSITION) + " == 1");
        }

        // Join with authors to make the names available
        join.join(TBL_AUTHORS);

        // Current table will be authors, so name parent explicitly to join books->book_series.
        join.leftOuterJoin(TBL_BOOKS, TBL_BOOK_SERIES);

        // If there was no series group, or user requests primary series only,
        // then just get primary series.
        if (buildInfoHolder.seriesGroup == null || !buildInfoHolder.seriesGroup.showAllSeries()) {
            join.append(" AND " + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + "==1");
        }

        // Join with series to get name
        join.leftOuterJoin(TBL_SERIES);

        return join.getSql();
    }

    /**
     * Create the WHERE clause based on all filters.
     *
     * @return a full WHERE clause (including keyword 'WHERE'), or the empty string for none.
     */
    @NonNull
    private String buildWhereClause() {
        StringBuilder where = new StringBuilder();

        // Add local Filters
        for (Filter filter : mFilters) {
            if (where.length() != 0) {
                where.append(" AND ");
            }
            where.append(' ').append(filter.getExpression());
        }

        // Add BooklistStyle Filters
        for (Filter filter : mStyle.getFilters().values()) {
            String filterSql = filter.getExpression();
            if (filterSql != null) {
                if (where.length() != 0) {
                    where.append(" AND ");
                }
                where.append(' ').append(filterSql);
            }
        }

        // all done
        if (where.length() > 0) {
            return where.insert(0, " WHERE ").toString();
        } else {
            return "";
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
    private String makeNestedTriggers(@NonNull final SummaryBuilder summary) {
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
        for (SortedDomainInfo domainInfo : summary.getSortedColumns()) {
            if (!sortedDomainNames.contains(domainInfo.domain.name)) {
                sortedDomainNames.add(domainInfo.domain.name);
                if (sortedCols.length() > 0) {
                    sortedCols.append(", ");
                    currInsertSql.append(", ");
                }
                sortedCols.append(domainInfo.domain.name);
                currInsertSql.append("new.").append(domainInfo.domain.name);
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
                            + ",new." + DOM_BL_ROOT_KEY);

            // Create the conditional to detect if next level up is already defined
            // (by checking the 'current' record/table)
            StringBuilder conditionSql = new StringBuilder();
            // Update the statement components
            //noinspection ConstantConditions
            for (DomainDefinition groupDomain : group.getDomains()) {
                insertSql.append(',').append(groupDomain);
                valuesSql.append(", new.").append(groupDomain);
                // Only update the 'condition' part if it is part of the SORT list
                if (sortedDomainNames.contains(groupDomain.name)) {
                    if (conditionSql.length() > 0) {
                        conditionSql.append(" AND ");
                    }
                    conditionSql.append("Coalesce(" + currentTableAlias + '.').append(groupDomain)
                                .append(",'')=Coalesce(New.").append(groupDomain).append(",'')")
                                .append(DBA.COLLATION);
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
                    + "\n WHEN New." + DOM_BL_NODE_LEVEL + '=' + (levelId + 1) + " AND NOT EXISTS("
                    + "SELECT 1 FROM " + currentTableName + ' ' + currentTableAlias
                    + " WHERE " + conditionSql
                    + ')'
                    + "\n BEGIN"
                    + "\n   " + insertSql + ';'
                    + "\n END";
            SynchronizedStatement stmt = mStatements.add(tgName, tgSql);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
                Logger.debug(this,
                             "makeNestedTriggers",
                             "add|" + tgName + '|' + stmt.toString());
            }
            mLevelBuildStmts.add(stmt);
            stmt.execute();
        }

        // Create a trigger to maintain the 'current' value -- just delete and insert
        String currentValueTriggerName = mListTable + "_TG_ZZZ";
        mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + currentValueTriggerName);
        String tgSql = "\nCREATE TEMP TRIGGER " + currentValueTriggerName
                + " AFTER INSERT ON " + mListTable + " FOR EACH ROW"
                + "\n WHEN New." + DOM_BL_NODE_LEVEL + '=' + mStyle.groupCount()
                // + " AND NOT EXISTS(SELECT 1 FROM " + currTblName + ' ' + currentTableAlias
                // + " WHERE " + conditionSql + ")\n"
                + "\n BEGIN"
                + "\n   DELETE FROM " + currentTableName + ';'
                + "\n   INSERT INTO " + currentTableName + " VALUES (" + currInsertSql + ");"
                + "\n END";

        SynchronizedStatement stmt = mStatements.add(currentValueTriggerName, tgSql);
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
            Logger.debug(this,
                         "makeNestedTriggers",
                         "add", currentValueTriggerName, stmt);
        }
        mLevelBuildStmts.add(stmt);
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
    private String makeSingleTrigger(@NonNull final SummaryBuilder summary) {
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
                fullValues.append("new.").append(d);
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
        for (SortedDomainInfo domainInfo : summary.getSortedColumns()) {
            if (!sortedDomainNames.contains(domainInfo.domain.name)) {
                sortedDomainNames.add(domainInfo.domain.name);
                if (sortedCols.length() > 0) {
                    sortedCols.append(',');
                    currInsertSql.append(',');
                }
                sortedCols.append(domainInfo.domain.name);
                currInsertSql.append("new.").append(domainInfo.domain.name);
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
        // "(" + DOM_BL_NODE_LEVEL + ", " + sortedCols + ", " + DOM_FK_BOOK_ID + ")");

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
                            + ',' + "new." + DOM_BL_ROOT_KEY);

            // Create the conditional to detect if next level up is already defined
            // (by checking the 'current' record/table)
            StringBuilder conditionSql = new StringBuilder();
            // Update the statement components
            //noinspection ConstantConditions
            for (DomainDefinition d : group.getDomains()) {
                insertSql.append(',').append(d);
                valuesSql.append(", new.").append(d);
                // Only update the 'condition' part if it is part of the SORT list
                if (sortedDomainNames.contains(d.name)) {
                    if (conditionSql.length() > 0) {
                        conditionSql.append(" AND ");
                    }
                    conditionSql.append("Coalesce(l.").append(d)
                                .append(",'') = Coalesce(new.").append(d)
                                .append(",'')")
                                .append(DBA.COLLATION).append('\n');
                }
            }
            //insertSql += ")\n	SELECT " + valuesSql
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


        SynchronizedStatement stmt =
                mStatements.add(tgForwardName, oneBigTrigger.toString());

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
            Logger.debug(this,
                         "makeSingleTrigger",
                         "add", tgForwardName, stmt);
        }
        mLevelBuildStmts.add(stmt);
        stmt.execute();

        return viewTblName;
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
        stmt.bindLong(1, mStyle.getGroupKindAt(0));
        stmt.executeUpdateDelete();
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

            deleteListNodeSettings();

            SynchronizedStatement stmt = mStatements.get(STMT_SAVE_ALL_LIST_NODE_SETTINGS);
            if (stmt == null) {
                stmt = mStatements.add(STMT_SAVE_ALL_LIST_NODE_SETTINGS,
                                       TBL_BOOK_LIST_NODE_SETTINGS
                                               .getInsert(false,
                                                          DOM_BL_NODE_ROW_KIND,
                                                          DOM_BL_ROOT_KEY)
                                               + " SELECT DISTINCT ?," + DOM_BL_ROOT_KEY
                                               + " FROM " + mNavTable
                                               + " WHERE " + DOM_BL_NODE_EXPANDED + "=1"
                                               + " AND " + DOM_BL_NODE_LEVEL + "=1");
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
                stmt = mStatements.add(STMT_SAVE_LIST_NODE_SETTING,
                                       TBL_BOOK_LIST_NODE_SETTINGS
                                               .getInsert(false,
                                                          DOM_BL_NODE_ROW_KIND,
                                                          DOM_BL_ROOT_KEY)
                                               + " SELECT ?, " + DOM_BL_ROOT_KEY
                                               + " FROM " + mNavTable
                                               + " WHERE " + DOM_BL_NODE_EXPANDED + "=1"
                                               + " AND " + DOM_BL_NODE_LEVEL + "=1"
                                               + " AND " + DOM_PK_ID + "=?");
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
     * Get all positions at which the specified book appears.
     *
     * @return Array of row details, including absolute positions and visibility or
     * null if not present.
     */
    @Nullable
    public ArrayList<BookRowInfo> getBookAbsolutePositions(final long bookId) {
        String sql = "SELECT " + mNavTable.dot(DOM_PK_ID)
                + ',' + mNavTable.dot(DOM_BL_NODE_VISIBLE)
                + " FROM " + mListTable + " bl " + mListTable.join(mNavTable)
                + " WHERE " + mListTable.dot(DOM_FK_BOOK_ID) + "=?";

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(bookId)})) {
            ArrayList<BookRowInfo> rows = new ArrayList<>();
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
     * @return a list of column names that will be in the list for cursor implementations.
     */
    @NonNull
    String[] getListColumnNames() {
        // Get the domains
        List<DomainDefinition> domains = mListTable.getDomains();
        // Make the array and allow for DOM_BL_ABSOLUTE_POSITION
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
            domains.append(prefix)
                   .append(domain.name)
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
    public BooklistPseudoCursor getList() {
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
     * @return the number of book records in the list
     */
    int getBookCount() {
        return pseudoCount("ListTableBooks",
                           "SELECT COUNT(*)"
                                   + " FROM " + mListTable
                                   + " WHERE "
                                   + DOM_BL_NODE_LEVEL + '=' + (mStyle.groupCount() + 1));
    }

    /**
     * @return the number of unique book records in the list
     */
    int getUniqueBookCount() {
        return pseudoCount("ListTableUniqueBooks",
                           "SELECT COUNT(DISTINCT " + DOM_FK_BOOK_ID + ')'
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
        @SuppressWarnings("UnusedAssignment")
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
    int levels() {
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
        boolean isVisible = (1 == stmt.simpleQueryForLongOrZero());

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
                    "SELECT " + DOM_PK_ID + "||'/'||" + DOM_BL_NODE_EXPANDED
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
        } catch (SQLiteDoneException ignore) {
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

            @SuppressWarnings("UnusedAssignment")
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
                Logger.debug(this,
                             "expandAll",
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

            // row position starts at 0, id's start at 1...
            final long rowId = absPos + 1;

            // Get the details of the passed row position.
            SynchronizedStatement getNodeLevelStmt = mStatements.get(STMT_GET_NODE_LEVEL);
            if (getNodeLevelStmt == null) {
                getNodeLevelStmt = mStatements.add(
                        STMT_GET_NODE_LEVEL,
                        "SELECT " + DOM_BL_NODE_LEVEL + "||'/'||" + DOM_BL_NODE_EXPANDED
                                + " FROM " + mNavTable.ref()
                                + " WHERE " + mNavTable.dot(DOM_PK_ID) + "=?");
            }
            getNodeLevelStmt.bindLong(1, rowId);

            String[] info;
            try {
                info = getNodeLevelStmt.simpleQueryForString().split("/");
            } catch (SQLiteDoneException ignore) {
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
                        "SELECT Coalesce(max(" + DOM_PK_ID + "),-1) FROM "
                                + "(SELECT " + DOM_PK_ID + " FROM " + mNavTable.ref()
                                + " WHERE "
                                + mNavTable.dot(DOM_PK_ID) + ">?"
                                + " AND "
                                + mNavTable.dot(DOM_BL_NODE_LEVEL) + "=?"
                                + " ORDER BY " + DOM_PK_ID + " LIMIT 1"
                                + ") zzz");
            }
            getNextAtSameLevelStmt.bindLong(1, rowId);
            getNextAtSameLevelStmt.bindLong(2, level);
            // the Coalesce(max(" + DOM_PK_ID + "),-1) prevents SQLiteDoneException
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

            // Update settings
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

    @NonNull
    public Context getContext() {
        return mContext;
    }

    /**
     * General cleanup routine called by both {@link #close()} and {@link #finalize()}.
     *
     * @param isFinalize set in the finalize[] method call
     */
    private void cleanup(final boolean isFinalize) {
        if (!mStatements.isEmpty()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER && isFinalize) {
                Logger.debug(this,
                             "cleanup",
                             "Finalizing with active mStatements (this is not an error): ");
                for (String name : mStatements.getNames()) {
                    Logger.debug(this, "cleanup", name);
                }
            }

            mStatements.close();
        }

        if (mNavTable != null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER && isFinalize) {
                Logger.debug(this,
                             "cleanup",
                             "Finalizing with mNavTable (this is not an error)");
            }

            try {
                mNavTable.clear();
                mNavTable.drop(mSyncedDb);
            } catch (RuntimeException e) {
                Logger.error(this, e);
            }
        }
        if (mListTable != null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER && isFinalize) {
                Logger.debug(this,
                             "cleanup",
                             "Finalizing with mListTable (this is not an error)");
            }

            try {
                mListTable.clear();
                mListTable.drop(mSyncedDb);
            } catch (RuntimeException e) {
                Logger.error(this, e);
            }
        }

        mDb.close();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOKLIST_BUILDER) {
            if (!mDebugReferenceDecremented) {
                // Only de-reference once!
                Logger.debug(this,
                             "cleanup",
                             "instances left: " + DEBUG_INSTANCE_COUNTER.decrementAndGet());
            }
            mDebugReferenceDecremented = true;
        }
    }

    /**
     * Close the builder.
     */
    @Override
    public void close() {
        cleanup(false);
    }

    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        cleanup(true);
        super.finalize();
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

        public static CompatibilityMode get() {
            if (instance == null) {
                instance = new CompatibilityMode();
            }
            return instance;
        }
    }

    /**
     * A domain + desc/asc sorting flag.
     */
    public static class SortedDomainInfo {

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
     * Structure used to store components of the SQL required to build the list.
     * We use this for experimenting with alternate means of construction.
     */
    private static class SqlComponents {

        public String destinationColumns;
        /** constructed & assigned, but not used right now (2019-03-29). */
        public String rootKeyExpression;
        public String join;
        public String insert;
        public String select;
        public String insertSelect;
        /** constructed & assigned, but not used right now (2019-03-29). */
        public String insertValues;
        public String where;

        /** List of column names appropriate for 'ORDER BY' clause. */
        String orderByColumns;
        /** List of column names appropriate for 'CREATE INDEX' column list. */
        String sortIndexColumnList;

    }

    /**
     * Record containing details of the positions of all instances of a single book.
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
     * A Holder class used to pass around multiple variables.
     * Allowed us to break up the big 'build' method as it existed in the original code.
     */
    private static class BuildInfoHolder {

        /** Will be set to appropriate Group if a Series group exists in style. */
        BooklistGroup.BooklistSeriesGroup seriesGroup;
        /** Will be set to appropriate Group if an Author group exists in style. */
        BooklistGroup.BooklistAuthorGroup authorGroup;
        /** Will be set to <tt>true</tt> if a LOANED group exists in style. */
        boolean hasGroupLOANED;
        /** Will be set to <tt>true</tt> if a BOOKSHELF group exists in style. */
        boolean hasGroupBOOKSHELF;
    }

    /**
     * Details of extra domain requested by caller before the build() method is called.
     */
    private static class ExtraDomainDetails {

        /**
         * Domain definition of domain to add.
         */
        DomainDefinition domain;
        /**
         * Expression to use in deriving domain value.
         */
        String sourceExpression;
        /**
         * Indicates if domain is to be part of the list sort key.
         */
        boolean isSorted;
    }

    /**
     * Accumulate data for the build() method.
     */
    private class SummaryBuilder {

        /** Flag (bitmask) indicating added domain has no special properties. */
        static final int FLAG_NONE = 0;
        /** Flag indicating added domain is SORTED. */
        static final int FLAG_SORTED = 1;
        /** Flag indicating added domain is GROUPED. */
        static final int FLAG_GROUPED = 1 << 1;

        // Not currently used.
        ///** Flag indicating added domain is part of the unique key. */
        //static final int FLAG_KEY = 1 << 2;

        /**
         * Flag indicating added domain should be SORTED in descending order.
         * DO NOT USE FOR GROUPED DATA. See notes below.
         */
        static final int FLAG_SORT_DESCENDING = 1 << 3;

        /** Domains required in output table. */
        private final ArrayList<DomainDefinition> mDomains = new ArrayList<>();
        /** Source expressions for output domains. */
        private final ArrayList<String> mExpressions = new ArrayList<>();
        /** Mapping from Domain to source Expression. */
        private final Map<DomainDefinition, String> mExpressionMap = new HashMap<>();

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
        private final Set<DomainDefinition> mSortedColumnsSet = new HashSet<>();

        /**
         * Add a domain and source expression to the summary.
         *
         * @param domain     Domain to add
         * @param expression Source Expression
         * @param flags      Flags indicating attributes of new domain
         */
        void addDomain(@NonNull final DomainDefinition domain,
                       @Nullable final String expression,
                       final int flags) {
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
            if ((flags & FLAG_GROUPED) != 0) {
                mGroupedDomains.add(domain);
            }

            if ((flags & FLAG_SORTED) != 0 && !mSortedColumnsSet.contains(domain)) {
                mSortedColumns.add(
                        new SortedDomainInfo(domain, (flags & FLAG_SORT_DESCENDING) != 0));
                mSortedColumnsSet.add(domain);
            }

            // Not currently used
            //if ((flags & FLAG_KEY) != 0)
            //	mKeys.add(domain);
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
            //TOMF TODO: shallow copy, is that enough or a bug ? check calling code
            //      Returns a shallow copy of this <tt>ArrayList</tt> instance.
            //     (The elements themselves are not copied.)
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
            @SuppressWarnings("UnusedAssignment")
            final long t0 = System.nanoTime();
            mListTable.drop(mSyncedDb);
            @SuppressWarnings("UnusedAssignment")
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
         * 'INSERT INTO.[mListTable]..([mDomains])..SELECT..[expressions]..FROM'
         *
         * @param rootKey The key for the root level group. Stored in each row and used
         *                to determine the expand/collapse results.
         *
         * @return SqlComponents structure
         */
        @NonNull
        SqlComponents buildSqlComponents(@NonNull final BooklistGroup.CompoundKey rootKey) {
            // Rebuild the data table
            recreateTable();
            // List of column names for the INSERT... part
            StringBuilder destColumns = new StringBuilder();
            // List of expressions for the SELECT... part.
            StringBuilder expressions = new StringBuilder();
            // List of ?'s for the VALUES... part.
            StringBuilder values = new StringBuilder();

            // Build the lists. mDomains and mExpressions were built in sync with each other.
            for (int i = 0; i < mDomains.size(); i++) {
                DomainDefinition domain = mDomains.get(i);
                String expression = mExpressions.get(i);
                if (i > 0) {
                    destColumns.append(',');
                    expressions.append(',');
                    values.append(',');
                }
                destColumns.append(domain.name);
                expressions.append(expression);
                values.append('?');
                // This is not strictly necessary, but makes SQL more readable and debugging easier.
                expressions.append(" AS ").append(domain.name);
            }

            // Build the expression for the root key.
            StringBuilder keyExpression = new StringBuilder('\'' + rootKey.getPrefix());
            for (DomainDefinition d : rootKey.getDomains()) {
                keyExpression.append("/'||Coalesce(").append(mExpressionMap.get(d)).append(",'')");
            }

            // Setup the SQL phrases.
            SqlComponents cmp = new SqlComponents();
            cmp.rootKeyExpression = keyExpression.toString();
            cmp.destinationColumns = destColumns + "," + DOM_BL_ROOT_KEY;
            cmp.insert = "INSERT INTO " + mListTable + " (" + cmp.destinationColumns + ')';
            cmp.select = "SELECT " + expressions + ',' + keyExpression;
            cmp.insertSelect = cmp.insert + ' ' + cmp.select + " FROM ";
            cmp.insertValues = cmp.insert + " VALUES (" + values + ",?)";

            return cmp;
        }
    }
}

//<editor-fold desc="Experimental commented code">
/*
 * Below was an interesting experiment, kept because it may one day get resurrected.
 *
 * The basic idea was to create the list in sorted order to start with by using
 * a BEFORE INSERT trigger on the book_list table. The problems were two-fold:
 *
 * - Android 1.6 has a buggy SQLite that does not like insert triggers that insert on the
 * same table (this is fixed by android 2.0). So the old code would have to be kept anyway,
 * to deal with old Android.
 *
 * - it did not speed things up, probably because of index maintenance. It took about 5% longer.
 *
 * Tests were performed on Android 2.2 using approx. 800 books.
 *
 * Circumstances under which this may be resurrected include:
 *
 * - Huge libraries (so that index build times are compensated for by huge tables).
 * 800 books is close to break-even
 * - Stored procedures in SQLite become available to avoid multiple nested trigger calls.
 *
 */
//
//	/**
//   * Clear and the build the temporary list of books based on the passed criteria.
//   *
//   * @param bookshelf     Search criteria: limit to shelf
//   * @param authorWhere   Search criteria: additional conditions that apply to authors table
//   * @param bookWhere     Search criteria: additional conditions that apply to book table
//   * @param loaned_to     Search criteria: only books loaned to named person
//   * @param seriesName    Search criteria: only books in named series
//   * @param searchText    Search criteria: book details must in some way contain the passed text
//   *
//   */
//  public BooklistCursor build(long previouslySelectedBookId, String bookshelf,
//                              String authorWhere, String bookWhere,
//                              String loaned_to, String seriesName, String searchText) {
//      final long t0 = System.nanoTime();
//      SummaryBuilder summary = new SummaryBuilder();
//      // Add the minimum required domains
//
//      summary.addDomain(DOM_PK_ID);
//      summary.addDomain(DOM_BL_NODE_LEVEL, String.valueOf(mLevels.size()+1), SummaryBuilder.FLAG_NONE);
//      summary.addDomain(DOM_BL_NODE_ROW_KIND, "" + RowKind.BOOK, SummaryBuilder.FLAG_NONE);
//      summary.addDomain(DOM_FK_BOOK_ID, TBL_BOOKS.dot(DOM_PK_ID), SummaryBuilder.FLAG_NONE);
//      summary.addDomain(DOM_BL_BOOK_COUNT, "1", SummaryBuilder.FLAG_NONE);
//      summary.addDomain(DOM_BL_ROOT_KEY);
//      //summary.addDomain(DOM_PARENT_KEY);
//
//      BooklistSeriesLevel seriesLevel = null;
//      BooklistAuthorLevel authorLevel = null;
//      boolean hasLevelLOANED = false;
//
//      final long t0a = System.nanoTime();
//
//      for (BooklistLevel l : mLevels) {
//          //
//          //  Build each row-kind group.
//          //
//          //  ****************************************************************************************
//          //  IMPORTANT NOTE: for each row kind, then FIRST SORTED AND GROUPED domain should be the one
//          //					that will be displayed and that level in the UI.
//          //  ****************************************************************************************
//          //
//			switch (l.kind) {
//
//			case RowKind.SERIES:
//				l.displayDomain = DOM_SERIES_TITLE;
//				seriesLevel = (BooklistSeriesLevel) l; // getLevel(RowKind.SERIES);
//				summary.addDomain(DOM_SERIES_TITLE, TBL_SERIES.dot(DOM_SERIES_TITLE), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
//				summary.addDomain(DOM_FK_SERIES_ID, TBL_BOOK_SERIES.dot(DOM_FK_SERIES_ID), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_KEY);
//				summary.addDomain(DOM_BOOK_SERIES_NUM, TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM), SummaryBuilder.FLAG_NONE);
//				summary.addDomain(DOM_BOOK_SERIES_POSITION, TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION), SummaryBuilder.FLAG_NONE);
//				summary.addDomain(DOM_BL_PRIMARY_SERIES_COUNT,"case when Coalesce(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + ",1) == 1 then 1 else 0 end", SummaryBuilder.FLAG_NONE);
//				summary.addDomain(DOM_BOOK_SERIES_NUM, TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM), SummaryBuilder.FLAG_SORTED);
//				//summary.addKeyComponents("'s'", TBL_BOOK_SERIES.dot(DOM_FK_SERIES_ID));
//				l.setKeyComponents("s", DOM_FK_SERIES_ID);
//				break;
//
//			case RowKind.AUTHOR:
//				l.displayDomain = DOM_AUTHOR_FORMATTED;
//				authorLevel = (BooklistAuthorLevel) l; //getLevel(RowKind.AUTHOR);
//				summary.addDomain(DOM_AUTHOR_SORT, X_AUTHOR_FORMATTED_LAST_FIRST, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
//				if (authorLevel.givenName)
//					summary.addDomain(DOM_AUTHOR_FORMATTED, X_AUTHOR_FORMATTED_FIRST_LAST, SummaryBuilder.FLAG_GROUPED);
//				else
//					summary.addDomain(DOM_AUTHOR_FORMATTED, X_AUTHOR_FORMATTED_LAST_FIRST, SummaryBuilder.FLAG_GROUPED);
//
//				summary.addDomain(DOM_FK_AUTHOR_ID, TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR_ID), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_KEY);
//
//				//summary.addKeyComponents("'a'", TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR_ID));
//				l.setKeyComponents("a", DOM_FK_AUTHOR_ID);
//
//				break;
//
//			case RowKind.GENRE:
//				l.displayDomain = DOM_BOOK_GENRE;
//				summary.addDomain(DOM_BOOK_GENRE, TBL_BOOKS.dot(DOM_BOOK_GENRE), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'g'", TBL_BOOKS.dot(DOM_BOOK_GENRE));
//				l.setKeyComponents("g", DOM_BOOK_GENRE);
//				break;
//
//			case RowKind.PUBLISHER:
//				l.displayDomain = DOM_BOOK_PUBLISHER;
//				summary.addDomain(DOM_BOOK_PUBLISHER, TBL_BOOKS.dot(DOM_BOOK_PUBLISHER), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'p'", TBL_BOOKS.dot(DOM_BOOK_PUBLISHER));
//				l.setKeyComponents("p", DOM_BOOK_PUBLISHER);
//				break;
//
//			case RowKind.ROW_KIND_UNREAD:
//				l.displayDomain = DOM_READ_STATUS;
//				String unreadExpr = "Case When " + TBL_BOOKS.dot(DOM_BOOK_READ) + " = 1 " +
//						" Then '" + BookCatalogueApp.getResString(R.string.booklist_read) + "'" +
//						" Else '" + BookCatalogueApp.getResString(R.string.booklist_unread) + "' end";
//				summary.addDomain(DOM_READ_STATUS, unreadExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
//				summary.addDomain(DOM_BOOK_READ, TBL_BOOKS.dot(DOM_BOOK_READ), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'r'", TBL_BOOKS.dot(DOM_BOOK_READ));
//				l.setKeyComponents("r", DOM_BOOK_READ);
//				break;
//
//			case RowKind.LOANED:
//				hasLevelLOANED = true;
//				l.displayDomain = DOM_BOOK_LOANEE;
//				summary.addDomain(DOM_BOOK_LOANEE, LOANED_TO_EXPRESSION, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'l'", DatabaseDefinitions.TBL_BOOK_LOANEE.dot(DOM_BOOK_LOANEE));
//				l.setKeyComponents("l", DOM_BOOK_LOANEE);
//				break;
//
//			case RowKind.TITLE_LETTER:
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
//		final long t0b = System.nanoTime();
//
//		if (previouslySelectedBookId != 0) {
//			summary.addDomain(DOM_BL_SELECTED, TBL_BOOKS.dot(DOM_PK_ID) + " = " + previouslySelectedBookId, SummaryBuilder.FLAG_NONE);
//		}
//
//		// Ensure any caller-specified extras (eg. title) are added at the end.
//		for(Entry<String,ExtraDomainInfo> d : mExtraDomains.entrySet()) {
//			ExtraDomainInfo info = d.get();
//			int flags;
//			if (info.isSorted)
//				flags = SummaryBuilder.FLAG_SORTED;
//			else
//				flags = SummaryBuilder.FLAG_NONE;
//			summary.addDomain(info.domain, info.sourceExpression, flags);
//		}
//		final long t0c = System.nanoTime();
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
//		final long t0d = System.nanoTime();
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
//						" On " + ALIAS_BOOK_BOOKSHELF + "." + KEY_BOOKSHELF + " = " + ALIAS_BOOKSHELF + "." + KEY_ID ;
//				sql +=	"    join " + DB_TB_BOOKS_AND_ALIAS + " on " + ALIAS_BOOKS + "." + KEY_ID + " = " + ALIAS_BOOK_BOOKSHELF + "." + KEY_BOOK_ID + "\n";
//			} else {
//				sql +=	"    " + DB_TB_BOOKS_AND_ALIAS + "\n";
//			}
//			*/
//
//		// If a LOANED level is present, we are ONLY interested in loaned books. So cross it here.
//		if (hasLevelLOANED) {
//			join.join(TBL_BOOK_LOANEE);
//		}
//
//		// Specify a parent in the join, because the last table joined was one of BOOKS or LOAN.
//		join.join(TBL_BOOKS, TBL_BOOK_AUTHOR);
//		if (authorLevel == null || !authorLevel.allAuthors) {
//			join.append( "		and " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_POSITION) + " == 1\n");
//		}
//		join.join(TBL_AUTHORS);
//		join.leftOuterJoin(TBL_BOOKS, TBL_BOOK_SERIES);
//			/*
//			sql +=	"    join " + DB_TB_BOOK_AUTHOR_AND_ALIAS + " on " + ALIAS_BOOK_AUTHOR + "." + KEY_BOOK_ID + " = " + ALIAS_BOOKS + "." + KEY_ID + "\n" +
//					"    join " + DB_TB_AUTHORS_AND_ALIAS + " on " + ALIAS_AUTHORS + "." + KEY_ID + " = " + ALIAS_BOOK_AUTHOR + "." + KEY_AUTHOR + "\n";
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
//		final long t0e = System.nanoTime();
//		String where = "";
//
//		if (!bookshelf.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + " = '" + DBA.encodeString(bookshelf) + "')";
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
//			where += "Exists(Select NULL From " + TBL_BOOK_LOANEE.ref() + " Where " + TBL_BOOK_LOANEE.dot(DOM_BOOK_LOANEE) + " = '" + encodeString(loaned_to) + "'" +
//					" and " + TBL_BOOK_LOANEE.fkMatch(TBL_BOOKS) + ")";
//					// .and()    .op(TBL_BOOK_LOANEE.dot(DOM_FK_BOOK_ID), "=", TBL_BOOKS.dot(DOM_PK_ID)) + ")";
//		}
//		if (!seriesName.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + TBL_SERIES.dot(DOM_SERIES_TITLE) + " = '" + encodeString(seriesName) + "')";
//		}
//		if(!searchText.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + TBL_BOOKS.dot(DOM_PK_ID) + " in (select docid from " + DB_TB_BOOKS_FTS + " where " + DB_TB_BOOKS_FTS + " match '" + encodeString(searchText) + "'))";
//		}
//
//		// If we got any conditions, add them to the initial insert statement
//		if (!where.equals(""))
//			sql += " where " + where.toString();
//
//		final long t1 = System.nanoTime();
//
//		{
//			final ArrayList<DomainDefinition> sort = summary.getExtraSort();
//			final StringBuilder sortCols = new StringBuilder();
//			for (DomainDefinition d: sort) {
//				sortCols.append(d.name);
//				sortCols.append(DBA.COLLATION + ", ");
//			}
//			sortCols.append(DOM_BL_NODE_LEVEL.name);
//			mSortColumnList = sortCols.toString();
//		}
//
//		{
//			final ArrayList<DomainDefinition> group = summary.cloneGroups();
//			final StringBuilder groupCols = new StringBuilder();;
//			for (DomainDefinition d: group) {
//				groupCols.append(d.name);
//				groupCols.append(DBA.COLLATION + ", ");
//			}
//			groupCols.append( DOM_BL_NODE_LEVEL.name );
//			mGroupColumnList = groupCols.toString();
//		}
//
//		{
//			final ArrayList<DomainDefinition> keys = summary.getKeys();
//			final StringBuilder keyCols = new StringBuilder();;
//			for (DomainDefinition d: keys) {
//				keyCols.append(d.name);
//				keyCols.append(DBA.COLLATION + ", ");
//			}
//			keyCols.append( DOM_BL_NODE_LEVEL.name );
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
//		//mSyncedDb.execSQL("Create Index " + mListTable + "_IX_TG on " + mListTable + "(" + DOM_BL_NODE_LEVEL + ", " + mGroupColumnList + ")");
//		mSyncedDb.execSQL("Create Unique Index " + mListTable + "_IX_TG1 on " + mListTable + "(" + DOM_BL_NODE_LEVEL + ", " + mKeyColumnList + ", " + DOM_FK_BOOK_ID + ")");
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
//				if (!d.equals(DOM_PK_ID)) {
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
//			SQLiteStatement stmt = mStatements.add(tgForwardName, tgForwardSql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();
//		}
//		*/
//
//		// Now make some BEFORE INSERT triggers to build hierarchy;
//      // no trigger on root level (index = 0).
//		//String[] tgLines = new String[mLevels.size()];
//
//		for (int i = mLevels.size()-1; i >= 0; i--) {
//			final BooklistLevel l = mLevels.get(i);
//			final int levelId = i + 1;
//			String insertSql = "INSERT INTO " + mListTable + "( " + DOM_BL_NODE_LEVEL
//                   + ',' + DOM_BL_NODE_ROW_KIND + ", " + DOM_BL_ROOT_KEY + "\n";
//			// If inserting with forwarding table:
//          //  String insertSql = "INSERT INTO " + TBL_BOOK_LIST + "( " + DOM_BL_NODE_LEVEL
//         //        + ',' + DOM_BL_NODE_ROW_KIND + ", " + DOM_BL_ROOT_KEY + "\n";

//			// If inserting in one trigger using multiple 'exists':
//          //  String valuesSql = levelId + ", " + l.kind + ", " + "new." + DOM_BL_ROOT_KEY + "\n";
//			String valuesSql = "VALUES (" + levelId + ", " + l.kind + ", " + "new." + DOM_BL_ROOT_KEY + "\n";
//			String conditionSql = "l." + DOM_BL_NODE_LEVEL + " = " + levelId + "\n";
//			for(DomainDefinition d  : l.groupDomains) {
//				insertSql += ", " + d;
//				valuesSql += ", new." + d;
//				if (summary.getKeys().contains(d))
//					conditionSql += "	and l." + d + " = new." + d + DBA.COLLATION + "\n";
//			}
//			//insertSql += ")\n	SELECT " + valuesSql + " WHERE NOT EXISTS (SELECT 1 FROM " + mListTable + " l WHERE " + conditionSql + ")";
//			//tgLines[i] = insertSql;
//
//			insertSql += ")\n" + valuesSql + ")";
//			String tgName = "header_A_tg_Level" + i;
//			mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + tgName);
//			// If using forwarding table: String tgSql = "CREATE TRIGGER " + tgName + " INSTEAD OF INSERT ON " + TBL_BOOK_LIST + " FOR EACH ROW WHEN new.level = " + (levelId+1) +
//			String tgSql = "CREATE TEMP TRIGGER " + tgName + " BEFORE INSERT ON " + mListTable + " FOR EACH ROW WHEN new.level = " + (levelId+1) +
//					" and not exists(SELECT 1 FROM " + mListTable + " l WHERE " + conditionSql + ")\n" +
//					"	BEGIN\n" +
//					"		" + insertSql + ";\n" +
//					"	END";
//			SQLiteStatement stmt = mStatements.add(tgName, tgSql);
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
//		tgSql += "\n	" + fullIns + ") " + values + ");\n";
//		tgSql += "	End";
//
//		SQLiteStatement tgStmt = mStatements.add(tgName, tgSql);
//		mLevelBuildStmts.add(tgStmt);
//		tgStmt.execute();
//		*/
//
//		// We are good to go.
//		final long t1a = System.nanoTime();
//		mSyncedDb.beginTransaction();
//		long t1b = System.nanoTime();
//		try {
//			// Build the lowest level summary using our initial insert statement
//			//mBaseBuildStmt = mStatements.add("mBaseBuild", sql + ") zzz Order by " + mGroupColumnList);
//			mBaseBuildStmt = mStatements.add("mBaseBuild", sql );
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
//			long t1c = System.nanoTime();
//			//mSyncedDb.execSQL(ix3cSql);
//			long t1d = System.nanoTime();
//			//mSyncedDb.analyze(mListTable);
//
//			long t2 = System.nanoTime();
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
//					//collatedCols += ",\n	" + d.name + DBA.COLLATION;
//					collatedCols += "\n	" + d.name + DBA.COLLATION;
//				}
//				sql = "INSERT INTO " + mListTable + "(\n	" + DOM_BL_NODE_LEVEL + ",\n	" + DOM_BL_NODE_ROW_KIND +
//						//",\n	" + DOM_PARENT_KEY +
//						cols + "," + DOM_BL_ROOT_KEY +
//						")" +
//						"\n SELECT " + levelId + " AS " + DOM_BL_NODE_LEVEL + ",\n	" + l.kind + " AS " + DOM_BL_NODE_ROW_KIND +
//						//l.getKeyExpression() +
//						cols + "," + DOM_BL_ROOT_KEY +
//						"\n FROM " + mListTable + "\n " + " WHERE level = " + (levelId+1) +
//						"\n GROUP BY " + collatedCols + "," + DOM_BL_ROOT_KEY + DBA.COLLATION;
//						//"\n GROUP BY " + DOM_BL_NODE_LEVEL + ", " + DOM_BL_NODE_ROW_KIND + collatedCols;
//
//				SQLiteStatement stmt = mStatements.add("L" + i, sql);
//				mLevelBuildStmts.add(stmt);
//				stmt.execute();
//				t2a[pos++] = System.nanoTime();
//			}
//			*/
//
//			String ix1Sql = "Create Index " + mListTable + "_IX1 on " + mListTable + "(" + mSortColumnList + ")";
//
//			SQLiteStatement stmt;
//			long t3 = System.nanoTime();
//			stmt = mStatements.add("ix1", ix1Sql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();
//			long t3a = System.nanoTime();
//			mSyncedDb.analyze(mListTable);
//			long t3b = System.nanoTime();
//
//			// Now build a lookup table to match row sort position to row ID. This is used to match a specific
//			// book (or other row in result set) to a position directly.
//			mNavTable.drop(mSyncedDb);
//			mNavTable.create(mSyncedDb, true, false);
//			sql = mNavTable.getInsert(DOM_BL_REAL_ROW_ID, DOM_BL_NODE_LEVEL, DOM_BL_ROOT_KEY, DOM_BL_NODE_VISIBLE, DOM_BL_NODE_EXPANDED) +
//					" Select " + mListTable.dot(DOM_PK_ID) + "," + mListTable.dot(DOM_BL_NODE_LEVEL) + "," + mListTable.dot(DOM_BL_ROOT_KEY) +
//					" ,\n	Case When " + DOM_BL_NODE_LEVEL + " = 1 Then 1 \n" +
//					"	When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY) + " IS NULL Then 0\n	Else 1 end,\n "+
//					"	Case When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY) + " IS NULL Then 0 Else 1 end\n"+
//					" From " + mListTable.ref() + "\n	left outer join " + TBL_BOOK_LIST_NODE_SETTINGS.ref() +
//					"\n		On " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_ROOT_KEY) + " = " + mListTable.dot(DOM_BL_ROOT_KEY) +
//					"\n			And " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_NODE_ROW_KIND) + " = " + mLevels.get(0).kind +
//					"\n	Order by " + mSortColumnList;
//
//			stmt = mStatements.add("InsNav", sql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();
//
//			long t4 = System.nanoTime();
//			mSyncedDb.execSQL("Create Index " + mNavTable + "_IX1" + " On " + mNavTable + "(" + DOM_BL_NODE_LEVEL + "," + DOM_BL_NODE_EXPANDED + "," + DOM_BL_ROOT_KEY + ")");
//			long t4a = System.nanoTime();
//			// Essential for main query! If not present, will make getCount() take ages because main query is a cross with no index.
//			mSyncedDb.execSQL("Create Unique Index " + mNavTable + "_IX2" + " On " + mNavTable + "(" + DOM_BL_REAL_ROW_ID + ")");
//			long t4b = System.nanoTime();
//			mSyncedDb.analyze(mNavTable);
//			long t4c = System.nanoTime();
//
//			/*
//			// Create Index book_list_tmp_row_pos_1_ix2 on book_list_tmp_row_pos_1(level, expanded, root_key);
//			long t5 = System.nanoTime();
//			sql = "UPDATE " + navName + " SET expanded = 1 WHERE level=1 AND " +
//					"exists(SELECT _id FROM " + TBL_BOOK_LIST_NODE_SETTINGS + " x2 WHERE x2.kind=" + mLevels.get(0).kind + " and x2.root_key = " + navName + ".root_key)";
//			mSyncedDb.execSQL(sql);
//			long t6 = System.nanoTime();
//			sql = "Update " + navName + " set visible = 1, expanded = 1 where level > 1 and " +
//					"exists(Select _id From " + navName + " x2 Where x2.level = 1 and x2.root_key = " + navName + ".root_key and x2.expanded=1)";
//			mSyncedDb.execSQL(sql);
//			long t7 = System.nanoTime();
//			sql = "Update " + navName + " set visible = 1 where level = 1";
//			mSyncedDb.execSQL(sql);
//			*/
//
//			long t8 = System.nanoTime();
//			//stmt = makeStatement(ix1Sql);
//			//mLevelBuildStmts.add(stmt);
//			//stmt.execute();
//			long t9 = System.nanoTime();
//			//mSyncedDb.execSQL(ix2Sql);
//			long t10 = System.nanoTime();
//			//mSyncedDb.analyze(mTableName);
//			long t11 = System.nanoTime();
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
//</editor-fold>
