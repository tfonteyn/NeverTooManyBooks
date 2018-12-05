package com.eleybourn.bookcatalogue.database;

import com.eleybourn.bookcatalogue.debug.Logger;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISFDB_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;

/**
 * Intention is to create cleanup routines for some columns/tables
 * which can be run at upgrades and/or startup
 *
 *
 * As seen in 5.2.2 BOOKS table:
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
 * - Goodreads-last-sync: 0000-00-00, ''
 *
 * - signed: 0,1,false,true

 *
 * BOOK_BOOKSHELF
 * - book: null  with bookshelf != 0
 *
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
        book_bookshelf(dryRun);

        // books table
        bookSigned(dryRun);
        bookRead(dryRun);
        bookAnthologyBitmask(dryRun);
        // v83
        bookISFDBid(dryRun);
        bookLTid(dryRun);
    }

    public void book_bookshelf(final boolean dryRun) {
        toLog("SELECT DISTINCT " + DOM_FK_BOOK_ID +
                " FROM " + TBL_BOOK_BOOKSHELF + " WHERE " + DOM_FK_BOOKSHELF_ID + "=NULL");
        if (!dryRun) {
            mSyncDb.rawQuery("DELETE " + TBL_BOOK_BOOKSHELF + " WHERE " + DOM_FK_BOOKSHELF_ID + "=NULL", null);
        }
    }

    /**
     * boolean: 0,1
     */
    public void bookSigned(final boolean dryRun) {
        toLog("SELECT DISTINCT " + DOM_BOOK_SIGNED +
                " FROM " + TBL_BOOKS + " WHERE " + DOM_BOOK_SIGNED + " NOT IN ('0','1')");
        if (!dryRun) {
            mSyncDb.rawQuery("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SIGNED + "=1 WHERE " + DOM_BOOK_SIGNED + "='true'", null);
            mSyncDb.rawQuery("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SIGNED + "=0 WHERE " + DOM_BOOK_SIGNED + "='false'", null);
        }
    }

    /**
     * boolean: 0,1
     */
    public void bookRead(final boolean dryRun) {
        toLog("SELECT DISTINCT " + DOM_BOOK_READ +
                " FROM " + TBL_BOOKS + " WHERE " + DOM_BOOK_READ + " NOT IN ('0','1')");
    }



    /**
     * int: 0,1,3
     */
    public void bookAnthologyBitmask(final boolean dryRun) {
        toLog("SELECT DISTINCT " + DOM_BOOK_ANTHOLOGY_BITMASK +
                " FROM " + TBL_BOOKS + " WHERE " + DOM_BOOK_ANTHOLOGY_BITMASK + " NOT IN (0,1,3)");
    }



    /** int */
    public void bookISFDBid(final boolean dryRun) {
        toLog("SELECT DISTINCT " + DOM_BOOK_ISFDB_ID +
                " FROM " + TBL_BOOKS + " WHERE " + DOM_BOOK_ISFDB_ID + " <> 0");
    }

    /** int */
    public void bookLTid(final boolean dryRun) {
        toLog("SELECT DISTINCT " + DOM_BOOK_LIBRARY_THING_ID +
                " FROM " + TBL_BOOKS + " WHERE " + DOM_BOOK_LIBRARY_THING_ID + " <> 0");
    }

    public void toLog(String sql) {
        DbSync.SynchronizedCursor cursor = mSyncDb.rawQuery(sql, null);
        while (cursor.moveToNext()) {
            String field = cursor.getColumnName(0);
            String value = cursor.getString(0);

            Logger.info(this, field + "=" + value);
        }
        cursor.close();
    }
}
