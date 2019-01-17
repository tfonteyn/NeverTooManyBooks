package com.eleybourn.bookcatalogue.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedStatement;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer;
import com.eleybourn.bookcatalogue.database.definitions.ColumnInfo;
import com.eleybourn.bookcatalogue.database.definitions.TableDefinition;
import com.eleybourn.bookcatalogue.database.definitions.TableInfo;
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
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_LOAN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_TOC_ENTRIES;

/**
 * Moved all upgrade specific definitions/methods from {@link DBHelper} here.
 * This should help reduce memory footprint
 * (well, we can hope.. but at least editing the former is easier now)
 */
public final class UpgradeDatabase {

    /** obsolete from v74  {@link #v74_fixupAuthorsAndSeries}. */
    public static final String V74_PREF_AUTHOR_SERIES_FIX_UP_REQUIRED =
            "v74.FAuthorSeriesFixupRequired";

    // column names from 'old' versions, used to upgrade
    private static final String OLD_KEY_AUDIOBOOK = "audiobook";
    private static final String OLD_KEY_AUTHOR = "author";
    private static final String OLD_KEY_SERIES = "series";

    //<editor-fold desc="CREATE TABLE definitions">

    // Renamed to the ****LAST*** version in which it was used

    /** */
    private static final String DATABASE_CREATE_BOOKSHELF_82 =
            "CREATE table " + TBL_BOOKSHELF + " (_id integer primary key autoincrement,"
                    + DOM_BOOKSHELF + " text not null "
                    + ')';

    private static final String DATABASE_CREATE_BOOK_LOAN_82 =
            "CREATE TABLE " + TBL_LOAN + " (_id integer primary key autoincrement,"
                    // this leaves the link after the book was deleted.  why ?
                    + DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS
                    + " ON DELETE SET NULL ON UPDATE SET NULL,"
                    + DOM_LOANED_TO + " text not null"
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

    private static final String DATABASE_CREATE_BOOKS_81 =
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
                    + DOM_BOOK_DATE_ADDED + " datetime default current_timestamp,"
                    + DOM_BOOK_GOODREADS_BOOK_ID + " int,"
                    + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + " date default '0000-00-00',"
                    + DOM_BOOK_UUID + " text not null default (lower(hex(randomblob(16)))),"
                    + DOM_LAST_UPDATE_DATE + " date not null default current_timestamp"
                    + ')';

    private static final String DATABASE_CREATE_BOOKS_68 =
            "CREATE TABLE " + TBL_BOOKS
                    + " (_id integer primary key autoincrement,"
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
                    + DOM_BOOK_DATE_ADDED + " datetime default current_timestamp"
                    + ')';

    private static final String DATABASE_CREATE_BOOKS_63 =
            "CREATE TABLE " + TBL_BOOKS
                    + " (_id integer primary key autoincrement,"
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
                    + DOM_BOOK_GENRE + " text "
                    + ')';

    private static final String DATABASE_CREATE_BOOK_SERIES_54 =
            "CREATE TABLE " + TBL_BOOK_SERIES + '('
                    + DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS
                    + " ON DELETE CASCADE ON UPDATE CASCADE,"
                    + DOM_FK_SERIES_ID + " integer REFERENCES " + TBL_SERIES
                    + " ON DELETE SET NULL ON UPDATE CASCADE,"
                    + DOM_BOOK_SERIES_NUM + " integer,"
                    + DOM_BOOK_SERIES_POSITION + " integer,"
                    + "PRIMARY KEY(" + DOM_FK_BOOK_ID + ',' + DOM_BOOK_SERIES_POSITION + ')'
                    + ')';

    private static final String DATABASE_CREATE_BOOKS_41 =
            "CREATE TABLE " + TBL_BOOKS
                    + " (_id integer primary key autoincrement,"
                    + DOM_FK_AUTHOR_ID + " integer not null REFERENCES " + TBL_AUTHORS + ','
                    + DOM_TITLE + " text not null,"
                    + DOM_BOOK_ISBN + " text,"
                    + DOM_BOOK_PUBLISHER + " text,"
                    + DOM_BOOK_DATE_PUBLISHED + " date,"
                    + DOM_BOOK_RATING + " float not null default 0,"
                    + DOM_BOOK_READ + " boolean not null default 'f',"
                    + OLD_KEY_SERIES + " text,"
                    + DOM_BOOK_PAGES + " int,"
                    + DOM_BOOK_SERIES_NUM + " text,"
                    + DOM_BOOK_NOTES + " text,"
                    + DOM_BOOK_PRICE_LISTED + " text,"
                    + DOM_BOOK_ANTHOLOGY_BITMASK + " int not null default 0,"
                    + DOM_BOOK_LOCATION + " text,"
                    + DOM_BOOK_READ_START + " date,"
                    + DOM_BOOK_READ_END + " date,"
                    + OLD_KEY_AUDIOBOOK + " boolean not null default 'f',"
                    + DOM_BOOK_SIGNED + " boolean not null default 'f' "
                    + ')';
    //</editor-fold>

    @NonNull
    private static String mMessage = "";

    private UpgradeDatabase() {
    }

    @NonNull
    public static String getMessage() {
        return mMessage;
    }

    /**
     * Provide a safe table copy method that is insulated from risks associated with
     * column reordering. This method will copy ALL columns from the source to the destination;
     * if columns do not exist in the destination, an error will occur. Columns in the
     * destination that are not in the source will be defaulted or set to NULL if no default
     * is defined.
     *
     * @param sdb      the database
     * @param from     from table
     * @param to       to table
     * @param toRemove List of fields to be removed from the source table (ignored in copy)
     */
    private static void copyTableSafely(@NonNull final SynchronizedDb sdb,
                                        @SuppressWarnings("SameParameterValue") @NonNull final String from,
                                        @NonNull final String to,
                                        @NonNull final String... toRemove) {
        // Get the source info
        TableInfo src = new TableInfo(sdb, from);
        // Build the column list
        StringBuilder cols = new StringBuilder();
        boolean first = true;
        for (ColumnInfo ci : src) {
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
                    cols.append(',');
                }
                cols.append(ci.name);
            }
        }
        String colList = cols.toString();
        String sql = "INSERT INTO " + to + '(' + colList + ") SELECT " + colList + " FROM " + from;
        sdb.execSQL(sql);
    }

    static void recreateAndReloadTable(@NonNull final SynchronizedDb db,
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
        SynchronizedDb syncDb = db.getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing();

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

        if (curVersion == 11) {
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_SERIES_NUM + " text");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SERIES_NUM + "=''");
            curVersion++;
        }
        if (curVersion == 12) {
            //do nothing except increment
            curVersion++;
        }
        if (curVersion == 13) {
            //do nothing except increment
            curVersion++;
        }
        if (curVersion == 14) {
            //do nothing except increment
            curVersion++;
        }
        if (curVersion == 15) {
            //do nothing except increment
            curVersion++;
        }
        if (curVersion == 16) {
            //do nothing except increment
            curVersion++;
            mMessage = mMessage
                    + "* This message will now appear whenever you upgrade\n\n"
                    + "* Various SQL bugs have been resolved\n\n";
        }
        if (curVersion == 17) {
            //do nothing except increment
            curVersion++;
        }
        if (curVersion == 18) {
            //do nothing except increment
            curVersion++;
        }
        if (curVersion == 19) {
            curVersion++;
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_NOTES + " text");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_NOTES + " = ''");
        }
        if (curVersion == 20) {
            curVersion++;
            db.execSQL(DATABASE_CREATE_BOOK_LOAN_82);
        }
        if (curVersion == 21) {
            //do nothing
            curVersion++;
        }
        if (curVersion == 22) {
            curVersion++;
            mMessage = mMessage
                    + "* There is a new tabbed 'edit' interface to simplify editing books.\n\n"
                    + "* The new comments tab also includes a notes field where you can add "
                    + "personal notes for any book (Requested by Luke).\n\n"
                    + "* The new loaned books tab allows you to record books loaned to"
                    + " friends. This will lookup your phone contacts to pre-populate the list "
                    + "(Requested by Luke)\n\n"
                    + "* Scanned books that already exist in the database (based on ISBN)"
                    + " will no longer be added (Identified by Colin)\n\n"
                    + "* After adding a book, the main view will now scroll to a appropriate"
                    + " location. \n\n"
                    + "* Searching has been made significantly faster.\n\n";
        }
        if (curVersion == 23) {
            //do nothing
            curVersion++;
        }
        if (curVersion == 24) {
            curVersion++;
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_NOTES + " text");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_NOTES + "=''");
            db.execSQL(DATABASE_CREATE_BOOK_LOAN_82);

        }
        if (curVersion == 25) {
            curVersion++;
            mMessage = mMessage
                    + "* Your sort order will be automatically saved when closing the"
                    + " application (Requested by Martin)\n\n"
                    + "* There is a new 'about this app' view available from the"
                    + " administration tabs (Also from Martin)\n\n";
        }
        if (curVersion == 26) {
            curVersion++;
            mMessage = mMessage
                    + "* There are two additional sort functions, by series and by loaned"
                    + " (Request from N4ppy)\n\n"
                    + "* Your bookshelf and current location will be saved when you exit"
                    + " (Feedback from Martin)\n\n"
                    + "* Minor interface improvements when sorting by title \n\n";
        }
        if (curVersion == 27) {
            curVersion++;
            mMessage = mMessage
                    + "* The book thumbnail now appears in the list view\n\n"
                    + "* Emailing the developer now works from the admin page\n\n"
                    + "* The Change Bookshelf option is now more obvious (Thanks Mike)\n\n"
                    + "* The exports have been renamed to csv, use the correct published"
                    + " date and are now unicode safe (Thanks Mike)\n\n";
        }
        if (curVersion == 28) {
            curVersion++;
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_PRICE_LISTED + " text");

        }
        if (curVersion == 29) {
            curVersion++;
            mMessage = mMessage
                    + "* Adding books will now (finally) search Amazon\n\n"
                    + "* A field for list price has been included (Requested by Brenda)\n\n"
                    + "* You can bulk update the thumbnails for all books with ISBN's from"
                    + " the Admin page\n\n";
        }
        if (curVersion == 30) {
            curVersion++;
            mMessage = mMessage
                    + "* You can now delete individual thumbnails by holding on the image and"
                    + " selecting delete.\n\n";
        }
        if (curVersion == 31) {
            curVersion++;
            mMessage = mMessage
                    + "* There is a new Admin option (Field Visibility) to hide unused fields\n\n"
                    + "* 'All Books' should now be saved as a bookshelf preference correctly\n\n"
                    + "* When adding books the bookshelf will default to your currently"
                    + " selected bookshelf (Thanks Martin)\n\n";
        }
        if (curVersion == 32) {
            curVersion++;
            db.execSQL(DATABASE_CREATE_ANTHOLOGY_82);
            db.execSQL("ALTER TABLE " + TBL_BOOKS
                               + " ADD " + DOM_BOOK_ANTHOLOGY_BITMASK + " int not null default 0");

            mMessage = mMessage
                    + "* There is now support to record books as anthologies and it's"
                    + " titles.\n\n"
                    + "* There is experimental support to automatically populate the"
                    + " anthology titles \n\n"
                    + "* You can now take photos for the book cover (long click on the"
                    + " thumbnail in edit) \n\n";
        }
        if (curVersion == 33) {
            curVersion++;
            mMessage = mMessage
                    + "* Minor enhancements\n\n"
                    + "* Online help has been written\n\n"
                    + "* Thumbnails can now be hidden just like any other field"
                    + " (Thanks Martin)\n\n"
                    + "* You can also rotate thumbnails; useful for thumbnails taken with"
                    + " the camera\n\n"
                    + "* Bookshelves will appear in the menu immediately"
                    + " (Thanks Martin/Julia)\n\n";
        }
        if (curVersion == 34) {
            curVersion++;
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_LOCATION + " text");
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_READ_START + " date");
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_READ_END + " date");
            db.execSQL("ALTER TABLE " + TBL_BOOKS
                               + " ADD " + OLD_KEY_AUDIOBOOK + " boolean not null default 'f'");
            db.execSQL("ALTER TABLE " + TBL_BOOKS
                               + " ADD " + DOM_BOOK_SIGNED + " boolean not null default 'f'");

        }
        if (curVersion == 35) {
            curVersion++;
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_LOCATION + "=''");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ_START + "=''");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ_END + "=''");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + OLD_KEY_AUDIOBOOK + "='f'");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SIGNED + "='f'");
        }
        if (curVersion == 36) {
            curVersion++;
            mMessage = mMessage
                    + "* Fixed several crashing defects when adding books\n\n"
                    + "* Added Autocompletion Location Field (For Cam)\n\n"
                    + "* Added Read Start & Read End Fields (For Robert)\n\n"
                    + "* Added an Audio Book Checkbox Field (For Ted)\n\n"
                    + "* Added a Book Signed Checkbox Field (For me)\n\n"
                    + "*** Don't forget you can hide any of the new fields that you"
                    + " do not want to see.\n\n"
                    + "* Series Number now support decimal figures (Requested by Beth)\n\n"
                    + "* List price now support decimal figures (Requested by eleavings)\n\n"
                    + "* Fixed Import Crashes (Thanks Roydalg)\n\n"
                    + "* Fixed several defects for Android 1.6 users - I do not have a 1.6"
                    + " device to test on so please let me know if you discover any errors\n\n";
        }
        if (curVersion == 37) {
            curVersion++;
            mMessage = mMessage
                    + "Tip: If you long click on a book title on the main list you can"
                    + " delete it\n\n"
                    + "Tip: If you want to see all books, change the bookshelf to"
                    + " 'All Books'\n\n"
                    + "Tip: You can find the correct barcode for many modern paperbacks on"
                    + " the inside cover\n\n"
                    + "* There is now a 'Sort by Unread' option, as well as a 'read' icon"
                    + " on the main list (requested by Angel)\n\n"
                    + "* If you long click on the (?) thumbnail you can now select a new"
                    + " thumbnail from the gallery (requested by Giovanni)\n\n"
                    + "* Bookshelves, loaned books and anthology titles will now import"
                    + " correctly\n\n";
        }
        if (curVersion == 38) {
            curVersion++;
            db.execSQL("DELETE FROM " + TBL_LOAN + " WHERE (" + DOM_LOANED_TO + "=''"
                               + " OR " + DOM_LOANED_TO + "='null')");
        }
        if (curVersion == 39) {
            curVersion++;
        }
        if (curVersion == 40) {
            curVersion++;
        }
        if (curVersion == 41) {
            curVersion++;
            mMessage = mMessage
                    + "Tip: You can find the correct barcode for many modern paperbacks"
                    + " on the inside cover\n\n"
                    + "* Added app2sd support (2.2 users only)\n\n"
                    + "* You can now assign books to multiple bookshelves"
                    + " (requested by many people)\n\n"
                    + "* A .nomedia file will be automatically created which will stop the"
                    + " thumbnails showing up in the gallery (thanks Brandon)\n\n"
                    + "* The 'Add Book by ISBN' page has been redesigned to be simpler and"
                    + " more stable (thanks Vinikia)\n\n"
                    + "* The export file is now formatted correctly (.csv) (thanks glohr)\n\n"
                    + "* You will be prompted to backup your books on a regular basis \n\n";

            db.execSQL("DROP TABLE tmp1");
            db.execSQL("DROP TABLE tmp2");
            db.execSQL("DROP TABLE tmp3");


            db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_82);
            db.execSQL("INSERT INTO " + TBL_BOOK_BOOKSHELF
                               + " (" + DOM_FK_BOOK_ID + ',' + DOM_FK_BOOKSHELF_ID + ')'
                               + " SELECT " + DOM_PK_ID + ',' + DOM_FK_BOOKSHELF_ID
                               + " FROM " + TBL_BOOKS);

            db.execSQL("CREATE TABLE tmp1 AS SELECT _id"
                               + ',' + OLD_KEY_AUTHOR
                               + ',' + DOM_TITLE
                               + ',' + DOM_BOOK_ISBN
                               + ',' + DOM_BOOK_PUBLISHER
                               + ',' + DOM_BOOK_DATE_PUBLISHED
                               + ',' + DOM_BOOK_RATING
                               + ',' + DOM_BOOK_READ
                               + ',' + OLD_KEY_SERIES
                               + ',' + DOM_BOOK_PAGES
                               + ',' + DOM_BOOK_SERIES_NUM
                               + ',' + DOM_BOOK_NOTES
                               + ',' + DOM_BOOK_PRICE_LISTED
                               + ',' + DOM_BOOK_ANTHOLOGY_BITMASK
                               + ',' + DOM_BOOK_LOCATION
                               + ',' + DOM_BOOK_READ_START
                               + ',' + DOM_BOOK_READ_END
                               + ',' + OLD_KEY_AUDIOBOOK
                               + ',' + DOM_BOOK_SIGNED
                               + " FROM " + TBL_BOOKS);

            db.execSQL("CREATE TABLE tmp2 AS SELECT _id"
                               + ',' + DOM_FK_BOOK_ID
                               + ',' + DOM_LOANED_TO
                               + " FROM " + TBL_LOAN);

            db.execSQL("CREATE TABLE tmp3 AS SELECT _id"
                               + ',' + DOM_FK_BOOK_ID
                               + ',' + OLD_KEY_AUTHOR
                               + ',' + DOM_TITLE
                               + ',' + DOM_BOOK_TOC_ENTRY_POSITION
                               + " FROM " + TBL_TOC_ENTRIES);

            db.execSQL("DROP TABLE " + TBL_TOC_ENTRIES);
            db.execSQL("DROP TABLE " + TBL_LOAN);
            db.execSQL("DROP TABLE " + TBL_BOOKS);

            db.execSQL(DATABASE_CREATE_BOOKS_41);
            db.execSQL(DATABASE_CREATE_BOOK_LOAN_82);
            db.execSQL(DATABASE_CREATE_ANTHOLOGY_82);

            db.execSQL("INSERT INTO " + TBL_BOOKS + " SELECT * FROM tmp1");
            db.execSQL("INSERT INTO " + TBL_LOAN + " SELECT * FROM tmp2");
            db.execSQL("INSERT INTO " + TBL_TOC_ENTRIES + " SELECT * FROM tmp3");

            db.execSQL("DROP TABLE tmp1");
            db.execSQL("DROP TABLE tmp2");
            db.execSQL("DROP TABLE tmp3");

        }
        if (curVersion == 42) {
            curVersion++;
            mMessage = mMessage
                    + "* Export bug fixed\n\n"
                    + "* Further enhancements to the new ISBN screen\n\n"
                    + "* Filtering by bookshelf on title view is now fixed\n\n"
                    + "* There is now an <Empty Series> when sorting by Series (thanks Vinikia)\n\n"
                    + "* App will now search all fields (Thanks Michael)\n\n";
        }
        if (curVersion == 43) {
            curVersion++;
            db.execSQL("DELETE FROM " + TBL_TOC_ENTRIES + " WHERE " + DOM_PK_ID + " IN ("
                               + "SELECT a." + DOM_PK_ID
                               + " FROM " + TBL_TOC_ENTRIES + " a," + TBL_TOC_ENTRIES + " b "
                               + " WHERE a." + DOM_FK_BOOK_ID + "=b." + DOM_FK_BOOK_ID
                               + " AND a." + OLD_KEY_AUTHOR + "=b." + OLD_KEY_AUTHOR
                               + " AND a." + DOM_TITLE + "=b." + DOM_TITLE
                               + " AND a." + DOM_PK_ID + ">b." + DOM_PK_ID
                               + ')');

            db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + TBL_TOC_ENTRIES
                            + " (" + DOM_FK_BOOK_ID + ',' + OLD_KEY_AUTHOR + ',' + DOM_TITLE + ')');
        }
        if (curVersion == 44) {
            curVersion++;

            db.execSQL("DROP TABLE tmp1");
            db.execSQL("DROP TABLE tmp2");
            db.execSQL("DROP TABLE tmp3");

            db.execSQL("CREATE TABLE tmp1 AS SELECT _id"
                               + ',' + OLD_KEY_AUTHOR
                               + ',' + DOM_TITLE
                               + ',' + DOM_BOOK_ISBN
                               + ',' + DOM_BOOK_PUBLISHER
                               + ',' + DOM_BOOK_DATE_PUBLISHED
                               + ',' + DOM_BOOK_RATING
                               + ',' + DOM_BOOK_READ
                               + ',' + OLD_KEY_SERIES
                               + ',' + DOM_BOOK_PAGES
                               + ',' + DOM_BOOK_SERIES_NUM
                               + ',' + DOM_BOOK_NOTES
                               + ',' + DOM_BOOK_PRICE_LISTED
                               + ',' + DOM_BOOK_ANTHOLOGY_BITMASK
                               + ',' + DOM_BOOK_LOCATION
                               + ',' + DOM_BOOK_READ_START
                               + ',' + DOM_BOOK_READ_END
                               + ','
                               + "CASE WHEN " + OLD_KEY_AUDIOBOOK + "='t'"
                               + " THEN 'Audiobook' ELSE 'Paperback' END AS " + OLD_KEY_AUDIOBOOK
                               + ',' + DOM_BOOK_SIGNED
                               + " FROM " + TBL_BOOKS);

            db.execSQL("CREATE TABLE tmp2 AS SELECT _id,"
                               + DOM_FK_BOOK_ID + ',' + DOM_LOANED_TO + " FROM " + TBL_LOAN);
            db.execSQL("CREATE TABLE tmp3 AS SELECT _id,"
                               + DOM_FK_BOOK_ID + ',' + OLD_KEY_AUTHOR + ',' + DOM_TITLE
                               + ',' + DOM_BOOK_TOC_ENTRY_POSITION
                               + " FROM " + TBL_TOC_ENTRIES);
            db.execSQL("CREATE TABLE tmp4 AS SELECT " + DOM_FK_BOOK_ID
                               + ',' + DOM_FK_BOOKSHELF_ID + " FROM " + TBL_BOOK_BOOKSHELF);

            db.execSQL("DROP TABLE " + TBL_TOC_ENTRIES);
            db.execSQL("DROP TABLE " + TBL_LOAN);
            db.execSQL("DROP TABLE " + TBL_BOOKS);
            db.execSQL("DROP TABLE " + TBL_BOOK_BOOKSHELF);

            String TMP_DATABASE_CREATE_BOOKS =
                    "CREATE TABLE " + TBL_BOOKS + " (_id integer primary key autoincrement"
                            + ',' + OLD_KEY_AUTHOR + " integer not null REFERENCES " + TBL_AUTHORS
                            + ',' + DOM_TITLE + " text not null"
                            + ',' + DOM_BOOK_ISBN + " text"
                            + ',' + DOM_BOOK_PUBLISHER + " text"
                            + ',' + DOM_BOOK_DATE_PUBLISHED + " date"
                            + ',' + DOM_BOOK_RATING + " float not null default 0"
                            + ',' + DOM_BOOK_READ + " boolean not null default 'f'"
                            + ',' + OLD_KEY_SERIES + " text"
                            + ',' + DOM_BOOK_PAGES + " int"
                            + ',' + DOM_BOOK_SERIES_NUM + " text"
                            + ',' + DOM_BOOK_NOTES + " text"
                            + ',' + DOM_BOOK_PRICE_LISTED + " text"
                            + ',' + DOM_BOOK_ANTHOLOGY_BITMASK + " int not null default 0"
                            + ',' + DOM_BOOK_LOCATION + " text"
                            + ',' + DOM_BOOK_READ_START + " date"
                            + ',' + DOM_BOOK_READ_END + " date"
                            + ',' + DOM_BOOK_FORMAT + " text"
                            + ',' + DOM_BOOK_SIGNED + " boolean not null default 'f'"
                            + ')';


            db.execSQL(TMP_DATABASE_CREATE_BOOKS);
            db.execSQL(DATABASE_CREATE_BOOK_LOAN_82);
            db.execSQL(DATABASE_CREATE_ANTHOLOGY_82);
            db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_82);

            db.execSQL("INSERT INTO " + TBL_BOOKS + " SELECT * FROM tmp1");
            db.execSQL("INSERT INTO " + TBL_LOAN + " SELECT * FROM tmp2");
            db.execSQL("INSERT INTO " + TBL_TOC_ENTRIES + " SELECT * FROM tmp3");
            db.execSQL("INSERT INTO " + TBL_BOOK_BOOKSHELF + " SELECT * FROM tmp4");

            db.execSQL("DROP TABLE tmp1");
            db.execSQL("DROP TABLE tmp2");
            db.execSQL("DROP TABLE tmp3");
            db.execSQL("DROP TABLE tmp4");
        }
        if (curVersion == 45) {
            curVersion++;
            db.execSQL("DELETE FROM " + TBL_LOAN + " WHERE " + DOM_LOANED_TO + "='null'");
        }
        if (curVersion == 46) {
            curVersion++;
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_DESCRIPTION + " text");
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_GENRE + " text");
        }
        if (curVersion == 47) {
            curVersion++;
            mMessage = mMessage
                    + "New in v3.1\n\n"
                    + "* The audiobook checkbox has been replaced with a format selector"
                    + " (inc. paperback, hardcover, companion etc)\n\n"
                    + "* When adding books the current bookshelf will be selected as the default"
                    + " bookshelf\n\n"
                    + "* Genre/Subject and Description fields have been added (Requested by Tosh)"
                    + " and will automatically populate based on Google Books and Amazon"
                    + " information\n\n"
                    + "* The save button will always be visible on the edit book screen\n\n"
                    + "* Searching for a single space will clear the search results page\n\n"
                    + "* The Date Picker will now appear in a popup in order to save space on the"
                    + " screen (Requested by several people)\n\n"
                    + "* To improve speed when sorting by title, the titles will be broken up by"
                    + " the first character. Remember prefixes such as 'the' and 'a' are listed"
                    + " after the title, e.g. 'The Trigger' becomes 'Trigger, The'\n\n";
        }
        if (curVersion == 48) {
            curVersion++;
            db.execSQL("DELETE FROM loan WHERE loaned_to='null';");
            db.execSQL("DELETE FROM loan WHERE _id!=(SELECT max(l2._id) FROM loan l2"
                               + " WHERE l2.book=loan.book);");
            db.execSQL("DELETE FROM anthology WHERE _id!=(SELECT max(a2._id) FROM anthology a2"
                               + " WHERE a2.book=anthology.book AND a2.author=anthology.author"
                               + " AND a2.title=anthology.title);");
        }
        if (curVersion == 49) {
            curVersion++;
            mMessage = mMessage
                    + "New in v3.2\n\n"
                    + "* Books can now be automatically added by searching for the author name"
                    + " and book title\n\n"
                    + "* Updating thumbnails, genre and description fields will also search by "
                    + " author name and title is the isbn does not exist\n\n"
                    + "* Expand/Collapse all bug fixed\n\n"
                    + "* The search query will be shown at the top of all search screens\n\n";
        }
        if (curVersion == 50) {
            curVersion++;
        }
        if (curVersion == 51) {
            curVersion++;
            mMessage = mMessage
                    + "New in v3.3 - Updates courtesy of Grunthos\n\n"
                    + "* The application should be significantly faster now - Fixed a bug with"
                    + " database index creation\n\n"
                    + "* The thumbnail can be rotated in both directions now\n\n"
                    + "* You can zoom in the thumbnail to see full detail\n\n"
                    + "* The help page will redirect to the, more frequently updated,"
                    + " online wiki\n\n"
                    + "* Dollar signs in the text fields will no longer FC on import/export\n\n";
        }
        if (curVersion == 52) {
            curVersion++;
            mMessage = mMessage
                    + "New in v3.3.1\n\n"
                    + "* Minor bug fixes and error logging.\n\n";
        }
        if (curVersion == 53) {
            curVersion++;
            //There is a conflict between eleybourn released branch (3.3.1) and Grunthos HEAD (3.4).
            // This is to check and skip as required
            boolean go = false;
            String checkSQL = "SELECT * FROM " + TBL_BOOKS;
            try (Cursor results = db.rawQuery(checkSQL, null)) {
                if (results.getCount() > 0) {
                    if (results.getColumnIndex(OLD_KEY_AUTHOR) > -1) {
                        go = true;
                    }
                }
            }

            if (go) {
                mMessage = mMessage
                        + "New in v3.4 - Updates courtesy of (mainly) Grunthos"
                        + " (blame him, politely, if it toasts your data)\n\n"
                        + "* Multiple Authors per book\n\n"
                        + "* Multiple Series per book\n\n"
                        + "* Fetches series (and other stuff) from LibraryThing\n\n"
                        + "* Can now make global changes to Author and Series"
                        + " (Access this feature via long-click on the catalogue screen)\n\n"
                        + "* Can replace a cover thumbnail from a different edition via"
                        + " LibraryThing.\n\n"
                        + "* Does concurrent ISBN searches at Amazon, Google and LibraryThing"
                        + " (it's faster)\n\n"
                        + "* Displays a progress dialog while searching for a book on the"
                        + " internet.\n\n"
                        + "* Adding by Amazon's ASIN is supported on the 'Add by ISBN' page\n\n"
                        + "* Duplicate books allowed, with a warning message\n\n"
                        + "* User-selectable fields when reloading data from internet"
                        + " (eg. just update authors).\n\n"
                        + "* Unsaved edits are retained when rotating the screen.\n\n"
                        + "* Changed the ISBN data entry screen when the device is in landscape"
                        + " mode.\n\n"
                        + "* Displays square brackets around the series name when displaying a"
                        + " list of books.\n\n"
                        + "* Suggestions available when searching\n\n"
                        + "* Removed the need for contacts permission. Though it does mean you"
                        + " will need to manually enter everyone you want to loan to.\n\n"
                        + "* Preserves *all* open groups when closing application.\n\n"
                        + "* The scanner and book search screens remain active after a book has"
                        + " been added or a search fails. It was viewed that this more closely"
                        + " represents the work-flow of people adding or scanning books.\n\n"
                        + "See the web site (from the Admin menu) for more details\n\n";


                db.execSQL(DATABASE_CREATE_SERIES_82);
                // We need to create a series table with series that are unique wrt case
                // and unicode. The old system allowed for series with slightly different case.
                // So we capture these by using max() to pick and arbitrary matching name to
                // use as our canonical version.
                db.execSQL("INSERT INTO " + TBL_SERIES + " (" + DOM_SERIES_NAME + ')'
                                   + " SELECT name FROM ("
                                   + " SELECT Upper(" + OLD_KEY_SERIES + ')'
                                   + COLLATION + " AS ucName,"
                                   + " max(" + OLD_KEY_SERIES + ')'
                                   + COLLATION + " AS name FROM " + TBL_BOOKS
                                   + " WHERE Coalesce(" + OLD_KEY_SERIES + ",'')<>''"
                                   + " GROUP BY Upper(" + OLD_KEY_SERIES + ')'
                                   + " )");

                db.execSQL(DATABASE_CREATE_BOOK_SERIES_54);
                db.execSQL(DATABASE_CREATE_BOOK_AUTHOR_82);

                db.execSQL("INSERT INTO " + TBL_BOOK_SERIES + " (" + DOM_FK_BOOK_ID
                                   + ',' + DOM_FK_SERIES_ID + ',' + DOM_BOOK_SERIES_NUM
                                   + ',' + DOM_BOOK_SERIES_POSITION + ')'
                                   + " SELECT DISTINCT b." + DOM_PK_ID + ",s." + DOM_PK_ID
                                   + ",b." + DOM_BOOK_SERIES_NUM + ",1"
                                   + " FROM " + TBL_BOOKS + " b"
                                   + " JOIN " + TBL_SERIES + " s ON"
                                   + " Upper(s." + DOM_SERIES_NAME + ')'
                                   + "=Upper(b." + OLD_KEY_SERIES + ')' + COLLATION
                                   + " WHERE Coalesce(b." + OLD_KEY_SERIES + ",'')<>''");

                db.execSQL("INSERT INTO " + TBL_BOOK_AUTHOR
                                   + " (" + DOM_FK_BOOK_ID
                                   + ',' + DOM_FK_AUTHOR_ID
                                   + ',' + DOM_BOOK_AUTHOR_POSITION + ')'
                                   + " SELECT b." + DOM_PK_ID
                                   + ",b." + OLD_KEY_AUTHOR + ",1 FROM " + TBL_BOOKS + " b");

                String tmpFields = DOM_PK_ID.name
                        /* ',' + KEY_AUTHOR */
                        + ',' + DOM_TITLE
                        + ',' + DOM_BOOK_ISBN
                        + ',' + DOM_BOOK_PUBLISHER
                        + ',' + DOM_BOOK_DATE_PUBLISHED
                        + ',' + DOM_BOOK_RATING
                        + ',' + DOM_BOOK_READ
                        /* + ',' + KEY_SERIES */
                        + ',' + DOM_BOOK_PAGES
                        /* + ,', + KEY_SERIES_NUM */
                        + ',' + DOM_BOOK_NOTES
                        + ',' + DOM_BOOK_PRICE_LISTED
                        + ',' + DOM_BOOK_ANTHOLOGY_BITMASK
                        + ',' + DOM_BOOK_LOCATION
                        + ',' + DOM_BOOK_READ_START
                        + ',' + DOM_BOOK_READ_END
                        + ',' + DOM_BOOK_FORMAT
                        + ',' + DOM_BOOK_SIGNED
                        + ',' + DOM_BOOK_DESCRIPTION
                        + ',' + DOM_BOOK_GENRE;

                db.execSQL("CREATE TABLE tmpBooks AS SELECT " + tmpFields
                                   + " FROM " + TBL_BOOKS);

                db.execSQL("DROP TABLE " + TBL_BOOKS);

                db.execSQL(DATABASE_CREATE_BOOKS_63);

                db.execSQL("INSERT INTO " + TBL_BOOKS + '(' + tmpFields + ')'
                                   + " SELECT * FROM tmpBooks");

                db.execSQL("DROP TABLE tmpBooks");

            }
            if (curVersion == 54) {
                curVersion++;
            }
            if (curVersion == 55) {
                //There is a conflict between eleybourn released branch (3.3.1)
                // and Grunthos HEAD (3.4). This is to check and skip as required
                boolean go2 = false;
                try (Cursor results = db.rawQuery("SELECT * FROM " + TBL_BOOKS, null)) {
                    if (results.getCount() > 0) {
                        if (results.getColumnIndex(OLD_KEY_AUTHOR) > -1) {
                            go2 = true;
                        }
                    } else {
                        go2 = true;
                    }
                }

                if (go2) {
                    db.execSQL(DATABASE_CREATE_SERIES_82);
                    // We need to create a series table with series that are unique
                    // wrt case and unicode. The old system allowed for series with
                    // slightly different case. So we capture these by using max() to
                    // pick and arbitrary matching name to use as our canonical version.
                    db.execSQL("INSERT INTO " + TBL_SERIES + " (" + DOM_SERIES_NAME + ')'
                                       + " SELECT name FROM ("
                                       + " SELECT Upper(" + OLD_KEY_SERIES + ')' + COLLATION
                                       + " AS ucName,"
                                       + " Max(" + OLD_KEY_SERIES + ')' + COLLATION
                                       + " AS name FROM " + TBL_BOOKS
                                       + " WHERE Coalesce(" + OLD_KEY_SERIES + ",'')<>''"
                                       + " GROUP BY Upper(" + OLD_KEY_SERIES + ')'
                                       + " )");

                    db.execSQL(DATABASE_CREATE_BOOK_SERIES_54);
                    db.execSQL(DATABASE_CREATE_BOOK_AUTHOR_82);

                    db.execSQL(
                            "INSERT INTO " + TBL_BOOK_SERIES
                                    + " (" + DOM_FK_BOOK_ID
                                    + ',' + DOM_FK_SERIES_ID
                                    + ',' + DOM_BOOK_SERIES_NUM
                                    + ',' + DOM_BOOK_SERIES_POSITION + ')'
                                    + " SELECT DISTINCT b." + DOM_PK_ID + ",s." + DOM_PK_ID
                                    + ",b." + DOM_BOOK_SERIES_NUM + ",1"
                                    + " FROM " + TBL_BOOKS + " b "
                                    + " JOIN " + TBL_SERIES + " s ON"
                                    + " Upper(s." + DOM_SERIES_NAME + ')'
                                    + "= Upper(b." + OLD_KEY_SERIES + ')' + COLLATION
                                    + " WHERE Coalesce(b." + OLD_KEY_SERIES + ",'')<>''");

                    db.execSQL("INSERT INTO " + TBL_BOOK_AUTHOR + " (" + DOM_FK_BOOK_ID
                                       + ',' + DOM_FK_AUTHOR_ID
                                       + ',' + DOM_BOOK_AUTHOR_POSITION + ')'
                                       + " SELECT b." + DOM_PK_ID + ",b." + OLD_KEY_AUTHOR
                                       + ",1 FROM " + TBL_BOOKS + " b ");

                    String tmpFields = DOM_PK_ID.name
                            /* + ',' + KEY_AUTHOR */
                            + ',' + DOM_TITLE
                            + ',' + DOM_BOOK_ISBN
                            + ',' + DOM_BOOK_PUBLISHER
                            + ',' + DOM_BOOK_DATE_PUBLISHED
                            + ',' + DOM_BOOK_RATING
                            + ',' + DOM_BOOK_READ
                            /* + ',' + KEY_SERIES */
                            + ',' + DOM_BOOK_PAGES
                            /* + ',' + KEY_SERIES_NUM */
                            + ',' + DOM_BOOK_NOTES
                            + ',' + DOM_BOOK_PRICE_LISTED
                            + ',' + DOM_BOOK_ANTHOLOGY_BITMASK
                            + ',' + DOM_BOOK_LOCATION
                            + ',' + DOM_BOOK_READ_START
                            + ',' + DOM_BOOK_READ_END
                            + ',' + DOM_BOOK_FORMAT
                            + ',' + DOM_BOOK_SIGNED
                            + ',' + DOM_BOOK_DESCRIPTION
                            + ',' + DOM_BOOK_GENRE;

                    db.execSQL("CREATE TABLE tmpBooks AS SELECT " + tmpFields
                                       + " FROM " + TBL_BOOKS);

                    db.execSQL("DROP TABLE " + TBL_BOOKS);

                    db.execSQL(DATABASE_CREATE_BOOKS_63);

                    db.execSQL("INSERT INTO " + TBL_BOOKS + "( " + tmpFields + ')'
                                       + " SELECT * FROM tmpBooks");

                    db.execSQL("DROP TABLE tmpBooks");
                }
                curVersion++;
            }
        }
        if (curVersion == 56) {
            curVersion++;
            mMessage = mMessage
                    + "New in v3.5.4\n\n"
                    + "* French translation available\n\n"
                    + "* There is an option to create a duplicate book (requested by Vinika)\n\n"
                    + "* Fixed errors caused by failed upgrades\n\n"
                    + "* Fixed errors caused by trailing spaces in bookshelf names\n\n";
        }
        if (curVersion == 57) {
            curVersion++;
            try (Cursor results57 = db.rawQuery(getTableNameSql(TBL_AUTHORS), null)) {
                if (results57.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_AUTHORS_82);
                }
            }
            try (Cursor results = db.rawQuery(getTableNameSql(TBL_BOOKSHELF), null)) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_BOOKSHELF_82);
                    db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                                       + " (" + DOM_BOOKSHELF + ") VALUES ('Default')");
                }
            }
            try (Cursor results = db.rawQuery(getTableNameSql(TBL_SERIES), null)) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_SERIES_82);
                    try (Cursor results2 = db.rawQuery("SELECT * FROM " + TBL_BOOKS, null)) {
                        if (results2.getCount() > 0
                                && results2.getColumnIndex(OLD_KEY_SERIES) > -1) {
                            db.execSQL("INSERT INTO " + TBL_SERIES
                                               + " (" + DOM_SERIES_NAME + ')'
                                               + " SELECT name FROM ("
                                               + " SELECT Upper(" + OLD_KEY_SERIES + ')'
                                               + COLLATION + " AS ucName,"
                                               + " max(" + OLD_KEY_SERIES + ')'
                                               + COLLATION + " AS name FROM " + TBL_BOOKS
                                               + " WHERE Coalesce(" + OLD_KEY_SERIES + ",'') <> ''"
                                               + " GROUP BY Upper(" + OLD_KEY_SERIES + ')'
                                               + " )"
                            );
                        }
                    }
                }
            }

            try (Cursor results = db.rawQuery(getTableNameSql(TBL_BOOKS), null)) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_BOOKS_63);
                }
            }
            try (Cursor results = db.rawQuery(getTableNameSql(TBL_LOAN), null)) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_BOOK_LOAN_82);
                }
            }
            try (Cursor results = db.rawQuery(getTableNameSql(TBL_TOC_ENTRIES), null)) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_ANTHOLOGY_82);
                }
            }
            try (Cursor results = db.rawQuery(getTableNameSql(TBL_BOOK_BOOKSHELF), null)) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_82);
                }
            }
            try (Cursor results = db.rawQuery(getTableNameSql(TBL_BOOK_SERIES), null)) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_BOOK_SERIES_54);
                    db.execSQL("INSERT INTO " + TBL_BOOK_SERIES + " (" + DOM_FK_BOOK_ID
                                       + ',' + DOM_FK_SERIES_ID
                                       + ',' + DOM_BOOK_SERIES_NUM
                                       + ',' + DOM_BOOK_SERIES_POSITION + ')'
                                       + " SELECT DISTINCT b." + DOM_PK_ID
                                       + ",s." + DOM_PK_ID
                                       + ",b." + DOM_BOOK_SERIES_NUM + ",1"
                                       + " FROM " + TBL_BOOKS + " b "
                                       + " JOIN " + TBL_SERIES + " s ON"
                                       + " Upper(s." + DOM_SERIES_NAME + ')'
                                       + "=Upper(b." + OLD_KEY_SERIES + ')' + COLLATION
                                       + " WHERE Coalesce(b." + OLD_KEY_SERIES + ",'')<>''");
                }
            }
            try (Cursor results = db.rawQuery(getTableNameSql(TBL_BOOK_AUTHOR), null)) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_BOOK_AUTHOR_82);
                    db.execSQL("INSERT INTO " + TBL_BOOK_AUTHOR
                                       + " (" + DOM_FK_BOOK_ID + ',' + DOM_FK_AUTHOR_ID
                                       + ',' + DOM_BOOK_AUTHOR_POSITION + ')'
                                       + " SELECT b." + DOM_PK_ID + ",b." + OLD_KEY_AUTHOR
                                       + ",1 FROM " + TBL_BOOKS + " b ");
                }
            }
            try (Cursor results = db.rawQuery("SELECT * FROM " + TBL_BOOKS, null)) {
                if (results.getCount() > 0) {
                    if (results.getColumnIndex(OLD_KEY_SERIES) > -1) {
                        String tmpFields = DOM_PK_ID.name
                                /* ',' + KEY_AUTHOR */
                                + ',' + DOM_TITLE
                                + ',' + DOM_BOOK_ISBN
                                + ',' + DOM_BOOK_PUBLISHER
                                + ',' + DOM_BOOK_DATE_PUBLISHED
                                + ',' + DOM_BOOK_RATING
                                + ',' + DOM_BOOK_READ
                                /* ',' + KEY_SERIES */
                                + ',' + DOM_BOOK_PAGES
                                /* + ',' + KEY_SERIES_NUM */
                                + ',' + DOM_BOOK_NOTES
                                + ',' + DOM_BOOK_PRICE_LISTED
                                + ',' + DOM_BOOK_ANTHOLOGY_BITMASK
                                + ',' + DOM_BOOK_LOCATION
                                + ',' + DOM_BOOK_READ_START
                                + ',' + DOM_BOOK_READ_END
                                + ',' + DOM_BOOK_FORMAT
                                + ',' + DOM_BOOK_SIGNED
                                + ',' + DOM_BOOK_DESCRIPTION
                                + ',' + DOM_BOOK_GENRE;

                        db.execSQL("CREATE TABLE tmpBooks AS SELECT " + tmpFields
                                           + " FROM " + TBL_BOOKS);
                        db.execSQL("DROP TABLE " + TBL_BOOKS);
                        db.execSQL(DATABASE_CREATE_BOOKS_63);
                        db.execSQL("INSERT INTO " + TBL_BOOKS + '(' + tmpFields + ')'
                                           + " SELECT * FROM tmpBooks");
                        db.execSQL("DROP TABLE tmpBooks");
                    }
                }
            }
        }
        if (curVersion == 58) {
            curVersion++;
            db.delete(TBL_LOAN.getName(),
                      "(" + DOM_FK_BOOK_ID + "='' OR " + DOM_FK_BOOK_ID + "=null"
                              + " OR " + DOM_LOANED_TO + "='' OR " + DOM_LOANED_TO + "=null)",
                      null);

            mMessage = mMessage
                    + "New in v3.6\n\n"
                    + "* The LibraryThing Key will be verified when added\n\n"
                    + "* When entering ISBN's manually, each button with vibrate the phone"
                    + " slightly\n\n"
                    + "* When automatically updating fields click on each checkbox twice"
                    + " will give you the option to override existing fields (rather than populate"
                    + " only blank fields)\n\n"
                    + "* Fixed a crash when exporting over 2000 books\n\n"
                    + "* Orphan loan records will now be correctly managed\n\n"
                    + "* The Amazon search will now look for English, French, German,"
                    + " Italian and Japanese versions\n\n";
        }
        if (curVersion == 59) {
            curVersion++;
            mMessage = mMessage
                    + "New in v3.6.2\n\n"
                    + "* Optionally restrict 'Sort By Author' to only the first Author"
                    + " (where there are multiple listed)\n\n"
                    + "* Minor bug fixes\n\n";
        }
        if (curVersion == 60) {
            curVersion++;
            mMessage = mMessage
                    + "New in v3.7\n\n"
                    + "Hint: The export function will create an export.csv file on the sdcard\n\n"
                    + "* You can crop cover thumbnails (both from the menu and after"
                    + " taking a camera image)\n\n"
                    + "* You can tweet about a book directly from the book edit screen.\n\n"
                    + "* Sort by Date Published added\n\n"
                    + "* Will check for network connection prior to searching for details"
                    + " (new permission required)\n\n"
                    + "* Fixed crash when opening search results\n\n";
        }
        if (curVersion == 61) {
            curVersion++;
            mMessage = mMessage
                    + "New in v3.8\n\n"
                    + "* Fixed several defects (including multiple author's and author"
                    + " prefix/suffix's)\n\n"
                    + "* Fixed issue with thumbnail resolutions from LibraryThing\n\n"
                    + "* Changed the 'Add Book' menu options to be submenu\n\n"
                    + "* The database backup has been renamed for clarity\n\n";
        }
        if (curVersion == 62) {
            curVersion++;
        }
        if (curVersion == 63) {
            // Fix up old default 'f' values to be 0 (true = 1).
            curVersion++;
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ + "=0"
                               + " WHERE " + DOM_BOOK_READ + "='f'");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ + "=1"
                               + " WHERE " + DOM_BOOK_READ + "='t'");
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " RENAME TO books_tmp");
            db.execSQL(DATABASE_CREATE_BOOKS_63);
            db.execSQL("INSERT INTO " + TBL_BOOKS + " SELECT * FROM books_tmp");
            db.execSQL("DROP TABLE books_tmp");
        }
        if (curVersion == 64) {
            // Changed SIGNED to boolean {0,1} and added DATE_ADDED
            // Fix up old default 'f' values to be 0 (true = 1).
            curVersion++;
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SIGNED + "=0"
                               + " WHERE " + DOM_BOOK_SIGNED + "='f'");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SIGNED + "=1"
                               + " WHERE " + DOM_BOOK_SIGNED + "='t'");
            // Just use a null value for old records
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " Add " + DOM_BOOK_DATE_ADDED + " datetime");
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " RENAME TO books_tmp");
            db.execSQL(DATABASE_CREATE_BOOKS_68);
            db.execSQL("INSERT INTO " + TBL_BOOKS + " SELECT * FROM books_tmp");
            db.execSQL("DROP TABLE books_tmp");
        }

        if (curVersion == 65) {
            curVersion++;
            DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS.drop(syncedDb);
            DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS.create(syncedDb);
        }
        if (curVersion == 66) {
            curVersion++;
            DatabaseDefinitions.TBL_BOOKS_FTS.drop(syncedDb);
            DatabaseDefinitions.TBL_BOOKS_FTS.create(syncedDb, false);
            StartupActivity.scheduleFtsRebuild();
        }
        if (curVersion == 67) {
            curVersion++;
            DatabaseDefinitions.TBL_BOOKLIST_STYLES.drop(syncedDb);
            DatabaseDefinitions.TBL_BOOKLIST_STYLES.createAll(syncedDb);
        }
        if (curVersion == 68) {
            curVersion++;
            db.execSQL("ALTER TABLE " + TBL_BOOKS
                               + " ADD " + DOM_BOOK_GOODREADS_BOOK_ID + " integer");
        }
        if (curVersion == 69 || curVersion == 70) {
            curVersion = 71;
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " RENAME TO books_tmp");
            db.execSQL(DATABASE_CREATE_BOOKS_81);
            copyTableSafely(syncedDb, "books_tmp", TBL_BOOKS.getName());
            db.execSQL("DROP TABLE books_tmp");
        }
        if (curVersion == 71) {
            curVersion++;
            v72_renameIdFilesToHash(syncedDb);
            // A bit of presumption here...
            mMessage = mMessage
                    + "New in v4.0 - Updates courtesy of (mainly) Philip Warner"
                    + " (a.k.a Grunthos) -- blame him, politely, if it toasts your data\n\n"
                    + "* New look, new startup page\n\n"
                    + "* Synchronization with goodreads (www.goodreads.com)\n\n"
                    + "* New styles for book lists (including 'Compact' and 'Unread')\n\n"
                    + "* User-defined styles for book lists\n\n"
                    + "* More efficient memory usage\n\n"
                    + "* New preferences, including 'always expanded/collapsed' lists\n\n"
                    + "* Faster expand/collapse with large collections\n\n"
                    + "* Cached covers for faster scrolling lists\n\n"
                    + "* Cover images now have globally unique names, and books have globally"
                    + " unique IDs, so sharing and combining collections is easier\n\n"
                    + "* Improved detection of series names\n\n";
        }
        if (curVersion == 72) {
            curVersion++;
            // Just to get the triggers applied
        }

        if (curVersion == 73) {
            curVersion++;
            mMessage = mMessage
                    + "New in v4.0.1 - many bugs fixed\n\n"
                    + "* Added a preference to completely disable background bitmap\n\n"
                    + "* Added book counts to book lists\n\n";
        }

        if (curVersion == 74) {
            curVersion++;
            Prefs.getPrefs().edit()
                 .putBoolean(V74_PREF_AUTHOR_SERIES_FIX_UP_REQUIRED, true).apply();

            mMessage = mMessage
                    + "New in v4.0.3\n\n"
                    + "* ISBN validation when searching/scanning and error beep when scanning"
                    + " (with preference to turn it off)\n\n"
                    + "* 'Loaned' list now shows available books under the heading 'Available'\n\n"
                    + "* Added preference to hide list headers (for small phones)\n\n"
                    + "* Restored functionality to use current bookshelf when adding book\n\n"
                    + "* FastScroller sizing improved\n\n"
                    + "* Several bugs fixed\n\n";
        }

        if (curVersion == 75) {
            curVersion++;
            StartupActivity.scheduleFtsRebuild();
            mMessage = mMessage
                    + "New in v4.0.4\n\n"
                    + "* Search now searches series and anthology data\n\n"
                    + "* Allows non-numeric data entry in series position\n\n"
                    + "* Better sorting of leading numerical in series position\n\n"
                    + "* Several bug fixes\n\n";
        }

        if (curVersion == 76) {
            curVersion++;
        }
        if (curVersion == 77) {
            curVersion++;
            mMessage = mMessage
                    + "New in v4.0.6\n\n"
                    + "* When adding books, scanning or typing an existing ISBN allows you"
                    + " the option to edit the book.\n"
                    + "* Allow ASINs to be entered manually as well as ISBNs\n"
                    + "* German translation updates (Robert Wetzlmayr)\n";
        }
        if (curVersion == 78) {
            curVersion++;
            mMessage = mMessage
                    + "New in v4.1\n\n"
                    + "* New style groups: Location and Date Read\n"
                    + "* Improved 'Share' functionality (filipeximenes)\n"
                    + "* French translation updates (Djiko)\n"
                    + "* Better handling of the 'back' key when editing books (filipeximenes)\n"
                    + "* Various bug fixes\n";
        }

        if (curVersion == 79) {
            curVersion++;
            // We want a rebuild in all lower case.
            StartupActivity.scheduleFtsRebuild();
        }

        if (curVersion == 80) {
            curVersion++;
            // Adjust the Book-series table to have a text 'series-num'.
            final String tempName = "books_series_tmp";
            db.execSQL("ALTER TABLE " + TBL_BOOK_SERIES + " RENAME TO " + tempName);
            db.execSQL(DATABASE_CREATE_BOOK_SERIES_82);
            copyTableSafely(syncedDb, tempName, TBL_BOOK_SERIES.getName());
            db.execSQL("DROP TABLE " + tempName);
        }

        if (curVersion == 81) {
            curVersion++;
            // added a language field
            UpgradeDatabase.recreateAndReloadTable(syncedDb, TBL_BOOKS.getName(),
                                                   DATABASE_CREATE_BOOKS_82);
        }

        return curVersion;
    }

    private static String getTableNameSql(@NonNull final TableDefinition table) {
        return "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + '\'';
    }

}
