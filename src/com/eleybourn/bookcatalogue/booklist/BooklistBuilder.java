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
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.filters.Filter;
import com.eleybourn.bookcatalogue.booklist.filters.ListOfValuesFilter;
import com.eleybourn.bookcatalogue.booklist.filters.WildcardFilter;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.CatalogueDBHelper;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
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
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ABSOLUTE_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_SORT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BL_BOOK_COUNT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BL_NODE_EXPANDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BL_NODE_LEVEL;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BL_NODE_ROW_KIND;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BL_NODE_VISIBLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BL_PRIMARY_SERIES_COUNT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO_SORT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_DOCID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_REAL_ROW_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ROOT_KEY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SELECTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NUM_FLOAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
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
    /** BookList Compatibility mode property values. */
    public static final int PREF_COMPATIBILITY_MODE_DEFAULT = 0;
    public static final int PREF_COMPATIBILITY_MODE_NESTED_TRIGGERS = 1;
    public static final int PREF_COMPATIBILITY_MODE_FLAT_TRIGGERS = 2;
    public static final int PREF_COMPATIBILITY_MODE_OLD_STYLE = 3;

    /**
     * Convenience expression for the SQL which gets formatted author names in 'Last, Given' form.
     */
    private static final String AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION =
            "Case" +
                    " When " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "=''" +
                    "  Then " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) +
                    "  Else " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + "|| ',' || " +
                    /*       */ TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) +
                    " End";
    /**
     * Convenience expression for the SQL which gets formatted author names in 'Given Last' form.
     */
    private static final String AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION =
            "Case" +
                    " When " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "=''" +
                    "  Then " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) +
                    "  Else " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "|| ' ' || " +
                    /*       */ TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) +
                    " End";

    /** Counter for BooklistBuilder IDs. */
    @NonNull
    private static final AtomicInteger mIdCounter = new AtomicInteger();
    /** Counter for 'flattened' book temp tables. */
    @NonNull
    private static final AtomicInteger mFlatListIdCounter = new AtomicInteger();
    /** DEBUG Instance counter. */
    @NonNull
    private static final AtomicInteger mDebugInstanceCounter = new AtomicInteger();

    @NonNull
    private final Context mContext;

    /** Collection of statements created by this Builder. */
    @NonNull
    private final SqlStatementManager mStatements;
    // not in use for now
    // List of columns for the group-by clause, including COLLATE clauses. Set by build() method.
    //private String mGroupColumnList;

    /** Database to use. */
    @NonNull
    private final CatalogueDBAdapter mDb;
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
    private final CursorFactory mBooklistCursorFactory = new CursorFactory() {
        @Override
        public Cursor newCursor(
                @NonNull final SQLiteDatabase db,
                @NonNull final SQLiteCursorDriver masterQuery,
                @NonNull final String editTable,
                @NonNull final SQLiteQuery query) {
            return new BooklistCursor(masterQuery, editTable, query, BooklistBuilder.this,
                                      CatalogueDBAdapter.getSynchronizer());
        }
    };
    /** not static, allow prefs to change. */
    private final String UNKNOWN = BookCatalogueApp.getResourceString(R.string.unknown_uc);
    /** the list of Filters; both active and non-active. */
    private final transient ArrayList<Filter> mFilters = new ArrayList<>();
    /** used in debug. */
    private boolean mDebugReferenceDecremented;
    /**
     * Local copy of the {@link DatabaseDefinitions#TBL_BOOK_LIST} table definition,
     * 'book_list_tmp' but renamed by adding the instance number to the end to match this instance.
     */
    private TableDefinition mListTable;
    /**
     * Local copy of the {@link DatabaseDefinitions#TBL_ROW_NAVIGATOR},
     * renamed to match this instance.
     */
    private TableDefinition mNavTable;
    /** Object used in constructing the output table. */
    private SummaryBuilder mSummary;
    /** Statement used to perform initial insert. */
    private SynchronizedStatement mBaseBuildStmt;
    /** Collection of statements used to build remaining data. */
    private ArrayList<SynchronizedStatement> mLevelBuildStmts;
    /** cached statement. */
    private SynchronizedStatement mDeleteListNodeSettingsStmt;
    private SynchronizedStatement mSaveAllListNodeSettingsStmt;
    private SynchronizedStatement mDeleteListNodeSettingStmt;
    private SynchronizedStatement mSaveListNodeSettingStmt;
    private SynchronizedStatement mGetPositionCheckVisibleStmt;
    private SynchronizedStatement mGetPositionStmt;
    private SynchronizedStatement mGetNodeRootStmt;
    private SynchronizedStatement mGetNodeLevelStmt;
    private SynchronizedStatement mGetNextAtSameLevelStmt;
    private SynchronizedStatement mShowStmt;
    private SynchronizedStatement mExpandStmt;
    /** */
    private long mFilterOnBookshelfId;

    /**
     * Constructor.
     *
     * @param context context for database
     * @param style   Book list style to use
     */
    public BooklistBuilder(@NonNull final Context context,
                           @NonNull final BooklistStyle style) {
        if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG) {
            Logger.info(this, "instances: " +
                    mDebugInstanceCounter.incrementAndGet());
        }
        // Allocate ID
        mBooklistBuilderId = mIdCounter.incrementAndGet();

        mContext = context;

        // Get the database and create a statements collection
        mDb = new CatalogueDBAdapter(mContext);
        mSyncedDb = mDb.getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing();
        mStatements = new SqlStatementManager(mSyncedDb);
        // Save the requested style
        mStyle = style;

        buildTableDefinitions();
    }

    /**
     * @return the current preferred rebuild state for the list.
     */
    public static int getListRebuildState() {
        //noinspection ConstantConditions
        return Prefs.getInt(R.string.pk_bob_list_state, PREF_LIST_REBUILD_ALWAYS_EXPANDED);
    }

    /**
     * @return the current preferred compatibility mode for the list.
     */
    private static int getCompatibilityMode() {
        return Prefs.getInt(R.string.pk_bob_list_generation, PREF_COMPATIBILITY_MODE_DEFAULT);
    }

    public static boolean thumbnailsAreCached() {
        return Prefs.getBoolean(R.string.pk_bob_thumbnails_cache_resized, false);
    }

    public static boolean thumbnailsAreGeneratedInBackground() {
        return Prefs.getBoolean(R.string.pk_bob_thumbnails_generating_mode, false);
    }

    /**
     * Clones the Temporary table definitions and append the ID to make new names in case
     * more than one view is open.
     */
    private void buildTableDefinitions() {

        mListTable = TBL_BOOK_LIST.clone();
        if (DEBUG_SWITCHES.TEMP_TABLES_ARE_STANDARD && BuildConfig.DEBUG) {
            mListTable.setType(TableTypes.Standard);
        } else {
            //RELEASE Make sure is TEMPORARY
            mListTable.setType(TableTypes.Temporary);
        }
        mListTable.setName(mListTable.getName() + '_' + mBooklistBuilderId);

        mNavTable = TBL_ROW_NAVIGATOR.clone();
        if (DEBUG_SWITCHES.TEMP_TABLES_ARE_STANDARD && BuildConfig.DEBUG) {
            mNavTable.setType(TableTypes.Standard);
        } else {
            //RELEASE Make sure is TEMPORARY
            mNavTable.setType(TableTypes.Temporary);
        }
        mNavTable.setName(mNavTable.getName() + '_' + mBooklistBuilderId);
        mNavTable.addReference(mListTable, DOM_REAL_ROW_ID);
    }

    /**
     * @return a flattened table of ordered book IDs based on the underlying list.
     */
    @NonNull
    public FlattenedBooklist createFlattenedBooklist() {
        int flatId = mFlatListIdCounter.getAndIncrement();

        TableDefinition flat = TBL_ROW_NAVIGATOR_FLATTENED.clone();
        flat.setName(flat.getName() + '_' + flatId);
        //RELEASE Make sure is TEMPORARY
        flat.setType(TableTypes.Temporary);
        flat.create(mSyncedDb);

        String sql = flat.getInsert(DOM_PK_ID, DOM_FK_BOOK_ID) +
                " SELECT " + mNavTable.dot(DOM_PK_ID) + ',' + mListTable.dot(DOM_FK_BOOK_ID) +
                " FROM " + mListTable.ref() + mListTable.join(mNavTable) +
                " WHERE " + mListTable.dot(DOM_FK_BOOK_ID) + " NOT NULL " +
                " ORDER BY " + mNavTable.dot(DOM_PK_ID);
        mSyncedDb.execSQL(sql);
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
                        "Required domain '" + domain.name + '\'' +
                                " added with differing source expression");
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
        mNavTable.create(mSyncedDb);

        if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD && BuildConfig.DEBUG) {
            Logger.info(this, "mBaseBuildStmt");
            Logger.info(this, mBaseBuildStmt.toString() + "\n\n");
        }

        // Build base data
        mBaseBuildStmt.execute();

        // Rebuild all the rest
        for (SynchronizedStatement stmt : mLevelBuildStmts) {
            if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD && BuildConfig.DEBUG) {
                Logger.info(this, "mLevelBuildStmts");
                Logger.info(this, stmt.toString() + "\n\n");
            }
            stmt.execute();
        }
    }

    /**
     * If the field has a time part, then convert to local time.
     * This deals with legacy 'date-only' dates.
     * The logic being that IF they had a time part then it would be UTC.
     * Without a time part, we assume the zone is local (or irrelevant).
     */
    @NonNull
    private String localDateExpression(@NonNull final String fieldSpec) {
        return "Case" +
                " When " + fieldSpec +
                " glob '*-*-* *' " +
                " Then datetime(" + fieldSpec + ", 'localtime')" +
                " Else " + fieldSpec +
                " End";
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
        return "Case" +
                " When " + fieldSpec + " glob '[0123456789][01234567890][01234567890][01234567890]*'" +
                "  Then substr(" + fieldSpec + ", 1, 4)" +
                "  Else '" + UNKNOWN + '\'' +
                " End";
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
        return "Case" +
                " When " + fieldSpec + " glob '[0123456789][01234567890][01234567890][01234567890]-[0123456789][01234567890]*'" +
                "  Then substr(" + fieldSpec + ", 6, 2)" +
                " When " + fieldSpec + " glob '[0123456789][01234567890][01234567890][01234567890]-[0123456789]*'" +
                "  Then substr(" + fieldSpec + ", 6, 1)" +
                "  Else '" + UNKNOWN + '\'' +
                " End";
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
        return "Case " +
                " When " + fieldSpec + " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789][0123456789]-[0123456789][0123456789]*'" +
                "  Then substr(" + fieldSpec + ", 9, 2)" +
                " When " + fieldSpec + " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789]-[0123456789][0123456789]*'" +
                "  Then substr(" + fieldSpec + ", 8, 2)" +
                " When " + fieldSpec + " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789][0123456789]-[0123456789]*'" +
                "  Then substr(" + fieldSpec + ", 9, 1)" +
                " When " + fieldSpec + " glob '[0123456789][0123456789][0123456789][0123456789]-[0123456789]-[0123456789]*'" +
                "  Then substr(" + fieldSpec + ", 8, 1)" +
                " Else " + fieldSpec +
                " End";
    }

    /**
     * expects a full WHERE condition.
     *
     * @param criteria additional conditions that apply
     */
    public void setGenericCriteria(@Nullable final String criteria) {
        if (isNonBlank(criteria)) {
            mFilters.add(new Filter() {

                @Override
                @NonNull
                public String getExpression(@Nullable final String uuid) {
                    return '(' + criteria + ')';
                }
            });
        }
    }

    /**
     * @param personName only books loaned to named person (exact name)
     */
    public void setFilterOnLoanedToPerson(@Nullable final String personName) {
        if (isNonBlank(personName)) {
            mFilters.add(new Filter() {
                @Override
                @NonNull
                public String getExpression(@Nullable final String uuid) {
                    return "EXISTS(SELECT NULL FROM " + TBL_LOAN.ref() +
                            " WHERE " +
                            TBL_LOAN.dot(DOM_LOANED_TO) + "='" + CatalogueDBAdapter.encodeString(
                            personName) + '\'' +
                            " AND " + TBL_LOAN.fkMatch(TBL_BOOKS) + ')';
                }
            });
        }
    }

    /**
     * @param name only books with named author (family or given) with added wildcards
     */
    public void setFilterOnAuthorName(@Nullable final String name) {
        if (isNonBlank(name)) {
            mFilters.add(new Filter() {
                @Override
                @NonNull
                public String getExpression(@Nullable final String uuid) {
                    return '(' +
                            TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) +
                            " LIKE '%" + CatalogueDBAdapter.encodeString(name) + "%'" +
                            " OR " +
                            TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) +
                            " LIKE '%" + CatalogueDBAdapter.encodeString(name) + "%'" +
                            ')';
                }
            });
        }
    }

    /**
     * @param name only books with named title with added wildcards
     */
    public void setFilterOnTitle(@Nullable final String name) {
        if (isNonBlank(name)) {
            mFilters.add(new WildcardFilter(TBL_BOOKS, DOM_TITLE, name));
        }
    }

    /**
     * @param name only books in named series with added wildcards
     */
    public void setFilterOnSeriesName(@Nullable final String name) {
        if (isNonBlank(name)) {
            mFilters.add(new WildcardFilter(TBL_SERIES, DOM_SERIES_NAME, name));
        }
    }

    /**
     * @param bookshelfId only books in on this shelf
     */
    public void setFilterOnBookshelfId(final long bookshelfId) {
        mFilterOnBookshelfId = bookshelfId;
    }

    /**
     * adds the FTS book table for a keyword match.
     *
     * @param text book details must in some way contain the passed text
     */
    public void setFilterOnText(@Nullable final String text) {
        if (isNonBlank(text)) {
            // Cleanup searchText
            // Because FTS does not understand locales in all android up to 4.2,
            // we do case folding here using the default locale.
            final String cleanCriteria = text.toLowerCase(Locale.getDefault());

            mFilters.add(new Filter() {
                @Override
                @NonNull
                public String getExpression(@Nullable final String uuid) {
                    return '(' + TBL_BOOKS.dot(
                            DOM_PK_ID) + " IN (SELECT " + DOM_PK_DOCID + " FROM " + TBL_BOOKS_FTS +
                            " WHERE " + TBL_BOOKS_FTS + " match '" +
                            CatalogueDBAdapter.encodeString(
                                    CatalogueDBAdapter.cleanupFtsCriterion(cleanCriteria)) +
                            "'))";
                }
            });
        }
    }

    /**
     * The where clause will add a "AND books._id IN (list)".
     * Be careful when combining with other criteria as you might get less then expected
     *
     * @param list a list of book id's.
     */
    public void setFilterOnBookIdList(@Nullable final List<Integer> list) {
        if (list != null && !list.isEmpty()) {
            mFilters.add(new ListOfValuesFilter<>(TBL_BOOKS, DOM_PK_ID, list));
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
        Tracker.handleEvent(this, Tracker.States.Enter, "build-" + mBooklistBuilderId);

        try {
            @SuppressWarnings("UnusedAssignment")
            final long t0 = System.currentTimeMillis();

            buildTableDefinitions();

            SummaryBuilder summary = new SummaryBuilder();

            // Add the minimum required domains which will have special handling

            // Will use default value
            mListTable.addDomain(DOM_PK_ID);
            // Will use expression based on first group; determined later
            mListTable.addDomain(DOM_ROOT_KEY);

            // Add the domains that have simple pre-determined expressions as sources
            summary.addDomain(DOM_BL_NODE_LEVEL, String.valueOf(mStyle.groupCount() + 1),
                              SummaryBuilder.FLAG_NONE);
            summary.addDomain(DOM_BL_NODE_ROW_KIND, "" + BooklistGroup.RowKind.BOOK,
                              SummaryBuilder.FLAG_NONE);
            summary.addDomain(DOM_FK_BOOK_ID, TBL_BOOKS.dot(DOM_PK_ID),
                              SummaryBuilder.FLAG_NONE);
            summary.addDomain(DOM_BL_BOOK_COUNT, "1",
                              SummaryBuilder.FLAG_NONE);

            // We can not use triggers to fill in headings in API < 8 since SQLite 3.5.9 is broken.
            // Allow for the user preferences to override in case another build is broken.
            final int listMode = getCompatibilityMode();

            /*
                PREF_COMPATIBILITY_MODE_OLD_STYLE = 1;
                PREF_COMPATIBILITY_MODE_FLAT_TRIGGERS = 2;
                PREF_COMPATIBILITY_MODE_NESTED_TRIGGERS = 3;
                PREF_COMPATIBILITY_MODE_DEFAULT = 4; // default
             */
            // we'll always use triggers unless we're in 'ancient' mode
            final boolean useTriggers = (listMode != PREF_COMPATIBILITY_MODE_OLD_STYLE);

            // nestedTriggers is ignored unless useTriggers is set to true.
            final boolean nestedTriggers =
                    // The default/preferred mode is to use nested triggers
                    (listMode == PREF_COMPATIBILITY_MODE_DEFAULT)
                            // or if the user explicitly asked for them
                            || (listMode == PREF_COMPATIBILITY_MODE_NESTED_TRIGGERS);

            // Build a sort mask based on if triggers are used;
            // we can not reverse sort if they are not used.
            final int sortDescendingMask = (useTriggers ? SummaryBuilder.FLAG_SORT_DESCENDING : 0);

            if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG) {
                Logger.info(this, "listMode          : " + listMode);
                Logger.info(this, "useTriggers       : " + useTriggers);
                Logger.info(this, "nestedTriggers    : " + nestedTriggers);
                Logger.info(this, "sortDescendingMask: " + sortDescendingMask);
            }

            @SuppressWarnings("UnusedAssignment")
            final long t1_basic_setup_done = System.currentTimeMillis();

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
            final long t2_groups_processed = System.currentTimeMillis();

            // Now we know if we have a Bookshelf group, add the Filter on it if we want one.
            if (mFilterOnBookshelfId > 0) {
                mFilters.add(new Filter() {
                    @Override
                    @NonNull
                    public String getExpression(@Nullable final String uuid) {
                        if (buildInfoHolder.hasGroupBOOKSHELF) {
                            return "EXISTS(SELECT NULL FROM "
                                    + TBL_BOOK_BOOKSHELF.ref() + ' '
                                    + TBL_BOOK_BOOKSHELF.join(TBL_BOOKS)
                                    + " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_FK_BOOKSHELF_ID)
                                    + '=' + mFilterOnBookshelfId +
                                    ')';
                        } else {
                            return '(' + TBL_BOOKSHELF.dot(
                                    DOM_PK_ID) + '=' + mFilterOnBookshelfId + ')';
                        }
                    }
                });
            }

            // We want the UUID for the book so we can get thumbnails
            summary.addDomain(DOM_BOOK_UUID, TBL_BOOKS.dot(DOM_BOOK_UUID),
                              SummaryBuilder.FLAG_NONE);

            // If we have a book ID to remember, then add the DOM_SELECTED field,
            // and setup the expression.
            if (previouslySelectedBookId != 0) {
                summary.addDomain(DOM_SELECTED,
                                  TBL_BOOKS.dot(DOM_PK_ID) + '=' + previouslySelectedBookId,
                                  SummaryBuilder.FLAG_NONE);
            }

            // TOMF: this is already added earlier ?
            summary.addDomain(DOM_BL_NODE_LEVEL, null,
                              SummaryBuilder.FLAG_SORTED);

            // Finished. Ensure any caller-specified extras (eg. title) are added at the end.
            for (Entry<String, ExtraDomainDetails> d : mExtraDomains.entrySet()) {
                ExtraDomainDetails info = d.getValue();
                int flags = info.isSorted ? SummaryBuilder.FLAG_SORTED
                                          : SummaryBuilder.FLAG_NONE;
                summary.addDomain(info.domain, info.sourceExpression, flags);
            }

            @SuppressWarnings("UnusedAssignment")
            final long t3 = System.currentTimeMillis();

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
            SqlComponents sqlCmp = summary.buildSqlComponents(
                    mStyle.getGroupAt(0).getCompoundKey());

            @SuppressWarnings("UnusedAssignment")
            final long t4_buildSqlComponents = System.currentTimeMillis();
            // Build the join tables
            sqlCmp.join = buildJoin(buildInfoHolder);

            @SuppressWarnings("UnusedAssignment")
            final long t5_build_join = System.currentTimeMillis();


            // Build the 'where' clause
            sqlCmp.where = buildWhereClause();

            @SuppressWarnings("UnusedAssignment")
            final long t6_build_where = System.currentTimeMillis();
            /*
             * Check if the collation we use is case sensitive; bug introduced in ICS was to
             * make UNICODE not CI.
             * Due to bugs in other language sorting, we are now forced to use a different
             * collation anyway, but we still check if it is CI.
             */
            boolean collationIsCs = mSyncedDb.isCollationCaseSensitive();

            processSortColumns(summary.getSortedColumns(), buildInfoHolder, collationIsCs);

            // unused
            // Process the group-by columns suitable for a group-by statement or index
//			{
//				final StringBuilder groupCols = new StringBuilder();
//                for (DomainDefinition d: summary.cloneGroups()) {
//					groupCols.append(d.name);
//					groupCols.append(CatalogueDBAdapter.COLLATION);
//					groupCols.append(", ");
//				}
//				groupCols.append( DOM_BL_NODE_LEVEL.name );
//				//mGroupColumnList = groupCols.toString();
//			}

            @SuppressWarnings("UnusedAssignment")
            final long t7_sortColumns_processed = System.currentTimeMillis();

            String ix1Sql = "CREATE INDEX " + mListTable + "_IX1 ON " + mListTable +
                    '(' + buildInfoHolder.sortIndexColumnList + ')';

			/* Indexes that were tried. None had a substantial impact with 800 books.
			String ix1aSql = "CREATE INDEX " + mListTable + "_IX1a ON " + mListTable +
			        "(" + DOM_BL_NODE_LEVEL + ", " + mSortColumnList + ")";
			String ix2Sql = "Create Unique Index " + mListTable + "_IX2 ON " + mListTable +
			        "(" + DOM_FK_BOOK_ID + ", " + DOM_PK_ID + ")";
			String ix3Sql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable
			        + "(" + mGroupColumnList + ")";
			String ix3aSql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable +
			        "(" + DOM_BL_NODE_LEVEL + "," + mGroupColumnList + ")";
			String ix3bSql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable +
			        "(" + mGroupColumnList +  "," + DOM_BL_NODE_LEVEL + ")";
			String ix3cSql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable +
			        "(" + mGroupColumnList +  "," + DOM_ROOT_KEY +
			        CatalogueDBAdapter.COLLATION + ")";
			String ix3dSql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable +
			        "(" + DOM_BL_NODE_LEVEL + "," + mGroupColumnList +  "," + DOM_ROOT_KEY + ")";
			String ix3eSql = "CREATE INDEX " + mListTable + "_IX3 ON " + mListTable +
			        "(" + mGroupColumnList +  "," + DOM_ROOT_KEY + "," + DOM_BL_NODE_LEVEL + ")";
			String ix4Sql = "CREATE INDEX " + mListTable + "_IX4 ON " + mListTable +
			         "(" + DOM_BL_NODE_LEVEL + "," + DOM_BL_NODE_EXPANDED + "," +
			         DOM_ROOT_KEY + ")";
			*/

            @SuppressWarnings("UnusedAssignment")
            final long t8_index_build = System.currentTimeMillis();

            // We are good to go.

            //mSyncedDb.execSQL("PRAGMA synchronous = OFF"); -- Has very little effect
            SyncLock txLock = mSyncedDb.beginTransaction(true);
            try {
                //<editor-fold desc="Experimental commented out code">

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
                //String selStmt = sqlCmp.select + " from " + sqlCmp.join + " " +
                //                  sqlCmp.where + " Order by " + sortIndexColumnList;
                //final Cursor selCsr = mSyncedDb.rawQuery(selStmt);
                //final SynchronizedStatement insStmt =
                //          mSyncedDb.compileStatement(sqlCmp.insertValues);
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
                //</editor-fold>

                //noinspection UnusedAssignment
                final long t9 = System.currentTimeMillis();

                mLevelBuildStmts = new ArrayList<>();
                // Build the lowest level summary using our initial insert statement
                if (useTriggers) {
                    // If we are using triggers, then we insert them in order and rely on the
                    // triggers to build the summary rows in the correct place.
                    String tgt = makeTriggers(summary, nestedTriggers);

                    mBaseBuildStmt = mStatements.add(
                            "mBaseBuildStmt",
                            "INSERT INTO " + tgt + '(' + sqlCmp.destinationColumns + ") " +
                                    sqlCmp.select +
                                    " FROM " + sqlCmp.join + sqlCmp.where +
                                    " ORDER BY " + buildInfoHolder.sortColNameList);

                    mBaseBuildStmt.executeInsert();
                } else {
                    // just moved out of the way... eventually to be removed maybe?
                    baseBuildWithoutTriggers(sqlCmp, collationIsCs, ix1Sql, t9);
                }

                @SuppressWarnings("UnusedAssignment")
                final long t10_BaseBuild_executed = System.currentTimeMillis();

                mSyncedDb.execSQL("analyze " + mListTable);
                @SuppressWarnings("UnusedAssignment")
                final long t11_table_optimized = System.currentTimeMillis();

                /*
                 * Now build a lookup table to match row sort position to row ID. This is used to
                 * match a specific book (or other row in result set) to a position directly
                 * without having to scan the database. This is especially useful in
                 * expand/collapse operations.
                 */
                mNavTable.drop(mSyncedDb);
                mNavTable.create(mSyncedDb);

                String sortExpression;
                if (useTriggers) {
                    sortExpression = mListTable.dot(DOM_PK_ID);
                } else {
                    sortExpression = buildInfoHolder.sortColNameList;
                }

                // TODO: Rebuild with state preserved is SLOWEST option
                // Need a better way to preserve state.
                String insSql = mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_BL_NODE_LEVEL,
                                                    DOM_ROOT_KEY, DOM_BL_NODE_VISIBLE,
                                                    DOM_BL_NODE_EXPANDED) +
                        " SELECT " + mListTable.dot(DOM_PK_ID) + ',' +
                        mListTable.dot(DOM_BL_NODE_LEVEL) + ',' +
                        mListTable.dot(DOM_ROOT_KEY) + ',' +
                        "\n Case" +
                        " When " + DOM_BL_NODE_LEVEL + "=1" +
                        " Then 1" +
                        " When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null" +
                        "  Then 0  Else 1" +
                        " End," +
                        "\n Case" +
                        " When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null" +
                        "  Then 0 Else 1" +
                        " End" +
                        "\n FROM " + mListTable.ref() +
                        /*      */ " LEFT OUTER JOIN " + TBL_BOOK_LIST_NODE_SETTINGS.ref() +
                        "  ON " + TBL_BOOK_LIST_NODE_SETTINGS.dot(
                        DOM_ROOT_KEY) + '=' + mListTable.dot(
                        DOM_ROOT_KEY) +
                        "	AND " + TBL_BOOK_LIST_NODE_SETTINGS.dot(
                        DOM_BL_NODE_ROW_KIND) + '=' + mStyle.getGroupKindAt(0) +
                        " ORDER BY " + sortExpression;

                // Always save the state-preserving navigator for rebuilds
                SynchronizedStatement navStmt = mStatements.add("mNavTable.insert", insSql);
                if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD && BuildConfig.DEBUG) {
                    Logger.info(this, "Adding mNavTable.insert to mLevelBuildStmts");
                    Logger.info(this, navStmt.toString());
                }
                mLevelBuildStmts.add(navStmt);

                // On first-time builds, get the Preferences-based list
                switch (preferredState) {
                    case PREF_LIST_REBUILD_ALWAYS_COLLAPSED:
                        mSyncedDb.execSQL(
                                mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_BL_NODE_LEVEL,
                                                    DOM_ROOT_KEY, DOM_BL_NODE_VISIBLE,
                                                    DOM_BL_NODE_EXPANDED) +
                                        " SELECT " + mListTable.dot(DOM_PK_ID) + ',' +
                                        mListTable.dot(DOM_BL_NODE_LEVEL) + ',' +
                                        mListTable.dot(DOM_ROOT_KEY) + ',' +
                                        "\n Case" +
                                        " When " + DOM_BL_NODE_LEVEL + "=1" +
                                        "  Then 1" +
                                        "  Else 0" +
                                        " End," +
                                        " 0" +
                                        " FROM " + mListTable.ref() +
                                        " ORDER BY " + sortExpression);
                        break;

                    case PREF_LIST_REBUILD_ALWAYS_EXPANDED:
                        mSyncedDb.execSQL(
                                mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_BL_NODE_LEVEL,
                                                    DOM_ROOT_KEY, DOM_BL_NODE_VISIBLE,
                                                    DOM_BL_NODE_EXPANDED) +
                                        " SELECT " + mListTable.dot(DOM_PK_ID) + ',' +
                                        mListTable.dot(DOM_BL_NODE_LEVEL) + ',' +
                                        mListTable.dot(DOM_ROOT_KEY) + ',' +
                                        " 1," +
                                        " 1" +
                                        " FROM " + mListTable.ref() +
                                        " ORDER BY " + sortExpression);
                        break;

                    default:
                        // Use already-defined SQL
                        navStmt.execute();
                        break;
                }

                @SuppressWarnings("UnusedAssignment")
                final long t12_nav_table_build = System.currentTimeMillis();
                {
                    // Create index on nav table
                    String navIx1Sql = "CREATE INDEX " + mNavTable + "_IX1" +
                            " ON " + mNavTable +
                            '(' + DOM_BL_NODE_LEVEL + ',' +
                            DOM_BL_NODE_EXPANDED + ',' +
                            DOM_ROOT_KEY + ')';

                    SynchronizedStatement ixStmt = mStatements.add("navIx1", navIx1Sql);

                    if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD && BuildConfig.DEBUG) {
                        Logger.info(this, "Adding index navIx1 to mLevelBuildStmts");
                        Logger.info(this, ixStmt.toString());
                    }
                    mLevelBuildStmts.add(ixStmt);
                    ixStmt.execute();
                }

                @SuppressWarnings("UnusedAssignment")
                final long t13_nav_table_index_IX1_created = System.currentTimeMillis();
                {
                    // Essential for main query! If not present, will make getCount() take
                    // ages because main query is a cross with no index.
                    String navIx2Sql = "CREATE UNIQUE INDEX " + mNavTable + "_IX2" +
                            " ON " + mNavTable + '(' + DOM_REAL_ROW_ID + ')';

                    SynchronizedStatement ixStmt = mStatements.add("navIx2", navIx2Sql);

                    if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD && BuildConfig.DEBUG) {
                        Logger.info(this, "Adding index navIx2 to mLevelBuildStmts");
                        Logger.info(this, ixStmt.toString());
                    }
                    mLevelBuildStmts.add(ixStmt);
                    ixStmt.execute();
                }

                @SuppressWarnings("UnusedAssignment")
                final long t14_nav_table_index_IX2_created = System.currentTimeMillis();
                mSyncedDb.execSQL("analyze " + mNavTable);

                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    Logger.info(this, "T1: " +
                            (t1_basic_setup_done - t0));
                    Logger.info(this, "T2: " +
                            (t2_groups_processed - t1_basic_setup_done));
                    Logger.info(this, "T3: " +
                            (t3 - t2_groups_processed));

                    Logger.info(this, "T4: " +
                            (t4_buildSqlComponents - t3));
                    Logger.info(this, "T5: " +
                            (t5_build_join - t4_buildSqlComponents));
                    Logger.info(this, "T6: " +
                            (t6_build_where - t5_build_join));

                    Logger.info(this, "T7: " +
                            (t7_sortColumns_processed - t6_build_where));
                    Logger.info(this, "T8: " +
                            (t8_index_build - t7_sortColumns_processed));
                    Logger.info(this, "T9: " +
                            (t9 - t8_index_build));
                    Logger.info(this, "T10: " +
                            (t10_BaseBuild_executed - t9));
                    Logger.info(this, "T11: " +
                            (t11_table_optimized - t10_BaseBuild_executed));
                    Logger.info(this, "T12: " +
                            (t12_nav_table_build - t11_table_optimized));
                    Logger.info(this, "T13: " +
                            (t13_nav_table_index_IX1_created - t12_nav_table_build));
                    Logger.info(this, "T14: " +
                            (t14_nav_table_index_IX2_created
                                    - t13_nav_table_index_IX1_created));
                    Logger.info(this, "T15: " +
                            (System.currentTimeMillis()
                                    - t14_nav_table_index_IX2_created));
                    Logger.info(this, "============================");
                    Logger.info(this, "Total time: "
                            + (System.currentTimeMillis() - t0));

                }
                mSyncedDb.setTransactionSuccessful();

                mSummary = summary;

                //if (previouslySelectedBookId > 0)
                //	ensureBookVisible(previouslySelectedBookId);

                // Get the final result
                //return getList();
                //sql = "select * from " + mTableName + " Order by " + mSortColumnList;

                //return (BooklistCursor) mSyncedDb.rawQueryWithFactory(mBooklistCursorFactory,
                //          sql, EMPTY_STRING_ARRAY, "");

            } finally {
                mSyncedDb.endTransaction(txLock);
                //mSyncedDb.execSQL("PRAGMA synchronous = FULL");

            }
        } finally {
            Tracker.handleEvent(this, Tracker.States.Exit,
                                "build-" + mBooklistBuilderId);
        }
    }

    /**
     * Process the 'sort-by' columns into a list suitable for a sort-by statement, or index.
     *
     * @param sortedColumns   the list of sorted domains from the builder
     * @param buildInfoHolder needed for the sort fields
     * @param collationIsCs   if <tt>true</tt> then we'll adjust the case ourselves
     */
    private void processSortColumns(@NonNull final List<SortedDomainInfo> sortedColumns,
                                    @NonNull final BuildInfoHolder buildInfoHolder,
                                    final boolean collationIsCs) {
        final StringBuilder sortCols = new StringBuilder();
        final StringBuilder indexCols = new StringBuilder();
        for (SortedDomainInfo sdi : sortedColumns) {
            indexCols.append(sdi.domain.name);
            if (sdi.domain.isText()) {
                indexCols.append(CatalogueDBHelper.COLLATION);

                // *If* collations is case-sensitive, handle it.
                if (collationIsCs) {
                    sortCols.append("lower(").append(sdi.domain.name).append(')');
                } else {
                    sortCols.append(sdi.domain.name);
                }
                sortCols.append(CatalogueDBHelper.COLLATION);
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
        buildInfoHolder.sortColNameList = sortCols.toString();
        buildInfoHolder.sortIndexColumnList = indexCols.toString();
    }

    private void baseBuildWithoutTriggers(@NonNull final SqlComponents /* in/out */ sqlCmp,
                                          final boolean collationIsCs,
                                          @NonNull final String ix1Sql,
                                          final long t9) {
        final long[] t_style = new long[mStyle.groupCount() + 1];
        t_style[0] = System.currentTimeMillis();

        // Without triggers we just get the base rows and add summary later
        mBaseBuildStmt = mStatements.add("mBaseBuildStmt",
                                         sqlCmp.insertSelect + sqlCmp.join + sqlCmp.where);
        //Logger.info("Base Build:\n" + sql);
        mBaseBuildStmt.execute();

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

                collatedCols.append(' ').append(d.name).append(CatalogueDBHelper.COLLATION);
            }
            // Construct the sum statement for this group
            String summarySql = "INSERT INTO " + mListTable +
                    " (" + DOM_BL_NODE_LEVEL + ',' +
                    DOM_BL_NODE_ROW_KIND + cols + ',' +
                    DOM_ROOT_KEY + ')' +

                    " SELECT " +
                    levelId + " AS " + DOM_BL_NODE_LEVEL + ',' +
                    group.getKind() + " AS " + DOM_BL_NODE_ROW_KIND + cols + ',' +
                    DOM_ROOT_KEY +
                    " FROM " + mListTable + " WHERE " + DOM_BL_NODE_LEVEL + '=' + (levelId + 1) +
                    " GROUP BY " + collatedCols + ',' + DOM_ROOT_KEY + CatalogueDBHelper.COLLATION;
            //" GROUP BY " + DOM_BL_NODE_LEVEL + ", " + DOM_BL_NODE_ROW_KIND + collatedCols;

            // Save, compile and run this statement
            SynchronizedStatement stmt = mStatements.add("L" + i, summarySql);
            if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD && BuildConfig.DEBUG) {
                Logger.info(this,
                            "Adding summarySql L" + i + " to mLevelBuildStmts: " +
                                    stmt.toString());
            }
            mLevelBuildStmts.add(stmt);
            stmt.executeInsert();
            t_style[timer++] = System.currentTimeMillis();
        }

        // Build an index if it will help sorting but *If* collation is case-sensitive,
        // don't bother with index, since everything is wrapped in lower().
        // ENHANCE: ICS UNICODE: Consider adding a duplicate _lc (lower case) column
        // to the SUMMARY table. Ugh.
        if (!collationIsCs) {
            SynchronizedStatement stmt = mStatements.add("ix1", ix1Sql);
            if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD && BuildConfig.DEBUG) {
                Logger.info(this, "Adding index ix1 to mLevelBuildStmts");
                Logger.info(this, stmt.toString());
            }
            mLevelBuildStmts.add(stmt);
            stmt.execute();
        }

        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            for (
                //noinspection UnusedAssignment
                    int i = 0; i < mStyle.groupCount();
                //noinspection UnusedAssignment
                    i++) {
                Logger.info(this, "t_style[" + i + "]: " +
                        (t_style[i] - t_style[i - 1]));
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
                                  ? AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION
                                  : AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION,
                                  SummaryBuilder.FLAG_GROUPED + SummaryBuilder.FLAG_SORTED);

                // Add the 'formatted' field of the requested type
                summary.addDomain(DOM_AUTHOR_FORMATTED,
                                  buildInfoHolder.authorGroup.showGivenNameFirst()
                                  ? AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION
                                  : AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION,
                                  SummaryBuilder.FLAG_GROUPED);

                // We also want the ID
                summary.addDomain(DOM_FK_AUTHOR_ID,
                                  TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR_ID),
                                  SummaryBuilder.FLAG_GROUPED);

                // we want the completed flag
                summary.addDomain(DOM_AUTHOR_IS_COMPLETE,
                                  TBL_AUTHORS.dot(DOM_AUTHOR_IS_COMPLETE),
                                  SummaryBuilder.FLAG_GROUPED);

                break;

            case BooklistGroup.RowKind.SERIES:
                // Save this for later use
                buildInfoHolder.seriesGroup = (BooklistGroup.BooklistSeriesGroup) booklistGroup;

                // Group and sort by name
                summary.addDomain(DOM_SERIES_NAME,
                                  TBL_SERIES.dot(DOM_SERIES_NAME),
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

                // we want the completed flag
                summary.addDomain(DOM_SERIES_IS_COMPLETE,
                                  TBL_SERIES.dot(DOM_SERIES_IS_COMPLETE),
                                  SummaryBuilder.FLAG_GROUPED);

                // We want the series number in the base data in sorted order
                // Allow for the possibility of 3.1, or even "3.1|Omnibus 3-10" as a series number.
                // so we convert it to a real (aka float).
                summary.addDomain(DOM_SERIES_NUM_FLOAT,
                                  "cast(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM) + " AS REAL)",
                                  SummaryBuilder.FLAG_SORTED);
                // We also add the base name as a sorted field for display purposes
                // and in case of non-numeric data.
                summary.addDomain(DOM_BOOK_SERIES_NUM,
                                  TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM),
                                  SummaryBuilder.FLAG_SORTED);

                // We want a counter of how many books use the series as a primary series,
                // so we can skip some series
                summary.addDomain(DOM_BL_PRIMARY_SERIES_COUNT,
                                  "Case" +
                                          " When Coalesce(" + TBL_BOOK_SERIES.dot(
                                          DOM_BOOK_SERIES_POSITION) + ",1)==1" +
                                          "  Then 1" +
                                          "  Else 0" +
                                          " End",
                                  SummaryBuilder.FLAG_NONE);
                break;

            case BooklistGroup.RowKind.LOANED:
                // Saved for later to indicate group was present
                buildInfoHolder.hasGroupLOANED = true;
                summary.addDomain(DOM_LOANED_TO_SORT,
                                  "Case" +
                                          " When " + TBL_LOAN.dot(DOM_LOANED_TO) + " is null" +
                                          "  Then 1" +
                                          "  Else 0" +
                                          " End",
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);

                summary.addDomain(
                        DOM_LOANED_TO,
                        "Case" +
                                " When " + TBL_LOAN.dot(DOM_LOANED_TO) + " is null" +
                                "  Then '" + BookCatalogueApp.getResourceString(
                                R.string.loan_book_available) + '\'' +
                                "  Else '" + BookCatalogueApp.getResourceString(
                                R.string.loaned_to_short) + "' || " + TBL_LOAN.dot(DOM_LOANED_TO) +
                                " End",
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
                                  "substr(" + TBL_BOOKS.dot(DOM_TITLE) + ",1,1)",
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
                break;

            case BooklistGroup.RowKind.RATING:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  "Cast(" + TBL_BOOKS.dot(DOM_BOOK_RATING) + " AS Integer)",
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
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_PUBLISHED_MONTH:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  monthGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_PUBLISHED), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_FIRST_PUBLICATION_YEAR:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  yearGlob(TBL_BOOKS.dot(DOM_FIRST_PUBLICATION), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_FIRST_PUBLICATION_MONTH:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  monthGlob(TBL_BOOKS.dot(DOM_FIRST_PUBLICATION), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_READ_YEAR:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  yearGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_READ_MONTH:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  monthGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_READ_DAY:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  dayGlob(TBL_BOOKS.dot(DOM_BOOK_READ_END), false),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | sortDescendingMask);
                break;


            case BooklistGroup.RowKind.DATE_ACQUIRED_YEAR:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  yearGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_ACQUIRED_MONTH:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  monthGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_ACQUIRED_DAY:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  dayGlob(TBL_BOOKS.dot(DOM_BOOK_DATE_ACQUIRED), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED); // | sortDescendingMask);
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

                summary.addDomain(DOM_LAST_UPDATE_DATE, null,
                                  SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                break;

            case BooklistGroup.RowKind.DATE_LAST_UPDATE_DAY:
                summary.addDomain(booklistGroup.getDisplayDomain(),
                                  dayGlob(TBL_BOOKS.dot(DOM_LAST_UPDATE_DATE), true),
                                  SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED
                                          | sortDescendingMask);

                summary.addDomain(DOM_LAST_UPDATE_DATE, null,
                                  SummaryBuilder.FLAG_SORTED | sortDescendingMask);
                break;


            // NEWKIND: RowKind.ROW_KIND_x

            case BooklistGroup.RowKind.BOOK:
                // nothing to do.
                break;

            default:
                throw new RTE.IllegalTypeException("" + booklistGroup.getKind());
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
                    .start()
                    .join(TBL_BOOK_BOOKSHELF)
                    .join(TBL_BOOKS);
        } else {
            join = new JoinContext(TBL_BOOKS)
                    .start();
        }

        // If a LOANED level is present, we are ONLY interested in loaned books. So cross it here.
        if (buildInfoHolder.hasGroupLOANED) {
            join.leftOuterJoin(TBL_LOAN);
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

        return join.toString();
    }

    private boolean isNonBlank(@Nullable final String s) {
        return (s != null && !s.trim().isEmpty());
    }

    @NonNull
    private String buildWhereClause() {
        StringBuilder where = new StringBuilder();

        /* Add local Filters */
        for (Filter filter : mFilters) {
            if (where.length() != 0) {
                where.append(" AND ");
            }
            where.append(' ').append(filter.getExpression(mStyle.getUuid()));
        }

        /* Add BooklistStyle Filters */
        for (Filter filter : mStyle.getFilters().values()) {
            String filterSql = filter.getExpression(mStyle.getUuid());
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
     * Clear the list of expanded nodes in the current view.
     */
    private void deleteListNodeSettings() {
        SyncLock txLock = null;

        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            int kind = mStyle.getGroupKindAt(0);
            if (mDeleteListNodeSettingsStmt == null) {
                mDeleteListNodeSettingsStmt =
                        mStatements.add("mDeleteListNodeSettingsStmt",
                                        "DELETE FROM " + TBL_BOOK_LIST_NODE_SETTINGS +
                                                " WHERE " + DOM_BL_NODE_ROW_KIND + "=?");
            }
            mDeleteListNodeSettingsStmt.bindLong(1, kind);
            mDeleteListNodeSettingsStmt.executeUpdateDelete();
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
     * Build a collection of triggers on the list table designed to fill in the summary/header
     * records as the data records are added in sorted order.
     * <p>
     * This approach means to allow DESCENDING sort orders.
     */
    @NonNull
    private String makeTriggers(@NonNull final SummaryBuilder summary,
                                final boolean nestedTriggers) {
        if (nestedTriggers) {
            // Nested triggers are compatible with Android 2.2+ and fast. (or at least relatively
            // fast when there are a 'reasonable' number of headings to be inserted).
            return makeNestedTriggers(summary);
        } else {
            // Flat triggers are compatible with Android 1.6+ but slower
            return makeSingleTrigger(summary);
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
        final String currTblName = mListTable + "_curr";
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
        mSyncedDb.execSQL("CREATE TEMP TABLE " + currTblName + " (" + sortedCols + ')');

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
                    "INSERT INTO " + mListTable +
                            " (" + DOM_BL_NODE_LEVEL + ',' +
                            DOM_BL_NODE_ROW_KIND + ',' +
                            DOM_ROOT_KEY + '\n');

            // Create the VALUES statement for the next level up
            StringBuilder valuesSql = new StringBuilder(
                    " VALUES(" + levelId + ", " + group.getKind() + ", " +
                            "new." + DOM_ROOT_KEY + '\n');

            // Create the conditional to detect if next level up is already defined
            // (by checking the 'current' record/table)
            StringBuilder conditionSql = new StringBuilder();// "l." + DOM_BL_NODE_LEVEL + " = " + levelId + "\n";
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
                    conditionSql.append("Coalesce(l.").append(groupDomain).append(
                            ",'') = Coalesce(new.").append(groupDomain).append(",'') ").append(
                            CatalogueDBHelper.COLLATION).append('\n');
                }
            }
            insertSql.append(")\n").append(valuesSql).append(')');

            String tgName = "header_A_tgL" + i;
            // Drop trigger if necessary
            mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + tgName);

            // Create the trigger
            String tgSql = "CREATE TEMP TRIGGER " + tgName +
                    " before INSERT ON " + mListTable + " for each row\n" +
                    " when new." + DOM_BL_NODE_LEVEL + '=' + (levelId + 1) + '\n' +
                    "      AND NOT EXISTS(SELECT 1 FROM " + currTblName + " l WHERE " + conditionSql + ")\n" +
                    "	Begin\n" +
                    "		" + insertSql + ";\n" +
                    "	End";
            SynchronizedStatement stmt = mStatements.add("TG " + tgName, tgSql);
            if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD && BuildConfig.DEBUG) {
                Logger.info(this, "Adding " + tgName + " to mLevelBuildStmts");
                Logger.info(this, stmt.toString());
            }
            mLevelBuildStmts.add(stmt);
            stmt.execute();
        }

        // Create a trigger to maintain the 'current' value -- just delete and insert
        String currentValueTriggerName = mListTable + "_TG_ZZZ";
        mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + currentValueTriggerName);
        String tgSql = "CREATE TEMP TRIGGER " + currentValueTriggerName +
                " after INSERT ON " + mListTable + " for each row\n" +
                " when new." + DOM_BL_NODE_LEVEL + '=' + mStyle.groupCount() +
                //" and not exists(Select 1 From " + currTblName + " l where " + conditionSql + ")\n" +
                "	Begin\n" +
                "		DELETE FROM " + currTblName + ";\n" +
                "		INSERT INTO " + currTblName + " VALUES (" + currInsertSql + ");\n" +
                "	End";

        SynchronizedStatement stmt = mStatements.add(currentValueTriggerName, tgSql);
        if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD && BuildConfig.DEBUG) {
            Logger.info(this,
                        "Adding " + currentValueTriggerName + " to mLevelBuildStmts");
            Logger.info(this, stmt.toString());
        }
        mLevelBuildStmts.add(stmt);
        stmt.execute();

        return mListTable.getName();
    }

    /**
     * Build a collection of triggers on the list table designed to fill in the summary/header
     * records as the data records are added in sorted order.
     * <p>
     * This approach is allows DESCENDING sort orders but is slightly slower than the old-style
     * manually generated lists.
     * <p>
     * 2018-11-12: this seems to be broken. See the drop/creation of the one big trigger.
     * Not pursuing for now as nested triggers should be used anyhow.
     *
     * @return the view table name
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
        StringBuilder fullInsert = new StringBuilder("INSERT INTO " + mListTable + '(');
        {
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
        }

        // We just create one big trigger
        StringBuilder oneBigTrigger = new StringBuilder(
                "CREATE TRIGGER " + tgForwardName +
                        " instead of insert ON " + viewTblName + " for each row \n" +
                        "	Begin\n");

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
            // Create an INSERT statement for the next level up
            StringBuilder insertSql = new StringBuilder(
                    "INSERT INTO " + mListTable +
                            "( " + DOM_BL_NODE_LEVEL + ',' +
                            DOM_BL_NODE_ROW_KIND + ',' +
                            DOM_ROOT_KEY + '\n');

            // Create the VALUES statement for the next level up
            StringBuilder valuesSql = new StringBuilder(
                    " SELECT " +
                            levelId + ',' +
                            group.getKind() + ',' +
                            "new." + DOM_ROOT_KEY + '\n');

            // Create the conditional to detect if next level up is already defined
            // (by checking the 'current' record/table)
            StringBuilder conditionSql = new StringBuilder();// "booklistGroup." + DOM_BL_NODE_LEVEL + " = " + levelId + "\n";
            // Update the statement components
            //noinspection ConstantConditions
            for (DomainDefinition d : group.getDomains()) {
                insertSql.append(',').append(d);
                valuesSql.append(", new.").append(d);
                // Only update the 'condition' part if it is part of the SORT list
                if (sortedDomainNames.contains(d.name)) {
                    if (conditionSql.length() > 0) {
                        conditionSql.append("	AND ");
                    }
                    conditionSql.append("Coalesce(l.").append(d).append(
                            ", '') = Coalesce(new.").append(d).append(",'') ").append(
                            CatalogueDBHelper.COLLATION).append('\n');
                }
            }
            //insertSql += ")\n	Select " + valuesSql + " Where not exists(Select 1 From " + mListTable + " l where " + conditionSql + ")";
            //tgLines[i] = insertSql;

            insertSql.append(")\n").append(valuesSql).append(
                    " WHERE NOT EXISTS(SELECT 1 FROM ").append(currTblName).append(
                    " l WHERE ").append(
                    conditionSql).append(")\n");
            oneBigTrigger.append("		").append(insertSql).append(";\n");
        }

        // Finalize the main trigger; insert the full row and update the 'current' header
        oneBigTrigger.append("		").append(fullInsert).append('\n')
                     .append("		DELETE FROM ").append(currTblName).append(";\n")
                     .append("		INSERT INTO ").append(currTblName).append(" VALUES (").append(
                currInsertSql).append(");\n")
                     .append("	End");

        {
            // rebuild(): SQLiteException: trigger book_list_tmp_2_TG_AAA already exists (code 1):
            //2018-11-12: added DROP to mLevelBuildStmts
            // but... then it fails with
            // SQLiteException: Cannot execute this statement because it might modify the
            // database but the connection is read-only.
//            SynchronizedStatement dropStmt = mStatements.add("Drop_" + tgForwardName,
//                    "DROP TRIGGER IF EXISTS " + tgForwardName);
//            if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD && BuildConfig.DEBUG) {
//                Logger.info(this, "Adding Drop_" + tgForwardName + " to mLevelBuildStmts");
//                Logger.info(this, dropStmt.toString());
//            }
//            mLevelBuildStmts.add(dropStmt);
//            dropStmt.execute();

            SynchronizedStatement stmt = mStatements.add("Create_" + tgForwardName,
                                                         oneBigTrigger.toString());
            if (DEBUG_SWITCHES.BOOKLIST_BUILDER_REBUILD && BuildConfig.DEBUG) {
                Logger.info(this, "Adding " + tgForwardName + " to mLevelBuildStmts");
                Logger.info(this, stmt.toString());
            }
            mLevelBuildStmts.add(stmt);
            stmt.execute();
        }

        return viewTblName;
    }

    /**
     * Clear the list of expanded nodes in the current view.
     */
    private void deleteListNodeSetting(final long rowId) {
        SyncLock txLock = null;

        try {
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }

            int kind = mStyle.getGroupKindAt(0);
            if (mDeleteListNodeSettingStmt == null) {
                mDeleteListNodeSettingStmt = mStatements.add(
                        "mDeleteListNodeSettingStmt",
                        "DELETE FROM " + TBL_BOOK_LIST_NODE_SETTINGS +
                                " WHERE " + DOM_BL_NODE_ROW_KIND + "=? AND " +
                                DOM_ROOT_KEY + " IN (SELECT DISTINCT " + DOM_ROOT_KEY +
                                " FROM " + mNavTable + " WHERE " + DOM_PK_ID + "=?)");
            }
            mDeleteListNodeSettingStmt.bindLong(1, kind);
            mDeleteListNodeSettingStmt.bindLong(2, rowId);
            mDeleteListNodeSettingStmt.executeUpdateDelete();
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
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

            deleteListNodeSettings();

            if (mSaveAllListNodeSettingsStmt == null) {
                mSaveAllListNodeSettingsStmt = mStatements.add(
                        "mSaveAllListNodeSettingsStmt",
                        TBL_BOOK_LIST_NODE_SETTINGS.getInsert(
                                DOM_BL_NODE_ROW_KIND,
                                DOM_ROOT_KEY) +
                                " SELECT DISTINCT ?, " + DOM_ROOT_KEY + " FROM " + mNavTable +
                                " WHERE " + DOM_BL_NODE_EXPANDED + "=1 AND " + DOM_BL_NODE_LEVEL + "=1");
            }
            int kind = mStyle.getGroupKindAt(0);

            mSaveAllListNodeSettingsStmt.bindLong(1, kind);
            mSaveAllListNodeSettingsStmt.execute();
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

            deleteListNodeSetting(rowId);

            if (mSaveListNodeSettingStmt == null) {
                String sql = TBL_BOOK_LIST_NODE_SETTINGS.getInsert(DOM_BL_NODE_ROW_KIND,
                                                                   DOM_ROOT_KEY) +
                        " SELECT ?, " + DOM_ROOT_KEY + " FROM " + mNavTable +
                        " WHERE " + DOM_BL_NODE_EXPANDED + "=1 AND " + DOM_BL_NODE_LEVEL + "=1" +
                        " AND " + DOM_PK_ID + "=?";
                mSaveListNodeSettingStmt = mStatements.add("mSaveListNodeSettingStmt", sql);
            }
            int kind = mStyle.getGroupKindAt(0);

            mSaveListNodeSettingStmt.bindLong(1, kind);
            mSaveListNodeSettingStmt.bindLong(2, rowId);
            mSaveListNodeSettingStmt.execute();
            mSyncedDb.setTransactionSuccessful();
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
        String sql = "SELECT " + mNavTable.dot(DOM_PK_ID) + ',' +
                mNavTable.dot(DOM_BL_NODE_VISIBLE) +
                " FROM " + mListTable + " bl " + mListTable.join(mNavTable) +
                " WHERE " + mListTable.dot(DOM_FK_BOOK_ID) + "=?";

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
        // Make the array and allow for ABSOLUTE_POSITION
        String[] names = new String[domains.size() + 1];
        // Copy domains
        for (int i = 0; i < domains.size(); i++) {
            names[i] = domains.get(i).name;
        }
        // Add ABSOLUTE_POSITION
        names[domains.size()] = DOM_ABSOLUTE_POSITION.name;
        return names;
    }

    /**
     * @return a list cursor starting at a given offset, using a given limit.
     */
    @NonNull
    BooklistCursor getOffsetCursor(final int position,
                                   @SuppressWarnings("SameParameterValue") final int size) {
        // Get the domains
        StringBuilder domains = new StringBuilder();
        final String prefix = mListTable.getAlias() + '.';
        for (DomainDefinition d : mListTable.getDomains()) {
            domains.append(prefix).append(d.name).append(" AS ").append(d.name).append(',');
        }

        // Build the SQL, adding ABS POS.
        final String sql = "SELECT " +
                domains +
                " (" + mNavTable.dot(DOM_PK_ID) + " - 1) AS " + DOM_ABSOLUTE_POSITION +
                " FROM " + mListTable.ref() + mListTable.join(mNavTable) +
                " WHERE " + mNavTable.dot(DOM_BL_NODE_VISIBLE) + "=1 ORDER BY " + mNavTable.dot(
                DOM_PK_ID) +
                " LIMIT " + size + " OFFSET " + position;

        // Get and return the cursor
        return (BooklistCursor) mSyncedDb.rawQueryWithFactory(mBooklistCursorFactory, sql,
                                                              new String[]{}, "");
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
     * Get the logical count of rows using a simple query rather than scanning the entire result set.
     */
    int getPseudoCount() {
        return pseudoCount("NavTable",
                           "SELECT COUNT(*) FROM " + mNavTable +
                                   " WHERE " + DOM_BL_NODE_VISIBLE + "=1");
    }

    /**
     * @return the number of book records in the list
     */
    int getBookCount() {
        return pseudoCount("ListTableBooks",
                           "SELECT COUNT(*)" +
                                   " FROM " + mListTable +
                                   " WHERE " + DOM_BL_NODE_LEVEL + '=' + (mStyle.groupCount() + 1));
    }

    /**
     * @return the number of unique book records in the list
     */
    int getUniqueBookCount() {
        return pseudoCount("ListTableUniqueBooks",
                           "SELECT COUNT(DISTINCT " + DOM_FK_BOOK_ID + ')' +
                                   " FROM " + mListTable +
                                   " WHERE " + DOM_BL_NODE_LEVEL + '=' + (mStyle.groupCount() + 1));
    }

    /**
     * Perform a single count query.
     *
     * @return the count
     */
    private int pseudoCount(@NonNull final String name,
                            @NonNull final String countSql) {
        @SuppressWarnings("UnusedAssignment")
        final long t0 = System.currentTimeMillis();
        int count;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(countSql)) {
            count = (int) stmt.count();
        }

        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            Logger.info(this,
                        "Pseudo-count (" + name + ") = " + count +
                                " completed in " + (System.currentTimeMillis() - t0) + "ms");
        }
        return count;
    }

    /**
     * Using a 1-based level index, retrieve the domain that is displayed in the
     * summary for the specified level.
     *
     * @param level 1-based level to check
     *
     * @return Name of the display field for this level
     */
    @NonNull
    public DomainDefinition getDisplayDomain(final int level) {
        return mStyle.getGroupAt(level - 1).getDisplayDomain();
    }

    /**
     * @return the number of levels in the list, including the 'base' books level
     */
    int numLevels() {
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
        if (mGetPositionCheckVisibleStmt == null) {
            mGetPositionCheckVisibleStmt =
                    mStatements.add("mGetPositionCheckVisibleStmt",
                                    "SELECT " + DOM_BL_NODE_VISIBLE + " FROM " + mNavTable +
                                            " WHERE " + DOM_PK_ID + "=?");
        }
        // Check the absolute position is visible
        final long rowId = absolutePosition + 1;
        mGetPositionCheckVisibleStmt.bindLong(1, rowId);
        boolean isVisible = (1 == mGetPositionCheckVisibleStmt.simpleQueryForLongOrZero());

        if (mGetPositionStmt == null) {
            mGetPositionStmt =
                    mStatements.add("mGetPositionStmt",
                                    "SELECT COUNT(*) FROM " + mNavTable +
                                            " WHERE " + DOM_BL_NODE_VISIBLE + "=1 AND " + DOM_PK_ID + " < ?");
        }
        // Count the number of *visible* rows *before* the specified one.
        mGetPositionStmt.bindLong(1, rowId);
        int newPos = (int) mGetPositionStmt.count();
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

        final long rowId = absPos + 1;

        if (mGetNodeRootStmt == null) {
            mGetNodeRootStmt = mStatements.add(
                    "mGetNodeRootStmt",
                    "SELECT " + DOM_PK_ID + "||'/'||" + DOM_BL_NODE_EXPANDED +
                            " FROM " + mNavTable +
                            " WHERE " + DOM_BL_NODE_LEVEL + "=1 AND " + DOM_PK_ID + " <= ?" +
                            " ORDER BY " + DOM_PK_ID + " DESC LIMIT 1");
        }

        // Get the root node, and expanded flag
        mGetNodeRootStmt.bindLong(1, rowId);
        try {
            String[] info = mGetNodeRootStmt.simpleQueryForString().split("/");
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
     * Build statements used by expand/collapse code.
     */
    private void buildExpandNodeStatements() {
        if (mGetNodeLevelStmt == null) {
            mGetNodeLevelStmt = mStatements.add(
                    "mGetNodeLevelStmt",
                    "SELECT " + DOM_BL_NODE_LEVEL + "||'/'||" + DOM_BL_NODE_EXPANDED +
                            " FROM " + mNavTable.ref() + " WHERE " + mNavTable.dot(
                            DOM_PK_ID) + "=?");
        }
        if (mGetNextAtSameLevelStmt == null) {
            mGetNextAtSameLevelStmt = mStatements.add(
                    "mGetNextAtSameLevelStmt",
                    "SELECT Coalesce( max(" + DOM_PK_ID + "), -1) FROM " +
                            "(SELECT " + DOM_PK_ID + " FROM " + mNavTable.ref() +
                            " WHERE " + mNavTable.dot(
                            DOM_PK_ID) + " > ? AND " + mNavTable.dot(
                            DOM_BL_NODE_LEVEL) + "=?" +
                            " ORDER BY " + DOM_PK_ID + " LIMIT 1" +
                            ')' +
                            " zzz");
        }
        if (mShowStmt == null) {
            mShowStmt = mStatements.add(
                    "mShowStmt",
                    "UPDATE " + mNavTable +
                            " SET " + DOM_BL_NODE_VISIBLE + "=?," + DOM_BL_NODE_EXPANDED + "=?" +
                            " WHERE " + DOM_PK_ID + " > ?" +
                            " AND " + DOM_BL_NODE_LEVEL + " > ? AND " + DOM_PK_ID + " < ?");
        }
        if (mExpandStmt == null) {
            mExpandStmt = mStatements.add(
                    "mExpandStmt",
                    "UPDATE " + mNavTable + " SET " + DOM_BL_NODE_EXPANDED + "=?" +
                            " WHERE " + DOM_PK_ID + "=?");
        }
    }

    /**
     * For EXPAND: Set all rows as visible/expanded.
     * For COLLAPSE: Set all non-root rows as invisible/unexpanded and mark all root
     * nodes as visible/unexpanded.
     */
    public void expandAll(final boolean expand) {
        @SuppressWarnings("UnusedAssignment")
        final long t0 = System.currentTimeMillis();
        if (expand) {
            String sql = "UPDATE " + mNavTable + " SET " +
                    DOM_BL_NODE_EXPANDED + "=1," +
                    DOM_BL_NODE_VISIBLE + "=1";
            mSyncedDb.execSQL(sql);
            saveListNodeSettings();
        } else {
            String sql = "UPDATE " + mNavTable + " SET " +
                    DOM_BL_NODE_EXPANDED + "=0," +
                    DOM_BL_NODE_VISIBLE + "=0" +
                    " WHERE " + DOM_BL_NODE_LEVEL + ">1";
            mSyncedDb.execSQL(sql);

            sql = "UPDATE " + mNavTable + " SET " +
                    DOM_BL_NODE_EXPANDED + "=0" +
                    " WHERE " + DOM_BL_NODE_LEVEL + "=1";
            mSyncedDb.execSQL(sql);
            deleteListNodeSettings();
        }

        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            Logger.info(this, "Expand All: " + (System.currentTimeMillis() - t0));
        }
    }

    /**
     * Toggle the expand/collapse status of the node as the specified absolute position.
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
        // Set intervening nodes as visible/invisible
        // visible
        mShowStmt.bindLong(1, isExpanded);
        // expanded
        mShowStmt.bindLong(2, isExpanded);
        mShowStmt.bindLong(3, rowId);
        mShowStmt.bindLong(4, level);
        mShowStmt.bindLong(5, next);

        mShowStmt.execute();

        // Set this node as expanded.
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
        if (mStatements.size() != 0) {
            if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG && isFinalize) {
                Logger.info(this,
                            "Finalizing with active mStatements (this is not an error): ");
                for (String name : mStatements.getNames()) {
                    Logger.info(this, name);
                }
            }

            mStatements.close();
        }

        if (mNavTable != null) {
            if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG && isFinalize) {
                Logger.info(this,
                            "Finalizing with mNavTable (this is not an error)");
            }

            try {
                mNavTable.close();
                mNavTable.drop(mSyncedDb);
            } catch (RuntimeException e) {
                Logger.error(e);
            }
        }
        if (mListTable != null) {
            if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG && isFinalize) {
                Logger.info(this,
                            "Finalizing with mListTable (this is not an error)");
            }

            try {
                mListTable.close();
                mListTable.drop(mSyncedDb);
            } catch (RuntimeException e) {
                Logger.error(e);
            }
        }

        mDb.close();

        if (DEBUG_SWITCHES.BOOKLIST_BUILDER && BuildConfig.DEBUG) {
            if (!mDebugReferenceDecremented) {
                // Only de-reference once!
                Logger.info(this,
                            "instances left: " + mDebugInstanceCounter.decrementAndGet());
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
    protected void finalize()
            throws Throwable {
        super.finalize();
        cleanup(true);
    }

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
        public String rootKeyExpression;
        public String join;
        public String insert;
        public String select;
        public String insertSelect;
        public String insertValues;
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

        /** List of column names appropriate for 'ORDER BY' clause. */
        String sortColNameList;
        /** List of column names appropriate for 'CREATE INDEX' column list. */
        String sortIndexColumnList;

        /** Will be set to appropriate Group if a Series group exists in style. */
        BooklistGroup.BooklistSeriesGroup seriesGroup;
        /** Will be set to appropriate Group if an Author group exists in style. */
        BooklistGroup.BooklistAuthorGroup authorGroup;
        /** Will be set to TRUE if a LOANED group exists in style. */
        boolean hasGroupLOANED;
        /** Will be set to TRUE if a BOOKSHELF group exists in style. */
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

        /** Options (bitmask) indicating added domain has no special properties. */
        static final int FLAG_NONE = 0;
        /** Options indicating added domain is SORTED. */
        static final int FLAG_SORTED = 1;
        /** Options indicating added domain is GROUPED. */
        static final int FLAG_GROUPED = 1 << 1;

        // Not currently used.
        ///** Options indicating added domain is part of the unique key. */
        //static final int FLAG_KEY = 1 << 2;

        /**
         * Options indicating added domain should be SORTED in descending order.
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
            //      * Returns a shallow copy of this <tt>ArrayList</tt> instance.  (The
            //     * elements themselves are not copied.)
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
            final long t0 = System.currentTimeMillis();
            mListTable.drop(mSyncedDb);
            @SuppressWarnings("UnusedAssignment")
            final long t1 = System.currentTimeMillis();
            //has to be withConstraints==false
            mListTable.create(mSyncedDb, false);

            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                Logger.info(this, "Drop   = " + (t1 - t0));
                Logger.info(this, "Create = " + (System.currentTimeMillis() - t1));
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
            cmp.destinationColumns = destColumns + "," + DOM_ROOT_KEY;
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
//      final long t0 = System.currentTimeMillis();
//      SummaryBuilder summary = new SummaryBuilder();
//      // Add the minimum required domains
//
//      summary.addDomain(DOM_PK_ID);
//      summary.addDomain(DOM_BL_NODE_LEVEL, String.valueOf(mLevels.size()+1), SummaryBuilder.FLAG_NONE);
//      summary.addDomain(DOM_BL_NODE_ROW_KIND, "" + RowKind.BOOK, SummaryBuilder.FLAG_NONE);
//      summary.addDomain(DOM_FK_BOOK_ID, TBL_BOOKS.dot(DOM_PK_ID), SummaryBuilder.FLAG_NONE);
//      summary.addDomain(DOM_BL_BOOK_COUNT, "1", SummaryBuilder.FLAG_NONE);
//      summary.addDomain(DOM_ROOT_KEY);
//      //summary.addDomain(DOM_PARENT_KEY);
//		
//      BooklistSeriesLevel seriesLevel = null;
//      BooklistAuthorLevel authorLevel = null;
//      boolean hasLevelLOANED = false;
//		
//      final long t0a = System.currentTimeMillis();
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
//				l.displayDomain = DOM_SERIES_NAME;
//				seriesLevel = (BooklistSeriesLevel) l; // getLevel(RowKind.SERIES);
//				summary.addDomain(DOM_SERIES_NAME, TBL_SERIES.dot(DOM_SERIES_NAME), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
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
//				summary.addDomain(DOM_AUTHOR_SORT, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
//				if (authorLevel.givenName)
//					summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_FIRST_LAST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);
//				else
//					summary.addDomain(DOM_AUTHOR_FORMATTED, AUTHOR_FORMATTED_LAST_FIRST_EXPRESSION, SummaryBuilder.FLAG_GROUPED);
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
//						" Then '" + BookCatalogueApp.getResourceString(R.string.booklist_read) + "'" +
//						" Else '" + BookCatalogueApp.getResourceString(R.string.booklist_unread) + "' end";
//				summary.addDomain(DOM_READ_STATUS, unreadExpr, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED);
//				summary.addDomain(DOM_BOOK_READ, TBL_BOOKS.dot(DOM_BOOK_READ), SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'r'", TBL_BOOKS.dot(DOM_BOOK_READ));
//				l.setKeyComponents("r", DOM_BOOK_READ);
//				break;
//
//			case RowKind.LOANED:
//				hasLevelLOANED = true;
//				l.displayDomain = DOM_LOANED_TO;
//				summary.addDomain(DOM_LOANED_TO, LOANED_TO_EXPRESSION, SummaryBuilder.FLAG_GROUPED | SummaryBuilder.FLAG_SORTED | SummaryBuilder.FLAG_KEY);
//				//summary.addKeyComponents("'l'", DatabaseDefinitions.TBL_LOAN.dot(DOM_LOANED_TO));
//				l.setKeyComponents("l", DOM_LOANED_TO);
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
//		final long t0b = System.currentTimeMillis();
//
//		if (previouslySelectedBookId != 0) {
//			summary.addDomain(DOM_SELECTED, TBL_BOOKS.dot(DOM_PK_ID) + " = " + previouslySelectedBookId, SummaryBuilder.FLAG_NONE);
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
//		final long t0c = System.currentTimeMillis();
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
//		final long t0d = System.currentTimeMillis();
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
//		final long t0e = System.currentTimeMillis();
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
//					// .and()    .op(TBL_LOAN.dot(DOM_FK_BOOK_ID), "=", TBL_BOOKS.dot(DOM_PK_ID)) + ")";
//		}
//		if (!seriesName.equals("")) {
//			if (!where.equals(""))
//				where += " and ";
//			where += "(" + TBL_SERIES.dot(DOM_SERIES_NAME) + " = '" + encodeString(seriesName) + "')";
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
//		final long t1 = System.currentTimeMillis();
//
//		{
//			final ArrayList<DomainDefinition> sort = summary.getExtraSort();
//			final StringBuilder sortCols = new StringBuilder();
//			for (DomainDefinition d: sort) {
//				sortCols.append(d.name);
//				sortCols.append(CatalogueDBAdapter.COLLATION + ", ");
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
//				groupCols.append(CatalogueDBAdapter.COLLATION + ", ");
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
//				keyCols.append(CatalogueDBAdapter.COLLATION + ", ");
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
//			String insertSql = "Insert into " + mListTable + "( " + DOM_BL_NODE_LEVEL + "," + DOM_BL_NODE_ROW_KIND + ", " + DOM_ROOT_KEY + "\n";
//			// If inserting with forwarding table: String insertSql = "Insert into " + TBL_BOOK_LIST + "( " + DOM_BL_NODE_LEVEL + "," + DOM_BL_NODE_ROW_KIND + ", " + DOM_ROOT_KEY + "\n";
//			// If inserting in one trigger using multiple 'exists': String valuesSql = levelId + ", " + l.kind + ", " + "new." + DOM_ROOT_KEY + "\n";
//			String valuesSql = "Values (" + levelId + ", " + l.kind + ", " + "new." + DOM_ROOT_KEY + "\n";
//			String conditionSql = "l." + DOM_BL_NODE_LEVEL + " = " + levelId + "\n";
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
//		final long t1a = System.currentTimeMillis();
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
//				sql = "INSERT INTO " + mListTable + "(\n	" + DOM_BL_NODE_LEVEL + ",\n	" + DOM_BL_NODE_ROW_KIND +
//						//",\n	" + DOM_PARENT_KEY +
//						cols + "," + DOM_ROOT_KEY +
//						")" +
//						"\n SELECT " + levelId + " AS " + DOM_BL_NODE_LEVEL + ",\n	" + l.kind + " AS " + DOM_BL_NODE_ROW_KIND +
//						//l.getKeyExpression() +
//						cols + "," + DOM_ROOT_KEY +
//						"\n FROM " + mListTable + "\n " + " WHERE level = " + (levelId+1) +
//						"\n GROUP BY " + collatedCols + "," + DOM_ROOT_KEY + CatalogueDBAdapter.COLLATION;
//						//"\n GROUP BY " + DOM_BL_NODE_LEVEL + ", " + DOM_BL_NODE_ROW_KIND + collatedCols;
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
//			sql = mNavTable.getInsert(DOM_REAL_ROW_ID, DOM_BL_NODE_LEVEL, DOM_ROOT_KEY, DOM_BL_NODE_VISIBLE, DOM_BL_NODE_EXPANDED) +
//					" Select " + mListTable.dot(DOM_PK_ID) + "," + mListTable.dot(DOM_BL_NODE_LEVEL) + "," + mListTable.dot(DOM_ROOT_KEY) +
//					" ,\n	Case When " + DOM_BL_NODE_LEVEL + " = 1 Then 1 \n" +
//					"	When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0\n	Else 1 end,\n "+ 
//					"	Case When " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " is null Then 0 Else 1 end\n"+
//					" From " + mListTable.ref() + "\n	left outer join " + TBL_BOOK_LIST_NODE_SETTINGS.ref() + 
//					"\n		On " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_ROOT_KEY) + " = " + mListTable.dot(DOM_ROOT_KEY) +
//					"\n			And " + TBL_BOOK_LIST_NODE_SETTINGS.dot(DOM_BL_NODE_ROW_KIND) + " = " + mLevels.get(0).kind +
//					"\n	Order by " + mSortColumnList;
//
//			stmt = mStatements.add("InsNav", sql);
//			mLevelBuildStmts.add(stmt);
//			stmt.execute();
//
//			long t4 = System.currentTimeMillis();
//			mSyncedDb.execSQL("Create Index " + mNavTable + "_IX1" + " On " + mNavTable + "(" + DOM_BL_NODE_LEVEL + "," + DOM_BL_NODE_EXPANDED + "," + DOM_ROOT_KEY + ")");
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
//</editor-fold>
