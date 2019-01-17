package com.eleybourn.bookcatalogue.database;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.dbsync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.dbsync.Synchronizer;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_ANTHOLOGY_BITMASK;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_AUTHOR_POSITION;
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
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOKSHELF_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_BOOK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_TOC_ENTRY_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LAST_UPDATE_DATE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_PK_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_UUID;
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
 * This is a specific version of {@link SQLiteOpenHelper}.
 * It handles {@link #onCreate} and {@link #onUpgrade}
 * <p>
 * This used to be an inner class of {@link DBA}
 * Externalised because of the size of the class. ONLY used by {@link DBA}
 * <p>
 * <p>
 * NOTE: ***BEFORE*** changing any CREATE TABLE statement:
 * <p>
 * Making changes:
 * 1. copy the create statement to a renamed version in {@link UpgradeDatabase#doUpgrade}
 * 2. Find all uses in {@link UpgradeDatabase} and make sure upgrades use the
 * 'old/renamed' version of the statement
 * 3. modify the current statement, and if not already so, make it 'private'.
 * (tables that have never changed since conception will be package-private)
 * 4. modify {@link DomainDefinition} if needed to reflect the new version
 * 5. Check and check again, that *everything* EXCEPT {@link UpgradeDatabase} is using
 * the current version
 *
 * @author evan
 */
public class DBHelper
        extends SQLiteOpenHelper {

    /**
     * RELEASE: Update database version. (last official version was 82).
     */
    public static final int DATABASE_VERSION = 100;

    /**
     * the one and only (aside of the covers one...).
     */
    private static final String DATABASE_NAME = "book_catalogue";

    /**
     * Database creation sql statement.
     */
    private static final String DATABASE_CREATE_BOOK_AUTHOR =
            "CREATE TABLE " + TBL_BOOK_AUTHOR + '('
                    + DOM_FK_BOOK_ID.def()
                    + ',' + DOM_FK_AUTHOR_ID.def()
                    + ',' + DOM_BOOK_AUTHOR_POSITION.def()
                    //TODO: check PRIMARY KEY usage for all 'link' tables.
                    + ',' + "PRIMARY KEY(" + DOM_FK_BOOK_ID + ',' + DOM_BOOK_AUTHOR_POSITION + ')'
                    + ')';

    /**
     * Database creation sql statement.
     */
    private static final String DATABASE_CREATE_BOOK_SERIES =
            "CREATE TABLE " + TBL_BOOK_SERIES + '('
                    + DOM_FK_BOOK_ID.def()
                    + ',' + DOM_FK_SERIES_ID.def()
                    + ',' + DOM_BOOK_SERIES_NUM.def()
                    + ',' + DOM_BOOK_SERIES_POSITION.def()
                    + ',' + "PRIMARY KEY(" + DOM_FK_BOOK_ID + ',' + DOM_BOOK_SERIES_POSITION + ')'
                    + ')';

    /**
     * Database creation sql statement.
     */
    private static final String DATABASE_CREATE_BOOKS =
            "CREATE TABLE " + TBL_BOOKS + " (" + DOM_PK_ID.def()
                    + ',' + DOM_TITLE.def()
                    + ',' + DOM_BOOK_UUID.def()

                    + ',' + DOM_BOOK_ISBN.def()
                    + ',' + DOM_BOOK_PUBLISHER.def()
                    + ',' + DOM_BOOK_DATE_PUBLISHED.def()
                    + ',' + DOM_FIRST_PUBLICATION.def()
                    + ',' + DOM_BOOK_EDITION_BITMASK.def()
                    + ',' + DOM_BOOK_ANTHOLOGY_BITMASK.def()

                    + ',' + DOM_BOOK_PRICE_LISTED.def()
                    + ',' + DOM_BOOK_PRICE_LISTED_CURRENCY.def()
                    + ',' + DOM_BOOK_PRICE_PAID.def()
                    + ',' + DOM_BOOK_PRICE_PAID_CURRENCY.def()
                    + ',' + DOM_BOOK_DATE_ACQUIRED.def()

                    + ',' + DOM_BOOK_FORMAT.def()
                    + ',' + DOM_BOOK_GENRE.def()
                    + ',' + DOM_BOOK_LANGUAGE.def()
                    + ',' + DOM_BOOK_LOCATION.def()
                    + ',' + DOM_BOOK_PAGES.def()

                    + ',' + DOM_BOOK_READ.def()
                    + ',' + DOM_BOOK_READ_START.def()
                    + ',' + DOM_BOOK_READ_END.def()

                    + ',' + DOM_BOOK_SIGNED.def()
                    + ',' + DOM_BOOK_RATING.def()

                    + ',' + DOM_BOOK_DESCRIPTION.def()
                    + ',' + DOM_BOOK_NOTES.def()

                    + ',' + DOM_BOOK_ISFDB_ID.def()
                    + ',' + DOM_BOOK_LIBRARY_THING_ID.def()
                    + ',' + DOM_BOOK_GOODREADS_BOOK_ID.def()
                    + ',' + DOM_BOOK_GOODREADS_LAST_SYNC_DATE.def()

                    + ',' + DOM_BOOK_DATE_ADDED.def()
                    + ',' + DOM_LAST_UPDATE_DATE.def()
                    + ')';

    /**
     * Database creation sql statement.
     */
    private static final String DATABASE_CREATE_TOC_ENTRIES =
            "CREATE TABLE " + TBL_TOC_ENTRIES + " (" + DOM_PK_ID.def()
                    // if an Author is deleted, set the id to null but keep the row.
                    + ',' + DOM_FK_AUTHOR_ID.def() + " REFERENCES " + TBL_AUTHORS
                    + " ON DELETE SET NULL ON UPDATE CASCADE"
                    + ')';

    /**
     * All the indexes.
     */
    private static final String[] DATABASE_CREATE_INDICES = {
            "CREATE INDEX IF NOT EXISTS authors_given_names ON " + TBL_AUTHORS
                    + " (" + DOM_AUTHOR_GIVEN_NAMES + ')',
            "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON " + TBL_AUTHORS
                    + " (" + DOM_AUTHOR_GIVEN_NAMES + DBA.COLLATION + ')',

            "CREATE INDEX IF NOT EXISTS authors_family_name ON " + TBL_AUTHORS
                    + " (" + DOM_AUTHOR_FAMILY_NAME + ')',
            "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON " + TBL_AUTHORS
                    + " (" + DOM_AUTHOR_FAMILY_NAME + DBA.COLLATION + ')',


            "CREATE INDEX IF NOT EXISTS books_title ON " + TBL_BOOKS
                    + " (" + DOM_TITLE + ')',
            "CREATE INDEX IF NOT EXISTS books_title_ci ON " + TBL_BOOKS
                    + " (" + DOM_TITLE + DBA.COLLATION + ')',

            "CREATE INDEX IF NOT EXISTS books_isbn ON " + TBL_BOOKS
                    + " (" + DOM_BOOK_ISBN + ')',
            "CREATE INDEX IF NOT EXISTS books_publisher ON " + TBL_BOOKS
                    + " (" + DOM_BOOK_PUBLISHER + ')',
            "CREATE UNIQUE INDEX IF NOT EXISTS books_uuid ON " + TBL_BOOKS
                    + " (" + DOM_BOOK_UUID + ')',

            "CREATE INDEX IF NOT EXISTS books_gr_book ON " + TBL_BOOKS
                    + " (" + DOM_BOOK_GOODREADS_BOOK_ID + ')',
            "CREATE INDEX IF NOT EXISTS books_isfdb_book ON " + TBL_BOOKS
                    + " (" + DOM_BOOK_ISFDB_ID + ')',


            "CREATE UNIQUE INDEX IF NOT EXISTS loan_book_loaned_to ON " + TBL_LOAN
                    + " (" + DOM_FK_BOOK_ID + ')',


            "CREATE INDEX IF NOT EXISTS anthology_author ON " + TBL_TOC_ENTRIES
                    + " (" + DOM_FK_AUTHOR_ID + ')',
            "CREATE INDEX IF NOT EXISTS anthology_title ON " + TBL_TOC_ENTRIES
                    + " (" + DOM_TITLE + ')',
            "CREATE UNIQUE INDEX IF NOT EXISTS anthology_pk_idx ON " + TBL_TOC_ENTRIES
                    + " (" + DOM_FK_AUTHOR_ID + ',' + DOM_TITLE + ')',


            "CREATE INDEX IF NOT EXISTS book_anthology_anthology ON " + TBL_BOOK_TOC_ENTRIES
                    + " (" + DOM_FK_TOC_ENTRY_ID + ')',
            "CREATE INDEX IF NOT EXISTS book_anthology_book ON " + TBL_BOOK_TOC_ENTRIES
                    + " (" + DOM_FK_BOOK_ID + ')',


            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_book ON " + TBL_BOOK_BOOKSHELF
                    + " (" + DOM_FK_BOOK_ID + ')',
            "CREATE INDEX IF NOT EXISTS book_bookshelf_weak_bookshelf ON " + TBL_BOOK_BOOKSHELF
                    + " (" + DOM_FK_BOOKSHELF_ID + ')',


            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_author ON " + TBL_BOOK_AUTHOR
                    + " (" + DOM_FK_AUTHOR_ID + ',' + DOM_FK_BOOK_ID + ')',
            "CREATE UNIQUE INDEX IF NOT EXISTS book_author_book ON " + TBL_BOOK_AUTHOR
                    + " (" + DOM_FK_BOOK_ID + ',' + DOM_FK_AUTHOR_ID + ')',


            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_series ON " + TBL_BOOK_SERIES
                    + " (" + DOM_FK_SERIES_ID + ','
                    + DOM_FK_BOOK_ID + ','
                    + DOM_BOOK_SERIES_NUM + ')',
            "CREATE UNIQUE INDEX IF NOT EXISTS book_series_book ON " + TBL_BOOK_SERIES
                    + " (" + DOM_FK_BOOK_ID + ','
                    + DOM_FK_SERIES_ID + ','
                    + DOM_BOOK_SERIES_NUM + ')',
            };

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static Synchronizer mSynchronizer;
    private static String mMessage = "";

    /**
     * Constructor.
     *
     * @param context      the context
     * @param factory      the cursor factor
     * @param synchronizer needed in onCreate/onUpgrade
     */
    DBHelper(@NonNull final Context context,
             @SuppressWarnings("SameParameterValue")
             @NonNull final SQLiteDatabase.CursorFactory factory,
             @SuppressWarnings("SameParameterValue")
             @NonNull final Synchronizer synchronizer) {
        super(context, DATABASE_NAME, factory, DATABASE_VERSION);
        mSynchronizer = synchronizer;
    }

    public static String getMessage() {
        return mMessage;
    }

    /**
     * @param context caller context
     *
     * @return the physical path of the database file.
     */
    public static String getDatabasePath(@NonNull final Context context) {
        return context.getDatabasePath(DATABASE_NAME).getAbsolutePath();
    }

    /**
     * This function is called when the database is first created.
     *
     * @param db The database to be created
     */
    @Override
    @CallSuper
    public void onCreate(@NonNull final SQLiteDatabase db) {
        StartupActivity startup = StartupActivity.getActiveActivity();

        Logger.info(this, "Creating database: " + db.getPath());

        SynchronizedDb syncedDb = new SynchronizedDb(db, mSynchronizer);

        // RELEASE: Make sure these are always DATABASE_CREATE_table WITHOUT VERSIONS

        TBL_BOOKSHELF.createAll(syncedDb);
        // inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf}.
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF + " (" + DOM_BOOKSHELF + ") VALUES ('"
                           + BookCatalogueApp.getResString(R.string.initial_bookshelf)
                           + "')");


        // the createAll() calls will also do the indexes, the create() calls not.
        TBL_AUTHORS.create(syncedDb);
        TBL_SERIES.createAll(syncedDb);
        db.execSQL(DATABASE_CREATE_TOC_ENTRIES);
        db.execSQL(DATABASE_CREATE_BOOKS);

        TBL_LOAN.create(syncedDb);
        TBL_BOOK_TOC_ENTRIES.create(syncedDb);
        db.execSQL(DATABASE_CREATE_BOOK_AUTHOR);
        TBL_BOOK_BOOKSHELF.create(syncedDb);
        db.execSQL(DATABASE_CREATE_BOOK_SERIES);
        // create the indexes not covered in the create() calls above.
        createIndices(db);

        TBL_BOOKLIST_STYLES.createAll(syncedDb);
        TBL_BOOK_LIST_NODE_SETTINGS.createAll(syncedDb);

        //reminder: FTS columns don't need a type nor constraints
        TBL_BOOKS_FTS.create(syncedDb, false);

        createTriggers(syncedDb);

        if (startup != null) {
            startup.setNewInstallDone();
        }
    }

    /**
     * Create the database triggers. Currently only one, and the implementation needs refining!
     */
    private void createTriggers(@NonNull final SynchronizedDb db) {
        String name = "books_tg_reset_goodreads";
        String body = " after update of " + DOM_BOOK_ISBN + " ON " + TBL_BOOKS + " for each row\n"
                + " When New." + DOM_BOOK_ISBN + " <> Old." + DOM_BOOK_ISBN + '\n'
                + " Begin \n"
                + "     UPDATE " + TBL_BOOKS + " SET \n"
                + "         " + DOM_BOOK_GOODREADS_BOOK_ID + "=0,\n"
                + "         " + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + "=''\n"
                + "     WHERE\n"
                + "         " + DOM_PK_ID + " = new." + DOM_PK_ID + ";\n"
                + " End";
        db.execSQL("DROP TRIGGER if Exists " + name);
        db.execSQL("CREATE TRIGGER " + name + body);
    }

    private void createIndices(@NonNull final SQLiteDatabase db) {
        //delete all indices first
        String sql = "SELECT name FROM sqlite_master WHERE type = 'index' AND sql is not null;";
        try (Cursor current = db.rawQuery(sql, null)) {
            while (current.moveToNext()) {
                String indexName = current.getString(0);
                String deleteSql = "DROP INDEX " + indexName;
                try {
                    db.execSQL(deleteSql);
                } catch (SQLException e) {
                    // bad sql is a developer issue... die!
                    Logger.error(e);
                    throw new IllegalArgumentException(e);
                } catch (RuntimeException e) {
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
            } catch (SQLException e) {
                // bad sql is a developer issue... die!
                Logger.error(e);
                throw new IllegalArgumentException(e);
            } catch (RuntimeException e) {
                // Expected on multi-version upgrades.
                Logger.error(e, "Index creation failed (probably not a problem),"
                        + " definition was: " + index);
            }
        }
        db.execSQL("analyze");
    }

    /**
     * This function is called each time the database is upgraded. The function will run all
     * upgrade scripts between the oldVersion and the newVersion.
     * <p>
     * REMINDER: do not use [column].ref() here. The 'current' definition might not match the
     * upgraded definition!
     *
     * @param db         The database to be upgraded
     * @param oldVersion The current version number of the database
     * @param newVersion The new version number of the database
     *
     * @see #DATABASE_VERSION
     */
    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase db,
                          final int oldVersion,
                          final int newVersion) {
        Logger.info(this, "Upgrading database: " + db.getPath());

        int curVersion = oldVersion;

        StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.updateProgress(R.string.progress_msg_upgrading);
        }

        if (oldVersion != newVersion) {
            StorageUtils.exportFile(db.getPath(),
                                    "DbUpgrade-" + oldVersion + '-' + newVersion);
        }

        if (curVersion < 11) {
            onCreate(db);
        }

        SynchronizedDb syncedDb = new SynchronizedDb(db, mSynchronizer);

        // older version upgrades archived/execute in UpgradeDatabase
        if (curVersion < 82) {
            curVersion = UpgradeDatabase.doUpgrade(db, syncedDb, curVersion);
            mMessage += UpgradeDatabase.getMessage();
        }

        // db82 == app179 == 5.2.2
        if (curVersion < newVersion && curVersion == 82) {
            // db100 == app200 == 6.0.0; next upgrades do a  curVersion++
            //noinspection UnusedAssignment
            curVersion = 100;
            mMessage += "Major upgrade; restart to see what's new\n\n";

            /* move cover files to a sub-folder.
            Only files with matching rows in 'books' are moved. */
            UpgradeDatabase.v200_moveCoversToDedicatedDirectory(syncedDb);

            /* now using the 'real' cache directory */
            StorageUtils.deleteFile(
                    new File(StorageUtils.getSharedStorage()
                                     + File.separator + "tmp_images"));

            // migrate old properties.
            Prefs.migratePreV200preferences(
                    BookCatalogueApp
                            .getAppContext()
                            .getSharedPreferences("bookCatalogue", Context.MODE_PRIVATE)
                            .getAll());

            // API: 24 -> BookCatalogueApp.getAppContext().deleteSharedPreferences("bookCatalogue");
            BookCatalogueApp
                    .getAppContext()
                    .getSharedPreferences("bookCatalogue", Context.MODE_PRIVATE)
                    .edit().clear().apply();


            //TEST a proper upgrade from 82 to 83 with non-clean data
            // Due to a number of code remarks, and some observation... do a clean of some columns.

            // these two are due to a remark in the CSV exporter that (at one time?)
            // the author name and title could be bad
            final String UNKNOWN = BookCatalogueApp.getResString(R.string.unknown);
            db.execSQL("UPDATE " + TBL_AUTHORS
                               + " SET " + DOM_AUTHOR_FAMILY_NAME + "='" + UNKNOWN + '\''
                               + " WHERE " + DOM_AUTHOR_FAMILY_NAME + "=''"
                               + " OR " + DOM_AUTHOR_FAMILY_NAME + " IS NULL");

            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_TITLE + "='" + UNKNOWN + '\''
                               + " WHERE " + DOM_TITLE + "='' OR " + DOM_TITLE + " IS NULL");

            // clean columns where we adding a "not null default ''" constraint
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_FORMAT + "=''"
                               + " WHERE " + DOM_BOOK_FORMAT + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_GENRE + "=''"
                               + " WHERE " + DOM_BOOK_GENRE + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_LANGUAGE + "=''"
                               + " WHERE " + DOM_BOOK_LANGUAGE + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_LOCATION + "=''"
                               + " WHERE " + DOM_BOOK_LOCATION + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_PUBLISHER + "=''"
                               + " WHERE " + DOM_BOOK_PUBLISHER + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_ISBN + "=''"
                               + " WHERE " + DOM_BOOK_ISBN + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_PRICE_LISTED + "=''"
                               + " WHERE " + DOM_BOOK_PRICE_LISTED + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_DESCRIPTION + "=''"
                               + " WHERE " + DOM_BOOK_DESCRIPTION + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_NOTES + "=''"
                               + " WHERE " + DOM_BOOK_NOTES + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ_START + "=''"
                               + " WHERE " + DOM_BOOK_READ_START + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ_END + "=''"
                               + " WHERE " + DOM_BOOK_READ_END + " IS NULL");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_DATE_PUBLISHED + "=''"
                               + " WHERE " + DOM_BOOK_DATE_PUBLISHED + " IS NULL");

            // clean boolean columns where we have seen non-0/1 values.
            // 'true'/'false' were seen in 5.2.2 exports. 't'/'f' just because paranoid.
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ + "=1"
                               + " WHERE lower(" + DOM_BOOK_READ + ") IN ('true', 't')");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_READ + "=0"
                               + " WHERE lower(" + DOM_BOOK_READ + ") IN ('false', 'f')");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SIGNED + "=1"
                               + " WHERE lower(" + DOM_BOOK_SIGNED + ") IN ('true', 't')");
            db.execSQL("UPDATE " + TBL_BOOKS + " SET " + DOM_BOOK_SIGNED + "=0"
                               + " WHERE lower(" + DOM_BOOK_SIGNED + ") IN ('false', 'f')");

            // probably not needed, but there were some 'coalesce' usages. Paranoia again...
            db.execSQL("UPDATE " + TBL_BOOKSHELF + " SET " + DOM_BOOKSHELF + "=''"
                               + " WHERE " + DOM_BOOKSHELF + " IS NULL");

            // this better work....
            UpgradeDatabase.recreateAndReloadTable(syncedDb, TBL_BOOKS.getName(),
                                                   DATABASE_CREATE_BOOKS);

            // anthology-titles are now cross-book;
            // e.g. one 'story' can be present in multiple books
            TBL_BOOK_TOC_ENTRIES.create(syncedDb);
            // move the existing book-anthology links to the new table
            db.execSQL("INSERT INTO " + TBL_BOOK_TOC_ENTRIES
                               + " SELECT " + DOM_FK_BOOK_ID + ',' + DOM_PK_ID + ','
                               + DOM_BOOK_TOC_ENTRY_POSITION + " FROM " + TBL_TOC_ENTRIES);

            // reorganise the original table
            UpgradeDatabase.recreateAndReloadTable(syncedDb,
                                                   TBL_TOC_ENTRIES.getName(),
                                                   DATABASE_CREATE_TOC_ENTRIES,
                    /* remove fields: */ DOM_FK_BOOK_ID.name, DOM_BOOK_TOC_ENTRY_POSITION.name);


            // add the UUID field for the move of styles to SharedPreferences
            db.execSQL("ALTER TABLE " + TBL_BOOKLIST_STYLES
                               + " ADD " + DOM_UUID + " text not null default ''");

            // convert user styles
            try (Cursor stylesCursor = db.rawQuery("SELECT " + DOM_PK_ID + ",style"
                                                           + " FROM " + TBL_BOOKLIST_STYLES,
                                                   null)) {
                while (stylesCursor.moveToNext()) {
                    long id = stylesCursor.getLong(0);
                    byte[] blob = stylesCursor.getBlob(1);
                    BooklistStyle style;
                    try {
                        // de-serializing will in effect write out the preference file.
                        style = SerializationUtils.deserializeObject(blob);
                        // update db with the newly created prefs file name.
                        db.execSQL("UPDATE " + TBL_BOOKLIST_STYLES
                                           + " SET " + DOM_UUID + "='" + style.getUuid() + '\''
                                           + " WHERE " + DOM_PK_ID + '=' + id);

                    } catch (RTE.DeserializationException e) {
                        Logger.error(e, "BooklistStyle id=" + id);
                    }
                }
            }
            // reorganise the original table
            UpgradeDatabase.recreateAndReloadTable(syncedDb, TBL_BOOKLIST_STYLES,
                    /* remove field */ "style");

            /* all books with a list price are assumed to be USD based on the only search up to v82
             * being Amazon US (via proxy... so this is my best guess).
             *
             * FIXME: if a user has manually edited the list price, can we scan for currencies ?
             *
             * set all rows which have a price, to being in USD. Does not change the price field.
             */
            db.execSQL("UPDATE " + TBL_BOOKS
                               + " SET " + DOM_BOOK_PRICE_LISTED_CURRENCY + "='USD'"
                               + " WHERE NOT " + DOM_BOOK_PRICE_LISTED + "=''");
        }

        // Rebuild all indices
        createIndices(db);
        // Rebuild all triggers
        createTriggers(syncedDb);
    }
}
