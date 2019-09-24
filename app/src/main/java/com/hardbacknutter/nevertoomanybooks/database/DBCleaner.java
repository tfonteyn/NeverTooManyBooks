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

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.Tracker;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;

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

    public void maybeUpdate(final boolean dryRun) {

        // remove orphan rows
        bookBookshelf(dryRun);

        // make sure these are '0' or '1'
        booleanCleanup(DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_BOOK_READ, dryRun);
        booleanCleanup(DBDefinitions.TBL_BOOKS, DBDefinitions.DOM_BOOK_SIGNED, dryRun);

        //TODO: books table: search out invalid UUIDs, check if there is a file, rename/remove...
        // in particular if the UUID is surrounded with '' or ""
    }

    /**
     * Do a mass update of any languages not yet converted to ISO codes.
     */
    public void updateLanguages() {
        List<String> names = mDb.getLanguageCodes();
        for (String name : names) {
            if (name != null && name.length() > 3) {
                String iso = LanguageUtils.getIso3fromDisplayName(name, Locale.getDefault());
                Logger.debug(this, "updateLanguages",
                             "Global language update of `" + name + "` to `" + iso + '`');
                if (!iso.equals(name)) {
                    mDb.updateLanguage(name, iso);
                }
            }
        }
    }

    /**
     * Validates the style versus Bookshelf. No dry-run.
     */
    public void bookshelves() {
        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            bookshelf.validateStyle(mDb);
        }
    }

    /**
     * Remove rows where books are sitting on a {@code null} bookshelf.
     *
     * @param dryRun {@code true} to run the update.
     */
    private void bookBookshelf(final boolean dryRun) {
        String select = "SELECT DISTINCT " + DBDefinitions.DOM_FK_BOOK
                        + " FROM " + DBDefinitions.TBL_BOOK_BOOKSHELF
                        + " WHERE " + DBDefinitions.DOM_FK_BOOKSHELF + "=NULL";
        toLog(Tracker.State.Enter, select);
        if (!dryRun) {
            String sql = "DELETE " + DBDefinitions.TBL_BOOK_BOOKSHELF
                         + " WHERE " + DBDefinitions.DOM_FK_BOOKSHELF + "=NULL";
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog(Tracker.State.Exit, select);
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

        toLog(Tracker.State.Enter, select);
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
            toLog(Tracker.State.Exit, select);
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
        toLog(Tracker.State.Enter, select);
        if (!dryRun) {
            String sql = "UPDATE " + table + " SET " + column + "=''"
                         + " WHERE " + column + "=NULL";
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog(Tracker.State.Exit, select);
        }
    }

    /**
     * WIP... debug
     * Execute the query and log the results.
     *
     * @param state Enter/Exit
     * @param query to execute
     */
    private void toLog(@NonNull final Tracker.State state,
                       @NonNull final String query) {
        try (SynchronizedCursor cursor = mSyncedDb.rawQuery(query, null)) {
            while (cursor.moveToNext()) {
                String field = cursor.getColumnName(0);
                String value = cursor.getString(0);

                Logger.debug(this, state.toString(), field + '=' + value);
            }
        }
    }
}
