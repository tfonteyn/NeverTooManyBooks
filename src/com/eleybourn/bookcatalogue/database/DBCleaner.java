package com.eleybourn.bookcatalogue.database;

import androidx.annotation.NonNull;

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
 *
 *
 * 5.2.2 BOOKS table:
 *
 * CREATE TABLE books (
 *
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
 *
 * title text not null,
 * language text default ''
 *
 * rating float not null default 0,
 * anthology int not null default 0,
 * read boolean not null default 0,
 * signed boolean not null default 0,
 *
 * date_added datetime default current_timestamp,
 * last_goodreads_sync_date date default '0000-00-00'
 * last_update_date date default current_timestamp not null)
 *
 * book_uuid text default (lower(hex(randomblob(16)))) not null
 *
 *
 *
 * - anthology, no '2' entries, so seems ok
 *
 * - notes: null, ''
 * - location: null, ''
 * - read-start: null, ''
 * - read-end: null, ''
 * - Goodreads-book-id: null, 0
 *
 * - Goodreads-last-sync: 0000-00-00, ''
 *
 * - signed: 0,1,false,true
 *
 *
 * BOOK_BOOKSHELF
 * - book: null  with bookshelf != 0
 */
public class DBCleaner {

    /** normal database actions */
    private final CatalogueDBAdapter mDb;

    /** direct SQL actions */
    private final DbSync.SynchronizedDb mSyncDb;

    /**
     * Constructor
     */
    public DBCleaner(final CatalogueDBAdapter db) {
        mDb = db;
        mSyncDb = mDb.getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing();
    }

    public void all(final boolean dryRun) {

        // sanity check for '2' only
        bookAnthologyBitmask(dryRun);

        // remove orphan rows
        book_bookshelf(dryRun);

        // make sure these are '0' or '1'
        booleanCleanup(TBL_BOOKS, DOM_BOOK_READ, dryRun);
        booleanCleanup(TBL_BOOKS, DOM_BOOK_SIGNED, dryRun);

        //TOMF: DOM_BOOK_PAGES is an integer, but often handled as a String....

        //TODO: books table: search out invalid uuid's, check if there is a file, rename/remove...
        // in particular if the UUID is surrounded with '' or ""
        //TODO: covers.db, filename column -> delete rows where the uuid is invalid
    }

    public void book_bookshelf(final boolean dryRun) {
        String sql = "SELECT DISTINCT " + DOM_FK_BOOK_ID +
                " FROM " + TBL_BOOK_BOOKSHELF + " WHERE " + DOM_FK_BOOKSHELF_ID + "=NULL";
        toLog(Tracker.States.Enter, sql);
        if (!dryRun) {
            mSyncDb.execSQL("DELETE " + TBL_BOOK_BOOKSHELF +
                    " WHERE " + DOM_FK_BOOKSHELF_ID + "=NULL");
            toLog(Tracker.States.Exit, sql);
        }
    }

    /**
     * int: 0,1,3
     */
    public void bookAnthologyBitmask(final boolean dryRun) {
        String sql = "SELECT DISTINCT " + DOM_BOOK_ANTHOLOGY_BITMASK +
                " FROM " + TBL_BOOKS + " WHERE " + DOM_BOOK_ANTHOLOGY_BITMASK + " NOT IN (0,1,3)";
        toLog(Tracker.States.Enter, sql);
    }

    /** int */
    public void idNotZero(final @NonNull DomainDefinition column, final boolean dryRun) {
        String sql = "SELECT DISTINCT " + column +
                " FROM " + TBL_BOOKS + " WHERE " + column + " <> 0";
        toLog(Tracker.States.Enter, sql);
    }

    /**
     * string default ''
     */
    public void nullString2empty(final @NonNull TableDefinition table,
                                 final @NonNull DomainDefinition column,
                                 final boolean dryRun) {
        String sql = "SELECT DISTINCT " + column +
                " FROM " + table + " WHERE " + column + "=NULL";
                toLog(Tracker.States.Enter, sql);

        if (!dryRun) {
            mSyncDb.execSQL("UPDATE " + table + " SET " + column + "=''" + " WHERE " + column + "=NULL");
            toLog(Tracker.States.Exit, sql);
        }
    }

    /**
     * boolean: 0,1
     */
    public void booleanCleanup(final @NonNull TableDefinition table,
                               final @NonNull DomainDefinition column,
                               final boolean dryRun) {
        String sql = "SELECT DISTINCT " + column +
                " FROM " + table + " WHERE " + column + " NOT IN ('0','1')";

        toLog(Tracker.States.Enter, sql);
        if (!dryRun) {
            mSyncDb.execSQL("UPDATE " + table + " SET " + column + "=1 WHERE lower(" + column + ") IN ('true', 't')");
            mSyncDb.execSQL("UPDATE " + table + " SET " + column + "=0 WHERE lower(" + column + ") IN ('false','f')");
            toLog(Tracker.States.Exit, sql);
        }
    }

    public void toLog(final @NonNull Tracker.States state, final @NonNull String sql) {
        DbSync.SynchronizedCursor cursor = mSyncDb.rawQuery(sql, null);
        while (cursor.moveToNext()) {
            String field = cursor.getColumnName(0);
            String value = cursor.getString(0);

            Logger.info(this, state + "|" + field + "=" + value);
        }
        cursor.close();
    }
}
