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
package com.hardbacknutter.nevertoomanybooks.database;

import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;
import android.util.Log;
import android.widget.AutoCompleteTextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvImporter;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableInfo;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithTitle;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_GIVEN_NAMES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_GIVEN_NAMES_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_CONDITION_COVER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_TOC_ENTRY_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DESCRIPTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EDITION_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_GOODREADS_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_ISFDB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_LIBRARY_THING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_OPEN_LIBRARY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_STRIP_INFO_BE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_TOC_ENTRY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FTS_AUTHOR_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FTS_BOOKS_PK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FTS_TOC_ENTRY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PAGES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_LISTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_LISTED_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_PAID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRICE_PAID_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRINT_RUN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PRIVATE_NOTES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_START;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SIGNED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_IS_BUILTIN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TOC_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TOC_TYPE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * Database access helper class.
 * <p>
 * This class is 'context-free'. KEEP IT THAT WAY. Passing in a context is fine, but NO caching.
 * We need to use this in background tasks and ViewModel classes.
 * Using {@link App#getAppContext} is however allowed.
 * <p>
 * insert: return new id, or {@code -1} for error.
 * <p>
 * update: return rows affected, can be 0; or boolean when appropriate.
 * TODO: some places ignore update failures. A storage full could trigger an update failure.
 * <p>
 * updateOrInsert: return true for success
 * <p>
 * TODO: caching of statements forces synchronisation ... is it worth it ?
 * There is an explicit warning that {@link SQLiteStatement} is not thread safe!
 */
public class DAO
        implements AutoCloseable {

    /**
     * Flag indicating the UPDATE_DATE field from the bundle should be trusted.
     * If this flag is not set, the UPDATE_DATE will be set based on the current time
     */
    public static final int BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT = 1;
    /**
     * In addition to SQLite's default BINARY collator (others: NOCASE and RTRIM),
     * Android supplies two more.
     * LOCALIZED: using the system's current Locale,
     * UNICODE  : Unicode Collation Algorithm and not tailored to the current Locale.
     * <p>
     * We tried 'Collate UNICODE' but it seemed to be case sensitive.
     * We ended up with 'Ursula Le Guin' and 'Ursula le Guin'.
     * <p>
     * We now use Collate LOCALE and check to see if it is case sensitive.
     * We *hope* in the future Android will add LOCALE_CI (or equivalent).
     * <p>
     * public static final String COLLATION = " Collate NOCASE";
     * public static final String COLLATION = " Collate UNICODE";
     * <p>
     * <strong>Note:</strong> Important to have a space at the start
     */
    public static final String COLLATION = " Collate LOCALIZED";
    /** Log tag. */
    private static final String TAG = "DAO";
    /** log error string. */
    private static final String ERROR_FAILED_CREATING_BOOK_FROM = "Failed creating book from\n";
    /** log error string. */
    private static final String ERROR_FAILED_UPDATING_BOOK_FROM = "Failed updating book from\n";

    /** Synchronizer to coordinate DB access. Must be STATIC so all instances share same sync. */
    private static final Synchronizer SYNCHRONIZER = new Synchronizer();

    /** Static Factory object to create a {@link SynchronizedCursor} cursor. */
    @NonNull
    private static final SQLiteDatabase.CursorFactory CURSOR_FACTORY =
            (db, d, et, q) -> new SynchronizedCursor(d, et, q, SYNCHRONIZER);

    /** Static Factory object to create an {@link ExtCursor} cursor. */
    @NonNull
    private static final SQLiteDatabase.CursorFactory EXT_CURSOR_FACTORY =
            (db, d, et, q) -> new ExtCursor(d, et, q, SYNCHRONIZER);

    /** Column alias. */
    private static final String COLUMN_ALIAS_NR_OF_SERIES = "_num_series";
//    /** Column alias. */
//    private static final String COLUMN_ALIAS_NR_OF_AUTHORS = "_num_authors";
    /** statement names; keys into the cache map. */
    private static final String STMT_CHECK_BOOK_EXISTS = "CheckBookExists";
    private static final String STMT_GET_AUTHOR_ID = "GetAuthorId";
    private static final String STMT_GET_SERIES_ID = "GetSeriesId";
    private static final String STMT_GET_TOC_ENTRY_ID = "GetTOCEntryId";
    private static final String STMT_GET_BOOK_ISBN = "GetBookIsbn";
    private static final String STMT_GET_BOOK_TITLE = "GetBookTitle";
    private static final String STMT_GET_BOOK_UPDATE_DATE = "GetBookUpdateDate";
    private static final String STMT_GET_BOOK_UUID = "GetBookUuid";
    private static final String STMT_GET_BOOK_ID_FROM_VALID_ISBN = "GetIdFromIsbn2";
    private static final String STMT_GET_BOOK_ID_FROM_ISBN = "GetIdFromIsbn1";
    private static final String STMT_GET_BOOK_ID_FROM_UUID = "GetBookIdFromUuid";
    private static final String STMT_GET_BOOKSHELF_ID_BY_NAME = "GetBookshelfIdByName";
    private static final String STMT_GET_LOANEE_BY_BOOK_ID = "GetLoaneeByBookId";
    private static final String STMT_GET_BOOKLIST_STYLE = "GetBooklistStyle";
    private static final String STMT_INSERT_BOOK_SERIES = "InsertBookSeries";
    private static final String STMT_INSERT_BOOK_TOC_ENTRY = "InsertBookTOCEntry";
    private static final String STMT_INSERT_BOOK_AUTHORS = "InsertBookAuthors";
    private static final String STMT_INSERT_BOOK_BOOKSHELF = "InsertBookBookshelf";
    private static final String STMT_INSERT_AUTHOR = "InsertAuthor";
    private static final String STMT_INSERT_TOC_ENTRY = "InsertTOCEntry";
    private static final String STMT_INSERT_FTS = "InsertFts";
    private static final String STMT_INSERT_SERIES = "InsertSeries";
    private static final String STMT_DELETE_BOOK = "DeleteBook";
    private static final String STMT_DELETE_SERIES = "DeleteSeries";
    private static final String STMT_DELETE_TOC_ENTRY = "DeleteTocEntry";
    private static final String STMT_DELETE_BOOK_TOC_ENTRIES = "DeleteBookTocEntries";
    private static final String STMT_DELETE_BOOK_AUTHORS = "DeleteBookAuthors";
    private static final String STMT_DELETE_BOOK_BOOKSHELF = "DeleteBookBookshelf";
    private static final String STMT_DELETE_BOOK_SERIES = "DeleteBookSeries";
    private static final String STMT_UPDATE_GOODREADS_BOOK_ID = "UpdateGoodreadsBookId";
    private static final String STMT_UPDATE_AUTHOR_ON_TOC_ENTRIES = "UpdateAuthorOnTocEntry";
    private static final String STMT_UPDATE_GOODREADS_SYNC_DATE = "UpdateGoodreadsSyncDate";
    private static final String STMT_UPDATE_FTS = "UpdateFts";
    /** log error string. */
    private static final String ERROR_REQUIRES_TRANSACTION = "Requires a transaction";
    /** log error string. */
    private static final String ERROR_FAILED_TO_UPDATE_FTS = "Failed to onProgress FTS";
    /** See {@link #encodeString}. */
    private static final Pattern ENCODE_STRING = Pattern.compile("'", Pattern.LITERAL);
    /** any non-word character. */
    private static final Pattern ENCODE_ORDERBY_PATTERN = Pattern.compile("\\W");
    private static final Pattern ASCII_REGEX = Pattern.compile("[^\\p{ASCII}]");
    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;
    private static final String _DELETE_FROM_ = "DELETE FROM ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    /** Actual SQLiteOpenHelper. */
    private static DBHelper sDbHelper;
    /** Synchronization wrapper around the real database. */
    private static SynchronizedDb sSyncedDb;

    /** Collection of statements pre-compiled for this object. */
    private final SqlStatementManager mSqlStatementManager;
    @NonNull
    private final String mInstanceName;
    /** DEBUG: Indicates close() has been called. Also see {@link Closeable#close()}. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     * <p>
     * <strong>Note:</strong> don't be tempted to turn this into a singleton...
     * this class is not fully thread safe (in contrast to the covers dao which is).
     *
     * @param name of this DAO for logging.
     */
    public DAO(@NonNull final String name) {
        mInstanceName = name;

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, mInstanceName + "|Constructor");
        }

        // static SQLiteOpenHelper
        if (sDbHelper == null) {
            sDbHelper = DBHelper.getInstance(App.getAppContext(), CURSOR_FACTORY, SYNCHRONIZER);
        }

        // static DB wrapper
        if (sSyncedDb == null) {
            sSyncedDb = new SynchronizedDb(sDbHelper, SYNCHRONIZER);
        }

        // statements are instance based/managed
        mSqlStatementManager = new SqlStatementManager(sSyncedDb, TAG + "|" + mInstanceName);
    }

    /**
     * Get the Synchronizer object for this database in case there is some other activity
     * that needs to be synced.
     * <p>
     * <strong>Note:</strong> {@link Cursor#requery()} is the only thing found so far.
     *
     * @return the synchronizer
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
     * <p>
     * <strong>Note:</strong> Using the compiled pattern is theoretically faster than using
     * {@link String#replace(CharSequence, CharSequence)}.
     */
    @NonNull
    public static String encodeString(@NonNull final CharSequence value) {
        return ENCODE_STRING.matcher(value).replaceAll("''");
    }

    /**
     * Prepare a string to be inserted in the 'Order By' column.
     * e.g. Author names, the Title of a book: strip spaces etc, make lowercase,...
     *
     * @param value  to encode
     * @param locale to use for case manipulation
     *
     * @return the encoded value
     */
    public static String encodeOrderByColumn(@NonNull final CharSequence value,
                                             @NonNull final Locale locale) {

        String s = toAscii(value);
        // remove all non-word characters. i.e. all characters not in [a-zA-Z_0-9]
        return ENCODE_ORDERBY_PATTERN.matcher(s).replaceAll("").toLowerCase(locale);
    }

    /**
     * Normalize a given string to contain only ASCII characters so we can easily text searches.
     *
     * @param text to normalize
     *
     * @return ascii text
     */
    private static String toAscii(@NonNull final CharSequence text) {
        return ASCII_REGEX.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                          .replaceAll("");
    }

    /**
     * DEBUG only. Return the instance name of this DAO.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return mInstanceName;
    }

    /**
     * A complete export of all tables (flattened) in the database.
     *
     * @param sinceDate to select all books added/modified since that date.
     *                  Set to {@code null} for *all* books.
     *
     * @return Cursor over all books, authors, etc
     */
    @NonNull
    public Cursor fetchBooksForExport(@Nullable final Date sinceDate) {
        String whereClause;
        if (sinceDate == null) {
            whereClause = "";
        } else {
            whereClause = TBL_BOOKS.dot(KEY_DATE_LAST_UPDATED)
                          + ">'" + DateUtils.utcSqlDateTime(sinceDate) + '\'';
        }

        String sql = SqlAllBooks.withPrimaryAuthorAndSeries(whereClause);
        return sSyncedDb.rawQuery(sql, null);
    }

    /**
     * Purge Booklist node state data for the given Bookshelf.<br>
     * Called when a Bookshelf is deleted or manually from the Bookshelf management context menu.
     *
     * @param bookshelfId to purge
     */
    public void purgeNodeStatesByBookshelf(final long bookshelfId) {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(
                _DELETE_FROM_ + DBDefinitions.TBL_BOOK_LIST_NODE_STATE
                + _WHERE_ + KEY_FK_BOOKSHELF + "=?")) {
            stmt.bindLong(1, bookshelfId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Purge Booklist node state data for the given Style.<br>
     * Called when a style is deleted or manually from the Styles management context menu.
     *
     * @param styleId to purge
     */
    public void purgeNodeStatesByStyle(final long styleId) {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(
                _DELETE_FROM_ + DBDefinitions.TBL_BOOK_LIST_NODE_STATE
                + _WHERE_ + KEY_FK_STYLE + "=?")) {
            stmt.bindLong(1, styleId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Get the local database.
     * DO NOT CALL THIS UNLESS YOU REALLY NEED TO. DATABASE ACCESS SHOULD GO THROUGH THIS CLASS.
     *
     * @return Database connection
     */
    @NonNull
    public SynchronizedDb getSyncDb() {
        return sSyncedDb;
    }

    /**
     * Set the Goodreads sync date to the current time.
     *
     * @param bookId the book
     */
    public void setGoodreadsSyncDate(final long bookId) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_UPDATE_GOODREADS_SYNC_DATE);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_UPDATE_GOODREADS_SYNC_DATE,
                                            SqlUpdate.GOODREADS_LAST_SYNC_DATE);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
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
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, mInstanceName + "|close");
        }
        if (mSqlStatementManager != null) {
            // the close() will perform a clear, ready to be re-used.
            mSqlStatementManager.close();
        }
        mCloseWasCalled = true;
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
                Logger.w(TAG, "finalize|" + mInstanceName);
            }
            close();
        }
        super.finalize();
    }

    /**
     * Used by {@link CsvImporter}.
     *
     * @param isUpdate Indicates if updates will be done in TX
     *
     * @return the lock
     */
    @NonNull
    public SyncLock startTransaction(final boolean isUpdate) {
        return sSyncedDb.beginTransaction(isUpdate);
    }

    /**
     * Used by {@link CsvImporter}.
     *
     * @param txLock Lock returned from BeginTransaction().
     */
    public void endTransaction(@Nullable final SyncLock txLock) {
        // it's cleaner to have the null detection here
        Objects.requireNonNull(txLock);
        sSyncedDb.endTransaction(txLock);
    }

    /**
     * Used by {@link CsvImporter}.
     */
    public boolean inTransaction() {
        return sSyncedDb.inTransaction();
    }

    /**
     * Used by {@link CsvImporter}.
     */
    public void setTransactionSuccessful() {
        sSyncedDb.setTransactionSuccessful();
    }

    /**
     * Delete an individual TocEntry.
     *
     * @param id to delete.
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteTocEntry(final long id) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_DELETE_TOC_ENTRY);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_DELETE_TOC_ENTRY, SqlDelete.TOC_ENTRY);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, id);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Delete the link between TocEntry's and the given Book.
     * Note that the actual TocEntry's are NOT deleted here.
     *
     * @param bookId id of the book
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    private int deleteBookTocEntryByBookId(final long bookId) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_DELETE_BOOK_TOC_ENTRIES);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_DELETE_BOOK_TOC_ENTRIES,
                                            SqlDelete.BOOK_TOC_ENTRIES_BY_BOOK_ID);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Return the TocEntry id. The incoming object is not modified.
     * (note that publication year is NOT used for comparing, under the assumption that
     * two search-sources can give different dates by mistake).
     *
     * @param context    Current context
     * @param tocEntry   tocEntry to search for
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getTocEntryId(@NonNull final Context context,
                              @NonNull final TocEntry tocEntry,
                              @NonNull final Locale bookLocale) {

        Locale tocLocale = tocEntry.getLocale(context, this, bookLocale);

        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_GET_TOC_ENTRY_ID);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_GET_TOC_ENTRY_ID, SqlGet.TOC_ENTRY_ID);
        }

        String obTitle = tocEntry.reorderTitleForSorting(context, tocLocale);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, tocEntry.getAuthor().getId());
            stmt.bindString(2, encodeOrderByColumn(tocEntry.getTitle(), tocLocale));
            stmt.bindString(3, encodeOrderByColumn(obTitle, tocLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Return all the {@link TocEntry} for the given {@link Author}.
     *
     * @param author         to retrieve
     * @param bookshelfId    limit the list to books on this shelf (pass -1 for all shelves)
     * @param withTocEntries add the toc entries
     * @param withBooks      add books without TOC as well; i.e. the toc of a book without a toc,
     *                       is the book title itself. (makes sense?)
     *
     * @return List of {@link TocEntry} for this {@link Author}
     */
    @NonNull
    public ArrayList<TocEntry> getTocEntryByAuthor(@NonNull final Author author,
                                                   final long bookshelfId,
                                                   final boolean withTocEntries,
                                                   final boolean withBooks) {

        // sanity check
        if (!withTocEntries && !withBooks) {
            throw new IllegalArgumentException("Must specify what to fetch");
        }

        boolean byShelf = bookshelfId != Bookshelf.ALL_BOOKS;

        // rawQuery wants String[] as bind parameters
        String authorIdStr = String.valueOf(author.getId());
        String bookshelfIdStr = String.valueOf(bookshelfId);

        String sql = "";
        List<String> paramList = new ArrayList<>();

        // MUST be toc first, books second; otherwise the GROUP BY is done on the whole
        // UNION instead of on the toc only; and SqLite rejects () around the sub selects.
        if (withTocEntries) {
            sql += SqlSelectList.TOC_ENTRIES_BY_AUTHOR_ID
                   // join with the books, so we can group by toc id, and get the number of books.
                   + _FROM_ + TBL_TOC_ENTRIES.ref() + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                   + (byShelf ? " JOIN " + TBL_BOOK_BOOKSHELF.ref()
                                + " ON (" + TBL_BOOK_TOC_ENTRIES.dot(KEY_FK_BOOK)
                                + '=' + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOK) + ")"
                              : "")
                   + _WHERE_ + TBL_TOC_ENTRIES.dot(KEY_FK_AUTHOR) + "=?"
                   + (byShelf ? " AND " + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOKSHELF) + "=?"
                              : "")
                   + " GROUP BY " + TBL_TOC_ENTRIES.dot(KEY_PK_ID);
            paramList.add(authorIdStr);
            if (byShelf) {
                paramList.add(bookshelfIdStr);
            }
        }

        if (withBooks && withTocEntries) {
            sql += " UNION ";
        }

        if (withBooks) {
            sql += SqlSelectList.BOOK_TITLES_BY_AUTHOR_ID
                   + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_AUTHOR)
                   + (byShelf ? TBL_BOOKS.join(TBL_BOOK_BOOKSHELF)
                              : "")
                   + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR) + "=?"
                   + (byShelf ? " AND " + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOKSHELF) + "=?"
                              : "");
            paramList.add(authorIdStr);
            if (byShelf) {
                paramList.add(bookshelfIdStr);
            }
        }

        sql += " ORDER BY " + KEY_TITLE_OB + COLLATION;

        ArrayList<TocEntry> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(sql, paramList.toArray(new String[0]))) {
            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                TocEntry tocEntry = new TocEntry(rowData.getLong(KEY_PK_ID),
                                                 author,
                                                 rowData.getString(KEY_TITLE),
                                                 rowData.getString(KEY_DATE_FIRST_PUBLICATION),
                                                 rowData.getString(KEY_TOC_TYPE).charAt(0),
                                                 rowData.getInt(KEY_BOOK_COUNT));
                list.add(tocEntry);
            }
        }
        return list;
    }

    /**
     * Creates a new {@link Author}.
     *
     * @param context Current context
     * @param author  object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted Author, or {@code -1} if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    private long insertAuthor(@NonNull final Context context,
                              @NonNull final Author /* in/out */ author) {

        Locale authorLocale = author.getLocale(context, this, LocaleUtils.getUserLocale(context));

        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_INSERT_AUTHOR);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_INSERT_AUTHOR, SqlInsert.AUTHOR);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, author.getFamilyName());
            stmt.bindString(2, encodeOrderByColumn(author.getFamilyName(), authorLocale));
            stmt.bindString(3, author.getGivenNames());
            stmt.bindString(4, encodeOrderByColumn(author.getGivenNames(), authorLocale));
            stmt.bindBoolean(5, author.isComplete());
            long iId = stmt.executeInsert();
            if (iId > 0) {
                author.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Update an Author.
     *
     * @param context Current context
     * @param author  to update
     *
     * @return {@code true} for success.
     */
    public boolean updateAuthor(@NonNull final Context context,
                                @NonNull final Author author) {

        Locale authorLocale = author.getLocale(context, this, LocaleUtils.getUserLocale(context));

        ContentValues cv = new ContentValues();
        cv.put(KEY_AUTHOR_FAMILY_NAME, author.getFamilyName());
        cv.put(KEY_AUTHOR_FAMILY_NAME_OB,
               encodeOrderByColumn(author.getFamilyName(), authorLocale));
        cv.put(KEY_AUTHOR_GIVEN_NAMES, author.getGivenNames());
        cv.put(KEY_AUTHOR_GIVEN_NAMES_OB,
               encodeOrderByColumn(author.getGivenNames(), authorLocale));
        cv.put(KEY_AUTHOR_IS_COMPLETE, author.isComplete());

        return 0 < sSyncedDb.update(TBL_AUTHORS.getName(), cv,
                                    KEY_PK_ID + "=?",
                                    new String[]{String.valueOf(author.getId())});
    }

    /**
     * Add or update the passed {@link Author}, depending whether author.id == 0.
     *
     * @param context Current context
     * @param author  object to insert or update. Will be updated with the id.
     *
     * @return {@code true} for success.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertAuthor(@NonNull final Context context,
                                        @NonNull final /* in/out */ Author author) {

        if (author.getId() != 0) {
            return updateAuthor(context, author);
        } else {
            // try to find first.
            if (author.fixId(context, this, LocaleUtils.getUserLocale(context)) == 0) {
                return insertAuthor(context, author) > 0;
            }
        }
        return false;
    }

    /**
     * Get all Authors; mainly for the purpose of backups.
     *
     * @return Cursor over all Authors
     */
    @NonNull
    public Cursor fetchAuthors() {
        return sSyncedDb.rawQuery(SqlSelectFullTable.AUTHORS, null);
    }

    /**
     * Get the {@link Author} based on the ID.
     *
     * @param id author id
     *
     * @return the author, or {@code null} if not found
     */
    @Nullable
    public Author getAuthor(final long id) {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelect.AUTHOR_BY_ID,
                                                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Author(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    /**
     * Find an Author, and return its ID. The incoming object is not modified.
     *
     * <strong>IMPORTANT:</strong> the query can return more then one row if the
     * given-name of the author is empty. But we only return the id of the first row found.
     *
     * @param context Current context
     * @param author  to find the id of
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getAuthorId(@NonNull final Context context,
                            @NonNull final Author author,
                            @NonNull final Locale fallbackLocale) {

        Locale authorLocale = author.getLocale(context, this, fallbackLocale);

        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_GET_AUTHOR_ID);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_GET_AUTHOR_ID, SqlGet.AUTHOR_ID_BY_NAME);
        }

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, encodeOrderByColumn(author.getFamilyName(), authorLocale));
            stmt.bindString(2, encodeOrderByColumn(author.getGivenNames(), authorLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Refresh the passed Author from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the Author.
     * <p>
     * Will NOT insert a new Author if not found.
     *
     * @param context Current context
     * @param author  to refresh
     */
    public void refreshAuthor(@NonNull final Context context,
                              @NonNull final Author /* out */ author) {

        if (author.getId() == 0) {
            // It wasn't saved before; see if it is now. If so, update ID.
            author.fixId(context, this, LocaleUtils.getUserLocale(context));

        } else {
            // It was saved, see if it still is and fetch possibly updated fields.
            Author dbAuthor = getAuthor(author.getId());
            if (dbAuthor != null) {
                // copy any updated fields
                author.copyFrom(dbAuthor, false);
            } else {
                // not found?, set as 'new'
                author.setId(0);
            }
        }
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
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_UPDATE_AUTHOR_ON_TOC_ENTRIES);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_UPDATE_AUTHOR_ON_TOC_ENTRIES,
                                            SqlUpdate.AUTHOR_ON_TOC_ENTRIES);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, to);
            stmt.bindLong(2, from);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Get authors names for filling an {@link AutoCompleteTextView}.
     *
     * @param key type of name wanted, one of
     *            {@link DBDefinitions#KEY_AUTHOR_FAMILY_NAME},
     *            {@link DBDefinitions#KEY_AUTHOR_GIVEN_NAMES},
     *            {@link DBDefinitions#KEY_AUTHOR_FORMATTED},
     *            {@link DBDefinitions#KEY_AUTHOR_FORMATTED_GIVEN_FIRST}
     *
     * @return list of all author names.
     */
    @NonNull
    public ArrayList<String> getAuthorNames(@NonNull final String key) {
        switch (key) {
            case KEY_AUTHOR_FAMILY_NAME:
                return getColumnAsList(SqlSelectFullTable.AUTHORS_FAMILY_NAMES, key);

            case KEY_AUTHOR_GIVEN_NAMES:
                return getColumnAsList(SqlSelectFullTable.AUTHORS_GIVEN_NAMES, key);

            case KEY_AUTHOR_FORMATTED:
                return getColumnAsList(SqlSelectFullTable.AUTHORS_FORMATTED_NAMES, key);

            case KEY_AUTHOR_FORMATTED_GIVEN_FIRST:
                return getColumnAsList(SqlSelectFullTable.AUTHORS_FORMATTED_NAMES_GIVEN_FIRST, key);

            default:
                throw new UnexpectedValueException(key);
        }
    }

    /**
     * Purge anything that is no longer in use.
     * <p>
     * Purging is no longer done at every occasion where it *might* be needed.
     * It was noticed (in the logs) that it was done far to often.
     * It is now called only:
     * <p>
     * Before a backup (but not before a CSV export)
     * After an import of data; includes after Archive, CSV, XML imports.
     * <p>
     * So orphaned data will stay around a little longer which in fact may be beneficial
     * while entering/correcting a book collection.
     */
    public void purge() {
        try {
            // Note: purging TocEntry's is automatic due to foreign key cascading.
            // i.e. a TocEntry is linked directly with authors;
            // and linked with books via a link table.
            purgeAuthors();
            purgeSeries();

            analyze();

        } catch (@NonNull final RuntimeException e) {
            // log to file, this is bad.
            Logger.error(App.getAppContext(), TAG, e);
        }
    }

    /**
     * Delete all Authors without related books / TocEntry's.
     */
    private void purgeAuthors() {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlDelete.PURGE_AUTHORS)) {
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Delete all Series without related books.
     */
    private void purgeSeries() {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlDelete.PURGE_SERIES)) {
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Count the books for the given Author.
     *
     * @param context Current context
     * @param author  to retrieve
     *
     * @return the number of {@link Book} this {@link Author} has
     */
    public long countBooksByAuthor(@NonNull final Context context,
                                   @NonNull final Author author) {
        if (author.getId() == 0 && author.fixId(context, this,
                                                LocaleUtils.getUserLocale(context)) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt =
                     sSyncedDb.compileStatement(SqlSelect.COUNT_BOOKS_BY_AUTHOR)) {
            stmt.bindLong(1, author.getId());
            return stmt.count();
        }
    }

    /**
     * Count the TocEntry's for the given Author.
     *
     * @param context Current context
     * @param author  to count the TocEntries of
     *
     * @return the number of {@link TocEntry} this {@link Author} has
     */
    public long countTocEntryByAuthor(@NonNull final Context context,
                                      @NonNull final Author author) {
        if (author.getId() == 0 && author.fixId(context, this,
                                                LocaleUtils.getUserLocale(context)) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt =
                     sSyncedDb.compileStatement(SqlSelect.COUNT_TOC_ENTRIES_BY_AUTHOR)) {
            stmt.bindLong(1, author.getId());
            return stmt.count();
        }
    }

    /**
     * Check that a book with the passed UUID exists and return the id of the book, or zero.
     *
     * @param uuid UUID of the book
     *
     * @return id of the book, or 0 'new' if not found
     */
    public long getBookIdFromUuid(@NonNull final String uuid) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_GET_BOOK_ID_FROM_UUID);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_GET_BOOK_ID_FROM_UUID, SqlGet.BOOK_ID_BY_UUID);
        }

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /***
     * Return the book last update date based on the id.
     *
     * @param bookId of the book
     *
     * @return the last update date as a standard sql date string
     */
    @Nullable
    public String getBookLastUpdateDate(final long bookId) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_GET_BOOK_UPDATE_DATE);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_GET_BOOK_UPDATE_DATE,
                                            SqlSelect.LAST_UPDATE_DATE_BY_BOOK_ID);
        }

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
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
     * @return the book UUID, or {@code null} if not found/failure
     *
     * @throws IllegalArgumentException if the bookId==0
     */
    @Nullable
    public String getBookUuid(final long bookId)
            throws IllegalArgumentException {
        // sanity check
        if (bookId == 0) {
            throw new IllegalArgumentException("cannot get uuid for id==0");
        }
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_GET_BOOK_UUID);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_GET_BOOK_UUID, SqlGet.BOOK_UUID_BY_ID);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    /**
     * Return the book title based on the id.
     *
     * @param bookId of the book
     *
     * @return the title, or {@code null} if not found
     */
    @Nullable
    public String getBookTitle(final long bookId) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_GET_BOOK_TITLE);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_GET_BOOK_TITLE, SqlGet.BOOK_TITLE_BY_BOOK_ID);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    /**
     * Return the book ISBN based on the id.
     *
     * @param bookId of the book
     *
     * @return the ISBN, or {@code null} if not found
     */
    @Nullable
    public String getBookIsbn(final long bookId) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_GET_BOOK_ISBN);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_GET_BOOK_ISBN, SqlGet.BOOK_ISBN_BY_BOOK_ID);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    /**
     * Delete the given book (and its covers).
     *
     * @param context Current context
     * @param bookId  of the book.
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteBook(@NonNull final Context context,
                          final long bookId) {

        String uuid = getBookUuid(bookId);

        int rowsAffected = 0;
        SyncLock txLock = sSyncedDb.beginTransaction(true);
        try {
            SynchronizedStatement stmt = mSqlStatementManager.get(STMT_DELETE_BOOK);
            if (stmt == null) {
                stmt = mSqlStatementManager.add(STMT_DELETE_BOOK, SqlDelete.BOOK_BY_ID);
            }
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, bookId);
                rowsAffected = stmt.executeUpdateDelete();
            }
            if (rowsAffected > 0) {
                deleteThumbnail(context, uuid);
            }
            sSyncedDb.setTransactionSuccessful();
        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e, "Failed to delete book");
        } finally {
            sSyncedDb.endTransaction(txLock);
        }

        return rowsAffected;
    }

    /**
     * Given a book's uuid, delete the thumbnail (if any).
     *
     * @param context Current context
     * @param uuid    UUID of the book
     */
    private void deleteThumbnail(@NonNull final Context context,
                                 @Nullable final String uuid) {
        if (uuid != null && !uuid.isEmpty()) {
            // remove from file system
            for (int cIdx = 0; cIdx < 2; cIdx++) {
                File thumb = AppDir.getCoverFile(context, uuid, cIdx);
                FileUtils.delete(thumb);
            }

            // remove from cache
            CoversDAO.delete(context, uuid);
        }
    }

    /**
     * Create a new book using the details provided.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     * @param bookId  <strong>must</strong> be {@code 0} for a new book.
     *                Only an Import is allowed to pass non-zero: will override the autoIncrement.
     * @param book    A collection with the columns to be set. May contain extra data.
     *                The id will be updated.
     *
     * @return the row id of the newly inserted row
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1, to = Integer.MAX_VALUE)
    public long insertBook(@NonNull final Context context,
                           final long bookId,
                           @NonNull final Book /* in/out */ book)
            throws DaoWriteException {

        SyncLock txLock = null;
        if (!sSyncedDb.inTransaction()) {
            txLock = sSyncedDb.beginTransaction(true);
        }

        try {
            book.preprocessForStoring(context, true);

            // Make sure we have at least one author
            List<Author> authors = book.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
            if (authors.isEmpty()) {
                throw new DaoWriteException("No authors for book=" + book);
            }

            // correct field types if needed, and filter out fields we don't have in the db table.
            ContentValues cv = filterValues(TBL_BOOKS, book, book.getLocale(context));

            // if we have an id, use it.
            if (bookId > 0) {
                cv.put(KEY_PK_ID, bookId);
            }

            // if we do NOT have a date set, then use TODAY
            if (!cv.containsKey(DBDefinitions.KEY_DATE_ADDED)) {
                cv.put(DBDefinitions.KEY_DATE_ADDED, DateUtils.utcSqlDateTimeForToday());
            }

            // if we do NOT have a date set, then use TODAY
            if (!cv.containsKey(KEY_DATE_LAST_UPDATED)) {
                cv.put(KEY_DATE_LAST_UPDATED, DateUtils.utcSqlDateTimeForToday());
            }

            // the book itself
            long newBookId = sSyncedDb.insert(TBL_BOOKS.getName(), null, cv);
            if (newBookId > 0) {
                // the links to series, authors,...
                insertBookDependents(context, newBookId, book);
                // and the search suggestions table
                ftsInsert(context, newBookId);
                if (txLock != null) {
                    sSyncedDb.setTransactionSuccessful();
                }

                // set the new id/uuid on the Book itself
                book.putLong(KEY_PK_ID, newBookId);
                //noinspection ConstantConditions
                book.putString(KEY_BOOK_UUID, getBookUuid(newBookId));
                // and return the id
                return newBookId;

            } else {
                throw new DaoWriteException(ERROR_FAILED_CREATING_BOOK_FROM + book);
            }

        } catch (@NonNull final IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_FAILED_CREATING_BOOK_FROM + book, e);
        } finally {
            if (txLock != null) {
                sSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     * @param bookId  of the book; takes precedence over the id of the book itself.
     * @param book    A collection with the columns to be set. May contain extra data.
     * @param flags   See {@link #BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT} for flag definition
     *
     * @throws DaoWriteException on failure
     */
    public void updateBook(@NonNull final Context context,
                           final long bookId,
                           @NonNull final Book book,
                           final int flags)
            throws DaoWriteException {

        SyncLock txLock = null;
        if (!sSyncedDb.inTransaction()) {
            txLock = sSyncedDb.beginTransaction(true);
        }

        try {
            book.preprocessForStoring(context, false);

            // correct field types if needed, and filter out fields we don't have in the db table.
            ContentValues cv = filterValues(TBL_BOOKS, book, book.getLocale(context));

            // Disallow UUID updates
            if (cv.containsKey(KEY_BOOK_UUID)) {
                cv.remove(KEY_BOOK_UUID);
            }

            // set the KEY_DATE_LAST_UPDATED to 'now' if we're allowed,
            // or if it's not present already.
            if ((flags & BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT) == 0
                || !cv.containsKey(KEY_DATE_LAST_UPDATED)) {
                cv.put(KEY_DATE_LAST_UPDATED, DateUtils.utcSqlDateTimeForToday());
            }

            // go !
            // A prepared statement would be faster for importing books....
            // but we don't know what columns are provided in the bundle....
            boolean success = 0 < sSyncedDb.update(TBL_BOOKS.getName(), cv, KEY_PK_ID + "=?",
                                                   new String[]{String.valueOf(bookId)});
            if (success) {
                insertBookDependents(context, bookId, book);
                ftsUpdate(context, bookId);
                if (txLock != null) {
                    sSyncedDb.setTransactionSuccessful();
                }
                // make sure the Book has the correct id.
                book.putLong(KEY_PK_ID, bookId);

            } else {
                throw new DaoWriteException(ERROR_FAILED_UPDATING_BOOK_FROM + book);
            }
        } catch (@NonNull final IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_FAILED_UPDATING_BOOK_FROM + book);

        } finally {
            if (txLock != null) {
                sSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * Update the 'read' status of the book.
     *
     * @param id   book to update
     * @param read the status to set
     *
     * @return {@code true} for success.
     */
    public boolean setBookRead(final long id,
                               final boolean read) {
        ContentValues cv = new ContentValues();
        cv.put(KEY_READ, read);
        if (read) {
            cv.put(KEY_READ_END, DateUtils.localSqlDateForToday());
        } else {
            cv.put(KEY_READ_END, "");
        }

        return 0 < sSyncedDb.update(TBL_BOOKS.getName(), cv, KEY_PK_ID + "=?",
                                    new String[]{String.valueOf(id)});
    }

    /**
     * Update the 'complete' status of an Author.
     *
     * @param authorId   to update
     * @param isComplete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    public boolean setAuthorComplete(final long authorId,
                                     final boolean isComplete) {
        ContentValues cv = new ContentValues();
        cv.put(KEY_AUTHOR_IS_COMPLETE, isComplete);

        return 0 < sSyncedDb.update(TBL_AUTHORS.getName(), cv, KEY_PK_ID + "=?",
                                    new String[]{String.valueOf(authorId)});
    }

    /**
     * Update the 'complete' status of a Series.
     *
     * @param seriesId   to update
     * @param isComplete Flag indicating the user considers this item to be 'complete'
     *
     * @return {@code true} for success.
     */
    public boolean setSeriesComplete(final long seriesId,
                                     final boolean isComplete) {
        ContentValues cv = new ContentValues();
        cv.put(KEY_SERIES_IS_COMPLETE, isComplete);

        return 0 < sSyncedDb.update(TBL_SERIES.getName(), cv, KEY_PK_ID + "=?",
                                    new String[]{String.valueOf(seriesId)});
    }

    /**
     * shared between book insert & update.
     * All of these will first delete all entries in the Book-[tableX] table for this bookId,
     * and then insert the new rows.
     *
     * @param context Current context
     * @param bookId  of the book
     * @param book    A collection with the columns to be set. May contain extra data.
     */
    private void insertBookDependents(@NonNull final Context context,
                                      final long bookId,
                                      @NonNull final Book book) {

        if (book.contains(Book.BKEY_BOOKSHELF_ARRAY)) {
            insertBookBookshelf(context, bookId, book);
        }

        if (book.contains(Book.BKEY_AUTHOR_ARRAY)) {
            insertBookAuthors(context, bookId,
                              book.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY));
        }

        if (book.contains(Book.BKEY_SERIES_ARRAY)) {
            insertBookSeries(context, bookId, book.getLocale(context),
                             book.getParcelableArrayList(Book.BKEY_SERIES_ARRAY));
        }

        if (book.contains(Book.BKEY_TOC_ENTRY_ARRAY)) {
            // update: toc entries are two steps away; they can exist in other books
            updateOrInsertTOC(context, bookId, book);
        }

        if (book.contains(KEY_LOANEE)
            && !book.getString(KEY_LOANEE).isEmpty()) {
            lendBook(bookId, book.getString(KEY_LOANEE));
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
        return sSyncedDb.rawQuery(SqlSelectFullTable.BOOK_ALL_UUID, null);
    }

    /**
     * Search for a book by the given ISBN.
     *
     * @param isbn to search for; can be generic/non-valid
     *
     * @return book id, or 0 if not found
     */
    public long getBookIdFromIsbn(@NonNull final ISBN isbn) {
        // if the string is ISBN-10 compatible, i.e. an actual ISBN-10,
        // or an ISBN-13 in the 978 range, we search on both formats
        if (isbn.isIsbn10Compat()) {
            SynchronizedStatement stmt =
                    mSqlStatementManager.get(STMT_GET_BOOK_ID_FROM_VALID_ISBN);
            if (stmt == null) {
                stmt = mSqlStatementManager.add(STMT_GET_BOOK_ID_FROM_VALID_ISBN,
                                                SqlGet.BOOK_ID_BY_VALID_ISBN);
            }
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                // the asText for ISBN10/ISBN13 is always uppercase
                // and their values in the database are also always uppercase.
                stmt.bindString(1, isbn.asText(ISBN.Type.ISBN10));
                stmt.bindString(2, isbn.asText(ISBN.Type.ISBN13));
                return stmt.simpleQueryForLongOrZero();
            }

        } else {
            // otherwise just search on the string as-is; regardless of validity
            // (this would actually include valid ISBN-13 in the 979 range).
            SynchronizedStatement stmt =
                    mSqlStatementManager.get(STMT_GET_BOOK_ID_FROM_ISBN);
            if (stmt == null) {
                stmt = mSqlStatementManager.add(STMT_GET_BOOK_ID_FROM_ISBN,
                                                SqlGet.BOOK_ID_BY_ISBN);
            }
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                // The generic asText can contain mixed case characters!
                // This is covered by the SQL, which is using 'upper()'.
                stmt.bindString(1, isbn.asText());
                return stmt.simpleQueryForLongOrZero();
            }
        }
    }

    /**
     * Check that a book with the passed id exists.
     *
     * @param bookId of the book
     *
     * @return {@code true} if exists
     */
    public boolean bookExists(final long bookId) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_CHECK_BOOK_EXISTS);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_CHECK_BOOK_EXISTS, SqlSelect.BOOK_EXISTS);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.count() == 1;
        }
    }

    /**
     * Create the link between {@link Book} and {@link Series}.
     * <p>
     * {@link DBDefinitions#TBL_BOOK_SERIES}
     * <p>
     * Note that {@link DBDefinitions#KEY_BOOK_SERIES_POSITION} is a simple incrementing
     * counter matching the order of the passed list.
     *
     * @param context    Current context
     * @param bookId     of the book
     * @param bookLocale to use as fallback for the series locale
     * @param seriesList the list of Series
     */
    void insertBookSeries(@NonNull final Context context,
                          final long bookId,
                          @NonNull final Locale bookLocale,
                          @SuppressWarnings("TypeMayBeWeakened")
                          @NonNull final List<Series> seriesList) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_REQUIRES_TRANSACTION);
        }

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookSeriesByBookId(bookId);

        // anything to insert ?
        if (seriesList.isEmpty()) {
            return;
        }

        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_INSERT_BOOK_SERIES);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_INSERT_BOOK_SERIES, SqlInsert.BOOK_SERIES);
        }

        // The list MAY contain duplicates (e.g. from Internet lookups of multiple
        // sources), so we track them in a hash map
        final Map<String, Boolean> idHash = new HashMap<>();
        int position = 0;
        for (Series series : seriesList) {
            if (series.fixId(context, this, bookLocale) == 0) {
                if (insertSeries(context, series, bookLocale) <= 0) {
                    Logger.warnWithStackTrace(context, TAG, "insertSeries failed??");
                }
            } else {
                if (!updateSeries(context, series, bookLocale)) {
                    Logger.warnWithStackTrace(context, TAG, "updateSeries failed??");
                }
            }

            Locale seriesLocale = series.getLocale(context, this, bookLocale);
            String uniqueId = series.getId() + '_' + series.getNumber().toLowerCase(seriesLocale);
            if (!idHash.containsKey(uniqueId)) {
                idHash.put(uniqueId, true);
                position++;
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (stmt) {
                    stmt.bindLong(1, bookId);
                    stmt.bindLong(2, series.getId());
                    stmt.bindString(3, series.getNumber());
                    stmt.bindLong(4, position);
                    stmt.executeInsert();
                }
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
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_DELETE_BOOK_SERIES);
        if (stmt == null) {
            stmt = mSqlStatementManager
                    .add(STMT_DELETE_BOOK_SERIES, SqlDelete.BOOK_SERIES_BY_BOOK_ID);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Insert a List of TocEntry's for the given book.
     *
     * @param context Current context
     * @param bookId  of the book
     * @param book    A collection with the columns to be set. May contain extra data.
     */
    private void updateOrInsertTOC(@NonNull final Context context,
                                   final long bookId,
                                   @NonNull final Book book) {
        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_REQUIRES_TRANSACTION);
        }

        ArrayList<TocEntry> list = book.getParcelableArrayList(Book.BKEY_TOC_ENTRY_ARRAY);

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookTocEntryByBookId(bookId);

        // anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        long position = 0;

        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_INSERT_BOOK_TOC_ENTRY);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_INSERT_BOOK_TOC_ENTRY, SqlInsert.BOOK_TOC_ENTRY);
        }

        Locale userLocale = LocaleUtils.getUserLocale(context);
        for (TocEntry tocEntry : list) {
            // handle the author.
            Author author = tocEntry.getAuthor();
            if (author.fixId(context, this, userLocale) == 0) {
                insertAuthor(context, author);
            }

            Locale bookLocale = book.getLocale(context);
            // As an entry can exist in multiple books, try to find the entry.
            if (tocEntry.fixId(context, this, bookLocale) == 0) {
                // it's a new entry
                insertTocEntry(context, tocEntry, bookLocale);

            } else {
                // It's an existing entry.
                // We cannot update the author (we never even get here if the author was changed)
                // We *do* update the title to allow corrections of case,
                // as the find was done on the KEY_TITLE_OB field.
                // and we update the KEY_TITLE_OB as well obviously.
                String obTitle = tocEntry.reorderTitleForSorting(context, bookLocale);

                ContentValues cv = new ContentValues();
                cv.put(KEY_TITLE, tocEntry.getTitle());
                cv.put(KEY_TITLE_OB, encodeOrderByColumn(obTitle, bookLocale));
                cv.put(KEY_DATE_FIRST_PUBLICATION, tocEntry.getFirstPublication());

                // failures are ignored
                sSyncedDb.update(TBL_TOC_ENTRIES.getName(), cv,
                                 KEY_PK_ID + "=?",
                                 new String[]{String.valueOf(tocEntry.getId())});
            }

            // create the book<->TocEntry link.
            //
            // As we delete all links before insert/update'ng above, we normally
            // *always* need to re-create the link here.
            // However, this will fail if we inserted "The Universe" and updated "Universe, The"
            // as the former will be stored as "Universe, The".
            // We tried to mitigate this conflict before it could trigger an issue here, but it
            // complicated the code and frankly ended in a chain of special condition code branches
            // during processing of internet search data.
            // So... let's just catch the SQL constraint exception and ignore it.
            // (do not use the sql 'REPLACE' command! We want to keep the original position)

            try {
                position++;
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (stmt) {
                    stmt.bindLong(1, tocEntry.getId());
                    stmt.bindLong(2, bookId);
                    stmt.bindLong(3, position);
                    stmt.executeInsert();
                }

            } catch (@NonNull final SQLiteConstraintException e) {
                // ignore and reset the position counter.
                position--;
            }
        }
    }

    /**
     * Creates a new TocEntry in the database.
     *
     * @param context    Current context
     * @param tocEntry   object to insert. Will be updated with the id.
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    private long insertTocEntry(@NonNull final Context context,
                                @NonNull final TocEntry tocEntry,
                                @NonNull final Locale bookLocale) {

        Locale tocLocale = tocEntry.getLocale(context, this, bookLocale);

        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_INSERT_TOC_ENTRY);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_INSERT_TOC_ENTRY, SqlInsert.TOC_ENTRY);
        }

        String obTitle = tocEntry.reorderTitleForSorting(context, tocLocale);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, tocEntry.getAuthor().getId());
            stmt.bindString(2, tocEntry.getTitle());
            stmt.bindString(3, encodeOrderByColumn(obTitle, tocLocale));
            stmt.bindString(4, tocEntry.getFirstPublication());
            long iId = stmt.executeInsert();
            if (iId > 0) {
                tocEntry.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Create the link between {@link Book} and {@link Author}.
     * <p>
     * {@link DBDefinitions#TBL_BOOK_AUTHOR}
     * <p>
     * Note that {@link DBDefinitions#KEY_BOOK_AUTHOR_POSITION} is a simple incrementing
     * counter matching the order of the passed list.
     *
     * @param context    Current context
     * @param bookId     of the book
     * @param authorList the list of authors
     */
    void insertBookAuthors(@NonNull final Context context,
                           final long bookId,
                           @SuppressWarnings("TypeMayBeWeakened")
                           @NonNull final List<Author> authorList) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_REQUIRES_TRANSACTION);
        }

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookAuthorByBookId(bookId);

        // anything to insert ?
        if (authorList.isEmpty()) {
            return;
        }

        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_INSERT_BOOK_AUTHORS);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_INSERT_BOOK_AUTHORS, SqlInsert.BOOK_AUTHOR);
        }

        Locale userLocale = LocaleUtils.getUserLocale(context);
        // The list MAY contain duplicates, so we track them in a hash table
        final Map<String, Boolean> idHash = new HashMap<>();
        int position = 0;
        for (Author author : authorList) {
            // find/insert the author
            if (author.fixId(context, this, userLocale) == 0) {
                insertAuthor(context, author);
            }

            // we use the id as the KEY here, so yes, a String.
            String authorIdStr = String.valueOf(author.getId());
            if (!idHash.containsKey(authorIdStr)) {
                // indicate this author(id) is already present...
                // but override, so we get elimination of duplicates.
                idHash.put(authorIdStr, true);

                position++;
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (stmt) {
                    stmt.bindLong(1, bookId);
                    stmt.bindLong(2, author.getId());
                    stmt.bindLong(3, position);
                    stmt.bindLong(4, author.getType());
                    stmt.executeInsert();
                }

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
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_DELETE_BOOK_AUTHORS);
        if (stmt == null) {
            stmt = mSqlStatementManager
                    .add(STMT_DELETE_BOOK_AUTHORS, SqlDelete.BOOK_AUTHOR_BY_BOOK_ID);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Create the link between {@link Book} and {@link Bookshelf}.
     * <p>
     * {@link DBDefinitions#TBL_BOOK_BOOKSHELF}
     * <p>
     * Note that {@link DBDefinitions#KEY_BOOK_SERIES_POSITION} is a simple incrementing
     * counter matching the order of the passed list.
     *
     * @param context Current context
     * @param bookId  of the book
     * @param book    A collection with the columns to be set. May contain extra data.
     */
    private void insertBookBookshelf(@NonNull final Context context,
                                     @IntRange(from = 1) final long bookId,
                                     @NonNull final Book book) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_REQUIRES_TRANSACTION);
        }

        ArrayList<Bookshelf> bookshelves =
                book.getParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY);

        // Need to delete the current records because they may have been reordered and a simple
        // set of updates could result in unique key or index violations.
        deleteBookBookshelfByBookId(bookId);

        // anything to insert ?
        if (bookshelves.isEmpty()) {
            return;
        }

        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_INSERT_BOOK_BOOKSHELF);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_INSERT_BOOK_BOOKSHELF, SqlInsert.BOOK_BOOKSHELF);
        }

        Locale userLocale = LocaleUtils.getUserLocale(context);
        for (Bookshelf bookshelf : bookshelves) {
            if (bookshelf.getName().isEmpty()) {
                continue;
            }

            // validate the style first
            long styleId = bookshelf.getStyle(context, this).getId();

            if (bookshelf.fixId(context, this, userLocale) == 0) {
                insertBookshelf(bookshelf, styleId);
            }

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, bookId);
                stmt.bindLong(2, bookshelf.getId());
                stmt.executeInsert();
            }
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
    private int deleteBookBookshelfByBookId(@IntRange(from = 1) final long bookId) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_DELETE_BOOK_BOOKSHELF);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_DELETE_BOOK_BOOKSHELF,
                                            SqlDelete.BOOK_BOOKSHELF_BY_BOOK_ID);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.executeUpdateDelete();
        }
    }

    /**
     * Get the list of TocEntry for this book.
     *
     * @param bookId of the book
     *
     * @return list
     */
    @NonNull
    public ArrayList<TocEntry> getTocEntryByBook(final long bookId) {
        ArrayList<TocEntry> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.TOC_ENTRIES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new TocEntry(rowData.getLong(KEY_PK_ID),
                                      new Author(rowData.getLong(KEY_FK_AUTHOR), rowData),
                                      rowData.getString(KEY_TITLE),
                                      rowData.getString(KEY_DATE_FIRST_PUBLICATION),
                                      TocEntry.Type.TYPE_BOOK,
                                      rowData.getInt(KEY_BOOK_COUNT)));
            }
        }
        return list;
    }

    /*
     * Bad idea. Instead use: Book book = Book.getBook(mDb, bookId);
     * So you never get a {@code null} Book!
     *
     * Leaving commented as a reminder
     *
     * @param bookId of the book
     *
     * @return the fully populated Book, or {@code null} if not found
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
     * Get a list of book ID's (most often just the one) in which this TocEntry (story) is present.
     *
     * @param tocId id of the entry (story)
     *
     * @return list with book ids
     */
    public ArrayList<Long> getBookIdsByTocEntry(final long tocId) {
        ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlGet.BOOK_ID_BY_TOC_ENTRY_ID,
                                                new String[]{String.valueOf(tocId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's for the given Author.
     *
     * @param authorId id of the author
     *
     * @return list with book ids
     */
    public ArrayList<Long> getBookIdsByAuthor(final long authorId) {
        ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.BOOK_IDS_BY_AUTHOR_ID,
                                                new String[]{String.valueOf(authorId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's for the given Series.
     *
     * @param seriesId id of the Series
     *
     * @return list with book ids
     */
    public ArrayList<Long> getBookIdsBySeries(final long seriesId) {
        ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.BOOK_IDS_BY_SERIES_ID,
                                                new String[]{String.valueOf(seriesId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's for the given Publisher.
     *
     * @param publisher name of the Publisher
     *
     * @return list with book ids
     */
    public ArrayList<Long> getBookIdsByPublisher(@NonNull final String publisher) {
        ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.BOOK_IDS_BY_PUBLISHER,
                                                new String[]{publisher})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
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
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.AUTHORS_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Author(rowData.getLong(KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    /**
     * Get a list of the Series a book belongs to.
     *
     * @param bookId of the book
     *
     * @return list of Series
     */
    @NonNull
    public ArrayList<Series> getSeriesByBookId(final long bookId) {
        ArrayList<Series> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.SERIES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Series(rowData.getLong(KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    /**
     * Return a Cursor for the given {@link Book} id.
     * The caller can retrieve columns as needed.
     *
     * <strong>Note:</strong> the cursor will in fact be a {@link ExtCursor}
     * enforcing database table based column types.
     *
     * @param bookId to retrieve
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchBookById(final long bookId) {
        String sql = SqlAllBooks.withAllAuthorsAndSeries(TBL_BOOKS.dot(KEY_PK_ID) + "=?");
        ExtCursor cursor = (ExtCursor) sSyncedDb.rawQueryWithFactory(
                EXT_CURSOR_FACTORY, sql, new String[]{String.valueOf(bookId)}, null);
        // force the ExtCursor to retrieve the real column types.
        cursor.setDb(sSyncedDb, TBL_BOOKS);
        return cursor;
    }

    /**
     * Return a Cursor for the given list of {@link Book} ID's.
     * The caller can retrieve columns as needed.
     * <p>
     * Full Author and Series are added.
     *
     * @param bookIds           List of book ID's to update, {@code null} for all books.
     * @param fromBookIdOnwards the lowest book id to start from.
     *                          This allows to fetch a subset of the requested set.
     *                          Pass in 0 to get the full set.
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchBooks(@Nullable final List<Long> bookIds,
                             final long fromBookIdOnwards) {
        String whereClause;
        if (bookIds == null || bookIds.isEmpty()) {
            // all books starting from the given id
            whereClause = TBL_BOOKS.dot(KEY_PK_ID) + ">=" + fromBookIdOnwards;

        } else if (bookIds.size() == 1) {
            // single book
            whereClause = TBL_BOOKS.dot(KEY_PK_ID) + '=' + bookIds.get(0);

        } else {
            // the given books, starting from the given id
            whereClause = TBL_BOOKS.dot(KEY_PK_ID)
                          + " IN (" + TextUtils.join(",", bookIds) + ')'
                          + " AND (" + TBL_BOOKS.dot(KEY_PK_ID) + ">=" + fromBookIdOnwards + ')';
        }

        // the order by is used to be able to restart the update (using fromBookIdOnwards)
        String sql = SqlAllBooks.withAllAuthorsAndSeries(whereClause)
                     + " ORDER BY " + TBL_BOOKS.dot(KEY_PK_ID);

        return sSyncedDb.rawQuery(sql, null);
    }

    /**
     * Return an Cursor for the given ISBN.
     * <strong>Note:</strong> MAY RETURN MORE THAN ONE BOOK
     *
     * @param isbnList list of ISBN(s) to retrieve
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchBooksByIsbnList(@NonNull final List<String> isbnList) {
        if (isbnList.isEmpty()) {
            throw new IllegalArgumentException("isbnList was empty");
        }

        StringBuilder whereClause = new StringBuilder(TBL_BOOKS.dot(KEY_ISBN));
        if (isbnList.size() == 1) {
            // single book
            whereClause.append("='").append(encodeString(isbnList.get(0))).append('\'');
        } else {
            whereClause.append(" IN (")
                       .append(Csv.join(isbnList, element -> '\'' + encodeString(element) + '\''))
                       .append(')');
        }

        String sql = SqlAllBooks.withAllAuthorsAndSeries(whereClause.toString());
        return sSyncedDb.rawQuery(sql, null);
    }

    /**
     * Creates a new bookshelf in the database.
     *
     * @param bookshelf object to insert. Will be updated with the id.
     * @param styleId   the style this bookshelf uses by default
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    @SuppressWarnings("UnusedReturnValue")
    private long insertBookshelf(@NonNull final Bookshelf /* in/out */ bookshelf,
                                 final long styleId) {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlInsert.BOOKSHELF)) {
            stmt.bindString(1, bookshelf.getName());
            stmt.bindLong(2, styleId);
            long iId = stmt.executeInsert();
            if (iId > 0) {
                bookshelf.setId(iId);
            }
            return iId;
        }
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

        ContentValues cv = new ContentValues();
        cv.put(KEY_FK_BOOKSHELF, destId);

        int rowsAffected;
        SyncLock txLock = sSyncedDb.beginTransaction(true);
        try {
            rowsAffected = sSyncedDb.update(TBL_BOOK_BOOKSHELF.getName(), cv,
                                            KEY_FK_BOOKSHELF + "=?",
                                            new String[]{String.valueOf(sourceId)});
            // delete the now empty shelf.
            deleteBookshelf(sourceId);

            sSyncedDb.setTransactionSuccessful();
        } finally {
            sSyncedDb.endTransaction(txLock);
        }

        return rowsAffected;
    }

    /**
     * Delete the bookshelf with the given rowId.
     *
     * @param id of bookshelf to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteBookshelf(final long id) {

        int rowsAffected;

        SyncLock txLock = sSyncedDb.beginTransaction(true);
        try {
            purgeNodeStatesByBookshelf(id);

            try (SynchronizedStatement stmt = sSyncedDb
                    .compileStatement(SqlDelete.BOOKSHELF_BY_ID)) {
                stmt.bindLong(1, id);
                rowsAffected = stmt.executeUpdateDelete();
            }
            sSyncedDb.setTransactionSuccessful();
        } finally {
            sSyncedDb.endTransaction(txLock);
        }

        return rowsAffected;
    }

    /**
     * Return the bookshelf id. The incoming object is not modified.
     *
     * @param bookshelf bookshelf to search for
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getBookshelfId(@NonNull final Bookshelf bookshelf) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_GET_BOOKSHELF_ID_BY_NAME);
        if (stmt == null) {
            stmt = mSqlStatementManager
                    .add(STMT_GET_BOOKSHELF_ID_BY_NAME, SqlGet.BOOKSHELF_ID_BY_NAME);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, bookshelf.getName());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Search for a Bookshelf with the given name.
     *
     * @param name of bookshelf to find
     *
     * @return the Bookshelf, or {@code null} if not found
     */
    @Nullable
    public Bookshelf getBookshelfByName(@NonNull final String name) {

        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelect.BOOKSHELF_BY_NAME,
                                                new String[]{name})) {
            if (cursor.moveToFirst()) {
                final RowDataHolder rowData = new CursorRow(cursor);
                return new Bookshelf(rowData.getLong(KEY_PK_ID), rowData);
            } else {
                return null;
            }
        }
    }

    /**
     * Return the bookshelf based on the given id.
     *
     * <strong>Don't call directly; instead use a static method in {@link Bookshelf}</strong>.
     *
     * @param id of bookshelf to find
     *
     * @return the Bookshelf, or {@code null} if not found
     */
    @Nullable
    public Bookshelf getBookshelf(final long id) {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelect.BOOKSHELF_BY_ID,
                                                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Bookshelf(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    /**
     * Update a bookshelf.
     *
     * @param bookshelf to update
     * @param styleId   a valid style id, no checks are done in this method
     *
     * @return {@code true} for success.
     */
    public boolean updateBookshelf(@NonNull final Bookshelf bookshelf,
                                   final long styleId) {

        ContentValues cv = new ContentValues();
        cv.put(KEY_BOOKSHELF_NAME, bookshelf.getName());
        cv.put(KEY_FK_STYLE, styleId);

        return 0 < sSyncedDb.update(TBL_BOOKSHELF.getName(), cv,
                                    KEY_PK_ID + "=?",
                                    new String[]{String.valueOf(bookshelf.getId())});
    }

    /**
     * Add or update the passed Bookshelf, depending whether bookshelf.id == 0.
     *
     * @param context   Current context
     * @param bookshelf object to insert or update. Will be updated with the id.
     * @param styleId   style for this bookshelf
     *
     * @return {@code true} for success
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertBookshelf(@NonNull final Context context,
                                           @NonNull final /* in/out */ Bookshelf bookshelf,
                                           final long styleId) {
        if (bookshelf.getId() != 0) {
            return updateBookshelf(bookshelf, styleId);
        } else {
            // try to find first.
            if (bookshelf.fixId(context, this, LocaleUtils.getUserLocale(context)) == 0) {
                return insertBookshelf(bookshelf, styleId) > 0;
            }
        }
        return false;
    }

    /**
     * Convenience method, fetch all shelves, and return them as a List.
     *
     * <strong>Note:</strong> we do not include the 'All Books' shelf.
     *
     * @return a list of all bookshelves in the database.
     */
    @NonNull
    public ArrayList<Bookshelf> getBookshelves() {
        ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = fetchBookshelves()) {
            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    /**
     * Get all Bookshelves; mainly for the purpose of backups.
     * <strong>Note:</strong> we do not include the 'All Books' shelf.
     *
     * @return Cursor over all Bookshelves
     */
    @NonNull
    public Cursor fetchBookshelves() {
        String sql = SqlSelectFullTable.BOOKSHELVES
                     + _WHERE_ + TBL_BOOKSHELF.dot(KEY_PK_ID) + ">0"
                     + " ORDER BY " + KEY_BOOKSHELF_NAME + COLLATION;
        return sSyncedDb.rawQuery(sql, null);
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
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectList.BOOKSHELVES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(KEY_PK_ID), rowData));
            }
            return list;
        }
    }

    /**
     * Get a list of all user defined {@link BooklistStyle}, arranged in a lookup map.
     * <p>
     * This is a slow call, as it needs to create a new {@link BooklistStyle} for each row.
     *
     * @param context Current context
     *
     * @return ordered map, with the uuid as key
     */
    @NonNull
    public Map<String, BooklistStyle> getUserStyles(@NonNull final Context context) {
        Map<String, BooklistStyle> list = new LinkedHashMap<>();

        String sql = SqlSelectFullTable.BOOKLIST_STYLES
                     + _WHERE_ + KEY_STYLE_IS_BUILTIN + "=0"
                     // We order by the id, i.e. in the order the styles were created.
                     // This is only done to get a reproducible and consistent order.
                     + " ORDER BY " + KEY_PK_ID;

        try (Cursor cursor = sSyncedDb.rawQuery(sql, null)) {
            final RowDataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                long id = rowData.getLong(KEY_PK_ID);
                String uuid = rowData.getString(KEY_UUID);
                list.put(uuid, new BooklistStyle(context, id, uuid));
            }
        }
        return list;
    }

    /**
     * Get the id of a {@link BooklistStyle} with matching UUID.
     *
     * @param uuid UUID of the style to find
     *
     * @return id
     */
    public long getStyleIdByUuid(@NonNull final String uuid) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_GET_BOOKLIST_STYLE);
        if (stmt == null) {
            stmt = mSqlStatementManager
                    .add(STMT_GET_BOOKLIST_STYLE, SqlGet.BOOKLIST_STYLE_ID_BY_UUID);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Create a new {@link BooklistStyle}.
     *
     * @param style object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long insertStyle(@NonNull final BooklistStyle /* in/out */ style) {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlInsert.BOOKLIST_STYLE)) {
            stmt.bindString(1, style.getUuid());
            stmt.bindBoolean(2, !style.isUserDefined());
            long iId = stmt.executeInsert();
            if (iId > 0) {
                style.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Delete a {@link BooklistStyle}.
     *
     * @param id of style to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteStyle(final long id) {

        int rowsAffected;

        SyncLock txLock = sSyncedDb.beginTransaction(true);
        try {
            purgeNodeStatesByStyle(id);

            try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlDelete.STYLE_BY_ID)) {
                stmt.bindLong(1, id);
                rowsAffected = stmt.executeUpdateDelete();
            }
            sSyncedDb.setTransactionSuccessful();
        } finally {
            sSyncedDb.endTransaction(txLock);
        }

        return rowsAffected;
    }

    /**
     * Returns a unique list of all currencies in the database for the specified domain.
     *
     * @param domainName for which to collect the used currency codes
     *
     * @return The list; values are always in uppercase.
     */
    @NonNull
    public ArrayList<String> getCurrencyCodes(@NonNull final String domainName) {
        String column;
        if (DBDefinitions.KEY_PRICE_LISTED_CURRENCY.equals(domainName)) {
            column = DBDefinitions.KEY_PRICE_LISTED_CURRENCY;
//        } else if (DBDefinitions.KEY_PRICE_PAID_CURRENCY.equals(type)) {
        } else {
            column = DBDefinitions.KEY_PRICE_PAID_CURRENCY;
        }

        String sql = "SELECT DISTINCT upper(" + column + ") FROM " + TBL_BOOKS.getName()
                     + " ORDER BY " + column + COLLATION;

        try (Cursor cursor = sSyncedDb.rawQuery(sql, null)) {
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
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.FORMATS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    public void updateFormat(@NonNull final String from,
                             @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlUpdate.FORMAT)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Returns a unique list of all colors in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getColors() {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.COLORS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    public void updateColor(@NonNull final String from,
                            @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlUpdate.COLOR)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Returns a unique list of all genres in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getGenres() {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.GENRES, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    public void updateGenre(@NonNull final String from,
                            @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlUpdate.GENRE)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Returns a unique list of all languages in the database.
     * The list is ordered by {@link DBDefinitions#KEY_DATE_LAST_UPDATED}.
     *
     * @return The list; normally all ISO codes
     */
    @NonNull
    public ArrayList<String> getLanguageCodes() {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.LANGUAGES, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    public void updateLanguage(@NonNull final String from,
                               @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlUpdate.LANGUAGE)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Returns a unique list of all loanee in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getLoanees() {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.LOANEE, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Get the name of the loanee for a given book, if any.
     *
     * @param bookId book to search for
     *
     * @return Who the book is lend to, or {@code null} when not lend out
     */
    @Nullable
    public String getLoaneeByBookId(final long bookId) {
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_GET_LOANEE_BY_BOOK_ID);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_GET_LOANEE_BY_BOOK_ID, SqlGet.LOANEE_BY_BOOK_ID);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    /**
     * Lend out a book / return a book.
     *
     * @param bookId book to lend
     * @param loanee person to lend to; set to {@code null} to delete the loan
     *
     * @return {@code true} for success.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean lendBook(final long bookId,
                            @Nullable final String loanee) {
        if (loanee == null || loanee.isEmpty()) {
            try (SynchronizedStatement stmt = sSyncedDb.compileStatement(
                    SqlDelete.BOOK_LOANEE_BY_BOOK_ID)) {
                stmt.bindLong(1, bookId);
                return stmt.executeUpdateDelete() == 1;
            }
        }

        final String current = getLoaneeByBookId(bookId);
        if (current == null || current.isEmpty()) {
            try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlInsert.BOOK_LOANEE)) {
                stmt.bindLong(1, bookId);
                stmt.bindString(2, loanee);
                return stmt.executeInsert() > 0;
            }

        } else if (!loanee.equals(current)) {
            final ContentValues cv = new ContentValues();
            cv.put(KEY_LOANEE, loanee);
            return 0 < sSyncedDb.update(TBL_BOOK_LOANEE.getName(), cv,
                                        KEY_FK_BOOK + "=?",
                                        new String[]{String.valueOf(bookId)});
        }

        return true;
    }

    /**
     * Get a unique list of all locations in the database.
     *
     * @return list
     */
    @NonNull
    public ArrayList<String> getLocations() {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.LOCATIONS, null)) {
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
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlUpdate.LOCATION)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Returns a unique list of all publishers in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getPublisherNames() {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.PUBLISHERS, null)) {
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
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlUpdate.PUBLISHER)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Creates a new Series in the database.
     *
     * @param context    Current context
     * @param series     object to insert. Will be updated with the id.
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    private long insertSeries(@NonNull final Context context,
                              @NonNull final Series /* in/out */ series,
                              @NonNull final Locale bookLocale) {

        Locale seriesLocale = series.getLocale(context, this, bookLocale);

        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_INSERT_SERIES);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_INSERT_SERIES, SqlInsert.SERIES);
        }

        String obTitle = series.reorderTitleForSorting(context, seriesLocale);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, series.getTitle());
            stmt.bindString(2, encodeOrderByColumn(obTitle, seriesLocale));
            stmt.bindBoolean(3, series.isComplete());
            long iId = stmt.executeInsert();
            if (iId > 0) {
                series.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Update a Series.
     *
     * @param context    Current context
     * @param series     to update
     * @param bookLocale Locale to use if the item has none set
     *
     * @return {@code true} for success.
     */
    public boolean updateSeries(@NonNull final Context context,
                                @NonNull final Series series,
                                @NonNull final Locale bookLocale) {

        Locale seriesLocale = series.getLocale(context, this, bookLocale);

        String obTitle = series.reorderTitleForSorting(context, seriesLocale);

        ContentValues cv = new ContentValues();
        cv.put(KEY_SERIES_TITLE, series.getTitle());
        cv.put(KEY_SERIES_TITLE_OB, encodeOrderByColumn(obTitle, seriesLocale));
        cv.put(KEY_SERIES_IS_COMPLETE, series.isComplete());

        return 0 < sSyncedDb.update(TBL_SERIES.getName(), cv,
                                    KEY_PK_ID + "=?",
                                    new String[]{String.valueOf(series.getId())});
    }

    /**
     * Add or update the passed Series, depending whether series.id == 0.
     *
     * @param context    Current context
     * @param bookLocale Locale to use if the item has none set
     * @param series     object to insert or update. Will be updated with the id.
     *
     * @return {@code true} for success.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean updateOrInsertSeries(@NonNull final Context context,
                                        @NonNull final Locale bookLocale,
                                        @NonNull final /* in/out */ Series series) {

        if (series.getId() != 0) {
            return updateSeries(context, series, bookLocale);
        } else {
            // try to find first.
            if (series.fixId(context, this, bookLocale) == 0) {
                return insertSeries(context, series, bookLocale) > 0;
            } else {
                return updateSeries(context, series, bookLocale);
            }
        }
    }

    /**
     * Delete the passed series.
     *
     * @param context Current context
     * @param id      series to delete
     *
     * @return the number of rows affected
     */
    @SuppressWarnings("UnusedReturnValue")
    public int deleteSeries(@NonNull final Context context,
                            final long id) {

        // delete the Series itself, this will cascade and delete the links to the books as well.
        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_DELETE_SERIES);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_DELETE_SERIES, SqlDelete.SERIES_BY_ID);
        }

        int rowsAffected;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, id);
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            // look for books affected by the delete, and re-position their book/series links.
            new DBCleaner(this).bookSeries(context);
        }
        return rowsAffected;
    }

    /**
     * Get all series; mainly for the purpose of backups.
     *
     * @return Cursor over all series
     */
    @NonNull
    public Cursor fetchSeries() {
        return sSyncedDb.rawQuery(SqlSelectFullTable.SERIES, null);
    }

    /**
     * Return the Series based on the ID.
     *
     * @param id series to get
     *
     * @return the Series, or {@code null} if not found
     */
    @Nullable
    public Series getSeries(final long id) {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelect.SERIES_BY_ID,
                                                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                final RowDataHolder rowData = new CursorRow(cursor);
                return new Series(id, rowData);
            } else {
                return null;
            }
        }
    }

    /**
     * Get the language (ISO3) code for a Series.
     * This is defined as the language code for the first book in the Series.
     *
     * @param id series to get
     *
     * @return the ISO3 code, or the empty String when none found.
     */
    @NonNull
    public String getSeriesLanguage(final long id) {
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelect.SERIES_LANGUAGE,
                                                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return "";
        }
    }

    /**
     * Find a Series, and return its ID. The incoming object is not modified.
     *
     * @param context    Current context
     * @param series     to find
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getSeriesId(@NonNull final Context context,
                            @NonNull final Series series,
                            @NonNull final Locale bookLocale) {

        Locale seriesLocale = series.getLocale(context, this, bookLocale);

        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_GET_SERIES_ID);
        if (stmt == null) {
            stmt = mSqlStatementManager.add(STMT_GET_SERIES_ID, SqlGet.SERIES_ID_BY_NAME);
        }

        String obTitle = series.reorderTitleForSorting(context, seriesLocale);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, encodeOrderByColumn(series.getTitle(), seriesLocale));
            stmt.bindString(2, encodeOrderByColumn(obTitle, seriesLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Refresh the passed Series from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the Series.
     * <p>
     * Will NOT insert a new Series if not found.
     *
     * @param context    Current context
     * @param series     to refresh
     * @param bookLocale Locale to use if the item has none set
     */
    public void refreshSeries(@NonNull final Context context,
                              @NonNull final Series /* out */ series,
                              @NonNull final Locale bookLocale) {

        if (series.getId() == 0) {
            // It wasn't saved before; see if it is now. If so, update ID.
            series.fixId(context, this, bookLocale);

        } else {
            // It was saved, see if it still is and fetch possibly updated fields.
            Series dbSeries = getSeries(series.getId());
            if (dbSeries != null) {
                // copy any updated fields
                series.copyFrom(dbSeries, false);
            } else {
                // not found?, set as 'new'
                series.setId(0);
            }
        }
    }

    /**
     * Count all books.
     *
     * @return number of books
     */
    public long countBooks() {
        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(SqlSelect.COUNT_BOOKS)) {
            return stmt.count();
        }
    }

    /**
     * Count the books for the given Series.
     *
     * @param context    Current context
     * @param series     to count the books in
     * @param bookLocale Locale to use if the item has none set
     *
     * @return number of books in series
     */
    public long countBooksInSeries(@NonNull final Context context,
                                   @NonNull final Series series,
                                   @NonNull final Locale bookLocale) {
        if (series.getId() == 0 && series.fixId(context, this, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = sSyncedDb.compileStatement(
                SqlSelect.COUNT_BOOKS_IN_SERIES)) {
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
    public ArrayList<String> getSeriesTitles() {
        return getColumnAsList(SqlSelectFullTable.SERIES_NAME, KEY_SERIES_TITLE);
    }

    /**
     * Set the Goodreads book id for this book.
     *
     * @param bookId          the/our book id
     * @param goodreadsBookId the Goodreads native book id
     */
    public void setGoodreadsBookId(final long bookId,
                                   final long goodreadsBookId) {

        SynchronizedStatement stmt = mSqlStatementManager.get(STMT_UPDATE_GOODREADS_BOOK_ID);
        if (stmt == null) {
            stmt = mSqlStatementManager
                    .add(STMT_UPDATE_GOODREADS_BOOK_ID, SqlUpdate.GOODREADS_BOOK_ID);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, goodreadsBookId);
            stmt.bindLong(2, bookId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Return an {@link ExtCursor} for the given Goodreads book Id.
     * <strong>Note:</strong> MAY RETURN MORE THAN ONE BOOK
     *
     * @param grBookId to retrieve
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchBooksByGoodreadsBookId(final long grBookId) {
        String sql = SqlAllBooks.withAllAuthorsAndSeries(
                TBL_BOOKS.dot(KEY_EID_GOODREADS_BOOK) + "=?");
        return sSyncedDb.rawQuery(sql, new String[]{String.valueOf(grBookId)});
    }

    /**
     * Query to get relevant {@link Book} columns for sending a set of books to Goodreads.
     *
     * @param startId     the 'first' (e.g. 'oldest') bookId to get
     *                    since the last sync with Goodreads
     * @param updatesOnly true, if we only want the updated records
     *                    since the last sync with Goodreads
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchBooksForExportToGoodreads(final long startId,
                                                 final boolean updatesOnly) {
        String sql = SqlSelectFullTable.GOODREADS_BOOK_DATA_TO_SEND
                     + _WHERE_ + KEY_PK_ID + ">?";

        if (updatesOnly) {
            sql += " AND " + KEY_DATE_LAST_UPDATED + '>' + KEY_BOOK_GOODREADS_LAST_SYNC_DATE;
        }

        // the order by is used to be able to restart an export.
        sql += " ORDER BY " + KEY_PK_ID;

        return sSyncedDb.rawQuery(sql, new String[]{String.valueOf(startId)});
    }

    /**
     * Query to get the ISBN for the given {@link Book} id, for sending to Goodreads.
     *
     * @param bookId to retrieve
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchBookForExportToGoodreads(final long bookId) {
        return sSyncedDb.rawQuery(SqlSelect.GOODREADS_GET_BOOK_TO_SEND_BY_BOOK_ID,
                                  new String[]{String.valueOf(bookId)});
    }

    /**
     * Fills an array with the first column (index==0, type==long) from the passed SQL.
     *
     * @param sql SQL to execute
     *
     * @return List of *all* values
     *
     * @see #getFirstColumnAsList
     */
    @NonNull
    ArrayList<Long> getIdList(@NonNull final String sql) {
        ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = sSyncedDb.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
            return list;
        }
    }

    /**
     * Fills an array with the specified (String) column from the passed SQL.
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
        try (Cursor cursor = sSyncedDb.rawQuery(sql, null)) {
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
        // Using a Set to avoid duplicates
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
     * <ul>
     *      <li>Exclude the primary key from the list of columns.</li>
     *      <li>data will be transformed based on the intended type of the underlying column
     * based on column definition (based on actual storage class of SQLite)
     * e.g. if a columns says it's Integer, an incoming boolean will be transformed to 0/1</li>
     * </ul>
     *
     * @param tableDefinition destination table
     * @param dataManager     A collection with the columns to be set. May contain extra data.
     * @param bookLocale      the Locale to use for character case manipulation
     *
     * @return New and filtered ContentValues
     *
     * @throws IllegalArgumentException on any failure.
     */
    @NonNull
    private ContentValues filterValues(@SuppressWarnings("SameParameterValue")
                                       @NonNull final TableDefinition tableDefinition,
                                       @NonNull final DataManager dataManager,
                                       @NonNull final Locale bookLocale)
            throws IllegalArgumentException {

        TableInfo tableInfo = tableDefinition.getTableInfo(sSyncedDb);

        ContentValues cv = new ContentValues();
        // Create the arguments
        for (String key : dataManager.keySet()) {
            // Get column info for this column.
            ColumnInfo columnInfo = tableInfo.getColumn(key);
            // Check if we actually have a matching column, and never update a PK.
            if (columnInfo != null && !columnInfo.isPrimaryKey()) {
                Object entry = dataManager.get(key);
                if (entry == null) {
                    if (columnInfo.isNullable()) {
                        cv.putNull(key);
                    } else {
                        throw new IllegalArgumentException(
                                "NULL on a non-nullable column|key=" + key);
                    }
                } else {
                    switch (columnInfo.storageClass) {
                        case Real: {
                            if (entry instanceof Number) {
                                cv.put(columnInfo.name, ((Number) entry).doubleValue());
                            } else {
                                // Theoretically we should only get here during an import,
                                // where everything is handled as a String.
                                String stringValue = entry.toString().trim();
                                if (!stringValue.isEmpty()) {
                                    // Sqlite does not care about float/double,
                                    // Using double covers float as well.
                                    //Reminder: do NOT use the bookLocale to parse.
                                    cv.put(columnInfo.name, Double.parseDouble(stringValue));
                                } else {
                                    cv.put(columnInfo.name, "");
                                }
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
                            } else if (entry instanceof Long) {
                                cv.put(columnInfo.name, (Long) entry);
                            } else {
                                // Theoretically we should only get here during an import,
                                // where everything is handled as a String.
                                String s = entry.toString().toLowerCase(bookLocale);
                                if (!s.isEmpty()) {
                                    // It's not strictly needed to do these conversions.
                                    // parseInt/catch(Exception) works,
                                    // but it's not elegant...
                                    switch (s) {
                                        case "1":
                                        case "true":
                                        case "t":
                                        case "yes":
                                        case "y":
                                            cv.put(columnInfo.name, 1);
                                            break;

                                        case "0":
                                        case "0.0":
                                        case "false":
                                        case "f":
                                        case "no":
                                        case "n":
                                            cv.put(columnInfo.name, 0);
                                            break;

                                        default:
                                            //Reminder: do NOT use the bookLocale to parse.
                                            cv.put(columnInfo.name, Integer.parseInt(s));
                                    }

                                } else {
                                    // s.isEmpty
                                    cv.put(columnInfo.name, "");
                                }
                            }
                            break;
                        }
                        case Text: {
                            if (entry instanceof String) {
                                cv.put(columnInfo.name, (String) entry);
                            } else {
                                cv.put(columnInfo.name, entry.toString());
                            }
                            break;
                        }
                        case Blob: {
                            if (entry instanceof byte[]) {
                                cv.put(columnInfo.name, (byte[]) entry);
                            } else {
                                throw new IllegalArgumentException(
                                        "non-null Blob but not a byte[] ?"
                                        + "|column.name=" + columnInfo.name
                                        + "|key=" + key);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return cv;
    }

    /**
     * Return a {@link Cursor}, suited for a local-search.
     * This is used by the standard system search dialog.
     *
     * @param keywords Keywords to find anywhere in book; this includes titles and authors
     *
     * @return a cursor, or {@code null} if all input was empty
     */
    @Nullable
    Cursor fetchSearchSuggestions(@NonNull final String keywords) {
        // non-FTS
        // String q = '%' + toAscii(query) + '%';
        // return sSyncedDb.rawQuery(SqlSelect.SEARCH_SUGGESTIONS,
        //                           new String[]{q, q, q, q, q, q, q});

        String query = SqlFTS.cleanupFtsCriterion(keywords, null);
        // do we have anything to search for?
        if (query.isEmpty()) {
            return null;
        }
        return sSyncedDb.rawQuery(SqlFTS.SEARCH_SUGGESTIONS, new String[]{query});
    }

    /**
     * Return a {@link Cursor}, suited for a local-search.
     * This is used by the advanced search activity.
     *
     * @param author      Author related keywords to find
     * @param title       Title related keywords to find
     * @param seriesTitle Series title related keywords to find
     * @param keywords    Keywords to find anywhere in book; this includes titles and authors
     * @param limit       maximum number of rows to return
     *
     * @return a cursor, or {@code null} if all input was empty
     */
    @Nullable
    public Cursor fetchSearchSuggestionsAdv(@Nullable final String author,
                                            @Nullable final String title,
                                            @Nullable final String seriesTitle,
                                            @Nullable final String keywords,
                                            final int limit) {

        String query = SqlFTS.createMatchString(author, title, seriesTitle, keywords);
        // do we have anything to search for?
        if (query.isEmpty()) {
            return null;
        }
        return sSyncedDb.rawQuery(SqlFTS.SEARCH, new String[]{query, String.valueOf(limit)});
    }

    /**
     * Bind a string or {@code null} value to a parameter since binding a {@code null}
     * in bindString produces an error.
     * <p>
     * <strong>Note:</strong> We specifically want to use the default Locale for this.
     */
    private void bindStringOrNull(@NonNull final SynchronizedStatement stmt,
                                  final int position,
                                  @Nullable final CharSequence text) {
        if (text == null) {
            stmt.bindNull(position);
        } else {
            stmt.bindString(position, toAscii(text));
        }
    }

    /**
     * Send the book details from the cursor to the passed fts query.
     * <p>
     * <strong>Note:</strong> This assumes a specific order for query parameters.
     * If modified, then update {@link SqlFTS#INSERT_BODY}, {@link SqlFTS#UPDATE}
     *
     * @param cursor Cursor of books to update
     * @param stmt   Statement to execute (insert or update)
     */
    private void ftsSendBooks(@NonNull final Cursor cursor,
                              @NonNull final SynchronizedStatement stmt) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_REQUIRES_TRANSACTION);
        }

        // Accumulator for author names for each book
        StringBuilder authorText = new StringBuilder();
        // Accumulator for series titles for each book
        StringBuilder seriesText = new StringBuilder();
        // Accumulator for TOCEntry titles for each book
        StringBuilder tocTitles = new StringBuilder();

        // Indexes of fields in the inner-loop cursors, -2 for 'not initialised yet'
        int colGivenNames = -2;
        int colFamilyName = -2;
        int colSeriesInfo = -2;
        int colTOCEntryInfo = -2;

        final RowDataHolder rowData = new CursorRow(cursor);
        // Process each book
        while (cursor.moveToNext()) {
            authorText.setLength(0);
            seriesText.setLength(0);
            tocTitles.setLength(0);

            // Get list of authors
            try (Cursor authors = sSyncedDb.rawQuery(
                    SqlFTS.GET_AUTHORS_BY_BOOK_ID,
                    new String[]{String.valueOf(rowData.getLong(KEY_PK_ID))})) {
                // Get column indexes, if not already got
                if (colGivenNames < 0) {
                    colGivenNames = authors.getColumnIndex(KEY_AUTHOR_GIVEN_NAMES);
                }
                if (colFamilyName < 0) {
                    colFamilyName = authors.getColumnIndex(KEY_AUTHOR_FAMILY_NAME);
                }
                // Append each author
                while (authors.moveToNext()) {
                    authorText.append(authors.getString(colGivenNames)).append(' ');
                    authorText.append(authors.getString(colFamilyName)).append(';');
                }
            }

            // Get list of series
            try (Cursor series = sSyncedDb.rawQuery(
                    SqlFTS.GET_SERIES_BY_BOOK_ID,
                    new String[]{String.valueOf(rowData.getLong(KEY_PK_ID))})) {
                // Get column indexes, if not already got
                if (colSeriesInfo < 0) {
                    colSeriesInfo = series.getColumnIndexOrThrow(KEY_SERIES_TITLE);
                }
                // Append each series
                while (series.moveToNext()) {
                    seriesText.append(series.getString(colSeriesInfo)).append(';');
                }
            }

            // Get list of TOC titles
            try (Cursor tocEntries = sSyncedDb.rawQuery(
                    SqlFTS.GET_TOC_TITLES_BY_BOOK_ID,
                    new String[]{String.valueOf(rowData.getLong(KEY_PK_ID))})) {
                // Get column indexes, if not already got
                if (colTOCEntryInfo < 0) {
                    colTOCEntryInfo = tocEntries.getColumnIndexOrThrow(KEY_TITLE);
                }
                while (tocEntries.moveToNext()) {
                    tocTitles.append(tocEntries.getString(colTOCEntryInfo)).append(';');
                }
            }

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                bindStringOrNull(stmt, 1, rowData.getString(KEY_TITLE));
                // DOM_FTS_AUTHOR_NAME
                bindStringOrNull(stmt, 2, authorText.toString());
                // DOM_SERIES_TITLE
                bindStringOrNull(stmt, 3, seriesText.toString());
                bindStringOrNull(stmt, 4, rowData.getString(KEY_DESCRIPTION));
                bindStringOrNull(stmt, 5, rowData.getString(KEY_PRIVATE_NOTES));
                bindStringOrNull(stmt, 6, rowData.getString(KEY_PUBLISHER));
                bindStringOrNull(stmt, 7, rowData.getString(KEY_GENRE));
                bindStringOrNull(stmt, 8, rowData.getString(KEY_LOCATION));
                bindStringOrNull(stmt, 9, rowData.getString(KEY_ISBN));
                // DOM_FTS_TOC_ENTRY_TITLE
                bindStringOrNull(stmt, 10, tocTitles.toString());

                // DOM_FTS_BOOKS_PK; either as insert param, or as where clause param
                stmt.bindLong(11, rowData.getLong(KEY_PK_ID));

                stmt.execute();
            }
        }
    }

    /**
     * Insert a book into the FTS. Assumes book does not already exist in FTS.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context Current context
     * @param bookId  the book to add to FTS
     */
    private void ftsInsert(@NonNull final Context context,
                           final long bookId) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException(ERROR_REQUIRES_TRANSACTION);
        }

        try {
            SynchronizedStatement stmt = mSqlStatementManager.get(STMT_INSERT_FTS);
            if (stmt == null) {
                stmt = mSqlStatementManager.add(STMT_INSERT_FTS, SqlFTS.INSERT);
            }

            try (Cursor cursor = sSyncedDb
                    .rawQuery(SqlSelect.BOOK_BY_ID, new String[]{String.valueOf(bookId)})) {
                ftsSendBooks(cursor, stmt);
            }
        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(context, TAG, e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    /**
     * Update an existing FTS record.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param context Application context
     * @param bookId  the book id
     */
    private void ftsUpdate(@NonNull final Context context,
                           final long bookId) {

        if (!sSyncedDb.inTransaction()) {
            throw new TransactionException();
        }

        try {
            SynchronizedStatement stmt = mSqlStatementManager.get(STMT_UPDATE_FTS);
            if (stmt == null) {
                stmt = mSqlStatementManager.add(STMT_UPDATE_FTS, SqlFTS.UPDATE);
            }
            try (Cursor cursor = sSyncedDb.rawQuery(SqlSelect.BOOK_BY_ID,
                                                    new String[]{String.valueOf(bookId)})) {
                ftsSendBooks(cursor, stmt);
            }
        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(context, TAG, e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    /**
     * Rebuild the entire FTS database.
     * This can take several seconds with many books or a slow device.
     */
    public void ftsRebuild() {
        if (sSyncedDb.inTransaction()) {
            throw new TransactionException();
        }

        long t0 = 0;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            t0 = System.nanoTime();
        }
        boolean gotError = false;

        // Make a copy of the FTS table definition for our temporary table.
        TableDefinition ftsTemp = new TableDefinition(TBL_FTS_BOOKS);
        // Give it a new name
        ftsTemp.setName(ftsTemp.getName() + "_temp");

        SyncLock txLock = sSyncedDb.beginTransaction(true);

        try {
            //IMPORTANT: withConstraints MUST BE false
            ftsTemp.recreate(sSyncedDb, false);

            try (SynchronizedStatement insert = sSyncedDb.compileStatement(
                    "INSERT INTO " + ftsTemp.getName() + SqlFTS.INSERT_BODY);
                 Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.BOOKS, null)) {
                ftsSendBooks(cursor, insert);
            }

            sSyncedDb.setTransactionSuccessful();
        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(App.getAppContext(), TAG, e);
            gotError = true;
        } finally {
            sSyncedDb.endTransaction(txLock);
            /*
            http://sqlite.1065341.n5.nabble.com/Bug-in-FTS3-when-trying-to-rename-table-within-a-transaction-td11430.html
            FTS tables should only be renamed outside of transactions.
            */
            //  Delete old table and rename the new table
            if (!gotError) {
                // Drop old table, ready for rename
                sSyncedDb.drop(TBL_FTS_BOOKS.getName());
                sSyncedDb.execSQL("ALTER TABLE " + ftsTemp + " RENAME TO " + TBL_FTS_BOOKS);
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
            Log.d(TAG, mInstanceName
                       + "|rebuildFts"
                       + "|completed in " + (System.nanoTime() - t0) / NANO_TO_MILLIS + " ms");
        }
    }

    /**
     * Repopulate all OrderBy TITLE columns.
     * Cleans up whitespace and non-ascii characters.
     * Optional reordering.
     *
     * <p>
     * Book:     KEY_TITLE  ==> KEY_TITLE_OB
     * TOCEntry: KEY_TITLE  ==> KEY_TITLE_OB
     * Series:   KEY_SERIES_TITLE => KEY_SERIES_TITLE_OB
     *
     * @param context Current context
     * @param reorder flag whether to reorder or not
     */
    public void rebuildOrderByTitleColumns(@NonNull final Context context,
                                           final boolean reorder) {

        // Books
        String language;
        Locale bookLocale;
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.BOOK_TITLES, null)) {
            int langIdx = cursor.getColumnIndex(KEY_LANGUAGE);
            while (cursor.moveToNext()) {
                language = cursor.getString(langIdx);
                bookLocale = LocaleUtils.getLocale(context, language);
                if (bookLocale == null) {
                    bookLocale = LocaleUtils.getUserLocale(context);
                }
                rebuildOrderByTitleColumns(context, bookLocale, reorder, cursor,
                                           TBL_BOOKS, KEY_TITLE_OB);
            }
        }

        // Series and TOC Entries use the user Locale.
        Locale userLocale = LocaleUtils.getUserLocale(context);

        // We should use the locale from the 1st book in the series... but that is a huge overhead.
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.SERIES_TITLES, null)) {
            while (cursor.moveToNext()) {
                rebuildOrderByTitleColumns(context, userLocale, reorder, cursor,
                                           TBL_SERIES, KEY_SERIES_TITLE_OB);
            }
        }

        // We should use primary book or Author Locale... but that is a huge overhead.
        try (Cursor cursor = sSyncedDb.rawQuery(SqlSelectFullTable.TOC_ENTRY_TITLES, null)) {
            while (cursor.moveToNext()) {
                rebuildOrderByTitleColumns(context, userLocale, reorder, cursor,
                                           TBL_TOC_ENTRIES, KEY_TITLE_OB);
            }
        }
    }

    /**
     * Process a <strong>single row</strong> from the cursor.
     *
     * @param context    Current context
     * @param locale     to use for this row
     * @param reorder    flag whether to reorder or not
     * @param cursor     positioned on the row to handle
     * @param table      to update
     * @param domainName to update
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean rebuildOrderByTitleColumns(@NonNull final Context context,
                                               @NonNull final Locale locale,
                                               final boolean reorder,
                                               @NonNull final Cursor cursor,
                                               @NonNull final TableDefinition table,
                                               @NonNull final String domainName) {

        final long id = cursor.getLong(0);
        final String title = cursor.getString(1);
        final String currentObTitle = cursor.getString(2);

        String rebuildObTitle;
        if (reorder) {
            rebuildObTitle = ItemWithTitle.reorder(context, title, locale);
        } else {
            rebuildObTitle = currentObTitle;
        }

        // only update the database if actually needed.
        if (!currentObTitle.equals(rebuildObTitle)) {
            ContentValues cv = new ContentValues();
            cv.put(domainName, encodeOrderByColumn(rebuildObTitle, locale));
            return 0 < sSyncedDb.update(table.getName(), cv, KEY_PK_ID + "=?",
                                        new String[]{String.valueOf(id)});
        }

        return true;
    }

    public void rebuildIndices() {
        sDbHelper.recreateIndices(sSyncedDb);
    }

    public void rebuildTriggers() {
        sDbHelper.createTriggers(sSyncedDb);
    }

    public void analyze() {
        sSyncedDb.analyze();
    }

    public void optimize() {
        sSyncedDb.optimize();
    }

    public static class DaoWriteException
            extends Exception {

        private static final long serialVersionUID = -2857466683799399619L;

        DaoWriteException(@NonNull final String message) {
            super(message);
        }

        DaoWriteException(@NonNull final String message,
                          @NonNull final Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Construct the SQL to get a list of books.
     * <ol>
     *      <li>{@link #withAllAuthorsAndSeries}: Books linked with Authors and Series</li>
     *      <li>{@link #withPrimaryAuthorAndSeries}: Books with a primary Author and<br>
     *          (if they have one) primary Series</li>
     * </ol>
     * <p>
     * We use ORDER BY position LIMIT 1, instead of "AND position=1",
     * to cover the case there is a gap in the sequence.
     */
    private static class SqlAllBooks {

        /** Virtual columns: "primary Author id" (, "total authors count" is disabled). */
        private static final String PRIM_AUTHOR =
                "(SELECT " + KEY_FK_AUTHOR + _FROM_ + TBL_BOOK_AUTHOR.ref()
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_BOOK) + '=' + TBL_BOOKS.dot(KEY_PK_ID)
                + " ORDER BY " + KEY_BOOK_AUTHOR_POSITION + ',' + TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR)
                + " LIMIT 1"
                + ") AS " + KEY_FK_AUTHOR;

//                // Get the total author count.
//                // No longer needed, but leaving for future use.
//                + ','
//                + "(SELECT COUNT(*) FROM " + TBL_BOOK_AUTHOR.ref()
//                + " WHERE " + TBL_BOOK_AUTHOR.dot(KEY_FK_BOOK) + '=' + TBL_BOOKS.dot(KEY_PK_ID)
//                + ')' + " AS " + COLUMN_ALIAS_NR_OF_AUTHORS;

        /**
         * Virtual columns: "primary Series id", "number", "total series count".
         * We use ORDER BY position LIMIT 1, instead of "AND position=1",
         * to cover the case there is a gap in the sequence.
         */
        private static final String PRIM_SERIES =
                // Find the first (i.e. primary) Series id.
                "(SELECT " + KEY_FK_SERIES + _FROM_ + TBL_BOOK_SERIES.ref()
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_BOOK) + '=' + TBL_BOOKS.dot(KEY_PK_ID)
                + " ORDER BY " + KEY_BOOK_SERIES_POSITION + " LIMIT 1)"
                + " AS " + KEY_FK_SERIES

                // Find the Series number for the first (i.e. primary) Series
                + ','
                + "(SELECT " + KEY_BOOK_NUM_IN_SERIES + _FROM_ + TBL_BOOK_SERIES.ref()
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_BOOK) + '=' + TBL_BOOKS.dot(KEY_PK_ID)
                + " ORDER BY " + KEY_BOOK_SERIES_POSITION + " LIMIT 1"
                + ") AS " + KEY_BOOK_NUM_IN_SERIES

                // Get the total series count.
                + ','
                + "(SELECT COUNT(*) FROM " + TBL_BOOK_SERIES.ref()
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_BOOK) + '=' + TBL_BOOKS.dot(KEY_PK_ID)
                + ')' + " AS " + COLUMN_ALIAS_NR_OF_SERIES;

        /**
         * set of fields suitable for a select of a Book.
         * <p>
         * TEST: could we not just do books.* ?
         * <b>Developer:</b> adding fields ? Now is a good time to update {@link Book#duplicate}/
         */
        private static final String BOOK =
                TBL_BOOKS.dotAs(KEY_PK_ID)
                + ',' + TBL_BOOKS.dotAs(KEY_BOOK_UUID)
                + ',' + TBL_BOOKS.dotAs(KEY_TITLE)
                // publication data
                + ',' + TBL_BOOKS.dotAs(KEY_ISBN)
                + ',' + TBL_BOOKS.dotAs(KEY_PUBLISHER)
                + ',' + TBL_BOOKS.dotAs(KEY_TOC_BITMASK)
                + ',' + TBL_BOOKS.dotAs(KEY_DATE_PUBLISHED)
                + ',' + TBL_BOOKS.dotAs(KEY_PRINT_RUN)
                + ',' + TBL_BOOKS.dotAs(KEY_PRICE_LISTED)
                + ',' + TBL_BOOKS.dotAs(KEY_PRICE_LISTED_CURRENCY)
                + ',' + TBL_BOOKS.dotAs(KEY_DATE_FIRST_PUBLICATION)
                + ',' + TBL_BOOKS.dotAs(KEY_FORMAT)
                + ',' + TBL_BOOKS.dotAs(KEY_COLOR)
                + ',' + TBL_BOOKS.dotAs(KEY_GENRE)
                + ',' + TBL_BOOKS.dotAs(KEY_LANGUAGE)
                + ',' + TBL_BOOKS.dotAs(KEY_PAGES)
                // common blurb
                + ',' + TBL_BOOKS.dotAs(KEY_DESCRIPTION)

                // partially edition info, partially user-owned info.
                + ',' + TBL_BOOKS.dotAs(KEY_EDITION_BITMASK)
                // user data
                + ',' + TBL_BOOKS.dotAs(KEY_PRIVATE_NOTES)
                + ',' + TBL_BOOKS.dotAs(KEY_BOOK_CONDITION)
                + ',' + TBL_BOOKS.dotAs(KEY_BOOK_CONDITION_COVER)
                + ',' + TBL_BOOKS.dotAs(KEY_LOCATION)
                + ',' + TBL_BOOKS.dotAs(KEY_SIGNED)
                + ',' + TBL_BOOKS.dotAs(KEY_RATING)
                + ',' + TBL_BOOKS.dotAs(KEY_READ)
                + ',' + TBL_BOOKS.dotAs(KEY_READ_START)
                + ',' + TBL_BOOKS.dotAs(KEY_READ_END)
                + ',' + TBL_BOOKS.dotAs(KEY_DATE_ACQUIRED)
                + ',' + TBL_BOOKS.dotAs(KEY_PRICE_PAID)
                + ',' + TBL_BOOKS.dotAs(KEY_PRICE_PAID_CURRENCY)
                // added/updated
                + ',' + TBL_BOOKS.dotAs(KEY_DATE_ADDED)
                + ',' + TBL_BOOKS.dotAs(KEY_DATE_LAST_UPDATED)
                // external links
                //NEWTHINGS: add new site specific ID: add column
                + ',' + TBL_BOOKS.dotAs(KEY_EID_LIBRARY_THING)
                + ',' + TBL_BOOKS.dotAs(KEY_EID_STRIP_INFO_BE)
                + ',' + TBL_BOOKS.dotAs(KEY_EID_OPEN_LIBRARY)
                + ',' + TBL_BOOKS.dotAs(KEY_EID_ISFDB)
                + ',' + TBL_BOOKS.dotAs(KEY_EID_GOODREADS_BOOK)
                + ',' + TBL_BOOKS.dotAs(KEY_BOOK_GOODREADS_LAST_SYNC_DATE);


        /** The base SELECT to get the Book columns. */
        private static final String ALL_BOOKS =
                "SELECT DISTINCT " + BOOK
                + ',' + PRIM_SERIES
                + ',' + PRIM_AUTHOR

                // list of the BookShelf IDs the book sits on
                //+ ',' + SqlColumns.EXP_BOOKSHELF_ID_CSV + " AS " + KEY_BOOKSHELF_ID_CSV

                // use an empty loanee (i.e. don't use null's) if there is no loanee
                + ',' + "COALESCE(" + KEY_LOANEE + ", '') AS " + KEY_LOANEE

                + _FROM_ + TBL_BOOKS.ref()
                + " LEFT OUTER JOIN " + TBL_BOOK_LOANEE.ref()
                + " ON (" + TBL_BOOK_LOANEE.dot(KEY_FK_BOOK) + '=' + TBL_BOOKS.dot(KEY_PK_ID) + ')';


        /** Envelope SQL to add the primary Author/Series. */
        private static final String PREFIX =
                "SELECT b.*"
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_IS_COMPLETE)
                + ','
                + SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN
                + " AS " + KEY_AUTHOR_FORMATTED
                + ','
                + SqlColumns.EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY
                + " AS " + KEY_AUTHOR_FORMATTED_GIVEN_FIRST

                + ',' + "a." + KEY_FK_AUTHOR + " AS " + KEY_FK_AUTHOR

                // use a dummy series for books not in a series
                // (i.e. don't use null's)
                + ',' + "COALESCE(s." + KEY_FK_SERIES + ", 0) AS " + KEY_FK_SERIES
                + ',' + "COALESCE(s." + KEY_SERIES_TITLE + ", '') AS " + KEY_SERIES_TITLE
                + ',' + "COALESCE(s." + KEY_BOOK_NUM_IN_SERIES
                + ", '') AS " + KEY_BOOK_NUM_IN_SERIES;

        /** Join with Authors and Series. */
        private static final String SUFFIX =
                " JOIN ("
                + "SELECT " + KEY_FK_AUTHOR
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_IS_COMPLETE)
                + ','
                + SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN + " AS " + KEY_AUTHOR_FORMATTED
                + ',' + TBL_BOOK_AUTHOR.dotAs(KEY_FK_BOOK)

                + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + ") a ON a." + KEY_FK_BOOK + "=b." + KEY_PK_ID
                + " AND a." + KEY_FK_AUTHOR + "=b." + KEY_FK_AUTHOR

                + " LEFT OUTER JOIN ("
                + "SELECT " + KEY_FK_SERIES
                + ',' + TBL_SERIES.dotAs(KEY_SERIES_TITLE)
                + ',' + TBL_SERIES.dotAs(KEY_SERIES_IS_COMPLETE)
                + ',' + TBL_BOOK_SERIES.dotAs(KEY_BOOK_NUM_IN_SERIES)
                + ',' + TBL_BOOK_SERIES.dotAs(KEY_FK_BOOK)
                + ',' + SqlColumns.EXP_SERIES_WITH_NUMBER + " AS " + KEY_SERIES_FORMATTED

                + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + ") s ON s." + KEY_FK_BOOK + "=b." + KEY_PK_ID
                + " AND s." + KEY_FK_SERIES + "=b." + KEY_FK_SERIES
                + " AND s." + KEY_BOOK_NUM_IN_SERIES + "=b." + KEY_BOOK_NUM_IN_SERIES + COLLATION;

        /**
         * Construct the SQL for a list of books in the database based on the given where-clause.
         *
         * @param whereClause to add to books search criteria (without the keyword 'WHERE')
         *
         * @return A full piece of SQL to perform the search
         */
        @NonNull
        private static String withAllAuthorsAndSeries(@NonNull final String whereClause) {
            //TODO: RTL
            String andOthersText = " …";

            // "a." (TBL_AUTHOR), "b." (TBL_BOOKS), "s." (TBL_SERIES}
            // BUT... here they have double-use:
            // in SQL macros -> the tables.
            // in the sql -> the query/sub-query.
            //
            // so DO NOT replace them with table.dot() etc... !
            return PREFIX
                   + ",CASE"
                   + " WHEN " + COLUMN_ALIAS_NR_OF_SERIES + "<2"
                   + " THEN COALESCE(s." + KEY_SERIES_FORMATTED + ",'')"
                   + " ELSE " + KEY_SERIES_FORMATTED + " || ' " + andOthersText + '\''
                   + " END AS " + KEY_SERIES_FORMATTED

                   + " FROM (" + ALL_BOOKS
                   + (!whereClause.isEmpty() ? _WHERE_ + " (" + whereClause + ')' : "")
                   + " ORDER BY " + TBL_BOOKS.dot(KEY_TITLE_OB) + ' ' + COLLATION
                   + ") b" + SUFFIX;
        }

        /**
         * Construct the SQL for a list of books based on the given where-clause.
         * This is specifically constructed to export a set of Books.
         *
         * @param whereClause to add to books search criteria (without the keyword 'WHERE')
         *
         * @return A full piece of SQL to perform the search
         */
        @NonNull
        private static String withPrimaryAuthorAndSeries(@NonNull final String whereClause) {
            return ALL_BOOKS
                   + (!whereClause.isEmpty() ? _WHERE_ + " (" + whereClause + ')' : "")
                   + " ORDER BY " + TBL_BOOKS.dot(KEY_PK_ID);
        }
    }

    /**
     * Commonly used SQL table columns.
     */
    public static final class SqlColumns {

        /**
         * SQL column: SORT author names in 'lastgiven' form.
         * Uses the OB field.
         * Not used for display.
         */
        public static final String EXP_AUTHOR_SORT_LAST_FIRST =
                "CASE"
                + " WHEN " + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB) + "=''"
                + " THEN " + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)
                + " ELSE " + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB) + "||"
                + /*      */ TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB)
                + " END";

        /**
         * SQL column: SORT author names in 'givenlast' form.
         * Uses the OB field.
         * Not used for display.
         */
        public static final String EXP_AUTHOR_SORT_FIRST_LAST =
                "CASE"
                + " WHEN " + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB) + "=''"
                + " THEN " + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)
                + " ELSE " + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB) + "||"
                + /*      */ TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)
                + " END";

        /**
         * Single column, with the formatted name of the Author.
         * <p>
         * If no given name -> "FamilyName"
         * otherwise -> "FamilyName, GivenNames"
         */
        public static final String EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN =
                "CASE"
                + " WHEN " + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES) + "=''"
                + " THEN " + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME)
                + " ELSE " + (TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME)
                              + " || ', ' || " + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES))
                + " END";

        /**
         * Single column, with the formatted name of the Author.
         * <p>
         * If no given name -> "FamilyName"
         * otherwise -> "GivenNames FamilyName"
         */
        public static final String EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY =
                "CASE"
                + " WHEN " + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES) + "=''"
                + " THEN " + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME)
                + " ELSE " + (TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES)
                              + " || ' ' || " + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME))
                + " END";

        /**
         * SQL column: return 1 if the book is available, 0 if not.
         * {@link DBDefinitions#KEY_LOANEE_AS_BOOLEAN}
         */
        public static final String EXP_LOANEE_AS_BOOLEAN =
                "CASE"
                + " WHEN " + TBL_BOOK_LOANEE.dot(KEY_LOANEE) + " IS NULL THEN 1"
                + " ELSE 0"
                + " END";
        /**
         * SQL column: return "" if the book is available, "loanee name" if not.
         */
        public static final String EXP_BOOK_LOANEE_OR_EMPTY =
                "CASE"
                + " WHEN " + TBL_BOOK_LOANEE.dot(KEY_LOANEE) + " IS NULL THEN ''"
                + " ELSE " + TBL_BOOK_LOANEE.dot(KEY_LOANEE)
                + " END";

        /**
         * Expression for the domain {@link DBDefinitions#KEY_BOOKSHELF_ID_CSV}.
         * <p>
         * The order of the returned names will be arbitrary.
         * We could add an ORDER BY GROUP_CONCAT(... if we GROUP BY
         */
        public static final String EXP_BOOKSHELF_ID_CSV =
                "("
                + "SELECT GROUP_CONCAT(" + TBL_BOOKSHELF.dot(KEY_PK_ID) + ",', ')"
                + _FROM_ + TBL_BOOKSHELF.ref() + TBL_BOOKSHELF.join(TBL_BOOK_BOOKSHELF)
                + _WHERE_ + TBL_BOOKS.dot(KEY_PK_ID) + "=" + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOK)
                + ")";

        /**
         * Expression for the domain {@link DBDefinitions#DOM_BOOKSHELF_NAME_CSV}.
         * <p>
         * The order of the returned names will be arbitrary.
         * We could add an ORDER BY GROUP_CONCAT(... if we GROUP BY
         */
        public static final String EXP_BOOKSHELF_NAME_CSV =
                "("
                + "SELECT GROUP_CONCAT(" + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME) + ",', ')"
                + _FROM_ + TBL_BOOKSHELF.ref() + TBL_BOOKSHELF.join(TBL_BOOK_BOOKSHELF)
                + _WHERE_ + TBL_BOOKS.dot(KEY_PK_ID) + "=" + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOK)
                + ")";

        /**
         * Single column, with the formatted name of the Series.
         * <p>
         * If no number -> "SeriesName".
         * otherwise -> "SeriesName #number"
         */
        private static final String EXP_SERIES_WITH_NUMBER =
                "CASE"
                + " WHEN " + KEY_BOOK_NUM_IN_SERIES + "='' THEN " + KEY_SERIES_TITLE
                + " ELSE " + KEY_SERIES_TITLE + " || ' #' || " + KEY_BOOK_NUM_IN_SERIES
                + " END";
        /**
         * Single column, with the formatted name of the Series.
         * <p>
         * If no number -> "SeriesName".
         * otherwise -> "SeriesName (number)"
         */
        private static final String EXP_SERIES_WITH_NUMBER_IN_BRACKETS =
                "CASE"
                + " WHEN " + KEY_BOOK_NUM_IN_SERIES + "='' THEN " + KEY_SERIES_TITLE
                + " ELSE " + KEY_SERIES_TITLE + " || ' (' || " + KEY_BOOK_NUM_IN_SERIES + " || ')'"
                + " END";

        /**
         * If the field has a time part, convert it to local time.
         * This deals with legacy 'date-only' dates.
         * The logic being that IF they had a time part then it would be UTC.
         * Without a time part, we assume the zone is local (or irrelevant).
         */
        @NonNull
        private static String localDateExpression(@NonNull final String fieldSpec) {
            return "CASE"
                   + " WHEN " + fieldSpec + " GLOB '*-*-* *' "
                   + " THEN datetime(" + fieldSpec + ", 'localtime')"
                   + " ELSE " + fieldSpec
                   + " END";
        }

        /**
         * Create a GLOB expression to get the 'year' from a text date field in a standard way.
         * <p>
         * Just look for 4 leading numbers. We don't care about anything else.
         * <p>
         * See <a href="https://www.sqlitetutorial.net/sqlite-glob/">sqlite-glob</a>
         *
         * @param fieldSpec fully qualified field name
         * @param toLocal   if set, first convert the fieldSpec to local time from UTC
         *
         * @return expression
         */
        @NonNull
        public static String year(@NonNull String fieldSpec,
                                  final boolean toLocal) {

            //TODO: This covers a timezone offset for Dec-31 / Jan-01 only - how important is this?
            if (toLocal) {
                fieldSpec = localDateExpression(fieldSpec);
            }
            return "(CASE"
                   + " WHEN " + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",1,4)"
                   + " ELSE ''"
                   + " END)";
        }

        /**
         * Create a GLOB expression to get the 'month' from a text date field in a standard way.
         * <p>
         * Just look for 4 leading numbers followed by '-' and by 2 or 1 digit.
         * We don't care about anything else.
         *
         * @param fieldSpec fully qualified field name
         * @param toLocal   if set, first convert the fieldSpec to local time from UTC
         *
         * @return expression
         */
        @NonNull
        public static String month(@NonNull String fieldSpec,
                                   final boolean toLocal) {
            if (toLocal) {
                fieldSpec = localDateExpression(fieldSpec);
            }
            return "CASE"
                   + " WHEN " + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",6,2)"
                   + " WHEN " + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",6,1)"
                   + " ELSE ''"
                   + " END";
        }

        /**
         * Create a GLOB expression to get the 'day' from a text date field in a standard way.
         * <p>
         * Just look for 4 leading numbers followed by '-' and by 2 or 1 digit,
         * and then by '-' and 1 or two digits.
         * We don't care about anything else.
         *
         * @param fieldSpec fully qualified field name
         * @param toLocal   if set, first convert the fieldSpec to local time from UTC
         *
         * @return expression
         */
        @NonNull
        public static String day(@NonNull String fieldSpec,
                                 final boolean toLocal) {
            if (toLocal) {
                fieldSpec = localDateExpression(fieldSpec);
            }
            // Just look for 4 leading numbers followed by 2 or 1 digit then another 2 or 1 digit.
            // We don't care about anything else.
            return "CASE"
                   + " WHEN " + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",9,2)"
                   + " WHEN " + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9][0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",8,2)"
                   + " WHEN " + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",9,1)"
                   + " WHEN " + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",8,1)"
                   + " ELSE ''"
                   + " END";
        }

    }

    /**
     * Sql SELECT of a single table, without a WHERE clause.
     */
    private static final class SqlSelectFullTable {

        /**
         * Columns from {@link DBDefinitions#TBL_BOOKS} we need to send a Book to Goodreads.
         * <p>
         * See {@link GoodreadsHandler#sendOneBook}
         * -> notes column disabled for now.
         */
        static final String GOODREADS_BOOK_DATA_TO_SEND =
                "SELECT " + KEY_PK_ID
                + ',' + KEY_ISBN
                + ',' + KEY_EID_GOODREADS_BOOK
                + ',' + KEY_READ
                + ',' + KEY_READ_START
                + ',' + KEY_READ_END
                + ',' + KEY_RATING
                + ',' + KEY_PRIVATE_NOTES
                + _FROM_ + TBL_BOOKS.getName();

        /** {@link Book}, all columns. */
        private static final String BOOKS = "SELECT * FROM " + TBL_BOOKS.getName();

        /** {@link Author}, all columns. */
        private static final String AUTHORS = "SELECT * FROM " + TBL_AUTHORS.getName();

        /** {@link Series}, all columns. */
        private static final String SERIES = "SELECT * FROM " + TBL_SERIES.getName();

        /** {@link Bookshelf} all columns. */
        private static final String BOOKSHELVES =
                "SELECT " + TBL_BOOKSHELF.dot(KEY_PK_ID)
                + ',' + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME)
                + ',' + TBL_BOOKSHELF.dot(KEY_FK_STYLE)
                + ',' + TBL_BOOKLIST_STYLES.dot(KEY_UUID)
                + _FROM_ + TBL_BOOKSHELF.ref() + TBL_BOOKSHELF.join(TBL_BOOKLIST_STYLES);

        /** {@link BooklistStyle} all columns. */
        private static final String BOOKLIST_STYLES =
                "SELECT * FROM " + TBL_BOOKLIST_STYLES.getName();

        /** Book UUID only, for accessing all cover image files. */
        private static final String BOOK_ALL_UUID =
                "SELECT " + KEY_BOOK_UUID + _FROM_ + TBL_BOOKS.getName();

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String AUTHORS_FAMILY_NAMES =
                "SELECT DISTINCT " + KEY_AUTHOR_FAMILY_NAME + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + _FROM_ + TBL_AUTHORS.getName()
                + " ORDER BY " + KEY_AUTHOR_FAMILY_NAME_OB + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String AUTHORS_GIVEN_NAMES =
                "SELECT DISTINCT " + KEY_AUTHOR_GIVEN_NAMES + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + _FROM_ + TBL_AUTHORS.getName()
                + " ORDER BY " + KEY_AUTHOR_GIVEN_NAMES_OB + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String AUTHORS_FORMATTED_NAMES =
                "SELECT "
                + SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN + " AS " + KEY_AUTHOR_FORMATTED
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + _FROM_ + TBL_AUTHORS.ref()
                + " ORDER BY " + KEY_AUTHOR_FAMILY_NAME_OB + COLLATION
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String AUTHORS_FORMATTED_NAMES_GIVEN_FIRST =
                "SELECT " + SqlColumns.EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY
                + " AS " + KEY_AUTHOR_FORMATTED_GIVEN_FIRST
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + _FROM_ + TBL_AUTHORS.ref()
                + " ORDER BY " + KEY_AUTHOR_FAMILY_NAME_OB + COLLATION
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String SERIES_NAME =
                "SELECT " + KEY_SERIES_TITLE
                + ',' + KEY_SERIES_TITLE_OB
                + _FROM_ + TBL_SERIES.getName()
                + " ORDER BY " + KEY_SERIES_TITLE_OB + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String FORMATS =
                "SELECT DISTINCT " + KEY_FORMAT
                + _FROM_ + TBL_BOOKS.getName()
                + " ORDER BY " + KEY_FORMAT + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String COLORS =
                "SELECT DISTINCT " + KEY_COLOR
                + _FROM_ + TBL_BOOKS.getName()
                + " ORDER BY " + KEY_COLOR + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String GENRES =
                "SELECT DISTINCT " + KEY_GENRE
                + _FROM_ + TBL_BOOKS.getName()
                + " ORDER BY " + KEY_GENRE + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String LANGUAGES =
                "SELECT DISTINCT " + KEY_LANGUAGE
                + _FROM_ + TBL_BOOKS.getName()
                + " ORDER BY " + KEY_DATE_LAST_UPDATED + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String LOCATIONS =
                "SELECT DISTINCT " + KEY_LOCATION
                + _FROM_ + TBL_BOOKS.getName()
                + " ORDER BY " + KEY_LOCATION + COLLATION;

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String PUBLISHERS =
                "SELECT DISTINCT " + KEY_PUBLISHER
                + _FROM_ + TBL_BOOKS.getName()
                + " ORDER BY " + KEY_PUBLISHER + COLLATION;

        /**
         * All Book titles for a rebuild of the {@link DBDefinitions#KEY_TITLE_OB} column.
         */
        private static final String BOOK_TITLES =
                // The index of KEY_PK_ID, KEY_TITLE, KEY_TITLE_OB is hardcoded - don't change!
                "SELECT " + KEY_PK_ID + ',' + KEY_TITLE + ',' + KEY_TITLE_OB
                + ',' + KEY_LANGUAGE
                + _FROM_ + TBL_BOOKS.getName();

        /**
         * All Series for a rebuild of the {@link DBDefinitions#KEY_SERIES_TITLE_OB} column.
         */
        private static final String SERIES_TITLES =
                // The index of KEY_PK_ID, KEY_SERIES_TITLE, KEY_SERIES_TITLE_OB is hardcoded
                // Don't change!
                "SELECT " + KEY_PK_ID + ',' + KEY_SERIES_TITLE + ',' + KEY_SERIES_TITLE_OB
                + _FROM_ + TBL_SERIES.getName();

        /**
         * All Series for a rebuild of the {@link DBDefinitions#KEY_TITLE_OB} column.
         */
        private static final String TOC_ENTRY_TITLES =
                // The index of KEY_PK_ID, KEY_TITLE, KEY_TITLE_OB is hardcoded - don't change!
                "SELECT " + KEY_PK_ID + ',' + KEY_TITLE + ',' + KEY_TITLE_OB
                + _FROM_ + TBL_TOC_ENTRIES.getName();

        /** name only, for {@link AutoCompleteTextView}. */
        private static final String LOANEE =
                "SELECT DISTINCT " + KEY_LOANEE
                + _FROM_ + TBL_BOOK_LOANEE.getName()
                + " ORDER BY " + KEY_LOANEE + COLLATION;
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
                + TBL_BOOKSHELF.dot(KEY_PK_ID)
                + ',' + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME)
                + ',' + TBL_BOOKSHELF.dot(KEY_FK_STYLE)
                + ',' + TBL_BOOKLIST_STYLES.dot(KEY_UUID)

                + _FROM_ + TBL_BOOK_BOOKSHELF.ref()
                + TBL_BOOK_BOOKSHELF.join(TBL_BOOKSHELF)
                + TBL_BOOKSHELF.join(TBL_BOOKLIST_STYLES)
                + _WHERE_ + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOK) + "=?"
                + " ORDER BY " + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME) + COLLATION;

        /**
         * All Authors for a Book; ordered by position, family, given.
         */
        private static final String AUTHORS_BY_BOOK_ID =
                "SELECT DISTINCT " + TBL_AUTHORS.dot(KEY_PK_ID)
                + ',' + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)
                + ',' + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB)
                + ',' + TBL_AUTHORS.dot(KEY_AUTHOR_IS_COMPLETE)
                + ',' + SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN
                + " AS " + KEY_AUTHOR_FORMATTED
                + ',' + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION)
                + ',' + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_TYPE_BITMASK)

                + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_BOOK) + "=?"
                + " ORDER BY "
                + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION)
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB + COLLATION
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + COLLATION;

        /**
         * All Series for a Book; ordered by position, name.
         */
        private static final String SERIES_BY_BOOK_ID =
                "SELECT DISTINCT " + TBL_SERIES.dot(KEY_PK_ID)
                + ',' + TBL_SERIES.dot(KEY_SERIES_TITLE)
                + ',' + TBL_SERIES.dot(KEY_SERIES_TITLE_OB)
                + ',' + TBL_SERIES.dot(KEY_SERIES_IS_COMPLETE)
                + ',' + TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES)
                + ',' + TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION)
                + ',' + SqlColumns.EXP_SERIES_WITH_NUMBER_IN_BRACKETS
                + " AS " + KEY_SERIES_FORMATTED

                + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_BOOK) + "=?"
                + " ORDER BY " + TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION)
                + ',' + TBL_SERIES.dot(KEY_SERIES_TITLE_OB) + COLLATION;

        /**
         * All TocEntry's for a Book; ordered by position in the book.
         */
        private static final String TOC_ENTRIES_BY_BOOK_ID =
                "SELECT " + TBL_TOC_ENTRIES.dot(KEY_PK_ID)
                + ',' + TBL_TOC_ENTRIES.dot(KEY_FK_AUTHOR)
                + ',' + TBL_TOC_ENTRIES.dot(KEY_TITLE)
                + ',' + TBL_TOC_ENTRIES.dot(KEY_DATE_FIRST_PUBLICATION)
                // for convenience, we fetch the Author here
                + ',' + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dot(KEY_AUTHOR_IS_COMPLETE)

                // count the number of books this TOC entry is present in.
                + ',' + "(SELECT COUNT(*) FROM " + TBL_BOOK_TOC_ENTRIES.getName()
                // use the full table name on the left as we need a full table scan
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.getName() + '.' + KEY_FK_TOC_ENTRY
                // but filtered on the results from the main query (i.e. alias on the right).
                + "=" + TBL_TOC_ENTRIES.dot(KEY_PK_ID) + ") AS " + KEY_BOOK_COUNT

                + _FROM_ + TBL_TOC_ENTRIES.ref()
                + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                + TBL_TOC_ENTRIES.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_FK_BOOK) + "=?"
                + " ORDER BY " + TBL_BOOK_TOC_ENTRIES.dot(KEY_BOOK_TOC_ENTRY_POSITION);

        /**
         * All Books (id only!) for a given Author.
         */
        private static final String BOOK_IDS_BY_AUTHOR_ID =
                "SELECT " + TBL_BOOKS.dot(KEY_PK_ID)
                + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_AUTHOR)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR) + "=?";

        /**
         * All Books (id only!) for a given Series.
         */
        private static final String BOOK_IDS_BY_SERIES_ID =
                "SELECT " + TBL_BOOKS.dot(KEY_PK_ID)
                + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_SERIES) + "=?";

        /**
         * All Books (id only!) for a given Publisher.
         */
        private static final String BOOK_IDS_BY_PUBLISHER =
                "SELECT " + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PUBLISHER + "=?";

        /**
         * All TocEntry's for an Author.
         * <p>
         * ORDER BY clause NOT added here, as this statement is used in a union as well.
         * <p>
         * We need KEY_TITLE_OB as it will be used to ORDER BY
         */
        private static final String TOC_ENTRIES_BY_AUTHOR_ID =
                "SELECT " + "'" + TocEntry.Type.TYPE_TOC + "' AS " + KEY_TOC_TYPE
                + ',' + TBL_TOC_ENTRIES.dot(KEY_PK_ID)
                + ',' + TBL_TOC_ENTRIES.dot(KEY_TITLE)
                + ',' + TBL_TOC_ENTRIES.dot(KEY_TITLE_OB)
                + ',' + TBL_TOC_ENTRIES.dot(KEY_DATE_FIRST_PUBLICATION)
                // count the number of books this TOC entry is present in.
                + ", COUNT(" + TBL_TOC_ENTRIES.dot(KEY_PK_ID) + ") AS " + KEY_BOOK_COUNT;

        /**
         * All Book titles and their first pub. date, for an Author..
         * <p>
         * ORDER BY clause NOT added here, as this statement is used in a union as well.
         * <p>
         * We need KEY_TITLE_OB as it will be used to ORDER BY
         */
        private static final String BOOK_TITLES_BY_AUTHOR_ID =
                "SELECT " + "'" + TocEntry.Type.TYPE_BOOK + "' AS " + KEY_TOC_TYPE
                + ',' + TBL_BOOKS.dot(KEY_PK_ID)
                + ',' + TBL_BOOKS.dot(KEY_TITLE)
                + ',' + TBL_BOOKS.dot(KEY_TITLE_OB)
                + ',' + TBL_BOOKS.dot(KEY_DATE_FIRST_PUBLICATION)
                + ",1 AS " + KEY_BOOK_COUNT;
    }

    /**
     * Sql SELECT that returns a single ID/UUID.
     */
    private static final class SqlGet {

        /**
         * Find the Book id based on a search for the ISBN (both 10 & 13).
         * The arguments MUST be bound in uppercase,
         * as valid ISBN numbers are always stored in uppercase (the 'X').
         */
        static final String BOOK_ID_BY_VALID_ISBN =
                "SELECT " + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_ISBN + " IN (?,?)";

        /**
         * Find the Book id based on a search for the ISBN; the isbn need not be valid
         * and can in fact be any code whatsoever with mixed case characters.
         * Hence, we use 'upper()' on BOTH sides.
         */
        static final String BOOK_ID_BY_ISBN =
                "SELECT " + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                + " WHERE upper(" + KEY_ISBN + ")=upper(?)";


        static final String BOOKLIST_STYLE_ID_BY_UUID =
                "SELECT " + KEY_PK_ID + _FROM_ + TBL_BOOKLIST_STYLES.getName()
                + _WHERE_ + KEY_UUID + "=?";

        /**
         * Can return more then one row if the KEY_AUTHOR_GIVEN_NAMES_OB is empty.
         */
        static final String AUTHOR_ID_BY_NAME =
                "SELECT " + KEY_PK_ID + _FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + KEY_AUTHOR_FAMILY_NAME_OB + "=?" + COLLATION
                + " AND " + KEY_AUTHOR_GIVEN_NAMES_OB + "=?" + COLLATION;

        static final String BOOKSHELF_ID_BY_NAME =
                "SELECT " + KEY_PK_ID + _FROM_ + TBL_BOOKSHELF.getName()
                + _WHERE_ + KEY_BOOKSHELF_NAME + "=?" + COLLATION;

        /**
         * Get the id of a {@link Series} by its Title.
         * Search KEY_SERIES_TITLE_OB on both "The Title" and "Title, The"
         */
        static final String SERIES_ID_BY_NAME =
                "SELECT " + KEY_PK_ID + _FROM_ + TBL_SERIES.getName()
                + _WHERE_ + KEY_SERIES_TITLE_OB + "=?" + COLLATION
                + " OR " + KEY_SERIES_TITLE_OB + "=?" + COLLATION;

        /**
         * Get the id of a {@link TocEntry} by its Title.
         * Search KEY_TITLE_OB on both "The Title" and "Title, The"
         */
        static final String TOC_ENTRY_ID =
                "SELECT " + KEY_PK_ID + _FROM_ + TBL_TOC_ENTRIES.getName()
                + _WHERE_ + KEY_FK_AUTHOR + "=?"
                + " AND (" + KEY_TITLE_OB + "=? " + COLLATION
                + " OR " + KEY_TITLE_OB + "=?" + COLLATION + ')';

        static final String BOOK_ID_BY_TOC_ENTRY_ID =
                "SELECT " + KEY_FK_BOOK + _FROM_ + TBL_BOOK_TOC_ENTRIES.getName()
                + _WHERE_ + KEY_FK_TOC_ENTRY + "=?";

        /**
         * Get the UUID of a {@link Book} by its id.
         */
        static final String BOOK_UUID_BY_ID =
                "SELECT " + KEY_BOOK_UUID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";
        /**
         * Get the id of a {@link Book} by its UUID.
         */
        static final String BOOK_ID_BY_UUID =
                "SELECT " + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_BOOK_UUID + "=?";
        /**
         * Get the ISBN of a {@link Book} by its id.
         */
        static final String BOOK_ISBN_BY_BOOK_ID =
                "SELECT " + KEY_ISBN + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";
        /**
         * Get the title of a {@link Book} by its id.
         */
        static final String BOOK_TITLE_BY_BOOK_ID =
                "SELECT " + KEY_TITLE + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";
        /**
         * Get the name of the loanee of a {@link Book}.
         */
        static final String LOANEE_BY_BOOK_ID =
                "SELECT " + KEY_LOANEE + _FROM_ + TBL_BOOK_LOANEE.getName()
                + _WHERE_ + KEY_FK_BOOK + "=?";
    }

    /**
     * Sql SELECT to get Objects by their id; and related queries.
     */
    private static final class SqlSelect {

        /**
         * Get a {@link Book} by its id.
         */
        static final String BOOK_BY_ID =
                SqlSelectFullTable.BOOKS + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get a {@link Bookshelf} by its id.
         */
        static final String BOOKSHELF_BY_ID =
                SqlSelectFullTable.BOOKSHELVES + _WHERE_ + TBL_BOOKSHELF.dot(KEY_PK_ID) + "=?";

        /**
         * Get a {@link Bookshelf} by its name.
         */
        static final String BOOKSHELF_BY_NAME =
                SqlSelectFullTable.BOOKSHELVES
                + _WHERE_ + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME) + "=?" + COLLATION;

        /**
         * Get an {@link Author} by its id.
         */
        static final String AUTHOR_BY_ID =
                SqlSelectFullTable.AUTHORS + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get a {@link Series} by its id.
         */
        static final String SERIES_BY_ID =
                SqlSelectFullTable.SERIES + _WHERE_ + KEY_PK_ID + "=?";


        /**
         * Get the language (ISO3) code for a Series.
         * This is defined as the language code for the first book in the Series.
         */
        static final String SERIES_LANGUAGE =
                "SELECT " + TBL_BOOKS.dot(KEY_LANGUAGE)
                + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_BOOKS)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_SERIES) + "=?"
                + " ORDER BY " + TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES)
                + " LIMIT 1";

        /**
         * Get the last-update-date for a {@link Book} by its id.
         */
        static final String LAST_UPDATE_DATE_BY_BOOK_ID =
                "SELECT " + KEY_DATE_LAST_UPDATED + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Check if a {@link Book} exists.
         */
        static final String BOOK_EXISTS =
                "SELECT COUNT(*) " + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Count all {@link Book}'s.
         */
        static final String COUNT_BOOKS = "SELECT COUNT(*) FROM " + TBL_BOOKS.getName();

        /**
         * Count the number of {@link Book}'s in a {@link Series}.
         */
        static final String COUNT_BOOKS_IN_SERIES =
                "SELECT COUNT(" + KEY_FK_BOOK + ") FROM " + TBL_BOOK_SERIES.getName()
                + _WHERE_ + KEY_FK_SERIES + "=?";

        /**
         * Count the number of {@link Book}'s by an {@link Author}.
         */
        static final String COUNT_BOOKS_BY_AUTHOR =
                "SELECT COUNT(" + KEY_FK_BOOK + ") FROM " + TBL_BOOK_AUTHOR.getName()
                + _WHERE_ + KEY_FK_AUTHOR + "=?";

        /**
         * Count the number of {@link TocEntry}'s by an {@link Author}.
         */
        static final String COUNT_TOC_ENTRIES_BY_AUTHOR =
                "SELECT COUNT(" + KEY_PK_ID + ") FROM " + TBL_TOC_ENTRIES.getName()
                + _WHERE_ + KEY_FK_AUTHOR + "=?";

        /**
         * Get the needed fields of a {@link Book} to send to Goodreads.
         * <p>
         * param KEY_PK_ID of the Book
         */
        static final String GOODREADS_GET_BOOK_TO_SEND_BY_BOOK_ID =
                SqlSelectFullTable.GOODREADS_BOOK_DATA_TO_SEND + _WHERE_ + KEY_PK_ID + "=?";
    }

    /**
     * Sql INSERT.
     */
    private static final class SqlInsert {

        static final String BOOKSHELF =
                "INSERT INTO " + TBL_BOOKSHELF.getName()
                + '(' + KEY_BOOKSHELF_NAME
                + ',' + KEY_FK_STYLE
                + ") VALUES (?,?)";

        static final String AUTHOR =
                "INSERT INTO " + TBL_AUTHORS.getName()
                + '(' + KEY_AUTHOR_FAMILY_NAME
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + ',' + KEY_AUTHOR_GIVEN_NAMES
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + ',' + KEY_AUTHOR_IS_COMPLETE
                + ") VALUES (?,?,?,?,?)";

        static final String SERIES =
                "INSERT INTO " + TBL_SERIES.getName()
                + '(' + KEY_SERIES_TITLE
                + ',' + KEY_SERIES_TITLE_OB
                + ',' + KEY_SERIES_IS_COMPLETE
                + ") VALUES (?,?,?)";

        static final String TOC_ENTRY =
                "INSERT INTO " + TBL_TOC_ENTRIES.getName()
                + '(' + KEY_FK_AUTHOR
                + ',' + KEY_TITLE
                + ',' + KEY_TITLE_OB
                + ',' + KEY_DATE_FIRST_PUBLICATION
                + ") VALUES (?,?,?,?)";


        static final String BOOK_TOC_ENTRY =
                "INSERT INTO " + TBL_BOOK_TOC_ENTRIES.getName()
                + '(' + KEY_FK_TOC_ENTRY
                + ',' + KEY_FK_BOOK
                + ',' + KEY_BOOK_TOC_ENTRY_POSITION
                + ") VALUES (?,?,?)";

        static final String BOOK_BOOKSHELF =
                "INSERT INTO " + TBL_BOOK_BOOKSHELF.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_FK_BOOKSHELF
                + ") VALUES (?,?)";

        static final String BOOK_AUTHOR =
                "INSERT INTO " + TBL_BOOK_AUTHOR.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_FK_AUTHOR
                + ',' + KEY_BOOK_AUTHOR_POSITION
                + ',' + KEY_BOOK_AUTHOR_TYPE_BITMASK
                + ") VALUES(?,?,?,?)";

        static final String BOOK_SERIES =
                "INSERT INTO " + TBL_BOOK_SERIES.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_FK_SERIES
                + ',' + KEY_BOOK_NUM_IN_SERIES
                + ',' + KEY_BOOK_SERIES_POSITION
                + ") VALUES(?,?,?,?)";

        static final String BOOK_LOANEE =
                "INSERT INTO " + TBL_BOOK_LOANEE.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_LOANEE
                + ") VALUES(?,?)";


        static final String BOOKLIST_STYLE =
                "INSERT INTO " + TBL_BOOKLIST_STYLES.getName()
                + '(' + KEY_UUID
                + ',' + KEY_STYLE_IS_BUILTIN
                + ") VALUES (?,?)";
    }

    /**
     * Sql UPDATE. Intention is to only have single-column updates here and do multi-column
     * with the ContentValues based update method.
     */
    private static final class SqlUpdate {

        /**
         * Update a single Book's last sync date with Goodreads.
         */
        static final String GOODREADS_LAST_SYNC_DATE =
                "UPDATE " + TBL_BOOKS.getName()
                + " SET " + KEY_BOOK_GOODREADS_LAST_SYNC_DATE + "=current_timestamp"
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Update a single Book's Goodreads id. Do not update the last-update-date!
         */
        static final String GOODREADS_BOOK_ID =
                "UPDATE " + TBL_BOOKS.getName()
                + " SET " + KEY_EID_GOODREADS_BOOK + "=?"
                + _WHERE_ + KEY_PK_ID + "=?";

        static final String AUTHOR_ON_TOC_ENTRIES =
                "UPDATE " + TBL_TOC_ENTRIES.getName()
                + " SET " + KEY_FK_AUTHOR + "=?"
                + _WHERE_ + KEY_FK_AUTHOR + "=?";

        static final String FORMAT =
                "UPDATE " + TBL_BOOKS.getName()
                + " SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_FORMAT + "=?"
                + _WHERE_ + KEY_FORMAT + "=?";

        static final String COLOR =
                "UPDATE " + TBL_BOOKS.getName()
                + " SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_COLOR + "=?"
                + _WHERE_ + KEY_COLOR + "=?";

        static final String GENRE =
                "UPDATE " + TBL_BOOKS.getName()
                + " SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_GENRE + "=?"
                + _WHERE_ + KEY_GENRE + "=?";

        static final String LANGUAGE =
                "UPDATE " + TBL_BOOKS.getName()
                + " SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_LANGUAGE + "=?"
                + _WHERE_ + KEY_LANGUAGE + "=?";

        static final String LOCATION =
                "UPDATE " + TBL_BOOKS.getName()
                + " SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_LOCATION + "=?"
                + _WHERE_ + KEY_LOCATION + "=?";

        static final String PUBLISHER =
                "UPDATE " + TBL_BOOKS.getName()
                + " SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_PUBLISHER + "=?"
                + _WHERE_ + KEY_PUBLISHER + "=?";
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
                _DELETE_FROM_ + TBL_BOOKS.getName() + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Delete a {@link Bookshelf}.
         */
        static final String BOOKSHELF_BY_ID =
                _DELETE_FROM_ + TBL_BOOKSHELF.getName() + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Delete a {@link Series}.
         */
        static final String SERIES_BY_ID =
                _DELETE_FROM_ + TBL_SERIES.getName() + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Delete a {@link TocEntry}.
         */
        static final String TOC_ENTRY =
                _DELETE_FROM_ + TBL_TOC_ENTRIES.getName() + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Delete a {@link BooklistStyle}.
         */
        static final String STYLE_BY_ID =
                _DELETE_FROM_ + TBL_BOOKLIST_STYLES.getName() + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Delete the link between a {@link Book} and an {@link Author}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_AUTHOR_BY_BOOK_ID =
                _DELETE_FROM_ + TBL_BOOK_AUTHOR.getName() + _WHERE_ + KEY_FK_BOOK + "=?";

        /**
         * Delete the link between a {@link Book} and a {@link Bookshelf}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_BOOKSHELF_BY_BOOK_ID =
                _DELETE_FROM_ + TBL_BOOK_BOOKSHELF.getName() + _WHERE_ + KEY_FK_BOOK + "=?";

        /**
         * Delete the link between a {@link Book} and a {@link Series}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_SERIES_BY_BOOK_ID =
                _DELETE_FROM_ + TBL_BOOK_SERIES.getName() + _WHERE_ + KEY_FK_BOOK + "=?";

        /**
         * Delete the link between a {@link Book} and a {@link TocEntry}.
         * <p>
         * This is done when a TOC is updated; first delete all links, then re-create them.
         */
        static final String BOOK_TOC_ENTRIES_BY_BOOK_ID =
                _DELETE_FROM_ + TBL_BOOK_TOC_ENTRIES.getName() + _WHERE_ + KEY_FK_BOOK + "=?";

        /**
         * Delete the loan of a {@link Book}; i.e. 'return the book'.
         */
        static final String BOOK_LOANEE_BY_BOOK_ID =
                _DELETE_FROM_ + TBL_BOOK_LOANEE.getName() + _WHERE_ + KEY_FK_BOOK + "=?";


        /**
         * Purge an {@link Author} if no longer in use (check both book_author AND toc_entries).
         */
        static final String PURGE_AUTHORS = _DELETE_FROM_ + TBL_AUTHORS.getName()
                                            + _WHERE_ + KEY_PK_ID + " NOT IN"
                                            + " (SELECT DISTINCT " + KEY_FK_AUTHOR + _FROM_
                                            + TBL_BOOK_AUTHOR.getName() + ')'
                                            + " AND " + KEY_PK_ID + " NOT IN"
                                            + " (SELECT DISTINCT " + KEY_FK_AUTHOR + _FROM_
                                            + TBL_TOC_ENTRIES.getName() + ')';

        /**
         * Purge a {@link Series} if no longer in use.
         */
        static final String PURGE_SERIES = _DELETE_FROM_ + TBL_SERIES.getName()
                                           + _WHERE_ + KEY_PK_ID + " NOT IN"
                                           + " (SELECT DISTINCT " + KEY_FK_SERIES + _FROM_
                                           + TBL_BOOK_SERIES.getName() + ')';

        private SqlDelete() {
        }
    }

    /**
     * Sql specific for FTS.
     */
    public static final class SqlFTS {

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_AUTHORS_BY_BOOK_ID =
                "SELECT " + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES)
                + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_BOOK) + "=?"
                + " ORDER BY " + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION);
        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_SERIES_BY_BOOK_ID =
                "SELECT " + TBL_SERIES.dot(KEY_SERIES_TITLE) + "||' '||"
                + " COALESCE(" + TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES) + ",'')"
                + " AS " + KEY_SERIES_TITLE
                + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_BOOK) + "=?"
                + " ORDER BY " + TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION);
        /** Used during insert of a book. Minimal column list. Not ordered. */
        static final String GET_TOC_TITLES_BY_BOOK_ID =
                "SELECT " + TBL_TOC_ENTRIES.dot(KEY_TITLE)
                + _FROM_ + TBL_TOC_ENTRIES.ref() + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)

                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_FK_BOOK) + "=?";
        /** the body of an INSERT INTO [table]. Used more than once. */
        static final String INSERT_BODY =
                " (" + KEY_TITLE
                + ',' + KEY_FTS_AUTHOR_NAME
                + ',' + KEY_SERIES_TITLE
                + ',' + KEY_DESCRIPTION
                + ',' + KEY_PRIVATE_NOTES
                + ',' + KEY_PUBLISHER
                + ',' + KEY_GENRE
                + ',' + KEY_LOCATION
                + ',' + KEY_ISBN
                + ',' + KEY_FTS_TOC_ENTRY_TITLE

                + ',' + KEY_FTS_BOOKS_PK
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)";

        /**
         * The full INSERT statement.
         * The parameter order MUST match the order expected in UPDATE.
         */
        static final String INSERT = "INSERT INTO " + TBL_FTS_BOOKS.getName() + INSERT_BODY;

        /**
         * The full UPDATE statement.
         * The parameter order MUST match the order expected in INSERT.
         */
        static final String UPDATE =
                "UPDATE " + TBL_FTS_BOOKS.getName()
                + " SET " + KEY_TITLE + "=?"
                + ',' + KEY_FTS_AUTHOR_NAME + "=?"
                + ',' + KEY_SERIES_TITLE + "=?"
                + ',' + KEY_DESCRIPTION + "=?"
                + ',' + KEY_PRIVATE_NOTES + "=?"
                + ',' + KEY_PUBLISHER + "=?"
                + ',' + KEY_GENRE + "=?"
                + ',' + KEY_LOCATION + "=?"
                + ',' + KEY_ISBN + "=?"
                + ',' + KEY_FTS_TOC_ENTRY_TITLE + "=?"

                + _WHERE_ + KEY_FTS_BOOKS_PK + "=?";

        /** Standard Local-search. */
        static final String SEARCH_SUGGESTIONS =
                // KEY_FTS_BOOKS_PK is the _id into the books table.
                "SELECT " + KEY_FTS_BOOKS_PK + " AS " + KEY_PK_ID
                + ',' + TBL_FTS_BOOKS.dotAs(KEY_TITLE,
                                            SearchManager.SUGGEST_COLUMN_TEXT_1)
                + ',' + TBL_FTS_BOOKS.dotAs(KEY_FTS_AUTHOR_NAME,
                                            SearchManager.SUGGEST_COLUMN_TEXT_2)
                + ',' + TBL_FTS_BOOKS.dotAs(KEY_TITLE,
                                            SearchManager.SUGGEST_COLUMN_INTENT_DATA)
                + _FROM_ + TBL_FTS_BOOKS.getName()
                + _WHERE_ + TBL_FTS_BOOKS.getName() + " MATCH ?";

        /** Advanced Local-search. */
        static final String SEARCH
                = "SELECT "
                  // KEY_FTS_BOOKS_PK is the _id into the books table.
                  + KEY_FTS_BOOKS_PK + " AS " + KEY_PK_ID
                  + _FROM_ + TBL_FTS_BOOKS.getName()
                  + _WHERE_ + TBL_FTS_BOOKS.getName() + " MATCH ?"
                  + " LIMIT ?";


        /**
         * Cleanup a search string as preparation for an FTS search.
         * <p>
         * All diacritic characters are converted to ASCII.
         * Remove punctuation from the search string to TRY to match the tokenizer.
         * The only punctuation we allow is a hyphen preceded by a space => negate the next word.
         * Everything else is translated to a space.
         *
         * @param searchText Search criteria to clean
         * @param domain     (optional) domain to prefix the searchText or {@code null} for none
         *
         * @return Clean string
         */
        @NonNull
        private static String cleanupFtsCriterion(@Nullable final String searchText,
                                                  @Nullable final String domain) {

            if (searchText == null || searchText.isEmpty()) {
                return "";
            }

            // Convert the text to pure ASCII. We'll use an array to loop over it.
            final char[] chars = toAscii(searchText).toCharArray();
            // Cached length
            final int len = chars.length;
            // Initial position
            int pos = 0;
            // Dummy 'previous' character
            char prev = ' ';

            // Output buffer
            final StringBuilder parameter = new StringBuilder();

            // Loop over array
            while (pos < len) {
                char current = chars[pos];
                // If current is letter or digit, use it.
                if (Character.isLetterOrDigit(current)) {
                    parameter.append(current);

                } else if (current == '-' && Character.isWhitespace(prev)) {
                    // Allow negation if preceded by space
                    parameter.append(current);

                } else {
                    // Turn everything else in whitespace
                    current = ' ';

                    if (!Character.isWhitespace(prev)) {
                        // If prev character was non-ws, and not negation, make wildcard
                        if (prev != '-') {
                            parameter.append('*');
                        }
                        // Append a whitespace only when last char was not a whitespace
                        parameter.append(' ');
                    }
                }
                prev = current;
                pos++;
            }

            // append a wildcard if prev character was non-ws, and not negation
            if (!Character.isWhitespace(prev) && (prev != '-')) {
                parameter.append('*');
            }
            // reminder to self: we do not need to prepend with a '*' for MATCH to work.
            String cleanedText = parameter.toString().trim();

            if (domain != null) {
                // prepend each word with the FTS column name.
                StringBuilder result = new StringBuilder();
                for (String word : cleanedText.split(" ")) {
                    if (!word.isEmpty()) {
                        result.append(' ').append(domain).append(':').append(word);
                    }
                }
                return result.toString();
            } else {
                // no domain, return as-is
                return cleanedText;
            }
        }

        /**
         * Create a string suited to be used with MATCH.
         *
         * @param author      Author related keywords to find
         * @param title       Title related keywords to find
         * @param seriesTitle Series title related keywords to find
         * @param keywords    Keywords to find anywhere in book; this includes titles and authors
         *
         * @return an query string suited to search FTS for the specified parameters,
         * or {@code ""} if all input was empty
         */
        @NonNull
        public static String createMatchString(@Nullable final String author,
                                               @Nullable final String title,
                                               @Nullable final String seriesTitle,
                                               @Nullable final String keywords) {

            return (cleanupFtsCriterion(keywords, null)
                    + cleanupFtsCriterion(author, KEY_FTS_AUTHOR_NAME)
                    + cleanupFtsCriterion(title, KEY_TITLE)
                    + cleanupFtsCriterion(seriesTitle, KEY_SERIES_TITLE))
                    .trim();
        }
    }
}
