package com.eleybourn.bookcatalogue.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_ANTHOLOGY_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISFDB_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_TOC_ENTRY_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_PAID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_PAID_CURRENCY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_END;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_READ_START;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_NUM;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SERIES_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_SIGNED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_ANTHOLOGY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_LOAN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_SERIES;


/**
 * This is a specific version of {@link SQLiteOpenHelper}. It handles {@link #onCreate} and {@link #onUpgrade}
 *
 * This used to be an inner class of {@link CatalogueDBAdapter}
 * Externalised ONLY because of the size of the class.
 * ONLY used by {@link CatalogueDBAdapter}
 *
 * @author evan
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    /** RELEASE: Update database version */
    private static final int DATABASE_VERSION = 83; // last official version was 82
    /** the one and only */
    private static final String DATABASE_NAME = "book_catalogue";

    /** We tried 'Collate UNICODE' but it seemed to be case sensitive. We ended
     * up with 'Ursula Le Guin' and 'Ursula le Guin'.
     *
     * We now use Collate LOCALE and check to see if it is case sensitive. We *hope* in the
     * future Android will add LOCALE_CI (or equivalent).
     *
     * public static final String COLLATION = " Collate NOCASE ";
     * public static final String COLLATION = " Collate UNICODE ";
     *
     * NOTE: Important to have start/end spaces!
     */
    public static final String COLLATION = " Collate LOCALIZED ";

    /**
     * NOTE: ***BEFORE*** changing any CREATE TABLE statement:
     *
     * Making changes:
     * 1. copy the create statement to a renamed version in {@link UpgradeDatabase#doUpgrade}
     * 2. Find all uses in {@link UpgradeDatabase} and make sure upgrades use the 'old' version of the statement
     * 3. modify the current, and if not already so, make it 'private'. (tables that have never changed since conception will be package-private)
     * 4. modify {@link DomainDefinition} if needed to reflect the new version
     *
     * Reminder: do NOT use {@link DomainDefinition} here ! Those can change in newer versions.
     */

    /* Database creation sql statement */
    static final String DATABASE_CREATE_AUTHORS =
            "CREATE table " + TBL_AUTHORS + " (_id integer primary key autoincrement, " +
                    DOM_AUTHOR_FAMILY_NAME + " text not null, " +
                    DOM_AUTHOR_GIVEN_NAMES + " text not null" +
                    ")";
    static final String DATABASE_CREATE_BOOKSHELF =
            "CREATE table " + TBL_BOOKSHELF + " (_id integer primary key autoincrement, " +
                    DOM_BOOKSHELF + " text not null " +
                    ")";

    /** this inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf} */
    static final String DATABASE_CREATE_BOOKSHELF_DATA =
            "INSERT INTO " + TBL_BOOKSHELF + " (" + DOM_BOOKSHELF + ") VALUES ('" + BookCatalogueApp.getResourceString(R.string.initial_bookshelf) +
                    "')";

    static final String DATABASE_CREATE_LOAN =
            "CREATE TABLE " + TBL_LOAN + " (_id integer primary key autoincrement, " +
                    DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
                    DOM_LOANED_TO + " text " +
                    ")";
    static final String DATABASE_CREATE_BOOK_BOOKSHELF_WEAK =
            "CREATE TABLE " + TBL_BOOK_BOOKSHELF + "(" +
                    DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
                    DOM_FK_BOOKSHELF_ID + " integer REFERENCES " + TBL_BOOKSHELF + " ON DELETE SET NULL ON UPDATE SET NULL" +
                    ")";
    static final String DATABASE_CREATE_SERIES =
            "CREATE TABLE " + TBL_SERIES + " (_id integer primary key autoincrement, " +
                    DOM_SERIES_NAME + " text not null " +
                    ")";
    static final String DATABASE_CREATE_BOOK_SERIES =
            "CREATE TABLE " + TBL_BOOK_SERIES + "(" +
                    DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE, " +
                    DOM_FK_SERIES_ID + " integer REFERENCES " + TBL_SERIES + " ON DELETE SET NULL ON UPDATE CASCADE, " +
                    DOM_BOOK_SERIES_NUM + " text, " +
                    DOM_BOOK_SERIES_POSITION + " integer," +
                    "PRIMARY KEY(" + DOM_FK_BOOK_ID + ", " + DOM_BOOK_SERIES_POSITION + ")" +
                    ")";
    static final String DATABASE_CREATE_BOOK_AUTHOR =
            "CREATE TABLE " + TBL_BOOK_AUTHOR + "(" +
                    DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE, " +
                    DOM_FK_AUTHOR_ID + " integer REFERENCES " + TBL_AUTHORS + " ON DELETE SET NULL ON UPDATE CASCADE, " +
                    DOM_AUTHOR_POSITION + " integer NOT NULL, " +
                    "PRIMARY KEY(" + DOM_FK_BOOK_ID + ", " + DOM_AUTHOR_POSITION + ")" +
                    ")";
    private static final String DATABASE_CREATE_ANTHOLOGY =
            "CREATE TABLE " + TBL_ANTHOLOGY + " (_id integer primary key autoincrement, " +
                    DOM_FK_AUTHOR_ID + " integer not null REFERENCES " + TBL_AUTHORS + ", " +
                    DOM_TITLE + " text not null, " +
                    DOM_FIRST_PUBLICATION + " date" +
                    ")";
    private static final String DATABASE_CREATE_BOOK_TOC_ENTRIES =
            "CREATE TABLE " + TBL_BOOK_TOC_ENTRIES + "(_id integer primary key autoincrement, " +
                    DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL, " +
                    DOM_FK_ANTHOLOGY_ID + " integer REFERENCES " + TBL_ANTHOLOGY + " ON DELETE CASCADE ON UPDATE CASCADE, " +
                    DOM_BOOK_TOC_ENTRY_POSITION + " integer" +
                    ")";

    private static final String DATABASE_CREATE_BOOKS =
            "CREATE TABLE " + TBL_BOOKS + " (_id integer primary key autoincrement, " +
                    DOM_TITLE + " text not null, " +
                    DOM_BOOK_ISBN + " text, " +
                    DOM_BOOK_PUBLISHER + " text, " +
                    DOM_BOOK_DATE_PUBLISHED + " date, " +
                    DOM_FIRST_PUBLICATION +  " date default '', " +
                    DOM_BOOK_EDITION_BITMASK + " integer not null default 0, " +
                    DOM_BOOK_RATING + " real not null default 0, " +
                    DOM_BOOK_READ + " boolean not null default 0, " +
                    DOM_BOOK_PAGES + " integer, " +
                    DOM_BOOK_NOTES + " text, " +

                    DOM_BOOK_PRICE_LISTED + " text default '', " +
                    DOM_BOOK_PRICE_LISTED_CURRENCY + " text default '', " +
                    DOM_BOOK_PRICE_PAID + " text default '', " +
                    DOM_BOOK_PRICE_PAID_CURRENCY + " text default '', " +

                    DOM_BOOK_ANTHOLOGY_BITMASK + " integer not null default 0, " +
                    DOM_BOOK_LOCATION + " text, " +
                    DOM_BOOK_READ_START + " date, " +
                    DOM_BOOK_READ_END + " date, " +
                    DOM_BOOK_FORMAT + " text, " +
                    DOM_BOOK_SIGNED + " boolean not null default 0, " +
                    DOM_DESCRIPTION + " text, " +
                    DOM_BOOK_GENRE + " text, " +
                    DOM_BOOK_LANGUAGE + " text default '', " +
                    DOM_BOOK_DATE_ADDED + " datetime default current_timestamp, " +

                    DOM_BOOK_ISFDB_ID + " integer, " +
                    DOM_BOOK_LIBRARY_THING_ID + " integer, " +
                    DOM_BOOK_GOODREADS_BOOK_ID + " integer, " +
                    DOM_BOOK_GOODREADS_LAST_SYNC_DATE + " date default '0000-00-00', " +

                    DOM_BOOK_UUID + " text not null default (lower(hex(randomblob(16)))), " +
                    DOM_LAST_UPDATE_DATE + " datetime not null default current_timestamp " +
                    ")";

    private static final String[] DATABASE_CREATE_INDICES = {
            "CREATE INDEX IF NOT EXISTS authors_given_names ON " + TBL_AUTHORS + " (" + DOM_AUTHOR_GIVEN_NAMES + ");",
            "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON " + TBL_AUTHORS + " (" + DOM_AUTHOR_GIVEN_NAMES + " " + COLLATION + ");",
            "CREATE INDEX IF NOT EXISTS authors_family_name ON " + TBL_AUTHORS + " (" + DOM_AUTHOR_FAMILY_NAME + ");",
            "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON " + TBL_AUTHORS + " (" + DOM_AUTHOR_FAMILY_NAME + " " + COLLATION + ");",

            "CREATE INDEX IF NOT EXISTS bookshelf_bookshelf ON " + TBL_BOOKSHELF + " (" + DOM_BOOKSHELF + ");",

            "CREATE INDEX IF NOT EXISTS books_title ON " + TBL_BOOKS + " (" + DOM_TITLE + ");",
            "CREATE INDEX IF NOT EXISTS books_title_ci ON " + TBL_BOOKS + " (" + DOM_TITLE + " " + COLLATION + ");",
            "CREATE INDEX IF NOT EXISTS books_isbn ON " + TBL_BOOKS + " (" + DOM_BOOK_ISBN + ");",
            "CREATE INDEX IF NOT EXISTS books_publisher ON " + TBL_BOOKS + " (" + DOM_BOOK_PUBLISHER + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS books_uuid ON " + TBL_BOOKS + " (" + DOM_BOOK_UUID + ");",
            "CREATE INDEX IF NOT EXISTS books_gr_book ON " + TBL_BOOKS + " (" + DOM_BOOK_GOODREADS_BOOK_ID + ");",

            "CREATE UNIQUE INDEX IF NOT EXISTS series_series ON " + TBL_SERIES + " (" + DOM_PK_ID + ");",

            "CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON " + TBL_LOAN + " (" + DOM_FK_BOOK_ID + ");",

            "CREATE INDEX IF NOT EXISTS anthology_author ON " + TBL_ANTHOLOGY + " (" + DOM_FK_AUTHOR_ID + ");",
            "CREATE INDEX IF NOT EXISTS anthology_title ON " + TBL_ANTHOLOGY + " (" + DOM_TITLE + ");",

            "CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + TBL_ANTHOLOGY + " (" + DOM_FK_AUTHOR_ID + ", " + DOM_TITLE + ")",

            "CREATE INDEX IF NOT EXISTS book_anthology_anthology ON " + TBL_BOOK_TOC_ENTRIES + " (" + DOM_FK_ANTHOLOGY_ID + ");",
            "CREATE INDEX IF NOT EXISTS book_anthology_book ON " + TBL_BOOK_TOC_ENTRIES + " (" + DOM_FK_BOOK_ID + ");",

            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_book ON " + TBL_BOOK_BOOKSHELF + " (" + DOM_FK_BOOK_ID + ");",
            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_bookshelf ON " + TBL_BOOK_BOOKSHELF + " (" + DOM_FK_BOOKSHELF_ID + ");",

            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_series ON " + TBL_BOOK_SERIES + " (" + DOM_FK_SERIES_ID + ", " + DOM_FK_BOOK_ID + ", " + DOM_BOOK_SERIES_NUM + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_book ON " + TBL_BOOK_SERIES + " (" + DOM_FK_BOOK_ID + ", " + DOM_FK_SERIES_ID + ", " + DOM_BOOK_SERIES_NUM + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_author ON " + TBL_BOOK_AUTHOR + " (" + DOM_FK_AUTHOR_ID + ", " + DOM_FK_BOOK_ID + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_book ON " + TBL_BOOK_AUTHOR + " (" + DOM_FK_BOOK_ID + ", " + DOM_FK_AUTHOR_ID + ");",
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static DbSync.Synchronizer mSynchronizer;
    private static boolean mDbWasCreated;
    private static String mMessage = "";

    DatabaseHelper(final @NonNull Context context,
                   @SuppressWarnings("SameParameterValue") final @NonNull SQLiteDatabase.CursorFactory trackedCursorFactory,
                   @SuppressWarnings("SameParameterValue") final @NonNull DbSync.Synchronizer synchronizer) {
        super(context, DATABASE_NAME, trackedCursorFactory, DATABASE_VERSION);
        mSynchronizer = synchronizer;
    }

    public static String getMessage() {
        return mMessage;
    }

    public static String getDatabasePath(final @NonNull Context context) {
        return context.getDatabasePath(DATABASE_NAME).getAbsolutePath();
    }

    /**
     * For the upgrade to version 83, all cover files were moved to a sub directory.
     *
     * This routine renames all files, if they exist.
     */
    private static void v83_moveCoversToDedicatedDirectory(final @NonNull DbSync.SynchronizedDb db) {

        try (Cursor cur = db.rawQuery("SELECT " + DOM_BOOK_UUID + " FROM " + TBL_BOOKS, new String[]{})) {
            while (cur.moveToNext()) {
                final String uuid = cur.getString(0);
                File file = StorageUtils.getFile(uuid + ".jpg");
                if (!file.exists()) {
                    file = StorageUtils.getFile(uuid + ".png");
                    if (!file.exists()) {
                        continue;
                    }
                }
                File newFile = StorageUtils.getCoverFile(uuid);
                StorageUtils.renameFile(file, newFile);
            }
        }
    }

    /**
     * @return a boolean indicating if this was a new install
     */
    boolean isNewInstall() {
        return mDbWasCreated;
    }

    /**
     * This function is called when the database is first created
     *
     * @param db The database to be created
     */
    @Override
    @CallSuper
    public void onCreate(@NonNull SQLiteDatabase db) {
        Logger.info(this,"Creating database: " + db.getPath());

        mDbWasCreated = true;
        db.execSQL(DATABASE_CREATE_AUTHORS);
        db.execSQL(DATABASE_CREATE_BOOKSHELF);
        db.execSQL(DATABASE_CREATE_BOOKS); // RELEASE: Make sure this is always DATABASE_CREATE_BOOKS after a rename of the original
        db.execSQL(DATABASE_CREATE_BOOKSHELF_DATA);
        db.execSQL(DATABASE_CREATE_LOAN);
        db.execSQL(DATABASE_CREATE_ANTHOLOGY);
        db.execSQL(DATABASE_CREATE_BOOK_TOC_ENTRIES);
        db.execSQL(DATABASE_CREATE_SERIES);
        db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);
        db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF_WEAK);
        db.execSQL(DATABASE_CREATE_BOOK_SERIES);
        createIndices(db);

        DbSync.SynchronizedDb syncedDb = new DbSync.SynchronizedDb(db, mSynchronizer);

        DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS.createAll(syncedDb, true);
        DatabaseDefinitions.TBL_BOOKS_FTS.create(syncedDb, false);
        DatabaseDefinitions.TBL_BOOKLIST_STYLES.createAll(syncedDb, true);

        createTriggers(syncedDb);
    }

    /**
     * Create the database triggers. Currently only one, and the implementation needs refining!
     */
    private void createTriggers(DbSync.SynchronizedDb db) {
        String name = "books_tg_reset_goodreads";
        String body = " after update of isbn ON books for each row\n" +
                " When New." + DOM_BOOK_ISBN + " <> Old." + DOM_BOOK_ISBN + "\n" +
                "	Begin \n" +
                "		UPDATE books SET \n" +
                "		    goodreads_book_id = 0,\n" +
                "		    last_goodreads_sync_date = ''\n" +
                "		WHERE\n" +
                "			" + DOM_PK_ID + " = new." + DOM_PK_ID + ";\n" +
                "	End";
        db.execSQL("DROP TRIGGER if Exists " + name);
        db.execSQL("CREATE TRIGGER " + name + body);
    }

    private void createIndices(@NonNull SQLiteDatabase db) {
        //delete all indices first
        String sql = "SELECT name FROM sqlite_master WHERE type = 'index' AND sql is not null;";
        try (Cursor current = db.rawQuery(sql, new String[]{})) {
            while (current.moveToNext()) {
                String index_name = current.getString(0);
                String delete_sql = "DROP INDEX " + index_name;
                try {
                    db.execSQL(delete_sql);
                } catch (Exception e) {
                    Logger.error(e, "Index deletion failed (probably not a problem)");
                }
            }
        }

        for (String index : DATABASE_CREATE_INDICES) {
            // Avoid index creation killing an upgrade because this method may be called by any
            // upgrade script and some tables may not yet exist. We probably still want indexes.
            //
            // Ideally, whenever an upgrade script is written, the 'createIndices()' call
            // from prior upgrades should be removed saved and used and a new version of this
            // script written for the new DB.
            try {
                db.execSQL(index);
            } catch (Exception e) {
                // Expected on multi-version upgrades.
                Logger.error(e, "Index creation failed (probably not a problem), definition was: " + index);
            }
        }
        db.execSQL("analyze");
    }

    /**
     * This function is called each time the database is upgraded. The function will run all
     * upgrade scripts between the oldVersion and the newVersion.
     *
     * @see #DATABASE_VERSION
     *
     * @param db         The database to be upgraded
     * @param oldVersion The current version number of the database
     * @param newVersion The new version number of the database
     */
    @Override
    public void onUpgrade(final @NonNull SQLiteDatabase db, final int oldVersion, final int newVersion) {
        Logger.info(this,"Upgrading database: " + db.getPath());
        mDbWasCreated = false;

        int curVersion = oldVersion;

        StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.updateProgress(R.string.progress_msg_upgrading);
        }

        if (oldVersion != newVersion) {
            StorageUtils.backupFile(db.getPath(), "DbUpgrade-" + oldVersion + "-" + newVersion);
        }

        if (curVersion < 11) {
            onCreate(db);
        }

        DbSync.SynchronizedDb syncedDb = new DbSync.SynchronizedDb(db, mSynchronizer);

        // older version upgrades archived/execute in UpgradeDatabase
        curVersion = UpgradeDatabase.doUpgrade(db, syncedDb, curVersion);
        mMessage += UpgradeDatabase.getMessage();

        if (curVersion < newVersion && curVersion == 82) {
            //noinspection UnusedAssignment
            curVersion++;
            mMessage += "New in v83:\n\n";
            mMessage += "* Moved to base Android 5.0 bringing lots of new UI goodies.";
            mMessage += "* Removed the old 'Classic' view (sorry)\n";
            mMessage += "* Cover thumbnails are moved to a 'covers' sub folder to clean up the root folder.\n\n";
            mMessage += "* Configurable website search order.\n";
            mMessage += "* New Search website: ISFDB.\n";
            mMessage += "* Better Anthology support (Edit/View/cross-book)\n";
            mMessage += "* Anthology titles auto populated (ISFDB site only!)\n";
            mMessage += "* New fields:\n";
            mMessage += "*   Books & Anthology titles now have a 'first published' field\n";
            mMessage += "*   Books Editions (1st, book-club, etc)\n";
            mMessage += "*   Book paid price field\n";
            mMessage += "*   Currency support for prices\n";
            mMessage += "*   LibraryThing & ISFDB local id\n";

            // cleanup of obsolete preferences
            BookCatalogueApp.Prefs.remove("StartupActivity.FAuthorSeriesFixupRequired");
            BookCatalogueApp.Prefs.remove("start_in_my_books");
            BookCatalogueApp.Prefs.remove("App.includeClassicView");
            BookCatalogueApp.Prefs.remove("App.DisableBackgroundImage");
            BookCatalogueApp.Prefs.remove("App.BooklistStyle");


            v83_moveCoversToDedicatedDirectory(syncedDb);

            //TEST a proper upgrade from 82 to 83

            // move the existing book-anthology links to the new table
            db.execSQL(DATABASE_CREATE_BOOK_TOC_ENTRIES);
            db.execSQL("INSERT INTO " + TBL_BOOK_TOC_ENTRIES +
                    " SELECT " + DOM_FK_BOOK_ID + ", " + DOM_PK_ID + ", " + DOM_BOOK_TOC_ENTRY_POSITION + " FROM " + TBL_ANTHOLOGY);

            // reorganise the original table
            UpgradeDatabase.recreateAndReloadTable(syncedDb, TBL_ANTHOLOGY.getName(), DATABASE_CREATE_ANTHOLOGY,
                    /* remove fields: */ DOM_FK_BOOK_ID.name, DOM_BOOK_TOC_ENTRY_POSITION.name);

            // add new fields
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_FIRST_PUBLICATION + " date");
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_EDITION_BITMASK + " integer NOT NULL default 0");

            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_PRICE_LISTED_CURRENCY + " text default ''");
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_PRICE_PAID + " text default ''");
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_PRICE_PAID_CURRENCY + " text default ''");

            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_ISFDB_ID + " integer");
            db.execSQL("ALTER TABLE " + TBL_BOOKS + " ADD " + DOM_BOOK_LIBRARY_THING_ID + " integer");


            // all books with a list price are assumed to be USD based on the only search up to v82 being Amazon US.
            // FIXME upgrade if a user has manually edited the list price, ouch....
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_PRICE_LISTED_CURRENCY + "='USD' WHERE NOT " + DOM_BOOK_PRICE_LISTED + "=''");
        }

        // Rebuild all indices
        createIndices(db);
        // Rebuild all triggers
        createTriggers(syncedDb);
    }
}
