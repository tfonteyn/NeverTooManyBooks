/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.StartupActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_STYLE_IS_PREFERRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_STYLE_MENU_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_CALIBRE_BOOK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_CALIBRE_BOOK_MAIN_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_CALIBRE_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_CALIBRE_LIBRARY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_IS_BUILTIN;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_IS_PREFERRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_MENU_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_STYLE_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_GOODREADS_LAST_SYNC_DATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_UTC_LAST_UPDATED;
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
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_LIBRARIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_VIRTUAL_LIBRARIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.FtsDefinitions.KEY_FTS_BOOK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.FtsDefinitions.TBL_FTS_BOOKS;

/**
 * Singleton {@link SQLiteOpenHelper} for the main database.
 * Uses the application context.
 */
public final class DBHelper
        extends SQLiteOpenHelper {

    /** Current version. */
    public static final int DATABASE_VERSION = 15;

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
    @NonNull
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
    @NonNull
    public static File getDatabasePath(@NonNull final Context context) {
        return context.getDatabasePath(DATABASE_NAME);
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

            final String s;
            try (Cursor c = db.rawQuery("SELECT t,i FROM collation_cs_check"
                                        + " ORDER BY t " + DAOSql._COLLATION + ",i",
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
     * @param db Database Access
     */
    public void recreateIndices(@NonNull final SynchronizedDb db) {
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
        for (final TableDefinition table : DBDefinitions.ALL_TABLES.values()) {
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
                // 1==true
                + ',' + KEY_STYLE_IS_BUILTIN
                // 0==false
                + ',' + KEY_STYLE_IS_PREFERRED
                + ',' + KEY_STYLE_MENU_POSITION
                + ',' + KEY_STYLE_UUID
                + ") VALUES(?,1,0,?,?)";
        try (SQLiteStatement stmt = db.compileStatement(sqlInsertStyles)) {
            for (int id = StyleDAO.BuiltinStyles.MAX_ID; id < 0; id++) {
                // remember, the id is negative -1..
                stmt.bindLong(1, id);
                // menu position, initially just as defined but with a positive number
                stmt.bindLong(2, -id);
                stmt.bindString(3, StyleDAO.BuiltinStyles.getUuidById(-id));

                // after inserting '-1' our debug logging will claim that the insert failed.
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
     * Run at installation time to add the 'all' and default shelves to the database.
     *
     * @param context Current context
     * @param db      Database Access
     */
    private void prepareBookshelfTable(@NonNull final Context context,
                                       @NonNull final SQLiteDatabase db) {
        // inserts a 'All Books' bookshelf with _id==-1, see {@link Bookshelf}.
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                   + '(' + KEY_PK_ID
                   + ',' + KEY_BOOKSHELF_NAME
                   + ',' + KEY_FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.ALL_BOOKS
                   + ",'" + context.getString(R.string.bookshelf_all_books)
                   + "'," + ListStyle.DEFAULT_STYLE_ID
                   + ')');

        // inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf}.
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                   + '(' + KEY_PK_ID
                   + ',' + KEY_BOOKSHELF_NAME
                   + ',' + KEY_FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.DEFAULT
                   + ",'" + context.getString(R.string.bookshelf_my_books)
                   + "'," + ListStyle.DEFAULT_STYLE_ID
                   + ')');
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
     * @param db Database Access
     */
    public void createTriggers(@NonNull final SynchronizedDb db) {

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
               + "  UPDATE " + TBL_BOOKS.getName() + " SET ";

        final StringBuilder eidSb = new StringBuilder();
        for (final Domain domain : SearchEngineRegistry.getInstance().getExternalIdDomains()) {
            eidSb.append(domain.getName()).append("=null,");
        }
        body += eidSb.toString();

        //NEWTHINGS: adding a new search engine: optional: add engine specific keys
        body += KEY_UTC_GOODREADS_LAST_SYNC_DATE + "=''";

        body += " WHERE " + KEY_PK_ID + "=NEW." + KEY_PK_ID + ";\n"
                + " END";

        db.execSQL("DROP TRIGGER IF EXISTS " + name);
        db.execSQL("\nCREATE TRIGGER " + name + body);
    }

    @Override
    public void onConfigure(@NonNull final SQLiteDatabase db) {
        // Turn OFF recursive triggers;
        db.execSQL("PRAGMA recursive_triggers = OFF");

        // DO NOT ENABLE FOREIGN KEY CONSTRAINTS HERE. Enable them in onOpen instead.
        // WE NEED THIS TO BE false (default) TO ALLOW onUpgrade TO MAKE SCHEMA CHANGES
        // db.setForeignKeyConstraintsEnabled(true);
    }

    /**
     * <strong>REMINDER: foreign key constraints are DISABLED here</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onCreate(@NonNull final SQLiteDatabase db) {


        final Context context = AppLocale.getInstance().apply(App.getAppContext());

        final SynchronizedDb syncedDb = new SynchronizedDb(sSynchronizer, db);

        // Create all the app & user data tables in the correct dependency order
        TableDefinition.onCreate(db, DBDefinitions.ALL_TABLES.values());

        // insert the builtin styles so foreign key rules are possible.
        prepareStylesTable(db);
        // and the all/default shelves
        prepareBookshelfTable(context, db);

        //IMPORTANT: withDomainConstraints MUST BE false (FTS columns don't use a type/constraints)
        TBL_FTS_BOOKS.create(syncedDb, false);

        createTriggers(syncedDb);
    }

    /**
     * <strong>REMINDER: foreign key constraints are DISABLED here</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase db,
                          final int oldVersion,
                          final int newVersion) {

        final Context context = App.getAppContext();

        final StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.onProgress(R.string.progress_msg_upgrading);
        }

        // take a backup before modifying the database
        if (oldVersion != newVersion) {
            final String backup = DB_UPGRADE_FILE_PREFIX + "-" + oldVersion + '-' + newVersion;
            try {
                final File destFile = AppDir.Upgrades.getFile(context, backup);
                // rename the existing file if there is one
                if (destFile.exists()) {
                    final File destination = new File(destFile.getPath() + ".bak");
                    try {
                        FileUtils.rename(destFile, destination);
                    } catch (@NonNull final IOException e) {
                        Logger.error(context, TAG, e,
                                     "failed to rename source=" + destFile
                                     + " TO destination=" + destination, e);
                    }
                }
                // and create a new copy
                FileUtils.copy(new File(db.getPath()), destFile);
            } catch (@NonNull final IOException e) {
                Logger.error(context, TAG, e);
            }
        }

        final SynchronizedDb syncedDb = new SynchronizedDb(sSynchronizer, db);

        if (oldVersion < 12) {
            // This version should be the current public available APK on github.
            // i.e. 1.2.1 is available which can upgrade older versions.
            throw new UnsupportedOperationException(
                    context.getString(R.string.error_upgrade_not_supported, "1.2.1"));
        }
        if (oldVersion < 13) {
            //
            // This is the v1.2.0 / 1.2.1 / 1.3.0 release.
            //
            TBL_BOOKLIST_STYLES.alterTableAddColumns(syncedDb,
                                                     DOM_STYLE_MENU_POSITION,
                                                     DOM_STYLE_IS_PREFERRED);

            final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
            final String itemsStr = global.getString("bookList.style.preferred.order", null);
            if (itemsStr != null && !itemsStr.isEmpty()) {
                final String[] entries = itemsStr.split(",");
                if (entries.length > 0) {
                    final ContentValues cv = new ContentValues();
                    for (int i = 0; i < entries.length; i++) {
                        final String uuid = entries[i];
                        if (uuid != null && !uuid.isEmpty()) {
                            cv.clear();
                            cv.put("menu_order", i);
                            cv.put("preferred", 1);
                            syncedDb.update("book_list_styles", cv,
                                            "uuid=?", new String[]{uuid});
                        }
                    }
                }
            }
            global.edit().remove("bookList.style.preferred.order").apply();
        }
        if (oldVersion < 14) {
            TBL_CALIBRE_BOOKS.create(syncedDb, true);
            TBL_CALIBRE_LIBRARIES.create(syncedDb, true);

            syncedDb.execSQL("INSERT INTO calibre_books (book,clb_book_uuid)"
                             + " SELECT _id, clb_book_uuid FROM books"
                             + " WHERE clb_book_uuid IS NOT NULL");
            syncedDb.execSQL("UPDATE books SET clb_book_uuid=NULL");

            PreferenceManager.getDefaultSharedPreferences(context)
                             .edit()
                             .remove("scanner.preferred")
                             .remove("compat.image.cropper.viewlayertype")
                             .apply();
        }
        if (oldVersion < 15) {

            db.execSQL("ALTER TABLE calibre_books RENAME TO tmp_cb");
            db.execSQL("ALTER TABLE calibre_vlib RENAME TO tmp_vl");

            TBL_CALIBRE_BOOKS.create(syncedDb, true);
            TBL_CALIBRE_LIBRARIES.create(syncedDb, true);
            TBL_CALIBRE_VIRTUAL_LIBRARIES.create(syncedDb, true);

            final Map<String, Long> libString2Id = new HashMap<>();
            try (Cursor cursor = db.rawQuery(
                    "SELECT library, vlib_name, bookshelf_id FROM tmp_vl"
                    + " WHERE expression = ''", null)) {
                while (cursor.moveToNext()) {
                    final String libraryId = cursor.getString(0);
                    final String name = cursor.getString(1);
                    final long bookshelfId = cursor.getLong(2);

                    try (SynchronizedStatement stmt = syncedDb
                            .compileStatement(DAOSql.SqlInsert.CALIBRE_LIBRARY)) {
                        stmt.bindString(1, "");
                        stmt.bindString(2, libraryId);
                        stmt.bindString(3, name);
                        stmt.bindString(4, "");
                        stmt.bindLong(5, bookshelfId);
                        final long iId = stmt.executeInsert();
                        libString2Id.put(libraryId, iId);
                    }
                }
            }
            try (Cursor cursor = db.rawQuery(
                    "SELECT library, vlib_name, bookshelf_id, expression FROM tmp_vl"
                    + " WHERE expression <> ''", null)) {
                while (cursor.moveToNext()) {
                    final String libraryId = cursor.getString(0);
                    final String name = cursor.getString(1);
                    final long bookshelfId = cursor.getLong(2);
                    final String expression = cursor.getString(3);

                    final Long libId = libString2Id.get(libraryId);
                    // db14 dev had a cleanup-bug, skip if not there.
                    if (libId != null) {
                        try (SynchronizedStatement stmt = syncedDb
                                .compileStatement(DAOSql.SqlInsert.CALIBRE_VIRTUAL_LIBRARY)) {
                            stmt.bindLong(1, libId);
                            stmt.bindString(2, name);
                            stmt.bindString(3, expression);
                            stmt.bindLong(4, bookshelfId);
                            stmt.executeInsert();
                        }
                    }
                }
            }

            try (Cursor cursor = db.rawQuery(
                    "SELECT book, clb_id, clb_uuid, main_format, library FROM tmp_cb", null)) {
                while (cursor.moveToNext()) {
                    final long bookId = cursor.getLong(0);
                    final long clbBookId = cursor.getLong(1);
                    final String clbUuid = cursor.getString(2);
                    final String format = cursor.getString(3);
                    final String libraryId = cursor.getString(4);

                    final Long libId = libString2Id.get(libraryId);
                    if (libId != null) {
                        final ContentValues cv = new ContentValues();
                        cv.put(KEY_FK_BOOK, bookId);
                        cv.put(KEY_CALIBRE_BOOK_ID, clbBookId);
                        cv.put(KEY_CALIBRE_BOOK_UUID, clbUuid);
                        cv.put(KEY_CALIBRE_BOOK_MAIN_FORMAT, format);
                        cv.put(KEY_FK_CALIBRE_LIBRARY, libId);

                        syncedDb.insert(TBL_CALIBRE_BOOKS.getName(), null, cv);
                    }
                }
            }

            db.execSQL("DROP TABLE tmp_vl");
            db.execSQL("DROP TABLE tmp_cb");

            PreferenceManager.getDefaultSharedPreferences(context)
                             .edit()
                             .remove("calibre.last.sync.date")
                             .apply();
        }

        //URGENT: the use of recreateAndReload is dangerous right now and can break updates.
        // More specifically: the recreateAndReload routine can only be used ONCE per table.
        // We'll need to keep previous table definitions as BC used to do.

        //TODO: if at a future time we make a change that requires to copy/reload the books table:
        // 1. change DOM_UTC_LAST_SYNC_DATE_GOODREADS -> See note in the DBDefinitions class.
        // 2. remove the column "clb_uuid"

        //NEWTHINGS: adding a new search engine: optional: add external id DOM
        //TBL_BOOKS.alterTableAddColumn(syncedDb, DBDefinitions.DOM_your_engine_external_id);

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

        // Turn ON foreign key support so that CASCADE etc. works.
        // This is the same as db.execSQL("PRAGMA foreign_keys = ON");
        db.setForeignKeyConstraintsEnabled(true);
    }

    /**
     * Not called yet, but this method collects ALL keys which were declared obsolete.
     * The issue being that some keys will be in backups and re-created.
     * <p>
     * pro: good to clean up<br>
     * con: a handful of obsolete keys is not an issue
     *
     * @param context Current context
     */
    private void removeObsoleteKeys(@NonNull final Context context) {

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        final Set<String> all = global.getAll().keySet();

        final SharedPreferences.Editor ed = global.edit();
        for (final String key : all) {
            if (key.startsWith("search.site")) {
                ed.remove(key);
            }
        }

        ed.remove("bookList.style.preferred.order")
          .remove("booklist.top.row")
          .remove("booklist.top.rowId")
          .remove("booklist.top.offset")
          .remove("compat.booklist.mode")
          .remove("compat.image.cropper.viewlayertype")
          .remove("edit.book.tab.authSer")
          .remove("edit.book.tab.nativeId")
          .remove("fields.visibility.bookshelf")
          .remove("startup.lastVersion")
          .remove("tmp.edit.book.tab.authSer")
          .remove("scanner.preferred")
          .remove("calibre.last.sync.date")
          .apply();
    }


    /**
     * Delete al user data from the database.
     * Tables will get their initial default data re-added (e.g. styles, shelves...)
     *
     * @param context Current context
     * @param db      Database Access
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean deleteAllContent(@NonNull final Context context,
                                    @NonNull final SynchronizedDb db) {

        Synchronizer.SyncLock syncLock = null;
        try {
            syncLock = db.beginTransaction(true);

            db.delete(TBL_CALIBRE_BOOKS.getName(), null, null);
            db.delete(TBL_CALIBRE_VIRTUAL_LIBRARIES.getName(), null, null);
            db.delete(TBL_CALIBRE_LIBRARIES.getName(), null, null);

            db.delete(TBL_BOOK_LIST_NODE_STATE.getName(), null, null);
            db.delete(TBL_FTS_BOOKS.getName(), null, null);

            db.delete(TBL_BOOKS.getName(), null, null);
            db.delete(TBL_PUBLISHERS.getName(), null, null);
            db.delete(TBL_SERIES.getName(), null, null);
            db.delete(TBL_AUTHORS.getName(), null, null);

            db.delete(TBL_BOOKSHELF.getName(), null, null);
            db.delete(TBL_BOOKLIST_STYLES.getName(), null, null);

            prepareStylesTable(db.getSQLiteDatabase());
            prepareBookshelfTable(context, db.getSQLiteDatabase());

            db.setTransactionSuccessful();
            return true;

        } catch (@NonNull final Exception e) {
            Logger.error(context, TAG, e);
            return false;

        } finally {
            if (syncLock != null) {
                db.endTransaction(syncLock);
            }
        }
    }
}
