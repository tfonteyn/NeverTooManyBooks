package com.eleybourn.bookcatalogue.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;

import static com.eleybourn.bookcatalogue.database.DBA.COLLATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_TOC_ENTRY_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_TOC_ENTRY_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANEE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_LOANEE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_TOC_ENTRIES;

/**
 * Moved all upgrade specific definitions/methods from {@link DBHelper} here.
 * and removed all pre v4.0.0 upgrades.
 * As 4.0.0 was from 2012, and it's now Jan-2019...
 *
 * This should help reduce memory footprint.
 *
 * **KEEP** the v82 table creation string for reference.
 */
public final class UpgradeDatabase {

    /** obsolete from v74  {@link #v74_fixupAuthorsAndSeries}. */
    public static final String V74_PREF_AUTHOR_SERIES_FIX_UP_REQUIRED =
            "v74.FAuthorSeriesFixupRequired";

    //<editor-fold desc="CREATE TABLE definitions">

    /** */
    private static final String DATABASE_CREATE_BOOKSHELF_82 =
            "CREATE table " + TBL_BOOKSHELF + " (_id integer primary key autoincrement,"
                    + DOM_BOOKSHELF + " text not null "
                    + ')';

    private static final String DATABASE_CREATE_BOOK_LOAN_82 =
            "CREATE TABLE " + TBL_BOOK_LOANEE + " (_id integer primary key autoincrement,"
                    // this leaves the link after the book was deleted.  why ?
                    + DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS
                    + " ON DELETE SET NULL ON UPDATE SET NULL,"
                    + DOM_LOANEE + " text not null"
                    + ')';

    private static final String DATABASE_CREATE_BOOK_BOOKSHELF_82 =
            "CREATE TABLE " + TBL_BOOK_BOOKSHELF + '('
                    // this leaves the link after the book was deleted.  why ?
                    + DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS
                    + " ON DELETE SET NULL ON UPDATE SET NULL,"
                    // this leaves the link after the bookshelf was deleted. why ?
                    + DOM_FK_BOOKSHELF_ID + " integer REFERENCES " + TBL_BOOKSHELF
                    + " ON DELETE SET NULL ON UPDATE SET NULL"
                    + ')';

    private static final String DATABASE_CREATE_ANTHOLOGY_82 =
            "CREATE TABLE " + TBL_TOC_ENTRIES + " (_id integer primary key autoincrement,"
                    + DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS
                    + " ON DELETE SET NULL ON UPDATE SET NULL,"
                    + DOM_FK_AUTHOR_ID + " integer not null REFERENCES " + TBL_AUTHORS + ','
                    + DOM_TITLE + " text not null,"
                    + DOM_BOOK_TOC_ENTRY_POSITION + " int"
                    + ')';

    private static final String DATABASE_CREATE_AUTHORS_82 =
            "CREATE table " + TBL_AUTHORS + " (_id integer primary key autoincrement,"
                    + DOM_AUTHOR_FAMILY_NAME + " text not null,"
                    + DOM_AUTHOR_GIVEN_NAMES + " text not null"
                    + ')';

    private static final String DATABASE_CREATE_SERIES_82 =
            "CREATE TABLE " + TBL_SERIES + " (_id integer primary key autoincrement,"
                    + DOM_SERIES_NAME + " text not null "
                    + ')';

    private static final String DATABASE_CREATE_BOOK_AUTHOR_82 =
            "CREATE TABLE " + TBL_BOOK_AUTHOR + '('
                    + DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS
                    + " ON DELETE CASCADE ON UPDATE CASCADE,"
                    + DOM_FK_AUTHOR_ID + " integer REFERENCES " + TBL_AUTHORS
                    + " ON DELETE SET NULL ON UPDATE CASCADE,"
                    + DOM_BOOK_AUTHOR_POSITION + " integer NOT NULL,"
                    + ',' + "PRIMARY KEY(" + DOM_FK_BOOK_ID + ',' + DOM_BOOK_AUTHOR_POSITION + ')'
                    + ')';

    private static final String DATABASE_CREATE_BOOK_SERIES_82 =
            "CREATE TABLE " + TBL_BOOK_SERIES + '('
                    + DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS
                    + " ON DELETE CASCADE ON UPDATE CASCADE,"
                    + DOM_FK_SERIES_ID + " integer REFERENCES " + TBL_SERIES
                    + " ON DELETE SET NULL ON UPDATE CASCADE,"
                    + DOM_BOOK_SERIES_NUM + " text,"
                    + DOM_BOOK_SERIES_POSITION + " integer,"
                    + "PRIMARY KEY(" + DOM_FK_BOOK_ID + ',' + DOM_BOOK_SERIES_POSITION + ')'
                    + ')';

    private static final String DATABASE_CREATE_BOOKS_82 =
            "CREATE TABLE " + TBL_BOOKS + " (_id integer primary key autoincrement,"
                    + DOM_TITLE + " text not null,"
                    + DOM_BOOK_ISBN + " text,"
                    + DOM_BOOK_PUBLISHER + " text,"
                    + DOM_BOOK_DATE_PUBLISHED + " date,"
                    + DOM_BOOK_RATING + " float not null default 0,"
                    + DOM_BOOK_READ + " boolean not null default 0,"
                    + DOM_BOOK_PAGES + " int,"
                    + DOM_BOOK_NOTES + " text,"
                    + DOM_BOOK_PRICE_LISTED + " text,"
                    + DOM_BOOK_ANTHOLOGY_BITMASK + " int not null default 0,"
                    + DOM_BOOK_LOCATION + " text,"
                    + DOM_BOOK_READ_START + " date,"
                    + DOM_BOOK_READ_END + " date,"
                    + DOM_BOOK_FORMAT + " text,"
                    + DOM_BOOK_SIGNED + " boolean not null default 0,"
                    + DOM_BOOK_DESCRIPTION + " text,"
                    + DOM_BOOK_GENRE + " text,"
                    + DOM_BOOK_LANGUAGE + " text default '',"
                    + DOM_BOOK_DATE_ADDED + " datetime default current_timestamp,"
                    + DOM_BOOK_GOODREADS_BOOK_ID + " int,"
                    + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + " date default '0000-00-00',"
                    + DOM_BOOK_UUID + " text not null default (lower(hex(randomblob(16)))),"
                    + DOM_LAST_UPDATE_DATE + " date not null default current_timestamp"
                    + ')';

    private static final String[] DATABASE_CREATE_INDICES_82 = {
            "CREATE INDEX IF NOT EXISTS authors_given_names ON " + TBL_AUTHORS +
                    " (" + DOM_AUTHOR_GIVEN_NAMES + ')',
            "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON " + TBL_AUTHORS +
                    " (" + DOM_AUTHOR_GIVEN_NAMES + ' ' + COLLATION + ')',

            "CREATE INDEX IF NOT EXISTS authors_family_name ON " + TBL_AUTHORS +
                    " (" + DOM_AUTHOR_FAMILY_NAME + ')',
            "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON " + TBL_AUTHORS +
                    " (" + DOM_AUTHOR_FAMILY_NAME + ' ' + COLLATION + ')',

            "CREATE INDEX IF NOT EXISTS bookshelf_bookshelf ON " + TBL_BOOKSHELF +
                    " (" + DOM_BOOKSHELF + ')',

            "CREATE INDEX IF NOT EXISTS books_title ON " + TBL_BOOKS +
                    " (" + DOM_TITLE + ')',
            "CREATE INDEX IF NOT EXISTS books_title_ci ON " + TBL_BOOKS +
                    " (" + DOM_TITLE + ' ' + COLLATION + ')',

            "CREATE INDEX IF NOT EXISTS books_isbn ON " + TBL_BOOKS +
                    " (" + DOM_BOOK_ISBN + ')',
            "CREATE INDEX IF NOT EXISTS books_publisher ON " + TBL_BOOKS +
                    " (" + DOM_BOOK_PUBLISHER + ')',
            "CREATE UNIQUE INDEX IF NOT EXISTS books_uuid ON " + TBL_BOOKS +
                    " (" + DOM_BOOK_UUID + ')',
            "CREATE INDEX IF NOT EXISTS books_gr_book ON " + TBL_BOOKS +
                    " (" + DOM_BOOK_GOODREADS_BOOK_ID + ')',

            "CREATE UNIQUE INDEX IF NOT EXISTS series_series ON " + TBL_SERIES +
                    " (" + DOM_PK_ID + ')',

            "CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON " + TBL_BOOK_LOANEE +
                    " (" + DOM_FK_BOOK_ID + ')',

            "CREATE INDEX IF NOT EXISTS anthology_author ON " + TBL_TOC_ENTRIES +
                    " (" + DOM_FK_AUTHOR_ID + ')',
            "CREATE INDEX IF NOT EXISTS anthology_title ON " + TBL_TOC_ENTRIES +
                    " (" + DOM_TITLE + ')',

            "CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + TBL_TOC_ENTRIES +
                    " (" + DOM_FK_AUTHOR_ID + ',' + DOM_TITLE + ')',

            "CREATE INDEX IF NOT EXISTS book_anthology_anthology ON " + TBL_BOOK_TOC_ENTRIES +
                    " (" + DOM_FK_TOC_ENTRY_ID + ')',
            "CREATE INDEX IF NOT EXISTS book_anthology_book ON " + TBL_BOOK_TOC_ENTRIES + ' ' +
                    '(' + DOM_FK_BOOK_ID + ')',

            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_book ON " + TBL_BOOK_BOOKSHELF +
                    " (" + DOM_FK_BOOK_ID + ')',
            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_bookshelf ON " + TBL_BOOK_BOOKSHELF +
                    " (" + DOM_FK_BOOKSHELF_ID + ')',

            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_series ON " + TBL_BOOK_SERIES +
                    " (" + DOM_FK_SERIES_ID + ',' + DOM_FK_BOOK_ID + ',' + DOM_BOOK_SERIES_NUM + ')',
            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_book ON " + TBL_BOOK_SERIES +
                    " (" + DOM_FK_BOOK_ID + ',' + DOM_FK_SERIES_ID + ',' + DOM_BOOK_SERIES_NUM + ')',

            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_author ON " + TBL_BOOK_AUTHOR +
                    " (" + DOM_FK_AUTHOR_ID + ',' + DOM_FK_BOOK_ID + ')',
            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_book ON " + TBL_BOOK_AUTHOR +
                    " (" + DOM_FK_BOOK_ID + ',' + DOM_FK_AUTHOR_ID + ')',
            };

    //</editor-fold>

    private UpgradeDatabase() {
    }

    /**
     * Renames the original table, recreates it, and loads the data into the new table.
     *
     * @param db              the database
     * @param tableName       the table
     * @param createStatement sql to recreate the table
     * @param toRemove        (optional) List of fields to be removed from the source table
     */
    private static void recreateAndReloadTable(@NonNull final SynchronizedDb db,
                                               @NonNull final String tableName,
                                               @NonNull final String createStatement,
                                               @NonNull final String... toRemove) {
        final String tempName = "recreate_tmp";
        db.execSQL("ALTER TABLE " + tableName + " RENAME TO " + tempName);
        db.execSQL(createStatement);
        // This handles re-ordered fields etc.
        DBHelper.copyTableSafely(db, tempName, tableName, toRemove);
        db.execSQL("DROP TABLE " + tempName);
    }

    /**
     * For the upgrade to version 4 (db version 72), all cover files were renamed based on the
     * uuid value in the books table to avoid future collisions.
     * <p>
     * This routine renames all files, if they exist.
     */
    private static void v72_renameIdFilesToHash(@NonNull final SynchronizedDb db) {
        try (Cursor c = db.rawQuery("SELECT " + DOM_PK_ID + ',' + DOM_BOOK_UUID
                                            + " FROM " + TBL_BOOKS + " ORDER BY " + DOM_PK_ID,
                                    null)) {
            while (c.moveToNext()) {
                final long id = c.getLong(0);
                final String uuid = c.getString(1);
                File source = StorageUtils.getCoverFile(String.valueOf(id));
                File destination = StorageUtils.getCoverFile(uuid);
                StorageUtils.renameFile(source, destination);
            }
        }
    }

    /**
     * Data cleanup routine called on upgrade to v4.0.3 to cleanup data integrity issues cased
     * by earlier merge code that could have left the first author or series for a book having
     * a position number > 1.
     */
    public static void v74_fixupAuthorsAndSeries(@NonNull final DBA db) {
        SynchronizedDb syncDb = db.getUnderlyingDatabase();

        if (syncDb.inTransaction()) {
            throw new DBExceptions.TransactionException();
        }

        Synchronizer.SyncLock txLock = syncDb.beginTransaction(true);
        try {
            v74_fixupPositionedBookItems(syncDb, TBL_BOOK_AUTHOR.getName(),
                                         DOM_BOOK_AUTHOR_POSITION.name);
            v74_fixupPositionedBookItems(syncDb, TBL_BOOK_SERIES.getName(),
                                         DOM_BOOK_SERIES_POSITION.name);
            syncDb.setTransactionSuccessful();
        } finally {
            syncDb.endTransaction(txLock);
        }

    }

    /**
     * Data cleaning routine for upgrade to version 4.0.3 to cleanup any books that
     * have no primary author/series.
     */
    private static void v74_fixupPositionedBookItems(@NonNull final SynchronizedDb db,
                                                     @NonNull final String tableName,
                                                     @NonNull final String positionField) {
        String sql = "SELECT " + TBL_BOOKS.dotAs(DOM_PK_ID) + ','
                + " min(o." + positionField + ") AS pos"
                + " FROM " + TBL_BOOKS.ref() + " JOIN " + tableName + " o"
                + " ON o." + DOM_FK_BOOK_ID + '=' + TBL_BOOKS.dot(DOM_PK_ID)
                + " GROUP BY " + TBL_BOOKS.dot(DOM_PK_ID);

        Synchronizer.SyncLock txLock = null;
        if (!db.inTransaction()) {
            txLock = db.beginTransaction(true);
        }
        // Statement to move records up by a given offset
        String updateSql = "UPDATE " + tableName + " SET " + positionField + "=1"
                + " WHERE " + DOM_FK_BOOK_ID + "=? AND " + positionField + "=?";

        try (Cursor cursor = db.rawQuery(sql, null);
             SynchronizedStatement moveStmt = db.compileStatement(updateSql)) {
            // Get the column indexes we need
            final int bookCol = cursor.getColumnIndexOrThrow(DOM_PK_ID.name);
            final int posCol = cursor.getColumnIndexOrThrow("pos");


            // Loop through all instances of the old author appearing
            while (cursor.moveToNext()) {
                // Get the details
                long pos = cursor.getLong(posCol);
                if (pos > 1) {
                    long book = cursor.getLong(bookCol);
                    // Move subsequent records up by one
                    moveStmt.bindLong(1, book);
                    moveStmt.bindLong(2, pos);
                    moveStmt.executeUpdateDelete();
                }
            }
            if (txLock != null) {
                db.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                db.endTransaction(txLock);
            }
        }
    }

    /**
     * For the upgrade to version 200, all cover files were moved to a sub directory.
     * <p>
     * This routine renames all files, if they exist.
     */
    static void v200_moveCoversToDedicatedDirectory(@NonNull final SynchronizedDb db) {

        try (Cursor cur = db.rawQuery("SELECT " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS,
                                      null)) {
            while (cur.moveToNext()) {
                final String uuid = cur.getString(0);
                File source = StorageUtils.getFile(uuid + ".jpg");
                if (!source.exists()) {
                    source = StorageUtils.getFile(uuid + ".png");
                    if (!source.exists()) {
                        continue;
                    }
                }
                File destination = StorageUtils.getCoverFile(uuid);
                StorageUtils.renameFile(source, destination);
            }
        }
    }

    static int doUpgrade(@NonNull final SQLiteDatabase db,
                         @NonNull final SynchronizedDb syncedDb,
                         final int oldVersion) {
        int curVersion = oldVersion;

        if (curVersion < 72) {
            // release 4.0.0 was in early 2012...
            curVersion = 72;
            v72_renameIdFilesToHash(syncedDb);
        }
        if (curVersion < 75) {
            curVersion = 75;
            Prefs.getPrefs()
                 .edit().putBoolean(V74_PREF_AUTHOR_SERIES_FIX_UP_REQUIRED, true).apply();
        }
        if (curVersion < 80) {
            curVersion = 80;
            StartupActivity.scheduleFtsRebuild();
        }
        if (curVersion < 81) {
            curVersion = 81;
            // Adjust the Book-series table to have a text 'series-num'.
            final String tempName = "books_series_tmp";
            db.execSQL("ALTER TABLE " + TBL_BOOK_SERIES + " RENAME TO " + tempName);
            db.execSQL(DATABASE_CREATE_BOOK_SERIES_82);
            DBHelper.copyTableSafely(syncedDb, tempName, TBL_BOOK_SERIES.getName());
            db.execSQL("DROP TABLE " + tempName);
        }

        if (curVersion < 82) {
            curVersion = 82;
            // added a language field
            UpgradeDatabase.recreateAndReloadTable(syncedDb, TBL_BOOKS.getName(),
                                                   DATABASE_CREATE_BOOKS_82);
        }

        return curVersion;
    }
}
