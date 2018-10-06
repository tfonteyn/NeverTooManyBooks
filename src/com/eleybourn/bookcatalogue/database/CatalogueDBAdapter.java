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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.entities.BookData;
import com.eleybourn.bookcatalogue.entities.BookRow;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ANTHOLOGY_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ANTHOLOGY_IS_AN_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ANTHOLOGY_WITH_MULTIPLE_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_MASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DOCID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_STYLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKLIST_STYLES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS_FTS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_LOAN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.COLLATION;

/**
 * TODO New anthology stuff:
 * - test first pub of book
 * - test an upgrade from 82 to 83
 *
 * next big step:
 * - allow 1 title in multiple books
 * AnthologyTitle needs splitting
 *
 * to think about:
 * - when to delete entries in ANTHOLOGY ? when last book is gone or keep them, for adding to new books / wish list
 * - consider a purge based on book for orphan AnthologyTitle (a purge based on Author is already done)
 *
 * TODO other things
 * long term:
 * - instead of string encoding of lists, use JSON ?
 * pro: easier / (sort of) foolproof / FLEXIBLE!
 * con: fools / fields will be bigger due to json labels
 * => or... do a specific JSON Exporter / Importer as an alternative to the Csv model (con: not easy to get 'others' to provide import files)
 *
 * - anthology titles can be in series... ouch....
 *
 *
 * - lots of using 'Upper()' when sorting. Replace by an extra table column with the value pre-upper'd
 * pro: sorting faster
 * con: more space
 *
 *
 *
 * - bind variables versus string concat
 *
 *
 *
 *
 * Book Catalogue database access helper class. Defines the basic CRUD operations
 * for the catalogue (based on the Notepad tutorial), and gives the
 * ability to list all books as well as retrieve or modify a specific book.
 *
 * NOTE: As of 4.2, DO NOT USE OnUpgrade TO DISPLAY UPGRADE MESSAGES.
 * Use the UpgradeMessageManager class.
 * This change separated messages from DB changes (most releases do not involve DB upgrades).
 *
 * FIXME:
 * Very few methods can throw an SQLiteDoneException. They have been marked as such.
 * The caller is either already catching them, or believes they will never happen.
 *
 * Methods return -1 for failing to insert. Not all caller check.
 *
 * ENHANCE: Use date_added to add 'Recent Acquisitions' virtual shelf; need to resolve how this may relate to date_purchased and 'I own this book'...
 */
public class CatalogueDBAdapter {

    /**
     * Flag indicating the UPDATE_DATE field from the bundle should be trusted.
     * If this flag is not set, the UPDATE_DATE will be set based on the current time
     */
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

    /** DEBUG only */
    private static final ArrayList<InstanceRefDebug> mInstances = new ArrayList<>();
    private static DatabaseHelper mDbHelper;
    private static SynchronizedDb mSyncedDb;
    private static Integer mDebugInstanceCount = 0;
    /** set of fields suitable for a select */
    private static final String SQL_FIELDS_FOR_BOOK =
            TBL_BOOKS.dotAs(DOM_ID) + "," +
                    TBL_BOOKS.dotAs(DOM_TITLE) + ", " +
                    // Find FIRST series ID.
                    "(SELECT " + DOM_SERIES_ID + " FROM " + TBL_BOOK_SERIES.ref() +
                    " WHERE " + TBL_BOOK_SERIES.dot(DOM_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_ID) +
                    " ORDER BY " + DOM_BOOK_SERIES_POSITION + " ASC  LIMIT 1) AS " + DOM_SERIES_ID + "," +
                    // Find FIRST series NUM.
                    "(SELECT " + DOM_BOOK_SERIES_NUM + " FROM " + TBL_BOOK_SERIES.ref() +
                    " WHERE " + TBL_BOOK_SERIES.dot(DOM_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_ID) +
                    " ORDER BY " + DOM_BOOK_SERIES_POSITION + " ASC  LIMIT 1) AS " + DOM_BOOK_SERIES_NUM + "," +
                    // Get the total series count
                    "(SELECT COUNT(*) FROM " + TBL_BOOK_SERIES.ref() +
                    " WHERE " + TBL_BOOK_SERIES.dot(DOM_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_ID) + ") AS _num_series," +
                    // Find the first AUTHOR ID
                    "(SELECT " + DOM_AUTHOR_ID + " FROM " + TBL_BOOK_AUTHOR.ref() +
                    " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_ID) +
                    " ORDER BY " + DOM_AUTHOR_POSITION + "," + TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID) + " LIMIT 1) AS " + DOM_AUTHOR_ID + "," +
                    // Get the total author count
                    "(SELECT COUNT(*) FROM " + TBL_BOOK_AUTHOR.ref() +
                    " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_ID) + ") AS _num_authors," +

                    TBL_BOOKS.dotAs(DOM_BOOK_ISBN) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_PUBLISHER) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_DATE_PUBLISHED) + "," +
                    TBL_BOOKS.dotAs(DOM_FIRST_PUBLICATION) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_RATING) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_READ) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_PAGES) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_NOTES) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_LIST_PRICE) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_ANTHOLOGY_MASK) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_LOCATION) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_READ_START) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_READ_END) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_FORMAT) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_SIGNED) + "," +
                    TBL_BOOKS.dotAs(DOM_DESCRIPTION) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_GENRE) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_LANGUAGE) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_DATE_ADDED) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_GOODREADS_BOOK_ID) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_GOODREADS_LAST_SYNC_DATE) + "," +
                    TBL_BOOKS.dotAs(DOM_LAST_UPDATE_DATE) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_UUID);


    /**
     * If no given name -> family name
     * otherwise -> family, given
     */
    private static final String SQL_FIELDS_AUTHOR_FORMATTED =
            " Case When " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "='' Then " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) +
                    " Else " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " || ',' || " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " End AS " + DOM_AUTHOR_FORMATTED;

    /**
     * If no given name -> family name
     * otherwise -> given family
     */
    private static final String SQL_FIELDS_AUTHOR_FORMATTED_GIVEN_FIRST =
            " Case When " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "='' Then " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) +
                    " Else " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " || ' ' || " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " End AS " + DOM_AUTHOR_FORMATTED_GIVEN_FIRST;

    /** set of 4 'family', 'given', 'family, given', 'given family' */
    private static String SQL_FIELDS_FOR_AUTHOR =
            TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME) + "," + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES) + "," + SQL_FIELDS_AUTHOR_FORMATTED + ", " + SQL_FIELDS_AUTHOR_FORMATTED_GIVEN_FIRST;


    private final Context mContext;

    /**
     * a cache for SQL strings, this 'should' make it more efficient, as strings will be
     * build/concatenated only once.
     *
     * But we should just make the sql all final static String's
     */
    private final Map<String, String> mSql = new HashMap<>();

    //region Statements
    /** Flag indicating {@link #close()} has been called */
    private boolean mCloseWasCalled = false;
    /** a cache for statements, where they are pre-compiled */
    private SqlStatementManager mStatements;
    /** {@link #getAnthologyTitleId } */
    private SynchronizedStatement mGetAnthologyTitleIdStmt = null;
    /** {@link #getBookAnthologyTitleId}  } */
    private SynchronizedStatement mgetBookAnthologyTitleIdStmt = null;
    /** {@link #getBookAnthologyTitleHighestPositionByBookId}  } */
    private SynchronizedStatement mgetBookAnthologyTitleHighestPositionByBookIdStmt = null;
    /** {@link #getAuthorIdByName } */
    private SynchronizedStatement mGetAuthorIdStmt = null;
    /** {@link #purgeAuthors} */
    private SynchronizedStatement mPurgeBookAuthorsStmt = null;
    /** {@link #purgeAuthors} */
    private SynchronizedStatement mPurgeAuthorsStmt = null;
    /** {@link #countAuthorBooks} */
    private SynchronizedStatement mGetAuthorBookCountQuery = null;
    /** {@link #countAuthorAnthologies} */
    private SynchronizedStatement mGetAuthorAnthologyCountQuery = null;
    /** {@link #getBookUuid} */
    private SynchronizedStatement mGetBookUuidQuery = null;
    /** {@link #getBookIdFromUuid} */
    private SynchronizedStatement mGetBookIdFromUuidStmt = null;
    /** {@link #getBookUpdateDate} */
    private SynchronizedStatement mGetBookUpdateDateQuery = null;
    /** {@link #getBookTitle} */
    private SynchronizedStatement mGetBookTitleQuery = null;
    /** {@link #countBooks} */
    private SynchronizedStatement mCountBooksQuery = null;
    /** {@link #getIdFromIsbn} */
    private SynchronizedStatement mGetIdFromIsbn1Stmt = null;
    /** {@link #getIdFromIsbn} */
    private SynchronizedStatement mGetIdFromIsbn2Stmt = null;
    /** {@link #bookExists} */
    private SynchronizedStatement mCheckBookExistsStmt = null;
    /** {@link #insertBookSeries} */
    private SynchronizedStatement mDeleteBookSeriesStmt = null;
    /** {@link #setBooksDirtyBySeries} */
    private SynchronizedStatement mSetBooksDirtyBySeriesStmt = null;
    /** {@link #setBooksDirtyByAuthor} */
    private SynchronizedStatement mSetBooksDirtyByAuthorStmt = null;
    /** {@link #setBooksDirtyByAuthor} */
    private SynchronizedStatement mSetBooksDirtyByAuthorStmt2 = null;
    /** {@link #setBooksDirtyByBookshelf} */
    private SynchronizedStatement mSetBooksDirtyByBookshelfStmt = null;
    /** {@link #insertBookSeries} */
    private SynchronizedStatement mAddBookSeriesStmt = null;
    /** {@link #insertBookAuthors} */
    private SynchronizedStatement mDeleteBookAuthorsStmt = null;
    /** {@link #insertBookAuthors} */
    private SynchronizedStatement mAddBookAuthorsStmt = null;
    /** {@link #insertBookBookshelf} */
    private SynchronizedStatement mDeleteBookBookshelfStmt = null;
    /** {@link #insertBookBookshelf} */
    private SynchronizedStatement mInsertBookBookshelfStmt = null;
    /** {@link #getBookshelfName} */
    private SynchronizedStatement mGetBookshelfNameStmt = null;
    /** {@link #getBookshelfId} */
    private SynchronizedStatement mGetBookshelfIdStmt = null;
    /** {@link #getBookshelfIdByName} */
    private SynchronizedStatement mGetBookshelfIdByNameStmt = null;
    /** {@link #insertBooklistStyle} */
    private SynchronizedStatement mInsertBooklistStyleStmt = null;
    /** {@link #updateBooklistStyle} */
    private SynchronizedStatement mUpdateBooklistStyleStmt = null;
    /** {@link #deleteBooklistStyle} */
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
    /** {@link #insertFts} */
    private SynchronizedStatement mInsertFtsStmt = null;
    /** {@link #updateFts} */
    private SynchronizedStatement mUpdateFtsStmt = null;
    /** {@link #deleteFts} */
    private SynchronizedStatement mDeleteFtsStmt = null;
    //endregion

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


    @NonNull
    public static String encodeString(@NonNull final String value) {
        return value.replace("'", "''");
    }


    /**
     * DEBUG only
     */
    @SuppressWarnings("unused")
    private static void debugAddInstance(@NonNull final CatalogueDBAdapter db) {
        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            mInstances.add(new InstanceRefDebug(db));
        }
    }
    /**
     * DEBUG only
     */
    @SuppressWarnings("unused")
    private static void debugRemoveInstance(@NonNull final CatalogueDBAdapter db) {
        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            List<InstanceRefDebug> toDelete = new ArrayList<>();
            for (InstanceRefDebug ref : mInstances) {
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
            for (InstanceRefDebug ref : toDelete) {
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

    /**
     * DEBUG only
     */
    @SuppressWarnings("unused")
    public static void debugDumpInstances() {
        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            for (InstanceRefDebug ref : mInstances) {
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

    //region Anthology

    /**
     * Insert or Update the anthology title in the database {@link DatabaseDefinitions#TBL_ANTHOLOGY}
     * The id for an update, will always be fetched from the database.
     *
     * @param authorId        The author
     * @param title           The title of the anthology story
     * @param publicationDate The original publication year
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred, or 1 for a successful update
     */
    private long insertOrUpdateAnthologyTitle(final long authorId,
                                              @NonNull final String title,
                                              @NonNull final String publicationDate) {

        if (title.isEmpty()) {
            return -1L;
        }
        ContentValues values = new ContentValues();
        values.put(DOM_AUTHOR_ID.name, authorId);
        values.put(DOM_TITLE.name, title);
        values.put(DOM_FIRST_PUBLICATION.name, publicationDate);

        long result = getAnthologyTitleId(authorId, title);
        if (result == 0) {
            result = mSyncedDb.insert(TBL_ANTHOLOGY.getName(), null, values);
        } else {
            result = mSyncedDb.update(TBL_ANTHOLOGY.getName(), values, DOM_ID + "=" + result, null);
        }
        return result;
    }

    /**
     * Create an anthology title for a book.
     * It will always be added to the END OF THE LIST (using the position column)
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred, or 1 for a successful update
     */
    @SuppressWarnings("UnusedReturnValue")
    private long insertOrUpdateBookAnthology(final long anthologyId, final long bookId,
                                             @SuppressWarnings("SameParameterValue") final boolean dirtyBookIfNecessary) {

        ContentValues values = new ContentValues();
        values.put(DOM_ANTHOLOGY_ID.name, anthologyId);
        values.put(DOM_BOOK_ID.name, bookId);
        values.put(DOM_BOOK_ANTHOLOGY_POSITION.name, getBookAnthologyTitleHighestPositionByBookId(bookId) + 1);

        long result = getBookAnthologyTitleId(bookId, anthologyId);
        if (result == 0) {
            result = mSyncedDb.insert(TBL_BOOK_ANTHOLOGY.getName(), null, values);
        } else {
            result = mSyncedDb.update(TBL_BOOK_ANTHOLOGY.getName(), values, DOM_ID + "=" + result, null);
        }

        if (result > 0 && dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        return result;
    }

    /**
     * Delete ALL the anthology records for a given book {@link DatabaseDefinitions#TBL_BOOK_ANTHOLOGY}, if any
     * But does NOT delete the Anthology Title itself {@link DatabaseDefinitions#TBL_ANTHOLOGY}
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    private int deleteAnthologyTitlesByBookId(final long bookId,
                                              @SuppressWarnings("SameParameterValue") final boolean dirtyBookIfNecessary) {

        int rowsAffected = mSyncedDb.delete(TBL_BOOK_ANTHOLOGY.getName(), DOM_BOOK_ID + "=" + bookId, null);
        if (rowsAffected > 0) {
            purgeAuthors();
            if (dirtyBookIfNecessary) {
                setBookDirty(bookId);
            }
        }
        return rowsAffected;
    }

    /**
     * Return the AnthologyTitle ID for a given author/title (note that publication year is NOT used)
     *
     * @param authorId id of author
     * @param title    title
     *
     * @return the id of the AnthologyTitle entry, or 0 if not found
     */
    public long getAnthologyTitleId(final long authorId, @NonNull final String title) {
        if (mGetAnthologyTitleIdStmt == null) {
            String sql = "SELECT Coalesce( Min(" + DOM_ID + "),0) FROM " + TBL_ANTHOLOGY +
                    " WHERE " + DOM_AUTHOR_ID + "=? AND " + DOM_TITLE + "=?" +
                    COLLATION;
            mGetAnthologyTitleIdStmt = mStatements.add("mGetAnthologyTitleIdStmt", sql);
        }
        mGetAnthologyTitleIdStmt.bindLong(1, authorId);
        mGetAnthologyTitleIdStmt.bindString(2, title);
        return mGetAnthologyTitleIdStmt.simpleQueryForLongOrZero();
    }

    /**
     * @return the id of the BookAnthologyTitle entry, or 0 if not found
     */
    private long getBookAnthologyTitleId(final long bookId, final long anthologyId) {
        if (mgetBookAnthologyTitleIdStmt == null) {
            String sql = "SELECT " + DOM_ID + " FROM " + TBL_BOOK_ANTHOLOGY +
                    " WHERE " + DOM_BOOK_ID + " = ? AND " + DOM_ANTHOLOGY_ID + " = ? ";
            mgetBookAnthologyTitleIdStmt = mStatements.add("mgetBookAnthologyTitleIdStmt", sql);
        }
        mgetBookAnthologyTitleIdStmt.bindLong(1, bookId);
        mgetBookAnthologyTitleIdStmt.bindLong(2, anthologyId);
        return mgetBookAnthologyTitleIdStmt.simpleQueryForLongOrZero();
    }

    /**
     * Return the largest anthology position (usually used for adding new titles)
     *
     * @param bookId id of book to retrieve
     *
     * @return An integer of the highest position. 0 if it is not an anthology, or not used yet
     *
     * V83
     */
    private long getBookAnthologyTitleHighestPositionByBookId(final long bookId) {
        if (mgetBookAnthologyTitleHighestPositionByBookIdStmt == null) {
            String sql = "SELECT max(" + DOM_BOOK_ANTHOLOGY_POSITION + ") FROM " + TBL_BOOK_ANTHOLOGY +
                    " WHERE " + DOM_BOOK_ID + "=?";
            mgetBookAnthologyTitleHighestPositionByBookIdStmt = mStatements.add("mgetBookAnthologyTitleHighestPositionByBookIdStmt", sql);
        }

        mgetBookAnthologyTitleHighestPositionByBookIdStmt.bindLong(1, bookId);
        return mgetBookAnthologyTitleHighestPositionByBookIdStmt.simpleQueryForLongOrZero();
    }


    /**
     * Return all the anthology titles and authors recorded for book
     *
     * @param bookId id of book to retrieve
     *
     * @return Cursor containing all records, if any
     *
     * V83
     */
    @NonNull
    public Cursor fetchAnthologyTitlesByBookId(final long bookId) {
        String sql = mSql.get("fetchAnthologyTitlesByBookId");
        if (sql == null) {
            sql = "SELECT " +
                    TBL_ANTHOLOGY.dotAs(DOM_ID) + "," +
                    TBL_ANTHOLOGY.dotAs(DOM_TITLE) + "," +
                    TBL_ANTHOLOGY.dotAs(DOM_AUTHOR_ID) + "," +
                    TBL_ANTHOLOGY.dotAs(DOM_FIRST_PUBLICATION) + "," +

                    TBL_BOOK_ANTHOLOGY.dotAs(DOM_BOOK_ID) + "," +
                    TBL_BOOK_ANTHOLOGY.dotAs(DOM_BOOK_ANTHOLOGY_POSITION) + "," +

                    TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME) + "," +
                    TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES) + "," +
                    TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " || ', ' || " + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES, DOM_AUTHOR_NAME) +

                    " FROM " + TBL_ANTHOLOGY.ref() + TBL_ANTHOLOGY.join(TBL_BOOK_ANTHOLOGY) + TBL_ANTHOLOGY.join(TBL_AUTHORS) +

                    " WHERE " + TBL_BOOK_ANTHOLOGY.dot(DOM_BOOK_ID) + "=?" +

                    " ORDER BY " + TBL_BOOK_ANTHOLOGY.dot(DOM_BOOK_ANTHOLOGY_POSITION);
            mSql.put("fetchAnthologyTitlesByBookId", sql);
        }
        return mSyncedDb.rawQuery(sql, new String[]{Long.toString(bookId)});
    }
    //endregion

    //region Author

    /**
     * Create a new author in the database
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    private long insertAuthor(@NonNull final Author author) {
        ContentValues values = new ContentValues();
        values.put(DOM_AUTHOR_FAMILY_NAME.name, author.familyName);
        values.put(DOM_AUTHOR_GIVEN_NAMES.name, author.givenNames);
        return mSyncedDb.insert(TBL_AUTHORS.getName(), null, values);
    }

    /**
     * @return the number of rows affected
     */
    private int updateAuthor(@NonNull final Author author) {
        ContentValues values = new ContentValues();
        values.put(DOM_AUTHOR_FAMILY_NAME.name, author.familyName);
        values.put(DOM_AUTHOR_GIVEN_NAMES.name, author.givenNames);
        return mSyncedDb.update(TBL_AUTHORS.getName(), values, DOM_ID + " = " + author.id, null);
    }

    /**
     * Add or update the passed author, depending whether author.id == 0.
     *
     * @return true when update/create was done + the updated Author
     */
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
        String sql = mSql.get("getAuthor");
        if (sql == null) {
            sql = "SELECT " + DOM_AUTHOR_FAMILY_NAME + "," + DOM_AUTHOR_GIVEN_NAMES +
                    " FROM " + TBL_AUTHORS +
                    " WHERE " + DOM_ID + "=?";
            mSql.put("getAuthor", sql);
        }

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{Long.toString(id)})) {
            return (cursor.moveToFirst() ? new Author(id, cursor.getString(0), cursor.getString(1)) : null);
        }
    }

    /**
     * @return author id, or 0 (e.g. 'new') when not found
     */
    public long getAuthorIdByName(@NonNull final String familyName, @NonNull final String givenNames) {
        if (mGetAuthorIdStmt == null) {
            mGetAuthorIdStmt = mStatements.add("mGetAuthorIdStmt",
                    "SELECT " + DOM_ID + " FROM " + TBL_AUTHORS +
                            " WHERE Upper(" + DOM_AUTHOR_FAMILY_NAME + ") = Upper(?) " + COLLATION +
                            " AND Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") = Upper(?)" + COLLATION);
        }

        mGetAuthorIdStmt.bindString(1, familyName);
        mGetAuthorIdStmt.bindString(2, givenNames);
        return mGetAuthorIdStmt.simpleQueryForLongOrZero();
    }

    /**
     * @param author to find
     *
     * @return the row ID of the 'found' row, the newly inserted row, or -1 if an error occurred
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
     * @throws DBExceptions.UpdateException on failure
     */
    public void globalReplaceAuthor(@NonNull final Author from, @NonNull final Author to) {
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
            throw new DBExceptions.NotFoundException("Old Author is not defined");
        }

        if (from.id == to.id) {
            return;
        }

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            setBooksDirtyByAuthor(from.id);

            // First handle anthologies; they have a single author and are easy
            mSyncedDb.execSQL("UPDATE " + TBL_ANTHOLOGY + " SET " + DOM_AUTHOR_ID + " = " + to.id
                    + " WHERE " + DOM_AUTHOR_ID + " = " + from.id);

            globalReplacePositionedBookItem(TBL_BOOK_AUTHOR, DOM_AUTHOR_ID.name, DOM_AUTHOR_POSITION.name, from.id, to.id);

            mSyncedDb.setTransactionSuccessful();
        } catch (Exception e) {
            Logger.logError(e);
            throw new DBExceptions.UpdateException(e);
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
        try (Cursor cursor = fetchAuthors()) {
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
     * @return Cursor over all notes
     */
    @NonNull
    private Cursor fetchAuthors() {
        String sql = mSql.get("fetchAuthors");
        if (sql == null) {
            sql = "SELECT DISTINCT " +
                    TBL_AUTHORS.dot(DOM_ID) + " AS " + DOM_ID.name + "," + SQL_FIELDS_FOR_AUTHOR +
                    " FROM " + TBL_AUTHORS.ref() + TBL_AUTHORS.join(TBL_BOOK_AUTHOR) +
                    " ORDER BY Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION + ", Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION;
            mSql.put("fetchAuthors", sql);
        }
        return mSyncedDb.rawQuery(sql, new String[]{});
    }

    /**
     * When we have no more books or AnthologyTitle's of a given Author, purge him.
     * {@link DatabaseDefinitions#TBL_BOOK_AUTHOR}
     * {@link DatabaseDefinitions#TBL_AUTHORS}
     */
    public void purgeAuthors() {
        if (mPurgeBookAuthorsStmt == null) {
            mPurgeBookAuthorsStmt = mStatements.add("mPurgeBookAuthorsStmt",
                    "DELETE FROM " + TBL_BOOK_AUTHOR +
                            " WHERE " + DOM_BOOK_ID + " NOT IN" +
                            " (SELECT DISTINCT " + DOM_ID + " FROM " + TBL_BOOKS + ") ");
        }
        try {
            mPurgeBookAuthorsStmt.execute();
        } catch (Exception e) {
            Logger.logError(e, "Failed to purge " + TBL_BOOK_AUTHOR);
        }

        if (mPurgeAuthorsStmt == null) {
            mPurgeAuthorsStmt = mStatements.add("mPurgeAuthorsStmt",
                    "DELETE FROM " + TBL_AUTHORS +
                            " WHERE " + DOM_ID + " NOT IN" +
                            " (SELECT DISTINCT " + DOM_AUTHOR_ID + " FROM " + TBL_BOOK_AUTHOR + ")" +
                            " AND " + DOM_ID + " NOT IN" +
                            " (SELECT DISTINCT " + DOM_AUTHOR_ID + " FROM " + TBL_ANTHOLOGY + ")");
        }
        try {
            mPurgeAuthorsStmt.execute();
        } catch (Exception e) {
            Logger.logError(e, "Failed to purge " + TBL_AUTHORS);
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
        String sql = mSql.get("fetchAuthorsByBookId");
        if (sql == null) {
            sql = "SELECT DISTINCT " +
                    TBL_AUTHORS.dotAs(DOM_ID) + "," +
                    TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME) + "," +
                    TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES) + "," +
                    SQL_FIELDS_AUTHOR_FORMATTED + "," +
                    TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_POSITION) +
                    " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS) +
                    " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_ID) + "=?" +
                    " ORDER BY " +
                    TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_POSITION) + " ASC," +
                    " Upper(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION + " ASC," +
                    " Upper(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION + " ASC";
            mSql.put("fetchAuthorsByBookId", sql);
        }
        return mSyncedDb.rawQuery(sql, new String[]{Long.toString(bookId)});
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
                    "SELECT COUNT(" + DOM_BOOK_ID + ") FROM " + TBL_BOOK_AUTHOR + " WHERE " + DOM_AUTHOR_ID + "=?");
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
                    "SELECT COUNT(" + DOM_ID + ") FROM " + TBL_ANTHOLOGY + " WHERE " + DOM_AUTHOR_ID + "=?");
        }
        // Be cautious
        synchronized (mGetAuthorAnthologyCountQuery) {
            mGetAuthorAnthologyCountQuery.bindLong(1, author.id);
            return mGetAuthorAnthologyCountQuery.count();
        }

    }
    //endregion

    //region Book

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

        // Handle ANTHOLOGY_MASK
        {
            ArrayList<AnthologyTitle> anthologyTitles = bookData.getAnthologyTitles();
            if (anthologyTitles.size() > 0) {
                // definitively an anthology, overrule whatever the KEY_ANTHOLOGY_MASK was.
                bookData.putInt(UniqueId.KEY_ANTHOLOGY_MASK, DOM_ANTHOLOGY_IS_AN_ANTHOLOGY);

                // check if its all the same author or not
                if (anthologyTitles.size() > 1) {
                    boolean sameAuthor;
                    Author author = anthologyTitles.get(0).getAuthor();
                    for (AnthologyTitle t : anthologyTitles) { // yes, we check 0 twice.. oh well.
                        sameAuthor = author.equals(t.getAuthor());
                        if (!sameAuthor) {
                            bookData.putInt(UniqueId.KEY_ANTHOLOGY_MASK, DOM_ANTHOLOGY_IS_AN_ANTHOLOGY | DOM_ANTHOLOGY_WITH_MULTIPLE_AUTHORS);
                            break;
                        }
                    }
                }
            }
            // should never be the case, but one developer made this mistake before (me!)
            bookData.remove(UniqueId.BKEY_ANTHOLOGY_DETAILS);
        }

        // Remove blank/null fields that have default values defined in the database
        // or which should never be blank.
        for (String name : new String[]{
                UniqueId.KEY_BOOK_UUID,
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
     * @param bookId of the book
     *
     * @return the book uuid (*every* book has a uuid in the database)
     *
     * @throws SQLiteDoneException if zero rows found
     */
    @NonNull
    public String getBookUuid(final long bookId) throws SQLiteDoneException {
        if (mGetBookUuidQuery == null) {
            mGetBookUuidQuery = mStatements.add("mGetBookUuidQuery",
                    "SELECT " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS + " WHERE " + DOM_ID + "=?");
        }
        // Be cautious; other threads may call this and set parameters.
        synchronized (mGetBookUuidQuery) {
            mGetBookUuidQuery.bindLong(1, bookId);
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
                    "SELECT " + DOM_ID + " FROM " + TBL_BOOKS + " WHERE " + DOM_BOOK_UUID + " = ?");
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
                    "SELECT " + DOM_LAST_UPDATE_DATE + " FROM " + TBL_BOOKS + " WHERE " + DOM_ID + "=?");
        }
        // Be cautious
        synchronized (mGetBookUpdateDateQuery) {
            mGetBookUpdateDateQuery.bindLong(1, id);
            return mGetBookUpdateDateQuery.simpleQueryForStringOrNull();
        }
    }

    /**
     * Utility routine to return the book title based on the id.
     *
     * @return the title, or null when not found
     */
    @Nullable
    public String getBookTitle(final long id) {
        if (mGetBookTitleQuery == null) {
            mGetBookTitleQuery = mStatements.add("mGetBookTitleQuery",
                    "SELECT " + DOM_TITLE + " FROM " + TBL_BOOKS + " WHERE " + DOM_ID + "=?");
        }
        // Be cautious
        synchronized (mGetBookTitleQuery) {
            mGetBookTitleQuery.bindLong(1, id);
            return mGetBookTitleQuery.simpleQueryForStringOrNull();
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
        int rowsAffected = 0;
        SyncLock l = mSyncedDb.beginTransaction(true);
        try {

            rowsAffected = mSyncedDb.delete(TBL_BOOKS.getName(), DOM_ID + "=" + bookId, null);
            if (rowsAffected > 0) {
                purgeAuthors();
                deleteFts(bookId);
                deleteThumbnail(uuid);
            }
            mSyncedDb.setTransactionSuccessful();
        } catch (Exception e) {
            Logger.logError(e, "Failed to delete book");
        } finally {
            mSyncedDb.endTransaction(l);
        }

        return rowsAffected;
    }

    private void deleteThumbnail(final String uuid) {
        if (uuid != null) {
            try {
                //TODO: understand the logic when uuid.isEmpty() -> see the second step with the CoversDbHelper
                File coverFile = StorageUtils.getCoverFile(uuid);
                while (coverFile.exists()) {
                    StorageUtils.deleteFile(coverFile);
                    coverFile = StorageUtils.getCoverFile(uuid);
                }
            } catch (Exception e) {
                Logger.logError(e, "Failed to delete cover thumbnail");
            }

            if (!uuid.isEmpty()) {
                try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(mContext)) {
                    coversDbHelper.eraseCachedBookCover(uuid);
                }
            }
        }
    }

    /**
     * Create a new book using the details provided.
     *
     * @param bookData A ContentValues collection with the columns to be updated. May contain extra data.
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insertBook(@NonNull final BookData bookData) {
        return insertBookWithId(0, bookData);
    }

    /**
     * Create a new book using the details provided.
     *
     * Transaction: participate, or run in new.
     *
     * @param bookId   The ID of the book to insert
     *                 zero: a new book
     *                 non-zero: will overwrite the normal autoIncrement, normally only an Import should use this
     * @param bookData A ContentValues collection with the columns to be updated. May contain extra data.
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insertBookWithId(final long bookId, @NonNull final BookData bookData) {

        SyncLock syncLock = null;
        if (!mSyncedDb.inTransaction()) {
            syncLock = mSyncedDb.beginTransaction(true);
        }

        try {
            // Cleanup fields (author, series, title and remove blank fields for which we have defaults)
            preprocessOutput(bookId == 0, bookData);

            /* Set defaults if not present in bookData
             *
             * TODO We may want to provide default values for these fields:
             * KEY_BOOK_RATING, KEY_BOOK_READ, KEY_NOTES, KEY_BOOK_LOCATION, KEY_BOOK_READ_START, KEY_BOOK_READ_END, KEY_BOOK_SIGNED
             */
            if (!bookData.containsKey(UniqueId.KEY_BOOK_DATE_ADDED)) {
                bookData.putString(UniqueId.KEY_BOOK_DATE_ADDED, DateUtils.toSqlDateTime(new Date()));
            }

            // Make sure we have an author
            List<Author> authors = bookData.getAuthors();
            if (authors.size() == 0) {
                throw new IllegalArgumentException();
            }

            ContentValues values = filterValues(TBL_BOOKS.getName(), bookData);
            if (bookId > 0) {
                values.put(DOM_ID.name, bookId);
            }

            if (!values.containsKey(DOM_LAST_UPDATE_DATE.name)) {
                values.put(DOM_LAST_UPDATE_DATE.name, DateUtils.toSqlDateTime(new Date()));
            }

            long newBookId = mSyncedDb.insert(TBL_BOOKS.getName(), null, values);
            if (newBookId > 0) {
                insertBookDependents(newBookId, bookData);
                insertFts(newBookId);
                // it's an insert, success only if we inserted.
                if (syncLock != null) {
                    mSyncedDb.setTransactionSuccessful();
                }
            }
            return newBookId;
        } catch (Exception e) {
            Logger.logError(e, "Error creating book from " + bookData);
            return -1L;
        } finally {
            if (syncLock != null) {
                mSyncedDb.endTransaction(syncLock);
            }
        }
    }

    /**
     * Transaction: participate, or run in new.
     *
     * @param bookId   of the book in the database
     * @param bookData A collection with the columns to be updated. May contain extra data.
     * @param flags    See {@link #BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT} an others for flag definitions
     *
     * @return the number of rows affected
     *
     * @throws DBExceptions.UpdateException on failure
     */
    public int updateBook(final long bookId, @NonNull final BookData bookData, final int flags) {

        SyncLock syncLock = null;
        if (!mSyncedDb.inTransaction()) {
            syncLock = mSyncedDb.beginTransaction(true);
        }

        try {
            // Cleanup fields (author, series, title, 'sameAuthor' if anthology, and remove blank fields for which we have defaults)
            preprocessOutput(bookId == 0, bookData);

            ContentValues values = filterValues(TBL_BOOKS.getName(), bookData);

            // Disallow UUID updates
            if (values.containsKey(DOM_BOOK_UUID.name)) {
                values.remove(DOM_BOOK_UUID.name);
            }

            // set the DOM_LAST_UPDATE_DATE to 'now' if we're allowed, or if it's not present already.
            if ((flags & BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT) == 0
                    || !values.containsKey(DOM_LAST_UPDATE_DATE.name)) {
                values.put(DOM_LAST_UPDATE_DATE.name, DateUtils.toSqlDateTime(new Date()));
            }

            // go !
            int rowsAffected = mSyncedDb.update(TBL_BOOKS.getName(), values, DOM_ID + "=" + bookId, null);
            insertBookDependents(bookId, bookData);

            // Only really skip the purge if a batch update of multiple books is being done.
            if ((flags & BOOK_UPDATE_SKIP_PURGE_REFERENCES) == 0) {
                purgeAuthors();
                purgeSeries();
            }

            updateFts(bookId);

            if (syncLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
            return rowsAffected;
        } catch (Exception e) {
            Logger.logError(e);
            throw new DBExceptions.UpdateException("Error updating book from " + bookData + ": " + e.getMessage(), e);
        } finally {
            if (syncLock != null) {
                mSyncedDb.endTransaction(syncLock);
            }
        }
    }

    /**
     * shared between book insert & update.
     * All of these will first delete the entries in the Book-[tableX] table, and then insert the all rows again
     */
    private void insertBookDependents(final long bookId, final @NonNull BookData bookData) {
        String bookshelf = bookData.getBookshelfList();
        if (bookshelf != null && !bookshelf.isEmpty()) {
            insertBookBookshelf(bookId, ArrayUtils.decodeList(Bookshelf.SEPARATOR, bookshelf), false);
        }

        if (bookData.containsKey(UniqueId.BKEY_AUTHOR_ARRAY)) {
            insertBookAuthors(bookId, bookData.getAuthors(), false);
        }

        if (bookData.containsKey(UniqueId.BKEY_SERIES_ARRAY)) {
            insertBookSeries(bookId, bookData.getSeries(), false);
        }

        if (bookData.containsKey(UniqueId.BKEY_ANTHOLOGY_TITLES_ARRAY)) {
            insertOrUpdateBookAnthologyAndAnthologyTitles(bookId, bookData.getAnthologyTitles(), false);
        }

        if (bookData.containsKey(UniqueId.KEY_LOANED_TO) && !"".equals(bookData.get(UniqueId.KEY_LOANED_TO))) {
            deleteLoan(bookId, false);
            insertLoan(bookData, false); // don't care about insert failures really
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
                    "SELECT COUNT(*) FROM " + TBL_BOOKS.ref());
        }
        return (int) mCountBooksQuery.count();
    }

    @NonNull
    public Cursor fetchBookUuidList() {
        return mSyncedDb.rawQuery("SELECT " + DOM_BOOK_UUID + " AS " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS.ref());
    }

    /**
     * @param isbn The isbn to search by
     *
     * @return book id, or 0 if not found
     */
    public long getIdFromIsbn(@NonNull final String isbn, /*FIXME */ final boolean checkAltIsbn) {
        SynchronizedStatement stmt;
        if (checkAltIsbn && IsbnUtils.isValid(isbn)) {
            if (mGetIdFromIsbn2Stmt == null) {
                mGetIdFromIsbn2Stmt = mStatements.add("mGetIdFromIsbn2Stmt",
                        "SELECT Coalesce(max(" + DOM_ID + "), 0) FROM " + TBL_BOOKS +
                                " WHERE Upper(" + DOM_BOOK_ISBN + ") in (Upper(?), Upper(?))");
            }
            stmt = mGetIdFromIsbn2Stmt;
            stmt.bindString(2, IsbnUtils.isbn2isbn(isbn));
        } else {
            if (mGetIdFromIsbn1Stmt == null) {
                mGetIdFromIsbn1Stmt = mStatements.add("mGetIdFromIsbn1Stmt",
                        "SELECT Coalesce(max(" + DOM_ID + "), 0) FROM " + TBL_BOOKS +
                                " WHERE Upper(" + DOM_BOOK_ISBN + ") = Upper(?)");
            }
            stmt = mGetIdFromIsbn1Stmt;
        }
        stmt.bindString(1, isbn);
        return stmt.simpleQueryForLongOrZero();
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
                    "SELECT " + DOM_ID + " FROM " + TBL_BOOKS + " WHERE " + DOM_ID + " = ?");
        }
        mCheckBookExistsStmt.bindLong(1, bookId);
        return (0 != mCheckBookExistsStmt.simpleQueryForLongOrZero());
    }

    /**
     * Set a book as in need of backup if any ancillary data has changed.
     */
    private void setBookDirty(final long bookId) {
        mSyncedDb.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                " WHERE " + TBL_BOOKS.dot(DOM_ID) + " = " + bookId);
    }

    /**
     * Set all books referencing a given author as dirty.
     */
    private void setBooksDirtyByAuthor(final long authorId) {
        // Mark all related books based on anthology author as dirty
        if (mSetBooksDirtyByAuthorStmt == null) {
            mSetBooksDirtyByAuthorStmt = mStatements.add("mSetBooksDirtyByAuthorStmt",
                    "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                            " WHERE" +
                            " Exists(SELECT * FROM " + TBL_ANTHOLOGY.ref() +
                            " WHERE " + TBL_ANTHOLOGY.dot(DOM_AUTHOR_ID) + " =?" +
                            " AND " + TBL_ANTHOLOGY.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS.dot(DOM_ID) + ")");
        }
        mSetBooksDirtyByAuthorStmt.bindLong(1, authorId);
        mSetBooksDirtyByAuthorStmt.execute();



        // Mark all related books based on series as dirty
        if (mSetBooksDirtyByAuthorStmt2 == null) {
            mSetBooksDirtyByAuthorStmt2 = mStatements.add("mSetBooksDirtyByAuthorStmt2",
                    "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                            " WHERE" +
                            " Exists(SELECT * FROM " + TBL_BOOK_AUTHOR.ref() +
                            " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_ID) + "=?" +
                            " AND " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS.dot(DOM_ID) + ")");
        }
        mSetBooksDirtyByAuthorStmt2.bindLong(1, authorId);
        mSetBooksDirtyByAuthorStmt2.execute();
    }

    /**
     * Set all books referencing a given series as dirty.
     */
    private void setBooksDirtyBySeries(final long seriesId) {
        if (mSetBooksDirtyBySeriesStmt == null) {
            mSetBooksDirtyBySeriesStmt = mStatements.add("mSetBooksDirtyBySeriesStmt",
                    "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                    " WHERE" +
                    " Exists(SELECT * FROM " + TBL_BOOK_SERIES.ref() + " WHERE " + TBL_BOOK_SERIES.dot(DOM_SERIES_ID) + " =?" +
                    " AND " + TBL_BOOK_SERIES.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS.dot(DOM_ID) + ")");
        }
        mSetBooksDirtyBySeriesStmt.bindLong(1, seriesId);
        mSetBooksDirtyBySeriesStmt.execute();
    }

    /**
     * Set all books referencing a given bookshelf as dirty.
     */
    private void setBooksDirtyByBookshelf(final long bookshelfId) {
        if (mSetBooksDirtyByBookshelfStmt == null) {
            mSetBooksDirtyByBookshelfStmt = mStatements.add("mSetBooksDirtyByBookshelfStmt",
                    "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                            " WHERE" +
                            " Exists(SELECT * FROM " + TBL_BOOK_BOOKSHELF.ref() + " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_BOOKSHELF_ID) + "=?" +
                            " AND " + TBL_BOOK_BOOKSHELF.dot(DOM_BOOK_ID) + " = " + TBL_BOOKS.dot(DOM_ID) + ")");
        }
        mSetBooksDirtyByBookshelfStmt.bindLong(1, bookshelfId);
        mSetBooksDirtyByBookshelfStmt.execute();
    }

    private void insertBookSeries(long bookId,
                                  @NonNull final List<Series> series,
                                  @SuppressWarnings("SameParameterValue") boolean dirtyBookIfNecessary) {
        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        if (mDeleteBookSeriesStmt == null) {
            mDeleteBookSeriesStmt = mStatements.add("mDeleteBookSeriesStmt",
                    "DELETE FROM " + TBL_BOOK_SERIES + " WHERE " + DOM_BOOK_ID + " = ?");
        }
        // Delete the current series
        mDeleteBookSeriesStmt.bindLong(1, bookId);
        mDeleteBookSeriesStmt.execute();

        if (series.size() == 0) {
            return;
        }

        if (mAddBookSeriesStmt == null) {
            mAddBookSeriesStmt = mStatements.add("mAddBookSeriesStmt",
                    "INSERT INTO " + TBL_BOOK_SERIES
                            + "(" + DOM_BOOK_ID + "," + DOM_SERIES_ID + "," + DOM_BOOK_SERIES_NUM + "," + DOM_BOOK_SERIES_POSITION + ")"
                            + " VALUES(?,?,?,?)");
        }

        /*

        FIXME: is this still true ?
             mAddBookSeriesStmt.bindLong(1, bookId);
        can in theory be outside of the for loop. This was once good enough, but Android 4 (at least)
        causes the bindings to clean when executed. So now we do it each time in loop.

            2018-10-05: call stack show that in theory clearBindings() is only called when ALL references are gone.

            https://android.googlesource.com/platform/frameworks/base/+/android-5.0.0_r7/core/java/android/database/sqlite/SQLiteStatement.java#77
                executeInsert()
            https://android.googlesource.com/platform/frameworks/base/+/android-5.0.0_r7/core/java/android/database/sqlite/SQLiteClosable.java#65
                releaseReference()
            https://android.googlesource.com/platform/frameworks/base/+/android-5.0.0_r7/core/java/android/database/sqlite/SQLiteProgram.java#207
                onAllReferencesReleased()
            https://android.googlesource.com/platform/frameworks/base/+/android-5.0.0_r7/core/java/android/database/sqlite/SQLiteProgram.java#180
                clearBindings() {

         */

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

                String uniqueId = seriesId + "(" + entry.number.toUpperCase() + ")";
                if (!idHash.containsKey(uniqueId)) {
                    idHash.put(uniqueId, true);
                    pos++;
                    mAddBookSeriesStmt.bindLong(1, bookId);
                    mAddBookSeriesStmt.bindLong(2, seriesId);
                    mAddBookSeriesStmt.bindString(3, entry.number);
                    mAddBookSeriesStmt.bindLong(4, pos);
                    mAddBookSeriesStmt.executeInsert();
                }
            } catch (Exception e) {
                Logger.logError(e);
                throw new DBExceptions.InsertException("Error adding series '" + entry.name +
                        "' {" + seriesId + "} to book " + bookId + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Insert a List of AnthologyTitles.
     *
     * @param bookId               the book
     * @param list                 the list
     * @param dirtyBookIfNecessary up to now, always set to 'false'
     */
    private void insertOrUpdateBookAnthologyAndAnthologyTitles(final long bookId,
                                                               @NonNull final List<AnthologyTitle> list,
                                                               @SuppressWarnings("SameParameterValue") boolean dirtyBookIfNecessary) {
        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        // remove all links between Book and AnthologyTitle's
        int rowsffected = deleteAnthologyTitlesByBookId(bookId, false);
        if (BuildConfig.DEBUG) {
            System.out.println("deleteAnthologyTitlesByBookId: " + rowsffected);
        }
        if (list.size() > 0) {
            for (AnthologyTitle anthologyTitle : list) {
                long authorId = getAuthorIdOrInsert(anthologyTitle.getAuthor());
                // insert new stories, or get the id from existing stories
                long anthologyId = insertOrUpdateAnthologyTitle(authorId, anthologyTitle.getTitle(), anthologyTitle.getFirstPublication());
                // create the book<->anthologyTitle link
                insertOrUpdateBookAnthology(anthologyId, bookId, false);
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
                                   @NonNull final List<Author> authors,
                                   @SuppressWarnings("SameParameterValue") final boolean dirtyBookIfNecessary) {

        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        // If we have author details, save them.

        if (mDeleteBookAuthorsStmt == null) {
            mDeleteBookAuthorsStmt = mStatements.add("mDeleteBookAuthorsStmt",
                    "DELETE FROM " + TBL_BOOK_AUTHOR + " WHERE " + DOM_BOOK_ID + " = ?");
        }
        // Need to delete the current records because they may have been reordered and a simple set of updates
        // could result in unique key or index violations.
        mDeleteBookAuthorsStmt.bindLong(1, bookId);
        mDeleteBookAuthorsStmt.execute();

        if (authors.size() == 0) {
            return;
        }

        if (mAddBookAuthorsStmt == null) {
            mAddBookAuthorsStmt = mStatements.add("mAddBookAuthorsStmt",
                    "INSERT INTO " + TBL_BOOK_AUTHOR
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
                    //FIXME ignoring failures to insert
                    mAddBookAuthorsStmt.executeInsert();
                    mAddBookAuthorsStmt.clearBindings();
                }
            } catch (Exception e) {
                Logger.logError(e);
                throw new DBExceptions.InsertException("Error adding author '" + author + "' {" + authorId + "} to book " + bookId + ": " + e.getMessage(), e);
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
        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        if (mDeleteBookBookshelfStmt == null) {
            mDeleteBookBookshelfStmt = mStatements.add("mDeleteBookBookshelfStmt",
                    "DELETE FROM " + TBL_BOOK_BOOKSHELF + " WHERE " + DOM_BOOK_ID + " = ?");
        }
        mDeleteBookBookshelfStmt.bindLong(1, bookId);
        mDeleteBookBookshelfStmt.execute();

        if (bookshelves.size() == 0) {
            return;
        }

        if (mInsertBookBookshelfStmt == null) {
            mInsertBookBookshelfStmt = mStatements.add("mInsertBookBookshelfStmt",
                    "INSERT INTO " + TBL_BOOK_BOOKSHELF + "(" + DOM_BOOK_ID + ", " + DOM_BOOKSHELF + ") Values (?,?)");
        }

        for (String bookshelf : bookshelves) {
            if (bookshelf.isEmpty()) {
                continue;
            }

            long bookshelfId = getBookshelfIdByName(bookshelf);
            if (bookshelfId == 0) {
                bookshelfId = insertBookshelf(bookshelf);
            }

            if (bookshelfId == -1) {
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


    }

    /**
     * transaction: needs
     * throws exceptions, caller must handle
     */
    private void globalReplacePositionedBookItem(@NonNull final TableDefinition table,
                                                 @NonNull final String objectIdField,
                                                 @NonNull final String positionField,
                                                 final long from,
                                                 final long to) {

        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        // Update books but prevent duplicate index errors - update books for which the new ID is not already present
        String sql = "UPDATE " + table + " SET " + objectIdField + "=" + to +
                " WHERE " +
                objectIdField + "=" + from +
                " AND NOT EXISTS(SELECT NULL FROM " + table + " WHERE " + DOM_BOOK_ID + "=" + DOM_BOOK_ID + " AND " + objectIdField + "=" + to + ")";

        mSyncedDb.execSQL(sql);

        // Finally, delete the rows that would have caused duplicates. Be cautious by using the
        // EXISTS statement again; it's not necessary, but we do it to reduce the risk of data
        // loss if one of the prior statements failed silently.
        //
        // We also move remaining items up one place to ensure positions remain correct
        //
        sql = "SELECT * FROM " + table + " WHERE " +
                objectIdField + "=" + from +
                " AND EXISTS(SELECT NULL FROM " + table + " WHERE " + DOM_BOOK_ID + "=" + table.dot(DOM_BOOK_ID) + " AND " + objectIdField + "=" + to + ")";

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
                    "DELETE FROM " + table +
                            " WHERE " + objectIdField + "=? AND " + DOM_BOOK_ID + "=?");

            // Statement to get the position of the already-existing 'new/replacement' object
            replacementIdPosStmt = mSyncedDb.compileStatement(
                    "SELECT " + positionField + " FROM " + table +
                            " WHERE " + DOM_BOOK_ID + "=? AND " + objectIdField + "=" + to);

            // Move statement; move a single entry to a new position
            moveStmt = mSyncedDb.compileStatement(
                    "UPDATE " + table + " SET " + positionField + "=?" +
                            " WHERE " + DOM_BOOK_ID + "=? AND " + positionField + "=?");

            // Sanity check to deal with legacy bad data
            checkMinStmt = mSyncedDb.compileStatement(
                    "SELECT min(" + positionField + ") FROM " + table +
                            " WHERE " + DOM_BOOK_ID + "=?");

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

                // It is tempting to move all rows up by one when we delete something, but that
                // would have to be done in another sorted cursor in order to prevent duplicate
                // index errors. So we just make sure we have something in position 1.

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
            final int pubDateCol = cursor.getColumnIndex(DOM_FIRST_PUBLICATION.name);

            while (cursor.moveToNext()) {
                Author author = new Author(
                        cursor.getLong(authorIdCol),
                        cursor.getString(familyNameCol),
                        cursor.getString(givenNameCol));

                list.add(new AnthologyTitle(author, cursor.getString(titleCol), cursor.getString(pubDateCol),
                        bookId));
            }
        }
        return list;
    }

    @NonNull
    public ArrayList<Author> getBookAuthorList(final long bookId) {
        ArrayList<Author> list = new ArrayList<>();
        try (Cursor authors = fetchAuthorsByBookId(bookId)) {
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

    public ArrayList<Series> getBookSeriesList(final long bookId) {
        ArrayList<Series> list = new ArrayList<>();
        try (Cursor series = fetchAllSeriesByBookId(bookId)) {
            if (series.getCount() == 0) {
                return list;
            }

            int idCol = series.getColumnIndex(DOM_ID.name);
            int nameCol = series.getColumnIndex(DOM_SERIES_NAME.name);
            int numCol = series.getColumnIndex(DOM_BOOK_SERIES_NUM.name);

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
    public BooksCursor fetchBooks(@NonNull final String bookWhere, @NonNull final String order) {
        String sql = getAllBooksSql(bookWhere);
        if (!order.isEmpty()) {
            sql += " ORDER BY " + order;
        }
        return fetchBooks(sql, new String[]{});
    }

    /**
     * Return the SQL for a list of all books in the database
     *
     * @param bookWhere clause to add to books search criteria
     *
     * @return A full piece of SQL to perform the search
     */
    private String getAllBooksSql(@NonNull final String bookWhere) {

        //TODO: redo this so the sql becomes static

        // there is no 'real' FROM table set. The 'from' is a combo of three sun-selects
        String fullSql = "SELECT b.*, " +
                SQL_FIELDS_FOR_AUTHOR + "," +
                "a." + DOM_AUTHOR_ID + ", " +
                "Coalesce(s." + DOM_SERIES_ID + ", 0) AS " + DOM_SERIES_ID + "," +
                "Coalesce(s." + DOM_SERIES_NAME + ", '') AS " + DOM_SERIES_NAME + "," +
                "Coalesce(s." + DOM_BOOK_SERIES_NUM + ", '') AS " + DOM_BOOK_SERIES_NUM + "," +
                " Case When _num_series < 2 Then Coalesce(s." + DOM_SERIES_FORMATTED + ", '') Else " + DOM_SERIES_FORMATTED + "||' " + BookCatalogueApp.getResourceString(R.string.and_others) + "' End AS " + DOM_SERIES_FORMATTED +
                " FROM (" +

                "SELECT DISTINCT " + SQL_FIELDS_FOR_BOOK + " FROM " + TBL_BOOKS.ref() +
                (!bookWhere.isEmpty() ? " WHERE " + " (" + bookWhere + ")" : "") + " ORDER BY Upper(" + TBL_BOOKS.dot(DOM_TITLE) + ") " + COLLATION + " ASC"

                + ") b" +

                // Get the 'default' author
                " JOIN (" +

                "SELECT " +
                DOM_AUTHOR_ID + "," +
                DOM_AUTHOR_FAMILY_NAME + "," +
                DOM_AUTHOR_GIVEN_NAMES + "," +
                TBL_BOOK_AUTHOR.dotAs(DOM_BOOK_ID) + "," +
                SQL_FIELDS_AUTHOR_FORMATTED +
                " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS) +

                ") a  ON a." + DOM_BOOK_ID + " = b." + DOM_ID + " AND a." + DOM_AUTHOR_ID + " = b." + DOM_AUTHOR_ID +

                // Get the 'default' series
                " LEFT OUTER JOIN (" +

                "SELECT " +
                DOM_SERIES_ID + "," +
                DOM_SERIES_NAME + "," +
                DOM_BOOK_SERIES_NUM + "," +
                TBL_BOOK_SERIES.dotAs(DOM_BOOK_ID) + "," +
                " Case When " + DOM_BOOK_SERIES_NUM + " = '' Then " + DOM_SERIES_NAME + "  Else " + DOM_SERIES_NAME + "||' #'||" + DOM_BOOK_SERIES_NUM + " End AS " + DOM_SERIES_FORMATTED +
                " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES) +

                ") s  ON s." + DOM_BOOK_ID + " = b." + DOM_ID + " AND s." + DOM_SERIES_ID + " = b." + DOM_SERIES_ID + " AND " + "Upper(s." + DOM_BOOK_SERIES_NUM + ")=Upper(b." + DOM_BOOK_SERIES_NUM + ") " + COLLATION;


        if (DEBUG_SWITCHES.SQL && DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            System.out.println("getAllBooksSql:\n\n" + fullSql);
        }
        return fullSql;
    }

    /**
     * Return a book that matches the given id
     *
     * @param bookId id of book to retrieve
     *
     * @return BookData matching book, if found
     *
     * @throws DBExceptions.NotFoundException if not found
     */
    public BookData getBookById(final long bookId) throws DBExceptions.NotFoundException {

        try (Cursor cursor = fetchBookById(bookId)) {
            // Put all cursor fields in collection
            BookData book = new BookData(cursor);

            // Get author, series, bookshelf and anthology title lists
            book.setAuthorList(getBookAuthorList(bookId));
            book.setSeriesList(getBookSeriesList(bookId));
            book.setAnthologyTitles(getBookAnthologyTitleList(bookId));
            book.setBookshelfList(getBookshelvesByBookIdAsStringList(bookId));
            return book;
        }
    }

    /**
     * Return a book (Cursor) that matches the given rowId
     *
     * @param bookId id of book to retrieve
     *
     * @return Cursor positioned to matching book, if found
     *
     * @throws DBExceptions.NotFoundException if not found
     */
    public BooksCursor fetchBookById(final long bookId) throws DBExceptions.NotFoundException {
        BooksCursor cursor = fetchBooks(TBL_BOOKS.dot(DOM_ID) + "=" + bookId, "");
        if (cursor == null || !cursor.moveToFirst()) {
            throw new DBExceptions.NotFoundException("Failed to find bookId=" + bookId);
        }
        return cursor;
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
    public BooksCursor fetchBooksByIsbnList(@NonNull final List<String> isbnList) {
        if (isbnList.size() == 0) {
            throw new IllegalArgumentException("No ISBNs specified in lookup");
        }

        StringBuilder where = new StringBuilder(TBL_BOOKS.dot(DOM_BOOK_ISBN));
        if (isbnList.size() == 1) {
            where.append("='").append(encodeString(isbnList.get(0))).append("'");
        } else {
            where.append(" IN (");
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
    //endregion

    //region Bookshelf

    /**
     * This function will create a new bookshelf in the database
     *
     * @param name The bookshelf name
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insertBookshelf(@NonNull final String name) {
        // TODO: Decide if we need to backup EMPTY bookshelves...
        ContentValues values = new ContentValues();
        values.put(DOM_BOOKSHELF.name, name);
        return mSyncedDb.insert(TBL_BOOKSHELF.getName(), null, values);
    }

    /**
     * Return a Cursor positioned at the bookshelf that matches the given rowId
     *
     * @param id of bookshelf to retrieve
     *
     * @return Name of bookshelf
     *
     * @throws SQLiteDoneException if zero rows found
     */
    @NonNull
    public String getBookshelfName(final long id) throws SQLiteDoneException {
        if (mGetBookshelfNameStmt == null) {
            mGetBookshelfNameStmt = mStatements.add("mGetBookshelfNameStmt",
                    "SELECT " + DOM_BOOKSHELF + " FROM " + TBL_BOOKSHELF + " WHERE " + DOM_ID + " = ?");
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

        boolean dirty = (0 < mSyncedDb.delete(TBL_BOOK_BOOKSHELF.getName(), DOM_BOOKSHELF + "=" + id, null));
        int rowsAffected = mSyncedDb.delete(TBL_BOOKSHELF.getName(), DOM_ID + "=" + id, null);

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
    public long getBookshelfId(@NonNull final Bookshelf bookshelf) {
        if (mGetBookshelfIdStmt == null) {
            mGetBookshelfIdStmt = mStatements.add("mGetBookshelfIdStmt",
                    "SELECT " + DOM_ID + " FROM " + TBL_BOOKSHELF +
                            " WHERE Upper(" + DOM_BOOKSHELF + ") = Upper(?)" + COLLATION);
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
        values.put(DOM_BOOKSHELF.name, name);
        int rowsAffected = mSyncedDb.update(TBL_BOOKSHELF.getName(), values, DOM_ID + "=" + id, null);
        if (rowsAffected > 0) {
            purgeAuthors();
            setBooksDirtyByBookshelf(id);
        }
        return rowsAffected;
    }

    /**
     * This will return the bookshelf id based on the name.
     *
     * @param name bookshelf to search for
     *
     * @return bookshelf id, or 0 when not found
     */
    private long getBookshelfIdByName(@NonNull final String name) {
        if (mGetBookshelfIdByNameStmt == null) {
            mGetBookshelfIdByNameStmt = mStatements.add("mGetBookshelfIdByNameStmt",
                    "SELECT " + DOM_ID + " FROM " + DOM_BOOKSHELF +
                            " WHERE Upper(" + DOM_BOOKSHELF + ") = Upper(?)" + COLLATION);
        }
        mGetBookshelfIdByNameStmt.bindString(1, name);
        return mGetBookshelfIdByNameStmt.simpleQueryForLongOrZero();
    }

    /**
     * Returns a list of all bookshelves in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<Bookshelf> getBookshelves() {
        String sql = mSql.get("getBookshelves");
        if (sql == null) {
            sql = "SELECT DISTINCT " + DOM_ID + "," + DOM_BOOKSHELF +
                    " FROM " + TBL_BOOKSHELF +
                    " ORDER BY Upper(" + DOM_BOOKSHELF + ") " + COLLATION;
            mSql.put("getBookshelves", sql);
        }
        ArrayList<Bookshelf> list = new ArrayList<>();

        try (Cursor cursor = mSyncedDb.rawQuery(sql)) {
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(cursor.getLong(0), cursor.getString(1)));
            }
            return list;
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
        String sql = mSql.get("fetchAllBookshelvesByBook");
        if (sql == null) {
            sql = "SELECT DISTINCT " + TBL_BOOKSHELF.dotAs(DOM_ID) + "," + TBL_BOOKSHELF.dotAs(DOM_BOOKSHELF) +
                    " FROM " + TBL_BOOK_BOOKSHELF.ref() + TBL_BOOK_BOOKSHELF.join(TBL_BOOKSHELF) +
                    " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_BOOK_ID) + "=?" +
                    " ORDER BY Upper(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + ") " + COLLATION;
            mSql.put("fetchAllBookshelvesByBook", sql);
        }
        return mSyncedDb.rawQuery(sql, new String[]{Long.toString(bookId)});
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
            int bsNameCol = cursor.getColumnIndex(DOM_BOOKSHELF.name);
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


    //endregion

    //region BookListStyle

    /**
     * @return a list of all defined styles in the database
     */
    @NonNull
    public List<BooklistStyle> getBooklistStyles() {
        final List<BooklistStyle> list = new ArrayList<>();
        String sql = "SELECT " + TBL_BOOKLIST_STYLES.refAll() + " FROM " + TBL_BOOKLIST_STYLES.ref();

        try (Cursor cursor = mSyncedDb.rawQuery(sql)) {
            int idCol = cursor.getColumnIndex(DOM_ID.name);
            int blobCol = cursor.getColumnIndex(DOM_STYLE.name);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                byte[] blob = cursor.getBlob(blobCol);
                BooklistStyle style;
                try {
                    style = SerializationUtils.deserializeObject(blob);
                    style.setRowId(id);
                    list.add(style);
                } catch (SerializationUtils.DeserializationException e) {
                    // Not much we can do; just delete it. Really should only happen in development.
                    deleteBooklistStyle(id);
                }
            }
        }
        return list;
    }

    /**
     * Create a new booklist style
     *
     * @return the row ID of the last row inserted, if this insert is successful. -1 otherwise.
     */
    public long insertBooklistStyle(@NonNull final BooklistStyle style) {
        if (mInsertBooklistStyleStmt == null) {
            mInsertBooklistStyleStmt = mStatements.add("mInsertBooklistStyleStmt",
                    TBL_BOOKLIST_STYLES.getInsert(DOM_STYLE) + " Values (?)");
        }
        final byte[] blob = SerializationUtils.serializeObject(style);
        mInsertBooklistStyleStmt.bindBlob(1, blob);
        return mInsertBooklistStyleStmt.executeInsert();
    }

    public void updateBooklistStyle(@NonNull final BooklistStyle style) {
        if (mUpdateBooklistStyleStmt == null) {
            mUpdateBooklistStyleStmt = mStatements.add("mUpdateBooklistStyleStmt",
                    TBL_BOOKLIST_STYLES.getInsertOrReplaceValues(DOM_ID, DOM_STYLE));
        }
        byte[] blob = SerializationUtils.serializeObject(style);
        mUpdateBooklistStyleStmt.bindLong(1, style.getRowId());
        mUpdateBooklistStyleStmt.bindBlob(2, blob);
        mUpdateBooklistStyleStmt.execute();
    }

    public void deleteBooklistStyle(final long id) {
        if (mDeleteBooklistStyleStmt == null) {
            mDeleteBooklistStyleStmt = mStatements.add("mDeleteBooklistStyleStmt",
                    "DELETE FROM " + TBL_BOOKLIST_STYLES + " WHERE " + DOM_ID + " = ?");
        }
        mDeleteBooklistStyleStmt.bindLong(1, id);
        mDeleteBooklistStyleStmt.execute();
    }
    //endregion

    /**
     * @param query The query string
     *
     * @return Cursor of search suggestions
     */
    @NonNull
    public Cursor fetchSearchSuggestions(@NonNull final String query) {
        String sql = mSql.get("fetchSearchSuggestions");
        if (sql == null) {
            sql = "SELECT * FROM (SELECT \"BK\" || " + TBL_BOOKS.dotAs(DOM_ID) + "," +
                    TBL_BOOKS.dot(DOM_TITLE) + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + "," +
                    TBL_BOOKS.dot(DOM_TITLE) + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                    " FROM " + TBL_BOOKS.ref() + " WHERE " + TBL_BOOKS.dot(DOM_TITLE) + " LIKE ?" +
                    " UNION " +
                    " SELECT \"AF\" || " + TBL_AUTHORS.dotAs(DOM_ID) + "," +
                    TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + "," +
                    TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                    " FROM " + TBL_AUTHORS.ref() + " WHERE " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " LIKE ?" +
                    " UNION " +
                    " SELECT \"AG\" || " + TBL_AUTHORS.dotAs(DOM_ID) + "," +
                    TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + "," +
                    TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                    " FROM " + TBL_AUTHORS.ref() + " WHERE " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " LIKE ?" +
                    " UNION " +
                    " SELECT \"BK\" || " + TBL_BOOKS.dotAs(DOM_ID) + "," +
                    TBL_BOOKS.dot(DOM_BOOK_ISBN) + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + "," +
                    TBL_BOOKS.dot(DOM_BOOK_ISBN) + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                    " FROM " + TBL_BOOKS + " WHERE " + TBL_BOOKS.dot(DOM_BOOK_ISBN) + " LIKE ?" +
                    " ) AS zzz " +
                    " ORDER BY Upper(" + SearchManager.SUGGEST_COLUMN_TEXT_1 + ") " + COLLATION;
            mSql.put("fetchSearchSuggestions", sql);
        }
        String q = query + "%";
        return mSyncedDb.rawQuery(sql, new String[]{q, q, q, q});
    }

    //region Format

    /**
     * Returns a unique list of all formats in the database; uses the pre-defined ones if none.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getFormats() {
        String sql = "SELECT DISTINCT " + DOM_BOOK_FORMAT + " FROM " + TBL_BOOKS
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
        // Update books but prevent duplicate index errors
        String sql = "UPDATE " + TBL_BOOKS +
                " SET " + DOM_BOOK_FORMAT + " = '" + encodeString(to) + "," +
                " " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                " WHERE " + DOM_BOOK_FORMAT + " = '" + encodeString(from) + "'";
        mSyncedDb.execSQL(sql);
    }
    //endregion

    //region Genre

    /**
     * Returns a unique list of all locations in the database; uses the pre-defined ones if none.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getGenres() {
        String sql = "SELECT DISTINCT " + DOM_BOOK_GENRE + " FROM " + TBL_BOOKS
                + " ORDER BY lower(" + DOM_BOOK_GENRE + ") " + COLLATION;

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
        // Update books but prevent duplicate index errors
        String sql = "UPDATE " + TBL_BOOKS +
                " SET " + DOM_BOOK_GENRE + " = '" + encodeString(to) + "'," +
                " " + DOM_LAST_UPDATE_DATE + " = current_timestamp" +
                " WHERE " + DOM_BOOK_GENRE + " = '" + encodeString(from) + "'";
        mSyncedDb.execSQL(sql);
    }
    //endregion

    //region Language

    /**
     * Returns a unique list of all languages in the database; uses the pre-defined ones if none.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getLanguages() {
        String sql = "SELECT DISTINCT " + DOM_BOOK_LANGUAGE + " FROM " + TBL_BOOKS
                + " ORDER BY lower(" + DOM_BOOK_LANGUAGE + ") " + COLLATION;

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

        // Update books but prevent duplicate index errors
        String sql = "UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_LANGUAGE + " = '" + encodeString(to) + "'," +
                " " + DOM_LAST_UPDATE_DATE + " = current_timestamp"
                + " WHERE " + DOM_BOOK_LANGUAGE + " = '" + encodeString(from) + "'";
        mSyncedDb.execSQL(sql);
    }
    //endregion

    //region Loan

    /**
     * This function will create a new loan in the database
     *
     * @param bookData             the book
     * @param dirtyBookIfNecessary flag to set book dirty or not (for now, always false...)
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertLoan(@NonNull final BookData bookData, final boolean dirtyBookIfNecessary) {
        ContentValues values = new ContentValues();
        values.put(DOM_BOOK_ID.name, bookData.getBookId());
        values.put(DOM_LOANED_TO.name, bookData.getString(DOM_LOANED_TO.name));

        long newId = mSyncedDb.insert(TBL_LOAN.getName(), null, values);
        if (newId > 0) {
            purgeLoans();
            if (dirtyBookIfNecessary) {
                setBookDirty(bookData.getBookId());
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
        int rowsAffected = mSyncedDb.delete(TBL_LOAN.getName(), DOM_BOOK_ID + "=" + bookId, null);
        if (rowsAffected > 0) {
            purgeLoans();
            if (dirtyBookIfNecessary) {
                setBookDirty(bookId);
            }
        }
        return rowsAffected;
    }

    /**
     * Purge loans for non-existent books
     *
     * {@link DatabaseDefinitions#TBL_LOAN}
     *
     */
    private void purgeLoans() {
        mSyncedDb.delete(TBL_LOAN.getName(),
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
        try (Cursor cursor = mSyncedDb.query(TBL_LOAN.getName(),
                new String[]{DOM_BOOK_ID.name, DOM_LOANED_TO.name},
                DOM_BOOK_ID + "=" + bookId,
                null, null, null, null)) {

            // 1= DOM_LOANED_TO.name
            return cursor.moveToFirst() ? cursor.getString(1) : null;
        }
    }
    //endregion

    //region Location

    /**
     * Returns a unique list of all locations in the database; uses the pre-defined ones if none.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getLocations() {
        String sql = "SELECT DISTINCT " + DOM_BOOK_LOCATION + " FROM " + TBL_BOOKS
                + " ORDER BY lower(" + DOM_BOOK_LOCATION + ") " + COLLATION;

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
        // Update books but prevent duplicate index errors
        String sql = "UPDATE " + TBL_BOOKS +
                " SET " + DOM_BOOK_LOCATION + " = '" + encodeString(to) + "'," +
                " " + DOM_LAST_UPDATE_DATE + " = current_timestamp "
                + " WHERE " + DOM_BOOK_LOCATION + " = '" + encodeString(from) + "'";
        mSyncedDb.execSQL(sql);
    }
    //endregion

    //region Publisher

    /**
     * Returns a unique list of all publishers in the database
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getPublishers() {
        String sql = "SELECT DISTINCT " + DOM_BOOK_PUBLISHER + " FROM " + TBL_BOOKS
                + " ORDER BY lower(" + DOM_BOOK_PUBLISHER + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql)) {
            return getFirstColumnAsList(c);
        }
    }

    public void globalReplacePublisher(@NonNull final Publisher from, @NonNull final Publisher to) {
        if (Objects.equals(from, to)) {
            return;
        }
        // Update books but prevent duplicate index errors
        String sql = "UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_PUBLISHER + " = '" + encodeString(to.name) + "'," +
                " " + DOM_LAST_UPDATE_DATE + " = current_timestamp"
                + " WHERE " + DOM_BOOK_PUBLISHER + " = '" + encodeString(from.name) + "'";
        mSyncedDb.execSQL(sql);
    }
    //endregion

    //region Series

    /**
     * Create a new series in the database
     *
     * @param name A string containing the series name
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    private long insertSeries(@NonNull final String name) {
        ContentValues values = new ContentValues();
        values.put(DOM_SERIES_NAME.name, name);
        return mSyncedDb.insert(TBL_SERIES.getName(), null, values);
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
        int rowsAffected = mSyncedDb.delete(TBL_BOOK_SERIES.getName(), DOM_SERIES_ID + " = " + id, null);
        if (rowsAffected > 0) {
            setBooksDirtyBySeries(id);
            purgeSeries();
        }
        return rowsAffected;
    }

    /**
     * This will return the series based on the ID.
     *
     * @return series, or null when not found
     */
    @Nullable
    public Series getSeriesById(final long id) {
        String sql = "SELECT " + DOM_SERIES_NAME + " FROM " + TBL_SERIES
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
                if (mSyncedDb.update(TBL_SERIES.getName(), values, DOM_ID + " = " + series.id, null) > 0) {
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
                    "SELECT " + DOM_ID + " FROM " + TBL_SERIES +
                            " WHERE Upper(" + DOM_SERIES_NAME + ") = Upper(?)" + COLLATION);
        }
        mGetSeriesIdStmt.bindString(1, name);
        return mGetSeriesIdStmt.simpleQueryForLongOrZero();
    }

    /**
     * @throws DBExceptions.UpdateException on failure
     */
    public void globalReplaceSeries(@NonNull final Series from, @NonNull final Series to) {
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
            throw new DBExceptions.NotFoundException("Old Series is not defined");
        }

        if (from.id == to.id) {
            return;
        }

        SyncLock l = mSyncedDb.beginTransaction(true);
        try {
            setBooksDirtyBySeries(from.id);

            // Update books but prevent duplicate index errors
            String sql = "UPDATE " + TBL_BOOK_SERIES + " SET " + DOM_SERIES_ID + "=" + to.id +
                    " WHERE " + DOM_SERIES_ID + " = " + from.id +
                    " AND NOT Exists(SELECT NULL FROM " + TBL_BOOK_SERIES.ref() + " WHERE " +
                    TBL_BOOK_SERIES.dot(DOM_BOOK_ID) + " = " + TBL_BOOK_SERIES.dot(DOM_BOOK_ID) +
                    " AND " + TBL_BOOK_SERIES.dot(DOM_SERIES_ID) + " = " + to.id + ")";
            mSyncedDb.execSQL(sql);

            globalReplacePositionedBookItem(TBL_BOOK_SERIES, DOM_SERIES_ID.name, DOM_BOOK_SERIES_POSITION.name, from.id, to.id);

            mSyncedDb.setTransactionSuccessful();
        } catch (Exception e) {
            Logger.logError(e);
            throw new DBExceptions.UpdateException(e);
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
     * {@link DatabaseDefinitions#TBL_BOOK_SERIES}
     * {@link DatabaseDefinitions#DB_TB_SERIES}
     */
    public void purgeSeries() {

        if (mPurgeBookSeriesStmt == null) {
            mPurgeBookSeriesStmt = mStatements.add("mPurgeBookSeriesStmt",
                    "DELETE FROM " + TBL_BOOK_SERIES + " WHERE " + DOM_BOOK_ID +
                            " NOT IN (SELECT DISTINCT " + DOM_ID + " FROM " + TBL_BOOKS + ")");
        }
        try {
            mPurgeBookSeriesStmt.execute();
        } catch (Exception e) {
            Logger.logError(e, "Failed to purge " + TBL_BOOK_SERIES);
        }

        if (mPurgeSeriesStmt == null) {
            mPurgeSeriesStmt = mStatements.add("mPurgeSeriesStmt",
                    "DELETE FROM " + TBL_SERIES + " WHERE " + DOM_ID +
                            " NOT IN (SELECT DISTINCT " + DOM_SERIES_ID + " FROM " + TBL_BOOK_SERIES + ") ");
        }
        try {
            mPurgeSeriesStmt.execute();
        } catch (Exception e) {
            Logger.logError(e, "Failed to purge " + TBL_SERIES);
        }
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
                    "SELECT COUNT(" + DOM_BOOK_ID + ") FROM " + TBL_BOOK_SERIES + " WHERE " + DOM_SERIES_ID + "=?");
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
        String sql = "SELECT DISTINCT " + DOM_SERIES_NAME + " FROM " + TBL_SERIES +
                " ORDER BY Upper(" + DOM_SERIES_NAME + ") " + COLLATION;
        return getColumnAsList(sql, DOM_SERIES_NAME.name);
    }

    /**
     * Return a Cursor over the list of all authors  in the database for the given book
     *
     * @param bookId the id of the book
     *
     * @return Cursor over all authors
     */
    @NonNull
    private Cursor fetchAllSeriesByBookId(final long bookId) {
        String sql = mSql.get("fetchAllSeriesByBookId");
        if (sql == null) {
            sql = "SELECT DISTINCT " +
                    TBL_SERIES.dotAs(DOM_ID) + ", " +
                    TBL_SERIES.dotAs(DOM_SERIES_NAME) + ", " +
                    TBL_BOOK_SERIES.dotAs(DOM_BOOK_SERIES_NUM) + ", " +
                    TBL_BOOK_SERIES.dotAs(DOM_BOOK_SERIES_POSITION) + ", " +
                    DOM_SERIES_NAME + "||' ('||" + DOM_BOOK_SERIES_NUM + "||')' AS " + DOM_SERIES_FORMATTED +
                    " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES) +
                    " WHERE " + TBL_BOOK_SERIES.dot(DOM_BOOK_ID) + "=?" +
                    " ORDER BY " + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + ", Upper(" + TBL_SERIES.dot(DOM_SERIES_NAME) + ") " + COLLATION + " ASC";
            mSql.put("fetchAllSeriesByBookId", sql);
        }

        return mSyncedDb.rawQuery(sql, new String[]{Long.toString(bookId)});
    }
    //endregion

    //region Helpers


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
     * @param tableName destination table
     * @param bookData  Source column data
     *
     * @return New and filtered ContentValues
     */
    @NonNull
    private ContentValues filterValues(@SuppressWarnings("SameParameterValue") @NonNull final String tableName,
                                       @NonNull final BookData bookData) {

        TableInfo table = new TableInfo(mSyncedDb, tableName);

        ContentValues values = new ContentValues();
        // Create the arguments
        for (String key : bookData.keySet()) {
            // Get column info for this column.
            TableInfo.ColumnInfo columnInfo = table.getColumn(key);
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
    private BooksCursor fetchBooks(@NonNull final String sql, @NonNull final String[] selectionArgs) {
        return (BooksCursor) mSyncedDb.rawQueryWithFactory(mBooksFactory, sql, selectionArgs, "");
    }
    //endregion

    //region Goodreads

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
        String where = TBL_BOOKS.dot(DOM_BOOK_GOODREADS_BOOK_ID) + "=" + grId;
        return fetchBooks(where, "");
    }

    /**
     * Query to get all book IDs and ISBN for sending to goodreads.
     */
    @NonNull
    public BooksCursor getAllBooksForGoodreadsCursor(final long startId, final boolean updatesOnly) {
        String sql = "SELECT " + DOM_BOOK_ISBN + ", " + DOM_ID + ", " + DOM_BOOK_GOODREADS_BOOK_ID + "," +
                " " + DOM_BOOK_NOTES + ", " + DOM_BOOK_READ + ", " + DOM_BOOK_READ_END + ", " + DOM_BOOK_RATING +
                " FROM " + TBL_BOOKS + " WHERE " + DOM_ID + " > " + startId;
        if (updatesOnly) {
            sql += " AND " + DOM_LAST_UPDATE_DATE + " > " + DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
        }
        sql += " ORDER BY " + DOM_ID;

        return fetchBooks(sql, new String[]{});
    }

    /**
     * Query to get a specific book ISBN from the ID for sending to goodreads.
     */
    @NonNull
    public BooksCursor getBookForGoodreadsCursor(final long bookId) {
        String sql = mSql.get("getBookForGoodreadsCursor");
        if (sql == null) {
            sql = "SELECT " + DOM_ID + "," +
                    DOM_BOOK_ISBN + ", " +
                    DOM_BOOK_GOODREADS_BOOK_ID + "," +
                    DOM_BOOK_NOTES + "," +
                    DOM_BOOK_READ + "," +
                    DOM_BOOK_READ_END + "," +
                    DOM_BOOK_RATING +
                    " FROM " + TBL_BOOKS + " WHERE " + DOM_ID + " =?  ORDER BY " + DOM_ID;
            mSql.put("getBookForGoodreadsCursor", sql);
        }
        return fetchBooks(sql, new String[]{Long.toString(bookId)});
    }


    /**
     * Query to get a all bookshelves for a book, for sending to goodreads.
     */
    @NonNull
    public Cursor fetchAllBookBookshelvesForGoodreads(final long bookId) {
        String sql = mSql.get("fetchAllBookBookshelvesForGoodreads");
        if (sql == null) {
         sql = "SELECT " + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) +
                " FROM " + TBL_BOOKSHELF.ref() + " JOIN " + TBL_BOOK_BOOKSHELF.ref() +
                " ON " + TBL_BOOK_BOOKSHELF.dot(DOM_BOOKSHELF) + "=" + TBL_BOOKSHELF.dot(DOM_ID) +
                " AND " + TBL_BOOK_BOOKSHELF.dot(DOM_BOOK_ID) + "=?" +
                " ORDER BY " + TBL_BOOKSHELF.dot(DOM_BOOKSHELF);
            mSql.put("fetchAllBookBookshelvesForGoodreads", sql);
        }
        return mSyncedDb.rawQuery(sql, new String[]{Long.toString(bookId)});
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
            mSetGoodreadsBookIdStmt = mStatements.add("mSetGoodreadsBookIdStmt",
                    "UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_GOODREADS_BOOK_ID + " = ? WHERE " + DOM_ID + " = ?");
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
            mSetGoodreadsSyncDateStmt = mStatements.add("mSetGoodreadsSyncDateStmt",
                    "UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + " = current_timestamp WHERE " + DOM_ID + " = ?");
        }
        mSetGoodreadsSyncDateStmt.bindLong(1, bookId);
        mSetGoodreadsSyncDateStmt.execute();
    }

    //endregion

    //region open,close,transactions

    /**
     * Open the books database.
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

    /**
     * Used by {@link com.eleybourn.bookcatalogue.backup.CsvImporter}
     */
    public SyncLock startTransaction(boolean isUpdate) {
        return mSyncedDb.beginTransaction(isUpdate);
    }

    /**
     * Used by {@link com.eleybourn.bookcatalogue.backup.CsvImporter}
     */
    public void endTransaction(SyncLock lock) {
        mSyncedDb.endTransaction(lock);
    }

    /**
     * Used by {@link com.eleybourn.bookcatalogue.backup.CsvImporter}
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean inTransaction() {
        return mSyncedDb.inTransaction();
    }

    /**
     * Used by {@link com.eleybourn.bookcatalogue.backup.CsvImporter}
     */
    public void setTransactionSuccessful() {
        mSyncedDb.setTransactionSuccessful();
    }
    //endregion

    //region FTS

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

        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        final BookRow book = books.getRowView();
        // Build the SQL to get author details for a book.

        // ... all authors
        final String authorBaseSql = "SELECT " + TBL_AUTHORS.dot("*") +
                " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS) +
                " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_BOOK_ID) + " = ";

        // ... all series
        final String seriesBaseSql = "SELECT " + TBL_SERIES.dot(DOM_SERIES_NAME) + " || ' ' || Coalesce(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM) + ",'') AS seriesInfo" +
                " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES) +
                " WHERE " + TBL_BOOK_SERIES.dot(DOM_BOOK_ID) + " = ";

        // ... all anthology titles
        final String anthologyBaseSql = "SELECT " +
                TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " || ' ' || " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " AS anthologyAuthorInfo, " +
                DOM_TITLE + " AS anthologyTitleInfo " +
                " FROM " + TBL_ANTHOLOGY.ref() + TBL_ANTHOLOGY.join(TBL_BOOK_ANTHOLOGY) + TBL_ANTHOLOGY.join(TBL_AUTHORS) +
                " WHERE " + TBL_BOOK_ANTHOLOGY.dot(DOM_BOOK_ID) + " = ";

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
     *
     * Transaction: required
     */
    private void insertFts(final long bookId) {

        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        try {
            long t0;
            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                t0 = System.currentTimeMillis();
            }

            if (mInsertFtsStmt == null) {
                // Build the FTS insert statement base. The parameter order MUST match the order expected in {@link #ftsSendBooks}.
                String sql = TBL_BOOKS_FTS.getInsert(
                        DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION,
                        DOM_BOOK_NOTES, DOM_BOOK_PUBLISHER, DOM_BOOK_GENRE,
                        DOM_BOOK_LOCATION, DOM_BOOK_ISBN, DOM_DOCID)
                        + " Values (?,?,?,?,?,?,?,?,?)";
                mInsertFtsStmt = mStatements.add("mInsertFtsStmt", sql);
            }

            try (BooksCursor books = fetchBooks("SELECT * FROM " + TBL_BOOKS + " WHERE " + DOM_ID + " = " + bookId, new String[]{})) {
                ftsSendBooks(books, mInsertFtsStmt);
            } finally {
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    System.out.println("Inserted FTS in " + (System.currentTimeMillis() - t0) + "ms");
                }
            }
        } catch (Exception e) {
            Logger.logError(e, "Failed to update FTS");
        }
    }

    /**
     * Update an existing FTS record.
     *
     * Transaction: required
     */
    private void updateFts(final long bookId) {

        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        try {
            long t0;
            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                t0 = System.currentTimeMillis();
            }

            if (mUpdateFtsStmt == null) {
                // Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
                String sql = TBL_BOOKS_FTS.getUpdate(
                        DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION,
                        DOM_BOOK_NOTES, DOM_BOOK_PUBLISHER, DOM_BOOK_GENRE,
                        DOM_BOOK_LOCATION, DOM_BOOK_ISBN) + " WHERE " + DOM_DOCID + " = ?";
                mUpdateFtsStmt = mStatements.add("mUpdateFtsStmt", sql);
            }

            try (BooksCursor books = fetchBooks("SELECT * FROM " + TBL_BOOKS + " WHERE " + DOM_ID + " = " + bookId, new String[]{})) {
                ftsSendBooks(books, mUpdateFtsStmt);
            } finally {
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    System.out.println("Updated FTS in " + (System.currentTimeMillis() - t0) + "ms");
                }
            }
        } catch (Exception e) {
            Logger.logError(e, "Failed to update FTS");
        }
    }

    /**
     * Delete an existing FTS record
     *
     * Transaction: required
     */
    private void deleteFts(final long bookId) {

        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        try {
            long t0;
            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                t0 = System.currentTimeMillis();
            }
            if (mDeleteFtsStmt == null) {
                mDeleteFtsStmt = mStatements.add("mDeleteFtsStmt",
                        "DELETE FROM " + TBL_BOOKS_FTS + " WHERE " + DOM_DOCID + " = ?");
            }
            mDeleteFtsStmt.bindLong(1, bookId);
            mDeleteFtsStmt.execute();
            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                System.out.println("Deleted FROM FTS in " + (System.currentTimeMillis() - t0) + "ms");
            }
        } catch (Exception e) {
            Logger.logError(e, "Failed to delete FTS");
        }
    }

    /**
     * Rebuild the entire FTS database. This can take several seconds with many books or a slow phone.
     */
    public void rebuildFts() {

        if (mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        long t0;
        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            t0 = System.currentTimeMillis();
        }
        boolean gotError = false;

        // Make a copy of the FTS table definition for our temp table.
        TableDefinition ftsTemp = TBL_BOOKS_FTS.clone();
        // Give it a new name
        ftsTemp.setName(ftsTemp.getName() + "_temp");

        SyncLock lock = mSyncedDb.beginTransaction(true);

        // Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
        final String sql = ftsTemp.getInsert(
                DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION,
                DOM_BOOK_NOTES, DOM_BOOK_PUBLISHER, DOM_BOOK_GENRE,
                DOM_BOOK_LOCATION, DOM_BOOK_ISBN, DOM_DOCID)
                + " values (?,?,?,?,?,?,?,?,?)";
        // Drop and recreate our temp copy
        ftsTemp.create(mSyncedDb, false);
        ftsTemp.drop(mSyncedDb);

        try (SynchronizedStatement insert = mSyncedDb.compileStatement(sql);
             BooksCursor books = fetchBooks("SELECT * FROM " + TBL_BOOKS, new String[]{})) {
            ftsSendBooks(books, insert);
            // Drop old table, ready for rename
            TBL_BOOKS_FTS.drop(mSyncedDb);

            mSyncedDb.setTransactionSuccessful();
        } catch (Exception e) {
            Logger.logError(e);
            gotError = true;
        } finally {
            mSyncedDb.endTransaction(lock);
			/* According to this:
			    http://old.nabble.com/Bug-in-FTS3-when-trying-to-rename-table-within-a-transaction-td29474500.html

			  FTS tables should only be renamed outside of transactions. Which is a pain.

			   => makes sense... this is DDL after all. DDL is not covered by most/all databases for transactions.
			   => very few (IMHO) programs manipulate tables themselves (other then temp obv). The DBA's don't allow it.
			*/

            //  Delete old table and rename the new table
            if (!gotError) {
                mSyncedDb.execSQL("ALTER TABLE " + ftsTemp + " rename to " + TBL_BOOKS_FTS);
            }
        }

        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            System.out.println("rebuildFts in " + (System.currentTimeMillis() - t0) + "ms");
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
                "SELECT " + DOM_DOCID + " FROM " + TBL_BOOKS_FTS + " WHERE " + TBL_BOOKS_FTS + " match '" + keywords);
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
                        // Make every token a wildcard TODO: Make this a preference
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
            // Make every token a wildcard TODO: Make this a preference
            out.append('*');
        }
        return out.toString();
    }

    //endregion

    /**
     * Backup database file using default file name
     */
    public void backupDbFile() {
        StorageUtils.backupDbFile(mSyncedDb.getUnderlyingDatabase(), "DbExport.db");
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
        String whereClause;
        if (sinceDate == null) {
            whereClause = "";
        } else {
            whereClause = " WHERE " + TBL_BOOKS.dot(DOM_LAST_UPDATE_DATE) + " > '" + DateUtils.toSqlDateTime(sinceDate) + "' ";
        }

        String sql = "SELECT DISTINCT " + SQL_FIELDS_FOR_BOOK + "," + TBL_LOAN.dotAs(DOM_LOANED_TO) +
                " FROM " + TBL_BOOKS.ref() + " LEFT OUTER JOIN " + TBL_LOAN.ref() + " ON (" + TBL_LOAN.dot(DOM_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_ID) + ") " +
                whereClause +
                " ORDER BY " + TBL_BOOKS.dot(DOM_ID);
        return fetchBooks(sql, new String[]{});
    }

    /**
     * Data cleanup routine called on upgrade to v4.0.3 to cleanup data integrity issues cased
     * by earlier merge code that could have left the first author or series for a book having
     * a position number > 1.
     */
    public void fixupAuthorsAndSeries() {
        if (mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        SyncLock syncLock = mSyncedDb.beginTransaction(true);
        try {
            fixupPositionedBookItems(TBL_BOOK_AUTHOR.getName(), DOM_AUTHOR_POSITION.name);
            fixupPositionedBookItems(TBL_BOOK_SERIES.getName(), DOM_BOOK_SERIES_POSITION.name);
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(syncLock);
        }

    }

    /**
     * Data cleaning routine for upgrade to version 4.0.3 to cleanup any books that have no primary author/series.
     */
    private void fixupPositionedBookItems(@NonNull final String tableName,
                                          @NonNull final String positionField) {
        String sql = "SELECT " + TBL_BOOKS.dotAs(DOM_ID) + "," +
                " min(o." + positionField + ") AS pos" +
                " FROM " + TBL_BOOKS.ref() + " JOIN " + tableName + " o ON o." + DOM_BOOK_ID + "=" + TBL_BOOKS.dot(DOM_ID) +
                " GROUP BY " + TBL_BOOKS.dot(DOM_ID);

        SyncLock syncLock = null;
        if (!mSyncedDb.inTransaction()) {
            syncLock = mSyncedDb.beginTransaction(true);
        }
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
                        sql = "UPDATE " + tableName + " SET " + positionField + "=1 WHERE " + DOM_BOOK_ID + "=? AND " + positionField + "=?";
                        moveStmt = mSyncedDb.compileStatement(sql);
                    }

                    long book = c.getLong(bookCol);
                    // Move subsequent records up by one
                    moveStmt.bindLong(1, book);
                    moveStmt.bindLong(2, pos);
                    moveStmt.execute();
                }
            }
            if (syncLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
        } finally {
            if (syncLock != null) {
                mSyncedDb.endTransaction(syncLock);
            }
            if (moveStmt != null) {
                moveStmt.close();
            }
        }
    }

    /**
     * DEBUG only
     */
    private static class InstanceRefDebug extends WeakReference<CatalogueDBAdapter> {
        private final Exception mCreationException;

        InstanceRefDebug(@NonNull final CatalogueDBAdapter db) {
            super(db);
            mCreationException = new RuntimeException();
        }

        @NonNull
        Exception getCreationException() {
            return mCreationException;
        }
    }
}

