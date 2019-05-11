package com.eleybourn.bookcatalogue.database;

import androidx.annotation.NonNull;

import java.util.List;

import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedCursor;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_TOC_BITMASK;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOK_BOOKSHELF;

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

    /** Database access. */
    @NonNull
    private final DAO mDb;

    /** The underlying database. */
    @NonNull
    private final SynchronizedDb mSyncedDb;

    /**
     * Constructor.
     */
    public DBCleaner(@NonNull final DAO db) {
        mDb = db;
        mSyncedDb = db.getUnderlyingDatabase();
    }

    public void maybeUpdate(final boolean dryRun) {

        // correct '2' entries
        bookAnthologyBitmask(dryRun);

        // remove orphan rows
        bookBookshelf(dryRun);

        // make sure these are '0' or '1'
        booleanCleanup(TBL_BOOKS, DOM_BOOK_READ, dryRun);
        booleanCleanup(TBL_BOOKS, DOM_BOOK_SIGNED, dryRun);

        //TODO: books table: search out invalid uuid's, check if there is a file, rename/remove...
        // in particular if the UUID is surrounded with '' or ""
        //TODO: covers.db, filename column -> delete rows where the uuid is invalid
    }


    /* ****************************************************************************************** */

    /**
     * Do a mass update of any languages not yet converted to ISO3 codes.
     */
    public void updateLanguages() {
        List<String> names = mDb.getLanguageCodes();
        for (String name : names) {
            if (name != null && name.length() > 3) {
                String iso = LocaleUtils.getISO3Language(name);
                Logger.debug(this,
                             "updateLanguages",
                             "Global language update of `" + name + "` to `" + iso + '`');
                if (!iso.equals(name)) {
                    mDb.updateLanguage(name, iso);
                }
            }
        }
    }

    /**
     * Make sure the TOC bitmask is valid.
     * <p>
     * int: 0,1,3.
     * <p>
     * 2 should become 0
     *
     * @param dryRun {@code true} to run the update.
     */
    public void bookAnthologyBitmask(final boolean dryRun) {
        String select = "SELECT DISTINCT " + DOM_BOOK_TOC_BITMASK + " FROM " + TBL_BOOKS
                + " WHERE " + DOM_BOOK_TOC_BITMASK + " NOT IN (0,1,3)";
        toLog(Tracker.State.Enter, select);
        if (!dryRun) {
            String sql = "UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_TOC_BITMASK + "=2"
                    + " WHERE " + DOM_BOOK_TOC_BITMASK + " NOT IN (0,1,3)";
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(sql)) {
                stmt.executeUpdateDelete();
            }
            toLog(Tracker.State.Exit, select);
        }
    }

    /**
     * Remove rows where books are sitting on a {@code null} bookshelf.
     *
     * @param dryRun {@code true} to run the update.
     */
    public void bookBookshelf(final boolean dryRun) {
        String select = "SELECT DISTINCT " + DOM_FK_BOOK_ID + " FROM " + TBL_BOOK_BOOKSHELF
                + " WHERE " + DOM_FK_BOOKSHELF_ID + "=NULL";
        toLog(Tracker.State.Enter, select);
        if (!dryRun) {
            String sql = "DELETE " + TBL_BOOK_BOOKSHELF
                    + " WHERE " + DOM_FK_BOOKSHELF_ID + "=NULL";
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
