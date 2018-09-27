package com.eleybourn.bookcatalogue.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.database.definitions.TableInfo;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOK_BOOKSHELF_WEAK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_LOAN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DB_TB_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ANTHOLOGY_MASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.COLLATION;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.DATABASE_CREATE_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.DATABASE_CREATE_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.DATABASE_CREATE_BOOKSHELF_DATA;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.DATABASE_CREATE_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.DATABASE_CREATE_BOOK_BOOKSHELF_WEAK;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.DATABASE_CREATE_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.DATABASE_CREATE_LOAN;
import static com.eleybourn.bookcatalogue.database.DatabaseHelper.DATABASE_CREATE_SERIES;

/**
 * Moved all upgrade specific definitions/methods from {@link DatabaseHelper} here.
 * This should help reduce memory footprint (well, we can hope.. but at least editing the former is easier now)
 *
 */
class UpgradeDatabase {

    // column names from 'old' versions, used to upgrade
    private static final String OLD_KEY_AUDIOBOOK = "audiobook";
    private static final String OLD_KEY_AUTHOR = "author";
    private static final String OLD_KEY_SERIES = "series";
    private static final String KEY_AUDIOBOOK_V41 = "audiobook";

    //<editor-fold desc="CREATE TABLE definitions">

    private static final String DATABASE_CREATE_ANTHOLOGY_V82 =
            "create table " + DB_TB_ANTHOLOGY + " (_id integer primary key autoincrement, " +
                    DOM_BOOK_ID + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
                    DOM_AUTHOR_ID + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " +
                    DOM_TITLE + " text not null, " +
                    DOM_BOOK_ANTHOLOGY_POSITION + " int" +
                    ")";

    // Renamed to the LAST version in which it was used
     private static final String DATABASE_CREATE_BOOKS_81 =
            "create table " + DB_TB_BOOKS + " (_id integer primary key autoincrement, " +
                    DOM_TITLE + " text not null, " +
                    DOM_BOOK_ISBN + " text, " +
                    DOM_PUBLISHER + " text, " +
                    DOM_DATE_PUBLISHED + " date, " +
                    DOM_BOOK_RATING + " float not null default 0, " +
                    DOM_BOOK_READ + " boolean not null default 0, " +
                    DOM_BOOK_PAGES + " int, " +
                    DOM_BOOK_NOTES + " text, " +
                    DOM_BOOK_LIST_PRICE + " text, " +
                    DOM_ANTHOLOGY_MASK + " int not null default " + TableInfo.ColumnInfo.ANTHOLOGY_NO + ", " +
                    DOM_BOOK_LOCATION + " text, " +
                    DOM_BOOK_READ_START + " date, " +
                    DOM_BOOK_READ_END + " date, " +
                    DOM_BOOK_FORMAT + " text, " +
                    DOM_BOOK_SIGNED + " boolean not null default 0, " +
                    DOM_DESCRIPTION + " text, " +
                    DOM_BOOK_GENRE + " text, " +
                    DOM_BOOK_DATE_ADDED + " datetime default current_timestamp, " +
                    DOM_GOODREADS_BOOK_ID + " int, " +
                    DOM_GOODREADS_LAST_SYNC_DATE + " date default '0000-00-00', " +
                    DOM_BOOK_UUID + " text not null default (lower(hex(randomblob(16)))), " +
                    DOM_LAST_UPDATE_DATE + " date not null default current_timestamp " +
                    ")";

    //private static final String DATABASE_CREATE_BOOKS_70 =
    //		"create table " + DB_TB_BOOKS +
    //		" (_id integer primary key autoincrement, " +
    //		/* KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + */
    //		KEY_TITLE + " text not null, " +
    //		KEY_ISBN + " text, " +
    //		KEY_PUBLISHER + " text, " +
    //		KEY_BOOK_DATE_PUBLISHED + " date, " +
    //		KEY_BOOK_RATING + " float not null default 0, " +
    //		KEY_BOOK_READ + " boolean not null default 0, " +
    //		/* KEY_SERIES + " text, " + */
    //		KEY_BOOK_PAGES + " int, " +
    //		/* KEY_SERIES_NUM + " text, " + */
    //		KEY_NOTES + " text, " +
    //		KEY_BOOK_LIST_PRICE + " text, " +
    //		KEY_IS_ANTHOLOGY + " int not null default " + ANTHOLOGY_NO + ", " +
    //		KEY_BOOK_LOCATION + " text, " +
    //		KEY_BOOK_READ_START + " date, " +
    //		KEY_BOOK_READ_END + " date, " +
    //		KEY_BOOK_FORMAT + " text, " +
    //		KEY_BOOK_SIGNED + " boolean not null default 0, " +
    //		KEY_DESCRIPTION + " text, " +
    //		KEY_BOOK_GENRE + " text, " +
    //		KEY_BOOK_DATE_ADDED + " datetime default current_timestamp, " +
    //		DOM_GOODREADS_BOOK_ID.getDefinition(true) + ", " +
    //		DOM_BOOK_UUID.getDefinition(true) +
    //		")";
    //
    //private static final String DATABASE_CREATE_BOOKS_69 =
    //		"create table " + DB_TB_BOOKS +
    //		" (_id integer primary key autoincrement, " +
    //		/* KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + */
    //		KEY_TITLE + " text not null, " +
    //		KEY_ISBN + " text, " +
    //		KEY_PUBLISHER + " text, " +
    //		KEY_BOOK_DATE_PUBLISHED + " date, " +
    //		KEY_BOOK_RATING + " float not null default 0, " +
    //		KEY_BOOK_READ + " boolean not null default 0, " +
    //		/* KEY_SERIES + " text, " + */
    //		KEY_BOOK_PAGES + " int, " +
    //		/* KEY_SERIES_NUM + " text, " + */
    //		KEY_NOTES + " text, " +
    //		KEY_BOOK_LIST_PRICE + " text, " +
    //		KEY_IS_ANTHOLOGY + " int not null default " + ANTHOLOGY_NO + ", " +
    //		KEY_BOOK_LOCATION + " text, " +
    //		KEY_BOOK_READ_START + " date, " +
    //		KEY_BOOK_READ_END + " date, " +
    //		KEY_BOOK_FORMAT + " text, " +
    //		KEY_BOOK_SIGNED + " boolean not null default 0, " +
    //		KEY_DESCRIPTION + " text, " +
    //		KEY_BOOK_GENRE + " text, " +
    //		KEY_BOOK_DATE_ADDED + " datetime default current_timestamp, " +
    //		DOM_GOODREADS_BOOK_ID.getDefinition(true) +
    //		")";

    private static final String DATABASE_CREATE_BOOKS_68 =
            "create table " + DB_TB_BOOKS +
                    " (_id integer primary key autoincrement, " +
                    /* KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + */
                    DOM_TITLE + " text not null, " +
                    DOM_BOOK_ISBN + " text, " +
                    DOM_PUBLISHER + " text, " +
                    DOM_DATE_PUBLISHED + " date, " +
                    DOM_BOOK_RATING + " float not null default 0, " +
                    DOM_BOOK_READ + " boolean not null default 0, " +
                    /* DOM_SERIES + " text, " + */
                    DOM_BOOK_PAGES + " int, " +
                    /* KEY_SERIES_NUM + " text, " + */
                    DOM_BOOK_NOTES + " text, " +
                    DOM_BOOK_LIST_PRICE + " text, " +
                    DOM_ANTHOLOGY_MASK + " int not null default " + TableInfo.ColumnInfo.ANTHOLOGY_NO + ", " +
                    DOM_BOOK_LOCATION + " text, " +
                    DOM_BOOK_READ_START + " date, " +
                    DOM_BOOK_READ_END + " date, " +
                    DOM_BOOK_FORMAT + " text, " +
                    DOM_BOOK_SIGNED + " boolean not null default 0, " +
                    DOM_DESCRIPTION + " text, " +
                    DOM_BOOK_GENRE + " text, " +
                    DOM_BOOK_DATE_ADDED + " datetime default current_timestamp" +
                    ")";
    private static final String DATABASE_CREATE_BOOKS_63 =
            "create table " + DB_TB_BOOKS +
                    " (_id integer primary key autoincrement, " +
                    /* KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + */
                    DOM_TITLE + " text not null, " +
                    DOM_BOOK_ISBN + " text, " +
                    DOM_PUBLISHER + " text, " +
                    DOM_DATE_PUBLISHED + " date, " +
                    DOM_BOOK_RATING + " float not null default 0, " +
                    DOM_BOOK_READ + " boolean not null default 0, " +
                    /* KEY_SERIES + " text, " + */
                    DOM_BOOK_PAGES + " int, " +
                    /* KEY_SERIES_NUM + " text, " + */
                    DOM_BOOK_NOTES + " text, " +
                    DOM_BOOK_LIST_PRICE + " text, " +
                    DOM_ANTHOLOGY_MASK + " int not null default " + TableInfo.ColumnInfo.ANTHOLOGY_NO + ", " +
                    DOM_BOOK_LOCATION + " text, " +
                    DOM_BOOK_READ_START + " date, " +
                    DOM_BOOK_READ_END + " date, " +
                    DOM_BOOK_FORMAT + " text, " +
                    DOM_BOOK_SIGNED + " boolean not null default 0, " +
                    DOM_DESCRIPTION + " text, " +
                    DOM_BOOK_GENRE + " text " +
                    ")";
    private static final String DATABASE_CREATE_BOOK_SERIES_54 =
            "create table " + DB_TB_BOOK_SERIES + "(" +
                    DOM_BOOK_ID + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE, " +
                    DOM_SERIES_ID + " integer REFERENCES " + DB_TB_SERIES + " ON DELETE SET NULL ON UPDATE CASCADE, " +
                    DOM_SERIES_NUM + " integer, " +
                    DOM_BOOK_SERIES_POSITION + " integer," +
                    "PRIMARY KEY(" + DOM_BOOK_ID + ", "  + DOM_BOOK_SERIES_POSITION + ")" +
                    ")";

    private static final String DATABASE_CREATE_BOOKS_V41 =
            "create table " + DB_TB_BOOKS +
                    " (_id integer primary key autoincrement, " +
                    DOM_AUTHOR_ID + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " +
                    DOM_TITLE + " text not null, " +
                    DOM_BOOK_ISBN + " text, " +
                    DOM_PUBLISHER + " text, " +
                    DOM_DATE_PUBLISHED + " date, " +
                    DOM_BOOK_RATING + " float not null default 0, " +
                    DOM_BOOK_READ + " boolean not null default 'f', " +
                    OLD_KEY_SERIES + " text, " +
                    DOM_BOOK_PAGES + " int, " +
                    DOM_SERIES_NUM + " text, " +
                    DOM_BOOK_NOTES + " text, " +
                    DOM_BOOK_LIST_PRICE + " text, " +
                    DOM_ANTHOLOGY_MASK + " int not null default " + TableInfo.ColumnInfo.ANTHOLOGY_NO + ", " +
                    DOM_BOOK_LOCATION + " text, " +
                    DOM_BOOK_READ_START + " date, " +
                    DOM_BOOK_READ_END + " date, " +
                    KEY_AUDIOBOOK_V41 + " boolean not null default 'f', " +
                    DOM_BOOK_SIGNED + " boolean not null default 'f' " +
                    ")";
    //</editor-fold>

    private static String mMessage = "";
    public static String getMessage() {
        return mMessage;
    }

    /**
     * Provide a safe table copy method that is insulated from risks associated with column reordering. This
     * method will copy ALL columns from the source to the destination; if columns do not exist in the
     * destination, an error will occur. Columns in the destination that are not in the source will be
     * defaulted or set to NULL if no default is defined.
     *
     * ENHANCE: allow an exclusion list to be added as a parameter so the some 'from' columns can be ignored.
     *
     * @param sdb       the database
     * @param from      from table
     * @param to        to table
     * @param toRemove	List of fields to be removed from the source table (ignored in copy)
     */
    static void copyTableSafely(@NonNull final DbSync.SynchronizedDb sdb,
                                @NonNull final String from,
                                @NonNull final String to,
                                @NonNull final String... toRemove) {
        // Get the source info
        TableInfo src = new TableInfo(sdb, from);
        // Build the column list
        StringBuilder cols = new StringBuilder();
        boolean first = true;
        for(TableInfo.ColumnInfo ci: src) {
            boolean isNeeded = true;
            for(String s: toRemove) {
                if (s.equalsIgnoreCase(ci.name)) {
                    isNeeded = false;
                    break;
                }
            }
            if (isNeeded) {
                if (first) {
                    first = false;
                } else {
                    cols.append(", ");
                }
                cols.append(ci.name);
            }
        }
        String colList = cols.toString();
        String sql = "Insert into " + to + "(" + colList + ") select " + colList + " from " + from;
        sdb.execSQL(sql);
    }

    static void recreateAndReloadTable(@NonNull final DbSync.SynchronizedDb db,
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
     * For the upgrade to version 4 (db version 72), all cover files were renamed based on the hash value in
     * the books table to avoid future collisions.
     *
     * This routine renames all files, if they exist.
     */
    private static void v72_renameIdFilesToHash(@NonNull final DbSync.SynchronizedDb db) {
        String sql = "select " + DOM_ID + ", " + DOM_BOOK_UUID + " from " + DB_TB_BOOKS + " Order by " + DOM_ID;
        try (Cursor c = db.rawQuery(sql)) {
            while (c.moveToNext()) {
                final long id = c.getLong(0);
                final String hash = c.getString(1);
                StorageUtils.renameFile(StorageUtils.getCoverFile(Long.toString(id)), StorageUtils.getCoverFile(hash));
            }
        }
    }

    public static int doUpgrade(@NonNull final SQLiteDatabase db,
                                @NonNull final DbSync.SynchronizedDb syncedDb,
                                final int oldVersion) {
        int curVersion = oldVersion;

        if (curVersion == 11) {
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + DOM_SERIES_NUM + " text");
            db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + DOM_SERIES_NUM + " = ''");
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
            mMessage += "* This message will now appear whenever you upgrade\n\n";
            mMessage += "* Various SQL bugs have been resolved\n\n";
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
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + DOM_BOOK_NOTES + " text");
            db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + DOM_BOOK_NOTES + " = ''");
        }
        if (curVersion == 20) {
            curVersion++;
            db.execSQL(DATABASE_CREATE_LOAN);
            //createIndices(db); // All createIndices prior to the latest have been removed
        }
        if (curVersion == 21) {
            //do nothing
            curVersion++;
        }
        if (curVersion == 22) {
            curVersion++;
            mMessage += "* There is a new tabbed 'edit' interface to simplify editing books.\n\n";
            mMessage += "* The new comments tab also includes a notes field where you can add personal notes for any book (Requested by Luke).\n\n";
            mMessage += "* The new loaned books tab allows you to record books loaned to friends. This will lookup your phone contacts to pre-populate the list (Requested by Luke)\n\n";
            mMessage += "* Scanned books that already exist in the database (based on ISBN) will no longer be added (Identified by Colin)\n\n";
            mMessage += "* After adding a book, the main view will now scroll to a appropriate location. \n\n";
            mMessage += "* Searching has been made significantly faster.\n\n";
        }
        if (curVersion == 23) {
            //do nothing
            curVersion++;
        }
        if (curVersion == 24) {
            curVersion++;
            try {
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + DOM_BOOK_NOTES + " text");
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
            try {
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + DOM_BOOK_NOTES + " = ''");
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
            try {
                db.execSQL(DATABASE_CREATE_LOAN);
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
				/*
				try {
					//createIndices(db); // All createIndices prior to the latest have been removed
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
				*/
        }
        if (curVersion == 25) {
            //do nothing
            curVersion++;
            mMessage += "* Your sort order will be automatically saved when to close the application (Requested by Martin)\n\n";
            mMessage += "* There is a new 'about this app' view available from the administration tabs (Also from Martin)\n\n";
        }
        if (curVersion == 26) {
            //do nothing
            curVersion++;
            mMessage += "* There are two additional sort functions, by series and by loaned (Request from N4ppy)\n\n";
            mMessage += "* Your bookshelf and current location will be saved when you exit (Feedback from Martin)\n\n";
            mMessage += "* Minor interface improvements when sorting by title \n\n";
        }
        if (curVersion == 27) {
            //do nothing
            curVersion++;
            mMessage += "* The book thumbnail now appears in the list view\n\n";
            mMessage += "* Emailing the developer now works from the admin page\n\n";
            mMessage += "* The Change Bookshelf option is now more obvious (Thanks Mike)\n\n";
            mMessage += "* The exports have been renamed to csv, use the correct published date and are now unicode safe (Thanks Mike)\n\n";
        }
        if (curVersion == 28) {
            curVersion++;
            try {
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + DOM_BOOK_LIST_PRICE + " text");
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
        }
        if (curVersion == 29) {
            //do nothing
            curVersion++;
            mMessage += "* Adding books will now (finally) search Amazon\n\n";
            mMessage += "* A field for list price has been included (Requested by Brenda)\n\n";
            mMessage += "* You can bulk update the thumbnails for all books with ISBN's from the Admin page\n\n";
        }
        if (curVersion == 30) {
            //do nothing
            curVersion++;
            mMessage += "* You can now delete individual thumbnails by holding on the image and selecting delete.\n\n";
        }
        if (curVersion == 31) {
            //do nothing
            curVersion++;
            mMessage += "* There is a new Admin option (Field Visibility) to hide unused fields\n\n";
            mMessage += "* 'All Books' should now be saved as a bookshelf preference correctly\n\n";
            mMessage += "* When adding books the bookshelf will default to your currently selected bookshelf (Thanks Martin)\n\n";
        }
        if (curVersion == 32) {
            //do nothing
            curVersion++;
            try {
                db.execSQL(DATABASE_CREATE_ANTHOLOGY_V82);
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
				/*
				try {
					//createIndices(db); // All createIndices prior to the latest have been removed
				} catch (Exception e) {
					Logger.logError(e);
					throw new RuntimeException("Failed to upgrade database", e);
				}
				*/
            try {
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + DOM_ANTHOLOGY_MASK + " int not null default " + TableInfo.ColumnInfo.ANTHOLOGY_NO);
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
            mMessage += "* There is now support to record books as anthologies and it's titles. \n\n";
            mMessage += "* There is experimental support to automatically populate the anthology titles \n\n";
            mMessage += "* You can now take photos for the book cover (long click on the thumbnail in edit) \n\n";
        }
        if (curVersion == 33) {
            //do nothing
            curVersion++;
            mMessage += "* Minor enhancements\n\n";
            mMessage += "* Online help has been written\n\n";
            mMessage += "* Thumbnails can now be hidden just like any other field (Thanks Martin)\n\n";
            mMessage += "* You can also rotate thumbnails; useful for thumbnails taken with the camera\n\n";
            mMessage += "* Bookshelves will appear in the menu immediately (Thanks Martin/Julia)\n\n";
        }
        if (curVersion == 34) {
            curVersion++;
            try {
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + DOM_BOOK_LOCATION + " text");
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + DOM_BOOK_READ_START + " date");
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + DOM_BOOK_READ_END + " date");
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + OLD_KEY_AUDIOBOOK + " boolean not null default 'f'");
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + DOM_BOOK_SIGNED + " boolean not null default 'f'");
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
        }
        if (curVersion == 35) {
            curVersion++;
            try {
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + DOM_BOOK_LOCATION + "=''");
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + DOM_BOOK_READ_START + "=''");
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + DOM_BOOK_READ_END + "=''");
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + OLD_KEY_AUDIOBOOK + "='f'");
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + DOM_BOOK_SIGNED + "='f'");
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
        }
        if (curVersion == 36) {
            //do nothing
            curVersion++;
            mMessage += "* Fixed several crashing defects when adding books\n\n";
            mMessage += "* Added Autocompletion Location Field (For Cam)\n\n";
            mMessage += "* Added Read Start & Read End Fields (For Robert)\n\n";
            mMessage += "* Added an Audio Book Checkbox Field (For Ted)\n\n";
            mMessage += "* Added a Book Signed Checkbox Field (For me)\n\n";
            mMessage += "*** Don't forget you can hide any of the new fields that you do not want to see.\n\n";
            mMessage += "* Series Number now support decimal figures (Requested by Beth)\n\n";
            mMessage += "* List price now support decimal figures (Requested by eleavings)\n\n";
            mMessage += "* Fixed Import Crashes (Thanks Roydalg) \n\n";
            mMessage += "* Fixed several defects for Android 1.6 users - I do not have a 1.6 device to test on so please let me know if you discover any errors\n\n";
        }
        if (curVersion == 37) {
            //do nothing
            curVersion++;
            mMessage += "Tip: If you long click on a book title on the main list you can delete it\n\n";
            mMessage += "Tip: If you want to see all books, change the bookshelf to 'All Books'\n\n";
            mMessage += "Tip: You can find the correct barcode for many modern paperbacks on the inside cover\n\n";
            mMessage += "* There is now a 'Sort by Unread' option, as well as a 'read' icon on the main list (requested by Angel)\n\n";
            mMessage += "* If you long click on the (?) thumbnail you can now select a new thumbnail from the gallery (requested by Giovanni)\n\n";
            mMessage += "* Bookshelves, loaned books and anthology titles will now import correctly\n\n";
        }
        if (curVersion == 38) {
            //do nothing
            curVersion++;
            db.execSQL("DELETE FROM " + DB_TB_LOAN + " WHERE (" + DOM_LOANED_TO + "='' OR " + DOM_LOANED_TO + "='null')");
        }
        if (curVersion == 39) {
            curVersion++;
        }
        if (curVersion == 40) {
            curVersion++;
        }
        if (curVersion == 41) {
            //do nothing
            curVersion++;
            mMessage += "Tip: You can find the correct barcode for many modern paperbacks on the inside cover\n\n";
            mMessage += "* Added app2sd support (2.2 users only)\n\n";
            mMessage += "* You can now assign books to multiple bookshelves (requested by many people)\n\n";
            mMessage += "* A .nomedia file will be automatically created which will stop the thumbnails showing up in the gallery (thanks Brandon)\n\n";
            mMessage += "* The 'Add Book by ISBN' page has been redesigned to be simpler and more stable (thanks Vinikia)\n\n";
            mMessage += "* The export file is now formatted correctly (.csv) (thanks glohr)\n\n";
            mMessage += "* You will be prompted to backup your books on a regular basis \n\n";

            try {
                db.execSQL("DROP TABLE tmp1");
            } catch (Exception e) {
                // Don't really care about this
            }
            try {
                db.execSQL("DROP TABLE tmp2");
            } catch (Exception e) {
                // Don't really care about this
            }
            try {
                db.execSQL("DROP TABLE tmp3");
            } catch (Exception e) {
                // Don't really care about this
            }

            try {
                db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);
                //createIndices(db); // All createIndices prior to the latest have been removed
                db.execSQL("INSERT INTO " + DB_TB_BOOK_BOOKSHELF_WEAK + " (" + DOM_BOOK_ID + ", " + DOM_BOOKSHELF_ID + ") SELECT " + DOM_ID + ", " + DOM_BOOKSHELF_ID + " FROM " + DB_TB_BOOKS + "");
                db.execSQL("CREATE TABLE tmp1 AS SELECT _id, " + OLD_KEY_AUTHOR + ", " + DOM_TITLE + ", " + DOM_BOOK_ISBN + ", " + DOM_PUBLISHER + ", " +
                        DOM_DATE_PUBLISHED + ", " + DOM_BOOK_RATING + ", " + DOM_BOOK_READ + ", " + OLD_KEY_SERIES + ", " + DOM_BOOK_PAGES + ", " + DOM_SERIES_NUM + ", " + DOM_BOOK_NOTES + ", " +
                        DOM_BOOK_LIST_PRICE + ", " + DOM_ANTHOLOGY_MASK + ", " + DOM_BOOK_LOCATION + ", " + DOM_BOOK_READ_START + ", " + DOM_BOOK_READ_END + ", " + OLD_KEY_AUDIOBOOK + ", " +
                        DOM_BOOK_SIGNED + " FROM " + DB_TB_BOOKS);
                db.execSQL("CREATE TABLE tmp2 AS SELECT _id, " + DOM_BOOK_ID + ", " + DOM_LOANED_TO + " FROM " + DB_TB_LOAN );
                db.execSQL("CREATE TABLE tmp3 AS SELECT _id, " + DOM_BOOK_ID + ", " + OLD_KEY_AUTHOR + ", " + DOM_TITLE + ", " + DOM_BOOK_ANTHOLOGY_POSITION + " FROM " + DB_TB_ANTHOLOGY);

                db.execSQL("DROP TABLE " + DB_TB_ANTHOLOGY);
                db.execSQL("DROP TABLE " + DB_TB_LOAN);
                db.execSQL("DROP TABLE " + DB_TB_BOOKS);

                db.execSQL(DATABASE_CREATE_BOOKS_V41);
                db.execSQL(DATABASE_CREATE_LOAN);
                db.execSQL(DATABASE_CREATE_ANTHOLOGY_V82);

                db.execSQL("INSERT INTO " + DB_TB_BOOKS + " SELECT * FROM tmp1");
                db.execSQL("INSERT INTO " + DB_TB_LOAN + " SELECT * FROM tmp2");
                db.execSQL("INSERT INTO " + DB_TB_ANTHOLOGY + " SELECT * FROM tmp3");

                db.execSQL("DROP TABLE tmp1");
                db.execSQL("DROP TABLE tmp2");
                db.execSQL("DROP TABLE tmp3");
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
        }
        if (curVersion == 42) {
            //do nothing
            curVersion++;
            mMessage += "* Export bug fixed\n\n";
            mMessage += "* Further enhancements to the new ISBN screen\n\n";
            mMessage += "* Filtering by bookshelf on title view is now fixed\n\n";
            mMessage += "* There is now an <Empty Series> when sorting by Series (thanks Vinikia)\n\n";
            mMessage += "* App will now search all fields (Thanks Michael)\n\n";
        }
        if (curVersion == 43) {
            curVersion++;
            db.execSQL("DELETE FROM " + DB_TB_ANTHOLOGY + " WHERE " + DOM_ID + " IN (" +
                    "SELECT a." + DOM_ID + " FROM " + DB_TB_ANTHOLOGY + " a, " + DB_TB_ANTHOLOGY + " b " +
                    " WHERE a." + DOM_BOOK_ID + "=b." + DOM_BOOK_ID + " AND a." + OLD_KEY_AUTHOR + "=b." + OLD_KEY_AUTHOR + " " +
                    " AND a." + DOM_TITLE + "=b." + DOM_TITLE + " AND a." + DOM_ID + " > b." + DOM_ID + "" +
                    ")");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + DB_TB_ANTHOLOGY + " (" + DOM_BOOK_ID + ", " + OLD_KEY_AUTHOR + ", " + DOM_TITLE + ")");
        }
        if (curVersion == 44) {
            //do nothing
            curVersion++;

            try {
                db.execSQL("DROP TABLE tmp1");
            } catch (Exception e) {
                // Don't care
            }
            try {
                db.execSQL("DROP TABLE tmp2");
            } catch (Exception e) {
                // Don't care
            }
            try {
                db.execSQL("DROP TABLE tmp3");
            } catch (Exception e) {
                // Don't care
            }

            db.execSQL("CREATE TABLE tmp1 AS SELECT _id, " + OLD_KEY_AUTHOR + ", " + DOM_TITLE + ", " + DOM_BOOK_ISBN + ", " + DOM_PUBLISHER + ", " +
                    DOM_DATE_PUBLISHED + ", " + DOM_BOOK_RATING + ", " + DOM_BOOK_READ + ", " + OLD_KEY_SERIES + ", " + DOM_BOOK_PAGES + ", " + DOM_SERIES_NUM + ", " + DOM_BOOK_NOTES + ", " +
                    DOM_BOOK_LIST_PRICE + ", " + DOM_ANTHOLOGY_MASK + ", " + DOM_BOOK_LOCATION + ", " + DOM_BOOK_READ_START + ", " + DOM_BOOK_READ_END + ", " +
                    "CASE WHEN " + OLD_KEY_AUDIOBOOK + "='t' THEN 'Audiobook' ELSE 'Paperback' END AS " + OLD_KEY_AUDIOBOOK + ", " +
                    DOM_BOOK_SIGNED + " FROM " + DB_TB_BOOKS);
            db.execSQL("CREATE TABLE tmp2 AS SELECT _id, " + DOM_BOOK_ID + ", " + DOM_LOANED_TO + " FROM " + DB_TB_LOAN );
            db.execSQL("CREATE TABLE tmp3 AS SELECT _id, " + DOM_BOOK_ID + ", " + OLD_KEY_AUTHOR + ", " + DOM_TITLE + ", " + DOM_BOOK_ANTHOLOGY_POSITION + " FROM " + DB_TB_ANTHOLOGY);
            db.execSQL("CREATE TABLE tmp4 AS SELECT " + DOM_BOOK_ID + ", " + DOM_BOOKSHELF_ID + " FROM " + DB_TB_BOOK_BOOKSHELF_WEAK);

            db.execSQL("DROP TABLE " + DB_TB_ANTHOLOGY);
            db.execSQL("DROP TABLE " + DB_TB_LOAN);
            db.execSQL("DROP TABLE " + DB_TB_BOOKS);
            db.execSQL("DROP TABLE " + DB_TB_BOOK_BOOKSHELF_WEAK);

            String TMP_DATABASE_CREATE_BOOKS =
                    "create table " + DB_TB_BOOKS +
                            " (_id integer primary key autoincrement, " +
                            OLD_KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " +
                            DOM_TITLE + " text not null, " +
                            DOM_BOOK_ISBN + " text, " +
                            DOM_PUBLISHER + " text, " +
                            DOM_DATE_PUBLISHED + " date, " +
                            DOM_BOOK_RATING + " float not null default 0, " +
                            DOM_BOOK_READ + " boolean not null default 'f', " +
                            OLD_KEY_SERIES + " text, " +
                            DOM_BOOK_PAGES + " int, " +
                            DOM_SERIES_NUM + " text, " +
                            DOM_BOOK_NOTES + " text, " +
                            DOM_BOOK_LIST_PRICE + " text, " +
                            DOM_ANTHOLOGY_MASK + " int not null default " + TableInfo.ColumnInfo.ANTHOLOGY_NO + ", " +
                            DOM_BOOK_LOCATION + " text, " +
                            DOM_BOOK_READ_START + " date, " +
                            DOM_BOOK_READ_END + " date, " +
                            DOM_BOOK_FORMAT + " text, " +
                            DOM_BOOK_SIGNED + " boolean not null default 'f' " +
                            ")";


            db.execSQL(TMP_DATABASE_CREATE_BOOKS);
            db.execSQL(DATABASE_CREATE_LOAN);
            db.execSQL(DATABASE_CREATE_ANTHOLOGY_V82);
            db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);

            db.execSQL("INSERT INTO " + DB_TB_BOOKS + " SELECT * FROM tmp1");
            db.execSQL("INSERT INTO " + DB_TB_LOAN + " SELECT * FROM tmp2");
            db.execSQL("INSERT INTO " + DB_TB_ANTHOLOGY + " SELECT * FROM tmp3");
            db.execSQL("INSERT INTO " + DB_TB_BOOK_BOOKSHELF_WEAK + " SELECT * FROM tmp4");

            db.execSQL("DROP TABLE tmp1");
            db.execSQL("DROP TABLE tmp2");
            db.execSQL("DROP TABLE tmp3");
            db.execSQL("DROP TABLE tmp4");

            //createIndices(db); // All createIndices prior to the latest have been removed
        }
        if (curVersion == 45) {
            //do nothing
            curVersion++;
            db.execSQL("DELETE FROM " + DB_TB_LOAN + " WHERE " + DOM_LOANED_TO + "='null'" );
        }
        if (curVersion == 46) {
            curVersion++;
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + DOM_DESCRIPTION + " text");
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + DOM_BOOK_GENRE + " text");
        }
        if (curVersion == 47) {
            curVersion++;
            // This used to be chaecks in BookCatalogueClassic, which is no longer called on startup...
            //do_action = DO_UPDATE_FIELDS;
            mMessage += "New in v3.1\n\n";
            mMessage += "* The audiobook checkbox has been replaced with a format selector (inc. paperback, hardcover, companion etc)\n\n";
            mMessage += "* When adding books the current bookshelf will be selected as the default bookshelf\n\n";
            mMessage += "* Genre/Subject and Description fields have been added (Requested by Tosh) and will automatically populate based on Google Books and Amazon information\n\n";
            mMessage += "* The save button will always be visible on the edit book screen\n\n";
            mMessage += "* Searching for a single space will clear the search results page\n\n";
            mMessage += "* The Date Picker will now appear in a popup in order to save space on the screen (Requested by several people)\n\n";
            mMessage += "* To improve speed when sorting by title, the titles will be broken up by the first character. Remember prefixes such as 'the' and 'a' are listed after the title, e.g. 'The Trigger' becomes 'Trigger, The'\n\n";
        }
        if (curVersion == 48) {
            curVersion++;
            db.execSQL("delete from loan where loaned_to='null';");
            db.execSQL("delete from loan where _id!=(select max(l2._id) from loan l2 where l2.book=loan.book);");
            db.execSQL("delete from anthology where _id!=(select max(a2._id) from anthology a2 where a2.book=anthology.book AND a2.author=anthology.author AND a2.title=anthology.title);");
            //createIndices(db); // All createIndices prior to the latest have been removed
        }
        if (curVersion == 49) {
            curVersion++;
            mMessage += "New in v3.2\n\n";
            mMessage += "* Books can now be automatically added by searching for the author name and book title\n\n";
            mMessage += "* Updating thumbnails, genre and description fields will also search by author name and title is the isbn does not exist\n\n";				mMessage += "* Expand/Collapse all bug fixed\n\n";
            mMessage += "* The search query will be shown at the top of all search screens\n\n";
        }
        if (curVersion == 50) {
            curVersion++;
            //createIndices(db); // All createIndices prior to the latest have been removed
        }
        if (curVersion == 51) {
            curVersion++;
            mMessage += "New in v3.3 - Updates courtesy of Grunthos\n\n";
            mMessage += "* The application should be significantly faster now - Fixed a bug with database index creation\n\n";
            mMessage += "* The thumbnail can be rotated in both directions now\n\n";
            mMessage += "* You can zoom in the thumbnail to see full detail\n\n";
            mMessage += "* The help page will redirect to the, more frequently updated, online wiki\n\n";
            mMessage += "* Dollar signs in the text fields will no longer FC on import/export\n\n";
        }
        if (curVersion == 52) {
            curVersion++;
            mMessage += "New in v3.3.1\n\n";
            mMessage += "* Minor bug fixes and error logging. Please email me if you have any further issues.\n\n";
        }
        if (curVersion == 53) {
            curVersion++;
            //There is a conflict between eleybourn released branch (3.3.1) and Grunthos HEAD (3.4).
            // This is to check and skip as required
            boolean go = false;
            String checkSQL = "SELECT * FROM " + DB_TB_BOOKS;
            try (Cursor results = db.rawQuery(checkSQL, new String[]{})) {
                if (results.getCount() > 0) {
                    if (results.getColumnIndex(OLD_KEY_AUTHOR) > -1) {
                        go = true;
                    }
                }
            }

            if (go) {
                String tmpMessage = "New in v3.4 - Updates courtesy of (mainly) Grunthos (blame him, politely, if it toasts your data)\n\n" +
                        "* Multiple Authors per book\n\n" +
                        "* Multiple Series per book\n\n" +
                        "* Fetches series (and other stuff) from LibraryThing\n\n" +
                        "* Can now make global changes to Author and Series (Access this feature via long-click on the catalogue screen)\n\n" +
                        "* Can replace a cover thumbnail from a different edition via LibraryThing.\n\n" +
                        "* Does concurrent ISBN searches at Amazon, Google and LibraryThing (it's faster)\n\n" +
                        "* Displays a progress dialog while searching for a book on the internet.\n\n" +
                        "* Adding by Amazon's ASIN is supported on the 'Add by ISBN' page\n\n" +
                        "* Duplicate books allowed, with a warning message\n\n" +
                        "* User-selectable fields when reloading data from internet (eg. just update authors).\n\n" +
                        "* Unsaved edits are retained when rotating the screen.\n\n" +
                        "* Changed the ISBN data entry screen when the device is in landscape mode.\n\n" +
                        "* Displays square brackets around the series name when displaying a list of books.\n\n" +
                        "* Suggestions available when searching\n\n" +
                        "* Removed the need for contacts permission. Though it does mean you will need to manually enter everyone you want to loan to.\n\n" +
                        "* Preserves *all* open groups when closing application.\n\n" +
                        "* The scanner and book search screens remain active after a book has been added or a search fails. It was viewed that this more closely represents the work-flow of people adding or scanning books.\n\n" +
                        "See the web site (from the Admin menu) for more details\n\n";
                try {
                    mMessage += tmpMessage;

                    db.execSQL(DATABASE_CREATE_SERIES);
                    // We need to create a series table with series that are unique wrt case and unicode. The old
                    // system allowed for series with slightly different case. So we capture these by using
                    // max() to pick and arbitrary matching name to use as our canonical version.
                    db.execSQL("INSERT INTO " + DB_TB_SERIES + " (" + DOM_SERIES_NAME + ") "
                            + "SELECT name from ("
                            + "    SELECT Upper(" + OLD_KEY_SERIES + ") " + COLLATION + " as ucName, "
                            + "    max(" + OLD_KEY_SERIES + ")" + COLLATION + " as name FROM " + DB_TB_BOOKS
                            + "    WHERE Coalesce(" + OLD_KEY_SERIES + ",'') <> ''"
                            + "    Group By Upper(" + OLD_KEY_SERIES + ")"
                            + " )"
                    );

                    db.execSQL(DATABASE_CREATE_BOOK_SERIES_54);
                    db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);

                    //createIndices(db); // All createIndices prior to the latest have been removed

                    db.execSQL("INSERT INTO " + DB_TB_BOOK_SERIES + " (" + DOM_BOOK_ID + ", " + DOM_SERIES_ID + ", " + DOM_SERIES_NUM + ", " + DOM_BOOK_SERIES_POSITION + ") "
                            + "SELECT DISTINCT b." + DOM_ID + ", s." + DOM_ID + ", b." + DOM_SERIES_NUM + ", 1"
                            + " FROM " + DB_TB_BOOKS + " b "
                            + " Join " + DB_TB_SERIES + " s On Upper(s." + DOM_SERIES_NAME + ") = Upper(b." + OLD_KEY_SERIES + ")" + COLLATION
                            + " Where Coalesce(b." + OLD_KEY_SERIES + ", '') <> ''");

                    db.execSQL("INSERT INTO " + DB_TB_BOOK_AUTHOR + " (" + DOM_BOOK_ID + ", " + DOM_AUTHOR_ID + ", " + DOM_AUTHOR_POSITION + ") "
                            + "SELECT b." + DOM_ID + ", b." + OLD_KEY_AUTHOR + ", 1 FROM " + DB_TB_BOOKS + " b ");

                    String tmpFields = DOM_ID + ", " /* + KEY_AUTHOR + ", " */ + DOM_TITLE + ", " + DOM_BOOK_ISBN
                            + ", " + DOM_PUBLISHER + ", " + DOM_DATE_PUBLISHED + ", " + DOM_BOOK_RATING + ", " + DOM_BOOK_READ
                            + /* ", " + KEY_SERIES + */ ", " + DOM_BOOK_PAGES /* + ", " + KEY_SERIES_NUM */ + ", " + DOM_BOOK_NOTES
                            + ", " + DOM_BOOK_LIST_PRICE + ", " + DOM_ANTHOLOGY_MASK + ", " + DOM_BOOK_LOCATION + ", " + DOM_BOOK_READ_START
                            + ", " + DOM_BOOK_READ_END + ", " + DOM_BOOK_FORMAT + ", " + DOM_BOOK_SIGNED + ", " + DOM_DESCRIPTION
                            + ", " + DOM_BOOK_GENRE;
                    db.execSQL("CREATE TABLE tmpBooks AS SELECT " + tmpFields + " FROM " + DB_TB_BOOKS);

                    db.execSQL("DROP TABLE " + DB_TB_BOOKS);

                    db.execSQL(DATABASE_CREATE_BOOKS_63);

                    db.execSQL("INSERT INTO " + DB_TB_BOOKS + "( " + tmpFields + ")  SELECT * FROM tmpBooks");

                    db.execSQL("DROP TABLE tmpBooks");
                } catch (Exception e) {
                    Logger.logError(e);
                    throw new RuntimeException("Failed to upgrade database", e);
                }
            }
            if (curVersion == 54) {
                //createIndices(db); // All createIndices prior to the latest have been removed
                curVersion++;
            }
            if (curVersion == 55) {
                //There is a conflict between eleybourn released branch (3.3.1) and Grunthos HEAD (3.4).
                // This is to check and skip as required
                boolean go2 = false;
                String checkSQL2 = "SELECT * FROM " + DB_TB_BOOKS;
                try (Cursor results = db.rawQuery(checkSQL2, new String[]{})) {
                    if (results.getCount() > 0) {
                        if (results.getColumnIndex(OLD_KEY_AUTHOR) > -1) {
                            go2 = true;
                        }
                    } else {
                        go2 = true;
                    }
                }
                if (go2) {
                    try {
                        db.execSQL(DATABASE_CREATE_SERIES);
                        // We need to create a series table with series that are unique wrt case and unicode. The old
                        // system allowed for series with slightly different case. So we capture these by using
                        // max() to pick and arbitrary matching name to use as our canonical version.
                        db.execSQL("INSERT INTO " + DB_TB_SERIES + " (" + DOM_SERIES_NAME + ") "
                                + "SELECT name from ("
                                + "    SELECT Upper(" + OLD_KEY_SERIES + ") " + COLLATION + " as ucName, "
                                + "    max(" + OLD_KEY_SERIES + ")" + COLLATION + " as name FROM " + DB_TB_BOOKS
                                + "    WHERE Coalesce(" + OLD_KEY_SERIES + ",'') <> ''"
                                + "    Group By Upper(" + OLD_KEY_SERIES + ")"
                                + " )"
                        );

                        db.execSQL(DATABASE_CREATE_BOOK_SERIES_54);
                        db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);

                        db.execSQL("INSERT INTO " + DB_TB_BOOK_SERIES + " (" + DOM_BOOK_ID + ", " + DOM_SERIES_ID + ", " + DOM_SERIES_NUM + ", " + DOM_BOOK_SERIES_POSITION + ") "
                                + "SELECT DISTINCT b." + DOM_ID + ", s." + DOM_ID + ", b." + DOM_SERIES_NUM + ", 1"
                                + " FROM " + DB_TB_BOOKS + " b "
                                + " Join " + DB_TB_SERIES + " s On Upper(s." + DOM_SERIES_NAME + ") = Upper(b." + OLD_KEY_SERIES + ")" + COLLATION
                                + " Where Coalesce(b." + OLD_KEY_SERIES + ", '') <> ''");

                        db.execSQL("INSERT INTO " + DB_TB_BOOK_AUTHOR + " (" + DOM_BOOK_ID + ", " + DOM_AUTHOR_ID + ", " + DOM_AUTHOR_POSITION + ") "
                                + "SELECT b." + DOM_ID + ", b." + OLD_KEY_AUTHOR + ", 1 FROM " + DB_TB_BOOKS + " b ");

                        String tmpFields = DOM_ID + ", " /* + KEY_AUTHOR + ", " */ + DOM_TITLE + ", " + DOM_BOOK_ISBN
                                + ", " + DOM_PUBLISHER + ", " + DOM_DATE_PUBLISHED + ", " + DOM_BOOK_RATING + ", " + DOM_BOOK_READ
                                + /* ", " + KEY_SERIES + */ ", " + DOM_BOOK_PAGES /* + ", " + KEY_SERIES_NUM */ + ", " + DOM_BOOK_NOTES
                                + ", " + DOM_BOOK_LIST_PRICE + ", " + DOM_ANTHOLOGY_MASK + ", " + DOM_BOOK_LOCATION + ", " + DOM_BOOK_READ_START
                                + ", " + DOM_BOOK_READ_END + ", " + DOM_BOOK_FORMAT + ", " + DOM_BOOK_SIGNED + ", " + DOM_DESCRIPTION
                                + ", " + DOM_BOOK_GENRE;
                        db.execSQL("CREATE TABLE tmpBooks AS SELECT " + tmpFields + " FROM " + DB_TB_BOOKS);

                        db.execSQL("DROP TABLE " + DB_TB_BOOKS);

                        db.execSQL(DATABASE_CREATE_BOOKS_63);

                        db.execSQL("INSERT INTO " + DB_TB_BOOKS + "( " + tmpFields + ")  SELECT * FROM tmpBooks");

                        db.execSQL("DROP TABLE tmpBooks");
                    } catch (Exception e) {
                        Logger.logError(e);
                        throw new RuntimeException("Failed to upgrade database", e);
                    }
                }
                //createIndices(db); // All createIndices prior to the latest have been removed
                curVersion++;
            }
        }
        if (curVersion == 56) {
            curVersion++;
            mMessage += "New in v3.5.4\n\n";
            mMessage += "* French translation available\n\n";
            mMessage += "* There is an option to create a duplicate book (requested by Vinika)\n\n";
            mMessage += "* Fixed errors caused by failed upgrades\n\n";
            mMessage += "* Fixed errors caused by trailing spaces in bookshelf names\n\n";
        }
        if (curVersion == 57) {
            curVersion++;
            try (Cursor results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_AUTHORS + "'", new String[]{})) {
                if (results57.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_AUTHORS);
                }
            }
            try (Cursor results = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOKSHELF + "'", new String[]{})) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_BOOKSHELF);
                    db.execSQL(DATABASE_CREATE_BOOKSHELF_DATA);
                }
            }
            try (Cursor results = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_SERIES + "'", new String[]{})) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_SERIES);
                    try (Cursor results2 = db.rawQuery("SELECT * FROM " + DB_TB_BOOKS, new String[]{})) {
                        if (results2.getCount() > 0) {
                            if (results2.getColumnIndex(OLD_KEY_SERIES) > -1) {
                                db.execSQL("INSERT INTO " + DB_TB_SERIES + " (" + DOM_SERIES_NAME + ") "
                                        + "SELECT name from ("
                                        + "    SELECT Upper(" + OLD_KEY_SERIES + ") " + COLLATION + " as ucName, "
                                        + "    max(" + OLD_KEY_SERIES + ")" + COLLATION + " as name FROM " + DB_TB_BOOKS
                                        + "    WHERE Coalesce(" + OLD_KEY_SERIES + ",'') <> ''"
                                        + "    Group By Upper(" + OLD_KEY_SERIES + ")"
                                        + " )"
                                );
                            }
                        }
                    }
                }
            }
            try (Cursor results = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOKS + "'", new String[]{})) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_BOOKS_63);
                }
            }
            try (Cursor results = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_LOAN + "'", new String[]{})) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_LOAN);
                }
            }
            try (Cursor results = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_ANTHOLOGY + "'", new String[]{})) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_ANTHOLOGY_V82);
                }
            }
            try (Cursor results = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOK_BOOKSHELF_WEAK + "'", new String[]{})) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);
                }
            }
            try (Cursor results = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOK_SERIES + "'", new String[]{})) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_BOOK_SERIES_54);
                    db.execSQL("INSERT INTO " + DB_TB_BOOK_SERIES + " (" + DOM_BOOK_ID + ", " + DOM_SERIES_ID + ", " + DOM_SERIES_NUM + ", " + DOM_BOOK_SERIES_POSITION + ") "
                            + "SELECT DISTINCT b." + DOM_ID + ", s." + DOM_ID + ", b." + DOM_SERIES_NUM + ", 1"
                            + " FROM " + DB_TB_BOOKS + " b "
                            + " Join " + DB_TB_SERIES + " s On Upper(s." + DOM_SERIES_NAME + ") = Upper(b." + OLD_KEY_SERIES + ")" + COLLATION
                            + " Where Coalesce(b." + OLD_KEY_SERIES + ", '') <> ''");
                }
            }
            try (Cursor results = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOK_AUTHOR + "'", new String[]{})) {
                if (results.getCount() == 0) {
                    //table does not exist
                    db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);
                    db.execSQL("INSERT INTO " + DB_TB_BOOK_AUTHOR + " (" + DOM_BOOK_ID + ", " + DOM_AUTHOR_ID + ", " + DOM_AUTHOR_POSITION + ") "
                            + "SELECT b." + DOM_ID + ", b." + OLD_KEY_AUTHOR + ", 1 FROM " + DB_TB_BOOKS + " b ");
                }
            }
            try (Cursor results = db.rawQuery("SELECT * FROM " + DB_TB_BOOKS, new String[]{})) {
                if (results.getCount() > 0) {
                    if (results.getColumnIndex(OLD_KEY_SERIES) > -1) {
                        String tmpFields = DOM_ID + ", " /* + KEY_AUTHOR + ", " */ + DOM_TITLE + ", " + DOM_BOOK_ISBN
                                + ", " + DOM_PUBLISHER + ", " + DOM_DATE_PUBLISHED + ", " + DOM_BOOK_RATING + ", " + DOM_BOOK_READ
                                + /* ", " + KEY_SERIES + */ ", " + DOM_BOOK_PAGES /* + ", " + KEY_SERIES_NUM */ + ", " + DOM_BOOK_NOTES
                                + ", " + DOM_BOOK_LIST_PRICE + ", " + DOM_ANTHOLOGY_MASK + ", " + DOM_BOOK_LOCATION + ", " + DOM_BOOK_READ_START
                                + ", " + DOM_BOOK_READ_END + ", " + DOM_BOOK_FORMAT + ", " + DOM_BOOK_SIGNED + ", " + DOM_DESCRIPTION
                                + ", " + DOM_BOOK_GENRE;
                        db.execSQL("CREATE TABLE tmpBooks AS SELECT " + tmpFields + " FROM " + DB_TB_BOOKS);
                        db.execSQL("DROP TABLE " + DB_TB_BOOKS);
                        db.execSQL(DATABASE_CREATE_BOOKS_63);
                        db.execSQL("INSERT INTO " + DB_TB_BOOKS + "( " + tmpFields + ")  SELECT * FROM tmpBooks");
                        db.execSQL("DROP TABLE tmpBooks");
                    }
                }
            }
        }
        if (curVersion == 58) {
            curVersion++;
            db.delete(DB_TB_LOAN, "("+ DOM_BOOK_ID + "='' OR " + DOM_BOOK_ID + "=null OR " + DOM_LOANED_TO + "='' OR " + DOM_LOANED_TO + "=null) ", null);
            mMessage += "New in v3.6\n\n";
            mMessage += "* The LibraryThing Key will be verified when added\n\n";
            mMessage += "* When entering ISBN's manually, each button with vibrate the phone slightly\n\n";
            mMessage += "* When automatically updating fields click on each checkbox twice will give you the option to override existing fields (rather than populate only blank fields)\n\n";
            mMessage += "* Fixed a crash when exporting over 2000 books\n\n";
            mMessage += "* Orphan loan records will now be correctly managed\n\n";
            mMessage += "* The Amazon search will now look for English, French, German, Italian and Japanese versions\n\n";
        }
        if (curVersion == 59) {
            curVersion++;
            mMessage += "New in v3.6.2\n\n";
            mMessage += "* Optionally restrict 'Sort By Author' to only the first Author (where there are multiple listed)\n\n";
            mMessage += "* Minor bug fixes\n\n";
        }
        if (curVersion == 60) {
            curVersion++;
            mMessage += "New in v3.7\n\n";
            mMessage += "Hint: The export function will create an export.csv file on the sdcard\n\n";
            mMessage += "* You can crop cover thumbnails (both from the menu and after taking a camera image)\n\n";
            mMessage += "* You can tweet about a book directly from the book edit screen.\n\n";
            mMessage += "* Sort by Date Published added\n\n";
            mMessage += "* Will check for network connection prior to searching for details (new permission required)\n\n";
            mMessage += "* Fixed crash when opening search results\n\n";
        }
        if (curVersion == 61) {
            curVersion++;
            mMessage += "New in v3.8\n\n";
            mMessage += "* Fixed several defects (including multiple author's and author prefix/suffix's)\n\n";
            mMessage += "* Fixed issue with thumbnail resolutions from LibraryThing\n\n";
            mMessage += "* Changed the 'Add Book' menu options to be submenu\n\n";
            mMessage += "* The database backup has been renamed for clarity\n\n";
        }
        if (curVersion == 62) {
            curVersion++;
        }
        if (curVersion == 63) {
            // Fix up old default 'f' values to be 0 (true = 1).
            curVersion++;
            db.execSQL("UPDATE " + DB_TB_BOOKS + " Set " + DOM_BOOK_READ + " = 0 Where " + DOM_BOOK_READ + " = 'f'");
            db.execSQL("UPDATE " + DB_TB_BOOKS + " Set " + DOM_BOOK_READ + " = 1 Where " + DOM_BOOK_READ + " = 't'");
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " RENAME TO books_tmp");
            db.execSQL(DATABASE_CREATE_BOOKS_63);
            db.execSQL("INSERT INTO " + DB_TB_BOOKS + " Select * FROM books_tmp");
            db.execSQL("DROP TABLE books_tmp");
        }
        if (curVersion == 64) {
            // Changed SIGNED to boolean {0,1} and added DATE_ADDED
            // Fix up old default 'f' values to be 0 (true = 1).
            curVersion++;
            db.execSQL("UPDATE " + DB_TB_BOOKS + " Set " + DOM_BOOK_SIGNED + " = 0 Where " + DOM_BOOK_SIGNED + " = 'f'");
            db.execSQL("UPDATE " + DB_TB_BOOKS + " Set " + DOM_BOOK_SIGNED + " = 1 Where " + DOM_BOOK_SIGNED + " = 't'");
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " Add " + DOM_BOOK_DATE_ADDED + " datetime"); // Just want a null value for old records
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " RENAME TO books_tmp");
            db.execSQL(DATABASE_CREATE_BOOKS_68);
            db.execSQL("INSERT INTO " + DB_TB_BOOKS + " Select * FROM books_tmp");
            db.execSQL("DROP TABLE books_tmp");
        }



        if (curVersion == 65) {
            curVersion++;
            DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS.drop(syncedDb);
            DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS.create(syncedDb, true);
            DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS.createIndices(syncedDb);
        }
        if (curVersion == 66) {
            curVersion++;
            DatabaseDefinitions.TBL_BOOKS_FTS.drop(syncedDb);
            DatabaseDefinitions.TBL_BOOKS_FTS.create(syncedDb, false);
            StartupActivity.scheduleFtsRebuild();
        }
        if (curVersion == 67) {
            curVersion++;
            DatabaseDefinitions.TBL_BOOK_LIST_STYLES.drop(syncedDb);
            DatabaseDefinitions.TBL_BOOK_LIST_STYLES.createAll(syncedDb, true);
        }
        if (curVersion == 68) {
            curVersion++;
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " Add " + DOM_GOODREADS_BOOK_ID.getDefinition(true));
        }
        if (curVersion == 69 || curVersion == 70) {
            curVersion = 71;
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " RENAME TO books_tmp");
            db.execSQL(DATABASE_CREATE_BOOKS_81);
            copyTableSafely(syncedDb, "books_tmp", DB_TB_BOOKS);
            db.execSQL("DROP TABLE books_tmp");
        }
        if (curVersion == 71) {
            curVersion++;
            v72_renameIdFilesToHash(syncedDb);
            // A bit of presumption here...
            mMessage += "New in v4.0 - Updates courtesy of (mainly) Philip Warner (a.k.a Grunthos) -- blame him, politely, if it toasts your data\n\n";
            mMessage += "* New look, new startup page\n\n";
            mMessage += "* Synchronization with goodreads (www.goodreads.com)\n\n";
            mMessage += "* New styles for book lists (including 'Compact' and 'Unread')\n\n";
            mMessage += "* User-defined styles for book lists\n\n";
            mMessage += "* More efficient memory usage\n\n";
            mMessage += "* New preferences, including 'always expanded/collapsed' lists\n\n";
            mMessage += "* Faster expand/collapse with large collections\n\n";
            mMessage += "* Cached covers for faster scrolling lists\n\n";
            mMessage += "* Cover images now have globally unique names, and books have globally unique IDs, so sharing and combining collections is easy\n\n";
            mMessage += "* Improved detection of series names\n\n";
        }
        if (curVersion == 72) {
            curVersion++;
            // Just to get the triggers applied
        }

        if (curVersion == 73) {
            //do nothing
            curVersion++;
            mMessage += "New in v4.0.1 - many bugs fixed\n\n";
            mMessage += "* Added a preference to completely disable background bitmap\n\n";
            mMessage += "* Added book counts to book lists\n\n";
            mMessage += "Thank you to all those people who reported bugs.\n\n";
        }

        if (curVersion == 74) {
            //do nothing
            curVersion++;
            StartupActivity.scheduleAuthorSeriesFixUp();
            mMessage += "New in v4.0.3\n\n";
            mMessage += "* ISBN validation when searching/scanning and error beep when scanning (with preference to turn it off)\n\n";
            mMessage += "* 'Loaned' list now shows available books under the heading 'Available'\n\n";
            mMessage += "* Added preference to hide list headers (for small phones)\n\n";
            mMessage += "* Restored functionality to use current bookshelf when adding book\n\n";
            mMessage += "* FastScroller sizing improved\n\n";
            mMessage += "* Several bugs fixed\n\n";
            mMessage += "Thank you to all those people who reported bugs and helped tracking them down.\n\n";
        }

        if (curVersion == 75) {
            //do nothing
            curVersion++;
            StartupActivity.scheduleFtsRebuild();
            mMessage += "New in v4.0.4\n\n";
            mMessage += "* Search now searches series and anthology data\n\n";
            mMessage += "* Allows non-numeric data entry in series position\n\n";
            mMessage += "* Better sorting of leading numerical in series position\n\n";
            mMessage += "* Several bug fixes\n\n";
        }

        if (curVersion == 76) {
            //do nothing
            curVersion++;
        }
        if (curVersion == 77) {
            //do nothing
            curVersion++;
            mMessage += "New in v4.0.6\n\n";
            mMessage += "* When adding books, scanning or typing an existing ISBN allows you the option to edit the book.\n";
            mMessage += "* Allow ASINs to be entered manually as well as ISBNs\n";
            mMessage += "* German translation updates (Robert Wetzlmayr)\n";
        }
        if (curVersion == 78) {
            //do nothing
            curVersion++;
            mMessage += "New in v4.1\n\n";
            mMessage += "* New style groups: Location and Date Read\n";
            mMessage += "* Improved 'Share' functionality (filipeximenes)\n";
            mMessage += "* French translation updates (Djiko)\n";
            mMessage += "* Better handling of the 'back' key when editing books (filipeximenes)\n";
            mMessage += "* Various bug fixes\n";
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
            db.execSQL("ALTER TABLE " + DB_TB_BOOK_SERIES + " RENAME TO " + tempName);
            db.execSQL(DATABASE_CREATE_BOOK_SERIES);
            copyTableSafely(syncedDb, tempName, DB_TB_BOOK_SERIES);
            db.execSQL("DROP TABLE " + tempName);
        }

        return curVersion;
    }


}
