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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;

/**
 * Cleanup routines for some columns/tables which can be run at upgrades, import, startup
 * <p>
 * Work in progress.
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
        mSyncedDb = db.getSyncDb();
    }

    /**
     * Do a mass update of any languages not yet converted to ISO codes.
     * Special entries are left untouched; example "Dutch+French" a bilingual edition.
     *
     * @param context Current context
     */
    public void languages(@NonNull final Context context) {
        for (String lang : mDb.getLanguageCodes()) {
            if (lang != null && !lang.isEmpty()) {
                final String iso;
                if (lang.length() > 3) {
                    // It's likely a 'display' name of a language.
                    iso = LanguageUtils.getISO3FromDisplayName(context, lang);
                } else {
                    // It's almost certainly a language code
                    iso = LanguageUtils.getISO3FromCode(lang);
                }

                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "languages|Global language update"
                               + "|from=" + lang
                               + "|to=" + iso);
                }
                if (!iso.equals(lang)) {
                    mDb.updateLanguage(lang, iso);
                }
            }
        }
    }

    /**
     * Validates {@link Bookshelf} being set to a valid {@link BooklistStyle}.
     *
     * @param context Current context
     */
    public void bookshelves(@NonNull final Context context) {
        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            bookshelf.validateStyle(context, mDb);
        }
    }

//    public void nativeIds(@NonNull final Context context) {
//        for (Domain domain : DBDefinitions.NATIVE_ID_DOMAINS) {
//            switch (domain.getType()) {
//                case ColumnInfo.TYPE_INTEGER:
//                    break;
//
//                case ColumnInfo.TYPE_TEXT:
//                    break;
//
//                default:
//                    Logger.warnWithStackTrace(context, TAG, "type=" + domain.getType());
//                    break;
//            }
//        }
//    }

    /**
     * Validates all boolean columns to contain '0' or '1'.
     *
     * @param tables list of tables
     */
    public void booleanColumns(@NonNull final TableDefinition... tables) {
        for (TableDefinition table : tables) {
            for (Domain domain : table.getDomains()) {
                if (domain.isBoolean()) {
                    booleanCleanup(table.getName(), domain.getName());
                }
            }
        }
    }

    /**
     * Enforce boolean columns to 0,1.
     *
     * @param table  to check
     * @param column to check
     */
    private void booleanCleanup(@NonNull final String table,
                                @NonNull final String column) {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "booleanCleanup|table=" + table + "|column=" + column);
        }

        final String select = "SELECT DISTINCT " + column + " FROM " + table
                              + " WHERE " + column + " NOT IN ('0','1')";
        toLog("booleanCleanup", select);

        final String update = "UPDATE " + table + " SET " + column + "=?"
                              + " WHERE lower(" + column + ") IN ";
        String sql;
        sql = update + "('true','t','yes')";
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
            stmt.bindLong(1, 1);
            final int count = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG /* always */) {
                if (count > 0) {
                    Log.d(TAG, "booleanCleanup|true=" + count);
                }
            }
        }

        sql = update + "('false','f','no')";
        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
            stmt.bindLong(1, 0);
            final int count = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG /* always */) {
                if (count > 0) {
                    Log.d(TAG, "booleanCleanup|false=" + count);
                }
            }
        }
    }

    /**
     * Check for books which do not have an Author at position 1.
     * For those that don't, read their authors, and re-save them.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     */
    public void bookAuthors(@NonNull final Context context) {
        final String sql = "SELECT " + KEY_FK_BOOK + " FROM "
                           + "(SELECT " + KEY_FK_BOOK + ", MIN(" + KEY_BOOK_AUTHOR_POSITION
                           + ") AS mp"
                           + " FROM " + TBL_BOOK_AUTHOR.getName() + " GROUP BY " + KEY_FK_BOOK
                           + ") WHERE mp > 1";

        final ArrayList<Long> bookIds = mDb.getIdList(sql);
        if (!bookIds.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "bookSeries|" + TBL_BOOK_AUTHOR.getName()
                           + ", rows=" + bookIds.size());
            }
            // ENHANCE: we really should fetch each book individually
            final Locale bookLocale = LocaleUtils.getUserLocale(context);

            Synchronizer.SyncLock txLock = null;
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }
            try {
                for (long bookId : bookIds) {
                    final ArrayList<Author> list = mDb.getAuthorsByBookId(bookId);
                    mDb.insertBookAuthors(context, bookId, list, false, bookLocale);
                }
                if (txLock != null) {
                    mSyncedDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException e) {
                Logger.error(context, TAG, e);

            } finally {
                if (txLock != null) {
                    mSyncedDb.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "bookSeries|done");
                }
            }
        }
    }

    /**
     * Check for books which do not have a Series at position 1.
     * For those that don't, read their series, and re-save them.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     */
    public void bookSeries(@NonNull final Context context) {
        final String sql = "SELECT " + KEY_FK_BOOK + " FROM "
                           + "(SELECT " + KEY_FK_BOOK + ", MIN(" + KEY_BOOK_SERIES_POSITION
                           + ") AS mp"
                           + " FROM " + TBL_BOOK_SERIES.getName() + " GROUP BY " + KEY_FK_BOOK
                           + ") WHERE mp > 1";

        final ArrayList<Long> bookIds = mDb.getIdList(sql);
        if (!bookIds.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "bookSeries|" + TBL_BOOK_SERIES.getName()
                           + ", rows=" + bookIds.size());
            }
            // ENHANCE: we really should fetch each book individually
            final Locale bookLocale = LocaleUtils.getUserLocale(context);

            Synchronizer.SyncLock txLock = null;
            if (!mSyncedDb.inTransaction()) {
                txLock = mSyncedDb.beginTransaction(true);
            }
            try {
                for (long bookId : bookIds) {
                    final ArrayList<Series> list = mDb.getSeriesByBookId(bookId);
                    mDb.insertBookSeries(context, bookId, list, false, bookLocale);
                }
                if (txLock != null) {
                    mSyncedDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException e) {
                Logger.error(context, TAG, e);
            } finally {
                if (txLock != null) {
                    mSyncedDb.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "bookSeries|done");
                }
            }
        }
    }

    /* ****************************************************************************************** */

    public void maybeUpdate(@NonNull final Context context,
                            final boolean dryRun) {

        // remove orphan rows
        bookBookshelf(dryRun);


        //TODO: books table: search for invalid UUIDs, check if there is a file, rename/remove...
        // in particular if the UUID is surrounded with '' or ""
    }

    /**
     * Remove rows where books are sitting on a {@code null} bookshelf.
     *
     * @param dryRun {@code true} to run the update.
     */
    private void bookBookshelf(final boolean dryRun) {
        final String select = "SELECT DISTINCT " + KEY_FK_BOOK
                              + " FROM " + TBL_BOOK_BOOKSHELF
                              + " WHERE " + KEY_FK_BOOKSHELF + "=NULL";
        toLog("bookBookshelf|ENTER", select);
        if (!dryRun) {
            final String sql = "DELETE " + TBL_BOOK_BOOKSHELF
                               + " WHERE " + KEY_FK_BOOKSHELF + "=NULL";
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog("bookBookshelf|EXIT", select);
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
    public void nullString2empty(@NonNull final String table,
                                 @NonNull final String column,
                                 final boolean dryRun) {
        final String select = "SELECT DISTINCT " + column + " FROM " + table
                              + " WHERE " + column + "=NULL";
        toLog("nullString2empty|ENTER", select);
        if (!dryRun) {
            final String sql = "UPDATE " + table + " SET " + column + "=''"
                               + " WHERE " + column + "=NULL";
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog("nullString2empty|EXIT", select);
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
                Log.d(TAG, state + "|row count=" + cursor.getCount());
                while (cursor.moveToNext()) {
                    final String field = cursor.getColumnName(0);
                    final String value = cursor.getString(0);

                    Log.d(TAG, state + '|' + field + '=' + value);
                }
            }
        }
    }
}
