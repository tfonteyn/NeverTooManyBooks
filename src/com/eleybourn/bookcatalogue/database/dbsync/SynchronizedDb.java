package com.eleybourn.bookcatalogue.database.dbsync;

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

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.DBExceptions;
import com.eleybourn.bookcatalogue.database.DBHelper;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.lang.reflect.Field;

/**
 * Database wrapper class that performs thread synchronization on all operations.
 *
 * @author Philip Warner
 */
public class SynchronizedDb {

    private static final String ERROR_UPDATE_INSIDE_SHARED_TX = "Update inside shared TX";
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

    private Boolean mIsCollationCaseSensitive;

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
        mSqlDb = openWithRetries(new DbOpener() {
            @NonNull
            @Override
            public SQLiteDatabase open() {
                return helper.getWritableDatabase();
            }
        });
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
        mSqlDb = openWithRetries(new DbOpener() {
            @NonNull
            @Override
            public SQLiteDatabase open() {
                return helper.getWritableDatabase();
            }
        });

        // only set when bigger then default
        if ((preparedStmtCache > 25)
                && (preparedStmtCache < SQLiteDatabase.MAX_SQL_CACHE_SIZE)) {
            mSqlDb.setMaxSqlCacheSize(preparedStmtCache);
        }
    }

    /**
     * Utility routine, purely for debugging ref count issues (mainly Android 2.1).
     *
     * @param msg Message to display (relating to context)
     * @param db  Database object
     **/
    @SuppressWarnings({"JavaReflectionMemberAccess", "UnusedAssignment"})
    public static void printRefCount(@Nullable final String msg,
                                     @NonNull final SQLiteDatabase db) {
        if (DEBUG_SWITCHES.DB_SYNC && BuildConfig.DEBUG) {
            System.gc();
            try {
                Field f = SQLiteClosable.class.getDeclaredField("mReferenceCount");
                f.setAccessible(true);
                int refs = (Integer) f.get(db);
                if (msg != null) {
                    Logger.info(SynchronizedDb.class, "DBRefs (" + msg + "): " + refs);
                    //if (refs < 100) {
                    //  Logger.info("DBRefs (" + msg + "): " + refs + " <-- TOO LOW (< 100)!");
                    //} else if (refs < 1001) {
                    //  Logger.info("DBRefs (" + msg + "): " + refs + " <-- TOO LOW (< 1000)!");
                    //} else {
                    //  Logger.info("DBRefs (" + msg + "): " + refs);
                    //}

                }
            } catch (@NonNull final NoSuchFieldException
                    | IllegalAccessException
                    | IllegalArgumentException e) {
                Logger.error(e);
            }
        }
    }

    public boolean isCollationCaseSensitive() {
        if (mIsCollationCaseSensitive == null) {
            mIsCollationCaseSensitive = checkIfCollationIsCaseSensitive();
            if (DEBUG_SWITCHES.DB_SYNC && BuildConfig.DEBUG) {
                Logger.info(this, "isCollationCaseSensitive=" + mIsCollationCaseSensitive);
            }
        }
        return mIsCollationCaseSensitive;
    }

    /**
     * Call the passed database opener with retries to reduce risks of access conflicts
     * causing crashes.
     *
     * @param opener DbOpener interface
     *
     * @return The opened database
     */
    @NonNull
    private SQLiteDatabase openWithRetries(@NonNull final DbOpener opener) {
        // 10ms
        int wait = 10;
        // 2^10 * 10ms = 10.24sec (actually 2x that due to total wait time)
        int retriesLeft = 10;

        SQLiteDatabase db;
        do {
            Synchronizer.SyncLock exclusiveLock = mSync.getExclusiveLock();
            try {
                db = opener.open();
                Logger.info(this, db.getPath() + "|retriesLeft=" + retriesLeft);
                return db;
            } catch (RuntimeException e) {
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
                } catch (InterruptedException e1) {
                    throw new RuntimeException("Unable to open database, interrupted", e1);
                }
            } finally {
                if (exclusiveLock != null) {
                    exclusiveLock.unlock();
                }
            }
        } while (true);

    }

    /**
     * Locking-aware wrapper for underlying database method.
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
     * Locking-aware wrapper for underlying database method.
     */
    public void execSQL(@NonNull final String sql)
            throws SQLException {
        if (DEBUG_SWITCHES.SQL && DEBUG_SWITCHES.DB_SYNC && BuildConfig.DEBUG) {
            Logger.debug(sql);
        }

        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.exclusive) {
                throw new DBExceptions.TransactionException(ERROR_UPDATE_INSIDE_SHARED_TX);
            }
            mSqlDb.execSQL(sql);
        } else {
            Synchronizer.SyncLock l = mSync.getExclusiveLock();
            try {
                mSqlDb.execSQL(sql);
            } finally {
                l.unlock();
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
            return mSqlDb.query(table, columns, selection, selectionArgs, groupBy, having,
                                orderBy);
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
                throw new DBExceptions.TransactionException(ERROR_UPDATE_INSIDE_SHARED_TX);
            }
        } else {
            txLock = mSync.getExclusiveLock();
        }

        // reminder: insert does not throw exceptions for the actual insert.
        // but it can throw other exceptions.
        try {
            return mSqlDb.insert(table, nullColumnHack, cv);
        } finally {
            if (txLock != null) {
                txLock.unlock();
            }
        }
    }

    /**
     * Locking-aware wrapper for underlying database method.
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
                throw new DBExceptions.TransactionException(ERROR_UPDATE_INSIDE_SHARED_TX);
            }
        } else {
            txLock = mSync.getExclusiveLock();
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
     *
     * @return the number of rows affected if a whereClause is passed in, 0
     * otherwise. To remove all rows and get a count pass "1" as the
     * whereClause.
     */
    public int delete(@NonNull final String table,
                      @Nullable final String whereClause,
                      @Nullable final String[] whereArgs) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.exclusive) {
                throw new DBExceptions.TransactionException(ERROR_UPDATE_INSIDE_SHARED_TX);
            }
        } else {
            txLock = mSync.getExclusiveLock();
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
     * Wrapper for underlying database method.
     * It is recommended that custom cursors subclass SynchronizedCursor.
     * <p>
     * Runs the provided SQL and returns a cursor over the result set.
     *
     * @param cursorFactory the cursor factory to use, or null for the default factory
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
    @NonNull
    public SynchronizedStatement compileStatement(@NonNull final String sql) {
        Synchronizer.SyncLock txLock = null;
        if (mTxLock != null) {
            if (mTxLock.getType() != Synchronizer.LockType.exclusive) {
                throw new DBExceptions.TransactionException("Compile inside shared TX");
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
     * @return the underlying SQLiteDatabase object.
     */
    @NonNull
    public SQLiteDatabase getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing() {
        return mSqlDb;
    }

    /**
     * Really only meant for backup purposes.
     *
     * @return the path to the actual database file
     */
    @NonNull
    public String getPath() {
        return mSqlDb.getPath();
    }

    public void analyze() {
        // Don't do VACUUM -- it's a complete rebuild
        //mSyncedDb.execSQL("vacuum");
        execSQL("analyze");
    }

    /**
     * Wrapper.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
                Logger.error("Starting a transaction when one is already started");
            }
        } catch (RuntimeException e) {
            txLock.unlock();
            throw new DBExceptions.TransactionException(
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
            throw new DBExceptions.TransactionException(
                    "Ending a transaction when none is started");
        }
        if (!mTxLock.equals(txLock)) {
            throw new DBExceptions.TransactionException(
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
     * Wrapper for underlying database method.
     */
    public boolean isOpen() {
        return mSqlDb.isOpen();
    }

    /**
     * @return the underlying synchronizer object.
     */
    @NonNull
    Synchronizer getSynchronizer() {
        return mSync;
    }

    /**
     * Method to detect if collation implementations are case sensitive.
     * This was built because ICS broke the UNICODE collation (making it CS) and we needed
     * to check for collation case-sensitivity.
     * <p>
     * This bug was introduced in ICS and present in 4.0-4.0.3, at least.
     * <p>
     * Now the code has been generalized to allow for arbitrary changes to choice of collation.
     *
     * @author Philip Warner
     */
    private boolean checkIfCollationIsCaseSensitive() {
        // Drop and create table
        mSqlDb.execSQL("DROP TABLE If Exists collation_cs_check");
        mSqlDb.execSQL("CREATE TABLE collation_cs_check (t text, i integer)");
        try {
            // Row that *should* be returned first assuming 'a' <=> 'A'
            mSqlDb.execSQL("INSERT INTO collation_cs_check VALUES('a', 1)");
            // Row that *should* be returned second assuming 'a' <=> 'A'; will be returned first if 'A' < 'a'.
            mSqlDb.execSQL("INSERT INTO collation_cs_check VALUES('A', 2)");

            String s;
            try (Cursor c = mSqlDb.rawQuery(
                    "SELECT t, i FROM collation_cs_check ORDER BY t " + DBHelper.COLLATION + ", i",
                    new String[]{})) {
                c.moveToFirst();
                s = c.getString(0);
            }
            return !"a".equals(s);
        } finally {
            try {
                mSqlDb.execSQL("DROP TABLE If Exists collation_cs_check");
            } catch (SQLException e) {
                Logger.error(e);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * Interface to an object that can return an open SQLite database object.
     */
    private interface DbOpener {

        @NonNull
        SQLiteDatabase open();
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
