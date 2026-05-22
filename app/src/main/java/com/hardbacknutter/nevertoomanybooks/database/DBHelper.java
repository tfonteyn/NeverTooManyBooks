/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupActivity;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.core.database.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.core.database.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.core.database.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.core.database.UpgradeFailedException;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.BookshelfDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.CalibreCustomFieldDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.IdentifierDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.StyleDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.TagMappingDaoImpl;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildIndexesTask;
import com.hardbacknutter.nevertoomanybooks.database.updates.Upgrade;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * {@link SQLiteOpenHelper} for the main database.
 * Uses the application context.
 * <p>
 * ENHANCE: dump android sqlite and replace with
 * https://sqlite.org/android/doc/trunk/www/index.wiki
 * https://sqlite.org/android/doc/trunk/www/usage.wiki
 * https://android.googlesource.com/platform/external/sqlite/+/refs/heads/main
 */
public class DBHelper
        extends SQLiteOpenHelper {

    /**
     * Previous versions.
     * v3.2.0: 22
     * v4.0.0: 23
     * v4.4.0: 24
     * v4.5.0: 25
     * v5.0.0: 26
     * v5.1.0: 27
     * v5.2.0: 29
     * v5.2.2: 30
     * v5.3.0: 31
     * v5.5.0: 32
     * v5.5.1: 33
     * v5.5.4: 34
     * v7.0.0: 35
     * v7.0.3: 36
     * v7.1.0: 38
     * v7.2.0: 39
     * v7.3.0: 40
     * v7.4.0: 41
     * v7.6.0: 42
     * v7.7.0: 43
     * v7.8.2: 44
     * v7.8.3: 45
     * v7.10.0: 46
     * (47 was dev only)
     * v7.11.0: 48
     * v7.12.0: 49
     * v7.13.0: 50
     * v7.16.0: 51
     * v8.0.0: 52
     * <p>
     * Current version.
     */
    public static final int DATABASE_VERSION = 52;

    /** NEVER change this name. */
    public static final String DATABASE_NAME = "nevertoomanybooks.db";

    /** Log tag. */
    private static final String TAG = "DBHelper";

    /** The database prepared statement cache size (default 25, max 100). */
    private static final String PK_STARTUP_DB_STMT_CACHE_SIZE = "db.stmt.cache.size";
    /** Default 25, see SynchronizedDb javadoc. */
    private static final int DEFAULT_MINIMUM_STMT_CACHE_SIZE = 25;

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

    /** Always use {@link #getCollation(SQLiteDatabase)} to access. */
    @Nullable
    private static Boolean sIsCollationCaseSensitive;

    @IntRange(from = DEFAULT_MINIMUM_STMT_CACHE_SIZE, to = SQLiteDatabase.MAX_SQL_CACHE_SIZE)
    private final int stmtCacheSize;

    /** DO NOT USE INSIDE THIS CLASS! ONLY FOR USE BY CLIENTS CALLING {@link #getDb()}. */
    @Nullable
    private SynchronizedDb synchronizedDb;

    /**
     * Constructor.
     *
     * @param context Application context
     */
    public DBHelper(@NonNull final Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, CURSOR_FACTORY, DATABASE_VERSION);

        final int size = ServiceLocator.getInstance().getSharedPreferences()
                                       .getInt(PK_STARTUP_DB_STMT_CACHE_SIZE,
                                               DEFAULT_MINIMUM_STMT_CACHE_SIZE);
        stmtCacheSize = MathUtils.clamp(size, DEFAULT_MINIMUM_STMT_CACHE_SIZE,
                                        SQLiteDatabase.MAX_SQL_CACHE_SIZE);
    }

    /**
     * This method should only be called at the *END* of {@link #onUpgrade}.
     * <p>
     * (re)Creates the indexes as defined on the tables.
     *
     * @param db Underlying database
     */
    private static void recreateIndices(@NonNull final SQLiteDatabase db) {
        // Delete all indices.
        // We read the index names from the database, so we can delete
        // indexes which were removed from the TableDefinition objects.
        try (Cursor current = db.rawQuery(SQL_GET_INDEX_NAMES, null)) {
            while (current.moveToNext()) {
                final String indexName = current.getString(0);
                //noinspection CheckStyle
                try {
                    db.execSQL("DROP INDEX " + indexName);
                } catch (@NonNull final SQLException e) {
                    LoggerFactory.getLogger().e(TAG, e);
                    throw e;
                } catch (@NonNull final RuntimeException e) {
                    LoggerFactory.getLogger()
                                 .e(TAG, e, "DROP INDEX failed: " + indexName);
                }
            }
        }

        // now recreate
        for (final TableDefinition table : DBDefinitions.ALL_TABLES.values()) {
            table.createIndices(db, getCollation(db));
        }

        db.execSQL("analyze");
    }

    private static boolean getCollation(@NonNull final SQLiteDatabase db) {
        synchronized (DBHelper.class) {
            if (sIsCollationCaseSensitive == null) {
                sIsCollationCaseSensitive = collationIsCaseSensitive(db);
            }
        }
        return sIsCollationCaseSensitive;
    }

    /**
     * Method to detect if collation implementations are case-sensitive.
     * This was built because ICS broke the UNICODE collation (making it case-sensitive (CS))
     * and we needed to check for collation case-sensitivity.
     * <p>
     * This bug was introduced in ICS and present in 4.0-4.0.3, at least.
     *
     * @param db Underlying database
     *
     * @return This method is supposed to return {@code false} in normal circumstances.
     */
    private static boolean collationIsCaseSensitive(@NonNull final SQLiteDatabase db) {
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
                LoggerFactory.getLogger().w(
                        TAG,
                        "==================== CASE SENSITIVE COLLATION ====================");
            }
            return cs;

        } catch (@NonNull final SQLException e) {
            LoggerFactory.getLogger().e(TAG, e);
            throw e;
        } finally {
            try {
                db.execSQL(dropTable);
            } catch (@NonNull final SQLException e) {
                LoggerFactory.getLogger().e(TAG, e);
            }
        }
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
                                                    getCollation(getWritableDatabase()),
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
     * Wrapper to allow
     * {@link RebuildIndexesTask}.
     * safe access to the database.
     */
    public void recreateIndices() {
        final SynchronizedDb db = getDb();
        final Synchronizer.SyncLock txLock = db.beginTransaction(true);
        try {
            // It IS safe here to get the underlying database, as we're in a SyncLock.
            recreateIndices(db.getSQLiteDatabase());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction(txLock);
        }
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
     * <strong>WARNING: do NOT use SynchronizedDb here!
     * This implies: do NOT get DAOs from the ServiceLocator!</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onCreate(@NonNull final SQLiteDatabase db) {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        // Create all the app & user data tables in the correct dependency order
        TableDefinition.onCreate(db, getCollation(db), DBDefinitions.ALL_TABLES.values());

        StyleDaoImpl.onPostCreate(db);
        CalibreCustomFieldDaoImpl.onPostCreate(db);

        BookshelfDaoImpl.onPostCreate(context, db);
        IdentifierDaoImpl.onPostCreate(context, db);
        TagMappingDaoImpl.onPostCreate(db);

        //IMPORTANT: withDomainConstraints MUST BE false (FTS columns don't use a type/constraints)
        DBDefinitions.TBL_FTS_BOOKS.create(db, false);

        Triggers.create(db);
    }

    /**
     * <strong>REMINDER: foreign key constraints are DISABLED here.</strong>
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase db,
                          final int oldVersion,
                          final int newVersion) {

        // reminder: do NOT get DAOs!
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final Context context = serviceLocator.getLocalizedAppContext();

        if (oldVersion < 23) {
            throw new UpgradeFailedException(
                    context.getString(R.string.error_upgrade_not_supported, "4.0.0"));
        }

        final StartupActivity startup = StartupActivity.getActiveActivity();
        if (startup != null) {
            startup.onProgress(context.getString(R.string.progress_msg_upgrading));
        }

        final Upgrade upgrade = new Upgrade(context, db);

        // take a backup before modifying the database
        if (oldVersion != newVersion) {
            upgrade.backup(serviceLocator.getUpgradesDir(),
                           DB_UPGRADE_FILE_PREFIX + "-" + oldVersion + '-' + newVersion);
        }

        upgrade.upgrade(oldVersion);

        // Rebuild all indices
        recreateIndices(db);

        // Rebuild all triggers
        Triggers.create(db);
    }

    @Override
    public void onOpen(@NonNull final SQLiteDatabase db) {
        // Turn ON foreign key support so that CASCADE etc. works.
        // This is the same as db.execSQL("PRAGMA foreign_keys = ON");
        db.setForeignKeyConstraintsEnabled(true);
    }
}
