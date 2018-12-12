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
import com.eleybourn.bookcatalogue.utils.UpgradeMessageManager;
import com.eleybourn.bookcatalogue.utils.UpgradeMigrations;

import java.io.File;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_AUTHOR_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ACQUIRED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_PUBLISHED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DESCRIPTION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_EDITION_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISBN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ISFDB_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_NOTES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PAGES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PRICE_LISTED;
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
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_TOC_ENTRY_POSITION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_UUID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FIRST_PUBLICATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_TOC_ENTRY_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_AUTHORS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKLIST_STYLES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOKS_FTS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_AUTHOR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_LIST_NODE_SETTINGS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_LOAN;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_SERIES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.TBL_TOC_ENTRIES;


/**
 * This is a specific version of {@link SQLiteOpenHelper}. It handles {@link #onCreate} and {@link #onUpgrade}
 *
 * This used to be an inner class of {@link CatalogueDBAdapter}
 * Externalised because of the size of the class. ONLY used by {@link CatalogueDBAdapter}
 *
 * @author evan
 */
public class CatalogueDBHelper extends SQLiteOpenHelper {

    /** RELEASE: Update database version */
    public static final int DATABASE_VERSION = 83; // last official version was 82

    /** the one and only */
    private static final String DATABASE_NAME = "book_catalogue";

    /**
     * In addition to SQLite's default BINARY collator (others: NOCASE and RTRIM)
     * Android supplies two more:
     * LOCALIZED: using the system's current locale,
     * UNICODE  : Unicode Collation Algorithm and not tailored to the current locale.
     *
     *
     * We tried 'Collate UNICODE' but it seemed to be case sensitive. We ended
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


    static final String DATABASE_CREATE_BOOKSHELF =
            "CREATE table " + TBL_BOOKSHELF + " (" +
                    DOM_PK_ID .def(true)+ "," +
                    DOM_BOOKSHELF + " text not null " +
                    ")";
    /** this inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf} */
    static final String DATABASE_CREATE_BOOKSHELF_DATA =
            "INSERT INTO " + TBL_BOOKSHELF + " (" + DOM_BOOKSHELF + ")" +
                    " VALUES ('" + BookCatalogueApp.getResourceString(R.string.initial_bookshelf) +
                    "')";
    static final String DATABASE_CREATE_BOOK_AUTHOR =
            "CREATE TABLE " + TBL_BOOK_AUTHOR + "(" +
                    // if a book is deleted, remove the link
                    DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    // if an author is deleted, remove the link
                    DOM_FK_AUTHOR_ID + " integer REFERENCES " + TBL_AUTHORS + " ON DELETE SET NULL ON UPDATE CASCADE," +
                    DOM_BOOK_AUTHOR_POSITION + " integer not null, " +
                    //TODO: check PRIMARY KEY usage for all 'link' tables.
                    "PRIMARY KEY(" + DOM_FK_BOOK_ID + ", " + DOM_BOOK_AUTHOR_POSITION + ")" +
                    ")";

    static final String DATABASE_CREATE_BOOK_SERIES =
            "CREATE TABLE " + TBL_BOOK_SERIES + "(" +
                    // if a book is deleted, remove the link
                    DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    //TODO: if a series is deleted, leave the row. why ?
                    DOM_FK_SERIES_ID + " integer REFERENCES " + TBL_SERIES + " ON DELETE SET NULL ON UPDATE CASCADE," +
                    DOM_BOOK_SERIES_NUM + " text, " +
                    DOM_BOOK_SERIES_POSITION + " integer not null," +
                    "PRIMARY KEY(" + DOM_FK_BOOK_ID + ", " + DOM_BOOK_SERIES_POSITION + ")" +
                    ")";

    /**
     * NOTE: ***BEFORE*** changing any CREATE TABLE statement:
     *
     * Making changes:
     * 1. copy the create statement to a renamed version in {@link UpgradeDatabase#doUpgrade}
     * 2. Find all uses in {@link UpgradeDatabase} and make sure upgrades use the 'old/renamed' version of the statement
     * 3. modify the current statement, and if not already so, make it 'private'.
     * (tables that have never changed since conception will be package-private)
     * 4. modify {@link DomainDefinition} if needed to reflect the new version
     * 5. Check and check again, that *everything* EXCEPT {@link UpgradeDatabase} is using the current version
     *
     * Reminder: do NOT use {@link DomainDefinition} in create statements !
     * Those can change in newer versions.
     */

    /* Database creation sql statement */
    private static final String DATABASE_CREATE_AUTHORS =
            "CREATE table " + TBL_AUTHORS + " (" +
                    DOM_PK_ID .def(true)+ "," +
                    DOM_AUTHOR_FAMILY_NAME + " text not null," +
                    DOM_AUTHOR_GIVEN_NAMES + " text not null default ''," +
                    DOM_AUTHOR_IS_COMPLETE + " boolean not null default 0" +
                    ")";
    private static final String DATABASE_CREATE_BOOK_BOOKSHELF =
            "CREATE TABLE " + TBL_BOOK_BOOKSHELF + "(" +
                    DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    DOM_FK_BOOKSHELF_ID + " integer REFERENCES " + TBL_BOOKSHELF + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    ")";
    private static final String DATABASE_CREATE_BOOK_LOAN =
            "CREATE TABLE " + TBL_LOAN + " (" +
                    DOM_PK_ID .def(true)+ "," +
                    DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    DOM_LOANED_TO + " text not null" +
                    ")";
    private static final String DATABASE_CREATE_BOOKS =
            "CREATE TABLE " + TBL_BOOKS + " (" +
                    DOM_PK_ID .def(true)+ "," +
                    DOM_TITLE + " text not null, " +
                    DOM_BOOK_UUID + " text not null default (lower(hex(randomblob(16))))," +

                    DOM_BOOK_ISBN + " text not null default ''," +
                    DOM_BOOK_PUBLISHER + " text not null default ''," +
                    DOM_BOOK_DATE_PUBLISHED + " date not null default ''," +
                    DOM_FIRST_PUBLICATION + " date not null default ''," +
                    DOM_BOOK_EDITION_BITMASK + " integer not null default 0," +
                    DOM_BOOK_ANTHOLOGY_BITMASK + " integer not null default 0," +

                    DOM_BOOK_PRICE_LISTED + " text not null default ''," +
                    DOM_BOOK_PRICE_LISTED_CURRENCY + " text not null default ''," +

                    DOM_BOOK_PRICE_PAID + " text not null default ''," +
                    DOM_BOOK_PRICE_PAID_CURRENCY + " text not null default ''," +
                    DOM_BOOK_DATE_ACQUIRED + " date not null default ''," +

                    DOM_BOOK_FORMAT + " text not null default ''," +
                    DOM_BOOK_GENRE + " text not null default ''," +
                    DOM_BOOK_LANGUAGE + " text not null default ''," +
                    DOM_BOOK_LOCATION + " text not null default ''," +

                    DOM_BOOK_PAGES + " integer," +

                    DOM_BOOK_READ + " boolean not null default 0," +
                    DOM_BOOK_READ_START + " date not null default ''," +
                    DOM_BOOK_READ_END + " date not null default ''," +

                    DOM_BOOK_SIGNED + " boolean not null default 0," +
                    DOM_BOOK_RATING + " real not null default 0," +

                    DOM_BOOK_DESCRIPTION + " text not null default ''," +
                    DOM_BOOK_NOTES + " text not null default ''," +

                    DOM_BOOK_ISFDB_ID + " integer," +
                    DOM_BOOK_LIBRARY_THING_ID + " integer," +
                    DOM_BOOK_GOODREADS_BOOK_ID + " integer," +
                    DOM_BOOK_GOODREADS_LAST_SYNC_DATE + " date default '0000-00-00'," +

                    DOM_BOOK_DATE_ADDED + " datetime not null default current_timestamp," +
                    DOM_LAST_UPDATE_DATE + " datetime not null default current_timestamp" +
                    ")";
    private static final String DATABASE_CREATE_SERIES =
            "CREATE TABLE " + TBL_SERIES + " (" +
                    DOM_PK_ID .def(true)+ "," +
                    DOM_SERIES_NAME + " text not null," +
                    DOM_SERIES_IS_COMPLETE + " boolean not null default 0" +
                    ")";
    private static final String DATABASE_CREATE_TOC_ENTRIES =
            "CREATE TABLE " + TBL_TOC_ENTRIES + " (" +
                    DOM_PK_ID .def(true)+ "," +
                    DOM_FK_AUTHOR_ID + " integer REFERENCES " + TBL_AUTHORS + "," +
                    DOM_TITLE + " text not null, " +
                    DOM_FIRST_PUBLICATION + " date not null default ''," +
                    ")";
    private static final String DATABASE_CREATE_BOOK_TOC_ENTRIES =
            "CREATE TABLE " + TBL_BOOK_TOC_ENTRIES + " (" +
                    DOM_PK_ID .def(true)+ "," +
                    //TODO: if a book is deleted, leave the row. why ?
                    DOM_FK_BOOK_ID + " integer REFERENCES " + TBL_BOOKS + " ON DELETE SET NULL ON UPDATE SET NULL," +
                    // if a TOC entry (anthology title) is deleted, remove the link.
                    DOM_FK_TOC_ENTRY_ID + " integer REFERENCES " + TBL_TOC_ENTRIES + " ON DELETE CASCADE ON UPDATE CASCADE," +
                    DOM_BOOK_TOC_ENTRY_POSITION + " integer not null" +
                    ")";

    /**
     * All the indexes
     */
    private static final String[] DATABASE_CREATE_INDICES = {
            "CREATE INDEX IF NOT EXISTS authors_given_names ON " + TBL_AUTHORS + " (" + DOM_AUTHOR_GIVEN_NAMES + ");",
            "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON " + TBL_AUTHORS + " (" + DOM_AUTHOR_GIVEN_NAMES + " " + COLLATION + ");",

            "CREATE INDEX IF NOT EXISTS authors_family_name ON " + TBL_AUTHORS + " (" + DOM_AUTHOR_FAMILY_NAME + ");",
            "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON " + TBL_AUTHORS + " (" + DOM_AUTHOR_FAMILY_NAME + " " + COLLATION + ");",

            "CREATE INDEX IF NOT EXISTS books_title ON " + TBL_BOOKS + " (" + DOM_TITLE + ");",
            "CREATE INDEX IF NOT EXISTS books_title_ci ON " + TBL_BOOKS + " (" + DOM_TITLE + " " + COLLATION + ");",

            "CREATE INDEX IF NOT EXISTS books_isbn ON " + TBL_BOOKS + " (" + DOM_BOOK_ISBN + ");",
            "CREATE INDEX IF NOT EXISTS books_publisher ON " + TBL_BOOKS + " (" + DOM_BOOK_PUBLISHER + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS books_uuid ON " + TBL_BOOKS + " (" + DOM_BOOK_UUID + ");",
            "CREATE INDEX IF NOT EXISTS books_gr_book ON " + TBL_BOOKS + " (" + DOM_BOOK_GOODREADS_BOOK_ID + ");",

            "CREATE INDEX IF NOT EXISTS bookshelf_bookshelf ON " + TBL_BOOKSHELF + " (" + DOM_BOOKSHELF + ");",

            "CREATE UNIQUE INDEX IF NOT EXISTS series_series ON " + TBL_SERIES + " (" + DOM_PK_ID + ");",

            "CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON " + TBL_LOAN + " (" + DOM_FK_BOOK_ID + ");",

            "CREATE INDEX IF NOT EXISTS anthology_author ON " + TBL_TOC_ENTRIES + " (" + DOM_FK_AUTHOR_ID + ");",
            "CREATE INDEX IF NOT EXISTS anthology_title ON " + TBL_TOC_ENTRIES + " (" + DOM_TITLE + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + TBL_TOC_ENTRIES + " (" + DOM_FK_AUTHOR_ID + ", " + DOM_TITLE + ")",

            "CREATE INDEX IF NOT EXISTS book_anthology_anthology ON " + TBL_BOOK_TOC_ENTRIES + " (" + DOM_FK_TOC_ENTRY_ID + ");",
            "CREATE INDEX IF NOT EXISTS book_anthology_book ON " + TBL_BOOK_TOC_ENTRIES + " " + "(" + DOM_FK_BOOK_ID + ");",

            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_book ON " + TBL_BOOK_BOOKSHELF + " (" + DOM_FK_BOOK_ID + ");",
            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_bookshelf ON " + TBL_BOOK_BOOKSHELF + " (" + DOM_FK_BOOKSHELF_ID + ");",

            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_author ON " + TBL_BOOK_AUTHOR + " (" + DOM_FK_AUTHOR_ID + ", " + DOM_FK_BOOK_ID + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_book ON " + TBL_BOOK_AUTHOR + " (" + DOM_FK_BOOK_ID + ", " + DOM_FK_AUTHOR_ID + ");",

            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_series ON " + TBL_BOOK_SERIES + " (" + DOM_FK_SERIES_ID + ", " + DOM_FK_BOOK_ID + ", " + DOM_BOOK_SERIES_NUM + ");",
            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_book ON " + TBL_BOOK_SERIES + " (" + DOM_FK_BOOK_ID + ", " + DOM_FK_SERIES_ID + ", " + DOM_BOOK_SERIES_NUM + ");",
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static DbSync.Synchronizer mSynchronizer;
    private static String mMessage = "";

    /**
     * @param context      the context
     * @param factory      the cursor factor
     * @param synchronizer needed in onCreate/onUpgrade
     */
    CatalogueDBHelper(final @NonNull Context context,
                      @SuppressWarnings("SameParameterValue") final @NonNull SQLiteDatabase.CursorFactory factory,
                      @SuppressWarnings("SameParameterValue") final @NonNull DbSync.Synchronizer synchronizer) {
        super(context, DATABASE_NAME, factory, DATABASE_VERSION);
        mSynchronizer = synchronizer;
    }

    public static String getMessage() {
        return mMessage;
    }

    public static String getDatabasePath(final @NonNull Context context) {
        return context.getDatabasePath(DATABASE_NAME).getAbsolutePath();
    }

    /**
     * This function is called when the database is first created
     *
     * @param db The database to be created
     */
    @Override
    @CallSuper
    public void onCreate(@NonNull SQLiteDatabase db) {
        Logger.info(this, "Creating database: " + db.getPath());

        DbSync.SynchronizedDb syncedDb = new DbSync.SynchronizedDb(db, mSynchronizer);

        // RELEASE: Make sure these is always DATABASE_CREATE_table WITHOUT VERSIONS after a rename of the original
        db.execSQL(DATABASE_CREATE_AUTHORS);
        db.execSQL(DATABASE_CREATE_BOOKSHELF);
        db.execSQL(DATABASE_CREATE_BOOKSHELF_DATA);
        db.execSQL(DATABASE_CREATE_BOOKS);
        db.execSQL(DATABASE_CREATE_BOOK_LOAN);
        db.execSQL(DATABASE_CREATE_TOC_ENTRIES);
        db.execSQL(DATABASE_CREATE_BOOK_TOC_ENTRIES);
        db.execSQL(DATABASE_CREATE_SERIES);
        db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);
        db.execSQL(DATABASE_CREATE_BOOK_BOOKSHELF);
        db.execSQL(DATABASE_CREATE_BOOK_SERIES);
        createIndices(db);

        // table + indices
        TBL_BOOKLIST_STYLES.createAll(syncedDb, true);
        // table + indices
        TBL_BOOK_LIST_NODE_SETTINGS.createAll(syncedDb, true);

        TBL_BOOKS_FTS.create(syncedDb, false);

        createTriggers(syncedDb);

        // indicate that this is new install; e.g. the 'upgrade' from not having our app before.
        // this avoids messing with 'isNewInstall' etc...
        UpgradeMessageManager.setUpgradeAcknowledged();
    }

    /**
     * Create the database triggers. Currently only one, and the implementation needs refining!
     */
    private void createTriggers(DbSync.SynchronizedDb db) {
        String name = "books_tg_reset_goodreads";
        String body = " after update of " + DOM_BOOK_ISBN + " ON " + TBL_BOOKS + " for each row\n" +
                " When New." + DOM_BOOK_ISBN + " <> Old." + DOM_BOOK_ISBN + "\n" +
                "	Begin \n" +
                "		UPDATE " + TBL_BOOKS + " SET \n" +
                "		    " + DOM_BOOK_GOODREADS_BOOK_ID + "=0,\n" +
                "		    " + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + "=''\n" +
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
     * @param db         The database to be upgraded
     * @param oldVersion The current version number of the database
     * @param newVersion The new version number of the database
     *
     * @see #DATABASE_VERSION
     */
    @Override
    public void onUpgrade(final @NonNull SQLiteDatabase db, final int oldVersion, final int newVersion) {
        Logger.info(this, "Upgrading database: " + db.getPath());

        int curVersion = oldVersion;

        StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.updateProgress(R.string.progress_msg_upgrading);
        }

        if (oldVersion != newVersion) {
            StorageUtils.exportFile(db.getPath(), "DbUpgrade-" + oldVersion + "-" + newVersion);
        }

        if (curVersion < 11) {
            onCreate(db);
        }

        DbSync.SynchronizedDb syncedDb = new DbSync.SynchronizedDb(db, mSynchronizer);

        // older version upgrades archived/execute in UpgradeDatabase
        if (curVersion < 82) {
            curVersion = UpgradeDatabase.doUpgrade(db, syncedDb, curVersion);
            mMessage += UpgradeDatabase.getMessage();
        }

        // db82 == app179 == 5.2.2
        if (curVersion < newVersion && curVersion == 82) {
            //noinspection UnusedAssignment
            curVersion++;
            mMessage += "Major upgrade; restart to see what's new\n\n";

            /* move cover files to a sub-folder.
            Only files with matching rows in 'books' are moved. */
            UpgradeMigrations.v200moveCoversToDedicatedDirectory(syncedDb);

            /* now using the 'real' cache directory */
            StorageUtils.deleteFile(new File(StorageUtils.getSharedStorage() + File.separator + "tmp_images"));

            // migrate the properties.
            UpgradeMigrations.v200preferences(BookCatalogueApp.getSharedPreferences(), true);

            //TEST a proper upgrade from 82 to 83 with non-clean data
            // Due to a number of code remarks, and some observation... let's do a clean of some columns.

            // these two are due to a remark in the CSV exporter that (at one time?) the author name and title could be bad
            final String UNKNOWN = BookCatalogueApp.getResourceString(R.string.unknown);
            db.execSQL("UPDATE " + TBL_AUTHORS + " SET " + DOM_AUTHOR_FAMILY_NAME + "='" + UNKNOWN + "' WHERE " +
                    DOM_AUTHOR_FAMILY_NAME + "='' OR " + DOM_AUTHOR_FAMILY_NAME + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_TITLE + "='" + UNKNOWN + "' WHERE " +
                    DOM_TITLE + "='' OR " + DOM_TITLE + " IS NULL");

            // clean columns where we are adding a "not null default ''" constraint
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_FORMAT + "=''" + " WHERE " + DOM_BOOK_FORMAT + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_GENRE + "=''" + " WHERE " + DOM_BOOK_GENRE + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_LANGUAGE + "=''" + " WHERE " + DOM_BOOK_LANGUAGE + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_LOCATION + "=''" + " WHERE " + DOM_BOOK_LOCATION + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_PUBLISHER + "=''" + " WHERE " + DOM_BOOK_PUBLISHER + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_ISBN + "=''" + " WHERE " + DOM_BOOK_ISBN + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_PRICE_LISTED + "=''" + " WHERE " + DOM_BOOK_PRICE_LISTED + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_DESCRIPTION + "=''" + " WHERE " + DOM_BOOK_DESCRIPTION + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_NOTES + "=''" + " WHERE " + DOM_BOOK_NOTES + " IS NULL");

            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ_START + "=''" + " WHERE " + DOM_BOOK_READ_START + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ_END + "=''" + " WHERE " + DOM_BOOK_READ_END + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_DATE_PUBLISHED + "=''" + " WHERE " + DOM_BOOK_DATE_PUBLISHED + " IS NULL");

            // clean boolean columns where we have seen non-0/1 values. 'true'/'false' were seen in 5.2.2 exports. 't'/'f' just because paranoid.
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ + "=1 WHERE lower(" + DOM_BOOK_READ + ") IN ('true', 't')");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ + "=0 WHERE lower(" + DOM_BOOK_READ + ") IN ('false', 'f')");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SIGNED + "=1 WHERE lower(" + DOM_BOOK_SIGNED + ") IN ('true', 't')");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SIGNED + "=0 WHERE lower(" + DOM_BOOK_SIGNED + ") IN ('false', 'f')");

            // probably not needed, but there were some 'coalesce' usages. Paranoia again...
            db.execSQL("UPDATE " + TBL_BOOKSHELF + " SET " + DOM_BOOKSHELF + "=''" + " WHERE " + DOM_BOOKSHELF + " IS NULL");

            // this better works....
            UpgradeDatabase.recreateAndReloadTable(syncedDb, TBL_BOOKS.getName(), DATABASE_CREATE_BOOKS);

            // anthology-titles are now cross-book; e.g. one 'story' can be present in multiple books
            db.execSQL(DATABASE_CREATE_BOOK_TOC_ENTRIES);
            // move the existing book-anthology links to the new table
            db.execSQL("INSERT INTO " + TBL_BOOK_TOC_ENTRIES +
                    " SELECT " + DOM_FK_BOOK_ID + "," + DOM_PK_ID + "," + DOM_BOOK_TOC_ENTRY_POSITION + " FROM " + TBL_TOC_ENTRIES);

            // reorganise the original table
            UpgradeDatabase.recreateAndReloadTable(syncedDb, TBL_TOC_ENTRIES.getName(), DATABASE_CREATE_TOC_ENTRIES,
                    /* remove fields: */ DOM_FK_BOOK_ID.name, DOM_BOOK_TOC_ENTRY_POSITION.name);


            /* all books with a list price are assumed to be USD based on the only search up to v82
             * being Amazon US (via proxy... so this is my best guess).
             *
             * FIXME if a user has manually edited the list price, can we scan for currencies ?
             *
             * set all rows which have a price, to being in USD. Does not change the price field.
             */
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_PRICE_LISTED_CURRENCY + "='USD' WHERE NOT " + DOM_BOOK_PRICE_LISTED + "=''");
        }

        // Rebuild all indices
        createIndices(db);
        // Rebuild all triggers
        createTriggers(syncedDb);
    }
}
