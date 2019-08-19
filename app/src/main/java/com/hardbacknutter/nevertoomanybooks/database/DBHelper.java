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

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StartupActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyles;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.IndexDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UpgradeMessageManager;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_AUTHOR_GIVEN_NAMES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GOODREADS_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_ISFDB_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_LIBRARY_THING_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_OPEN_LIBRARY_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_DATE_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_STYLE_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_DOCID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_STYLE_IS_BUILTIN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS_FTS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_SETTINGS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * Our version of {@link SQLiteOpenHelper} handling {@link #onCreate} and {@link #onUpgrade}.
 * Uses the application context.
 * Singleton.
 */
public final class DBHelper
        extends SQLiteOpenHelper {

    /**
     * RELEASE: Update database version.
     * <p>
     * db100 == app200 == 6.0.0 beta01, 2019-08-14 release to github.
     * db82 == app179 == 5.2.2 == "Book Catalogue" version from which we forked.
     */
    public static final int DATABASE_VERSION = 100;

    /** NEVER change this name. */
    private static final String DATABASE_NAME = "nevertoomanybooks.db";

    /**
     * Indexes which have not been added to the TBL definitions yet.
     * For now, there is no API to add an index with a collation suffix.
     * <p>
     * These (should) speed up SQL where we lookup the id by name/title.
     */
    private static final String[] DATABASE_CREATE_INDICES = {
            "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON " + TBL_AUTHORS
            + " (" + DOM_AUTHOR_FAMILY_NAME + DAO.COLLATION + ')',
            "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON " + TBL_AUTHORS
            + " (" + DOM_AUTHOR_GIVEN_NAMES + DAO.COLLATION + ')',

            "CREATE INDEX IF NOT EXISTS books_title_ci ON " + TBL_BOOKS
            + " (" + DOM_TITLE + DAO.COLLATION + ')',
            };

    /** Readers/Writer lock for this database. */
    private static Synchronizer sSynchronizer;

    /** Singleton. */
    private static DBHelper sInstance;

    /**
     * Constructor.
     *
     * @param context      the Application Context (as a param, to allow testing)
     * @param factory      the cursor factor
     * @param synchronizer needed in onCreate/onUpgrade
     */
    private DBHelper(@NonNull final Context context,
                     @SuppressWarnings("SameParameterValue")
                     @NonNull final SQLiteDatabase.CursorFactory factory,
                     @SuppressWarnings("SameParameterValue")
                     @NonNull final Synchronizer synchronizer) {

        super(context, DATABASE_NAME, factory, DATABASE_VERSION);
        sSynchronizer = synchronizer;
    }

    /**
     * Get the singleton instance.
     *
     * @param factory      the cursor factor
     * @param synchronizer needed in onCreate/onUpgrade
     */
    public static DBHelper getInstance(@SuppressWarnings("SameParameterValue")
                                       @NonNull final SQLiteDatabase.CursorFactory factory,
                                       @SuppressWarnings("SameParameterValue")
                                       @NonNull final Synchronizer synchronizer) {
        if (sInstance == null) {
            // ALWAYS use the app context here!
            sInstance = new DBHelper(App.getAppContext(), factory, synchronizer);
        }
        return sInstance;
    }

    /**
     * @return the physical path of the database file.
     */
    public static File getDatabasePath(@NonNull final Context context) {
        return context.getDatabasePath(DATABASE_NAME);
    }

    /**
     * Run at installation (and v200 upgrade) time to add the builtin style ID's to the database.
     * This allows foreign keys to work.
     *
     * @param db Database Access
     */
    static void prepareStylesTable(@NonNull final SQLiteDatabase db) {
        String sqlInsertStyles =
                "INSERT INTO " + TBL_BOOKLIST_STYLES
                + '(' + DOM_PK_ID
                + ',' + DOM_STYLE_IS_BUILTIN
                + ',' + DOM_UUID
                // 1==true
                + ") VALUES(?,1,?)";
        try (SQLiteStatement stmt = db.compileStatement(sqlInsertStyles)) {
            for (int id = BooklistStyles.BUILTIN_MAX_ID; id < 0; id++) {
                stmt.bindLong(1, id);
                stmt.bindString(2, BooklistStyles.ID_UUID[-id]);

                // oops... after inserting '-1' our debug logging will claim that insert failed.
                if (BuildConfig.DEBUG /* always */) {
                    if (id == -1) {
                        Logger.debug(BooklistStyles.class, "prepareStylesTable",
                                     "Ignore the debug message about inserting -1 here...");
                    }
                }
                stmt.executeInsert();
            }
        }
    }

    @Override
    public void onConfigure(@NonNull final SQLiteDatabase db) {
        // Turn ON foreign key support so that CASCADE etc. works.
        //db.execSQL("PRAGMA foreign_keys = ON");
        db.setForeignKeyConstraintsEnabled(true);

        // Turn OFF recursive triggers;
        db.execSQL("PRAGMA recursive_triggers = OFF");

        // for debug
        //sSyncedDb.execSQL("PRAGMA temp_store = FILE");
    }

    @SuppressWarnings("unused")
    @Override
    public void onCreate(@NonNull final SQLiteDatabase db) {
        // 'Upgrade' from not being installed. Run this first to avoid racing issues.
        UpgradeMessageManager.setUpgradeAcknowledged();

        if (BuildConfig.DEBUG /* always */) {
            Logger.debugEnter(this, "onCreate", "database: " + db.getPath());
        }

        SynchronizedDb syncedDb = new SynchronizedDb(db, sSynchronizer);

        TableDefinition.createTables(syncedDb,
                                     // app tables
                                     TBL_BOOKLIST_STYLES,
                                     // basic user data tables
                                     TBL_BOOKSHELF,
                                     TBL_AUTHORS,
                                     TBL_SERIES,
                                     TBL_BOOKS,
                                     TBL_TOC_ENTRIES,
                                     // link tables
                                     TBL_BOOK_TOC_ENTRIES,
                                     TBL_BOOK_AUTHOR,
                                     TBL_BOOK_BOOKSHELF,
                                     TBL_BOOK_SERIES,
                                     TBL_BOOK_LOANEE,
                                     // permanent booklist management tables
                                     TBL_BOOK_LIST_NODE_SETTINGS);

        // create the indexes not covered in the calls above.
        createIndices(syncedDb, false);

        // insert the builtin styles so foreign key rules are possible.
        prepareStylesTable(db);

        // inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf}.
        syncedDb.execSQL("INSERT INTO " + TBL_BOOKSHELF
                         + '(' + DOM_BOOKSHELF
                         + ',' + DOM_FK_STYLE_ID
                         + ") VALUES ("
                         + "'" + App.getFakeUserContext().getString(R.string.bookshelf_my_books)
                         + "'," + BooklistStyles.DEFAULT_STYLE_ID
                         + ')');

        //reminder: FTS columns don't need a type nor constraints
        TBL_BOOKS_FTS.create(syncedDb, false);

        createTriggers(syncedDb);
    }

    /**
     * Since renaming the application, a direct upgrade from the original database is
     * actually no longer needed/done. Migrating from BC to this rewritten version should
     * be a simple 'backup to archive' in the old app, and an 'import from archive' in this app.
     * <p>
     * Leaving the code here below for now, but it's bound to be completely removed soon.
     * <p>
     * This function is called each time the database is upgraded.
     * It will run all upgrade scripts between the oldVersion and the newVersion.
     * <p>
     * Minimal application version 5.2.2 (database version 82). Older versions not supported.
     * <p>
     * REMINDER: do not use [column].ref() or [table].create/createAll.
     * The 'current' definition might not match the upgraded definition!
     *
     * @param db         The SQLiteDatabase to be upgraded
     * @param oldVersion The current version number of the SQLiteDatabase
     * @param newVersion The new version number of the SQLiteDatabase
     *
     * @see #DATABASE_VERSION
     */
    @SuppressWarnings("unused")
    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase db,
                          final int oldVersion,
                          final int newVersion) {

        if (BuildConfig.DEBUG /* always */) {
            Logger.debugEnter(this, "onUpgrade",
                              "Old database version: " + oldVersion,
                              "Upgrading database: " + db.getPath());
        }
        if (oldVersion < 82) {
            throw new UpgradeException(R.string.error_database_upgrade_failed);
        }

        StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.onProgress(R.string.progress_msg_upgrading);
        }

        if (oldVersion != newVersion) {
            StorageUtils.exportFile(new File(db.getPath()),
                                    "DbUpgrade-" + oldVersion + '-' + newVersion);
        }

        int curVersion = oldVersion;
        SynchronizedDb syncedDb = new SynchronizedDb(db, sSynchronizer);

        // db82 == app179 == 5.2.2 == last official version.
        if (curVersion < newVersion && curVersion == 82) {
            // db100 == app200 == 6.0.0;
            curVersion = 100;
            UpgradeDatabase.toDb100(db, syncedDb);
        }

//        if (curVersion < newVersion && curVersion == 100) {
//            //noinspection UnusedAssignment
//            curVersion = 101;
//            UpgradeDatabase.toDb101(db, syncedDb);
//        }

        // Rebuild all indices
        createIndices(syncedDb, true);
        // Rebuild all triggers
        createTriggers(syncedDb);
    }

    /**
     * Create all database triggers.
     *
     * <p>
     * set Book dirty when:
     * - Author: delete, update.
     * - Bookshelf: delete, update.
     * - Series: delete, update.
     * - Loan: delete, update, insert.
     *
     * <p>
     * Update FTS when:
     * - Book: delete (update,insert is to complicated to use a trigger)
     *
     * <p>
     * Others:
     * - When a books ISBN is updated, reset external data.
     *
     * <p>
     * not needed + why now:
     * - insert a new Series,Author,TocEntry is only done when a Book is inserted/updated.
     * - insert a new Bookshelf has no effect until a Book is added to the shelf (update bookshelf)
     * <p>
     * - insert/delete/update TocEntry only done when a book is inserted/updated.
     * ENHANCE: once we allow editing of TocEntry's through the 'author detail' screen
     * this will need to be added.
     *
     * @param syncedDb the database
     */
    void createTriggers(@NonNull final SynchronizedDb syncedDb) {

        String name;
        String body;

        /*
         * Deleting a {@link Bookshelf).
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_delete_on_" + TBL_BOOK_BOOKSHELF;
        body = " AFTER DELETE ON " + TBL_BOOK_BOOKSHELF + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + DOM_PK_ID + "=Old." + DOM_FK_BOOK + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Updating a {@link Bookshelf}.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on" + TBL_BOOKSHELF;
        body = " AFTER UPDATE ON " + TBL_BOOKSHELF + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + DOM_PK_ID + " IN \n"
               + "(SELECT " + DOM_FK_BOOK + " FROM " + TBL_BOOK_BOOKSHELF
               + " WHERE " + DOM_FK_BOOKSHELF + "=Old." + DOM_PK_ID + ");\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

//        /*
//         * Deleting an {@link Author). Currently not possible to delete an Author directly.
//         *
//         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
//         */
//        name = "after_delete_on_" + TBL_BOOK_AUTHOR;
//        body = " AFTER DELETE ON " + TBL_BOOK_AUTHOR + " FOR EACH ROW\n"
//                + " BEGIN\n"
//                + "  UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
//                + " WHERE " + DOM_PK_ID + "=Old." + DOM_FK_BOOK + ";\n"
//                + " END";
//
//        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
//        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Updating an {@link Author).
         *
         * This is for both actual Books, and for any TocEntry's they have done in anthologies.
         * The latter because a Book might not have the full list of Authors set.
         * (i.e. each toc has the right author, but the book says "many authors")
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on" + TBL_AUTHORS;
        body = " AFTER UPDATE ON " + TBL_AUTHORS + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"

               + " WHERE " + DOM_PK_ID + " IN \n"
               // actual books by this Author
               + "(SELECT " + DOM_FK_BOOK + " FROM " + TBL_BOOK_AUTHOR
               + " WHERE " + DOM_FK_AUTHOR + "=Old." + DOM_PK_ID + ")\n"

               + " OR " + DOM_PK_ID + " IN \n"
               // books with entries in anthologies by this Author
               + "(SELECT " + DOM_FK_BOOK + " FROM " + TBL_BOOK_TOC_ENTRIES.ref()
               + TBL_BOOK_TOC_ENTRIES.join(TBL_TOC_ENTRIES)
               + " WHERE " + DOM_FK_AUTHOR + "=Old." + DOM_PK_ID + ");\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Deleting a {@link Series).
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_delete_on_" + TBL_BOOK_SERIES;
        body = " AFTER DELETE ON " + TBL_BOOK_SERIES + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + DOM_PK_ID + "=Old." + DOM_FK_BOOK + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Update a {@link Series}
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on" + TBL_SERIES;
        body = " AFTER UPDATE ON " + TBL_SERIES + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + DOM_PK_ID + " IN \n"
               + "(SELECT " + DOM_FK_BOOK + " FROM " + TBL_BOOK_SERIES
               + " WHERE " + DOM_FK_SERIES + "=Old." + DOM_PK_ID + ");\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Deleting a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_delete_on_" + TBL_BOOK_LOANEE;
        body = " AFTER DELETE ON " + TBL_BOOK_LOANEE + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + DOM_PK_ID + "=Old." + DOM_FK_BOOK + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Updating a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on_" + TBL_BOOK_LOANEE;
        body = " AFTER UPDATE ON " + TBL_BOOK_LOANEE + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + DOM_PK_ID + "=New." + DOM_FK_BOOK + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Inserting a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_insert_on_" + TBL_BOOK_LOANEE;
        body = " AFTER INSERT ON " + TBL_BOOK_LOANEE + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS + " SET " + DOM_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + DOM_PK_ID + "=New." + DOM_FK_BOOK + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);


        /*
         * Deleting a {@link Book).
         *
         * Delete the book from FTS.
         */
        name = "after_delete_on_" + TBL_BOOKS;
        body = " AFTER DELETE ON " + TBL_BOOKS + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  DELETE FROM " + TBL_BOOKS_FTS
               + " WHERE " + DOM_PK_DOCID + '=' + "Old." + DOM_PK_ID + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);


        /*
         * If the ISBN of a {@link Book) is changed, reset external ID's and sync dates.
         */
        name = "after_update_of_" + DOM_BOOK_ISBN + "_on_" + TBL_BOOKS;
        body = " AFTER UPDATE OF " + DOM_BOOK_ISBN + " ON " + TBL_BOOKS + " FOR EACH ROW\n"
               + " WHEN New." + DOM_BOOK_ISBN + " <> Old." + DOM_BOOK_ISBN + '\n'
               + " BEGIN\n"
               + "    UPDATE " + TBL_BOOKS + " SET "
               + /* */ DOM_BOOK_GOODREADS_ID + "=0"
               + ',' + DOM_BOOK_ISFDB_ID + "=0"
               + ',' + DOM_BOOK_LIBRARY_THING_ID + "=0"
               + ',' + DOM_BOOK_OPEN_LIBRARY_ID + "=0"

               + ',' + DOM_BOOK_GOODREADS_LAST_SYNC_DATE + "=''"
               + /* */ " WHERE " + DOM_PK_ID + "=New." + DOM_PK_ID + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);
    }

    /**
     * This method should only be called at the *END* of onCreate/onUpdate.
     * <p>
     * (re)Creates the indexes as defined on the tables,
     * followed by the {@link #DATABASE_CREATE_INDICES} set of indexes.
     *
     * @param syncedDb the database
     * @param recreate if {@code true} will delete all indexes first, and recreate them.
     */
    private void createIndices(@NonNull final SynchronizedDb syncedDb,
                               final boolean recreate) {

        if (recreate) {
            // clean slate
            IndexDefinition.dropAllIndexes(syncedDb);
            // recreate the ones that are defined with the TableDefinition's
            for (TableDefinition table : DBDefinitions.ALL_TABLES.values()) {
                table.createIndices(syncedDb);
            }
        }

        // create/recreate the indexes with collation / which we have not added to
        // the TableDefinition's yet
        for (String create_index : DATABASE_CREATE_INDICES) {
            try {
                syncedDb.execSQL(create_index);
            } catch (@NonNull final SQLException e) {
                // bad sql is a developer issue... die!
                Logger.error(this, e);
                throw e;
            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e, "Index creation failed: " + create_index);
            }
        }
        syncedDb.analyze();
    }

    public static class UpgradeException
            extends RuntimeException {

        private static final long serialVersionUID = -6910121313418068318L;

        @StringRes
        final int messageId;

        @SuppressWarnings("SameParameterValue")
        UpgradeException(final int messageId) {
            this.messageId = messageId;
        }
    }
}
