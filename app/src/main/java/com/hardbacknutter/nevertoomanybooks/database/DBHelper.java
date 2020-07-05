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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StartupActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.tasks.Scheduler;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UpgradeMessageManager;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_UTC_LAST_SYNC_DATE_GOODREADS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_PUBLISHER_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_GOODREADS_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_ISFDB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_LIBRARY_THING;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_OPEN_LIBRARY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_EID_STRIP_INFO_BE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FTS_BOOK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PUBLISHER_NAME_OB;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_IS_BUILTIN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_SYNC_DATE_GOODREADS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_UPDATED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * Our version of {@link SQLiteOpenHelper} handling {@link #onCreate} and {@link #onUpgrade}.
 * Uses the application context.
 * Singleton.
 */
public final class DBHelper
        extends SQLiteOpenHelper {

    /** Current version. */
    public static final int DATABASE_VERSION = 7;

    /**
     * Prefix for the filename of a database backup before doing an upgrade.
     * Stored in {@link AppDir#Upgrades}.
     */
    public static final String DB_UPGRADE_FILE_PREFIX = "DbUpgrade";

    /** Log tag. */
    private static final String TAG = "DBHelper";
    /** NEVER change this name. */
    private static final String DATABASE_NAME = "nevertoomanybooks.db";
    /** SQL to get the names of all indexes. */
    private static final String SQL_GET_INDEX_NAMES =
            "SELECT name FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL";
    /** Readers/Writer lock for this database. */
    private static Synchronizer sSynchronizer;
    /** Singleton. */
    private static DBHelper sInstance;

    @Nullable
    private static Boolean sIsCollationCaseSensitive;

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
        synchronized (DBHelper.class) {
            if (sInstance == null) {
                sInstance = new DBHelper(context, factory, synchronizer);
            }
            return sInstance;
        }
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
    public static void dumpTempTableNames(@NonNull final SynchronizedDb db) {
        final String sql = "SELECT name FROM sqlite_temp_master WHERE type='table'";
        final Collection<String> names = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                final String name = cursor.getString(0);
                if (!name.startsWith("sqlite")) {
                    names.add(name);
                }
            }
        }
        Log.d(TAG, TextUtils.join(", ", names));
    }

    /**
     * Check if the collation we use is case sensitive.
     *
     * @return {@code true} if case-sensitive (i.e. up to "you" to add lower/upper calls)
     */
    public static boolean isCollationCaseSensitive() {
        //noinspection ConstantConditions
        return sIsCollationCaseSensitive;
    }

    /**
     * Method to detect if collation implementations are case sensitive.
     * This was built because ICS broke the UNICODE collation (making it case sensitive (CS))
     * and we needed to check for collation case-sensitivity.
     * <p>
     * This bug was introduced in ICS and present in 4.0-4.0.3, at least.
     *
     * @return This method is supposed to return {@code false} in normal circumstances.
     */
    private boolean collationIsCaseSensitive(@NonNull final SQLiteDatabase db) {
        final String dropTable = "DROP TABLE IF EXISTS collation_cs_check";
        // Drop and create table
        db.execSQL(dropTable);
        db.execSQL("CREATE TEMPORARY TABLE collation_cs_check (t text, i integer)");
        try {
            // Row that *should* be returned first assuming 'a' <=> 'A'
            db.execSQL("INSERT INTO collation_cs_check VALUES('a', 1)");
            // Row that *should* be returned second assuming 'a' <=> 'A';
            // will be returned first if 'A' < 'a'.
            db.execSQL("INSERT INTO collation_cs_check VALUES('A', 2)");

            String s;
            try (Cursor c = db.rawQuery("SELECT t,i FROM collation_cs_check"
                                        + " ORDER BY t " + DAO._COLLATION + ",i",
                                        null)) {
                c.moveToFirst();
                s = c.getString(0);
            }

            final boolean cs = !"a".equals(s);
            if (cs) {
                Log.e(TAG, "\n=============================================="
                           + "\n========== CASE SENSITIVE COLLATION =========="
                           + "\n==============================================");
            }
            return cs;

        } catch (@NonNull final SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(App.getAppContext(), TAG, e);
            throw e;
        } finally {
            try {
                db.execSQL(dropTable);
            } catch (@NonNull final SQLException e) {
                Logger.error(App.getAppContext(), TAG, e);
            }
        }
    }

    /**
     * This method should only be called at the *END* of onCreate/onUpdate.
     * <p>
     * (re)Creates the indexes as defined on the tables.
     *
     * @param db the database
     */
    void recreateIndices(@NonNull final SynchronizedDb db) {
        // Delete all indices.
        // We read the index names from the database, so we can delete
        // indexes which were removed from the TableDefinition objects.
        try (Cursor current = db.rawQuery(SQL_GET_INDEX_NAMES, null)) {
            while (current.moveToNext()) {
                final String indexName = current.getString(0);
                try {
                    db.execSQL("DROP INDEX " + indexName);
                } catch (@NonNull final SQLException e) {
                    // bad sql is a developer issue... die!
                    Logger.error(App.getAppContext(), TAG, e);
                    throw e;
                } catch (@NonNull final RuntimeException e) {
                    Logger.error(App.getAppContext(), TAG, e, "DROP INDEX failed: " + indexName);
                }
            }
        }

        // now recreate
        for (TableDefinition table : DBDefinitions.ALL_TABLES.values()) {
            table.createIndices(db);
        }

        db.analyze();
    }

    /**
     * Run at installation time to add the builtin style ID's to the database.
     * This allows foreign keys to work.
     *
     * @param db Database Access
     */
    private void prepareStylesTable(@NonNull final SQLiteDatabase db) {
        final String sqlInsertStyles =
                "INSERT INTO " + TBL_BOOKLIST_STYLES
                + '(' + KEY_PK_ID
                + ',' + KEY_STYLE_IS_BUILTIN
                + ',' + KEY_UUID
                // 1==true
                + ") VALUES(?,1,?)";
        try (SQLiteStatement stmt = db.compileStatement(sqlInsertStyles)) {
            for (int id = BooklistStyle.Builtin.MAX_ID; id < 0; id++) {
                stmt.bindLong(1, id);
                stmt.bindString(2, BooklistStyle.Builtin.getUuidById(-id));

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
     * Create all database triggers.
     *
     * <p>
     * set Book dirty when:
     * - Author: delete, update.
     * - Series: delete, update.
     * - Bookshelf: delete.
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
     * @param db the database
     */
    void createTriggers(@NonNull final SynchronizedDb db) {

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
               + "  SET " + KEY_UTC_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + "=OLD." + KEY_FK_BOOK + ";\n"
               + " END";

        db.execSQL("DROP TRIGGER IF EXISTS " + name);
        db.execSQL("\nCREATE TRIGGER " + name + body);

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
               + "  SET " + KEY_UTC_LAST_UPDATED + "=current_timestamp"

               + " WHERE " + KEY_PK_ID + " IN \n"
               // actual books by this Author
               + "(SELECT " + KEY_FK_BOOK + " FROM " + TBL_BOOK_AUTHOR.getName()
               + " WHERE " + KEY_FK_AUTHOR + "=OLD." + KEY_PK_ID + ")\n"

               + " OR " + KEY_PK_ID + " IN \n"
               // books with entries in anthologies by this Author
               + "(SELECT " + KEY_FK_BOOK + " FROM " + TBL_BOOK_TOC_ENTRIES.ref()
               + TBL_BOOK_TOC_ENTRIES.join(TBL_TOC_ENTRIES)
               + " WHERE " + KEY_FK_AUTHOR + "=OLD." + KEY_PK_ID + ");\n"
               + " END";

        db.execSQL("DROP TRIGGER IF EXISTS " + name);
        db.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Deleting a {@link Series).
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_delete_on_" + TBL_BOOK_SERIES.getName();
        body = " AFTER DELETE ON " + TBL_BOOK_SERIES.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_UTC_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + "=OLD." + KEY_FK_BOOK + ";\n"
               + " END";

        db.execSQL("DROP TRIGGER IF EXISTS " + name);
        db.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Update a {@link Series}
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on" + TBL_SERIES.getName();
        body = " AFTER UPDATE ON " + TBL_SERIES.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_UTC_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + " IN \n"
               + "(SELECT " + KEY_FK_BOOK + " FROM " + TBL_BOOK_SERIES.getName()
               + " WHERE " + KEY_FK_SERIES + "=OLD." + KEY_PK_ID + ");\n"
               + " END";

        db.execSQL("DROP TRIGGER IF EXISTS " + name);
        db.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Deleting a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_delete_on_" + TBL_BOOK_LOANEE.getName();
        body = " AFTER DELETE ON " + TBL_BOOK_LOANEE.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_UTC_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + "=OLD." + KEY_FK_BOOK + ";\n"
               + " END";

        db.execSQL("DROP TRIGGER IF EXISTS " + name);
        db.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Updating a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_update_on_" + TBL_BOOK_LOANEE.getName();
        body = " AFTER UPDATE ON " + TBL_BOOK_LOANEE.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_UTC_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + "=NEW." + KEY_FK_BOOK + ";\n"
               + " END";

        db.execSQL("DROP TRIGGER IF EXISTS " + name);
        db.execSQL("\nCREATE TRIGGER " + name + body);

        /*
         * Inserting a Loan.
         *
         * Update the books last-update-date (aka 'set dirty', aka 'flag for backup').
         */
        name = "after_insert_on_" + TBL_BOOK_LOANEE.getName();
        body = " AFTER INSERT ON " + TBL_BOOK_LOANEE.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName()
               + "  SET " + KEY_UTC_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + KEY_PK_ID + "=NEW." + KEY_FK_BOOK + ";\n"
               + " END";

        db.execSQL("DROP TRIGGER IF EXISTS " + name);
        db.execSQL("\nCREATE TRIGGER " + name + body);


        /*
         * Deleting a {@link Book).
         *
         * Delete the book from FTS.
         */
        name = "after_delete_on_" + TBL_BOOKS.getName();
        body = " AFTER DELETE ON " + TBL_BOOKS.getName() + " FOR EACH ROW\n"
               + " BEGIN\n"
               + "  DELETE FROM " + TBL_FTS_BOOKS.getName()
               + " WHERE " + KEY_FTS_BOOK_ID + "=OLD." + KEY_PK_ID + ";\n"
               + " END";

        db.execSQL("DROP TRIGGER IF EXISTS " + name);
        db.execSQL("\nCREATE TRIGGER " + name + body);


        /*
         * If the ISBN of a {@link Book) is changed, reset external ID's and sync dates.
         */
        name = "after_update_of_" + KEY_ISBN + "_on_" + TBL_BOOKS.getName();
        body = " AFTER UPDATE OF " + KEY_ISBN + " ON " + TBL_BOOKS.getName() + " FOR EACH ROW\n"
               + " WHEN NEW." + KEY_ISBN + " <> OLD." + KEY_ISBN + '\n'
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName() + " SET "
               //NEWTHINGS: add new site specific ID: add a reset value (0 or '')
               + /* */ KEY_EID_GOODREADS_BOOK + "=0"
               + ',' + KEY_EID_ISFDB + "=0"
               + ',' + KEY_EID_LIBRARY_THING + "=0"
               + ',' + KEY_EID_STRIP_INFO_BE + "=0"

               + ',' + KEY_EID_OPEN_LIBRARY + "=''"

               + ',' + KEY_UTC_LAST_SYNC_DATE_GOODREADS + "="
               + DOM_UTC_LAST_SYNC_DATE_GOODREADS.getDefault()

               + /* */ " WHERE " + KEY_PK_ID + "=NEW." + KEY_PK_ID + ";\n"
               + " END";

        db.execSQL("DROP TRIGGER IF EXISTS " + name);
        db.execSQL("\nCREATE TRIGGER " + name + body);
    }

    @Override
    public void onConfigure(@NonNull final SQLiteDatabase db) {
        // Turn ON foreign key support so that CASCADE etc. works.
        // This is the same as db.execSQL("PRAGMA foreign_keys = ON");
        db.setForeignKeyConstraintsEnabled(true);

        // Turn OFF recursive triggers;
        db.execSQL("PRAGMA recursive_triggers = OFF");

        // for debug
        //sSyncedDb.execSQL("PRAGMA temp_store = FILE");
    }

    @Override
    public void onCreate(@NonNull final SQLiteDatabase db) {
        final Context context = LocaleUtils.applyLocale(App.getAppContext());

        // 'Upgrade' from not being installed. Run this first to avoid racing issues.
        UpgradeMessageManager.setUpgradeAcknowledged(context);

        final SynchronizedDb syncedDb = new SynchronizedDb(sSynchronizer, db);

        TableDefinition.createTables(db,
                                     // app tables
                                     TBL_BOOKLIST_STYLES,
                                     // basic user data tables
                                     TBL_BOOKSHELF,
                                     TBL_AUTHORS,
                                     TBL_SERIES,
                                     TBL_PUBLISHERS,
                                     TBL_BOOKS,
                                     TBL_TOC_ENTRIES,
                                     // link tables
                                     TBL_BOOK_TOC_ENTRIES,
                                     TBL_BOOK_AUTHOR,
                                     TBL_BOOK_BOOKSHELF,
                                     TBL_BOOK_SERIES,
                                     TBL_BOOK_PUBLISHER,
                                     TBL_BOOK_LOANEE,
                                     // permanent booklist management tables
                                     TBL_BOOK_LIST_NODE_STATE);

        // insert the builtin styles so foreign key rules are possible.
        prepareStylesTable(db);

        // inserts a 'All Books' bookshelf with _id==-1, see {@link Bookshelf}.
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                   + '(' + KEY_PK_ID
                   + ',' + KEY_BOOKSHELF_NAME
                   + ',' + KEY_FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.ALL_BOOKS
                   + ",'" + context.getString(R.string.bookshelf_all_books)
                   + "'," + BooklistStyle.DEFAULT_STYLE_ID
                   + ')');

        // inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf}.
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                   + '(' + KEY_PK_ID
                   + ',' + KEY_BOOKSHELF_NAME
                   + ',' + KEY_FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.DEFAULT
                   + ",'" + context.getString(R.string.bookshelf_my_books)
                   + "'," + BooklistStyle.DEFAULT_STYLE_ID
                   + ')');

        //IMPORTANT: withConstraints MUST BE false (FTS columns don't use a type/constraints)
        TBL_FTS_BOOKS.create(syncedDb, false);

        createTriggers(syncedDb);
    }

    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase db,
                          final int oldVersion,
                          final int newVersion) {

        final Context context = App.getAppContext();

        final StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.onProgress(R.string.progress_msg_upgrading);
        }

        if (oldVersion != newVersion) {

            final String backup = DB_UPGRADE_FILE_PREFIX + "-" + oldVersion + '-' + newVersion;
            try {
                final File destFile = AppDir.Upgrades.getFile(context, backup);
                // rename the existing file
                FileUtils.rename(destFile, new File(destFile.getPath() + ".bak"));
                // and create a new copy
                FileUtils.copy(new File(db.getPath()), destFile);
            } catch (@NonNull final IOException e) {
                Logger.error(context, TAG, e);
            }
        }

        final SynchronizedDb syncedDb = new SynchronizedDb(sSynchronizer, db);

        int curVersion = oldVersion;

        if (curVersion < newVersion && curVersion == 1) {
            curVersion = 2;
            TBL_BOOKS.alterTableAddColumn(syncedDb, DBDefinitions.DOM_BOOK_COLOR);
        }
        if (curVersion < newVersion && curVersion == 2) {
            curVersion = 3;
            TBL_BOOKS.alterTableAddColumn(syncedDb, DBDefinitions.DOM_BOOK_CONDITION);
            TBL_BOOKS.alterTableAddColumn(syncedDb, DBDefinitions.DOM_BOOK_CONDITION_DUST_COVER);
        }
        if (curVersion < newVersion && curVersion == 3) {
            curVersion = 4;
            // bug fix: this was modifying the books last update-date each time a bookshelf
            // changed its current style.
            syncedDb.execSQL("DROP TRIGGER IF EXISTS after_update_onbookshelf");
        }
        if (curVersion < newVersion && curVersion == 4) {
            curVersion = 5;
            // changed column names; just scrap the old data
            TBL_BOOK_LIST_NODE_STATE.recreate(syncedDb, true);
        }
        if (curVersion < newVersion && curVersion == 5) {
            curVersion = 6;
            TBL_BOOKSHELF.alterTableAddColumn(syncedDb, DBDefinitions.DOM_BOOKSHELF_BL_TOP_POS);
            TBL_BOOKSHELF.alterTableAddColumn(syncedDb, DBDefinitions.DOM_BOOKSHELF_BL_TOP_OFFSET);
            TBL_BOOKSHELF.alterTableAddColumn(syncedDb, DBDefinitions.DOM_BOOKSHELF_BL_TOP_ROW_ID);

            PreferenceManager.getDefaultSharedPreferences(context)
                             .edit()
                             .remove("booklist.top.row")
                             .remove("booklist.top.rowId")
                             .remove("booklist.top.offset")
                             .remove("fields.visibility.bookshelf")
                             .remove("tmp.edit.book.tab.authSer")
                             .remove("edit.book.tab.authSer")
                             .remove("compat.booklist.mode")
                             .apply();
        }

        if (curVersion < newVersion && curVersion == 6) {
            //noinspection UnusedAssignment
            curVersion = 7;
            TBL_PUBLISHERS.create(syncedDb, true);
            TBL_BOOK_PUBLISHER.create(syncedDb, true);

            final ContentValues cv = new ContentValues();
            final Map<String, Long> pubs = new HashMap<>();
            try (Cursor cursor = syncedDb.rawQuery(
                    "SELECT _id,publisher,language FROM books", null)) {
                while (cursor.moveToNext()) {
                    final long bookId = cursor.getLong(0);
                    final String publisherName = cursor.getString(1).trim();
                    final String lang = cursor.getString(2);
                    final long pubId;
                    if (!pubs.containsKey(publisherName)) {
                        Locale locale = LocaleUtils.getLocale(context, lang);
                        if (locale == null) {
                            locale = LocaleUtils.getUserLocale(context);
                        }
                        cv.clear();
                        cv.put(KEY_PUBLISHER_NAME, publisherName);
                        cv.put(KEY_PUBLISHER_NAME_OB,
                               DAO.encodeOrderByColumn(publisherName, locale));
                        pubId = syncedDb.insert(TBL_PUBLISHERS.getName(), null, cv);
                        pubs.put(publisherName, pubId);
                    } else {
                        //noinspection ConstantConditions
                        pubId = pubs.get(publisherName);
                    }

                    cv.clear();
                    cv.put(KEY_FK_BOOK, bookId);
                    cv.put(KEY_FK_PUBLISHER, pubId);
                    cv.put(KEY_BOOK_PUBLISHER_POSITION, 1);
                    syncedDb.insert(TBL_BOOK_PUBLISHER.getName(), null, cv);
                }
            }

            // we'll remove the column at a later version.
            syncedDb.execSQL("UPDATE books SET publisher=''");

            Scheduler.scheduleFtsRebuild(context, true);
        }

        // TODO: if at a future time we make a change that requires to copy/reload the books table,
        // we should at the same time change DOM_UTC_LAST_SYNC_DATE_GOODREADS
        // See note in the DBDefinitions class.

        // Rebuild all indices
        recreateIndices(syncedDb);
        // Rebuild all triggers
        createTriggers(syncedDb);
    }

    @Override
    public void onOpen(@NonNull final SQLiteDatabase db) {
        if (sIsCollationCaseSensitive == null) {
            sIsCollationCaseSensitive = collationIsCaseSensitive(db);
        }
    }
}
