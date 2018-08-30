package com.eleybourn.bookcatalogue.database.dbaadapter;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.DbSync;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;

import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LANGUAGE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LAST_GOODREADS_SYNC_DATE;
import static com.eleybourn.bookcatalogue.booklist.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames.*;


/**
 * This is a specific version of the SQLiteOpenHelper class. It handles onCreate and onUpgrade events
 *
 * @author evan
 */
@SuppressWarnings("unused")
public class DatabaseHelper extends SQLiteOpenHelper {

    //TODO: Update database version RELEASE: Update database version
    private static final int DATABASE_VERSION = 82;

    /** same object as in the {@link CatalogueDBAdapter} , only ever set here in our constructor*/
    private static DbSync.Synchronizer mSynchronizer;

    private static boolean mDbWasCreated;

    private static String message = "";
    public static String getMessage() {
        return message;
    }

    private DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    private DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version, DatabaseErrorHandler errorHandler) {
        super(context, name, factory, version, errorHandler);
    }

    public DatabaseHelper(Context context,
                          SQLiteDatabase.CursorFactory mTrackedCursorFactory,
                          DbSync.Synchronizer synchronizer) {
        super(context, StorageUtils.getDatabaseName(), mTrackedCursorFactory, DATABASE_VERSION);
        mSynchronizer = synchronizer;
    }

    /**
     * @return a boolean indicating if this was a new install
     */
    public boolean isNewInstall() {
        return mDbWasCreated;
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
    private static void copyTableSafely(DbSync.SynchronizedDb sdb, String from, String to, String... toRemove) {
        // Get the source info
        TableInfo src = new TableInfo(sdb, from);
        // Build the column list
        StringBuilder cols = new StringBuilder();
        boolean first = true;
        for(ColumnInfo ci: src) {
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

    private static void recreateAndReloadTable(DbSync.SynchronizedDb db, String tableName, String createStatement, String...toRemove) {
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
    private static void renameIdFilesToHash(DbSync.SynchronizedDb db) {
        String sql = "select " + KEY_ROWID + ", " + DOM_BOOK_UUID + " from " + DB_TB_BOOKS + " Order by " + KEY_ROWID;
        Cursor c = db.rawQuery(sql);
        try {
            while (c.moveToNext()) {
                final long id = c.getLong(0);
                final String hash = c.getString(1);
                File f = CatalogueDBAdapter.fetchThumbnailByName(Long.toString(id),"");
                if ( f.exists() ) {
                    File newFile = CatalogueDBAdapter.fetchThumbnailByUuid(hash);
                    //noinspection ResultOfMethodCallIgnored
                    f.renameTo(newFile);
                }
            }
        } finally {
            if (c != null)
                c.close();
        }
    }
    // We tried 'Collate UNICODE' but it seemed to be case sensitive. We ended
    // up with 'Ursula Le Guin' and 'Ursula le Guin'.
    //
    //	We now use Collate LOCALE and check to see if it is case sensitive. We *hope* in the
    // future Android will add LOCALE_CI (or equivalent).
    //
    //public static final String COLLATION = "Collate NOCASE";
    //public static final String COLLATION = " Collate UNICODE ";
    public static final String COLLATION = " Collate LOCALIZED "; // NOTE: Important to have start/end spaces!


    /* private database variables as static reference */
    public static final String DB_TB_BOOKS = "books";
    public static final String DB_TB_BOOK_AUTHOR = "book_author";
    public static final String DB_TB_BOOK_BOOKSHELF_WEAK = "book_bookshelf_weak";
    public static final String DB_TB_BOOK_SERIES = "book_series";
    public static final String DB_TB_ANTHOLOGY = "anthology";
    public static final String DB_TB_AUTHORS = "authors";
    public static final String DB_TB_BOOKSHELF = "bookshelf";
    public static final String DB_TB_LOAN = "loan";
    public static final String DB_TB_SERIES = "series";

    public static final int ANTHOLOGY_NO = 0;
    public static final int ANTHOLOGY_IS_ANTHOLOGY = 1;
    public static final int ANTHOLOGY_MULTIPLE_AUTHORS = 2;


    /* Database creation sql statement */
    private static final String DATABASE_CREATE_AUTHORS =
            "create table " + DB_TB_AUTHORS +
                    " (_id integer primary key autoincrement, " +
                    KEY_FAMILY_NAME + " text not null, " +
                    KEY_GIVEN_NAMES + " text not null" +
                    ")";

    private static final String DATABASE_CREATE_BOOKSHELF =
            "create table " + DB_TB_BOOKSHELF +
                    " (_id integer primary key autoincrement, " +
                    KEY_BOOKSHELF + " text not null " +
                    ")";

    private static final String DATABASE_CREATE_BOOKSHELF_DATA =
            "INSERT INTO " + DB_TB_BOOKSHELF +
                    " (" + KEY_BOOKSHELF + ") VALUES ('Default')";

    // Renamed to the LAST version in which it was used
    private static final String DATABASE_CREATE_BOOKS_81 =
            "create table " + DB_TB_BOOKS +
                    " (_id integer primary key autoincrement, " +
                    /* KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + */
                    KEY_TITLE + " text not null, " +
                    KEY_ISBN + " text, " +
                    KEY_PUBLISHER + " text, " +
                    KEY_DATE_PUBLISHED + " date, " +
                    KEY_RATING + " float not null default 0, " +
                    KEY_READ + " boolean not null default 0, " +
                    /* KEY_SERIES + " text, " + */
                    KEY_PAGES + " int, " +
                    /* KEY_SERIES_NUM + " text, " + */
                    KEY_NOTES + " text, " +
                    KEY_LIST_PRICE + " text, " +
                    KEY_ANTHOLOGY_MASK + " int not null default " + ANTHOLOGY_NO + ", " +
                    KEY_LOCATION + " text, " +
                    KEY_READ_START + " date, " +
                    KEY_READ_END + " date, " +
                    KEY_FORMAT + " text, " +
                    KEY_SIGNED + " boolean not null default 0, " +
                    KEY_DESCRIPTION + " text, " +
                    KEY_GENRE + " text, " +
                    KEY_DATE_ADDED + " datetime default current_timestamp, " +
                    DOM_GOODREADS_BOOK_ID.getDefinition(true) + ", " +
                    DOM_LAST_GOODREADS_SYNC_DATE.getDefinition(true) + ", " +
                    DOM_BOOK_UUID.getDefinition(true) + ", " +
                    DOM_LAST_UPDATE_DATE.getDefinition(true) +
                    ")";

    // NOTE: **NEVER** change this. Rename it, and create a new one. Unless you know what you are doing.
    private static final String DATABASE_CREATE_BOOKS =
            "create table " + DB_TB_BOOKS +
                    " (_id integer primary key autoincrement, " +
                    /* KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + */
                    KEY_TITLE + " text not null, " +
                    KEY_ISBN + " text, " +
                    KEY_PUBLISHER + " text, " +
                    KEY_DATE_PUBLISHED + " date, " +
                    KEY_RATING + " float not null default 0, " +
                    KEY_READ + " boolean not null default 0, " +
                    /* KEY_SERIES + " text, " + */
                    KEY_PAGES + " int, " +
                    /* KEY_SERIES_NUM + " text, " + */
                    KEY_NOTES + " text, " +
                    KEY_LIST_PRICE + " text, " +
                    KEY_ANTHOLOGY_MASK + " int not null default " + ANTHOLOGY_NO + ", " +
                    KEY_LOCATION + " text, " +
                    KEY_READ_START + " date, " +
                    KEY_READ_END + " date, " +
                    KEY_FORMAT + " text, " +
                    KEY_SIGNED + " boolean not null default 0, " +
                    KEY_DESCRIPTION + " text, " +
                    KEY_GENRE + " text, " +
                    DOM_LANGUAGE.getDefinition(true) + ", " + // Added in version 82
                    KEY_DATE_ADDED + " datetime default current_timestamp, " +
                    DOM_GOODREADS_BOOK_ID.getDefinition(true) + ", " +
                    DOM_LAST_GOODREADS_SYNC_DATE.getDefinition(true) + ", " +
                    DOM_BOOK_UUID.getDefinition(true) + ", " +
                    DOM_LAST_UPDATE_DATE.getDefinition(true) +
                    ")";
    // ^^^^ NOTE: **NEVER** change this. Rename it, and create a new one. Unless you know what you are doing.

    //private static final String DATABASE_CREATE_BOOKS_70 =
    //		"create table " + DB_TB_BOOKS +
    //		" (_id integer primary key autoincrement, " +
    //		/* KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + */
    //		KEY_TITLE + " text not null, " +
    //		KEY_ISBN + " text, " +
    //		KEY_PUBLISHER + " text, " +
    //		KEY_DATE_PUBLISHED + " date, " +
    //		KEY_RATING + " float not null default 0, " +
    //		KEY_READ + " boolean not null default 0, " +
    //		/* KEY_SERIES + " text, " + */
    //		KEY_PAGES + " int, " +
    //		/* KEY_SERIES_NUM + " text, " + */
    //		KEY_NOTES + " text, " +
    //		KEY_LIST_PRICE + " text, " +
    //		KEY_ANTHOLOGY + " int not null default " + ANTHOLOGY_NO + ", " +
    //		KEY_LOCATION + " text, " +
    //		KEY_READ_START + " date, " +
    //		KEY_READ_END + " date, " +
    //		KEY_FORMAT + " text, " +
    //		KEY_SIGNED + " boolean not null default 0, " +
    //		KEY_DESCRIPTION + " text, " +
    //		KEY_GENRE + " text, " +
    //		KEY_DATE_ADDED + " datetime default current_timestamp, " +
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
    //		KEY_DATE_PUBLISHED + " date, " +
    //		KEY_RATING + " float not null default 0, " +
    //		KEY_READ + " boolean not null default 0, " +
    //		/* KEY_SERIES + " text, " + */
    //		KEY_PAGES + " int, " +
    //		/* KEY_SERIES_NUM + " text, " + */
    //		KEY_NOTES + " text, " +
    //		KEY_LIST_PRICE + " text, " +
    //		KEY_ANTHOLOGY + " int not null default " + ANTHOLOGY_NO + ", " +
    //		KEY_LOCATION + " text, " +
    //		KEY_READ_START + " date, " +
    //		KEY_READ_END + " date, " +
    //		KEY_FORMAT + " text, " +
    //		KEY_SIGNED + " boolean not null default 0, " +
    //		KEY_DESCRIPTION + " text, " +
    //		KEY_GENRE + " text, " +
    //		KEY_DATE_ADDED + " datetime default current_timestamp, " +
    //		DOM_GOODREADS_BOOK_ID.getDefinition(true) +
    //		")";

    private static final String DATABASE_CREATE_BOOKS_68 =
            "create table " + DB_TB_BOOKS +
                    " (_id integer primary key autoincrement, " +
                    /* KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + */
                    KEY_TITLE + " text not null, " +
                    KEY_ISBN + " text, " +
                    KEY_PUBLISHER + " text, " +
                    KEY_DATE_PUBLISHED + " date, " +
                    KEY_RATING + " float not null default 0, " +
                    KEY_READ + " boolean not null default 0, " +
                    /* KEY_SERIES + " text, " + */
                    KEY_PAGES + " int, " +
                    /* KEY_SERIES_NUM + " text, " + */
                    KEY_NOTES + " text, " +
                    KEY_LIST_PRICE + " text, " +
                    KEY_ANTHOLOGY_MASK + " int not null default " + ANTHOLOGY_NO + ", " +
                    KEY_LOCATION + " text, " +
                    KEY_READ_START + " date, " +
                    KEY_READ_END + " date, " +
                    KEY_FORMAT + " text, " +
                    KEY_SIGNED + " boolean not null default 0, " +
                    KEY_DESCRIPTION + " text, " +
                    KEY_GENRE + " text, " +
                    KEY_DATE_ADDED + " datetime default current_timestamp" +
                    ")";

    private static final String DATABASE_CREATE_BOOKS_63 =
            "create table " + DB_TB_BOOKS +
                    " (_id integer primary key autoincrement, " +
                    /* KEY_AUTHOR + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " + */
                    KEY_TITLE + " text not null, " +
                    KEY_ISBN + " text, " +
                    KEY_PUBLISHER + " text, " +
                    KEY_DATE_PUBLISHED + " date, " +
                    KEY_RATING + " float not null default 0, " +
                    KEY_READ + " boolean not null default 0, " +
                    /* KEY_SERIES + " text, " + */
                    KEY_PAGES + " int, " +
                    /* KEY_SERIES_NUM + " text, " + */
                    KEY_NOTES + " text, " +
                    KEY_LIST_PRICE + " text, " +
                    KEY_ANTHOLOGY_MASK + " int not null default " + ANTHOLOGY_NO + ", " +
                    KEY_LOCATION + " text, " +
                    KEY_READ_START + " date, " +
                    KEY_READ_END + " date, " +
                    KEY_FORMAT + " text, " +
                    KEY_SIGNED + " boolean not null default 0, " +
                    KEY_DESCRIPTION + " text, " +
                    KEY_GENRE + " text " +
                    ")";

    private static final String KEY_AUDIOBOOK_V41 = "audiobook";
    private static final String DATABASE_CREATE_BOOKS_V41 =
            "create table " + DB_TB_BOOKS +
                    " (_id integer primary key autoincrement, " +
                    KEY_AUTHOR_ID + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " +
                    KEY_TITLE + " text not null, " +
                    KEY_ISBN + " text, " +
                    KEY_PUBLISHER + " text, " +
                    KEY_DATE_PUBLISHED + " date, " +
                    KEY_RATING + " float not null default 0, " +
                    KEY_READ + " boolean not null default 'f', " +
                    KEY_SERIES_OLD + " text, " +
                    KEY_PAGES + " int, " +
                    KEY_SERIES_NUM + " text, " +
                    KEY_NOTES + " text, " +
                    KEY_LIST_PRICE + " text, " +
                    KEY_ANTHOLOGY_MASK + " int not null default " + ANTHOLOGY_NO + ", " +
                    KEY_LOCATION + " text, " +
                    KEY_READ_START + " date, " +
                    KEY_READ_END + " date, " +
                    KEY_AUDIOBOOK_V41 + " boolean not null default 'f', " +
                    KEY_SIGNED + " boolean not null default 'f' " +
                    ")";

    private static final String DATABASE_CREATE_LOAN =
            "create table " + DB_TB_LOAN +
                    " (_id integer primary key autoincrement, " +
                    KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
                    KEY_LOANED_TO + " text " +
                    ")";

    private static final String DATABASE_CREATE_ANTHOLOGY =
            "create table " + DB_TB_ANTHOLOGY +
                    " (_id integer primary key autoincrement, " +
                    KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
                    KEY_AUTHOR_ID + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " +
                    KEY_TITLE + " text not null, " +
                    KEY_POSITION + " int" +
                    ")";

    private static final String DATABASE_CREATE_BOOK_BOOKSHELF_WEAK =
            "create table " + DB_TB_BOOK_BOOKSHELF_WEAK + "(" +
                    KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
                    KEY_BOOKSHELF + " integer REFERENCES " + DB_TB_BOOKSHELF + " ON DELETE SET NULL ON UPDATE SET NULL" +
                    ")";

    private static final String DATABASE_CREATE_SERIES =
            "create table " + DB_TB_SERIES +
                    " (_id integer primary key autoincrement, " +
                    KEY_SERIES_NAME + " text not null " +
                    ")";

    private static final String DATABASE_CREATE_BOOK_SERIES_54 =
            "create table " + DB_TB_BOOK_SERIES + "(" +
                    KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE, " +
                    KEY_SERIES_ID + " integer REFERENCES " + DB_TB_SERIES + " ON DELETE SET NULL ON UPDATE CASCADE, " +
                    KEY_SERIES_NUM + " integer, " +
                    KEY_SERIES_POSITION + " integer," +
                    "PRIMARY KEY(" + KEY_BOOK + ", "  + KEY_SERIES_POSITION + ")" +
                    ")";

    private static final String DATABASE_CREATE_BOOK_SERIES =
            "create table " + DB_TB_BOOK_SERIES + "(" +
                    KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE, " +
                    KEY_SERIES_ID + " integer REFERENCES " + DB_TB_SERIES + " ON DELETE SET NULL ON UPDATE CASCADE, " +
                    KEY_SERIES_NUM + " text, " +
                    KEY_SERIES_POSITION + " integer," +
                    "PRIMARY KEY(" + KEY_BOOK + ", "  + KEY_SERIES_POSITION + ")" +
                    ")";

    private static final String DATABASE_CREATE_BOOK_AUTHOR =
            "create table " + DB_TB_BOOK_AUTHOR + "(" +
                    KEY_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE, " +
                    KEY_AUTHOR_ID + " integer REFERENCES " + DB_TB_AUTHORS + " ON DELETE SET NULL ON UPDATE CASCADE, " +
                    KEY_AUTHOR_POSITION + " integer NOT NULL, " +
                    "PRIMARY KEY(" + KEY_BOOK + ", "  + KEY_AUTHOR_POSITION + ")" +
                    ")";

    private static final String[] DATABASE_CREATE_INDICES = {
            "CREATE INDEX IF NOT EXISTS authors_given_names ON "+DB_TB_AUTHORS+" ("+KEY_GIVEN_NAMES+");",
            "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON "+DB_TB_AUTHORS+" ("+KEY_GIVEN_NAMES+" " + COLLATION + ");",
            "CREATE INDEX IF NOT EXISTS authors_family_name ON "+DB_TB_AUTHORS+" ("+KEY_FAMILY_NAME+");",
            "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON "+DB_TB_AUTHORS+" ("+KEY_FAMILY_NAME+" " + COLLATION + ");",
            "CREATE INDEX IF NOT EXISTS bookshelf_bookshelf ON "+DB_TB_BOOKSHELF+" ("+KEY_BOOKSHELF+");",
            /* "CREATE INDEX IF NOT EXISTS books_author ON "+DB_TB_BOOKS+" ("+KEY_AUTHOR+");",*/
            /*"CREATE INDEX IF NOT EXISTS books_author_ci ON "+DB_TB_BOOKS+" ("+KEY_AUTHOR+" " + COLLATION + ");",*/
            "CREATE INDEX IF NOT EXISTS books_title ON "+DB_TB_BOOKS+" ("+KEY_TITLE+");",
            "CREATE INDEX IF NOT EXISTS books_title_ci ON "+DB_TB_BOOKS+" ("+KEY_TITLE+" " + COLLATION + ");",
            "CREATE INDEX IF NOT EXISTS books_isbn ON "+DB_TB_BOOKS+" ("+KEY_ISBN+");",
            /* "CREATE INDEX IF NOT EXISTS books_series ON "+DB_TB_BOOKS+" ("+KEY_SERIES+");",*/
            "CREATE INDEX IF NOT EXISTS books_publisher ON "+DB_TB_BOOKS+" ("+KEY_PUBLISHER+");",
            "CREATE UNIQUE INDEX IF NOT EXISTS books_uuid ON "+DB_TB_BOOKS+" ("+DOM_BOOK_UUID+");",
            "CREATE INDEX IF NOT EXISTS books_gr_book ON "+DB_TB_BOOKS+" ("+DOM_GOODREADS_BOOK_ID.name+");",
            "CREATE INDEX IF NOT EXISTS anthology_book ON "+DB_TB_ANTHOLOGY+" ("+KEY_BOOK+");",
            "CREATE INDEX IF NOT EXISTS anthology_author ON "+DB_TB_ANTHOLOGY+" ("+KEY_AUTHOR_ID+");",
            "CREATE INDEX IF NOT EXISTS anthology_title ON "+DB_TB_ANTHOLOGY+" ("+KEY_TITLE+");",
            "CREATE UNIQUE INDEX IF NOT EXISTS series_series ON "+DB_TB_SERIES+" ("+KEY_ROWID+");",
            "CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON "+DB_TB_LOAN+" ("+KEY_BOOK+");",
            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_book ON "+DB_TB_BOOK_BOOKSHELF_WEAK+" ("+KEY_BOOK+");",
            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_bookshelf ON "+DB_TB_BOOK_BOOKSHELF_WEAK+" ("+KEY_BOOKSHELF+");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_series ON "+DB_TB_BOOK_SERIES+" ("+KEY_SERIES_ID+", " + KEY_BOOK + ", " + KEY_SERIES_NUM + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_book ON "+DB_TB_BOOK_SERIES+" ("+KEY_BOOK+", " + KEY_SERIES_ID + ", " + KEY_SERIES_NUM + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_author ON "+DB_TB_BOOK_AUTHOR+" ("+KEY_AUTHOR_ID+", " + KEY_BOOK + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_book ON "+DB_TB_BOOK_AUTHOR+" ("+KEY_BOOK+", " + KEY_AUTHOR_ID + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + DB_TB_ANTHOLOGY + " (" + KEY_BOOK + ", " + KEY_AUTHOR_ID + ", " + KEY_TITLE + ")"
    };
    /**
     * This function is called when the database is first created
     *
     * @param db The database to be created
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        mDbWasCreated = true;
        db.execSQL(DATABASE_CREATE_AUTHORS);
        db.execSQL(DATABASE_CREATE_BOOKSHELF);
        db.execSQL(DATABASE_CREATE_BOOKS); // RELEASE: Make sure this is always DATABASE_CREATE_BOOKS after a rename of the original
        db.execSQL(DATABASE_CREATE_BOOKSHELF_DATA);
        db.execSQL(DATABASE_CREATE_LOAN);
        db.execSQL(DATABASE_CREATE_ANTHOLOGY);
        db.execSQL(DATABASE_CREATE_SERIES);
        db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);
        db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);
        db.execSQL(DATABASE_CREATE_BOOK_SERIES);
        createIndices(db);

        DbSync.SynchronizedDb sdb = new DbSync.SynchronizedDb(db, mSynchronizer);

        DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS.createAll(sdb, true);
        DatabaseDefinitions.TBL_BOOKS_FTS.create(sdb, false);
        DatabaseDefinitions.TBL_BOOK_LIST_STYLES.createAll(sdb, true);

        createTriggers(sdb);
    }

    /**
     * Create the database triggers. Currently only one, and the implementation needs refinining!
     */
    private void createTriggers(DbSync.SynchronizedDb db) {
        String name = "books_tg_reset_goodreads";
        String body = " after update of isbn on books for each row\n" +
                " When New." + KEY_ISBN + " <> Old." + KEY_ISBN + "\n" +
                "	Begin \n" +
                "		Update books Set \n" +
                "		    goodreads_book_id = 0,\n" +
                "		    last_goodreads_sync_date = ''\n" +
                "		Where\n" +
                "			" + KEY_ROWID + " = new." + KEY_ROWID + ";\n" +
                "	End";
        db.execSQL("Drop Trigger if Exists " + name);
        db.execSQL("Create Trigger " + name + body);
    }

    private void createIndices(SQLiteDatabase db) {
        //delete all indices first
        String sql = "select name from sqlite_master where type = 'index' and sql is not null;";
        Cursor current = db.rawQuery(sql, new String[]{});
        while (current.moveToNext()) {
            String index_name = current.getString(0);
            String delete_sql = "DROP INDEX " + index_name;
            //db.beginTransaction();
            try {
                db.execSQL(delete_sql);
                //db.setTransactionSuccessful();
            } catch (Exception e) {
                Logger.logError(e, "Index deletion failed (probably not a problem)");
            } finally {
                //db.endTransaction();
            }
        }
        current.close();

        for (String index : DATABASE_CREATE_INDICES) {
            // Avoid index creation killing an upgrade because this
            // method may be called by any upgrade script and some tables
            // may not yet exist. We probably still want indexes.
            //
            // Ideally, whenever an upgrade script is written, the 'createIndices()' call
            // from prior upgrades should be removed saved and used and a new version of this
            // script written for the new DB.
            //db.beginTransaction();
            try {
                db.execSQL(index);
                //db.setTransactionSuccessful();
            } catch (Exception e) {
                // Expected on multi-version upgrades.
                Logger.logError(e, "Index creation failed (probably not a problem), definition was: " + index);
            } finally {
                //db.endTransaction();
            }
        }
        db.execSQL("analyze");
    }

    /**
     * This function is called each time the database is upgraded. The function will run all
     * upgrade scripts between the oldVersion and the newVersion.
     *
     * NOTE: As of 4.2, DO NOT USE OnUpgrade TO DISPLAY UPGRADE MESSAGES. See header for details.
     *
     * see DATABASE_VERSION
     *
     * @param db The database to be upgraded
     * @param oldVersion The current version number of the database
     * @param newVersion The new version number of the database
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        mDbWasCreated = false;

        int curVersion = oldVersion;

        StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null)
            startup.updateProgress(R.string.upgrading_ellipsis);

        if (oldVersion != newVersion)
            StorageUtils.backupDbFile(db, "DbUpgrade-" + oldVersion + "-" + newVersion);

        if (curVersion < 11) {
            onCreate(db);
        }
        if (curVersion == 11) {
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_SERIES_NUM + " text");
            db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_SERIES_NUM + " = ''");
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
            message += "* This message will now appear whenever you upgrade\n\n";
            message += "* Various SQL bugs have been resolved\n\n";
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
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_NOTES + " text");
            db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_NOTES + " = ''");
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
            message += "* There is a new tabbed 'edit' interface to simplify editing books.\n\n";
            message += "* The new comments tab also includes a notes field where you can add personal notes for any book (Requested by Luke).\n\n";
            message += "* The new loaned books tab allows you to record books loaned to friends. This will lookup your phone contacts to pre-populate the list (Requested by Luke)\n\n";
            message += "* Scanned books that already exist in the database (based on ISBN) will no longer be added (Identified by Colin)\n\n";
            message += "* After adding a book, the main view will now scroll to a appropriate location. \n\n";
            message += "* Searching has been made significantly faster.\n\n";
        }
        if (curVersion == 23) {
            //do nothing
            curVersion++;
        }
        if (curVersion == 24) {
            curVersion++;
            try {
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_NOTES + " text");
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
            try {
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_NOTES + " = ''");
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
            message += "* Your sort order will be automatically saved when to close the application (Requested by Martin)\n\n";
            message += "* There is a new 'about this app' view available from the administration tabs (Also from Martin)\n\n";
        }
        if (curVersion == 26) {
            //do nothing
            curVersion++;
            message += "* There are two additional sort functions, by series and by loaned (Request from N4ppy)\n\n";
            message += "* Your bookshelf and current location will be saved when you exit (Feedback from Martin)\n\n";
            message += "* Minor interface improvements when sorting by title \n\n";
        }
        if (curVersion == 27) {
            //do nothing
            curVersion++;
            message += "* The book thumbnail now appears in the list view\n\n";
            message += "* Emailing the developer now works from the admin page\n\n";
            message += "* The Change Bookshelf option is now more obvious (Thanks Mike)\n\n";
            message += "* The exports have been renamed to csv, use the correct published date and are now unicode safe (Thanks Mike)\n\n";
        }
        if (curVersion == 28) {
            curVersion++;
            try {
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_LIST_PRICE + " text");
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
        }
        if (curVersion == 29) {
            //do nothing
            curVersion++;
            message += "* Adding books will now (finally) search Amazon\n\n";
            message += "* A field for list price has been included (Requested by Brenda)\n\n";
            message += "* You can bulk update the thumbnails for all books with ISBN's from the Admin page\n\n";
        }
        if (curVersion == 30) {
            //do nothing
            curVersion++;
            message += "* You can now delete individual thumbnails by holding on the image and selecting delete.\n\n";
        }
        if (curVersion == 31) {
            //do nothing
            curVersion++;
            message += "* There is a new Admin option (Field Visibility) to hide unused fields\n\n";
            message += "* 'All Books' should now be saved as a bookshelf preference correctly\n\n";
            message += "* When adding books the bookshelf will default to your currently selected bookshelf (Thanks Martin)\n\n";
        }
        if (curVersion == 32) {
            //do nothing
            curVersion++;
            try {
                db.execSQL(DATABASE_CREATE_ANTHOLOGY);
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
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_ANTHOLOGY_MASK + " int not null default " + ANTHOLOGY_NO);
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
            message += "* There is now support to record books as anthologies and it's titles. \n\n";
            message += "* There is experimental support to automatically populate the anthology titles \n\n";
            message += "* You can now take photos for the book cover (long click on the thumbnail in edit) \n\n";
        }
        if (curVersion == 33) {
            //do nothing
            curVersion++;
            message += "* Minor enhancements\n\n";
            message += "* Online help has been written\n\n";
            message += "* Thumbnails can now be hidden just like any other field (Thanks Martin)\n\n";
            message += "* You can also rotate thumbnails; useful for thumbnails taken with the camera\n\n";
            message += "* Bookshelves will appear in the menu immediately (Thanks Martin/Julia)\n\n";
        }
        if (curVersion == 34) {
            curVersion++;
            try {
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_LOCATION + " text");
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_READ_START + " date");
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_READ_END + " date");
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + OLD_KEY_AUDIOBOOK + " boolean not null default 'f'");
                db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_SIGNED + " boolean not null default 'f'");
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
        }
        if (curVersion == 35) {
            curVersion++;
            try {
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_LOCATION + "=''");
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_READ_START + "=''");
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_READ_END + "=''");
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + OLD_KEY_AUDIOBOOK + "='f'");
                db.execSQL("UPDATE " + DB_TB_BOOKS + " SET " + KEY_SIGNED + "='f'");
            } catch (Exception e) {
                Logger.logError(e);
                throw new RuntimeException("Failed to upgrade database", e);
            }
        }
        if (curVersion == 36) {
            //do nothing
            curVersion++;
            message += "* Fixed several crashing defects when adding books\n\n";
            message += "* Added Autocompleting Location Field (For Cam)\n\n";
            message += "* Added Read Start & Read End Fields (For Robert)\n\n";
            message += "* Added an Audiobook Checkbox Field (For Ted)\n\n";
            message += "* Added a Book Signed Checkbox Field (For me)\n\n";
            message += "*** Don't forget you can hide any of the new fields that you do not want to see.\n\n";
            message += "* Series Number now support decimal figures (Requested by Beth)\n\n";
            message += "* List price now support decimal figures (Requested by eleavings)\n\n";
            message += "* Fixed Import Crashes (Thanks Roydalg) \n\n";
            message += "* Fixed several defects for Android 1.6 users - I do not have a 1.6 device to test on so please let me know if you discover any errors\n\n";
        }
        if (curVersion == 37) {
            //do nothing
            curVersion++;
            message += "Tip: If you long click on a book title on the main list you can delete it\n\n";
            message += "Tip: If you want to see all books, change the bookshelf to 'All Books'\n\n";
            message += "Tip: You can find the correct barcode for many modern paperbacks on the inside cover\n\n";
            message += "* There is now a 'Sort by Unread' option, as well as a 'read' icon on the main list (requested by Angel)\n\n";
            message += "* If you long click on the (?) thumbnail you can now select a new thumbnail from the gallery (requested by Giovanni)\n\n";
            message += "* Bookshelves, loaned books and anthology titles will now import correctly\n\n";
        }
        if (curVersion == 38) {
            //do nothing
            curVersion++;
            db.execSQL("DELETE FROM " + DB_TB_LOAN + " WHERE (" + KEY_LOANED_TO + "='' OR " + KEY_LOANED_TO + "='null')");
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
            message += "Tip: You can find the correct barcode for many modern paperbacks on the inside cover\n\n";
            message += "* Added app2sd support (2.2 users only)\n\n";
            message += "* You can now assign books to multiple bookshelves (requested by many people)\n\n";
            message += "* A .nomedia file will be automatically created which will stop the thumbnails showing up in the gallery (thanks Brandon)\n\n";
            message += "* The 'Add Book by ISBN' page has been redesigned to be simpler and more stable (thanks Vinikia)\n\n";
            message += "* The export file is now formatted correctly (.csv) (thanks glohr)\n\n";
            message += "* You will be prompted to backup your books on a regular basis \n\n";

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
                db.execSQL("INSERT INTO " + DB_TB_BOOK_BOOKSHELF_WEAK + " (" + KEY_BOOK + ", " + KEY_BOOKSHELF + ") SELECT " + KEY_ROWID + ", " + KEY_BOOKSHELF + " FROM " + DB_TB_BOOKS + "");
                db.execSQL("CREATE TABLE tmp1 AS SELECT _id, " + KEY_AUTHOR_OLD + ", " + KEY_TITLE + ", " + KEY_ISBN + ", " + KEY_PUBLISHER + ", " +
                        KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ + ", " + KEY_SERIES_OLD + ", " + KEY_PAGES + ", " + KEY_SERIES_NUM + ", " + KEY_NOTES + ", " +
                        KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY_MASK + ", " + KEY_LOCATION + ", " + KEY_READ_START + ", " + KEY_READ_END + ", " + OLD_KEY_AUDIOBOOK + ", " +
                        KEY_SIGNED + " FROM " + DB_TB_BOOKS);
                db.execSQL("CREATE TABLE tmp2 AS SELECT _id, " + KEY_BOOK + ", " + KEY_LOANED_TO + " FROM " + DB_TB_LOAN );
                db.execSQL("CREATE TABLE tmp3 AS SELECT _id, " + KEY_BOOK + ", " + KEY_AUTHOR_OLD + ", " + KEY_TITLE + ", " + KEY_POSITION + " FROM " + DB_TB_ANTHOLOGY);

                db.execSQL("DROP TABLE " + DB_TB_ANTHOLOGY);
                db.execSQL("DROP TABLE " + DB_TB_LOAN);
                db.execSQL("DROP TABLE " + DB_TB_BOOKS);

                db.execSQL(DATABASE_CREATE_BOOKS_V41);
                db.execSQL(DATABASE_CREATE_LOAN);
                db.execSQL(DATABASE_CREATE_ANTHOLOGY);

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
            message += "* Export bug fixed\n\n";
            message += "* Further enhancements to the new ISBN screen\n\n";
            message += "* Filtering by bookshelf on title view is now fixed\n\n";
            message += "* There is now an <Empty Series> when sorting by Series (thanks Vinikia)\n\n";
            message += "* App will now search all fields (Thanks Michael)\n\n";
        }
        if (curVersion == 43) {
            curVersion++;
            db.execSQL("DELETE FROM " + DB_TB_ANTHOLOGY + " WHERE " + KEY_ROWID + " IN (" +
                    "SELECT a." + KEY_ROWID + " FROM " + DB_TB_ANTHOLOGY + " a, " + DB_TB_ANTHOLOGY + " b " +
                    " WHERE a." + KEY_BOOK + "=b." + KEY_BOOK + " AND a." + KEY_AUTHOR_OLD + "=b." + KEY_AUTHOR_OLD + " " +
                    " AND a." + KEY_TITLE + "=b." + KEY_TITLE + " AND a." + KEY_ROWID + " > b." + KEY_ROWID + "" +
                    ")");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + DB_TB_ANTHOLOGY + " (" + KEY_BOOK + ", " + KEY_AUTHOR_OLD + ", " + KEY_TITLE + ")");
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

            db.execSQL("CREATE TABLE tmp1 AS SELECT _id, " + KEY_AUTHOR_OLD + ", " + KEY_TITLE + ", " + KEY_ISBN + ", " + KEY_PUBLISHER + ", " +
                    KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ + ", " + KEY_SERIES_OLD + ", " + KEY_PAGES + ", " + KEY_SERIES_NUM + ", " + KEY_NOTES + ", " +
                    KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY_MASK + ", " + KEY_LOCATION + ", " + KEY_READ_START + ", " + KEY_READ_END + ", " +
                    "CASE WHEN " + OLD_KEY_AUDIOBOOK + "='t' THEN 'Audiobook' ELSE 'Paperback' END AS " + OLD_KEY_AUDIOBOOK + ", " +
                    KEY_SIGNED + " FROM " + DB_TB_BOOKS);
            db.execSQL("CREATE TABLE tmp2 AS SELECT _id, " + KEY_BOOK + ", " + KEY_LOANED_TO + " FROM " + DB_TB_LOAN );
            db.execSQL("CREATE TABLE tmp3 AS SELECT _id, " + KEY_BOOK + ", " + KEY_AUTHOR_OLD + ", " + KEY_TITLE + ", " + KEY_POSITION + " FROM " + DB_TB_ANTHOLOGY);
            db.execSQL("CREATE TABLE tmp4 AS SELECT " + KEY_BOOK + ", " + KEY_BOOKSHELF+ " FROM " + DB_TB_BOOK_BOOKSHELF_WEAK);

            db.execSQL("DROP TABLE " + DB_TB_ANTHOLOGY);
            db.execSQL("DROP TABLE " + DB_TB_LOAN);
            db.execSQL("DROP TABLE " + DB_TB_BOOKS);
            db.execSQL("DROP TABLE " + DB_TB_BOOK_BOOKSHELF_WEAK);

            String TMP_DATABASE_CREATE_BOOKS =
                    "create table " + DB_TB_BOOKS +
                            " (_id integer primary key autoincrement, " +
                            KEY_AUTHOR_OLD + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " +
                            KEY_TITLE + " text not null, " +
                            KEY_ISBN + " text, " +
                            KEY_PUBLISHER + " text, " +
                            KEY_DATE_PUBLISHED + " date, " +
                            KEY_RATING + " float not null default 0, " +
                            KEY_READ + " boolean not null default 'f', " +
                            KEY_SERIES_OLD + " text, " +
                            KEY_PAGES + " int, " +
                            KEY_SERIES_NUM + " text, " +
                            KEY_NOTES + " text, " +
                            KEY_LIST_PRICE + " text, " +
                            KEY_ANTHOLOGY_MASK + " int not null default " + ANTHOLOGY_NO + ", " +
                            KEY_LOCATION + " text, " +
                            KEY_READ_START + " date, " +
                            KEY_READ_END + " date, " +
                            KEY_FORMAT + " text, " +
                            KEY_SIGNED + " boolean not null default 'f' " +
                            ")";


            db.execSQL(TMP_DATABASE_CREATE_BOOKS);
            db.execSQL(DATABASE_CREATE_LOAN);
            db.execSQL(DATABASE_CREATE_ANTHOLOGY);
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
            db.execSQL("DELETE FROM " + DB_TB_LOAN + " WHERE " + KEY_LOANED_TO + "='null'" );
        }
        if (curVersion == 46) {
            curVersion++;
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_DESCRIPTION + " text");
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " ADD " + KEY_GENRE + " text");
        }
        if (curVersion == 47) {
            curVersion++;
            // This used to be chaecks in BookCatalogueClassic, which is no longer called on startup...
            //do_action = DO_UPDATE_FIELDS;
            message += "New in v3.1\n\n";
            message += "* The audiobook checkbox has been replaced with a format selector (inc. paperback, hardcover, companion etc)\n\n";
            message += "* When adding books the current bookshelf will be selected as the default bookshelf\n\n";
            message += "* Genre/Subject and Description fields have been added (Requested by Tosh) and will automatically populate based on Google Books and Amazon information\n\n";
            message += "* The save button will always be visible on the edit book screen\n\n";
            message += "* Searching for a single space will clear the search results page\n\n";
            message += "* The Date Picker will now appear in a popup in order to save space on the screen (Requested by several people)\n\n";
            message += "* To improve speed when sorting by title, the titles will be broken up by the first character. Remember prefixes such as 'the' and 'a' are listed after the title, e.g. 'The Trigger' becomes 'Trigger, The'\n\n";
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
            message += "New in v3.2\n\n";
            message += "* Books can now be automatically added by searching for the author name and book title\n\n";
            message += "* Updating thumbnails, genre and description fields will also search by author name and title is the isbn does not exist\n\n";				message += "* Expand/Collapse all bug fixed\n\n";
            message += "* The search query will be shown at the top of all search screens\n\n";
        }
        if (curVersion == 50) {
            curVersion++;
            //createIndices(db); // All createIndices prior to the latest have been removed
        }
        if (curVersion == 51) {
            curVersion++;
            message += "New in v3.3 - Updates courtesy of Grunthos\n\n";
            message += "* The application should be significantly faster now - Fixed a bug with database index creation\n\n";
            message += "* The thumbnail can be rotated in both directions now\n\n";
            message += "* You can zoom in the thumbnail to see full detail\n\n";
            message += "* The help page will redirect to the, more frequently updated, online wiki\n\n";
            message += "* Dollar signs in the text fields will no longer FC on import/export\n\n";
        }
        if (curVersion == 52) {
            curVersion++;
            message += "New in v3.3.1\n\n";
            message += "* Minor bug fixes and error logging. Please email me if you have any further issues.\n\n";
        }
        if (curVersion == 53) {
            curVersion++;
            //There is a conflict between eleybourn released branch (3.3.1) and grunthos HEAD (3.4).
            // This is to check and skip as required
            boolean go = false;
            String checkSQL = "SELECT * FROM " + DB_TB_BOOKS;
            Cursor results = db.rawQuery(checkSQL, new String[]{});
            if (results.getCount() > 0) {
                if (results.getColumnIndex(KEY_AUTHOR_OLD) > -1) {
                    go = true;
                }
            }
            if (go) {
                try {
                    message += "New in v3.4 - Updates courtesy of (mainly) Grunthos (blame him, politely, if it toasts your data)\n\n";
                    message += "* Multiple Authors per book\n\n";
                    message += "* Multiple Series per book\n\n";
                    message += "* Fetches series (and other stuff) from LibraryThing\n\n";
                    message += "* Can now make global changes to Author and Series (Access this feature via long-click on the catalogue screen)\n\n";
                    message += "* Can replace a cover thumbnail from a different edition via LibraryThing.\n\n";
                    message += "* Does concurrent ISBN searches at Amazon, Google and LibraryThing (it's faster)\n\n";
                    message += "* Displays a progress dialog while searching for a book on the internet.\n\n";
                    message += "* Adding by Amazon's ASIN is supported on the 'Add by ISBN' page\n\n";
                    message += "* Duplicate books allowed, with a warning message\n\n";
                    message += "* User-selectable fields when reloading data from internet (eg. just update authors).\n\n";
                    message += "* Unsaved edits are retained when rotating the screen.\n\n";
                    message += "* Changed the ISBN data entry screen when the device is in landscape mode.\n\n";
                    message += "* Displays square brackets around the series name when displaying a list of books.\n\n";
                    message += "* Suggestions available when searching\n\n";
                    message += "* Removed the need for contacts permission. Though it does mean you will need to manually enter everyone you want to loan to.\n\n";
                    message += "* Preserves *all* open groups when closing application.\n\n";
                    message += "* The scanner and book search screens remain active after a book has been added or a search fails. It was viewed that this more closely represents the work-flow of people adding or scanning books.\n\n";
                    message += "See the web site (from the Admin menu) for more details\n\n";

                    db.execSQL(DATABASE_CREATE_SERIES);
                    // We need to create a series table with series that are unique wrt case and unicode. The old
                    // system allowed for series with slightly different case. So we capture these by using
                    // max() to pick and arbitrary matching name to use as our canonical version.
                    db.execSQL("INSERT INTO " + DB_TB_SERIES + " (" + KEY_SERIES_NAME + ") "
                            + "SELECT name from ("
                            + "    SELECT Upper(" + KEY_SERIES_OLD + ") " + COLLATION + " as ucName, "
                            + "    max(" + KEY_SERIES_OLD + ")" + COLLATION + " as name FROM " + DB_TB_BOOKS
                            + "    WHERE Coalesce(" + KEY_SERIES_OLD + ",'') <> ''"
                            + "    Group By Upper(" + KEY_SERIES_OLD + ")"
                            + " )"
                    );

                    db.execSQL(DATABASE_CREATE_BOOK_SERIES_54);
                    db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);

                    //createIndices(db); // All createIndices prior to the latest have been removed

                    db.execSQL("INSERT INTO " + DB_TB_BOOK_SERIES + " (" + KEY_BOOK + ", " + KEY_SERIES_ID + ", " + KEY_SERIES_NUM + ", " + KEY_SERIES_POSITION + ") "
                            + "SELECT DISTINCT b." + KEY_ROWID + ", s." + KEY_ROWID + ", b." + KEY_SERIES_NUM + ", 1"
                            + " FROM " + DB_TB_BOOKS + " b "
                            + " Join " + DB_TB_SERIES + " s On Upper(s." + KEY_SERIES_NAME + ") = Upper(b." + KEY_SERIES_OLD + ")" + COLLATION
                            + " Where Coalesce(b." + KEY_SERIES_OLD + ", '') <> ''");

                    db.execSQL("INSERT INTO " + DB_TB_BOOK_AUTHOR + " (" + KEY_BOOK + ", " + KEY_AUTHOR_ID + ", " + KEY_AUTHOR_POSITION + ") "
                            + "SELECT b." + KEY_ROWID + ", b." + KEY_AUTHOR_OLD + ", 1 FROM " + DB_TB_BOOKS + " b ");

                    String tmpFields = KEY_ROWID + ", " /* + KEY_AUTHOR + ", " */ + KEY_TITLE + ", " + KEY_ISBN
                            + ", " + KEY_PUBLISHER + ", " + KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ
                            + /* ", " + KEY_SERIES + */ ", " + KEY_PAGES /* + ", " + KEY_SERIES_NUM */ + ", " + KEY_NOTES
                            + ", " + KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY_MASK + ", " + KEY_LOCATION + ", " + KEY_READ_START
                            + ", " + KEY_READ_END + ", " + KEY_FORMAT + ", " + KEY_SIGNED + ", " + KEY_DESCRIPTION
                            + ", " + KEY_GENRE;
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
                //There is a conflict between eleybourn released branch (3.3.1) and grunthos HEAD (3.4).
                // This is to check and skip as required
                boolean go2 = false;
                String checkSQL2 = "SELECT * FROM " + DB_TB_BOOKS;
                Cursor results2 = db.rawQuery(checkSQL2, new String[]{});
                if (results2.getCount() > 0) {
                    if (results2.getColumnIndex(KEY_AUTHOR_OLD) > -1) {
                        go2 = true;
                    }
                } else {
                    go2 = true;
                }
                if (go2) {
                    try {
                        db.execSQL(DATABASE_CREATE_SERIES);
                        // We need to create a series table with series that are unique wrt case and unicode. The old
                        // system allowed for series with slightly different case. So we capture these by using
                        // max() to pick and arbitrary matching name to use as our canonical version.
                        db.execSQL("INSERT INTO " + DB_TB_SERIES + " (" + KEY_SERIES_NAME + ") "
                                + "SELECT name from ("
                                + "    SELECT Upper(" + KEY_SERIES_OLD + ") " + COLLATION + " as ucName, "
                                + "    max(" + KEY_SERIES_OLD + ")" + COLLATION + " as name FROM " + DB_TB_BOOKS
                                + "    WHERE Coalesce(" + KEY_SERIES_OLD + ",'') <> ''"
                                + "    Group By Upper(" + KEY_SERIES_OLD + ")"
                                + " )"
                        );

                        db.execSQL(DATABASE_CREATE_BOOK_SERIES_54);
                        db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);

                        db.execSQL("INSERT INTO " + DB_TB_BOOK_SERIES + " (" + KEY_BOOK + ", " + KEY_SERIES_ID + ", " + KEY_SERIES_NUM + ", " + KEY_SERIES_POSITION + ") "
                                + "SELECT DISTINCT b." + KEY_ROWID + ", s." + KEY_ROWID + ", b." + KEY_SERIES_NUM + ", 1"
                                + " FROM " + DB_TB_BOOKS + " b "
                                + " Join " + DB_TB_SERIES + " s On Upper(s." + KEY_SERIES_NAME + ") = Upper(b." + KEY_SERIES_OLD + ")" + COLLATION
                                + " Where Coalesce(b." + KEY_SERIES_OLD + ", '') <> ''");

                        db.execSQL("INSERT INTO " + DB_TB_BOOK_AUTHOR + " (" + KEY_BOOK + ", " + KEY_AUTHOR_ID + ", " + KEY_AUTHOR_POSITION + ") "
                                + "SELECT b." + KEY_ROWID + ", b." + KEY_AUTHOR_OLD + ", 1 FROM " + DB_TB_BOOKS + " b ");

                        String tmpFields = KEY_ROWID + ", " /* + KEY_AUTHOR + ", " */ + KEY_TITLE + ", " + KEY_ISBN
                                + ", " + KEY_PUBLISHER + ", " + KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ
                                + /* ", " + KEY_SERIES + */ ", " + KEY_PAGES /* + ", " + KEY_SERIES_NUM */ + ", " + KEY_NOTES
                                + ", " + KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY_MASK + ", " + KEY_LOCATION + ", " + KEY_READ_START
                                + ", " + KEY_READ_END + ", " + KEY_FORMAT + ", " + KEY_SIGNED + ", " + KEY_DESCRIPTION
                                + ", " + KEY_GENRE;
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
            message += "New in v3.5.4\n\n";
            message += "* French translation available\n\n";
            message += "* There is an option to create a duplicate book (requested by Vinika)\n\n";
            message += "* Fixed errors caused by failed upgrades\n\n";
            message += "* Fixed errors caused by trailing spaces in bookshelf names\n\n";
        }
        if (curVersion == 57) {
            curVersion++;
            Cursor results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_AUTHORS + "'", new String[]{});
            if (results57.getCount() == 0) {
                //table does not exist
                db.execSQL(DATABASE_CREATE_AUTHORS);
            }
            results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOKSHELF + "'", new String[]{});
            if (results57.getCount() == 0) {
                //table does not exist
                db.execSQL(DATABASE_CREATE_BOOKSHELF);
                db.execSQL(DATABASE_CREATE_BOOKSHELF_DATA);
            }
            results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_SERIES + "'", new String[]{});
            if (results57.getCount() == 0) {
                //table does not exist
                db.execSQL(DATABASE_CREATE_SERIES);
                Cursor results57_2 = db.rawQuery("SELECT * FROM " + DB_TB_BOOKS, new String[]{});
                if (results57_2.getCount() > 0) {
                    if (results57_2.getColumnIndex(KEY_SERIES_OLD) > -1) {
                        db.execSQL("INSERT INTO " + DB_TB_SERIES + " (" + KEY_SERIES_NAME + ") "
                                + "SELECT name from ("
                                + "    SELECT Upper(" + KEY_SERIES_OLD + ") " + COLLATION + " as ucName, "
                                + "    max(" + KEY_SERIES_OLD + ")" + COLLATION + " as name FROM " + DB_TB_BOOKS
                                + "    WHERE Coalesce(" + KEY_SERIES_OLD + ",'') <> ''"
                                + "    Group By Upper(" + KEY_SERIES_OLD + ")"
                                + " )"
                        );
                    }
                }
            }
            results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOKS + "'", new String[]{});
            if (results57.getCount() == 0) {
                //table does not exist
                db.execSQL(DATABASE_CREATE_BOOKS_63);
            }
            results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_LOAN + "'", new String[]{});
            if (results57.getCount() == 0) {
                //table does not exist
                db.execSQL(DATABASE_CREATE_LOAN);
            }
            results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_ANTHOLOGY + "'", new String[]{});
            if (results57.getCount() == 0) {
                //table does not exist
                db.execSQL(DATABASE_CREATE_ANTHOLOGY);
            }
            results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOK_BOOKSHELF_WEAK + "'", new String[]{});
            if (results57.getCount() == 0) {
                //table does not exist
                db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);
            }
            results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOK_SERIES + "'", new String[]{});
            if (results57.getCount() == 0) {
                //table does not exist
                db.execSQL(DATABASE_CREATE_BOOK_SERIES_54);
                db.execSQL("INSERT INTO " + DB_TB_BOOK_SERIES + " (" + KEY_BOOK + ", " + KEY_SERIES_ID + ", " + KEY_SERIES_NUM + ", " + KEY_SERIES_POSITION + ") "
                        + "SELECT DISTINCT b." + KEY_ROWID + ", s." + KEY_ROWID + ", b." + KEY_SERIES_NUM + ", 1"
                        + " FROM " + DB_TB_BOOKS + " b "
                        + " Join " + DB_TB_SERIES + " s On Upper(s." + KEY_SERIES_NAME + ") = Upper(b." + KEY_SERIES_OLD + ")" + COLLATION
                        + " Where Coalesce(b." + KEY_SERIES_OLD + ", '') <> ''");
            }
            results57 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_TB_BOOK_AUTHOR + "'", new String[]{});
            if (results57.getCount() == 0) {
                //table does not exist
                db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);
                db.execSQL("INSERT INTO " + DB_TB_BOOK_AUTHOR + " (" + KEY_BOOK + ", " + KEY_AUTHOR_ID + ", " + KEY_AUTHOR_POSITION + ") "
                        + "SELECT b." + KEY_ROWID + ", b." + KEY_AUTHOR_OLD + ", 1 FROM " + DB_TB_BOOKS + " b ");
            }
            Cursor results57_3 = db.rawQuery("SELECT * FROM " + DB_TB_BOOKS, new String[]{});
            if (results57_3.getCount() > 0) {
                if (results57_3.getColumnIndex(KEY_SERIES_OLD) > -1) {
                    String tmpFields = KEY_ROWID + ", " /* + KEY_AUTHOR + ", " */ + KEY_TITLE + ", " + KEY_ISBN
                            + ", " + KEY_PUBLISHER + ", " + KEY_DATE_PUBLISHED + ", " + KEY_RATING + ", " + KEY_READ
                            + /* ", " + KEY_SERIES + */ ", " + KEY_PAGES /* + ", " + KEY_SERIES_NUM */ + ", " + KEY_NOTES
                            + ", " + KEY_LIST_PRICE + ", " + KEY_ANTHOLOGY_MASK + ", " + KEY_LOCATION + ", " + KEY_READ_START
                            + ", " + KEY_READ_END + ", " + KEY_FORMAT + ", " + KEY_SIGNED + ", " + KEY_DESCRIPTION
                            + ", " + KEY_GENRE;
                    db.execSQL("CREATE TABLE tmpBooks AS SELECT " + tmpFields + " FROM " + DB_TB_BOOKS);
                    db.execSQL("DROP TABLE " + DB_TB_BOOKS);
                    db.execSQL(DATABASE_CREATE_BOOKS_63);
                    db.execSQL("INSERT INTO " + DB_TB_BOOKS + "( " + tmpFields + ")  SELECT * FROM tmpBooks");
                    db.execSQL("DROP TABLE tmpBooks");
                }
            }
        }
        if (curVersion == 58) {
            curVersion++;
            db.delete(DB_TB_LOAN, "("+KEY_BOOK+ "='' OR " + KEY_BOOK+ "=null OR " + KEY_LOANED_TO + "='' OR " + KEY_LOANED_TO + "=null) ", null);
            message += "New in v3.6\n\n";
            message += "* The LibraryThing Key will be verified when added\n\n";
            message += "* When entering ISBN's manually, each button with vibrate the phone slightly\n\n";
            message += "* When automatically updating fields click on each checkbox twice will give you the option to override existing fields (rather than populate only blank fields)\n\n";
            message += "* Fixed a crash when exporting over 2000 books\n\n";
            message += "* Orphan loan records will now be correctly managed\n\n";
            message += "* The Amazon search will now look for English, French, German, Italian and Japanese versions\n\n";
        }
        if (curVersion == 59) {
            curVersion++;
            message += "New in v3.6.2\n\n";
            message += "* Optionally restrict 'Sort By Author' to only the first Author (where there are multiple listed)\n\n";
            message += "* Minor bug fixes\n\n";
        }
        if (curVersion == 60) {
            curVersion++;
            message += "New in v3.7\n\n";
            message += "Hint: The export function will create an export.csv file on the sdcard\n\n";
            message += "* You can crop cover thumbnails (both from the menu and after taking a camera image)\n\n";
            message += "* You can tweet about a book directly from the book edit screen.\n\n";
            message += "* Sort by Date Published added\n\n";
            message += "* Will check for network connection prior to searching for details (new permission required)\n\n";
            message += "* Fixed crash when opening search results\n\n";
        }
        if (curVersion == 61) {
            curVersion++;
            message += "New in v3.8\n\n";
            message += "* Fixed several defects (including multiple author's and author prefix/suffix's)\n\n";
            message += "* Fixed issue with thumbnail resolutions from LibraryThing\n\n";
            message += "* Changed the 'Add Book' menu options to be submenu\n\n";
            message += "* The database backup has been renamed for clarity\n\n";
        }
        if (curVersion == 62) {
            curVersion++;
        }
        if (curVersion == 63) {
            // Fix up old default 'f' values to be 0 (true = 1).
            curVersion++;
            db.execSQL("UPDATE " + DB_TB_BOOKS + " Set " + KEY_READ + " = 0 Where " + KEY_READ + " = 'f'");
            db.execSQL("UPDATE " + DB_TB_BOOKS + " Set " + KEY_READ + " = 1 Where " + KEY_READ + " = 't'");
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " RENAME TO books_tmp");
            db.execSQL(DATABASE_CREATE_BOOKS_63);
            db.execSQL("INSERT INTO " + DB_TB_BOOKS + " Select * FROM books_tmp");
            db.execSQL("DROP TABLE books_tmp");
        }
        if (curVersion == 64) {
            // Changed SIGNED to boolean {0,1} and added DATE_ADDED
            // Fix up old default 'f' values to be 0 (true = 1).
            curVersion++;
            db.execSQL("UPDATE " + DB_TB_BOOKS + " Set " + KEY_SIGNED + " = 0 Where " + KEY_SIGNED + " = 'f'");
            db.execSQL("UPDATE " + DB_TB_BOOKS + " Set " + KEY_SIGNED + " = 1 Where " + KEY_SIGNED + " = 't'");
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " Add " + KEY_DATE_ADDED + " datetime"); // Just want a null value for old records
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " RENAME TO books_tmp");
            db.execSQL(DATABASE_CREATE_BOOKS_68);
            db.execSQL("INSERT INTO " + DB_TB_BOOKS + " Select * FROM books_tmp");
            db.execSQL("DROP TABLE books_tmp");
        }
        DbSync.SynchronizedDb sdb = new DbSync.SynchronizedDb(db, mSynchronizer);
        if (curVersion == 65) {
            curVersion++;
            DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS.drop(sdb);
            DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS.create(sdb, true);
            DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS.createIndices(sdb);
        }
        if (curVersion == 66) {
            curVersion++;
            DatabaseDefinitions.TBL_BOOKS_FTS.drop(sdb);
            DatabaseDefinitions.TBL_BOOKS_FTS.create(sdb, false);
            StartupActivity.scheduleFtsRebuild();
        }
        if (curVersion == 67) {
            curVersion++;
            DatabaseDefinitions.TBL_BOOK_LIST_STYLES.drop(sdb);
            DatabaseDefinitions.TBL_BOOK_LIST_STYLES.createAll(sdb, true);
        }
        if (curVersion == 68) {
            curVersion++;
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " Add " + DOM_GOODREADS_BOOK_ID.getDefinition(true));
        }
        if (curVersion == 69 || curVersion == 70) {
            curVersion = 71;
            db.execSQL("ALTER TABLE " + DB_TB_BOOKS + " RENAME TO books_tmp");
            db.execSQL(DATABASE_CREATE_BOOKS_81);
            copyTableSafely(sdb, "books_tmp", DB_TB_BOOKS);
            db.execSQL("DROP TABLE books_tmp");
        }
        if (curVersion == 71) {
            curVersion++;
            renameIdFilesToHash(sdb);
            // A bit of presumption here...
            message += "New in v4.0 - Updates courtesy of (mainly) Philip Warner (a.k.a Grunthos) -- blame him, politely, if it toasts your data\n\n";
            message += "* New look, new startup page\n\n";
            message += "* Synchronization with goodreads (www.goodreads.com)\n\n";
            message += "* New styles for book lists (including 'Compact' and 'Unread')\n\n";
            message += "* User-defined styles for book lists\n\n";
            message += "* More efficient memory usage\n\n";
            message += "* New preferences, including 'always expanded/collapsed' lists\n\n";
            message += "* Faster expand/collapse with large collections\n\n";
            message += "* Cached covers for faster scrolling lists\n\n";
            message += "* Cover images now have globally unique names, and books have globally unique IDs, so sharing and combining collections is easy\n\n";
            message += "* Improved detection of series names\n\n";
        }
        if (curVersion == 72) {
            curVersion++;
            // Just to get the triggers applied
        }

        if (curVersion == 73) {
            //do nothing
            curVersion++;
            message += "New in v4.0.1 - many bugs fixed\n\n";
            message += "* Added a preference to completely disable background bitmap\n\n";
            message += "* Added book counts to book lists\n\n";
            message += "Thank you to all those people who reported bugs.\n\n";
        }

        if (curVersion == 74) {
            //do nothing
            curVersion++;
            StartupActivity.scheduleAuthorSeriesFixUp();
            message += "New in v4.0.3\n\n";
            message += "* ISBN validation when searching/scanning and error beep when scanning (with preference to turn it off)\n\n";
            message += "* 'Loaned' list now shows available books under the heading 'Available'\n\n";
            message += "* Added preference to hide list headers (for small phones)\n\n";
            message += "* Restored functionality to use current bookshelf when adding book\n\n";
            message += "* FastScroller sizing improved\n\n";
            message += "* Several bugs fixed\n\n";
            message += "Thank you to all those people who reported bugs and helped tracking them down.\n\n";
        }

        if (curVersion == 75) {
            //do nothing
            curVersion++;
            StartupActivity.scheduleFtsRebuild();
            message += "New in v4.0.4\n\n";
            message += "* Search now searches series and anthology data\n\n";
            message += "* Allows non-numeric data entry in series position\n\n";
            message += "* Better sorting of leading numerics in series position\n\n";
            message += "* Several bug fixes\n\n";
        }

        if (curVersion == 76) {
            //do nothing
            curVersion++;
        }
        if (curVersion == 77) {
            //do nothing
            curVersion++;
            message += "New in v4.0.6\n\n";
            message += "* When adding books, scanning or typing an existing ISBN allows you the option to edit the book.\n";
            message += "* Allow ASINs to be entered manually as well as ISBNs\n";
            message += "* German translation updates (Robert Wetzlmayr)\n";
        }
        if (curVersion == 78) {
            //do nothing
            curVersion++;
            message += "New in v4.1\n\n";
            message += "* New style groups: Location and Date Read\n";
            message += "* Improved 'Share' functionality (filipeximenes)\n";
            message += "* French translation updates (Djiko)\n";
            message += "* Better handling of the 'back' key when editing books (filipeximenes)\n";
            message += "* Various bug fixes\n";
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
            copyTableSafely(sdb, tempName, DB_TB_BOOK_SERIES);
            db.execSQL("DROP TABLE " + tempName);
        }
        if (curVersion == 81) {
            curVersion++;
            recreateAndReloadTable(sdb, DB_TB_BOOKS, DATABASE_CREATE_BOOKS);
        }
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
        // NOTE: As of 4.2, DO NOT USE OnUpgrade TO DISPLAY UPGRADE MESSAGES. See header for details.
        // ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

        // Rebuild all indices
        createIndices(db);
        // Rebuild all triggers
        createTriggers(sdb);

        //TODO: NOTE: END OF UPDATE
    }
}
