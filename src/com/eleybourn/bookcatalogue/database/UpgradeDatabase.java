package com.eleybourn.bookcatalogue.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;

import java.io.File;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer;
import com.eleybourn.bookcatalogue.database.dbsync.TransactionException;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import static com.eleybourn.bookcatalogue.database.DAO.COLLATION;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DBDefinitions.TBL_BOOKS;

/**
 * Moved all upgrade specific definitions/methods from {@link DBHelper} here.
 * and removed all pre v4.0.0 upgrades.
 * As 4.0.0 was from 2012, and it's now Mar-2019...
 * <p>
 * This should help reduce memory footprint.
 * <p>
 * **KEEP** the v82 table creation string for reference.
 */
public final class UpgradeDatabase {

    /** obsolete from v74  {@link #v74_fixupAuthorsAndSeries}. */
    public static final String V74_PREF_AUTHOR_SERIES_FIX_UP_REQUIRED =
            "v74.FAuthorSeriesFixupRequired";

    /** Flag to indicate FTS rebuild is required at startup. */
    public static final String PREF_STARTUP_FTS_REBUILD_REQUIRED = "Startup.FtsRebuildRequired";

    //<editor-fold desc="CREATE TABLE definitions">

    /**
     *
     */
    private static final String DATABASE_CREATE_BOOKSHELF_82 =
            "CREATE TABLE bookshelf (_id integer PRIMARY KEY autoincrement,"
                    + " bookshelf text not null"
                    + ')';

    private static final String DATABASE_CREATE_BOOK_LOAN_82 =
            "CREATE TABLE loan (_id integer PRIMARY KEY autoincrement,"
                    // this leaves the link after the book was deleted.
                    + "book integer REFERENCES books ON DELETE SET NULL ON UPDATE SET NULL,"
                    + " loaned_to text not null"
                    + ')';

    private static final String DATABASE_CREATE_BOOK_BOOKSHELF_82 =
            "CREATE TABLE book_bookshelf_weak ("
                    // this leaves the link after the book was deleted.
                    + "book integer REFERENCES books ON DELETE SET NULL ON UPDATE SET NULL,"
                    // this leaves the link after the bookshelf was deleted.
                    + " bookshelf integer REFERENCES bookshelf ON DELETE SET NULL ON UPDATE SET NULL"
                    + ')';

    private static final String DATABASE_CREATE_ANTHOLOGY_82 =
            "CREATE TABLE anthology (_id integer PRIMARY KEY autoincrement,"
                    + "book integer REFERENCES books"
                    + " ON DELETE SET NULL ON UPDATE SET NULL,"
                    + " author integer not null REFERENCES authors,"
                    + " title text not null,"
                    + " toc_entry_position int"
                    + ')';

    private static final String DATABASE_CREATE_AUTHORS_82 =
            "CREATE TABLE authors (_id integer PRIMARY KEY autoincrement,"
                    + " family_name text not null,"
                    + " given_names text not null"
                    + ')';

    private static final String DATABASE_CREATE_SERIES_82 =
            "CREATE TABLE series (_id integer PRIMARY KEY autoincrement,"
                    + " series_name text not null "
                    + ')';

    private static final String DATABASE_CREATE_BOOK_AUTHOR_82 =
            "CREATE TABLE book_author ("
                    + "book integer REFERENCES books ON DELETE CASCADE ON UPDATE CASCADE,"
                    + " author integer REFERENCES authors ON DELETE SET NULL ON UPDATE CASCADE,"
                    + " author_position integer NOT NULL,"
                    + " PRIMARY KEY(book,author_position)"
                    + ')';

    private static final String DATABASE_CREATE_BOOK_SERIES_82 =
            "CREATE TABLE book_series ("
                    + "book integer REFERENCES books ON DELETE CASCADE ON UPDATE CASCADE,"
                    + " series_id integer REFERENCES series ON DELETE SET NULL ON UPDATE CASCADE,"
                    + " series_num text,"
                    + " series_position integer,"
                    + "PRIMARY KEY(book,series_position)"
                    + ')';

    private static final String DATABASE_CREATE_BOOKS_82 =
            "CREATE TABLE books (_id integer PRIMARY KEY autoincrement,"
                    + " title text not null,"
                    + " isbn text,"
                    + " publisher text,"
                    + " date_published date,"
                    + " rating float not null default 0,"
                    + " read boolean not null default 0,"
                    + " pages int,"
                    + " notes text,"
                    + " list_price text,"
                    + " anthology int not null default 0,"
                    + " location text,"
                    + " read_start date,"
                    + " read_end date,"
                    + " format text,"
                    + " signed boolean not null default 0,"
                    + " description text,"
                    + " genre text,"
                    + " language text default '',"
                    + " date_added datetime default current_timestamp,"
                    + " goodreads_book_id int,"
                    + " last_goodreads_sync_date date default '0000-00-00',"
                    + " book_uuid text not null default (lower(hex(randomblob(16)))),"
                    + " last_update_date date not null default current_timestamp"
                    + ')';

    private static final String[] DATABASE_CREATE_INDICES_82 = {
            "CREATE INDEX IF NOT EXISTS authors_given_names ON authors (given_names)",
            "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON authors"
                    + " (given_names" + COLLATION + ')',

            "CREATE INDEX IF NOT EXISTS authors_family_name ON authors (family_name)",
            "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON authors"
                    + " (family_name" + COLLATION + ')',

            "CREATE INDEX IF NOT EXISTS bookshelf_bookshelf ON bookshelf (bookshelf)",

            "CREATE INDEX IF NOT EXISTS books_title ON books (title)",
            "CREATE INDEX IF NOT EXISTS books_title_ci ON books (title" + COLLATION + ')',

            "CREATE INDEX IF NOT EXISTS books_isbn ON books (isbn)",
            "CREATE INDEX IF NOT EXISTS books_publisher ON books (publisher)",
            "CREATE UNIQUE INDEX IF NOT EXISTS books_uuid ON books (book_uuid)",
            "CREATE INDEX IF NOT EXISTS books_gr_book ON books (goodreads_book_id)",

            "CREATE UNIQUE INDEX IF NOT EXISTS series_series ON series (series_name)",

            "CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON loan (book)",

            "CREATE INDEX IF NOT EXISTS anthology_author ON anthology (author)",
            "CREATE INDEX IF NOT EXISTS anthology_title ON anthology (title)",

            "CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON anthology (author, title)",

            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_book ON book_bookshelf_weak (book)",
            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_bookshelf ON book_bookshelf_weak"
                    + " (bookshelf)",

            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_series ON book_series"
                    + " (series_id, book,series_num)",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_book ON book_series"
                    + " (book,series_id,series_num)",

            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_author ON book_author (author, book)",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_book ON book_author (book,author)",
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

    /** Set the flag to indicate an FTS rebuild is required. */
    private static void scheduleFtsRebuild() {
        App.getPrefs().edit().putBoolean(PREF_STARTUP_FTS_REBUILD_REQUIRED, true).apply();
    }

    /**
     * For the upgrade to version 4 (db version 72), all cover files were renamed based on the
     * uuid value in the books table to avoid future collisions.
     * <p>
     * This routine renames all files, if they exist.
     */
    private static void v72_renameIdFilesToHash(@NonNull final SynchronizedDb db) {
        try (Cursor c = db.rawQuery("SELECT _id, book_uuid FROM books ORDER BY _id",
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
    public static void v74_fixupAuthorsAndSeries(@NonNull final DAO db) {
        SynchronizedDb syncDb = db.getUnderlyingDatabase();

        if (syncDb.inTransaction()) {
            throw new TransactionException();
        }

        Synchronizer.SyncLock txLock = syncDb.beginTransaction(true);
        try {
            v74_fixupPositionedBookItems(syncDb, "book_author", "author_position");
            v74_fixupPositionedBookItems(syncDb, "book_series", "series_position");
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
        String sql = "SELECT books._id AS _id,"
                + " min(o." + positionField + ") AS pos"
                + " FROM books JOIN " + tableName + " o ON o.book=books._id"
                + " GROUP BY books._id";

        Synchronizer.SyncLock txLock = null;
        if (!db.inTransaction()) {
            txLock = db.beginTransaction(true);
        }
        // Statement to move records up by a given offset
        String updateSql = "UPDATE " + tableName + " SET " + positionField + "=1"
                + " WHERE book=? AND " + positionField + "=?";

        try (Cursor cursor = db.rawQuery(sql, null);
             SynchronizedStatement moveStmt = db.compileStatement(updateSql)) {
            // Get the column indexes we need
            final int bookCol = cursor.getColumnIndexOrThrow("_id");
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
    static void v200_moveCoversToDedicatedDirectory(@NonNull final SQLiteDatabase db) {

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
                "UPDATE " + table + " SET " + destination + "=? WHERE " + DOM_PK_ID + "=?");

        try (Cursor cur = db.rawQuery("SELECT " + DOM_PK_ID + ',' + source + " FROM " + table,
                                      null)) {
            while (cur.moveToNext()) {
                final long id = cur.getLong(0);
                final String in = cur.getString(1);
                update.bindString(1, DAO.encodeOrderByColumn(in, LocaleUtils.getSystemLocale()));
                update.bindLong(2, id);
                update.executeUpdateDelete();
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
            App.getPrefs()
               .edit().putBoolean(V74_PREF_AUTHOR_SERIES_FIX_UP_REQUIRED, true).apply();
        }
        if (curVersion < 80) {
            curVersion = 80;
            scheduleFtsRebuild();
        }
        if (curVersion < 81) {
            curVersion = 81;
            // Adjust the Book-series table to have a text 'series-num'.
            final String tempName = "books_series_tmp";
            db.execSQL("ALTER TABLE book_series RENAME TO " + tempName);
            db.execSQL(DATABASE_CREATE_BOOK_SERIES_82);
            DBHelper.copyTableSafely(syncedDb, tempName, "book_series");
            db.execSQL("DROP TABLE " + tempName);
        }

        if (curVersion < 82) {
            curVersion = 82;
            // added a language field
            UpgradeDatabase.recreateAndReloadTable(syncedDb, "books",
                                                   DATABASE_CREATE_BOOKS_82);
        }

        return curVersion;
    }

}
