package com.eleybourn.bookcatalogue.database;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedCursor;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;

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
 * <p>
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
 * <p>
 * BOOK_BOOKSHELF
 * - book: null  with bookshelf != 0
 */
public class DBCleaner {

    /** normal database actions. */
    private final DBA mDb;

    /** direct SQL actions. */
    private final SynchronizedDb mSyncDb;

    /**
     * Constructor.
     */
    public DBCleaner(@NonNull final DBA db) {
        mDb = db;
        mSyncDb = mDb.getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing();
    }

    public void all(final boolean dryRun) {

        // correct '2' entries
        bookAnthologyBitmask(dryRun);

        // remove orphan rows
        bookBookshelf(dryRun);

        // make sure these are '0' or '1'
        booleanCleanup(TBL_BOOKS, DOM_BOOK_READ, dryRun);
        booleanCleanup(TBL_BOOKS, DOM_BOOK_SIGNED, dryRun);

        //TODO: DOM_BOOK_PAGES is an integer, but often handled as a String....

        //TODO: books table: search out invalid uuid's, check if there is a file, rename/remove...
        // in particular if the UUID is surrounded with '' or ""
        //TODO: covers.db, filename column -> delete rows where the uuid is invalid
    }


    /** int. */
    public void idNotZero(@NonNull final DomainDefinition column,
                          final boolean dryRun) {
        String sql = "SELECT DISTINCT " + column + " FROM " + TBL_BOOKS
                + " WHERE " + column + " <> 0";
        toLog(Tracker.States.Enter, sql);
    }



    /* ****************************************************************************************** */
    /* The methods below are (for now) valid & tested / in-use. */
    /* ****************************************************************************************** */

    /**
     * Make sure the TOC bitmask is valid.
     * <p>
     * int: 0,1,3.
     * <p>
     * 2 should become 0
     *
     * @param dryRun <tt>true</tt> to run the update.
     */
    public void bookAnthologyBitmask(final boolean dryRun) {
        String sql = "SELECT DISTINCT " + DOM_BOOK_ANTHOLOGY_BITMASK + " FROM " + TBL_BOOKS
                + " WHERE " + DOM_BOOK_ANTHOLOGY_BITMASK + " NOT IN (0,1,3)";
        toLog(Tracker.States.Enter, sql);
        if (!dryRun) {
            mSyncDb.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_ANTHOLOGY_BITMASK + "=2"
                                    + " WHERE " + DOM_BOOK_ANTHOLOGY_BITMASK + " NOT IN (0,1,3)");
            toLog(Tracker.States.Exit, sql);
        }
    }

    /**
     * Remove rows where books are sitting on a 'null' bookshelf.
     *
     * @param dryRun <tt>true</tt> to run the update.
     */
    public void bookBookshelf(final boolean dryRun) {
        String sql = "SELECT DISTINCT " + DOM_FK_BOOK_ID + " FROM " + TBL_BOOK_BOOKSHELF
                + " WHERE " + DOM_FK_BOOKSHELF_ID + "=NULL";
        toLog(Tracker.States.Enter, sql);
        if (!dryRun) {
            mSyncDb.execSQL("DELETE " + TBL_BOOK_BOOKSHELF
                                    + " WHERE " + DOM_FK_BOOKSHELF_ID + "=NULL");
            toLog(Tracker.States.Exit, sql);
        }
    }

    /**
     * Convert any null values to an empty string.
     * <p>
     * Used to correct data in columns which have "string default ''"
     *
     * @param table  to check
     * @param column to check
     * @param dryRun <tt>true</tt> to run the update.
     */
    public void nullString2empty(@NonNull final TableDefinition table,
                                 @NonNull final DomainDefinition column,
                                 final boolean dryRun) {
        String sql = "SELECT DISTINCT " + column + " FROM " + table
                + " WHERE " + column + "=NULL";
        toLog(Tracker.States.Enter, sql);
        if (!dryRun) {
            mSyncDb.execSQL("UPDATE " + table + " SET " + column + "=''"
                                    + " WHERE " + column + "=NULL");
            toLog(Tracker.States.Exit, sql);
        }
    }

    /**
     * Set boolean columns to 0,1.
     *
     * @param table  to check
     * @param column to check
     * @param dryRun <tt>true</tt> to run the update.
     */
    public void booleanCleanup(@NonNull final TableDefinition table,
                               @NonNull final DomainDefinition column,
                               final boolean dryRun) {
        String sql = "SELECT DISTINCT " + column + " FROM " + table
                + " WHERE " + column + " NOT IN ('0','1')";

        toLog(Tracker.States.Enter, sql);
        if (!dryRun) {
            mSyncDb.execSQL("UPDATE " + table + " SET " + column + "=1"
                                    + " WHERE lower(" + column + ") IN ('true', 't')");
            mSyncDb.execSQL("UPDATE " + table + " SET " + column + "=0"
                                    + " WHERE lower(" + column + ") IN ('false','f')");
            toLog(Tracker.States.Exit, sql);
        }
    }

    /**
     * Execute the SQL and log the results.
     *
     * @param state Enter/Exit
     * @param sql   to execute
     */
    private void toLog(@NonNull final Tracker.States state,
                       @NonNull final String sql) {
        try (SynchronizedCursor cursor = mSyncDb.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                String field = cursor.getColumnName(0);
                String value = cursor.getString(0);

                Logger.info(this, state + "|" + field + '=' + value);
            }
        }
    }
}
