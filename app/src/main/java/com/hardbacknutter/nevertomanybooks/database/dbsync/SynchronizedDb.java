/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.database.dbsync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteClosable;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertomanybooks.debug.Logger;

/**
 * Database wrapper class that performs thread synchronization on all operations.
 */
public class SynchronizedDb {

    private static final String ERROR_UPDATE_INSIDE_SHARED_TX = "Update inside shared TX";
    private static Boolean sIsCollationCaseSensitive;
    /** Underlying database. */
    @NonNull
    private final SQLiteDatabase mSqlDb;
    /** Sync object to use. */
    @NonNull
    private final Synchronizer mSync;
    /** Factory object to create the custom cursor. Can not be static because it needs mSync. */
    private final SynchronizedCursorFactory mCursorFactory = new SynchronizedCursorFactory();
    /** Currently held transaction lock, if any. */
    @Nullable
    private Synchronizer.SyncLock mTxLock;

    /**
     * Constructor. Use of this method is not recommended. It is better to use
     * the methods that take a {@link SQLiteOpenHelper} object since opening the database
     * may block another thread, or vice versa.
     *
     * @param db   Underlying database
     * @param sync Synchronizer to use
     */
    public SynchronizedDb(@NonNull final SQLiteDatabase db,
                          @NonNull final Synchronizer sync) {
        mSqlDb = db;
        mSync = sync;
    }

    /**
     * Constructor.
     *
     * @param helper SQLiteOpenHelper to open underlying database
     * @param sync   Synchronizer to use
     */
    public SynchronizedDb(@NonNull final SQLiteOpenHelper helper,
                          @NonNull final Synchronizer sync) {
        mSync = sync;
        mSqlDb = openWithRetries(helper);
    }

    /**
     * Constructor.
     *
     * @param helper            SQLiteOpenHelper to open underlying database
     * @param sync              Synchronizer to use
     * @param preparedStmtCache the number or prepared statements to cache.
     *                          The javadoc for setMaxSqlCacheSize says the default is 10,
     *                          but if you follow the source code, you end up in
     *                          android.database.sqlite.SQLiteDatabaseConfiguration
     *                          where the default is in fact 25!
     *                          Do NOT set the size to less then 25.
     */
    public SynchronizedDb(@NonNull final SQLiteOpenHelper helper,
                          @NonNull final Synchronizer sync,
                          final int preparedStmtCache) {
        mSync = sync;
        mSqlDb = openWithRetries(helper);

        // only set when bigger then default
        if ((preparedStmtCache > 25)
            && (preparedStmtCache < SQLiteDatabase.MAX_SQL_CACHE_SIZE)) {
            mSqlDb.setMaxSqlCacheSize(preparedStmtCache);
        }
    }

    /**
     * DEBUG.
     * <p>
     * Utility routine, purely for debugging ref count issues (mainly Android 2.1).
     *
     * @param message Message to display (relating to context)
     * @param db      Database object
     **/
    @SuppressWarnings({"JavaReflectionMemberAccess"})
    public static void printRefCount(@Nullable final String message,
                                     @NonNull final SQLiteDatabase db) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_SYNC) {
            System.gc();
            try {
                Field f = SQLiteClosable.class.getDeclaredField("mReferenceCount");
                f.setAccessible(true);
                int refs = (Integer) f.get(db);
                if (message != null) {
                    Logger.debug(SynchronizedDb.class, "printRefCount",
                                 "DBRefs (" + message + "): " + refs);
//                    if (refs < 100) {
//                        Logger.debug(SynchronizedDb.class, "printRefCount",
//                                     "DBRefs (" + msg + "): " + refs + " <-- TOO LOW (< 100)!");
//                    } else if (refs < 1001) {
//                        Logger.debug(SynchronizedDb.class, "printRefCount",
//                                     "DBRefs (" + msg + "): " + refs + " <-- TOO LOW (< 1000)!");
//                    } else {
//                        Logger.debug(SynchronizedDb.class, "printRefCount",
//                                     "DBRefs (" + msg + "): " + refs);
//                    }

                }
            } catch (@NonNull final NoSuchFieldException | IllegalAccessException e) {
                // already in debug block
                Logger.error(SynchronizedDb.class, e);
            }
        }
    }

    /**
     * Call the passed database opener with retries to reduce risks of access conflicts
     * causing crashes.
     * <p>
     * About the SQLite version:
     * <a href="https://developer.android.com/reference/android/database/sqlite/package-summary">
     * https://developer.android.com/reference/android/database/sqlite/package-summary</a>
     * API 28   3.22.0
     * API 27   3.19.4
     * API 26   3.18.2
     * API 25   3.9.2
     * API 24   3.9.2
     * API 23   3.8.10.2 <=
     * API 21   3.8.6
     * API 11   3.7
     * API 8    3.6
     * API 3    3.5
     * API 1    3.4
     * But some device manufacturers include different versions of SQLite on their devices.
     *
     * @param opener SQLiteOpenHelper interface
     *
     * @return a writable database
     */
    @NonNull
    private SQLiteDatabase openWithRetries(@NonNull final SQLiteOpenHelper opener) {
        // 10ms
        int wait = 10;
        // 2^10 * 10ms = 10.24sec (actually 2x that due to total wait time)
        int retriesLeft = 10;

        do {
            Synchronizer.SyncLock exclusiveLock = mSync.getExclusiveLock();
            try {
                SQLiteDatabase db = opener.getWritableDatabase();
                if (BuildConfig.DEBUG /* always */) {
                    Logger.debug(this, "openWithRetries",
                                 db.getPath(), "retriesLeft=" + retriesLeft);
                    debugDumpInfo(db);
                }
                return db;
            } catch (@NonNull final RuntimeException e) {
                exclusiveLock.unlock();
                exclusiveLock = null;
                if (retriesLeft == 0) {
                    throw new RuntimeException("Unable to open database, retries exhausted", e);
                }
                try {
                    Thread.sleep(wait);
                    // Decrement tries
                    retriesLeft--;
                    // Wait longer next time
                    wait *= 2;
                } catch (@NonNull final InterruptedException e1) {
                    throw new RuntimeException("Unable to open database, interrupted", e1);
                }
            } finally {
                if (exclusiveLock != null) {
                    exclusiveLock.unlock();
                }
            }
        } while (true);
    }

    /** DEBUG usage. */
    private void debugDumpInfo(@NonNull final SQLiteDatabase db) {
        String[] sql = {"select sqlite_version() AS sqlite_version",
                        "PRAGMA encoding",
                        "PRAGMA collation_list",
                        };
        for (String s : sql) {
            try (Cursor cursor = db.rawQuery(s, null)) {
                if (cursor.moveToNext()) {
                    Logger.debug(this, "debugDumpInfo",
                                 s + " => " + cursor.getString(0));
                }
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     */
    @NonNull
    public Cursor query(@NonNull final String table,
                        @NonNull final String[] columns,
                        @NonNull final String selection,
                        @Nullable final String[] selectionArgs,
                        @Nullable final String groupBy,
                        @Nullable final String having,
                        @Nullable final String orderBy) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock == null) {
            txLock = mSync.getSharedLock();
        }

        try {
            return mSqlDb.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     *
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insert(@NonNull final String table,
                       @SuppressWarnings("SameParameterValue")
                       @Nullable final String nullColumnHack,
                       @NonNull final ContentValues cv) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.exclusive) {
                throw new TransactionException(ERROR_UPDATE_INSIDE_SHARED_TX);
            }
        } else {
            txLock = mSync.getExclusiveLock();
        }

        // reminder: insert does not throw exceptions for the actual insert.
        // but it can throw other exceptions.
        try {
            long id = mSqlDb.insert(table, nullColumnHack, cv);
            if (id == -1) {
                Logger.warnWithStackTrace(this, "Insert failed");
            }
            return id;
        } catch (@NonNull final SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(this, e);
            throw e;
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     * <p>
     * <b>Note:</b> as far as I can tell, the Statement behind this call is not cached.
     * So this is fine for single-action inserts, but not for loops (should use a prepared stmt).
     *
     * @return the number of rows affected
     */
    public int update(@NonNull final String table,
                      @NonNull final ContentValues cv,
                      @NonNull final String whereClause,
                      @Nullable final String[] whereArgs) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.exclusive) {
                throw new TransactionException(ERROR_UPDATE_INSIDE_SHARED_TX);
            }
        } else {
            txLock = mSync.getExclusiveLock();
        }

        // reminder: update does not throw exceptions for the actual update.
        // but it can throw other exceptions.
        try {
            return mSqlDb.update(table, cv, whereClause, whereArgs);
        } catch (@NonNull final SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(this, e);
            throw e;
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     * <p>
     * <b>Note:</b> as far as I can tell, the Statement behind this call is not cached.
     * So this is fine for single-action inserts, but not for loops (should use a prepared stmt).
     *
     * @return the number of rows affected if a whereClause is passed in, 0
     * otherwise. To remove all rows and get a count pass "1" as the
     * whereClause.
     */
    @SuppressWarnings("UnusedReturnValue")
    public int delete(@NonNull final String table,
                      @Nullable final String whereClause,
                      @Nullable final String[] whereArgs) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.exclusive) {
                throw new TransactionException(ERROR_UPDATE_INSIDE_SHARED_TX);
            }
        } else {
            txLock = mSync.getExclusiveLock();
        }

        // reminder: delete does not throw exceptions for the actual delete.
        // but it can throw other exceptions.
        try {
            return mSqlDb.delete(table, whereClause, whereArgs);
        } catch (@NonNull final SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(this, e);
            throw e;
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     * <p>
     * lint says this cursor is not always closed.
     * 2019-01-14: the only place it's not closed is in
     * {@link com.hardbacknutter.nevertomanybooks.searches.SearchSuggestionProvider}
     * where it seems not possible to close it ourselves.
     *
     * @return the cursor
     */
    @NonNull
    public SynchronizedCursor rawQuery(@NonNull final String sql,
                                       @Nullable final String[] selectionArgs) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock == null) {
            txLock = mSync.getSharedLock();
        }

        try {
            return (SynchronizedCursor) mSqlDb.rawQueryWithFactory(mCursorFactory, sql,
                                                                   selectionArgs, "");
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Wrapper for underlying database method.
     * It is recommended that custom cursors subclass SynchronizedCursor.
     * <p>
     * Runs the provided SQL and returns a cursor over the result set.
     *
     * @param cursorFactory the cursor factory to use, or {@code null} for the default factory
     * @param sql           the SQL query. The SQL string must not be ; terminated
     * @param selectionArgs You may include ?s in where clause in the query,
     *                      which will be replaced by the values from selectionArgs. The
     *                      values will be bound as Strings.
     * @param editTable     the name of the first table, which is editable
     *
     * @return A {@link Cursor} object, which is positioned before the first entry. Note that
     * {@link Cursor}s are not synchronized, see the documentation for more details.
     */
    @NonNull
    public Cursor rawQueryWithFactory(@NonNull final SQLiteDatabase.CursorFactory cursorFactory,
                                      @NonNull final String sql,
                                      @Nullable final String[] selectionArgs,
                                      @NonNull final String editTable) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock == null) {
            txLock = mSync.getSharedLock();
        }
        try {
            return mSqlDb.rawQueryWithFactory(cursorFactory, sql, selectionArgs, editTable);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     */
    public void execSQL(@NonNull final String sql) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_SYNC_EXEC_SQL) {
            Logger.debugEnter(this, "execSQL", sql);
        }

        try {
            if (mTxLock != null) {
                if (mTxLock.getType() != Synchronizer.LockType.exclusive) {
                    throw new TransactionException(ERROR_UPDATE_INSIDE_SHARED_TX);
                }
                mSqlDb.execSQL(sql);
            } else {
                Synchronizer.SyncLock txLock = mSync.getExclusiveLock();
                try {
                    mSqlDb.execSQL(sql);
                } finally {
                    txLock.unlock();
                }
            }
        } catch (@NonNull final SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(this, e, sql);
            throw e;
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     */
    @NonNull
    public SynchronizedStatement compileStatement(@NonNull final String sql) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.exclusive) {
                throw new TransactionException("Compile inside shared TX");
            }
        } else {
            txLock = mSync.getExclusiveLock();
        }

        try {
            return new SynchronizedStatement(this, sql);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * DO NOT CALL THIS UNLESS YOU REALLY NEED TO. DATABASE ACCESS SHOULD GO THROUGH THIS CLASS.
     *
     * @return the underlying SQLiteDatabase object.
     */
    @NonNull
    public SQLiteDatabase getUnderlyingDatabase() {
        return mSqlDb;
    }

    /**
     * Run 'analyse' on the whole database.
     */
    public void analyze() {
        // Don't do VACUUM -- it's a complete rebuild
        //execSQL("vacuum");
        try {
            execSQL("analyze");
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e, "Analyze failed");
        }
    }

    /**
     * Run 'analyse' on a table.
     *
     * @param table to analyse.
     */
    public void analyze(@NonNull final TableDefinition table) {
        execSQL("analyze " + table);
    }

    /**
     * Wrapper
     *
     * @return {@code true} if the current thread is in a transaction.
     */
    public boolean inTransaction() {
        return mSqlDb.inTransaction();
    }

    /**
     * Locking-aware wrapper for underlying database method.
     *
     * @param isUpdate Indicates if updates will be done in TX
     *
     * @return the lock
     */
    @NonNull
    public Synchronizer.SyncLock beginTransaction(final boolean isUpdate) {
        Synchronizer.SyncLock txLock;
        if (isUpdate) {
            txLock = mSync.getExclusiveLock();
        } else {
            txLock = mSync.getSharedLock();
        }
        // We have the lock, but if the real beginTransaction() throws an exception,
        // we need to release the lock.
        try {
            // If we have a lock, and there is currently a TX active...die
            // Note: because we get a lock, two 'isUpdate' transactions will
            // block, this is only likely to happen with two TXs on the current thread
            // or two non-update TXs on different thread.
            // ENHANCE: Consider allowing nested TXs
            // ENHANCE: Consider returning NULL if TX active and handle null locks...
            if (mTxLock == null) {
                mSqlDb.beginTransaction();
            } else {
                Logger.warnWithStackTrace(this,
                                          "Starting a transaction when one is already started");
            }
        } catch (@NonNull final RuntimeException e) {
            txLock.unlock();
            throw new TransactionException(
                    "Unable to start database transaction: " + e.getLocalizedMessage(), e);
        }
        mTxLock = txLock;
        return txLock;
    }

    /**
     * Locking-aware wrapper for underlying database method.
     *
     * @param txLock Lock returned from BeginTransaction().
     */
    public void endTransaction(@NonNull final Synchronizer.SyncLock txLock) {
        if (mTxLock == null) {
            throw new TransactionException(
                    "Ending a transaction when none is started");
        }
        if (!mTxLock.equals(txLock)) {
            throw new TransactionException(
                    "Ending a transaction with wrong transaction lock");
        }

        try {
            mSqlDb.endTransaction();
        } finally {
            // Clear mTxLock before unlocking so another thread does not
            // see the old lock when it gets the lock
            mTxLock = null;
            txLock.unlock();
        }
    }

    /**
     * Wrapper for underlying database method.
     */
    public void setTransactionSuccessful() {
        mSqlDb.setTransactionSuccessful();
    }

    /**
     * Check if the collation we use is case sensitive.
     * ; bug introduced in ICS was to make UNICODE not CI.
     * Due to bugs in other language sorting, we are now forced to use a different
     * collation anyway, but we still check if it is CI.
     *
     * @return {@code true} if case-sensitive (i.e. up to "you" to add lower/upper calls)
     */
    public boolean isCollationCaseSensitive() {
        if (sIsCollationCaseSensitive == null) {
            sIsCollationCaseSensitive = collationIsCaseSensitive();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_SYNC) {
                Logger.debug(this, "isCollationCaseSensitive",
                             sIsCollationCaseSensitive);
            }
        }
        return sIsCollationCaseSensitive;
    }

    /**
     * @return the underlying Synchronizer object.
     */
    @NonNull
    Synchronizer getSynchronizer() {
        return mSync;
    }

    /**
     * Method to detect if collation implementations are case sensitive.
     * This was built because ICS broke the UNICODE collation (making it case sensitive (CS))
     * and we needed to check for collation case-sensitivity.
     * <p>
     * This bug was introduced in ICS and present in 4.0-4.0.3, at least.
     * <p>
     * FIXME: Generalize code to allow for arbitrary changes to choice of collation.
     */
    private boolean collationIsCaseSensitive() {
        String dropTable = "DROP TABLE IF EXISTS collation_cs_check";
        // Drop and create table
        mSqlDb.execSQL(dropTable);
        mSqlDb.execSQL("CREATE TABLE collation_cs_check (t text, i integer)");
        try {
            // Row that *should* be returned first assuming 'a' <=> 'A'
            mSqlDb.execSQL("INSERT INTO collation_cs_check VALUES('a', 1)");
            // Row that *should* be returned second assuming 'a' <=> 'A';
            // will be returned first if 'A' < 'a'.
            mSqlDb.execSQL("INSERT INTO collation_cs_check VALUES('A', 2)");

            String s;
            try (Cursor c = mSqlDb.rawQuery("SELECT t,i FROM collation_cs_check"
                                            + " ORDER BY t " + DAO.COLLATION + ",i",
                                            null)) {
                c.moveToFirst();
                s = c.getString(0);
            }
            return !"a".equals(s);
        } catch (@NonNull final SQLException e) {
            // bad sql is a developer issue... die!
            Logger.error(this, e);
            throw e;
        } finally {
            try {
                mSqlDb.execSQL(dropTable);
            } catch (@NonNull final SQLException e) {
                Logger.error(this, e);
            }
        }
    }

    /**
     * Factory for Synchronized Cursor objects.
     * This can be subclassed by other Cursor implementations.
     */
    class SynchronizedCursorFactory
            implements SQLiteDatabase.CursorFactory {

        @Override
        @NonNull
        public SynchronizedCursor newCursor(@NonNull final SQLiteDatabase db,
                                            @NonNull final SQLiteCursorDriver masterQuery,
                                            @NonNull final String editTable,
                                            @NonNull final SQLiteQuery query) {
            return new SynchronizedCursor(masterQuery, editTable, query, mSync);
        }
    }
}
