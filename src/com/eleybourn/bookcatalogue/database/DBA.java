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
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.csv.CsvImporter;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.database.definitions.ColumnInfo;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Publisher;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TOCEntry;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UpgradeMessageManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DESCRIPTION;
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
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_WITH_MULTIPLE_WORKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_TOC_ENTRY_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FTS_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANEE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_DOCID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKLIST_STYLES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS_FTS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_LOANEE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_TOC_ENTRIES;


/**
 * Book Catalogue database access helper class. Defines the basic CRUD operations
 * for the catalogue (based on the Notepad tutorial), and gives the
 * ability to list all books as well as retrieve or modify a specific book.
 *
 * insert:
 * * return new id, or -1 for 'small' error.
 * * Any Exception is rethrown as
 * {@link com.eleybourn.bookcatalogue.database.DBExceptions.InsertException}
 *
 * update:
 * * return rows affected, can be 0.
 * * Any Exception is rethrown as
 * {@link com.eleybourn.bookcatalogue.database.DBExceptions.UpdateException}
 *
 * insertOrUpdate:
 * * insert: as above
 *
 * * update: the existing id !
 * * Any Exception is rethrown as
 * {@link com.eleybourn.bookcatalogue.database.DBExceptions.UpdateException}
 *
 *
 * ENHANCE: Use date_acquired to add 'Recent Acquisitions' virtual shelf;
 * need to resolve how this may relate to date_added
 */
public class DBA
    implements AutoCloseable {

    /**
     * Flag indicating the UPDATE_DATE field from the bundle should be trusted.
     * If this flag is not set, the UPDATE_DATE will be set based on the current time
     */
    public static final int BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT = 1;
    /**
     * Flag indicating to skip doing the 'purge' step; mainly used in batch operations.
     */
    public static final int BOOK_UPDATE_SKIP_PURGE_REFERENCES = 1 << 1;
    /**
     * In addition to SQLite's default BINARY collator (others: NOCASE and RTRIM),
     * Android supplies two more.
     * LOCALIZED: using the system's current locale,
     * UNICODE  : Unicode Collation Algorithm and not tailored to the current locale.
     * <p>
     * We tried 'Collate UNICODE' but it seemed to be case sensitive.
     * We ended up with 'Ursula Le Guin' and 'Ursula le Guin'.
     * <p>
     * We now use Collate LOCALE and check to see if it is case sensitive.
     * We *hope* in the future Android will add LOCALE_CI (or equivalent).
     * <p>
     * public static final String COLLATION = " Collate NOCASE ";
     * public static final String COLLATION = " Collate UNICODE ";
     * <p>
     * NOTE: Important to have start/end spaces!
     */
    public static final String COLLATION = " Collate LOCALIZED ";

    /** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync. */
    private static final Synchronizer SYNCHRONIZER = new Synchronizer();
    /** Static Factory object to create the custom cursor. */
    private static final CursorFactory CURSOR_FACTORY = new CursorFactory() {
        @Override
        @NonNull
        public Cursor newCursor(
            final SQLiteDatabase db,
            @NonNull final SQLiteCursorDriver masterQuery,
            @NonNull final String editTable,
            @NonNull final SQLiteQuery query) {
            return new TrackedCursor(masterQuery, editTable, query, SYNCHRONIZER);
        }
    };
    /** Static Factory object to create the custom cursor. */
    @NonNull
    private static final CursorFactory BOOKS_CURSOR_FACTORY = new CursorFactory() {
        @Override
        public Cursor newCursor(
            final SQLiteDatabase db,
            @NonNull final SQLiteCursorDriver masterQuery,
            @NonNull final String editTable,
            @NonNull final SQLiteQuery query) {
            return new BookCursor(masterQuery, editTable, query, SYNCHRONIZER);
        }
    };

    /** DEBUG only. */
    private static final ArrayList<InstanceRefDebug> INSTANCES = new ArrayList<>();
    private static final String COLUMN_ALIAS_NR_OF_SERIES = "_num_series";
    private static final String COLUMN_ALIAS_NR_OF_AUTHORS = "_num_authors";

    /* log file error msg. */
    private static final String FAILED_TO_PURGE_ERROR = "Failed to purge ";

    /** DEBUG instance counter. */
    @NonNull
    private static final AtomicInteger DEBUG_INSTANCE_COUNT = new AtomicInteger();
    /** the actual SQLiteOpenHelper. */
    private static DBHelper mDbHelper;
    /** the synchronized wrapper around the real database. */
    private static SynchronizedDb mSyncedDb;
    /** needed for Resources & for getting the covers db. */
    @NonNull
    private final Context mContext;

    //region Statements
    /** a cache for statements, where they are pre-compiled. */
    private final SqlStatementManager mStatements;

    /** {@link #deleteBookshelf}. */
    @Nullable
    private SynchronizedStatement mDeleteBookshelf;

    /** {@link #deleteBook}. */
    @Nullable
    private SynchronizedStatement mDeleteBook;
    /** {@link #deleteTOCEntriesByBookId}. */
    @Nullable
    private SynchronizedStatement mDeleteTOCEntries;

    /** {@link #deleteSeries}. */
    @Nullable
    private SynchronizedStatement mDeleteSeries;

    /** {@link #deleteBookshelf}. */
    @Nullable
    private SynchronizedStatement mDeleteBookBookshelf;
    /** {@link #insertBookBookshelf}. */
    @Nullable
    private SynchronizedStatement mDeleteBookBookshelfStmt;

    /** {@link #insertBookAuthors}. */
    @Nullable
    private SynchronizedStatement mDeleteBookAuthorsStmt;
    /** {@link #insertBookSeries}. */
    @Nullable
    private SynchronizedStatement mDeleteBookSeriesStmt;
    /** {@link #deleteLoan}. */
    @Nullable
    private SynchronizedStatement mDeleteBookLoan;



    /** {@link #insertBookSeries}. */
    @Nullable
    private SynchronizedStatement mAddBookSeriesStmt;

    /** {@link #getTOCEntryId}. */
    @Nullable
    private SynchronizedStatement mGetTOCEntryIdQuery;
    /** {@link #getBookTOCEntryAtHighestPositionByBookId}. */
    @Nullable
    private SynchronizedStatement mGetBookTOCEntryAtHighestPositionByBookIdQuery;
    /** {@link #getAuthorIdByName}. */
    @Nullable
    private SynchronizedStatement mGetAuthorIdQuery;
    /** {@link #purgeAuthors}. */
    @Nullable
    private SynchronizedStatement mPurgeBookAuthorsStmt;
    /** {@link #purgeAuthors}. */
    @Nullable
    private SynchronizedStatement mPurgeAuthorsStmt;
    /** {@link #countAuthorBooks}. */
    @Nullable
    private SynchronizedStatement mGetAuthorBookCountQuery;
    /** {@link #countAuthorAnthologies}. */
    @Nullable
    private SynchronizedStatement mGetAuthorAnthologyCountQuery;
    /** {@link #getBookUuid}. */
    @Nullable
    private SynchronizedStatement mGetBookUuidQuery;
    /** {@link #getBookIdFromUuid}. */
    @Nullable
    private SynchronizedStatement mGetBookIdFromUuidQuery;
    /** {@link #getBookLastUpdateDate}. */
    @Nullable
    private SynchronizedStatement mGetBookUpdateDateQuery;
    /** {@link #getBookTitle}. */
    @Nullable
    private SynchronizedStatement mGetBookTitleQuery;

    /** {@link #getLoaneeByBookId}. */
    @Nullable
    private SynchronizedStatement  mGetLoaneeByBookIdQuery;

    /** {@link #getIdFromIsbn}. */
    @Nullable
    private SynchronizedStatement mGetIdFromIsbn1Query;
    /** {@link #getIdFromIsbn}. */
    @Nullable
    private SynchronizedStatement mGetIdFromIsbn2Query;
    /** {@link #bookExists}. */
    @Nullable
    private SynchronizedStatement mCheckBookExistsStmt;

    /** {@link #setBookDirty}. */
    @Nullable
    private SynchronizedStatement mSetBooksDirtyStmt;
    /** {@link #setBooksDirtyBySeriesId}. */
    @Nullable
    private SynchronizedStatement mSetBooksDirtyBySeriesStmt;
    /** {@link #setBooksDirtyByAuthorId}. */
    @Nullable
    private SynchronizedStatement mSetBooksDirtyByAuthor1Stmt;
    /** {@link #setBooksDirtyByAuthorId}. */
    @Nullable
    private SynchronizedStatement mSetBooksDirtyByAuthor2Stmt;
    /** {@link #setBooksDirtyByBookshelfId}. */
    @Nullable
    private SynchronizedStatement mSetBooksDirtyByBookshelfStmt;

    /** {@link #insertBookAuthors}. */
    @Nullable
    private SynchronizedStatement mAddBookAuthorsStmt;

    /** {@link #insertBookBookshelf}. */
    @Nullable
    private SynchronizedStatement mInsertBookBookshelfStmt;
    /** {@link #getBookshelfName}. */
    @Nullable
    private SynchronizedStatement mGetBookshelfNameQuery;
    /** {@link #getBookshelfIdByName}. */
    @Nullable
    private SynchronizedStatement mGetBookshelfIdByNameQuery;
    /** {@link #getBooklistStyleIdByUuid}. */
    @Nullable
    private SynchronizedStatement mGetBooklistStyleStmt;
    /** {@link #insertBooklistStyle}. */
    @Nullable
    private SynchronizedStatement mInsertBooklistStyleStmt;
    /** {@link #deleteBooklistStyle}. */
    @Nullable
    private SynchronizedStatement mDeleteBooklistStyleStmt;
    /** {@link #getSeriesId}. */
    @Nullable
    private SynchronizedStatement mGetSeriesIdQuery;
    /** {@link #purgeSeries}. */
    @Nullable
    private SynchronizedStatement mPurgeBookSeriesStmt;
    /** {@link #purgeSeries}. */
    @Nullable
    private SynchronizedStatement mPurgeSeriesStmt;
    /** {@link #purgeLoans}. */
    @Nullable
    private SynchronizedStatement mPurgeLoans;

    /** {@link #countSeriesBooks}. */
    @Nullable
    private SynchronizedStatement mGetSeriesBookCountQuery;
    /** {@link #setGoodreadsBookId}. */
    @Nullable
    private SynchronizedStatement mSetGoodreadsBookIdStmt;
    /** {@link #setGoodreadsSyncDate}. */
    @Nullable
    private SynchronizedStatement mSetGoodreadsSyncDateStmt;

    /** {@link #insertFts}. */
    @Nullable
    private SynchronizedStatement mInsertFtsStmt;
    /** {@link #updateFts}. */
    @Nullable
    private SynchronizedStatement mUpdateFtsStmt;
    /** {@link #deleteFts}. */
    @Nullable
    private SynchronizedStatement mDeleteFtsStmt;
    /** {@link #globalReplaceFormat}. */
    @Nullable
    private SynchronizedStatement mGlobalReplaceFormat;
    /** {@link #globalReplaceGenre}. */
    @Nullable
    private SynchronizedStatement mGlobalReplaceGenre;
    /** {@link #globalReplaceLanguage}. */
    @Nullable
    private SynchronizedStatement mGlobalReplaceLanguage;
    /** {@link #globalReplaceLocation}. */
    @Nullable
    private SynchronizedStatement mGlobalReplaceLocation;
    /** {@link #globalReplacePublisher}. */
    @Nullable
    private SynchronizedStatement mGlobalReplacePublisher;
    /** {@link #globalReplaceSeries}. */
    @Nullable
    private SynchronizedStatement mGlobalReplaceSeries;
    /** {@link #globalReplaceAuthor}. */
    @Nullable
    private SynchronizedStatement mGlobalReplaceAuthorOnTOCEntries;
    //endregion Statements

    /** used by finalize so close does not get called twice. */
    private boolean mCloseCalled;

    /**
     * Constructor - takes the context to allow the database to be opened/created.
     *
     * Small note: don't be tempted to turn this into a singleton...
     * multi-threading does not like that.
     *
     * @param context the Context within which to work
     */
    public DBA(@NonNull final Context context) {
        mContext = context;
        // initialise static if not done yet
        if (mDbHelper == null) {
            mDbHelper = new DBHelper(mContext, CURSOR_FACTORY, SYNCHRONIZER);
        }

        // initialise static if not done yet
        if (mSyncedDb == null) {
            // Get the DB wrapper
            mSyncedDb = new SynchronizedDb(mDbHelper, SYNCHRONIZER);
            // Turn on foreign key support so that CASCADE works.
            mSyncedDb.execSQL("PRAGMA foreign_keys = ON");
            // Turn on recursive triggers; not strictly necessary
            mSyncedDb.execSQL("PRAGMA recursive_triggers = ON");

            mSyncedDb.getUnderlyingDatabase()
                     .setMaxSqlCacheSize(10);
        }

        // for debug
        //mSyncedDb.execSQL("PRAGMA temp_store = FILE");

        // instance based
        mStatements = new SqlStatementManager(mSyncedDb);

        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            Logger.info(this,
                        "instances created: " + DEBUG_INSTANCE_COUNT.incrementAndGet());
            //debugAddInstance(this);
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
        return SYNCHRONIZER;
    }

    /**
     * Escape single quotation marks by doubling them (standard SQL escape).
     *
     * @return escape value.
     */
    @NonNull
    public static String encodeString(@NonNull final String value) {
        return value.replace("'", "''");
    }

    @NonNull
    public static String SELECT(@NonNull final String... sa) {
        return "SELECT " + Csv.csv(",", sa);
    }

    @NonNull
    public static String SELECT_DISTINCT(@NonNull final String... sa) {
        return "SELECT DISTINCT " + Csv.csv(",", sa);
    }

    /**
     * Cleanup a search string to remove all quotes etc.
     *
     * Remove punctuation from the search string to TRY to match the tokenizer.
     * The only punctuation we allow is a hyphen preceded by a space.
     * Everything else is translated to a space.
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

    /**
     * DEBUG only.
     */
    @SuppressWarnings("unused")
    private static void debugAddInstance(@NonNull final DBA db) {
        INSTANCES.add(new InstanceRefDebug(db));
    }

    /**
     * DEBUG only.
     */
    @SuppressWarnings("unused")
    private static void debugRemoveInstance(@NonNull final DBA db) {
        Iterator<InstanceRefDebug> it = INSTANCES.iterator();

        while (it.hasNext()) {
            InstanceRefDebug ref = it.next();
            DBA refDb = ref.get();
            if (refDb == null) {
                Logger.info(DBA.class,"<-- **** Missing ref (not closed?) **** vvvvvvv");
                Logger.error(ref.getCreationException());
                Logger.info(DBA.class,"--> **** Missing ref (not closed?) **** ^^^^^^^");
            } else {
                if (refDb == db) {
                    it.remove();
                }
            }
        }
    }

    /**
     * DEBUG ONLY; used when tracking a bug in android 2.1, but kept because
     * there are still non-fatal anomalies.
     */
    @SuppressWarnings("unused")
    public static void debugPrintReferenceCount(@Nullable final String msg) {
        if (mSyncedDb != null) {
            SynchronizedDb.printRefCount(msg, mSyncedDb.getUnderlyingDatabase());
        }
    }

    /**
     * DEBUG only.
     */
    @SuppressWarnings("unused")
    public static void debugDumpInstances() {
        for (InstanceRefDebug ref : INSTANCES) {
            if (ref.get() == null) {
                Logger.info(DBA.class,
                            "<-- **** Missing ref (not closed?) **** vvvvvvv");
                Logger.error(ref.getCreationException());
                Logger.info(DBA.class,
                            "--> **** Missing ref (not closed?) **** ^^^^^^^");
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
            mSyncedDb.analyze();
        } catch (RuntimeException e) {
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
    public SynchronizedDb getUnderlyingDatabase() {
        return mSyncedDb;
    }

    /**
     * Set the Goodreads sync date to the current time.
     */
    public void setGoodreadsSyncDate(final long bookId) {
        if (mSetGoodreadsSyncDateStmt == null) {
            mSetGoodreadsSyncDateStmt = mStatements.add("mSetGoodreadsSyncDateStmt",
                                                        SqlUpdate.GOODREADS_LAST_SYNC_DATE);
        }
        mSetGoodreadsSyncDateStmt.bindLong(1, bookId);
        mSetGoodreadsSyncDateStmt.executeUpdateDelete();
    }

    /**
     * Generic function to close the database.
     * It does not 'close' the database in the literal sense, but
     * performs a cleanup by closing all open statements
     *
     * So it should really be called cleanup()
     * But it allows us to use try-with-resources.
     *
     * Consequently, there is no need to 'open' anything before running further operations.
     */
    @Override
    public void close() {

        if (mStatements != null) {
            // the close() will perform a clear, ready to be re-used.
            mStatements.close();
        }

        if (DEBUG_SWITCHES.DB_ADAPTER && BuildConfig.DEBUG) {
            Logger.info(this,
                        "instances left: " + DEBUG_INSTANCE_COUNT.decrementAndGet());
            //debugRemoveInstance(this);
        }
        mCloseCalled = true;
    }

    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        if (!mCloseCalled) {
            Logger.info(this,
                        "Leaking instances: " + DEBUG_INSTANCE_COUNT.get());
            close();
        }
        super.finalize();
    }

    /**
     * Used by {@link CsvImporter}.
     */
    @NonNull
    public SyncLock startTransaction(final boolean isUpdate) {
        return mSyncedDb.beginTransaction(isUpdate);
    }

    /**
     * Used by {@link CsvImporter}.
     */
    public void endTransaction(@NonNull final SyncLock txLock) {
        mSyncedDb.endTransaction(txLock);
    }

    /**
     * Used by {@link CsvImporter}.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean inTransaction() {
        return mSyncedDb.inTransaction();
    }

    /**
     * Used by {@link CsvImporter}.
     */
    public void setTransactionSuccessful() {
        mSyncedDb.setTransactionSuccessful();
    }

    /**
     * Insert or Update the anthology title in the database.
     * {@link DatabaseDefinitions#TBL_TOC_ENTRIES}
     * The id for an update, will always be fetched from the database.
     *
     * @param authorId        The author
     * @param title           The title of the anthology story
     * @param publicationDate The original publication year
     *
     * @return insert: the row ID of the newly inserted TOCEntry,
     * or -1 if an error occurred during insert
     * update: the existing TOCEntry id
     *
     * @throws DBExceptions.UpdateException if the update failed
     */
    private long insertOrUpdateTOCEntry(final long authorId,
                                        @NonNull final String title,
                                        @NonNull final String publicationDate)
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
            tocEntryId = mSyncedDb.insert(TBL_TOC_ENTRIES.getName(), null, cv);

            if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
                Logger.info(this,
                            "inserted TOCEntry: " + tocEntryId
                                    + ", authorId=" + authorId + ", " + title);
            }
        } else {
            int rowsAffected = mSyncedDb.update(TBL_TOC_ENTRIES.getName(), cv,
                                                DOM_PK_ID + "=?",
                                                new String[]{String.valueOf(tocEntryId)});
            if (rowsAffected != 1) {
                throw new DBExceptions.UpdateException();
            }

            if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
                Logger.info(this, "updated TOCEntry: " + tocEntryId
                        + ", authorId=" + authorId + ", " + title);
            }
        }
        return tocEntryId;
    }

    /**
     * Create a TOC entry (anthology title) for a book.
     *
     * It will always be added to the END OF THE LIST into
     * {@link DatabaseDefinitions#DOM_BOOK_TOC_ENTRY_POSITION}
     *
     * @param tocEntryId           the TOCEntry id (anthology title)
     * @param bookId               the book
     * @param dirtyBookIfNecessary flag to set book dirty or not
     *
     * @return insert: the row ID of the newly inserted BookTOCEntry,
     * or -1 if an error occurred during insert
     */
    private long insertBookTOCEntry(final long tocEntryId,
                                    final long bookId,
                                    final boolean dirtyBookIfNecessary) {

        ContentValues cv = new ContentValues();
        cv.put(DOM_FK_TOC_ENTRY_ID.name, tocEntryId);
        cv.put(DOM_FK_BOOK_ID.name, bookId);
        cv.put(DOM_BOOK_TOC_ENTRY_POSITION.name,
               getBookTOCEntryAtHighestPositionByBookId(bookId) + 1);
        long newId = mSyncedDb.insert(TBL_BOOK_TOC_ENTRIES.getName(), null, cv);

        if (newId > 0 && dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        return newId;
    }

    /**
     * Delete ALL the anthology records for a given book.
     * {@link DatabaseDefinitions#TBL_BOOK_TOC_ENTRIES}
     * But does NOT delete the Anthology Title itself {@link DatabaseDefinitions#TBL_TOC_ENTRIES}
     *
     * @param bookId               id of the book
     * @param dirtyBookIfNecessary flag to set book dirty or not
     *
     * @return the number of rows affected
     */
    private int deleteTOCEntriesByBookId(final long bookId,
                                         final boolean dirtyBookIfNecessary) {
        if (mDeleteTOCEntries == null) {
            mDeleteTOCEntries = mStatements.add("mDeleteTOCEntries",
                                                SqlDelete.BOOK_TOC_ENTRIES_BY_BOOK_ID);
        }
        mDeleteTOCEntries.bindLong(1, bookId);
        int rowsAffected = mDeleteTOCEntries.executeUpdateDelete();

        if (rowsAffected > 0) {
            if (dirtyBookIfNecessary) {
                setBookDirty(bookId);
            }
        }
        return rowsAffected;
    }

    /**
     * Return the TOCEntry ID for a given author/title (note that publication year is NOT used).
     * The title will be checked for (equal 'OR' as {@link #preprocessTitle})
     *
     * @param authorId id of author
     * @param title    title
     *
     * @return the id of the TOCEntry entry, or 0 if not found
     */
    public long getTOCEntryId(final long authorId,
                              @NonNull final String title) {
        if (mGetTOCEntryIdQuery == null) {
            mGetTOCEntryIdQuery = mStatements.add(
                    "mGetTOCEntryIdQuery",
                    "SELECT Coalesce( Min(" + DOM_PK_ID + "),0) FROM " + TBL_TOC_ENTRIES
                            + " WHERE " + DOM_FK_AUTHOR_ID + "=?"
                            + " AND (" + DOM_TITLE + "=? OR " + DOM_TITLE + "=?)" + COLLATION);
        }
        mGetTOCEntryIdQuery.bindLong(1, authorId);
        mGetTOCEntryIdQuery.bindString(2, title);
        mGetTOCEntryIdQuery.bindString(3, preprocessTitle(title));
        return mGetTOCEntryIdQuery.simpleQueryForLongOrZero();
    }

    /**
     * Return the largest anthology position (usually used for adding new titles).
     *
     * @param bookId id of book to retrieve
     *
     * @return An integer of the highest position. 0 if it is not an anthology, or not used yet
     */
    private long getBookTOCEntryAtHighestPositionByBookId(final long bookId) {
        if (mGetBookTOCEntryAtHighestPositionByBookIdQuery == null) {
            mGetBookTOCEntryAtHighestPositionByBookIdQuery = mStatements.add(
                "mGetBookTOCEntryAtHighestPositionByBookIdQuery",
                "SELECT max(" + DOM_BOOK_TOC_ENTRY_POSITION + ')'
                        + " FROM " + TBL_BOOK_TOC_ENTRIES
                        + " WHERE " + DOM_FK_BOOK_ID + "=?");
        }

        mGetBookTOCEntryAtHighestPositionByBookIdQuery.bindLong(1, bookId);
        return mGetBookTOCEntryAtHighestPositionByBookIdQuery.simpleQueryForLongOrZero();
    }

    /**
     * Return all the {@link TOCEntry} for the given {@link Author}.
     *
     * @param author to retrieve
     *
     * @return List of {@link TOCEntry} for this {@link Author}
     */
    @NonNull
    public ArrayList<TOCEntry> getTOCEntriesByAuthor(@NonNull final Author author) {
        ArrayList<TOCEntry> list = new ArrayList<>();
        try (Cursor cursor = this.fetchTOCEntriesByAuthorId(author.id)) {
            if (cursor.getCount() == 0) {
                return list;
            }
            ColumnMapper mapper = new ColumnMapper(cursor, DOM_PK_ID, DOM_TITLE,
                                                   DOM_FIRST_PUBLICATION);
            while (cursor.moveToNext()) {
                TOCEntry title = new TOCEntry(mapper.getLong(DOM_PK_ID),
                                              author,
                                              mapper.getString(DOM_TITLE),
                                              mapper.getString(DOM_FIRST_PUBLICATION));
                list.add(title);
            }
        }
        return list;
    }

    /**
     * Return a {@link Cursor} over all the {@link TOCEntry} and their {@link Author}
     * for the given {@link Book} id.
     *
     * @param bookId to retrieve
     *
     * @return {@link Cursor} containing all records ordered ascending by position, if any
     *
     * for convenience, we fetch 'full' {@link Author} objects here
     */
    @NonNull
    private Cursor fetchTOCEntriesByBookId(final long bookId) {
        return mSyncedDb.rawQuery(SqlSelectList.TOC_ENTRIES_BY_BOOK_ID,
                                  new String[]{String.valueOf(bookId)});
    }

    /**
     * Return a {@link Cursor} over all the {@link TOCEntry} for the given {@link Author} id.
     *
     * @param authorId to retrieve
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    private Cursor fetchTOCEntriesByAuthorId(final long authorId) {
        return mSyncedDb.rawQuery(SqlSelectList.GET_TOC_ENTRIES_BY_AUTHOR_ID,
                                  new String[]{String.valueOf(authorId)});
    }

    /**
     * Creates a new author.
     *
     * @param author object to insert. Will be updated with the id.
     *
     * @return the row ID of the newly inserted Author, or -1 if an error occurred
     */
    private long insertAuthor(@NonNull final Author /* in/out */ author) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_AUTHOR_FAMILY_NAME.name, author.getFamilyName());
        cv.put(DOM_AUTHOR_GIVEN_NAMES.name, author.getGivenNames());
        cv.put(DOM_AUTHOR_IS_COMPLETE.name, author.isComplete());
        long id = mSyncedDb.insert(TBL_AUTHORS.getName(), null, cv);
        if (id > 0) {
            author.id = id;
        }
        return id;
    }

    /**
     * @param author to update
     *
     * @return rows affected, should be 1 for success
     *
     * @throws DBExceptions.UpdateException on failure to update
     */
    public int updateAuthor(@NonNull final Author author)
        throws DBExceptions.UpdateException {

        ContentValues cv = new ContentValues();
        cv.put(DOM_AUTHOR_FAMILY_NAME.name, author.getFamilyName());
        cv.put(DOM_AUTHOR_GIVEN_NAMES.name, author.getGivenNames());
        cv.put(DOM_AUTHOR_IS_COMPLETE.name, author.isComplete());

        int rowsAffected = mSyncedDb.update(TBL_AUTHORS.getName(), cv,
                                            DOM_PK_ID + "=?",
                                            new String[]{String.valueOf(author.id)});
        if (rowsAffected == 1) {
            setBooksDirtyByAuthorId(author.id);
            return 1;
        } else {
            throw new DBExceptions.UpdateException();
        }
    }

    /**
     * Add or update the passed author, depending whether author.id == 0.
     *
     * @param author object to insert or update. Will be updated with the id.
     *
     * @return insert: the row ID of the newly inserted Author,
     * or -1 if an error occurred during insert
     * update: the existing Author id
     *
     * @throws DBExceptions.UpdateException if the update failed
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertOrUpdateAuthor(@NonNull final /* in/out */ Author author)
            throws DBExceptions.UpdateException {

        if (author.id != 0) {
            updateAuthor(author);
        } else {
            author.id = insertAuthor(author);
        }
        return author.id;
    }

    /**
     * This will return ALL Authors; mainly for the purpose of backups.
     *
     * @return Cursor over all Authors
     */
    @NonNull
    public Cursor fetchAuthors() {
        return mSyncedDb.rawQuery("SELECT * FROM " + TBL_AUTHORS, null);
    }

    /**
     * This will return the Author based on the ID.
     *
     * @return the author, or null if not found
     */
    @Nullable
    public Author getAuthor(final long id) {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.AUTHOR_BY_ID,
                                                new String[]{String.valueOf(id)})) {
            return (cursor.moveToFirst()
                    ? new Author(id, cursor.getString(0),
                               cursor.getString(1),
                               cursor.getInt(2) == 1)
                    : null);
        }
    }

    /**
     * @return author id, or 0 (e.g. 'new') when not found
     */
    public long getAuthorIdByName(@NonNull final String familyName,
                                  @NonNull final String givenNames) {
        if (mGetAuthorIdQuery == null) {
            mGetAuthorIdQuery = mStatements.add("mGetAuthorIdQuery",
                                                SqlGetId.AUTHOR_ID_BY_NAME);
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
    private long getOrInsertAuthorId(@NonNull final Author author) {
        long id = getAuthorIdByName(author.getFamilyName(), author.getGivenNames());
        return id == 0 ? insertAuthor(author) : id;
    }

    /**
     * Refresh the passed author from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the author.
     *
     * Will NOT insert a new Author if not found.
     */
    public void refreshAuthor(@NonNull final Author /* out */ author) {
        if (author.id == 0) {
            // It wasn't a known author; see if it is now. If so, update ID.
            long id = getAuthorIdByName(author.getFamilyName(), author.getGivenNames());
            // If we have a match, just update the object
            if (id > 0) {
                author.id = id;
            }
        } else {
            // It was a known author, see if it still is and fetch possibly updated fields.
            Author dbAuthor = this.getAuthor(author.id);
            if (dbAuthor != null) {
                // copy any updated fields
                author.copy(dbAuthor);
            } else {
                // Author not found?, set the author as 'new'
                author.id = 0;
            }
        }
    }

    /**
     * @throws DBExceptions.UpdateException   on failure to insertOrUpdateAuthor(to)
     * or on the global replace itself
     * @throws DBExceptions.NotFoundException on failure to find old Author
     */
    public void globalReplaceAuthor(@NonNull final Author from,
                                    @NonNull final Author to) {
        // Create or update the new author
        if (to.id == 0) {
            // try to find
            long id = getAuthorIdByName(to.getFamilyName(), to.getGivenNames());
            // If we have a match, just update the object and done
            if (id > 0) {
                to.id = id;
            } else {
                insertOrUpdateAuthor(to);
            }
        } else {
            insertOrUpdateAuthor(to);
        }

        // Do some basic sanity checks
        if (from.id == 0) {
            from.id = getAuthorIdByName(from.getFamilyName(), from.getGivenNames());
        }
        if (from.id == 0) {
            throw new DBExceptions.NotFoundException("Old Author is not defined");
        }

        if (from.id == to.id) {
            return;
        }

        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            setBooksDirtyByAuthorId(from.id);

            // First handle anthologies; they have a single author and are easy
            if (mGlobalReplaceAuthorOnTOCEntries == null) {
                mGlobalReplaceAuthorOnTOCEntries =
                        mStatements.add("mGlobalReplaceAuthorOnTOCEntries",
                                        SqlGlobalReplace.AUTHOR_ON_TOC_ENTRIES);
            }
            mGlobalReplaceAuthorOnTOCEntries.bindLong(1, to.id);
            mGlobalReplaceAuthorOnTOCEntries.bindLong(2, from.id);
            mGlobalReplaceAuthorOnTOCEntries.executeUpdateDelete();

            globalReplacePositionedBookItem(TBL_BOOK_AUTHOR, DOM_FK_AUTHOR_ID.name,
                                            DOM_BOOK_AUTHOR_POSITION.name, from.id, to.id);

            mSyncedDb.setTransactionSuccessful();
        } catch (RuntimeException e) {
            Logger.error(e);
            throw new DBExceptions.UpdateException(e);
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
    }

    /**
     * @return a complete list of author names from the database.
     * Used for {@link android.widget.AutoCompleteTextView}.
     */
    @NonNull
    public ArrayList<String> getAuthorsFormattedName() {
        ArrayList<String> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.AUTHORS_WITH_FORMATTED_NAMES,
                                                null)) {
            int col = cursor.getColumnIndexOrThrow(DOM_AUTHOR_FORMATTED.name);
            while (cursor.moveToNext()) {
                String name = cursor.getString(col);
                list.add(name);
            }
            return list;
        }
    }

    /**
     * When we have no more books or TOCEntry's of a given Author, purge the Author.
     * {@link DatabaseDefinitions#TBL_BOOK_AUTHOR}
     * {@link DatabaseDefinitions#TBL_AUTHORS}
     */
    public void purgeAuthors() {
//        if (mPurgeBookAuthorsStmt == null) {
//            mPurgeBookAuthorsStmt = mStatements.add("mPurgeBookAuthorsStmt",
//                                                    SqlDelete.PURGE_BOOK_AUTHORS);
//        }
//        try {
//            mPurgeBookAuthorsStmt.executeUpdateDelete();
//        } catch (RuntimeException e) {
//            Logger.error(e, FAILED_TO_PURGE_ERROR + TBL_BOOK_AUTHOR);
//        }
//
//        if (mPurgeAuthorsStmt == null) {
//            mPurgeAuthorsStmt = mStatements.add("mPurgeAuthorsStmt",
//                                                SqlDelete.PURGE_AUTHORS);
//        }
//        try {
//            mPurgeAuthorsStmt.executeUpdateDelete();
//        } catch (RuntimeException e) {
//            Logger.error(e, FAILED_TO_PURGE_ERROR + TBL_AUTHORS);
//        }
    }

    /**
     * Return a {@link Cursor} over the list of all {@link Author} for the given {@link Book} id.
     *
     * @param id of book
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    private Cursor fetchAuthorsByBookId(final long id) {
        return mSyncedDb.rawQuery(SqlSelectList.AUTHORS_BY_BOOK_ID,
                                  new String[]{String.valueOf(id)});
    }

    /**
     * @param author to retrieve
     *
     * @return the number of {@link Book} this {@link Author} has
     */
    public long countAuthorBooks(@NonNull final Author author) {
        if (author.id == 0) {
            author.id = getAuthorIdByName(author.getFamilyName(), author.getGivenNames());
        }

        if (author.id == 0) {
            return 0;
        }

        if (mGetAuthorBookCountQuery == null) {
            mGetAuthorBookCountQuery = mStatements.add(
                    "mGetAuthorBookCountQuery",
                    "SELECT COUNT(" + DOM_FK_BOOK_ID + ") FROM " + TBL_BOOK_AUTHOR
                            + " WHERE " + DOM_FK_AUTHOR_ID + "=?");
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
    public long countAuthorAnthologies(@NonNull final Author author) {
        if (author.id == 0) {
            author.id = getAuthorIdByName(author.getFamilyName(), author.getGivenNames());
        }
        if (author.id == 0) {
            return 0;
        }

        if (mGetAuthorAnthologyCountQuery == null) {
            mGetAuthorAnthologyCountQuery = mStatements.add(
                    "mGetAuthorAnthologyCountQuery",
                    "SELECT COUNT(" + DOM_PK_ID + ") FROM " + TBL_TOC_ENTRIES
                            + " WHERE " + DOM_FK_AUTHOR_ID + "=?");
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
    private void preprocessBook(final boolean isNew,
                                @NonNull final Book book) {

        // Handle AUTHOR
        // If present, get the author ID from the author name
        // (it may have changed with a name change)
        if (book.containsKey(UniqueId.KEY_AUTHOR_FORMATTED)) {
            Author author = new Author(book.getString(UniqueId.KEY_AUTHOR_FORMATTED));
            book.putLong(UniqueId.KEY_AUTHOR, getOrInsertAuthorId(author));
        } else {
            if (book.containsKey(UniqueId.KEY_AUTHOR_FAMILY_NAME)) {
                String family = book.getString(UniqueId.KEY_AUTHOR_FAMILY_NAME);
                String given;
                if (book.containsKey(UniqueId.KEY_AUTHOR_GIVEN_NAMES)) {
                    given = book.getString(UniqueId.KEY_AUTHOR_GIVEN_NAMES);
                } else {
                    given = "";
                }
                book.putLong(UniqueId.KEY_AUTHOR, getOrInsertAuthorId(new Author(family, given)));
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
            int type = DOM_BOOK_WITH_MULTIPLE_WORKS;
            if (TOCEntry.hasMultipleAuthors(TOCEntries)) {
                type |= DOM_BOOK_WITH_MULTIPLE_AUTHORS;
            }
            book.putInt(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, type);
        }

        //ENHANCE: handle price fields for legacy embedded currencies.
        // Perhaps moving those to currency fields ?

        // Handle currencies making sure they are uppercase
        if (book.containsKey(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY)) {
            book.putString(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY,
                           book.getString(UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY).toUpperCase());
        }
        if (book.containsKey(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY)) {
            book.putString(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY,
                           book.getString(UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY).toUpperCase());
        }

        // Handle Language field. Try to only store ISO3 code.
        if (book.containsKey(UniqueId.KEY_BOOK_LANGUAGE)) {
            String lang = book.getString(UniqueId.KEY_BOOK_LANGUAGE);
            if (lang.length() > 3) {
                book.putString(UniqueId.KEY_BOOK_LANGUAGE, LocaleUtils.getISO3Language(lang));
            }
        }

        // Remove blank/null fields that have default values defined in the database
        // or which should never be blank.
        for (String name : new String[]{
            UniqueId.KEY_BOOK_UUID,

            UniqueId.KEY_BOOK_ISBN,
            UniqueId.KEY_BOOK_PUBLISHER,
            UniqueId.KEY_BOOK_DATE_PUBLISHED,
            UniqueId.KEY_FIRST_PUBLICATION,
            UniqueId.KEY_BOOK_EDITION_BITMASK,
            UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK,

            UniqueId.KEY_BOOK_PRICE_LISTED,
            UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY,
            UniqueId.KEY_BOOK_PRICE_PAID,
            UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY,
            UniqueId.KEY_BOOK_DATE_ACQUIRED,

            UniqueId.KEY_BOOK_FORMAT,
            UniqueId.KEY_BOOK_GENRE,
            UniqueId.KEY_BOOK_LANGUAGE,
            UniqueId.KEY_BOOK_LOCATION,

            UniqueId.KEY_BOOK_READ,
            UniqueId.KEY_BOOK_READ_START,
            UniqueId.KEY_BOOK_READ_END,

            UniqueId.KEY_BOOK_SIGNED,
            UniqueId.KEY_BOOK_RATING,

            UniqueId.KEY_BOOK_DESCRIPTION,
            UniqueId.KEY_BOOK_NOTES,

            UniqueId.KEY_BOOK_GR_LAST_SYNC_DATE,

            UniqueId.KEY_BOOK_DATE_ADDED,
            UniqueId.KEY_LAST_UPDATE_DATE,
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
     * Move "The, A, An" etc... to the end of the string.
     *
     * @param title to format
     *
     * @return formatted title
     */
    private String preprocessTitle(@NonNull final String title) {

        StringBuilder newTitle = new StringBuilder();
        String[] titleWords = title.split(" ");
        try {
            if (titleWords[0].matches(mContext.getString(R.string.title_reorder))) {
                for (int i = 1; i < titleWords.length; i++) {
                    if (i != 1) {
                        newTitle.append(' ');
                    }
                    newTitle.append(titleWords[i]);
                }
                newTitle.append(", ").append(titleWords[0]);
                return newTitle.toString();
            }
        } catch (RuntimeException ignore) {
            //do nothing. Title stays the same
        }
        return title;
    }

    /**
     * Return the book UUID based on the id.
     *
     * @param bookId of the book
     *
     * @return the book UUID
     *
     * @throws SQLiteDoneException if zero rows found
     */
    @NonNull
    public String getBookUuid(final long bookId)
        throws SQLiteDoneException {
        if (mGetBookUuidQuery == null) {
            mGetBookUuidQuery = mStatements.add("mGetBookUuidQuery",
                                                SqlGetId.BOOK_UUID_BY_ID);
        }
        // Be cautious; other threads may call this and set parameters.
        synchronized (mGetBookUuidQuery) {
            mGetBookUuidQuery.bindLong(1, bookId);
            return mGetBookUuidQuery.simpleQueryForString();
        }
    }

    ;

    /**
     * Check that a book with the passed UUID exists and return the ID of the book, or zero.
     *
     * @param uuid of book
     *
     * @return ID of the book, or 0 'new' if not found
     */
    public long getBookIdFromUuid(@NonNull final String uuid) {
        if (mGetBookIdFromUuidQuery == null) {
            mGetBookIdFromUuidQuery = mStatements.add("mGetBookIdFromUuidQuery",
                                                      SqlGetId.BOOK_ID_BY_UUID);
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
            mGetBookUpdateDateQuery = mStatements.add(
                    "mGetBookUpdateDateQuery",
                    "SELECT " + DOM_LAST_UPDATE_DATE
                            + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?");
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
            mGetBookTitleQuery = mStatements.add(
                    "mGetBookTitleQuery",
                    "SELECT " + DOM_TITLE
                            + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?");
        }
        // Be cautious
        synchronized (mGetBookTitleQuery) {
            mGetBookTitleQuery.bindLong(1, bookId);
            return mGetBookTitleQuery.simpleQueryForStringOrNull();
        }
    }

    /**
     * @param bookId of the book.
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteBook(final long bookId) {
        String uuid = null;
        try {
            uuid = getBookUuid(bookId);
        } catch (SQLiteDoneException e) {
            Logger.error(e, "Failed to get book UUID");
        }

        int rowsAffected = 0;
        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            if (mDeleteBook == null) {
                mDeleteBook = mStatements.add("mDeleteBook",
                                              SqlDelete.BOOK_BY_ID);
            }
            mDeleteBook.bindLong(1, bookId);
            rowsAffected = mDeleteBook.executeUpdateDelete();

            if (rowsAffected > 0) {
                purgeAuthors();
                purgeSeries();
                deleteFts(bookId);
                deleteThumbnail(uuid);
            }
            mSyncedDb.setTransactionSuccessful();
        } catch (RuntimeException e) {
            Logger.error(e, "Failed to delete book");
        } finally {
            mSyncedDb.endTransaction(txLock);
        }

        return rowsAffected;
    }

    private void deleteThumbnail(@Nullable final String uuid) {
        if (uuid != null) {
            // remove from file system
            StorageUtils.deleteFile(StorageUtils.getCoverFile(uuid));
            // remove from cache
            if (!uuid.isEmpty()) {
                try (CoversDBA coversDBAdapter = CoversDBA.getInstance()) {
                    coversDBAdapter.deleteBookCover(uuid);
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
    public long insertBook(@NonNull final Book book) {
        return insertBookWithId(0, book);
    }

    /**
     * Create a new book using the details provided.
     *
     * Transaction: participate, or run in new.
     *
     * @param bookId of the book
     *               zero: a new book
     *               non-zero: will overwrite the small autoIncrement, normally only
     *               an Import should use this
     * @param book   A collection with the columns to be set. May contain extra data.
     *               The id will be updated.
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insertBookWithId(final long bookId,
                                 @NonNull final Book /* in/out */ book) {

        SyncLock txLock = null;
        if (!mSyncedDb.inTransaction()) {
            txLock = mSyncedDb.beginTransaction(true);
        }

        try {
            if (DEBUG_SWITCHES.DUMP_BOOK_BUNDLE_AT_INSERT && BuildConfig.DEBUG) {
                Logger.info(this, book.getRawData().toString());
            }
            // Cleanup fields (author, series, title and remove blank fields for which
            // we have defaults)
            preprocessBook(bookId == 0, book);

            /* Set defaults if not present in book
             *
             * TODO: We may want to provide default values for these fields:
             * KEY_BOOK_RATING, KEY_BOOK_READ, KEY_BOOK_NOTES, KEY_BOOK_LOCATION,
             * KEY_BOOK_READ_START, KEY_BOOK_READ_END, KEY_BOOK_SIGNED
             */
            if (!book.containsKey(UniqueId.KEY_BOOK_DATE_ADDED)) {
                book.putString(UniqueId.KEY_BOOK_DATE_ADDED, DateUtils.utcSqlDateTimeForToday());
            }

            // Make sure we have an author
            List<Author> authors = book.getAuthorList();
            if (authors.size() == 0) {
                Logger.error("No authors in book from\n" + book);
                return -1L;
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
                // it's an insert, success only if we really inserted.
                if (txLock != null) {
                    mSyncedDb.setTransactionSuccessful();
                }
            }

            // set the new id on the Book itself
            book.putLong(UniqueId.KEY_ID, newBookId);
            // and return it
            return newBookId;

        } catch (NumberFormatException e) {
            Logger.error(e, "NumberFormatException creating book from\n" + book);
            return -1L;
        } catch (RuntimeException e) {
            Logger.error(e, "Exception Error creating book from\n" + book);
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
     * @param bookId of the book; takes precedence over the id of the book itself.
     * @param book   A collection with the columns to be set. May contain extra data.
     * @param flags  See {@link #BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT} an others for
     *               flag definitions
     *
     * @return the number of rows affected, should be 1 for success.
     *
     * @throws DBExceptions.UpdateException on failure
     */
    public int updateBook(final long bookId,
                          @NonNull final Book book,
                          final int flags) {

        SyncLock txLock = null;
        if (!mSyncedDb.inTransaction()) {
            txLock = mSyncedDb.beginTransaction(true);
        }

        try {
            if (DEBUG_SWITCHES.DUMP_BOOK_BUNDLE_AT_UPDATE && BuildConfig.DEBUG) {
                Logger.info(this, book.getRawData().toString());
            }

            // Cleanup fields (author, series, title, 'sameAuthor' if anthology,
            // and remove blank fields for which we have defaults)
            preprocessBook(bookId == 0, book);

            ContentValues cv = filterValues(TBL_BOOKS.getName(), book);

            // Disallow UUID updates
            if (cv.containsKey(DOM_BOOK_UUID.name)) {
                cv.remove(DOM_BOOK_UUID.name);
            }

            // set the DOM_LAST_UPDATE_DATE to 'now' if we're allowed,
            // or if it's not present already.
            if ((flags & BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT) == 0
                || !cv.containsKey(DOM_LAST_UPDATE_DATE.name)) {
                cv.put(DOM_LAST_UPDATE_DATE.name, DateUtils.utcSqlDateTimeForToday());
            }

            // go !
            int rowsAffected = mSyncedDb.update(TBL_BOOKS.getName(), cv, DOM_PK_ID + "=?",
                                                new String[]{String.valueOf(bookId)});
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
            // make sure the Book has the correct id.
            book.putLong(UniqueId.KEY_ID, bookId);

            return rowsAffected;
        } catch (Exception e) {
            Logger.error(e);
            throw new DBExceptions.UpdateException(
                "Error updating book from " + book + ": " + e.getLocalizedMessage(), e);
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
    private void insertBookDependents(final long bookId,
                                      @NonNull final Book book) {

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

        if (book.containsKey(UniqueId.KEY_LOAN_LOANED_TO) && !book.getString(
            UniqueId.KEY_LOAN_LOANED_TO).isEmpty()) {
            deleteLoan(bookId, false);
            // don't care about insert failures really
            insertLoan(book, false);
        }
    }

    /**
     * Return a {@link Cursor} with a single column, the UUID of all {@link Book}.
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    public Cursor fetchBookUuidList() {
        return mSyncedDb.rawQuery("SELECT " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS, null);
    }

    /**
     * @param isbn to search for
     *
     * @return book id, or 0 if not found
     */
    public long getIdFromIsbn(@NonNull final String isbn,
                              final boolean checkBothIsbn10and13) {
        SynchronizedStatement stmt;
        if (checkBothIsbn10and13 && IsbnUtils.isValid(isbn)) {
            if (mGetIdFromIsbn2Query == null) {
                mGetIdFromIsbn2Query = mStatements.add("mGetIdFromIsbn2Query",
                                                       SqlGetId.BOOK_ID_BY_ISBN2);
            }
            stmt = mGetIdFromIsbn2Query;
            stmt.bindString(2, IsbnUtils.isbn2isbn(isbn));
        } else {
            if (mGetIdFromIsbn1Query == null) {
                mGetIdFromIsbn1Query = mStatements.add("mGetIdFromIsbn1Query",
                                                       SqlGetId.BOOK_ID_BY_ISBN);
            }
            stmt = mGetIdFromIsbn1Query;
        }
        stmt.bindString(1, isbn);
        return stmt.simpleQueryForLongOrZero();
    }

    /**
     * Check that a book with the passed ID exists.
     *
     * @param bookId of the book
     *
     * @return <tt>true</tt> if exists
     */
    public boolean bookExists(final long bookId) {
        if (mCheckBookExistsStmt == null) {
            mCheckBookExistsStmt = mStatements.add(
                    "mCheckBookExistsStmt",
                    "SELECT COUNT(*) " + " FROM " + TBL_BOOKS
                            + " WHERE " + DOM_PK_ID + "=?");
        }
        mCheckBookExistsStmt.bindLong(1, bookId);
        return (1 == mCheckBookExistsStmt.simpleQueryForLongOrZero());
    }

    /**
     * Set a book as in need of backup if any ancillary data has changed.
     *
     * @param bookId of the book
     */
    private void setBookDirty(final long bookId) {
        if (mSetBooksDirtyStmt == null) {
            mSetBooksDirtyStmt = mStatements.add("mSetBooksDirtyStmt",
                                                 SqlSetLastUpdateDate.BOOK_ID);
        }
        mSetBooksDirtyStmt.bindLong(1, bookId);
        mSetBooksDirtyStmt.executeUpdateDelete();
    }

    /**
     * Set all books referencing a given author as dirty.
     */
    private void setBooksDirtyByAuthorId(final long id) {
        // set all related books based on anthology Author as dirty
        if (mSetBooksDirtyByAuthor1Stmt == null) {
            mSetBooksDirtyByAuthor1Stmt = mStatements.add("mSetBooksDirtyByAuthor1Stmt",
                                                          SqlSetLastUpdateDate.TOC_ENTRY_AUTHOR_ID);
        }
        mSetBooksDirtyByAuthor1Stmt.bindLong(1, id);
        mSetBooksDirtyByAuthor1Stmt.executeUpdateDelete();

        // set all books of this Author dirty
        if (mSetBooksDirtyByAuthor2Stmt == null) {
            mSetBooksDirtyByAuthor2Stmt = mStatements.add("mSetBooksDirtyByAuthor2Stmt",
                                                          SqlSetLastUpdateDate.BY_AUTHOR_ID);
        }
        mSetBooksDirtyByAuthor2Stmt.bindLong(1, id);
        mSetBooksDirtyByAuthor2Stmt.executeUpdateDelete();
    }

    /**
     * Set all books referencing a given series as dirty.
     */
    private void setBooksDirtyBySeriesId(final long id) {
        if (mSetBooksDirtyBySeriesStmt == null) {
            mSetBooksDirtyBySeriesStmt = mStatements.add("mSetBooksDirtyBySeriesStmt",
                                                         SqlSetLastUpdateDate.BY_SERIES_ID);
        }
        mSetBooksDirtyBySeriesStmt.bindLong(1, id);
        mSetBooksDirtyBySeriesStmt.executeUpdateDelete();
    }

    /**
     * Set all books referencing a given bookshelf as dirty.
     */
    private void setBooksDirtyByBookshelfId(final long id) {
        if (mSetBooksDirtyByBookshelfStmt == null) {
            mSetBooksDirtyByBookshelfStmt = mStatements.add("mSetBooksDirtyByBookshelfStmt",
                                                            SqlSetLastUpdateDate.BY_BOOKSHELF_ID);
        }
        mSetBooksDirtyByBookshelfStmt.bindLong(1, id);
        mSetBooksDirtyByBookshelfStmt.executeUpdateDelete();
    }

    /**
     * Create the link between {@link Book} and {@link Series}.
     *
     * {@link DatabaseDefinitions#TBL_BOOK_SERIES}
     *
     * Note that {@link DatabaseDefinitions#DOM_BOOK_SERIES_POSITION} is a simple incrementing
     * counter matching the order of the passed list.
     *
     * @param bookId               the book which
     * @param list                 belongs to this *ordered* list of {@link Series}
     * @param dirtyBookIfNecessary flag to set book dirty or not
     */
    private void insertBookSeries(final long bookId,
                                  @NonNull final List<Series> list,
                                  final boolean dirtyBookIfNecessary) {
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
                                                    SqlDelete.BOOK_SERIES_BY_BOOK_ID);
        }
        mDeleteBookSeriesStmt.bindLong(1, bookId);
        mDeleteBookSeriesStmt.executeUpdateDelete();

        // anything to insert ?
        if (list.size() == 0) {
            return;
        }

        if (mAddBookSeriesStmt == null) {
            mAddBookSeriesStmt = mStatements.add(
                    "mAddBookSeriesStmt",
                    "INSERT INTO " + TBL_BOOK_SERIES
                            + '(' + DOM_FK_BOOK_ID
                            + ',' + DOM_FK_SERIES_ID
                            + ',' + DOM_BOOK_SERIES_NUM
                            + ',' + DOM_BOOK_SERIES_POSITION
                            + ") VALUES(?,?,?,?)");
        }

        /*
        TEST: is this still true ?
        mAddBookSeriesStmt.bindLong(1, bookId);
        can in theory be outside of the for loop. This was once good enough,
        but Android 4 (at least) causes the bindings to clean when executed.
        So now we do it each time in loop.

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
        for (Series series : list) {
            long seriesId = 0;
            try {
                seriesId = getSeriesIdByName(series.name);
                if (seriesId == 0) {
                    seriesId = insertSeries(series);
                    if (seriesId < 0) {
                        // it's unlikely, but *if* it happens, let's log it
                        Logger.error("insert failed");
                    }
                }

                String uniqueId = seriesId + '(' + series.getNumber().toUpperCase() + ')';
                if (!idHash.containsKey(uniqueId)) {
                    idHash.put(uniqueId, true);
                    pos++;
                    mAddBookSeriesStmt.bindLong(1, bookId);
                    mAddBookSeriesStmt.bindLong(2, seriesId);
                    mAddBookSeriesStmt.bindString(3, series.getNumber());
                    mAddBookSeriesStmt.bindLong(4, pos);
                    mAddBookSeriesStmt.executeInsert();
                }
            } catch (RuntimeException e) {
                Logger.error(e);
                throw new DBExceptions.InsertException(
                        "Error adding series '" + series.name + "' {" + seriesId + '}'
                                + " to book " + bookId + ": " + e.getLocalizedMessage(), e);
            }
        }
    }

    /**
     * Insert a List of TOCEntries.
     *
     * @param bookId               of the book
     * @param list                 the list
     * @param dirtyBookIfNecessary flag to set book dirty or not
     */
    private void insertOrUpdateTOC(final long bookId,
                                   @NonNull final List<TOCEntry> list,
                                   final boolean dirtyBookIfNecessary) {
        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        if (dirtyBookIfNecessary) {
            setBookDirty(bookId);
        }

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        @SuppressWarnings("UnusedAssignment")
        int rowsAffected = deleteTOCEntriesByBookId(bookId, false);
        if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
            Logger.info(this, "deleteTOCEntriesByBookId: bookId=" + bookId
                    + ", rowsAffected=" + rowsAffected);
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
            long tocId = insertOrUpdateTOCEntry(authorId, tocEntry.getTitle(),
                                                tocEntry.getFirstPublication());

            // create the book<->TOCEntry link
            @SuppressWarnings("UnusedAssignment")
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
     * Create the link between {@link Book} and {@link Author}.
     * <p>
     * {@link DatabaseDefinitions#TBL_BOOK_AUTHOR}
     * <p>
     * Note that {@link DatabaseDefinitions#DOM_BOOK_AUTHOR_POSITION} is a simple incrementing
     * counter matching the order of the passed list.
     *
     * @param bookId               the book which
     * @param list                 belongs to this *ordered* list of {@link Author}
     * @param dirtyBookIfNecessary flag to set book dirty or not
     */
    private void insertBookAuthors(final long bookId,
                                   @NonNull final List<Author> list,
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
                                                     SqlDelete.BOOK_AUTHOR_BY_BOOK_ID);
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
                                                          + '(' + DOM_FK_BOOK_ID
                                                          + ',' + DOM_FK_AUTHOR_ID
                                                          + ',' + DOM_BOOK_AUTHOR_POSITION
                                                          + ") VALUES(?,?,?)");
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
                String authorIdStr = String.valueOf(authorId);
                if (!idHash.containsKey(authorIdStr)) {
                    // indicate this author(id) is already present...
                    // but override, so we get elimination of duplicates.
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
            } catch (RuntimeException e) {
                Logger.error(e);
                throw new DBExceptions.InsertException(
                        "Error adding author '" + author + "' {" + authorId + '}'
                                + " to book " + bookId + ": " + e.getLocalizedMessage(), e);
            }
        }
    }

    /**
     * Create the link between {@link Book} and {@link Bookshelf}.
     *
     * {@link DatabaseDefinitions#TBL_BOOK_BOOKSHELF}
     *
     * Note that {@link DatabaseDefinitions#DOM_BOOK_SERIES_POSITION} is a simple incrementing
     * counter matching the order of the passed list.
     *
     * @param bookId               the book which
     * @param list                 belongs to this *ordered* list of {@link Bookshelf}
     * @param dirtyBookIfNecessary flag to set book dirty or not
     */
    private void insertBookBookshelf(final long bookId,
                                     @NonNull final List<Bookshelf> list,
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
                                                       SqlDelete.BOOK_BOOKSHELF_BY_BOOK_ID);
        }
        mDeleteBookBookshelfStmt.bindLong(1, bookId);
        mDeleteBookBookshelfStmt.executeUpdateDelete();

        // anything to insert ?
        if (list.size() == 0) {
            return;
        }

        if (mInsertBookBookshelfStmt == null) {
            mInsertBookBookshelfStmt = mStatements.add("mInsertBookBookshelfStmt",
                                    "INSERT INTO " + TBL_BOOK_BOOKSHELF
                                            + '(' + DOM_FK_BOOK_ID
                                            + ',' + DOM_BOOKSHELF
                                            + ") VALUES (?,?)");
        }

        for (Bookshelf bookshelf : list) {
            if (bookshelf.name.isEmpty()) {
                continue;
            }

            // ENHANCE: we cannot trust the id of Bookshelf for now
            long bookshelfId = getBookshelfIdByName(bookshelf.name);
            if (bookshelfId == 0) {
                bookshelfId = insertBookshelf(bookshelf);
            }

            if (bookshelfId == -1) {
                bookshelfId = Bookshelf.DEFAULT_ID;
            }

            try {
                mInsertBookBookshelfStmt.bindLong(1, bookId);
                mInsertBookBookshelfStmt.bindLong(2, bookshelfId);
                mInsertBookBookshelfStmt.executeInsert();
            } catch (RuntimeException e) {
                Logger.error(e, "Error assigning a book to a bookshelf");
            }
        }
    }

    /**
     * transaction: needs.
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

        // Update books but prevent duplicate index errors
        // update books for which the new ID is not already present
        String sql = "UPDATE " + table
                + " SET " + objectIdField + '=' + to
                + " WHERE " + objectIdField + '=' + from
                + " AND NOT EXISTS(SELECT NULL FROM " + table
                + " WHERE " + DOM_FK_BOOK_ID + '=' + DOM_FK_BOOK_ID
                + " AND " + objectIdField + '=' + to + ')';

        mSyncedDb.execSQL(sql);

        // Finally, delete the rows that would have caused duplicates. Be cautious by using the
        // EXISTS statement again; it's not necessary, but we do it to reduce the risk of data
        // loss if one of the prior statements failed silently.
        //
        // We also move remaining items up one place to ensure positions remain correct
        //
        sql = "SELECT * FROM " + table + " WHERE "
                + objectIdField + '=' + from
                + " AND EXISTS(SELECT NULL FROM " + table
                + " WHERE " + DOM_FK_BOOK_ID + '=' + table.dot(DOM_FK_BOOK_ID) + " AND "
                + objectIdField + '=' + to + ')';

        SynchronizedStatement delStmt = null;
        SynchronizedStatement replacementIdPosStmt = null;
        SynchronizedStatement checkMinStmt = null;
        SynchronizedStatement moveStmt = null;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            // Get the column indexes we need
            final int bookCol = cursor.getColumnIndexOrThrow(DOM_FK_BOOK_ID.name);
            final int posCol = cursor.getColumnIndexOrThrow(positionField);

            // Statement to delete a specific object record
            delStmt = mSyncedDb.compileStatement(
                    "DELETE FROM " + table
                            + " WHERE " + objectIdField + "=? AND " + DOM_FK_BOOK_ID + "=?");

            // Statement to get the position of the already-existing 'new/replacement' object
            replacementIdPosStmt = mSyncedDb.compileStatement(
                    "SELECT " + positionField + " FROM " + table
                            + " WHERE " + DOM_FK_BOOK_ID + "=? AND " + objectIdField + "=?");

            // Move statement; move a single entry to a new position
            moveStmt = mSyncedDb.compileStatement(
                    "UPDATE " + table + " SET " + positionField + "=?"
                            + " WHERE " + DOM_FK_BOOK_ID + "=? AND " + positionField + "=?");

            // Sanity check to deal with legacy bad data
            checkMinStmt = mSyncedDb.compileStatement(
                    "SELECT" + " min(" + positionField + ')'
                            + " FROM " + table + " WHERE " + DOM_FK_BOOK_ID + "=?");

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

                // If the deleted object was more prominent than the new object,
                // move the new one up
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
            ColumnMapper mapper = new ColumnMapper(cursor,
                                                   DOM_FK_AUTHOR_ID,
                                                   DOM_AUTHOR_FAMILY_NAME,
                                                   DOM_AUTHOR_GIVEN_NAMES,
                                                   DOM_AUTHOR_IS_COMPLETE,
                                                   DOM_PK_ID,
                                                   DOM_TITLE,
                                                   DOM_FIRST_PUBLICATION);
            //DOM_BOOK_TOC_ENTRY_POSITION

            while (cursor.moveToNext()) {
                Author author = new Author(mapper.getLong(DOM_FK_AUTHOR_ID),
                                           mapper.getString(DOM_AUTHOR_FAMILY_NAME),
                                           mapper.getString(DOM_AUTHOR_GIVEN_NAMES),
                                           mapper.getBoolean(DOM_AUTHOR_IS_COMPLETE));

                list.add(new TOCEntry(mapper.getLong(DOM_PK_ID),
                                      author,
                                      mapper.getString(DOM_TITLE),
                                      mapper.getString(DOM_FIRST_PUBLICATION)));
            }
        }
        return list;
    }

    /**
     * Get a list of book id's (most often just the one) in which this TOCEntry (story) is present.
     *
     * @param tocId id of the entry (story)
     *
     * @return id-of-book list
     *
     * ENHANCE: we use Integer here, but a primary key can be 8 bytes, i.e. a Long
     * On the other hand, the autoincrement reaching 33 bit long values....
     */
    public ArrayList<Integer> getBookIdsByTOCEntry(final long tocId) {
        ArrayList<Integer> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlGetId.BOOK_ID_BY_TOC_ENTRY_ID,
                                                new String[]{String.valueOf(tocId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getInt(0));
            }
        }
        return list;
    }

    /**
     * Get a list of the authors for a book.
     *
     * @param bookId of the book
     *
     * @return list of authors
     */
    @NonNull
    public ArrayList<Author> getBookAuthorList(final long bookId) {
        ArrayList<Author> list = new ArrayList<>();
        try (Cursor cursor = fetchAuthorsByBookId(bookId)) {
            if (cursor.getCount() == 0) {
                return list;
            }
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_AUTHORS);
            while (cursor.moveToNext()) {
                list.add(new Author(mapper.getLong(DOM_PK_ID),
                                    mapper.getString(DOM_AUTHOR_FAMILY_NAME),
                                    mapper.getString(DOM_AUTHOR_GIVEN_NAMES),
                                    mapper.getBoolean(DOM_AUTHOR_IS_COMPLETE)));
            }
        }
        return list;
    }

    /**
     * Get a list of the series a book belongs to.
     *
     * @param bookId of the book
     *
     * @return list of series
     */
    @NonNull
    public ArrayList<Series> getBookSeriesList(final long bookId) {
        ArrayList<Series> list = new ArrayList<>();
        try (Cursor cursor = fetchAllSeriesByBookId(bookId)) {
            if (cursor.getCount() == 0) {
                return list;
            }
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_SERIES, DOM_BOOK_SERIES_NUM);
            while (cursor.moveToNext()) {
                list.add(new Series(mapper.getLong(DOM_PK_ID),
                                    mapper.getString(DOM_SERIES_NAME),
                                    mapper.getBoolean(DOM_SERIES_IS_COMPLETE),
                                    mapper.getString(DOM_BOOK_SERIES_NUM)));
            }
        }
        return list;
    }

    /*
     * Bad idea. Instead use: Book book = Book.getBook(mDb, bookId);
     * So you never get a null object!
     *
     * Leaving commented as a reminder
     *
     * @param bookId of the book
     *
     * @return the fully populated Book, or null if not found
     *
     * @see #fetchBookById(long) which allows a partial retrieval
     */
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
     * Return the SQL for a list of all books in the database.
     *
     * @param whereClause to add to books search criteria (without the keyword 'WHERE')
     *
     * @return A full piece of SQL to perform the search
     */
    @NonNull
    private String getAllBooksSql(@NonNull final String whereClause) {

        //TODO: redo this so the sql becomes static

        // there is no 'real' FROM table set. The 'from' is a combo of three sub-selects
        String fullSql = SELECT(
                "b.*",
                // these two SQL macros use "a." due to TBL_AUTHOR being "a." but that is
                // just a happy (on purpose) coincidence.
                // Here, the "a." refers to the JOIN "Get the 'default' author" (see below)
                SqlColumns.AUTHOR,
                SqlColumns.AUTHOR_FORMATTED_GIVEN_FIRST,
                "a." + DOM_FK_AUTHOR_ID + " AS " + DOM_FK_AUTHOR_ID,

                "Coalesce(s." + DOM_FK_SERIES_ID + ", 0) AS " + DOM_FK_SERIES_ID,
                "Coalesce(s." + DOM_SERIES_NAME + ", '') AS " + DOM_SERIES_NAME,
                "Coalesce(s." + DOM_BOOK_SERIES_NUM + ", '') AS " + DOM_BOOK_SERIES_NUM,

                // if in more then one series, concat " et al"
                " Case When " + COLUMN_ALIAS_NR_OF_SERIES + " < 2"
                        + " Then Coalesce(s." + DOM_SERIES_FORMATTED + ", '')"
                        + " Else " + DOM_SERIES_FORMATTED + "||' "
                        + BookCatalogueApp.getResString(R.string.and_others) + '\''
                        + " End AS " + DOM_SERIES_FORMATTED
        )
                + " FROM ("
                + "SELECT DISTINCT " + SqlColumns.BOOK + " FROM " + TBL_BOOKS.ref()
                + (!whereClause.isEmpty() ? " WHERE " + " (" + whereClause + ')' : "")
                + " ORDER BY lower(" + TBL_BOOKS.dot(DOM_TITLE) + ") " + COLLATION + " ASC"
                + ") b"

            // Get the 'default' author
                + " JOIN ("
                + SELECT(DOM_FK_AUTHOR_ID.name,
                         TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME),
                         TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES),
                         TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE),
                         SqlColumns.AUTHOR_FORMATTED,
                         TBL_BOOK_AUTHOR.dotAs(DOM_FK_BOOK_ID)
        )
                + " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + ") a ON a." + DOM_FK_BOOK_ID + "=b." + DOM_PK_ID
                + " AND a." + DOM_FK_AUTHOR_ID + "=b." + DOM_FK_AUTHOR_ID

                // Get the 'default' series
                + " LEFT OUTER JOIN ("
                + SELECT(DOM_FK_SERIES_ID.name,
                         TBL_SERIES.dotAs(DOM_SERIES_NAME),
                         TBL_SERIES.dotAs(DOM_SERIES_IS_COMPLETE),
                         TBL_BOOK_SERIES.dotAs(DOM_BOOK_SERIES_NUM),
                         TBL_BOOK_SERIES.dotAs(DOM_FK_BOOK_ID),
                         " Case When " + DOM_BOOK_SERIES_NUM + "=''"
                                 + " Then " + DOM_SERIES_NAME
                                 + " Else " + DOM_SERIES_NAME + "||' #'||" + DOM_BOOK_SERIES_NUM
                                 + " End AS " + DOM_SERIES_FORMATTED
        )
                + " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + ") s ON s." + DOM_FK_BOOK_ID + "=b." + DOM_PK_ID
                + " AND s." + DOM_FK_SERIES_ID + "=b." + DOM_FK_SERIES_ID
                + " AND lower(s." + DOM_BOOK_SERIES_NUM + ")=lower(b." + DOM_BOOK_SERIES_NUM + ')'
                + COLLATION;

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
    public BookCursor fetchBookById(final long bookId)
        throws DBExceptions.NotFoundException {
        return fetchBooksWhere(TBL_BOOKS.dot(DOM_PK_ID) + "=?",
                               new String[]{String.valueOf(bookId)}, "");
    }

    /**
     * The passed sql should at least return some of the fields from the books table.
     * If a method call is made to retrieve a column that does not exists, an exception
     * will be thrown.
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    private BookCursor fetchBooks(@NonNull final String sql,
                                  @Nullable final String[] selectionArgs) {
        return (BookCursor) mSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY, sql, selectionArgs,
                                                          "");
    }

    /**
     * Return a {@link BookCursor} for the given whereClause.
     *
     * @param whereClause to add to books search criteria (without the keyword 'WHERE')
     * @param order       What order to return the books in (without the keyword 'ORDER BY')
     *                    TODO: check if order by is really needed/used
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooksWhere(@NonNull final String whereClause,
                                      @Nullable final String[] selectionArgs,
                                      @NonNull final String order) {
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
    public BookCursor fetchBooksByIsbnList(@NonNull final List<String> isbnList) {
        if (isbnList.size() == 0) {
            throw new IllegalArgumentException("isbnList was empty");
        }

        StringBuilder where = new StringBuilder(TBL_BOOKS.dot(DOM_BOOK_ISBN));
        if (isbnList.size() == 1) {
            where.append("='").append(encodeString(isbnList.get(0))).append('\'');
        } else {
            where.append(" IN (")
                 .append(Csv.csv(",", isbnList, new Csv.Formatter<String>() {
                     @Override
                     public String format(@NonNull final String element) {
                         return '\'' + encodeString(element) + '\'';
                     }
                 }))
                 .append(')');
        }
        return fetchBooksWhere(where.toString(), null, "");
    }

    /**
     * A complete export of all tables (flattened) in the database.
     *
     * @param sinceDate to select all books added/modified since that date.
     *                 Set to null for *ALL* books.
     *
     * @return BookCursor over all books, authors, etc
     */
    @NonNull
    public BookCursor fetchFlattenedBooks(@Nullable final Date sinceDate) {
        String whereClause;
        if (sinceDate == null) {
            whereClause = "";
        } else {
            whereClause = " WHERE " + TBL_BOOKS.dot(DOM_LAST_UPDATE_DATE)
                    + ">'" + DateUtils.utcSqlDateTime(sinceDate) + '\'';
        }

        String sql = SELECT_DISTINCT(
                SqlColumns.BOOK,
                TBL_BOOK_LOANEE.dotAs(DOM_LOANEE))
                + " FROM " + TBL_BOOKS.ref() + " LEFT OUTER JOIN " + TBL_BOOK_LOANEE.ref()
                + " ON (" + TBL_BOOK_LOANEE.dot(DOM_FK_BOOK_ID) + '=' + TBL_BOOKS.dot(
                DOM_PK_ID) + ')'
                + whereClause
                + " ORDER BY " + TBL_BOOKS.dot(DOM_PK_ID);
        return fetchBooks(sql, null);
    }

    /**
     * Creates a new bookshelf in the database.
     *
     * @param bookshelf object to insert. Will be updated with the id.
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insertBookshelf(@NonNull final Bookshelf /* in/out */ bookshelf) {
        // TODO: Decide if we need to backup EMPTY bookshelves...
        ContentValues cv = new ContentValues();
        cv.put(DOM_BOOKSHELF.name, bookshelf.name);
        long id = mSyncedDb.insert(TBL_BOOKSHELF.getName(), null, cv);
        if (id > 0) {
            bookshelf.id = id;
        }
        return id;
    }

    /**
     * Take all books from Bookshelf 'sourceId', and put them onto Bookshelf 'destId',
     * then delete Bookshelf 'sourceId'.
     *
     * @throws DBExceptions.UpdateException if the update failed
     */
    public void mergeBookshelves(final long sourceId,
                                 final long destId)
            throws DBExceptions.UpdateException {

        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            // 'move' the books
            String sql = "UPDATE " + TBL_BOOK_BOOKSHELF
                    + " SET " + DOM_FK_BOOKSHELF_ID + '=' + destId
                    + " WHERE " + DOM_FK_BOOKSHELF_ID + '=' + sourceId;
            mSyncedDb.execSQL(sql);

            // delete the now empty shelf.
            deleteBookshelf(sourceId);

            mSyncedDb.setTransactionSuccessful();
        } catch (RuntimeException e) {
            Logger.error(e);
            throw new DBExceptions.UpdateException(e);
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
    }

    /**
     * Return a Cursor positioned at the bookshelf that matches the given rowId.
     *
     * @param id of bookshelf to retrieve
     *
     * @return Name of bookshelf
     *
     * @throws SQLiteDoneException if zero rows found
     */
    @NonNull
    public String getBookshelfName(final long id)
        throws SQLiteDoneException {
        if (mGetBookshelfNameQuery == null) {
            mGetBookshelfNameQuery = mStatements.add("mGetBookshelfNameQuery",
                                                     SqlGetName.BOOKSHELF_NAME_BY_ID);
        }
        mGetBookshelfNameQuery.bindLong(1, id);
        return mGetBookshelfNameQuery.simpleQueryForString();
    }

    /**
     * Delete the bookshelf with the given rowId.
     *
     * Transaction: participate, or run in new.
     *
     * @param id id of bookshelf to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteBookshelf(final long id) {
        int rowsAffected;

        SyncLock txLock = null;
        if (!mSyncedDb.inTransaction()) {
            txLock = mSyncedDb.beginTransaction(true);
        }

        try {
            if (mDeleteBookBookshelf == null) {
                mDeleteBookBookshelf = mStatements.add("mDeleteBookBookshelf",
                                                       SqlDelete.BOOK_BOOKSHELF_BY_BOOKSHELF_ID);
            }
            mDeleteBookBookshelf.bindLong(1, id);
            rowsAffected = mDeleteBookBookshelf.executeUpdateDelete();

            boolean dirty = (0 < rowsAffected);

            if (mDeleteBookshelf == null) {
                mDeleteBookshelf = mStatements.add("mDeleteBookshelf",
                                                   SqlDelete.BOOKSHELF_BY_ID);
            }
            mDeleteBookshelf.bindLong(1, id);
            rowsAffected = mDeleteBookshelf.executeUpdateDelete();

            if (dirty || rowsAffected > 0) {
                setBooksDirtyByBookshelfId(id);
            }

            if (txLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
        } catch (RuntimeException e) {
            Logger.error(e);
            throw new DBExceptions.UpdateException(e);
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
        return rowsAffected;
    }

    /**
     * Return the bookshelf id based on the name.
     *
     * @param name bookshelf to search for
     *
     * @return bookshelf id, or 0 when not found
     */
    public long getBookshelfIdByName(@NonNull final String name) {
        if (mGetBookshelfIdByNameQuery == null) {
            mGetBookshelfIdByNameQuery = mStatements.add("mGetBookshelfIdByNameQuery",
                                                         SqlGetId.BOOKSHELF_ID_BY_NAME);
        }
        mGetBookshelfIdByNameQuery.bindString(1, name);
        return mGetBookshelfIdByNameQuery.simpleQueryForLongOrZero();
    }

    /**
     * @param name of bookshelf to find
     *
     * @return the Bookshelf, or null if not found
     */
    @Nullable
    public Bookshelf getBookshelfByName(@NonNull final String name) {
        long id = getBookshelfIdByName(name);
        if (id == 0) {
            return null;
        }
        return new Bookshelf(id, name);
    }

    /**
     * @param id of bookshelf to find
     *
     * @return the Bookshelf, or null if not found
     */
    @Nullable
    public Bookshelf getBookshelf(final long id) {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.BOOKSHELF_BY_ID,
                                                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Bookshelf(id, cursor.getString(0));
            }
        }
        return null;
    }

    /**
     * Update a bookshelf.
     *
     * @param bookshelf to update
     *
     * @return rows affected, should be 1 for success
     *
     * @throws DBExceptions.UpdateException on failure to update
     */
    @SuppressWarnings("UnusedReturnValue")
    public int updateBookshelf(@NonNull final Bookshelf bookshelf)
        throws DBExceptions.UpdateException {

        ContentValues cv = new ContentValues();
        cv.put(DOM_BOOKSHELF.name, bookshelf.name);

        int rowsAffected = mSyncedDb.update(TBL_BOOKSHELF.getName(), cv,
                                            DOM_PK_ID + "=?",
                                            new String[]{String.valueOf(bookshelf.id)});

        if (rowsAffected == 1) {
            setBooksDirtyByBookshelfId(bookshelf.id);
            return 1;
        } else {
            throw new DBExceptions.UpdateException();
        }
    }

    /**
     * Add or update the passed Bookshelf, depending whether bookshelf.id == 0.
     *
     * @param bookshelf object to insert or update. Will be updated with the id.
     *
     * @return insert: the row ID of the newly inserted Bookshelf,
     * or -1 if an error occurred during insert
     * update: the existing Bookshelf id
     *
     * @throws DBExceptions.UpdateException if the update failed
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertOrUpdateBookshelf(@NonNull final /* in/out */ Bookshelf bookshelf)
        throws DBExceptions.UpdateException {

        if (bookshelf.id != 0) {
                updateBookshelf(bookshelf);
        } else {
            bookshelf.id = insertBookshelf(bookshelf);
        }
        return bookshelf.id;
    }

    /**
     * Returns a list of all bookshelves in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<Bookshelf> getBookshelves() {
        ArrayList<Bookshelf> list = new ArrayList<>();

        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.BOOKSHELVES, null)) {
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(cursor.getLong(0), cursor.getString(1)));
            }
        }
        return list;
    }

    /**
     * Return a {@link Cursor} over the list of all {@link Bookshelf}
     * for the given {@link Book} id.
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    public Cursor fetchBookshelvesByBookId(final long bookId) {
        return mSyncedDb.rawQuery(SqlSelectList.BOOKSHELVES_BY_BOOK_ID,
                                  new String[]{String.valueOf(bookId)});
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
     * @return a list of all defined styles in the database
     */
    @NonNull
    public Map<Long, String> getBooklistStyles() {
        Map<Long, String> list = new LinkedHashMap<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.BOOKLIST_STYLES, null)) {
            ColumnMapper mapper = new ColumnMapper(cursor, DOM_PK_ID, DOM_UUID);
            while (cursor.moveToNext()) {
                list.put(mapper.getLong(DOM_PK_ID), mapper.getString(DOM_UUID));
            }
        }
        return list;
    }

    /**
     * Get the id of a Style with matching UUID.
     *
     * @param uuid to find
     *
     * @return id
     */
    public long getBooklistStyleIdByUuid(@NonNull final String uuid) {
        if (mGetBooklistStyleStmt == null) {
            mGetBooklistStyleStmt = mStatements.add("mGetBooklistStyleStmt",
                                                    SqlGetId.BOOKLIST_STYLE_ID_BY_UUID);
        }
        mGetBooklistStyleStmt.bindString(1, uuid);
        return mGetBooklistStyleStmt.simpleQueryForLongOrZero();
    }

    /**
     * Create a new booklist style.
     *
     * @param style object to insert. Will be updated with the id.
     *
     * @return the row ID of the last row inserted, if this insert is successful. -1 otherwise.
     */
    public long insertBooklistStyle(@NonNull final BooklistStyle /* in/out */ style) {
        if (mInsertBooklistStyleStmt == null) {
            mInsertBooklistStyleStmt = mStatements.add(
                    "mInsertBooklistStyleStmt",
                          TBL_BOOKLIST_STYLES.getInsert(true, DOM_UUID));
        }
        mInsertBooklistStyleStmt.bindString(1, style.getUuid());
        long id = mInsertBooklistStyleStmt.executeInsert();
        if (id > 0) {
            style.setId(id);
        }
        return id;
    }

    public void deleteBooklistStyle(final long id) {
        if (mDeleteBooklistStyleStmt == null) {
            mDeleteBooklistStyleStmt = mStatements.add("mDeleteBooklistStyleStmt",
                                                       SqlDelete.STYLE_BY_ID);
        }
        mDeleteBooklistStyleStmt.bindLong(1, id);
        mDeleteBooklistStyleStmt.executeUpdateDelete();
    }

    /**
     * Return a {@link Cursor} for the given query string.
     *
     * @param query string
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    public Cursor fetchSearchSuggestions(@NonNull final String query) {
        String q = query + '%';
        return mSyncedDb.rawQuery(SqlSelect.SEARCH_SUGGESTIONS, new String[]{q, q, q, q});
    }

    /**
     * Returns a unique list of all currencies in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getCurrencyCodes(@NonNull final String type) {
        String column;
        if (UniqueId.KEY_BOOK_PRICE_LISTED_CURRENCY.equals(type)) {
            column = DOM_BOOK_PRICE_LISTED_CURRENCY.name;
//        } else if (UniqueId.KEY_BOOK_PRICE_PAID_CURRENCY.equals(type)) {
        } else {
            column = DOM_BOOK_PRICE_PAID_CURRENCY.name;
        }

        String sql = "SELECT DISTINCT " + column + " FROM " + TBL_BOOKS
                + " ORDER BY lower(" + column + ") " + COLLATION;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            ArrayList<String> list = getFirstColumnAsList(cursor);
            if (list.isEmpty()) {
                // sure, this is very crude and discriminating.
                // But it will only ever be used *once* per currency column
                list.add("EUR");
                list.add("GBP");
                list.add("USD");
            }
            return list;
        }
    }

    /**
     * Returns a unique list of all formats in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getFormats() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.FORMATS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    public void globalReplaceFormat(@NonNull final String from,
                                    @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        // Update books but prevent duplicate index errors
        if (mGlobalReplaceFormat == null) {
            mGlobalReplaceFormat = mStatements.add("mGlobalReplaceFormat",
                                                   SqlGlobalReplace.FORMAT);
        }
        mGlobalReplaceFormat.bindString(1, to);
        mGlobalReplaceFormat.bindString(2, from);
        mGlobalReplaceFormat.executeUpdateDelete();
    }

    /**
     * Returns a unique list of all genres in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getGenres() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.GENRES, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    public void globalReplaceGenre(@NonNull final String from,
                                   @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        // Update books but prevent duplicate index errors
        if (mGlobalReplaceGenre == null) {
            mGlobalReplaceGenre = mStatements.add("mGlobalReplaceGenre",
                                                  SqlGlobalReplace.GENRE);
        }
        mGlobalReplaceGenre.bindString(1, to);
        mGlobalReplaceGenre.bindString(2, from);
        mGlobalReplaceGenre.executeUpdateDelete();
    }

    /**
     * Returns a unique list of all languages in the database.
     *
     * @return The list; normally all ISO3 codes
     */
    @NonNull
    public ArrayList<String> getLanguageCodes() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.LANGUAGES, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Returns a unique list of all languages in the database.
     *
     * @return The list; expanded full displayName's in the current Locale
     */
    @NonNull
    public ArrayList<String> getLanguages() {
        ArrayList<String> names = new ArrayList<>();
        for (String code : getLanguageCodes()) {
            names.add(LocaleUtils.getDisplayName(code));
        }

        return names;
    }

    public void globalReplaceLanguage(@NonNull final String from,
                                      @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        // Update books but prevent duplicate index errors
        if (mGlobalReplaceLanguage == null) {
            mGlobalReplaceLanguage = mStatements.add("mGlobalReplaceLanguage",
                                                     SqlGlobalReplace.LANGUAGE);
        }
        mGlobalReplaceLanguage.bindString(1, to);
        mGlobalReplaceLanguage.bindString(2, from);
        mGlobalReplaceLanguage.executeUpdateDelete();
    }

    /**
     * Get the name of the loanee for a given book, if any.
     *
     * @param bookId book to search for
     *
     * @return Who the book is loaned to, null when not loaned to someone
     */
    @Nullable
    public String getLoaneeByBookId(final long bookId) {


        if (mGetLoaneeByBookIdQuery == null) {
            mGetLoaneeByBookIdQuery = mStatements.add("mGetLoaneeByBookIdQuery",
                                                      SqlGetName.LOANEE_BY_BOOK_ID);
        }

        mGetLoaneeByBookIdQuery.bindLong(1, bookId);
        return mGetLoaneeByBookIdQuery.simpleQueryForStringOrNull();
    }

    /**
     * Creates a new loan in the database.
     *
     * @param book                 A collection with the columns to be set. May contain extra data.
     * @param dirtyBookIfNecessary flag to set book dirty or not
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertLoan(@NonNull final Book book,
                           final boolean dirtyBookIfNecessary) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_FK_BOOK_ID.name, book.getBookId());
        cv.put(DOM_LOANEE.name, book.getString(DOM_LOANEE.name));

        long newId = mSyncedDb.insert(TBL_BOOK_LOANEE.getName(), null, cv);
        if (newId > 0) {
            purgeLoans();
            if (dirtyBookIfNecessary) {
                setBookDirty(book.getBookId());
            }
        }
        return newId;
    }


    /**
     * Delete the loan with the given rowId.
     *
     * @param bookId id of book whose loan is to be deleted
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteLoan(final long bookId,
                          final boolean dirtyBookIfNecessary) {
        if (mDeleteBookLoan == null) {
            mDeleteBookLoan = mStatements.add("mDeleteBookLoan",
                                              SqlDelete.BOOK_LOANEE_BY_BOOK_ID);
        }
        mDeleteBookLoan.bindLong(1, bookId);
        int rowsAffected = mDeleteBookLoan.executeUpdateDelete();

        if (rowsAffected > 0) {
            purgeLoans();
            if (dirtyBookIfNecessary) {
                setBookDirty(bookId);
            }
        }
        return rowsAffected;
    }

    /**
     * Purge loans for non-existent books.
     *
     * {@link DatabaseDefinitions#TBL_BOOK_LOANEE}
     */
    private void purgeLoans() {
//        if (mPurgeLoans == null) {
//            mPurgeLoans = mStatements.add("mPurgeLoans", SqlDelete.PURGE_BOOK_LOANEE);
//        }
//        mPurgeLoans.executeUpdateDelete();
    }

    /**
     * @return a unique list of all locations in the database
     */
    @NonNull
    public ArrayList<String> getLocations() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.LOCATIONS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Rename a Location.
     */
    public void globalReplaceLocation(@NonNull final String from,
                                      @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        // Update books but prevent duplicate index errors
        if (mGlobalReplaceLocation == null) {
            mGlobalReplaceLocation = mStatements.add("mGlobalReplaceLocation",
                                                     SqlGlobalReplace.LOCATION);
        }
        mGlobalReplaceLocation.bindString(1, to);
        mGlobalReplaceLocation.bindString(2, from);
        mGlobalReplaceLocation.executeUpdateDelete();
    }

    /**
     * Returns a unique list of all publishers in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getPublisherNames() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.PUBLISHERS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Rename a Publisher.
     */
    public void globalReplacePublisher(@NonNull final Publisher from,
                                       @NonNull final Publisher to) {
        if (Objects.equals(from, to)) {
            return;
        }
        // Update books but prevent duplicate index errors
        if (mGlobalReplacePublisher == null) {
            mGlobalReplacePublisher = mStatements.add("mGlobalReplacePublisher",
                                                      SqlGlobalReplace.PUBLISHER);
        }
        mGlobalReplacePublisher.bindString(1, to.getName());
        mGlobalReplacePublisher.bindString(2, from.getName());
        mGlobalReplacePublisher.executeUpdateDelete();
    }

    /**
     * Creates a new series in the database.
     *
     * @param series object to insert. Will be updated with the id.
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    private long insertSeries(@NonNull final Series series) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_SERIES_NAME.name, series.name);
        cv.put(DOM_SERIES_IS_COMPLETE.name, series.isComplete);
        long id = mSyncedDb.insert(TBL_SERIES.getName(), null, cv);
        if (id > 0) {
            series.id = id;
        }
        return id;
    }

    /**
     * @param series to update
     *
     * @return rows affected, should be 1 for success
     *
     * @throws DBExceptions.UpdateException on failure to update
     */
    public int updateSeries(@NonNull final Series series)
        throws DBExceptions.UpdateException {

        ContentValues cv = new ContentValues();
        cv.put(DOM_SERIES_NAME.name, series.name);
        cv.put(DOM_SERIES_IS_COMPLETE.name, series.isComplete);

        int rowsAffected = mSyncedDb.update(TBL_SERIES.getName(), cv,
                                            DOM_PK_ID + "=?",
                                            new String[]{String.valueOf(series.id)});
        if (rowsAffected == 1) {
            setBooksDirtyBySeriesId(series.id);
            return 1;
        } else {
            throw new DBExceptions.UpdateException();
        }
    }

    /**
     * Add or update the passed Series, depending whether series.id == 0.
     *
     * @param series object to insert or update. Will be updated with the id.
     *
     * @return insert: the row ID of the newly inserted Series,
     * or -1 if an error occurred during insert
     * update: the existing Series id
     *
     * @throws DBExceptions.UpdateException if the update failed
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertOrUpdateSeries(@NonNull final /* in/out */ Series series)
        throws DBExceptions.UpdateException {

        if (series.id != 0) {
                updateSeries(series);
        } else {
            series.id = insertSeries(series);
        }
        return series.id;
    }

    /**
     * Delete the passed series.
     *
     * @param id series to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteSeries(final long id) {
        if (mDeleteSeries == null) {
            mDeleteSeries = mStatements.add("mDeleteSeries",
                                            SqlDelete.BOOK_SERIES_BY_SERIES_ID);
        }
        mDeleteSeries.bindLong(1, id);
        int rowsAffected = mDeleteSeries.executeUpdateDelete();

        if (rowsAffected > 0) {
            setBooksDirtyBySeriesId(id);
            purgeSeries();
        }
        return rowsAffected;
    }

    /**
     * This will return ALL series; mainly for the purpose of backups.
     *
     * @return Cursor over all series
     */
    @NonNull
    public Cursor fetchSeries() {
        return mSyncedDb.rawQuery("SELECT * FROM " + TBL_SERIES, null);
    }

    /**
     * Return the series based on the ID.
     *
     * @return series, or null when not found
     */
    @Nullable
    public Series getSeries(final long id) {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.SERIES_BY_ID,
                                                new String[]{String.valueOf(id)})) {
            return cursor.moveToFirst()
                   ? new Series(id, cursor.getString(0),
                                cursor.getInt(1) == 1, "")
                   : null;
        }
    }

    /**
     * @return the id, or 0 when not found
     */
    public long getSeriesId(@NonNull final Series s) {
        return getSeriesIdByName(s.name);
    }

    /**
     * @return the id, or 0 when not found
     */
    public long getSeriesIdByName(@NonNull final String name) {
        if (mGetSeriesIdQuery == null) {
            mGetSeriesIdQuery = mStatements.add("mGetSeriesIdQuery",
                                                SqlGetId.SERIES_ID_BY_NAME);
        }
        mGetSeriesIdQuery.bindString(1, name);
        return mGetSeriesIdQuery.simpleQueryForLongOrZero();
    }

    /**
     * @throws DBExceptions.UpdateException   on failure to insertOrUpdateSeries(to)
     * or on the global replace itself
     * @throws DBExceptions.NotFoundException on failure to find old series
     */
    public void globalReplaceSeries(@NonNull final Series from,
                                    @NonNull final Series to)
        throws DBExceptions.UpdateException, DBExceptions.NotFoundException {
        // Create or update the new series
        if (to.id == 0) {
            // try to find
            long id = getSeriesId(to);
            // If we have a match, just update the object and done
            if (id > 0) {
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
            setBooksDirtyBySeriesId(from.id);

            // Update books but prevent duplicate index errors
            if (mGlobalReplaceSeries == null) {
                mGlobalReplaceSeries = mStatements.add("mGlobalReplaceSeries",
                                                       SqlGlobalReplace.SERIES);
            }
            mGlobalReplaceSeries.bindLong(1, to.id);
            mGlobalReplaceSeries.bindLong(2, from.id);
            mGlobalReplaceSeries.bindLong(3, to.id);
            mGlobalReplaceSeries.executeUpdateDelete();

            globalReplacePositionedBookItem(TBL_BOOK_SERIES, DOM_FK_SERIES_ID.name,
                                            DOM_BOOK_SERIES_POSITION.name, from.id, to.id);

            mSyncedDb.setTransactionSuccessful();
        } catch (RuntimeException e) {
            Logger.error(e);
            throw new DBExceptions.UpdateException(e);
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
    }

    /**
     * Delete the series with no related books.
     *
     * {@link DatabaseDefinitions#TBL_BOOK_SERIES}
     * {@link DatabaseDefinitions#TBL_SERIES}
     */
    public void purgeSeries() {

//        if (mPurgeBookSeriesStmt == null) {
//            mPurgeBookSeriesStmt = mStatements.add("mPurgeBookSeriesStmt",
//                                                   SqlDelete.PURGE_BOOK_SERIES);
//        }
//        try {
//            mPurgeBookSeriesStmt.executeUpdateDelete();
//        } catch (RuntimeException e) {
//            Logger.error(e, FAILED_TO_PURGE_ERROR + TBL_BOOK_SERIES);
//        }
//
//        if (mPurgeSeriesStmt == null) {
//            mPurgeSeriesStmt = mStatements.add("mPurgeSeriesStmt",
//                                               SqlDelete.PURGE_SERIES);
//        }
//        try {
//            mPurgeSeriesStmt.executeUpdateDelete();
//        } catch (RuntimeException e) {
//            Logger.error(e, FAILED_TO_PURGE_ERROR + TBL_SERIES);
//        }
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
            mGetSeriesBookCountQuery = mStatements.add(
                    "mGetSeriesBookCountQuery",
                          "SELECT COUNT(" + DOM_FK_BOOK_ID + ')'
                                  + " FROM " + TBL_BOOK_SERIES
                                  + " WHERE " + DOM_FK_SERIES_ID + "=?");
        }
        // Be cautious
        synchronized (mGetSeriesBookCountQuery) {
            mGetSeriesBookCountQuery.bindLong(1, series.id);
            return mGetSeriesBookCountQuery.count();
        }
    }


    /**
     * Builds an arrayList of all series names.
     * Used for AutoCompleteTextView
     */
    @NonNull
    public ArrayList<String> getAllSeriesNames() {

        return getColumnAsList(SqlSelectFullTable.SERIES, DOM_SERIES_NAME.name);
    }

    /**
     * Return a {@link Cursor} over the list of all {@link Series}.
     * for the given {@link Book} id
     *
     * @param id of book
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    private Cursor fetchAllSeriesByBookId(final long id) {
        return mSyncedDb.rawQuery(SqlSelectList.SERIES_BY_BOOK_ID,
                                  new String[]{String.valueOf(id)});
    }

    /**
     * Set the Goodreads book id for this book.
     */
    public void setGoodreadsBookId(final long bookId,
                                   final long goodreadsBookId) {
        if (mSetGoodreadsBookIdStmt == null) {
            mSetGoodreadsBookIdStmt = mStatements.add("mSetGoodreadsBookIdStmt",
                                                      SqlUpdate.GOODREADS_BOOK_ID);
        }
        mSetGoodreadsBookIdStmt.bindLong(1, goodreadsBookId);
        mSetGoodreadsBookIdStmt.bindLong(2, bookId);
        mSetGoodreadsBookIdStmt.executeUpdateDelete();
    }

    /**
     * Return a {@link BookCursor} for the given Goodreads book Id.
     * Note: MAY RETURN MORE THAN ONE BOOK
     *
     * @param grBookId to retrieve
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooksByGoodreadsBookId(final long grBookId) {
        return fetchBooksWhere(TBL_BOOKS.dot(DOM_BOOK_GOODREADS_BOOK_ID) + "=?",
                               new String[]{String.valueOf(grBookId)}, "");
    }

    /**
     * Query to get relevant {@link Book} columns for sending a set of books to Goodreads.
     *
     * @param startId     the 'first' (e.g. 'oldest') bookId to get
     *                    since the last sync with Goodreads
     * @param updatesOnly true, if we only want the updated records
     *                    since the last sync with Goodreads
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooksForGoodreadsCursor(final long startId,
                                                   final boolean updatesOnly) {
        String sql = "SELECT " + SqlColumns.GOODREADS_FIELDS_FOR_SENDING
                + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + ">?";

        if (updatesOnly) {
            sql += " AND " + DOM_LAST_UPDATE_DATE + '>' + DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
        }

        // the order by is used to be able to restart an export.
        sql += " ORDER BY " + DOM_PK_ID;

        return fetchBooks(sql, new String[]{String.valueOf(startId)});
    }

    /**
     * Query to get the ISBN for the given {@link Book} id, for sending to Goodreads.
     *
     * @param bookId to retrieve
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBookForGoodreadsCursor(final long bookId) {
        return fetchBooks(SqlSelect.GOODREADS_GET_BOOK_TO_SEND_BY_BOOK_ID,
                          new String[]{String.valueOf(bookId)});
    }

    /**
     * Fills an array with the specified column from the passed SQL.
     *
     * @param sql        SQL to execute
     * @param columnName Column to fetch
     *
     * @return List of *all* values
     *
     * @see #getFirstColumnAsList
     */
    @NonNull
    private ArrayList<String> getColumnAsList(@NonNull final String sql,
                                              @NonNull final String columnName) {
        ArrayList<String> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            int column = cursor.getColumnIndexOrThrow(columnName);
            while (cursor.moveToNext()) {
                list.add(cursor.getString(column));
            }
            return list;
        }
    }

    /**
     * Takes the ResultSet from a Cursor, and fetches column 0 as a String into an ArrayList.
     *
     * @param cursor cursor
     *
     * @return List of unique values (case sensitive)
     *
     * @see #getColumnAsList
     */
    @NonNull
    private ArrayList<String> getFirstColumnAsList(@NonNull final Cursor cursor) {
        // Hash to avoid duplicates
        Set<String> set = new LinkedHashSet<>();
        while (cursor.moveToNext()) {
            String name = cursor.getString(0);
            if (name != null && !name.isEmpty()) {
                set.add(name);
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * Return a ContentValues collection containing only those values from 'source'
     * that match columns in 'dest'.
     * - Exclude the primary key from the list of columns.
     * - data will be transformed based on the intended type of the underlying column
     * based on column definition.
     *
     * @param tableName destination table
     * @param book      A collection with the columns to be set. May contain extra data.
     *
     * @return New and filtered ContentValues
     *
     * @throws NumberFormatException on parsing issues
     */
    @NonNull
    private ContentValues filterValues(@NonNull final String tableName,
                                       @NonNull final Book book)
        throws NumberFormatException {

        TableInfo table = new TableInfo(mSyncedDb, tableName);

        ContentValues cv = new ContentValues();
        // Create the arguments
        for (String key : book.keySet()) {
            // Get column info for this column.
            ColumnInfo columnInfo = table.getColumn(key);
            // Check if we actually have a matching column.
            if (columnInfo != null) {
                // Never update PK.
                if (!columnInfo.isPrimaryKey) {
                    // Try to set the appropriate value, but if that fails, just use TEXT...
                    Object entry = book.get(key);
                    try {
                        switch (columnInfo.storageClass) {
                            case Real:
                                if (entry instanceof Float) {
                                    cv.put(columnInfo.name, (Float) entry);
                                } else if (entry != null) {
                                    cv.put(columnInfo.name, Float.parseFloat(entry.toString()));
                                }
                                break;

                            case Integer:
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

                            case Text:
                                if (entry instanceof String) {
                                    cv.put(columnInfo.name, (String) entry);
                                } else if (entry != null) {
                                    cv.put(columnInfo.name, entry.toString());
                                }
                                break;

                            default:
                                Logger.error("" + ColumnInfo.StorageClass.Blob);
                                break;
                        }
                    } catch (@NonNull final NumberFormatException e) {
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
    private void ftsSendBooks(@NonNull final BookCursor books,
                              @NonNull final SynchronizedStatement stmt) {

        if (!mSyncedDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
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
        final BookRowView bookCursorRow = books.getCursorRow();
        while (books.moveToNext()) {
            // Reset authors/series/title
            authorText.setLength(0);
            seriesText.setLength(0);
            titleText.setLength(0);
            // Get list of authors
            try (Cursor c = mSyncedDb.rawQuery(SqlFTS.GET_AUTHORS_BY_BOOK_ID,
                                               new String[]{String.valueOf(
                                                   bookCursorRow.getId())})) {
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
                    authorText.append(' ');
                    authorText.append(c.getString(colFamilyName));
                    authorText.append(';');
                }
            }

            // Get list of series
            try (Cursor c = mSyncedDb.rawQuery(SqlFTS.GET_SERIES_BY_BOOK_ID,
                                               new String[]{String.valueOf(
                                                   bookCursorRow.getId())})) {
                // Get column indexes, if not already got
                if (colSeriesInfo < 0) {
                    colSeriesInfo = c.getColumnIndexOrThrow("seriesInfo");
                }
                // Append each series
                while (c.moveToNext()) {
                    seriesText.append(c.getString(colSeriesInfo));
                    seriesText.append(';');
                }
            }


            // Get list of anthology data (author and title)
            try (Cursor c = mSyncedDb.rawQuery(SqlFTS.GET_TOC_ENTRIES_BY_BOOK_ID,
                                               new String[]{String.valueOf(
                                                   bookCursorRow.getId())})) {
                // Get column indexes, if not already got
                if (colTOCEntryAuthorInfo < 0) {
                    colTOCEntryAuthorInfo = c.getColumnIndexOrThrow(
                            SqlFTS.DOM_TOC_ENTRY_AUTHOR_INFO);
                }
                if (colTOCEntryInfo < 0) {
                    colTOCEntryInfo = c.getColumnIndexOrThrow(SqlFTS.DOM_TOC_ENTRY_INFO);
                }
                // Append each series
                while (c.moveToNext()) {
                    authorText.append(c.getString(colTOCEntryAuthorInfo));
                    authorText.append(';');
                    titleText.append(c.getString(colTOCEntryInfo));
                    titleText.append(';');
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
     * Bind a string or NULL value to a parameter since binding a NULL
     * in bindString produces an error.
     *
     * NOTE: We specifically want to use the default locale for this.
     */
    private void bindStringOrNull(@NonNull final SynchronizedStatement stmt,
                                  final int position,
                                  @Nullable final String s) {
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
                // Build the FTS insert statement base. The parameter order MUST match
                // the order expected in {@link #ftsSendBooks}.
                mInsertFtsStmt = mStatements.add(
                        "mInsertFtsStmt",
                        TBL_BOOKS_FTS.getInsert(true,
                                                DOM_FTS_AUTHOR_NAME,
                                                DOM_TITLE,
                                                DOM_BOOK_DESCRIPTION,
                                                DOM_BOOK_NOTES,
                                                DOM_BOOK_PUBLISHER,
                                                DOM_BOOK_GENRE,
                                                DOM_BOOK_LOCATION,
                                                DOM_BOOK_ISBN,
                                                DOM_PK_DOCID));
            }

            try (BookCursor books = fetchBooks(
                    "SELECT * FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=? ",
                    new String[]{String.valueOf(bookId)})) {
                ftsSendBooks(books, mInsertFtsStmt);
            } finally {
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    Logger.info(this,
                                "Inserted FTS in " + (System.currentTimeMillis() - t0) + "ms");
                }
            }
        } catch (RuntimeException e) {
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
                // Build the FTS update statement base.
                // The parameter order MUST match the order expected in ftsSendBooks().
                mUpdateFtsStmt = mStatements.add("mUpdateFtsStmt",
                                                 TBL_BOOKS_FTS.getUpdate(
                                                     DOM_FTS_AUTHOR_NAME,
                                                     DOM_TITLE,
                                                     DOM_BOOK_DESCRIPTION,
                                                     DOM_BOOK_NOTES,
                                                     DOM_BOOK_PUBLISHER,
                                                     DOM_BOOK_GENRE,
                                                     DOM_BOOK_LOCATION,
                                                     DOM_BOOK_ISBN)
                                                         + " WHERE " + DOM_PK_DOCID + "=?");
            }

            try (BookCursor books = fetchBooks(
                "SELECT * FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?",
                new String[]{String.valueOf(bookId)})) {
                ftsSendBooks(books, mUpdateFtsStmt);
            } finally {
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    Logger.info(this,
                                "Updated FTS in " + (System.currentTimeMillis() - t0) + "ms");
                }
            }
        } catch (RuntimeException e) {
            Logger.error(e, "Failed to update FTS");
        }
    }

    /**
     * Delete an existing FTS record.
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
                                                 SqlFTS.DEL_BOOKS_BY_DOCID);
            }
            mDeleteFtsStmt.bindLong(1, bookId);
            mDeleteFtsStmt.executeUpdateDelete();
            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                Logger.info(this,
                            "Deleted FROM FTS in " + (System.currentTimeMillis() - t0) + "ms");
            }
        } catch (RuntimeException e) {
            Logger.error(e, "Failed to delete FTS");
        }
    }

    /**
     * Rebuild the entire FTS database.
     * This can take several seconds with many books or a slow phone.
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

        // Build the FTS update statement base.
        // The parameter order MUST match the order expected in ftsSendBooks().
        // reminder (to me) .. don't re-use in mStatements as the table is temporary.
        final String sql = ftsTemp.getInsert(true,
                                             DOM_FTS_AUTHOR_NAME,
                                             DOM_TITLE,
                                             DOM_BOOK_DESCRIPTION,
                                             DOM_BOOK_NOTES,
                                             DOM_BOOK_PUBLISHER,
                                             DOM_BOOK_GENRE,
                                             DOM_BOOK_LOCATION,
                                             DOM_BOOK_ISBN,
                                             DOM_PK_DOCID);

        // Drop and recreate our temp copy
        ftsTemp.create(mSyncedDb, false);
        ftsTemp.drop(mSyncedDb);

        try (SynchronizedStatement insert = mSyncedDb.compileStatement(sql);
             BookCursor books = fetchBooks("SELECT * FROM " + TBL_BOOKS, null)) {
            ftsSendBooks(books, insert);
            // Drop old table, ready for rename
            TBL_BOOKS_FTS.drop(mSyncedDb);

            mSyncedDb.setTransactionSuccessful();
        } catch (RuntimeException e) {
            Logger.error(e);
            gotError = true;
        } finally {
            mSyncedDb.endTransaction(txLock);
            /*
            http://sqlite.1065341.n5.nabble.com/Bug-in-FTS3-when-trying-to-rename-table-within-a-transaction-td11430.html
            FTS tables should only be renamed outside of transactions.
            */

            //  Delete old table and rename the new table
            if (!gotError) {
                mSyncedDb.execSQL("ALTER TABLE " + ftsTemp + " RENAME TO " + TBL_BOOKS_FTS);
            }
        }

        if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
            Logger.info(this,
                        "rebuildFts in " + (System.currentTimeMillis() - t0) + "ms");
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
    public Cursor searchFts(@NonNull String author,
                            @NonNull String title,
                            @NonNull String keywords) {
        author = cleanupFtsCriterion(author);
        title = cleanupFtsCriterion(title);
        keywords = cleanupFtsCriterion(keywords);

        if ((author.length() + title.length() + keywords.length()) == 0) {
            return null;
        }

        StringBuilder sql = new StringBuilder("SELECT " + DOM_PK_DOCID
                                                      + " FROM " + TBL_BOOKS_FTS
                                                      + " WHERE " + TBL_BOOKS_FTS
                                                      + " MATCH '" + keywords);

        for (String w : author.split(" ")) {
            if (!w.isEmpty()) {
                sql.append(' ').append(DOM_FTS_AUTHOR_NAME).append(':').append(w);
            }
        }
        for (String w : title.split(" ")) {
            if (!w.isEmpty()) {
                sql.append(' ').append(DOM_TITLE).append(':').append(w);
            }
        }
        sql.append('\'');

        return mSyncedDb.rawQuery(sql.toString(), null);
    }

    /**
     * DEBUG only.
     */
    private static class InstanceRefDebug
        extends WeakReference<DBA> {

        @NonNull
        private final Exception mCreationException;

        InstanceRefDebug(@NonNull final DBA db) {
            super(db);
            mCreationException = new RuntimeException();
        }

        @NonNull
        Exception getCreationException() {
            return mCreationException;
        }
    }


    /**
     * Commonly used SQL table columns.
     */
    private static final class SqlColumns {

        /**
         * set of fields suitable for a select of a Book.
         */
        private static final String BOOK =
                TBL_BOOKS.dotAs(DOM_PK_ID)
                        + ',' + TBL_BOOKS.dotAs(DOM_TITLE)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_ISBN)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PUBLISHER)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DATE_PUBLISHED)
                        + ',' + TBL_BOOKS.dotAs(DOM_FIRST_PUBLICATION)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_EDITION_BITMASK)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_RATING)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_READ)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PAGES)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_NOTES)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_LISTED)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_LISTED_CURRENCY)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_PAID)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_PAID_CURRENCY)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DATE_ACQUIRED)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_ANTHOLOGY_BITMASK)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_LOCATION)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_READ_START)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_READ_END)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_FORMAT)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_SIGNED)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DESCRIPTION)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_GENRE)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_LANGUAGE)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DATE_ADDED)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_LIBRARY_THING_ID)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_ISFDB_ID)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_GOODREADS_BOOK_ID)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_GOODREADS_LAST_SYNC_DATE)
                        + ',' + TBL_BOOKS.dotAs(DOM_LAST_UPDATE_DATE)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_UUID)

                        // Find FIRST series ID.
                        + ',' + "(SELECT " + DOM_FK_SERIES_ID + " FROM " + TBL_BOOK_SERIES.ref()
                        + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID)
                        + '=' + TBL_BOOKS.dot(DOM_PK_ID)
                        + " ORDER BY " + DOM_BOOK_SERIES_POSITION + " ASC  LIMIT 1)"
                        + " AS " + DOM_FK_SERIES_ID

                        // Find FIRST series NUM.
                        + ',' + "(SELECT " + DOM_BOOK_SERIES_NUM + " FROM " + TBL_BOOK_SERIES.ref()
                        + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID)
                        + '=' + TBL_BOOKS.dot(DOM_PK_ID)
                        + " ORDER BY " + DOM_BOOK_SERIES_POSITION + " ASC  LIMIT 1)"
                        + " AS " + DOM_BOOK_SERIES_NUM

                        // Get the total series count
                        + ',' + "(SELECT COUNT(*) FROM " + TBL_BOOK_SERIES.ref()
                        + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID)
                        + '=' + TBL_BOOKS.dot(DOM_PK_ID) + ')'
                        + " AS " + COLUMN_ALIAS_NR_OF_SERIES

                        // Find the first AUTHOR ID
                        + ',' + "(SELECT " + DOM_FK_AUTHOR_ID + " FROM " + TBL_BOOK_AUTHOR.ref()
                        + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK_ID)
                        + '=' + TBL_BOOKS.dot(DOM_PK_ID)
                        + " ORDER BY " + DOM_BOOK_AUTHOR_POSITION
                        + ',' + TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR_ID) + " LIMIT 1)"
                        + " AS " + DOM_FK_AUTHOR_ID

                        // Get the total author count. TODO: does not seem to get used anywhere ?
                        + ',' + "(SELECT COUNT(*) FROM " + TBL_BOOK_AUTHOR.ref()
                        + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK_ID)
                        + '=' + TBL_BOOKS.dot(DOM_PK_ID) + ')'
                        + " AS " + COLUMN_ALIAS_NR_OF_AUTHORS;

        /**
         * Author "FamilyName, GivenNames" in one SQL column.
         */
        private static final String AUTHOR_FAMILY_COMMA_GIVEN =
                TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                        + " || ',' || " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES);

        /**
         * Author "GivenNames FamilyName" in one SQL column.
         */
        private static final String AUTHOR_GIVEN_SPACE_FAMILY =
                TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES)
                        + " || ' ' || " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME);

        /**
         * If no given name -> family name.
         * otherwise -> family, given.
         */
        private static final String AUTHOR_FORMATTED =
                " Case When " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "=''"
                        + " Then " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                        + " Else " + AUTHOR_FAMILY_COMMA_GIVEN
                        + " End"
                        + " AS " + DOM_AUTHOR_FORMATTED;

        /**
         * If no given name -> family name.
         * otherwise -> given family.
         */
        private static final String AUTHOR_FORMATTED_GIVEN_FIRST =
                " Case When " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "=''"
                        + " Then " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                        + " Else " + AUTHOR_GIVEN_SPACE_FAMILY
                        + " End"
                        + " AS " + DOM_AUTHOR_FORMATTED_GIVEN_FIRST;

        /**
         * Columns for an Author, with the standard 'family, given' formatted added.
         */
        private static final String AUTHOR =
                TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME)
                        + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES)
                        + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE)
                        + ',' + AUTHOR_FORMATTED;


        /**
         * The columns from {@link DatabaseDefinitions#TBL_BOOKS} we need.
         * to send a Book to Goodreads.
         */
        private static final String GOODREADS_FIELDS_FOR_SENDING =
                DOM_PK_ID.name
                        + ',' + DOM_BOOK_ISBN
                        + ',' + DOM_BOOK_GOODREADS_BOOK_ID
                        + ',' + DOM_BOOK_NOTES
                        + ',' + DOM_BOOK_READ
                        + ',' + DOM_BOOK_READ_END
                        + ',' + DOM_BOOK_RATING;
    }

    /**
     * Sql SELECT of a single table, without a where clause.
     */
    private static final class SqlSelectFullTable {

        private static final String AUTHORS_WITH_FORMATTED_NAMES =
                "SELECT DISTINCT " + SqlColumns.AUTHOR_FORMATTED
                        + " FROM " + TBL_AUTHORS.ref()
                        + " ORDER BY lower(" + DOM_AUTHOR_FAMILY_NAME + ')' + COLLATION
                        + ", lower(" + DOM_AUTHOR_GIVEN_NAMES + ')' + COLLATION;

        private static final String BOOKSHELVES =
                SELECT_DISTINCT(TBL_BOOKSHELF.dotAs(DOM_PK_ID),
                                TBL_BOOKSHELF.dotAs(DOM_BOOKSHELF))
                        + " FROM " + TBL_BOOKSHELF.ref()
                        + " ORDER BY lower(" + DOM_BOOKSHELF + ')' + COLLATION;

        private static final String FORMATS =
                "SELECT DISTINCT " + DOM_BOOK_FORMAT
                        + " FROM " + TBL_BOOKS
                        + " ORDER BY lower(" + DOM_BOOK_FORMAT + ')' + COLLATION;

        private static final String GENRES =
                "SELECT DISTINCT " + DOM_BOOK_GENRE
                        + " FROM " + TBL_BOOKS
                        + " ORDER BY lower(" + DOM_BOOK_GENRE + ')' + COLLATION;

        private static final String LANGUAGES =
                "SELECT DISTINCT " + DOM_BOOK_LANGUAGE
                        + " FROM " + TBL_BOOKS
                        + " ORDER BY lower(" + DOM_BOOK_LANGUAGE + ')' + COLLATION;

        private static final String LOCATIONS =
                "SELECT DISTINCT " + DOM_BOOK_LOCATION
                        + " FROM " + TBL_BOOKS
                        + " ORDER BY lower(" + DOM_BOOK_LOCATION + ')' + COLLATION;

        private static final String PUBLISHERS =
                "SELECT DISTINCT " + DOM_BOOK_PUBLISHER
                        + " FROM " + TBL_BOOKS
                        + " ORDER BY lower(" + DOM_BOOK_PUBLISHER + ')' + COLLATION;

        private static final String SERIES =
                "SELECT DISTINCT " + DOM_SERIES_NAME
                        + " FROM " + TBL_SERIES
                        + " ORDER BY lower(" + DOM_SERIES_NAME + ')' + COLLATION;

        /**
         * Return a list of all Styles.
         */
        private static final String BOOKLIST_STYLES =
                SELECT(DOM_PK_ID.name,
                       DOM_UUID.name)
                        + " FROM " + TBL_BOOKLIST_STYLES;
    }

    /**
     * Sql SELECT returning a list, with a WHERE clause.
     */
    private static final class SqlSelectList {

        /**
         * All Bookshelves for a Book.
         */
        private static final String BOOKSHELVES_BY_BOOK_ID =
                SELECT_DISTINCT(TBL_BOOKSHELF.dotAs(DOM_PK_ID),
                                TBL_BOOKSHELF.dotAs(DOM_BOOKSHELF))
                        + " FROM " + TBL_BOOK_BOOKSHELF.ref()
                        + TBL_BOOK_BOOKSHELF.join(TBL_BOOKSHELF)
                        + " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_FK_BOOK_ID) + "=?"
                        + " ORDER BY lower(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + ')' + COLLATION;

        /**
         * All Authors for a Book.
         */
        private static final String AUTHORS_BY_BOOK_ID =
                SELECT_DISTINCT(TBL_AUTHORS.dotAs(DOM_PK_ID),
                                TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME),
                                TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES),
                                TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE),
                                SqlColumns.AUTHOR_FORMATTED,
                                TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_POSITION))
                        + " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                        + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK_ID) + "=?"
                        + " ORDER BY "
                        + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_POSITION) + " ASC,"
                        + " lower(" + DOM_AUTHOR_FAMILY_NAME + ')' + COLLATION + "ASC,"
                        + " lower(" + DOM_AUTHOR_GIVEN_NAMES + ')' + COLLATION + "ASC";

        /**
         * All Series for a Book.
         */
        private static final String SERIES_BY_BOOK_ID =
                SELECT_DISTINCT(TBL_SERIES.dotAs(DOM_PK_ID),
                                TBL_SERIES.dotAs(DOM_SERIES_NAME),
                                TBL_SERIES.dotAs(DOM_SERIES_IS_COMPLETE),
                                TBL_BOOK_SERIES.dotAs(DOM_BOOK_SERIES_NUM),
                                TBL_BOOK_SERIES.dotAs(DOM_BOOK_SERIES_POSITION),
                                DOM_SERIES_NAME + "||' ('||" + DOM_BOOK_SERIES_NUM + "||')'"
                                        + " AS " + DOM_SERIES_FORMATTED)
                        + " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                        + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) + "=?"
                        + " ORDER BY " + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION)
                        + ",lower(" + TBL_SERIES.dot(DOM_SERIES_NAME) + ')' + COLLATION + "ASC";

        /**
         * All TOCEntries for a Book.
         */
        private static final String TOC_ENTRIES_BY_BOOK_ID =
                SELECT(TBL_TOC_ENTRIES.dotAs(DOM_PK_ID),
                       TBL_TOC_ENTRIES.dotAs(DOM_TITLE),
                       TBL_TOC_ENTRIES.dotAs(DOM_FIRST_PUBLICATION),
                       // for convenience, we fetch 'full' Author objects here
                       TBL_TOC_ENTRIES.dotAs(DOM_FK_AUTHOR_ID),
                       TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME),
                       TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES),
                       TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE))
                        + " FROM " + TBL_TOC_ENTRIES.ref()
                        + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                        + TBL_TOC_ENTRIES.join(TBL_AUTHORS)
                        + " WHERE " + TBL_BOOK_TOC_ENTRIES.dot(DOM_FK_BOOK_ID) + "=?"
                        + " ORDER BY " + TBL_BOOK_TOC_ENTRIES.dot(DOM_BOOK_TOC_ENTRY_POSITION);

        /**
         * All TOCEntries for an Author.
         */
        private static final String GET_TOC_ENTRIES_BY_AUTHOR_ID =
                SELECT(TBL_TOC_ENTRIES.dotAs(DOM_PK_ID),
                       TBL_TOC_ENTRIES.dotAs(DOM_TITLE),
                       TBL_TOC_ENTRIES.dotAs(DOM_FIRST_PUBLICATION))
                        + " FROM " + TBL_TOC_ENTRIES.ref()
                        + " WHERE " + TBL_TOC_ENTRIES.dot(DOM_FK_AUTHOR_ID) + "=?"
                        + " ORDER BY lower(" + TBL_TOC_ENTRIES.dot(DOM_TITLE) + ')' + COLLATION;

    }

    /**
     * Sql SELECT that returns a single ID.
     */
    private static final class SqlGetId {

        /**
         * Find the Book ID based on a search for the ISBN.
         */
        private static final String BOOK_ID_BY_ISBN =
                "SELECT Coalesce(max(" + DOM_PK_ID + "),0)" + " FROM " + TBL_BOOKS
                        + " WHERE lower(" + DOM_BOOK_ISBN + ")=lower(?)";
        /**
         * Find the Book ID based on a search for the ISBN (both 10 & 13).
         */
        private static final String BOOK_ID_BY_ISBN2 =
                "SELECT Coalesce(max(" + DOM_PK_ID + "),0)" + " FROM " + TBL_BOOKS
                        + " WHERE lower(" + DOM_BOOK_ISBN + ") IN (lower(?),lower(?))";

        private static final String BOOKLIST_STYLE_ID_BY_UUID =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKLIST_STYLES
                        + " WHERE " + DOM_UUID + "=?";

        private static final String BOOKSHELF_ID_BY_NAME =
                "SELECT " + DOM_PK_ID + " FROM " + DOM_BOOKSHELF
                        + " WHERE lower(" + DOM_BOOKSHELF + ")=lower(?)" + COLLATION;

        private static final String AUTHOR_ID_BY_NAME =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_AUTHORS
                        + " WHERE lower(" + DOM_AUTHOR_FAMILY_NAME + ")=lower(?)" + COLLATION
                        + " AND lower(" + DOM_AUTHOR_GIVEN_NAMES + ")=lower(?)" + COLLATION;

        private static final String SERIES_ID_BY_NAME =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_SERIES
                        + " WHERE lower(" + DOM_SERIES_NAME + ") = lower(?)" + COLLATION;

        private static final String BOOK_ID_BY_TOC_ENTRY_ID =
                "SELECT " + DOM_FK_BOOK_ID + " FROM " + TBL_BOOK_TOC_ENTRIES
                        + " WHERE " + DOM_FK_TOC_ENTRY_ID + "=?";

        private static final String BOOK_UUID_BY_ID =
                "SELECT " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS
                        + " WHERE " + DOM_PK_ID + "=?";

        private static final String BOOK_ID_BY_UUID =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKS
                        + " WHERE " + DOM_BOOK_UUID + "=?";
    }

    /**
     * Sql SELECT that returns a single String (name etc).
     */
    private static final class SqlGetName {

        /**
         * Get the name of the loanee of a book.
         */
        private static final String LOANEE_BY_BOOK_ID =
                "SELECT " + DOM_LOANEE
                        + " FROM " + TBL_BOOK_LOANEE + " WHERE " + DOM_FK_BOOK_ID + "=?";

        /**
         * Get the name of a Bookshelf by it's id.
         */
        private static final String BOOKSHELF_NAME_BY_ID =
                "SELECT " + DOM_BOOKSHELF
                        + " FROM " + TBL_BOOKSHELF + " WHERE " + DOM_PK_ID + "=?";
    }

    /**
     * Sql SELECT to get Objects by their id; and related queries.
     */
    private static final class SqlSelect {

        /**
         * Get a {@link Bookshelf} by it's id.
         */
        private static final String BOOKSHELF_BY_ID =
                SELECT(DOM_PK_ID.name,
                       DOM_BOOKSHELF.name)
                        + " FROM " + TBL_BOOKSHELF + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Get an {@link Author} by it's id.
         */
        private static final String AUTHOR_BY_ID =
                SELECT(DOM_AUTHOR_FAMILY_NAME.name,
                       DOM_AUTHOR_GIVEN_NAMES.name,
                       DOM_AUTHOR_IS_COMPLETE.name)
                        + " FROM " + TBL_AUTHORS + " WHERE " + DOM_PK_ID + "=?";


        /**
         * Get a {@link Series} by it's id.
         */
        private static final String SERIES_BY_ID =
                SELECT(DOM_SERIES_NAME.name,
                       DOM_SERIES_IS_COMPLETE.name)
                        + " FROM " + TBL_SERIES + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Get the needed fields of a Book to send to Goodreads.
         * <p>
         * param DOM_PK_ID of the Book
         */
        private static final String GOODREADS_GET_BOOK_TO_SEND_BY_BOOK_ID =
                "SELECT " + SqlColumns.GOODREADS_FIELDS_FOR_SENDING
                        + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";


        private static final String SEARCH_SUGGESTIONS =
                "SELECT * FROM ("
                        // Book Title
                        + "SELECT \"BK\" || " + TBL_BOOKS.dotAs(DOM_PK_ID)
                        + ',' + TBL_BOOKS.dot(DOM_TITLE)
                        + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1
                        + ',' + TBL_BOOKS.dot(DOM_TITLE)
                        + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA
                        + " FROM " + TBL_BOOKS.ref()
                        + " WHERE " + TBL_BOOKS.dot(DOM_TITLE) + " LIKE ?"

                        + " UNION "

                        // Author Family Name
                        + " SELECT \"AF\" || " + TBL_AUTHORS.dotAs(DOM_PK_ID)
                        + ',' + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                        + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1
                        + ',' + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                        + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA
                        + " FROM " + TBL_AUTHORS.ref()
                        + " WHERE " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME) + " LIKE ?"

                        + " UNION "

                        // Author Given Names
                        + " SELECT \"AG\" || " + TBL_AUTHORS.dotAs(DOM_PK_ID)
                        + ',' + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES)
                        + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1
                        + ',' + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES)
                        + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA
                        + " FROM " + TBL_AUTHORS.ref()
                        + " WHERE " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + " LIKE ?"

                        + " UNION "

                        // Book ISBN
                        + " SELECT \"BK\" || " + TBL_BOOKS.dotAs(DOM_PK_ID)
                        + ',' + TBL_BOOKS.dot(DOM_BOOK_ISBN)
                        + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1
                        + ',' + TBL_BOOKS.dot(DOM_BOOK_ISBN)
                        + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA
                        + " FROM " + TBL_BOOKS.ref()
                        + " WHERE " + TBL_BOOKS.dot(DOM_BOOK_ISBN) + " LIKE ?"

                        + " ) AS zzz "
                        + " ORDER BY lower(" + SearchManager.SUGGEST_COLUMN_TEXT_1 + ") "
                        + COLLATION;
    }

    /**
     * Sql UPDATE to globally replace a value across a table.
     */
    private static final class SqlGlobalReplace {

        /**
         * FIXME: this should not be needed due to FOREIGN KEY's ?
         */
        private static final String AUTHOR_ON_TOC_ENTRIES =
                "UPDATE " + TBL_TOC_ENTRIES + " SET " + DOM_FK_AUTHOR_ID + "=?"
                        + " WHERE " + DOM_FK_AUTHOR_ID + "=?";

        private static final String FORMAT =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + DOM_BOOK_FORMAT + "=?,"
                        + " WHERE " + DOM_BOOK_FORMAT + "=?";

        private static final String GENRE =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + DOM_BOOK_GENRE + "=?,"
                        + " WHERE " + DOM_BOOK_GENRE + "=?";

        private static final String LANGUAGE =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + DOM_BOOK_LANGUAGE + "=?,"
                        + " WHERE " + DOM_BOOK_LANGUAGE + "=?";

        private static final String LOCATION =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + DOM_BOOK_LOCATION + "=?,"
                        + " WHERE " + DOM_BOOK_LOCATION + "=?";

        private static final String PUBLISHER =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + DOM_BOOK_PUBLISHER + "=?,"
                        + " WHERE " + DOM_BOOK_PUBLISHER + "=?";

        private static final String SERIES =
                "UPDATE " + TBL_BOOK_SERIES + " SET " + DOM_FK_SERIES_ID + "=?"
                        + " WHERE " + DOM_FK_SERIES_ID + "=?"
                        + " AND NOT Exists(SELECT NULL FROM " + TBL_BOOK_SERIES.ref()
                        + " WHERE "
                        + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) + '='
                        + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID)
                        + " AND "
                        + TBL_BOOK_SERIES.dot(DOM_FK_SERIES_ID) + "=?)";
    }

    private static final class SqlUpdate {

        /**
         * Update a single Book's last sync date with Goodreads.
         */
        private static final String GOODREADS_LAST_SYNC_DATE =
                "UPDATE " + TBL_BOOKS + " SET "
                        + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + "=current_timestamp"
                        + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Update a single Book's Goodreads id.
         */
        private static final String GOODREADS_BOOK_ID =
                "UPDATE " + TBL_BOOKS + " SET "
                        + DOM_BOOK_GOODREADS_BOOK_ID + "=?"
                        + " WHERE " + DOM_PK_ID + "=?";
    }

    /**
     * Sql UPDATE commands to set the last update date of a Book.
     */
    private static final class SqlSetLastUpdateDate {


        private static final String BOOK_ID =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + " WHERE " + DOM_PK_ID + "=?";

        private static final String TOC_ENTRY_AUTHOR_ID =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + " WHERE"
                        + " Exists(SELECT * FROM " + TBL_TOC_ENTRIES.ref()
                        + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                        + TBL_BOOK_TOC_ENTRIES.join(TBL_BOOKS)
                        + " WHERE " + TBL_TOC_ENTRIES.dot(DOM_FK_AUTHOR_ID) + "=?)";

        private static final String BY_AUTHOR_ID =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + " WHERE"
                        + " Exists(SELECT * FROM " + TBL_BOOK_AUTHOR.ref()
                        + TBL_BOOK_AUTHOR.join(TBL_BOOKS)
                        + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_AUTHOR_ID) + "=?)";

        private static final String BY_SERIES_ID =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + " WHERE"
                        + " Exists(SELECT * FROM " + TBL_BOOK_SERIES.ref()
                        + TBL_BOOK_SERIES.join(TBL_BOOKS)
                        + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_SERIES_ID) + "=?)";

        private static final String BY_BOOKSHELF_ID =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + " WHERE"
                        + " Exists(SELECT * FROM " + TBL_BOOK_BOOKSHELF.ref()
                        + TBL_BOOK_BOOKSHELF.join(TBL_BOOKS)
                        + " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_FK_BOOKSHELF_ID) + "=?)";
    }

    /**
     * Sql DELETE commands.
     */
    private static final class SqlDelete {

        private static final String BOOK_BY_ID =
                "DELETE FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";

        private static final String BOOKSHELF_BY_ID =
                "DELETE FROM " + TBL_BOOKSHELF + " WHERE " + DOM_PK_ID + "=?";

        private static final String STYLE_BY_ID =
                "DELETE FROM " + TBL_BOOKLIST_STYLES + " WHERE " + DOM_PK_ID + "=?";


        private static final String BOOK_AUTHOR_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_AUTHOR + " WHERE " + DOM_FK_BOOK_ID + "=?";

        private static final String BOOK_BOOKSHELF_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_BOOKSHELF + " WHERE " + DOM_FK_BOOK_ID + "=?";
        private static final String BOOK_BOOKSHELF_BY_BOOKSHELF_ID =
                "DELETE FROM " + TBL_BOOK_BOOKSHELF + " WHERE " + DOM_FK_BOOKSHELF_ID + "=?";

        private static final String BOOK_SERIES_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_SERIES + " WHERE " + DOM_FK_BOOK_ID + "=?";
        private static final String BOOK_SERIES_BY_SERIES_ID =
                "DELETE FROM " + TBL_BOOK_SERIES + " WHERE " + DOM_FK_SERIES_ID + "=?";

        private static final String BOOK_TOC_ENTRIES_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_TOC_ENTRIES + " WHERE " + DOM_FK_BOOK_ID + "=?";

        private static final String BOOK_LOANEE_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_LOANEE + " WHERE " + DOM_FK_BOOK_ID + "=?";



        private static final String PURGE_AUTHORS =
                "DELETE FROM " + TBL_AUTHORS
                        + " WHERE " + DOM_PK_ID + " NOT IN"
                        + " (SELECT DISTINCT " + DOM_FK_AUTHOR_ID + " FROM " + TBL_BOOK_AUTHOR + ')'
                        + " AND " + DOM_PK_ID + " NOT IN"
                        + " (SELECT DISTINCT " + DOM_FK_AUTHOR_ID + " FROM " + TBL_TOC_ENTRIES + ')';
        private static final String PURGE_BOOK_AUTHORS =
                "DELETE FROM " + TBL_BOOK_AUTHOR
                        + " WHERE " + DOM_FK_BOOK_ID + " NOT IN"
                        + " (SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKS + ')';


        private static final String PURGE_SERIES =
                "DELETE FROM " + TBL_SERIES + " WHERE " + DOM_PK_ID
                        + " NOT IN (SELECT DISTINCT " + DOM_FK_SERIES_ID
                        + " FROM " + TBL_BOOK_SERIES + ')';
        private static final String PURGE_BOOK_SERIES =
                "DELETE FROM " + TBL_BOOK_SERIES + " WHERE " + DOM_FK_BOOK_ID
                        + " NOT IN (SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKS + ')';


        private static final String PURGE_BOOK_LOANEE =
                "DELETE FROM " + TBL_BOOK_LOANEE + " WHERE "
                        + '(' + DOM_FK_BOOK_ID + "=''" + " OR " + DOM_FK_BOOK_ID + "=null"
                        + " OR " + DOM_LOANEE + "=''" + " OR " + DOM_LOANEE + "=null)";
    }

    /**
     * Sql specific for FTS.
     */
    private static final class SqlFTS {

        private static final String GET_AUTHORS_BY_BOOK_ID =
                "SELECT " + TBL_AUTHORS.dot("*")
                        + " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                        + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK_ID) + "=?";

        private static final String GET_SERIES_BY_BOOK_ID =
                SELECT(TBL_SERIES.dot(DOM_SERIES_NAME)
                               + " || ' ' ||"
                               + " Coalesce(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM) + ",'')"
                               + " AS seriesInfo")
                        + " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                        + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) + "=?";

        private static final String DOM_TOC_ENTRY_INFO = "TOCEntryInfo";
        private static final String DOM_TOC_ENTRY_AUTHOR_INFO = "TOCEntryAuthorInfo";

        private static final String GET_TOC_ENTRIES_BY_BOOK_ID =
                SELECT(SqlColumns.AUTHOR_GIVEN_SPACE_FAMILY
                               + " AS " + DOM_TOC_ENTRY_AUTHOR_INFO,
                       DOM_TITLE + " AS " + DOM_TOC_ENTRY_INFO)
                        + " FROM " + TBL_TOC_ENTRIES.ref()
                        + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                        + TBL_TOC_ENTRIES.join(TBL_AUTHORS)
                        + " WHERE " + TBL_BOOK_TOC_ENTRIES.dot(DOM_FK_BOOK_ID) + "=?";

        private static final String DEL_BOOKS_BY_DOCID =
                "DELETE FROM " + TBL_BOOKS_FTS + " WHERE " + DOM_PK_DOCID + "=?";
    }
}
