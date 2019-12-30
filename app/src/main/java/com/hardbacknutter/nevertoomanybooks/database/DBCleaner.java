/*
 * @Copyright 2019 HardBackNutter
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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_READ;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_SIGNED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;

/**
 * Intention is to create cleanup routines for some columns/tables
 * which can be run at upgrades, import, startup
 * <p>
 * Work in progress.
 * <p>
 * 5.2.2 BOOKS table:
 * <p>
 * CREATE TABLE books (
 * <p>
 * isbn text,
 * publisher text,
 * date_published date,
 * pages int,
 * notes text,
 * list_price text,
 * location text,
 * read_start date,
 * read_end date,
 * format text,
 * description text,
 * genre text,
 * goodreads_book_id int
 * <p>
 * title text not null,
 * language text default ''
 * <p>
 * rating float not null default 0,
 * anthology int not null default 0,
 * read boolean not null default 0,
 * signed boolean not null default 0,
 * <p>
 * date_added datetime default current_timestamp,
 * last_goodreads_sync_date date default '0000-00-00'
 * last_update_date date default current_timestamp not null)
 * <p>
 * book_uuid text default (lower(hex(randomblob(16)))) not null
 * <p>
 * - anthology, no '2' entries, so seems ok
 * <p>
 * - notes: null, ''
 * - location: null, ''
 * - read-start: null, ''
 * - read-end: null, ''
 * - Goodreads-book-id: null, 0
 * <p>
 * - Goodreads-last-sync: 0000-00-00, ''
 * <p>
 * - signed: 0,1,false,true
 * <p>
 * BOOK_BOOKSHELF
 * - book: null  with bookshelf != 0
 */
public class DBCleaner {

    /** Log tag. */
    private static final String TAG = "DBCleaner";

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    /** The underlying database. */
    @NonNull
    private final SynchronizedDb mSyncedDb;

    /**
     * Constructor.
     *
     * @param db Database Access
     */
    public DBCleaner(@NonNull final DAO db) {
        mDb = db;
        mSyncedDb = db.getUnderlyingDatabase();
    }

    /**
     * Do a mass update of any languages not yet converted to ISO codes.
     *
     * @param context Current context
     */
    public void updateLanguages(@NonNull final Context context) {
        List<String> names = mDb.getLanguageCodes();
        for (String name : names) {
            if (name != null && name.length() > 3) {
                String iso = LanguageUtils.getISO3FromDisplayName(context, name);
                Log.d(TAG, "updateLanguages|Global language update"
                           + "|from=" + name
                           + "|to=" + iso);
                if (!iso.equals(name)) {
                    mDb.updateLanguage(name, iso);
                }
            }
        }
    }

    /**
     * Validates the style versus Bookshelf.
     *
     * @param context Current context
     */
    public void bookshelves(@NonNull final Context context) {
        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            bookshelf.validateStyle(context, mDb);
        }
    }

    /* ****************************************************************************************** */


    public void maybeUpdate(@NonNull final Context context,
                            final boolean dryRun) {

        // remove orphan rows
        bookBookshelf(dryRun);


        // make sure these are '0' or '1'
        booleanCleanup(TBL_BOOKS, DOM_BOOK_READ, dryRun);
        booleanCleanup(TBL_BOOKS, DOM_BOOK_SIGNED, dryRun);

        //TODO: books table: search for invalid UUIDs, check if there is a file, rename/remove...
        // in particular if the UUID is surrounded with '' or ""
    }

    /**
     * Remove rows where books are sitting on a {@code null} bookshelf.
     *
     * @param dryRun {@code true} to run the update.
     */
    private void bookBookshelf(final boolean dryRun) {
        String select = "SELECT DISTINCT " + DOM_FK_BOOK
                        + " FROM " + TBL_BOOK_BOOKSHELF
                        + " WHERE " + DOM_FK_BOOKSHELF + "=NULL";
        toLog("ENTER", select);
        if (!dryRun) {
            String sql = "DELETE " + TBL_BOOK_BOOKSHELF
                         + " WHERE " + DOM_FK_BOOKSHELF + "=NULL";
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog("EXIT", select);
        }
    }

    /**
     * Check for books which do not have an Author at position 1.
     * For those that don't, read their authors, and re-save them.
     * <p>
     * Transaction: participate, or runs in new.
     *
     * @param context Current context
     */
    public void bookAuthors(@NonNull final Context context) {
        String sql = "SELECT " + DOM_FK_BOOK + " FROM "
                     + "(SELECT " + DOM_FK_BOOK + ", MIN(" + DOM_BOOK_AUTHOR_POSITION + ") AS mp"
                     + " FROM " + TBL_BOOK_AUTHOR + " GROUP BY " + DOM_FK_BOOK
                     + ") WHERE mp > 1";

        ArrayList<Long> bookIds = mDb.getIdList(sql);
        if (!bookIds.isEmpty()) {
            Log.w(TAG, "bookSeries|" + TBL_BOOK_AUTHOR.getName() + ", rows=" + bookIds.size());
            Synchronizer.SyncLock txLock = null;
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }
            try {
                for (long bookId : bookIds) {
                    ArrayList<Author> list = mDb.getAuthorsByBookId(bookId);
                    mDb.insertBookAuthors(context, bookId, list);
                }
                if (txLock != null) {
                    mSyncedDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException e) {
                Logger.error(TAG, e);
            } finally {
                if (txLock != null) {
                    mSyncedDb.endTransaction(txLock);
                }
                Log.w(TAG, "bookSeries|done");
            }
        }
    }

    /**
     * Check for books which do not have a Series at position 1.
     * For those that don't, read their series, and re-save them.
     * <p>
     * Transaction: participate, or runs in new.
     *
     * @param context Current context
     */
    public void bookSeries(@NonNull final Context context) {
        String sql = "SELECT " + DOM_FK_BOOK + " FROM "
                     + "(SELECT " + DOM_FK_BOOK + ", MIN(" + DOM_BOOK_SERIES_POSITION + ") AS mp"
                     + " FROM " + TBL_BOOK_SERIES + " GROUP BY " + DOM_FK_BOOK
                     + ") WHERE mp > 1";

        ArrayList<Long> bookIds = mDb.getIdList(sql);
        if (!bookIds.isEmpty()) {
            Log.w(TAG, "bookSeries|" + TBL_BOOK_SERIES.getName() + ", rows=" + bookIds.size());
            // ENHANCE: we really should fetch each book individually
            Locale bookLocale = Locale.getDefault();

            Synchronizer.SyncLock txLock = null;
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }
            try {
                for (long bookId : bookIds) {
                    ArrayList<Series> list = mDb.getSeriesByBookId(bookId);
                    mDb.insertBookSeries(context, bookId, bookLocale, list);
                }
                if (txLock != null) {
                    mSyncedDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException e) {
                Logger.error(TAG, e);
            } finally {
                if (txLock != null) {
                    mSyncedDb.endTransaction(txLock);
                }
                Log.w(TAG, "bookSeries|done");
            }
        }
    }

    /* ****************************************************************************************** */

    /**
     * Set boolean columns to 0,1.
     *
     * @param table  to check
     * @param column to check
     * @param dryRun {@code true} to run the update.
     */
    public void booleanCleanup(@NonNull final TableDefinition table,
                               @NonNull final DomainDefinition column,
                               final boolean dryRun) {
        String select = "SELECT DISTINCT " + column + " FROM " + table
                        + " WHERE " + column + " NOT IN ('0','1')";

        toLog("ENTER", select);
        if (!dryRun) {
            String sql = "UPDATE " + table + " SET " + column + "=1"
                         + " WHERE lower(" + column + ") IN ('true','t')";
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            sql = "UPDATE " + table + " SET " + column + "=0"
                  + " WHERE lower(" + column + ") IN ('false','f')";
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog("EXIT", select);
        }
    }

    /**
     * Convert any {@code null} values to an empty string.
     * <p>
     * Used to correct data in columns which have "string default ''"
     *
     * @param table  to check
     * @param column to check
     * @param dryRun {@code true} to run the update.
     */
    public void nullString2empty(@NonNull final TableDefinition table,
                                 @NonNull final DomainDefinition column,
                                 final boolean dryRun) {
        String select = "SELECT DISTINCT " + column + " FROM " + table
                        + " WHERE " + column + "=NULL";
        toLog("ENTER", select);
        if (!dryRun) {
            String sql = "UPDATE " + table + " SET " + column + "=''"
                         + " WHERE " + column + "=NULL";
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog("EXIT", select);
        }
    }

    /**
     * WIP... debug
     * Execute the query and log the results.
     *
     * @param state Enter/Exit
     * @param query to execute
     */
    private void toLog(@NonNull final String state,
                       @NonNull final String query) {
        if (BuildConfig.DEBUG) {
            try (SynchronizedCursor cursor = mSyncedDb.rawQuery(query, null)) {
                while (cursor.moveToNext()) {
                    String field = cursor.getColumnName(0);
                    String value = cursor.getString(0);

                    Log.d(TAG, state + '|' + field + '=' + value);
                }
            }
        }
    }
}
