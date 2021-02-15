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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_PUBLISHER_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_TOC_ENTRY_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_ADDED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_GOODREADS_LAST_SYNC_DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;

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
    private final SynchronizedDb mSyncDb;

    /**
     * Constructor.
     *
     * @param db Database Access
     */
    public DBCleaner(@NonNull final DAO db) {
        mDb = db;
        mSyncDb = mDb.getSyncDb();
    }

    /**
     * Do a bulk update of any languages not yet converted to ISO codes.
     * Special entries are left untouched; example "Dutch+French" a bilingual edition.
     *
     * @param context Current context
     */
    public void languages(@NonNull final Context context,
                          @NonNull final Locale userLocale) {
        for (final String lang : mDb.getLanguageCodes()) {
            if (lang != null && !lang.isEmpty()) {
                final String iso;
                if (lang.length() > 3) {
                    // It's likely a 'display' name of a language.
                    iso = Languages
                            .getInstance().getISO3FromDisplayName(context, userLocale, lang);
                } else {
                    // It's almost certainly a language code
                    iso = Languages.getInstance().getISO3FromCode(lang);
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
     * Replace 'T' occurrences with ' '.
     * See package-info docs for
     * {@link com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser}
     */
    public void datetimeFormat() {
        final String[] columns = new String[]{
                KEY_UTC_LAST_UPDATED,
                KEY_UTC_ADDED,
                KEY_UTC_GOODREADS_LAST_SYNC_DATE
        };

        final Pattern T = Pattern.compile("T");

        final Collection<Pair<Long, String>> rows = new ArrayList<>();

        for (final String key : columns) {
            try (Cursor cursor = mSyncDb.rawQuery(
                    "SELECT " + KEY_PK_ID + ',' + key + " FROM " + TBL_BOOKS.getName()
                    + " WHERE " + key + " LIKE '%T%'", null)) {
                while (cursor.moveToNext()) {
                    rows.add(new Pair<>(cursor.getLong(0), cursor.getString(1)));
                }
            }

            if (BuildConfig.DEBUG /* always */) {
                Logger.d(TAG, "dates",
                         "key=" + key
                         + "|rows.size()=" + rows.size());
            }
            try (SynchronizedStatement stmt = mSyncDb.compileStatement(
                    "UPDATE " + TBL_BOOKS.getName()
                    + " SET " + key + "=? WHERE " + KEY_PK_ID + "=?")) {

                for (final Pair<Long, String> row : rows) {
                    stmt.bindString(1, T.matcher(row.second).replaceFirst(" "));
                    stmt.bindLong(2, row.first);
                    stmt.executeUpdateDelete();
                }
            }
            // reuse for next column
            rows.clear();
        }
    }

    /**
     * Validates {@link Bookshelf} being set to a valid {@link ListStyle}.
     *
     * @param context Current context
     */
    public void bookshelves(@NonNull final Context context) {
        for (final Bookshelf bookshelf : mDb.getBookshelves()) {
            bookshelf.validateStyle(context, mDb);
        }
    }

    /**
     * Validates all boolean columns to contain '0' or '1'.
     *
     * @param tables list of tables
     */
    public void booleanColumns(@NonNull final TableDefinition... tables) {
        for (final TableDefinition table : tables) {
            table.getDomains()
                 .stream()
                 .filter(Domain::isBoolean)
                 .forEach(domain -> booleanCleanup(table.getName(), domain.getName()));
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
        try (SynchronizedStatement stmt = mSyncDb.compileStatement(sql)) {
            stmt.bindLong(1, 1);
            final int count = stmt.executeUpdateDelete();
            if (BuildConfig.DEBUG /* always */) {
                if (count > 0) {
                    Log.d(TAG, "booleanCleanup|true=" + count);
                }
            }
        }

        sql = update + "('false','f','no')";
        try (SynchronizedStatement stmt = mSyncDb.compileStatement(sql)) {
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
     * Check for books which do not have an {@link Author} at position 1.
     * For those that don't, read their list, and re-save them.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     */
    public void bookAuthor(@NonNull final Context context) {
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
            final Locale bookLocale = AppLocale.getInstance().getUserLocale(context);

            Synchronizer.SyncLock txLock = null;
            try {
                if (!mDb.inTransaction()) {
                    txLock = mDb.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final ArrayList<Author> list = mDb.getAuthorsByBookId(bookId);
                    mDb.insertBookAuthors(context, bookId, list, false, bookLocale);
                }
                if (txLock != null) {
                    mDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DAO.DaoWriteException e) {
                Logger.error(context, TAG, e);

            } finally {
                if (txLock != null) {
                    mDb.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "bookSeries|done");
                }
            }
        }
    }

    /**
     * Check for books which do not have a {@link Series} at position 1.
     * For those that don't, read their list, and re-save them.
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
            final Locale bookLocale = AppLocale.getInstance().getUserLocale(context);

            Synchronizer.SyncLock txLock = null;
            try {
                if (!mDb.inTransaction()) {
                    txLock = mDb.beginTransaction(true);
                }

                for (final long bookId : bookIds) {
                    final ArrayList<Series> list = mDb.getSeriesByBookId(bookId);
                    mDb.insertBookSeries(context, bookId, list, false, bookLocale);
                }
                if (txLock != null) {
                    mDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DAO.DaoWriteException e) {
                Logger.error(context, TAG, e);
            } finally {
                if (txLock != null) {
                    mDb.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "bookSeries|done");
                }
            }
        }
    }

    /**
     * Check for books which do not have an {@link Publisher} at position 1.
     * For those that don't, read their list, and re-save them.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     */
    public void bookPublisher(@NonNull final Context context) {
        final String sql = "SELECT " + KEY_FK_BOOK + " FROM "
                           + "(SELECT " + KEY_FK_BOOK + ", MIN(" + KEY_BOOK_PUBLISHER_POSITION
                           + ") AS mp"
                           + " FROM " + TBL_BOOK_PUBLISHER.getName() + " GROUP BY " + KEY_FK_BOOK
                           + ") WHERE mp > 1";

        final ArrayList<Long> bookIds = mDb.getIdList(sql);
        if (!bookIds.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "bookPublishers|" + TBL_BOOK_PUBLISHER.getName()
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
                    final ArrayList<Publisher> list = mDb.getPublishersByBookId(bookId);
                    mDb.insertBookPublishers(context, bookId, list, false, bookLocale);
                }
                if (txLock != null) {
                    mDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DAO.DaoWriteException e) {
                Logger.error(context, TAG, e);

            } finally {
                if (txLock != null) {
                    mDb.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "bookPublishers|done");
                }
            }
        }
    }

    /**
     * Check for books which do not have a {@link TocEntry} at position 1.
     * For those that don't, read their list, and re-save them.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     */
    @SuppressWarnings("WeakerAccess")
    public void bookTocEntry(@NonNull final Context context) {
        final String sql = "SELECT " + KEY_FK_BOOK + " FROM "
                           + "(SELECT " + KEY_FK_BOOK + ", MIN(" + KEY_BOOK_TOC_ENTRY_POSITION
                           + ") AS mp"
                           + " FROM " + TBL_BOOK_TOC_ENTRIES.getName() + " GROUP BY " + KEY_FK_BOOK
                           + ") WHERE mp > 1";

        final ArrayList<Long> bookIds = mDb.getIdList(sql);
        if (!bookIds.isEmpty()) {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "bookTocEntry|" + TBL_BOOK_TOC_ENTRIES.getName()
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
                    final ArrayList<TocEntry> list = mDb.getTocEntryByBookId(bookId);
                    mDb.saveTocList(context, bookId, list, false, bookLocale);
                }
                if (txLock != null) {
                    mDb.setTransactionSuccessful();
                }
            } catch (@NonNull final RuntimeException | DAO.DaoWriteException e) {
                Logger.error(context, TAG, e);

            } finally {
                if (txLock != null) {
                    mDb.endTransaction(txLock);
                }
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "bookTocEntry|done");
                }
            }
        }
    }

    /* ****************************************************************************************** */

    public void maybeUpdate(@NonNull final Context context,
                            final boolean dryRun) {

        // remove orphan rows
        bookBookshelf(dryRun);
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
            try (SynchronizedStatement stmt = mSyncDb.compileStatement(sql)) {
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
            try (SynchronizedStatement stmt = mSyncDb.compileStatement(sql)) {
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
        if (BuildConfig.DEBUG /* always */) {
            try (SynchronizedCursor cursor = mSyncDb.rawQuery(query, null)) {
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
