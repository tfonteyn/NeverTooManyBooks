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
package com.hardbacknutter.nevertoomanybooks.database.dbsync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.SearchSuggestionProvider;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Database wrapper class that performs thread synchronization on all operations.
 * <p>
 * After getting a question "why?":
 * <p>
 * We use syncing to allow simultaneous access by multiple threads
 * (including e.g. goodreads sync task).
 * This is possibly overkill since transactions where added everywhere.
 * <p>
 * See {@link Synchronizer} for more details.
 *
 * <p>
 * About the SQLite version:
 * <a href="https://developer.android.com/reference/android/database/sqlite/package-summary">
 * package-summary</a>
 * <p>
 * API 28   3.22.0
 * API 27   3.19.4
 * API 26   3.18.2
 * API 25   3.9.2
 * API 24   3.9.2
 * <p>
 * But some device manufacturers include different versions of SQLite on their devices.
 */
public class SynchronizedDb {

    /** Log tag. */
    private static final String TAG = "SynchronizedDb";

    /** Instance map. */
    private static final Map<Synchronizer, SynchronizedDb> DB_MAP = new HashMap<>(2);

    /** Underlying (and open for writing) database. */
    @NonNull
    private final SQLiteDatabase mSqlDb;

    /** Sync object to use. */
    @NonNull
    private final Synchronizer mSynchronizer;

    /** Factory object to create the custom cursor. */
    private final SQLiteDatabase.CursorFactory mCursorFactory = (db, mq, et, q) ->
            new SynchronizedCursor(mq, et, q, getSynchronizer());

    /** Currently held transaction lock, if any. */
    @Nullable
    private Synchronizer.SyncLock mTxLock;

    /**
     * Constructor. ONLY to be used by {@link DBHelper#onCreate} and {@link DBHelper#onUpgrade}
     * where we have an already open database we need to wrap.
     *
     * @param synchronizer Synchronizer to use
     * @param db           Underlying database
     */
    public SynchronizedDb(@NonNull final Synchronizer synchronizer,
                          @NonNull final SQLiteDatabase db) {
        mSynchronizer = synchronizer;
        mSqlDb = db;
    }

    /**
     * Private Constructor. Use {@link #getInstance(Synchronizer, SQLiteOpenHelper)} instead.
     *
     * @param synchronizer     Synchronizer to use
     * @param sqLiteOpenHelper SQLiteOpenHelper to open the underlying database
     */
    private SynchronizedDb(@NonNull final Synchronizer synchronizer,
                           @NonNull final SQLiteOpenHelper sqLiteOpenHelper) {
        mSynchronizer = synchronizer;
        mSqlDb = open(sqLiteOpenHelper);
    }

    /**
     * Use from DAO constructors.
     *
     * @param synchronizer     Synchronizer to use
     * @param sqLiteOpenHelper SQLiteOpenHelper to open the underlying database
     *
     * @return instance matching the passed synchronizer
     */
    @NonNull
    public static SynchronizedDb getInstance(@NonNull final Synchronizer synchronizer,
                                             @NonNull final SQLiteOpenHelper sqLiteOpenHelper) {
        synchronized (DB_MAP) {
            SynchronizedDb db = DB_MAP.get(synchronizer);
            if (db == null) {
                db = new SynchronizedDb(synchronizer, sqLiteOpenHelper);
                DB_MAP.put(synchronizer, db);
            }
            return db;
        }
    }

    /**
     * Use when it's 100% certain that we've been created before.
     *
     * @param synchronizer Synchronizer to use
     *
     * @return instance matching the passed synchronizer
     */
    @NonNull
    public static SynchronizedDb getInstance(@NonNull final Synchronizer synchronizer) {
        synchronized (DB_MAP) {
            return Objects.requireNonNull(DB_MAP.get(synchronizer));
        }
    }

//    /**
//     * Constructor.
//     *
//     * @param synchronizer      Synchronizer to use
//     * @param sqLiteOpenHelper  SQLiteOpenHelper to open the underlying database
//     * @param preparedStmtCache the number or prepared statements to cache.
//     *                          The javadoc for setMaxSqlCacheSize says the default is 10,
//     *                          but if you follow the source code, you end up in
//     *                          android.database.sqlite.SQLiteDatabaseConfiguration
//     *                          where the default is in fact 25!
//     */
//    public SynchronizedDb(@NonNull final Synchronizer synchronizer,
//                          @NonNull final SQLiteOpenHelper sqLiteOpenHelper,
//                          final int preparedStmtCache) {
//        this(synchronizer, sqLiteOpenHelper);
//
//        // only set when bigger than default
//        if ((preparedStmtCache > 25)
//            && (preparedStmtCache < SQLiteDatabase.MAX_SQL_CACHE_SIZE)) {
//            mSqlDb.setMaxSqlCacheSize(preparedStmtCache);
//        }
//    }

    /**
     * Open the actual database.
     *
     * @param sqLiteOpenHelper SQLiteOpenHelper interface
     *
     * @return a writable database
     */
    @NonNull
    private SQLiteDatabase open(@NonNull final SQLiteOpenHelper sqLiteOpenHelper) {
        final Synchronizer.SyncLock exclusiveLock = mSynchronizer.getExclusiveLock();
        try {
            return sqLiteOpenHelper.getWritableDatabase();
        } finally {
            exclusiveLock.unlock();
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    public long insert(@NonNull final String table,
                       @SuppressWarnings("SameParameterValue")
                       @Nullable final String nullColumnHack,
                       @NonNull final ContentValues cv) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LOCK_EXCLUSIVE) {
                throw new TransactionException(TransactionException.INSIDE_SHARED_TX);
            }
        } else {
            txLock = mSynchronizer.getExclusiveLock();
        }

        // reminder: insert does not throw exceptions for the actual insert.
        // but it can throw other exceptions.
        try {
            final long id = mSqlDb.insert(table, nullColumnHack, cv);
            if (id == -1) {
                Logger.error(App.getAppContext(), TAG, new Throwable(), "Insert failed"
                                                                        + "|table=" + table
                                                                        + "|cv=" + cv);
            }
            return id;
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     * <p>
     * <strong>Note:</strong> as far as I can tell, the Statement behind this call is not cached.
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
            if (mTxLock.getType() != Synchronizer.LOCK_EXCLUSIVE) {
                throw new TransactionException(TransactionException.INSIDE_SHARED_TX);
            }
        } else {
            txLock = mSynchronizer.getExclusiveLock();
        }

        // reminder: update does not throw exceptions for the actual update.
        // but it can throw other exceptions.
        try {
            return mSqlDb.update(table, cv, whereClause, whereArgs);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
     * <p>
     * <strong>Note:</strong> as far as I can tell, the Statement behind this call is not cached.
     * So this is fine for single-action deletes, but not for loops (should use a prepared stmt).
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
            if (mTxLock.getType() != Synchronizer.LOCK_EXCLUSIVE) {
                throw new TransactionException(TransactionException.INSIDE_SHARED_TX);
            }
        } else {
            txLock = mSynchronizer.getExclusiveLock();
        }

        // reminder: delete does not throw exceptions for the actual delete.
        // but it can throw other exceptions.
        try {
            return mSqlDb.delete(table, whereClause, whereArgs);
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
     * {@link SearchSuggestionProvider}
     * where it seems not possible to close it ourselves.
     *
     * @return the cursor
     */
    @NonNull
    public SynchronizedCursor rawQuery(@NonNull final String sql,
                                       @Nullable final String[] selectionArgs) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock == null) {
            txLock = mSynchronizer.getSharedLock();
        }

        try {
            return (SynchronizedCursor) mSqlDb.rawQueryWithFactory(mCursorFactory, sql,
                                                                   selectionArgs, null);
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
                                      @Nullable final String editTable) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock == null) {
            txLock = mSynchronizer.getSharedLock();
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
    @NonNull
    public SynchronizedStatement compileStatement(@NonNull final String sql) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LOCK_EXCLUSIVE) {
                throw new TransactionException(TransactionException.INSIDE_SHARED_TX);
            }
        } else {
            txLock = mSynchronizer.getExclusiveLock();
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
     * Locking-aware wrapper for underlying database method.
     */
    public void execSQL(@NonNull final String sql) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.DB_EXEC_SQL) {
            Log.d(TAG, "ENTER|execSQL|sql=" + sql);
        }

        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LOCK_EXCLUSIVE) {
                throw new TransactionException(TransactionException.INSIDE_SHARED_TX);
            }
            mSqlDb.execSQL(sql);
        } else {
            final Synchronizer.SyncLock txLock = mSynchronizer.getExclusiveLock();
            try {
                mSqlDb.execSQL(sql);
            } finally {
                txLock.unlock();
            }
        }
    }

    /**
     * Drop the given table, if it exists.
     *
     * @param tableName to drop
     */
    public void drop(@NonNull final String tableName) {
        execSQL("DROP TABLE IF EXISTS " + tableName);
    }

    /**
     * Run '<a href="https://www.sqlite.org/pragma.html#pragma_optimize">optimize</a>'
     * on the whole database.
     */
    public void optimize() {
        execSQL("PRAGMA optimize");
    }

    /**
     * Run '<a href="https://www.sqlite.org/lang_analyze.html">analyse</a>' on the whole database.
     */
    public void analyze() {
        execSQL("analyze");
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
     * Wrapper.
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
        final Synchronizer.SyncLock txLock;
        if (isUpdate) {
            txLock = mSynchronizer.getExclusiveLock();
        } else {
            txLock = mSynchronizer.getSharedLock();
        }
        // We have the lock, but if the real beginTransaction() throws an exception,
        // we need to release the lock.
        try {
            // If we have a lock, and there is currently a TX active...die
            // Note: because we get a lock, two 'isUpdate' transactions will
            // block, this is only likely to happen with two TXs on the current thread
            // or two non-update TXs on different thread.
            if (mTxLock == null) {
                mSqlDb.beginTransaction();
            } else {
                throw new TransactionException(TransactionException.ALREADY_STARTED);
            }
        } catch (@NonNull final RuntimeException e) {
            txLock.unlock();
            throw new TransactionException("beginTransaction failed: "
                                           + e.getLocalizedMessage(), e);
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
            throw new TransactionException(TransactionException.NOT_STARTED);
        }
        if (!mTxLock.equals(txLock)) {
            throw new TransactionException(TransactionException.WRONG_LOCK);
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
     * DO NOT CALL THIS UNLESS YOU REALLY NEED TO. DATABASE ACCESS SHOULD GO THROUGH THIS CLASS.
     *
     * @return the underlying SQLiteDatabase object.
     */
    @NonNull
    public SQLiteDatabase getSQLiteDatabase() {
        return mSqlDb;
    }

    /**
     * @return the underlying Synchronizer object.
     */
    @NonNull
    Synchronizer getSynchronizer() {
        return mSynchronizer;
    }

    /**
     * DEBUG only.
     */
    @SuppressWarnings("unused")
    private void debugDumpInfo(@NonNull final SQLiteDatabase db) {
        final String[] sql = {"SELECT sqlite_version() AS sqlite_version",
                              "PRAGMA encoding",
                              "PRAGMA collation_list",
                              "PRAGMA foreign_keys",
                              "PRAGMA recursive_triggers",
                              };
        for (final String s : sql) {
            try (Cursor cursor = db.rawQuery(s, null)) {
                if (cursor.moveToNext()) {
                    Log.d(TAG, "debugDumpInfo|" + s + " = " + cursor.getString(0));
                }
            }
        }
    }

}
