/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookshelfDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CalibreCustomFieldDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.StyleDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UpgradeFailedException;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF_FILTERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_TOC_ENTRIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_CALIBRE_CUSTOM_FIELDS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_STRIPINFO_COLLECTION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_TOC_ENTRIES;

/**
 * {@link SQLiteOpenHelper} for the main database.
 * Uses the application context.
 */
public class DBHelper
        extends SQLiteOpenHelper {

    /** Current version. */
    public static final int DATABASE_VERSION = 23;

    /** NEVER change this name. */
    private static final String DATABASE_NAME = "nevertoomanybooks.db";

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
    private static final Synchronizer SYNCHRONIZER = new Synchronizer();

    /** Static Factory object to create a {@link SynchronizedCursor} cursor. */
    private static final SQLiteDatabase.CursorFactory CURSOR_FACTORY =
            (db, d, et, q) -> new SynchronizedCursor(d, et, q, SYNCHRONIZER);

    @Nullable
    private static Boolean sIsCollationCaseSensitive;

    @IntRange(from = 25, to = SQLiteDatabase.MAX_SQL_CACHE_SIZE)
    private final int stmtCacheSize;

    /** DO NOT USE INSIDE THIS CLASS! ONLY FOR USE BY CLIENTS CALLING {@link #getDb()}. */
    @Nullable
    private SynchronizedDb synchronizedDb;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public DBHelper(@NonNull final Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, CURSOR_FACTORY, DATABASE_VERSION);

        // default 25, see SynchronizedDb javadoc
        final int size = PreferenceManager.getDefaultSharedPreferences(context)
                                          .getInt(PK_STARTUP_DB_STMT_CACHE_SIZE, 25);
        stmtCacheSize = MathUtils.clamp(size, 25, SQLiteDatabase.MAX_SQL_CACHE_SIZE);
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
     * Get the main database.
     *
     * @return database connection
     *
     * @throws SQLiteException if the database cannot be opened
     */
    @NonNull
    public SynchronizedDb getDb() {
        synchronized (this) {
            if (synchronizedDb == null) {
                // Dev note: don't move this to the constructor, "this" must
                // be fully constructed before we can pass it to the SynchronizedDb constructor
                synchronizedDb = new SynchronizedDb(SYNCHRONIZER, this,
                                                    stmtCacheSize);
            }
        }
        return synchronizedDb;
    }

    @Override
    public void close() {
        if (synchronizedDb != null) {
            synchronizedDb.close();
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

            final boolean cs;
            try (Cursor c = db.rawQuery(
                    "SELECT t,i FROM collation_cs_check ORDER BY t COLLATE LOCALIZED,i",
                    null)) {
                c.moveToFirst();
                cs = !"a".equals(c.getString(0));
            }

            if (cs) {
                Log.e(TAG, "\n=============================================="
                           + "\n========== CASE SENSITIVE COLLATION =========="
                           + "\n==============================================");
            }
            return cs;

        } catch (@NonNull final SQLException e) {
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
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
               + " WHERE " + DBKey.PK_ID + "=OLD." + DBKey.FK_BOOK + ";\n"
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
//                + "  SET " + DATE_LAST_UPDATED + "=current_timestamp"
//                + " WHERE " + PK_ID + "=Old." + FK_BOOK + ";\n"
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
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"

               + " WHERE " + DBKey.PK_ID + " IN \n"
               // actual books by this Author
               + "(SELECT " + DBKey.FK_BOOK + " FROM " + TBL_BOOK_AUTHOR.getName()
               + " WHERE " + DBKey.FK_AUTHOR + "=OLD." + DBKey.PK_ID + ")\n"

               + " OR " + DBKey.PK_ID + " IN \n"
               // books with entries in anthologies by this Author
               + "(SELECT " + DBKey.FK_BOOK
               + " FROM " + TBL_BOOK_TOC_ENTRIES.startJoin(TBL_TOC_ENTRIES)
               + " WHERE " + DBKey.FK_AUTHOR + "=OLD." + DBKey.PK_ID + ");\n"
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
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
               + " WHERE " + DBKey.PK_ID + "=OLD." + DBKey.FK_BOOK + ";\n"
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
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
               + " WHERE " + DBKey.PK_ID + " IN \n"
               + "(SELECT " + DBKey.FK_BOOK + " FROM " + TBL_BOOK_SERIES.getName()
               + " WHERE " + DBKey.FK_SERIES + "=OLD." + DBKey.PK_ID + ");\n"
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
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
               + " WHERE " + DBKey.PK_ID + "=OLD." + DBKey.FK_BOOK + ";\n"
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
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
               + " WHERE " + DBKey.PK_ID + "=NEW." + DBKey.FK_BOOK + ";\n"
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
               + "  SET " + DBKey.DATE_LAST_UPDATED__UTC + "=current_timestamp"
               + " WHERE " + DBKey.PK_ID + "=NEW." + DBKey.FK_BOOK + ";\n"
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
               + " WHERE " + DBKey.FTS_BOOK_ID + "=OLD." + DBKey.PK_ID + ";\n"
               + " END";

        db.execSQL("DROP TRIGGER IF EXISTS " + name);
        db.execSQL("\nCREATE TRIGGER " + name + body);


        /*
         * If the ISBN of a {@link Book) is changed, reset external ID's and sync dates.
         */
        name = "after_update_of_" + DBKey.BOOK_ISBN + "_on_" + TBL_BOOKS.getName();
        body = " AFTER UPDATE OF " + DBKey.BOOK_ISBN + " ON " + TBL_BOOKS.getName()
               + " FOR EACH ROW\n"
               + " WHEN NEW." + DBKey.BOOK_ISBN + " <> OLD." + DBKey.BOOK_ISBN + '\n'
               + " BEGIN\n"
               + "  UPDATE " + TBL_BOOKS.getName() + " SET ";

        body += SearchEngineConfig
                .getExternalIdDomains()
                .stream()
                .map(domain -> domain.getName() + "=null")
                .collect(Collectors.joining(","));

        //NEWTHINGS: adding a new search engine: optional: add engine specific keys

        body += " WHERE " + DBKey.PK_ID + "=NEW." + DBKey.PK_ID + ";\n"
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
     * <strong>REMINDER: foreign key constraints are DISABLED here.</strong>
     * <strong>WARNING: do NOT use SynchronizedDb here!</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onCreate(@NonNull final SQLiteDatabase db) {
        initCollation(db);

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        // Create all the app & user data tables in the correct dependency order
        TableDefinition.onCreate(db, DBDefinitions.ALL_TABLES.values());

        // insert the builtin styles so foreign key rules are possible.
        StyleDaoImpl.onPostCreate(db);
        // and the all/default shelves
        BookshelfDaoImpl.onPostCreate(context, db);

        CalibreCustomFieldDaoImpl.onPostCreate(db);

        //IMPORTANT: withDomainConstraints MUST BE false (FTS columns don't use a type/constraints)
        TBL_FTS_BOOKS.create(db, false);

        createTriggers(db);
    }

    /**
     * <strong>REMINDER: foreign key constraints are DISABLED here.</strong>
     * <p>
     * {@inheritDoc}
     */
    @SuppressLint("ApplySharedPref")
    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase db,
                          final int oldVersion,
                          final int newVersion) {
        initCollation(db);

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

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

        if (oldVersion < 15) {
            throw new UpgradeFailedException(
                    context.getString(R.string.error_upgrade_not_supported, "2.0.0"));
        }
        if (oldVersion < 16) {
            TBL_STRIPINFO_COLLECTION.create(db, true);

            context.deleteDatabase("taskqueue.db");
        }
        if (oldVersion < 17) {
            TBL_CALIBRE_CUSTOM_FIELDS.create(db, true);
            CalibreCustomFieldDaoImpl.onPostCreate(db);
        }
        if (oldVersion < 18) {
            TBL_BOOKSHELF_FILTERS.create(db, true);
        }
        if (oldVersion < 19) {
            final SharedPreferences global = PreferenceManager
                    .getDefaultSharedPreferences(context);
            // change the name of these for easier migration
            final boolean visSeries = global.getBoolean("fields.visibility."
                                                        + DBKey.SERIES_TITLE, true);
            final boolean visPublisher = global.getBoolean("fields.visibility."
                                                           + DBKey.PUBLISHER_NAME, true);
            global.edit()
                  .putBoolean("fields.visibility." + DBKey.FK_SERIES, visSeries)
                  .putBoolean("fields.visibility." + DBKey.FK_PUBLISHER, visPublisher)
                  .commit();


            TBL_BOOKLIST_STYLES.alterTableAddColumns(
                    db,
                    DBDefinitions.DOM_STYLE_NAME,

                    DBDefinitions.DOM_STYLE_GROUPS,
                    DBDefinitions.DOM_STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH,
                    DBDefinitions.DOM_STYLE_GROUPS_AUTHOR_PRIMARY_TYPE,
                    DBDefinitions.DOM_STYLE_GROUPS_SERIES_SHOW_UNDER_EACH,
                    DBDefinitions.DOM_STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH,
                    DBDefinitions.DOM_STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH,

                    DBDefinitions.DOM_STYLE_EXP_LEVEL,
                    DBDefinitions.DOM_STYLE_ROW_USES_PREF_HEIGHT,
                    DBDefinitions.DOM_STYLE_AUTHOR_SORT_BY_GIVEN_NAME,
                    DBDefinitions.DOM_STYLE_AUTHOR_SHOW_BY_GIVEN_NAME,
                    DBDefinitions.DOM_STYLE_TEXT_SCALE,
                    DBDefinitions.DOM_STYLE_COVER_SCALE,
                    DBDefinitions.DOM_STYLE_LIST_HEADER,
                    DBDefinitions.DOM_STYLE_DETAILS_SHOW_FIELDS,
                    DBDefinitions.DOM_STYLE_LIST_SHOW_FIELDS);

            final List<String> uuids = new ArrayList<>();
            try (Cursor cursor = db.rawQuery(
                    "SELECT uuid FROM " + TBL_BOOKLIST_STYLES.getName()
                    + " WHERE " + DBKey.STYLE_IS_BUILTIN + "=0", null)) {
                while (cursor.moveToNext()) {
                    uuids.add(cursor.getString(0));
                }
            }

            try (SQLiteStatement stmt = db.compileStatement(
                    "UPDATE " + TBL_BOOKLIST_STYLES.getName() + " SET "
                    + DBKey.STYLE_NAME + "=?, "

                    + DBKey.STYLE_GROUPS + "=?,"
                    + DBKey.STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH + "=?,"
                    + DBKey.STYLE_GROUPS_AUTHOR_PRIMARY_TYPE + "=?,"
                    + DBKey.STYLE_GROUPS_SERIES_SHOW_UNDER_EACH + "=?,"
                    + DBKey.STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH + "=?,"
                    + DBKey.STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH + "=?,"

                    + DBKey.STYLE_EXP_LEVEL + "=?,"
                    + DBKey.STYLE_ROW_USES_PREF_HEIGHT + "=?,"
                    + DBKey.STYLE_AUTHOR_SORT_BY_GIVEN_NAME + "=?,"
                    + DBKey.STYLE_AUTHOR_SHOW_BY_GIVEN_NAME + "=?,"
                    + DBKey.STYLE_TEXT_SCALE + "=?,"
                    + DBKey.STYLE_COVER_SCALE + "=?,"
                    + DBKey.STYLE_LIST_HEADER + "=?,"
                    + DBKey.STYLE_DETAILS_SHOW_FIELDS + "=?,"
                    + DBKey.STYLE_LIST_SHOW_FIELDS + "=?"

                    + " WHERE " + DBKey.STYLE_UUID + "=?")) {

                uuids.forEach(uuid -> {
                    final SharedPreferences stylePrefs = context
                            .getSharedPreferences(uuid, Context.MODE_PRIVATE);

                    int c = 0;

                    stmt.bindString(++c, stylePrefs.getString(
                            StyleDataStore.PK_NAME, null));

                    stmt.bindString(++c, stylePrefs.getString(
                            StyleDataStore.PK_GROUPS, null));

                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            StyleDataStore.PK_GROUPS_AUTHOR_SHOW_BOOKS_UNDER_EACH, false) ? 1 : 0);

                    stmt.bindLong(++c, StyleDataStore.convert(
                            stylePrefs.getStringSet(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE,
                                                    null),
                            Author.TYPE_UNKNOWN));

                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            StyleDataStore.PK_GROUPS_SERIES_SHOW_BOOKS_UNDER_EACH, false) ? 1 : 0);
                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            StyleDataStore.PK_GROUPS_PUBLISHER_SHOW_BOOKS_UNDER_EACH,
                            false) ? 1 : 0);
                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            StyleDataStore.PK_GROUPS_BOOKSHELF_SHOW_BOOKS_UNDER_EACH,
                            false) ? 1 : 0);

                    stmt.bindLong(++c, stylePrefs.getInt(
                            StyleDataStore.PK_EXPANSION_LEVEL, 1));
                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            StyleDataStore.PK_GROUP_ROW_HEIGHT, true) ? 1 : 0);

                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST, false) ? 1 : 0);

                    stmt.bindLong(++c, stylePrefs.getBoolean(
                            StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST, false) ? 1 : 0);


                    stmt.bindLong(++c, stylePrefs.getInt(
                            StyleDataStore.PK_TEXT_SCALE, Style.DEFAULT_TEXT_SCALE));
                    stmt.bindLong(++c, stylePrefs.getInt(
                            StyleDataStore.PK_COVER_SCALE, Style.DEFAULT_COVER_SCALE));

                    stmt.bindLong(++c, StyleDataStore.convert(
                            stylePrefs.getStringSet(StyleDataStore.PK_LIST_HEADER, null),
                            BooklistHeader.BITMASK_ALL));

                    final long detailFields =
                            (stylePrefs.getBoolean(StyleDataStore.PK_DETAILS_SHOW_COVER[0], true)
                             ? FieldVisibility.getBitValue(
                                    FieldVisibility.COVER[0]) : 0)
                            | (stylePrefs.getBoolean(StyleDataStore.PK_DETAILS_SHOW_COVER[1], true)
                               ? FieldVisibility.getBitValue(
                                    FieldVisibility.COVER[1]) : 0);

                    stmt.bindLong(++c, detailFields);

                    final long listFields =
                            FieldVisibility.getBitValue(DBKey.FK_SERIES)

                            | (stylePrefs.getBoolean(StyleDataStore.PK_LIST_SHOW_COVERS, true)
                               ? FieldVisibility.getBitValue(FieldVisibility.COVER[0]) : 0)
                            | (stylePrefs.getBoolean(StyleDataStore.PK_LIST_SHOW_AUTHOR, true)
                               ? FieldVisibility.getBitValue(DBKey.FK_AUTHOR) : 0)
                            | (stylePrefs.getBoolean(StyleDataStore.PK_LIST_SHOW_PUBLISHER, true)
                               ? FieldVisibility.getBitValue(DBKey.FK_PUBLISHER) : 0)
                            | (stylePrefs.getBoolean(StyleDataStore.PK_LIST_SHOW_PUB_DATE, true)
                               ? FieldVisibility.getBitValue(DBKey.BOOK_PUBLICATION__DATE) : 0)
                            | (stylePrefs.getBoolean(StyleDataStore.PK_LIST_SHOW_FORMAT, true)
                               ? FieldVisibility.getBitValue(DBKey.FORMAT) : 0)
                            | (stylePrefs.getBoolean(StyleDataStore.PK_LIST_SHOW_LOCATION, true)
                               ? FieldVisibility.getBitValue(DBKey.LOCATION) : 0)
                            | (stylePrefs.getBoolean(StyleDataStore.PK_LIST_SHOW_RATING, true)
                               ? FieldVisibility.getBitValue(DBKey.RATING) : 0)
                            | (stylePrefs.getBoolean(StyleDataStore.PK_LIST_SHOW_BOOKSHELVES, true)
                               ? FieldVisibility.getBitValue(DBKey.FK_BOOKSHELF) : 0)
                            | (stylePrefs.getBoolean(StyleDataStore.PK_LIST_SHOW_ISBN, true)
                               ? FieldVisibility.getBitValue(DBKey.BOOK_ISBN) : 0);

                    stmt.bindLong(++c, listFields);

                    stmt.bindString(++c, uuid);
                    stmt.executeUpdateDelete();

                    context.deleteSharedPreferences(uuid);
                });
            }

        }
        if (oldVersion < 20) {
            TBL_BOOKS.alterTableAddColumns(db, DBDefinitions.DOM_AUTO_UPDATE);
        }
        if (oldVersion < 21) {
            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(context);

            // convert the old bit ids to the preference-key of the engine
            Stream.of("search.siteOrder.data",
                      "search.siteOrder.covers",
                      "search.siteOrder.alted")
                  .forEach(key -> {
                      final String order = Arrays
                              .stream(prefs.getString(key, "").split(","))
                              .map(i -> {
                                  switch (i) {
                                      case "1":
                                          return "googlebooks";
                                      case "2":
                                          return "amazon";
                                      case "4":
                                          return "librarything";
                                      case "8":
                                          return "goodreads";
                                      case "16":
                                          return "isfdb";
                                      case "32":
                                          return "openlibrary";
                                      case "64":
                                          return "kbnl";
                                      case "128":
                                          return "stripinfo";
                                      case "256":
                                          return "lastdodo";
                                      default:
                                          return "";
                                  }
                              })
                              .collect(Collectors.joining(","));
                      prefs.edit().putString(key, order).apply();
                  });
        }
        if (oldVersion < 22) {
            // remove builtin style ID_DEPRECATED_1
            db.execSQL("DELETE FROM " + TBL_BOOKLIST_STYLES.getName() + " WHERE _id=-2");
        }
        if (oldVersion < 23) {
            TBL_AUTHORS.alterTableAddColumns(db, DBDefinitions.DOM_AUTHOR_IS_PSEUDONYM_FOR);
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
        initCollation(db);

        // Turn ON foreign key support so that CASCADE etc. works.
        // This is the same as db.execSQL("PRAGMA foreign_keys = ON");
        db.setForeignKeyConstraintsEnabled(true);
    }

    /**
     * MUST be called as the first line from {@link #onCreate(SQLiteDatabase)},
     * {@link #onUpgrade(SQLiteDatabase, int, int)} nd {@link #onOpen(SQLiteDatabase)}
     * to cover all possible startup paths.
     */
    private void initCollation(@NonNull final SQLiteDatabase db) {
        if (sIsCollationCaseSensitive == null) {
            sIsCollationCaseSensitive = collationIsCaseSensitive(db);
        }
    }

    /**
     * This method removes all keys which were declared obsolete.
     *
     * @param context Current context
     */
    private void removeObsoleteKeys(@NonNull final Context context) {

        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        final SharedPreferences.Editor editor = prefs.edit();

        prefs.getAll()
             .keySet()
             .stream()
             .filter(key -> key.startsWith("style.booklist."))
             .forEach(editor::remove);

        editor.remove("tips.tip.BOOKLIST_STYLES_EDITOR")
              .remove("tips.tip.BOOKLIST_STYLE_GROUPS")
              .remove("tips.tip.BOOKLIST_STYLE_PROPERTIES")
              .remove("tips.tip.booklist_style_menu")

              .remove("BookList.Style.Preferred.Order")
              .remove("bookList.style.preferred.order")
              .remove("BookList.Style.Current")

              .remove("booklist.top.rowId")
              .remove("booklist.top.row")
              .remove("booklist..top.row")
              .remove("booklist.top.offset")
              .remove("booklist..top.offset")

              .remove("calibre.last.sync.date")
              .remove("camera.id.scan.barcode")
              .remove("compat.booklist.mode")
              .remove("compat.image.cropper.viewlayertype")
              .remove("edit.book.tab.authSer")
              .remove("edit.book.tab.nativeId")
              .remove("goodreads.enabled")
              .remove("goodreads.showMenu")
              .remove("goodreads.search.collect.genre")
              .remove("goodreads.AccessToken.Token")
              .remove("goodreads.AccessToken.Secret")
              .remove("librarything.dev_key")
              .remove("scanner.preferred")
              .remove("search.form.advanced")
              .remove("search.site.goodreads.data.enabled")
              .remove("search.site.goodreads.covers.enabled")
              .remove("startup.lastVersion")
              .remove("tmp.edit.book.tab.authSer")
              .remove("ui.messages.use")

              .remove("fields.visibility.bookshelf")
              .remove("fields.visibility.read")
              .commit();
    }
}
