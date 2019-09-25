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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableInfo;

/**
 * Moved all upgrade specific definitions/methods from {@link DBHelper} here.
 * and removed all pre 5.2.2 upgrades.
 * <p>
 * **KEEP** the v82 table creation string for reference.
 */
public final class UpgradeDatabase {

    /** Flag to indicate FTS rebuild is required at startup. */
    public static final String PREF_STARTUP_FTS_REBUILD_REQUIRED = "Startup.FtsRebuildRequired";

    //<editor-fold desc="V83 CREATE TABLE definitions">

//    private static final String DATABASE_CREATE_BOOKSHELF_82 =
//        "CREATE TABLE bookshelf (_id integer PRIMARY KEY autoincrement,"
//        + " bookshelf text not null"
//        + ')';
//
//    private static final String DATABASE_CREATE_BOOK_LOAN_82 =
//        "CREATE TABLE loan (_id integer PRIMARY KEY autoincrement,"
//        // this leaves the link after the book was deleted.
//        + "book integer REFERENCES books ON DELETE SET NULL ON UPDATE SET NULL,"
//        + " loaned_to text not null"
//        + ')';
//
//    private static final String DATABASE_CREATE_BOOK_BOOKSHELF_82 =
//        "CREATE TABLE book_bookshelf_weak ("
//        // this leaves the link after the book was deleted.
//        + "book integer REFERENCES books ON DELETE SET NULL ON UPDATE SET NULL,"
//        // this leaves the link after the bookshelf was deleted.
//        + " bookshelf integer REFERENCES bookshelf ON DELETE SET NULL ON UPDATE SET NULL"
//        + ')';
//
//    private static final String DATABASE_CREATE_ANTHOLOGY_82 =
//        "CREATE TABLE anthology (_id integer PRIMARY KEY autoincrement,"
//        + "book integer REFERENCES books"
//        + " ON DELETE SET NULL ON UPDATE SET NULL,"
//        + " author integer not null REFERENCES authors,"
//        + " title text not null,"
//        + " toc_entry_position int"
//        + ')';
//
//    private static final String DATABASE_CREATE_AUTHORS_82 =
//        "CREATE TABLE authors (_id integer PRIMARY KEY autoincrement,"
//        + " family_name text not null,"
//        + " given_names text not null"
//        + ')';
//
//    private static final String DATABASE_CREATE_SERIES_82 =
//        "CREATE TABLE series (_id integer PRIMARY KEY autoincrement,"
//        + " series_name text not null "
//        + ')';
//
//    private static final String DATABASE_CREATE_BOOK_AUTHOR_82 =
//        "CREATE TABLE book_author ("
//        + "book integer REFERENCES books ON DELETE CASCADE ON UPDATE CASCADE,"
//        + " author integer REFERENCES authors ON DELETE SET NULL ON UPDATE CASCADE,"
//        + " author_position integer NOT NULL,"
//        + " PRIMARY KEY(book,author_position)"
//        + ')';
//
//    private static final String DATABASE_CREATE_BOOK_SERIES_82 =
//        "CREATE TABLE book_series ("
//        + "book integer REFERENCES books ON DELETE CASCADE ON UPDATE CASCADE,"
//        + " series_id integer REFERENCES series ON DELETE SET NULL ON UPDATE CASCADE,"
//        + " series_num text,"
//        + " series_position integer,"
//        + "PRIMARY KEY(book,series_position)"
//        + ')';
//
//    private static final String DATABASE_CREATE_BOOKS_82 =
//        "CREATE TABLE books (_id integer PRIMARY KEY autoincrement,"
//        + " title text not null,"
//        + " isbn text,"
//        + " publisher text,"
//        + " date_published date,"
//        + " rating float not null default 0,"
//        + " read boolean not null default 0,"
//        + " pages int,"
//        + " notes text,"
//        + " list_price text,"
//        + " anthology int not null default 0,"
//        + " location text,"
//        + " read_start date,"
//        + " read_end date,"
//        + " format text,"
//        + " signed boolean not null default 0,"
//        + " description text,"
//        + " genre text,"
//        + " language text default '',"
//        + " date_added datetime default current_timestamp,"
//        + " goodreads_book_id int,"
//        + " last_goodreads_sync_date date default '0000-00-00',"
//        + " book_uuid text not null default (lower(hex(randomblob(16)))),"
//        + " last_update_date date not null default current_timestamp"
//        + ')';
//
//    private static final String[] DATABASE_CREATE_INDICES_82 = {
//        "CREATE INDEX IF NOT EXISTS authors_given_names ON authors (given_names)",
//        "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON authors"
//        + " (given_names" + COLLATION + ')',
//
//        "CREATE INDEX IF NOT EXISTS authors_family_name ON authors (family_name)",
//        "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON authors"
//        + " (family_name" + COLLATION + ')',
//
//        "CREATE INDEX IF NOT EXISTS bookshelf_bookshelf ON bookshelf (bookshelf)",
//
//        "CREATE INDEX IF NOT EXISTS books_title ON books (title)",
//        "CREATE INDEX IF NOT EXISTS books_title_ci ON books (title" + COLLATION + ')',
//
//        "CREATE INDEX IF NOT EXISTS books_isbn ON books (isbn)",
//        "CREATE INDEX IF NOT EXISTS books_publisher ON books (publisher)",
//        "CREATE UNIQUE INDEX IF NOT EXISTS books_uuid ON books (book_uuid)",
//        "CREATE INDEX IF NOT EXISTS books_gr_book ON books (goodreads_book_id)",
//
//        "CREATE UNIQUE INDEX IF NOT EXISTS series_series ON series (series_name)",
//
//        "CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON loan (book)",
//
//        "CREATE INDEX IF NOT EXISTS anthology_author ON anthology (author)",
//        "CREATE INDEX IF NOT EXISTS anthology_title ON anthology (title)",
//
//        "CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON anthology (author, title)",
//
//        "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_book ON book_bookshelf_weak (book)",
//        "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_bookshelf ON book_bookshelf_weak"
//        + " (bookshelf)",
//
//        "CREATE UNIQUE INDEX IF NOT EXISTS book_series_series ON book_series"
//        + " (series_id, book,series_num)",
//        "CREATE UNIQUE INDEX IF NOT EXISTS book_series_book ON book_series"
//        + " (book,series_id,series_num)",
//
//        "CREATE UNIQUE INDEX IF NOT EXISTS book_author_author ON book_author (author, book)",
//        "CREATE UNIQUE INDEX IF NOT EXISTS book_author_book ON book_author (book,author)",
//        };

    //</editor-fold>

    private UpgradeDatabase() {
    }

    /** Set the flag to indicate an FTS rebuild is required. */
    private static void scheduleFtsRebuild() {
        PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                         .edit().putBoolean(PREF_STARTUP_FTS_REBUILD_REQUIRED, true)
                         .apply();
    }

    /**
     * Renames the original table, recreates it, and loads the data into the new table.
     *
     * @param db              Database Access
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
        copyTableSafely(db, tempName, tableName, toRemove);
        db.execSQL("DROP TABLE " + tempName);
    }

    /**
     * Renames the original table, recreates it, and loads the data into the new table.
     *
     * @param db              Database Access
     * @param tableToRecreate the table
     * @param toRemove        (optional) List of fields to be removed from the source table
     */
    static void recreateAndReloadTable(@NonNull final SynchronizedDb db,
                                       @SuppressWarnings("SameParameterValue")
                                       @NonNull final TableDefinition tableToRecreate,
                                       @NonNull final String... toRemove) {
        final String tableName = tableToRecreate.getName();
        final String tempName = "recreate_tmp";
        db.execSQL("ALTER TABLE " + tableName + " RENAME TO " + tempName);
        tableToRecreate.createAll(db);
        // This handles re-ordered fields etc.
        copyTableSafely(db, tempName, tableName, toRemove);
        db.execSQL("DROP TABLE " + tempName);
    }

    /**
     * Provide a safe table copy method that is insulated from risks associated with
     * column reordering. This method will copy all columns from the source to the destination;
     * if columns do not exist in the destination, an error will occur. Columns in the
     * destination that are not in the source will be defaulted or set to {@code null}
     * if no default is defined.
     *
     * @param db          Database Access
     * @param source      from table
     * @param destination to table
     * @param toRemove    (optional) List of fields to be removed from the source table
     *                    (skipped in copy)
     */
    @SuppressWarnings("WeakerAccess")
    static void copyTableSafely(@NonNull final SynchronizedDb db,
                                @SuppressWarnings("SameParameterValue")
                                @NonNull final String source,
                                @NonNull final String destination,
                                @NonNull final String... toRemove) {
        // Get the source info
        TableInfo sourceTable = new TableInfo(db, source);
        // Build the column list
        StringBuilder columns = new StringBuilder();
        boolean first = true;
        for (ColumnInfo ci : sourceTable.getColumns()) {
            boolean isNeeded = true;
            for (String s : toRemove) {
                if (s.equalsIgnoreCase(ci.name)) {
                    isNeeded = false;
                    break;
                }
            }
            if (isNeeded) {
                if (first) {
                    first = false;
                } else {
                    columns.append(',');
                }
                columns.append(ci.name);
            }
        }
        String colList = columns.toString();
        String sql = "INSERT INTO " + destination + '(' + colList + ") SELECT "
                     + colList + " FROM " + source;
        try (SynchronizedStatement stmt = db.compileStatement(sql)) {
            stmt.executeInsert();
        }
    }

    /**
     * Create and populate the 'order by' column.
     * This method is used/meant for use during upgrades.
     * <p>
     * Note this is a lazy approach using the users preferred Locale,
     * as compared to the DAO code where we take the book's language/Locale into account.
     * The overhead here would be huge.
     * If the user has any specific book issue, a simple update of the book will fix it.
     */
    private static void addOrderByColumn(@NonNull final SQLiteDatabase db,
                                         @NonNull final TableDefinition table,
                                         @NonNull final DomainDefinition source,
                                         @NonNull final DomainDefinition destination) {

        db.execSQL("ALTER TABLE " + table + " ADD " + destination + " text not null default ''");

        SQLiteStatement update = db.compileStatement(
                "UPDATE " + table + " SET " + destination + "=?"
                + " WHERE " + DBDefinitions.DOM_PK_ID + "=?");

        try (Cursor cur = db.rawQuery("SELECT " + DBDefinitions.DOM_PK_ID
                                      + ',' + source + " FROM " + table,
                                      null)) {
            while (cur.moveToNext()) {
                final long id = cur.getLong(0);
                final String in = cur.getString(1);
                update.bindString(1, DAO.encodeOrderByColumn(in, Locale.getDefault()));
                update.bindLong(2, id);
                update.executeUpdateDelete();
            }
        }
    }
}
