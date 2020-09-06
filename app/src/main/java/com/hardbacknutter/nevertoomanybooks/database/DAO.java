/*
 * @Copyright 2020 HardBackNutter
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
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;

import java.io.Closeable;
import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
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
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableInfo;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithTitle;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_GIVEN_NAMES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_GIVEN_NAMES_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_BL_TOP_OFFSET;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_BL_TOP_POS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_BL_TOP_ROW_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_CONDITION_COVER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_PUBLISHER_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_TOC_ENTRY_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DESCRIPTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EDITION_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_CALIBRE_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_GOODREADS_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_TOC_ENTRY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FTS_AUTHOR_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FTS_BOOK_ID;
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
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_READ_START;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_SIGNED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_IS_BUILTIN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TOC_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TOC_TYPE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_SYNC_DATE_GOODREADS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * Database access helper class.
 * <p>
 * This class is 'context-free'. KEEP IT THAT WAY. Passing in a context is fine, but NO caching.
 * We need to use this in background tasks and ViewModel classes.
 * Using {@link App#getAppContext} is however allowed.
 * <p>
 * insert/update of a Book failures are handled with {@link DaoWriteException}
 * which makes the deep nesting of calls easier to handle.
 * <p>
 * All others follow the pattern of:
 * insert: return new id, or {@code -1} for error.
 * update: return rows affected, can be 0; or boolean when appropriate.
 * <p>
 * Individual deletes return boolean (i.e. 0 or 1 row affected)
 * Multi-deletes return either void, or the number of rows deleted.
 * <p>
 * TODO: some places ignore insert/update failures. A storage full could trigger a failure.
 * <p>
 * URGENT: caching of statements forces synchronized (stmt) ... is it worth it ?
 * There is an explicit warning that {@link SQLiteStatement} is not thread safe!
 */
public class DAO
        implements AutoCloseable {

    /**
     * Book Insert/update flag.
     * If set, relax some rules which would affect performance otherwise.
     * This is/should only be used during imports.
     */
    public static final int BOOK_FLAG_IS_BATCH_OPERATION = 1;
    /**
     * Book Insert/update flag.
     * If set, and the book bundle has an id !=0, force the id to be used.
     * This is/should only be used during imports of new books
     * i.e. during import of a backup archive/csv
     */
    public static final int BOOK_FLAG_USE_ID_IF_PRESENT = 1 << 1;
    /**
     * Book Insert/update flag.
     * If set, the UPDATE_DATE field from the bundle should be trusted.
     * If this flag is not set, the UPDATE_DATE will be set based on the current time
     */
    public static final int BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT = 1 << 2;

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
     * <strong>Note:</strong> Important to have a space at the start
     */
    public static final String _COLLATION = " Collate LOCALIZED";

    /**
     * Static Synchronizer to coordinate access to <strong>this</strong> database.
     */
    public static final Synchronizer SYNCHRONIZER = new Synchronizer();


    private static final String SELECT_DISTINCT_ = "SELECT DISTINCT ";
    private static final String SELECT_ = "SELECT ";
    private static final String _AS_ = " AS ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _ORDER_BY_ = " ORDER BY ";


    /** Log tag. */
    private static final String TAG = "DAO";

    /** Static Factory object to create a {@link SynchronizedCursor} cursor. */
    private static final SQLiteDatabase.CursorFactory CURSOR_FACTORY =
            (db, d, et, q) -> new SynchronizedCursor(d, et, q, SYNCHRONIZER);

    /** Static Factory object to create an {@link TypedCursor} cursor. */
    private static final SQLiteDatabase.CursorFactory EXT_CURSOR_FACTORY =
            (db, d, et, q) -> new TypedCursor(d, et, q, SYNCHRONIZER);

    /** statement names; keys into the cache map. */
    private static final String STMT_CHECK_BOOK_EXISTS = "CheckBookExists";
    private static final String STMT_GET_AUTHOR_ID = "GetAuthorId";
    private static final String STMT_GET_SERIES_ID = "GetSeriesId";
    private static final String STMT_GET_PUBLISHER_ID = "GetPublisherId";
    private static final String STMT_GET_TOC_ENTRY_ID = "GetTOCEntryId";
    private static final String STMT_GET_BOOK_ISBN = "GetBookIsbn";
    private static final String STMT_GET_BOOK_TITLE = "GetBookTitle";
    private static final String STMT_GET_BOOK_UPDATE_DATE = "GetBookUpdateDate";
    private static final String STMT_GET_BOOK_UUID = "GetBookUuid";
    private static final String STMT_GET_BOOK_ID_FROM_UUID = "GetBookIdFromUuid";
    private static final String STMT_GET_BOOKSHELF_ID_BY_NAME = "GetBookshelfIdByName";
    private static final String STMT_GET_LOANEE_BY_BOOK_ID = "GetLoaneeByBookId";
    private static final String STMT_GET_BOOKLIST_STYLE = "GetBooklistStyle";

    private static final String STMT_INSERT_AUTHOR = "InsertAuthor";
    private static final String STMT_INSERT_SERIES = "InsertSeries";
    private static final String STMT_INSERT_PUBLISHER = "InsertPublisher";
    private static final String STMT_INSERT_TOC_ENTRY = "InsertTOCEntry";

    private static final String STMT_INSERT_BOOK_AUTHORS = "InsertBookAuthors";
    private static final String STMT_INSERT_BOOK_SERIES = "InsertBookSeries";
    private static final String STMT_INSERT_BOOK_PUBLISHER = "InsertBookPublisher";
    private static final String STMT_INSERT_BOOK_TOC_ENTRY = "InsertBookTOCEntry";
    private static final String STMT_INSERT_BOOK_BOOKSHELF = "InsertBookBookshelf";

    private static final String STMT_DELETE_BOOK = "DeleteBook";

    private static final String STMT_DELETE_BOOK_TOC_ENTRIES = "DeleteBookTocEntries";
    private static final String STMT_DELETE_BOOK_AUTHORS = "DeleteBookAuthors";
    private static final String STMT_DELETE_BOOK_BOOKSHELF = "DeleteBookBookshelf";
    private static final String STMT_DELETE_BOOK_SERIES = "DeleteBookSeries";
    private static final String STMT_DELETE_BOOK_PUBLISHER = "DeleteBookPublisher";

    private static final String STMT_UPDATE_GOODREADS_BOOK_ID = "UpdateGoodreadsBookId";
    private static final String STMT_UPDATE_GOODREADS_SYNC_DATE = "UpdateGoodreadsSyncDate";

    private static final String STMT_INSERT_FTS = "InsertFts";
    private static final String STMT_UPDATE_FTS = "UpdateFts";

    /** log error string. */
    private static final String ERROR_FAILED_TO_UPDATE_FTS = "Failed to onProgress FTS";
    /** log error string. */
    private static final String ERROR_CREATING_BOOK_FROM = "Failed creating book from\n";
    /** log error string. */
    private static final String ERROR_UPDATING_BOOK_FROM = "Failed updating book from\n";


    /** See {@link #encodeString}. */
    private static final Pattern SINGLE_QUOTE_LITERAL = Pattern.compile("'", Pattern.LITERAL);
    /** See {@link #encodeOrderByColumn}. */
    private static final Pattern NON_WORD_CHARACTER_PATTERN = Pattern.compile("\\W");
    /** See {@link #toAscii}. */
    private static final Pattern ASCII_PATTERN = Pattern.compile("[^\\p{ASCII}]");

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

    /** Reference to the singleton. */
    private final DBHelper mDBHelper;

    /** Reference to the singleton. */
    private final SynchronizedDb mSyncedDb;

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

        mDBHelper = DBHelper.getInstance(App.getAppContext(), CURSOR_FACTORY, SYNCHRONIZER);
        mSyncedDb = SynchronizedDb.getInstance(SYNCHRONIZER, mDBHelper);

        // statements are instance based/managed
        mSqlStatementManager = new SqlStatementManager(mSyncedDb, TAG + "|" + mInstanceName);
    }

    @VisibleForTesting
    public DAO(@NonNull final Context context,
               @NonNull final String name) {
        mInstanceName = name;

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, mInstanceName + "|Constructor");
        }

        mDBHelper = DBHelper.getInstance(context, CURSOR_FACTORY, SYNCHRONIZER);
        mSyncedDb = SynchronizedDb.getInstance(SYNCHRONIZER, mDBHelper);

        // statements are instance based/managed
        mSqlStatementManager = new SqlStatementManager(mSyncedDb, TAG + "|" + mInstanceName);
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
        return SINGLE_QUOTE_LITERAL.matcher(value).replaceAll("''");
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
        final String s = toAscii(value);
        // remove all non-word characters. i.e. all characters not in [a-zA-Z_0-9]
        return NON_WORD_CHARACTER_PATTERN.matcher(s).replaceAll("").toLowerCase(locale);
    }

    /**
     * Normalize a given string to contain only ASCII characters so we can easily text searches.
     *
     * @param text to normalize
     *
     * @return ascii text
     */
    private static String toAscii(@NonNull final CharSequence text) {
        return ASCII_PATTERN.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                            .replaceAll("");
    }

    public DBHelper getDBHelper() {
        return mDBHelper;
    }

    /**
     * Get the local database.
     *
     * @return Underlying database connection
     */
    @NonNull
    public SynchronizedDb getSyncDb() {
        return mSyncedDb;
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
     * Wrapper to {@link SynchronizedDb#beginTransaction(boolean)}.
     *
     * @param isUpdate Indicates if updates will be done in TX
     *
     * @return the lock
     */
    @NonNull
    public SyncLock beginTransaction(final boolean isUpdate) {
        return mSyncedDb.beginTransaction(isUpdate);
    }

    /**
     * Wrapper to {@link SynchronizedDb#endTransaction}.
     *
     * @param txLock Lock returned from BeginTransaction().
     */
    public void endTransaction(@Nullable final SyncLock txLock) {
        // it's cleaner to have the null detection here
        Objects.requireNonNull(txLock);
        mSyncedDb.endTransaction(txLock);
    }

    /**
     * Wrapper to {@link SynchronizedDb#inTransaction}.
     */
    public boolean inTransaction() {
        return mSyncedDb.inTransaction();
    }

    /**
     * Wrapper to {@link SynchronizedDb#setTransactionSuccessful}.
     */
    public void setTransactionSuccessful() {
        mSyncedDb.setTransactionSuccessful();
    }

    /**
     * Create a new Book using the details provided.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set. May contain extra data.
     *                The id will be updated.
     * @param flags   See {@link BookFlags} for flag definitions; {@code 0} for 'normal'.
     *
     * @return the row id of the newly inserted row
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1, to = Integer.MAX_VALUE)
    public long insert(@NonNull final Context context,
                       @NonNull final Book /* in/out */ book,
                       @BookFlags final int flags)
            throws DaoWriteException {

        SyncLock txLock = null;
        if (!mSyncedDb.inTransaction()) {
            txLock = mSyncedDb.beginTransaction(true);
        }

        try {
            book.preprocessForStoring(context, true);

            // Make sure we have at least one author
            final List<Author> authors = book.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
            if (authors.isEmpty()) {
                throw new DaoWriteException(ErrorMsg.EMPTY_ARRAY_OR_LIST + "|authors|book=" + book);
            }

            // correct field types if needed, and filter out fields we don't have in the db table.
            final ContentValues cv = filterValues(TBL_BOOKS, book, book.getLocale(context));

            final String utcNow = LocalDateTime
                    .now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            // if we do NOT have a date set, use 'now'
            if (!cv.containsKey(DBDefinitions.KEY_UTC_ADDED)) {
                cv.put(DBDefinitions.KEY_UTC_ADDED, utcNow);
            }
            // if we do NOT have a date set, use 'now'
            if (!cv.containsKey(KEY_UTC_LAST_UPDATED)) {
                cv.put(KEY_UTC_LAST_UPDATED, utcNow);
            }

            // if allowed, and we have an id, use it.
            if ((flags & BOOK_FLAG_USE_ID_IF_PRESENT) != 0 && book.getId() > 0) {
                cv.put(KEY_PK_ID, book.getId());
            } else {
                // in all other circumstances, make absolutely sure we DO NOT pass in an id.
                cv.remove(KEY_PK_ID);
            }

            // go!
            final long newBookId = mSyncedDb.insert(TBL_BOOKS.getName(), null, cv);

            if (newBookId > 0) {
                // set the new id/uuid on the Book itself
                book.putLong(KEY_PK_ID, newBookId);
                // always lookup the UUID (even if we inserted with a uuid... to protect against
                // future changes)
                //noinspection ConstantConditions
                book.putString(KEY_BOOK_UUID, getBookUuid(newBookId));

                // next we add the links to series, authors,...
                insertBookLinks(context, book, flags);

                // and populate the search suggestions table
                ftsInsert(context, newBookId);

                if (txLock != null) {
                    mSyncedDb.setTransactionSuccessful();
                }
                // all done
                return newBookId;

            } else {
                book.putLong(KEY_PK_ID, 0);
                book.remove(KEY_BOOK_UUID);
                throw new DaoWriteException(ERROR_CREATING_BOOK_FROM + book);
            }

        } catch (@NonNull final IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_CREATING_BOOK_FROM + book, e);

        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * Update the given {@link Book}.
     * This will update <strong>ONLY</strong> the fields present in the passed in Book.
     * Non-present fields will not be touched. i.e. this is a delta operation.
     *
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set.
     *                May contain extra data which will be ignored.
     * @param flags   See {@link BookFlags} for flag definitions; {@code 0} for 'normal'.
     *
     * @throws DaoWriteException on failure
     */
    public void update(@NonNull final Context context,
                       @NonNull final Book book,
                       @BookFlags final int flags)
            throws DaoWriteException {

        SyncLock txLock = null;
        if (!mSyncedDb.inTransaction()) {
            txLock = mSyncedDb.beginTransaction(true);
        }

        try {
            book.preprocessForStoring(context, false);

            // correct field types if needed, and filter out fields we don't have in the db table.
            final ContentValues cv = filterValues(TBL_BOOKS, book, book.getLocale(context));

            // Disallow UUID updates
            if (cv.containsKey(KEY_BOOK_UUID)) {
                cv.remove(KEY_BOOK_UUID);
            }

            // set the KEY_DATE_LAST_UPDATED to 'now' if we're allowed,
            // or if it's not already present.
            if ((flags & BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT) == 0
                || !cv.containsKey(KEY_UTC_LAST_UPDATED)) {
                cv.put(KEY_UTC_LAST_UPDATED, LocalDateTime
                        .now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }


            // Reminder: We're updating ONLY the fields present in the ContentValues.
            // Other fields in the database row are not affected.
            // go !
            final boolean success = 0 < mSyncedDb.update(
                    TBL_BOOKS.getName(), cv, KEY_PK_ID + "=?",
                    new String[]{String.valueOf(book.getId())});

            if (success) {
                insertBookLinks(context, book, flags);
                ftsUpdate(context, book.getId());
                if (txLock != null) {
                    mSyncedDb.setTransactionSuccessful();
                }
            } else {
                throw new DaoWriteException(ERROR_UPDATING_BOOK_FROM + book);
            }
        } catch (@NonNull final IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_UPDATING_BOOK_FROM + book, e);

        } finally {
            if (txLock != null) {
                mSyncedDb.endTransaction(txLock);
            }
        }
    }

    /**
     * Return a ContentValues collection containing only those values from 'source'
     * that match columns in 'dest'.
     * <ul>
     *      <li>Exclude the primary key from the list of columns.</li>
     *      <li>data will be transformed based on the column definition.<br>
     *          e.g. if a columns says it's Integer, an incoming boolean will
     *          be transformed to 0/1</li>
     * </ul>
     *
     * @param tableDefinition destination table
     * @param dataManager     A collection with the columns to be set. May contain extra data.
     * @param bookLocale      the Locale to use for character case manipulation
     *
     * @return New and filtered ContentValues
     */
    @NonNull
    private ContentValues filterValues(@SuppressWarnings("SameParameterValue")
                                       @NonNull final TableDefinition tableDefinition,
                                       @NonNull final DataManager dataManager,
                                       @NonNull final Locale bookLocale) {

        final TableInfo tableInfo = tableDefinition.getTableInfo(mSyncedDb);

        final ContentValues cv = new ContentValues();
        // Create the arguments
        for (String key : dataManager.keySet()) {
            // Get column info for this column.
            final ColumnInfo columnInfo = tableInfo.getColumn(key);
            // Check if we actually have a matching column, and never update a PK.
            if (columnInfo != null && !columnInfo.isPrimaryKey()) {
                final Object entry = dataManager.get(key);
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
                                final String stringValue = entry.toString().trim();
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
                                final String s = entry.toString().toLowerCase(bookLocale);
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
     * Delete the given book (and its covers).
     *
     * @param context Current context
     * @param book    to delete
     *
     * @return {@code true} if a row was deleted
     */
    public boolean delete(@NonNull final Context context,
                          @NonNull final Book book) {
        return deleteBook(context, book.getId());
    }

    /**
     * Delete the given book (and its covers).
     *
     * @param context Current context
     * @param bookId  of the book.
     *
     * @return {@code true} if a row was deleted
     */
    public boolean deleteBook(@NonNull final Context context,
                              @IntRange(from = 1) final long bookId) {

        final String uuid = getBookUuid(bookId);
        // sanity check
        if (uuid == null) {
            return false;
        }

        int rowsAffected = 0;
        final SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            final SynchronizedStatement stmt = mSqlStatementManager.get(
                    STMT_DELETE_BOOK, () -> SqlDelete.BOOK_BY_ID);

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, bookId);
                rowsAffected = stmt.executeUpdateDelete();
            }

            if (rowsAffected > 0) {
                // sanity check
                if (!uuid.isEmpty()) {
                    // delete the thumbnail (if any) from file system
                    for (int cIdx = 0; cIdx < 2; cIdx++) {
                        final File thumb = AppDir.getCoverFile(context, uuid, cIdx);
                        FileUtils.delete(thumb);
                    }
                    // delete the thumbnail (if any) from cache
                    CoversDAO.delete(context, uuid);
                }
            }
            mSyncedDb.setTransactionSuccessful();
        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e, "Failed to delete book");
        } finally {
            mSyncedDb.endTransaction(txLock);
        }

        return rowsAffected == 1;
    }

    /**
     * Called during book insert & update.
     * Each step in this method will first delete all entries in the Book-[tableX] table
     * for this bookId, and then insert the new links.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set. May contain extra data.
     * @param flags   See {@link BookFlags} for flag definitions; {@code 0} for 'normal'.
     *
     * @throws DaoWriteException on failure
     */
    private void insertBookLinks(@NonNull final Context context,
                                 @NonNull final Book book,
                                 final int flags)
            throws DaoWriteException {

        // Only lookup locales when we're NOT in batch mode (i.e. NOT doing an import)
        final boolean lookupLocale = (flags & BOOK_FLAG_IS_BATCH_OPERATION) == 0;

        // unconditional lookup of the book locale!
        final Locale bookLocale = book.getLocale(context);

        if (book.contains(Book.BKEY_BOOKSHELF_ARRAY)) {
            // Bookshelves will be inserted if new, but not updated
            insertBookBookshelf(context,
                                book.getId(),
                                book.getParcelableArrayList(Book.BKEY_BOOKSHELF_ARRAY));
        }

        if (book.contains(Book.BKEY_AUTHOR_ARRAY)) {
            // Authors will be inserted if new, but not updated
            insertBookAuthors(context,
                              book.getId(),
                              book.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY),
                              lookupLocale,
                              bookLocale);
        }

        if (book.contains(Book.BKEY_SERIES_ARRAY)) {
            // Series will be inserted if new, but not updated
            insertBookSeries(context,
                             book.getId(),
                             book.getParcelableArrayList(Book.BKEY_SERIES_ARRAY),
                             lookupLocale,
                             bookLocale);
        }

        if (book.contains(Book.BKEY_PUBLISHER_ARRAY)) {
            // Publishers will be inserted if new, but not updated
            insertBookPublishers(context,
                                 book.getId(),
                                 book.getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY),
                                 lookupLocale,
                                 bookLocale);
        }

        if (book.contains(Book.BKEY_TOC_ARRAY)) {
            // TOC entries are two steps away; they can exist in other books
            // Hence we will both insert new entries
            // AND update existing ones if needed.
            saveTocList(context,
                        book.getId(),
                        book.getParcelableArrayList(Book.BKEY_TOC_ARRAY),
                        lookupLocale,
                        bookLocale);
        }

        if (book.contains(KEY_LOANEE)) {
            lendBook(book.getId(), book.getString(KEY_LOANEE));
        }
    }

    /**
     * Create the link between {@link Book} and {@link Bookshelf}.
     * {@link DBDefinitions#TBL_BOOK_BOOKSHELF}
     * <p>
     * The list is pruned before storage.
     * New shelves are added, existing ones are NOT updated.
     *
     * <strong>Transaction:</strong> required
     *
     * @param context Current context
     * @param bookId  of the book
     * @param list    the list of bookshelves
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    private void insertBookBookshelf(@NonNull final Context context,
                                     @IntRange(from = 1) final long bookId,
                                     @NonNull final Collection<Bookshelf> list)
            throws DaoWriteException {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(TransactionException.REQUIRED);
        }

        // fix id's and remove duplicates; shelves don't use a Locale, hence no lookup done.
        Bookshelf.pruneList(list, this);

        // Just delete all current links; we'll insert them from scratch.
        deleteBookBookshelfByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_BOOK_BOOKSHELF, () -> SqlInsert.BOOK_BOOKSHELF);

        for (Bookshelf bookshelf : list) {
            // create if needed - do NOT do updates here
            if (bookshelf.getId() == 0) {
                if (insert(context, bookshelf) == -1) {
                    throw new DaoWriteException("insert Bookshelf");
                }
            }

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, bookId);
                stmt.bindLong(2, bookshelf.getId());
                if (stmt.executeInsert() == -1) {
                    throw new DaoWriteException("insert Book-Bookshelf");
                }
            }
        }
    }

    /**
     * Delete the link between Bookshelves and the given Book.
     * Note that the actual Bookshelves are not deleted.
     *
     * @param bookId id of the book
     */
    private void deleteBookBookshelfByBookId(@IntRange(from = 1) final long bookId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_DELETE_BOOK_BOOKSHELF, () -> SqlDelete.BOOK_BOOKSHELF_BY_BOOK_ID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Create the link between {@link Book} and {@link Author}.
     * {@link DBDefinitions#TBL_BOOK_AUTHOR}
     * <p>
     * The list is pruned before storage.
     * New authors are added, existing ones are NOT updated.
     *
     * <strong>Transaction:</strong> required
     *
     * @param context      Current context
     * @param bookId       of the book
     * @param list         the list of authors
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertBookAuthors(@NonNull final Context context,
                           final long bookId,
                           @NonNull final Collection<Author> list,
                           final boolean lookupLocale,
                           @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(TransactionException.REQUIRED);
        }

        // fix id's and remove duplicates
        Author.pruneList(list, context, this, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch (easier positioning)
        deleteBookAuthorByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_BOOK_AUTHORS, () -> SqlInsert.BOOK_AUTHOR);

        int position = 0;
        for (Author author : list) {
            // create if needed - do NOT do updates here
            if (author.getId() == 0) {
                if (insert(context, author) == -1) {
                    throw new DaoWriteException("insert Author");
                }
            }

            position++;
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, bookId);
                stmt.bindLong(2, author.getId());
                stmt.bindLong(3, position);
                stmt.bindLong(4, author.getType());
                if (stmt.executeInsert() == -1) {
                    throw new DaoWriteException("insert Book-Author");
                }
            }
        }
    }

    /**
     * Delete the link between Authors and the given Book.
     * Note that the actual Authors are not deleted.
     *
     * @param bookId id of the book
     */
    private void deleteBookAuthorByBookId(@IntRange(from = 1) final long bookId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_DELETE_BOOK_AUTHORS, () -> SqlDelete.BOOK_AUTHOR_BY_BOOK_ID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Create the link between {@link Book} and {@link Series}.
     * {@link DBDefinitions#TBL_BOOK_SERIES}
     * <p>
     * The list is pruned before storage.
     * New series are added, existing ones are NOT updated.
     *
     * <strong>Transaction:</strong> required
     *
     * @param context      Current context
     * @param bookId       of the book
     * @param list         the list of Series
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertBookSeries(@NonNull final Context context,
                          final long bookId,
                          @NonNull final Collection<Series> list,
                          final boolean lookupLocale,
                          @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(TransactionException.REQUIRED);
        }

        // fix id's and remove duplicates
        Series.pruneList(list, context, this, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch.
        deleteBookSeriesByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_BOOK_SERIES, () -> SqlInsert.BOOK_SERIES);

        int position = 0;
        for (Series series : list) {
            // create if needed - do NOT do updates here
            if (series.getId() == 0) {
                if (insert(context, series, bookLocale) == -1) {
                    throw new DaoWriteException("insert Series");
                }
            }

            position++;
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, bookId);
                stmt.bindLong(2, series.getId());
                stmt.bindString(3, series.getNumber());
                stmt.bindLong(4, position);
                if (stmt.executeInsert() == -1) {
                    throw new DaoWriteException("insert Book-Series");
                }
            }
        }
    }

    /**
     * Delete the link between Series and the given Book.
     * Note that the actual Series are not deleted.
     *
     * @param bookId id of the book
     */
    private void deleteBookSeriesByBookId(@IntRange(from = 1) final long bookId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_DELETE_BOOK_SERIES, () -> SqlDelete.BOOK_SERIES_BY_BOOK_ID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Create the link between {@link Book} and {@link Publisher}.
     * {@link DBDefinitions#TBL_BOOK_PUBLISHER}
     * <p>
     * The list is pruned before storage.
     * New Publishers are added, existing ones are NOT updated.
     *
     * <strong>Transaction:</strong> required
     *
     * @param context      Current context
     * @param bookId       of the book
     * @param list         the list of Publishers
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertBookPublishers(@NonNull final Context context,
                              final long bookId,
                              @NonNull final Collection<Publisher> list,
                              final boolean lookupLocale,
                              @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(TransactionException.REQUIRED);
        }

        // fix id's and remove duplicates
        Publisher.pruneList(list, context, this, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch.
        deleteBookPublishersByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_BOOK_PUBLISHER, () -> SqlInsert.BOOK_PUBLISHER);

        int position = 0;
        for (Publisher publisher : list) {
            // create if needed - do NOT do updates here
            if (publisher.getId() == 0) {
                if (insert(context, publisher, bookLocale) == -1) {
                    throw new DaoWriteException("insert Publisher");
                }
            }

            position++;
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, bookId);
                stmt.bindLong(2, publisher.getId());
                stmt.bindLong(3, position);
                if (stmt.executeInsert() == -1) {
                    throw new DaoWriteException("insert Book-Publisher");
                }
            }
        }
    }

    /**
     * Delete the link between Publisher and the given Book.
     * Note that the actual Publisher are not deleted.
     *
     * @param bookId id of the book
     */
    private void deleteBookPublishersByBookId(@IntRange(from = 1) final long bookId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_DELETE_BOOK_PUBLISHER, () -> SqlDelete.BOOK_PUBLISHER_BY_BOOK_ID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Delete the link between TocEntry's and the given Book.
     * Note that the actual TocEntry's are NOT deleted here.
     *
     * @param bookId id of the book
     */
    private void deleteBookTocEntryByBookId(@IntRange(from = 1) final long bookId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_DELETE_BOOK_TOC_ENTRIES, () -> SqlDelete.BOOK_TOC_ENTRIES_BY_BOOK_ID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Update a bookshelf.
     *
     * @param context   Current context
     * @param bookshelf to update
     *
     * @return {@code true} for success.
     */
    public boolean update(@NonNull final Context context,
                          @NonNull final Bookshelf bookshelf) {

        // validate the style first
        final long styleId = bookshelf.getStyle(context, this).getId();

        final ContentValues cv = new ContentValues();
        cv.put(KEY_BOOKSHELF_NAME, bookshelf.getName());
        cv.put(KEY_BOOKSHELF_BL_TOP_POS, bookshelf.getTopItemPosition());
        cv.put(KEY_BOOKSHELF_BL_TOP_OFFSET, bookshelf.getTopViewOffset());

        cv.put(KEY_FK_STYLE, styleId);

        return 0 < mSyncedDb.update(
                TBL_BOOKSHELF.getName(), cv, KEY_PK_ID + "=?",
                new String[]{String.valueOf(bookshelf.getId())});
    }

    /**
     * Creates a new bookshelf in the database.
     *
     * @param context   Current context
     * @param bookshelf object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final Context context,
                       @NonNull final Bookshelf /* in/out */ bookshelf) {

        // validate the style first
        final long styleId = bookshelf.getStyle(context, this).getId();

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlInsert.BOOKSHELF)) {
            stmt.bindString(1, bookshelf.getName());
            stmt.bindLong(2, styleId);
            stmt.bindLong(3, bookshelf.getTopItemPosition());
            stmt.bindLong(4, bookshelf.getTopViewOffset());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                bookshelf.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Delete the passed {@link Bookshelf}.
     * Cleans up {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE} as well.
     *
     * @param bookshelf to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Bookshelf bookshelf) {

        final int rowsAffected;

        final SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            purgeNodeStatesByBookshelf(bookshelf.getId());

            try (SynchronizedStatement stmt = mSyncedDb
                    .compileStatement(SqlDelete.BOOKSHELF_BY_ID)) {
                stmt.bindLong(1, bookshelf.getId());
                rowsAffected = stmt.executeUpdateDelete();
            }
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(txLock);
        }

        if (rowsAffected > 0) {
            // and make the object 'new'
            bookshelf.setId(0);
        }

        return rowsAffected == 1;
    }

    /**
     * Update an Author.
     *
     * @param context Current context
     * @param author  to update
     *
     * @return {@code true} for success.
     */
    public boolean update(@NonNull final Context context,
                          @NonNull final Author author) {

        final Locale authorLocale =
                author.getLocale(context, this, AppLocale.getInstance().getUserLocale(context));

        final ContentValues cv = new ContentValues();
        cv.put(KEY_AUTHOR_FAMILY_NAME, author.getFamilyName());
        cv.put(KEY_AUTHOR_FAMILY_NAME_OB,
               encodeOrderByColumn(author.getFamilyName(), authorLocale));
        cv.put(KEY_AUTHOR_GIVEN_NAMES, author.getGivenNames());
        cv.put(KEY_AUTHOR_GIVEN_NAMES_OB,
               encodeOrderByColumn(author.getGivenNames(), authorLocale));
        cv.put(KEY_AUTHOR_IS_COMPLETE, author.isComplete());

        return 0 < mSyncedDb.update(
                TBL_AUTHORS.getName(), cv, KEY_PK_ID + "=?",
                new String[]{String.valueOf(author.getId())});
    }

    /**
     * Creates a new {@link Author}.
     *
     * @param context Current context
     * @param author  object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted Author, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final Context context,
                       @NonNull final Author /* in/out */ author) {

        final Locale authorLocale =
                author.getLocale(context, this, AppLocale.getInstance().getUserLocale(context));

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_AUTHOR, () -> SqlInsert.AUTHOR);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, author.getFamilyName());
            stmt.bindString(2, encodeOrderByColumn(author.getFamilyName(), authorLocale));
            stmt.bindString(3, author.getGivenNames());
            stmt.bindString(4, encodeOrderByColumn(author.getGivenNames(), authorLocale));
            stmt.bindBoolean(5, author.isComplete());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                author.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Refresh the passed Author from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the Author.
     * <p>
     * Will NOT insert a new Author if not found.
     *
     * @param context    Current context
     * @param author     to refresh
     * @param bookLocale Locale to use if the item has none set
     */
    public void refresh(@NonNull final Context context,
                        @NonNull final Author /* out */ author,
                        @NonNull final Locale bookLocale) {

        if (author.getId() == 0) {
            // It wasn't saved before; see if it is now. If so, update ID.
            author.fixId(context, this, true, bookLocale);

        } else {
            // It was saved, see if it still is and fetch possibly updated fields.
            final Author dbAuthor = getAuthor(author.getId());
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
     * Update a Series.
     *
     * @param context    Current context
     * @param series     to update
     * @param bookLocale Locale to use if the item has none set
     *
     * @return {@code true} for success.
     */
    public boolean update(@NonNull final Context context,
                          @NonNull final Series series,
                          @NonNull final Locale bookLocale) {

        final Locale seriesLocale = series.getLocale(context, this, bookLocale);

        final String obTitle = series.reorderTitleForSorting(context, seriesLocale);

        final ContentValues cv = new ContentValues();
        cv.put(KEY_SERIES_TITLE, series.getTitle());
        cv.put(KEY_SERIES_TITLE_OB, encodeOrderByColumn(obTitle, seriesLocale));
        cv.put(KEY_SERIES_IS_COMPLETE, series.isComplete());

        return 0 < mSyncedDb.update(
                TBL_SERIES.getName(), cv, KEY_PK_ID + "=?",
                new String[]{String.valueOf(series.getId())});
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
    public long insert(@NonNull final Context context,
                       @NonNull final Series /* in/out */ series,
                       @NonNull final Locale bookLocale) {

        final Locale seriesLocale = series.getLocale(context, this, bookLocale);

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_SERIES, () -> SqlInsert.SERIES);

        final String obTitle = series.reorderTitleForSorting(context, seriesLocale);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, series.getTitle());
            stmt.bindString(2, encodeOrderByColumn(obTitle, seriesLocale));
            stmt.bindBoolean(3, series.isComplete());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                series.setId(iId);
            }
            return iId;
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
    public void refresh(@NonNull final Context context,
                        @NonNull final Series /* out */ series,
                        @NonNull final Locale bookLocale) {

        if (series.getId() == 0) {
            // It wasn't saved before; see if it is now. If so, update ID.
            series.fixId(context, this, true, bookLocale);

        } else {
            // It was saved, see if it still is and fetch possibly updated fields.
            final Series dbSeries = getSeries(series.getId());
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
     * Delete the passed {@link Series}.
     *
     * @param context Current context
     * @param series  to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Context context,
                          @NonNull final Series series) {

        final int rowsAffected;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlDelete.SERIES_BY_ID)) {
            stmt.bindLong(1, series.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            // look for books affected by the delete, and re-position their book/series links.
            new DBCleaner(this).bookSeries(context);
            // and make the object 'new'
            series.setId(0);
        }
        return rowsAffected == 1;
    }

    /**
     * Update a Publisher.
     *
     * @param context    Current context
     * @param publisher  to update
     * @param bookLocale Locale to use if the item has none set
     *
     * @return {@code true} for success.
     */
    public boolean update(@NonNull final Context context,
                          @NonNull final Publisher publisher,
                          @NonNull final Locale bookLocale) {

        final Locale publisherLocale = publisher.getLocale(context, bookLocale);

        final String obTitle = publisher.reorderTitleForSorting(context, publisherLocale);

        final ContentValues cv = new ContentValues();
        cv.put(KEY_PUBLISHER_NAME, publisher.getName());
        cv.put(KEY_PUBLISHER_NAME_OB, encodeOrderByColumn(obTitle, publisherLocale));

        return 0 < mSyncedDb.update(
                TBL_PUBLISHERS.getName(), cv, KEY_PK_ID + "=?",
                new String[]{String.valueOf(publisher.getId())});
    }

    /**
     * Creates a new Publisher in the database.
     *
     * @param context    Current context
     * @param publisher  object to insert. Will be updated with the id.
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final Context context,
                       @NonNull final Publisher /* in/out */ publisher,
                       @NonNull final Locale bookLocale) {

        final Locale publisherLocale = publisher.getLocale(context, bookLocale);

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_PUBLISHER, () -> SqlInsert.PUBLISHER);

        final String obTitle = publisher.reorderTitleForSorting(context, publisherLocale);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, publisher.getName());
            stmt.bindString(2, encodeOrderByColumn(obTitle, publisherLocale));
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                publisher.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Refresh the passed Publisher from the database, if present. Used to ensure that
     * the current record matches the current DB if some other task may have
     * changed the Publisher.
     * <p>
     * Will NOT insert a new Publisher if not found.
     *
     * @param context    Current context
     * @param publisher  to refresh
     * @param bookLocale Locale to use if the item has none set
     */
    public void refresh(@NonNull final Context context,
                        @NonNull final Publisher /* out */ publisher,
                        @NonNull final Locale bookLocale) {

        if (publisher.getId() == 0) {
            // It wasn't saved before; see if it is now. If so, update ID.
            publisher.fixId(context, this, true, bookLocale);

        } else {
            // It was saved, see if it still is and fetch possibly updated fields.
            final Publisher dbPublisher = getPublisher(publisher.getId());
            if (dbPublisher != null) {
                // copy any updated fields
                publisher.copyFrom(dbPublisher);
            } else {
                // not found?, set as 'new'
                publisher.setId(0);
            }
        }
    }

    /**
     * Delete the passed {@link Author}.
     *
     * @param context Current context
     * @param author  to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Context context,
                          @NonNull final Author author) {

        final int rowsAffected;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlDelete.AUTHOR_BY_ID)) {
            stmt.bindLong(1, author.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            // look for books affected by the delete, and re-position their book/author links.
            new DBCleaner(this).bookAuthor(context);
            // and make the object 'new'
            author.setId(0);
        }
        return rowsAffected == 1;
    }

    /**
     * Delete the passed {@link Publisher}.
     *
     * @param context   Current context
     * @param publisher to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Context context,
                          @NonNull final Publisher publisher) {

        final int rowsAffected;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlDelete.PUBLISHER_BY_ID)) {
            stmt.bindLong(1, publisher.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            // look for books affected by the delete, and re-position their book/publisher links.
            new DBCleaner(this).bookPublisher(context);
            // and make the object 'new'
            publisher.setId(0);
        }
        return rowsAffected == 1;
    }

    /**
     * Saves a list of {@link TocEntry} items.
     * <ol>
     *     <li>The list is pruned first.</li>
     *     <li>New authors will be inserted. No updates.</li>
     *     <li>TocEntry's existing in the database will be updated, new ones inserted.</li>
     *     <li>Creates the links between {@link Book} and {@link TocEntry}
     *         in {@link DBDefinitions#TBL_BOOK_TOC_ENTRIES}</li>
     * </ol>
     *
     * <strong>Transaction:</strong> required
     *
     * @param context      Current context
     * @param bookId       of the book
     * @param list         the list of TocEntry
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void saveTocList(@NonNull final Context context,
                     final long bookId,
                     @NonNull final Collection<TocEntry> list,
                     final boolean lookupLocale,
                     @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(TransactionException.REQUIRED);
        }

        // fix id's and remove duplicates
        TocEntry.pruneList(list, context, this, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch (easier positioning)
        deleteBookTocEntryByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_BOOK_TOC_ENTRY, () -> SqlInsert.BOOK_TOC_ENTRY);

        long position = 0;
        for (TocEntry tocEntry : list) {
            // Author must be handled separately; the id will already have been 'fixed'
            // during the TocEntry.pruneList call.
            // Create if needed - do NOT do updates here
            final Author author = tocEntry.getAuthor();
            if (author.getId() == 0) {
                if (insert(context, author) == -1) {
                    throw new DaoWriteException("insert Author");
                }
            }

            // See method doc, we do both inserts and updates
            if (tocEntry.getId() == 0) {
                if (insert(context, tocEntry, bookLocale) == -1) {
                    throw new DaoWriteException("insert TocEntry");
                }
            } else {
                if (!update(context, tocEntry, bookLocale)) {
                    throw new DaoWriteException("update TocEntry");
                }
            }

            // create the book<->TocEntry link.
            //
            // As we delete all links before insert/update'ng above, we normally
            // *always* need to re-create the link here.
            // However, this will fail if we inserted "The Universe" and updated "Universe, The"
            // as the former will be stored as "Universe, The" so conflicting with the latter.
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
                    if (stmt.executeInsert() == -1) {
                        throw new DaoWriteException("insert Book-TocEntry");
                    }
                }
            } catch (@NonNull final SQLiteConstraintException ignore) {
                // ignore and reset the position counter.
                position--;

                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "updateOrInsertTOC"
                               + "|SQLiteConstraintException"
                               + "|tocEntry=" + tocEntry.getId()
                               + "|bookId=" + bookId);
                }
            }
        }
    }

    /**
     * Update a TocEntry. Does <strong>NOT</strong> update the author id.
     *
     * @param context    Current context
     * @param tocEntry   to update
     * @param bookLocale Locale to use if the item has none set
     *
     * @return {@code true} for success.
     */
    private boolean update(@NonNull final Context context,
                           @NonNull final TocEntry tocEntry,
                           @NonNull final Locale bookLocale) {

        final Locale tocLocale = tocEntry.getLocale(context, this, bookLocale);

        final String obTitle = tocEntry.reorderTitleForSorting(context, tocLocale);

        // We cannot update the author as it's part of the primary key.
        // (we should never even get here if the author was changed)
        final ContentValues cv = new ContentValues();
        cv.put(KEY_TITLE, tocEntry.getTitle());
        cv.put(KEY_TITLE_OB, encodeOrderByColumn(obTitle, tocLocale));
        cv.put(KEY_DATE_FIRST_PUBLICATION, tocEntry.getFirstPublication());

        return 0 < mSyncedDb.update(
                TBL_TOC_ENTRIES.getName(), cv, KEY_PK_ID + "=?",
                new String[]{String.valueOf(tocEntry.getId())});
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
    private long insert(@NonNull final Context context,
                        @NonNull final TocEntry /* in/out */ tocEntry,
                        @NonNull final Locale bookLocale) {

        final Locale tocLocale = tocEntry.getLocale(context, this, bookLocale);

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_TOC_ENTRY, () -> SqlInsert.TOC_ENTRY);

        final String obTitle = tocEntry.reorderTitleForSorting(context, tocLocale);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, tocEntry.getAuthor().getId());
            stmt.bindString(2, tocEntry.getTitle());
            stmt.bindString(3, encodeOrderByColumn(obTitle, tocLocale));
            stmt.bindString(4, tocEntry.getFirstPublication());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                tocEntry.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Delete the passed {@link TocEntry}.
     *
     * @param context  Current context
     * @param tocEntry to delete.
     *
     * @return {@code true} if a row was deleted
     */
    public boolean delete(@NonNull final Context context,
                          @NonNull final TocEntry tocEntry) {

        final int rowsAffected;
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlDelete.TOC_ENTRY)) {
            stmt.bindLong(1, tocEntry.getId());
            rowsAffected = stmt.executeUpdateDelete();
        }

        if (rowsAffected > 0) {
            // look for books affected by the delete, and re-position their book/tocentry links.
            new DBCleaner(this).bookTocEntry(context);
            // and make the object 'new'
            tocEntry.setId(0);
        }

        return rowsAffected == 1;
    }

    /**
     * Lend out a book / return a book.
     *
     * @param bookId book to lend
     * @param loanee person to lend to; set to {@code null} or {@code ""} to delete the loan
     *
     * @return {@code true} for success.
     */
    public boolean lendBook(final long bookId,
                            @Nullable final String loanee) {
        if (loanee == null || loanee.isEmpty()) {
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(
                    SqlDelete.BOOK_LOANEE_BY_BOOK_ID)) {
                stmt.bindLong(1, bookId);
                return stmt.executeUpdateDelete() == 1;
            }
        }

        final String current = getLoaneeByBookId(bookId);
        if (current == null || current.isEmpty()) {
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlInsert.BOOK_LOANEE)) {
                stmt.bindLong(1, bookId);
                stmt.bindString(2, loanee);
                return stmt.executeInsert() > 0;
            }

        } else if (!loanee.equals(current)) {
            final ContentValues cv = new ContentValues();
            cv.put(KEY_LOANEE, loanee);
            return 0 < mSyncedDb.update(
                    TBL_BOOK_LOANEE.getName(), cv, KEY_FK_BOOK + "=?",
                    new String[]{String.valueOf(bookId)});
        }

        return true;
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
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_LOANEE_BY_BOOK_ID, () -> SqlGet.LOANEE_BY_BOOK_ID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    /**
     * Returns a unique list of all loanee in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getLoanees() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlAutoCompleteText.LOANEE, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Create a new {@link BooklistStyle}.
     *
     * @param style object to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final BooklistStyle /* in/out */ style) {
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlInsert.BOOKLIST_STYLE)) {
            stmt.bindString(1, style.getUuid());
            stmt.bindBoolean(2, !style.isUserDefined());
            final long iId = stmt.executeInsert();
            if (iId > 0) {
                style.setId(iId);
            }
            return iId;
        }
    }

    /**
     * Delete a {@link BooklistStyle}.
     * Cleans up {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE} as well.
     *
     * @param style to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final BooklistStyle style) {

        final int rowsAffected;

        final SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            purgeNodeStatesByStyle(style.getId());

            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlDelete.STYLE_BY_ID)) {
                stmt.bindLong(1, style.getId());
                rowsAffected = stmt.executeUpdateDelete();
            }
            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(txLock);
        }

        return rowsAffected == 1;
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
            try (SynchronizedStatement stmt =
                         mSyncedDb.compileStatement(SqlDelete.PURGE_AUTHORS)) {
                stmt.executeUpdateDelete();
            }
            try (SynchronizedStatement stmt =
                         mSyncedDb.compileStatement(SqlDelete.PURGE_SERIES)) {
                stmt.executeUpdateDelete();
            }
            try (SynchronizedStatement stmt =
                         mSyncedDb.compileStatement(SqlDelete.PURGE_PUBLISHERS)) {
                stmt.executeUpdateDelete();
            }

            // Note: purging TocEntry's is automatic due to foreign key cascading.
            // i.e. a TocEntry is linked directly with authors;
            // and linked with books via a link table.

            mSyncedDb.analyze();

        } catch (@NonNull final RuntimeException e) {
            // log to file, this is bad.
            Logger.error(App.getAppContext(), TAG, e);
        }
    }

    /**
     * Purge Booklist node state data for the given Bookshelf.<br>
     * Called when a Bookshelf is deleted or manually from the Bookshelf management context menu.
     *
     * @param bookshelfId to purge
     */
    public void purgeNodeStatesByBookshelf(final long bookshelfId) {
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(
                SqlDelete.BOOK_LIST_NODE_STATE_BY_BOOKSHELF)) {
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
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(
                SqlDelete.BOOK_LIST_NODE_STATE_BY_STYLE)) {
            stmt.bindLong(1, styleId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Count all books.
     *
     * @return number of books
     */
    public long countBooks() {
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlCount.BOOKS)) {
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Count the books for the given Author.
     *
     * @param context    Current context
     * @param author     to retrieve
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of {@link Book} this {@link Author} has
     */
    public long countBooksByAuthor(@NonNull final Context context,
                                   @NonNull final Author author,
                                   @NonNull final Locale bookLocale) {
        if (author.getId() == 0 && author.fixId(context, this, true, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlCount.BOOKS_BY_AUTHOR)) {
            stmt.bindLong(1, author.getId());
            return stmt.simpleQueryForLongOrZero();
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
    public long countBooksBySeries(@NonNull final Context context,
                                   @NonNull final Series series,
                                   @NonNull final Locale bookLocale) {
        if (series.getId() == 0 && series.fixId(context, this, true, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlCount.BOOKS_IN_SERIES)) {
            stmt.bindLong(1, series.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Count the books for the given Publisher.
     *
     * @param context    Current context
     * @param publisher  to retrieve
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of {@link Book} this {@link Publisher} has
     */
    public long countBooksByPublisher(@NonNull final Context context,
                                      @NonNull final Publisher publisher,
                                      @NonNull final Locale bookLocale) {
        if (publisher.getId() == 0 && publisher.fixId(context, this, true, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlCount.BOOKS_BY_PUBLISHER)) {
            stmt.bindLong(1, publisher.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Count the TocEntry's for the given Author.
     *
     * @param context    Current context
     * @param author     to count the TocEntries of
     * @param bookLocale Locale to use if the item has none set
     *
     * @return the number of {@link TocEntry} this {@link Author} has
     */
    public long countTocEntryByAuthor(@NonNull final Context context,
                                      @NonNull final Author author,
                                      @NonNull final Locale bookLocale) {
        if (author.getId() == 0 && author.fixId(context, this, true, bookLocale) == 0) {
            return 0;
        }

        try (SynchronizedStatement stmt =
                     mSyncedDb.compileStatement(SqlCount.TOC_ENTRIES_BY_AUTHOR)) {
            stmt.bindLong(1, author.getId());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Return an Cursor with all Books selected by the passed arguments.
     *
     * @param whereClause   without the 'where' keyword, can be {@code null} or {@code ""}
     * @param selectionArgs You may include ?s in where clause in the query,
     *                      which will be replaced by the values from selectionArgs. The
     *                      values will be bound as Strings.
     * @param orderByClause without the 'order by' keyword, can be {@code null} or {@code ""}
     *
     * @return A Book Cursor with 0..1 row
     */
    private TypedCursor getBookCursor(@Nullable final CharSequence whereClause,
                                      @Nullable final String[] selectionArgs,
                                      @Nullable final CharSequence orderByClause) {

        final String sql = SqlSelectFullTable.SQL_BOOK
                           + (whereClause != null && whereClause.length() > 0
                              ? _WHERE_ + whereClause : "")
                           + (orderByClause != null && orderByClause.length() > 0
                              ? _ORDER_BY_ + orderByClause : "")

                           + _COLLATION;

        final TypedCursor cursor = (TypedCursor) mSyncedDb.rawQueryWithFactory(
                EXT_CURSOR_FACTORY, sql, selectionArgs, null);
        // force the TypedCursor to retrieve the real column types.
        cursor.setDb(mSyncedDb, TBL_BOOKS);
        return cursor;
    }

    /**
     * Return a Cursor with the Book for the given {@link Book} id.
     *
     * @param bookId to retrieve
     *
     * @return A Book Cursor with 0..1 row
     */
    @NonNull
    public TypedCursor fetchBookById(@IntRange(from = 1) final long bookId) {
        return getBookCursor(TBL_BOOKS.dot(KEY_PK_ID) + "=?",
                             new String[]{String.valueOf(bookId)},
                             null);
    }

    /**
     * Return an Cursor with all Books for the given external book ID.
     * <strong>Note:</strong> MAY RETURN MORE THAN ONE BOOK
     *
     * @param key        to use
     * @param externalId to retrieve
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    public TypedCursor fetchBooksByKey(@NonNull final String key,
                                       @NonNull final String externalId) {
        return getBookCursor(TBL_BOOKS.dot(key) + "=?", new String[]{externalId},
                             TBL_BOOKS.dot(KEY_PK_ID));
    }

    /**
     * Return an Cursor with all Books for the given list of {@link Book} ID's.
     *
     * @param idList List of book ID's to retrieve; should not be empty!
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     *
     * @throws IllegalArgumentException if the list is empty
     */
    @NonNull
    public TypedCursor fetchBooks(@NonNull final List<Long> idList) {
        if (idList.isEmpty()) {
            throw new IllegalArgumentException(ErrorMsg.EMPTY_ARRAY_OR_LIST);
        }

        if (idList.size() == 1) {
            // optimize for single book
            return getBookCursor(TBL_BOOKS.dot(KEY_PK_ID) + "=?",
                                 new String[]{String.valueOf(idList.get(0))},
                                 null);

        } else {
            return getBookCursor(TBL_BOOKS.dot(KEY_PK_ID)
                                 + " IN (" + TextUtils.join(",", idList) + ')',
                                 null,
                                 TBL_BOOKS.dot(KEY_PK_ID));
        }
    }

    /**
     * Return an Cursor with all Books where the {@link Book} id > the given id.
     * Pass in {@code 0} for all books.
     *
     * @param fromBookIdOnwards the lowest book id to start from.
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    public TypedCursor fetchBooks(final long fromBookIdOnwards) {
        return getBookCursor(TBL_BOOKS.dot(KEY_PK_ID) + ">=?",
                             new String[]{String.valueOf(fromBookIdOnwards)},
                             TBL_BOOKS.dot(KEY_PK_ID));
    }

    /**
     * Return an Cursor with all Books, or with all updated Books since the given date/time.
     *
     * @param lastUpdateInUtc to select all books added/modified since that date/time (UTC based).
     *                        Set to {@code null} for *all* books.
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    public TypedCursor fetchBooksForExport(@Nullable final LocalDateTime lastUpdateInUtc) {
        if (lastUpdateInUtc == null) {
            return getBookCursor(null, null, TBL_BOOKS.dot(KEY_PK_ID));
        } else {
            return getBookCursor(
                    TBL_BOOKS.dot(KEY_UTC_LAST_UPDATED) + ">?",
                    new String[]{lastUpdateInUtc.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)},
                    TBL_BOOKS.dot(KEY_PK_ID));
        }
    }

    /**
     * Return an Cursor with all Books for the given list of ISBN numbers.
     *
     * @param isbnList list of ISBN numbers; should not be empty!
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     *
     * @throws IllegalArgumentException if the list is empty
     */
    @NonNull
    public TypedCursor fetchBooksByIsbnList(@NonNull final List<String> isbnList) {
        if (isbnList.isEmpty()) {
            throw new IllegalArgumentException(ErrorMsg.EMPTY_ARRAY_OR_LIST);
        }

        if (isbnList.size() == 1) {
            // optimize for single book
            return getBookCursor(TBL_BOOKS.dot(KEY_ISBN) + "=?",
                                 new String[]{encodeString(isbnList.get(0))},
                                 null);
        } else {
            return getBookCursor(
                    TBL_BOOKS.dot(KEY_ISBN)
                    + " IN (" + Csv.join(isbnList, s -> '\'' + encodeString(s) + '\'') + ')',
                    null,
                    TBL_BOOKS.dot(KEY_PK_ID));
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
        return mSyncedDb.rawQuery(SqlSelectFullTable.ALL_BOOK_UUID, null);
    }

    /**
     * Get all Bookshelves; mainly for the purpose of backups.
     * <strong>Note:</strong> we do not include the 'All Books' shelf.
     *
     * @return Cursor over all Bookshelves
     */
    @NonNull
    public Cursor fetchBookshelves() {
        return mSyncedDb.rawQuery(SqlSelectFullTable.BOOKSHELVES_USER_SHELVES, null);
    }

    /**
     * Get all Authors; mainly for the purpose of backups.
     *
     * @return Cursor over all Authors
     */
    @NonNull
    public Cursor fetchAuthors() {
        return mSyncedDb.rawQuery(SqlSelectFullTable.AUTHORS, null);
    }

    /**
     * Get all series; mainly for the purpose of backups.
     *
     * @return Cursor over all series
     */
    @NonNull
    public Cursor fetchSeries() {
        return mSyncedDb.rawQuery(SqlSelectFullTable.SERIES, null);
    }

    /**
     * Get all publishers; mainly for the purpose of backups.
     *
     * @return Cursor over all publishers
     */
    @NonNull
    public Cursor fetchPublishers() {
        return mSyncedDb.rawQuery(SqlSelectFullTable.PUBLISHERS, null);
    }

    /**
     * Get all TOC entries; mainly for the purpose of backups.
     *
     * @return Cursor over all TOC entries
     */
    @NonNull
    public Cursor fetchTocs() {
        return mSyncedDb.rawQuery(SqlSelectFullTable.TOC_ENTRIES, null);
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
        final ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = fetchBookshelves()) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    /**
     * Get a unique list of author names in the specified format.
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
                return getColumnAsList(SqlAutoCompleteText.AUTHORS_FAMILY_NAMES, key);

            case KEY_AUTHOR_GIVEN_NAMES:
                return getColumnAsList(SqlAutoCompleteText.AUTHORS_GIVEN_NAMES, key);

            case KEY_AUTHOR_FORMATTED:
                return getColumnAsList(SqlAutoCompleteText.AUTHORS_FORMATTED_NAMES, key);

            case KEY_AUTHOR_FORMATTED_GIVEN_FIRST:
                return getColumnAsList(SqlAutoCompleteText.AUTHORS_FORMATTED_NAMES_GIVEN_FIRST,
                                       key);

            default:
                throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + key);
        }
    }

    /**
     * Get a unique list of all series titles.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getSeriesTitles() {
        return getColumnAsList(SqlAutoCompleteText.SERIES_NAME, KEY_SERIES_TITLE);
    }

    /**
     * Get a unique list of all publisher names.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getPublisherNames() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlAutoCompleteText.PUBLISHERS_NAME, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Get a unique list of all colors.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getColors() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlAutoCompleteText.COLORS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Get a unique list of all currencies for the specified domain.
     *
     * @param domainName for which to collect the used currency codes
     *
     * @return The list; values are always in uppercase.
     */
    @NonNull
    public ArrayList<String> getCurrencyCodes(@NonNull final String domainName) {
        final String column;
        if (DBDefinitions.KEY_PRICE_LISTED_CURRENCY.equals(domainName)) {
            column = DBDefinitions.KEY_PRICE_LISTED_CURRENCY;
//        } else if (DBDefinitions.KEY_PRICE_PAID_CURRENCY.equals(type)) {
        } else {
            column = DBDefinitions.KEY_PRICE_PAID_CURRENCY;
        }

        final String sql = "SELECT DISTINCT upper(" + column + ") FROM " + TBL_BOOKS.getName()
                           + _ORDER_BY_ + column + _COLLATION;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            final ArrayList<String> list = getFirstColumnAsList(cursor);
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
     * Get a unique list of all formats.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getFormats() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlAutoCompleteText.FORMATS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Get a unique list of all genres.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getGenres() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlAutoCompleteText.GENRES, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Get a unique list of all language codes.
     * The list is ordered by {@link DBDefinitions#KEY_UTC_LAST_UPDATED}.
     *
     * @return The list; normally all ISO codes
     */
    @NonNull
    public ArrayList<String> getLanguageCodes() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlAutoCompleteText.LANGUAGES, null)) {
            return getFirstColumnAsList(cursor);
        }
    }

    /**
     * Get a unique list of all languages.
     * The list is ordered by {@link DBDefinitions#KEY_UTC_LAST_UPDATED}.
     *
     * @param context Current context
     *
     * @return The list; resolved/localized names; may contain custom names.
     */
    @NonNull
    public ArrayList<String> getLanguages(@NonNull final Context context) {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlAutoCompleteText.LANGUAGES, null)) {
            // Using a Set to avoid duplicates
            // The cursor is distinct, but we need to make sure code->name does not create
            // duplicates (very unlikely, but not impossible)
            final Set<String> set = new LinkedHashSet<>();
            while (cursor.moveToNext()) {
                final String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) {
                    set.add(Languages.getInstance().getDisplayNameFromISO3(context, name));
                }
            }
            return new ArrayList<>(set);
        }
    }

    /**
     * Get a unique list of all locations.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getLocations() {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlAutoCompleteText.LOCATIONS, null)) {
            return getFirstColumnAsList(cursor);
        }
    }


    public void updateColor(@NonNull final String from,
                            @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlUpdate.COLOR)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    public void updateFormat(@NonNull final String from,
                             @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlUpdate.FORMAT)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    public void updateGenre(@NonNull final String from,
                            @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlUpdate.GENRE)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    public void updateLanguage(@NonNull final String from,
                               @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlUpdate.LANGUAGE)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }

    public void updateLocation(@NonNull final String from,
                               @NonNull final String to) {
        if (Objects.equals(from, to)) {
            return;
        }
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SqlUpdate.LOCATION)) {
            stmt.bindString(1, to);
            stmt.bindString(2, from);
            stmt.executeUpdateDelete();
        }
    }


    /**
     * Find a {@link Bookshelf} by using the appropriate fields of the passed {@link Bookshelf}.
     * The incoming object is not modified.
     *
     * @param bookshelf to find the id of
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getBookshelfId(@NonNull final Bookshelf bookshelf) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_BOOKSHELF_ID_BY_NAME, () -> SqlGetId.BOOKSHELF_ID_BY_NAME);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, bookshelf.getName());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get the {@link Bookshelf} based on the given id.
     *
     * <strong>Don't call directly; instead use a static method in {@link Bookshelf}</strong>.
     *
     * @param id of Bookshelf to find
     *
     * @return the {@link Bookshelf}, or {@code null} if not found
     */
    @Nullable
    public Bookshelf getBookshelf(final long id) {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlGet.BOOKSHELF,
                                                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Bookshelf(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    /**
     * Find a {@link Bookshelf} with the given name.
     *
     * @param name of bookshelf to find
     *
     * @return the Bookshelf, or {@code null} if not found
     */
    @Nullable
    public Bookshelf getBookshelfByName(@NonNull final String name) {

        try (Cursor cursor = mSyncedDb.rawQuery(SqlGet.BOOKSHELF_BY_NAME,
                                                new String[]{name})) {
            if (cursor.moveToFirst()) {
                final DataHolder rowData = new CursorRow(cursor);
                return new Bookshelf(rowData.getLong(KEY_PK_ID), rowData);
            } else {
                return null;
            }
        }
    }

    /**
     * Get a list of all the bookshelves this book is on.
     *
     * @param bookId to use
     *
     * @return the list
     */
    @NonNull
    public ArrayList<Bookshelf> getBookshelvesByBookId(final long bookId) {
        ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.BOOKSHELVES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(KEY_PK_ID), rowData));
            }
            return list;
        }
    }


    /**
     * Find a {@link Author} by using the appropriate fields of the passed {@link Author}.
     * The incoming object is not modified.
     *
     * <strong>IMPORTANT:</strong> the query can return more then one row if the
     * given-name of the author is empty. e.g. "Asimov" and "Asimov"+"Isaac"
     * We only return the id of the  <strong>first row found</strong>.
     *
     * @param context      Current context
     * @param author       to find the id of
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getAuthorId(@NonNull final Context context,
                            @NonNull final Author author,
                            final boolean lookupLocale,
                            @NonNull final Locale bookLocale) {

        final Locale authorLocale;
        if (lookupLocale) {
            authorLocale = author.getLocale(context, this, bookLocale);
        } else {
            authorLocale = bookLocale;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_AUTHOR_ID, () -> SqlGetId.AUTHOR_ID_BY_NAME);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, encodeOrderByColumn(author.getFamilyName(), authorLocale));
            stmt.bindString(2, encodeOrderByColumn(author.getGivenNames(), authorLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get the {@link Author} based on the given id.
     *
     * @param id of Author to find
     *
     * @return the {@link Author}, or {@code null} if not found
     */
    @Nullable
    public Author getAuthor(final long id) {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlGet.AUTHOR,
                                                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Author(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    /**
     * Find a {@link Series} by using the appropriate fields of the passed {@link Series}.
     * The incoming object is not modified.
     *
     * @param context      Current context
     * @param series       to find the id of
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getSeriesId(@NonNull final Context context,
                            @NonNull final Series series,
                            final boolean lookupLocale,
                            @NonNull final Locale bookLocale) {

        final Locale seriesLocale;
        if (lookupLocale) {
            seriesLocale = series.getLocale(context, this, bookLocale);
        } else {
            seriesLocale = bookLocale;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_SERIES_ID, () -> SqlGetId.SERIES_ID_BY_NAME);

        final String obTitle = series.reorderTitleForSorting(context, seriesLocale);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, encodeOrderByColumn(series.getTitle(), seriesLocale));
            stmt.bindString(2, encodeOrderByColumn(obTitle, seriesLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get the {@link Series} based on the given id.
     *
     * @param id of Series to find
     *
     * @return the {@link Series}, or {@code null} if not found
     */
    @Nullable
    public Series getSeries(final long id) {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlGet.SERIES,
                                                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Series(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    /**
     * Find a {@link Publisher} by using the appropriate fields of the passed {@link Publisher}.
     * The incoming object is not modified.
     *
     * @param context      Current context
     * @param publisher    to find the id of
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getPublisherId(@NonNull final Context context,
                               @NonNull final Publisher publisher,
                               final boolean lookupLocale,
                               @NonNull final Locale bookLocale) {

        final Locale publisherLocale;
        if (lookupLocale) {
            publisherLocale = publisher.getLocale(context, bookLocale);
        } else {
            publisherLocale = bookLocale;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_PUBLISHER_ID, () -> SqlGetId.PUBLISHER_ID_BY_NAME);

        final String obName = publisher.reorderTitleForSorting(context, publisherLocale);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, encodeOrderByColumn(publisher.getName(), publisherLocale));
            stmt.bindString(2, encodeOrderByColumn(obName, publisherLocale));
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get the {@link Publisher} based on the given id.
     *
     * @param id of Publisher to find
     *
     * @return the {@link Publisher}, or {@code null} if not found
     */
    @Nullable
    public Publisher getPublisher(final long id) {
        try (Cursor cursor = mSyncedDb.rawQuery(SqlGet.PUBLISHER,
                                                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Publisher(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }


    /**
     * Get a list of book ID's for the given Author.
     *
     * @param authorId id of the author
     *
     * @return list with book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIdsByAuthor(final long authorId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.BOOK_IDS_BY_AUTHOR_ID,
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
     * @return list with book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIdsBySeries(final long seriesId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.BOOK_IDS_BY_SERIES_ID,
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
     * @param publisherId id of the Publisher
     *
     * @return list with book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIdsByPublisher(final long publisherId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.BOOK_IDS_BY_PUBLISHER_ID,
                                                new String[]{String.valueOf(publisherId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's (most often just the one) in which this TocEntry (story) is present.
     *
     * @param tocId id of the entry (story)
     *
     * @return list with book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIdsByTocEntry(final long tocId) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.BOOK_ID_LIST_BY_TOC_ENTRY_ID,
                                                new String[]{String.valueOf(tocId)})) {
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        }
        return list;
    }

    /**
     * Get a list of book ID's (most often just the one) for the given ISBN.
     *
     * @param isbn to search for; can be generic/non-valid
     *
     * @return list with book ID's
     */
    public ArrayList<Long> getBookIdsByIsbn(@NonNull final ISBN isbn) {
        final ArrayList<Long> list = new ArrayList<>();
        // if the string is ISBN-10 compatible, i.e. an actual ISBN-10,
        // or an ISBN-13 in the 978 range, we search on both formats
        if (isbn.isIsbn10Compat()) {
            try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.BY_VALID_ISBN,
                                                    new String[]{isbn.asText(ISBN.TYPE_ISBN10),
                                                                 isbn.asText(ISBN.TYPE_ISBN13)})) {
                while (cursor.moveToNext()) {
                    list.add(cursor.getLong(0));
                }
            }
            return list;

        } else {
            // otherwise just search on the string as-is; regardless of validity
            // (this would actually include valid ISBN-13 in the 979 range).
            try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.BY_ISBN,
                                                    new String[]{isbn.asText()})) {
                while (cursor.moveToNext()) {
                    list.add(cursor.getLong(0));
                }
            }
            return list;
        }
    }


    /**
     * Check that a book with the passed key=value exists and return the id of the book, or zero.
     *
     * @param key   column name
     * @param value id/value for the key
     *
     * @return id of the book, or 0 'new' if not found
     */
    public long getBookIdFromKey(@NonNull final String key,
                                 @NonNull final String value) {
        final String sql = SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                           + _WHERE_ + key + "=?";

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
            stmt.bindString(1, value);
            return stmt.simpleQueryForLongOrZero();
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
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_BOOK_ID_FROM_UUID, () -> SqlGetId.BY_UUID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Return the book UUID based on the id.
     *
     * @param bookId of the book
     *
     * @return the book UUID, or {@code null} if not found/failure
     */
    @Nullable
    private String getBookUuid(final long bookId) {
        // sanity check
        if (bookId == 0) {
            throw new IllegalArgumentException(ErrorMsg.ZERO_ID_FOR_BOOK);
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_BOOK_UUID, () -> SqlGet.BOOK_UUID_BY_ID);

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
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_BOOK_TITLE, () -> SqlGet.BOOK_TITLE_BY_BOOK_ID);

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
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_BOOK_ISBN, () -> SqlGet.BOOK_ISBN_BY_BOOK_ID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
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
        final ArrayList<Author> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.AUTHORS_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
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
        final ArrayList<Series> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.SERIES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Series(rowData.getLong(KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    /**
     * Get a list of the Publisher for a book.
     *
     * @param bookId of the book
     *
     * @return list of Publisher
     */
    @NonNull
    public ArrayList<Publisher> getPublishersByBookId(final long bookId) {
        final ArrayList<Publisher> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.PUBLISHER_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Publisher(rowData.getLong(KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    /**
     * Get the list of TocEntry for this book.
     *
     * @param bookId of the book
     *
     * @return list
     */
    @NonNull
    public ArrayList<TocEntry> getTocEntryByBookId(final long bookId) {
        final ArrayList<TocEntry> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelect.TOC_ENTRIES_BY_BOOK_ID,
                                                new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new TocEntry(rowData.getLong(KEY_PK_ID),
                                      new Author(rowData.getLong(KEY_FK_AUTHOR), rowData),
                                      rowData.getString(KEY_TITLE),
                                      rowData.getString(KEY_DATE_FIRST_PUBLICATION),
                                      TocEntry.TYPE_BOOK,
                                      rowData.getInt(KEY_BOOK_COUNT)));
            }
        }
        return list;
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
        try (Cursor cursor = mSyncedDb.rawQuery(SqlGet.SERIES_LANGUAGE,
                                                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return "";
        }
    }


    /**
     * Return the TocEntry id. The incoming object is not modified.
     * Note that the publication year is NOT used for comparing, under the assumption that
     * two search-sources can give different dates by mistake.
     *
     * @param context      Current context
     * @param tocEntry     tocEntry to search for
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set
     *
     * @return the id, or 0 (i.e. 'new') when not found
     */
    public long getTocEntryId(@NonNull final Context context,
                              @NonNull final TocEntry tocEntry,
                              final boolean lookupLocale,
                              @NonNull final Locale bookLocale) {

        final Locale tocLocale;
        if (lookupLocale) {
            tocLocale = tocEntry.getLocale(context, this, bookLocale);
        } else {
            tocLocale = bookLocale;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_TOC_ENTRY_ID, () -> SqlGetId.TOC_ENTRY_ID_BY_TITLE_AND_AUTHOR);

        final String obTitle = tocEntry.reorderTitleForSorting(context, tocLocale);

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

        final boolean byShelf = bookshelfId != Bookshelf.ALL_BOOKS;

        // rawQuery wants String[] as bind parameters
        final String authorIdStr = String.valueOf(author.getId());
        final String bookshelfIdStr = String.valueOf(bookshelfId);

        String sql = "";
        final List<String> paramList = new ArrayList<>();

        // MUST be toc first, books second; otherwise the GROUP BY is done on the whole
        // UNION instead of on the toc only; and SqLite rejects () around the sub selects.
        if (withTocEntries) {
            sql += SqlSelect.TOC_ENTRIES_BY_AUTHOR_ID
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
            sql += SqlSelect.BOOK_TITLES_BY_AUTHOR_ID
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

        sql += _ORDER_BY_ + KEY_TITLE_OB + _COLLATION;

        final ArrayList<TocEntry> list = new ArrayList<>();
        //noinspection ZeroLengthArrayAllocation
        try (Cursor cursor = mSyncedDb.rawQuery(sql, paramList.toArray(new String[0]))) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final TocEntry tocEntry =
                        new TocEntry(rowData.getLong(KEY_PK_ID),
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
     * Return a list of paired book-id and book-title 's for the given TOC id.
     *
     * @param id TOC id
     *
     * @return list of id/titles of books.
     */
    @NonNull
    public List<Pair<Long, String>> getBookTitlesForToc(@IntRange(from = 1) final long id) {
        final List<Pair<Long, String>> list = new ArrayList<>();
        final String sql =
                SELECT_ + TBL_BOOKS.dot(KEY_PK_ID) + ',' + TBL_BOOKS.dot(KEY_TITLE)
                + _FROM_ + TBL_BOOK_TOC_ENTRIES.ref() + TBL_BOOK_TOC_ENTRIES.join(TBL_BOOKS)
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_FK_TOC_ENTRY) + "=?"
                + _ORDER_BY_ + TBL_BOOKS.dot(KEY_TITLE_OB);

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{String.valueOf(id)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Pair<>(rowData.getLong(KEY_PK_ID),
                                    rowData.getString(KEY_TITLE)));
            }
        }
        return list;
    }


    /***
     * Return the book last update date based on the id.
     *
     *
     * @param context Current context
     * @param bookId of the book
     *
     * @return the last update date; UTC based.
     */
    @Nullable
    public LocalDateTime getBookLastUpdateUtcDate(@NonNull final Context context,
                                                  final long bookId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_BOOK_UPDATE_DATE, () -> SqlGet.LAST_UPDATE_DATE_BY_BOOK_ID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return DateParser.getInstance(context).parseISO(stmt.simpleQueryForStringOrNull());
        }
    }


    /**
     * Check that a book with the passed id exists.
     *
     * @param bookId of the book
     *
     * @return {@code true} if exists
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean bookExistsById(final long bookId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_CHECK_BOOK_EXISTS, () -> SqlCount.BOOK_EXISTS);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForLongOrZero() == 1;
        }
    }

    /**
     * Check that a book with the passed isbn exists.
     *
     * @param isbnStr of the book
     *
     * @return {@code true} if exists
     */
    public boolean bookExistsByIsbn(@NonNull final String isbnStr) {
        //TODO: optimize this call
        final ISBN isbn = ISBN.createISBN(isbnStr);
        return !getBookIdsByIsbn(isbn).isEmpty();
    }


    /**
     * Update the 'read' status of the book.
     *
     * @param bookId book to update
     * @param read   the status to set
     *
     * @return {@code true} for success.
     */
    public boolean setBookRead(final long bookId,
                               final boolean read) {
        final ContentValues cv = new ContentValues();
        cv.put(KEY_READ, read);
        if (read) {
            cv.put(KEY_READ_END, LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        } else {
            cv.put(KEY_READ_END, "");
        }

        return 0 < mSyncedDb.update(TBL_BOOKS.getName(), cv, KEY_PK_ID + "=?",
                                    new String[]{String.valueOf(bookId)});
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
        final ContentValues cv = new ContentValues();
        cv.put(KEY_AUTHOR_IS_COMPLETE, isComplete);

        return 0 < mSyncedDb.update(TBL_AUTHORS.getName(), cv, KEY_PK_ID + "=?",
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

        return 0 < mSyncedDb.update(TBL_SERIES.getName(), cv, KEY_PK_ID + "=?",
                                    new String[]{String.valueOf(seriesId)});
    }

    /**
     * Set the Goodreads sync date to the current time.
     *
     * @param bookId the book
     */
    public void setGoodreadsSyncDate(final long bookId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_UPDATE_GOODREADS_SYNC_DATE, () -> SqlUpdate.GOODREADS_LAST_SYNC_DATE);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Set the Goodreads book id for this book.
     *
     * @param bookId          the/our book id
     * @param goodreadsBookId the Goodreads book id
     */
    public void setGoodreadsBookId(final long bookId,
                                   final long goodreadsBookId) {

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_UPDATE_GOODREADS_BOOK_ID, () -> SqlUpdate.GOODREADS_BOOK_ID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, goodreadsBookId);
            stmt.bindLong(2, bookId);
            stmt.executeUpdateDelete();
        }
    }


    /**
     * Moves all books from the 'source' {@link Bookshelf}, to the 'destId' {@link Bookshelf}.
     * The (now unused) 'source' {@link Bookshelf} is deleted.
     *
     * @param source from where to move
     * @param destId to move to
     *
     * @return the amount of books moved.
     */
    @SuppressWarnings("UnusedReturnValue")
    public int merge(@NonNull final Bookshelf source,
                     final long destId) {

        final ContentValues cv = new ContentValues();
        cv.put(KEY_FK_BOOKSHELF, destId);

        final int rowsAffected;

        final SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            // we don't hold 'position' for shelves... so just do a mass update
            rowsAffected = mSyncedDb.update(
                    TBL_BOOK_BOOKSHELF.getName(), cv, KEY_FK_BOOKSHELF + "=?",
                    new String[]{String.valueOf(source.getId())});

            // delete the obsolete source.
            delete(source);

            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(txLock);
        }

        return rowsAffected;
    }

    /**
     * Moves all books from the 'source' {@link Author}, to the 'destId' {@link Author}.
     * The (now unused) 'source' {@link Author} is deleted.
     *
     * @param context Current context
     * @param source  from where to move
     * @param destId  to move to
     *
     * @throws DaoWriteException on failure
     */
    public void merge(@NonNull final Context context,
                      @NonNull final Author source,
                      final long destId)
            throws DaoWriteException {

        final SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            // TOC is easy: just do a mass update
            final ContentValues cv = new ContentValues();
            cv.put(KEY_FK_AUTHOR, destId);
            mSyncedDb.update(
                    TBL_TOC_ENTRIES.getName(), cv, KEY_FK_AUTHOR + "=?",
                    new String[]{String.valueOf(source.getId())});

            // the books must be done one by one, as we need to prevent duplicate authors
            // e.g. suppose we have a book with author
            // a1@pos1
            // a2@pos2
            // and we want to replace a1 with a2, we cannot simply do a mass update.
            final Author destination = getAuthor(destId);
            for (long bookId : getBookIdsByAuthor(source.getId())) {
                final Book book = new Book();
                book.load(bookId, this);

                final Collection<Author> fromBook =
                        book.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
                final Collection<Author> destList = new ArrayList<>();

                for (Author item : fromBook) {
                    if (source.getId() == item.getId()) {
                        // replace this one.
                        destList.add(destination);
                        // We could 'break' here as there should be no duplicates, but paranoia...
                    } else {
                        // just keep/copy
                        destList.add(item);
                    }
                }
                // delete old links and store all new links
                insertBookAuthors(context, bookId, destList, true, book.getLocale(context));
            }

            // delete the obsolete source.
            delete(context, source);

            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
    }

    /**
     * Moves all books from the 'source' {@link Series}, to the 'destId' {@link Series}.
     * The (now unused) 'source' {@link Series} is deleted.
     *
     * @param context Current context
     * @param source  from where to move
     * @param destId  to move to
     *
     * @throws DaoWriteException on failure
     */
    public void merge(@NonNull final Context context,
                      @NonNull final Series source,
                      final long destId)
            throws DaoWriteException {

        final ContentValues cv = new ContentValues();
        cv.put(KEY_FK_SERIES, destId);

        final SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            // see #merge(Context, Author, long)
            final Series destination = getSeries(destId);
            for (long bookId : getBookIdsBySeries(source.getId())) {
                final Book book = new Book();
                book.load(bookId, this);

                final Collection<Series> fromBook =
                        book.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
                final Collection<Series> destList = new ArrayList<>();

                for (Series item : fromBook) {
                    if (source.getId() == item.getId()) {
                        destList.add(destination);
                    } else {
                        destList.add(item);
                    }
                }
                insertBookSeries(context, bookId, destList, true, book.getLocale(context));
            }

            // delete the obsolete source.
            delete(context, source);

            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(txLock);
        }
    }

    /**
     * Moves all books from the 'source' {@link Publisher}, to the 'destId' {@link Publisher}.
     * The (now unused) 'source' {@link Publisher} is deleted.
     *
     * @param context Current context
     * @param source  from where to move
     * @param destId  to move to
     *
     * @throws DaoWriteException on failure
     */
    public void merge(@NonNull final Context context,
                      @NonNull final Publisher source,
                      final long destId)
            throws DaoWriteException {

        final ContentValues cv = new ContentValues();
        cv.put(KEY_FK_PUBLISHER, destId);

        final SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            // see #merge(Context, Author, long)
            final Publisher destination = getPublisher(destId);
            for (long bookId : getBookIdsByPublisher(source.getId())) {
                final Book book = new Book();
                book.load(bookId, this);

                final Collection<Publisher> fromBook =
                        book.getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
                final Collection<Publisher> destList = new ArrayList<>();

                for (Publisher item : fromBook) {
                    if (source.getId() == item.getId()) {
                        destList.add(destination);
                    } else {
                        destList.add(item);
                    }
                }
                insertBookPublishers(context, bookId, destList, true, book.getLocale(context));
            }

            // delete the obsolete source.
            delete(context, source);

            mSyncedDb.setTransactionSuccessful();
        } finally {
            mSyncedDb.endTransaction(txLock);
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
                     + _ORDER_BY_ + KEY_PK_ID;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            final DataHolder rowData = new CursorRow(cursor);
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
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_BOOKLIST_STYLE, () -> SqlGetId.BOOKLIST_STYLE_ID_BY_UUID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindString(1, uuid);
            return stmt.simpleQueryForLongOrZero();
        }
    }


    /**
     * Query to get the relevant columns for searching for a {@link Book} on Goodreads.
     *
     * @param bookId to retrieve
     *
     * @return A subset of book columns, suitable for searches on Goodreads.
     */
    @NonNull
    public Cursor fetchBookColumnsForGoodreadsSearch(@IntRange(from = 1) final long bookId) {
        return mSyncedDb.rawQuery(SqlSelect.BOOK_COLUMNS_FOR_GOODREADS_SEARCH,
                                  new String[]{String.valueOf(bookId)});
    }

    /**
     * Query to get the relevant columns for sending a {@link Book} to Goodreads.
     *
     * @param bookId to retrieve
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchBookForGoodreadsExport(final long bookId) {
        return mSyncedDb.rawQuery(SqlGoodreadsSendBook.SINGLE_BOOK,
                                  new String[]{String.valueOf(bookId)});
    }

    /**
     * Query to get the relevant columns for sending a set of {@link Book} 's to Goodreads.
     *
     * @param startId     the 'first' (e.g. 'oldest') bookId to get
     *                    since the last sync with Goodreads
     * @param updatesOnly true, if we only want the updated records
     *                    since the last sync with Goodreads
     *
     * @return Cursor containing all records, if any
     */
    @NonNull
    public Cursor fetchBooksForGoodreadsExport(final long startId,
                                               final boolean updatesOnly) {
        if (updatesOnly) {
            return mSyncedDb.rawQuery(SqlGoodreadsSendBook.UPDATED_BOOKS,
                                      new String[]{String.valueOf(startId)});
        } else {
            return mSyncedDb.rawQuery(SqlGoodreadsSendBook.ALL_BOOKS,
                                      new String[]{String.valueOf(startId)});
        }
    }


    /**
     * Fills an array with the first column (index==0, type==long) from the passed SQL.
     *
     * @param sql SQL to execute
     *
     * @return List of *all* values
     */
    @NonNull
    ArrayList<Long> getIdList(@NonNull final String sql) {
        final ArrayList<Long> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
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
        final ArrayList<String> list = new ArrayList<>();
        try (Cursor cursor = mSyncedDb.rawQuery(sql, null)) {
            final int column = cursor.getColumnIndexOrThrow(columnName);
            while (cursor.moveToNext()) {
                list.add(cursor.getString(column));
            }
            return list;
        }
    }

    /**
     * Takes the ResultSet from a Cursor, and fetches column 0 as a String into an ArrayList.
     * Skips {@code null} and {@code ""} entries.
     *
     * @param cursor cursor
     *
     * @return List of values (case sensitive)
     *
     * @see #getColumnAsList
     */
    @NonNull
    private ArrayList<String> getFirstColumnAsList(@NonNull final Cursor cursor) {
        final ArrayList<String> list = new ArrayList<>();
        while (cursor.moveToNext()) {
            final String name = cursor.getString(0);
            if (name != null && !name.isEmpty()) {
                list.add(name);
            }
        }
        return list;
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
        final String query = SqlFTS.cleanupFtsCriterion(keywords, null);
        // do we have anything to search for?
        if (query.isEmpty()) {
            return null;
        }
        return mSyncedDb.rawQuery(SqlFTS.SEARCH_SUGGESTIONS, new String[]{query});
    }

    /**
     * Return a {@link Cursor}, suited for a local-search.
     * This is used by the advanced search activity.
     *
     * @param author        Author related keywords to find
     * @param title         Title related keywords to find
     * @param seriesTitle   Series title related keywords to find
     * @param publisherName Publisher name related keywords to find
     * @param keywords      Keywords to find anywhere in book; this includes titles and authors
     * @param limit         maximum number of rows to return
     *
     * @return a cursor, or {@code null} if all input was empty
     */
    @Nullable
    public Cursor fetchSearchSuggestionsAdv(@Nullable final String author,
                                            @Nullable final String title,
                                            @Nullable final String seriesTitle,
                                            @Nullable final String publisherName,
                                            @Nullable final String keywords,
                                            final int limit) {

        final String query = SqlFTS.createMatchString(author, title, seriesTitle,
                                                      publisherName, keywords);
        // do we have anything to search for?
        if (query.isEmpty()) {
            return null;
        }
        return mSyncedDb.rawQuery(SqlFTS.SEARCH, new String[]{query, String.valueOf(limit)});
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
     * Process the book details from the cursor using the passed fts query.
     * <p>
     * <strong>Note:</strong> This assumes a specific order for query parameters.
     * If modified, then update {@link SqlFTS#INSERT_BODY}, {@link SqlFTS#UPDATE}
     *
     * <strong>Transaction:</strong> required
     *
     * @param cursor Cursor of books to update
     * @param stmt   Statement to execute (insert or update)
     *
     * @throws TransactionException a transaction must be started before calling this method
     */
    private void ftsProcessBooks(@NonNull final Cursor cursor,
                                 @NonNull final SynchronizedStatement stmt) {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(TransactionException.REQUIRED);
        }

        // Accumulator for author names for each book
        final StringBuilder authorText = new StringBuilder();
        // Accumulator for series titles for each book
        final StringBuilder seriesText = new StringBuilder();
        // Accumulator for publisher names for each book
        final StringBuilder publisherText = new StringBuilder();
        // Accumulator for TOCEntry titles for each book
        final StringBuilder tocTitles = new StringBuilder();

        // Indexes of fields in the inner-loop cursors, -2 for 'not initialised yet'
        int colGivenNames = -2;
        int colFamilyName = -2;
        int colSeriesTitle = -2;
        int colPublisherName = -2;
        int colTOCEntryTitle = -2;

        final DataHolder rowData = new CursorRow(cursor);
        // Process each book
        while (cursor.moveToNext()) {
            authorText.setLength(0);
            seriesText.setLength(0);
            publisherText.setLength(0);
            tocTitles.setLength(0);

            final long bookId = rowData.getLong(KEY_PK_ID);
            // Query Parameter
            final String[] qpBookId = new String[]{String.valueOf(bookId)};

            // Get list of authors
            try (Cursor authors = mSyncedDb.rawQuery(SqlFTS.GET_AUTHORS_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colGivenNames < 0) {
                    colGivenNames = authors.getColumnIndex(KEY_AUTHOR_GIVEN_NAMES);
                }
                if (colFamilyName < 0) {
                    colFamilyName = authors.getColumnIndex(KEY_AUTHOR_FAMILY_NAME);
                }

                while (authors.moveToNext()) {
                    authorText.append(authors.getString(colGivenNames))
                              .append(' ')
                              .append(authors.getString(colFamilyName))
                              .append(';');
                }
            }

            // Get list of series
            try (Cursor series = mSyncedDb.rawQuery(SqlFTS.GET_SERIES_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colSeriesTitle < 0) {
                    colSeriesTitle = series.getColumnIndexOrThrow(KEY_SERIES_TITLE);
                }

                while (series.moveToNext()) {
                    seriesText.append(series.getString(colSeriesTitle)).append(';');
                }
            }

            // Get list of publishers
            try (Cursor publishers = mSyncedDb
                    .rawQuery(SqlFTS.GET_PUBLISHERS_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colPublisherName < 0) {
                    colPublisherName = publishers.getColumnIndexOrThrow(KEY_PUBLISHER_NAME);
                }

                while (publishers.moveToNext()) {
                    publisherText.append(publishers.getString(colPublisherName)).append(';');
                }
            }

            // Get list of TOC titles
            try (Cursor toc = mSyncedDb.rawQuery(SqlFTS.GET_TOC_TITLES_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colTOCEntryTitle < 0) {
                    colTOCEntryTitle = toc.getColumnIndexOrThrow(KEY_TITLE);
                }

                while (toc.moveToNext()) {
                    tocTitles.append(toc.getString(colTOCEntryTitle)).append(';');
                }
            }

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                bindStringOrNull(stmt, 1, rowData.getString(KEY_TITLE));
                // KEY_FTS_AUTHOR_NAME
                bindStringOrNull(stmt, 2, authorText.toString());
                // KEY_SERIES_TITLE
                bindStringOrNull(stmt, 3, seriesText.toString());
                bindStringOrNull(stmt, 4, rowData.getString(KEY_DESCRIPTION));
                bindStringOrNull(stmt, 5, rowData.getString(KEY_PRIVATE_NOTES));
                bindStringOrNull(stmt, 6, publisherText.toString());
                bindStringOrNull(stmt, 7, rowData.getString(KEY_GENRE));
                bindStringOrNull(stmt, 8, rowData.getString(KEY_LOCATION));
                bindStringOrNull(stmt, 9, rowData.getString(KEY_ISBN));
                // KEY_FTS_TOC_ENTRY_TITLE
                bindStringOrNull(stmt, 10, tocTitles.toString());

                // KEY_FTS_BOOK_ID : in a where clause, or as insert parameter
                stmt.bindLong(11, bookId);

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
     *
     * @throws TransactionException a transaction must be started before calling this method
     */
    private void ftsInsert(@NonNull final Context context,
                           final long bookId) {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(TransactionException.REQUIRED);
        }

        try {
            final SynchronizedStatement stmt = mSqlStatementManager.get(
                    STMT_INSERT_FTS, () -> SqlFTS.INSERT);

            try (Cursor cursor = mSyncedDb.rawQuery(SqlFTS.BOOK_BY_ID,
                                                    new String[]{String.valueOf(bookId)})) {
                ftsProcessBooks(cursor, stmt);
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
     *
     * @throws TransactionException a transaction must be started before calling this method
     */
    private void ftsUpdate(@NonNull final Context context,
                           final long bookId) {

        if (!mSyncedDb.inTransaction()) {
            throw new TransactionException(TransactionException.REQUIRED);
        }

        try {
            final SynchronizedStatement stmt = mSqlStatementManager.get(
                    STMT_UPDATE_FTS, () -> SqlFTS.UPDATE);

            try (Cursor cursor = mSyncedDb.rawQuery(SqlFTS.BOOK_BY_ID,
                                                    new String[]{String.valueOf(bookId)})) {
                ftsProcessBooks(cursor, stmt);
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
        long t0 = 0;
        if (BuildConfig.DEBUG /* always */) {
            t0 = System.nanoTime();
        }
        boolean gotError = false;

        // Make a copy of the FTS table definition for our temporary table.
        final TableDefinition ftsTemp = new TableDefinition(TBL_FTS_BOOKS);
        // Give it a new name
        ftsTemp.setName(ftsTemp.getName() + "_temp");

        final SyncLock txLock = mSyncedDb.beginTransaction(true);

        try {
            //IMPORTANT: withConstraints MUST BE false
            ftsTemp.recreate(mSyncedDb, false);

            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(
                    "INSERT INTO " + ftsTemp.getName() + SqlFTS.INSERT_BODY);
                 Cursor cursor = mSyncedDb.rawQuery(SqlFTS.ALL_BOOKS, null)) {
                ftsProcessBooks(cursor, stmt);
            }

            mSyncedDb.setTransactionSuccessful();
        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(App.getAppContext(), TAG, e);
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
                mSyncedDb.drop(TBL_FTS_BOOKS.getName());
                mSyncedDb.execSQL("ALTER TABLE " + ftsTemp + " RENAME TO " + TBL_FTS_BOOKS);
            }
        }

        if (BuildConfig.DEBUG /* always */) {
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
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.BOOK_TITLES, null)) {
            int langIdx = cursor.getColumnIndex(KEY_LANGUAGE);
            while (cursor.moveToNext()) {
                language = cursor.getString(langIdx);
                bookLocale = AppLocale.getInstance().getLocale(context, language);
                if (bookLocale == null) {
                    bookLocale = AppLocale.getInstance().getUserLocale(context);
                }
                rebuildOrderByTitleColumns(context, bookLocale, reorder, cursor,
                                           TBL_BOOKS, KEY_TITLE_OB);
            }
        }

        // Series and TOC Entries use the user Locale.
        final Locale userLocale = AppLocale.getInstance().getUserLocale(context);

        // We should use the locale from the 1st book in the series... but that is a huge overhead.
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.SERIES_TITLES, null)) {
            while (cursor.moveToNext()) {
                rebuildOrderByTitleColumns(context, userLocale, reorder, cursor,
                                           TBL_SERIES, KEY_SERIES_TITLE_OB);
            }
        }

        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.PUBLISHER_NAMES, null)) {
            while (cursor.moveToNext()) {
                rebuildOrderByTitleColumns(context, userLocale, reorder, cursor,
                                           TBL_PUBLISHERS, KEY_PUBLISHER_NAME_OB);
            }
        }

        // We should use primary book or Author Locale... but that is a huge overhead.
        try (Cursor cursor = mSyncedDb.rawQuery(SqlSelectFullTable.TOC_ENTRY_TITLES, null)) {
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
            return 0 < mSyncedDb.update(table.getName(), cv, KEY_PK_ID + "=?",
                                        new String[]{String.valueOf(id)});
        }

        return true;
    }

    @IntDef(flag = true, value = {BOOK_FLAG_IS_BATCH_OPERATION,
                                  BOOK_FLAG_USE_ID_IF_PRESENT,
                                  BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT})
    @Retention(RetentionPolicy.SOURCE)
    @interface BookFlags {

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
     * Commonly used SQL table columns.
     */
    public static final class SqlColumns {

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
         * Expression for the domain {@link DBDefinitions#DOM_PUBLISHER_NAME_CSV}.
         * <p>
         * The order of the returned names will be arbitrary.
         * We could add an ORDER BY GROUP_CONCAT(... if we GROUP BY
         */
        public static final String EXP_PUBLISHER_NAME_CSV =
                "("
                + "SELECT GROUP_CONCAT(" + TBL_PUBLISHERS.dot(KEY_PUBLISHER_NAME) + ",', ')"
                + _FROM_ + TBL_PUBLISHERS.ref() + TBL_PUBLISHERS.join(TBL_BOOK_PUBLISHER)
                + _WHERE_ + TBL_BOOKS.dot(KEY_PK_ID) + "=" + TBL_BOOK_PUBLISHER.dot(KEY_FK_BOOK)
                + ")";
        private static final String _WHEN_ = " WHEN ";
        private static final String CASE = "CASE";
        private static final String _THEN_ = " THEN ";
        private static final String _ELSE_ = " ELSE ";
        private static final String _END = " END";
        /**
         * SQL column: return 1 if the book is available, 0 if not.
         * {@link DBDefinitions#KEY_LOANEE_AS_BOOLEAN}
         */
        public static final String EXP_LOANEE_AS_BOOLEAN =
                CASE
                + _WHEN_ + TBL_BOOK_LOANEE.dot(KEY_LOANEE) + " IS NULL THEN 1"
                + " ELSE 0"
                + _END;
        /**
         * SQL column: return "" if the book is available, "loanee name" if not.
         */
        public static final String EXP_BOOK_LOANEE_OR_EMPTY =
                CASE
                + _WHEN_ + TBL_BOOK_LOANEE.dot(KEY_LOANEE) + " IS NULL THEN ''"
                + _ELSE_ + TBL_BOOK_LOANEE.dot(KEY_LOANEE)
                + _END;

        /**
         * Single column, with the formatted name of the Author.
         * Note how the 'otherwise' will always concatenate the names without white space.
         *
         * @param givenNameFirst {@code true}
         *                       If no given name -> "FamilyName"
         *                       otherwise -> "GivenNamesFamilyName"
         *                       {@code false}
         *                       If no given name -> "FamilyName"
         *                       otherwise -> "FamilyNameGivenNames"
         *
         * @return column expression
         */
        @NonNull
        public static String getSortAuthor(final boolean givenNameFirst) {
            if (givenNameFirst) {
                return CASE
                       + _WHEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB) + "=''"
                       + _THEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)

                       + _ELSE_ + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB)
                       + "||" + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)
                       + _END;
            } else {
                return CASE
                       + _WHEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB) + "=''"
                       + _THEN_ + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)

                       + _ELSE_ + TBL_AUTHORS.dot(KEY_AUTHOR_FAMILY_NAME_OB)
                       + "||" + TBL_AUTHORS.dot(KEY_AUTHOR_GIVEN_NAMES_OB)
                       + _END;
            }
        }

        /**
         * Single column, with the formatted name of the Author.
         *
         * @param tableAlias     to prefix
         * @param givenNameFirst {@code true}
         *                       If no given name -> "FamilyName"
         *                       otherwise -> "GivenNames FamilyName"
         *                       {@code false}
         *                       If no given name -> "FamilyName"
         *                       otherwise -> "FamilyName, GivenNames"
         *
         * @return column expression
         */
        @NonNull
        public static String getDisplayAuthor(@NonNull final String tableAlias,
                                              final boolean givenNameFirst) {
            if (givenNameFirst) {
                return CASE
                       + _WHEN_ + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES + "=''"
                       + _THEN_ + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME
                       + _ELSE_
                       + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES
                       + "||' '||" + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME
                       + _END;
            } else {
                return CASE
                       + _WHEN_ + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES + "=''"
                       + _THEN_ + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME
                       + _ELSE_
                       + tableAlias + '.' + KEY_AUTHOR_FAMILY_NAME
                       + "||', '||" + tableAlias + '.' + KEY_AUTHOR_GIVEN_NAMES
                       + _END;
            }
        }

        /**
         * If the field has a time part, convert it to local time.
         * This deals with legacy 'date-only' dates.
         * The logic being that IF they had a time part then it would be UTC.
         * Without a time part, we assume the zone is local (or irrelevant).
         *
         * @param fieldSpec fully qualified field name
         *
         * @return expression
         */
        @NonNull
        private static String localDateTimeExpression(@NonNull final String fieldSpec) {
            return CASE
                   + _WHEN_ + fieldSpec + " GLOB '*-*-* *' "
                   + " THEN datetime(" + fieldSpec + ", 'localtime')"
                   + _ELSE_ + fieldSpec
                   + _END;
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
                fieldSpec = localDateTimeExpression(fieldSpec);
            }
            return "(CASE"
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]*'"
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
                fieldSpec = localDateTimeExpression(fieldSpec);
            }
            return CASE
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",6,2)"
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",6,1)"
                   + " ELSE ''"
                   + _END;
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
                fieldSpec = localDateTimeExpression(fieldSpec);
            }
            // Just look for 4 leading numbers followed by 2 or 1 digit then another 2 or 1 digit.
            // We don't care about anything else.
            return CASE
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",9,2)"
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9][0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",8,2)"
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",9,1)"
                   + _WHEN_ + fieldSpec + " GLOB '[0-9][0-9][0-9][0-9]-[0-9]-[0-9]*'"
                   + " THEN SUBSTR(" + fieldSpec + ",8,1)"
                   + " ELSE ''"
                   + _END;
        }
    }

    /**
     * Count/exist statements.
     */
    private static final class SqlCount {

        /**
         * Count all {@link Book}'s.
         */
        static final String BOOKS =
                "SELECT COUNT(*) FROM " + TBL_BOOKS.getName();

        /**
         * Count the number of {@link Book}'s in a {@link Series}.
         */
        static final String BOOKS_IN_SERIES =
                "SELECT COUNT(" + KEY_FK_BOOK + ") FROM " + TBL_BOOK_SERIES.getName()
                + _WHERE_ + KEY_FK_SERIES + "=?";

        /**
         * Count the number of {@link Book}'s by an {@link Author}.
         */
        static final String BOOKS_BY_AUTHOR =
                "SELECT COUNT(" + KEY_FK_BOOK + ") FROM " + TBL_BOOK_AUTHOR.getName()
                + _WHERE_ + KEY_FK_AUTHOR + "=?";

        /**
         * Count the number of {@link Book}'s by an {@link Publisher}.
         */
        static final String BOOKS_BY_PUBLISHER =
                "SELECT COUNT(" + KEY_FK_BOOK + ") FROM " + TBL_BOOK_PUBLISHER.getName()
                + _WHERE_ + KEY_FK_PUBLISHER + "=?";

        /**
         * Count the number of {@link TocEntry}'s by an {@link Author}.
         */
        static final String TOC_ENTRIES_BY_AUTHOR =
                "SELECT COUNT(" + KEY_PK_ID + ") FROM " + TBL_TOC_ENTRIES.getName()
                + _WHERE_ + KEY_FK_AUTHOR + "=?";

        /**
         * Check if a {@link Book} exists.
         */
        static final String BOOK_EXISTS =
                "SELECT COUNT(*) " + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";
    }


    /**
     * Sql SELECT to lookup a single item.
     */
    private static final class SqlGet {

        /**
         * Get an {@link Author} by the Author id.
         */
        static final String AUTHOR =
                SqlSelectFullTable.AUTHORS
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get a {@link Series} by the Series id.
         */
        static final String SERIES =
                SqlSelectFullTable.SERIES
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get a {@link Publisher} by the Publisher id.
         */
        static final String PUBLISHER =
                SqlSelectFullTable.PUBLISHERS
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get a {@link Bookshelf} by the Bookshelf id.
         */
        static final String BOOKSHELF =
                SqlSelectFullTable.BOOKSHELVES
                + _WHERE_ + TBL_BOOKSHELF.dot(KEY_PK_ID) + "=?";

        /**
         * Get the UUID of a {@link Book} by the Book id.
         */
        static final String BOOK_UUID_BY_ID =
                SELECT_ + KEY_BOOK_UUID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get the ISBN of a {@link Book} by the Book id.
         */
        static final String BOOK_ISBN_BY_BOOK_ID =
                SELECT_ + KEY_ISBN + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get the title of a {@link Book} by the Book id.
         */
        static final String BOOK_TITLE_BY_BOOK_ID =
                SELECT_ + KEY_TITLE + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get the name of the loanee of a {@link Book} by the Book id.
         */
        static final String LOANEE_BY_BOOK_ID =
                SELECT_ + KEY_LOANEE + _FROM_ + TBL_BOOK_LOANEE.getName()
                + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Get a {@link Bookshelf} by its name.
         */
        static final String BOOKSHELF_BY_NAME =
                SqlSelectFullTable.BOOKSHELVES
                + _WHERE_ + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME) + "=?" + _COLLATION;
        /**
         * Get the language (ISO3) code for a Series.
         * This is defined as the language code for the first book in the Series.
         */
        static final String SERIES_LANGUAGE =
                SELECT_ + TBL_BOOKS.dotAs(KEY_LANGUAGE)
                + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_BOOKS)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_SERIES) + "=?"
                + _ORDER_BY_ + TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES)
                + " LIMIT 1";
        /**
         * Get the last-update-date for a {@link Book} by its id.
         */
        static final String LAST_UPDATE_DATE_BY_BOOK_ID =
                SELECT_ + KEY_UTC_LAST_UPDATED + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_PK_ID + "=?";
    }

    /**
     * Sql SELECT to lookup an ID.
     */
    private static final class SqlGetId {

        /**
         * Get the id of a {@link Book} by UUID.
         */
        static final String BY_UUID =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_BOOK_UUID + "=?";

        /**
         * Get the id of a {@link BooklistStyle} by UUID.
         */
        static final String BOOKLIST_STYLE_ID_BY_UUID =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKLIST_STYLES.getName()
                + _WHERE_ + KEY_UUID + "=?";

        /**
         * Get the id of a {@link Bookshelf} by name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         */
        static final String BOOKSHELF_ID_BY_NAME =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKSHELF.getName()
                + _WHERE_ + KEY_BOOKSHELF_NAME + "=?" + _COLLATION;

        /**
         * Get the id of a {@link Author} by name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * Can return more then one row if the KEY_AUTHOR_GIVEN_NAMES_OB is empty.
         */
        static final String AUTHOR_ID_BY_NAME =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + KEY_AUTHOR_FAMILY_NAME_OB + "=?" + _COLLATION
                + " AND " + KEY_AUTHOR_GIVEN_NAMES_OB + "=?" + _COLLATION;

        /**
         * Get the id of a {@link Series} by Title.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * Searches KEY_SERIES_TITLE_OB on both "The Title" and "Title, The"
         */
        static final String SERIES_ID_BY_NAME =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_SERIES.getName()
                + _WHERE_ + KEY_SERIES_TITLE_OB + "=?" + _COLLATION
                + " OR " + KEY_SERIES_TITLE_OB + "=?" + _COLLATION;

        /**
         * Get the id of a {@link Publisher} by name.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         */
        static final String PUBLISHER_ID_BY_NAME =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_PUBLISHERS.getName()
                + _WHERE_ + KEY_PUBLISHER_NAME_OB + "=?" + _COLLATION
                + " OR " + KEY_PUBLISHER_NAME_OB + "=?" + _COLLATION;

        /**
         * Get the id of a {@link TocEntry} by Title.
         * The lookup is by EQUALITY and CASE-SENSITIVE.
         * Search KEY_TITLE_OB on both "The Title" and "Title, The"
         */
        static final String TOC_ENTRY_ID_BY_TITLE_AND_AUTHOR =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_TOC_ENTRIES.getName()
                + _WHERE_ + KEY_FK_AUTHOR + "=?"
                + " AND (" + KEY_TITLE_OB + "=? " + _COLLATION
                + " OR " + KEY_TITLE_OB + "=?" + _COLLATION + ')';
    }


    /**
     * Return a single text column for use with {@link AutoCompleteTextView}.
     */
    private static final class SqlAutoCompleteText {

        /** name only. */
        private static final String LOANEE =
                SELECT_DISTINCT_ + KEY_LOANEE
                + _FROM_ + TBL_BOOK_LOANEE.getName()
                + _WHERE_ + KEY_LOANEE + "<> ''"
                + _ORDER_BY_ + KEY_LOANEE + _COLLATION;

        /** name only. */
        private static final String AUTHORS_FAMILY_NAMES =
                SELECT_DISTINCT_ + KEY_AUTHOR_FAMILY_NAME + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + _FROM_ + TBL_AUTHORS.getName()
                + _ORDER_BY_ + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION;

        /** name only. */
        private static final String AUTHORS_GIVEN_NAMES =
                SELECT_DISTINCT_ + KEY_AUTHOR_GIVEN_NAMES + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + _FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + KEY_AUTHOR_GIVEN_NAMES_OB + "<> ''"
                + _ORDER_BY_ + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

        /** name only. */
        private static final String AUTHORS_FORMATTED_NAMES =
                SELECT_
                + SqlColumns.getDisplayAuthor(TBL_AUTHORS.getAlias(), false)
                + _AS_ + KEY_AUTHOR_FORMATTED
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + _FROM_ + TBL_AUTHORS.ref()
                + _ORDER_BY_ + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

        /** name only. */
        private static final String AUTHORS_FORMATTED_NAMES_GIVEN_FIRST =
                SELECT_ + SqlColumns.getDisplayAuthor(TBL_AUTHORS.getAlias(), true)
                + _AS_ + KEY_AUTHOR_FORMATTED_GIVEN_FIRST
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + _FROM_ + TBL_AUTHORS.ref()
                + _ORDER_BY_ + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

        /** name only. */
        private static final String SERIES_NAME =
                SELECT_ + KEY_SERIES_TITLE
                + ',' + KEY_SERIES_TITLE_OB
                + _FROM_ + TBL_SERIES.getName()
                + _ORDER_BY_ + KEY_SERIES_TITLE_OB + _COLLATION;

        /** name only. */
        private static final String PUBLISHERS_NAME =
                SELECT_DISTINCT_ + KEY_PUBLISHER_NAME
                + ',' + KEY_PUBLISHER_NAME_OB
                + _FROM_ + TBL_PUBLISHERS.getName()
                + _ORDER_BY_ + KEY_PUBLISHER_NAME_OB + _COLLATION;

        /** name only. */
        private static final String FORMATS =
                SELECT_DISTINCT_ + KEY_FORMAT
                + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_FORMAT + "<> ''"
                + _ORDER_BY_ + KEY_FORMAT + _COLLATION;

        /** name only. */
        private static final String COLORS =
                SELECT_DISTINCT_ + KEY_COLOR
                + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_COLOR + "<> ''"
                + _ORDER_BY_ + KEY_COLOR + _COLLATION;

        /** name only. */
        private static final String GENRES =
                SELECT_DISTINCT_ + KEY_GENRE
                + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_GENRE + "<> ''"
                + _ORDER_BY_ + KEY_GENRE + _COLLATION;

        /** name only. */
        private static final String LANGUAGES =
                SELECT_DISTINCT_ + KEY_LANGUAGE
                + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_LANGUAGE + "<> ''"
                + _ORDER_BY_ + KEY_UTC_LAST_UPDATED + _COLLATION;

        /** name only. */
        private static final String LOCATIONS =
                SELECT_DISTINCT_ + KEY_LOCATION
                + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_LOCATION + "<> ''"
                + _ORDER_BY_ + KEY_LOCATION + _COLLATION;
    }

    /**
     * Sql SELECT of a single table, without a WHERE clause.
     */
    private static final class SqlSelectFullTable {

        /** {@link Author}, all columns. */
        static final String AUTHORS = "SELECT * FROM " + TBL_AUTHORS.getName();

        /** {@link Series}, all columns. */
        static final String SERIES = "SELECT * FROM " + TBL_SERIES.getName();

        /** {@link Publisher}, all columns. */
        static final String PUBLISHERS = "SELECT * FROM " + TBL_PUBLISHERS.getName();

        /** {@link TocEntry}, all columns. */
        static final String TOC_ENTRIES = "SELECT * FROM " + TBL_TOC_ENTRIES.getName();

        /** {@link BooklistStyle} all columns. */
        static final String BOOKLIST_STYLES =
                "SELECT * FROM " + TBL_BOOKLIST_STYLES.getName();

        /** {@link Bookshelf} all columns; linked with the styles table. */
        private static final String BOOKSHELVES =
                SELECT_ + TBL_BOOKSHELF.dotAs(KEY_PK_ID)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_NAME)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_POS)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_OFFSET)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_ROW_ID)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_FK_STYLE)
                + ',' + TBL_BOOKLIST_STYLES.dotAs(KEY_UUID)
                + _FROM_ + TBL_BOOKSHELF.ref() + TBL_BOOKSHELF.join(TBL_BOOKLIST_STYLES);

        /** User defined {@link Bookshelf} all columns; linked with the styles table. */
        private static final String BOOKSHELVES_USER_SHELVES =
                SqlSelectFullTable.BOOKSHELVES
                + _WHERE_ + TBL_BOOKSHELF.dot(KEY_PK_ID) + ">0"
                + _ORDER_BY_ + KEY_BOOKSHELF_NAME + _COLLATION;

        /** Book UUID only, for accessing all cover image files. */
        private static final String ALL_BOOK_UUID =
                SELECT_ + KEY_BOOK_UUID + _FROM_ + TBL_BOOKS.getName();

        /**
         * All Book titles for a rebuild of the {@link DBDefinitions#KEY_TITLE_OB} column.
         */
        private static final String BOOK_TITLES =
                // The index of KEY_PK_ID, KEY_TITLE, KEY_TITLE_OB is hardcoded - don't change!
                SELECT_ + KEY_PK_ID + ',' + KEY_TITLE + ',' + KEY_TITLE_OB
                + ',' + KEY_LANGUAGE
                + _FROM_ + TBL_BOOKS.getName();

        /**
         * All Series for a rebuild of the {@link DBDefinitions#KEY_SERIES_TITLE_OB} column.
         */
        private static final String SERIES_TITLES =
                // The index of KEY_PK_ID, KEY_SERIES_TITLE, KEY_SERIES_TITLE_OB is hardcoded
                // Don't change!
                SELECT_ + KEY_PK_ID + ',' + KEY_SERIES_TITLE + ',' + KEY_SERIES_TITLE_OB
                + _FROM_ + TBL_SERIES.getName();

        /**
         * All Publishers for a rebuild of the {@link DBDefinitions#KEY_PUBLISHER_NAME_OB} column.
         */
        private static final String PUBLISHER_NAMES =
                // The index of KEY_PK_ID, KEY_PUBLISHER_NAME, KEY_PUBLISHER_NAME_OB is hardcoded
                // Don't change!
                SELECT_ + KEY_PK_ID + ',' + KEY_PUBLISHER_NAME + ',' + KEY_PUBLISHER_NAME_OB
                + _FROM_ + TBL_PUBLISHERS.getName();

        /**
         * All Series for a rebuild of the {@link DBDefinitions#KEY_TITLE_OB} column.
         */
        private static final String TOC_ENTRY_TITLES =
                // The index of KEY_PK_ID, KEY_TITLE, KEY_TITLE_OB is hardcoded - don't change!
                SELECT_ + KEY_PK_ID + ',' + KEY_TITLE + ',' + KEY_TITLE_OB
                + _FROM_ + TBL_TOC_ENTRIES.getName();

        /**
         * The SELECT and FROM clause for getting a book (list).
         * <p>
         */
        private static final String SQL_BOOK;

        static {
            // Developer: adding fields ? Now is a good time to update {@link Book#duplicate}/
            // Note we could use TBL_BOOKS.dot("*")
            // We'ld fetch the unneeded TITLE_OB field, but that would be ok.
            // Nevertheless, listing the fields here gives a better understanding
            final StringBuilder sqlBookTmp = new StringBuilder(
                    SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
                    + ',' + TBL_BOOKS.dotAs(KEY_BOOK_UUID)
                    + ',' + TBL_BOOKS.dotAs(KEY_TITLE)
                    // publication data
                    + ',' + TBL_BOOKS.dotAs(KEY_ISBN)
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
                    // Main/public description about the content/publication
                    + ',' + TBL_BOOKS.dotAs(KEY_DESCRIPTION)

                    // partially edition info, partially user-owned info.
                    + ',' + TBL_BOOKS.dotAs(KEY_EDITION_BITMASK)
                    // user notes
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
                    + ',' + TBL_BOOKS.dotAs(KEY_UTC_ADDED)
                    + ',' + TBL_BOOKS.dotAs(KEY_UTC_LAST_UPDATED));

            for (Domain domain : SearchEngineRegistry.getExternalIdDomains()) {
                sqlBookTmp.append(',').append(TBL_BOOKS.dotAs(domain.getName()));
            }

            //NEWTHINGS: adding a new search engine: optional: add engine specific keys
            sqlBookTmp.append(',').append(TBL_BOOKS.dotAs(KEY_UTC_LAST_SYNC_DATE_GOODREADS));

            sqlBookTmp.append(',').append(TBL_BOOKS.dotAs(KEY_EID_CALIBRE_UUID));

            SQL_BOOK = sqlBookTmp.toString()
                       // COALESCE nulls to "" for the LEFT OUTER JOIN'ed tables
                       + ',' + "COALESCE(" + TBL_BOOK_LOANEE.dot(KEY_LOANEE) + ", '')" + _AS_
                       + KEY_LOANEE

                       + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.leftOuterJoin(TBL_BOOK_LOANEE);
        }
    }

    /**
     * Sql SELECT returning a list, with a WHERE clause.
     */
    private static final class SqlSelect {

        static final String BOOK_ID_LIST_BY_TOC_ENTRY_ID =
                SELECT_ + KEY_FK_BOOK + _FROM_ + TBL_BOOK_TOC_ENTRIES.getName()
                + _WHERE_ + KEY_FK_TOC_ENTRY + "=?";
        /**
         * Find the {@link Book} id based on a search for the ISBN (both 10 & 13).
         */
        static final String BY_VALID_ISBN =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_ISBN + " LIKE ? OR " + KEY_ISBN + " LIKE ?";
        /**
         * Find the {@link Book} id based on a search for the ISBN.
         * The isbn need not be valid and can in fact be any code whatsoever.
         */
        static final String BY_ISBN =
                SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                + _WHERE_ + KEY_ISBN + " LIKE ?";

        /**
         * All Bookshelves for a Book; ordered by name.
         */
        private static final String BOOKSHELVES_BY_BOOK_ID =
                SELECT_DISTINCT_
                + TBL_BOOKSHELF.dotAs(KEY_PK_ID)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_NAME)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_POS)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_OFFSET)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_ROW_ID)
                + ',' + TBL_BOOKSHELF.dotAs(KEY_FK_STYLE)
                + ',' + TBL_BOOKLIST_STYLES.dotAs(KEY_UUID)

                + _FROM_ + TBL_BOOK_BOOKSHELF.ref()
                + TBL_BOOK_BOOKSHELF.join(TBL_BOOKSHELF)
                + TBL_BOOKSHELF.join(TBL_BOOKLIST_STYLES)
                + _WHERE_ + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME) + _COLLATION;

        /**
         * All Authors for a Book; ordered by position, family, given.
         */
        private static final String AUTHORS_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_AUTHORS.dotAs(KEY_PK_ID)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME_OB)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES_OB)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_IS_COMPLETE)
                + ',' + SqlColumns.getDisplayAuthor(TBL_AUTHORS.getAlias(), false)
                + _AS_ + KEY_AUTHOR_FORMATTED
                + ',' + TBL_BOOK_AUTHOR.dotAs(KEY_BOOK_AUTHOR_POSITION)
                + ',' + TBL_BOOK_AUTHOR.dotAs(KEY_BOOK_AUTHOR_TYPE_BITMASK)

                + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_
                + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION)
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

        /**
         * All Series for a Book; ordered by position, name.
         */
        private static final String SERIES_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_SERIES.dotAs(KEY_PK_ID)
                + ',' + TBL_SERIES.dotAs(KEY_SERIES_TITLE)
                + ',' + TBL_SERIES.dotAs(KEY_SERIES_TITLE_OB)
                + ',' + TBL_SERIES.dotAs(KEY_SERIES_IS_COMPLETE)
                + ',' + TBL_BOOK_SERIES.dotAs(KEY_BOOK_NUM_IN_SERIES)
                + ',' + TBL_BOOK_SERIES.dotAs(KEY_BOOK_SERIES_POSITION)

                + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION)
                + ',' + TBL_SERIES.dot(KEY_SERIES_TITLE_OB) + _COLLATION;

        /**
         * All Publishers for a Book; ordered by position, name.
         */
        private static final String PUBLISHER_BY_BOOK_ID =
                SELECT_DISTINCT_ + TBL_PUBLISHERS.dotAs(KEY_PK_ID)
                + ',' + TBL_PUBLISHERS.dotAs(KEY_PUBLISHER_NAME)
                + ',' + TBL_PUBLISHERS.dotAs(KEY_PUBLISHER_NAME_OB)
                + ',' + TBL_BOOK_PUBLISHER.dotAs(KEY_BOOK_PUBLISHER_POSITION)

                + _FROM_ + TBL_BOOK_PUBLISHER.ref() + TBL_BOOK_PUBLISHER.join(TBL_PUBLISHERS)
                + _WHERE_ + TBL_BOOK_PUBLISHER.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_PUBLISHER.dot(KEY_BOOK_PUBLISHER_POSITION)
                + ',' + TBL_PUBLISHERS.dot(KEY_PUBLISHER_NAME_OB) + _COLLATION;

        /**
         * All TocEntry's for a Book; ordered by position in the book.
         */
        private static final String TOC_ENTRIES_BY_BOOK_ID =
                SELECT_ + TBL_TOC_ENTRIES.dotAs(KEY_PK_ID)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_FK_AUTHOR)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_TITLE)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_DATE_FIRST_PUBLICATION)
                // for convenience, we fetch the Author here
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_IS_COMPLETE)

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
                + _ORDER_BY_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_BOOK_TOC_ENTRY_POSITION);

        /**
         * All Books (id only!) for a given Author.
         */
        private static final String BOOK_IDS_BY_AUTHOR_ID =
                SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
                + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_AUTHOR)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR) + "=?";

        /**
         * All Books (id only!) for a given Series.
         */
        private static final String BOOK_IDS_BY_SERIES_ID =
                SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
                + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_SERIES) + "=?";

        /**
         * All Books (id only!) for a given Publisher.
         */
        private static final String BOOK_IDS_BY_PUBLISHER_ID =
                SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
                + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_PUBLISHER)
                + _WHERE_ + TBL_BOOK_PUBLISHER.dot(KEY_FK_PUBLISHER) + "=?";

        /**
         * All TocEntry's for an Author.
         * <p>
         * ORDER BY clause NOT added here, as this statement is used in a union as well.
         * <p>
         * We need KEY_TITLE_OB as it will be used to ORDER BY
         */
        private static final String TOC_ENTRIES_BY_AUTHOR_ID =
                SELECT_ + "'" + TocEntry.TYPE_TOC + "' AS " + KEY_TOC_TYPE
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_PK_ID)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_TITLE)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_TITLE_OB)
                + ',' + TBL_TOC_ENTRIES.dotAs(KEY_DATE_FIRST_PUBLICATION)
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
                SELECT_ + "'" + TocEntry.TYPE_BOOK + "' AS " + KEY_TOC_TYPE
                + ',' + TBL_BOOKS.dotAs(KEY_PK_ID)
                + ',' + TBL_BOOKS.dotAs(KEY_TITLE)
                + ',' + TBL_BOOKS.dotAs(KEY_TITLE_OB)
                + ',' + TBL_BOOKS.dotAs(KEY_DATE_FIRST_PUBLICATION)
                + ",1 AS " + KEY_BOOK_COUNT;

        /**
         * A subset of book columns, to be used for searches on Goodreads.
         */
        private static final String BOOK_COLUMNS_FOR_GOODREADS_SEARCH =
                SELECT_ + TBL_BOOKS.dotAs(KEY_PK_ID)
                + ',' + TBL_BOOKS.dotAs(KEY_TITLE)
                + ',' + TBL_BOOKS.dotAs(KEY_ISBN)
                + ',' + SqlColumns.getDisplayAuthor(TBL_AUTHORS.getAlias(), true)
                + _AS_ + KEY_AUTHOR_FORMATTED_GIVEN_FIRST

                + _FROM_ + TBL_BOOKS.ref()
                + TBL_BOOKS.join(TBL_BOOK_AUTHOR) + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOKS.dot(KEY_PK_ID) + "=?";
    }


    /**
     * Sql specific for syncing books with Goodreads.
     */
    private static final class SqlGoodreadsSendBook {

        /**
         * Base SELECT from {@link DBDefinitions#TBL_BOOKS} for the fields
         * we need to send a Book to Goodreads.
         * <p>
         * Must be kept in sync with {@link GoodreadsManager#sendOneBook}
         */
        private static final String BASE_SELECT =
                SELECT_ + KEY_PK_ID
                + ',' + KEY_ISBN
                + ',' + KEY_EID_GOODREADS_BOOK
                + ',' + KEY_READ
                + ',' + KEY_READ_START
                + ',' + KEY_READ_END
                + ',' + KEY_RATING
                + _FROM_ + TBL_BOOKS.getName();

        /**
         * Get the needed {@link Book} fields for sending to Goodreads.
         * <p>
         * param KEY_PK_ID of the Book
         */
        static final String SINGLE_BOOK =
                BASE_SELECT + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Get the needed {@link Book} fields for sending to Goodreads.
         * <p>
         * param KEY_PK_ID of the first Book
         * <p>
         * Send all books.
         */
        static final String ALL_BOOKS =
                BASE_SELECT + _WHERE_ + KEY_PK_ID + ">?"
                + _ORDER_BY_ + KEY_PK_ID;

        /**
         * Get the needed {@link Book} fields for sending to Goodreads.
         * <p>
         * param KEY_PK_ID of the first Book
         * <p>
         * Send only new/updated books
         */
        static final String UPDATED_BOOKS =
                BASE_SELECT + _WHERE_ + KEY_PK_ID + ">?"
                + " AND " + KEY_UTC_LAST_UPDATED + '>' + KEY_UTC_LAST_SYNC_DATE_GOODREADS
                + _ORDER_BY_ + KEY_PK_ID;
    }


    /**
     * Sql INSERT.
     */
    private static final class SqlInsert {

        static final String INSERT_INTO_ = "INSERT INTO ";

        static final String BOOKSHELF =
                INSERT_INTO_ + TBL_BOOKSHELF.getName()
                + '(' + KEY_BOOKSHELF_NAME
                + ',' + KEY_FK_STYLE
                + ',' + KEY_BOOKSHELF_BL_TOP_POS
                + ',' + KEY_BOOKSHELF_BL_TOP_OFFSET

                + ") VALUES (?,?,?,?)";

        static final String AUTHOR =
                INSERT_INTO_ + TBL_AUTHORS.getName()
                + '(' + KEY_AUTHOR_FAMILY_NAME
                + ',' + KEY_AUTHOR_FAMILY_NAME_OB
                + ',' + KEY_AUTHOR_GIVEN_NAMES
                + ',' + KEY_AUTHOR_GIVEN_NAMES_OB
                + ',' + KEY_AUTHOR_IS_COMPLETE
                + ") VALUES (?,?,?,?,?)";

        static final String SERIES =
                INSERT_INTO_ + TBL_SERIES.getName()
                + '(' + KEY_SERIES_TITLE
                + ',' + KEY_SERIES_TITLE_OB
                + ',' + KEY_SERIES_IS_COMPLETE
                + ") VALUES (?,?,?)";

        static final String PUBLISHER =
                INSERT_INTO_ + TBL_PUBLISHERS.getName()
                + '(' + KEY_PUBLISHER_NAME
                + ',' + KEY_PUBLISHER_NAME_OB
                + ") VALUES (?,?)";

        static final String TOC_ENTRY =
                INSERT_INTO_ + TBL_TOC_ENTRIES.getName()
                + '(' + KEY_FK_AUTHOR
                + ',' + KEY_TITLE
                + ',' + KEY_TITLE_OB
                + ',' + KEY_DATE_FIRST_PUBLICATION
                + ") VALUES (?,?,?,?)";


        static final String BOOK_TOC_ENTRY =
                INSERT_INTO_ + TBL_BOOK_TOC_ENTRIES.getName()
                + '(' + KEY_FK_TOC_ENTRY
                + ',' + KEY_FK_BOOK
                + ',' + KEY_BOOK_TOC_ENTRY_POSITION
                + ") VALUES (?,?,?)";

        static final String BOOK_BOOKSHELF =
                INSERT_INTO_ + TBL_BOOK_BOOKSHELF.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_FK_BOOKSHELF
                + ") VALUES (?,?)";

        static final String BOOK_AUTHOR =
                INSERT_INTO_ + TBL_BOOK_AUTHOR.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_FK_AUTHOR
                + ',' + KEY_BOOK_AUTHOR_POSITION
                + ',' + KEY_BOOK_AUTHOR_TYPE_BITMASK
                + ") VALUES(?,?,?,?)";

        static final String BOOK_SERIES =
                INSERT_INTO_ + TBL_BOOK_SERIES.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_FK_SERIES
                + ',' + KEY_BOOK_NUM_IN_SERIES
                + ',' + KEY_BOOK_SERIES_POSITION
                + ") VALUES(?,?,?,?)";

        static final String BOOK_PUBLISHER =
                INSERT_INTO_ + TBL_BOOK_PUBLISHER.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_FK_PUBLISHER
                + ',' + KEY_BOOK_PUBLISHER_POSITION
                + ") VALUES(?,?,?)";


        static final String BOOK_LOANEE =
                INSERT_INTO_ + TBL_BOOK_LOANEE.getName()
                + '(' + KEY_FK_BOOK
                + ',' + KEY_LOANEE
                + ") VALUES(?,?)";


        static final String BOOKLIST_STYLE =
                INSERT_INTO_ + TBL_BOOKLIST_STYLES.getName()
                + '(' + KEY_UUID
                + ',' + KEY_STYLE_IS_BUILTIN
                + ") VALUES (?,?)";
    }

    /**
     * Sql UPDATE. Intention is to only have single-column updates here and do multi-column
     * with the ContentValues based update method.
     */
    private static final class SqlUpdate {

        static final String UPDATE_ = "UPDATE ";
        static final String _SET_ = " SET ";

        /**
         * Update a single Book's last sync date with Goodreads.
         */
        static final String GOODREADS_LAST_SYNC_DATE =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_SYNC_DATE_GOODREADS + "=current_timestamp"
                + _WHERE_ + KEY_PK_ID + "=?";

        /**
         * Update a single Book's Goodreads id. Do not update the last-update-date!
         */
        static final String GOODREADS_BOOK_ID =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_EID_GOODREADS_BOOK + "=?"
                + _WHERE_ + KEY_PK_ID + "=?";

        static final String FORMAT =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_FORMAT + "=?"
                + _WHERE_ + KEY_FORMAT + "=?";

        static final String COLOR =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_COLOR + "=?"
                + _WHERE_ + KEY_COLOR + "=?";

        static final String GENRE =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_GENRE + "=?"
                + _WHERE_ + KEY_GENRE + "=?";

        static final String LANGUAGE =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_LANGUAGE + "=?"
                + _WHERE_ + KEY_LANGUAGE + "=?";

        static final String LOCATION =
                UPDATE_ + TBL_BOOKS.getName()
                + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                + ',' + KEY_LOCATION + "=?"
                + _WHERE_ + KEY_LOCATION + "=?";
    }

    /**
     * Sql DELETE commands.
     * <p>
     * All 'link' tables will be updated due to their FOREIGN KEY constraints.
     * The 'other-side' of a link table is cleaned by triggers.
     */
    static final class SqlDelete {

        static final String _NOT_IN_ = " NOT IN ";
        private static final String DELETE_FROM_ = "DELETE FROM ";
        /** Delete a {@link Book}. */
        static final String BOOK_BY_ID =
                DELETE_FROM_ + TBL_BOOKS.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete a {@link Bookshelf}. */
        static final String BOOKSHELF_BY_ID =
                DELETE_FROM_ + TBL_BOOKSHELF.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete an {@link Author}. */
        static final String AUTHOR_BY_ID =
                DELETE_FROM_ + TBL_AUTHORS.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete a {@link Series}. */
        static final String SERIES_BY_ID =
                DELETE_FROM_ + TBL_SERIES.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete a {@link Publisher}. */
        static final String PUBLISHER_BY_ID =
                DELETE_FROM_ + TBL_PUBLISHERS.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete a {@link TocEntry}. */
        static final String TOC_ENTRY =
                DELETE_FROM_ + TBL_TOC_ENTRIES.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /** Delete a {@link BooklistStyle}. */
        static final String STYLE_BY_ID =
                DELETE_FROM_ + TBL_BOOKLIST_STYLES.getName() + _WHERE_ + KEY_PK_ID + "=?";
        /**
         * Delete the link between a {@link Book} and an {@link Author}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_AUTHOR_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_AUTHOR.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Delete the link between a {@link Book} and a {@link Bookshelf}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_BOOKSHELF_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_BOOKSHELF.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Delete the link between a {@link Book} and a {@link Series}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_SERIES_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_SERIES.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Delete the link between a {@link Book} and a {@link Publisher}.
         * <p>
         * This is done when a book is updated; first delete all links, then re-create them.
         */
        static final String BOOK_PUBLISHER_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_PUBLISHER.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Delete the link between a {@link Book} and a {@link TocEntry}.
         * <p>
         * This is done when a TOC is updated; first delete all links, then re-create them.
         */
        static final String BOOK_TOC_ENTRIES_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_TOC_ENTRIES.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Delete the loan of a {@link Book}; i.e. 'return the book'.
         */
        static final String BOOK_LOANEE_BY_BOOK_ID =
                DELETE_FROM_ + TBL_BOOK_LOANEE.getName() + _WHERE_ + KEY_FK_BOOK + "=?";
        /**
         * Purge an {@link Author} if no longer in use (check both book_author AND toc_entries).
         */
        static final String PURGE_AUTHORS =
                DELETE_FROM_ + TBL_AUTHORS.getName()
                + _WHERE_ + KEY_PK_ID + _NOT_IN_
                + "(SELECT DISTINCT " + KEY_FK_AUTHOR + _FROM_ + TBL_BOOK_AUTHOR.getName() + ')'
                + " AND " + KEY_PK_ID + _NOT_IN_
                + "(SELECT DISTINCT " + KEY_FK_AUTHOR + _FROM_ + TBL_TOC_ENTRIES.getName() + ')';

        /**
         * Purge a {@link Series} if no longer in use.
         */
        static final String PURGE_SERIES =
                DELETE_FROM_ + TBL_SERIES.getName()
                + _WHERE_ + KEY_PK_ID + _NOT_IN_
                + "(SELECT DISTINCT " + KEY_FK_SERIES + _FROM_ + TBL_BOOK_SERIES.getName() + ')';

        /**
         * Purge a {@link Publisher} if no longer in use.
         */
        static final String PURGE_PUBLISHERS =
                DELETE_FROM_ + TBL_PUBLISHERS.getName()
                + _WHERE_ + KEY_PK_ID + _NOT_IN_
                + "(SELECT DISTINCT " + KEY_FK_PUBLISHER
                + _FROM_ + TBL_BOOK_PUBLISHER.getName() + ')';


        static final String BOOK_LIST_NODE_STATE_BY_BOOKSHELF =
                DELETE_FROM_ + DBDefinitions.TBL_BOOK_LIST_NODE_STATE
                + _WHERE_ + KEY_FK_BOOKSHELF + "=?";

        static final String BOOK_LIST_NODE_STATE_BY_STYLE =
                DELETE_FROM_ + DBDefinitions.TBL_BOOK_LIST_NODE_STATE
                + _WHERE_ + KEY_FK_STYLE + "=?";

        private SqlDelete() {
        }
    }

    /**
     * Sql specific for FTS.
     */
    public static final class SqlFTS {

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_AUTHORS_BY_BOOK_ID =
                SELECT_ + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME)
                + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES)
                + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION);

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_SERIES_BY_BOOK_ID =
                SELECT_ + TBL_SERIES.dot(KEY_SERIES_TITLE) + "||' '||"
                + " COALESCE(" + TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES) + ",'')"
                + _AS_ + KEY_SERIES_TITLE
                + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION);

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_PUBLISHERS_BY_BOOK_ID =
                SELECT_ + TBL_PUBLISHERS.dotAs(KEY_PUBLISHER_NAME)
                + _FROM_ + TBL_BOOK_PUBLISHER.ref() + TBL_BOOK_PUBLISHER.join(TBL_PUBLISHERS)
                + _WHERE_ + TBL_BOOK_PUBLISHER.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_PUBLISHER.dot(KEY_BOOK_PUBLISHER_POSITION);

        /** Used during insert of a book. Minimal column list. Ordered by position. */
        static final String GET_TOC_TITLES_BY_BOOK_ID =
                SELECT_ + TBL_TOC_ENTRIES.dotAs(KEY_TITLE)
                + _FROM_ + TBL_TOC_ENTRIES.ref() + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_FK_BOOK) + "=?"
                + _ORDER_BY_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_BOOK_TOC_ENTRY_POSITION);

        /** the body of an INSERT INTO [table]. Used more than once. */
        static final String INSERT_BODY =
                " (" + KEY_TITLE
                + ',' + KEY_FTS_AUTHOR_NAME
                + ',' + KEY_SERIES_TITLE
                + ',' + KEY_DESCRIPTION
                + ',' + KEY_PRIVATE_NOTES
                + ',' + KEY_PUBLISHER_NAME
                + ',' + KEY_GENRE
                + ',' + KEY_LOCATION
                + ',' + KEY_ISBN
                + ',' + KEY_FTS_TOC_ENTRY_TITLE

                + ',' + KEY_FTS_BOOK_ID
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
                + ',' + KEY_PUBLISHER_NAME + "=?"
                + ',' + KEY_GENRE + "=?"
                + ',' + KEY_LOCATION + "=?"
                + ',' + KEY_ISBN + "=?"
                + ',' + KEY_FTS_TOC_ENTRY_TITLE + "=?"

                + _WHERE_ + KEY_FTS_BOOK_ID + "=?";

        /** Standard Local-search. */
        static final String SEARCH_SUGGESTIONS =
                // KEY_FTS_BOOKS_PK is the _id into the books table.
                SELECT_ + KEY_FTS_BOOK_ID + _AS_ + KEY_PK_ID
                + ',' + (TBL_FTS_BOOKS.dot(KEY_TITLE)
                         + _AS_ + SearchManager.SUGGEST_COLUMN_TEXT_1)
                + ',' + (TBL_FTS_BOOKS.dot(KEY_FTS_AUTHOR_NAME)
                         + _AS_ + SearchManager.SUGGEST_COLUMN_TEXT_2)
                + ',' + (TBL_FTS_BOOKS.dot(KEY_TITLE)
                         + _AS_ + SearchManager.SUGGEST_COLUMN_INTENT_DATA)
                + _FROM_ + TBL_FTS_BOOKS.getName()
                + _WHERE_ + TBL_FTS_BOOKS.getName() + " MATCH ?";

        /** Advanced Local-search. */
        static final String SEARCH
                = SELECT_
                  // KEY_FTS_BOOKS_PK is the _id into the books table.
                  + KEY_FTS_BOOK_ID + _AS_ + KEY_PK_ID
                  + _FROM_ + TBL_FTS_BOOKS.getName()
                  + _WHERE_ + TBL_FTS_BOOKS.getName() + " MATCH ?"
                  + " LIMIT ?";

        /** Used during a full FTS rebuild. Minimal column list. */
        private static final String ALL_BOOKS =
                SELECT_ + KEY_PK_ID
                + ',' + KEY_TITLE
                + ',' + KEY_DESCRIPTION
                + ',' + KEY_PRIVATE_NOTES
                + ',' + KEY_GENRE
                + ',' + KEY_LOCATION
                + ',' + KEY_ISBN
                + _FROM_ + TBL_BOOKS.getName();

        /** Used during insert of a book. Minimal column list. */
        static final String BOOK_BY_ID = ALL_BOOKS + _WHERE_ + KEY_PK_ID + "=?";

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
        static String cleanupFtsCriterion(@Nullable final String searchText,
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
            // 'previous' character
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
            final String cleanedText = parameter.toString().trim();

            if (domain != null) {
                // prepend each word with the FTS column name.
                final StringBuilder result = new StringBuilder();
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
         * @param author        Author related keywords to find
         * @param title         Title related keywords to find
         * @param seriesTitle   Series title related keywords to find
         * @param publisherName Publisher name related keywords to find
         * @param keywords      Keywords to find anywhere in book; this includes titles and authors
         *
         * @return an query string suited to search FTS for the specified parameters,
         * or {@code ""} if all input was empty
         */
        @NonNull
        public static String createMatchString(@Nullable final String author,
                                               @Nullable final String title,
                                               @Nullable final String seriesTitle,
                                               @Nullable final String publisherName,
                                               @Nullable final String keywords) {

            return (cleanupFtsCriterion(keywords, null)
                    + cleanupFtsCriterion(author, KEY_FTS_AUTHOR_NAME)
                    + cleanupFtsCriterion(title, KEY_TITLE)
                    + cleanupFtsCriterion(seriesTitle, KEY_SERIES_TITLE)
                    + cleanupFtsCriterion(publisherName, KEY_PUBLISHER_NAME))
                    .trim();
        }
    }
}
