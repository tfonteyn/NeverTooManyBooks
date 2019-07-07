package com.eleybourn.bookcatalogue.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;

import java.io.File;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Moved all upgrade specific definitions/methods from {@link DBHelper} here.
 * and removed all pre 5.2.2 upgrades.
 * <p>
 * This should help reduce memory footprint.
 * <p>
 * **KEEP** the v82 table creation string for reference.
 */
public final class UpgradeDatabase {

    /** Flag to indicate FTS rebuild is required at startup. */
    public static final String PREF_STARTUP_FTS_REBUILD_REQUIRED = "Startup.FtsRebuildRequired";

    //<editor-fold desc="V83 CREATE TABLE definitions">
//
//    private static final String DATABASE_CREATE_BOOKSHELF_82 =
//            "CREATE TABLE bookshelf (_id integer PRIMARY KEY autoincrement,"
//                    + " bookshelf text not null"
//                    + ')';
//
//    private static final String DATABASE_CREATE_BOOK_LOAN_82 =
//            "CREATE TABLE loan (_id integer PRIMARY KEY autoincrement,"
//                    // this leaves the link after the book was deleted.
//                    + "book integer REFERENCES books ON DELETE SET NULL ON UPDATE SET NULL,"
//                    + " loaned_to text not null"
//                    + ')';
//
//    private static final String DATABASE_CREATE_BOOK_BOOKSHELF_82 =
//            "CREATE TABLE book_bookshelf_weak ("
//                    // this leaves the link after the book was deleted.
//                    + "book integer REFERENCES books ON DELETE SET NULL ON UPDATE SET NULL,"
//                    // this leaves the link after the bookshelf was deleted.
//                    + " bookshelf integer REFERENCES bookshelf ON DELETE SET NULL ON UPDATE SET NULL"
//                    + ')';
//
//    private static final String DATABASE_CREATE_ANTHOLOGY_82 =
//            "CREATE TABLE anthology (_id integer PRIMARY KEY autoincrement,"
//                    + "book integer REFERENCES books"
//                    + " ON DELETE SET NULL ON UPDATE SET NULL,"
//                    + " author integer not null REFERENCES authors,"
//                    + " title text not null,"
//                    + " toc_entry_position int"
//                    + ')';
//
//    private static final String DATABASE_CREATE_AUTHORS_82 =
//            "CREATE TABLE authors (_id integer PRIMARY KEY autoincrement,"
//                    + " family_name text not null,"
//                    + " given_names text not null"
//                    + ')';
//
//    private static final String DATABASE_CREATE_SERIES_82 =
//            "CREATE TABLE series (_id integer PRIMARY KEY autoincrement,"
//                    + " series_name text not null "
//                    + ')';
//
//    private static final String DATABASE_CREATE_BOOK_AUTHOR_82 =
//            "CREATE TABLE book_author ("
//                    + "book integer REFERENCES books ON DELETE CASCADE ON UPDATE CASCADE,"
//                    + " author integer REFERENCES authors ON DELETE SET NULL ON UPDATE CASCADE,"
//                    + " author_position integer NOT NULL,"
//                    + " PRIMARY KEY(book,author_position)"
//                    + ')';
//
//    private static final String DATABASE_CREATE_BOOK_SERIES_82 =
//            "CREATE TABLE book_series ("
//                    + "book integer REFERENCES books ON DELETE CASCADE ON UPDATE CASCADE,"
//                    + " series_id integer REFERENCES series ON DELETE SET NULL ON UPDATE CASCADE,"
//                    + " series_num text,"
//                    + " series_position integer,"
//                    + "PRIMARY KEY(book,series_position)"
//                    + ')';
//
//    private static final String DATABASE_CREATE_BOOKS_82 =
//            "CREATE TABLE books (_id integer PRIMARY KEY autoincrement,"
//                    + " title text not null,"
//                    + " isbn text,"
//                    + " publisher text,"
//                    + " date_published date,"
//                    + " rating float not null default 0,"
//                    + " read boolean not null default 0,"
//                    + " pages int,"
//                    + " notes text,"
//                    + " list_price text,"
//                    + " anthology int not null default 0,"
//                    + " location text,"
//                    + " read_start date,"
//                    + " read_end date,"
//                    + " format text,"
//                    + " signed boolean not null default 0,"
//                    + " description text,"
//                    + " genre text,"
//                    + " language text default '',"
//                    + " date_added datetime default current_timestamp,"
//                    + " goodreads_book_id int,"
//                    + " last_goodreads_sync_date date default '0000-00-00',"
//                    + " book_uuid text not null default (lower(hex(randomblob(16)))),"
//                    + " last_update_date date not null default current_timestamp"
//                    + ')';
//
//    private static final String[] DATABASE_CREATE_INDICES_82 = {
//            "CREATE INDEX IF NOT EXISTS authors_given_names ON authors (given_names)",
//            "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON authors"
//                    + " (given_names" + COLLATION + ')',
//
//            "CREATE INDEX IF NOT EXISTS authors_family_name ON authors (family_name)",
//            "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON authors"
//                    + " (family_name" + COLLATION + ')',
//
//            "CREATE INDEX IF NOT EXISTS bookshelf_bookshelf ON bookshelf (bookshelf)",
//
//            "CREATE INDEX IF NOT EXISTS books_title ON books (title)",
//            "CREATE INDEX IF NOT EXISTS books_title_ci ON books (title" + COLLATION + ')',
//
//            "CREATE INDEX IF NOT EXISTS books_isbn ON books (isbn)",
//            "CREATE INDEX IF NOT EXISTS books_publisher ON books (publisher)",
//            "CREATE UNIQUE INDEX IF NOT EXISTS books_uuid ON books (book_uuid)",
//            "CREATE INDEX IF NOT EXISTS books_gr_book ON books (goodreads_book_id)",
//
//            "CREATE UNIQUE INDEX IF NOT EXISTS series_series ON series (series_name)",
//
//            "CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON loan (book)",
//
//            "CREATE INDEX IF NOT EXISTS anthology_author ON anthology (author)",
//            "CREATE INDEX IF NOT EXISTS anthology_title ON anthology (title)",
//
//            "CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON anthology (author, title)",
//
//            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_book ON book_bookshelf_weak (book)",
//            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_bookshelf ON book_bookshelf_weak"
//                    + " (bookshelf)",
//
//            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_series ON book_series"
//                    + " (series_id, book,series_num)",
//            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_book ON book_series"
//                    + " (book,series_id,series_num)",
//
//            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_author ON book_author (author, book)",
//            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_book ON book_author (book,author)",
//            };

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

    /** Set the flag to indicate an FTS rebuild is required. */
    private static void scheduleFtsRebuild() {
        App.getPrefs().edit().putBoolean(PREF_STARTUP_FTS_REBUILD_REQUIRED, true).apply();
    }

    /**
     * For the upgrade to version 200, all cover files were moved to a sub directory.
     * <p>
     * This routine renames all files, if they exist.
     */
    static void v200_moveCoversToDedicatedDirectory(@NonNull final SQLiteDatabase db) {

        try (Cursor cur = db.rawQuery("SELECT " + DBDefinitions.DOM_BOOK_UUID
                                              + " FROM " + DBDefinitions.TBL_BOOKS,
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

    /**
     * Populate the 'order by' column.
     * <p>
     * Note this is a lazy approach using the system Locale, as compared to the DAO code where
     * we take the book's language/locale into account! The overhead here would be huge.
     * If the user has any specific book issue, a simple update of the book will fix it.
     */
    static void v200_setOrderByColumn(@NonNull final SQLiteDatabase db,
                                      @NonNull final TableDefinition table,
                                      @NonNull final DomainDefinition source,
                                      @NonNull final DomainDefinition destination) {

        SQLiteStatement update = db.compileStatement(
                "UPDATE " + table + " SET " + destination + "=?"
                        + " WHERE " + DBDefinitions.DOM_PK_ID + "=?");

        try (Cursor cur = db.rawQuery("SELECT " + DBDefinitions.DOM_PK_ID + ',' + source + " FROM " + table,
                                      null)) {
            while (cur.moveToNext()) {
                final long id = cur.getLong(0);
                final String in = cur.getString(1);
                update.bindString(1, DAO.encodeOrderByColumn(in, LocaleUtils.getPreferredLocal()));
                update.bindLong(2, id);
                update.executeUpdateDelete();
            }
        }
    }
}
