/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.TypedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.AuthorDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BaseDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableInfo;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookAsWork;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

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
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_AUTHOR_FAMILY_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_AUTHOR_FORMATTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_AUTHOR_GIVEN_NAMES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_AUTHOR_GIVEN_NAMES_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_AUTHOR_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOKSHELF_BL_TOP_OFFSET;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOKSHELF_BL_TOP_POS;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOK_AUTHOR_TYPE_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOK_CONDITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOK_CONDITION_COVER;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOK_COUNT;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOK_DATE_PUBLISHED;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOK_PUBLISHER_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOK_TOC_ENTRY_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_CALIBRE_BOOK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_CALIBRE_BOOK_MAIN_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_CALIBRE_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_COLOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_DATE_ACQUIRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_DATE_FIRST_PUBLICATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_DESCRIPTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_EDITION_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FK_CALIBRE_LIBRARY;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FK_TOC_ENTRY;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FTS_AUTHOR_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FTS_BOOK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_FTS_TOC_ENTRY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_GENRE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_LANGUAGE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_LOCATION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_PAGES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_PRICE_LISTED;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_PRICE_LISTED_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_PRICE_PAID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_PRICE_PAID_CURRENCY;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_PRINT_RUN;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_PRIVATE_NOTES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_PUBLISHER_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_RATING;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_READ_END;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_READ_START;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_SERIES_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_SERIES_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_SIGNED;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_STYLE_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_TITLE_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_TOC_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_TOC_TYPE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_UTC_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_UTC_GOODREADS_LAST_SYNC_DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKeys.KEY_UTC_LAST_UPDATED;

/**
 * Database access helper class.
 *
 * <strong>Note:</strong> don't be tempted to turn this into a singleton...
 * this class is not fully thread safe due to the use of a statement manager.
 *
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
public class BookDao
        extends BaseDaoImpl
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

    /** Log tag. */
    private static final String TAG = "BookDao";

    /** statement names; keys into the cache map. */
    private static final String STMT_CHECK_BOOK_EXISTS = "CheckBookExists";

    private static final String STMT_GET_BOOK_UPDATE_DATE = "gBookUpdateDate";

    private static final String STMT_GET_BOOK_UUID = "gBookUuid";
    private static final String STMT_GET_BOOK_ID_FROM_UUID = "gBookIdFromUuid";

    private static final String STMT_INSERT_BOOK_AUTHORS = "iBookAuthors";
    private static final String STMT_INSERT_BOOK_SERIES = "iBookSeries";
    private static final String STMT_INSERT_BOOK_PUBLISHER = "iBookPublisher";
    private static final String STMT_INSERT_BOOK_BOOKSHELF = "iBookBookshelf";

    private static final String STMT_DELETE_BOOK = "dBook";
    private static final String STMT_DELETE_BOOK_TOC_ENTRIES = "dBookTocEntries";
    private static final String STMT_DELETE_BOOK_AUTHORS = "dBookAuthors";
    private static final String STMT_DELETE_BOOK_BOOKSHELF = "dBookBookshelf";
    private static final String STMT_DELETE_BOOK_SERIES = "dBookSeries";
    private static final String STMT_DELETE_BOOK_PUBLISHER = "dBookPublisher";

    private static final String STMT_INSERT_FTS = "iFts";
    private static final String STMT_UPDATE_FTS = "uFts";

    /** log error string. */
    private static final String ERROR_FAILED_TO_UPDATE_FTS = "Failed to update FTS";
    /** log error string. */
    private static final String ERROR_CREATING_BOOK_FROM = "Failed creating book from\n";
    /** log error string. */
    private static final String ERROR_UPDATING_BOOK_FROM = "Failed updating book from\n";
    /** log error string. */
    private static final String ERROR_STORING_COVERS = "Failed storing the covers for book from\n";

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

    /** Collection of statements pre-compiled for this object. */
    private final SqlStatementManager mSqlStatementManager;

    /** DEBUG: Indicates close() has been called. Also see {@link AutoCloseable#close()}. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     *
     * @param logTag of this DAO for logging.
     */
    public BookDao(@NonNull final String logTag) {
        super(logTag);
        mSqlStatementManager = new SqlStatementManager(mDb);
    }

    /**
     * Bind a string or {@code null} value to a parameter since binding a {@code null}
     * in bindString produces an error.
     * <p>
     * <strong>Note:</strong> We specifically want to use the default Locale for this.
     */
    private static void bindStringOrNull(@NonNull final SynchronizedStatement stmt,
                                         final int position,
                                         @Nullable final CharSequence text) {
        if (text == null) {
            stmt.bindNull(position);
        } else {
            stmt.bindString(position, ParseUtils.toAscii(text));
        }
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

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            book.preprocessForStoring(context, true);

            // Make sure we have at least one author
            final List<Author> authors = book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
            if (authors.isEmpty()) {
                throw new DaoWriteException("No authors for book=" + book);
            }

            // correct field types if needed, and filter out fields we don't have in the db table.
            final ContentValues cv = filterValues(TBL_BOOKS, book, book.getLocale(context));

            final String addedOrUpdatedNow = encodeDate(LocalDateTime.now(ZoneOffset.UTC));

            // if we do NOT have a date set, use 'now'
            if (!cv.containsKey(KEY_UTC_ADDED)) {
                cv.put(KEY_UTC_ADDED, addedOrUpdatedNow);
            }
            // if we do NOT have a date set, use 'now'
            if (!cv.containsKey(KEY_UTC_LAST_UPDATED)) {
                cv.put(KEY_UTC_LAST_UPDATED, addedOrUpdatedNow);
            }

            // if allowed, and we have an id, use it.
            if ((flags & BOOK_FLAG_USE_ID_IF_PRESENT) != 0 && book.getId() > 0) {
                cv.put(KEY_PK_ID, book.getId());
            } else {
                // in all other circumstances, make absolutely sure we DO NOT pass in an id.
                cv.remove(KEY_PK_ID);
            }

            // go!
            final long newBookId = mDb.insert(TBL_BOOKS.getName(), cv);
            if (newBookId <= 0) {
                book.putLong(KEY_PK_ID, 0);
                book.remove(KEY_BOOK_UUID);
                throw new DaoWriteException(ERROR_CREATING_BOOK_FROM + book);
            }

            // set the new id/uuid on the Book itself
            book.putLong(KEY_PK_ID, newBookId);
            // always lookup the UUID
            // (even if we inserted with a uuid... to protect against future changes)
            final String uuid = getBookUuid(newBookId);
            SanityCheck.requireValue(uuid, "uuid");
            book.putString(KEY_BOOK_UUID, uuid);

            // next we add the links to series, authors,...
            insertBookLinks(context, book, flags);

            // and populate the search suggestions table
            ftsInsert(newBookId);

            // lastly we move the covers from the cache dir to their permanent dir/name
            if (!book.storeCovers()) {
                book.putLong(KEY_PK_ID, 0);
                book.remove(KEY_BOOK_UUID);
                throw new DaoWriteException(ERROR_STORING_COVERS + book);
            }

            // all done
            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
            return newBookId;

        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_CREATING_BOOK_FROM + book, e);

        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
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

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

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
                cv.put(KEY_UTC_LAST_UPDATED, encodeDate(LocalDateTime.now(ZoneOffset.UTC)));
            }

            // Reminder: We're updating ONLY the fields present in the ContentValues.
            // Other fields in the database row are not affected.
            // go !
            final boolean success =
                    0 < mDb.update(TBL_BOOKS.getName(), cv, KEY_PK_ID + "=?",
                                   new String[]{String.valueOf(book.getId())});

            if (success) {
                // always lookup the UUID
                final String uuid = getBookUuid(book.getId());
                SanityCheck.requireValue(uuid, "uuid");
                book.putString(KEY_BOOK_UUID, uuid);

                insertBookLinks(context, book, flags);

                ftsUpdate(book.getId());

                if (!book.storeCovers()) {
                    throw new DaoWriteException(ERROR_STORING_COVERS + book);
                }

                if (txLock != null) {
                    mDb.setTransactionSuccessful();
                }
            } else {
                throw new DaoWriteException(ERROR_UPDATING_BOOK_FROM + book);
            }
        } catch (@NonNull final SQLiteException | IllegalArgumentException e) {
            throw new DaoWriteException(ERROR_UPDATING_BOOK_FROM + book, e);

        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
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

        final TableInfo tableInfo = mDb.getTableInfo(tableDefinition);

        final ContentValues cv = new ContentValues();
        // Create the arguments
        for (final String key : dataManager.keySet()) {
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
     * @param book to delete
     *
     * @return {@code true} if a row was deleted
     */
    public boolean delete(@NonNull final Book book) {
        final boolean success = deleteBook(book.getId());
        if (success) {
            book.setId(0);
            book.remove(KEY_BOOK_UUID);
        }
        return success;
    }

    /**
     * Delete the given book (and its covers).
     *
     * @param bookId of the book.
     *
     * @return {@code true} if a row was deleted
     */
    public boolean deleteBook(@IntRange(from = 1) final long bookId) {

        final String uuid = getBookUuid(bookId);
        // sanity check
        if (uuid == null) {
            return false;
        }

        int rowsAffected = 0;
        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            final SynchronizedStatement stmt = mSqlStatementManager.get(
                    STMT_DELETE_BOOK, () -> Sql.Delete.BOOK_BY_ID);

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (stmt) {
                stmt.bindLong(1, bookId);
                rowsAffected = stmt.executeUpdateDelete();
            }

            if (rowsAffected > 0) {
                // sanity check
                if (!uuid.isEmpty()) {
                    // Delete the covers from the file system.
                    for (int cIdx = 0; cIdx < 2; cIdx++) {
                        FileUtils.delete(Book.getUuidCoverFile(uuid, cIdx));
                    }
                    // and from the cache.
                    if (ImageUtils.isImageCachingEnabled()) {
                        ServiceLocator.getInstance().getCoverCacheDao().delete(uuid);
                    }
                }
            }
            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } catch (@NonNull final RuntimeException e) {
            Logger.error(TAG, e, "Failed to delete book");

        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
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

        if (book.contains(Book.BKEY_BOOKSHELF_LIST)) {
            // Bookshelves will be inserted if new, but not updated
            insertBookBookshelf(context,
                                book.getId(),
                                book.getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST));
        }

        if (book.contains(Book.BKEY_AUTHOR_LIST)) {
            // Authors will be inserted if new, but not updated
            insertBookAuthors(context,
                              book.getId(),
                              book.getParcelableArrayList(Book.BKEY_AUTHOR_LIST),
                              lookupLocale,
                              bookLocale);
        }

        if (book.contains(Book.BKEY_SERIES_LIST)) {
            // Series will be inserted if new, but not updated
            insertBookSeries(context,
                             book.getId(),
                             book.getParcelableArrayList(Book.BKEY_SERIES_LIST),
                             lookupLocale,
                             bookLocale);
        }

        if (book.contains(Book.BKEY_PUBLISHER_LIST)) {
            // Publishers will be inserted if new, but not updated
            insertBookPublishers(context,
                                 book.getId(),
                                 book.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST),
                                 lookupLocale,
                                 bookLocale);
        }

        if (book.contains(Book.BKEY_TOC_LIST)) {
            // TOC entries are two steps away; they can exist in other books
            // Hence we will both insert new entries
            // AND update existing ones if needed.
            saveTocList(context,
                        book.getId(),
                        book.getParcelableArrayList(Book.BKEY_TOC_LIST),
                        lookupLocale,
                        bookLocale);
        }

        if (book.contains(KEY_LOANEE)) {
            ServiceLocator.getInstance().getLoaneeDao().setLoanee(book, book.getString(KEY_LOANEE));
        }

        if (book.contains(KEY_CALIBRE_BOOK_UUID)) {
            // Calibre libraries will be inserted if new, but not updated
            insertBookCalibreData(book);
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

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // fix id's and remove duplicates; shelves don't use a Locale, hence no lookup done.
        Bookshelf.pruneList(list);

        // Just delete all current links; we'll insert them from scratch.
        deleteBookBookshelfByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_BOOK_BOOKSHELF, () -> Sql.Insert.BOOK_BOOKSHELF);

        final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();
        for (final Bookshelf bookshelf : list) {
            // create if needed - do NOT do updates here
            if (bookshelf.getId() == 0) {
                if (bookshelfDao.insert(context, bookshelf) == -1) {
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
                STMT_DELETE_BOOK_BOOKSHELF, () -> Sql.Delete.BOOK_BOOKSHELF_BY_BOOK_ID);

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
    public void insertBookAuthors(@NonNull final Context context,
                                  @IntRange(from = 1) final long bookId,
                                  @NonNull final Collection<Author> list,
                                  final boolean lookupLocale,
                                  @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // fix id's and remove duplicates
        Author.pruneList(list, context, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch (easier positioning)
        deleteBookAuthorByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_BOOK_AUTHORS, () -> Sql.Insert.BOOK_AUTHOR);

        int position = 0;
        for (final Author author : list) {
            // create if needed - do NOT do updates here
            if (author.getId() == 0) {
                if (ServiceLocator.getInstance().getAuthorDao().insert(context, author) == -1) {
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
                STMT_DELETE_BOOK_AUTHORS, () -> Sql.Delete.BOOK_AUTHOR_BY_BOOK_ID);

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
    public void insertBookSeries(@NonNull final Context context,
                                 @IntRange(from = 1) final long bookId,
                                 @NonNull final Collection<Series> list,
                                 final boolean lookupLocale,
                                 @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // fix id's and remove duplicates
        Series.pruneList(list, context, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch.
        deleteBookSeriesByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_BOOK_SERIES, () -> Sql.Insert.BOOK_SERIES);

        final SeriesDao seriesDao = ServiceLocator.getInstance().getSeriesDao();

        int position = 0;
        for (final Series series : list) {
            // create if needed - do NOT do updates here
            if (series.getId() == 0) {
                if (seriesDao.insert(context, series, bookLocale) == -1) {
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
                STMT_DELETE_BOOK_SERIES, () -> Sql.Delete.BOOK_SERIES_BY_BOOK_ID);

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
    public void insertBookPublishers(@NonNull final Context context,
                                     @IntRange(from = 1) final long bookId,
                                     @NonNull final Collection<Publisher> list,
                                     final boolean lookupLocale,
                                     @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // fix id's and remove duplicates
        Publisher.pruneList(list, context, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch.
        deleteBookPublishersByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        final PublisherDao publisherDao = ServiceLocator.getInstance().getPublisherDao();

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_INSERT_BOOK_PUBLISHER, () -> Sql.Insert.BOOK_PUBLISHER);

        int position = 0;
        for (final Publisher publisher : list) {
            // create if needed - do NOT do updates here
            if (publisher.getId() == 0) {
                if (publisherDao.insert(context, publisher, bookLocale) == -1) {
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
                STMT_DELETE_BOOK_PUBLISHER, () -> Sql.Delete.BOOK_PUBLISHER_BY_BOOK_ID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Create the link between {@link Book} and {@link CalibreLibrary}.
     * New libraries are added, existing ones are NOT updated.
     *
     * @param book A collection with the columns to be set. May contain extra data.
     *
     * @throws DaoWriteException on failure
     */
    private void insertBookCalibreData(@NonNull final Book book)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // Just delete all current links; we'll insert them from scratch.
        deleteBookCalibreData(book);

        final CalibreLibraryDao libraryDao = ServiceLocator.getInstance().getCalibreLibraryDao();
        final CalibreLibrary library;
        if (book.contains(Book.BKEY_CALIBRE_LIBRARY)) {
            library = book.getParcelable(Book.BKEY_CALIBRE_LIBRARY);

            //noinspection ConstantConditions
            libraryDao.fixId(library);
            if (library.getId() == 0) {
                if (libraryDao.insert(library) == -1) {
                    throw new DaoWriteException("insert Calibre library");
                }
            }
        } else if (book.contains(KEY_FK_CALIBRE_LIBRARY)) {
            library = libraryDao.getLibrary(book.getLong(KEY_FK_CALIBRE_LIBRARY));
            if (library == null) {
                // The book did not have a full library object;
                // It did have a library id, but that library does not exist.
                // Theoretically this should never happen... flw
                // log and bail out.
                Logger.warn(TAG, "calibre library invalid for book="
                                 + book.getId());
                return;
            }
        } else {
            // should never be the case... flw
            // log and bail out.
            Logger.warn(TAG, "calibre library invalid for book="
                             + book.getId());
            return;
        }

        final ContentValues cv = new ContentValues();
        cv.put(KEY_FK_BOOK, book.getId());
        cv.put(KEY_CALIBRE_BOOK_ID, book.getInt(KEY_CALIBRE_BOOK_ID));
        cv.put(KEY_CALIBRE_BOOK_UUID, book.getString(KEY_CALIBRE_BOOK_UUID));
        cv.put(KEY_CALIBRE_BOOK_MAIN_FORMAT, book.getString(KEY_CALIBRE_BOOK_MAIN_FORMAT));

        cv.put(KEY_FK_CALIBRE_LIBRARY, library.getId());

        final long rowId = mDb.insert(TBL_CALIBRE_BOOKS.getName(), cv);
        if (rowId == -1) {
            throw new DaoWriteException("insert book-calibre");
        }
    }

    /**
     * Delete all data related to Calibre from the database (but leaves them in the book object).
     *
     * @param book to process
     */
    public void deleteBookCalibreData(@NonNull final Book book) {
        mDb.delete(TBL_CALIBRE_BOOKS.getName(), KEY_FK_BOOK + "=?",
                   new String[]{String.valueOf(book.getId())});
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
    private void saveTocList(@NonNull final Context context,
                             @IntRange(from = 1) final long bookId,
                             @NonNull final Collection<TocEntry> list,
                             final boolean lookupLocale,
                             @NonNull final Locale bookLocale)
            throws DaoWriteException {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        // fix id's and remove duplicates
        TocEntry.pruneList(list, context, lookupLocale, bookLocale);

        // Just delete all current links; we'll insert them from scratch (easier positioning)
        deleteBookTocEntryByBookId(bookId);

        // is there anything to insert ?
        if (list.isEmpty()) {
            return;
        }

        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Insert.BOOK_TOC_ENTRY);
             SynchronizedStatement stmtInsToc = mDb.compileStatement(Sql.Insert.TOC_ENTRY);
             SynchronizedStatement stmtUpdToc = mDb.compileStatement(Sql.Update.TOCENTRY)) {

            long position = 0;
            for (final TocEntry tocEntry : list) {
                // Author must be handled separately; the id will already have been 'fixed'
                // during the TocEntry.pruneList call.
                // Create if needed - do NOT do updates here
                final Author author = tocEntry.getPrimaryAuthor();
                if (author.getId() == 0) {
                    if (ServiceLocator.getInstance().getAuthorDao().insert(context, author) == -1) {
                        throw new DaoWriteException("insert Author");
                    }
                }

                final Locale tocLocale = tocEntry.getLocale(context, bookLocale);
                final String obTitle = tocEntry.reorderTitleForSorting(context, tocLocale);

                if (tocEntry.getId() == 0) {
                    stmtInsToc.bindLong(1, tocEntry.getPrimaryAuthor().getId());
                    stmtInsToc.bindString(2, tocEntry.getTitle());
                    stmtInsToc.bindString(3, BaseDaoImpl.encodeOrderByColumn(obTitle, tocLocale));
                    stmtInsToc.bindString(4, tocEntry.getFirstPublicationDate()
                                                     .getIsoString());
                    final long iId = stmtInsToc.executeInsert();
                    if (iId > 0) {
                        tocEntry.setId(iId);
                    } else {
                        throw new DaoWriteException("insert TocEntry");
                    }

                } else {
                    // We cannot update the author as it's part of the primary key.
                    // (we should never even get here if the author was changed)
                    //noinspection SynchronizationOnLocalVariableOrMethodParameter
                    synchronized (stmtUpdToc) {
                        stmtUpdToc.bindString(1, tocEntry.getTitle());
                        stmtUpdToc.bindString(2, BaseDaoImpl
                                .encodeOrderByColumn(obTitle, tocLocale));
                        stmtUpdToc.bindString(3, tocEntry.getFirstPublicationDate()
                                                         .getIsoString());
                        stmtUpdToc.bindLong(4, tocEntry.getId());
                        if (stmtUpdToc.executeUpdateDelete() != 1) {
                            throw new DaoWriteException("update TocEntry");
                        }
                    }
                }

                // create the book<->TocEntry link.
                //
                // As we delete all links before insert/update'ng above, we normally
                // *always* need to re-create the link here.
                // However, this will fail if we inserted "The Universe" and updated "Universe, The"
                // as the former will be stored as "Universe, The" so conflicting with the latter.
                // We tried to mitigate this conflict before it could trigger an issue here, but it
                // complicated the code and frankly ended in a chain of special condition
                // code branches during processing of internet search data.
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
    }

    /**
     * Delete the link between TocEntry's and the given Book.
     * Note that the actual TocEntry's are NOT deleted here.
     *
     * @param bookId id of the book
     */
    private void deleteBookTocEntryByBookId(@IntRange(from = 1) final long bookId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_DELETE_BOOK_TOC_ENTRIES, () -> Sql.Delete.BOOK_TOC_ENTRIES_BY_BOOK_ID);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (stmt) {
            stmt.bindLong(1, bookId);
            stmt.executeUpdateDelete();
        }
    }


    /**
     * Count all books.
     *
     * @return number of books
     */
    public long countBooks() {
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Count.BOOKS)) {
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

        final String sql = Sql.Select.SQL_BOOK
                           + (whereClause != null && whereClause.length() > 0
                              ? _WHERE_ + whereClause : "")
                           + (orderByClause != null && orderByClause.length() > 0
                              ? _ORDER_BY_ + orderByClause : "")

                           + _COLLATION;

        final TypedCursor cursor = mDb.rawQueryWithTypedCursor(sql, selectionArgs, null);
        // force the TypedCursor to retrieve the real column types.
        cursor.setDb(mDb, TBL_BOOKS);
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
                                       final long externalId) {
        return getBookCursor(TBL_BOOKS.dot(key) + "=?",
                             new String[]{String.valueOf(externalId)},
                             TBL_BOOKS.dot(KEY_PK_ID));
    }


    /**
     * Return an Cursor with all Books for the given list of {@link Book} ID's.
     *
     * @param idList List of book ID's to retrieve; should not be empty!
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     *
     * @throws SanityCheck.MissingValueException if the list is empty
     */
    @NonNull
    public TypedCursor fetchBooks(@NonNull final List<Long> idList) {
        SanityCheck.requireValue(idList, "idList");

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

    public int countBooksForExport(@Nullable final LocalDateTime since) {
        if (since == null) {
            try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Count.BOOKS)) {
                return (int) stmt.simpleQueryForLongOrZero();
            }
        } else {
            try (SynchronizedStatement stmt = mDb.compileStatement(
                    Sql.Count.BOOKS + _WHERE_ + KEY_UTC_LAST_UPDATED + ">=?")) {
                stmt.bindString(1, encodeDate(since));
                return (int) stmt.simpleQueryForLongOrZero();
            }
        }
    }

    /**
     * Return an Cursor with all Books, or with all updated Books since the given date/time.
     *
     * @param since to select all books added/modified since that date/time (UTC based).
     *              Set to {@code null} for *all* books.
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    public TypedCursor fetchBooksForExport(@Nullable final LocalDateTime since) {
        if (since == null) {
            return getBookCursor(null, null, TBL_BOOKS.dot(KEY_PK_ID));
        } else {
            return getBookCursor(TBL_BOOKS.dot(KEY_UTC_LAST_UPDATED) + ">=?",
                                 new String[]{encodeDate(since)},
                                 TBL_BOOKS.dot(KEY_PK_ID));
        }
    }

    @NonNull
    public TypedCursor fetchBooksForExportToCalibre(final long libraryId,
                                                    @Nullable final LocalDateTime since) {
        if (since == null) {
            return getBookCursor(TBL_CALIBRE_BOOKS.dot(KEY_FK_CALIBRE_LIBRARY) + "=?",
                                 new String[]{String.valueOf(libraryId)},
                                 TBL_BOOKS.dot(KEY_PK_ID));
        } else {
            return getBookCursor(TBL_CALIBRE_BOOKS.dot(KEY_FK_CALIBRE_LIBRARY) + "=?"
                                 + _AND_ + TBL_BOOKS.dot(KEY_UTC_LAST_UPDATED) + ">=?",
                                 new String[]{String.valueOf(libraryId), encodeDate(since)},
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
     * @throws SanityCheck.MissingValueException if the list is empty
     */
    @NonNull
    public TypedCursor fetchBooksByIsbnList(@NonNull final List<String> isbnList) {
        SanityCheck.requireValue(isbnList, "isbnList");

        if (isbnList.size() == 1) {
            // optimize for single book
            return getBookCursor(TBL_BOOKS.dot(KEY_ISBN) + "=?",
                                 new String[]{encodeString(isbnList.get(0))},
                                 null);
        } else {
            return getBookCursor(TBL_BOOKS.dot(KEY_ISBN)
                                 + " IN ("
                                 + isbnList.stream()
                                           .map(s -> '\'' + encodeString(s) + '\'')
                                           .collect(Collectors.joining(","))
                                 + ')',
                                 null,
                                 TBL_BOOKS.dot(KEY_PK_ID));
        }
    }

    /**
     * Fetch all book UUID, and return them as a List.
     *
     * @return a list of all book UUID in the database.
     */
    @NonNull
    public ArrayList<String> getBookUuidList() {
        return getColumnAsStringArrayList(Sql.Select.ALL_BOOK_UUID);
    }

    /**
     * Get a unique list of all currencies for the specified domain (from the Books table).
     *
     * @param domainName for which to collect the used currency codes
     *
     * @return The list; values are always in uppercase.
     */
    @NonNull
    public ArrayList<String> getCurrencyCodes(@NonNull final String domainName) {
        final String column;
        if (KEY_PRICE_LISTED_CURRENCY.equals(domainName)) {
            column = KEY_PRICE_LISTED_CURRENCY;
//        } else if (KEY_PRICE_PAID_CURRENCY.equals(type)) {
        } else {
            column = KEY_PRICE_PAID_CURRENCY;
        }

        final String sql = "SELECT DISTINCT upper(" + column + ") FROM " + TBL_BOOKS.getName()
                           + _ORDER_BY_ + column + _COLLATION;

        final ArrayList<String> list = getColumnAsStringArrayList(sql);
        if (list.isEmpty()) {
            // sure, this is very crude and discriminating.
            // But it will only ever be used *once* per currency column
            list.add(Money.EUR);
            list.add(Money.GBP);
            list.add(Money.USD);
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
    @NonNull
    public ArrayList<Bookshelf> getBookshelvesByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(Sql.Select.BOOKSHELVES_BY_BOOK_ID,
                                          new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(KEY_PK_ID), rowData));
            }
            return list;
        }
    }

    /**
     * Get a list of book id/title's (most often just the one) for the given ISBN.
     *
     * @param isbn to search for; can be generic/non-valid
     *
     * @return list with book id/title
     */
    @NonNull
    public ArrayList<Pair<Long, String>> getBookIdAndTitlesByIsbn(@NonNull final ISBN isbn) {
        final ArrayList<Pair<Long, String>> list = new ArrayList<>();
        // if the string is ISBN-10 compatible, i.e. an actual ISBN-10,
        // or an ISBN-13 in the 978 range, we search on both formats
        if (isbn.isIsbn10Compat()) {
            try (Cursor cursor = mDb.rawQuery(Sql.Select.BY_VALID_ISBN,
                                              new String[]{isbn.asText(
                                                      ISBN.TYPE_ISBN10), isbn.asText(
                                                      ISBN.TYPE_ISBN13)})) {
                while (cursor.moveToNext()) {
                    list.add(new Pair<>(cursor.getLong(0),
                                        cursor.getString(1)));
                }
            }
        } else {
            // otherwise just search on the string as-is; regardless of validity
            // (this would actually include valid ISBN-13 in the 979 range).
            try (Cursor cursor = mDb.rawQuery(Sql.Select.BY_ISBN,
                                              new String[]{isbn.asText()})) {
                while (cursor.moveToNext()) {
                    list.add(new Pair<>(cursor.getLong(0),
                                        cursor.getString(1)));
                }
            }
        }

        return list;
    }

    /**
     * Check that a book with the passed UUID exists and return the id of the book, or zero.
     *
     * @param uuid UUID of the book
     *
     * @return id of the book, or 0 'new' if not found
     */
    @IntRange(from = 0)
    public long getBookIdFromUuid(@NonNull final String uuid) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_BOOK_ID_FROM_UUID, () -> Sql.Get.BOOK_ID_BY_UUID);

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
    private String getBookUuid(@IntRange(from = 1) final long bookId) {
        SanityCheck.requirePositiveValue(bookId, "bookId");

        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_BOOK_UUID, () -> Sql.Get.BOOK_UUID_BY_ID);

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
    public String getBookTitle(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = mDb.compileStatement(
                Sql.Get.BOOK_TITLE_BY_BOOK_ID)) {
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
    public String getBookIsbn(@IntRange(from = 1) final long bookId) {
        try (SynchronizedStatement stmt = mDb.compileStatement(
                Sql.Get.BOOK_ISBN_BY_BOOK_ID)) {
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
    public ArrayList<Author> getAuthorsByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<Author> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(Sql.Select.AUTHORS_BY_BOOK_ID,
                                          new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Author(rowData.getLong(KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    /**
     * Check for books which do not have an {@link Author} at position 1.
     * For those that don't, read their list, and re-save them.
     * <p>
     * Note that in normal usage from {@link AuthorDao} we could first get the book id's
     * that needs updating, and then update only those.
     * However, that means two statements to execute and overall a longer execution time
     * as compared to the single stmt approach here.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     *
     * @return the number of books processed
     */
    public int repositionAuthor(@NonNull final Context context) {
        final String sql =
                "SELECT " + KEY_FK_BOOK + " FROM "
                + "(SELECT " + KEY_FK_BOOK + ", MIN(" + KEY_BOOK_AUTHOR_POSITION + ") AS mp"
                + " FROM " + TBL_BOOK_AUTHOR.getName() + " GROUP BY " + KEY_FK_BOOK
                + ") WHERE mp > 1";

        final ArrayList<Long> bookIds = getColumnAsLongArrayList(sql);
        if (!bookIds.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "repositionAuthor|" + TBL_BOOK_AUTHOR.getName()
                           + ", rows=" + bookIds.size());
            }
            // ENHANCE: we really should fetch each book individually
            final Locale bookLocale = AppLocale.getInstance().getUserLocale(context);

            Synchronizer.SyncLock txLock = null;
            try {
                if (!mDb.inTransaction()) {
                    txLock = mDb.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final ArrayList<Author> list = getAuthorsByBookId(bookId);
                    insertBookAuthors(context, bookId, list, false, bookLocale);
                }
                if (txLock != null) {
                    mDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DaoWriteException e) {
                Logger.error(TAG, e);

            } finally {
                if (txLock != null) {
                    mDb.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "repositionAuthor|done");
                }
            }
        }
        return bookIds.size();
    }

    /**
     * Get a list of the Series a book belongs to.
     *
     * @param bookId of the book
     *
     * @return list of Series
     */
    @NonNull
    public ArrayList<Series> getSeriesByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<Series> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(Sql.Select.SERIES_BY_BOOK_ID,
                                          new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Series(rowData.getLong(KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    /**
     * Check for books which do not have a {@link Series} at position 1.
     * For those that don't, read their list, and re-save them.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     *
     * @return the number of books processed
     */
    public int repositionSeries(@NonNull final Context context) {
        final String sql =
                "SELECT " + KEY_FK_BOOK + " FROM "
                + "(SELECT " + KEY_FK_BOOK + ", MIN(" + KEY_BOOK_SERIES_POSITION + ") AS mp"
                + " FROM " + TBL_BOOK_SERIES.getName() + " GROUP BY " + KEY_FK_BOOK
                + ") WHERE mp > 1";

        final ArrayList<Long> bookIds = getColumnAsLongArrayList(sql);
        if (!bookIds.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "repositionSeries|" + TBL_BOOK_SERIES.getName()
                           + ", rows=" + bookIds.size());
            }
            // ENHANCE: we really should fetch each book individually
            final Locale bookLocale = AppLocale.getInstance().getUserLocale(context);

            Synchronizer.SyncLock txLock = null;
            try {
                if (!mDb.inTransaction()) {
                    txLock = mDb.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final ArrayList<Series> list = getSeriesByBookId(bookId);
                    insertBookSeries(context, bookId, list, false, bookLocale);
                }
                if (txLock != null) {
                    mDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DaoWriteException e) {
                Logger.error(TAG, e);
            } finally {
                if (txLock != null) {
                    mDb.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "repositionSeries|done");
                }
            }
        }
        return bookIds.size();
    }

    /**
     * Get a list of the Publisher for a book.
     *
     * @param bookId of the book
     *
     * @return list of Publisher
     */
    @NonNull
    public ArrayList<Publisher> getPublishersByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<Publisher> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(Sql.Select.PUBLISHER_BY_BOOK_ID,
                                          new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Publisher(rowData.getLong(KEY_PK_ID), rowData));
            }
        }
        return list;
    }

    /**
     * Check for books which do not have an {@link Publisher} at position 1.
     * For those that don't, read their list, and re-save them.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     *
     * @return the number of books processed
     */
    public int repositionPublishers(@NonNull final Context context) {
        final String sql = "SELECT " + KEY_FK_BOOK + " FROM "
                           + "(SELECT " + KEY_FK_BOOK + ", MIN(" + KEY_BOOK_PUBLISHER_POSITION
                           + ") AS mp"
                           + " FROM " + TBL_BOOK_PUBLISHER.getName() + " GROUP BY " + KEY_FK_BOOK
                           + ") WHERE mp > 1";

        final ArrayList<Long> bookIds = getColumnAsLongArrayList(sql);
        if (!bookIds.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "repositionPublishers|" + TBL_BOOK_PUBLISHER.getName()
                           + ", rows=" + bookIds.size());
            }
            // ENHANCE: we really should fetch each book individually
            final Locale bookLocale = AppLocale.getInstance().getUserLocale(context);

            Synchronizer.SyncLock txLock = null;
            try {
                if (!mDb.inTransaction()) {
                    txLock = mDb.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final ArrayList<Publisher> list = getPublishersByBookId(bookId);
                    insertBookPublishers(context, bookId, list, false, bookLocale);
                }
                if (txLock != null) {
                    mDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DaoWriteException e) {
                Logger.error(TAG, e);

            } finally {
                if (txLock != null) {
                    mDb.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "repositionPublishers|done");
                }
            }
        }
        return bookIds.size();
    }

    /**
     * Get the list of TocEntry for this book.
     *
     * @param bookId of the book
     *
     * @return list
     */
    @NonNull
    public ArrayList<TocEntry> getTocEntryByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<TocEntry> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(Sql.Select.TOC_ENTRIES_BY_BOOK_ID,
                                          new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new TocEntry(rowData.getLong(KEY_PK_ID),
                                      new Author(rowData.getLong(KEY_FK_AUTHOR), rowData),
                                      rowData.getString(KEY_TITLE),
                                      rowData.getString(KEY_DATE_FIRST_PUBLICATION),
                                      rowData.getInt(KEY_BOOK_COUNT)));
            }
        }
        return list;
    }

    /**
     * Check for books which do not have a {@link TocEntry} at position 1.
     * For those that don't, read their list, and re-save them.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     *
     * @return the number of books processed
     */
    public int repositionTocEntries(@NonNull final Context context) {
        final String sql =
                "SELECT " + KEY_FK_BOOK + " FROM "
                + "(SELECT " + KEY_FK_BOOK + ", MIN(" + KEY_BOOK_TOC_ENTRY_POSITION + ") AS mp"
                + " FROM " + TBL_BOOK_TOC_ENTRIES.getName() + " GROUP BY " + KEY_FK_BOOK
                + ") WHERE mp > 1";

        final ArrayList<Long> bookIds = getColumnAsLongArrayList(sql);
        if (!bookIds.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "repositionTocEntries|" + TBL_BOOK_TOC_ENTRIES.getName()
                           + ", rows=" + bookIds.size());
            }
            // ENHANCE: we really should fetch each book individually
            final Locale bookLocale = AppLocale.getInstance().getUserLocale(context);

            Synchronizer.SyncLock txLock = null;
            try {
                if (!mDb.inTransaction()) {
                    txLock = mDb.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final ArrayList<TocEntry> list = getTocEntryByBookId(bookId);
                    saveTocList(context, bookId, list, false, bookLocale);
                }
                if (txLock != null) {
                    mDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DaoWriteException e) {
                Logger.error(TAG, e);

            } finally {
                if (txLock != null) {
                    mDb.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "repositionTocEntries|done");
                }
            }
        }
        return bookIds.size();
    }

    /**
     * Return all the {@link AuthorWork} for the given {@link Author}.
     *
     * @param author         to retrieve
     * @param bookshelfId    limit the list to books on this shelf (pass -1 for all shelves)
     * @param withTocEntries add the toc entries
     * @param withBooks      add books without TOC as well; i.e. the toc of a book without a toc,
     *                       is the book title itself. (makes sense?)
     *
     * @return List of {@link AuthorWork} for this {@link Author}
     */
    @NonNull
    public ArrayList<AuthorWork> getAuthorWorks(@NonNull final Author author,
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
            sql += Sql.AuthorWorks.TOC_ENTRIES_BY_AUTHOR_ID
                   + (byShelf ? " JOIN " + TBL_BOOK_BOOKSHELF.ref()
                                + " ON (" + TBL_BOOK_TOC_ENTRIES.dot(KEY_FK_BOOK)
                                + '=' + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOK) + ")"
                              : "")
                   + _WHERE_ + TBL_TOC_ENTRIES.dot(KEY_FK_AUTHOR) + "=?"
                   + (byShelf ? _AND_ + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOKSHELF) + "=?" : "")
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
            sql += Sql.AuthorWorks.BOOK_TITLES_BY_AUTHOR_ID
                   + (byShelf ? TBL_BOOKS.join(TBL_BOOK_BOOKSHELF) : "")
                   + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_AUTHOR) + "=?"
                   + (byShelf ? _AND_ + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOKSHELF) + "=?" : "");
            paramList.add(authorIdStr);
            if (byShelf) {
                paramList.add(bookshelfIdStr);
            }
        }

        sql += _ORDER_BY_ + KEY_TITLE_OB + _COLLATION;

        final ArrayList<AuthorWork> list = new ArrayList<>();
        //noinspection ZeroLengthArrayAllocation
        try (Cursor cursor = mDb.rawQuery(sql, paramList.toArray(new String[0]))) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final char type = rowData.getString(KEY_TOC_TYPE).charAt(0);
                switch (type) {
                    case AuthorWork.TYPE_TOC:
                        list.add(new TocEntry(rowData.getLong(KEY_PK_ID),
                                              author,
                                              rowData.getString(KEY_TITLE),
                                              rowData.getString(KEY_DATE_FIRST_PUBLICATION),
                                              rowData.getInt(KEY_BOOK_COUNT)));
                        break;

                    case AuthorWork.TYPE_BOOK:
                        // Eventually we'll create a Book here...
                        list.add(new BookAsWork(rowData.getLong(KEY_PK_ID),
                                                author,
                                                rowData.getString(KEY_TITLE),
                                                rowData.getString(KEY_DATE_FIRST_PUBLICATION)));
                        break;

                    default:
                        throw new IllegalArgumentException(String.valueOf(type));
                }
            }
        }
        return list;
    }

    /***
     * Return the book last update date based on the id.
     *
     * @param context Current context
     * @param bookId of the book
     *
     * @return the last update date; UTC based.
     */
    @Nullable
    public LocalDateTime getBookLastUpdateUtcDate(@NonNull final Context context,
                                                  @IntRange(from = 1) final long bookId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_GET_BOOK_UPDATE_DATE, () -> Sql.Get.LAST_UPDATE_DATE_BY_BOOK_ID);

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
    public boolean bookExistsById(@IntRange(from = 1) final long bookId) {
        final SynchronizedStatement stmt = mSqlStatementManager.get(
                STMT_CHECK_BOOK_EXISTS, () -> Sql.Count.BOOK_EXISTS);

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
        return !getBookIdAndTitlesByIsbn(isbn).isEmpty();
    }

    /**
     * Update the 'read' status and the 'read_end' date of the book.
     * This method should only be called from places where only the book id is available.
     * If the full Book is available, use {@link #setBookRead(Book, boolean)} instead.
     *
     * @param bookId id of the book to update
     * @param isRead the status to set
     *
     * @return {@code true} for success.
     */
    public boolean setBookRead(@IntRange(from = 1) final long bookId,
                               final boolean isRead) {
        final String now = isRead ? encodeDate(LocalDateTime.now()) : "";

        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Update.READ)) {
            stmt.bindBoolean(1, isRead);
            stmt.bindString(2, now);
            stmt.bindLong(3, bookId);
            return 0 < stmt.executeUpdateDelete();
        }
    }

    /**
     * Update the 'read' status and the 'read_end' date of the book.
     * The book will be updated.
     *
     * @param book   to update
     * @param isRead the status to set
     *
     * @return {@code true} for success.
     */
    public boolean setBookRead(@NonNull final Book book,
                               final boolean isRead) {
        final String now = isRead ? encodeDate(LocalDateTime.now()) : "";

        final boolean success;
        // don't call standalone method, we want to use the same 'now' to update the book
        try (SynchronizedStatement stmt = mDb.compileStatement(Sql.Update.READ)) {
            stmt.bindBoolean(1, isRead);
            stmt.bindString(2, now);
            stmt.bindLong(3, book.getId());
            success = 0 < stmt.executeUpdateDelete();
        }

        if (success) {
            book.putBoolean(KEY_READ, isRead);
            book.putString(KEY_READ_END, now);
            book.putString(KEY_UTC_LAST_UPDATED, now);
        }

        return success;
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
    public Cursor search(@Nullable final String author,
                         @Nullable final String title,
                         @Nullable final String seriesTitle,
                         @Nullable final String publisherName,
                         @Nullable final String keywords,
                         final int limit) {

        final String query = Sql.Fts.createMatchString(author, title, seriesTitle,
                                                       publisherName, keywords);
        // do we have anything to search for?
        if (query.isEmpty()) {
            return null;
        }
        return mDb.rawQuery(Sql.Fts.SEARCH, new String[]{query, String.valueOf(limit)});
    }

    /**
     * Insert a book into the FTS. Assumes book does not already exist in FTS.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param bookId the book to add to FTS
     *
     * @throws TransactionException a transaction must be started before calling this method
     */
    private void ftsInsert(@IntRange(from = 1) final long bookId) {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        try {
            final SynchronizedStatement stmt = mSqlStatementManager.get(
                    STMT_INSERT_FTS, () -> Sql.Fts.INSERT);

            try (Cursor cursor = mDb.rawQuery(Sql.Fts.BOOK_BY_ID,
                                              new String[]{String.valueOf(bookId)})) {
                processBooks(cursor, stmt);
            }
        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(TAG, e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    /**
     * Update an existing FTS record.
     * <p>
     * <strong>Transaction:</strong> required
     *
     * @param bookId the book id
     *
     * @throws TransactionException a transaction must be started before calling this method
     */
    private void ftsUpdate(@IntRange(from = 1) final long bookId) {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
        }

        try {
            final SynchronizedStatement stmt = mSqlStatementManager.get(
                    STMT_UPDATE_FTS, () -> Sql.Fts.UPDATE);

            try (Cursor cursor = mDb.rawQuery(Sql.Fts.BOOK_BY_ID,
                                              new String[]{String.valueOf(bookId)})) {
                processBooks(cursor, stmt);
            }
        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(TAG, e, ERROR_FAILED_TO_UPDATE_FTS);
        }
    }

    /**
     * Process the book details from the cursor using the passed fts query.
     * <p>
     * <strong>Note:</strong> This assumes a specific order for query parameters.
     * If modified, then update {@link Sql.Fts#INSERT_BODY},
     * {@link Sql.Fts#UPDATE}
     *
     * <strong>Transaction:</strong> required
     *
     * @param cursor Cursor of books to update
     * @param stmt   Statement to execute (insert or update)
     *
     * @throws TransactionException a transaction must be started before calling this method
     */
    private void processBooks(@NonNull final Cursor cursor,
                              @NonNull final SynchronizedStatement stmt) {

        if (BuildConfig.DEBUG /* always */) {
            if (!mDb.inTransaction()) {
                throw new TransactionException(TransactionException.REQUIRED);
            }
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
            try (Cursor authors = mDb.rawQuery(Sql.Fts.GET_AUTHORS_BY_BOOK_ID, qpBookId)) {
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
            try (Cursor series = mDb.rawQuery(Sql.Fts.GET_SERIES_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colSeriesTitle < 0) {
                    colSeriesTitle = series.getColumnIndexOrThrow(KEY_SERIES_TITLE);
                }

                while (series.moveToNext()) {
                    seriesText.append(series.getString(colSeriesTitle)).append(';');
                }
            }

            // Get list of publishers
            try (Cursor publishers = mDb
                    .rawQuery(Sql.Fts.GET_PUBLISHERS_BY_BOOK_ID, qpBookId)) {
                // Get column indexes, if not already got
                if (colPublisherName < 0) {
                    colPublisherName = publishers.getColumnIndexOrThrow(KEY_PUBLISHER_NAME);
                }

                while (publishers.moveToNext()) {
                    publisherText.append(publishers.getString(colPublisherName)).append(';');
                }
            }

            // Get list of TOC titles
            try (Cursor toc = mDb.rawQuery(Sql.Fts.GET_TOC_TITLES_BY_BOOK_ID, qpBookId)) {
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
     * Rebuild the entire FTS database.
     * This can take several seconds with many books or a slow device.
     */
    public void ftsRebuild() {

        long t0 = 0;
        if (BuildConfig.DEBUG /* always */) {
            t0 = System.nanoTime();
        }
        boolean gotError = false;

        final String tmpTableName = "books_fts_rebuilding";
        final TableDefinition ftsTemp = DBDefinitions.createFtsTableDefinition(tmpTableName);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            //IMPORTANT: withDomainConstraints MUST BE false
            mDb.recreate(ftsTemp, false);

            try (SynchronizedStatement stmt = mDb.compileStatement(
                    INSERT_INTO_ + tmpTableName + Sql.Fts.INSERT_BODY);
                 Cursor cursor = mDb.rawQuery(Sql.Fts.ALL_BOOKS, null)) {
                processBooks(cursor, stmt);
            }

            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } catch (@NonNull final RuntimeException e) {
            // updating FTS should not be fatal.
            Logger.error(TAG, e);
            gotError = true;
            mDb.drop(tmpTableName);

        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }

            /*
            http://sqlite.1065341.n5.nabble.com/Bug-in-FTS3-when-trying-to-rename-table-within-a-transaction-td11430.html
            FTS tables should only be renamed outside of transactions.
            */
            //  Delete old table and rename the new table
            if (!gotError) {
                // Drop old table, ready for rename
                mDb.drop(TBL_FTS_BOOKS.getName());
                mDb.execSQL("ALTER TABLE " + tmpTableName
                            + " RENAME TO " + TBL_FTS_BOOKS.getName());
            }
        }

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "|rebuildFts|completed in "
                       + (System.nanoTime() - t0) / NANO_TO_MILLIS + " ms");
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
            Logger.d(TAG, "close", mInstanceName);
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

    @IntDef(flag = true, value = {BOOK_FLAG_IS_BATCH_OPERATION,
                                  BOOK_FLAG_USE_ID_IF_PRESENT,
                                  BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT})
    @Retention(RetentionPolicy.SOURCE)
    @interface BookFlags {

    }

    @SuppressWarnings("UtilityClassWithoutPrivateConstructor")
    public static class Sql {

        /**
         * Count/exist statements.
         */
        public static final class Count {

            /** Count all {@link Book}'s. */
            static final String BOOKS =
                    SELECT_COUNT_FROM_ + TBL_BOOKS.getName();

            /** Check if a {@link Book} exists. */
            static final String BOOK_EXISTS =
                    SELECT_COUNT_FROM_ + TBL_BOOKS.getName() + _WHERE_ + KEY_PK_ID + "=?";
        }

        /**
         * Sql SELECT to lookup a single item.
         */
        public static final class Get {

            /** Get the UUID of a {@link Book} by the Book id. */
            static final String BOOK_UUID_BY_ID =
                    SELECT_ + KEY_BOOK_UUID + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + KEY_PK_ID + "=?";

            /** Get the ISBN of a {@link Book} by the Book id. */
            static final String BOOK_ISBN_BY_BOOK_ID =
                    SELECT_ + KEY_ISBN + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + KEY_PK_ID + "=?";

            /** Get the title of a {@link Book} by the Book id. */
            static final String BOOK_TITLE_BY_BOOK_ID =
                    SELECT_ + KEY_TITLE + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + KEY_PK_ID + "=?";

            /** Get the last-update-date for a {@link Book} by its id. */
            static final String LAST_UPDATE_DATE_BY_BOOK_ID =
                    SELECT_ + KEY_UTC_LAST_UPDATED + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + KEY_PK_ID + "=?";

            /** Get the id of a {@link Book} by UUID. */
            static final String BOOK_ID_BY_UUID =
                    SELECT_ + KEY_PK_ID + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + KEY_BOOK_UUID + "=?";
        }

        /**
         * Sql SELECT returning a list, with a WHERE clause.
         */
        public static final class Select {

            /** Find the {@link Book} id+title based on a search for the ISBN (both 10 & 13). */
            static final String BY_VALID_ISBN =
                    SELECT_ + KEY_PK_ID + ',' + KEY_TITLE + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + KEY_ISBN + " LIKE ? OR " + KEY_ISBN + " LIKE ?";

            /**
             * Find the {@link Book} id+title based on a search for the ISBN.
             * The isbn need not be valid and can in fact be any code whatsoever.
             */
            static final String BY_ISBN =
                    SELECT_ + KEY_PK_ID + ',' + KEY_TITLE + _FROM_ + TBL_BOOKS.getName()
                    + _WHERE_ + KEY_ISBN + " LIKE ?";

            /** All Bookshelves for a Book; ordered by name. */
            static final String BOOKSHELVES_BY_BOOK_ID =
                    SELECT_DISTINCT_ + TBL_BOOKSHELF.dotAs(KEY_PK_ID)
                    + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_NAME)
                    + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_POS)
                    + ',' + TBL_BOOKSHELF.dotAs(KEY_BOOKSHELF_BL_TOP_OFFSET)
                    + ',' + TBL_BOOKSHELF.dotAs(KEY_FK_STYLE)
                    + ',' + TBL_BOOKLIST_STYLES.dotAs(KEY_STYLE_UUID)

                    + _FROM_ + TBL_BOOK_BOOKSHELF.ref()
                    + TBL_BOOK_BOOKSHELF.join(TBL_BOOKSHELF)
                    + TBL_BOOKSHELF.join(TBL_BOOKLIST_STYLES)
                    + _WHERE_ + TBL_BOOK_BOOKSHELF.dot(KEY_FK_BOOK) + "=?"
                    + _ORDER_BY_ + TBL_BOOKSHELF.dot(KEY_BOOKSHELF_NAME) + _COLLATION;

            /** All Authors for a Book; ordered by position, family, given. */
            static final String AUTHORS_BY_BOOK_ID =
                    SELECT_DISTINCT_ + TBL_AUTHORS.dotAs(KEY_PK_ID)
                    + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME)
                    + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME_OB)
                    + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES)
                    + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES_OB)
                    + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_IS_COMPLETE)
                    + ',' + AuthorDaoImpl.getDisplayAuthor(false) + _AS_ + KEY_AUTHOR_FORMATTED
                    + ',' + TBL_BOOK_AUTHOR.dotAs(KEY_BOOK_AUTHOR_POSITION)
                    + ',' + TBL_BOOK_AUTHOR.dotAs(KEY_BOOK_AUTHOR_TYPE_BITMASK)

                    + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                    + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_BOOK) + "=?"
                    + _ORDER_BY_
                    + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION)
                    + ',' + KEY_AUTHOR_FAMILY_NAME_OB + _COLLATION
                    + ',' + KEY_AUTHOR_GIVEN_NAMES_OB + _COLLATION;

            /** All Series for a Book; ordered by position, name. */
            static final String SERIES_BY_BOOK_ID =
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

            /** All Publishers for a Book; ordered by position, name. */
            static final String PUBLISHER_BY_BOOK_ID =
                    SELECT_DISTINCT_ + TBL_PUBLISHERS.dotAs(KEY_PK_ID)
                    + ',' + TBL_PUBLISHERS.dotAs(KEY_PUBLISHER_NAME)
                    + ',' + TBL_PUBLISHERS.dotAs(KEY_PUBLISHER_NAME_OB)
                    + ',' + TBL_BOOK_PUBLISHER.dotAs(KEY_BOOK_PUBLISHER_POSITION)

                    + _FROM_ + TBL_BOOK_PUBLISHER.ref() + TBL_BOOK_PUBLISHER.join(TBL_PUBLISHERS)
                    + _WHERE_ + TBL_BOOK_PUBLISHER.dot(KEY_FK_BOOK) + "=?"
                    + _ORDER_BY_ + TBL_BOOK_PUBLISHER.dot(KEY_BOOK_PUBLISHER_POSITION)
                    + ',' + TBL_PUBLISHERS.dot(KEY_PUBLISHER_NAME_OB) + _COLLATION;

            /** All TocEntry's for a Book; ordered by position in the book. */
            static final String TOC_ENTRIES_BY_BOOK_ID =
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

            /** Book UUID only, for accessing all cover image files. */
            static final String ALL_BOOK_UUID =
                    SELECT_ + KEY_BOOK_UUID + _FROM_ + TBL_BOOKS.getName();

            /** The SELECT and FROM clause for getting a book (list). */
            static final String SQL_BOOK;

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
                        + ',' + TBL_BOOKS.dotAs(KEY_BOOK_DATE_PUBLISHED)
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

                for (final Domain domain : SearchEngineRegistry
                        .getInstance().getExternalIdDomains()) {
                    sqlBookTmp.append(',').append(TBL_BOOKS.dotAs(domain.getName()));
                }

                //NEWTHINGS: adding a new search engine: optional: add engine specific keys
                sqlBookTmp.append(',').append(TBL_BOOKS.dotAs(KEY_UTC_GOODREADS_LAST_SYNC_DATE));

                // LEFT OUTER JOIN, columns default to NULL
                sqlBookTmp.append(',').append(TBL_CALIBRE_BOOKS.dotAs(KEY_CALIBRE_BOOK_ID))
                          .append(',').append(TBL_CALIBRE_BOOKS.dotAs(KEY_CALIBRE_BOOK_UUID))
                          .append(',').append(TBL_CALIBRE_BOOKS.dotAs(KEY_CALIBRE_BOOK_MAIN_FORMAT))
                          .append(',').append(TBL_CALIBRE_BOOKS.dotAs(KEY_FK_CALIBRE_LIBRARY));

                // COALESCE nulls to "" for the LEFT OUTER JOIN'ed LOANEE name
                sqlBookTmp.append(",COALESCE(")
                          .append(TBL_BOOK_LOANEE.dot(KEY_LOANEE))
                          .append(", '')")
                          .append(_AS_).append(KEY_LOANEE);

                sqlBookTmp.append(_FROM_).append(TBL_BOOKS.ref())
                          .append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_LOANEE))
                          .append(TBL_BOOKS.leftOuterJoin(TBL_CALIBRE_BOOKS));

                SQL_BOOK = sqlBookTmp.toString();
            }
        }

        static final class AuthorWorks {

            /**
             * All TocEntry's for an Author.
             * <p>
             * ORDER BY clause NOT added here, as this statement is used in a union as well.
             * <p>
             * We need KEY_TITLE_OB as it will be used to ORDER BY
             */
            static final String TOC_ENTRIES_BY_AUTHOR_ID =
                    SELECT_ + "'" + AuthorWork.TYPE_TOC + "' AS " + KEY_TOC_TYPE
                    + ',' + TBL_TOC_ENTRIES.dotAs(KEY_PK_ID)
                    + ',' + TBL_TOC_ENTRIES.dotAs(KEY_TITLE)
                    + ',' + TBL_TOC_ENTRIES.dotAs(KEY_TITLE_OB)
                    + ',' + TBL_TOC_ENTRIES.dotAs(KEY_DATE_FIRST_PUBLICATION)
                    // count the number of books this TOC entry is present in.
                    + ", COUNT(" + TBL_TOC_ENTRIES.dot(KEY_PK_ID) + ") AS " + KEY_BOOK_COUNT
                    // join with the books, so we can group by toc id, and get the number of books.
                    + _FROM_ + TBL_TOC_ENTRIES.ref() + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES);

            /**
             * All Book titles and their first pub. date, for an Author..
             * <p>
             * ORDER BY clause NOT added here, as this statement is used in a union as well.
             * <p>
             * We need KEY_TITLE_OB as it will be used to ORDER BY
             */
            static final String BOOK_TITLES_BY_AUTHOR_ID =
                    SELECT_ + "'" + AuthorWork.TYPE_BOOK + "' AS " + KEY_TOC_TYPE
                    + ',' + TBL_BOOKS.dotAs(KEY_PK_ID)
                    + ',' + TBL_BOOKS.dotAs(KEY_TITLE)
                    + ',' + TBL_BOOKS.dotAs(KEY_TITLE_OB)
                    + ',' + TBL_BOOKS.dotAs(KEY_DATE_FIRST_PUBLICATION)
                    + ",1 AS " + KEY_BOOK_COUNT
                    + _FROM_ + TBL_BOOKS.ref() + TBL_BOOKS.join(TBL_BOOK_AUTHOR);
        }

        /**
         * Sql INSERT.
         */
        static final class Insert {

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
        }

        /**
         * Sql UPDATE.
         */
        static final class Update {

            /** Update a single {@link TocEntry}. */
            static final String TOCENTRY =
                    UPDATE_ + TBL_TOC_ENTRIES.getName()
                    + _SET_ + KEY_TITLE + "=?"
                    + ',' + KEY_TITLE_OB + "=?"
                    + ',' + KEY_DATE_FIRST_PUBLICATION + "=?"
                    + _WHERE_ + KEY_PK_ID + "=?";

            /** Update a single Book's read status and read_end date. */
            static final String READ =
                    UPDATE_ + TBL_BOOKS.getName()
                    + _SET_ + KEY_UTC_LAST_UPDATED + "=current_timestamp"
                    + ',' + KEY_READ + "=?"
                    + ',' + KEY_READ_END + "=?"
                    + _WHERE_ + KEY_PK_ID + "=?";
        }

        /**
         * Sql DELETE.
         * <p>
         * All 'link' tables will be updated due to their FOREIGN KEY constraints.
         * The 'other-side' of a link table is cleaned by triggers.
         */
        public static final class Delete {

            /** Delete a {@link Book}. */
            static final String BOOK_BY_ID =
                    DELETE_FROM_ + TBL_BOOKS.getName() + _WHERE_ + KEY_PK_ID + "=?";
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
        }

        /**
         * Sql specific for FTS.
         */
        public static final class Fts {

            private static final String _MATCH = " MATCH ?";

            /** Standard Local-search. */
            public static final String SEARCH_SUGGESTIONS =
                    // KEY_FTS_BOOKS_PK is the _id into the books table.
                    SELECT_ + KEY_FTS_BOOK_ID + _AS_ + KEY_PK_ID
                    + ',' + (TBL_FTS_BOOKS.dot(KEY_TITLE)
                             + _AS_ + SearchManager.SUGGEST_COLUMN_TEXT_1)
                    + ',' + (TBL_FTS_BOOKS.dot(KEY_FTS_AUTHOR_NAME)
                             + _AS_ + SearchManager.SUGGEST_COLUMN_TEXT_2)
                    + ',' + (TBL_FTS_BOOKS.dot(KEY_TITLE)
                             + _AS_ + SearchManager.SUGGEST_COLUMN_INTENT_DATA)
                    + _FROM_ + TBL_FTS_BOOKS.getName()
                    + _WHERE_ + TBL_FTS_BOOKS.getName() + _MATCH;

            /**
             * The full UPDATE statement.
             * The parameter order MUST match the order expected in INSERT.
             */
            private static final String UPDATE =
                    UPDATE_ + TBL_FTS_BOOKS.getName()
                    + _SET_ + KEY_TITLE + "=?"
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

            /** the body of an INSERT INTO [table]. Used more than once. */
            private static final String INSERT_BODY =
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
            private static final String INSERT =
                    INSERT_INTO_ + TBL_FTS_BOOKS.getName() + INSERT_BODY;

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
            private static final String BOOK_BY_ID = ALL_BOOKS + _WHERE_ + KEY_PK_ID + "=?";

            /** Used during insert of a book. Minimal column list. Ordered by position. */
            private static final String GET_AUTHORS_BY_BOOK_ID =
                    SELECT_ + TBL_AUTHORS.dotAs(KEY_AUTHOR_FAMILY_NAME)
                    + ',' + TBL_AUTHORS.dotAs(KEY_AUTHOR_GIVEN_NAMES)
                    + _FROM_ + TBL_BOOK_AUTHOR.ref() + TBL_BOOK_AUTHOR.join(TBL_AUTHORS)
                    + _WHERE_ + TBL_BOOK_AUTHOR.dot(KEY_FK_BOOK) + "=?"
                    + _ORDER_BY_ + TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION);

            /** Used during insert of a book. Minimal column list. Ordered by position. */
            private static final String GET_PUBLISHERS_BY_BOOK_ID =
                    SELECT_ + TBL_PUBLISHERS.dotAs(KEY_PUBLISHER_NAME)
                    + _FROM_ + TBL_BOOK_PUBLISHER.ref() + TBL_BOOK_PUBLISHER.join(TBL_PUBLISHERS)
                    + _WHERE_ + TBL_BOOK_PUBLISHER.dot(KEY_FK_BOOK) + "=?"
                    + _ORDER_BY_ + TBL_BOOK_PUBLISHER.dot(KEY_BOOK_PUBLISHER_POSITION);

            /** Used during insert of a book. Minimal column list. Ordered by position. */
            private static final String GET_TOC_TITLES_BY_BOOK_ID =
                    SELECT_ + TBL_TOC_ENTRIES.dotAs(KEY_TITLE)
                    + _FROM_ + TBL_TOC_ENTRIES.ref() + TBL_TOC_ENTRIES.join(TBL_BOOK_TOC_ENTRIES)
                    + _WHERE_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_FK_BOOK) + "=?"
                    + _ORDER_BY_ + TBL_BOOK_TOC_ENTRIES.dot(KEY_BOOK_TOC_ENTRY_POSITION);

            /** Used during insert of a book. Minimal column list. Ordered by position. */
            private static final String GET_SERIES_BY_BOOK_ID =
                    SELECT_ + TBL_SERIES.dot(KEY_SERIES_TITLE) + "||' '||"
                    + " COALESCE(" + TBL_BOOK_SERIES.dot(KEY_BOOK_NUM_IN_SERIES) + ",'')"
                    + _AS_ + KEY_SERIES_TITLE
                    + _FROM_ + TBL_BOOK_SERIES.ref() + TBL_BOOK_SERIES.join(TBL_SERIES)
                    + _WHERE_ + TBL_BOOK_SERIES.dot(KEY_FK_BOOK) + "=?"
                    + _ORDER_BY_ + TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION);

            /** Advanced Local-search. */
            private static final String SEARCH =
                    // KEY_FTS_BOOKS_PK is the _id into the books table.
                    SELECT_ + KEY_FTS_BOOK_ID + _AS_ + KEY_PK_ID
                    + _FROM_ + TBL_FTS_BOOKS.getName()
                    + _WHERE_ + TBL_FTS_BOOKS.getName() + _MATCH + " LIMIT ?";

            /**
             * Prepare a search string for doing an FTS search.
             * <p>
             * All diacritic characters are converted to ASCII.
             * Remove punctuation from the search string to TRY to match the tokenizer.
             * The only punctuation we allow is a hyphen preceded by a space => negate
             * the next word.
             * Everything else is translated to a space.
             *
             * @param searchText Search criteria to clean
             * @param domain     (optional) domain to prefix the searchText
             *                   or {@code null} for none
             *
             * @return Clean string
             */
            @NonNull
            public static String prepareSearchText(@Nullable final String searchText,
                                                   @Nullable final String domain) {

                if (searchText == null || searchText.isEmpty()) {
                    return "";
                }

                // Convert the text to pure ASCII. We'll use an array to loop over it.
                final char[] chars = ParseUtils.toAscii(searchText).toCharArray();
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
                    for (final String word : cleanedText.split(" ")) {
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
             * @param keywords      Keywords to find anywhere in book;
             *                      this includes titles and authors
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

                return (prepareSearchText(keywords, null)
                        + prepareSearchText(author, KEY_FTS_AUTHOR_NAME)
                        + prepareSearchText(title, KEY_TITLE)
                        + prepareSearchText(seriesTitle, KEY_SERIES_TITLE)
                        + prepareSearchText(publisherName, KEY_PUBLISHER_NAME)
                ).trim();
            }
        }
    }

    /**
     * Manages the construction and closure of persisted SQLiteStatement objects.
     * <p>
     * The purpose is not the actual caching of the statement for re-use in loops
     * (Android does that anyhow), but the handling of properly closing statements.
     * You do get extra caching across individual calls, but not sure if that makes any impact.
     */
    private static class SqlStatementManager
            implements AutoCloseable {

        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "SqlStatementManager";

        /**
         * Note: the key is a String instead of an int, so we don't have to make sure integers
         * are unique across different classes using the same SqlStatementManager.
         */
        @NonNull
        private final Map<String, SynchronizedStatement> mStatements =
                Collections.synchronizedMap(new HashMap<>());

        /** The underlying database. */
        @NonNull
        private final SynchronizedDb mDb;

        /**
         * Constructor.
         *
         * @param db Database Access
         */
        SqlStatementManager(@NonNull final SynchronizedDb db) {
            mDb = db;
        }

        /**
         * Get a statement from the cache. Create it if needed.
         *
         * @param name        of the statement
         * @param sqlSupplier a Supplier to get the SQL for the statement if compiling is needed
         *
         * @return the statement.
         */
        @NonNull
        public SynchronizedStatement get(@NonNull final String name,
                                         @NonNull final Supplier<String> sqlSupplier) {
            SynchronizedStatement stmt = mStatements.get(name);
            if (stmt == null) {
                stmt = mDb.compileStatement(sqlSupplier.get());
                mStatements.put(name, stmt);
            }
            return stmt;
        }

        /**
         * Close and remove <strong>all</strong> statements.
         * <p>
         * RuntimeException are caught and ignored.
         */
        @Override
        public void close() {
            for (final SynchronizedStatement stmt : mStatements.values()) {
                try {
                    stmt.close();
                } catch (@NonNull final RuntimeException ignore) {
                    // ignore
                }
            }
            mStatements.clear();
        }

        /**
         * DEBUG: if we see the warn in the logs, we know we have an issue to fix.
         */
        @SuppressWarnings("FinalizeDeclaration")
        @Override
        @CallSuper
        protected void finalize()
                throws Throwable {
            if (!mStatements.isEmpty()) {
                if (BuildConfig.DEBUG /* always */) {
                    Logger.w(TAG, "finalize|mStatements=" + mStatements);
                }
                close();
            }
            super.finalize();
        }
    }
}
