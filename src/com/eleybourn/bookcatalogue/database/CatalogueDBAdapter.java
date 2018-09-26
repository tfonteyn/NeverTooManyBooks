/*
 * @copyright 2010 Evan Leybourn
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
package com.eleybourn.bookcatalogue.database;

import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteQuery;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookData;
import com.eleybourn.bookcatalogue.BooksRow;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.AnthologyTitle;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOK_BOOKSHELF_WEAK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_LOAN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ANTHOLOGY_MASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ANTHOLOGY_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DOCID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_STYLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS_FTS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_LIST_STYLES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.COLLATION;
import static com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement.INSERT_FAILED;

/**
 * TODO:
 * - sql statements
 *
 * public  countXYZ     will *always* return a long, no Exceptions
 * private countXYZ     will *always* return a long, no Exceptions
 *
 * public  insertXYZ    will return {@link SynchronizedStatement#INSERT_FAILED} when error, no Exceptions
 * private insertXYZ    will return {@link SynchronizedStatement#INSERT_FAILED} when error, no Exceptions
 *
 * long getXYZ      return 0 when not found
 * Object getXYZ    return null when not found
 *
 * updateOrInsertXYZ
 *
 * getXyzId  should return 0 for not found, e.g. 'new'
 *
 *
 *
 * - table aliases hardcoded
 * - bind variables versus string concat
 *
 * - TBL_BOOKS.ref()  instead of manually concat -> make sure all tables have alias set!
 *
 * FIXME: some SQLiteDoneException , not all of the callers can deal with
 * that (but maybe they never will have to?). Those are marked with FIXME
 * All others are either ok, or the caller catches them.
 *
 * Book Catalogue database access helper class. Defines the basic CRUD operations
 * for the catalogue (based on the Notepad tutorial), and gives the
 * ability to list all books as well as retrieve or modify a specific book.
 *
 * NOTE: As of 4.2, DO NOT USE OnUpgrade TO DISPLAY UPGRADE MESSAGES.
 * Use the UpgradeMessageManager class.
 * This change separated messages from DB changes (most releases do not involve DB upgrades).
 *
 * ENHANCE: Use date_added to add 'Recent Acquisitions' virtual shelf; need to resolve how this may relate to date_purchased and 'I own this book'...
 */
public class CatalogueDBAdapter {

    /** Flag indicating the UPDATE_DATE field from the bundle should be trusted. If this flag is not set, the UPDATE_DATE will be set based on the current time */
    public static final int BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT = 1;
    /** Flag indicating to skip doing the 'purge' step; mainly used in batch operations. */
    public static final int BOOK_UPDATE_SKIP_PURGE_REFERENCES = 2;
    /** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync */
    private static final Synchronizer mSynchronizer = new Synchronizer();

    /** Static Factory object to create the custom cursor */
    private static final CursorFactory mTrackedCursorFactory = new CursorFactory() {
        @Override
        @NonNull
        public Cursor newCursor(
                final SQLiteDatabase db,
                final SQLiteCursorDriver masterQuery,
                final String editTable,
                final SQLiteQuery query) {
            return new TrackedCursor(masterQuery, editTable, query, mSynchronizer);
        }
    };

    /** Static Factory object to create the custom cursor */
    @NonNull
    private static final CursorFactory mBooksFactory = new CursorFactory() {
        @Override
        public Cursor newCursor(
                final SQLiteDatabase db,
                final SQLiteCursorDriver masterQuery,
                final String editTable,
                final SQLiteQuery query) {
            return new BooksCursor(masterQuery, editTable, query, mSynchronizer);
        }
    };

    private static final ArrayList<InstanceRef> mInstances = new ArrayList<>();
    private static DatabaseHelper mDbHelper;
    private static SynchronizedDb mSyncedDb;
    private static Integer mDebugInstanceCount = 0;
    private final Context mContext;
    private TableInfo mBooksInfo = null;
    private SqlStatementManager mStatements;

    /** Flag indicating {@link #close()} has been called */
    private boolean mCloseWasCalled = false;

    /** Statements used by {@link #getAnthologyTitleId } */
    private SynchronizedStatement mGetAnthologyTitleIdStmt = null;
    /** Statements used by {@link #getAuthorIdByName } */
    private SynchronizedStatement mGetAuthorIdStmt = null;
    /** Statements for {@link #purgeAuthors} */
    private SynchronizedStatement mPurgeBookAuthorsStmt = null;
    /** Statements for {@link #purgeAuthors} */
    private SynchronizedStatement mPurgeAuthorsStmt = null;
    /** used in {@link #countAuthorBooks} */
    private SynchronizedStatement mGetAuthorBookCountQuery = null;
    /** used in {@link #countAuthorAnthologies} */
    private SynchronizedStatement mGetAuthorAnthologyCountQuery = null;
    /** Used in {@link #getBookUuid} */
    private SynchronizedStatement mGetBookUuidQuery = null;
    /** Used in {@link #getBookIdFromUuid} */
    private SynchronizedStatement mGetBookIdFromUuidStmt = null;
    /** Used in {@link #getBookUpdateDate} */
    private SynchronizedStatement mGetBookUpdateDateQuery = null;
    /** Used in {@link #getBookTitle} */
    private SynchronizedStatement mGetBookTitleQuery = null;
    /** used in {@link #countBooks} */
    private SynchronizedStatement mCountBooksQuery = null;
    /** used in {@link #getIdFromIsbn} */
    private SynchronizedStatement mGetIdFromIsbn1Stmt = null;
    /** used in {@link #getIdFromIsbn} */
    private SynchronizedStatement mGetIdFromIsbn2Stmt = null;
    /** used in {@link #bookExists} */
    private SynchronizedStatement mCheckBookExistsStmt = null;
    /** Statement used in {@link #insertBookSeries} */
    private SynchronizedStatement mDeleteBookSeriesStmt = null;
    /** Statement used in {@link #insertBookSeries} */
    private SynchronizedStatement mAddBookSeriesStmt = null;
    /** Statements used by {@link #insertBookAuthors} */
    private SynchronizedStatement mDeleteBookAuthorsStmt = null;
    /** Statements used by {@link #insertBookAuthors} */
    private SynchronizedStatement mAddBookAuthorsStmt = null;
    /** Statements used by {@link #insertBookBookshelf} */
    private SynchronizedStatement mDeleteBookBookshelfStmt = null;
    /** Statements used by {@link #insertBookBookshelf} */
    private SynchronizedStatement mInsertBookBookshelfStmt = null;
    /** Statements used by {@link #getBookshelfName} */
    private SynchronizedStatement mGetBookshelfNameStmt = null;
    /** used in {@link #getBookshelfId} */
    private SynchronizedStatement mGetBookshelfIdStmt = null;
    /** used in {@link #getBookshelfIdByName} */
    private SynchronizedStatement mFetchBookshelfIdByNameStmt = null;
    /** {@link #insertBooklistStyle} */
    private SynchronizedStatement mInsertBooklistStyleStmt = null;
    /**
     * Update an existing booklist style
     */
    private SynchronizedStatement mUpdateBooklistStyleStmt = null;
    /**
     * Delete an existing booklist style
     */
    private SynchronizedStatement mDeleteBooklistStyleStmt = null;
    /** {@link #getSeriesId} */
    private SynchronizedStatement mGetSeriesIdStmt = null;
    /** {@link #purgeSeries} */
    private SynchronizedStatement mPurgeBookSeriesStmt = null;
    /** {@link #purgeSeries} */
    private SynchronizedStatement mPurgeSeriesStmt = null;
    /** {@link #countSeriesBooks} */
    private SynchronizedStatement mGetSeriesBookCountQuery = null;
    /** {@link #setGoodreadsBookId} */
    private SynchronizedStatement mSetGoodreadsBookIdStmt = null;
    /** {@link #setGoodreadsSyncDate} */
    private SynchronizedStatement mSetGoodreadsSyncDateStmt = null;
    /** Support statement for {@link #getGoodreadsSyncDate} */
    private SynchronizedStatement mGetGoodreadsSyncDateStmt = null;
    /** {@link #insertFts} */
    private SynchronizedStatement mInsertFtsStmt = null;
    /** {@link #updateFts} */
    private SynchronizedStatement mUpdateFtsStmt = null;
    /** {@link #deleteFts} */
    private SynchronizedStatement mDeleteFtsStmt = null;

    /**
     * Constructor - takes the context to allow the database to be opened/created
     *
     * @param context the Context within which to work
     */
    public CatalogueDBAdapter(@NonNull final Context context) {
        mContext = context;
        if (mDbHelper == null) {
            mDbHelper = new DatabaseHelper(mContext, mTrackedCursorFactory, mSynchronizer);
        }

        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            synchronized (mDebugInstanceCount) {
                mDebugInstanceCount++;
                System.out.println("CatDBA instances: " + mDebugInstanceCount);
                //debugAddInstance(this);
            }
        }
    }

    /**
     * Get the synchronizer object for this database in case there is some other activity
     * that needs to be synced.
     *
     * Note: Cursor requery() is the only thing found so far.
     */
    @NonNull
    public static Synchronizer getSynchronizer() {
        return mSynchronizer;
    }

    /**
     * @param tableAlias alias to apply to the table
     * @param idAlias    alias to apply to the ID column
     *
     * @return a string with all the fields concatenated, suitable for a select
     */
    private static String getSQLFieldsForAuthor(@SuppressWarnings("SameParameterValue") @NonNull final String tableAlias,
                                                @Nullable final String idAlias) {
        String sql;
        if (idAlias != null && !idAlias.isEmpty()) {
            sql = " " + tableAlias + "." + DOM_ID + " as " + idAlias + ", ";
        } else {
            sql = " ";
        }

        return sql + tableAlias + "." + DOM_AUTHOR_FAMILY_NAME + " as " + DOM_AUTHOR_FAMILY_NAME + "," +
                " " + tableAlias + "." + DOM_AUTHOR_GIVEN_NAMES + " as " + DOM_AUTHOR_GIVEN_NAMES + "," +
                "  Case When " + tableAlias + "." + DOM_AUTHOR_GIVEN_NAMES + " = '' Then " + DOM_AUTHOR_FAMILY_NAME +
                "        Else " + tableAlias + "." + DOM_AUTHOR_FAMILY_NAME + " || ', ' || " +
                "             " + tableAlias + "." + DOM_AUTHOR_GIVEN_NAMES + " End as " + DOM_AUTHOR_FORMATTED +
                ",  Case When " + tableAlias + "." + DOM_AUTHOR_GIVEN_NAMES + " = '' Then " + DOM_AUTHOR_FAMILY_NAME +
                "        Else " + tableAlias + "." + DOM_AUTHOR_GIVEN_NAMES + " || ' ' || " +
                tableAlias + "." + DOM_AUTHOR_FAMILY_NAME + " End as " + DOM_AUTHOR_FORMATTED_GIVEN_FIRST + " ";
    }

    /**
     * @param tableAlias table alias name to use, cannot be blank
     * @param idAlias    alias to use fo the ID field, can be null/blank in which case the ID will *not* be included in the Select
     *
     * @return set of fields suitable for a select
     */
    @NonNull
    private static String getSQLFieldsForBook(@SuppressWarnings("SameParameterValue") @NonNull final String tableAlias,
                                              @Nullable final String idAlias) {
        String prefix;
        if (idAlias != null && !idAlias.isEmpty()) {
            prefix = tableAlias + "." + DOM_ID + " as " + idAlias + ", ";
        } else {
            prefix = "";
        }

        return prefix
                + tableAlias + "." + DOM_TITLE + " AS " + DOM_TITLE + ", " +
                // Find FIRST series ID.
                "(SELECT " + DOM_SERIES_ID + " FROM " + DB_TB_BOOK_SERIES + " bs" +
                " WHERE bs." + DOM_BOOK_ID + " = " + tableAlias + "." + DOM_ID +
                " ORDER BY " + DOM_BOOK_SERIES_POSITION + " ASC  Limit 1) AS " + DOM_SERIES_ID + ", " +
                // Find FIRST series NUM.
                "(SELECT " + DOM_SERIES_NUM + " FROM " + DB_TB_BOOK_SERIES + " bs" +
                " WHERE bs." + DOM_BOOK_ID + " = " + tableAlias + "." + DOM_ID +
                " ORDER BY " + DOM_BOOK_SERIES_POSITION + " ASC  Limit 1) AS " + DOM_SERIES_NUM + ", " +
                // Get the total series count
                "(SELECT Count(*) FROM " + DB_TB_BOOK_SERIES + " bs" +
                " WHERE bs." + DOM_BOOK_ID + " = " + tableAlias + "." + DOM_ID + ") AS _num_series," +
                // Find the first AUTHOR ID
                "(SELECT " + DOM_AUTHOR_ID + " FROM " + DB_TB_BOOK_AUTHOR + " ba " +
                " WHERE ba." + DOM_BOOK_ID + " = " + tableAlias + "." + DOM_ID +
                " ORDER BY " + DOM_AUTHOR_POSITION + ", ba." + DOM_AUTHOR_ID + " Limit 1) AS " + DOM_AUTHOR_ID + ", " +
                // Get the total author count
                "(SELECT Count(*) FROM " + DB_TB_BOOK_AUTHOR + " ba" +
                " WHERE ba." + DOM_BOOK_ID + " = " + tableAlias + "." + DOM_ID + ") AS _num_authors," +

                tableAlias + "." + DOM_BOOK_ISBN + " AS " + DOM_BOOK_ISBN + ", " +
                tableAlias + "." + DOM_PUBLISHER + " AS " + DOM_PUBLISHER + ", " +
                tableAlias + "." + DOM_BOOK_DATE_PUBLISHED + " AS " + DOM_BOOK_DATE_PUBLISHED + ", " +
                tableAlias + "." + DOM_BOOK_RATING + " AS " + DOM_BOOK_RATING + ", " +
                tableAlias + "." + DOM_BOOK_READ + " AS " + DOM_BOOK_READ + ", " +
                tableAlias + "." + DOM_BOOK_PAGES + " AS " + DOM_BOOK_PAGES + ", " +
                tableAlias + "." + DOM_BOOK_NOTES + " AS " + DOM_BOOK_NOTES + ", " +
                tableAlias + "." + DOM_BOOK_LIST_PRICE + " AS " + DOM_BOOK_LIST_PRICE + ", " +
                tableAlias + "." + DOM_ANTHOLOGY_MASK + " AS " + DOM_ANTHOLOGY_MASK + ", " +
                tableAlias + "." + DOM_BOOK_LOCATION + " AS " + DOM_BOOK_LOCATION + ", " +
                tableAlias + "." + DOM_BOOK_READ_START + " AS " + DOM_BOOK_READ_START + ", " +
                tableAlias + "." + DOM_BOOK_READ_END + " AS " + DOM_BOOK_READ_END + ", " +
                tableAlias + "." + DOM_BOOK_FORMAT + " AS " + DOM_BOOK_FORMAT + ", " +
                tableAlias + "." + DOM_BOOK_SIGNED + " AS " + DOM_BOOK_SIGNED + ", " +
                tableAlias + "." + DOM_DESCRIPTION + " AS " + DOM_DESCRIPTION + ", " +
                tableAlias + "." + DOM_BOOK_GENRE + " AS " + DOM_BOOK_GENRE + ", " +
                tableAlias + "." + DOM_BOOK_LANGUAGE + " AS " + DOM_BOOK_LANGUAGE + ", " +
                tableAlias + "." + DOM_BOOK_DATE_ADDED + " AS " + DOM_BOOK_DATE_ADDED + ", " +
                tableAlias + "." + DOM_GOODREADS_BOOK_ID + " AS " + DOM_GOODREADS_BOOK_ID + ", " +
                tableAlias + "." + DOM_GOODREADS_LAST_SYNC_DATE + " AS " + DOM_GOODREADS_LAST_SYNC_DATE + ", " +
                tableAlias + "." + DOM_LAST_UPDATE_DATE + " AS " + DOM_LAST_UPDATE_DATE + ", " +
                tableAlias + "." + DOM_BOOK_UUID + " AS " + DOM_BOOK_UUID;
    }

    @NonNull
    public static String encodeString(@NonNull final String value) {
        return value.replace("'", "''");
    }

    /**
     * Cleanup a search string to remove all quotes etc.
     *
     * Remove punctuation from the search string to TRY to match the tokenizer. The only punctuation we
     * allow is a hyphen preceded by a space. Everything else is translated to a space.
     *
     * TODO: Consider making '*' to the end of all words a preference item.
     *
     * @param search Search criteria to clean
     *
     * @return Clean string
     */
    @NonNull
    public static String cleanupFtsCriterion(@NonNull final String search) {
        if (search.isEmpty()) {
            return search;
        }

        // Output buffer
        final StringBuilder out = new StringBuilder();
        // Array (convenience)
        final char[] chars = search.toCharArray();
        // Cached length
        final int len = chars.length;
        // Initial position
        int pos = 0;
        // Dummy 'previous' character
        char prev = ' ';

        // Loop over array
        while (pos < len) {
            char curr = chars[pos];
            // If current is letter or ...use it.
            if (Character.isLetterOrDigit(curr)) {
                out.append(curr);
            } else if (curr == '-' && Character.isWhitespace(prev)) {
                // Allow negation if preceded by space
                out.append(curr);
            } else {
                // Everything else is whitespace
                curr = ' ';
                if (!Character.isWhitespace(prev)) {
                    // If prev character was non-ws, and not negation, make wildcard
                    if (prev != '-') {
                        // Make every token a wildcard
                        // TODO: Make this a preference
                        out.append('*');
                    }
                    // Append a whitespace only when last char was not a whitespace
                    out.append(' ');
                }
            }
            prev = curr;
            pos++;
        }
        if (!Character.isWhitespace(prev) && (prev != '-')) {
            // Make every token a wildcard
            // TODO: Make this a preference
            out.append('*');
        }
        return out.toString();
    }

    @SuppressWarnings("unused")
    private static void debugAddInstance(@NonNull final CatalogueDBAdapter db) {
        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            mInstances.add(new InstanceRef(db));
        }
    }

    @SuppressWarnings("unused")
    private static void debugRemoveInstance(@NonNull final CatalogueDBAdapter db) {
        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            ArrayList<InstanceRef> toDelete = new ArrayList<>();
            for (InstanceRef ref : mInstances) {
                CatalogueDBAdapter refDb = ref.get();
                if (refDb == null) {
                    System.out.println("<-- **** Missing ref (not closed?) **** vvvvvvv");
                    Logger.logError(ref.getCreationException());
                    System.out.println("--> **** Missing ref (not closed?) **** ^^^^^^^");
                } else {
                    if (refDb == db) {
                        toDelete.add(ref);
                    }
                }
            }
            for (InstanceRef ref : toDelete) {
                mInstances.remove(ref);
            }
        }
    }

    /**
     * DEBUG ONLY; used when tracking a bug in android 2.1, but kept because
     * there are still non-fatal anomalies.
     */
    @SuppressWarnings("unused")
    public static void debugPrintReferenceCount(@Nullable final String msg) {
        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            if (mSyncedDb != null) {
                SynchronizedDb.printRefCount(msg, mSyncedDb.getUnderlyingDatabase());
            }
        }
    }

    @SuppressWarnings("unused")
    public static void debugDumpInstances() {
        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            for (InstanceRef ref : mInstances) {
                if (ref.get() == null) {
                    System.out.println("<-- **** Missing ref (not closed?) **** vvvvvvv");
                    Logger.logError(ref.getCreationException());
                    System.out.println("--> **** Missing ref (not closed?) **** ^^^^^^^");
                } else {
                    Logger.logError(ref.getCreationException());
                }
            }
        }
    }

    public void analyzeDb() {
        try {
            mSyncedDb.execSQL("analyze");
        } catch (Exception e) {
            Logger.logError(e, "Analyze failed");
        }
    }

    /**
     * Get the local database.
     * DO NOT CALL THIS UNLESS YOU REALLY NEED TO. DATABASE ACCESS SHOULD GO THROUGH THIS CLASS.
     *
     * @return Database connection
     */
    @NonNull
    public SynchronizedDb getDbIfYouAreSureWhatYouAreDoing() {
        if (mSyncedDb == null || !mSyncedDb.isOpen()) {
            this.open();
        }
        return mSyncedDb;
    }

    /**
     * Return a boolean indicating this instance was a new installation
     */
    public boolean isNewInstall() {
        return mDbHelper.isNewInstall();
    }

    /**
     * Create an anthology title for a book.
     * It will always be added to the END OF THE LIST (using the position column)
     *
     * @param bookId                     id of book
     * @param author                     author
     * @param title                      title of anthology title
     * @param acceptAndReturnDuplicateId If title already exists then if
     *                                   true: return existing ID
     *                                   false: throws AnthologyTitleExistsException
     *
     * @return the row ID of the newly inserted row, or {@link SynchronizedStatement#INSERT_FAILED} if an error occurred
     */
    public long insertAnthologyTitle(final long bookId,
                                     @NonNull final Author author,
                                     @NonNull final String title,
                                     final boolean acceptAndReturnDuplicateId,
                                     final boolean dirtyBookIfNecessary) {
        if (title.isEmpty()) {
            return INSERT_FAILED;
        }

        long authorId = getAuthorIdOrInsert(author);
        return insertAnthologyTitle(bookId, authorId, title, acceptAndReturnDuplicateId, dirtyBookIfNecessary);
    }

    /**
     * Create an anthology title for a book.
     * It will always be added to the END OF THE LIST (using the position column)
     *
     * @param bookId                     id of book
     * @param authorId                   id of author
     * @param title                      title of anthology title
     * @param acceptAndReturnDuplicateId If title already exists then if
     *                                   true: return existing ID
     *                                   false: throws AnthologyTitleExistsException
     *
     * @return the row ID of the newly inserted row, or {@link SynchronizedStatement#INSERT_FAILED} if an error occurred
     */
    private long insertAnthologyTitle(final long bookId,
                                      final long authorId,
                                      @NonNull final String title,
                                      final boolean acceptAndReturnDuplicateId,
                                      final boolean dirtyBookIfNecessary) throws AnthologyTitleExistsException {
        if (title.isEmpty()) {
            return INSERT_FAILED;
        }
        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        int position = getAnthologyTitleHighestPositionByBookId(bookId) + 1;

        ContentValues values = new ContentValues();
        values.put(DOM_BOOK_ID.name, bookId);
        values.put(DOM_AUTHOR_ID.name, authorId);
        values.put(DOM_TITLE.name, title);
        values.put(DOM_ANTHOLOGY_POSITION.name, position);
        long newId = getAnthologyTitleId(bookId, authorId, title);
        if (newId <= 0) {
            newId = mSyncedDb.insert(DB_TB_ANTHOLOGY, null, values);
        } else {
            if (!acceptAndReturnDuplicateId) {
                throw new AnthologyTitleExistsException();
            }
        }
        return newId;
    }

    /**
     * Delete ALL the anthology records for a given book, if any
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteAnthologyTitlesByBookId(final long bookId, final boolean dirtyBookIfNecessary) {
        int rowsAffected = mSyncedDb.delete(DB_TB_ANTHOLOGY, DOM_BOOK_ID + "=" + bookId, null);
        if (rowsAffected == 0) {
            return 0;
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }
        purgeAuthors();

        return rowsAffected;
    }

    /**
     * Return the AnthologyTitle ID for a given book/author/title
     *
     * @param bookId   id of book
     * @param authorId id of author
     * @param title    title
     *
     * @return ID, or 0 'new' if it does not exist
     */
    public long getAnthologyTitleId(final long bookId,
                                    final long authorId,
                                    @NonNull final String title) {
        if (mGetAnthologyTitleIdStmt == null) {
            String sql = "SELECT Coalesce( Min(" + DOM_ID + "),0) FROM " + DB_TB_ANTHOLOGY +
                    " WHERE " + DOM_BOOK_ID + " = ?" +
                    " and " + DOM_AUTHOR_ID + " = ?" +
                    " and " + DOM_TITLE + " = ? " +
                    COLLATION;
            mGetAnthologyTitleIdStmt = mStatements.add("mGetAnthologyTitleIdStmt", sql);
        }
        mGetAnthologyTitleIdStmt.bindLong(1, bookId);
        mGetAnthologyTitleIdStmt.bindLong(2, authorId);
        mGetAnthologyTitleIdStmt.bindString(3, title);
        return mGetAnthologyTitleIdStmt.simpleQueryForLong();
    }

    /**
     * Return the largest anthology position (usually used for adding new titles)
     *
     * @param bookId id of book to retrieve
     *
     * @return An integer of the highest position. 0 if it is not an anthology, or no AntTits yet
     */
    private int getAnthologyTitleHighestPositionByBookId(final long bookId) {
        String sql = "SELECT max(" + DOM_ANTHOLOGY_POSITION + ") FROM " + DB_TB_ANTHOLOGY +
                " WHERE " + DOM_BOOK_ID + "=" + bookId;
        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /**
     * Update the anthology title in the database
     *
     * @param id     The rowId of the anthology title
     * @param bookId The id of the book
     * @param author The author
     * @param title  The title of the anthology story
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("unused")
    public int updateAnthologyTitle(final long id,
                                    final long bookId,
                                    @NonNull final Author author,
                                    @NonNull final String title,
                                    final boolean dirtyBookIfNecessary) {

        final long authorId = getAuthorIdOrInsert(author);

        final long existingId = getAnthologyTitleId(bookId, authorId, title);
        if (existingId > 0 && existingId != id) {
            throw new AnthologyTitleExistsException();
        }

        final ContentValues values = new ContentValues();
        values.put(DOM_BOOK_ID.name, bookId);
        values.put(DOM_AUTHOR_ID.name, authorId);
        values.put(DOM_TITLE.name, title);
        int rowsAffected = mSyncedDb.update(DB_TB_ANTHOLOGY, values, DOM_ID + "=" + id, null);
        if (rowsAffected > 0) {
            purgeAuthors();

            if (dirtyBookIfNecessary) {
                setBookDirty(bookId);
            }
        }
        return rowsAffected;
    }

    /**
     * Return all the anthology titles and authors recorded for book
     *
     * @param bookId id of book to retrieve
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchAnthologyTitlesByBookId(final long bookId) {
        String sql = "SELECT an." + DOM_ID + " as " + DOM_ID + "," +
                " an." + DOM_TITLE + " as " + DOM_TITLE + "," +
                " an." + DOM_ANTHOLOGY_POSITION + " as " + DOM_ANTHOLOGY_POSITION + "," +
                " au." + DOM_AUTHOR_FAMILY_NAME + " as " + DOM_AUTHOR_FAMILY_NAME + "," +
                " au." + DOM_AUTHOR_GIVEN_NAMES + " as " + DOM_AUTHOR_GIVEN_NAMES + "," +
                " au." + DOM_AUTHOR_FAMILY_NAME + " || ', ' || au." + DOM_AUTHOR_GIVEN_NAMES + " as " + DOM_AUTHOR_NAME + "," +
                " an." + DOM_BOOK_ID + " as " + DOM_BOOK_ID + "," +
                " an." + DOM_AUTHOR_ID + " as " + DOM_AUTHOR_ID +
                " FROM " + DB_TB_ANTHOLOGY + " an, " + DB_TB_AUTHORS + " au" +
                " WHERE an." + DOM_AUTHOR_ID + "=au." + DOM_ID + " AND an." + DOM_BOOK_ID + "=" + bookId +
                " ORDER BY an." + DOM_ANTHOLOGY_POSITION + "";
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * Create a new author in the database
     *
     * @return the row ID of the newly inserted row, or {@link SynchronizedStatement#INSERT_FAILED} if an error occurred
     */
    private long insertAuthor(@NonNull final Author author) {
        ContentValues values = new ContentValues();
        values.put(DOM_AUTHOR_FAMILY_NAME.name, author.familyName);
        values.put(DOM_AUTHOR_GIVEN_NAMES.name, author.givenNames);
        return mSyncedDb.insert(DB_TB_AUTHORS, null, values);
    }

    /**
     * @return the number of rows affected
     */
    private int updateAuthor(@NonNull final Author author) {
        ContentValues values = new ContentValues();
        values.put(DOM_AUTHOR_FAMILY_NAME.name, author.familyName);
        values.put(DOM_AUTHOR_GIVEN_NAMES.name, author.givenNames);
        return mSyncedDb.update(DB_TB_AUTHORS, values, DOM_ID + " = " + author.id, null);
    }

    /**
     * Add or update the passed author, depending whether author.id == 0.
     *
     * @return true when update/create was done + the updated Author
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean updateOrInsertAuthor(@NonNull final /* out */ Author author) {
        if (author.id != 0) {
            Author previous = this.getAuthor(author.id);
            if (author.equals(previous)) {
                return true;
            }
            if (1 == updateAuthor(author)) {
                setBooksDirtyByAuthor(author.id);
                return true;
            } else {
                return false;
            }
        } else {
            author.id = insertAuthor(author);
            return true;
        }
    }

    /**
     * This will return the author based on the ID.
     *
     * @return the author, or null if not found
     */
    @Nullable
    public Author getAuthor(final long id) {
        String sql = "SELECT " + DOM_AUTHOR_FAMILY_NAME + ", " + DOM_AUTHOR_GIVEN_NAMES +
                " FROM " + DB_TB_AUTHORS +
                " WHERE " + DOM_ID + " = " + id;
        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            return (cursor.moveToFirst() ? new Author(id, cursor.getString(0), cursor.getString(1)) : null);
        }
    }

    /**
     * @return author id, or 0 (e.g. 'new') when not found
     */
    public long getAuthorIdByName(@NonNull final String familyName, @NonNull final String givenNames) {
        if (mGetAuthorIdStmt == null) {
            mGetAuthorIdStmt = mStatements.add("mGetAuthorIdStmt",
                    "SELECT " + DOM_ID + " FROM " + DB_TB_AUTHORS +
                            " WHERE Upper(" + DOM_AUTHOR_FAMILY_NAME + ") = Upper(?) " + COLLATION +
                            " And Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") = Upper(?)" + COLLATION);
        }

        mGetAuthorIdStmt.bindString(1, familyName);
        mGetAuthorIdStmt.bindString(2, givenNames);
        return mGetAuthorIdStmt.simpleQueryForLongOrZero();
    }

    /**
     * @param author to find
     *
     * @return the row ID of the 'found' row, the newly inserted row, or {@link SynchronizedStatement#INSERT_FAILED} if an error occurred
     */
    private long getAuthorIdOrInsert(@NonNull final Author author) {
        long id = getAuthorIdByName(author.familyName, author.givenNames);
        return id == 0 ? insertAuthor(author) : id;
    }

    /**
     * Refresh the passed author from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the author.
     */
    public void refreshAuthor(@NonNull final Author /* out */ author) {
        if (author.id == 0) {
            // It wasn't a known author; see if it is now. If so, update ID.
            long id = getAuthorIdByName(author.familyName, author.givenNames);
            // If we have a match, just update the object
            if (id != 0) {
                author.id = id;
            }
        } else {
            // It was a known author, see if it still is and update fields.
            Author dbAuthor = this.getAuthor(author.id);
            if (dbAuthor != null) {
                // id stays obv.
                author.familyName = dbAuthor.familyName;
                author.givenNames = dbAuthor.givenNames;
            } else {
                // gone, set the author as 'new'
                author.id = 0;
            }
        }
    }

    /**
     * Update or create the passed author.
     * First tries to find the id, based on the name
     * if found , update the id
     * calls {@link #updateOrInsertAuthor}
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertAuthorByName(@NonNull final Author author) {
        if (author.id == 0) {
            author.id = getAuthorIdByName(author.familyName, author.givenNames);
        }
        return updateOrInsertAuthor(author);
    }

    /**
     * @throws SQLiteDoneException FIXME
     */
    public void globalReplaceAuthor(@NonNull final Author from, @NonNull final Author to) throws SQLiteDoneException {
        // Create or update the new author
        if (to.id == 0) {
            long id = getAuthorIdByName(to.familyName, to.givenNames);
            // If we have a match, just update the object
            if (id != 0) {
                to.id = id;
            }
            updateOrInsertAuthor(to);
        } else {
            updateOrInsertAuthorByName(to);
        }

        // Do some basic sanity checks
        if (from.id == 0) {
            from.id = getAuthorIdByName(from.familyName, from.givenNames);
        }
        if (from.id == 0) {
            throw new RuntimeException("Old Author is not defined");
        }

        if (from.id == to.id) {
            return;
        }

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            setBooksDirtyByAuthor(from.id);

            // First handle anthologies; they have a single author and are easy
            String sql = "UPDATE " + DB_TB_ANTHOLOGY + " set " + DOM_AUTHOR_ID + " = " + to.id
                    + " WHERE " + DOM_AUTHOR_ID + " = " + from.id;
            mSyncedDb.execSQL(sql);

            globalReplacePositionedBookItem(DB_TB_BOOK_AUTHOR, DOM_AUTHOR_ID.name, DOM_AUTHOR_POSITION.name, from.id, to.id);

            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    /**
     * @return a complete list of author names from the database; used for AutoComplete.
     */
    @NonNull
    public ArrayList<String> getAuthors() {
        ArrayList<String> list = new ArrayList<>();
        try (Cursor cursor = fetchAuthors(true)) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DOM_AUTHOR_FORMATTED.name));
                list.add(name);
            }
            return list;
        }
    }

    /**
     * Return a Cursor over the list of all books in the database
     *
     * @param sortByFamily flag     TODO: rework this after introducing pseudonyms.
     *
     * @return Cursor over all notes
     */
    @NonNull
    private Cursor fetchAuthors(@SuppressWarnings("SameParameterValue") final boolean sortByFamily) {
        String order;
        if (sortByFamily) {
            order = " ORDER BY Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION + ", Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION;
        } else {
            order = " ORDER BY Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION + ", Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION;
        }
        String sql = "SELECT DISTINCT " + getSQLFieldsForAuthor("a", DOM_ID.name) +
                " FROM " + DB_TB_AUTHORS + " a, " + DB_TB_BOOK_AUTHOR + " ab " +
                " WHERE a." + DOM_ID + "=ab." + DOM_AUTHOR_ID + " ";

        return mSyncedDb.rawQuery(sql + order, new String[]{});
    }

    public void purgeAuthors() {
        /* Delete {@link DatabaseDefinitions#DB_TB_BOOK_AUTHOR} with no books */
        if (mPurgeBookAuthorsStmt == null) {
            mPurgeBookAuthorsStmt = mStatements.add("mPurgeBookAuthorsStmt",
                    "DELETE FROM " + DB_TB_BOOK_AUTHOR +
                            " WHERE " + DOM_BOOK_ID + " Not In" +
                            " (SELECT DISTINCT " + DOM_ID + " FROM " + DB_TB_BOOKS + ") ");
        }

        try {
            mPurgeBookAuthorsStmt.execute();
        } catch (Exception e) {
            Logger.logError(e, "Failed to purge DB_TB_BOOK_AUTHOR");
        }

        /* Delete {@link DatabaseDefinitions#DB_TB_AUTHORS} with no books */
        if (mPurgeAuthorsStmt == null) {
            mPurgeAuthorsStmt = mStatements.add("mPurgeAuthorsStmt",
                    "DELETE FROM " + DB_TB_AUTHORS +
                            " WHERE " + DOM_ID + " Not In" +
                            " (SELECT DISTINCT " + DOM_AUTHOR_ID + " FROM " + DB_TB_BOOK_AUTHOR + ")" +
                            " And " + DOM_ID + " Not In" +
                            " (SELECT DISTINCT " + DOM_AUTHOR_ID + " FROM " + DB_TB_ANTHOLOGY + ")");
        }

        try {
            mPurgeAuthorsStmt.execute();
        } catch (Exception e) {
            Logger.logError(e, "Failed to purge DB_TB_AUTHORS");
        }
    }

    /**
     * Return a Cursor over the list of all authors  in the database for the given book
     *
     * @param bookId the rowId of the book
     *
     * @return Cursor over all authors
     */
    @NonNull
    private Cursor fetchAuthorsByBookId(final long bookId) {
        String sql = "SELECT DISTINCT" +
                " a." + DOM_ID + " AS " + DOM_ID + "," +
                " a." + DOM_AUTHOR_FAMILY_NAME + " AS " + DOM_AUTHOR_FAMILY_NAME + "," +
                " a." + DOM_AUTHOR_GIVEN_NAMES + " AS " + DOM_AUTHOR_GIVEN_NAMES + "," +
                " Case When a." + DOM_AUTHOR_GIVEN_NAMES + " = ''" +
                " Then " + DOM_AUTHOR_FAMILY_NAME +
                " Else " + authorFormattedSource("") +
                " End as " + DOM_AUTHOR_FORMATTED + "," +
                "  ba." + DOM_AUTHOR_POSITION +
                " FROM " + DB_TB_BOOK_AUTHOR + " ba JOIN " + DB_TB_AUTHORS + " a ON a." + DOM_ID + " = ba." + DOM_AUTHOR_ID +
                " WHERE" +
                "    ba." + DOM_BOOK_ID + "=" + bookId + " " +
                " ORDER BY" +
                " ba." + DOM_AUTHOR_POSITION + " ASC," +
                " Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION + " ASC," +
                " Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION + " ASC";

        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    @NonNull
    private String authorFormattedSource(@SuppressWarnings("SameParameterValue") @NonNull String alias) {
        if (!alias.isEmpty()) {
            alias += ".";
        }
        return alias + DOM_AUTHOR_FAMILY_NAME + "||', '||" + DOM_AUTHOR_GIVEN_NAMES;
    }

    public long countAuthorBooks(@NonNull final Author author) {
        if (author.id == 0) {
            author.id = getAuthorIdByName(author.familyName, author.givenNames);
        }

        if (author.id == 0) {
            return 0;
        }

        if (mGetAuthorBookCountQuery == null) {
            mGetAuthorBookCountQuery = mStatements.add("mGetAuthorBookCountQuery",
                    "SELECT Count(" + DOM_BOOK_ID + ") FROM " + DB_TB_BOOK_AUTHOR + " WHERE " + DOM_AUTHOR_ID + "=?");
        }
        // Be cautious
        synchronized (mGetAuthorBookCountQuery) {
            mGetAuthorBookCountQuery.bindLong(1, author.id);
            return mGetAuthorBookCountQuery.count();
        }
    }

    /**
     * @param author id
     *
     * @return #
     */
    public long countAuthorAnthologies(@NonNull final Author author) {
        if (author.id == 0) {
            author.id = getAuthorIdByName(author.familyName, author.givenNames);
        }
        if (author.id == 0) {
            return 0;
        }

        if (mGetAuthorAnthologyCountQuery == null) {
            mGetAuthorAnthologyCountQuery = mStatements.add("mGetAuthorAnthologyCountQuery",
                    "SELECT Count(" + DOM_ID + ") FROM " + DB_TB_ANTHOLOGY + " WHERE " + DOM_AUTHOR_ID + "=?");
        }
        // Be cautious
        synchronized (mGetAuthorAnthologyCountQuery) {
            mGetAuthorAnthologyCountQuery.bindLong(1, author.id);
            return mGetAuthorAnthologyCountQuery.count();
        }

    }

    /**
     * Examine the values and make any changes necessary before writing the data.
     *
     * @param bookData Collection of field values.
     */
    private void preprocessOutput(final boolean isNew, @NonNull final BookData bookData) {

        // Handle AUTHOR
        {
            // If present, get the author ID from the author name (it may have changed with a name change)
            if (bookData.containsKey(UniqueId.KEY_AUTHOR_FORMATTED)) {
                Author author = Author.toAuthor(bookData.getString(UniqueId.KEY_AUTHOR_FORMATTED));
                bookData.putLong(UniqueId.KEY_AUTHOR_ID, getAuthorIdOrInsert(author));
            } else {
                if (bookData.containsKey(UniqueId.KEY_AUTHOR_FAMILY_NAME)) {
                    String family = bookData.getString(UniqueId.KEY_AUTHOR_FAMILY_NAME);
                    String given;
                    if (bookData.containsKey(UniqueId.KEY_AUTHOR_GIVEN_NAMES)) {
                        given = bookData.getString(UniqueId.KEY_AUTHOR_GIVEN_NAMES);
                    } else {
                        given = "";
                    }
                    bookData.putLong(UniqueId.KEY_AUTHOR_ID, getAuthorIdOrInsert(new Author(family, given)));
                }
            }
        }

        // Handle TITLE; but only for new books
        {
            if (isNew && bookData.containsKey(UniqueId.KEY_TITLE)) {
                /* Move "The, A, An" to the end of the string */
                String title = bookData.getString(UniqueId.KEY_TITLE);
                StringBuilder newTitle = new StringBuilder();
                String[] title_words = title.split(" ");
                try {
                    if (title_words[0].matches(mContext.getResources().getString(R.string.title_reorder))) {
                        for (int i = 1; i < title_words.length; i++) {
                            if (i != 1) {
                                newTitle.append(" ");
                            }
                            newTitle.append(title_words[i]);
                        }
                        newTitle.append(", ").append(title_words[0]);
                        bookData.putString(UniqueId.KEY_TITLE, newTitle.toString());
                    }
                } catch (Exception ignore) {
                    //do nothing. Title stays the same
                }
            }
        }

        // Remove blank/null fields that have default values defined in the database
        // or which should never be blank.
        for (String name : new String[]{
                UniqueId.KEY_BOOK_UUID,
                UniqueId.KEY_ANTHOLOGY_MASK,
                UniqueId.KEY_BOOK_RATING,
                UniqueId.KEY_BOOK_READ,
                UniqueId.KEY_BOOK_SIGNED,
                UniqueId.KEY_BOOK_DATE_ADDED,
                UniqueId.KEY_GOODREADS_LAST_SYNC_DATE,
                UniqueId.KEY_LAST_UPDATE_DATE}) {
            if (bookData.containsKey(name)) {
                Object o = bookData.get(name);
                // Need to allow for the possibility the stored value is not
                // a string, in which case getString() would return a NULL.
                if (o == null || o.toString().isEmpty()) {
                    bookData.remove(name);
                }
            }
        }
    }

    /**
     * Utility routine to return the book uuid based on the id.
     *
     * @param id of the book
     *
     * @return the book uuid (*every* book has a uuid in the database)
     */
    @NonNull
    public String getBookUuid(final long id) {
        if (mGetBookUuidQuery == null) {
            mGetBookUuidQuery = mStatements.add("mGetBookUuidQuery",
                    "SELECT " + DOM_BOOK_UUID + " FROM " + DB_TB_BOOKS + " WHERE " + DOM_ID + "=?");
        }
        // Be cautious; other threads may call this and set parameters.
        synchronized (mGetBookUuidQuery) {
            mGetBookUuidQuery.bindLong(1, id);
            return mGetBookUuidQuery.simpleQueryForString();
        }
    }

    /**
     * Check that a book with the passed UUID exists and return the ID of the book, or zero
     *
     * @param uuid UUID of book
     *
     * @return ID of the book, or 0 'new' if not found
     */
    public long getBookIdFromUuid(@NonNull final String uuid) {
        if (mGetBookIdFromUuidStmt == null) {
            mGetBookIdFromUuidStmt = mStatements.add("mGetBookIdFromUuidStmt",
                    "SELECT " + DOM_ID + " FROM " + DB_TB_BOOKS + " WHERE " + DOM_BOOK_UUID + " = ?");
        }

        mGetBookIdFromUuidStmt.bindString(1, uuid);
        return mGetBookIdFromUuidStmt.simpleQueryForLongOrZero();
    }

    /**
     * Utility routine to return the book title based on the id.
     */
    @Nullable
    public String getBookUpdateDate(final long id) {
        if (mGetBookUpdateDateQuery == null) {
            mGetBookUpdateDateQuery = mStatements.add("mGetBookUpdateDateQuery",
                    "SELECT " + DOM_LAST_UPDATE_DATE + " FROM " + DB_TB_BOOKS + " WHERE " + DOM_ID + "=?");
        }
        // Be cautious
        synchronized (mGetBookUpdateDateQuery) {
            mGetBookUpdateDateQuery.bindLong(1, id);
            return mGetBookUpdateDateQuery.simpleQueryForStringOrNull();
        }
    }

    /**
     * Utility routine to return the book title based on the id.
     */
    @NonNull
    public String getBookTitle(final long id) {
        if (mGetBookTitleQuery == null) {
            mGetBookTitleQuery = mStatements.add("mGetBookTitleQuery",
                    "SELECT " + DOM_TITLE + " FROM " + DB_TB_BOOKS + " WHERE " + DOM_ID + "=?");
        }
        // Be cautious
        synchronized (mGetBookTitleQuery) {
            mGetBookTitleQuery.bindLong(1, id);
            return mGetBookTitleQuery.simpleQueryForString();
        }
    }

    /**
     * Delete the book with the given rowId
     *
     * @param bookId id of book to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteBook(final long bookId) {
        String uuid = null;
        try {
            uuid = this.getBookUuid(bookId);
        } catch (Exception e) {
            Logger.logError(e, "Failed to get book UUID");
        }

        int rowsAffected = mSyncedDb.delete(DB_TB_BOOKS, DOM_ID + "=" + bookId, null);
        if (rowsAffected == 0) {
            return 0;
        }

        purgeAuthors();
        try {
            deleteFts(bookId);
        } catch (Exception e) {
            Logger.logError(e, "Failed to delete FTS");
        }
        // Delete thumbnail(s)
        if (uuid != null) {
            try {
                File f = StorageUtils.getCoverFile(uuid);
                while (f.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                    f = StorageUtils.getCoverFile(uuid);
                }
            } catch (Exception e) {
                Logger.logError(e, "Failed to delete cover thumbnail");
            }
        }

        if (uuid != null && !uuid.isEmpty()) {
            try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(mContext)) {
                coversDbHelper.eraseCachedBookCover(uuid);
            }
        }

        return rowsAffected;
    }

    /**
     * Create a new book using the details provided.
     *
     * @param bookData A ContentValues collection with the columns to be updated. May contain extra data.
     * @param flags    See {@link #BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT} an others for flag definitions
     *
     * @return the row ID of the newly inserted row, or {@link SynchronizedStatement#INSERT_FAILED} if an error occurred
     */
    public long insertBook(@NonNull final BookData bookData, final int flags) {
        return insertBookWithId(0, bookData, flags);
    }

    /**
     * Create a new book using the details provided.
     *
     * @param bookId   The ID of the book to insert
     *                 zero: a new book
     *                 non-zero: will overwrite the normal autoIncrement, normally only an Import should use this
     * @param bookData A ContentValues collection with the columns to be updated. May contain extra data.
     * @param flags    See {@link #BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT} an others for flag definitions
     *
     * @return the row ID of the newly inserted row, or {@link SynchronizedStatement#INSERT_FAILED} if an error occurred
     */
    public long insertBookWithId(final long bookId,
                                 @NonNull final BookData bookData,
                                 @SuppressWarnings("unused") final int flags) {

        try {
            // Make sure we have the target table details
            if (mBooksInfo == null) {
                mBooksInfo = new TableInfo(mSyncedDb, DB_TB_BOOKS);
            }

            // Cleanup fields (author, series, title and remove blank fields for which we have defaults)
            preprocessOutput(bookId == 0, bookData);

            /* TODO We may want to provide default values for these fields:
             * KEY_BOOK_RATING, KEY_BOOK_READ, KEY_NOTES, KEY_BOOK_LOCATION, KEY_BOOK_READ_START, KEY_BOOK_READ_END, KEY_BOOK_SIGNED, & DATE_ADDED
             */
            if (!bookData.containsKey(UniqueId.KEY_BOOK_DATE_ADDED)) {
                bookData.putString(UniqueId.KEY_BOOK_DATE_ADDED, DateUtils.toSqlDateTime(new Date()));
            }

            // Make sure we have an author
            List<Author> authors = bookData.getAuthors();
            if (authors.size() == 0) {
                throw new IllegalArgumentException();
            }

            ContentValues values = filterValues(bookData);
            if (bookId > 0) {
                values.put(DOM_ID.name, bookId);
            }

            if (!values.containsKey(DOM_LAST_UPDATE_DATE.name)) {
                values.put(DOM_LAST_UPDATE_DATE.name, DateUtils.toSqlDateTime(new Date()));
            }

            // This was commented out in the original code... ?
            // ALWAYS set the INSTANCE_UPDATE_DATE; this is used for backups
            //values.put(DOM_INSTANCE_UPDATE_DATE.name, Utils.toSqlDateTime(Calendar.getInstance().getTime()));

            long newBookId = mSyncedDb.insert(DB_TB_BOOKS, null, values);

            String bookshelf = bookData.getBookshelfList();
            if (bookshelf != null && !bookshelf.trim().isEmpty()) {
                insertBookBookshelf(newBookId, ArrayUtils.decodeList(Bookshelf.SEPARATOR, bookshelf), false);
            }

            insertBookAuthors(newBookId, authors, false);
            insertBookSeries(newBookId, bookData.getSeries(), false);
            insertBookAnthologyTitles(newBookId, bookData.getAnthologyTitles(), false);

            try {
                insertFts(newBookId);
            } catch (Exception e) {
                Logger.logError(e, "Failed to update FTS");
            }

            return newBookId;
        } catch (Exception e) {
            Logger.logError(e);
            throw new RuntimeException("Error creating book from " + bookData + ": " + e.getMessage(), e);
        }
    }

    /**
     * Update the book using the details provided. The book to be updated is
     * specified using the rowId, and it is altered to use values passed in
     *
     * @param id       of the book in the database
     * @param bookData A collection with the columns to be updated. May contain extra data.
     * @param flags    See {@link #BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT} an others for flag definitions
     *
     * @return the number of rows affected
     */
    public int updateBook(final long id, @NonNull final BookData bookData, final int flags) {
        try {
            // Make sure we have the target table details
            if (mBooksInfo == null) {
                mBooksInfo = new TableInfo(mSyncedDb, DB_TB_BOOKS);
            }

            // Cleanup fields (author, series, title and remove blank fields for which we have defaults)
            preprocessOutput(id == 0, bookData);

            ContentValues values = filterValues(bookData);

            // Disallow UUID updates
            if (values.containsKey(DOM_BOOK_UUID.name)) {
                values.remove(DOM_BOOK_UUID.name);
            }

            // We may be just updating series, or author lists but we still update the last_update_date.
            if ((flags & BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT) == 0 || !values.containsKey(DOM_LAST_UPDATE_DATE.name)) {
                values.put(DOM_LAST_UPDATE_DATE.name, DateUtils.toSqlDateTime(Calendar.getInstance().getTime()));
            }

            // ALWAYS set the INSTANCE_UPDATE_DATE; this is used for backups
            //args.put(DOM_INSTANCE_UPDATE_DATE.name, Utils.toSqlDateTime(Calendar.getInstance().getTime()));

            // go !
            int rowsAffected = mSyncedDb.update(DB_TB_BOOKS, values, DOM_ID + "=" + id, null);

            String bookshelf = bookData.getBookshelfList();
            if (bookshelf != null && !bookshelf.trim().isEmpty()) {
                insertBookBookshelf(id, ArrayUtils.decodeList(Bookshelf.SEPARATOR, bookshelf), false);
            }

            if (bookData.containsKey(UniqueId.BKEY_AUTHOR_ARRAY)) {
                insertBookAuthors(id, bookData.getAuthors(), false);
            }
            if (bookData.containsKey(UniqueId.BKEY_SERIES_ARRAY)) {
                insertBookSeries(id, bookData.getSeries(), false);
            }

            if (bookData.containsKey(UniqueId.BKEY_ANTHOLOGY_TITLE_ARRAY)) {
                insertBookAnthologyTitles(id, bookData.getAnthologyTitles(), false);
            }

            // Only really skip the purge if a batch update of multiple books is being done.
            if ((flags & BOOK_UPDATE_SKIP_PURGE_REFERENCES) == 0) {
                purgeAuthors();
                purgeSeries();
            }

            try {
                updateFts(id);
            } catch (Exception e) {
                Logger.logError(e, "Failed to update FTS");
            }
            return rowsAffected;
        } catch (Exception e) {
            Logger.logError(e);
            throw new RuntimeException("Error updating book from " + bookData + ": " + e.getMessage(), e);
        }
    }

    /**
     * Return the number of books
     *
     * @return The number of books
     */
    public int countBooks() {
        if (mCountBooksQuery == null) {
            mCountBooksQuery = mStatements.add("mGetAuthorBookCountQuery",
                    "SELECT Count(*) FROM " + TBL_BOOKS.ref());
        }
        return (int) mCountBooksQuery.count();
    }

    @NonNull
    public Cursor getBookUuidList() {
        return mSyncedDb.rawQuery("SELECT " + DOM_BOOK_UUID + " AS " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS.ref());
    }

    /**
     * @param isbn The isbn to search by
     *
     * @return book id, or 0 if not found
     */
    public long getIdFromIsbn(@NonNull final String isbn, final boolean checkAltIsbn) {
        SynchronizedStatement stmt;
        if (checkAltIsbn && IsbnUtils.isValid(isbn)) {
            if (mGetIdFromIsbn2Stmt == null) {
                mGetIdFromIsbn2Stmt = mStatements.add("mGetIdFromIsbn2Stmt",
                        "SELECT Coalesce(max(" + DOM_ID + "), 0) FROM " + DB_TB_BOOKS +
                                " WHERE Upper(" + DOM_BOOK_ISBN + ") in (Upper(?), Upper(?))");
            }
            stmt = mGetIdFromIsbn2Stmt;
            stmt.bindString(2, IsbnUtils.isbn2isbn(isbn));
        } else {
            if (mGetIdFromIsbn1Stmt == null) {
                mGetIdFromIsbn1Stmt = mStatements.add("mGetIdFromIsbn1Stmt",
                        "SELECT Coalesce(max(" + DOM_ID + "), 0) FROM " + DB_TB_BOOKS +
                                " WHERE Upper(" + DOM_BOOK_ISBN + ") = Upper(?)");
            }
            stmt = mGetIdFromIsbn1Stmt;
        }
        stmt.bindString(1, isbn);
        return stmt.simpleQueryForLongOrZero();
    }

    /**
     * @param isbn The isbn to search by
     *
     * @return boolean indicating ISBN already in DB
     */
    public boolean isbnExists(@NonNull final String isbn, final boolean checkAltIsbn) {
        return getIdFromIsbn(isbn, checkAltIsbn) > 0;
    }

    /**
     * Check that a book with the passed ID exists
     *
     * @param bookId of book
     *
     * @return true if exists
     */
    public boolean bookExists(final long bookId) {
        if (mCheckBookExistsStmt == null) {
            mCheckBookExistsStmt = mStatements.add("mCheckBookExistsStmt",
                    "SELECT " + DOM_ID + " FROM " + DB_TB_BOOKS + " WHERE " + DOM_ID + " = ?");
        }
        mCheckBookExistsStmt.bindLong(1, bookId);
        return (0 != mCheckBookExistsStmt.simpleQueryForLongOrZero());
    }

    /**
     * Set a book as in need of backup if any ancillary data has changed.
     */
    private void setBookDirty(final long bookId) {
        mSyncedDb.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                " WHERE " + TBL_BOOKS + "." + DOM_ID + " = " + bookId);
    }

    /**
     * Set all books referencing a given author as dirty.
     */
    private void setBooksDirtyByAuthor(final long authorId) {
        // Mark all related books based on anthology author as dirty
        String sql = "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                " WHERE" +
                " Exists(Select * FROM " + TBL_ANTHOLOGY.ref() +
                " WHERE " + TBL_ANTHOLOGY.dot(DOM_AUTHOR_ID) + " = " + authorId +
                " and " + TBL_ANTHOLOGY.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
        mSyncedDb.execSQL(sql);

        // Mark all related books based on series as dirty
        sql = "UPDATE " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                " WHERE" +
                " Exists(Select * FROM " + TBL_BOOK_AUTHOR.ref() +
                " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID) + " = " + authorId +
                " and " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
        mSyncedDb.execSQL(sql);
    }

    /**
     * Set all books referencing a given series as dirty.
     */
    private void setBooksDirtyBySeries(final long seriesId) {
        String sql;
        sql = "UPDATE " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                " WHERE" +
                " Exists(Select * FROM " + TBL_BOOK_SERIES.ref() + " WHERE " + TBL_BOOK_SERIES.dot(DOM_SERIES_ID) + " = " + seriesId +
                " and " + TBL_BOOK_SERIES.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
        mSyncedDb.execSQL(sql);
    }

    /**
     * Set all books referencing a given bookshelf as dirty.
     */
    private void setBooksDirtyByBookshelf(final long bookshelfId) {
        String sql = "UPDATE " + TBL_BOOKS + " set " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                " WHERE" +
                " Exists(Select * FROM " + TBL_BOOK_BOOKSHELF.ref() + " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_BOOKSHELF_ID) + " = " + bookshelfId +
                " and " + TBL_BOOK_BOOKSHELF.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS + "." + DOM_ID + ")";
        mSyncedDb.execSQL(sql);
    }

    private void insertBookSeries(long bookId,
                                  @Nullable final ArrayList<Series> series,
                                  @SuppressWarnings("SameParameterValue") boolean dirtyBookIfNecessary) {
        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        // If we have series details, save them.
        if (series != null) {
            if (mDeleteBookSeriesStmt == null) {
                mDeleteBookSeriesStmt = mStatements.add("mDeleteBookSeriesStmt",
                        "DELETE FROM " + DB_TB_BOOK_SERIES + " WHERE " + DOM_BOOK_ID + " = ?");
            }
            // Delete the current series
            mDeleteBookSeriesStmt.bindLong(1, bookId);
            mDeleteBookSeriesStmt.execute();

            if (mAddBookSeriesStmt == null) {
                mAddBookSeriesStmt = mStatements.add("mAddBookSeriesStmt",
                        "Insert Into " + DB_TB_BOOK_SERIES
                                + "(" + DOM_BOOK_ID + "," + DOM_SERIES_ID + "," + DOM_SERIES_NUM + "," + DOM_BOOK_SERIES_POSITION + ")"
                                + " Values(?,?,?,?)");
            }

            //FIXME: is this still true ?
            // Setup the book in the ADD statement. This was once good enough, but
            // Android 4 (at least) causes the bindings to clean when executed. So
            // now we do it each time in loop.
            // mAddBookSeriesStmt.bindLong(1, bookId);
            // See call to releaseAndUnlock in:
            // 		http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.0.1_r1/android/database/sqlite/SQLiteStatement.java#SQLiteStatement.executeUpdateDelete%28%29
            // which calls clearBindings():
            //		http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.0.1_r1/android/database/sqlite/SQLiteStatement.java#SQLiteStatement.releaseAndUnlock%28%29
            //

            // Get the authors and turn into a list of names
            // The list MAY contain duplicates (eg. from Internet lookups of multiple
            // sources), so we track them in a hash map
            final HashMap<String, Boolean> idHash = new HashMap<>();
            int pos = 0;
            for (Series entry : series) {
                long seriesId = 0;
                try {
                    seriesId = getSeriesId(entry.name);
                    if (seriesId == 0) {
                        //FIXME: ignores failures
                        seriesId = insertSeries(entry.name);
                    }

                    String uniqueId = seriesId + "(" + entry.number.trim().toUpperCase() + ")";
                    if (!idHash.containsKey(uniqueId)) {
                        idHash.put(uniqueId, true);
                        pos++;
                        mAddBookSeriesStmt.bindLong(1, bookId);
                        mAddBookSeriesStmt.bindLong(2, seriesId);
                        mAddBookSeriesStmt.bindString(3, entry.number);
                        mAddBookSeriesStmt.bindLong(4, pos);
                        mAddBookSeriesStmt.execute();
                    }
                } catch (Exception e) {
                    Logger.logError(e);
                    throw new RuntimeException("Error adding series '" + entry.name +
                            "' {" + seriesId + "} to book " + bookId + ": " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Insert a List of AnthologyTitles.
     *
     * @param bookId               the book
     * @param list                 the list
     * @param dirtyBookIfNecessary up to now, always set to 'false'
     *
     *                             Failures to insert are logged but nor reported back TODO: @return ?
     */
    private void insertBookAnthologyTitles(final long bookId,
                                           @NonNull final ArrayList<AnthologyTitle> list,
                                           @SuppressWarnings("SameParameterValue") boolean dirtyBookIfNecessary) {
        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        deleteAnthologyTitlesByBookId(bookId, false);

        for (AnthologyTitle anthologyTitle : list) {
            long authorId = getAuthorIdOrInsert(anthologyTitle.getAuthor());
            long newId = insertAnthologyTitle(bookId, authorId, anthologyTitle.getTitle(), true, false);
            if (newId == INSERT_FAILED) {
                Logger.logError("Failed to insert: " + anthologyTitle);
            }
        }
    }

    /**
     * If the passed ContentValues contains KEY_AUTHOR_LIST, parse them
     * and add the authors.
     *
     * @param bookId               ID of book
     * @param dirtyBookIfNecessary flag to set book dirty or not (for now, always false...)
     */
    private void insertBookAuthors(final long bookId,
                                   @Nullable final List<Author> authors,
                                   @SuppressWarnings("SameParameterValue") final boolean dirtyBookIfNecessary) {
        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        // If we have author details, save them.
        if (authors != null) {
            if (mDeleteBookAuthorsStmt == null) {
                mDeleteBookAuthorsStmt = mStatements.add("mDeleteBookAuthorsStmt",
                        "DELETE FROM " + DB_TB_BOOK_AUTHOR + " WHERE " + DOM_BOOK_ID + " = ?");
            }
            // Need to delete the current records because they may have been reordered and a simple set of updates
            // could result in unique key or index violations.
            mDeleteBookAuthorsStmt.bindLong(1, bookId);
            mDeleteBookAuthorsStmt.execute();

            if (mAddBookAuthorsStmt == null) {
                mAddBookAuthorsStmt = mStatements.add("mAddBookAuthorsStmt",
                        "INSERT Into " + DB_TB_BOOK_AUTHOR
                                + "(" + DOM_BOOK_ID + "," + DOM_AUTHOR_ID + "," + DOM_AUTHOR_POSITION + ")"
                                + "Values(?,?,?)");
            }

            // The list MAY contain duplicates (eg. from Internet lookups of multiple
            // sources), so we track them in a hash table
            final Map<String, Boolean> idHash = new HashMap<>();
            int pos = 0;
            for (Author author : authors) {
                long authorId = 0;
                try {
                    // Get the name and find/add the author
                    authorId = getAuthorIdOrInsert(author);
                    // we use the id as the KEY here, so yes, a String.
                    String authorIdStr = Long.toString(authorId);
                    if (!idHash.containsKey(authorIdStr)) {
                        // indicate this author(id) is already present... but override, so we get elimination of duplicates.
                        idHash.put(authorIdStr, true);
                        pos++;
                        mAddBookAuthorsStmt.bindLong(1, bookId);
                        mAddBookAuthorsStmt.bindLong(2, authorId);
                        mAddBookAuthorsStmt.bindLong(3, pos);
                        // @return the row ID of the last row inserted, if this insert is successful. {@link SynchronizedStatement#INSERT_FAILED} otherwise.
                        mAddBookAuthorsStmt.executeInsert();
                        mAddBookAuthorsStmt.clearBindings();
                    }
                } catch (Exception e) {
                    Logger.logError(e);
                    throw new RuntimeException("Error adding author '" + author + "' {" + authorId + "} to book " + bookId + ": " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Create each book/bookshelf combo in the weak entity
     *
     * @param bookId               The book id
     * @param bookshelves          A separated string of bookshelf names
     * @param dirtyBookIfNecessary flag to set book dirty or not
     */
    private void insertBookBookshelf(final long bookId,
                                     @NonNull final List<String> bookshelves,
                                     @SuppressWarnings("SameParameterValue") final boolean dirtyBookIfNecessary) {

        if (mDeleteBookBookshelfStmt == null) {
            mDeleteBookBookshelfStmt = mStatements.add("mDeleteBookBookshelfStmt",
                    "DELETE FROM " + DB_TB_BOOK_BOOKSHELF_WEAK + " WHERE " + DOM_BOOK_ID + " = ?");
        }
        mDeleteBookBookshelfStmt.bindLong(1, bookId);
        mDeleteBookBookshelfStmt.execute();

        if (mInsertBookBookshelfStmt == null) {
            mInsertBookBookshelfStmt = mStatements.add("mInsertBookBookshelfStmt",
                    "INSERT INTO " + DB_TB_BOOK_BOOKSHELF_WEAK + "(" + DOM_BOOK_ID + ", " + DOM_BOOKSHELF_NAME + ")"
                            + " Values (?,?)");
        }

        for (String bookshelf : bookshelves) {
            String name = bookshelf.trim();
            if (name.isEmpty()) {
                continue;
            }

            long bookshelfId = getBookshelfIdByName(name);
            if (bookshelfId == 0) {
                bookshelfId = insertBookshelf(name);
            }

            if (bookshelfId == INSERT_FAILED) {
                bookshelfId = Bookshelf.DEFAULT_ID;
            }

            try {
                mInsertBookBookshelfStmt.bindLong(1, bookId);
                mInsertBookBookshelfStmt.bindLong(2, bookshelfId);
                mInsertBookBookshelfStmt.execute();
            } catch (Exception e) {
                Logger.logError(e, "Error assigning a book to a bookshelf.");
            }
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }
    }

    /**
     * @throws SQLiteDoneException FIXME
     */
    private void globalReplacePositionedBookItem(@NonNull final String tableName,
                                                 @NonNull final String objectIdField,
                                                 @NonNull final String positionField,
                                                 final long from, final long to) throws SQLiteDoneException {
        if (!mSyncedDb.inTransaction()) {
            throw new RuntimeException("globalReplacePositionedBookItem must be called in a transaction");
        }

        // Update books but prevent duplicate index errors - update books for which the new ID is not already present
        String sql = "UPDATE " + tableName + " Set " + objectIdField + " = " + to +
                " WHERE " + objectIdField + " = " + from +
                " and Not Exists(SELECT NULL FROM " + tableName + " ba" +
                " WHERE ba." + DOM_BOOK_ID + " = " + tableName + "." + DOM_BOOK_ID +
                "   and ba." + objectIdField + " = " + to + ")";
        mSyncedDb.execSQL(sql);

        // Finally, delete the rows that would have caused duplicates. Be cautious by using the
        // EXISTS statement again; it's not necessary, but we do it to reduce the risk of data
        // loss if one of the prior statements failed silently.
        //
        // We also move remaining items up one place to ensure positions remain correct
        //
        sql = "SELECT * FROM " + tableName + " WHERE " + objectIdField + " = " + from
                + " And Exists(SELECT NULL FROM " + tableName + " ba WHERE "
                + "   ba." + DOM_BOOK_ID + " = " + tableName + "." + DOM_BOOK_ID
                + "   and ba." + objectIdField + " = " + to + ")";

        SynchronizedStatement delStmt = null;
        SynchronizedStatement replacementIdPosStmt = null;
        SynchronizedStatement checkMinStmt = null;
        SynchronizedStatement moveStmt = null;
        try (Cursor cursor = mSyncedDb.rawQuery(sql)) {
            // Get the column indexes we need
            final int bookCol = cursor.getColumnIndexOrThrow(DOM_BOOK_ID.name);
            final int posCol = cursor.getColumnIndexOrThrow(positionField);

            // Statement to delete a specific object record
            delStmt = mSyncedDb.compileStatement(
                    "DELETE FROM " + tableName +
                            " WHERE " + objectIdField + " = ? and " + DOM_BOOK_ID + " = ?");

            // Statement to get the position of the already-existing 'new/replacement' object
            replacementIdPosStmt = mSyncedDb.compileStatement(
                    "SELECT " + positionField + " FROM " + tableName +
                            " WHERE " + DOM_BOOK_ID + " = ? and " + objectIdField + " = " + to);

            // Move statement; move a single entry to a new position
            moveStmt = mSyncedDb.compileStatement(
                    "UPDATE " + tableName + " Set " + positionField + " = ?" +
                            " WHERE " + DOM_BOOK_ID + " = ? and " + positionField + " = ?");

            // Sanity check to deal with legacy bad data
            checkMinStmt = mSyncedDb.compileStatement(
                    "SELECT min(" + positionField + ") FROM " + tableName +
                            " WHERE " + DOM_BOOK_ID + " = ?");

            // Loop through all instances of the old author appearing
            while (cursor.moveToNext()) {
                // Get the details of the old object
                long bookId = cursor.getLong(bookCol);
                long pos = cursor.getLong(posCol);

                // Get the position of the new/replacement object
                replacementIdPosStmt.bindLong(1, bookId);
                long replacementIdPos = replacementIdPosStmt.simpleQueryForLong();

                // Delete the old record
                delStmt.bindLong(1, from);
                delStmt.bindLong(2, bookId);
                delStmt.execute();

                // If the deleted object was more prominent than the new object, move the new one up
                if (replacementIdPos > pos) {
                    moveStmt.bindLong(1, pos);
                    moveStmt.bindLong(2, bookId);
                    moveStmt.bindLong(3, replacementIdPos);
                    moveStmt.execute();
                }

                //
                // It is tempting to move all rows up by one when we delete something, but that would have to be
                // done in another sorted cursor in order to prevent duplicate index errors. So we just make
                // sure we have something in position 1.
                //

                // Get the minimum position
                checkMinStmt.bindLong(1, bookId);
                long minPos = checkMinStmt.simpleQueryForLong();
                // If it's > 1, move it to 1
                if (minPos > 1) {
                    moveStmt.bindLong(1, 1);
                    moveStmt.bindLong(2, bookId);
                    moveStmt.bindLong(3, minPos);
                    moveStmt.execute();
                }
            }
        } finally {
            if (delStmt != null) {
                delStmt.close();
            }
            if (moveStmt != null) {
                moveStmt.close();
            }
            if (checkMinStmt != null) {
                checkMinStmt.close();
            }
            if (replacementIdPosStmt != null) {
                replacementIdPosStmt.close();
            }
        }
    }


    /**
     * @param bookId id of book
     *
     * @return list of AnthologyTitle for this book
     */
    @NonNull
    public ArrayList<AnthologyTitle> getBookAnthologyTitleList(final long bookId) {
        ArrayList<AnthologyTitle> list = new ArrayList<>();
        try (Cursor cursor = this.fetchAnthologyTitlesByBookId(bookId)) {
            if (cursor.getCount() == 0) {
                return list;
            }

            final int familyNameCol = cursor.getColumnIndex(DOM_AUTHOR_FAMILY_NAME.name);
            final int givenNameCol = cursor.getColumnIndex(DOM_AUTHOR_GIVEN_NAMES.name);
            final int authorIdCol = cursor.getColumnIndex(DOM_AUTHOR_ID.name);
            final int titleCol = cursor.getColumnIndex(DOM_TITLE.name);

            while (cursor.moveToNext()) {
                Author author = new Author(
                        cursor.getLong(authorIdCol),
                        cursor.getString(familyNameCol),
                        cursor.getString(givenNameCol));

                list.add(new AnthologyTitle(bookId, author, cursor.getString(titleCol)));
            }
        }
        return list;
    }

    @NonNull
    public ArrayList<Author> getBookAuthorList(long rowId) {
        ArrayList<Author> list = new ArrayList<>();
        try (Cursor authors = fetchAuthorsByBookId(rowId)) {
            if (authors.getCount() == 0) {
                return list;
            }

            int idCol = authors.getColumnIndex(DOM_ID.name);
            int familyCol = authors.getColumnIndex(DOM_AUTHOR_FAMILY_NAME.name);
            int givenCol = authors.getColumnIndex(DOM_AUTHOR_GIVEN_NAMES.name);

            while (authors.moveToNext()) {
                list.add(new Author(authors.getLong(idCol), authors.getString(familyCol), authors.getString(givenCol)));
            }
        }
        return list;
    }

    public ArrayList<Series> getBookSeriesList(long rowId) {
        ArrayList<Series> list = new ArrayList<>();
        try (Cursor series = fetchAllSeriesByBook(rowId)) {
            if (series.getCount() == 0) {
                return list;
            }

            int idCol = series.getColumnIndex(DOM_ID.name);
            int nameCol = series.getColumnIndex(DOM_SERIES_NAME.name);
            int numCol = series.getColumnIndex(DOM_SERIES_NUM.name);

            while (series.moveToNext()) {
                list.add(new Series(series.getLong(idCol), series.getString(nameCol), series.getString(numCol)));
            }
        }
        return list;
    }

    /**
     * Return a list of all books in the database
     *
     * @param order What order to return the books
     *
     * @return Cursor over all Books
     */
    public BooksCursor fetchBooks(@NonNull final String bookWhere, @Nullable final String order) {
        // Get the SQL
        String fullSql = getAllBooksSql(bookWhere, order);
        return fetchBooks(fullSql, new String[]{});
    }

    /**
     * Return a book (Cursor) that matches the given rowId
     *
     * @param rowId id of book to retrieve
     *
     * @return Cursor positioned to matching book, if found
     *
     * @throws SQLException if note could not be found/retrieved
     */
    public BooksCursor fetchBookById(final long rowId) throws SQLException {
        String where = "b." + DOM_ID + "=" + rowId;
        return fetchBooks(where, "");
    }

    /**
     * Return a book (Cursor) that matches the given ISBN.
     * Note: MAYBE RETURN MORE THAN ONE BOOK
     *
     * @param isbnList list of ISBN(s) to retrieve
     *
     * @return Cursor positioned to matching book, if found
     *
     * @throws SQLException if none could not be found/retrieved
     */
    public BooksCursor fetchBooksByIsbnList(@NonNull final ArrayList<String> isbnList) {
        if (isbnList.size() == 0) {
            throw new RuntimeException("No ISBNs specified in lookup");
        }

        StringBuilder where = new StringBuilder(TBL_BOOKS.dot(DOM_BOOK_ISBN));
        if (isbnList.size() == 1) {
            where.append(" = '").append(encodeString(isbnList.get(0))).append("'");
        } else {
            where.append(" in (");
            boolean first = true;
            for (String isbn : isbnList) {
                if (first) {
                    first = false;
                } else {
                    where.append(",");
                }
                where.append("'").append(encodeString(isbn)).append("'");
            }
            where.append(")");
        }
        return fetchBooks(where.toString(), "");
    }

    /**
     * This function will create a new bookshelf in the database
     *
     * @param name The bookshelf name
     *
     * @return the row ID of the newly inserted row, or {@link SynchronizedStatement#INSERT_FAILED} if an error occurred
     */
    public long insertBookshelf(@NonNull final String name) {
        // TODO: Decide if we need to backup EMPTY bookshelves...
        ContentValues values = new ContentValues();
        values.put(DOM_BOOKSHELF_NAME.name, name);
        return mSyncedDb.insert(DB_TB_BOOKSHELF, null, values);
    }

    /**
     * Return a Cursor positioned at the bookshelf that matches the given rowId
     *
     * @param id of bookshelf to retrieve
     *
     * @return Name of bookshelf
     */
    @NonNull
    public String getBookshelfName(final long id) {
        if (mGetBookshelfNameStmt == null) {
            mGetBookshelfNameStmt = mStatements.add("mGetBookshelfNameStmt",
                    "SELECT " + DOM_BOOKSHELF_NAME + " FROM " + DB_TB_BOOKSHELF + " WHERE " + DOM_ID + " = ?");
        }
        mGetBookshelfNameStmt.bindLong(1, id);
        return mGetBookshelfNameStmt.simpleQueryForString();
    }

    /**
     * Delete the bookshelf with the given rowId
     *
     * @param id id of bookshelf to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteBookshelf(final long id) {

        boolean dirty = (0 < mSyncedDb.delete(DB_TB_BOOK_BOOKSHELF_WEAK, DOM_BOOKSHELF_NAME + "=" + id, null));
        int rowsAffected = mSyncedDb.delete(DB_TB_BOOKSHELF, DOM_ID + "=" + id, null);

        if (dirty || rowsAffected > 0) {
            setBooksDirtyByBookshelf(id);
        }
        return rowsAffected;
    }

    /**
     * @param bookshelf to find
     *
     * @return the id or 0 if not found
     */
    public long getBookshelfId(@NonNull final Bookshelf bookshelf) throws SQLiteDoneException {
        if (mGetBookshelfIdStmt == null) {
            mGetBookshelfIdStmt = mStatements.add("mGetBookshelfIdStmt",
                    "SELECT " + DOM_ID + " FROM " + DB_TB_BOOKSHELF +
                            " WHERE Upper(" + DOM_BOOKSHELF_NAME + ") = Upper(?)" + COLLATION);
        }

        mGetBookshelfIdStmt.bindString(1, bookshelf.name);
        return mGetBookshelfIdStmt.simpleQueryForLongOrZero();
    }

    /**
     * Update the bookshelf name
     *
     * @param id   id of bookshelf to update
     * @param name value to set bookshelf name to
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int updateBookshelf(final long id, @NonNull final String name) {
        ContentValues values = new ContentValues();
        values.put(DOM_BOOKSHELF_NAME.name, name);
        int rowsAffected = mSyncedDb.update(DB_TB_BOOKSHELF, values, DOM_ID + "=" + id, null);
        if (rowsAffected > 0) {
            purgeAuthors();
            setBooksDirtyByBookshelf(id);
        }
        return rowsAffected;
    }

    /**
     * This will return JUST the bookshelf id based on the name.
     *
     * @param name bookshelf to search for
     *
     * @return bookshelf id, or 0 when not found
     */
    private long getBookshelfIdByName(@NonNull final String name) {
        if (mFetchBookshelfIdByNameStmt == null) {
            mFetchBookshelfIdByNameStmt = mStatements.add("mFetchBookshelfIdByNameStmt",
                    "SELECT " + DOM_ID + " FROM " + DOM_BOOKSHELF_NAME +
                            " WHERE Upper(" + DOM_BOOKSHELF_NAME + ") = Upper(?)" + COLLATION);
        }
        mFetchBookshelfIdByNameStmt.bindString(1, name);
        return mFetchBookshelfIdByNameStmt.simpleQueryForLongOrZero();
    }

    /**
     * Returns a list of all bookshelves in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<Bookshelf> getBookshelves() {
        String sql = "SELECT DISTINCT " + DOM_ID + "," + DOM_BOOKSHELF_NAME +
                " FROM " + DB_TB_BOOKSHELF +
                " ORDER BY Upper(" + DOM_BOOKSHELF_NAME + ") " + COLLATION;

        ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(sql)) {
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(cursor.getLong(0), cursor.getString(1)));
            }
            return list;
        }
    }

    /**
     * Create the list of bookshelves in the underlying data
     *
     * @return The list
     */
    @NonNull
    public String getBookshelvesByBookIdAsStringList(final long bookId) {
        try (Cursor cursor = fetchAllBookshelvesByBook(bookId)) {
            StringBuilder bookshelves_list = new StringBuilder();
            int bsNameCol = cursor.getColumnIndex(UniqueId.KEY_BOOKSHELF_NAME);
            while (cursor.moveToNext()) {
                String encoded_name = ArrayUtils.encodeListItem(Bookshelf.SEPARATOR, cursor.getString(bsNameCol));
                if (bookshelves_list.length() == 0) {
                    bookshelves_list.append(encoded_name);
                } else {
                    bookshelves_list.append(Bookshelf.SEPARATOR).append(encoded_name);
                }
            }
            return bookshelves_list.toString();
        }
    }

    /**
     * Return a Cursor over the list of all bookshelves in the database for the given book
     *
     * @param bookId the book
     *
     * @return Cursor over all bookshelves
     */
    @NonNull
    public Cursor fetchAllBookshelvesByBook(final long bookId) {
        String sql = "SELECT DISTINCT bs." + DOM_ID + " AS " + DOM_ID + "," +
                " bs." + DOM_BOOKSHELF_NAME + " AS " + DOM_BOOKSHELF_NAME +

                " FROM " + DB_TB_BOOKSHELF + " bs, " + DB_TB_BOOK_BOOKSHELF_WEAK + " w " +
                " WHERE w." + DOM_BOOKSHELF_NAME + "=bs." + DOM_ID + " AND w." + DOM_BOOK_ID + "=" + bookId + " " +
                " ORDER BY Upper(bs." + DOM_BOOKSHELF_NAME + ") " + COLLATION;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * @return a list of all defined styles in the database
     */
    @NonNull
    public Cursor getBooklistStyles() {
        final String sql = "SELECT " + TBL_BOOK_LIST_STYLES.ref(DOM_ID, DOM_STYLE) +
                " FROM " + TBL_BOOK_LIST_STYLES.ref();
        // + " Order By " + TBL_BOOK_LIST_STYLES.ref(DOM_ANTHOLOGY_POSITION, DOM_ID);
        return mSyncedDb.rawQuery(sql);
    }

    /**
     * Create a new booklist style
     *
     * @return the row ID of the last row inserted, if this insert is successful. {@link SynchronizedStatement#INSERT_FAILED} otherwise.
     */
    public long insertBooklistStyle(@NonNull final BooklistStyle style) {
        if (mInsertBooklistStyleStmt == null) {
            mInsertBooklistStyleStmt = mStatements.add("mInsertBooklistStyleStmt",
                    TBL_BOOK_LIST_STYLES.getInsert(DOM_STYLE) + " Values (?)");
        }
        final byte[] blob = SerializationUtils.serializeObject(style);
        mInsertBooklistStyleStmt.bindBlob(1, blob);
        // @return the row ID of the last row inserted, if this insert is successful. {@link SynchronizedStatement#INSERT_FAILED} otherwise.
        return mInsertBooklistStyleStmt.executeInsert();
    }

    public void updateBooklistStyle(@NonNull final BooklistStyle style) {
        if (mUpdateBooklistStyleStmt == null) {
            mUpdateBooklistStyleStmt = mStatements.add("mUpdateBooklistStyleStmt",
                    TBL_BOOK_LIST_STYLES.getInsertOrReplaceValues(DOM_ID, DOM_STYLE));
        }
        byte[] blob = SerializationUtils.serializeObject(style);
        mUpdateBooklistStyleStmt.bindLong(1, style.getRowId());
        mUpdateBooklistStyleStmt.bindBlob(2, blob);
        mUpdateBooklistStyleStmt.execute();
    }

    public void deleteBooklistStyle(final long id) {
        if (mDeleteBooklistStyleStmt == null) {
            mDeleteBooklistStyleStmt = mStatements.add("mDeleteBooklistStyleStmt",
                    "DELETE FROM " + TBL_BOOK_LIST_STYLES + " WHERE " + DOM_ID + " = ?");
        }
        mDeleteBooklistStyleStmt.bindLong(1, id);
        mDeleteBooklistStyleStmt.execute();
    }

    /**
     * @param query The query string
     *
     * @return Cursor of search suggestions
     */
    @NonNull
    public Cursor fetchSearchSuggestions(@NonNull final String query) {
        String sql = "SELECT * FROM (SELECT \"BK\" || b." + DOM_ID + " AS " + BaseColumns._ID
                + ", b." + DOM_TITLE + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1
                + ", b." + DOM_TITLE + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                " FROM " + DB_TB_BOOKS + " b" +
                " WHERE b." + DOM_TITLE + " LIKE '" + query + "%'" +
                " UNION " +
                " SELECT \"AF\" || a." + DOM_ID + " AS " + BaseColumns._ID
                + ", a." + DOM_AUTHOR_FAMILY_NAME + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1
                + ", a." + DOM_AUTHOR_FAMILY_NAME + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                " FROM " + DB_TB_AUTHORS + " a" +
                " WHERE a." + DOM_AUTHOR_FAMILY_NAME + " LIKE '" + query + "%'" +
                " UNION " +
                " SELECT \"AG\" || a." + DOM_ID + " AS " + BaseColumns._ID
                + ", a." + DOM_AUTHOR_GIVEN_NAMES + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1
                + ", a." + DOM_AUTHOR_GIVEN_NAMES + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                " FROM " + DB_TB_AUTHORS + " a" +
                " WHERE a." + DOM_AUTHOR_GIVEN_NAMES + " LIKE '" + query + "%'" +
                " UNION " +
                " SELECT \"BK\" || b." + DOM_ID + " AS " + BaseColumns._ID
                + ", b." + DOM_BOOK_ISBN + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1
                + ", b." + DOM_BOOK_ISBN + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                " FROM " + DB_TB_BOOKS + " b" +
                " WHERE b." + DOM_BOOK_ISBN + " LIKE '" + query + "%'" +
                " ) as zzz " +
                " ORDER BY Upper(" + SearchManager.SUGGEST_COLUMN_TEXT_1 + ") " + COLLATION;
        return mSyncedDb.rawQuery(sql, null);
    }

    /**
     * Returns a unique list of all formats in the database; uses the pre-defined ones if none.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getFormats() {
        String sql = "SELECT distinct " + DOM_BOOK_FORMAT + " FROM " + DB_TB_BOOKS
                + " ORDER BY lower(" + DOM_BOOK_FORMAT + ") " + COLLATION;

        try (Cursor cursor = mSyncedDb.rawQuery(sql)) {
            ArrayList<String> list = getFirstColumnAsList(cursor);
            if (list.size() == 0) {
                Collections.addAll(list, BookCatalogueApp.getResourceStringArray(R.array.predefined_formats));
            }
            return list;
        }
    }

    public void globalReplaceFormat(@NonNull final String from, @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            // Update books but prevent duplicate index errors
            String sql = "UPDATE " + DB_TB_BOOKS +
                    " Set " + DOM_BOOK_FORMAT + " = '" + encodeString(to) + "," +
                    " " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                    " WHERE " + DOM_BOOK_FORMAT + " = '" + encodeString(from) + "'";
            mSyncedDb.execSQL(sql);
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    /**
     * Returns a unique list of all locations in the database; uses the pre-defined ones if none.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getGenres() {
        String sql = "SELECT distinct " + DOM_BOOK_GENRE + " FROM " + DB_TB_BOOKS
                + " Order by lower(" + DOM_BOOK_GENRE + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            ArrayList<String> list = getFirstColumnAsList(c);
            if (list.size() == 0) {
                Collections.addAll(list, BookCatalogueApp.getResourceStringArray(R.array.predefined_genres));
            }
            return list;
        }
    }

    public void globalReplaceGenre(@NonNull final String from, @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            String sql;
            // Update books but prevent duplicate index errors
            sql = "UPDATE " + DB_TB_BOOKS +
                    " Set " + DOM_BOOK_GENRE + " = '" + encodeString(to) + "'," +
                    " " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                    " WHERE " + DOM_BOOK_GENRE + " = '" + encodeString(from) + "'";
            mSyncedDb.execSQL(sql);
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    /**
     * Returns a unique list of all languages in the database; uses the pre-defined ones if none.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getLanguages() {
        String sql = "SELECT distinct " + DOM_BOOK_LANGUAGE + " FROM " + DB_TB_BOOKS
                + " Order by lower(" + DOM_BOOK_LANGUAGE + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            ArrayList<String> list = getFirstColumnAsList(c);
            if (list.size() == 0) {
                Collections.addAll(list, BookCatalogueApp.getResourceStringArray(R.array.predefined_languages));
            }
            return list;
        }
    }

    public void globalReplaceLanguage(@NonNull final String from, @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            String sql;
            // Update books but prevent duplicate index errors
            sql = "UPDATE " + DB_TB_BOOKS + " Set " + DOM_BOOK_LANGUAGE + " = '" + encodeString(to) + "'," +
                    " " + DOM_LAST_UPDATE_DATE + " = current_timestamp"
                    + " WHERE " + DOM_BOOK_LANGUAGE + " = '" + encodeString(from) + "'";
            mSyncedDb.execSQL(sql);
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    /**
     * This function will create a new loan in the database
     *
     * @param bookData             the book
     * @param dirtyBookIfNecessary flag to set book dirty or not (for now, always false...)
     *
     * @return the row ID of the newly inserted row, or {@link SynchronizedStatement#INSERT_FAILED} if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertLoan(@NonNull final BookData bookData, final boolean dirtyBookIfNecessary) {
        ContentValues values = new ContentValues();
        values.put(DOM_BOOK_ID.name, bookData.getRowId());
        values.put(DOM_LOANED_TO.name, bookData.getString(DOM_LOANED_TO.name));
        long newId = mSyncedDb.insert(DB_TB_LOAN, null, values);
        if (newId != INSERT_FAILED) {
            purgeLoans();
            if (dirtyBookIfNecessary) {
                setBookDirty(bookData.getRowId());
            }
        }
        return newId;
    }

    /**
     * Delete the loan with the given rowId
     *
     * @param bookId id of book whose loan is to be deleted
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteLoan(final long bookId, final boolean dirtyBookIfNecessary) {
        int rowsAffected = mSyncedDb.delete(DB_TB_LOAN, DOM_BOOK_ID + "=" + bookId, null);
        if (rowsAffected == 0) {
            return 0;
        }

        purgeLoans();
        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }
        return rowsAffected;
    }

    private void purgeLoans() {
        mSyncedDb.delete(DB_TB_LOAN,
                "(" + DOM_BOOK_ID + "='' OR " + DOM_BOOK_ID + "=null" +
                        " OR " + DOM_LOANED_TO + "='' OR " + DOM_LOANED_TO + "=null) ",
                null);
    }

    /**
     * This will return the borrower for a given book, if any
     *
     * @param bookId book to search for
     *
     * @return Who the book is loaned to, blank or null when not loaned to someone
     */
    @Nullable
    public String getLoanByBookId(final long bookId) {
        try (Cursor cursor = mSyncedDb.query(DB_TB_LOAN,
                new String[]{DOM_BOOK_ID.name, DOM_LOANED_TO.name},
                DOM_BOOK_ID + "=" + bookId,
                null, null, null, null)) {

            // 1= DOM_LOANED_TO.name
            return cursor.moveToFirst() ? cursor.getString(1) : null;
        }
    }

    /**
     * Returns a unique list of all locations in the database; uses the pre-defined ones if none.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getLocations() {
        String sql = "SELECT distinct " + DOM_BOOK_LOCATION + " FROM " + DB_TB_BOOKS
                + " Order by lower(" + DOM_BOOK_LOCATION + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            ArrayList<String> list = getFirstColumnAsList(c);
            if (list.size() == 0) {
                Collections.addAll(list, BookCatalogueApp.getResourceStringArray(R.array.predefined_locations));
            }
            return list;
        }
    }

    public void globalReplaceLocation(@NonNull final String from, @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            String sql;
            // Update books but prevent duplicate index errors
            sql = "UPDATE " + DB_TB_BOOKS +
                    " Set " + DOM_BOOK_LOCATION + " = '" + encodeString(to) + "'," +
                    " " + DOM_LAST_UPDATE_DATE + " = current_timestamp "
                    + " WHERE " + DOM_BOOK_LOCATION + " = '" + encodeString(from) + "'";
            mSyncedDb.execSQL(sql);

            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    /**
     * Returns a unique list of all publishers in the database
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getPublishers() {
        String sql = "SELECT distinct " + DOM_PUBLISHER + " FROM " + DB_TB_BOOKS
                + " Order by lower(" + DOM_PUBLISHER + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            return getFirstColumnAsList(c);
        }
    }

    public void globalReplacePublisher(@NonNull final Publisher from, @NonNull final Publisher to) {
        if (Objects.equals(from, to)) {
            return;
        }

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            String sql;
            // Update books but prevent duplicate index errors
            sql = "UPDATE " + DB_TB_BOOKS + " Set " + DOM_PUBLISHER + " = '" + encodeString(to.name) + "'," +
                    " " + DOM_LAST_UPDATE_DATE + " = current_timestamp"
                    + " WHERE " + DOM_PUBLISHER + " = '" + encodeString(from.name) + "'";
            mSyncedDb.execSQL(sql);
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    /**
     * Create a new series in the database
     *
     * @param name A string containing the series name
     *
     * @return the row ID of the newly inserted row, or {@link SynchronizedStatement#INSERT_FAILED} if an error occurred
     */
    private long insertSeries(@NonNull final String name) {
        ContentValues values = new ContentValues();
        values.put(DOM_SERIES_NAME.name, name);
        return mSyncedDb.insert(DB_TB_SERIES, null, values);
    }

    /**
     * Delete the passed series
     *
     * @param id series to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteSeries(final long id) {
        int rowsAffected = mSyncedDb.delete(DB_TB_BOOK_SERIES, DOM_SERIES_ID + " = " + id, null);
        if (rowsAffected == 0) {
            return 0;
        }

        setBooksDirtyBySeries(id);
        purgeSeries();
        return rowsAffected;
    }

    /**
     * This will return the series based on the ID.
     *
     * @return series, or null when not found
     */
    @Nullable
    public Series getSeriesById(final long id) {
        String sql = "SELECT " + DOM_SERIES_NAME + " FROM " + DB_TB_SERIES
                + " WHERE " + DOM_ID + " = " + id;
        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            return cursor.moveToFirst() ? new Series(id, cursor.getString(0), "") : null;
        }
    }

    /**
     * Add or update the passed series, depending whether s.id == 0.
     */
    private void insertOrUpdateSeries(@NonNull final /* out */ Series series) {
        if (series.id != 0) {
            // Get the old series
            Series previous = this.getSeriesById(series.id);
            // Update if changed (case SENSITIVE)
            if (previous != null && !series.equals(previous)) {
                ContentValues values = new ContentValues();
                values.put(DOM_SERIES_NAME.name, series.name);
                // @return the number of rows affected
                if (mSyncedDb.update(DB_TB_SERIES, values, DOM_ID + " = " + series.id, null) > 0) {
                    // Mark all books referencing this series as dirty
                    this.setBooksDirtyBySeries(series.id);
                }
            }
        } else {
            //FIXME: ignores failures
            series.id = insertSeries(series.name);
        }
    }

    /**
     * @return the id, or 0 when not found
     */
    public long getSeriesId(@NonNull final Series s) {
        return getSeriesId(s.name);
    }

    /**
     * @return the id, or 0 when not found
     */
    public long getSeriesId(@NonNull final String name) {
        if (mGetSeriesIdStmt == null) {
            mGetSeriesIdStmt = mStatements.add("mGetSeriesIdStmt",
                    "SELECT " + DOM_ID + " FROM " + DB_TB_SERIES +
                            " WHERE Upper(" + DOM_SERIES_NAME + ") = Upper(?)" + COLLATION);
        }
        mGetSeriesIdStmt.bindString(1, name);
        return mGetSeriesIdStmt.simpleQueryForLongOrZero();
    }

    /**
     * @throws SQLiteDoneException FIXME
     */
    public void globalReplaceSeries(@NonNull final Series from,
                                    @NonNull final Series to) throws SQLiteDoneException {
        if (to.id == 0) {
            long id = getSeriesId(to);
            if (id != 0) {
                to.id = id;
            } else {
                insertOrUpdateSeries(to);
            }
        } else {
            sendSeries(to);
        }

        // Do some basic sanity checks
        if (from.id == 0) {
            from.id = getSeriesId(from);
        }
        if (from.id == 0) {
            throw new RuntimeException("Old Series is not defined");
        }

        if (from.id == to.id) {
            return;
        }

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            setBooksDirtyBySeries(from.id);

            // Update books but prevent duplicate index errors
            String sql = "UPDATE " + DB_TB_BOOK_SERIES + " Set " + DOM_SERIES_ID + " = " + to.id +
                    " WHERE " + DOM_SERIES_ID + " = " + from.id +
                    " and Not Exists(Select NULL FROM " + DB_TB_BOOK_SERIES + " bs WHERE " +
                    "  bs." + DOM_BOOK_ID + " = " + DB_TB_BOOK_SERIES + "." + DOM_BOOK_ID +
                    "   and bs." + DOM_SERIES_ID + " = " + to.id + ")";
            mSyncedDb.execSQL(sql);

            globalReplacePositionedBookItem(DB_TB_BOOK_SERIES, DOM_SERIES_ID.name, DOM_BOOK_SERIES_POSITION.name, from.id, to.id);

            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }
    }

    /**
     * Update or create the passed series.
     */
    public void sendSeries(@NonNull final Series series) {
        if (series.id == 0) {
            series.id = getSeriesId(series);
        }
        insertOrUpdateSeries(series);
    }

    /**
     * Delete the series with no related books
     *
     * @return true if deleted, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean purgeSeries() {
        if (mPurgeBookSeriesStmt == null) {
            mPurgeBookSeriesStmt = mStatements.add("mPurgeBookSeriesStmt",
                    "DELETE FROM " + DB_TB_BOOK_SERIES + " WHERE "
                            + DOM_BOOK_ID + " NOT IN (SELECT DISTINCT " + DOM_ID + " FROM " + DB_TB_BOOKS + ")");
        }
        boolean success;
        // Delete DB_TB_BOOK_SERIES with no books
        try {
            mPurgeBookSeriesStmt.execute();
            success = true;
        } catch (Exception e) {
            Logger.logError(e, "Failed to purge Book Authors");
            success = false;
        }

        if (mPurgeSeriesStmt == null) {
            mPurgeSeriesStmt = mStatements.add("mPurgeSeriesStmt",
                    "DELETE FROM " + DB_TB_SERIES + " WHERE "
                            + DOM_ID + " NOT IN (SELECT DISTINCT " + DOM_SERIES_ID + " FROM " + DB_TB_BOOK_SERIES + ") ");
        }
        // Delete series entries with no Book_Series
        try {
            mPurgeSeriesStmt.execute();
        } catch (Exception e) {
            Logger.logError(e, "Failed to purge Book Authors");
            success = false;
        }
        return success;
    }

    /**
     * @param series id
     *
     * @return number of books in series
     */
    public long countSeriesBooks(@NonNull final Series series) {
        if (series.id == 0) {
            series.id = getSeriesId(series);
        }
        if (series.id == 0) {
            return 0;
        }

        if (mGetSeriesBookCountQuery == null) {
            mGetSeriesBookCountQuery = mStatements.add("mGetSeriesBookCountQuery",
                    "SELECT Count(" + DOM_BOOK_ID + ") FROM " + DB_TB_BOOK_SERIES + " WHERE " + DOM_SERIES_ID + "=?");
        }
        // Be cautious
        synchronized (mGetSeriesBookCountQuery) {
            mGetSeriesBookCountQuery.bindLong(1, series.id);
            return mGetSeriesBookCountQuery.count();
        }
    }

    /**
     * Utility routine to build an arrayList of all series names.
     */
    @NonNull
    public ArrayList<String> getAllSeries() {
        String sql = "SELECT DISTINCT " + DOM_SERIES_NAME + " FROM " + DB_TB_SERIES +
                " ORDER BY Upper(" + DOM_SERIES_NAME + ") " + COLLATION;
        return getColumnAsList(sql, DOM_SERIES_NAME.name);
    }

    /**
     * Return a Cursor over the list of all authors  in the database for the given book
     *
     * @param rowId the rowId of the book
     *
     * @return Cursor over all authors
     */
    @NonNull
    private Cursor fetchAllSeriesByBook(final long rowId) {
        String sql = "SELECT DISTINCT s." + DOM_ID + " AS " + DOM_ID
                + ", s." + DOM_SERIES_NAME + " AS " + DOM_SERIES_NAME
                + ", bs." + DOM_SERIES_NUM + " AS " + DOM_SERIES_NUM
                + ", bs." + DOM_BOOK_SERIES_POSITION + " AS " + DOM_BOOK_SERIES_POSITION
                + ", " + DOM_SERIES_NAME + "||' ('||" + DOM_SERIES_NUM + "||')' as " + DOM_SERIES_FORMATTED
                + " FROM " + DB_TB_BOOK_SERIES + " bs Join " + DB_TB_SERIES + " s "
                + "       On s." + DOM_ID + " = bs." + DOM_SERIES_ID
                + " WHERE bs." + DOM_BOOK_ID + "=" + rowId + " "
                + " ORDER BY bs." + DOM_BOOK_SERIES_POSITION + ", Upper(s." + DOM_SERIES_NAME + ") " + COLLATION + " ASC";
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    @NonNull
    private String makeEqualFieldsTerm(@NonNull final String v1, @NonNull final String v2) {
        return "Upper(" + v1 + ") = Upper(" + v2 + ") " + COLLATION;
    }

    /**
     * Return the SQL for a list of all books in the database
     *
     * @param bookWhere clause to add to books search criteria
     * @param order     What order to return the books; comma separated list of column names
     *
     * @return A full piece of SQL to perform the search
     */
    private String getAllBooksSql(@NonNull final String bookWhere, @Nullable final String order) {
        // Get the basic query; we will use it as a sub-query
        String sql = "SELECT DISTINCT " + getSQLFieldsForBook(TBL_BOOKS.getAlias(), DOM_ID.name) +
                " FROM " + DB_TB_BOOKS + " b" +
                (!bookWhere.isEmpty() ? " WHERE " + " (" + bookWhere + ")" : "") +
                " ORDER BY Upper(b." + DOM_TITLE + ") " + COLLATION + " ASC";

        String fullSql = "SELECT b.*, " + getSQLFieldsForAuthor(TBL_AUTHORS.getAlias(), "") + ", " +
                "a." + DOM_AUTHOR_ID + ", " +
                "Coalesce(s." + DOM_SERIES_ID + ", 0) AS " + DOM_SERIES_ID + ", " +
                "Coalesce(s." + DOM_SERIES_NAME + ", '') AS " + DOM_SERIES_NAME + ", " +
                "Coalesce(s." + DOM_SERIES_NUM + ", '') AS " + DOM_SERIES_NUM + ", " +
                " Case When _num_series < 2 Then Coalesce(s." + DOM_SERIES_FORMATTED + ", '')" +
                " Else " + DOM_SERIES_FORMATTED + "||' et. al.' End AS " + DOM_SERIES_FORMATTED + " " +
                " FROM (" + sql + ") b";

        // Get the 'default' author...defined in getSQLFieldsForBook()
        fullSql += " Join (Select "
                + DOM_AUTHOR_ID + ", "
                + DOM_AUTHOR_FAMILY_NAME + ", "
                + DOM_AUTHOR_GIVEN_NAMES + ", "
                + "ba." + DOM_BOOK_ID + " AS " + DOM_BOOK_ID + ", "
                + " Case When " + DOM_AUTHOR_GIVEN_NAMES + " = '' Then " + DOM_AUTHOR_FAMILY_NAME
                + " Else " + authorFormattedSource("") + " End AS " + DOM_AUTHOR_FORMATTED
                + " FROM " + DB_TB_BOOK_AUTHOR + " ba Join " + DB_TB_AUTHORS + " a"
                + "    On ba." + DOM_AUTHOR_ID + " = a." + DOM_ID + ") a "
                + " On a." + DOM_BOOK_ID + " = b." + DOM_ID + " and a." + DOM_AUTHOR_ID + " = b." + DOM_AUTHOR_ID;

        // Get the 'default' series...defined in getSQLFieldsForBook()
        fullSql += " Left Outer Join (Select "
                + DOM_SERIES_ID + ", "
                + DOM_SERIES_NAME + ", "
                + DOM_SERIES_NUM + ", "
                + "bs." + DOM_BOOK_ID + " AS " + DOM_BOOK_ID + ", "
                + " Case When " + DOM_SERIES_NUM + " = '' Then " + DOM_SERIES_NAME
                + " Else " + DOM_SERIES_NAME + "||' #'||" + DOM_SERIES_NUM + " End AS " + DOM_SERIES_FORMATTED
                + " FROM " + DB_TB_BOOK_SERIES + " bs Join " + DB_TB_SERIES + " s"
                + "    On bs." + DOM_SERIES_ID + " = s." + DOM_ID + ") s "
                + " On s." + DOM_BOOK_ID + " = b." + DOM_ID
                + " and s." + DOM_SERIES_ID + " = b." + DOM_SERIES_ID
                + " and " + this.makeEqualFieldsTerm("s." + DOM_SERIES_NUM, "b." + DOM_SERIES_NUM);

        if (order != null && !order.isEmpty()) {
            fullSql += " ORDER BY " + order;
        }
        return fullSql;
    }

    /**
     * Utility routine to fill an array with the specified column from the passed SQL.
     *
     * @param sql        SQL to execute
     * @param columnName Column to fetch
     *
     * @return List of *all* values
     *
     * @see #getFirstColumnAsList
     */
    @NonNull
    private ArrayList<String> getColumnAsList(@NonNull final String sql, @NonNull final String columnName) {
        ArrayList<String> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{})) {
            int column = cursor.getColumnIndexOrThrow(columnName);
            while (cursor.moveToNext()) {
                list.add(cursor.getString(column));
            }
            return list;
        }
    }

    /**
     * Takes the ResultSet from a Cursor, and fetches column 0 as a String into an ArrayList
     *
     * @param cursor cursor
     *
     * @return List of unique values
     *
     * @see #getColumnAsList
     */
    @NonNull
    private ArrayList<String> getFirstColumnAsList(@NonNull final Cursor cursor) {
        ArrayList<String> list = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                if (name != null) {
                    // Hash to *try* to avoid duplicates
                    Set<String> foundSoFar = new HashSet<>();
                    if (!name.isEmpty() && !foundSoFar.contains(name.toLowerCase())) {
                        foundSoFar.add(name.toLowerCase());
                        list.add(name);
                    }
                }
            }
        } finally {
            cursor.close();
        }
        return list;
    }

    /**
     * Return a ContentValues collection containing only those values from 'source' that match columns in 'dest'.
     * - Exclude the primary key from the list of columns.
     * - data will be transformed based on the intended type of the underlying column based on column definition.
     *
     * @param bookData Source column data
     *
     * @return New, filtered, collection
     */
    @NonNull
    private ContentValues filterValues(@NonNull final BookData bookData) {
        ContentValues values = new ContentValues();
        // Create the arguments
        for (String key : bookData.keySet()) {
            // Get column info for this column.
            TableInfo.ColumnInfo columnInfo = mBooksInfo.getColumn(key);
            // Check if we actually have a matching column.
            if (columnInfo != null) {
                // Never update PK.
                if (!columnInfo.isPrimaryKey) {

                    Object entry = bookData.get(key);

                    // Try to set the appropriate value, but if that fails, just use TEXT...
                    try {

                        switch (columnInfo.typeClass) {

                            case TableInfo.CLASS_REAL:
                                if (entry instanceof Float) {
                                    values.put(columnInfo.name, (Float) entry);
                                } else {
                                    values.put(columnInfo.name, Float.parseFloat(entry.toString()));
                                }
                                break;

                            case TableInfo.CLASS_INTEGER:
                                if (entry instanceof Boolean) {
                                    if ((Boolean) entry) {
                                        values.put(columnInfo.name, 1);
                                    } else {
                                        values.put(columnInfo.name, 0);
                                    }
                                } else if (entry instanceof Integer) {
                                    values.put(columnInfo.name, (Integer) entry);
                                } else {
                                    values.put(columnInfo.name, Integer.parseInt(entry.toString()));
                                }
                                break;

                            case TableInfo.CLASS_TEXT:
                                if (entry instanceof String) {
                                    values.put(columnInfo.name, ((String) entry));
                                } else {
                                    values.put(columnInfo.name, entry.toString());
                                }
                                break;
                        }

                    } catch (Exception e) {
                        if (entry != null) {
                            values.put(columnInfo.name, entry.toString());
                        }
                    }
                }
            }
        }
        return values;
    }

    /**
     * The passed sql should at least return some of the fields from the books table!
     * If a method call is made to retrieve a column that does not exists, an exception
     * will be thrown.
     *
     * @return A new BooksCursor
     */
    private BooksCursor fetchBooks(@NonNull final String sql,
                                   @NonNull final String[] selectionArgs) {
        return (BooksCursor) mSyncedDb.rawQueryWithFactory(mBooksFactory, sql, selectionArgs, "");
    }

    /**
     * Return a book (Cursor) that matches the given goodreads book Id.
     * Note: MAYE RETURN MORE THAN ONE BOOK
     *
     * @param grId Goodreads id of book(s) to retrieve
     *
     * @return Cursor positioned to matching book, if found
     *
     * @throws SQLException if note could not be found/retrieved
     */
    public BooksCursor fetchBooksByGoodreadsBookId(long grId) throws SQLException {
        String where = TBL_BOOKS.dot(DOM_GOODREADS_BOOK_ID) + "=" + grId;
        return fetchBooks(where, "");
    }

    /**
     * Query to get all book IDs and ISBN for sending to goodreads.
     */
    @NonNull
    public BooksCursor getAllBooksForGoodreadsCursor(final long startId, final boolean updatesOnly) {
        String sql = "SELECT " + DOM_BOOK_ISBN + ", " + DOM_ID + ", " + DOM_GOODREADS_BOOK_ID + "," +
                " " + DOM_BOOK_NOTES + ", " + DOM_BOOK_READ + ", " + DOM_BOOK_READ_END + ", " + DOM_BOOK_RATING +
                " FROM " + DB_TB_BOOKS + " WHERE " + DOM_ID + " > " + startId;
        if (updatesOnly) {
            sql += " and " + DOM_LAST_UPDATE_DATE + " > " + DOM_GOODREADS_LAST_SYNC_DATE;
        }
        sql += " Order by " + DOM_ID;

        return fetchBooks(sql, new String[]{});
    }

    /**
     * Query to get a specific book ISBN from the ID for sending to goodreads.
     */
    @NonNull
    public BooksCursor getBookForGoodreadsCursor(final long bookId) {
        String sql = "SELECT " + DOM_ID + ", " + DOM_BOOK_ISBN + ", " + DOM_GOODREADS_BOOK_ID +
                ", " + DOM_BOOK_NOTES + ", " + DOM_BOOK_READ + ", " + DOM_BOOK_READ_END + ", " + DOM_BOOK_RATING +
                " FROM " + DB_TB_BOOKS + " WHERE " + DOM_ID + " = " + bookId + " Order by " + DOM_ID;
        return fetchBooks(sql, new String[]{});
    }

    /**
     * Query to get a all bookshelves for a book, for sending to goodreads.
     */
    @NonNull
    public Cursor getAllBookBookshelvesForGoodreadsCursor(final long bookId) {
        String sql = "SELECT s." + DOM_BOOKSHELF_NAME + " FROM " + DB_TB_BOOKSHELF + " s"
                + " Join " + DB_TB_BOOK_BOOKSHELF_WEAK + " bbs On bbs." + DOM_BOOKSHELF_NAME + " = s." + DOM_ID
                + " and bbs." + DOM_BOOK_ID + " = " + bookId + " Order by s." + DOM_BOOKSHELF_NAME;
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * Set the goodreads book id for this passed book. This is used by other goodreads-related
     * functions.
     *
     * @param bookId          Book to update
     * @param goodreadsBookId GR book id
     */
    public void setGoodreadsBookId(final long bookId, final long goodreadsBookId) {
        if (mSetGoodreadsBookIdStmt == null) {
            String sql = "UPDATE " + TBL_BOOKS + " Set " + DOM_GOODREADS_BOOK_ID + " = ? WHERE " + DOM_ID + " = ?";
            mSetGoodreadsBookIdStmt = mStatements.add("mSetGoodreadsBookIdStmt", sql);
        }
        mSetGoodreadsBookIdStmt.bindLong(1, goodreadsBookId);
        mSetGoodreadsBookIdStmt.bindLong(2, bookId);
        mSetGoodreadsBookIdStmt.execute();
    }

    /**
     * Set the goodreads sync date to the current time
     */
    public void setGoodreadsSyncDate(final long bookId) {
        if (mSetGoodreadsSyncDateStmt == null) {
            String sql = "UPDATE " + DB_TB_BOOKS + " Set " + DOM_GOODREADS_LAST_SYNC_DATE + " = current_timestamp WHERE " + DOM_ID + " = ?";
            mSetGoodreadsSyncDateStmt = mStatements.add("mSetGoodreadsSyncDateStmt", sql);
        }
        mSetGoodreadsSyncDateStmt.bindLong(1, bookId);
        mSetGoodreadsSyncDateStmt.execute();
    }

    /**
     * Set the goodreads sync date to the current time
     *
     * @param bookId if found
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    @NonNull
    public String getGoodreadsSyncDate(final long bookId) {
        if (mGetGoodreadsSyncDateStmt == null) {
            String sql = "SELECT " + DOM_GOODREADS_LAST_SYNC_DATE + " FROM " + DB_TB_BOOKS + " WHERE " + DOM_ID + " = ?";
            mGetGoodreadsSyncDateStmt = mStatements.add("mGetGoodreadsSyncDateStmt", sql);
        }
        mGetGoodreadsSyncDateStmt.bindLong(1, bookId);
        return mGetGoodreadsSyncDateStmt.simpleQueryForString();
    }

    /**
     * Open the books database. If it cannot be opened, try to create a new instance of the
     * database. If it cannot be created, throw an exception to signal the failure
     *
     * @throws SQLException if the database could be neither opened or created
     */
    public void open() throws SQLException {

        if (mSyncedDb == null) {
            // Get the DB wrapper
            mSyncedDb = new SynchronizedDb(mDbHelper, mSynchronizer);
            // Turn on foreign key support so that CASCADE works.
            mSyncedDb.execSQL("PRAGMA foreign_keys = ON");
            // Turn on recursive triggers; not strictly necessary
            mSyncedDb.execSQL("PRAGMA recursive_triggers = ON");
        }
        //mSyncedDb.execSQL("PRAGMA temp_store = FILE");
        mStatements = new SqlStatementManager(mSyncedDb);
    }

    /**
     * Generic function to close the database
     */
    public void close() {

        if (!mCloseWasCalled) {
            mCloseWasCalled = true;

            if (mStatements != null) {
                mStatements.close();
            }

            if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
                synchronized (mDebugInstanceCount) {
                    mDebugInstanceCount--;
                    System.out.println("CatDBA instances: " + mDebugInstanceCount);
                    //debugRemoveInstance(this);
                }
            }
        }
    }

    @Override
    protected void finalize() {
        close();
    }

    public SyncLock startTransaction(boolean isUpdate) {
        return mSyncedDb.beginTransaction(isUpdate);
    }

    public void endTransaction(SyncLock lock) {
        mSyncedDb.endTransaction(lock);
    }

    public void setTransactionSuccessful() {
        mSyncedDb.setTransactionSuccessful();
    }

    /**
     * Send the book details from the cursor to the passed fts query.
     *
     * NOTE: This assumes a specific order for query parameters.
     * If modified, then update {@link #insertFts} , {@link #updateFts} and {@link #rebuildFts}
     *
     * @param books Cursor of books to update
     * @param stmt  Statement to execute
     */
    private void ftsSendBooks(@NonNull final BooksCursor books, @NonNull final SynchronizedStatement stmt) {

        final BooksRow book = books.getRowView();
        // Build the SQL to get author details for a book.
        // ... all authors
        final String authorBaseSql = "SELECT " + TBL_AUTHORS.dot("*") + " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS) +
                " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_ID) + " = ";
        // ... all series
        final String seriesBaseSql = "SELECT " + TBL_SERIES.dot(DOM_SERIES_NAME) + " || ' ' || Coalesce(" + TBL_BOOK_SERIES.dot(DOM_SERIES_NUM) + ",'') as seriesInfo FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES) +
                " WHERE " + TBL_BOOK_SERIES.dot(DOM_BOOK_ID) + " = ";
        // ... all anthology titles
        final String anthologyBaseSql = "SELECT " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " || ' ' || " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " as anthologyAuthorInfo, " + DOM_TITLE + " as anthologyTitleInfo "
                + " FROM " + TBL_ANTHOLOGY.ref() + TBL_ANTHOLOGY.join(TBL_AUTHORS) +
                " WHERE " + TBL_ANTHOLOGY.dot(DOM_BOOK_ID) + " = ";

        // Accumulator for author names for each book
        StringBuilder authorText = new StringBuilder();
        // Accumulator for series names for each book
        StringBuilder seriesText = new StringBuilder();
        // Accumulator for title names for each anthology
        StringBuilder titleText = new StringBuilder();
        // Indexes of fields in cursor, -1 for 'not initialised yet'
        int colGivenNames = -1;
        int colFamilyName = -1;
        int colSeriesInfo = -1;
        int colAnthologyAuthorInfo = -1;
        int colAnthologyTitleInfo = -1;

        // Process each book
        while (books.moveToNext()) {
            // Reset authors/series/title
            authorText.setLength(0);
            seriesText.setLength(0);
            titleText.setLength(0);
            // Get list of authors
            try (Cursor c = mSyncedDb.rawQuery(authorBaseSql + book.getId())) {
                // Get column indexes, if not already got
                if (colGivenNames < 0) {
                    colGivenNames = c.getColumnIndex(DOM_AUTHOR_GIVEN_NAMES.name);
                }
                if (colFamilyName < 0) {
                    colFamilyName = c.getColumnIndex(DOM_AUTHOR_FAMILY_NAME.name);
                }
                // Append each author
                while (c.moveToNext()) {
                    authorText.append(c.getString(colGivenNames));
                    authorText.append(" ");
                    authorText.append(c.getString(colFamilyName));
                    authorText.append(";");
                }
            }

            // Get list of series
            try (Cursor c = mSyncedDb.rawQuery(seriesBaseSql + book.getId())) {
                // Get column indexes, if not already got
                if (colSeriesInfo < 0) {
                    colSeriesInfo = c.getColumnIndex("seriesInfo");
                }
                // Append each series
                while (c.moveToNext()) {
                    seriesText.append(c.getString(colSeriesInfo));
                    seriesText.append(";");
                }
            }

            // Get list of anthology data (author and title)
            try (Cursor c = mSyncedDb.rawQuery(anthologyBaseSql + book.getId())) {
                // Get column indexes, if not already got
                if (colAnthologyAuthorInfo < 0) {
                    colAnthologyAuthorInfo = c.getColumnIndex("anthologyAuthorInfo");
                }
                if (colAnthologyTitleInfo < 0) {
                    colAnthologyTitleInfo = c.getColumnIndex("anthologyTitleInfo");
                }
                // Append each series
                while (c.moveToNext()) {
                    authorText.append(c.getString(colAnthologyAuthorInfo));
                    authorText.append(";");
                    titleText.append(c.getString(colAnthologyTitleInfo));
                    titleText.append(";");
                }
            }

            // Set the parameters and call
            bindStringOrNull(stmt, 1, authorText.toString());
            // Titles should only contain title, not SERIES
            bindStringOrNull(stmt, 2, book.getTitle() + "; " + titleText);
            // We could add a 'series' column, or just add it as part of the description
            bindStringOrNull(stmt, 3, book.getDescription() + seriesText);
            bindStringOrNull(stmt, 4, book.getNotes());
            bindStringOrNull(stmt, 5, book.getPublisher());
            bindStringOrNull(stmt, 6, book.getGenre());
            bindStringOrNull(stmt, 7, book.getLocation());
            bindStringOrNull(stmt, 8, book.getIsbn());
            stmt.bindLong(9, book.getId());

            stmt.execute();
        }
    }

    /**
     * Utility function to bind a string or NULL value to a parameter since binding a NULL
     * in bindString produces an error.
     *
     * NOTE: We specifically want to use the default locale for this.
     */
    private void bindStringOrNull(@NonNull final SynchronizedStatement stmt, final int position, @Nullable final String s) {
        if (s == null) {
            stmt.bindNull(position);
        } else {
            //
            // Because FTS does not understand locales in all android up to 4.2,
            // we do case folding here using the default locale. TODO: check if still so.
            //
            stmt.bindString(position, s.toLowerCase(Locale.getDefault()));
        }
    }

    /**
     * Insert a book into the FTS. Assumes book does not already exist in FTS.
     */
    private void insertFts(final long bookId) {
        long t0 = System.currentTimeMillis();

        if (mInsertFtsStmt == null) {
            // Build the FTS insert statement base. The parameter order MUST match the order expected in {@link #ftsSendBooks}.
            String sql = TBL_BOOKS_FTS.getInsert(
                    DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION,
                    DOM_BOOK_NOTES, DOM_PUBLISHER, DOM_BOOK_GENRE,
                    DOM_BOOK_LOCATION, DOM_BOOK_ISBN, DOM_DOCID)
                    + " Values (?,?,?,?,?,?,?,?,?)";
            mInsertFtsStmt = mStatements.add("mInsertFtsStmt", sql);
        }

        // Start an update TX
        SyncLock l = null;
        if (!mSyncedDb.inTransaction()) {
            l = mSyncedDb.beginTransaction(true);
        }
        try (BooksCursor books = fetchBooks("SELECT * FROM " + TBL_BOOKS + " WHERE " + DOM_ID + " = " + bookId, new String[]{})) {
            // Compile statement and get books cursor
            // Send the book
            ftsSendBooks(books, mInsertFtsStmt);
            if (l != null) {
                mSyncedDb.setTransactionSuccessful();
            }
        } finally {
            if (l != null) {
                mSyncedDb.endTransaction(l);
            }
            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                long t1 = System.currentTimeMillis();
                System.out.println("Inserted FTS in " + (t1 - t0) + "ms");
            }
        }
    }

    /**
     * Update an existing FTS record.
     */
    private void updateFts(final long bookId) {
        long t0 = System.currentTimeMillis();

        if (mUpdateFtsStmt == null) {
            // Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
            String sql = TBL_BOOKS_FTS.getUpdate(
                    DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION,
                    DOM_BOOK_NOTES, DOM_PUBLISHER, DOM_BOOK_GENRE,
                    DOM_BOOK_LOCATION, DOM_BOOK_ISBN) + " WHERE " + DOM_DOCID + " = ?";
            mUpdateFtsStmt = mStatements.add("mUpdateFtsStmt", sql);
        }

        SyncLock l = null;
        if (!mSyncedDb.inTransaction()) {
            l = mSyncedDb.beginTransaction(true);
        }
        try (BooksCursor books = fetchBooks("SELECT * FROM " + TBL_BOOKS + " where " + DOM_ID + " = " + bookId, new String[]{})) {
            ftsSendBooks(books, mUpdateFtsStmt);
            if (l != null) {
                mSyncedDb.setTransactionSuccessful();
            }
        } finally {
            if (l != null) {
                mSyncedDb.endTransaction(l);
            }
            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                long t1 = System.currentTimeMillis();
                System.out.println("Updated FTS in " + (t1 - t0) + "ms");
            }
        }
    }

    /**
     * Delete an existing FTS record
     */
    private void deleteFts(final long bookId) {
        long t0 = System.currentTimeMillis();
        if (mDeleteFtsStmt == null) {
            mDeleteFtsStmt = mStatements.add("mDeleteFtsStmt",
                    "DELETE FROM " + TBL_BOOKS_FTS + " WHERE " + DOM_DOCID + " = ?");
        }
        mDeleteFtsStmt.bindLong(1, bookId);
        mDeleteFtsStmt.execute();
        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            long t1 = System.currentTimeMillis();
            System.out.println("Deleted FROM FTS in " + (t1 - t0) + "ms");
        }
    }

    /**
     * Rebuild the entire FTS database. This can take several seconds with many books or a slow phone.
     */
    public void rebuildFts() {
        long t0;
        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            t0 = System.currentTimeMillis();
        }
        boolean gotError = false;

        // Make a copy of the FTS table definition for our temp table.
        TableDefinition ftsTemp = TBL_BOOKS_FTS.clone();
        // Give it a new name
        ftsTemp.setName(ftsTemp.getName() + "_temp");

        SyncLock lock = null;
        if (!mSyncedDb.inTransaction()) {
            lock = mSyncedDb.beginTransaction(true);
        }
        // Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
        final String sql = ftsTemp.getInsert(
                DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION,
                DOM_BOOK_NOTES, DOM_PUBLISHER, DOM_BOOK_GENRE,
                DOM_BOOK_LOCATION, DOM_BOOK_ISBN, DOM_DOCID)
                + " values (?,?,?,?,?,?,?,?,?)";
        // Drop and recreate our temp copy
        ftsTemp.create(mSyncedDb, false);
        ftsTemp.drop(mSyncedDb);

        try (SynchronizedStatement insert = mSyncedDb.compileStatement(sql);
             BooksCursor cursor = fetchBooks("SELECT * FROM " + TBL_BOOKS, new String[]{})) {
            ftsSendBooks(cursor, insert);
            // Drop old table, ready for rename
            TBL_BOOKS_FTS.drop(mSyncedDb);
            if (lock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
        } catch (Exception e) {
            Logger.logError(e);
            gotError = true;
        } finally {
            if (lock != null) {
                mSyncedDb.endTransaction(lock);
            }

			/* According to this:
			    http://old.nabble.com/Bug-in-FTS3-when-trying-to-rename-table-within-a-transaction-td29474500.html

			  FTS tables should only be renamed outside of transactions. Which is a pain.

			   => makes sense... this is DDL after all. DDL is not covered by most/all databases for transactions.
			   => very few (IMHO) programs manipulate tables themselves. The DBA's don't allow it.
			*/

            //  Delete old table and rename the new table
            if (!gotError) {
                mSyncedDb.execSQL("Alter Table " + ftsTemp + " rename to " + TBL_BOOKS_FTS);
            }
        }

        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            System.out.println("books reindexed in " + (System.currentTimeMillis() - t0) + "ms");
        }
    }

    /**
     * Search the FTS table and return a cursor.
     *
     * ENHANCE: Integrate with existing search code, if we keep it.
     *
     * @param author   Author-related keywords to find
     * @param title    Title-related keywords to find
     * @param keywords Keywords to find anywhere in book
     *
     * @return a cursor
     */
    @Nullable
    public Cursor searchFts(@NonNull String author, @NonNull String title, @NonNull String keywords) {
        author = cleanupFtsCriterion(author);
        title = cleanupFtsCriterion(title);
        keywords = cleanupFtsCriterion(keywords);

        if ((author.length() + title.length() + keywords.length()) == 0) {
            return null;
        }

        StringBuilder sql = new StringBuilder(
                "SELECT " + DOM_DOCID + " FROM " + TBL_BOOKS_FTS + " where " + TBL_BOOKS_FTS + " match '" + keywords);
        for (String w : author.split(" ")) {
            if (!w.isEmpty()) {
                sql.append(" ").append(DOM_AUTHOR_NAME).append(":").append(w);
            }
        }
        for (String w : title.split(" ")) {
            if (!w.isEmpty()) {
                sql.append(" ").append(DOM_TITLE).append(":").append(w);
            }
        }
        sql.append("'");

        return mSyncedDb.rawQuery(sql.toString(), new String[]{});
    }

    /**
     * Backup database file using default file name
     */
    public void backupDbFile() {
        backupDbFile("DbExport.db");
    }

    /**
     * Backup database file to the specified filename
     */
    public void backupDbFile(@NonNull final String toFile) {
        StorageUtils.backupDbFile(mSyncedDb.getUnderlyingDatabase(), toFile);
    }

    /**
     * A complete export of all tables (flattened) in the database
     *
     * @return BooksCursor over all books, authors, etc
     */
    @NonNull
    public BooksCursor exportBooks(@Nullable final Date sinceDate) {
        String sinceClause;
        if (sinceDate == null) {
            sinceClause = "";
        } else {
            sinceClause = " WHERE b." + DOM_LAST_UPDATE_DATE + " > '" + DateUtils.toSqlDateTime(sinceDate) + "' ";
        }

        String sql = "SELECT DISTINCT " +
                getSQLFieldsForBook("b", DOM_ID.name) + ", " +
                "l." + DOM_LOANED_TO + " AS " + DOM_LOANED_TO +
                " FROM " + DB_TB_BOOKS + " b" +
                " LEFT OUTER JOIN " + DB_TB_LOAN + " l ON (l." + DOM_BOOK_ID + "=b." + DOM_ID + ") " +
                sinceClause +
                " ORDER BY b._id";
        return fetchBooks(sql, new String[]{});
    }

    /**
     * Data cleanup routine called on upgrade to v4.0.3 to cleanup data integrity issues cased
     * by earlier merge code that could have left the first author or series for a book having
     * a position number > 1.
     */
    public void fixupAuthorsAndSeries() {
        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            fixupPositionedBookItems(DB_TB_BOOK_AUTHOR, DOM_AUTHOR_POSITION.name);
            fixupPositionedBookItems(DB_TB_BOOK_SERIES, DOM_BOOK_SERIES_POSITION.name);
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(l);
        }

    }

    /**
     * Data cleaning routine for upgrade to version 4.0.3 to cleanup any books that have no primary author/series.
     */
    private void fixupPositionedBookItems(@NonNull final String tableName,
                                          @NonNull final String positionField) {
        String sql = "SELECT b." + DOM_ID + " AS " + DOM_ID + ", min(o." + positionField + ") as pos" +
                " FROM " + TBL_BOOKS + " b join " + tableName + " o On o." + DOM_BOOK_ID + " = b." + DOM_ID +
                " Group by b." + DOM_ID;

        SynchronizedStatement moveStmt = null;
        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            // Get the column indexes we need
            final int bookCol = c.getColumnIndexOrThrow(DOM_ID.name);
            final int posCol = c.getColumnIndexOrThrow("pos");
            // Loop through all instances of the old author appearing
            while (c.moveToNext()) {
                // Get the details
                long pos = c.getLong(posCol);
                if (pos > 1) {
                    if (moveStmt == null) {
                        // Statement to move records up by a given offset
                        sql = "UPDATE " + tableName + " Set " + positionField + " = 1 WHERE " + DOM_BOOK_ID + " = ? and " + positionField + " = ?";
                        moveStmt = mSyncedDb.compileStatement(sql);
                    }

                    long book = c.getLong(bookCol);
                    // Move subsequent records up by one
                    moveStmt.bindLong(1, book);
                    moveStmt.bindLong(2, pos);
                    moveStmt.execute();
                }
            }
        } finally {
            if (moveStmt != null) {
                moveStmt.close();
            }
        }
    }

    private static class InstanceRef extends WeakReference<CatalogueDBAdapter> {
        private final Exception mCreationException;

        InstanceRef(@NonNull final CatalogueDBAdapter db) {
            super(db);
            mCreationException = new RuntimeException();
        }

        @NonNull
        Exception getCreationException() {
            return mCreationException;
        }
    }

    public class AnthologyTitleExistsException extends RuntimeException {
        private static final long serialVersionUID = -9052087086134217566L;

        AnthologyTitleExistsException() {
            super("Anthology title already exists");
        }
    }
}

