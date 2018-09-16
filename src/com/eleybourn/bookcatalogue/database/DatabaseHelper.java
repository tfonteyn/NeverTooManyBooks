package com.eleybourn.bookcatalogue.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
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
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LIST_PRICE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;


/**
 * This is a specific version of {@link SQLiteOpenHelper}. It handles {@link #onCreate} and {@link #onUpgrade}
 *
 * This used to be an inner class of {@link CatalogueDBAdapter}
 * Externalised ONLY because of the size of the class
 * ONLY used by {@link CatalogueDBAdapter}
 *
 * @author evan
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    //TODO: Update database version RELEASE: Update database version
    private static final int DATABASE_VERSION = 82;

    // We tried 'Collate UNICODE' but it seemed to be case sensitive. We ended
    // up with 'Ursula Le Guin' and 'Ursula le Guin'.
    //
    //	We now use Collate LOCALE and check to see if it is case sensitive. We *hope* in the
    // future Android will add LOCALE_CI (or equivalent).
    //
    //public static final String COLLATION = "Collate NOCASE";
    //public static final String COLLATION = " Collate UNICODE ";
    public static final String COLLATION = " Collate LOCALIZED "; // NOTE: Important to have start/end spaces!

    ////////////////////////////////////////////////////////////////////////////////////////////////

    //<editor-fold desc="Create statements">
    /* Database creation sql statement */
    static final String DATABASE_CREATE_AUTHORS =
            "create table " + DB_TB_AUTHORS +
                    " (_id integer primary key autoincrement, " +
                    DOM_AUTHOR_FAMILY_NAME + " text not null, " +
                    DOM_AUTHOR_GIVEN_NAMES + " text not null" +
                    ")";
    static final String DATABASE_CREATE_BOOKSHELF =
            "create table " + DB_TB_BOOKSHELF +
                    " (_id integer primary key autoincrement, " +
                    DOM_BOOKSHELF + " text not null " +
                    ")";
    static final String DATABASE_CREATE_BOOKSHELF_DATA =
            "INSERT INTO " + DB_TB_BOOKSHELF +
                    " (" + DOM_BOOKSHELF + ") VALUES ('Default')";
    static final String DATABASE_CREATE_LOAN =
            "create table " + DB_TB_LOAN +
                    " (_id integer primary key autoincrement, " +
                    DOM_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
                    DOM_LOANED_TO + " text " +
                    ")";
    static final String DATABASE_CREATE_ANTHOLOGY =
            "create table " + DB_TB_ANTHOLOGY +
                    " (_id integer primary key autoincrement, " +
                    DOM_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
                    DOM_AUTHOR_ID + " integer not null REFERENCES " + DB_TB_AUTHORS + ", " +
                    DOM_TITLE + " text not null, " +
                    DOM_POSITION + " int" +
                    ")";
    static final String DATABASE_CREATE_BOOK_BOOKSHELF_WEAK =
            "create table " + DB_TB_BOOK_BOOKSHELF_WEAK + "(" +
                    DOM_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
                    DOM_BOOKSHELF + " integer REFERENCES " + DB_TB_BOOKSHELF + " ON DELETE SET NULL ON UPDATE SET NULL" +
                    ")";
    static final String DATABASE_CREATE_SERIES =
            "create table " + DB_TB_SERIES +
                    " (_id integer primary key autoincrement, " +
                    DOM_SERIES_NAME + " text not null " +
                    ")";

    static final String DATABASE_CREATE_BOOK_SERIES =
            "create table " + DB_TB_BOOK_SERIES + "(" +
                    DOM_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE, " +
                    DOM_SERIES_ID + " integer REFERENCES " + DB_TB_SERIES + " ON DELETE SET NULL ON UPDATE CASCADE, " +
                    DOM_SERIES_NUM + " text, " +
                    DOM_SERIES_POSITION + " integer," +
                    "PRIMARY KEY(" + DOM_BOOK + ", " + DOM_SERIES_POSITION + ")" +
                    ")";
    static final String DATABASE_CREATE_BOOK_AUTHOR =
            "create table " + DB_TB_BOOK_AUTHOR + "(" +
                    DOM_BOOK + " integer REFERENCES " + DB_TB_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE, " +
                    DOM_AUTHOR_ID + " integer REFERENCES " + DB_TB_AUTHORS + " ON DELETE SET NULL ON UPDATE CASCADE, " +
                    DOM_AUTHOR_POSITION + " integer NOT NULL, " +
                    "PRIMARY KEY(" + DOM_BOOK + ", " + DOM_AUTHOR_POSITION + ")" +
                    ")";

    /**
     * NOTE: **NEVER** change this. Rename it and move it to {@link UpgradeDatabase#doUpgrade},
     * and create a new one. Unless you know what you are doing.
     */
    private static final String DATABASE_CREATE_BOOKS =
            "create table " + DB_TB_BOOKS + " (_id integer primary key autoincrement, " +

                    DOM_TITLE.getDefinition(true) + ", " +
                    DOM_ISBN.getDefinition(true) + ", " +
                    DOM_PUBLISHER.getDefinition(true) + ", " +
                    DOM_DATE_PUBLISHED.getDefinition(true) + ", " +
                    DOM_RATING.getDefinition(true) + ", " +
                    DOM_READ.getDefinition(true) + ", " +
                    DOM_PAGES.getDefinition(true) + ", " +
                    DOM_NOTES.getDefinition(true) + ", " +
                    DOM_LIST_PRICE.getDefinition(true) + ", " +
                    DOM_ANTHOLOGY_MASK.getDefinition(true) + ", " +
                    DOM_LOCATION.getDefinition(true) + ", " +
                    DOM_READ_START.getDefinition(true) + ", " +
                    DOM_READ_END.getDefinition(true) + ", " +
                    DOM_FORMAT.getDefinition(true) + ", " +
                    DOM_SIGNED.getDefinition(true) + ", " +
                    DOM_DESCRIPTION.getDefinition(true) + ", " +
                    DOM_GENRE.getDefinition(true) + ", " +
                    DOM_LANGUAGE.getDefinition(true) + ", " + // Added in version 82
                    DOM_DATE_ADDED.getDefinition(true) + ", " +
                    DOM_GOODREADS_BOOK_ID.getDefinition(true) + ", " +
                    DOM_GOODREADS_LAST_SYNC_DATE.getDefinition(true) + ", " +
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
    private static final String[] DATABASE_CREATE_INDICES = {
            "CREATE INDEX IF NOT EXISTS authors_given_names ON " + DB_TB_AUTHORS + " (" + DOM_AUTHOR_GIVEN_NAMES + ");",
            "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON " + DB_TB_AUTHORS + " (" + DOM_AUTHOR_GIVEN_NAMES + " " + COLLATION + ");",
            "CREATE INDEX IF NOT EXISTS authors_family_name ON " + DB_TB_AUTHORS + " (" + DOM_AUTHOR_FAMILY_NAME + ");",
            "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON " + DB_TB_AUTHORS + " (" + DOM_AUTHOR_FAMILY_NAME + " " + COLLATION + ");",
            "CREATE INDEX IF NOT EXISTS bookshelf_bookshelf ON " + DB_TB_BOOKSHELF + " (" + DOM_BOOKSHELF + ");",
            /* "CREATE INDEX IF NOT EXISTS books_author ON "+DB_TB_BOOKS+" ("+KEY_AUTHOR+");",*/
            /*"CREATE INDEX IF NOT EXISTS books_author_ci ON "+DB_TB_BOOKS+" ("+KEY_AUTHOR+" " + COLLATION + ");",*/
            "CREATE INDEX IF NOT EXISTS books_title ON " + DB_TB_BOOKS + " (" + DOM_TITLE + ");",
            "CREATE INDEX IF NOT EXISTS books_title_ci ON " + DB_TB_BOOKS + " (" + DOM_TITLE + " " + COLLATION + ");",
            "CREATE INDEX IF NOT EXISTS books_isbn ON " + DB_TB_BOOKS + " (" + DOM_ISBN + ");",
            /* "CREATE INDEX IF NOT EXISTS books_series ON "+DB_TB_BOOKS+" ("+KEY_SERIES+");",*/
            "CREATE INDEX IF NOT EXISTS books_publisher ON " + DB_TB_BOOKS + " (" + DOM_PUBLISHER + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS books_uuid ON " + DB_TB_BOOKS + " (" + DOM_BOOK_UUID + ");",
            "CREATE INDEX IF NOT EXISTS books_gr_book ON " + DB_TB_BOOKS + " (" + DOM_GOODREADS_BOOK_ID.name + ");",
            "CREATE INDEX IF NOT EXISTS anthology_book ON " + DB_TB_ANTHOLOGY + " (" + DOM_BOOK + ");",
            "CREATE INDEX IF NOT EXISTS anthology_author ON " + DB_TB_ANTHOLOGY + " (" + DOM_AUTHOR_ID + ");",
            "CREATE INDEX IF NOT EXISTS anthology_title ON " + DB_TB_ANTHOLOGY + " (" + DOM_TITLE + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS series_series ON " + DB_TB_SERIES + " (" + DOM_ID + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON " + DB_TB_LOAN + " (" + DOM_BOOK + ");",
            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_book ON " + DB_TB_BOOK_BOOKSHELF_WEAK + " (" + DOM_BOOK + ");",
            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_bookshelf ON " + DB_TB_BOOK_BOOKSHELF_WEAK + " (" + DOM_BOOKSHELF + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_series ON " + DB_TB_BOOK_SERIES + " (" + DOM_SERIES_ID + ", " + DOM_BOOK + ", " + DOM_SERIES_NUM + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_book ON " + DB_TB_BOOK_SERIES + " (" + DOM_BOOK + ", " + DOM_SERIES_ID + ", " + DOM_SERIES_NUM + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_author ON " + DB_TB_BOOK_AUTHOR + " (" + DOM_AUTHOR_ID + ", " + DOM_BOOK + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_book ON " + DB_TB_BOOK_AUTHOR + " (" + DOM_BOOK + ", " + DOM_AUTHOR_ID + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + DB_TB_ANTHOLOGY + " (" + DOM_BOOK + ", " + DOM_AUTHOR_ID + ", " + DOM_TITLE + ")"
    };
    //</editor-fold>

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static DbSync.Synchronizer mSynchronizer;
    private static boolean mDbWasCreated;
    private static String mMessage = "";

    DatabaseHelper(@NonNull final Context context,
                   @NonNull final SQLiteDatabase.CursorFactory mTrackedCursorFactory,
                   @NonNull final DbSync.Synchronizer synchronizer) {
        super(context, StorageUtils.getDatabaseName(), mTrackedCursorFactory, DATABASE_VERSION);
        mSynchronizer = synchronizer;
    }

    public static String getMessage() {
        return mMessage;
    }

    /**
     * @return a boolean indicating if this was a new install
     */
    public boolean isNewInstall() {
        return mDbWasCreated;
    }

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

        DbSync.SynchronizedDb syncedDb = new DbSync.SynchronizedDb(db, mSynchronizer);

        DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS.createAll(syncedDb, true);
        DatabaseDefinitions.TBL_BOOKS_FTS.create(syncedDb, false);
        DatabaseDefinitions.TBL_BOOK_LIST_STYLES.createAll(syncedDb, true);

        createTriggers(syncedDb);
    }

    /**
     * Create the database triggers. Currently only one, and the implementation needs refining!
     */
    private void createTriggers(DbSync.SynchronizedDb db) {
        String name = "books_tg_reset_goodreads";
        String body = " after update of isbn on books for each row\n" +
                " When New." + DOM_ISBN + " <> Old." + DOM_ISBN + "\n" +
                "	Begin \n" +
                "		Update books Set \n" +
                "		    goodreads_book_id = 0,\n" +
                "		    last_goodreads_sync_date = ''\n" +
                "		Where\n" +
                "			" + DOM_ID + " = new." + DOM_ID + ";\n" +
                "	End";
        db.execSQL("Drop Trigger if Exists " + name);
        db.execSQL("Create Trigger " + name + body);
    }

    private void createIndices(SQLiteDatabase db) {
        //delete all indices first
        String sql = "select name from sqlite_master where type = 'index' and sql is not null;";
        try (Cursor current = db.rawQuery(sql, new String[]{})) {
            while (current.moveToNext()) {
                String index_name = current.getString(0);
                String delete_sql = "DROP INDEX " + index_name;
                //db.beginTransaction();
                try {
                    db.execSQL(delete_sql);
                    //db.setTransactionSuccessful();
                } catch (Exception ignore) {
                    Logger.logError(ignore, "Index deletion failed (probably not a problem)");
                } //finally {
                //db.endTransaction();
                //}
            }
        }

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
            } //finally {
            //db.endTransaction();
            //}
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
     * @param db         The database to be upgraded
     * @param oldVersion The current version number of the database
     * @param newVersion The new version number of the database
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        mDbWasCreated = false;

        int curVersion = oldVersion;

        StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.updateProgress(R.string.upgrading_ellipsis);
        }

        if (oldVersion != newVersion) {
            StorageUtils.backupDbFile(db, "DbUpgrade-" + oldVersion + "-" + newVersion);
        }

        if (curVersion < 11) {
            onCreate(db);
        }

        DbSync.SynchronizedDb syncedDb = new DbSync.SynchronizedDb(db, mSynchronizer);

        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
        // NOTE: As of 4.2, DO NOT USE OnUpgrade TO DISPLAY UPGRADE MESSAGES. See header for details.
        // ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

        curVersion = UpgradeDatabase.doUpgrade(db, syncedDb, curVersion);
        mMessage += UpgradeDatabase.getMessage();

        if (curVersion == 81) {
            curVersion++;
            UpgradeDatabase.recreateAndReloadTable(syncedDb, DB_TB_BOOKS, DATABASE_CREATE_BOOKS);
        }


        // Rebuild all indices
        createIndices(db);
        // Rebuild all triggers
        createTriggers(syncedDb);

        //TODO: NOTE: END OF UPDATE
    }
}
