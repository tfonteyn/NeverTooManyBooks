/*
 * @Copyright 2020 HardBackNutter
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StartupActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UpgradeMessageManager;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_FAMILY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_AUTHOR_GIVEN_NAMES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_GOODREADS_LAST_SYNC_DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_DATE_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_GOODREADS_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_ISFDB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_LIBRARY_THING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_OPEN_LIBRARY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_STRIP_INFO_BE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FTS_BOOKS_PK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_IS_BUILTIN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_TITLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
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
     * db1 == app1 == 1.0.0
     * db2 == app1 == 1.0.0
     */
    public static final int DATABASE_VERSION = 2;
    /** Log tag. */
    private static final String TAG = "DBHelper";
    /** NEVER change this name. */
    private static final String DATABASE_NAME = "nevertoomanybooks.db";

    /**
     * Indexes which have not been added to the TBL definitions yet.
     * For now, there is no API to add an index with a collation suffix.
     * <p>
     * These (should) speed up SQL where we lookup the id by name/title.
     */
    private static final String[] DATABASE_CREATE_INDICES = {
            "CREATE INDEX IF NOT EXISTS authors_family_name_ci ON " + TBL_AUTHORS.getName()
            + " (" + KEY_AUTHOR_FAMILY_NAME + DAO.COLLATION + ')',
            "CREATE INDEX IF NOT EXISTS authors_given_names_ci ON " + TBL_AUTHORS.getName()
            + " (" + KEY_AUTHOR_GIVEN_NAMES + DAO.COLLATION + ')',

            "CREATE INDEX IF NOT EXISTS books_title_ci ON " + TBL_BOOKS.getName()
            + " (" + KEY_TITLE + DAO.COLLATION + ')',
            };

    /** SQL to get the names of all indexes. */
    private static final String SQL_GET_INDEX_NAMES =
            "SELECT name FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL";

    /** Readers/Writer lock for this database. */
    private static Synchronizer sSynchronizer;
    /** Singleton. */
    private static DBHelper sInstance;

    /**
     * Singleton Constructor.
     *
     * @param context      Current context
     * @param factory      the cursor factor
     * @param synchronizer needed in onCreate/onUpgrade
     */
    private DBHelper(@NonNull final Context context,
                     @SuppressWarnings("SameParameterValue")
                     @NonNull final SQLiteDatabase.CursorFactory factory,
                     @SuppressWarnings("SameParameterValue")
                     @NonNull final Synchronizer synchronizer) {

        super(context.getApplicationContext(), DATABASE_NAME, factory, DATABASE_VERSION);
        sSynchronizer = synchronizer;
    }

    /**
     * Get the singleton instance.
     *
     * @param context      Current context
     * @param factory      the cursor factor
     * @param synchronizer needed in onCreate/onUpgrade
     *
     * @return the instance
     */
    public static DBHelper getInstance(@NonNull final Context context,
                                       @SuppressWarnings("SameParameterValue")
                                       @NonNull final SQLiteDatabase.CursorFactory factory,
                                       @SuppressWarnings("SameParameterValue")
                                       @NonNull final Synchronizer synchronizer) {
        if (sInstance == null) {
            sInstance = new DBHelper(context, factory, synchronizer);
        }
        return sInstance;
    }

    /**
     * Get the physical path of the database file.
     *
     * @param context Current context
     *
     * @return path
     */
    public static File getDatabasePath(@NonNull final Context context) {
        return context.getDatabasePath(DATABASE_NAME);
    }

    /**
     * DEBUG only.
     */
    @SuppressLint("LogConditional")
    public static void dumpTempTableNames(@NonNull final SynchronizedDb syncedDb) {
        String sql = "SELECT name FROM sqlite_temp_master WHERE type='table'";
        Collection<String> names = new ArrayList<>();
        try (Cursor cursor = syncedDb.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                if (!name.startsWith("sqlite")) {
                    names.add(name);
                }
            }
        }
        Log.d(TAG, TextUtils.join(", ", names));
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
        Context localContext = App.getLocalizedAppContext();

        // 'Upgrade' from not being installed. Run this first to avoid racing issues.
        UpgradeMessageManager.setUpgradeAcknowledged(localContext);

        SynchronizedDb syncedDb = new SynchronizedDb(db, sSynchronizer);

        TableDefinition.createTables(db,
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
                                     TBL_BOOK_LIST_NODE_STATE);

        // create the indexes not covered in the calls above.
        createIndices(syncedDb, false);

        // insert the builtin styles so foreign key rules are possible.
        prepareStylesTable(db);

        // inserts a 'All Books' bookshelf with _id==-1, see {@link Bookshelf}.
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                   + '(' + KEY_PK_ID
                   + ',' + KEY_BOOKSHELF
                   + ',' + KEY_FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.ALL_BOOKS
                   + ",'" + localContext.getString(R.string.bookshelf_all_books)
                   + "'," + BooklistStyle.DEFAULT_STYLE_ID
                   + ')');

        // inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf}.
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                   + '(' + KEY_PK_ID
                   + ',' + KEY_BOOKSHELF
                   + ',' + KEY_FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.DEFAULT_ID
                   + ",'" + localContext.getString(R.string.bookshelf_my_books)
                   + "'," + BooklistStyle.DEFAULT_STYLE_ID
                   + ')');

        //IMPORTANT: withConstraints MUST BE false (FTS columns don't use a type/constraints)
        TBL_FTS_BOOKS.create(syncedDb, false);

        createTriggers(syncedDb);
    }

    /**
     * Run at installation time to add the builtin style ID's to the database.
     * This allows foreign keys to work.
     *
     * @param db Database Access
     */
    private void prepareStylesTable(@NonNull final SQLiteDatabase db) {
        String sqlInsertStyles =
                "INSERT INTO " + TBL_BOOKLIST_STYLES
                + '(' + KEY_PK_ID
                + ',' + KEY_STYLE_IS_BUILTIN
                + ',' + KEY_UUID
                // 1==true
                + ") VALUES(?,1,?)";
        try (SQLiteStatement stmt = db.compileStatement(sqlInsertStyles)) {
            for (int id = BooklistStyle.Builtin.MAX_ID; id < 0; id++) {
                stmt.bindLong(1, id);
                stmt.bindString(2, BooklistStyle.Builtin.ID_UUID[-id]);

                // oops... after inserting '-1' our debug logging will claim that insert failed.
                if (BuildConfig.DEBUG /* always */) {
                    if (id == -1) {
                        Log.d(TAG, "prepareStylesTable|Ignore debug message inserting -1 ^^^");
                    }
                }
                stmt.executeInsert();
            }
        }
    }

    /**
     * This function is called each time the database is upgraded.
     * It will run all upgrade scripts between the oldVersion and the newVersion.
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
            Log.d(TAG, "ENTER|onUpgrade"
                       + "|Old database version: " + oldVersion
                       + "|Upgrading database: " + db.getPath());
        }

        StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.onProgress(R.string.progress_msg_upgrading);
        }

        if (oldVersion != newVersion) {
            StorageUtils.copyFileWithBackup(new File(db.getPath()),
                                            new File(StorageUtils.getRootDir(App.getAppContext()),
                                                     "DbUpgrade-" + oldVersion + '-' + newVersion));
        }

        SynchronizedDb syncedDb = new SynchronizedDb(db, sSynchronizer);

        int curVersion = oldVersion;

        // db1 == app1 == 1.0.0;
        if (curVersion < newVersion && curVersion == 1) {
            //noinspection UnusedAssignment
            curVersion = 2;
            UpgradeDatabase.toDb2(syncedDb);
        }

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
        name = "after_delete_on_" + TBL_BOOK_BOOKSHELF.getName();
        body = " AFTER DELETE ON " + TBL_BOOK_BOOKSHELF.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + "=Old." + KEY_FK_BOOK + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Updating a {@link Bookshelf}.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on" + TBL_BOOKSHELF.getName();
        body = " AFTER UPDATE ON " + TBL_BOOKSHELF.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + " IN \n"
               + "(SELECT " + KEY_FK_BOOK + " FROM " + TBL_BOOK_BOOKSHELF.getName()
               + " WHERE " + KEY_FK_BOOKSHELF + "=Old." + KEY_PK_ID + ");\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

//        /*
//         * Deleting an {@link Author). Currently not possible to delete an Author directly.
//         *
//         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
//         */
//        name = "after_delete_on_" + TBL_BOOK_AUTHOR.getName();
//        body = " AFTER DELETE ON " + TBL_BOOK_AUTHOR.getName() + " FOR EACH ROW\n"
//                + " BEGIN\n"
//                + "  UPDATE " + TBL_BOOKS.getName()
//                + "  SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
//                + " WHERE " + KEY_PK_ID + "=Old." + KEY_FK_BOOK + ";\n"
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
        name = "after_update_on" + TBL_AUTHORS.getName();
        body = " AFTER UPDATE ON " + TBL_AUTHORS.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"

               + " WHERE " + KEY_PK_ID + " IN \n"
               // actual books by this Author
               + "(SELECT " + KEY_FK_BOOK + " FROM " + TBL_BOOK_AUTHOR.getName()
               + " WHERE " + KEY_FK_AUTHOR + "=Old." + KEY_PK_ID + ")\n"

               + " OR " + KEY_PK_ID + " IN \n"
               // books with entries in anthologies by this Author
               + "(SELECT " + KEY_FK_BOOK + " FROM " + TBL_BOOK_TOC_ENTRIES.ref()
               + TBL_BOOK_TOC_ENTRIES.join(TBL_TOC_ENTRIES)
               + " WHERE " + KEY_FK_AUTHOR + "=Old." + KEY_PK_ID + ");\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Deleting a {@link Series).
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_delete_on_" + TBL_BOOK_SERIES.getName();
        body = " AFTER DELETE ON " + TBL_BOOK_SERIES.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + "=Old." + KEY_FK_BOOK + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Update a {@link Series}
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on" + TBL_SERIES.getName();
        body = " AFTER UPDATE ON " + TBL_SERIES.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + " IN \n"
               + "(SELECT " + KEY_FK_BOOK + " FROM " + TBL_BOOK_SERIES.getName()
               + " WHERE " + KEY_FK_SERIES + "=Old." + KEY_PK_ID + ");\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Deleting a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_delete_on_" + TBL_BOOK_LOANEE.getName();
        body = " AFTER DELETE ON " + TBL_BOOK_LOANEE.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + "=Old." + KEY_FK_BOOK + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Updating a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on_" + TBL_BOOK_LOANEE.getName();
        body = " AFTER UPDATE ON " + TBL_BOOK_LOANEE.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + "=New." + KEY_FK_BOOK + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Inserting a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_insert_on_" + TBL_BOOK_LOANEE.getName();
        body = " AFTER INSERT ON " + TBL_BOOK_LOANEE.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + "=New." + KEY_FK_BOOK + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);


        /*
         * Deleting a {@link Book).
         *
         * Delete the book from FTS.
         */
        name = "after_delete_on_" + TBL_BOOKS.getName();
        body = " AFTER DELETE ON " + TBL_BOOKS.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  DELETE FROM " + TBL_FTS_BOOKS.getName()
               + " WHERE " + KEY_FTS_BOOKS_PK + '=' + "Old." + KEY_PK_ID + ";\n"
               + " END";

        syncedDb.execSQL("DROP TRIGGER IF EXISTS " + name);
        syncedDb.execSQL("\nCREATE TRIGGER " + name + body);


        /*
         * If the ISBN of a {@link Book) is changed, reset external ID's and sync dates.
         */
        name = "after_update_of_" + KEY_ISBN + "_on_" + TBL_BOOKS.getName();
        body = " AFTER UPDATE OF " + KEY_ISBN + " ON " + TBL_BOOKS.getName() + " FOR EACH ROW\n"
               + " WHEN New." + KEY_ISBN + " <> Old." + KEY_ISBN + '\n'
               + " BEGIN\n"
               + "    UPDATE " + TBL_BOOKS.getName() + " SET "
               //NEWTHINGS: add new site specific ID: add a reset value
               + /* */ KEY_EID_GOODREADS_BOOK + "=0"
               + ',' + KEY_EID_ISFDB + "=0"
               + ',' + KEY_EID_LIBRARY_THING + "=0"
               + ',' + KEY_EID_OPEN_LIBRARY + "=0"
               + ',' + KEY_EID_STRIP_INFO_BE + "=0"

               + ',' + KEY_BOOK_GOODREADS_LAST_SYNC_DATE + "=''"
               + /* */ " WHERE " + KEY_PK_ID + "=New." + KEY_PK_ID + ";\n"
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
            dropAllIndexes(syncedDb);
            // recreate the ones that are defined with the TableDefinition's
            for (TableDefinition table : DBDefinitions.ALL_TABLES.values()) {
                table.createIndices(syncedDb);
            }
        }

        // create/recreate the indexes with collation / which we have not added to
        // the TableDefinition's yet
        for (String createIndex : DATABASE_CREATE_INDICES) {
            try {
                syncedDb.execSQL(createIndex);
            } catch (@NonNull final SQLException e) {
                // bad sql is a developer issue... die!
                Logger.error(TAG, e);
                throw e;
            } catch (@NonNull final RuntimeException e) {
                Logger.error(TAG, e, "Index creation failed: " + createIndex);
            }
        }
        syncedDb.analyze();
    }

    /**
     * Find and delete all indexes on all tables.
     *
     * @param db Database Access.
     */
    private void dropAllIndexes(@NonNull final SynchronizedDb db) {
        try (Cursor current = db.rawQuery(SQL_GET_INDEX_NAMES, null)) {
            while (current.moveToNext()) {
                String indexName = current.getString(0);
                try {
                    db.execSQL("DROP INDEX " + indexName);
                } catch (@NonNull final SQLException e) {
                    // bad sql is a developer issue... die!
                    Logger.error(TAG, e);
                    throw e;
                } catch (@NonNull final RuntimeException e) {
                    Logger.error(TAG, e, "Index deletion failed: " + indexName);
                }
            }
        }
    }
}
