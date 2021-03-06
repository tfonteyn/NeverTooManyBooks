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
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_LIBRARIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_VIRTUAL_LIBRARIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_STRIPINFO_COLLECTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOL_STYLE_IS_BUILTIN;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOL_STYLE_IS_PREFERRED;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_CALIBRE_LIBRARY;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_CALIBRE_BOOK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_CALIBRE_BOOK_MAIN_FORMAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_CALIBRE_BOOK_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_CALIBRE_LIBRARY_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_CALIBRE_LIBRARY_STRING_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_CALIBRE_LIBRARY_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_CALIBRE_VIRT_LIB_EXPR;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_FTS_BOOK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_ISBN;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_STYLE_MENU_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_STYLE_UUID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.UTC_DATE_LAST_SYNC_CALIBRE_LIBRARY;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.UTC_DATE_LAST_UPDATED;

/**
 * {@link SQLiteOpenHelper} for the main database.
 * Uses the application context.
 */
public class DBHelper
        extends SQLiteOpenHelper {

    /** Current version. */
    public static final int DATABASE_VERSION = 16;

    /** NEVER change this name. */
    public static final String DATABASE_NAME = "nevertoomanybooks.db";

    /** Log tag. */
    private static final String TAG = "DBHelper";

    /** The database prepared statement cache size (default 25, max 100). */
    private static final String PK_STARTUP_DB_STMT_CACHE_SIZE = "db.stmt.cache.size";

    /** Prefix for the filename of a database backup before doing an upgrade. */
    private static final String DB_UPGRADE_FILE_PREFIX = "DbUpgrade";

    /** SQL to get the names of all indexes. */
    private static final String SQL_GET_INDEX_NAMES =
            "SELECT name FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL";

    /** Readers/Writer lock for <strong>this</strong> database. */
    private static final Synchronizer sSynchronizer = new Synchronizer();

    /** Static Factory object to create a {@link SynchronizedCursor} cursor. */
    private static final SQLiteDatabase.CursorFactory CURSOR_FACTORY =
            (db, d, et, q) -> new SynchronizedCursor(d, et, q, sSynchronizer);

    @Nullable
    private static Boolean sIsCollationCaseSensitive;

    /** DO NOT USE INSIDE THIS CLASS! ONLY FOR USE BY CLIENTS CALLING {@link #getDb()}. */
    @Nullable
    private SynchronizedDb mSynchronizedDb;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public DBHelper(@NonNull final Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, CURSOR_FACTORY, DATABASE_VERSION);
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
     * Wrapper to allow
     * {@link com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildIndexesTask}.
     * safe access to the database.
     */
    public static void recreateIndices() {
        final SynchronizedDb db = ServiceLocator.getInstance().getDb();
        final Synchronizer.SyncLock txLock = db.beginTransaction(true);
        try {
            // It IS safe here to get the underlying database, as we're in a SyncLock.
            recreateIndices(db.getSQLiteDatabase());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction(txLock);
        }
    }

    /**
     * This method should only be called at the *END* of {@link #onUpgrade}.
     * <p>
     * (re)Creates the indexes as defined on the tables.
     */
    private static void recreateIndices(@NonNull final SQLiteDatabase db) {
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
                    Logger.error(TAG, e);
                    throw e;
                } catch (@NonNull final RuntimeException e) {
                    Logger.error(TAG, e, "DROP INDEX failed: " + indexName);
                }
            }
        }

        // now recreate
        for (final TableDefinition table : DBDefinitions.ALL_TABLES.values()) {
            table.createIndices(db);
        }

        db.execSQL("analyze");
    }

    /**
     * Check if the collation we use is case sensitive.
     *
     * @return {@code true} if case-sensitive (i.e. up to "you" to add lower/upper calls)
     */
    public boolean isCollationCaseSensitive() {
        //noinspection ConstantConditions
        return sIsCollationCaseSensitive;
    }

    /**
     * Create the Synchronized database.
     * <p>
     * Called during startup. If needed, this will trigger the creation/upgrade process.
     *
     * @param global Global preferences
     */
    public void initialiseDb(@NonNull final SharedPreferences global) {
        synchronized (this) {
            if (mSynchronizedDb != null) {
                throw new IllegalStateException("Already initialized");
            }

            // default 25, see SynchronizedDb javadoc
            final int stmtCacheSize = global.getInt(PK_STARTUP_DB_STMT_CACHE_SIZE, 25);

            // Dev note: don't move this to the constructor, "this" must
            // be fully constructed before we can pass it to the SynchronizedDb constructor
            mSynchronizedDb = new SynchronizedDb(sSynchronizer, this,
                                                 stmtCacheSize);
        }
    }

    /**
     * Get the Synchronized database.
     *
     * @return database connection
     */
    @NonNull
    public SynchronizedDb getDb() {
        return Objects.requireNonNull(mSynchronizedDb, "Not initialized");
    }

    @Override
    public void close() {
        if (mSynchronizedDb != null) {
            mSynchronizedDb.close();
        }
        super.close();
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
        try {
            // Drop and create table
            db.execSQL(dropTable);
            db.execSQL("CREATE TEMPORARY TABLE collation_cs_check (t text, i integer)");

            // Row that *should* be returned first assuming 'a' <=> 'A'
            db.execSQL("INSERT INTO collation_cs_check VALUES('a', 1)");
            // Row that *should* be returned second assuming 'a' <=> 'A';
            // will be returned first if 'A' < 'a'.
            db.execSQL("INSERT INTO collation_cs_check VALUES('A', 2)");

            final String s;
            try (Cursor c = db.rawQuery("SELECT t,i FROM collation_cs_check"
                                        + " ORDER BY t COLLATE LOCALIZED" + ",i",
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
            Logger.error(TAG, e);
            throw e;
        } finally {
            try {
                db.execSQL(dropTable);
            } catch (@NonNull final SQLException e) {
                Logger.error(TAG, e);
            }
        }
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
                + '(' + PK_ID
                // 1==true
                + ',' + BOOL_STYLE_IS_BUILTIN
                // 0==false
                + ',' + BOOL_STYLE_IS_PREFERRED
                + ',' + KEY_STYLE_MENU_POSITION
                + ',' + KEY_STYLE_UUID
                + ") VALUES(?,1,0,?,?)";
        try (SQLiteStatement stmt = db.compileStatement(sqlInsertStyles)) {
            for (int id = BuiltinStyle.MAX_ID; id < 0; id++) {
                // remember, the id is negative -1..
                stmt.bindLong(1, id);
                // menu position, initially just as defined but with a positive number
                stmt.bindLong(2, -id);
                stmt.bindString(3, BuiltinStyle.getUuidById(-id));

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
                   + '(' + PK_ID
                   + ',' + KEY_BOOKSHELF_NAME
                   + ',' + FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.ALL_BOOKS
                   + ",'" + context.getString(R.string.bookshelf_all_books)
                   + "'," + BuiltinStyle.DEFAULT_ID
                   + ')');

        // inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf}.
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                   + '(' + PK_ID
                   + ',' + KEY_BOOKSHELF_NAME
                   + ',' + FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.DEFAULT
                   + ",'" + context.getString(R.string.bookshelf_my_books)
                   + "'," + BuiltinStyle.DEFAULT_ID
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
     */
    private void createTriggers(@NonNull final SQLiteDatabase db) {

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
               + "  SET " + UTC_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + PK_ID + "=OLD." + FK_BOOK + ";\n"
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
//        db.execSQL("DROP TRIGGER IF EXISTS " + name);
//        db.execSQL("\nCREATE TRIGGER " + name + body);

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
               + "  SET " + UTC_DATE_LAST_UPDATED + "=current_timestamp"

               + " WHERE " + PK_ID + " IN \n"
               // actual books by this Author
               + "(SELECT " + FK_BOOK + " FROM " + TBL_BOOK_AUTHOR.getName()
               + " WHERE " + FK_AUTHOR + "=OLD." + PK_ID + ")\n"

               + " OR " + PK_ID + " IN \n"
               // books with entries in anthologies by this Author
               + "(SELECT " + FK_BOOK
               + " FROM " + TBL_BOOK_TOC_ENTRIES.startJoin(TBL_TOC_ENTRIES)
               + " WHERE " + FK_AUTHOR + "=OLD." + PK_ID + ");\n"
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
               + "  SET " + UTC_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + PK_ID + "=OLD." + FK_BOOK + ";\n"
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
               + "  SET " + UTC_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + PK_ID + " IN \n"
               + "(SELECT " + FK_BOOK + " FROM " + TBL_BOOK_SERIES.getName()
               + " WHERE " + FK_SERIES + "=OLD." + PK_ID + ");\n"
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
               + "  SET " + UTC_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + PK_ID + "=OLD." + FK_BOOK + ";\n"
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
               + "  SET " + UTC_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + PK_ID + "=NEW." + FK_BOOK + ";\n"
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
               + "  SET " + UTC_DATE_LAST_UPDATED + "=current_timestamp"
               + " WHERE " + PK_ID + "=NEW." + FK_BOOK + ";\n"
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
               + " WHERE " + KEY_FTS_BOOK_ID + "=OLD." + PK_ID + ";\n"
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

        body += SearchEngineRegistry.getInstance()
                                    .getExternalIdDomains()
                                    .stream()
                                    .map(domain -> domain.getName() + "=null")
                                    .collect(Collectors.joining(","));

        //NEWTHINGS: adding a new search engine: optional: add engine specific keys

        body += " WHERE " + PK_ID + "=NEW." + PK_ID + ";\n"
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
     * <strong>WARNING: do NOT use SynchronizedDb here!</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onCreate(@NonNull final SQLiteDatabase db) {

        final Context context = ServiceLocator.getLocalizedAppContext();

        // Create all the app & user data tables in the correct dependency order
        TableDefinition.onCreate(db, DBDefinitions.ALL_TABLES.values());

        // insert the builtin styles so foreign key rules are possible.
        prepareStylesTable(db);
        // and the all/default shelves
        prepareBookshelfTable(context, db);

        //IMPORTANT: withDomainConstraints MUST BE false (FTS columns don't use a type/constraints)
        TBL_FTS_BOOKS.create(db, false);

        createTriggers(db);
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

        final Context context = ServiceLocator.getLocalizedAppContext();

        final StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.onProgress(context.getString(R.string.progress_msg_upgrading));
        }

        // take a backup before modifying the database
        if (oldVersion != newVersion) {
            final String backup = DB_UPGRADE_FILE_PREFIX + "-" + oldVersion + '-' + newVersion;
            try {
                final File destFile = new File(ServiceLocator.getUpgradesDir(), backup);
                // rename the existing file if there is one
                if (destFile.exists()) {
                    final File destination = new File(destFile.getPath() + ".bak");
                    try {
                        FileUtils.rename(destFile, destination);
                    } catch (@NonNull final IOException e) {
                        Logger.error(TAG, e, "failed to rename source=" + destFile
                                             + " TO destination=" + destination, e);
                    }
                }
                // and create a new copy
                FileUtils.copy(new File(db.getPath()), destFile);
            } catch (@NonNull final IOException e) {
                Logger.error(TAG, e);
            }
        }

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
            TBL_BOOKLIST_STYLES.alterTableAddColumns(db,
                                                     DBDefinitions.DOM_STYLE_MENU_POSITION,
                                                     DBDefinitions.DOM_STYLE_IS_PREFERRED);

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
                            db.update("book_list_styles", cv,
                                      "uuid=?", new String[]{uuid});
                        }
                    }
                }
            }
        }
        if (oldVersion < 14) {
            TBL_CALIBRE_BOOKS.create(db, true);
            TBL_CALIBRE_LIBRARIES.create(db, true);

            db.execSQL("INSERT INTO calibre_books (book,clb_book_uuid)"
                       + " SELECT _id, clb_book_uuid FROM books"
                       + " WHERE clb_book_uuid IS NOT NULL");
            db.execSQL("UPDATE books SET clb_book_uuid=NULL");
        }
        if (oldVersion < 15) {

            db.execSQL("ALTER TABLE calibre_books RENAME TO tmp_cb");
            db.execSQL("ALTER TABLE calibre_vlib RENAME TO tmp_vl");

            TBL_CALIBRE_BOOKS.create(db, true);
            TBL_CALIBRE_LIBRARIES.create(db, true);
            TBL_CALIBRE_VIRTUAL_LIBRARIES.create(db, true);

            final Map<String, Long> libString2Id = new HashMap<>();
            try (Cursor cursor = db.rawQuery(
                    "SELECT library, vlib_name, bookshelf_id FROM tmp_vl"
                    + " WHERE expression = ''", null)) {
                while (cursor.moveToNext()) {
                    final String libraryId = cursor.getString(0);
                    final String name = cursor.getString(1);
                    final long bookshelfId = cursor.getLong(2);

                    try (SQLiteStatement stmt = db.compileStatement(
                            "INSERT INTO " + TBL_CALIBRE_LIBRARIES.getName()
                            + '(' + KEY_CALIBRE_LIBRARY_UUID
                            + ',' + KEY_CALIBRE_LIBRARY_STRING_ID
                            + ',' + KEY_CALIBRE_LIBRARY_NAME
                            + ',' + UTC_DATE_LAST_SYNC_CALIBRE_LIBRARY
                            + ',' + FK_BOOKSHELF
                            + ") VALUES (?,?,?,?,?)")) {
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
                        try (SQLiteStatement stmt = db.compileStatement(
                                "INSERT INTO " + TBL_CALIBRE_VIRTUAL_LIBRARIES.getName()
                                + '(' + FK_CALIBRE_LIBRARY
                                + ',' + KEY_CALIBRE_LIBRARY_NAME
                                + ',' + KEY_CALIBRE_VIRT_LIB_EXPR
                                + ',' + FK_BOOKSHELF
                                + ") VALUES (?,?,?,?)")) {
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
                        cv.put(FK_BOOK, bookId);
                        cv.put(KEY_CALIBRE_BOOK_ID, clbBookId);
                        cv.put(KEY_CALIBRE_BOOK_UUID, clbUuid);
                        cv.put(KEY_CALIBRE_BOOK_MAIN_FORMAT, format);
                        cv.put(FK_CALIBRE_LIBRARY, libId);

                        db.insert(TBL_CALIBRE_BOOKS.getName(), null, cv);
                    }
                }
            }

            db.execSQL("DROP TABLE tmp_vl");
            db.execSQL("DROP TABLE tmp_cb");
        }
        if (oldVersion < 16) {
            TBL_STRIPINFO_COLLECTION.create(db, true);

            context.deleteDatabase("taskqueue.db");
        }

        //TODO: if at a future time we make a change that requires to copy/reload the books table:
        // 1. remove the column "books.clb_uuid"
        // 2. remove the column "books.last_goodreads_sync_date"

        //NEWTHINGS: adding a new search engine: optional: add external id DOM
        //TBL_BOOKS.alterTableAddColumn(db, DBDefinitions.DOM_your_engine_external_id);


        removeObsoleteKeys(context);

        // Rebuild all indices
        recreateIndices(db);

        // Rebuild all triggers
        createTriggers(db);
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
     * This method removes all keys which were declared obsolete.
     *
     * @param context Current context
     */
    private void removeObsoleteKeys(@NonNull final Context context) {

        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit()
                         .remove("tips.tip.BOOKLIST_STYLES_EDITOR")
                         .remove("tips.tip.BOOKLIST_STYLE_GROUPS")
                         .remove("tips.tip.BOOKLIST_STYLE_PROPERTIES")

                         .remove("bookList.style.preferred.order")

                         .remove("booklist.top.rowId")
                         .remove("booklist.top.row")
                         .remove("booklist..top.row")
                         .remove("booklist.top.offset")
                         .remove("booklist..top.offset")

                         .remove("calibre.last.sync.date")
                         .remove("compat.booklist.mode")
                         .remove("compat.image.cropper.viewlayertype")
                         .remove("edit.book.tab.authSer")
                         .remove("edit.book.tab.nativeId")
                         .remove("fields.visibility.bookshelf")
                         .remove("goodreads.enabled")
                         .remove("goodreads.showMenu")
                         .remove("goodreads.search.collect.genre")
                         .remove("goodreads.AccessToken.Token")
                         .remove("goodreads.AccessToken.Secret")
                         .remove("librarything.dev_key")
                         .remove("scanner.preferred")
                         .remove("search.site.goodreads.data.enabled")
                         .remove("search.site.goodreads.covers.enabled")
                         .remove("startup.lastVersion")
                         .remove("tmp.edit.book.tab.authSer")
                         .apply();
    }
}
