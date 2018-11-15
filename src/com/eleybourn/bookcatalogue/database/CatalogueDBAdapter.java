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
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TOCEntry;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.StringList;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISFDB_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_PAID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_PAID_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_TOC_ENTRY_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_WITH_MULTIPLE_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_ANTHOLOGY_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_IS_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_DOCID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_STYLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKLIST_STYLES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS_FTS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_LOAN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.COLLATION;


/**
 * Book Catalogue database access helper class. Defines the basic CRUD operations
 * for the catalogue (based on the Notepad tutorial), and gives the
 * ability to list all books as well as retrieve or modify a specific book.
 *
 * NOTE: As of 4.2, DO NOT USE OnUpgrade TO DISPLAY UPGRADE MESSAGES.
 * Use the UpgradeMessageManager class.
 * This change separated messages from DB changes (most releases do not involve DB upgrades).
 *
 * insert:
 * * return new id, or -1 for 'small' error.
 * * Any Exception is rethrown as {@link com.eleybourn.bookcatalogue.database.DBExceptions.InsertException}
 *
 * update:
 * * return rows affected, can be 0.
 * * Any Exception is rethrown as {@link com.eleybourn.bookcatalogue.database.DBExceptions.UpdateException}
 *
 * insertOrUpdate:
 * * insert: as above
 *
 * * update: the existing id !
 * * Any Exception is rethrown as {@link com.eleybourn.bookcatalogue.database.DBExceptions.UpdateException}
 *
 *
 * ENHANCE: Use date_added to add 'Recent Acquisitions' virtual shelf; need to resolve how this may relate to date_acquired
 */
public class CatalogueDBAdapter implements AutoCloseable {

    /**
     * Options indicating the UPDATE_DATE field from the bundle should be trusted.
     * If this flag is not set, the UPDATE_DATE will be set based on the current time
     */
    public static final int BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT = 1;
    /** Options indicating to skip doing the 'purge' step; mainly used in batch operations. */
    public static final int BOOK_UPDATE_SKIP_PURGE_REFERENCES = 1 << 1;

    /** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync */
    private static final Synchronizer mSynchronizer = new Synchronizer();

    /** Static Factory object to create the custom cursor */
    private static final CursorFactory mTrackedCursorFactory = new CursorFactory() {
        @Override
        @NonNull
        public Cursor newCursor(
                final SQLiteDatabase db,
                final @NonNull SQLiteCursorDriver masterQuery,
                final @NonNull String editTable,
                final @NonNull SQLiteQuery query) {
            return new TrackedCursor(masterQuery, editTable, query, mSynchronizer);
        }
    };

    /** Static Factory object to create the custom cursor */
    @NonNull
    private static final CursorFactory mBooksCursorFactory = new CursorFactory() {
        @Override
        public Cursor newCursor(
                final SQLiteDatabase db,
                final @NonNull SQLiteCursorDriver masterQuery,
                final @NonNull String editTable,
                final @NonNull SQLiteQuery query) {
            return new BookCursor(masterQuery, editTable, query, mSynchronizer);
        }
    };

    /** DEBUG only */
    private static final ArrayList<InstanceRefDebug> mInstances = new ArrayList<>();

    private static final String COLUMN_ALIAS_NR_OF_SERIES = "_num_series";
    private static final String COLUMN_ALIAS_NR_OF_AUTHORS = "_num_authors";

    /**
     * set of fields suitable for a select
     */
    private static final String SQL_FIELDS_FOR_BOOK =
            TBL_BOOKS.dotAs(DOM_PK_ID) + "," +
                    TBL_BOOKS.dotAs(DOM_TITLE) + "," +
                    // Find FIRST series ID.
                    "(SELECT " + DOM_FK_SERIES_ID + " FROM " + TBL_BOOK_SERIES.ref() +
                    " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_PK_ID) +
                    " ORDER BY " + DOM_BOOK_SERIES_POSITION + " ASC  LIMIT 1) AS " + DOM_FK_SERIES_ID + "," +
                    // Find FIRST series NUM.
                    "(SELECT " + DOM_BOOK_SERIES_NUM + " FROM " + TBL_BOOK_SERIES.ref() +
                    " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_PK_ID) +
                    " ORDER BY " + DOM_BOOK_SERIES_POSITION + " ASC  LIMIT 1) AS " + DOM_BOOK_SERIES_NUM + "," +
                    // Get the total series count
                    "(SELECT COUNT(*) FROM " + TBL_BOOK_SERIES.ref() +
                    " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_PK_ID) + ") AS " + COLUMN_ALIAS_NR_OF_SERIES + "," +

                    // Find the first AUTHOR ID
                    "(SELECT " + DOM_FK_AUTHOR_ID + " FROM " + TBL_BOOK_AUTHOR.ref() +
                    " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_PK_ID) +
                    " ORDER BY " + DOM_AUTHOR_POSITION + "," + TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR_ID) + " LIMIT 1) AS " + DOM_FK_AUTHOR_ID + "," +
                    // Get the total author count. TODO: does not seem to get used anywhere ?
                    "(SELECT COUNT(*) FROM " + TBL_BOOK_AUTHOR.ref() +
                    " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_PK_ID) + ") AS " + COLUMN_ALIAS_NR_OF_AUTHORS + "," +

                    TBL_BOOKS.dotAs(DOM_BOOK_ISBN) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_PUBLISHER) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_DATE_PUBLISHED) + "," +
                    TBL_BOOKS.dotAs(DOM_FIRST_PUBLICATION) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_EDITION_BITMASK) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_RATING) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_READ) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_PAGES) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_NOTES) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_PRICE_LISTED) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_PRICE_LISTED_CURRENCY) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_PRICE_PAID) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_PRICE_PAID_CURRENCY) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_DATE_ACQUIRED) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_ANTHOLOGY_BITMASK) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_LOCATION) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_READ_START) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_READ_END) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_FORMAT) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_SIGNED) + "," +
                    TBL_BOOKS.dotAs(DOM_DESCRIPTION) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_GENRE) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_LANGUAGE) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_DATE_ADDED) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_LIBRARY_THING_ID) + "," +
                    TBL_BOOKS.dotAs(DOM_BOOK_ISFDB_ID) + "," +
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
                    " Else " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " || ',' || " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " End" +
                    " AS " + DOM_AUTHOR_FORMATTED;
    /**
     * If no given name -> family name
     * otherwise -> given family
     */
    private static final String SQL_FIELDS_AUTHOR_FORMATTED_GIVEN_FIRST =
            " Case When " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "='' Then " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) +
                    " Else " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " || ' ' || " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " End" +
                    " AS " + DOM_AUTHOR_FORMATTED_GIVEN_FIRST;

    /**
     * set of 4:
     * 'family',
     * 'given',
     * 'family, given',
     * 'given family'
     */
    private static final String SQL_FIELDS_FOR_AUTHOR =
            TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME) + "," +
                    TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES) + "," +
                    SQL_FIELDS_AUTHOR_FORMATTED;

    private static DatabaseHelper mDbHelper;
    private static SynchronizedDb mSyncedDb;
    @NonNull
    private static Integer mDebugInstanceCount = 0;
    @NonNull
    private final Context mContext;

    /**
     * a cache for SQL strings, this 'should' make it more efficient, as strings will be
     * build/concatenated only once.
     *
     * But we should just make the sql all final static String's
     */
    private final Map<String, String> mSql = new HashMap<>();

    //region Statements
    /** Options indicating {@link #close()} has been called */
    private boolean mCloseWasCalled = false;
    /** a cache for statements, where they are pre-compiled */
    private SqlStatementManager mStatements;
    /** {@link #getTOCEntryId } */
    @Nullable
    private SynchronizedStatement mGetTOCEntryIdQuery = null;
    /** {@link #getBookTOCEntryAtHighestPositionByBookId}  } */
    @Nullable
    private SynchronizedStatement mGetBookTOCEntryAtHighestPositionByBookIdQuery = null;
    /** {@link #getAuthorIdByName } */
    @Nullable
    private SynchronizedStatement mGetAuthorIdQuery = null;
    /** {@link #purgeAuthors} */
    @Nullable
    private SynchronizedStatement mPurgeBookAuthorsStmt = null;
    /** {@link #purgeAuthors} */
    @Nullable
    private SynchronizedStatement mPurgeAuthorsStmt = null;
    /** {@link #countAuthorBooks} */
    @Nullable
    private SynchronizedStatement mGetAuthorBookCountQuery = null;
    /** {@link #countAuthorAnthologies} */
    @Nullable
    private SynchronizedStatement mGetAuthorAnthologyCountQuery = null;
    /** {@link #getBookUuid} */
    @Nullable
    private SynchronizedStatement mGetBookUuidQuery = null;
    /** {@link #getBookIdFromUuid} */
    @Nullable
    private SynchronizedStatement mGetBookIdFromUuidQuery = null;
    /** {@link #getBookLastUpdateDate} */
    @Nullable
    private SynchronizedStatement mGetBookUpdateDateQuery = null;
    /** {@link #getBookTitle} */
    @Nullable
    private SynchronizedStatement mGetBookTitleQuery = null;
    /** {@link #countBooks} */
    @Nullable
    private SynchronizedStatement mCountBooksQuery = null;
    /** {@link #getIdFromIsbn} */
    @Nullable
    private SynchronizedStatement mGetIdFromIsbn1Query = null;
    /** {@link #getIdFromIsbn} */
    @Nullable
    private SynchronizedStatement mGetIdFromIsbn2Query = null;
    /** {@link #bookExists} */
    @Nullable
    private SynchronizedStatement mCheckBookExistsStmt = null;
    /** {@link #insertBookSeries} */
    @Nullable
    private SynchronizedStatement mDeleteBookSeriesStmt = null;
    /** {@link #setBooksDirtyBySeries} */
    @Nullable
    private SynchronizedStatement mSetBooksDirtyBySeriesStmt = null;
    /** {@link #setBooksDirtyByAuthor} */
    @Nullable
    private SynchronizedStatement mSetBooksDirtyByAuthor1Stmt = null;
    /** {@link #setBooksDirtyByAuthor} */
    @Nullable
    private SynchronizedStatement mSetBooksDirtyByAuthor2Stmt = null;
    /** {@link #setBooksDirtyByBookshelf} */
    @Nullable
    private SynchronizedStatement mSetBooksDirtyByBookshelfStmt = null;
    /** {@link #insertBookSeries} */
    @Nullable
    private SynchronizedStatement mAddBookSeriesStmt = null;
    /** {@link #insertBookAuthors} */
    @Nullable
    private SynchronizedStatement mDeleteBookAuthorsStmt = null;
    /** {@link #insertBookAuthors} */
    @Nullable
    private SynchronizedStatement mAddBookAuthorsStmt = null;
    /** {@link #insertBookBookshelf} */
    @Nullable
    private SynchronizedStatement mDeleteBookBookshelfStmt = null;
    /** {@link #insertBookBookshelf} */
    @Nullable
    private SynchronizedStatement mInsertBookBookshelfStmt = null;
    /** {@link #getBookshelfName} */
    @Nullable
    private SynchronizedStatement mGetBookshelfNameQuery = null;
    /** {@link #getBookshelfId} */
    @Nullable
    private SynchronizedStatement mGetBookshelfIdQuery = null;
    /** {@link #getBookshelfIdByName} */
    @Nullable
    private SynchronizedStatement mGetBookshelfIdByNameQuery = null;
    /** {@link #insertBooklistStyle} */
    @Nullable
    private SynchronizedStatement mInsertBooklistStyleStmt = null;
    /** {@link #updateBooklistStyle} */
    @Nullable
    private SynchronizedStatement mUpdateBooklistStyleStmt = null;
    /** {@link #deleteBooklistStyle} */
    @Nullable
    private SynchronizedStatement mDeleteBooklistStyleStmt = null;
    /** {@link #getSeriesId} */
    @Nullable
    private SynchronizedStatement mGetSeriesIdQuery = null;
    /** {@link #purgeSeries} */
    @Nullable
    private SynchronizedStatement mPurgeBookSeriesStmt = null;
    /** {@link #purgeSeries} */
    @Nullable
    private SynchronizedStatement mPurgeSeriesStmt = null;
    /** {@link #countSeriesBooks} */
    @Nullable
    private SynchronizedStatement mGetSeriesBookCountQuery = null;
    /** {@link #setGoodreadsBookId} */
    @Nullable
    private SynchronizedStatement mSetGoodreadsBookIdStmt = null;
    /** {@link #setGoodreadsSyncDate} */
    @Nullable
    private SynchronizedStatement mSetGoodreadsSyncDateStmt = null;
    /** {@link #insertFts} */
    @Nullable
    private SynchronizedStatement mInsertFtsStmt = null;
    /** {@link #updateFts} */
    @Nullable
    private SynchronizedStatement mUpdateFtsStmt = null;
    /** {@link #deleteFts} */
    @Nullable
    private SynchronizedStatement mDeleteFtsStmt = null;
    //endregion Statements

    /**
     * Constructor - takes the context to allow the database to be opened/created
     *
     * @param context the Context within which to work
     */
    public CatalogueDBAdapter(final @NonNull Context context) {
        mContext = context;
        if (mDbHelper == null) {
            mDbHelper = new DatabaseHelper(mContext, mTrackedCursorFactory, mSynchronizer);
        }

        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            synchronized (mDebugInstanceCount) {
                mDebugInstanceCount++;
                Logger.info(this, "CatDBA instances: " + mDebugInstanceCount);
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
     * double the single quotation marks (standard SQL escape)
     */
    @NonNull
    public static String encodeString(final @NonNull String value) {
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
    public static String cleanupFtsCriterion(final @NonNull String search) {
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

    /**
     * DEBUG only
     */
    @SuppressWarnings("unused")
    private static void debugAddInstance(final @NonNull CatalogueDBAdapter db) {
        mInstances.add(new InstanceRefDebug(db));
    }

    /**
     * DEBUG only
     */
    @SuppressWarnings("unused")
    private static void debugRemoveInstance(final @NonNull CatalogueDBAdapter db) {
        List<InstanceRefDebug> toDelete = new ArrayList<>();
        for (InstanceRefDebug ref : mInstances) {
            CatalogueDBAdapter refDb = ref.get();
            if (refDb == null) {
                Logger.info(CatalogueDBAdapter.class, "<-- **** Missing ref (not closed?) **** vvvvvvv");
                Logger.error(ref.getCreationException());
                Logger.info(CatalogueDBAdapter.class, "--> **** Missing ref (not closed?) **** ^^^^^^^");
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

    /**
     * DEBUG ONLY; used when tracking a bug in android 2.1, but kept because
     * there are still non-fatal anomalies.
     */
    @SuppressWarnings("unused")
    public static void debugPrintReferenceCount(final @Nullable String msg) {
        if (mSyncedDb != null) {
            SynchronizedDb.printRefCount(msg, mSyncedDb.getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing());
        }
    }

    /**
     * DEBUG only
     */
    @SuppressWarnings("unused")
    public static void debugDumpInstances() {
        for (InstanceRefDebug ref : mInstances) {
            if (ref.get() == null) {
                Logger.info(CatalogueDBAdapter.class, "<-- **** Missing ref (not closed?) **** vvvvvvv");
                Logger.error(ref.getCreationException());
                Logger.info(CatalogueDBAdapter.class, "--> **** Missing ref (not closed?) **** ^^^^^^^");
            } else {
                Logger.error(ref.getCreationException());
            }
        }
    }

    /**
     * Really only meant for backup purposes.
     *
     * @return the path to the actual database file
     */
    @NonNull
    public String getPath() {
        return mSyncedDb.getPath();
    }

    public void analyzeDb() {
        try {
            mSyncedDb.execSQL("analyze");
        } catch (Exception e) {
            Logger.error(e, "Analyze failed");
        }
    }

    /**
     * Get the local database.
     * DO NOT CALL THIS UNLESS YOU REALLY NEED TO. DATABASE ACCESS SHOULD GO THROUGH THIS CLASS.
     *
     * @return Database connection
     */
    @NonNull
    public SynchronizedDb getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing() {
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
     * Set the goodreads sync date to the current time
     */
    public void setGoodreadsSyncDate(final long bookId) {
        if (mSetGoodreadsSyncDateStmt == null) {
            mSetGoodreadsSyncDateStmt = mStatements.add("mSetGoodreadsSyncDateStmt",
                    "UPDATE " + TBL_BOOKS +
                            " SET " +
                            DOM_BOOK_GOODREADS_LAST_SYNC_DATE + "=current_timestamp" +
                            " WHERE " + DOM_PK_ID + "=?");
        }
        mSetGoodreadsSyncDateStmt.bindLong(1, bookId);
        mSetGoodreadsSyncDateStmt.executeUpdateDelete();
    }

    /**
     * Open the books database.
     */
    public CatalogueDBAdapter open() throws SQLException {

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

        return this;
    }

    /**
     * Generic function to close the database
     */
    @Override
    public void close() {

        if (!mCloseWasCalled) {
            mCloseWasCalled = true;

            if (mStatements != null) {
                mStatements.close();
            }

            if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
                synchronized (mDebugInstanceCount) {
                    mDebugInstanceCount--;
                    Logger.info(this, "CatDBA instances: " + mDebugInstanceCount);
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
    @NonNull
    public SyncLock startTransaction(boolean isUpdate) {
        return mSyncedDb.beginTransaction(isUpdate);
    }

    /**
     * Used by {@link com.eleybourn.bookcatalogue.backup.CsvImporter}
     */
    public void endTransaction(@NonNull SyncLock txLock) {
        mSyncedDb.endTransaction(txLock);
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

    /**
     * Insert or Update the anthology title in the database {@link DatabaseDefinitions#TBL_ANTHOLOGY}
     * The id for an update, will always be fetched from the database.
     *
     * @param authorId        The author
     * @param title           The title of the anthology story
     * @param publicationDate The original publication year
     *
     * @return insert: the row ID of the newly inserted TOCEntry, or -1 if an error occurred during insert
     * update: the existing TOCEntry id
     *
     * @throws DBExceptions.UpdateException if the update failed
     */
    private long insertOrUpdateTOCEntry(final long authorId,
                                        final @NonNull String title,
                                        final @NonNull String publicationDate)
            throws DBExceptions.UpdateException {

        if (title.isEmpty()) {
            return -1L;
        }

        // do NOT update the title/author. Those are the primary key, so only for inserts
        ContentValues cv = new ContentValues();
        cv.put(DOM_FIRST_PUBLICATION.name, publicationDate);

        long tocEntryId = getTOCEntryId(authorId, title);

        if (tocEntryId == 0) {
            cv.put(DOM_FK_AUTHOR_ID.name, authorId);
            // standardize the title for new entries
            cv.put(DOM_TITLE.name, preprocessTitle(title));
            tocEntryId = mSyncedDb.insert(TBL_ANTHOLOGY.getName(), null, cv);

            if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
                Logger.info(this, "inserted TOCEntry: " + tocEntryId + ", authorId=" + authorId + ", " + title);
            }
        } else {
            int rowsAffected = mSyncedDb.update(TBL_ANTHOLOGY.getName(), cv,
                    DOM_PK_ID + "=?", new String[]{Long.toString(tocEntryId)});
            if (rowsAffected != 1) {
                throw new DBExceptions.UpdateException();
            }

            if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
                Logger.info(this, "updated TOCEntry: " + tocEntryId + ", authorId=" + authorId + ", " + title);
            }
        }
        return tocEntryId;
    }

    /**
     * Create an anthology title for a book.
     * It will always be added to the END OF THE LIST (using the position column)
     *
     * @return insert: the row ID of the newly inserted BookTOCEntry, or -1 if an error occurred during insert
     */
    private long insertBookTOCEntry(final long anthologyId, final long bookId,
                                    final boolean dirtyBookIfNecessary) {

        ContentValues cv = new ContentValues();
        cv.put(DOM_FK_ANTHOLOGY_ID.name, anthologyId);
        cv.put(DOM_FK_BOOK_ID.name, bookId);
        cv.put(DOM_BOOK_TOC_ENTRY_POSITION.name, getBookTOCEntryAtHighestPositionByBookId(bookId) + 1);
        long newId = mSyncedDb.insert(TBL_BOOK_TOC_ENTRIES.getName(), null, cv);

        if (newId > 0 && dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        return newId;
    }

    /**
     * Delete ALL the anthology records for a given book {@link DatabaseDefinitions#TBL_BOOK_TOC_ENTRIES}, if any
     * But does NOT delete the Anthology Title itself {@link DatabaseDefinitions#TBL_ANTHOLOGY}
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    private int deleteTOCEntriesByBookId(final long bookId,
                                         final boolean dirtyBookIfNecessary) {

        int rowsAffected = mSyncedDb.delete(TBL_BOOK_TOC_ENTRIES.getName(),
                DOM_FK_BOOK_ID + "=?", new String[]{Long.toString(bookId)});
        if (rowsAffected > 0) {
            if (dirtyBookIfNecessary) {
                setBookDirty(bookId);
            }
        }
        return rowsAffected;
    }

    /**
     * Return the TOCEntry ID for a given author/title (note that publication year is NOT used)
     * The title will be checked for as-is 'OR' as {@link #preprocessTitle}.
     *
     * @param authorId id of author
     * @param title    title
     *
     * @return the id of the TOCEntry entry, or 0 if not found
     */
    public long getTOCEntryId(final long authorId, final @NonNull String title) {
        if (mGetTOCEntryIdQuery == null) {
            mGetTOCEntryIdQuery = mStatements.add("mGetTOCEntryIdQuery",
                    "SELECT Coalesce( Min(" + DOM_PK_ID + "),0) FROM " + TBL_ANTHOLOGY +
                            " WHERE " + DOM_FK_AUTHOR_ID + "=? AND (" + DOM_TITLE + "=? OR " + DOM_TITLE + "=?)" +
                            COLLATION);
        }
        mGetTOCEntryIdQuery.bindLong(1, authorId);
        mGetTOCEntryIdQuery.bindString(2, title);
        mGetTOCEntryIdQuery.bindString(3, preprocessTitle(title));
        return mGetTOCEntryIdQuery.simpleQueryForLongOrZero();
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
    private long getBookTOCEntryAtHighestPositionByBookId(final long bookId) {
        if (mGetBookTOCEntryAtHighestPositionByBookIdQuery == null) {
            mGetBookTOCEntryAtHighestPositionByBookIdQuery = mStatements.add("mGetBookTOCEntryAtHighestPositionByBookIdQuery",
                    "SELECT max(" + DOM_BOOK_TOC_ENTRY_POSITION + ") FROM " + TBL_BOOK_TOC_ENTRIES +
                            " WHERE " + DOM_FK_BOOK_ID + "=?");
        }

        mGetBookTOCEntryAtHighestPositionByBookIdQuery.bindLong(1, bookId);
        return mGetBookTOCEntryAtHighestPositionByBookIdQuery.simpleQueryForLongOrZero();
    }

    /**
     * Return all the {@link TOCEntry} for the given {@link Author}
     *
     * @param author to retrieve
     *
     * @return List of {@link TOCEntry} for this {@link Author}
     */
    @NonNull
    public ArrayList<TOCEntry> getTOCEntriesByAuthor(final Author author) {
        ArrayList<TOCEntry> list = new ArrayList<>();
        try (Cursor cursor = this.fetchTOCEntriesByAuthorId(author.id)) {
            if (cursor.getCount() == 0) {
                return list;
            }

            final int titleCol = cursor.getColumnIndex(DOM_TITLE.name);
            final int pubDateCol = cursor.getColumnIndex(DOM_FIRST_PUBLICATION.name);

            while (cursor.moveToNext()) {
                TOCEntry title = new TOCEntry(author, cursor.getString(titleCol), cursor.getString(pubDateCol));
                list.add(title);
            }
        }
        return list;
    }

    /**
     * Return a {@link Cursor} over all the {@link TOCEntry} and their {@link Author}
     * for the given {@link Book} id
     *
     * @param bookId to retrieve
     *
     * @return {@link Cursor} containing all records ordered ascending by position, if any
     */
    @NonNull
    private Cursor fetchTOCEntriesByBookId(final long bookId) {
        String sql = mSql.get("fetchTOCEntriesByBookId");
        if (sql == null) {
            sql = "SELECT " +
                    TBL_ANTHOLOGY.dotAs(DOM_PK_ID) + "," +
                    TBL_ANTHOLOGY.dotAs(DOM_TITLE) + "," +
                    TBL_ANTHOLOGY.dotAs(DOM_FK_AUTHOR_ID) + "," +
                    TBL_ANTHOLOGY.dotAs(DOM_FIRST_PUBLICATION) + "," +

                    //TBL_BOOK_TOC_ENTRIES.dotAs(DOM_FK_BOOK_ID) + "," +
                    //TBL_BOOK_TOC_ENTRIES.dotAs(DOM_BOOK_TOC_ENTRY_POSITION) + "," +

                    TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME) + "," +
                    TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES) + "," +
                    TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " || ', ' || " + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES, DOM_AUTHOR_NAME) +

                    " FROM " + TBL_ANTHOLOGY.ref() + TBL_ANTHOLOGY.join(TBL_BOOK_TOC_ENTRIES) + TBL_ANTHOLOGY.join(TBL_AUTHORS) +

                    " WHERE " + TBL_BOOK_TOC_ENTRIES.dot(DOM_FK_BOOK_ID) + "=?" +

                    " ORDER BY " + TBL_BOOK_TOC_ENTRIES.dot(DOM_BOOK_TOC_ENTRY_POSITION);
            mSql.put("fetchTOCEntriesByBookId", sql);
        }
        return mSyncedDb.rawQuery(sql, new String[]{Long.toString(bookId)});
    }

    /**
     * Return a {@link Cursor} over all the {@link TOCEntry}
     * for the given {@link Author} id
     *
     * @param authorId to retrieve
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    private Cursor fetchTOCEntriesByAuthorId(final long authorId) {
        String sql = mSql.get("fetchTOCEntriesByAuthorId");
        if (sql == null) {
            sql = "SELECT " +
                    TBL_ANTHOLOGY.dotAs(DOM_PK_ID) + "," +
                    TBL_ANTHOLOGY.dotAs(DOM_TITLE) + "," +
                    TBL_ANTHOLOGY.dotAs(DOM_FIRST_PUBLICATION) +
                    " FROM " + TBL_ANTHOLOGY.ref() +
                    " WHERE " + TBL_ANTHOLOGY.dot(DOM_FK_AUTHOR_ID) + "=?" +
                    " ORDER BY lower(" + TBL_ANTHOLOGY.dot(DOM_TITLE) + ")" + COLLATION;
            mSql.put("fetchTOCEntriesByAuthorId", sql);
        }
        return mSyncedDb.rawQuery(sql, new String[]{Long.toString(authorId)});
    }

    /**
     * Create a new author
     *
     * @return the row ID of the newly inserted Author, or -1 if an error occurred
     */
    private long insertAuthor(final @NonNull Author author) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_AUTHOR_FAMILY_NAME.name, author.familyName);
        cv.put(DOM_AUTHOR_GIVEN_NAMES.name, author.givenNames);
        return mSyncedDb.insert(TBL_AUTHORS.getName(), null, cv);
    }

    /**
     * @return the number of rows affected, should be 1
     *
     * @throws DBExceptions.UpdateException() on failure
     */
    private int updateAuthor(final @NonNull Author author) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_AUTHOR_FAMILY_NAME.name, author.familyName);
        cv.put(DOM_AUTHOR_GIVEN_NAMES.name, author.givenNames);
        int rowsAffected = mSyncedDb.update(TBL_AUTHORS.getName(), cv, DOM_PK_ID + "=?", new String[]{Long.toString(author.id)});
        if (rowsAffected != 1) {
            throw new DBExceptions.UpdateException();
        }
        return rowsAffected;
    }

    /**
     * Add or update the passed author, depending whether author.id == 0.
     *
     * @return insert: the row ID of the newly inserted Author, or -1 if an error occurred during insert
     * update: the existing Author id
     *
     * @throws DBExceptions.UpdateException if the update failed
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertOrUpdateAuthor(final @NonNull /* out */ Author author) {
        if (author.id != 0) {
            Author previous = this.getAuthor(author.id);
            if (author.equals(previous)) {
                return author.id;
            }
            if (updateAuthor(author) == 1) {
                setBooksDirtyByAuthor(author.id);
            }
        } else {
            author.id = insertAuthor(author);
        }
        return author.id;
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
                    " FROM " + TBL_AUTHORS + " WHERE " + DOM_PK_ID + "=?";
            mSql.put("getAuthor", sql);
        }

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{Long.toString(id)})) {
            return (cursor.moveToFirst() ? new Author(id, cursor.getString(0), cursor.getString(1)) : null);
        }
    }

    /**
     * @return author id, or 0 (e.g. 'new') when not found
     */
    public long getAuthorIdByName(final @NonNull String familyName, final @NonNull String givenNames) {
        if (mGetAuthorIdQuery == null) {
            mGetAuthorIdQuery = mStatements.add("mGetAuthorIdQuery",
                    "SELECT " +
                            DOM_PK_ID +
                            " FROM " + TBL_AUTHORS +
                            " WHERE lower(" + DOM_AUTHOR_FAMILY_NAME + ") = lower(?) " + COLLATION +
                            " AND lower(" + DOM_AUTHOR_GIVEN_NAMES + ") = lower(?)" + COLLATION);
        }

        mGetAuthorIdQuery.bindString(1, familyName);
        mGetAuthorIdQuery.bindString(2, givenNames);
        return mGetAuthorIdQuery.simpleQueryForLongOrZero();
    }

    /**
     * Find an Author based on name. If not found, insert the Author.
     *
     * @param author to find
     *
     * @return the row ID of the Author (new of existing), or -1 if an insert error occurred
     */
    private long getOrInsertAuthorId(final @NonNull Author author) {
        long id = getAuthorIdByName(author.familyName, author.givenNames);
        return id == 0 ? insertAuthor(author) : id;
    }

    /**
     * Refresh the passed author from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the author.
     *
     * Will NOT insert a new Author if not found.
     */
    public void refreshAuthor(final @NonNull Author /* out */ author) {
        if (author.id == 0) {
            // It wasn't a known author; see if it is now. If so, update ID.
            long id = getAuthorIdByName(author.familyName, author.givenNames);
            // If we have a match, just update the object
            if (id != 0) {
                author.id = id;
            }
        } else {
            // It was a known author, see if it still is and fetch possibly updated fields.
            Author dbAuthor = this.getAuthor(author.id);
            if (dbAuthor != null) {
                // id stays the same obviously.
                author.familyName = dbAuthor.familyName;
                author.givenNames = dbAuthor.givenNames;
            } else {
                // Author not found?, set the author as 'new'
                author.id = 0;
            }
        }
    }

    /**
     * @throws DBExceptions.UpdateException   on failure to insertOrUpdateAuthor(to) or on the global replace itself
     * @throws DBExceptions.NotFoundException on failure to find old Author
     */
    public void globalReplaceAuthor(final @NonNull Author from, final @NonNull Author to) {
        // Create or update the new author
        if (to.id == 0) {
            // try to find
            long id = getAuthorIdByName(to.familyName, to.givenNames);
            // If we have a match, just update the object and done
            if (id != 0) {
                to.id = id;
            } else {
                insertOrUpdateAuthor(to);
            }
        } else {
            insertOrUpdateAuthor(to);
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

        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            setBooksDirtyByAuthor(from.id);

            // First handle anthologies; they have a single author and are easy
            mSyncedDb.execSQL("UPDATE " + TBL_ANTHOLOGY +
                    " SET " +
                    DOM_FK_AUTHOR_ID + "=" + to.id +
                    " WHERE " + DOM_FK_AUTHOR_ID + "=" + from.id);

            globalReplacePositionedBookItem(TBL_BOOK_AUTHOR, DOM_FK_AUTHOR_ID.name, DOM_AUTHOR_POSITION.name, from.id, to.id);

            mSyncedDb.setTransactionSuccessful();
        } catch (Exception e) {
            Logger.error(e);
            throw new DBExceptions.UpdateException(e);
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
    }

    /**
     * @return a complete list of author names from the database; used for AutoComplete.
     */
    @NonNull
    public ArrayList<String> getAuthorsFormattedName() {
        String sql = mSql.get("fetchAuthors");
        if (sql == null) {
            sql = "SELECT DISTINCT " +
                    SQL_FIELDS_AUTHOR_FORMATTED +
                    " FROM " + TBL_AUTHORS.ref() +
                    " ORDER BY lower(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION + ", lower(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION;
            mSql.put("fetchAuthors", sql);
        }

        ArrayList<String> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{})) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DOM_AUTHOR_FORMATTED.name));
                list.add(name);
            }
            return list;
        }
    }

    /**
     * When we have no more books or TOCEntry's of a given Author, purge him.
     * {@link DatabaseDefinitions#TBL_BOOK_AUTHOR}
     * {@link DatabaseDefinitions#TBL_AUTHORS}
     */
    public void purgeAuthors() {
        if (mPurgeBookAuthorsStmt == null) {
            mPurgeBookAuthorsStmt = mStatements.add("mPurgeBookAuthorsStmt",
                    "DELETE FROM " + TBL_BOOK_AUTHOR +
                            " WHERE " + DOM_FK_BOOK_ID + " NOT IN" +
                            " (SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKS + ") ");
        }
        try {
            mPurgeBookAuthorsStmt.executeUpdateDelete();
        } catch (Exception e) {
            Logger.error(e, "Failed to purge " + TBL_BOOK_AUTHOR);
        }

        if (mPurgeAuthorsStmt == null) {
            mPurgeAuthorsStmt = mStatements.add("mPurgeAuthorsStmt",
                    "DELETE FROM " + TBL_AUTHORS +
                            " WHERE " + DOM_PK_ID + " NOT IN" +
                            " (SELECT DISTINCT " + DOM_FK_AUTHOR_ID + " FROM " + TBL_BOOK_AUTHOR + ")" +
                            " AND " + DOM_PK_ID + " NOT IN" +
                            " (SELECT DISTINCT " + DOM_FK_AUTHOR_ID + " FROM " + TBL_ANTHOLOGY + ")");
        }
        try {
            mPurgeAuthorsStmt.executeUpdateDelete();
        } catch (Exception e) {
            Logger.error(e, "Failed to purge " + TBL_AUTHORS);
        }
    }

    /**
     * Return a {@link Cursor} over the list of all {@link Author}
     * for the given {@link Book} id
     *
     * @param bookId to retrieve
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    private Cursor fetchAuthorsByBookId(final long bookId) {
        String sql = mSql.get("fetchAuthorsByBookId");
        if (sql == null) {
            sql = "SELECT DISTINCT " +
                    TBL_AUTHORS.dotAs(DOM_PK_ID) + "," +
                    TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME) + "," +
                    TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES) + "," +
                    SQL_FIELDS_AUTHOR_FORMATTED + "," +

                    TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_POSITION) +

                    " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS) +
                    " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK_ID) + "=?" +
                    " ORDER BY " +
                    TBL_BOOK_AUTHOR.dot(DOM_AUTHOR_POSITION) + " ASC," +
                    " lower(" + DOM_AUTHOR_FAMILY_NAME + ") " + COLLATION + " ASC," +
                    " lower(" + DOM_AUTHOR_GIVEN_NAMES + ") " + COLLATION + " ASC";
            mSql.put("fetchAuthorsByBookId", sql);
        }
        return mSyncedDb.rawQuery(sql, new String[]{Long.toString(bookId)});
    }

    /**
     * @param author to retrieve
     *
     * @return the number of {@link Book} this {@link Author} has
     */
    public long countAuthorBooks(final @NonNull Author author) {
        if (author.id == 0) {
            author.id = getAuthorIdByName(author.familyName, author.givenNames);
        }

        if (author.id == 0) {
            return 0;
        }

        if (mGetAuthorBookCountQuery == null) {
            mGetAuthorBookCountQuery = mStatements.add("mGetAuthorBookCountQuery",
                    "SELECT COUNT(" + DOM_FK_BOOK_ID + ") FROM " + TBL_BOOK_AUTHOR + " WHERE " + DOM_FK_AUTHOR_ID + "=?");
        }
        // Be cautious
        synchronized (mGetAuthorBookCountQuery) {
            mGetAuthorBookCountQuery.bindLong(1, author.id);
            return mGetAuthorBookCountQuery.count();
        }
    }

    /**
     * @param author to retrieve
     *
     * @return the number of {@link TOCEntry} this {@link Author} has
     */
    public long countAuthorAnthologies(final @NonNull Author author) {
        if (author.id == 0) {
            author.id = getAuthorIdByName(author.familyName, author.givenNames);
        }
        if (author.id == 0) {
            return 0;
        }

        if (mGetAuthorAnthologyCountQuery == null) {
            mGetAuthorAnthologyCountQuery = mStatements.add("mGetAuthorAnthologyCountQuery",
                    "SELECT COUNT(" + DOM_PK_ID + ") FROM " + TBL_ANTHOLOGY + " WHERE " + DOM_FK_AUTHOR_ID + "=?");
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
     * @param isNew indicates if the book is new
     * @param book  A collection with the columns to be set. May contain extra data.
     */
    private void preprocessBook(final boolean isNew, final @NonNull Book book) {

        // Handle AUTHOR
        // If present, get the author ID from the author name (it may have changed with a name change)
        if (book.containsKey(UniqueId.KEY_AUTHOR_FORMATTED)) {
            Author author = Author.toAuthor(book.getString(UniqueId.KEY_AUTHOR_FORMATTED));
            book.putLong(UniqueId.KEY_AUTHOR_ID, getOrInsertAuthorId(author));
        } else {
            if (book.containsKey(UniqueId.KEY_AUTHOR_FAMILY_NAME)) {
                String family = book.getString(UniqueId.KEY_AUTHOR_FAMILY_NAME);
                String given;
                if (book.containsKey(UniqueId.KEY_AUTHOR_GIVEN_NAMES)) {
                    given = book.getString(UniqueId.KEY_AUTHOR_GIVEN_NAMES);
                } else {
                    given = "";
                }
                book.putLong(UniqueId.KEY_AUTHOR_ID, getOrInsertAuthorId(new Author(family, given)));
            }
        }

        // Handle TITLE; but only for new books
        if (isNew && book.containsKey(UniqueId.KEY_TITLE)) {
            book.putString(UniqueId.KEY_TITLE, preprocessTitle(book.getString(UniqueId.KEY_TITLE)));
        }

        // Handle ANTHOLOGY_BITMASK only, no handling of actual titles here
        ArrayList<TOCEntry> TOCEntries = book.getTOC();
        if (TOCEntries.size() > 0) {
            // definitively an anthology, overrule whatever the KEY_BOOK_ANTHOLOGY_BITMASK was.
            int type = DOM_IS_ANTHOLOGY;
            if (TOCEntry.isSingleAuthor(TOCEntries)) {
                type = type ^ DOM_BOOK_WITH_MULTIPLE_AUTHORS;
            }
            book.putInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, type);
        }

        //ENHANCE: handle price fields for legacy embedded currencies. Perhaps moving those to currency fields ?

        // Handle currencies making sure they are uppercase
        if (book.containsKey(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY)) {
            book.putString(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY,
                    book.getString(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY).toUpperCase());
        }
        if (book.containsKey(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY)) {
            book.putString(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY,
                    book.getString(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY).toUpperCase());
        }

        // Remove blank/null fields that have default values defined in the database
        // or which should never be blank.
        for (String name : new String[]{
                UniqueId.KEY_BOOK_UUID,
                UniqueId.KEY_BOOK_RATING,
                UniqueId.KEY_BOOK_READ,
                UniqueId.KEY_BOOK_SIGNED,
                UniqueId.KEY_BOOK_GOODREADS_LAST_SYNC_DATE,
                UniqueId.KEY_LAST_UPDATE_DATE,

                UniqueId.KEY_BOOK_PRICE_LISTED,
                UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY,
                UniqueId.KEY_BOOK_PRICE_PAID,
                UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY,
                UniqueId.KEY_BOOK_DATE_ADDED,
                UniqueId.KEY_BOOK_DATE_ACQUIRED,

                UniqueId.KEY_BOOK_EDITION_BITMASK,
        }) {
            if (book.containsKey(name)) {
                Object o = book.get(name);
                // Need to allow for the possibility the value is not
                // a string, in which case getString() would return a NULL.
                if (o == null || o.toString().isEmpty()) {
                    book.remove(name);
                }
            }
        }
    }

    /**
     * Move "The, A, An" etc... to the end of the string
     *
     * @param title to format
     *
     * @return formatted title
     */
    private String preprocessTitle(final @NonNull String title) {

        StringBuilder newTitle = new StringBuilder();
        String[] title_words = title.split(" ");
        try {
            if (title_words[0].matches(mContext.getString(R.string.title_reorder))) {
                for (int i = 1; i < title_words.length; i++) {
                    if (i != 1) {
                        newTitle.append(" ");
                    }
                    newTitle.append(title_words[i]);
                }
                newTitle.append(", ").append(title_words[0]);
                return newTitle.toString();
            }
        } catch (Exception ignore) {
            //do nothing. Title stays the same
        }
        return title;
    }

    /**
     * Utility routine to return the book uuid based on the id.
     *
     * @param bookId of the book
     *
     * @return the book uuid
     *
     * @throws SQLiteDoneException if zero rows found
     */
    @NonNull
    public String getBookUuid(final long bookId) throws SQLiteDoneException {
        if (mGetBookUuidQuery == null) {
            mGetBookUuidQuery = mStatements.add("mGetBookUuidQuery",
                    "SELECT " +
                            DOM_BOOK_UUID +
                            " FROM " + TBL_BOOKS +
                            " WHERE " + DOM_PK_ID + "=?");
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
     * @param uuid of book
     *
     * @return ID of the book, or 0 'new' if not found
     */
    public long getBookIdFromUuid(final @NonNull String uuid) {
        if (mGetBookIdFromUuidQuery == null) {
            mGetBookIdFromUuidQuery = mStatements.add("mGetBookIdFromUuidQuery",
                    "SELECT " +
                            DOM_PK_ID +
                            " FROM " + TBL_BOOKS +
                            " WHERE " + DOM_BOOK_UUID + "=?");
        }

        mGetBookIdFromUuidQuery.bindString(1, uuid);
        return mGetBookIdFromUuidQuery.simpleQueryForLongOrZero();
    }

    /***
     * @param bookId of the book
     *
     * @return the last update date as a standard sql date string
     */
    @Nullable
    public String getBookLastUpdateDate(final long bookId) {
        if (mGetBookUpdateDateQuery == null) {
            mGetBookUpdateDateQuery = mStatements.add("mGetBookUpdateDateQuery",
                    "SELECT " +
                            DOM_LAST_UPDATE_DATE +
                            " FROM " + TBL_BOOKS +
                            " WHERE " + DOM_PK_ID + "=?");
        }
        // Be cautious
        synchronized (mGetBookUpdateDateQuery) {
            mGetBookUpdateDateQuery.bindLong(1, bookId);
            return mGetBookUpdateDateQuery.simpleQueryForStringOrNull();
        }
    }

    /**
     * @param bookId of the book
     *
     * @return the title, or null when not found
     */
    @Nullable
    public String getBookTitle(final long bookId) {
        if (mGetBookTitleQuery == null) {
            mGetBookTitleQuery = mStatements.add("mGetBookTitleQuery",
                    "SELECT " +
                            DOM_TITLE +
                            " FROM " + TBL_BOOKS +
                            " WHERE " + DOM_PK_ID + "=?");
        }
        // Be cautious
        synchronized (mGetBookTitleQuery) {
            mGetBookTitleQuery.bindLong(1, bookId);
            return mGetBookTitleQuery.simpleQueryForStringOrNull();
        }
    }

    /**
     * @param bookId of the book
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteBook(final long bookId) {
        String uuid = null;
        try {
            uuid = this.getBookUuid(bookId);
        } catch (Exception e) {
            Logger.error(e, "Failed to get book UUID");
        }
        int rowsAffected = 0;
        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {

            rowsAffected = mSyncedDb.delete(TBL_BOOKS.getName(), DOM_PK_ID + "=?", new String[]{Long.toString(bookId)});
            if (rowsAffected > 0) {
                purgeAuthors();
                purgeSeries();
                deleteFts(bookId);
                deleteThumbnail(uuid);
            }
            mSyncedDb.setTransactionSuccessful();
        } catch (Exception e) {
            Logger.error(e, "Failed to delete book");
        } finally {
            mSyncedDb.endTransaction(txLock);
        }

        return rowsAffected;
    }

    private void deleteThumbnail(final @Nullable String uuid) {
        if (uuid != null) {
            // remove from file system
            try {
                //TODO: understand the logic of the loop....
                File coverFile = StorageUtils.getCoverFile(uuid);
                while (coverFile.exists()) {
                    StorageUtils.deleteFile(coverFile);
                    coverFile = StorageUtils.getCoverFile(uuid);
                }
            } catch (Exception e) {
                Logger.error(e, "Failed to delete cover thumbnail");
            }

            // remove from cache
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
     * @param book A collection with the columns to be set. May contain extra data.
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insertBook(final @NonNull Book book) {
        return insertBookWithId(0, book);
    }

    /**
     * Create a new book using the details provided.
     *
     * Transaction: participate, or run in new.
     *
     * @param bookId of the book
     *               zero: a new book
     *               non-zero: will overwrite the small autoIncrement, normally only an Import should use this
     * @param book   A collection with the columns to be set. May contain extra data.
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insertBookWithId(final long bookId, final @NonNull Book book) {

        SyncLock txLock = null;
        if (!mSyncedDb.inTransaction()) {
            txLock = mSyncedDb.beginTransaction(true);
        }

        try {
            if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
                Logger.info(this, book.getRawData().toString());
            }
            // Cleanup fields (author, series, title and remove blank fields for which we have defaults)
            preprocessBook(bookId == 0, book);

            /* Set defaults if not present in book
             *
             * TODO We may want to provide default values for these fields:
             * KEY_BOOK_RATING, KEY_BOOK_READ, KEY_NOTES, KEY_BOOK_LOCATION,
             * KEY_BOOK_READ_START, KEY_BOOK_READ_END, KEY_BOOK_SIGNED
             */
            if (!book.containsKey(UniqueId.KEY_BOOK_DATE_ADDED)) {
                book.putString(UniqueId.KEY_BOOK_DATE_ADDED, DateUtils.utcSqlDateTimeForToday());
            }

            // Make sure we have an author
            List<Author> authors = book.getAuthorList();
            if (authors.size() == 0) {
                throw new IllegalArgumentException();
            }

            ContentValues cv = filterValues(TBL_BOOKS.getName(), book);
            if (bookId > 0) {
                cv.put(DOM_PK_ID.name, bookId);
            }

            if (!cv.containsKey(DOM_LAST_UPDATE_DATE.name)) {
                cv.put(DOM_LAST_UPDATE_DATE.name, DateUtils.utcSqlDateTimeForToday());
            }

            long newBookId = mSyncedDb.insert(TBL_BOOKS.getName(), null, cv);
            if (newBookId > 0) {
                insertBookDependents(newBookId, book);
                insertFts(newBookId);
                // it's an insert, onTextFieldEditorSave only if we inserted.
                if (txLock != null) {
                    mSyncedDb.setTransactionSuccessful();
                }
            }
            return newBookId;
        } catch (Exception e) {
            Logger.error(e, "Error creating book from\n" + book);
            return -1L;
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * Transaction: participate, or run in new.
     *
     * @param bookId of the book
     * @param book   A collection with the columns to be set. May contain extra data.
     * @param flags  See {@link #BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT} an others for flag definitions
     *
     * @return the number of rows affected, should be 1 for onTextFieldEditorSave.
     *
     * @throws DBExceptions.UpdateException on failure
     */
    public int updateBook(final long bookId, final @NonNull Book book, final int flags) {

        SyncLock txLock = null;
        if (!mSyncedDb.inTransaction()) {
            txLock = mSyncedDb.beginTransaction(true);
        }

        try {
            if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
                Logger.info(this, book.getRawData().toString());
            }

            // Cleanup fields (author, series, title, 'sameAuthor' if anthology, and remove blank fields for which we have defaults)
            preprocessBook(bookId == 0, book);

            ContentValues cv = filterValues(TBL_BOOKS.getName(), book);

            // Disallow UUID updates
            if (cv.containsKey(DOM_BOOK_UUID.name)) {
                cv.remove(DOM_BOOK_UUID.name);
            }

            // set the DOM_LAST_UPDATE_DATE to 'now' if we're allowed, or if it's not present already.
            if ((flags & BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT) == 0
                    || !cv.containsKey(DOM_LAST_UPDATE_DATE.name)) {
                cv.put(DOM_LAST_UPDATE_DATE.name, DateUtils.utcSqlDateTimeForToday());
            }

            // go !
            int rowsAffected = mSyncedDb.update(TBL_BOOKS.getName(), cv, DOM_PK_ID + "=?", new String[]{Long.toString(bookId)});
            insertBookDependents(bookId, book);

            // Only really skip the purge if a batch update of multiple books is being done.
            if ((flags & BOOK_UPDATE_SKIP_PURGE_REFERENCES) == 0) {
                purgeAuthors();
                purgeSeries();
            }

            updateFts(bookId);

            if (txLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
            return rowsAffected;
        } catch (Exception e) {
            Logger.error(e);
            throw new DBExceptions.UpdateException("Error updating book from " + book + ": " + e.getLocalizedMessage(), e);
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * shared between book insert & update.
     * All of these will first delete the entries in the Book-[tableX] table,
     * and then insert the all rows again
     *
     * @param bookId of the book
     * @param book   A collection with the columns to be set. May contain extra data.
     */
    private void insertBookDependents(final long bookId, final @NonNull Book book) {

        if (book.containsKey(UniqueId.BKEY_BOOKSHELF_ARRAY)) {
            insertBookBookshelf(bookId, book.getBookshelfList(), false);
        }

        if (book.containsKey(UniqueId.BKEY_AUTHOR_ARRAY)) {
            insertBookAuthors(bookId, book.getAuthorList(), false);
        }

        if (book.containsKey(UniqueId.BKEY_SERIES_ARRAY)) {
            insertBookSeries(bookId, book.getSeriesList(), false);
        }

        if (book.containsKey(UniqueId.BKEY_TOC_TITLES_ARRAY)) {
            insertOrUpdateTOC(bookId, book.getTOC(), false);
        }

        if (book.containsKey(UniqueId.KEY_LOAN_LOANED_TO) && !book.getString(UniqueId.KEY_LOAN_LOANED_TO).isEmpty()) {
            deleteLoan(bookId, false);
            insertLoan(book, false); // don't care about insert failures really
        }
    }

    /**
     * @return The total number of books
     */
    public int countBooks() {
        if (mCountBooksQuery == null) {
            mCountBooksQuery = mStatements.add("mGetAuthorBookCountQuery",
                    "SELECT COUNT(*) FROM " + TBL_BOOKS);
        }
        return (int) mCountBooksQuery.count();
    }

    /**
     * Return a {@link Cursor} with a single column, the UUID of all {@link Book}
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    public Cursor fetchBookUuidList() {
        return mSyncedDb.rawQuery(
                "SELECT " +
                        DOM_BOOK_UUID +
                        " FROM " + TBL_BOOKS, new String[]{});
    }

    /**
     * @param isbn to search for
     *
     * @return book id, or 0 if not found
     */
    public long getIdFromIsbn(final @NonNull String isbn, final boolean checkBothIsbn10and13) {
        SynchronizedStatement stmt;
        if (checkBothIsbn10and13 && IsbnUtils.isValid(isbn)) {
            if (mGetIdFromIsbn2Query == null) {
                mGetIdFromIsbn2Query = mStatements.add("mGetIdFromIsbn2Query",
                        "SELECT" +
                                " Coalesce(max(" + DOM_PK_ID + "), 0)" +
                                " FROM " + TBL_BOOKS +
                                " WHERE lower(" + DOM_BOOK_ISBN + ") IN (lower(?), lower(?))");
            }
            stmt = mGetIdFromIsbn2Query;
            stmt.bindString(2, IsbnUtils.isbn2isbn(isbn));
        } else {
            if (mGetIdFromIsbn1Query == null) {
                mGetIdFromIsbn1Query = mStatements.add("mGetIdFromIsbn1Query",
                        "SELECT" +
                                " Coalesce(max(" + DOM_PK_ID + "), 0)" +
                                " FROM " + TBL_BOOKS +
                                " WHERE lower(" + DOM_BOOK_ISBN + ") = lower(?)");
            }
            stmt = mGetIdFromIsbn1Query;
        }
        stmt.bindString(1, isbn);
        return stmt.simpleQueryForLongOrZero();
    }

    /**
     * Check that a book with the passed ID exists
     *
     * @param bookId of the book
     *
     * @return <tt>true</tt>if exists
     */
    public boolean bookExists(final long bookId) {
        if (mCheckBookExistsStmt == null) {
            mCheckBookExistsStmt = mStatements.add("mCheckBookExistsStmt",
                    "SELECT " +
                            DOM_PK_ID +
                            " FROM " + TBL_BOOKS +
                            " WHERE " + DOM_PK_ID + "=?");
        }
        mCheckBookExistsStmt.bindLong(1, bookId);
        return (0 != mCheckBookExistsStmt.simpleQueryForLongOrZero());
    }

    /**
     * Set a book as in need of backup if any ancillary data has changed.
     *
     * @param bookId of the book
     */
    private void setBookDirty(final long bookId) {
        mSyncedDb.execSQL("UPDATE " + TBL_BOOKS +
                " SET " +
                DOM_LAST_UPDATE_DATE + "=current_timestamp" +
                " WHERE " + DOM_PK_ID + "=" + bookId);
    }

    /**
     * Set all books referencing a given author as dirty.
     */
    private void setBooksDirtyByAuthor(final long authorId) {
        // set all related books based on anthology author as dirty
        if (mSetBooksDirtyByAuthor1Stmt == null) {
            mSetBooksDirtyByAuthor1Stmt = mStatements.add("mSetBooksDirtyByAuthor1Stmt",
                    "UPDATE " + TBL_BOOKS +
                            " SET " +
                            DOM_LAST_UPDATE_DATE + "=current_timestamp" +
                            " WHERE" +
                            " Exists(SELECT * FROM " + TBL_ANTHOLOGY.ref() + TBL_ANTHOLOGY.join(TBL_BOOKS) +
                            " WHERE " + TBL_ANTHOLOGY.dot(DOM_FK_AUTHOR_ID) + "=?)");
        }
        mSetBooksDirtyByAuthor1Stmt.bindLong(1, authorId);
        mSetBooksDirtyByAuthor1Stmt.executeUpdateDelete();


        // set all related books based on series as dirty
        if (mSetBooksDirtyByAuthor2Stmt == null) {
            mSetBooksDirtyByAuthor2Stmt = mStatements.add("mSetBooksDirtyByAuthor2Stmt",
                    "UPDATE " + TBL_BOOKS +
                            " SET " +
                            DOM_LAST_UPDATE_DATE + "=current_timestamp" +
                            " WHERE" +
                            " Exists(SELECT * FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_BOOKS) +
                            " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR_ID) + "=?)");
        }
        mSetBooksDirtyByAuthor2Stmt.bindLong(1, authorId);
        mSetBooksDirtyByAuthor2Stmt.executeUpdateDelete();
    }

    /**
     * Set all books referencing a given series as dirty.
     */
    private void setBooksDirtyBySeries(final long seriesId) {
        if (mSetBooksDirtyBySeriesStmt == null) {
            mSetBooksDirtyBySeriesStmt = mStatements.add("mSetBooksDirtyBySeriesStmt",
                    "UPDATE " + TBL_BOOKS +
                            " SET " +
                            DOM_LAST_UPDATE_DATE + "=current_timestamp" +
                            " WHERE" +
                            " Exists(SELECT * FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_BOOKS) +
                            " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_SERIES_ID) + "=?)");
        }
        mSetBooksDirtyBySeriesStmt.bindLong(1, seriesId);
        mSetBooksDirtyBySeriesStmt.executeUpdateDelete();
    }

    /**
     * Set all books referencing a given bookshelf as dirty.
     */
    private void setBooksDirtyByBookshelf(final long bookshelfId) {
        if (mSetBooksDirtyByBookshelfStmt == null) {
            mSetBooksDirtyByBookshelfStmt = mStatements.add("mSetBooksDirtyByBookshelfStmt",
                    "UPDATE " + TBL_BOOKS +
                            " SET " +
                            DOM_LAST_UPDATE_DATE + "=current_timestamp" +
                            " WHERE" +
                            " Exists(SELECT * FROM " + TBL_BOOK_BOOKSHELF.ref() + TBL_BOOK_BOOKSHELF.join(TBL_BOOKS) +
                            " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_FK_BOOKSHELF_ID) + "=?)");
        }
        mSetBooksDirtyByBookshelfStmt.bindLong(1, bookshelfId);
        mSetBooksDirtyByBookshelfStmt.executeUpdateDelete();
    }

    private void insertBookSeries(long bookId,
                                  final @NonNull List<Series> list,
                                  boolean dirtyBookIfNecessary) {
        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        if (mDeleteBookSeriesStmt == null) {
            mDeleteBookSeriesStmt = mStatements.add("mDeleteBookSeriesStmt",
                    "DELETE FROM " + TBL_BOOK_SERIES + " WHERE " + DOM_FK_BOOK_ID + "=?");
        }
        mDeleteBookSeriesStmt.bindLong(1, bookId);
        mDeleteBookSeriesStmt.executeUpdateDelete();

        // anything to insert ?
        if (list.size() == 0) {
            return;
        }

        if (mAddBookSeriesStmt == null) {
            mAddBookSeriesStmt = mStatements.add("mAddBookSeriesStmt",
                    "INSERT INTO " + TBL_BOOK_SERIES + "(" +
                            DOM_FK_BOOK_ID + "," +
                            DOM_FK_SERIES_ID + "," +
                            DOM_BOOK_SERIES_NUM + "," +
                            DOM_BOOK_SERIES_POSITION + ")"
                            + " VALUES(?,?,?,?)");
        }

        /*
        TEST: is this still true ?
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
        final Map<String, Boolean> idHash = new HashMap<>();
        int pos = 0;
        for (Series entry : list) {
            long seriesId = 0;
            try {
                seriesId = getSeriesId(entry.name);
                if (seriesId == 0) {
                    seriesId = insertSeries(entry.name);
                    if (seriesId < 0) {
                        // it's unlikely, but *if* it happens, let's log it
                        Logger.error("insert failed");
                    }
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
                Logger.error(e);
                throw new DBExceptions.InsertException("Error adding series '" + entry.name +
                        "' {" + seriesId + "} to book " + bookId + ": " + e.getLocalizedMessage(), e);
            }
        }
    }

    /**
     * Insert a List of TOCEntries.
     *
     * @param bookId               of the book
     * @param list                 the list
     * @param dirtyBookIfNecessary up to now, always set to 'false'
     */
    private void insertOrUpdateTOC(final long bookId,
                                   final @NonNull List<TOCEntry> list,
                                   boolean dirtyBookIfNecessary) {
        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        int rowsAffected = deleteTOCEntriesByBookId(bookId, false);
        if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
            Logger.info(this, "deleteTOCEntriesByBookId: bookId=" + bookId + ", rowsAffected=" + rowsAffected);
        }

        // anything to insert ?
        if (list.size() == 0) {
            return;
        }

        for (TOCEntry tocEntry : list) {
            if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
                Logger.info(this, "Adding TOCEntriesByBookId: " + tocEntry);
            }
            long authorId = getOrInsertAuthorId(tocEntry.getAuthor());

            // insert new stories, or get the id from existing stories
            long tocId = insertOrUpdateTOCEntry(authorId, tocEntry.getTitle(), tocEntry.getFirstPublication());

            // create the book<->TOCEntry link
            long btId = insertBookTOCEntry(tocId, bookId, false);

            if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
                Logger.info(this, "     bookId   : " + bookId);
                Logger.info(this, "     authorId : " + authorId);
                Logger.info(this, "     tocId    : " + tocId);
                Logger.info(this, "     btId     : " + btId);
            }
        }
    }

    /**
     * Create the link between Book and Author.
     *
     * @param bookId               of the book
     * @param list                 List of {@link Author} for this book
     * @param dirtyBookIfNecessary flag to set book dirty or not (for now, always false...)
     */
    private void insertBookAuthors(final long bookId,
                                   final @NonNull List<Author> list,
                                   final boolean dirtyBookIfNecessary) {

        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        if (mDeleteBookAuthorsStmt == null) {
            mDeleteBookAuthorsStmt = mStatements.add("mDeleteBookAuthorsStmt",
                    "DELETE FROM " + TBL_BOOK_AUTHOR + " WHERE " + DOM_FK_BOOK_ID + "=?");
        }
        mDeleteBookAuthorsStmt.bindLong(1, bookId);
        mDeleteBookAuthorsStmt.executeUpdateDelete();

        // anything to insert ?
        if (list.size() == 0) {
            return;
        }

        if (mAddBookAuthorsStmt == null) {
            mAddBookAuthorsStmt = mStatements.add("mAddBookAuthorsStmt",
                    "INSERT INTO " + TBL_BOOK_AUTHOR
                            + "(" +
                            DOM_FK_BOOK_ID + "," +
                            DOM_FK_AUTHOR_ID + "," +
                            DOM_AUTHOR_POSITION +
                            ") VALUES(?,?,?)");
        }

        // The list MAY contain duplicates (eg. from Internet lookups of multiple
        // sources), so we track them in a hash table
        final Map<String, Boolean> idHash = new HashMap<>();
        int pos = 0;
        for (Author author : list) {
            long authorId = 0;
            try {
                // find/insert the author
                authorId = getOrInsertAuthorId(author);
                // we use the id as the KEY here, so yes, a String.
                String authorIdStr = Long.toString(authorId);
                if (!idHash.containsKey(authorIdStr)) {
                    // indicate this author(id) is already present... but override, so we get elimination of duplicates.
                    idHash.put(authorIdStr, true);

                    pos++;
                    mAddBookAuthorsStmt.bindLong(1, bookId);
                    mAddBookAuthorsStmt.bindLong(2, authorId);
                    mAddBookAuthorsStmt.bindLong(3, pos);
                    long tmpId = mAddBookAuthorsStmt.executeInsert();
                    if (tmpId < 0) {
                        // it's unlikely, but *if* it happens, let's log it
                        Logger.error("insert failed");
                    }
                    mAddBookAuthorsStmt.clearBindings();
                }
            } catch (Exception e) {
                Logger.error(e);
                throw new DBExceptions.InsertException("Error adding author '" + author + "' {" + authorId + "} to book " + bookId + ": " + e.getLocalizedMessage(), e);
            }
        }

    }

    /**
     * Create each book/bookshelf combo in the weak entity
     *
     * @param bookId               of the book
     * @param list                 A list of bookshelves
     * @param dirtyBookIfNecessary flag to set book dirty or not
     */
    private void insertBookBookshelf(final long bookId,
                                     final @NonNull List<Bookshelf> list,
                                     final boolean dirtyBookIfNecessary) {
        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        if (mDeleteBookBookshelfStmt == null) {
            mDeleteBookBookshelfStmt = mStatements.add("mDeleteBookBookshelfStmt",
                    "DELETE FROM " + TBL_BOOK_BOOKSHELF + " WHERE " + DOM_FK_BOOK_ID + "=?");
        }
        mDeleteBookBookshelfStmt.bindLong(1, bookId);
        mDeleteBookBookshelfStmt.executeUpdateDelete();

        // anything to insert ?
        if (list.size() == 0) {
            return;
        }

        if (mInsertBookBookshelfStmt == null) {
            mInsertBookBookshelfStmt = mStatements.add("mInsertBookBookshelfStmt",
                    "INSERT INTO " + TBL_BOOK_BOOKSHELF + "(" +
                            DOM_FK_BOOK_ID + "," +
                            DOM_BOOKSHELF +
                            ") VALUES (?,?)");
        }

        for (Bookshelf bookshelf : list) {
            if (bookshelf.name.isEmpty()) {
                continue;
            }

            // ENHANCE: we cannot trust the id of Bookshelf for now
            long bookshelfId = getBookshelfIdByName(bookshelf.name);
            if (bookshelfId == 0) {
                bookshelfId = insertBookshelf(bookshelf.name);
            }

            if (bookshelfId == -1) {
                bookshelfId = Bookshelf.DEFAULT_ID;
            }

            try {
                mInsertBookBookshelfStmt.bindLong(1, bookId);
                mInsertBookBookshelfStmt.bindLong(2, bookshelfId);
                mInsertBookBookshelfStmt.executeInsert();
            } catch (Exception e) {
                Logger.error(e, "Error assigning a book to a bookshelf");
            }
        }
    }

    /**
     * transaction: needs
     * throws exceptions, caller must handle
     */
    private void globalReplacePositionedBookItem(final @NonNull TableDefinition table,
                                                 final @NonNull String objectIdField,
                                                 final @NonNull String positionField,
                                                 final long from,
                                                 final long to) {

        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        // Update books but prevent duplicate index errors - update books for which the new ID is not already present
        String sql = "UPDATE " + table +
                " SET " + objectIdField + "=" + to +
                " WHERE " + objectIdField + "=" + from +
                " AND NOT EXISTS(SELECT NULL FROM " + table + " WHERE " + DOM_FK_BOOK_ID + "=" + DOM_FK_BOOK_ID + " AND " + objectIdField + "=" + to + ")";

        mSyncedDb.execSQL(sql);

        // Finally, delete the rows that would have caused duplicates. Be cautious by using the
        // EXISTS statement again; it's not necessary, but we do it to reduce the risk of data
        // loss if one of the prior statements failed silently.
        //
        // We also move remaining items up one place to ensure positions remain correct
        //
        sql = "SELECT * FROM " + table + " WHERE " +
                objectIdField + "=" + from +
                " AND EXISTS(SELECT NULL FROM " + table + " WHERE " + DOM_FK_BOOK_ID + "=" + table.dot(DOM_FK_BOOK_ID) + " AND " + objectIdField + "=" + to + ")";

        SynchronizedStatement delStmt = null;
        SynchronizedStatement replacementIdPosStmt = null;
        SynchronizedStatement checkMinStmt = null;
        SynchronizedStatement moveStmt = null;
        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{})) {
            // Get the column indexes we need
            final int bookCol = cursor.getColumnIndexOrThrow(DOM_FK_BOOK_ID.name);
            final int posCol = cursor.getColumnIndexOrThrow(positionField);

            // Statement to delete a specific object record
            delStmt = mSyncedDb.compileStatement(
                    "DELETE FROM " + table +
                            " WHERE " + objectIdField + "=? AND " + DOM_FK_BOOK_ID + "=?");

            // Statement to get the position of the already-existing 'new/replacement' object
            replacementIdPosStmt = mSyncedDb.compileStatement(
                    "SELECT " +
                            positionField +
                            " FROM " + table +
                            " WHERE " + DOM_FK_BOOK_ID + "=? AND " + objectIdField + "=?");

            // Move statement; move a single entry to a new position
            moveStmt = mSyncedDb.compileStatement(
                    "UPDATE " + table +
                            " SET " + positionField + "=?" +
                            " WHERE " + DOM_FK_BOOK_ID + "=? AND " + positionField + "=?");

            // Sanity check to deal with legacy bad data
            checkMinStmt = mSyncedDb.compileStatement(
                    "SELECT" +
                            " min(" + positionField + ")" +
                            " FROM " + table +
                            " WHERE " + DOM_FK_BOOK_ID + "=?");

            // Loop through all instances of the old author appearing
            while (cursor.moveToNext()) {
                // Get the details of the old object
                long bookId = cursor.getLong(bookCol);
                long pos = cursor.getLong(posCol);

                // Get the position of the new/replacement object
                replacementIdPosStmt.bindLong(1, bookId);
                replacementIdPosStmt.bindLong(1, bookId);
                long replacementIdPos = replacementIdPosStmt.simpleQueryForLong();

                // Delete the old record
                delStmt.bindLong(1, from);
                delStmt.bindLong(2, to);
                delStmt.executeUpdateDelete();

                // If the deleted object was more prominent than the new object, move the new one up
                if (replacementIdPos > pos) {
                    moveStmt.bindLong(1, pos);
                    moveStmt.bindLong(2, bookId);
                    moveStmt.bindLong(3, replacementIdPos);
                    moveStmt.executeUpdateDelete();
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
                    moveStmt.executeUpdateDelete();
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
     * @param bookId of the book
     *
     * @return list of TOCEntry for this book
     */
    @NonNull
    public ArrayList<TOCEntry> getTOCEntriesByBook(final long bookId) {
        ArrayList<TOCEntry> list = new ArrayList<>();
        try (Cursor cursor = this.fetchTOCEntriesByBookId(bookId)) {
            if (cursor.getCount() == 0) {
                return list;
            }

            final int familyNameCol = cursor.getColumnIndex(DOM_AUTHOR_FAMILY_NAME.name);
            final int givenNameCol = cursor.getColumnIndex(DOM_AUTHOR_GIVEN_NAMES.name);
            final int authorIdCol = cursor.getColumnIndex(DOM_FK_AUTHOR_ID.name);

            final int titleCol = cursor.getColumnIndex(DOM_TITLE.name);
            final int pubDateCol = cursor.getColumnIndex(DOM_FIRST_PUBLICATION.name);

            //final int positionCol = cursor.getColumnIndex(DOM_BOOK_TOC_ENTRY_POSITION.name);

            while (cursor.moveToNext()) {
                Author author = new Author(
                        cursor.getLong(authorIdCol),
                        cursor.getString(familyNameCol),
                        cursor.getString(givenNameCol));
                list.add(new TOCEntry(author, cursor.getString(titleCol), cursor.getString(pubDateCol)));
            }
        }
        return list;
    }

    /**
     * @param bookId of the book
     */
    @NonNull
    public ArrayList<Author> getBookAuthorList(final long bookId) {
        ArrayList<Author> list = new ArrayList<>();
        try (Cursor authors = fetchAuthorsByBookId(bookId)) {
            if (authors.getCount() == 0) {
                return list;
            }

            int idCol = authors.getColumnIndex(DOM_PK_ID.name);
            int familyCol = authors.getColumnIndex(DOM_AUTHOR_FAMILY_NAME.name);
            int givenCol = authors.getColumnIndex(DOM_AUTHOR_GIVEN_NAMES.name);

            while (authors.moveToNext()) {
                list.add(new Author(authors.getLong(idCol), authors.getString(familyCol), authors.getString(givenCol)));
            }
        }
        return list;
    }

    /**
     * @param bookId of the book
     */
    @NonNull
    public ArrayList<Series> getBookSeriesList(final long bookId) {
        ArrayList<Series> list = new ArrayList<>();
        try (Cursor series = fetchAllSeriesByBookId(bookId)) {
            if (series.getCount() == 0) {
                return list;
            }

            int idCol = series.getColumnIndex(DOM_PK_ID.name);
            int nameCol = series.getColumnIndex(DOM_SERIES_NAME.name);
            int numCol = series.getColumnIndex(DOM_BOOK_SERIES_NUM.name);

            while (series.moveToNext()) {
                list.add(new Series(series.getLong(idCol), series.getString(nameCol), series.getString(numCol)));
            }
        }
        return list;
    }

//    /**
//     * Bad idea. Instead use: Book book = new Book(bookId, null)
//     * So you never get a null object!
//     *
//     * Leaving commented as a reminder
//     *
//     * @param bookId of the book
//     *
//     * @return the fully populated Book, or null if not found
//     *
//     * @see #fetchBookById(long) which allows a partial retrieval
//     */
//    @Nullable
//    public Book getBookById(final long bookId) {
//
//        try (Cursor cursor = fetchBookById(bookId)) {
//            if (cursor.moveToFirst()) {
//                // Put all cursor fields in collection
//                Book book = new Book(cursor);
//
//                // load lists (or init with empty lists)
//                book.putBookshelfList(getBookshelvesByBookId(bookId));
//                book.putAuthorList(getBookAuthorList(bookId));
//                book.putSeriesList(getBookSeriesList(bookId));
//                book.putTOC(getTOCEntriesByBook(bookId));
//
//                return book;
//            }
//        }
//        return null;
//    }

    /**
     * Return the SQL for a list of all books in the database
     *
     * @param whereClause to add to books search criteria (without the keyword 'WHERE')
     *
     * @return A full piece of SQL to perform the search
     */
    @NonNull
    private String getAllBooksSql(final @NonNull String whereClause) {

        //TODO: redo this so the sql becomes static

        // there is no 'real' FROM table set. The 'from' is a combo of three sub-selects
        String fullSql = "SELECT b.*, " +
                // these two SQL macros use "a." due to TBL_AUTHOR being "a." but that is just a happy coincidence.
                // Here, the "a." refers to the JOIN "Get the 'default' author" (see below)
                SQL_FIELDS_FOR_AUTHOR + "," +
                SQL_FIELDS_AUTHOR_FORMATTED_GIVEN_FIRST + "," +
                "a." + DOM_FK_AUTHOR_ID + " AS " + DOM_FK_AUTHOR_ID + "," +

                "Coalesce(s." + DOM_FK_SERIES_ID + ", 0) AS " + DOM_FK_SERIES_ID + "," +
                "Coalesce(s." + DOM_SERIES_NAME + ", '') AS " + DOM_SERIES_NAME + "," +
                "Coalesce(s." + DOM_BOOK_SERIES_NUM + ", '') AS " + DOM_BOOK_SERIES_NUM + "," +
                // if in more then one series, concat " et al"
                " Case" +
                "  When " + COLUMN_ALIAS_NR_OF_SERIES + " < 2" +
                "   Then Coalesce(s." + DOM_SERIES_FORMATTED + ", '')" +
                "   Else " + DOM_SERIES_FORMATTED + "||' " + BookCatalogueApp.getResourceString(R.string.and_others) + "'" +
                " End AS " + DOM_SERIES_FORMATTED +

                " FROM (" +
                /* */ "SELECT DISTINCT " + SQL_FIELDS_FOR_BOOK + " FROM " + TBL_BOOKS.ref() +
                /* */ (!whereClause.isEmpty() ? " WHERE " + " (" + whereClause + ")" : "") +
                /* */ " ORDER BY lower(" + TBL_BOOKS.dot(DOM_TITLE) + ") " + COLLATION + " ASC"
                + ") b" +

                // Get the 'default' author
                " JOIN (" +
                /* */ "SELECT " +
                /* */ DOM_FK_AUTHOR_ID + "," +
                /* */ DOM_AUTHOR_FAMILY_NAME + "," +
                /* */ DOM_AUTHOR_GIVEN_NAMES + "," +
                /* */ SQL_FIELDS_AUTHOR_FORMATTED + "," +
                /* */ TBL_BOOK_AUTHOR.dotAs(DOM_FK_BOOK_ID) +
                /* */ " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS) +
                ") a ON a." + DOM_FK_BOOK_ID + "=b." + DOM_PK_ID +
                /* */ " AND a." + DOM_FK_AUTHOR_ID + "=b." + DOM_FK_AUTHOR_ID +

                // Get the 'default' series
                " LEFT OUTER JOIN (" +
                /* */ "SELECT " +
                /* */ DOM_FK_SERIES_ID + "," +
                /* */ DOM_SERIES_NAME + "," +
                /* */ DOM_BOOK_SERIES_NUM + "," +
                /* */ TBL_BOOK_SERIES.dotAs(DOM_FK_BOOK_ID) + "," +
                /* */ " Case" +
                /* */ " When " + DOM_BOOK_SERIES_NUM + " = ''" +
                /* */ "  Then " + DOM_SERIES_NAME + "" +
                /* */ "  Else " + DOM_SERIES_NAME + "||' #'||" + DOM_BOOK_SERIES_NUM +
                /* */ " End AS " + DOM_SERIES_FORMATTED +
                /* */ " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES) +
                ") s ON s." + DOM_FK_BOOK_ID + "=b." + DOM_PK_ID +
                /* */ " AND s." + DOM_FK_SERIES_ID + "=b." + DOM_FK_SERIES_ID +
                /* */ " AND lower(s." + DOM_BOOK_SERIES_NUM + ")=lower(b." + DOM_BOOK_SERIES_NUM + ") " + COLLATION;


        if (DEBUG_SWITCHES.SQL && DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            Logger.info(this, "getAllBooksSql:\n\n" + fullSql);
        }
        return fullSql;
    }

    /**
     * Return a {@link BookCursor} for the given {@link Book} id.
     * The caller can then retrieve columns as needed.
     *
     * @param bookId to retrieve
     *
     * @return @return {@link Cursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBookById(final long bookId) throws DBExceptions.NotFoundException {
        return fetchBooksWhere(TBL_BOOKS.dot(DOM_PK_ID) + "=?", new String[]{Long.toString(bookId)}, "");
    }

    /**
     * The passed sql should at least return some of the fields from the books table!
     * If a method call is made to retrieve a column that does not exists, an exception
     * will be thrown.
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    private BookCursor fetchBooks(final @NonNull String sql, final @NonNull String[] selectionArgs) {
        return (BookCursor) mSyncedDb.rawQueryWithFactory(mBooksCursorFactory, sql, selectionArgs, "");
    }

    /**
     * Return a {@link BookCursor} for the given whereClause
     *
     * @param whereClause to add to books search criteria (without the keyword 'WHERE')
     * @param order       What order to return the books in (without the keyword 'ORDER BY')
     *                    TODO: check if order by is really needed/used
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooksWhere(final @NonNull String whereClause,
                                      final @NonNull String[] selectionArgs,
                                      final @NonNull String order) {
        String sql = getAllBooksSql(whereClause);
        if (!order.isEmpty()) {
            sql += " ORDER BY " + order;
        }
        return fetchBooks(sql, selectionArgs);
    }

    /**
     * Return a {@link BookCursor} for the given ISBN.
     * Note: CAN RETURN MORE THAN ONE BOOK
     *
     * @param isbnList list of ISBN(s) to retrieve
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooksByIsbnList(final @NonNull List<String> isbnList) {
        if (isbnList.size() == 0) {
            throw new IllegalArgumentException("isbnList was empty");
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
        return fetchBooksWhere(where.toString(), new String[]{}, "");
    }

    /**
     * A complete export of all tables (flattened) in the database
     *
     * @return BookCursor over all books, authors, etc
     */
    @NonNull
    public BookCursor exportBooks(final @Nullable Date sinceDate) {
        String whereClause;
        if (sinceDate == null) {
            whereClause = "";
        } else {
            whereClause = " WHERE " + TBL_BOOKS.dot(DOM_LAST_UPDATE_DATE) + " > '" + DateUtils.utcSqlDateTime(sinceDate) + "' ";
        }

        String sql = "SELECT DISTINCT " +
                SQL_FIELDS_FOR_BOOK + "," +
                TBL_LOAN.dotAs(DOM_LOANED_TO) +
                " FROM " + TBL_BOOKS.ref() + " LEFT OUTER JOIN " + TBL_LOAN.ref() +
                " ON (" + TBL_LOAN.dot(DOM_FK_BOOK_ID) + "=" + TBL_BOOKS.dot(DOM_PK_ID) + ") " +
                whereClause +
                " ORDER BY " + TBL_BOOKS.dot(DOM_PK_ID);
        return fetchBooks(sql, new String[]{});
    }

    /**
     * This function will create a new bookshelf in the database
     *
     * @param name The bookshelf name
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insertBookshelf(final @NonNull String name) {
        // TODO: Decide if we need to backup EMPTY bookshelves...
        ContentValues cv = new ContentValues();
        cv.put(DOM_BOOKSHELF.name, name);
        return mSyncedDb.insert(TBL_BOOKSHELF.getName(), null, cv);
    }

    /**
     * Take all books from Bookshelf 'sourceId', and put them onto Bookshelf 'destId',
     * then delete Bookshelf 'sourceId'.
     *
     * @throws DBExceptions.UpdateException if the update failed
     *
     */
    public void mergeBookshelves(final long sourceId, final long destId)
            throws DBExceptions.UpdateException {

        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            String sql = "UPDATE " + TBL_BOOK_BOOKSHELF +
                    " SET " + DOM_FK_BOOKSHELF_ID + "=" + destId + " WHERE " + DOM_FK_BOOKSHELF_ID + "=" + sourceId;
            mSyncedDb.execSQL(sql);

            sql = "DELETE FROM " + TBL_BOOKSHELF + " WHERE " + DOM_PK_ID + "=" + sourceId;
            mSyncedDb.execSQL(sql);

            mSyncedDb.setTransactionSuccessful();
        } catch (Exception e) {
            Logger.error(e);
            throw new DBExceptions.UpdateException(e);
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
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
        if (mGetBookshelfNameQuery == null) {
            mGetBookshelfNameQuery = mStatements.add("mGetBookshelfNameQuery",
                    "SELECT " +
                            DOM_BOOKSHELF +
                            " FROM " + TBL_BOOKSHELF +
                            " WHERE " + DOM_PK_ID + "=?");
        }
        mGetBookshelfNameQuery.bindLong(1, id);
        return mGetBookshelfNameQuery.simpleQueryForString();
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

        boolean dirty = (0 < mSyncedDb.delete(TBL_BOOK_BOOKSHELF.getName(), DOM_BOOKSHELF + "=?", new String[]{Long.toString(id)}));
        int rowsAffected = mSyncedDb.delete(TBL_BOOKSHELF.getName(), DOM_PK_ID + "=?", new String[]{Long.toString(id)});

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
    public long getBookshelfId(final @NonNull Bookshelf bookshelf) {
        Bookshelf bs = getBookshelfByName(bookshelf.name);
        return (bs != null ? bs.id : 0);
    }

    /**
     * @param name of bookshelf to find
     *
     * @return the Bookshelf, or null if not found
     */
    @Nullable
    public Bookshelf getBookshelfByName(final @NonNull String name) {
        if (mGetBookshelfIdQuery == null) {
            mGetBookshelfIdQuery = mStatements.add("mGetBookshelfIdQuery",
                    "SELECT " +
                            DOM_PK_ID +
                            " FROM " + TBL_BOOKSHELF +
                            " WHERE lower(" + DOM_BOOKSHELF + ") = lower(?)" + COLLATION);
        }

        mGetBookshelfIdQuery.bindString(1, name);
        long id = mGetBookshelfIdQuery.simpleQueryForLongOrZero();
        if (id == 0) {
            return null;
        }
        return new Bookshelf(id, name);
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
    public int updateBookshelf(final long id, final @NonNull String name) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_BOOKSHELF.name, name);
        int rowsAffected = mSyncedDb.update(TBL_BOOKSHELF.getName(), cv, DOM_PK_ID + "=?", new String[]{Long.toString(id)});
        if (rowsAffected > 0) {
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
    private long getBookshelfIdByName(final @NonNull String name) {
        if (mGetBookshelfIdByNameQuery == null) {
            mGetBookshelfIdByNameQuery = mStatements.add("mGetBookshelfIdByNameQuery",
                    "SELECT " +
                            DOM_PK_ID +
                            " FROM " + DOM_BOOKSHELF +
                            " WHERE lower(" + DOM_BOOKSHELF + ") = lower(?)" + COLLATION);
        }
        mGetBookshelfIdByNameQuery.bindString(1, name);
        return mGetBookshelfIdByNameQuery.simpleQueryForLongOrZero();
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
            sql = "SELECT DISTINCT " +
                    DOM_PK_ID + "," +
                    DOM_BOOKSHELF +
                    " FROM " + TBL_BOOKSHELF +
                    " ORDER BY lower(" + DOM_BOOKSHELF + ") " + COLLATION;
            mSql.put("getBookshelves", sql);
        }
        ArrayList<Bookshelf> list = new ArrayList<>();

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{})) {
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(cursor.getLong(0), cursor.getString(1)));
            }
            return list;
        }
    }

    /**
     * Return a {@link Cursor} over the list of all {@link Bookshelf}
     * for the given {@link Book} id
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    public Cursor fetchBookshelvesByBookId(final long bookId) {
        String sql = mSql.get("fetchBookshelvesByBookId");
        if (sql == null) {
            sql = "SELECT DISTINCT " +
                    TBL_BOOKSHELF.dotAs(DOM_PK_ID) + "," +
                    TBL_BOOKSHELF.dotAs(DOM_BOOKSHELF) +
                    " FROM " + TBL_BOOK_BOOKSHELF.ref() + TBL_BOOK_BOOKSHELF.join(TBL_BOOKSHELF) +
                    " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_FK_BOOK_ID) + "=?" +
                    " ORDER BY lower(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + ") " + COLLATION;
            mSql.put("fetchBookshelvesByBookId", sql);
        }
        return mSyncedDb.rawQuery(sql, new String[]{Long.toString(bookId)});
    }

    public ArrayList<Bookshelf> getBookshelvesByBookId(final long bookId) {
        ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = fetchBookshelvesByBookId(bookId)) {
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(cursor.getLong(0), cursor.getString(1)));
            }
            return list;
        }
    }

    /**
     * Create the list of bookshelves in the underlying data
     *
     * @return The list, Bookshelf.SEPARATOR separated
     */
    @NonNull
    public String getBookshelvesByBookIdAsStringList(final long bookId) {
        try (Cursor cursor = fetchBookshelvesByBookId(bookId)) {
            StringBuilder bookshelves_list = new StringBuilder();
            int bsNameCol = cursor.getColumnIndex(DOM_BOOKSHELF.name);
            while (cursor.moveToNext()) {
                String encoded_name = StringList.encodeListItem(Bookshelf.SEPARATOR, cursor.getString(bsNameCol));
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
     * @return a list of all defined styles in the database
     */
    @NonNull
    public List<BooklistStyle> getBooklistStyles() {
        final List<BooklistStyle> list = new ArrayList<>();
        String sql = "SELECT " +
                TBL_BOOKLIST_STYLES.refAll() +
                " FROM " + TBL_BOOKLIST_STYLES.ref();

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{})) {
            int idCol = cursor.getColumnIndex(DOM_PK_ID.name);
            int blobCol = cursor.getColumnIndex(DOM_STYLE.name);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                byte[] blob = cursor.getBlob(blobCol);
                BooklistStyle style;
                try {
                    style = SerializationUtils.deserializeObject(blob);
                    style.id = id;
                    list.add(style);
                } catch (RTE.DeserializationException e) {
                    // Not much we can do; just delete it. Really should only happen in development.
                    deleteBooklistStyle(id);
                }
            }
        }
        return list;
    }

    /**
     * Save this style as a custom user style to the database.
     * Either updates or creates as necessary
     *
     * @return insert: the row ID of the newly inserted row, or -1 if an error occurred during insert
     * update: the existing id
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertOrUpdateBooklistStyle(final @NonNull BooklistStyle /* in/out */ style) {
        if (style.id != 0) {
            updateBooklistStyle(style);
        } else {
            long newId = insertBooklistStyle(style);
            if (newId > 0) {
                style.id = newId;
            } else {
                //error
                return newId;
            }
        }
        return style.id;
    }

    /**
     * Create a new booklist style
     *
     * @return the row ID of the last row inserted, if this insert is successful. -1 otherwise.
     */
    private long insertBooklistStyle(final @NonNull BooklistStyle style) {
        if (mInsertBooklistStyleStmt == null) {
            mInsertBooklistStyleStmt = mStatements.add("mInsertBooklistStyleStmt",
                    TBL_BOOKLIST_STYLES.getInsert(DOM_STYLE) + " VALUES (?)");
        }
        mInsertBooklistStyleStmt.bindBlob(1, SerializationUtils.serializeObject(style));
        return mInsertBooklistStyleStmt.executeInsert();
    }

    private void updateBooklistStyle(final @NonNull BooklistStyle style) {
        if (mUpdateBooklistStyleStmt == null) {
            mUpdateBooklistStyleStmt = mStatements.add("mUpdateBooklistStyleStmt",
                    TBL_BOOKLIST_STYLES.getInsertOrReplaceValues(DOM_PK_ID, DOM_STYLE));
        }
        mUpdateBooklistStyleStmt.bindLong(1, style.id);
        mUpdateBooklistStyleStmt.bindBlob(2, SerializationUtils.serializeObject(style));
        mUpdateBooklistStyleStmt.execute();
    }

    public void deleteBooklistStyle(final long id) {
        if (mDeleteBooklistStyleStmt == null) {
            mDeleteBooklistStyleStmt = mStatements.add("mDeleteBooklistStyleStmt",
                    "DELETE FROM " + TBL_BOOKLIST_STYLES + " WHERE " + DOM_PK_ID + "=?");
        }
        mDeleteBooklistStyleStmt.bindLong(1, id);
        mDeleteBooklistStyleStmt.executeUpdateDelete();
    }

    /**
     * Return a {@link Cursor} for the given query string
     *
     * @param query string
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    public Cursor fetchSearchSuggestions(final @NonNull String query) {
        String sql = mSql.get("fetchSearchSuggestions");
        if (sql == null) {
            sql = "SELECT * FROM (SELECT \"BK\" || " + TBL_BOOKS.dotAs(DOM_PK_ID) + "," +
                    TBL_BOOKS.dot(DOM_TITLE) + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + "," +
                    TBL_BOOKS.dot(DOM_TITLE) + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                    " FROM " + TBL_BOOKS.ref() + " WHERE " + TBL_BOOKS.dot(DOM_TITLE) + " LIKE ?" +
                    " UNION " +
                    " SELECT \"AF\" || " + TBL_AUTHORS.dotAs(DOM_PK_ID) + "," +
                    TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + "," +
                    TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                    " FROM " + TBL_AUTHORS.ref() + " WHERE " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " LIKE ?" +
                    " UNION " +
                    " SELECT \"AG\" || " + TBL_AUTHORS.dotAs(DOM_PK_ID) + "," +
                    TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + "," +
                    TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                    " FROM " + TBL_AUTHORS.ref() + " WHERE " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " LIKE ?" +
                    " UNION " +
                    " SELECT \"BK\" || " + TBL_BOOKS.dotAs(DOM_PK_ID) + "," +
                    TBL_BOOKS.dot(DOM_BOOK_ISBN) + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + "," +
                    TBL_BOOKS.dot(DOM_BOOK_ISBN) + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA +
                    " FROM " + TBL_BOOKS.ref() + " WHERE " + TBL_BOOKS.dot(DOM_BOOK_ISBN) + " LIKE ?" +
                    " ) AS zzz " +
                    " ORDER BY lower(" + SearchManager.SUGGEST_COLUMN_TEXT_1 + ") " + COLLATION;
            mSql.put("fetchSearchSuggestions", sql);
        }
        String q = query + "%";
        return mSyncedDb.rawQuery(sql, new String[]{q, q, q, q});
    }

    /**
     * Returns a unique list of all currencies in the database
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getCurrencyCodes(final @NonNull String type) {
        String column;
        if (UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY.equals(type)) {
            column = DOM_BOOK_PRICE_LISTED_CURRENCY.name;
//        } else if (UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY.equals(type)) {
        } else {
            column = DOM_BOOK_PRICE_PAID_CURRENCY.name;
        }

        String sql = "SELECT DISTINCT " +
                column +
                " FROM " + TBL_BOOKS +
                " ORDER BY lower(" + column + ") " + COLLATION;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{})) {
            ArrayList<String> list = getFirstColumnAsList(cursor);
            if (list.isEmpty()) {
                // sure, this is very crude. But it will only ever be used once per currency column
                list.add("EUR");
                list.add("GBP");
                list.add("USD");
            }
            return list;
        }
    }

    /**
     * Returns a unique list of all formats in the database
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getFormats() {
        String sql = "SELECT DISTINCT " +
                DOM_BOOK_FORMAT +
                " FROM " + TBL_BOOKS +
                " ORDER BY lower(" + DOM_BOOK_FORMAT + ") " + COLLATION;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{})) {
            return getFirstColumnAsList(cursor);
        }
    }

    public void globalReplaceFormat(final @NonNull String from, final @NonNull String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        // Update books but prevent duplicate index errors
        String sql = "UPDATE " + TBL_BOOKS +
                " SET " +
                DOM_BOOK_FORMAT + "='" + encodeString(to) + "," +
                DOM_LAST_UPDATE_DATE + "=current_timestamp" +
                " WHERE " + DOM_BOOK_FORMAT + "='" + encodeString(from) + "'";
        mSyncedDb.execSQL(sql);
    }

    /**
     * Returns a unique list of all locations in the database
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getGenres() {
        String sql = "SELECT DISTINCT " +
                DOM_BOOK_GENRE +
                " FROM " + TBL_BOOKS +
                " ORDER BY lower(" + DOM_BOOK_GENRE + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql, new String[]{})) {
            return getFirstColumnAsList(c);
        }
    }

    public void globalReplaceGenre(final @NonNull String from, final @NonNull String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        // Update books but prevent duplicate index errors
        String sql = "UPDATE " + TBL_BOOKS +
                " SET " +
                DOM_BOOK_GENRE + "='" + encodeString(to) + "'," +
                DOM_LAST_UPDATE_DATE + "=current_timestamp" +
                " WHERE " + DOM_BOOK_GENRE + "='" + encodeString(from) + "'";
        mSyncedDb.execSQL(sql);
    }

    /**
     * Returns a unique list of all languages in the database
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getLanguages() {
        String sql = "SELECT DISTINCT " +
                DOM_BOOK_LANGUAGE +
                " FROM " + TBL_BOOKS +
                " ORDER BY lower(" + DOM_BOOK_LANGUAGE + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql, new String[]{})) {
            return getFirstColumnAsList(c);
        }
    }

    public void globalReplaceLanguage(final @NonNull String from, final @NonNull String to) {
        if (Objects.equals(from, to)) {
            return;
        }

        // Update books but prevent duplicate index errors
        String sql = "UPDATE " + TBL_BOOKS +
                " SET " +
                DOM_BOOK_LANGUAGE + "='" + encodeString(to) + "'," +
                DOM_LAST_UPDATE_DATE + "=current_timestamp"
                + " WHERE " + DOM_BOOK_LANGUAGE + "='" + encodeString(from) + "'";
        mSyncedDb.execSQL(sql);
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
                new String[]{DOM_FK_BOOK_ID.name, DOM_LOANED_TO.name},
                DOM_FK_BOOK_ID + "=?", new String[]{Long.toString(bookId)},
                null, null, null)) {

            // 1= DOM_LOANED_TO.name
            return cursor.moveToFirst() ? cursor.getString(1) : null;
        }
    }

    /**
     * This function will create a new loan in the database
     *
     * @param book                 A collection with the columns to be set. May contain extra data.
     * @param dirtyBookIfNecessary flag to set book dirty or not
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertLoan(final @NonNull Book book, final boolean dirtyBookIfNecessary) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_FK_BOOK_ID.name, book.getBookId());
        cv.put(DOM_LOANED_TO.name, book.getString(DOM_LOANED_TO.name));

        long newId = mSyncedDb.insert(TBL_LOAN.getName(), null, cv);
        if (newId > 0) {
            purgeLoans();
            if (dirtyBookIfNecessary) {
                setBookDirty(book.getBookId());
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
        int rowsAffected = mSyncedDb.delete(TBL_LOAN.getName(),
                DOM_FK_BOOK_ID + "=?",
                new String[]{Long.toString(bookId)});
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
     */
    private void purgeLoans() {
        mSyncedDb.delete(TBL_LOAN.getName(),
                "(" + DOM_FK_BOOK_ID + "='' OR " + DOM_FK_BOOK_ID + "=null" +
                        " OR " + DOM_LOANED_TO + "='' OR " + DOM_LOANED_TO + "=null) ",
                null);
    }

    /**
     * Returns a unique list of all locations in the database
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getLocations() {
        String sql = "SELECT DISTINCT " +
                DOM_BOOK_LOCATION +
                " FROM " + TBL_BOOKS +
                " ORDER BY lower(" + DOM_BOOK_LOCATION + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql, new String[]{})) {
            return getFirstColumnAsList(c);
        }
    }

    public void globalReplaceLocation(final @NonNull String from, final @NonNull String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        // Update books but prevent duplicate index errors
        String sql = "UPDATE " + TBL_BOOKS +
                " SET " +
                DOM_BOOK_LOCATION + "='" + encodeString(to) + "'," +
                DOM_LAST_UPDATE_DATE + "=current_timestamp "
                + " WHERE " + DOM_BOOK_LOCATION + "='" + encodeString(from) + "'";
        mSyncedDb.execSQL(sql);
    }

    /**
     * Returns a unique list of all publishers in the database
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getPublishers() {
        String sql = "SELECT DISTINCT " +
                DOM_BOOK_PUBLISHER +
                " FROM " + TBL_BOOKS +
                " ORDER BY lower(" + DOM_BOOK_PUBLISHER + ") " + COLLATION;

        try (Cursor c = mSyncedDb.rawQuery(sql, new String[]{})) {
            return getFirstColumnAsList(c);
        }
    }

    public void globalReplacePublisher(final @NonNull Publisher from, final @NonNull Publisher to) {
        if (Objects.equals(from, to)) {
            return;
        }
        // Update books but prevent duplicate index errors
        String sql = "UPDATE " + TBL_BOOKS +
                " SET " +
                DOM_BOOK_PUBLISHER + "='" + encodeString(to.name) + "'," +
                DOM_LAST_UPDATE_DATE + "=current_timestamp"
                + " WHERE " + DOM_BOOK_PUBLISHER + "='" + encodeString(from.name) + "'";
        mSyncedDb.execSQL(sql);
    }

    /**
     * Create a new series in the database
     *
     * @param name A string containing the series name
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    private long insertSeries(final @NonNull String name) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_SERIES_NAME.name, name);
        return mSyncedDb.insert(TBL_SERIES.getName(), null, cv);
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
        int rowsAffected = mSyncedDb.delete(TBL_BOOK_SERIES.getName(), DOM_FK_SERIES_ID + "=?", new String[]{Long.toString(id)});
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
    public Series getSeries(final long id) {
        String sql = "SELECT " +
                DOM_SERIES_NAME +
                " FROM " + TBL_SERIES +
                " WHERE " + DOM_PK_ID + "=?";

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{Long.toString(id)})) {
            return cursor.moveToFirst() ? new Series(id, cursor.getString(0), "") : null;
        }
    }

    /**
     * Add or update the passed Series, depending whether series.id == 0.
     *
     * @return insert: the row ID of the newly inserted Series, or -1 if an error occurred during insert
     * update: the existing Series id
     *
     * @throws DBExceptions.UpdateException if the update failed
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertOrUpdateSeries(final @NonNull /* out */ Series series)
            throws DBExceptions.UpdateException {

        if (series.id != 0) {
            Series previous = this.getSeries(series.id);
            if (series.equals(previous)) {
                return series.id;
            }

            ContentValues cv = new ContentValues();
            cv.put(DOM_SERIES_NAME.name, series.name);

            int rowsAffected = mSyncedDb.update(TBL_SERIES.getName(), cv, DOM_PK_ID + "=?", new String[]{Long.toString(series.id)});
            if (rowsAffected == 1) {
                setBooksDirtyBySeries(series.id);
                return series.id;
            } else {
                throw new DBExceptions.UpdateException();
            }

        } else {
            series.id = insertSeries(series.name);
            return series.id;
        }
    }

    /**
     * @return the id, or 0 when not found
     */
    public long getSeriesId(final @NonNull Series s) {
        return getSeriesId(s.name);
    }

    /**
     * @return the id, or 0 when not found
     */
    public long getSeriesId(final @NonNull String name) {
        if (mGetSeriesIdQuery == null) {
            mGetSeriesIdQuery = mStatements.add("mGetSeriesIdQuery",
                    "SELECT " +
                            DOM_PK_ID +
                            " FROM " + TBL_SERIES +
                            " WHERE lower(" + DOM_SERIES_NAME + ") = lower(?)" + COLLATION);
        }
        mGetSeriesIdQuery.bindString(1, name);
        return mGetSeriesIdQuery.simpleQueryForLongOrZero();
    }

    /**
     * @throws DBExceptions.UpdateException   on failure to insertOrUpdateSeries(to) or on the global replace itself
     * @throws DBExceptions.NotFoundException on failure to find old series
     */
    public void globalReplaceSeries(final @NonNull Series from, final @NonNull Series to)
            throws DBExceptions.UpdateException, DBExceptions.NotFoundException {
        // Create or update the new series
        if (to.id == 0) {
            // try to find
            long id = getSeriesId(to);
            // If we have a match, just update the object and done
            if (id != 0) {
                to.id = id;
            } else {
                insertOrUpdateSeries(to);
            }
        } else {
            insertOrUpdateSeries(to);
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

        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            setBooksDirtyBySeries(from.id);

            // Update books but prevent duplicate index errors
            String sql = "UPDATE " + TBL_BOOK_SERIES +
                    " SET " +
                    DOM_FK_SERIES_ID + "=" + to.id +
                    " WHERE " + DOM_FK_SERIES_ID + "=" + from.id +
                    " AND NOT Exists(SELECT NULL FROM " + TBL_BOOK_SERIES.ref() + " WHERE " +
                    TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) + "=" + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) +
                    " AND " + TBL_BOOK_SERIES.dot(DOM_FK_SERIES_ID) + "=" + to.id + ")";
            mSyncedDb.execSQL(sql);

            globalReplacePositionedBookItem(TBL_BOOK_SERIES, DOM_FK_SERIES_ID.name, DOM_BOOK_SERIES_POSITION.name, from.id, to.id);

            mSyncedDb.setTransactionSuccessful();
        } catch (Exception e) {
            Logger.error(e);
            throw new DBExceptions.UpdateException(e);
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
    }

    /**
     * Delete the series with no related books
     * {@link DatabaseDefinitions#TBL_BOOK_SERIES}
     * {@link DatabaseDefinitions#DB_TB_SERIES}
     */
    public void purgeSeries() {

        if (mPurgeBookSeriesStmt == null) {
            mPurgeBookSeriesStmt = mStatements.add("mPurgeBookSeriesStmt",
                    "DELETE FROM " + TBL_BOOK_SERIES + " WHERE " + DOM_FK_BOOK_ID +
                            " NOT IN (SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKS + ")");
        }
        try {
            mPurgeBookSeriesStmt.executeUpdateDelete();
        } catch (Exception e) {
            Logger.error(e, "Failed to purge " + TBL_BOOK_SERIES);
        }

        if (mPurgeSeriesStmt == null) {
            mPurgeSeriesStmt = mStatements.add("mPurgeSeriesStmt",
                    "DELETE FROM " + TBL_SERIES + " WHERE " + DOM_PK_ID +
                            " NOT IN (SELECT DISTINCT " + DOM_FK_SERIES_ID + " FROM " + TBL_BOOK_SERIES + ") ");
        }
        try {
            mPurgeSeriesStmt.executeUpdateDelete();
        } catch (Exception e) {
            Logger.error(e, "Failed to purge " + TBL_SERIES);
        }
    }

    /**
     * @param series id
     *
     * @return number of books in series
     */
    public long countSeriesBooks(final @NonNull Series series) {
        if (series.id == 0) {
            series.id = getSeriesId(series);
        }
        if (series.id == 0) {
            return 0;
        }

        if (mGetSeriesBookCountQuery == null) {
            mGetSeriesBookCountQuery = mStatements.add("mGetSeriesBookCountQuery",
                    "SELECT" +
                            " COUNT(" + DOM_FK_BOOK_ID + ")" +
                            " FROM " + TBL_BOOK_SERIES +
                            " WHERE " + DOM_FK_SERIES_ID + "=?");
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
        String sql = "SELECT DISTINCT " +
                DOM_SERIES_NAME +
                " FROM " + TBL_SERIES +
                " ORDER BY lower(" + DOM_SERIES_NAME + ") " + COLLATION;
        return getColumnAsList(sql, DOM_SERIES_NAME.name);
    }

    /**
     * Return a {@link Cursor} over the list of all {@link Author}
     * for the given {@link Book} id
     *
     * @param bookId to retrieve
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    private Cursor fetchAllSeriesByBookId(final long bookId) {
        String sql = mSql.get("fetchAllSeriesByBookId");
        if (sql == null) {
            sql = "SELECT DISTINCT " +
                    TBL_SERIES.dotAs(DOM_PK_ID) + "," +
                    TBL_SERIES.dotAs(DOM_SERIES_NAME) + "," +
                    TBL_BOOK_SERIES.dotAs(DOM_BOOK_SERIES_NUM) + "," +
                    TBL_BOOK_SERIES.dotAs(DOM_BOOK_SERIES_POSITION) + "," +
                    DOM_SERIES_NAME + "||' ('||" + DOM_BOOK_SERIES_NUM + "||')' AS " + DOM_SERIES_FORMATTED +
                    " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES) +
                    " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) + "=?" +
                    " ORDER BY " + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION) + ", lower(" + TBL_SERIES.dot(DOM_SERIES_NAME) + ") " + COLLATION + " ASC";
            mSql.put("fetchAllSeriesByBookId", sql);
        }

        return mSyncedDb.rawQuery(sql, new String[]{Long.toString(bookId)});
    }

    /**
     * Set the goodreads book id for this book
     */
    public void setGoodreadsBookId(final long bookId, final long goodreadsBookId) {
        if (mSetGoodreadsBookIdStmt == null) {
            mSetGoodreadsBookIdStmt = mStatements.add("mSetGoodreadsBookIdStmt",
                    "UPDATE " + TBL_BOOKS +
                            " SET " + DOM_BOOK_GOODREADS_BOOK_ID + "=? WHERE " + DOM_PK_ID + "=?");
        }
        mSetGoodreadsBookIdStmt.bindLong(1, goodreadsBookId);
        mSetGoodreadsBookIdStmt.bindLong(2, bookId);
        mSetGoodreadsBookIdStmt.executeUpdateDelete();
    }

    /**
     * Return a {@link BookCursor} for the given goodreads book Id.
     * Note: MAY RETURN MORE THAN ONE BOOK
     *
     * @param grBookId to retrieve
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooksByGoodreadsBookId(long grBookId) {
        return fetchBooksWhere(TBL_BOOKS.dot(DOM_BOOK_GOODREADS_BOOK_ID) + "=?", new String[]{Long.toString(grBookId)}, "");
    }

    /**
     * Query to get relevant {@link Book} columns for sending to goodreads.
     *
     * @param startId     the 'first' (e.g. 'oldest') bookId to since the last sync with goodreads
     * @param updatesOnly true, if we only want the updated records since the last sync with goodreads
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooksForGoodreadsCursor(final long startId, final boolean updatesOnly) {
        String sql = "SELECT " +
                DOM_BOOK_ISBN + "," +
                DOM_PK_ID + "," +
                DOM_BOOK_GOODREADS_BOOK_ID + "," +
                DOM_BOOK_NOTES + "," +
                DOM_BOOK_READ + "," +
                DOM_BOOK_READ_END + "," +
                DOM_BOOK_RATING +
                " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + " > ?";
        if (updatesOnly) {
            sql += " AND " + DOM_LAST_UPDATE_DATE + " > " + DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
        }
        sql += " ORDER BY " + DOM_PK_ID;

        return fetchBooks(sql, new String[]{Long.toString(startId)});
    }

    /**
     * Query to get the ISBN for the given {@link Book} id, for sending to goodreads.
     *
     * @param bookId to retrieve
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBookForGoodreadsCursor(final long bookId) {
        String sql = mSql.get("fetchBookForGoodreadsCursor");
        if (sql == null) {
            sql = "SELECT " + DOM_PK_ID + "," +
                    DOM_BOOK_ISBN + "," +
                    DOM_BOOK_GOODREADS_BOOK_ID + "," +
                    DOM_BOOK_NOTES + "," +
                    DOM_BOOK_READ + "," +
                    DOM_BOOK_READ_END + "," +
                    DOM_BOOK_RATING +
                    " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + " =?  ORDER BY " + DOM_PK_ID;
            mSql.put("fetchBookForGoodreadsCursor", sql);
        }
        return fetchBooks(sql, new String[]{Long.toString(bookId)});
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
    private ArrayList<String> getColumnAsList(final @NonNull String sql, final @NonNull String columnName) {
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
    private ArrayList<String> getFirstColumnAsList(final @NonNull Cursor cursor) {
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
     * @param book      A collection with the columns to be set. May contain extra data.
     *
     * @return New and filtered ContentValues
     */
    @NonNull
    private ContentValues filterValues(final @NonNull String tableName,
                                       final @NonNull Book book) {

        TableInfo table = new TableInfo(mSyncedDb, tableName);

        ContentValues cv = new ContentValues();
        // Create the arguments
        for (String key : book.keySet()) {
            // Get column info for this column.
            TableInfo.ColumnInfo columnInfo = table.getColumn(key);
            // Check if we actually have a matching column.
            if (columnInfo != null) {
                // Never update PK.
                if (!columnInfo.isPrimaryKey) {
                    // Try to set the appropriate value, but if that fails, just use TEXT...
                    Object entry = book.get(key);
                    try {
                        switch (columnInfo.storageClass) {
                            case Real: {
                                if (entry instanceof Float) {
                                    cv.put(columnInfo.name, (Float) entry);
                                } else if (entry != null) {
                                    cv.put(columnInfo.name, Float.parseFloat(entry.toString()));
                                }
                                break;
                            }
                            case Integer: {
                                if (entry instanceof Boolean) {
                                    if ((Boolean) entry) {
                                        cv.put(columnInfo.name, 1);
                                    } else {
                                        cv.put(columnInfo.name, 0);
                                    }
                                } else if (entry instanceof Integer) {
                                    cv.put(columnInfo.name, (Integer) entry);
                                } else if (entry != null) {
                                    cv.put(columnInfo.name, Integer.parseInt(entry.toString()));
                                }
                                break;
                            }
                            case Text: {
                                if (entry instanceof String) {
                                    cv.put(columnInfo.name, ((String) entry));
                                } else if (entry != null) {
                                    cv.put(columnInfo.name, entry.toString());
                                }
                                break;
                            }
                        }
                    } catch (Exception e) {
                        cv.put(columnInfo.name, entry.toString());
                    }
                }
            }
        }
        return cv;
    }

    /**
     * Send the book details from the cursor to the passed fts query.
     *
     * NOTE: This assumes a specific order for query parameters.
     * If modified, then update {@link #insertFts} , {@link #updateFts} and {@link #rebuildFts}
     *
     * @param books Cursor of books to update
     * @param stmt  Statement to execute (insert or update)
     */
    private void ftsSendBooks(final @NonNull BookCursor books, final @NonNull SynchronizedStatement stmt) {

        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        final BookCursorRow bookCursorRow = books.getCursorRow();

        // Build the SQL to get author details for a book.

        String aliasTOCEntryInfo = "TOCEntryInfo";
        String aliasTOCEntryAuthorInfo = "anthologyAuthorInfo";

        // ... all authors
        String authorBaseSql = mSql.get("ftsSendBooks.authorBaseSql");
        if (authorBaseSql == null) {
            authorBaseSql = "SELECT " +
                    TBL_AUTHORS.dot("*") +
                    " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS) +
                    " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK_ID) + "=?";
            mSql.put("ftsSendBooks.authorBaseSql", authorBaseSql);
        }

        // ... all series
        String seriesBaseSql = mSql.get("ftsSendBooks.seriesBaseSql");
        if (seriesBaseSql == null) {
            seriesBaseSql = "SELECT " +
                    TBL_SERIES.dot(DOM_SERIES_NAME) + " || ' ' || Coalesce(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM) + ",'') AS seriesInfo" +
                    " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES) +
                    " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) + "=?";
            mSql.put("ftsSendBooks.seriesBaseSql", seriesBaseSql);
        }

        // ... all anthology titles
        String anthologyBaseSql = mSql.get("ftsSendBooks.anthologyBaseSql");
        if (anthologyBaseSql == null) {
            anthologyBaseSql = "SELECT " +
                    TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " || ' ' || " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " AS " + aliasTOCEntryAuthorInfo + ", " +
                    DOM_TITLE + " AS " + aliasTOCEntryInfo +
                    " FROM " + TBL_ANTHOLOGY.ref() + TBL_ANTHOLOGY.join(TBL_BOOK_TOC_ENTRIES) + TBL_ANTHOLOGY.join(TBL_AUTHORS) +
                    " WHERE " + TBL_BOOK_TOC_ENTRIES.dot(DOM_FK_BOOK_ID) + "=?";
            mSql.put("ftsSendBooks.anthologyBaseSql", anthologyBaseSql);
        }

        // Accumulator for author names for each book
        StringBuilder authorText = new StringBuilder();
        // Accumulator for series names for each book
        StringBuilder seriesText = new StringBuilder();
        // Accumulator for title names for each anthology
        StringBuilder titleText = new StringBuilder();
        // Indexes of fields in cursor, -2 for 'not initialised yet'
        int colGivenNames = -2;
        int colFamilyName = -2;
        int colSeriesInfo = -2;
        int colTOCEntryAuthorInfo = -2;
        int colTOCEntryInfo = -2;

        // Process each book
        while (books.moveToNext()) {
            // Reset authors/series/title
            authorText.setLength(0);
            seriesText.setLength(0);
            titleText.setLength(0);
            // Get list of authors
            try (Cursor c = mSyncedDb.rawQuery(authorBaseSql, new String[]{Long.toString(bookCursorRow.getId())})) {
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
            try (Cursor c = mSyncedDb.rawQuery(seriesBaseSql, new String[]{Long.toString(bookCursorRow.getId())})) {
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
            try (Cursor c = mSyncedDb.rawQuery(anthologyBaseSql, new String[]{Long.toString(bookCursorRow.getId())})) {
                // Get column indexes, if not already got
                if (colTOCEntryAuthorInfo < 0) {
                    colTOCEntryAuthorInfo = c.getColumnIndex(aliasTOCEntryAuthorInfo);
                }
                if (colTOCEntryInfo < 0) {
                    colTOCEntryInfo = c.getColumnIndex(aliasTOCEntryInfo);
                }
                // Append each series
                while (c.moveToNext()) {
                    authorText.append(c.getString(colTOCEntryAuthorInfo));
                    authorText.append(";");
                    titleText.append(c.getString(colTOCEntryInfo));
                    titleText.append(";");
                }
            }

            // Set the parameters and call
            bindStringOrNull(stmt, 1, authorText.toString());
            // Titles should only contain title, not SERIES
            bindStringOrNull(stmt, 2, bookCursorRow.getTitle() + "; " + titleText);
            // We could add a 'series' column, or just add it as part of the description
            bindStringOrNull(stmt, 3, bookCursorRow.getDescription() + seriesText);
            bindStringOrNull(stmt, 4, bookCursorRow.getNotes());
            bindStringOrNull(stmt, 5, bookCursorRow.getPublisherName());
            bindStringOrNull(stmt, 6, bookCursorRow.getGenre());
            bindStringOrNull(stmt, 7, bookCursorRow.getLocation());
            bindStringOrNull(stmt, 8, bookCursorRow.getIsbn());
            stmt.bindLong(9, bookCursorRow.getId());

            stmt.execute();
        }
    }

    /**
     * Utility function to bind a string or NULL value to a parameter since binding a NULL
     * in bindString produces an error.
     *
     * NOTE: We specifically want to use the default locale for this.
     */
    private void bindStringOrNull(final @NonNull SynchronizedStatement stmt, final int position, final @Nullable String s) {
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
                //noinspection UnusedAssignment
                t0 = System.currentTimeMillis();
            }

            if (mInsertFtsStmt == null) {
                // Build the FTS insert statement base. The parameter order MUST match the order expected in {@link #ftsSendBooks}.
                mInsertFtsStmt = mStatements.add("mInsertFtsStmt",
                        TBL_BOOKS_FTS.getInsert(
                                DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION,
                                DOM_BOOK_NOTES, DOM_BOOK_PUBLISHER, DOM_BOOK_GENRE,
                                DOM_BOOK_LOCATION, DOM_BOOK_ISBN, DOM_PK_DOCID)
                                + " VALUES (?,?,?,?,?,?,?,?,?)");
            }

            try (BookCursor books = fetchBooks("SELECT * FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=? ", new String[]{Long.toString(bookId)})) {
                ftsSendBooks(books, mInsertFtsStmt);
            } finally {
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    Logger.info(this, "Inserted FTS in " + (System.currentTimeMillis() - t0) + "ms");
                }
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to update FTS");
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
                //noinspection UnusedAssignment
                t0 = System.currentTimeMillis();
            }

            if (mUpdateFtsStmt == null) {
                // Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
                mUpdateFtsStmt = mStatements.add("mUpdateFtsStmt",
                        TBL_BOOKS_FTS.getUpdate(
                                DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION,
                                DOM_BOOK_NOTES, DOM_BOOK_PUBLISHER, DOM_BOOK_GENRE,
                                DOM_BOOK_LOCATION, DOM_BOOK_ISBN) + " WHERE " + DOM_PK_DOCID + "=?");
            }

            try (BookCursor books = fetchBooks("SELECT * FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?", new String[]{Long.toString(bookId)})) {
                ftsSendBooks(books, mUpdateFtsStmt);
            } finally {
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    Logger.info(this, "Updated FTS in " + (System.currentTimeMillis() - t0) + "ms");
                }
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to update FTS");
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
                //noinspection UnusedAssignment
                t0 = System.currentTimeMillis();
            }
            if (mDeleteFtsStmt == null) {
                mDeleteFtsStmt = mStatements.add("mDeleteFtsStmt",
                        "DELETE FROM " + TBL_BOOKS_FTS + " WHERE " + DOM_PK_DOCID + "=?");
            }
            mDeleteFtsStmt.bindLong(1, bookId);
            mDeleteFtsStmt.executeUpdateDelete();
            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                Logger.info(this, "Deleted FROM FTS in " + (System.currentTimeMillis() - t0) + "ms");
            }
        } catch (Exception e) {
            Logger.error(e, "Failed to delete FTS");
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
            //noinspection UnusedAssignment
            t0 = System.currentTimeMillis();
        }
        boolean gotError = false;

        // Make a copy of the FTS table definition for our temporary table.
        TableDefinition ftsTemp = TBL_BOOKS_FTS.clone();
        // Give it a new name
        ftsTemp.setName(ftsTemp.getName() + "_temp");

        SyncLock txLock = mSyncedDb.beginTransaction(true);

        // Build the FTS update statement base. The parameter order MUST match the order expected in ftsSendBooks().
        // reminder (to me) .. don't re-use in mStatements as the table is temporary.
        final String sql = ftsTemp.getInsert(
                DOM_AUTHOR_NAME, DOM_TITLE, DOM_DESCRIPTION,
                DOM_BOOK_NOTES, DOM_BOOK_PUBLISHER, DOM_BOOK_GENRE,
                DOM_BOOK_LOCATION, DOM_BOOK_ISBN, DOM_PK_DOCID)
                + " VALUES (?,?,?,?,?,?,?,?,?)";
        // Drop and recreate our temp copy
        ftsTemp.create(mSyncedDb, false);
        ftsTemp.drop(mSyncedDb);

        try (SynchronizedStatement insert = mSyncedDb.compileStatement(sql);
             BookCursor books = fetchBooks("SELECT * FROM " + TBL_BOOKS, new String[]{})) {
            ftsSendBooks(books, insert);
            // Drop old table, ready for rename
            TBL_BOOKS_FTS.drop(mSyncedDb);

            mSyncedDb.setTransactionSuccessful();
        } catch (Exception e) {
            Logger.error(e);
            gotError = true;
        } finally {
            mSyncedDb.endTransaction(txLock);
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
            Logger.info(this, "rebuildFts in " + (System.currentTimeMillis() - t0) + "ms");
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
     * @return a cursor, or null if all input was empty
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
                "SELECT " + DOM_PK_DOCID + " FROM " + TBL_BOOKS_FTS + " WHERE " + TBL_BOOKS_FTS + " MATCH '" + keywords);

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
     * DEBUG only
     */
    private static class InstanceRefDebug extends WeakReference<CatalogueDBAdapter> {
        @NonNull
        private final Exception mCreationException;

        InstanceRefDebug(final @NonNull CatalogueDBAdapter db) {
            super(db);
            mCreationException = new RuntimeException();
        }

        @NonNull
        Exception getCreationException() {
            return mCreationException;
        }
    }
}

