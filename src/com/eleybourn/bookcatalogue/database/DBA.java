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

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.csv.CsvImporter;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.database.cursors.ColumnMapper;
import com.eleybourn.bookcatalogue.database.cursors.TrackedCursor;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.database.dbsync.TransactionException;
import com.eleybourn.bookcatalogue.database.definitions.ColumnInfo;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.Csv;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

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
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOANEE;
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
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_STYLE_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_TOC_ENTRY_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FTS_AUTHOR_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
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
 * Book Catalogue database access helper class.
 * (reminder: setting books dirty is now done with triggers).
 *
 * <p>
 * insert:
 * * return new id, or -1 for error.
 * <p>
 * update:
 * * return rows affected, can be 0.
 * <p>
 * updateOrInsert:
 * * return true for success (either insert or update with rowsAffected > 0)
 *
 * <p>
 * ENHANCE: Use date_acquired to add 'Recent Acquisitions' virtual shelf;
 * need to resolve how this may relate to date_added
 */
public class DBA
        implements AutoCloseable {

    /**
     * Flag indicating the UPDATE_DATE field from the bundle should be trusted.
     * If this flag is not set, the UPDATE_DATE will be set based on the current time
     * <p>
     * Currently down to a single flag, but not switching to a boolean for now.
     */
    public static final int BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT = 1;

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

    private static final int PREPARED_CACHE_SIZE = 20;
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
                @NonNull final SQLiteDatabase db,
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
    /** DEBUG instance counter. */
    @NonNull
    private static final AtomicInteger DEBUG_INSTANCE_COUNT = new AtomicInteger();

    /** statement names; keys into the cache map. */
    private static final String STMT_CHECK_BOOK_EXISTS = "CheckBookExists";

    private static final String STMT_GET_AUTHOR_ID = "GetAuthorId";
    private static final String STMT_GET_SERIES_ID = "GetSeriesId";
    private static final String STMT_GET_TOC_ENTRY_ID = "GetTOCEntryId";

    private static final String STMT_GET_BOOK_ISBN = "GetBookIsbn";
    private static final String STMT_GET_BOOK_TITLE = "GetBookTitle";
    private static final String STMT_GET_BOOK_UPDATE_DATE = "GetBookUpdateDate";
    private static final String STMT_GET_BOOK_UUID = "GetBookUuid";

    private static final String STMT_GET_BOOK_ID_FROM_ISBN_2 = "GetIdFromIsbn2";
    private static final String STMT_GET_BOOK_ID_FROM_ISBN_1 = "GetIdFromIsbn1";
    private static final String STMT_GET_BOOK_ID_FROM_UUID = "GetBookIdFromUuid";

    private static final String STMT_GET_AUTHOR_BOOK_COUNT = "GetAuthorBookCount";
    private static final String STMT_GET_SERIES_BOOK_COUNT = "GetSeriesBookCount";
    private static final String STMT_GET_AUTHOR_ANTHOLOGY_COUNT = "GetAuthorAnthologyCount";

    private static final String STMT_GET_BOOKSHELF_ID_BY_NAME = "GetBookshelfIdByName";
    private static final String STMT_GET_LOANEE_BY_BOOK_ID = "GetLoaneeByBookId";
    private static final String STMT_GET_BOOKLIST_STYLE = "GetBooklistStyle";

    private static final String STMT_ADD_BOOK_SERIES = "AddBookSeries";
    private static final String STMT_ADD_BOOK_TOC_ENTRY = "AddBookTOCEntry";
    private static final String STMT_ADD_BOOK_AUTHORS = "AddBookAuthors";
    private static final String STMT_ADD_BOOK_BOOKSHELF = "AddBookBookshelf";
    private static final String STMT_ADD_BOOKSHELF = "AddBookshelf";
    private static final String STMT_ADD_AUTHOR = "AddAuthor";
    private static final String STMT_ADD_TOC_ENTRY = "AddTOCEntry";
    private static final String STMT_ADD_FTS = "AddFts";
    private static final String STMT_ADD_BOOK_LOAN = "InsertBookLoan";
    private static final String STMT_ADD_SERIES = "AddSeries";
    private static final String STMT_ADD_BOOKLIST_STYLE = "AddBooklistStyle";

    private static final String STMT_DELETE_BOOKSHELF = "DeleteBookshelf";
    private static final String STMT_DELETE_BOOK = "DeleteBook";
    private static final String STMT_DELETE_SERIES = "DeleteSeries";
    private static final String STMT_DELETE_BOOK_TOC_ENTRIES = "DeleteTocEntry";
    private static final String STMT_DELETE_BOOK_AUTHORS = "DeleteBookAuthors";
    private static final String STMT_DELETE_BOOK_BOOKSHELF = "DeleteBookBookshelf";
    private static final String STMT_DELETE_BOOK_LOAN = "DeleteBookLoan";
    private static final String STMT_DELETE_BOOK_SERIES = "DeleteBookSeries";
    private static final String STMT_DELETE_BOOKLIST_STYLE = "DeleteBooklistStyle";

    private static final String STMT_UPDATE_GOODREADS_BOOK_ID = "SetGoodreadsBookId";
    private static final String STMT_UPDATE_AUTHOR_ON_TOC_ENTRIES = "UpdateAuthorOnTocEntry";
    private static final String STMT_UPDATE_GOODREADS_SYNC_DATE = "SetGoodreadsSyncDate";
    private static final String STMT_UPDATE_FORMAT = "GlobalReplaceFormat";
    private static final String STMT_UPDATE_GENRE = "GlobalReplaceGenre";
    private static final String STMT_UPDATE_LANGUAGE = "GlobalReplaceLanguage";
    private static final String STMT_UPDATE_LOCATION = "GlobalReplaceLocation";
    private static final String STMT_UPDATE_PUBLISHER = "GlobalReplacePublisher";
    private static final String STMT_UPDATE_FTS = "UpdateFts";

    private static final String STMT_PURGE_SERIES = "PurgeSeries";
    private static final String STMT_PURGE_AUTHORS = "PurgeAuthors";

    /** error message. */
    private static final String ERROR_NEEDS_TRANSACTION = "Needs transaction";
    private static final String ERROR_FAILED_TO_UPDATE_FTS = "Failed to update FTS";


    /** the actual SQLiteOpenHelper. */
    private static DBHelper mDbHelper;
    /** the synchronized wrapper around the real database. */
    private static SynchronizedDb mSyncedDb;
    /** needed for Resources & for getting the covers db. */
    @NonNull
    private final Context mContext;

    /** a cache for statements, where they are pre-compiled. */
    private final SqlStatementManager mStatements;

    /** used by finalize so close does not get called twice. */
    private boolean mCloseCalled;

    /**
     * Constructor - takes the context to allow the database to be opened/created.
     * <p>
     * Note: don't be tempted to turn this into a singleton...
     * multi-threading does not like that.
     *
     * @param context the caller context
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

            // Turn ON foreign key support so that CASCADE works.
            mSyncedDb.execSQL("PRAGMA foreign_keys = ON");

            // Turn OFF recursive triggers;
            mSyncedDb.execSQL("PRAGMA recursive_triggers = OFF");

            mSyncedDb.getUnderlyingDatabase()
                     .setMaxSqlCacheSize(PREPARED_CACHE_SIZE);
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
     * @return the synchronizer object for this database in case there is some other activity
     * that needs to be synced.
     * <p>
     * Note: Cursor requery() is the only thing found so far.
     */
    @NonNull
    public static Synchronizer getSynchronizer() {
        return SYNCHRONIZER;
    }

    /**
     * Escape single quotation marks by doubling them (standard SQL escape).
     *
     * @param value to encode
     *
     * @return escaped value.
     */
    @NonNull
    public static String encodeString(@NonNull final String value) {
        return value.replace("'", "''");
    }

    /**
     * Cleanup a search string to remove all quotes etc.
     * <p>
     * Remove punctuation from the search string to TRY to match the tokenizer.
     * The only punctuation we allow is a hyphen preceded by a space.
     * Everything else is translated to a space.
     * <p>
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
                Logger.info(DBA.class, "<-- **** Missing ref (not closed?) **** vvvvvvv");
                Logger.error(ref.getCreationException());
                Logger.info(DBA.class, "--> **** Missing ref (not closed?) **** ^^^^^^^");
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

    public void recreateTriggers() {
        mDbHelper.createTriggers(mSyncedDb);
    }

    @NonNull
    public Context getContext() {
        return mContext;
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

    public void analyze() {
        mSyncedDb.analyze();
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
        SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_GOODREADS_SYNC_DATE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_UPDATE_GOODREADS_SYNC_DATE,
                                   SqlUpdate.GOODREADS_LAST_SYNC_DATE);
        }
        stmt.bindLong(1, bookId);
        stmt.executeUpdateDelete();
    }

    /**
     * Generic function to close the database.
     * It does not 'close' the database in the literal sense, but
     * performs a cleanup by closing all open statements
     * <p>
     * So it should really be called cleanup()
     * But it allows us to use try-with-resources.
     * <p>
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
     * @param tocEntry to insert
     *
     * @return insert: the row ID of the newly inserted TocEntry,
     * or -1 if an error occurred during insert
     */
    @SuppressWarnings("UnusedReturnValue")
    private long insertTOCEntry(@NonNull final TocEntry tocEntry) {
        SynchronizedStatement stmt = mStatements.get(STMT_ADD_TOC_ENTRY);
        if (stmt == null) {
            stmt = mStatements.add(STMT_ADD_TOC_ENTRY, SqlInsert.TOC_ENTRY);
        }
        stmt.bindLong(1, tocEntry.getAuthor().getId());
        // standardize the title for new entries
        stmt.bindString(2, preprocessTitle(tocEntry.getTitle()));
        stmt.bindString(3, tocEntry.getFirstPublication());
        long iId = stmt.executeInsert();
        if (iId > 0) {
            tocEntry.setId(iId);
        }
        return iId;
    }

    /**
     * @param tocEntry to update
     *
     * @return rows affected, should be 1 for success
     */
    @SuppressWarnings("UnusedReturnValue")
    private int updateTOCEntry(@NonNull final TocEntry tocEntry) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_FIRST_PUBLICATION.name, tocEntry.getFirstPublication());
        return mSyncedDb.update(TBL_TOC_ENTRIES.getName(), cv,
                                DOM_PK_ID + "=?",
                                new String[]{String.valueOf(tocEntry.getId())});
    }

    /**
     * Delete the link between TocEntry's and the given Book.
     * Note that the actual TocEntry's are not deleted.
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    private int deleteBookTocEntryByBookId(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOK_TOC_ENTRIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_BOOK_TOC_ENTRIES,
                                   SqlDelete.BOOK_TOC_ENTRIES_BY_BOOK_ID);
        }
        stmt.bindLong(1, bookId);
        return stmt.executeUpdateDelete();
    }

    /**
     * Return the {@link TocEntry} ID for a given author/title.
     * (note that publication year is NOT used).
     * The title will be checked for (equal 'OR' as {@link #preprocessTitle})
     *
     * @param authorId id of author
     * @param title    title
     *
     * @return the id of the {@link TocEntry} entry, or 0 if not found
     */
    public long getTOCEntryId(final long authorId,
                              @NonNull final String title) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_TOC_ENTRY_ID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_TOC_ENTRY_ID, SqlGet.TOC_ENTRY_ID);
        }
        stmt.bindLong(1, authorId);
        stmt.bindString(2, title);
        stmt.bindString(3, preprocessTitle(title));
        return stmt.simpleQueryForLongOrZero();
    }

    /**
     * Return all the {@link TocEntry} for the given {@link Author}.
     *
     * @param author to retrieve
     *
     * @return List of {@link TocEntry} for this {@link Author}
     */
    @NonNull
    public ArrayList<TocEntry> getTocEntryByAuthor(@NonNull final Author author) {
        ArrayList<TocEntry> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectList.GET_TOC_ENTRIES_BY_AUTHOR_ID,
                                                new String[]{String.valueOf(author.getId())})) {
            if (cursor.getCount() == 0) {
                return list;
            }
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_TOC_ENTRIES);
            while (cursor.moveToNext()) {
                TocEntry title = new TocEntry(mapper.getLong(DOM_PK_ID),
                                              author,
                                              mapper.getString(DOM_TITLE),
                                              mapper.getString(DOM_FIRST_PUBLICATION));
                list.add(title);
            }
        }
        return list;
    }

    /**
     * Creates a new {@link Author}.
     *
     * @param author object to insert. Will be updated with the id.
     *
     * @return the row ID of the newly inserted Author, or -1 if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    private long insertAuthor(@NonNull final Author /* in/out */ author) {
        SynchronizedStatement stmt = mStatements.get(STMT_ADD_AUTHOR);
        if (stmt == null) {
            stmt = mStatements.add(STMT_ADD_AUTHOR, SqlInsert.AUTHOR);
        }

        stmt.bindString(1, author.getFamilyName());
        stmt.bindString(2, author.getGivenNames());
        stmt.bindLong(3, author.isComplete() ? 1 : 0);
        long iId = stmt.executeInsert();
        if (iId > 0) {
            author.setId(iId);
        }
        return iId;
    }

    /**
     * @param author to update
     *
     * @return rows affected, should be 1 for success
     */
    public int updateAuthor(@NonNull final Author author) {

        ContentValues cv = new ContentValues();
        cv.put(DOM_AUTHOR_FAMILY_NAME.name, author.getFamilyName());
        cv.put(DOM_AUTHOR_GIVEN_NAMES.name, author.getGivenNames());
        cv.put(DOM_AUTHOR_IS_COMPLETE.name, author.isComplete());

        return mSyncedDb.update(TBL_AUTHORS.getName(), cv,
                                DOM_PK_ID + "=?",
                                new String[]{String.valueOf(author.getId())});
    }

    /**
     * Add or update the passed {@link Author}, depending whether author.id == 0.
     *
     * @param author object to insert or update. Will be updated with the id.
     *
     * @return <tt>true</tt> for success.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertAuthor(@NonNull final /* in/out */ Author author) {

        if (author.getId() != 0) {
            return updateAuthor(author) > 0;
        } else {
            // try to find first.
            if (author.fixupId(this) == 0) {
                return insertAuthor(author) > 0;
            }
        }
        return false;
    }

    /**
     * Get ALL Authors; mainly for the purpose of backups.
     *
     * @return Cursor over all Authors
     */
    @NonNull
    public Cursor fetchAuthors() {
        return mSyncedDb.rawQuery(SqlSelectFullTable.AUTHORS, null);
    }

    /**
     * Get the {@link Author} based on the ID.
     *
     * @return the author, or null if not found
     */
    @Nullable
    public Author getAuthor(final long id) {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.AUTHOR_BY_ID,
                                                new String[]{String.valueOf(id)})) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_AUTHORS);
            if (cursor.moveToFirst()) {
                return new Author(id,
                                  mapper.getString(DOM_AUTHOR_FAMILY_NAME),
                                  mapper.getString(DOM_AUTHOR_GIVEN_NAMES),
                                  mapper.getBoolean(DOM_AUTHOR_IS_COMPLETE));
            } else {
                return null;
            }
        }
    }

    /**
     * @return author id, or 0 (e.g. 'new') when not found
     */
    public long getAuthorIdByName(@NonNull final String familyName,
                                  @NonNull final String givenNames) {

        SynchronizedStatement stmt = mStatements.get(STMT_GET_AUTHOR_ID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_AUTHOR_ID, SqlGet.AUTHOR_ID_BY_NAME);
        }

        stmt.bindString(1, familyName);
        stmt.bindString(2, givenNames);
        return stmt.simpleQueryForLongOrZero();
    }

    /**
     * Refresh the passed Author from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the Author.
     * <p>
     * Will NOT insert a new Author if not found.
     */
    public void refreshAuthor(@NonNull final Author /* out */ author) {
        if (author.getId() == 0) {
            // It wasn't a known author; see if it is now. If so, update ID.
            author.fixupId(this);
        } else {
            // It was a known author, see if it still is and fetch possibly updated fields.
            Author dbAuthor = getAuthor(author.getId());
            if (dbAuthor != null) {
                // copy any updated fields
                author.copyFrom(dbAuthor);
            } else {
                // Author not found?, set the author as 'new'
                author.setId(0);
            }
        }
    }

    /**
     * @return <tt>true</tt> for success.
     */
    public boolean globalReplaceAuthor(@NonNull final Author from,
                                       @NonNull final Author to) {

        // process the destination Author
        if (!updateOrInsertAuthor(to)) {
            Logger.error("Could not update Author");
            return false;
        }

        // Do some basic sanity checks.
        if (from.getId() == 0 && from.fixupId(this) == 0) {
            Logger.error("Old Author is not defined");
            return false;
        }

        if (from.getId() == to.getId()) {
            return true;
        }

        if (DEBUG_SWITCHES.DBA_GLOBAL_REPLACE && BuildConfig.DEBUG) {
            Logger.info(this, "globalReplaceAuthor",
                        "from=" + from.getId() + ", to=" + to.getId());
        }

        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            // replace the old id with the new id on the TOC entries
            updateAuthorOnTocEntry(from.getId(), to.getId());

            // update books for which the new ID is not already present
            globalReplaceId(TBL_BOOK_AUTHOR, DOM_FK_AUTHOR_ID, from.getId(), to.getId());

            globalReplacePositionedBookItem(TBL_BOOK_AUTHOR,
                                            DOM_FK_AUTHOR_ID,
                                            DOM_BOOK_AUTHOR_POSITION,
                                            from.getId(), to.getId());

            mSyncedDb.setTransactionSuccessful();
        } catch (RuntimeException e) {
            Logger.error(e);
            return false;
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
        return true;
    }

    /**
     * Update the author id on TocEntry's.
     *
     * @param from source id
     * @param to   destination id
     *
     * @return rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    private int updateAuthorOnTocEntry(final long from,
                                       final long to) {
        SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_AUTHOR_ON_TOC_ENTRIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_UPDATE_AUTHOR_ON_TOC_ENTRIES,
                                   SqlUpdate.AUTHOR_ON_TOC_ENTRIES);
        }
        stmt.bindLong(1, to);
        stmt.bindLong(2, from);
        return stmt.executeUpdateDelete();
    }

    /**
     * @return a complete list of author family names from the database.
     * Used for {@link android.widget.AutoCompleteTextView}.
     */
    @NonNull
    public ArrayList<String> getAuthorsFamilyName() {
        return getColumnAsList(SqlSelectFullTable.AUTHORS_FAMILY_NAMES,
                               DOM_AUTHOR_FAMILY_NAME.name);
    }

    /**
     * @return a complete list of author given names from the database.
     * Used for {@link android.widget.AutoCompleteTextView}.
     */
    @NonNull
    public ArrayList<String> getAuthorsGivenNames() {
        return getColumnAsList(SqlSelectFullTable.AUTHORS_GIVEN_NAMES,
                               DOM_AUTHOR_GIVEN_NAMES.name);
    }

    /**
     * @return a complete list of formatted author names from the database.
     * Used for {@link android.widget.AutoCompleteTextView}.
     */
    @NonNull
    public ArrayList<String> getAuthorsFormattedName() {
        return getColumnAsList(SqlSelectFullTable.AUTHORS_WITH_FORMATTED_NAMES,
                               DOM_AUTHOR_FORMATTED.name);
    }

    /**
     * Purge anything that is no longer in use.
     * <p>
     * Purging is no longer done at every occasion where it *might* be needed.
     * It was noticed it was done to many times.
     * It is now called only:
     * <p>
     * Before a backup (but not before a CSV export)
     * After an import of data; includes after Archive, CSV, XML imports.
     * <p>
     * So orphaned data will stay around a little longer which in fact may be beneficial
     * while entering/correcting a book collection.
     */
    public void purge() {
        // Note: purging TocEntry's is automatic due to foreign key cascading.
        // i.e. a TocEntry is linked directly with authors; and linked with books via a link table.
        purgeAuthors();
        purgeSeries();

        analyze();
    }

    /**
     * Delete all Authors without related books / TocEntry's.
     */
    private void purgeAuthors() {
        SynchronizedStatement stmt = mStatements.get(STMT_PURGE_AUTHORS);
        if (stmt == null) {
            stmt = mStatements.add(STMT_PURGE_AUTHORS, SqlDelete.PURGE_AUTHORS);
        }
        stmt.executeUpdateDelete();
    }

    /**
     * Delete all Series without related books.
     */
    private void purgeSeries() {
        SynchronizedStatement stmt = mStatements.get(STMT_PURGE_SERIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_PURGE_SERIES, SqlDelete.PURGE_SERIES);
        }
        stmt.executeUpdateDelete();
    }

    /**
     * @param author to retrieve
     *
     * @return the number of {@link Book} this {@link Author} has
     */
    public long countBooksByAuthor(@NonNull final Author author) {
        if (author.getId() == 0 && author.fixupId(this) == 0) {
            return 0;
        }

        SynchronizedStatement stmt = mStatements.get(STMT_GET_AUTHOR_BOOK_COUNT);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_AUTHOR_BOOK_COUNT, SqlSelect.COUNT_BOOKS_BY_AUTHOR);
        }
        // Be cautious
        synchronized (stmt) {
            stmt.bindLong(1, author.getId());
            return stmt.count();
        }
    }

    /**
     * @param author to retrieve
     *
     * @return the number of {@link TocEntry} this {@link Author} has
     */
    public long countTocEntryByAuthor(@NonNull final Author author) {
        if (author.getId() == 0 && author.fixupId(this) == 0) {
            return 0;
        }

        SynchronizedStatement stmt = mStatements.get(STMT_GET_AUTHOR_ANTHOLOGY_COUNT);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_AUTHOR_ANTHOLOGY_COUNT,
                                   SqlSelect.COUNT_TOC_ENTRIES_BY_AUTHOR);
        }
        // Be cautious
        synchronized (stmt) {
            stmt.bindLong(1, author.getId());
            return stmt.count();
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

        // Handle AUTHOR. When is this needed? Legacy archive import ?
        if (book.containsKey(UniqueId.KEY_AUTHOR_FORMATTED)
                || book.containsKey(UniqueId.KEY_AUTHOR_FAMILY_NAME)) {
            preprocessLegacyAuthor(book);
        }

        // Handle TITLE; but only for new books
        if (isNew && book.containsKey(UniqueId.KEY_TITLE)) {
            String cleanTitle = preprocessTitle(book.getString(UniqueId.KEY_TITLE));
            book.putString(UniqueId.KEY_TITLE, cleanTitle);
        }

        // Handle ANTHOLOGY_BITMASK only, no handling of actual titles here
        ArrayList<TocEntry> tocEntries = book.getList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
        if (!tocEntries.isEmpty()) {
            // definitively an anthology, overrule whatever the KEY_BOOK_ANTHOLOGY_BITMASK was.
            int type = TocEntry.Type.MULTIPLE_WORKS;
            if (TocEntry.hasMultipleAuthors(tocEntries)) {
                type |= TocEntry.Type.MULTIPLE_AUTHORS;
            }
            book.putLong(UniqueId.KEY_BOOK_ANTHOLOGY_BITMASK, type);
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
                // translate to iso3 code, or if that fails, stores the original
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
                // Need to allow for the possibility the value is not a string,
                // in which case getString() would return a NULL.
                if (o == null || o.toString().isEmpty()) {
                    book.remove(name);
                }
            }
        }
    }

    /**
     * Needed for reading from legacy archive versions... I think?
     */
    private void preprocessLegacyAuthor(@NonNull final Book book) {

        // If present, get the author ID from the author name
        // (it may have changed with a name change)
        if (book.containsKey(UniqueId.KEY_AUTHOR_FORMATTED)) {

            Author author = Author.fromString(book.getString(UniqueId.KEY_AUTHOR_FORMATTED));
            if (author.fixupId(this) == 0) {
                if (BuildConfig.DEBUG) {
                    Logger.info(this, "preprocessBook",
                                "KEY_AUTHOR_FORMATTED|inserting author: "
                                        + author.stringEncoded());
                }
                insertAuthor(author);
            }
            book.putLong(UniqueId.KEY_AUTHOR, author.getId());

        } else if (book.containsKey(UniqueId.KEY_AUTHOR_FAMILY_NAME)) {
            String family = book.getString(UniqueId.KEY_AUTHOR_FAMILY_NAME);
            String given;
            if (book.containsKey(UniqueId.KEY_AUTHOR_GIVEN_NAMES)) {
                given = book.getString(UniqueId.KEY_AUTHOR_GIVEN_NAMES);
            } else {
                given = "";
            }

            Author author = new Author(family, given);
            if (author.fixupId(this) == 0) {
                if (BuildConfig.DEBUG) {
                    Logger.info(this, "preprocessBook",
                                "KEY_AUTHOR_FAMILY_NAME|inserting author: "
                                        + author.stringEncoded());
                }
                insertAuthor(author);
            }
            book.putLong(UniqueId.KEY_AUTHOR, author.getId());
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
            // ENHANCE: if a user runs in language X, but enters books in language Y then
            // this code *should* load the lang Y resource.
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
     * Check that a book with the passed UUID exists and return the ID of the book, or zero.
     *
     * @param uuid of book
     *
     * @return ID of the book, or 0 'new' if not found
     */
    public long getBookIdFromUuid(@NonNull final String uuid) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOK_ID_FROM_UUID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOK_ID_FROM_UUID, SqlGet.BOOK_ID_BY_UUID);
        }

        stmt.bindString(1, uuid);
        return stmt.simpleQueryForLongOrZero();
    }

    /***
     * @param bookId of the book
     *
     * @return the last update date as a standard sql date string
     */
    @Nullable
    public String getBookLastUpdateDate(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOK_UPDATE_DATE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOK_UPDATE_DATE,
                                   SqlSelect.GET_LAST_UPDATE_DATE_BY_BOOK_ID);
        }
        // Be cautious
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
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
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOK_UUID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOK_UUID, SqlGet.BOOK_UUID_BY_ID);
        }
        // Be cautious; other threads may call this and set parameters.
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForString();
        }
    }

    /**
     * @param bookId of the book
     *
     * @return the title, or null when not found
     */
    @Nullable
    public String getBookTitle(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOK_TITLE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOK_TITLE,
                                   SqlGet.GET_BOOK_TITLE_BY_BOOK_ID);
        }
        // Be cautious
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    /**
     * @param bookId of the book
     *
     * @return the ISBN, or null when not found
     */
    @Nullable
    public String getBookIsbn(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOK_ISBN);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOK_ISBN,
                                   SqlGet.GET_BOOK_ISBN_BY_BOOK_ID);
        }
        // Be cautious
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
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
            // need the UUID to delete the thumbnail.
            uuid = getBookUuid(bookId);
        } catch (SQLiteDoneException e) {
            Logger.error(e, "Failed to get book UUID");
        }

        int rowsAffected = 0;
        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOK);
            if (stmt == null) {
                stmt = mStatements.add(STMT_DELETE_BOOK, SqlDelete.BOOK_BY_ID);
            }
            stmt.bindLong(1, bookId);
            rowsAffected = stmt.executeUpdateDelete();

            if (rowsAffected > 0) {
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

    /**
     * Given a book's uuid, delete the thumbnail (if any).
     *
     * @param uuid of the book
     */
    private void deleteThumbnail(@Nullable final String uuid) {
        if (uuid != null) {
            // remove from file system
            StorageUtils.deleteFile(StorageUtils.getCoverFile(uuid));
            // remove from cache
            if (!uuid.isEmpty()) {
                try (CoversDBA coversDBAdapter = CoversDBA.getInstance()) {
                    coversDBAdapter.delete(uuid);
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
     * <p>
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
             * KEY_BOOK_RATING, KEY_BOOK_LOCATION
             * KEY_BOOK_READ, KEY_BOOK_READ_START, KEY_BOOK_READ_END
             */
            if (!book.containsKey(UniqueId.KEY_BOOK_DATE_ADDED)) {
                book.putString(UniqueId.KEY_BOOK_DATE_ADDED, DateUtils.utcSqlDateTimeForToday());
            }

            // Make sure we have an author
            List<Author> authors = book.getList(UniqueId.BKEY_AUTHOR_ARRAY);
            if (authors.isEmpty()) {
                Logger.error("No authors in book from\n" + book);
                return -1L;
            }

            ContentValues cv = filterValues(TBL_BOOKS.getName(), book);

            // if we have an id, use it.
            if (bookId > 0) {
                cv.put(DOM_PK_ID.name, bookId);
            }

            // if we do NOT have a date set, then use TODAY
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
     * @param flags  See {@link #BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT} for flag definitions
     *
     * @return the number of rows affected, should be 1 for success.
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
            // A prepared statement would be faster for importing books....
            // but we don't know what columns are provided in the bundle....
            int rowsAffected = mSyncedDb.update(TBL_BOOKS.getName(), cv, DOM_PK_ID + "=?",
                                                new String[]{String.valueOf(bookId)});
            insertBookDependents(bookId, book);
            updateFts(bookId);

            if (txLock != null) {
                mSyncedDb.setTransactionSuccessful();
            }
            // make sure the Book has the correct id.
            book.putLong(UniqueId.KEY_ID, bookId);

            return rowsAffected;
        } catch (RuntimeException e) {
            Logger.error(e);
            throw new RuntimeException(
                    "Error updating book from " + book + ": " + e.getLocalizedMessage(), e);
        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * shared between book insert & update.
     * All of these will first delete all entries in the Book-[tableX] table for this bookId,
     * and then insert the new rows.
     *
     * @param bookId of the book
     * @param book   A collection with the columns to be set. May contain extra data.
     */
    private void insertBookDependents(final long bookId,
                                      @NonNull final Book book) {

        if (book.containsKey(UniqueId.BKEY_BOOKSHELF_ARRAY)) {
            ArrayList<Bookshelf> list = book.getList(UniqueId.BKEY_BOOKSHELF_ARRAY);
            insertBookBookshelf(bookId, list);
        }

        if (book.containsKey(UniqueId.BKEY_AUTHOR_ARRAY)) {
            ArrayList<Author> list = book.getList(UniqueId.BKEY_AUTHOR_ARRAY);
            insertBookAuthors(bookId, list);
        }

        if (book.containsKey(UniqueId.BKEY_SERIES_ARRAY)) {
            ArrayList<Series> list = book.getList(UniqueId.BKEY_SERIES_ARRAY);
            insertBookSeries(bookId, list);
        }

        if (book.containsKey(UniqueId.BKEY_TOC_ENTRY_ARRAY)) {
            ArrayList<TocEntry> list = book.getList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
            // update: toc entries are two steps away; they can exist in other books
            updateOrInsertTOC(bookId, list);
        }

        if (book.containsKey(UniqueId.KEY_BOOK_LOANEE)
                && !book.getString(UniqueId.KEY_BOOK_LOANEE).isEmpty()) {
            updateOrInsertLoan(bookId, book.getString(DOM_BOOK_LOANEE.name));
        }
    }

    /**
     * Return a {@link Cursor} with a single column, the UUID of all {@link Book}.
     * Used to loop across all books during backup to save the cover images.
     *
     * @return {@link Cursor} containing all records, if any
     */
    @NonNull
    public Cursor fetchBookUuidList() {
        return mSyncedDb.rawQuery(SqlSelectFullTable.BOOK_UUIDS, null);
    }

    /**
     * @param isbn to search for (10 or 13)
     * @param both set to <tt>true</tt> to search for both isbn 10 and 13.
     *
     * @return book id, or 0 if not found
     */
    public long getBookIdFromIsbn(@NonNull final String isbn,
                                  final boolean both) {
        SynchronizedStatement stmt;
        if (both && IsbnUtils.isValid(isbn)) {
            stmt = mStatements.get(STMT_GET_BOOK_ID_FROM_ISBN_2);
            if (stmt == null) {
                stmt = mStatements.add(STMT_GET_BOOK_ID_FROM_ISBN_2, SqlGet.BOOK_ID_BY_ISBN2);
            }
            stmt.bindString(2, IsbnUtils.isbn2isbn(isbn));
        } else {
            stmt = mStatements.get(STMT_GET_BOOK_ID_FROM_ISBN_1);
            if (stmt == null) {
                stmt = mStatements.add(STMT_GET_BOOK_ID_FROM_ISBN_1, SqlGet.BOOK_ID_BY_ISBN);
            }
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
        SynchronizedStatement stmt = mStatements.get(STMT_CHECK_BOOK_EXISTS);
        if (stmt == null) {
            stmt = mStatements.add(STMT_CHECK_BOOK_EXISTS, SqlSelect.BOOK_EXISTS);
        }
        stmt.bindLong(1, bookId);
        return stmt.count() == 1;
    }

    /**
     * Create the link between {@link Book} and {@link Series}.
     * <p>
     * {@link DatabaseDefinitions#TBL_BOOK_SERIES}
     * <p>
     * Note that {@link DatabaseDefinitions#DOM_BOOK_SERIES_POSITION} is a simple incrementing
     * counter matching the order of the passed list.
     *
     * @param bookId the book which
     * @param list   belongs to this *ordered* list of {@link Series}
     */
    private void insertBookSeries(final long bookId,
                                  @NonNull final List<Series> list) {
        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookSeriesByBookId(bookId);

        // anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        SynchronizedStatement stmt = mStatements.get(STMT_ADD_BOOK_SERIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_ADD_BOOK_SERIES, SqlInsert.BOOK_SERIES);
        }

        // The list MAY contain duplicates (eg. from Internet lookups of multiple
        // sources), so we track them in a hash map
        final Map<String, Boolean> idHash = new HashMap<>();
        int position = 0;
        for (Series series : list) {
            if (series.fixupId(this) == 0) {
                if (BuildConfig.DEBUG) {
                    Logger.info(this, "insertBookSeries",
                                "inserting series: " + series.stringEncoded());
                }
                insertSeries(series);
            }

            String uniqueId = series.getId() + '(' + series.getNumber().toUpperCase() + ')';
            if (!idHash.containsKey(uniqueId)) {
                idHash.put(uniqueId, true);
                position++;
                stmt.bindLong(1, bookId);
                stmt.bindLong(2, series.getId());
                stmt.bindString(3, series.getNumber());
                stmt.bindLong(4, position);
                stmt.executeInsert();
            }
        }
    }

    /**
     * Delete the link between Series and the given Book.
     * Note that the actual Series are not deleted.
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    private int deleteBookSeriesByBookId(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOK_SERIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_BOOK_SERIES, SqlDelete.BOOK_SERIES_BY_BOOK_ID);
        }
        stmt.bindLong(1, bookId);
        return stmt.executeUpdateDelete();
    }

    /**
     * Insert a List of TocEntry's for the given book.
     *
     * @param bookId of the book
     * @param list   the list
     */
    private void updateOrInsertTOC(final long bookId,
                                   @NonNull final List<TocEntry> list) {
        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookTocEntryByBookId(bookId);

        // anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        long position = 0;
        for (TocEntry tocEntry : list) {
            if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
                Logger.info(this, "Adding TocEntryByBookId: " + tocEntry);
            }

            // handle the author.
            Author author = tocEntry.getAuthor();
            if (author.fixupId(this) == 0) {
                if (BuildConfig.DEBUG) {
                    Logger.info(this, "updateOrInsertTOC",
                                "inserting author: " + author.stringEncoded());
                }
                insertAuthor(author);
            }

            // As an entry can exist in multiple books, try to find the entry.
            if (tocEntry.fixupId(this) == 0) {
                if (BuildConfig.DEBUG) {
                    Logger.info(this, "updateOrInsertTOC",
                                "inserting tocEntry: " + tocEntry.stringEncoded());
                }
                insertTOCEntry(tocEntry);
            } else {
                // if found, FIXME: (do not) unconditionally update the publication year.
                updateTOCEntry(tocEntry);
            }

            // create the book<->TocEntry link
            SynchronizedStatement stmt = mStatements.get(STMT_ADD_BOOK_TOC_ENTRY);
            if (stmt == null) {
                stmt = mStatements.add(STMT_ADD_BOOK_TOC_ENTRY, SqlInsert.BOOK_TOC_ENTRY);
            }
            position++;
            stmt.bindLong(1, tocEntry.getId());
            stmt.bindLong(2, bookId);
            stmt.bindLong(3, position);
            stmt.executeInsert();

            if (DEBUG_SWITCHES.TMP_ANTHOLOGY && BuildConfig.DEBUG) {
                Logger.info(this, "     bookId   : " + bookId);
                Logger.info(this, "     authorId : " + author.getId());
                Logger.info(this, "     tocId    : " + tocEntry.getId());
                Logger.info(this, "     position : " + position);
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
     * @param bookId the book which
     * @param list   belongs to this *ordered* list of {@link Author}
     */
    private void insertBookAuthors(final long bookId,
                                   @NonNull final List<Author> list) {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookAuthorByBookId(bookId);

        // anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        SynchronizedStatement stmt = mStatements.get(STMT_ADD_BOOK_AUTHORS);
        if (stmt == null) {
            stmt = mStatements.add(STMT_ADD_BOOK_AUTHORS, SqlInsert.BOOK_AUTHOR);
        }

        // The list MAY contain duplicates (eg. from Internet lookups of multiple
        // sources), so we track them in a hash table
        final Map<String, Boolean> idHash = new HashMap<>();
        int position = 0;
        for (Author author : list) {
            // find/insert the author
            if (author.fixupId(this) == 0) {
                if (BuildConfig.DEBUG) {
                    Logger.info(this, "insertBookAuthors",
                                "inserting author: " + author.stringEncoded());
                }
                insertAuthor(author);
            }

            // we use the id as the KEY here, so yes, a String.
            String authorIdStr = String.valueOf(author.getId());
            if (!idHash.containsKey(authorIdStr)) {
                // indicate this author(id) is already present...
                // but override, so we get elimination of duplicates.
                idHash.put(authorIdStr, true);

                position++;
                stmt.bindLong(1, bookId);
                stmt.bindLong(2, author.getId());
                stmt.bindLong(3, position);
                stmt.executeInsert();
            }
        }
    }

    /**
     * Delete the link between Authors and the given Book.
     * Note that the actual Authors are not deleted.
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    private int deleteBookAuthorByBookId(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOK_AUTHORS);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_BOOK_AUTHORS, SqlDelete.BOOK_AUTHOR_BY_BOOK_ID);
        }
        stmt.bindLong(1, bookId);
        return stmt.executeUpdateDelete();
    }

    /**
     * Create the link between {@link Book} and {@link Bookshelf}.
     * <p>
     * {@link DatabaseDefinitions#TBL_BOOK_BOOKSHELF}
     * <p>
     * Note that {@link DatabaseDefinitions#DOM_BOOK_SERIES_POSITION} is a simple incrementing
     * counter matching the order of the passed list.
     *
     * @param bookId the book which
     * @param list   belongs to this *ordered* list of {@link Bookshelf}
     */
    private void insertBookBookshelf(final long bookId,
                                     @NonNull final List<Bookshelf> list) {
        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookBookshelfByBookId(bookId);

        // anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        SynchronizedStatement stmt = mStatements.get(STMT_ADD_BOOK_BOOKSHELF);
        if (stmt == null) {
            stmt = mStatements.add(STMT_ADD_BOOK_BOOKSHELF, SqlInsert.BOOK_BOOKSHELF);
        }

        for (Bookshelf bookshelf : list) {
            if (bookshelf.getName().isEmpty()) {
                continue;
            }

            if (bookshelf.fixupId(this) == 0) {
                if (BuildConfig.DEBUG) {
                    Logger.info(this, "insertBookBookshelf",
                                "inserting bookshelf: " + bookshelf.stringEncoded());
                }
                insertBookshelf(bookshelf);
            }

            stmt.bindLong(1, bookId);
            stmt.bindLong(2, bookshelf.getId());
            stmt.executeInsert();
        }
    }

    /**
     * Delete the link between Bookshelves and the given Book.
     * Note that the actual Bookshelves are not deleted.
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    private int deleteBookBookshelfByBookId(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOK_BOOKSHELF);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_BOOK_BOOKSHELF,
                                   SqlDelete.BOOK_BOOKSHELF_BY_BOOK_ID);
        }
        stmt.bindLong(1, bookId);
        return stmt.executeUpdateDelete();
    }

    /**
     * update books for which the new ID is not already present.
     * <p>
     * In other words: replace the id for books that are not already linked with the new one
     *
     * @param table  the link-table between books and author or series
     * @param domain name of the column to use, again author or series
     * @param fromId the id to replace
     * @param toId   the id to use
     */
    private void globalReplaceId(@NonNull final TableDefinition table,
                                 @NonNull final DomainDefinition domain,
                                 final long fromId,
                                 final long toId) {
        /*
        sql = "Update " + tableName + " Set " + objectIdField + " = " + newId
        + " Where " + objectIdField + " = " + oldId
        + " and Not Exists(Select NULL From " + tableName + " ba Where "
        + "                 ba." + KEY_BOOK + " = " + tableName + "." + KEY_BOOK
        + "                 and ba." + objectIdField + " = " + newId + ")";
         */
        SynchronizedStatement stmt = mSyncedDb.compileStatement(
                "UPDATE " + table + " SET " + domain + "=? WHERE " + domain + "=?"
                        + " AND NOT EXISTS"

                        + " (SELECT NULL FROM " + table.ref() + " WHERE "
                        // left: the aliased table, right the actual table
                        + table.dot(DOM_FK_BOOK_ID) + '=' + table + '.' + DOM_FK_BOOK_ID
                        // left: the aliased table
                        + " AND " + table.dot(domain) + "=?)");

        stmt.bindLong(1, toId);
        stmt.bindLong(2, fromId);
        stmt.bindLong(3, toId);
        stmt.executeUpdateDelete();
        stmt.close();
    }

    /**
     * Books use an ordered list of Authors and Series (custom order by the end-user).
     * When replacing one of them, lists have to be adjusted.
     * <p>
     * transaction: needs.
     * <p>
     * throws exceptions, caller must handle
     *
     * @param table         : TBL_BOOK_AUTHORS or TBL_BOOK_SERIES
     * @param domain        : DOM_FK_AUTHOR_ID or DOM_FK_SERIES_ID
     * @param positionField : DOM_BOOK_AUTHOR_POSITION or DOM_BOOK_SERIES_POSITION
     */
    private void globalReplacePositionedBookItem(@NonNull final TableDefinition table,
                                                 @NonNull final DomainDefinition domain,
                                                 @NonNull final DomainDefinition positionField,
                                                 final long fromId,
                                                 final long toId) {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        SynchronizedStatement delStmt = null;
        SynchronizedStatement replacementIdPosStmt = null;
        SynchronizedStatement checkMinStmt = null;
        SynchronizedStatement moveStmt = null;

        // Re-position remaining items up one place to ensure positions remain correct
        /*
        sql = "select * from " + tableName + " Where " + objectIdField + " = " + oldId
        + " And Exists(Select NULL From " + tableName + " ba Where "
        + "                 ba." + KEY_BOOK + " = " + tableName + "." + KEY_BOOK
        + "                 and ba." + objectIdField + " = " + newId + ")";
         */
        String sql = "SELECT " + DOM_FK_BOOK_ID + ',' + positionField
                + " FROM " + table + " WHERE " + domain + "=?"
                + " AND EXISTS"

                + " (SELECT NULL FROM " + table.ref() + " WHERE "
                // left: the aliased table, right the actual table
                + table.dot(DOM_FK_BOOK_ID) + '=' + table + '.' + DOM_FK_BOOK_ID
                // left: the aliased table
                + " AND " + table.dot(domain) + "=?)";

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(fromId),
                                                                  String.valueOf(toId)})) {

            if (DEBUG_SWITCHES.DBA_GLOBAL_REPLACE && BuildConfig.DEBUG) {
                Logger.info(this, "globalReplacePositionedBookItem",
                            "Re-position, total count=" + cursor.getCount());
            }

            // Get the column indexes we need
            final int bookCol = cursor.getColumnIndexOrThrow(DOM_FK_BOOK_ID.name);
            final int posCol = cursor.getColumnIndexOrThrow(positionField.name);

            // Delete a specific object record
            delStmt = mSyncedDb.compileStatement(
                    "DELETE FROM " + table
                            + " WHERE " + domain + "=? AND " + DOM_FK_BOOK_ID + "=?");

            // Get the position of the already-existing 'new/replacement' object
            replacementIdPosStmt = mSyncedDb.compileStatement(
                    "SELECT " + positionField + " FROM " + table
                            + " WHERE " + DOM_FK_BOOK_ID + "=? AND " + domain + "=?");

            // Move a single entry to a new position
            moveStmt = mSyncedDb.compileStatement(
                    "UPDATE " + table + " SET " + positionField + "=?"
                            + " WHERE " + DOM_FK_BOOK_ID + "=? AND " + positionField + "=?");

            // Sanity check to deal with legacy bad data
            checkMinStmt = mSyncedDb.compileStatement(
                    "SELECT min(" + positionField + ") FROM " + table
                            + " WHERE " + DOM_FK_BOOK_ID + "=?");

            // Loop through all instances of the old object appearing
            while (cursor.moveToNext()) {
                // Get the details of the old object
                long bookId = cursor.getLong(bookCol);
                long pos = cursor.getLong(posCol);

                // Get the position of the new/replacement object
                replacementIdPosStmt.bindLong(1, bookId);
                replacementIdPosStmt.bindLong(2, toId);
                long replacementIdPos = replacementIdPosStmt.simpleQueryForLong();
                if (DEBUG_SWITCHES.DBA_GLOBAL_REPLACE && BuildConfig.DEBUG) {
                    Logger.info(this, "globalReplacePositionedBookItem",
                                "id=" + bookId + ", to=" + toId + "=> replacementIdPos=" + replacementIdPos);
                }
                // Delete the old record
                delStmt.bindLong(1, fromId);
                delStmt.bindLong(2, toId);
                delStmt.executeUpdateDelete();

                // If the deleted object was more prominent than the new object,
                // move the new one up
                if (replacementIdPos > pos) {
                    if (DEBUG_SWITCHES.DBA_GLOBAL_REPLACE && BuildConfig.DEBUG) {
                        Logger.info(this, "globalReplacePositionedBookItem",
                                    "id=" + bookId + ", pos=" + pos
                                            + ", replacementIdPos=" + replacementIdPos);
                    }
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
                    if (DEBUG_SWITCHES.DBA_GLOBAL_REPLACE && BuildConfig.DEBUG) {
                        Logger.info(this, "globalReplacePositionedBookItem",
                                    "id=" + bookId + ", pos to 1, minPos=" + minPos);
                    }
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
     * @return list of TocEntry for this book
     */
    @NonNull
    public ArrayList<TocEntry> getTocEntryByBook(final long bookId) {
        ArrayList<TocEntry> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectList.TOC_ENTRIES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            if (cursor.getCount() == 0) {
                return list;
            }
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_TOC_ENTRIES,
                                                   DOM_AUTHOR_FAMILY_NAME,
                                                   DOM_AUTHOR_GIVEN_NAMES,
                                                   DOM_AUTHOR_IS_COMPLETE);

            while (cursor.moveToNext()) {
                Author author = new Author(mapper.getLong(DOM_FK_AUTHOR_ID),
                                           mapper.getString(DOM_AUTHOR_FAMILY_NAME),
                                           mapper.getString(DOM_AUTHOR_GIVEN_NAMES),
                                           mapper.getBoolean(DOM_AUTHOR_IS_COMPLETE));

                list.add(new TocEntry(mapper.getLong(DOM_PK_ID),
                                      author,
                                      mapper.getString(DOM_TITLE),
                                      mapper.getString(DOM_FIRST_PUBLICATION)));
            }
        }
        return list;
    }

    /**
     * Get a list of book id's (most often just the one) in which this TocEntry (story) is present.
     *
     * @param tocId id of the entry (story)
     *
     * @return id-of-book list
     * <p>
     * ENHANCE: we use Integer here, but a primary key can be 8 bytes, i.e. a Long
     * On the other hand, the autoincrement reaching 33 bit long values....
     */
    public ArrayList<Integer> getBookIdsByTocEntry(final long tocId) {
        ArrayList<Integer> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlGet.BOOK_ID_BY_TOC_ENTRY_ID,
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
    public ArrayList<Author> getAuthorsByBookId(final long bookId) {
        ArrayList<Author> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectList.AUTHORS_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_AUTHORS);
            while (cursor.moveToNext()) {
                list.add(new Author(mapper));
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
    public ArrayList<Series> getSeriesByBookId(final long bookId) {
        ArrayList<Series> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectList.SERIES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            if (cursor.getCount() == 0) {
                return list;
            }
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_SERIES, DOM_BOOK_SERIES_NUM);
            while (cursor.moveToNext()) {
                list.add(new Series(mapper));
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
//                book.putAuthorList(getAuthorsByBookId(bookId));
//                book.putSeriesList(getSeriesByBookId(bookId));
//                book.putTOC(getTocEntryByBook(bookId));
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

        // "a." (TBL_AUTHOR), "b." (TBL_BOOKS), "s." (TBL_SERIES}
        // BUT... here they have double-use:
        // in SQL macros -> the tables.
        // in the sql -> the query/sub-query.
        //
        // so DO NOT replace them with table.dot() etc... !

        // there is no 'real' FROM table set. The 'from' is a combo of three sub-selects
        return "SELECT "
                + "b.*"
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE)
                + ',' + SqlColumns.AUTHOR_FORMATTED
                + ',' + SqlColumns.AUTHOR_FORMATTED_GIVEN_FIRST
                + ',' + "a." + DOM_FK_AUTHOR_ID + " AS " + DOM_FK_AUTHOR_ID

                // use a dummy series for books not in a series (i.e. don't use null's)
                + ',' + "Coalesce(s." + DOM_FK_SERIES_ID + ", 0) AS " + DOM_FK_SERIES_ID
                + ',' + "Coalesce(s." + DOM_SERIES_NAME + ", '') AS " + DOM_SERIES_NAME
                + ',' + "Coalesce(s." + DOM_BOOK_SERIES_NUM + ", '') AS " + DOM_BOOK_SERIES_NUM

                + ',' + SqlColumns.SERIES_LIST

                + " FROM"

                // all books (with WHERE clause if passed in).
                + " ("
                + "SELECT DISTINCT " + SqlColumns.BOOK + " FROM " + TBL_BOOKS.ref()
                + (!whereClause.isEmpty() ? " WHERE " + " (" + whereClause + ')' : "")
                + " ORDER BY lower(" + TBL_BOOKS.dot(DOM_TITLE) + ") " + COLLATION + " ASC"
                + ") b"

                // with their primary author
                + " JOIN ("
                + "SELECT "
                + DOM_FK_AUTHOR_ID
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE)
                + ',' + SqlColumns.AUTHOR_FORMATTED
                + ',' + TBL_BOOK_AUTHOR.dotAs(DOM_FK_BOOK_ID)

                + " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + ") a ON a." + DOM_FK_BOOK_ID + "=b." + DOM_PK_ID
                + " AND a." + DOM_FK_AUTHOR_ID + "=b." + DOM_FK_AUTHOR_ID

                // and (if they have one) their primary series
                + " LEFT OUTER JOIN ("
                + "SELECT "
                + DOM_FK_SERIES_ID
                + ',' + TBL_SERIES.dotAs(DOM_SERIES_NAME)
                + ',' + TBL_SERIES.dotAs(DOM_SERIES_IS_COMPLETE)
                + ',' + TBL_BOOK_SERIES.dotAs(DOM_BOOK_SERIES_NUM)
                + ',' + TBL_BOOK_SERIES.dotAs(DOM_FK_BOOK_ID)
                + ',' + SqlColumns.SERIES_WITH_NUMBER

                + " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + ") s ON s." + DOM_FK_BOOK_ID + "=b." + DOM_PK_ID
                + " AND s." + DOM_FK_SERIES_ID + "=b." + DOM_FK_SERIES_ID
                + " AND lower(s." + DOM_BOOK_SERIES_NUM + ")=lower(b." + DOM_BOOK_SERIES_NUM + ')'
                + COLLATION;
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
    public BookCursor fetchBookById(final long bookId) {
        return (BookCursor)
                mSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                              getAllBooksSql(TBL_BOOKS.dot(DOM_PK_ID) + "=?"),
                                              new String[]{String.valueOf(bookId)},
                                              "");
    }

    /**
     * Return a {@link BookCursor} for the given whereClause.
     *
     * @param whereClause to add to books search criteria (without the keyword 'WHERE')
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBooksForFieldUpdate(@NonNull final String whereClause) {
        // the order by is used to be able to restart the update.
        String sql = getAllBooksSql(whereClause)
                + " ORDER BY " + DatabaseDefinitions.TBL_BOOKS.dot(DatabaseDefinitions.DOM_PK_ID);

        return (BookCursor) mSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                                          sql, null, "");
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
        if (isbnList.isEmpty()) {
            throw new IllegalArgumentException("isbnList was empty");
        }

        StringBuilder where = new StringBuilder(TBL_BOOKS.dot(DOM_BOOK_ISBN));
        if (isbnList.size() == 1) {
            // single ISBN
            where.append("='").append(encodeString(isbnList.get(0))).append('\'');
        } else {
            where.append(" IN (")
                 .append(Csv.join(",", isbnList, new Csv.Formatter<String>() {
                     @Override
                     public String format(@NonNull final String element) {
                         return '\'' + encodeString(element) + '\'';
                     }
                 }))
                 .append(')');
        }
        return (BookCursor) mSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                                          where.toString(),
                                                          null, "");
    }

    /**
     * A complete export of all tables (flattened) in the database.
     *
     * @param sinceDate to select all books added/modified since that date.
     *                  Set to null for *ALL* books.
     *
     * @return BookCursor over all books, authors, etc
     */
    @NonNull
    public BookCursor fetchBooksForExport(@Nullable final Date sinceDate) {
        String whereClause;
        if (sinceDate == null) {
            whereClause = "";
        } else {
            whereClause = " WHERE " + TBL_BOOKS.dot(DOM_LAST_UPDATE_DATE)
                    + ">'" + DateUtils.utcSqlDateTime(sinceDate) + '\'';
        }

        String sql = "SELECT DISTINCT "
                + SqlColumns.BOOK
                + ',' + TBL_BOOK_LOANEE.dotAs(DOM_BOOK_LOANEE)

                + " FROM " + TBL_BOOKS.ref() + " LEFT OUTER JOIN " + TBL_BOOK_LOANEE.ref()
                + " ON (" + TBL_BOOK_LOANEE.dot(DOM_FK_BOOK_ID)
                + '=' + TBL_BOOKS.dot(DOM_PK_ID) + ')'
                + whereClause
                + " ORDER BY " + TBL_BOOKS.dot(DOM_PK_ID);
        return (BookCursor) mSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                                          sql, null, "");
    }

    /**
     * Creates a new bookshelf in the database.
     *
     * @param bookshelf object to insert. Will be updated with the id.
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    private long insertBookshelf(@NonNull final Bookshelf /* in/out */ bookshelf) {
        SynchronizedStatement stmt = mStatements.get(STMT_ADD_BOOKSHELF);
        if (stmt == null) {
            stmt = mStatements.add(STMT_ADD_BOOKSHELF, SqlInsert.BOOKSHELF);
        }

        stmt.bindString(1, bookshelf.getName());
        stmt.bindLong(2, bookshelf.getStyle(this).getId());
        long iId = stmt.executeInsert();
        if (iId > 0) {
            bookshelf.setId(iId);
        }
        return iId;
    }

    /**
     * Takes all books from Bookshelf 'sourceId', and puts them onto Bookshelf 'destId',
     * then deletes Bookshelf 'sourceId'.
     * <p>
     * The style of the bookshelf will not be changed.
     *
     * @return the amount of books moved.
     */
    public int mergeBookshelves(final long sourceId,
                                final long destId) {

        int rowsAffected;

        ContentValues cv = new ContentValues();
        cv.put(DOM_FK_BOOKSHELF_ID.name, destId);

        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            rowsAffected = mSyncedDb.update(TBL_BOOK_BOOKSHELF.getName(), cv,
                                            DOM_FK_BOOKSHELF_ID + "=?",
                                            new String[]{String.valueOf(sourceId)});

            // delete the now empty shelf.
            deleteBookshelf(sourceId);

            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(txLock);
        }

        return rowsAffected;
    }

    /**
     * Delete the bookshelf with the given rowId.
     *
     * @param id id of bookshelf to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteBookshelf(final long id) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOKSHELF);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_BOOKSHELF, SqlDelete.BOOKSHELF_BY_ID);
        }
        stmt.bindLong(1, id);
        return stmt.executeUpdateDelete();
    }

    /**
     * Return the bookshelf id based on the name.
     *
     * @param name bookshelf to search for
     *
     * @return bookshelf id, or 0 when not found
     */
    public long getBookshelfIdByName(@NonNull final String name) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOKSHELF_ID_BY_NAME);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOKSHELF_ID_BY_NAME, SqlGet.BOOKSHELF_ID_BY_NAME);
        }
        stmt.bindString(1, name);
        return stmt.simpleQueryForLongOrZero();
    }

    /**
     * @param name of bookshelf to find
     *
     * @return the Bookshelf, or null if not found
     */
    @Nullable
    public Bookshelf getBookshelfByName(@NonNull final String name) {

        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.BOOKSHELF_BY_NAME,
                                                new String[]{name})) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_BOOKSHELF);
            if (cursor.moveToFirst()) {
                return new Bookshelf(mapper);
            }
            return null;
        }
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
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_BOOKSHELF);
            if (cursor.moveToFirst()) {
                return new Bookshelf(mapper);
            }
            return null;
        }
    }

    /**
     * Update a bookshelf.
     *
     * @param bookshelf to update
     *
     * @return rows affected, should be 1 for success
     */
    @SuppressWarnings("UnusedReturnValue")
    private int updateBookshelf(@NonNull final Bookshelf bookshelf) {

        ContentValues cv = new ContentValues();
        cv.put(DOM_BOOKSHELF.name, bookshelf.getName());
        cv.put(DOM_FK_STYLE_ID.name, bookshelf.getStyle(this).getId());

        return mSyncedDb.update(TBL_BOOKSHELF.getName(), cv,
                                DOM_PK_ID + "=?",
                                new String[]{String.valueOf(bookshelf.getId())});
    }

    /**
     * Add or update the passed Bookshelf, depending whether bookshelf.id == 0.
     *
     * @param bookshelf object to insert or update. Will be updated with the id.
     *
     * @return <tt>true</tt> for success
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertBookshelf(@NonNull final /* in/out */ Bookshelf bookshelf) {

        if (bookshelf.getId() != 0) {
            return updateBookshelf(bookshelf) > 0;
        } else {
            // try to find first.
            if (bookshelf.fixupId(this) == 0) {
                return insertBookshelf(bookshelf) > 0;
            }
        }
        return false;
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
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_BOOKSHELF);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(mapper));
            }
        }
        return list;
    }

    /**
     * Get a list of all the bookshelves this book is on.
     *
     * @param bookId to use
     *
     * @return the list
     */
    public ArrayList<Bookshelf> getBookshelvesByBookId(final long bookId) {
        ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectList.BOOKSHELVES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_BOOKSHELF);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(mapper));
            }
            return list;
        }
    }

    /**
     * Get a list of all user defined styles, arranged in a lookup map.
     *
     * @return the lookup map
     */
    @NonNull
    public Map<Long, BooklistStyle> getBooklistStyles() {
        Map<Long, BooklistStyle> list = new LinkedHashMap<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.BOOKLIST_STYLES, null)) {
            if (cursor.getCount() == 0) {
                return list;
            }
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_BOOKLIST_STYLES);
            while (cursor.moveToNext()) {
                long id = mapper.getLong(DOM_PK_ID);
                list.put(id, new BooklistStyle(id, mapper.getString(DOM_UUID)));
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
        SynchronizedStatement stmt = mStatements.get(STMT_GET_BOOKLIST_STYLE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_BOOKLIST_STYLE, SqlGet.BOOKLIST_STYLE_ID_BY_UUID);
        }
        stmt.bindString(1, uuid);
        return stmt.simpleQueryForLongOrZero();
    }

    /**
     * Create a new booklist style.
     *
     * @param style object to insert. Will be updated with the id.
     *
     * @return the row ID of the last row inserted, if this insert is successful. -1 otherwise.
     */
    public long insertBooklistStyle(@NonNull final BooklistStyle /* in/out */ style) {
        SynchronizedStatement stmt = mStatements.get(STMT_ADD_BOOKLIST_STYLE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_ADD_BOOKLIST_STYLE, SqlInsert.BOOKLIST_STYLE);
        }

        stmt.bindString(1, style.getUuid());
        long iId = stmt.executeInsert();
        if (iId > 0) {
            style.setId(iId);
        }
        return iId;
    }


    /**
     * Delete a style.
     *
     * @param id of style to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteBooklistStyle(final long id) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOKLIST_STYLE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_BOOKLIST_STYLE, SqlDelete.STYLE_BY_ID);
        }
        stmt.bindLong(1, id);
        return stmt.executeUpdateDelete();
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
        String q = '%' + query + '%';
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

    public void updateFormat(@NonNull final String from,
                             @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_FORMAT);
        if (stmt == null) {
            stmt = mStatements.add(STMT_UPDATE_FORMAT, SqlUpdate.FORMAT);
        }
        stmt.bindString(1, to);
        stmt.bindString(2, from);
        stmt.executeUpdateDelete();
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

    public void updateGenre(@NonNull final String from,
                            @NonNull final String to) {

        if (Objects.equals(from, to)) {
            return;
        }
        SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_GENRE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_UPDATE_GENRE, SqlUpdate.GENRE);
        }
        stmt.bindString(1, to);
        stmt.bindString(2, from);
        stmt.executeUpdateDelete();
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

    public void updateLanguage(@NonNull final String from,
                               @NonNull final String to) {

        if (Objects.equals(from, to)) {
            return;
        }
        SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_LANGUAGE);
        if (stmt == null) {
            stmt = mStatements.add(STMT_UPDATE_LANGUAGE, SqlUpdate.LANGUAGE);
        }
        stmt.bindString(1, to);
        stmt.bindString(2, from);
        stmt.executeUpdateDelete();
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

        SynchronizedStatement stmt = mStatements.get(STMT_GET_LOANEE_BY_BOOK_ID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_LOANEE_BY_BOOK_ID, SqlGet.LOANEE_BY_BOOK_ID);
        }

        stmt.bindLong(1, bookId);
        return stmt.simpleQueryForStringOrNull();
    }

    /**
     * @param bookId book to lend
     * @param loanee person to lend to
     *
     * @return <tt>true</tt> for success.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertLoan(final long bookId,
                                      @NonNull final String loanee) {

        if (getLoaneeByBookId(bookId) == null) {
            return insertLoan(bookId, loanee) > 0;
        } else {
            ContentValues cv = new ContentValues();
            cv.put(DOM_BOOK_LOANEE.name, loanee);
            int rowsAffected = mSyncedDb.update(TBL_BOOK_LOANEE.getName(), cv,
                                                DOM_FK_BOOK_ID + "=?",
                                                new String[]{String.valueOf(bookId)});
            return rowsAffected > 0;
        }
    }

    /**
     * Creates a new loan in the database.
     *
     * @param bookId the book we're lending
     * @param loanee name of the person we're loaning to.
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    public long insertLoan(final long bookId,
                           @NonNull final String loanee) {
        SynchronizedStatement stmt = mStatements.get(STMT_ADD_BOOK_LOAN);
        if (stmt == null) {
            stmt = mStatements.add(STMT_ADD_BOOK_LOAN, SqlInsert.BOOK_LOANEE);
        }
        stmt.bindLong(1, bookId);
        stmt.bindString(2, loanee);
        return stmt.executeInsert();
    }

    /**
     * Delete the loan for the given book.
     *
     * @param bookId id of book whose loan is to be deleted
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteLoan(final long bookId) {
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_BOOK_LOAN);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_BOOK_LOAN, SqlDelete.BOOK_LOANEE_BY_BOOK_ID);
        }
        stmt.bindLong(1, bookId);
        return stmt.executeUpdateDelete();
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
    public void updateLocation(@NonNull final String from,
                               @NonNull final String to) {

        if (Objects.equals(from, to)) {
            return;
        }
        SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_LOCATION);
        if (stmt == null) {
            stmt = mStatements.add(STMT_UPDATE_LOCATION, SqlUpdate.LOCATION);
        }
        stmt.bindString(1, to);
        stmt.bindString(2, from);
        stmt.executeUpdateDelete();
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
    public void updatePublisher(@NonNull final String from,
                                @NonNull final String to) {

        if (Objects.equals(from, to)) {
            return;
        }
        SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_PUBLISHER);
        if (stmt == null) {
            stmt = mStatements.add(STMT_UPDATE_PUBLISHER, SqlUpdate.PUBLISHER);
        }
        stmt.bindString(1, to);
        stmt.bindString(2, from);
        stmt.executeUpdateDelete();
    }

    /**
     * Creates a new series in the database.
     *
     * @param series object to insert. Will be updated with the id.
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    private long insertSeries(@NonNull final Series series) {
        SynchronizedStatement stmt = mStatements.get(STMT_ADD_SERIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_ADD_SERIES, SqlInsert.SERIES);
        }

        stmt.bindString(1, series.getName());
        stmt.bindLong(2, series.isComplete() ? 1 : 0);
        long iId = stmt.executeInsert();
        if (iId > 0) {
            series.setId(iId);
        }
        return iId;
    }

    /**
     * @param series to update
     *
     * @return rows affected, should be 1 for success
     */
    public int updateSeries(@NonNull final Series series) {

        ContentValues cv = new ContentValues();
        cv.put(DOM_SERIES_NAME.name, series.getName());
        cv.put(DOM_SERIES_IS_COMPLETE.name, series.isComplete());

        return mSyncedDb.update(TBL_SERIES.getName(), cv,
                                DOM_PK_ID + "=?",
                                new String[]{String.valueOf(series.getId())});
    }

    /**
     * Add or update the passed Series, depending whether series.id == 0.
     *
     * @param series object to insert or update. Will be updated with the id.
     *
     * @return <tt>true</tt> for success.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertSeries(@NonNull final /* in/out */ Series series) {

        if (series.getId() != 0) {
            return updateSeries(series) > 0;
        } else {
            // try to find first.
            if (series.fixupId(this) == 0) {
                return insertSeries(series) > 0;
            }
        }
        return false;
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
        SynchronizedStatement stmt = mStatements.get(STMT_DELETE_SERIES);
        if (stmt == null) {
            stmt = mStatements.add(STMT_DELETE_SERIES, SqlDelete.SERIES_BY_ID);
        }
        stmt.bindLong(1, id);
        return stmt.executeUpdateDelete();
    }

    /**
     * Get ALL series; mainly for the purpose of backups.
     *
     * @return Cursor over all series
     */
    @NonNull
    public Cursor fetchSeries() {
        return mSyncedDb.rawQuery(SqlSelectFullTable.SERIES, null);
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
            ColumnMapper mapper = new ColumnMapper(cursor, TBL_SERIES);
            if (cursor.moveToFirst()) {
                return new Series(id, mapper.getString(DOM_SERIES_NAME),
                                  mapper.getBoolean(DOM_SERIES_IS_COMPLETE));
            }
            return null;
        }
    }

    /**
     * Find a Series, and return its ID.
     *
     * @param series to find
     *
     * @return the id, or 0 when not found
     */
    public long getSeriesId(@NonNull final Series series) {
        SynchronizedStatement stmt = mStatements.get(STMT_GET_SERIES_ID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_SERIES_ID, SqlGet.SERIES_ID_BY_NAME);
        }
        // for now, we only need the name to find it.
        stmt.bindString(1, series.getName());
        return stmt.simpleQueryForLongOrZero();
    }

    /**
     * Refresh the passed Series from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the Series.
     * <p>
     * Will NOT insert a new Series if not found.
     */
    public void refreshSeries(@NonNull final Series /* out */ series) {
        if (series.getId() == 0) {
            // It wasn't a known series; see if it is now. If so, update ID.
            series.fixupId(this);
        } else {
            // It was a known author, see if it still is and fetch possibly updated fields.
            Series dbSeries = getSeries(series.getId());
            if (dbSeries != null) {
                // copy any updated fields
                series.copyFrom(dbSeries);
            } else {
                // series not found?, set the series as 'new'
                series.setId(0);
            }
        }
    }

    /**
     * @return <tt>true</tt> for success.
     */
    public boolean globalReplaceSeries(@NonNull final Series from,
                                       @NonNull final Series to) {

        // process the destination Series.
        if (!updateOrInsertSeries(to)) {
            Logger.error("Could not update Series");
            return false;
        }

        // Do some basic sanity checks
        if (from.getId() == 0 && from.fixupId(this) == 0) {
            Logger.error("Old Series is not defined");
            return false;
        }

        if (from.getId() == to.getId()) {
            return true;
        }

        if (DEBUG_SWITCHES.DBA_GLOBAL_REPLACE && BuildConfig.DEBUG) {
            Logger.info(this, "globalReplaceSeries",
                        "from=" + from.getId() + ", to=" + to.getId());
        }

        SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            // update books for which the new ID is not already present
            globalReplaceId(TBL_BOOK_SERIES, DOM_FK_SERIES_ID, from.getId(), to.getId());

            globalReplacePositionedBookItem(TBL_BOOK_SERIES,
                                            DOM_FK_SERIES_ID,
                                            DOM_BOOK_SERIES_POSITION,
                                            from.getId(), to.getId());

            mSyncedDb.setTransactionSuccessful();
        } catch (RuntimeException e) {
            Logger.error(e);
            return false;
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
        return true;
    }

    /**
     * @param series id
     *
     * @return number of books in series
     */
    public long countBooksInSeries(@NonNull final Series series) {
        if (series.getId() == 0 && series.fixupId(this) == 0) {
            return 0;
        }

        SynchronizedStatement stmt = mStatements.get(STMT_GET_SERIES_BOOK_COUNT);
        if (stmt == null) {
            stmt = mStatements.add(STMT_GET_SERIES_BOOK_COUNT, SqlSelect.COUNT_BOOKS_IN_SERIES);
        }
        // Be cautious
        synchronized (stmt) {
            stmt.bindLong(1, series.getId());
            return stmt.count();
        }
    }

    /**
     * Builds an arrayList of all series names.
     * Used for AutoCompleteTextView
     *
     * @return the list
     */
    @NonNull
    public ArrayList<String> getAllSeriesNames() {
        return getColumnAsList(SqlSelectFullTable.SERIES_NAME, DOM_SERIES_NAME.name);
    }

    /**
     * Set the Goodreads book id for this book.
     */
    public void setGoodreadsBookId(final long bookId,
                                   final long goodreadsBookId) {

        SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_GOODREADS_BOOK_ID);
        if (stmt == null) {
            stmt = mStatements.add(STMT_UPDATE_GOODREADS_BOOK_ID, SqlUpdate.GOODREADS_BOOK_ID);
        }
        stmt.bindLong(1, goodreadsBookId);
        stmt.bindLong(2, bookId);
        stmt.executeUpdateDelete();
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
        String sql = getAllBooksSql(TBL_BOOKS.dot(DOM_BOOK_GOODREADS_BOOK_ID) + "=?");
        return (BookCursor)
                mSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                              sql,
                                              new String[]{String.valueOf(grBookId)},
                                              "");
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
    public BookCursor fetchBooksForExportToGoodreads(final long startId,
                                                     final boolean updatesOnly) {
        String sql = "SELECT " + SqlColumns.GOODREADS_FIELDS_FOR_SENDING
                + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + ">?";

        if (updatesOnly) {
            sql += " AND " + DOM_LAST_UPDATE_DATE + '>' + DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
        }

        // the order by is used to be able to restart an export.
        sql += " ORDER BY " + DOM_PK_ID;

        return (BookCursor)
                mSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                              sql,
                                              new String[]{String.valueOf(startId)},
                                              "");
    }

    /**
     * Query to get the ISBN for the given {@link Book} id, for sending to Goodreads.
     *
     * @param bookId to retrieve
     *
     * @return {@link BookCursor} containing all records, if any
     */
    @NonNull
    public BookCursor fetchBookForExportToGoodreads(final long bookId) {
        return (BookCursor)
                mSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                              SqlSelect.GOODREADS_GET_BOOK_TO_SEND_BY_BOOK_ID,
                                              new String[]{String.valueOf(bookId)},
                                              "");
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
     * based on column definition (based on actual storage class of sqlite)
     *
     * @param tableName destination table
     * @param book      A collection with the columns to be set. May contain extra data.
     *
     * @return New and filtered ContentValues
     */
    @NonNull
    private ContentValues filterValues(@NonNull final String tableName,
                                       @NonNull final Book book) {

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
                                } else if (entry instanceof Double) {
                                    cv.put(columnInfo.name, (Double) entry);
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
                                } else if (entry instanceof Long) {
                                    cv.put(columnInfo.name, (Long) entry);
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

                            case Blob:
                                if (entry instanceof byte[]) {
                                    cv.put(columnInfo.name, (byte[]) entry);
                                } else if (entry != null) {
                                    throw new IllegalArgumentException(
                                            "non-null Blob but not a byte[] "
                                                    + "? column.name=" + columnInfo.name
                                                    + ", key=" + key);
                                }
                                break;

                            //noinspection UnnecessaryDefault
                            default:
                                Logger.error("unknown storage class for " + columnInfo.toString());
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
     * <p>
     * NOTE: This assumes a specific order for query parameters.
     * If modified, then update {@link #insertFts} , {@link #updateFts} and {@link #rebuildFts}
     *
     * @param bookCursor Cursor of books to update
     * @param stmt       Statement to execute (insert or update)
     */
    private void ftsSendBooks(@NonNull final BookCursor bookCursor,
                              @NonNull final SynchronizedStatement stmt) {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
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
        final BookCursorRow row = bookCursor.getCursorRow();
        while (bookCursor.moveToNext()) {
            // Reset authors/series/title
            authorText.setLength(0);
            seriesText.setLength(0);
            titleText.setLength(0);
            // Get list of authors
            try (Cursor authors = mSyncedDb.rawQuery(SqlFTS.GET_AUTHORS_BY_BOOK_ID,
                                                     new String[]{String.valueOf(row.getId())})) {
                // Get column indexes, if not already got
                if (colGivenNames < 0) {
                    colGivenNames = authors.getColumnIndex(DOM_AUTHOR_GIVEN_NAMES.name);
                }
                if (colFamilyName < 0) {
                    colFamilyName = authors.getColumnIndex(DOM_AUTHOR_FAMILY_NAME.name);
                }
                // Append each author
                while (authors.moveToNext()) {
                    authorText.append(authors.getString(colGivenNames));
                    authorText.append(' ');
                    authorText.append(authors.getString(colFamilyName));
                    authorText.append(';');
                }
            }

            // Get list of series
            try (Cursor series = mSyncedDb.rawQuery(SqlFTS.GET_SERIES_BY_BOOK_ID,
                                                    new String[]{String.valueOf(row.getId())})) {
                // Get column indexes, if not already got
                if (colSeriesInfo < 0) {
                    colSeriesInfo = series.getColumnIndexOrThrow(SqlFTS.DOM_SERIES_INFO);
                }
                // Append each series
                while (series.moveToNext()) {
                    seriesText.append(series.getString(colSeriesInfo));
                    seriesText.append(';');
                }
            }


            // Get list of anthology data (author and title)
            try (Cursor tocs = mSyncedDb.rawQuery(SqlFTS.GET_TOC_ENTRIES_BY_BOOK_ID,
                                                  new String[]{String.valueOf(row.getId())})) {
                // Get column indexes, if not already got
                if (colTOCEntryAuthorInfo < 0) {
                    colTOCEntryAuthorInfo = tocs.getColumnIndexOrThrow(
                            SqlFTS.DOM_TOC_ENTRY_AUTHOR_INFO);
                }
                if (colTOCEntryInfo < 0) {
                    colTOCEntryInfo = tocs.getColumnIndexOrThrow(SqlFTS.DOM_TOC_ENTRY_INFO);
                }
                // Append each series
                while (tocs.moveToNext()) {
                    authorText.append(tocs.getString(colTOCEntryAuthorInfo));
                    authorText.append(';');
                    titleText.append(tocs.getString(colTOCEntryInfo));
                    titleText.append(';');
                }
            }

            // Set the parameters and call
            bindStringOrNull(stmt, 1, authorText.toString());
            // Titles should only contain title, not SERIES
            bindStringOrNull(stmt, 2, row.getTitle() + "; " + titleText);
            // We could add a 'series' column, or just add it as part of the description
            bindStringOrNull(stmt, 3, row.getDescription() + seriesText);
            bindStringOrNull(stmt, 4, row.getNotes());
            bindStringOrNull(stmt, 5, row.getPublisherName());
            bindStringOrNull(stmt, 6, row.getGenre());
            bindStringOrNull(stmt, 7, row.getLocation());
            bindStringOrNull(stmt, 8, row.getIsbn());
            // DOM_PK_DOCID
            stmt.bindLong(9, row.getId());

            stmt.execute();
        }
    }

    /**
     * Bind a string or NULL value to a parameter since binding a NULL
     * in bindString produces an error.
     * <p>
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
     * <p>
     * Transaction: required
     */
    private void insertFts(final long bookId) {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_NEEDS_TRANSACTION);
        }

        try {
            SynchronizedStatement stmt = mStatements.get(STMT_ADD_FTS);
            if (stmt == null) {
                stmt = mStatements.add(STMT_ADD_FTS, SqlFTS.INSERT);
            }

            try (BookCursor books = (BookCursor)
                    mSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                                  SqlSelect.BOOK_BY_ID,
                                                  new String[]{String.valueOf(bookId)},
                                                  "")) {
                ftsSendBooks(books, stmt);
            }
        } catch (RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    /**
     * Update an existing FTS record.
     * <p>
     * Transaction: required
     */
    private void updateFts(final long bookId) {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException();
        }

        try {
            SynchronizedStatement stmt = mStatements.get(STMT_UPDATE_FTS);
            if (stmt == null) {
                stmt = mStatements.add(STMT_UPDATE_FTS, SqlFTS.UPDATE);
            }
            try (BookCursor books = (BookCursor)
                    mSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                                  SqlSelect.BOOK_BY_ID,
                                                  new String[]{String.valueOf(bookId)},
                                                  "")) {
                ftsSendBooks(books, stmt);
            }
        } catch (RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    /**
     * Rebuild the entire FTS database.
     * This can take several seconds with many books or a slow phone.
     */
    public void rebuildFts() {

        if (mSyncedDb.inTransaction()) {
            throw new TransactionException();
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

        try {
            // Drop and recreate our temp copy
            ftsTemp.drop(mSyncedDb);
            ftsTemp.create(mSyncedDb, false);

            try (SynchronizedStatement insert = mSyncedDb.compileStatement(
                    "INSERT INTO " + ftsTemp.getName() + SqlFTS.INSERT_BODY);
                 BookCursor books = (BookCursor)
                         mSyncedDb.rawQueryWithFactory(BOOKS_CURSOR_FACTORY,
                                                       SqlSelectFullTable.BOOKS,
                                                       null,
                                                       "")) {
                ftsSendBooks(books, insert);
            }

            mSyncedDb.setTransactionSuccessful();
        } catch (RuntimeException e) {
            // updating FTS should not be fatal.
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
                // Drop old table, ready for rename
                TBL_BOOKS_FTS.drop(mSyncedDb);
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
     * <p>
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
         * <p>
         * Dev note: adding fields ? Now is a good time to update
         * {@link Book#duplicate}/
         */
        private static final String BOOK =
                TBL_BOOKS.dotAs(DOM_PK_ID)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_UUID)
                        + ',' + TBL_BOOKS.dotAs(DOM_TITLE)
                        // publication data
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_ISBN)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PUBLISHER)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_ANTHOLOGY_BITMASK)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DATE_PUBLISHED)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_LISTED)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_LISTED_CURRENCY)
                        + ',' + TBL_BOOKS.dotAs(DOM_FIRST_PUBLICATION)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_FORMAT)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_GENRE)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_LANGUAGE)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PAGES)
                        // common blurb
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DESCRIPTION)

                        // partially edition info, partially use-owned info.
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_EDITION_BITMASK)
                        // user data
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_NOTES)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_LOCATION)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_SIGNED)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_RATING)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_READ)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_READ_START)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_READ_END)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DATE_ACQUIRED)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_PAID)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_PRICE_PAID_CURRENCY)
                        // added/updated
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_DATE_ADDED)
                        + ',' + TBL_BOOKS.dotAs(DOM_LAST_UPDATE_DATE)
                        // external links
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_LIBRARY_THING_ID)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_ISFDB_ID)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_GOODREADS_BOOK_ID)
                        + ',' + TBL_BOOKS.dotAs(DOM_BOOK_GOODREADS_LAST_SYNC_DATE)

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
         * Single column, with the formatted name of the Author.
         * <p>
         * If no given name -> family name.
         * otherwise -> family, given.
         */
        private static final String AUTHOR_FORMATTED =
                " CASE WHEN " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "=''"
                        + " THEN " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                        + " ELSE " + AUTHOR_FAMILY_COMMA_GIVEN
                        + " END"
                        + " AS " + DOM_AUTHOR_FORMATTED;

        /**
         * If no given name -> family name.
         * otherwise -> given family.
         */
        private static final String AUTHOR_FORMATTED_GIVEN_FIRST =
                " CASE WHEN " + TBL_AUTHORS.dot(DOM_AUTHOR_GIVEN_NAMES) + "=''"
                        + " THEN " + TBL_AUTHORS.dot(DOM_AUTHOR_FAMILY_NAME)
                        + " ELSE " + AUTHOR_GIVEN_SPACE_FAMILY
                        + " END"
                        + " AS " + DOM_AUTHOR_FORMATTED_GIVEN_FIRST;


        /**
         * If no number -> series name.
         * otherwise -> series name #number
         */
        private static final String SERIES_WITH_NUMBER =
                " CASE WHEN " + DOM_BOOK_SERIES_NUM + "=''"
                        + " THEN " + DOM_SERIES_NAME
                        + " ELSE " + DOM_SERIES_NAME + "||' #'||" + DOM_BOOK_SERIES_NUM
                        + " END"
                        + " AS " + DOM_SERIES_FORMATTED;

        /**
         * Series a book belongs to.
         * If the book has more then one series, concat " et al" after the primary series.
         */
        private static final String SERIES_LIST =
                " CASE WHEN " + COLUMN_ALIAS_NR_OF_SERIES + " < 2"
                        + " THEN Coalesce(s." + DOM_SERIES_FORMATTED + ",'')"
                        + " ELSE " + DOM_SERIES_FORMATTED
                        + "||' " + BookCatalogueApp.getResString(R.string.and_others) + '\''
                        + " END"
                        + " AS " + DOM_SERIES_FORMATTED;
        /**
         * The columns from {@link DatabaseDefinitions#TBL_BOOKS} we need.
         * to send a Book to Goodreads.
         * <p>
         * See {@link GoodreadsManager#sendOneBook(DBA, BookCursorRow)}
         * -> notes disabled for now.
         */
        private static final String GOODREADS_FIELDS_FOR_SENDING =
                DOM_PK_ID.name
                        + ',' + DOM_BOOK_ISBN
                        + ',' + DOM_BOOK_GOODREADS_BOOK_ID
                        + ',' + DOM_BOOK_READ
                        + ',' + DOM_BOOK_READ_END
                        + ',' + DOM_BOOK_RATING
                //+ ',' + DOM_BOOK_NOTES
                ;
    }

    /**
     * Sql SELECT of a single table, without a where clause.
     */
    private static final class SqlSelectFullTable {

        /** {@link Book}, all columns. */
        private static final String BOOKS =
                "SELECT * FROM " + TBL_BOOKS;

        /** {@link Author}, all columns. */
        private static final String AUTHORS =
                "SELECT * FROM " + TBL_AUTHORS;

        /** {@link Series}, all columns. */
        private static final String SERIES =
                "SELECT * FROM " + TBL_SERIES;

        /** {@link Bookshelf} all columns. Ordered, will be displayed to user. */
        private static final String BOOKSHELVES =
                "SELECT * FROM " + TBL_BOOKSHELF
                        + " ORDER BY lower(" + DOM_BOOKSHELF + ')' + COLLATION;

        /** {@link BooklistStyle} all columns. Note the where clause: user-styles only. */
        private static final String BOOKLIST_STYLES =
                "SELECT * FROM " + TBL_BOOKLIST_STYLES + " WHERE " + DOM_PK_ID + ">0";

        /** Book UUID only, for accessing all cover image files. */
        private static final String BOOK_UUIDS =
                "SELECT " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS;


        /** name only, for {@link android.widget.AutoCompleteTextView}. */
        private static final String AUTHORS_FAMILY_NAMES =
                "SELECT DISTINCT " + DOM_AUTHOR_FAMILY_NAME + " FROM " + TBL_AUTHORS
                        + " ORDER BY lower(" + DOM_AUTHOR_FAMILY_NAME + ')' + COLLATION;

        /** name only, for {@link android.widget.AutoCompleteTextView}. */
        private static final String AUTHORS_GIVEN_NAMES =
                "SELECT DISTINCT " + DOM_AUTHOR_GIVEN_NAMES + " FROM " + TBL_AUTHORS
                        + " ORDER BY lower(" + DOM_AUTHOR_GIVEN_NAMES + ')' + COLLATION;

        /** name only, for {@link android.widget.AutoCompleteTextView}. */
        private static final String AUTHORS_WITH_FORMATTED_NAMES =
                "SELECT " + SqlColumns.AUTHOR_FORMATTED
                        + " FROM " + TBL_AUTHORS.ref()
                        + " ORDER BY lower(" + DOM_AUTHOR_FAMILY_NAME + ')' + COLLATION
                        + ", lower(" + DOM_AUTHOR_GIVEN_NAMES + ')' + COLLATION;

        /** name only, for {@link android.widget.AutoCompleteTextView}. */
        private static final String SERIES_NAME =
                "SELECT " + DOM_SERIES_NAME
                        + " FROM " + TBL_SERIES
                        + " ORDER BY lower(" + DOM_SERIES_NAME + ')' + COLLATION;

        /** name only, for {@link android.widget.AutoCompleteTextView}. */
        private static final String FORMATS =
                "SELECT DISTINCT " + DOM_BOOK_FORMAT
                        + " FROM " + TBL_BOOKS
                        + " ORDER BY lower(" + DOM_BOOK_FORMAT + ')' + COLLATION;

        /** name only, for {@link android.widget.AutoCompleteTextView}. */
        private static final String GENRES =
                "SELECT DISTINCT " + DOM_BOOK_GENRE
                        + " FROM " + TBL_BOOKS
                        + " ORDER BY lower(" + DOM_BOOK_GENRE + ')' + COLLATION;

        /** name only, for {@link android.widget.AutoCompleteTextView}. */
        private static final String LANGUAGES =
                "SELECT DISTINCT " + DOM_BOOK_LANGUAGE
                        + " FROM " + TBL_BOOKS
                        + " ORDER BY lower(" + DOM_BOOK_LANGUAGE + ')' + COLLATION;

        /** name only, for {@link android.widget.AutoCompleteTextView}. */
        private static final String LOCATIONS =
                "SELECT DISTINCT " + DOM_BOOK_LOCATION
                        + " FROM " + TBL_BOOKS
                        + " ORDER BY lower(" + DOM_BOOK_LOCATION + ')' + COLLATION;

        /** name only, for {@link android.widget.AutoCompleteTextView}. */
        private static final String PUBLISHERS =
                "SELECT DISTINCT " + DOM_BOOK_PUBLISHER
                        + " FROM " + TBL_BOOKS
                        + " ORDER BY lower(" + DOM_BOOK_PUBLISHER + ')' + COLLATION;
    }

    /**
     * Sql SELECT returning a list, with a WHERE clause.
     */
    private static final class SqlSelectList {

        /**
         * All Bookshelves for a Book; ordered by name.
         */
        private static final String BOOKSHELVES_BY_BOOK_ID =
                "SELECT DISTINCT "
                        + TBL_BOOKSHELF.dotAs(DOM_PK_ID)
                        + ',' + TBL_BOOKSHELF.dotAs(DOM_BOOKSHELF)
                        + ',' + TBL_BOOKSHELF.dotAs(DOM_FK_STYLE_ID)
                        + " FROM " + TBL_BOOK_BOOKSHELF.ref()
                        + TBL_BOOK_BOOKSHELF.join(TBL_BOOKSHELF)
                        + " WHERE " + TBL_BOOK_BOOKSHELF.dot(DOM_FK_BOOK_ID) + "=?"
                        + " ORDER BY lower(" + TBL_BOOKSHELF.dot(DOM_BOOKSHELF) + ')' + COLLATION;

        /**
         * All Authors for a Book; ordered by position, family, given.
         */
        private static final String AUTHORS_BY_BOOK_ID =
                "SELECT DISTINCT " + TBL_AUTHORS.dotAs(DOM_PK_ID)
                        + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME)
                        + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES)
                        + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE)
                        + ',' + SqlColumns.AUTHOR_FORMATTED
                        + ',' + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_POSITION)
                        + " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                        + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK_ID) + "=?"
                        + " ORDER BY "
                        + TBL_BOOK_AUTHOR.dot(DOM_BOOK_AUTHOR_POSITION) + " ASC,"
                        + " lower(" + DOM_AUTHOR_FAMILY_NAME + ')' + COLLATION + "ASC,"
                        + " lower(" + DOM_AUTHOR_GIVEN_NAMES + ')' + COLLATION + "ASC";

        /**
         * All Series for a Book; ordered by position, name.
         */
        private static final String SERIES_BY_BOOK_ID =
                "SELECT DISTINCT " + TBL_SERIES.dotAs(DOM_PK_ID)
                        + ',' + TBL_SERIES.dotAs(DOM_SERIES_NAME)
                        + ',' + TBL_SERIES.dotAs(DOM_SERIES_IS_COMPLETE)
                        + ',' + TBL_BOOK_SERIES.dotAs(DOM_BOOK_SERIES_NUM)
                        + ',' + TBL_BOOK_SERIES.dotAs(DOM_BOOK_SERIES_POSITION)
                        + ',' + DOM_SERIES_NAME + "||' ('||" + DOM_BOOK_SERIES_NUM + "||')'"
                        + /*    */ " AS " + DOM_SERIES_FORMATTED
                        + " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                        + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) + "=?"
                        + " ORDER BY " + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_POSITION)
                        + ",lower(" + TBL_SERIES.dot(DOM_SERIES_NAME) + ')' + COLLATION + "ASC";

        /**
         * All TocEntry's for a Book; ordered by position in the book.
         */
        private static final String TOC_ENTRIES_BY_BOOK_ID =
                "SELECT " + TBL_TOC_ENTRIES.dotAs(DOM_PK_ID)
                        + ',' + TBL_TOC_ENTRIES.dotAs(DOM_FK_AUTHOR_ID)
                        + ',' + TBL_TOC_ENTRIES.dotAs(DOM_TITLE)
                        + ',' + TBL_TOC_ENTRIES.dotAs(DOM_FIRST_PUBLICATION)
                        // for convenience, we fetch the Author here
                        + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_FAMILY_NAME)
                        + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_GIVEN_NAMES)
                        + ',' + TBL_AUTHORS.dotAs(DOM_AUTHOR_IS_COMPLETE)

                        + " FROM " + TBL_TOC_ENTRIES.ref()
                        + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                        + TBL_TOC_ENTRIES.join(TBL_AUTHORS)
                        + " WHERE " + TBL_BOOK_TOC_ENTRIES.dot(DOM_FK_BOOK_ID) + "=?"
                        + " ORDER BY " + TBL_BOOK_TOC_ENTRIES.dot(DOM_BOOK_TOC_ENTRY_POSITION);

        /**
         * All TocEntry's for an Author; ordered by title.
         */
        private static final String GET_TOC_ENTRIES_BY_AUTHOR_ID =
                "SELECT " + TBL_TOC_ENTRIES.dotAs(DOM_PK_ID)
                        + ',' + TBL_TOC_ENTRIES.dotAs(DOM_TITLE)
                        + ',' + TBL_TOC_ENTRIES.dotAs(DOM_FIRST_PUBLICATION)
                        + " FROM " + TBL_TOC_ENTRIES.ref()
                        + " WHERE " + TBL_TOC_ENTRIES.dot(DOM_FK_AUTHOR_ID) + "=?"
                        + " ORDER BY lower(" + TBL_TOC_ENTRIES.dot(DOM_TITLE) + ')' + COLLATION;

    }

    /**
     * Sql SELECT that returns a single ID/UUID.
     */
    private static final class SqlGet {

        /**
         * Find the Book ID based on a search for the ISBN (10 OR 13).
         */
        static final String BOOK_ID_BY_ISBN =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKS
                        + " WHERE lower(" + DOM_BOOK_ISBN + ")=lower(?)";
        /**
         * Find the Book ID based on a search for the ISBN (both 10 & 13).
         */
        static final String BOOK_ID_BY_ISBN2 =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKS
                        + " WHERE lower(" + DOM_BOOK_ISBN + ") IN (lower(?),lower(?))";

        static final String BOOKLIST_STYLE_ID_BY_UUID =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKLIST_STYLES
                        + " WHERE " + DOM_UUID + "=?";

        static final String AUTHOR_ID_BY_NAME =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_AUTHORS
                        + " WHERE lower(" + DOM_AUTHOR_FAMILY_NAME + ")=lower(?)" + COLLATION
                        + " AND lower(" + DOM_AUTHOR_GIVEN_NAMES + ")=lower(?)" + COLLATION;

        static final String BOOKSHELF_ID_BY_NAME =
                "SELECT " + DOM_PK_ID + " FROM " + DOM_BOOKSHELF
                        + " WHERE lower(" + DOM_BOOKSHELF + ")=lower(?)" + COLLATION;

        static final String SERIES_ID_BY_NAME =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_SERIES
                        + " WHERE lower(" + DOM_SERIES_NAME + ") = lower(?)" + COLLATION;

        static final String TOC_ENTRY_ID =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_TOC_ENTRIES
                        + " WHERE " + DOM_FK_AUTHOR_ID + "=?"
                        + " AND (" + DOM_TITLE + "=? OR " + DOM_TITLE + "=?)" + COLLATION;

        static final String BOOK_ID_BY_TOC_ENTRY_ID =
                "SELECT " + DOM_FK_BOOK_ID + " FROM " + TBL_BOOK_TOC_ENTRIES
                        + " WHERE " + DOM_FK_TOC_ENTRY_ID + "=?";

        static final String BOOK_UUID_BY_ID =
                "SELECT " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS
                        + " WHERE " + DOM_PK_ID + "=?";

        static final String BOOK_ID_BY_UUID =
                "SELECT " + DOM_PK_ID + " FROM " + TBL_BOOKS
                        + " WHERE " + DOM_BOOK_UUID + "=?";

        /**
         * Get the ISBN of a {@link Book} by it's id.
         */
        static final String GET_BOOK_ISBN_BY_BOOK_ID =
                "SELECT " + DOM_BOOK_ISBN + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";
        /**
         * Get the title of a {@link Book} by it's id.
         */
        static final String GET_BOOK_TITLE_BY_BOOK_ID =
                "SELECT " + DOM_TITLE + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";
        /**
         * Get the name of the loanee of a {@link Book}.
         */
        static final String LOANEE_BY_BOOK_ID =
                "SELECT " + DOM_BOOK_LOANEE + " FROM " + TBL_BOOK_LOANEE
                        + " WHERE " + DOM_FK_BOOK_ID + "=?";
    }

    /**
     * Sql SELECT to get Objects by their id; and related queries.
     */
    private static final class SqlSelect {

        /**
         * Check if a {@link Book} exists.
         */
        static final String BOOK_EXISTS =
                "SELECT COUNT(*) " + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Get a {@link Book} by it's id.
         */
        static final String BOOK_BY_ID =
                "SELECT * FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Get a {@link Bookshelf} by it's id.
         */
        static final String BOOKSHELF_BY_ID =
                "SELECT " + DOM_PK_ID + ',' + DOM_BOOKSHELF + ',' + DOM_FK_STYLE_ID
                        + " FROM " + TBL_BOOKSHELF + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Get a {@link Bookshelf} by it's name.
         */
        static final String BOOKSHELF_BY_NAME =
                "SELECT " + DOM_PK_ID + ',' + DOM_BOOKSHELF + ',' + DOM_FK_STYLE_ID
                        + " FROM " + DOM_BOOKSHELF
                        + " WHERE lower(" + DOM_BOOKSHELF + ")=lower(?)" + COLLATION;

        /**
         * Get an {@link Author} by it's id.
         */
        static final String AUTHOR_BY_ID =
                "SELECT " + DOM_AUTHOR_FAMILY_NAME
                        + ',' + DOM_AUTHOR_GIVEN_NAMES
                        + ',' + DOM_AUTHOR_IS_COMPLETE
                        + " FROM " + TBL_AUTHORS + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Get a {@link Series} by it's id.
         */
        static final String SERIES_BY_ID =
                "SELECT " + DOM_SERIES_NAME + ',' + DOM_SERIES_IS_COMPLETE
                        + " FROM " + TBL_SERIES + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Get the last-update-date for a {@link Book} by it's id.
         */
        static final String GET_LAST_UPDATE_DATE_BY_BOOK_ID =
                "SELECT " + DOM_LAST_UPDATE_DATE + " FROM " + TBL_BOOKS
                        + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Count the number of {@link Book}'s in a {@link Series}.
         */
        static final String COUNT_BOOKS_IN_SERIES =
                "SELECT COUNT(" + DOM_FK_BOOK_ID + ") FROM " + TBL_BOOK_SERIES
                        + " WHERE " + DOM_FK_SERIES_ID + "=?";


        /**
         * Count the number of {@link Book}'s by an {@link Author}.
         */
        static final String COUNT_BOOKS_BY_AUTHOR =
                "SELECT COUNT(" + DOM_FK_BOOK_ID + ") FROM " + TBL_BOOK_AUTHOR
                        + " WHERE " + DOM_FK_AUTHOR_ID + "=?";

        static final String COUNT_TOC_ENTRIES_BY_AUTHOR =
                "SELECT COUNT(" + DOM_PK_ID + ") FROM " + TBL_TOC_ENTRIES
                        + " WHERE " + DOM_FK_AUTHOR_ID + "=?";

        /**
         * Get the needed fields of a {@link Book} to send to Goodreads.
         * <p>
         * param DOM_PK_ID of the Book
         */
        static final String GOODREADS_GET_BOOK_TO_SEND_BY_BOOK_ID =
                "SELECT " + SqlColumns.GOODREADS_FIELDS_FOR_SENDING
                        + " FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";


        static final String SEARCH_SUGGESTIONS =
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
                        + " ORDER BY lower(" + SearchManager.SUGGEST_COLUMN_TEXT_1 + ')'
                        + COLLATION;
    }

    /**
     * Sql INSERT.
     */
    private static final class SqlInsert {

        static final String BOOKSHELF =
                "INSERT INTO " + TBL_BOOKSHELF
                        + '(' + DOM_BOOKSHELF
                        + ',' + DOM_FK_STYLE_ID
                        + ") VALUES (?,?)";

        static final String AUTHOR =
                "INSERT INTO " + TBL_AUTHORS
                        + '(' + DOM_AUTHOR_FAMILY_NAME
                        + ',' + DOM_AUTHOR_GIVEN_NAMES
                        + ',' + DOM_AUTHOR_IS_COMPLETE
                        + ") VALUES (?,?,?)";

        static final String SERIES =
                "INSERT INTO " + TBL_SERIES
                        + '(' + DOM_SERIES_NAME
                        + ',' + DOM_SERIES_IS_COMPLETE
                        + ") VALUES (?,?)";

        static final String BOOK_TOC_ENTRY =
                "INSERT INTO " + TBL_BOOK_TOC_ENTRIES
                        + '(' + DOM_FK_TOC_ENTRY_ID
                        + ',' + DOM_FK_BOOK_ID
                        + ',' + DOM_BOOK_TOC_ENTRY_POSITION
                        + ") VALUES (?,?,?)";

        static final String TOC_ENTRY =
                "INSERT INTO " + TBL_TOC_ENTRIES
                        + '(' + DOM_FK_AUTHOR_ID
                        + ',' + DOM_TITLE
                        + ',' + DOM_FIRST_PUBLICATION
                        + ") VALUES (?,?,?)";

        static final String BOOK_BOOKSHELF =
                "INSERT INTO " + TBL_BOOK_BOOKSHELF
                        + '(' + DOM_FK_BOOK_ID
                        + ',' + DOM_BOOKSHELF
                        + ") VALUES (?,?)";

        static final String BOOK_AUTHOR =
                "INSERT INTO " + TBL_BOOK_AUTHOR
                        + '(' + DOM_FK_BOOK_ID
                        + ',' + DOM_FK_AUTHOR_ID
                        + ',' + DOM_BOOK_AUTHOR_POSITION
                        + ") VALUES(?,?,?)";

        static final String BOOK_SERIES =
                "INSERT INTO " + TBL_BOOK_SERIES
                        + '(' + DOM_FK_BOOK_ID
                        + ',' + DOM_FK_SERIES_ID
                        + ',' + DOM_BOOK_SERIES_NUM
                        + ',' + DOM_BOOK_SERIES_POSITION
                        + ") VALUES(?,?,?,?)";

        static final String BOOK_LOANEE =
                "INSERT INTO " + TBL_BOOK_LOANEE
                        + '(' + DOM_FK_BOOK_ID
                        + ',' + DOM_BOOK_LOANEE
                        + ") VALUES(?,?)";

        static final String BOOKLIST_STYLE =
                "INSERT INTO " + TBL_BOOKLIST_STYLES + " (" + DOM_UUID + ") VALUES (?)";

    }

    /**
     * Sql UPDATE.
     */
    private static final class SqlUpdate {

        /**
         * Update a single Book's last sync date with Goodreads.
         */
        static final String GOODREADS_LAST_SYNC_DATE =
                "UPDATE " + TBL_BOOKS + " SET "
                        + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + "=current_timestamp"
                        + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Update a single Book's Goodreads id.
         */
        static final String GOODREADS_BOOK_ID =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_GOODREADS_BOOK_ID + "=?"
                        + " WHERE " + DOM_PK_ID + "=?";

        static final String AUTHOR_ON_TOC_ENTRIES =
                "UPDATE " + TBL_TOC_ENTRIES + " SET " + DOM_FK_AUTHOR_ID + "=?"
                        + " WHERE " + DOM_FK_AUTHOR_ID + "=?";


        static final String FORMAT =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + DOM_BOOK_FORMAT + "=?,"
                        + " WHERE " + DOM_BOOK_FORMAT + "=?";

        static final String GENRE =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + DOM_BOOK_GENRE + "=?,"
                        + " WHERE " + DOM_BOOK_GENRE + "=?";

        static final String LANGUAGE =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + DOM_BOOK_LANGUAGE + "=?,"
                        + " WHERE " + DOM_BOOK_LANGUAGE + "=?";

        static final String LOCATION =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + DOM_BOOK_LOCATION + "=?,"
                        + " WHERE " + DOM_BOOK_LOCATION + "=?";

        static final String PUBLISHER =
                "UPDATE " + TBL_BOOKS + " SET " + DOM_LAST_UPDATE_DATE + "=current_timestamp"
                        + DOM_BOOK_PUBLISHER + "=?,"
                        + " WHERE " + DOM_BOOK_PUBLISHER + "=?";
    }

    /**
     * Sql DELETE commands.
     * <p>
     * All 'link' tables will be updated due to their FOREIGN KEY constraints.
     * The 'other-side' of a link table is cleaned by triggers.
     */
    static final class SqlDelete {

        /**
         * Delete a {@link Book}.
         */
        static final String BOOK_BY_ID =
                "DELETE FROM " + TBL_BOOKS + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Delete a {@link Bookshelf}.
         */
        static final String BOOKSHELF_BY_ID =
                "DELETE FROM " + TBL_BOOKSHELF + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Delete a {@link Series}.
         */
        static final String SERIES_BY_ID =
                "DELETE FROM " + TBL_SERIES + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Delete a {@link BooklistStyle}.
         */
        static final String STYLE_BY_ID =
                "DELETE FROM " + TBL_BOOKLIST_STYLES + " WHERE " + DOM_PK_ID + "=?";

        /**
         * Delete the link between a {@link Book} and an {@link Author}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_AUTHOR_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_AUTHOR + " WHERE " + DOM_FK_BOOK_ID + "=?";

        /**
         * Delete the link between a {@link Book} and a {@link Bookshelf}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_BOOKSHELF_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_BOOKSHELF + " WHERE " + DOM_FK_BOOK_ID + "=?";

        /**
         * Delete the link between a {@link Book} and a {@link Series}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_SERIES_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_SERIES + " WHERE " + DOM_FK_BOOK_ID + "=?";

        /**
         * Delete the link between a {@link Book} and a {@link TocEntry}.
         * <p>
         * This is done when a TOC is updated; first delete all links, then re-create them.
         */
        static final String BOOK_TOC_ENTRIES_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_TOC_ENTRIES + " WHERE " + DOM_FK_BOOK_ID + "=?";

        /**
         * Delete the loan of a {@link Book}; i.e. 'return the book'.
         */
        static final String BOOK_LOANEE_BY_BOOK_ID =
                "DELETE FROM " + TBL_BOOK_LOANEE + " WHERE " + DOM_FK_BOOK_ID + "=?";


        /**
         * Purge an {@link Author} if no longer in use (check both book_author AND toc_entries).
         */
        static final String PURGE_AUTHORS =
                "DELETE FROM " + TBL_AUTHORS
                        + " WHERE " + DOM_PK_ID + " NOT IN"
                        + " (SELECT DISTINCT " + DOM_FK_AUTHOR_ID
                        + " FROM " + TBL_BOOK_AUTHOR + ')'
                        + " AND " + DOM_PK_ID + " NOT IN"
                        + " (SELECT DISTINCT " + DOM_FK_AUTHOR_ID
                        + " FROM " + TBL_TOC_ENTRIES + ')';

        /**
         * Purge a {@link Series} if no longer in use.
         */
        static final String PURGE_SERIES =
                "DELETE FROM " + TBL_SERIES + " WHERE " + DOM_PK_ID + " NOT IN"
                        + " (SELECT DISTINCT " + DOM_FK_SERIES_ID
                        + " FROM " + TBL_BOOK_SERIES + ')';

        private SqlDelete() {
        }
    }

    /**
     * Sql specific for FTS.
     */
    private static final class SqlFTS {

        static final String GET_AUTHORS_BY_BOOK_ID =
                "SELECT " + TBL_AUTHORS.dot("*")
                        + " FROM " + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                        + " WHERE " + TBL_BOOK_AUTHOR.dot(DOM_FK_BOOK_ID) + "=?";

        static final String DOM_SERIES_INFO = "seriesInfo";
        static final String GET_SERIES_BY_BOOK_ID =
                "SELECT " + TBL_SERIES.dot(DOM_SERIES_NAME)
                        + " || ' ' ||"
                        + " Coalesce(" + TBL_BOOK_SERIES.dot(DOM_BOOK_SERIES_NUM) + ",'')"
                        + " AS " + DOM_SERIES_INFO
                        + " FROM " + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                        + " WHERE " + TBL_BOOK_SERIES.dot(DOM_FK_BOOK_ID) + "=?";

        static final String DOM_TOC_ENTRY_INFO = "TOCEntryInfo";
        static final String DOM_TOC_ENTRY_AUTHOR_INFO = "TOCEntryAuthorInfo";
        static final String GET_TOC_ENTRIES_BY_BOOK_ID =
                "SELECT "
                        + SqlColumns.AUTHOR_GIVEN_SPACE_FAMILY + " AS " + DOM_TOC_ENTRY_AUTHOR_INFO
                        + ',' + DOM_TITLE + " AS " + DOM_TOC_ENTRY_INFO
                        + " FROM " + TBL_TOC_ENTRIES.ref()
                        + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                        + TBL_TOC_ENTRIES.join(TBL_AUTHORS)
                        + " WHERE " + TBL_BOOK_TOC_ENTRIES.dot(DOM_FK_BOOK_ID) + "=?";


        // the body of an INSERT INTO [table]. Used more then once.
        static final String INSERT_BODY =
                " (" + DOM_FTS_AUTHOR_NAME
                        + ',' + DOM_TITLE
                        + ',' + DOM_BOOK_DESCRIPTION
                        + ',' + DOM_BOOK_NOTES
                        + ',' + DOM_BOOK_PUBLISHER
                        + ',' + DOM_BOOK_GENRE
                        + ',' + DOM_BOOK_LOCATION
                        + ',' + DOM_BOOK_ISBN
                        + ',' + DOM_PK_DOCID
                        + ") VALUES (?,?,?,?,?,?,?,?,?)";

        // The parameter order MUST match the order expected in UPDATE.
        static final String INSERT =
                "INSERT INTO " + TBL_BOOKS_FTS + INSERT_BODY;

        // The parameter order MUST match the order expected in INSERT.
        static final String UPDATE =
                "UPDATE " + TBL_BOOKS_FTS + " SET "
                        + DOM_FTS_AUTHOR_NAME + "=?,"
                        + DOM_TITLE + "=?,"
                        + DOM_BOOK_DESCRIPTION + "=?,"
                        + DOM_BOOK_NOTES + "=?,"
                        + DOM_BOOK_PUBLISHER + "=?,"
                        + DOM_BOOK_GENRE + "=?,"
                        + DOM_BOOK_LOCATION + "=?,"
                        + DOM_BOOK_ISBN + "=?"
                        + " WHERE " + DOM_PK_DOCID + "=?";
    }
}
