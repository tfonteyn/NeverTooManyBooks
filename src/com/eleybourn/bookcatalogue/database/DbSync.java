/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteClosable;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.database.sqlite.SQLiteStatement;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.LockType;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Classes used to help synchronize database access across threads.
 *
 * @author Philip Warner
 */
public class DbSync {

    /**
     * Implementation of a Readers/Writer lock that is fully reentrant.
     *
     * Because SQLite throws exception on locking conflicts, this class can be used to serialize
     * WRITE access while allowing concurrent read access.
     *
     * Each logical database should have its own {@link Synchronizer}
     * Before any read, or group of reads, a call to getSharedLock() should be made.
     * A call to getExclusiveLock() should be made before any update.
     * Multiple calls can be made as necessary so long as an unlock() is called for all get*()
     * calls by using the SyncLock object returned from the get*() call.
     *
     * These can be called in any order and locks in the current thread never block requests.
     *
     * Deadlocks are not possible because the implementation involves a single lock object.
     *
     * NOTE: This lock can cause writer starvation since it does not introduce pending locks.
     *
     * @author Philip Warner
     */
    public static class Synchronizer {
        /** Main lock for synchronization */
        private final ReentrantLock mLock = new ReentrantLock();
        /** Condition fired when a reader releases a lock */
        private final Condition mReleased = mLock.newCondition();
        /** Collection of threads that have shared locks */
        private final Map<Thread, Integer> mSharedOwners = Collections.synchronizedMap(new HashMap<Thread, Integer>());
        /** Lock used to pass back to consumers of shared locks */
        private final SharedLock mSharedLock = new SharedLock();
        /** Lock used to pass back to consumers of exclusive locks */
        private final ExclusiveLock mExclusiveLock = new ExclusiveLock();

        /**
         * Routine to purge shared locks held by dead threads. Can only be called
         * while mLock is held.
         */
        private void purgeOldLocks() {
            if (!mLock.isHeldByCurrentThread()) {
                throw new DBExceptions.LockException("Can not cleanup old locks if not locked");
            }

            for (Thread t : mSharedOwners.keySet()) {
                if (!t.isAlive()) {
                    mSharedOwners.remove(t);
                }
            }
        }

        /**
         * Add a new SharedLock to the collection and return it.
         */
        @NonNull
        SyncLock getSharedLock() {
            final Thread t = Thread.currentThread();
            //Logger.debug(t.getName() + " requesting SHARED lock");
            mLock.lock();
            //Logger.info(t.getName() + " locked lock held by " + mLock.getHoldCount());
            purgeOldLocks();
            try {
                Integer count;
                if (mSharedOwners.containsKey(t)) {
                    count = mSharedOwners.get(t) + 1;
                } else {
                    count = 1;
                }
                mSharedOwners.put(t, count);
                //Logger.info(t.getName() + " " + count + " SHARED threads");
                return mSharedLock;
            } finally {
                mLock.unlock();
                //Logger.info(t.getName() + " unlocked lock held by " + mLock.getHoldCount());
            }
        }

        /**
         * Release a shared lock. If no more locks in thread, remove from list.
         */
        void releaseSharedLock() {
            final Thread t = Thread.currentThread();
            //Logger.debug(t.getName() + " releasing SHARED lock");
            mLock.lock();
            //Logger.info(t.getName() + " locked lock held by " + mLock.getHoldCount());
            try {
                if (mSharedOwners.containsKey(t)) {
                    Integer count = mSharedOwners.get(t) - 1;
                    //Logger.info(t.getName() + " now has " + count + " SHARED locks");
                    if (count < 0) {
                        throw new DBExceptions.LockException("Release a lock count already zero");
                    }
                    if (count != 0) {
                        mSharedOwners.put(t, count);
                    } else {
                        mSharedOwners.remove(t);
                        mReleased.signal();
                    }
                } else {
                    throw new DBExceptions.LockException("Release a lock when not held");
                }
            } finally {
                mLock.unlock();
                //Logger.info(t.getName() + " unlocked lock held by " + mLock.getHoldCount());
            }
        }

        /**
         * Return when exclusive access is available.
         *
         * - take a lock on the collection
         * - see if there are any other locks
         * - if not, return with the lock still held -- this prevents more EX or SH locks.
         * - if there are other SH locks, wait for one to be release and loop.
         */
        @NonNull
        SyncLock getExclusiveLock() {
            final Thread ourThread = Thread.currentThread();
            @SuppressWarnings("UnusedAssignment")
            long t0 = System.currentTimeMillis();

            // Synchronize with other code
            mLock.lock();
            try {
                while (true) {
                    // Cleanup any old threads that are dead.
                    purgeOldLocks();
                    //Logger.debug(t.getName() + " requesting EXCLUSIVE lock with " + mSharedOwners.size() + " shared locks (attempt #" + i + ")");
                    //Logger.info("Lock held by " + mLock.getHoldCount());
                    try {
                        // Simple case -- no locks held, just return and keep the lock
                        if (mSharedOwners.size() == 0) {
                            return mExclusiveLock;
                        }
                        // Check for one lock, and it being this thread.
                        if (mSharedOwners.size() == 1 && mSharedOwners.containsKey(ourThread)) {
                            // One locker, and it is us...so upgrade is OK.
                            return mExclusiveLock;
                        }
                        // Someone else has it. Wait.
                        //Logger.info("Thread " + t.getName() + " waiting for DB access");
                        mReleased.await();
                    } catch (Exception e) {
                        // Probably happens because thread was interrupted. Just die.
                        try {
                            mLock.unlock();
                        } catch (Exception ignored) {
                        }
                        throw new DBExceptions.LockException("Unable to get exclusive lock", e);
                    }
                }
            } finally {
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    if (mLock.isHeldByCurrentThread()) {
                        Logger.info(this,ourThread.getName() + " waited " + (System.currentTimeMillis() - t0) + "ms for EXCLUSIVE access");
                    } else {
                        Logger.info(this,ourThread.getName() + " waited " + (System.currentTimeMillis() - t0) + "ms AND FAILED TO GET EXCLUSIVE access");
                    }
                }
            }
        }

        /**
         * Release the lock previously taken
         */
        void releaseExclusiveLock() {
            //Logger.info(Thread.currentThread().getName() + " releasing EXCLUSIVE lock");
            if (!mLock.isHeldByCurrentThread()) {
                throw new DBExceptions.LockException("Exclusive Lock is not held by this thread");
            }
            mLock.unlock();
            //Logger.info("Release lock held by " + mLock.getHoldCount());
            //Logger.info(t.getName() + " released EXCLUSIVE lock");
        }

        /** Enum of lock types supported */
        public enum LockType {
            shared, exclusive
        }

        /**
         * Interface common to all lock types.
         *
         * @author Philip Warner
         */
        public interface SyncLock {
            void unlock();

            @NonNull
            LockType getType();
        }

        /**
         * Internal implementation of a Shared Lock.
         *
         * @author Philip Warner
         */
        private class SharedLock implements SyncLock {
            @Override
            public void unlock() {
                releaseSharedLock();
            }

            @SuppressWarnings("SameReturnValue")
            @NonNull
            @Override
            public LockType getType() {
                return LockType.shared;
            }
        }

        /**
         * Internal implementation of an Exclusive Lock.
         *
         * @author Philip Warner
         */
        private class ExclusiveLock implements SyncLock {
            @Override
            public void unlock() {
                releaseExclusiveLock();
            }

            @SuppressWarnings("SameReturnValue")
            @NonNull
            @Override
            public LockType getType() {
                return LockType.exclusive;
            }
        }
    }

    /**
     * Database wrapper class that performs thread synchronization on all operations.
     *
     * @author Philip Warner
     */
    public static class SynchronizedDb {
        static final String ERROR_UPDATE_INSIDE_SHARED_TX = "Update inside shared TX";
        /** Underlying database */
        @NonNull
        final SQLiteDatabase mSqlDb;
        /** Sync object to use */
        @NonNull
        final Synchronizer mSync;
        /** Factory object to create the custom cursor. Can not be static because it needs mSync */
        final SynchronizedCursorFactory mCursorFactory = new SynchronizedCursorFactory();
        /** Currently held transaction lock, if any */
        @Nullable
        private SyncLock mTxLock = null;

        private Boolean mIsCollationCaseSensitive;

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
         * Constructor. Use of this method is not recommended. It is better to use
         * the methods that take a {@link SQLiteOpenHelper} object since opening the database may block
         * another thread, or vice versa.
         *
         * @param db   Underlying database
         * @param sync Synchronizer to use
         */
        SynchronizedDb(final @NonNull SQLiteDatabase db, final @NonNull Synchronizer sync) {
            mSqlDb = db;
            mSync = sync;
        }

        /**
         * Constructor.
         *
         * @param helper SQLiteOpenHelper to open underlying database
         * @param sync   Synchronizer to use
         */
        SynchronizedDb(final @NonNull SQLiteOpenHelper helper, final @NonNull Synchronizer sync) {
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
         * Utility routine, purely for debugging ref count issues (mainly Android 2.1)
         *
         * @param msg Message to display (relating to context)
         * @param db  Database object
         **/
        @SuppressWarnings({"JavaReflectionMemberAccess", "UnusedAssignment"})
        static void printRefCount(final @Nullable String msg, final @NonNull SQLiteDatabase db) {
            if (DEBUG_SWITCHES.DB_SYNC && BuildConfig.DEBUG) {
                System.gc();
                try {
                    Field f = SQLiteClosable.class.getDeclaredField("mReferenceCount");
                    f.setAccessible(true);
                    int refs = (Integer) f.get(db);
                    if (msg != null) {
                        Logger.info(SynchronizedDb.class,"DBRefs (" + msg + "): " + refs);
                        //if (refs < 100) {
                        //	Logger.info("DBRefs (" + msg + "): " + refs + " <-- TOO LOW (< 100)!");
                        //} else if (refs < 1001) {
                        //	Logger.info("DBRefs (" + msg + "): " + refs + " <-- TOO LOW (< 1000)!");
                        //} else {
                        //	Logger.info("DBRefs (" + msg + "): " + refs);
                        //}

                    }
                } catch (@NonNull NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
                    Logger.error(e);
                }
            }
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
        private SQLiteDatabase openWithRetries(final @NonNull DbOpener opener) {
            int wait = 10; // 10ms
            //int retriesLeft = 5; // up to 320ms
            int retriesLeft = 10; // 2^10 * 10ms = 10.24sec (actually 2x that due to total wait time)
            SQLiteDatabase db;
            do {
                SyncLock exclusiveLock = mSync.getExclusiveLock();
                try {
                    db = opener.open();
                    Logger.info(this, db.getPath() + "|retriesLeft=" + retriesLeft);
                    return db;
                } catch (Exception e) {
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
        public SynchronizedCursor rawQuery(final @NonNull String sql,
                                           final @Nullable String[] selectionArgs) {
            SyncLock txLock = null;
            if (mTxLock == null) {
                txLock = mSync.getSharedLock();
            }

            try {
                return (SynchronizedCursor) mSqlDb.rawQueryWithFactory(mCursorFactory, sql, selectionArgs, "");
            } finally {
                if (txLock != null) {
                    txLock.unlock();
                }
            }
        }

        /**
         * Locking-aware wrapper for underlying database method.
         */
        public void execSQL(final @NonNull String sql) throws SQLException {
            if (DEBUG_SWITCHES.SQL && DEBUG_SWITCHES.DB_SYNC && BuildConfig.DEBUG) {
                Logger.debug(sql);
            }

            if (mTxLock != null) {
                if (mTxLock.getType() != LockType.exclusive) {
                    throw new DBExceptions.TransactionException(ERROR_UPDATE_INSIDE_SHARED_TX);
                }
                mSqlDb.execSQL(sql);
            } else {
                SyncLock l = mSync.getExclusiveLock();
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
        public Cursor query(final @NonNull String table,
                            final @NonNull String[] columns,
                            final @NonNull String selection,
                            final @Nullable String[] selectionArgs,
                            final @Nullable String groupBy,
                            final @Nullable String having,
                            final @Nullable String orderBy) {
            SyncLock txLock = null;
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
         * Locking-aware wrapper for underlying database method
         *
         * @return the row ID of the newly inserted row, or -1 if an error occurred
         */
        long insert(final @NonNull String table,
                    @SuppressWarnings("SameParameterValue") final @Nullable String nullColumnHack,
                    final @NonNull ContentValues cv) {
            SyncLock txLock = null;
            if (mTxLock != null) {
                if (mTxLock.getType() != LockType.exclusive) {
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
        public int update(final @NonNull String table,
                          final @NonNull ContentValues cv,
                          final @NonNull String whereClause,
                          final @Nullable String[] whereArgs) {
            SyncLock txLock = null;
            if (mTxLock != null) {
                if (mTxLock.getType() != LockType.exclusive) {
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
        public int delete(final @NonNull String table,
                          final @Nullable String whereClause,
                          final @Nullable String[] whereArgs) {
            SyncLock txLock = null;
            if (mTxLock != null) {
                if (mTxLock.getType() != LockType.exclusive) {
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
         * Wrapper for underlying database method. It is recommended that custom cursors subclass SynchronizedCursor.
         *
         * Runs the provided SQL and returns a cursor over the result set.
         *
         * @param cursorFactory the cursor factory to use, or null for the default factory
         * @param sql the SQL query. The SQL string must not be ; terminated
         * @param selectionArgs You may include ?s in where clause in the query,
         *     which will be replaced by the values from selectionArgs. The
         *     values will be bound as Strings.
         * @param editTable the name of the first table, which is editable
         * @return A {@link Cursor} object, which is positioned before the first entry. Note that
         * {@link Cursor}s are not synchronized, see the documentation for more details.
         */
        @NonNull
        public Cursor rawQueryWithFactory(final @NonNull SQLiteDatabase.CursorFactory cursorFactory,
                                          final @NonNull String sql,
                                          final @Nullable String[] selectionArgs,
                                          final @NonNull String editTable) {
            SyncLock txLock = null;
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
        public SynchronizedStatement compileStatement(final @NonNull String sql) {
            SyncLock txLock = null;
            if (mTxLock != null) {
                if (mTxLock.getType() != LockType.exclusive) {
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
        SQLiteDatabase getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing() {
            return mSqlDb;
        }

        /**
         * Really only meant for backup purposes.
         *
         * @return the path to the actual database file
         */
        @NonNull
        String getPath() {
            return mSqlDb.getPath();
        }

        void analyze() {
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
        public SyncLock beginTransaction(final boolean isUpdate) {
            SyncLock txLock;
            if (isUpdate) {
                txLock = mSync.getExclusiveLock();
            } else {
                txLock = mSync.getSharedLock();
            }
            // We have the lock, but if the real beginTransaction() throws an exception, we need to release the lock
            try {
                // If we have a lock, and there is currently a TX active...die
                // Note: because we get a lock, two 'isUpdate' transactions will
                // block, this is only likely to happen with two TXs on the current thread
                // or two non-update TXs on different thread.
                // ENHANCE: Consider allowing nested TXs
                // ENHANCE: Consider returning NULL if TX active and handle null locks...
                if (mTxLock != null) {
                    throw new DBExceptions.TransactionException("Starting a transaction when one is already started");
                }

                mSqlDb.beginTransaction();
            } catch (Exception e) {
                txLock.unlock();
                throw new DBExceptions.TransactionException("Unable to start database transaction: " + e.getLocalizedMessage(), e);
            }
            mTxLock = txLock;
            return txLock;
        }

        /**
         * Locking-aware wrapper for underlying database method.
         *
         * @param txLock Lock returned from BeginTransaction().
         */
        public void endTransaction(final @NonNull SyncLock txLock) {
            if (mTxLock == null) {
                throw new DBExceptions.TransactionException("Ending a transaction when none is started");
            }
            if (!mTxLock.equals(txLock)) {
                throw new DBExceptions.TransactionException("Ending a transaction with wrong transaction lock");
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
         *
         * This bug was introduced in ICS and present in 4.0-4.0.3, at least.
         *
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
                try (Cursor c = mSqlDb.rawQuery("SELECT t, i FROM collation_cs_check ORDER BY t " + CatalogueDBHelper.COLLATION + ", i", new String[] {})) {
                    c.moveToFirst();
                    s = c.getString(0);
                }
                return !"a".equals(s);
            } finally {
                try { mSqlDb.execSQL("DROP TABLE If Exists collation_cs_check");
                } catch (Exception ignored) {}
            }
        }

        /**
         * Interface to an object that can return an open SQLite database object
         *
         * @author pjw
         */
        private interface DbOpener {
            @NonNull
            SQLiteDatabase open();
        }

        /**
         * Factory for Synchronized Cursor objects. This can be subclassed by other
         * Cursor implementations.
         *
         * @author Philip Warner
         */
        class SynchronizedCursorFactory implements CursorFactory {
            @Override
            @NonNull
            public SynchronizedCursor newCursor(final SQLiteDatabase db,
                                                final @NonNull SQLiteCursorDriver masterQuery,
                                                final @NonNull String editTable,
                                                final @NonNull SQLiteQuery query) {
                return new SynchronizedCursor(masterQuery, editTable, query, mSync);
            }
        }
    }

    /**
     * Wrapper for statements that ensures locking is used.
     *
     * Represents a statement that can be executed against a database.  The statement
     * cannot return multiple rows or columns, but single value (1 x 1) result sets
     * are supported.
     *
     * @author Philip Warner
     */
    public static class SynchronizedStatement implements Closeable {

        /** Synchronizer from database */
        @NonNull
        final Synchronizer mSync;
        /** Underlying statement */
        final SQLiteStatement mStatement;
        /** Indicates this is a 'read-only' statement */
        final boolean mIsReadOnly;
        /** Copy of SQL used for debugging */
        @NonNull
        private final String mSql;
        /** Indicates close() has been called */
        private boolean mIsClosed = false;

        private SynchronizedStatement(final @NonNull SynchronizedDb db, final @NonNull String sql) {
            mSync = db.getSynchronizer();
            mSql = sql;

            mIsReadOnly = sql.trim().toUpperCase().startsWith("SELECT");
            mStatement = db.getUnderlyingDatabaseIfYouAreSureWhatYouAreDoing().compileStatement(sql);

            if (DEBUG_SWITCHES.SQL && BuildConfig.DEBUG) {
                Logger.info(this,sql + "\n\n");
            }
        }

        /**
         * Wrapper for underlying method on SQLiteStatement.
         */
        @SuppressWarnings("unused")
        public void bindDouble(final int index, final double value) {
            mStatement.bindDouble(index, value);
        }

        /**
         * Wrapper for underlying method on SQLiteStatement.
         */
        public void bindLong(final int index, final long value) {
            mStatement.bindLong(index, value);
        }

        /**
         * Wrapper for underlying method on SQLiteStatement.
         */
        public void bindString(final int index, final @Nullable String value) {
            if (value == null) {
                if (/* always print debug */ BuildConfig.DEBUG) {
                    Logger.debug("binding NULL");
                }
                mStatement.bindNull(index);
            } else {
                mStatement.bindString(index, value);
            }
        }

        /**
         * Wrapper for underlying method on SQLiteStatement.
         */
        void bindBlob(final int index, final @Nullable byte[] value) {
            if (value == null) {
                mStatement.bindNull(index);
            } else {
                mStatement.bindBlob(index, value);
            }
        }

        /**
         * Wrapper for underlying method on SQLiteStatement.
         */
        public void bindNull(final int index) {
            mStatement.bindNull(index);
        }

        /**
         * Wrapper for underlying method on SQLiteStatement.
         */
        void clearBindings() {
            mStatement.clearBindings();
        }

        /**
         * Wrapper for underlying method on SQLiteStatement.
         */
        @Override
        public void close() {
            mIsClosed = true;
            mStatement.close();
        }

        /**
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute a statement that returns a 1 by 1 table with a numeric value.
         *
         * @return The result of the query.
         *
         * @throws SQLiteDoneException if the query returns zero rows
         */
        public long simpleQueryForLong() throws SQLiteDoneException {
            SyncLock sharedLock = mSync.getSharedLock();
            try {
                long result = mStatement.simpleQueryForLong();
                if (DEBUG_SWITCHES.DB_SYNC_QUERY_FOR_LONG && BuildConfig.DEBUG && result <= 0) {
                    Logger.debug("simpleQueryForLong got: " + result);
                }
                return result;
            } finally {
                sharedLock.unlock();
            }
        }

        /**
         * Syntax sugar
         *
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute a statement that returns a 1 by 1 table with a numeric value.
         *
         * @return The result of the query, or 0 when no rows found
         */
        public long simpleQueryForLongOrZero() {
            SyncLock sharedLock = mSync.getSharedLock();
            try {
                long result = mStatement.simpleQueryForLong();
                if (DEBUG_SWITCHES.DB_SYNC_QUERY_FOR_LONG && BuildConfig.DEBUG && result <= 0) {
                    Logger.debug("simpleQueryForLongOrZero got: " + result);
                }
                return result;
            } catch (SQLiteDoneException ignore) {
                return 0;
            } finally {
                sharedLock.unlock();
            }
        }

        /**
         * Syntax sugar to identify SELECT COUNT(..) statements
         *
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute a statement that returns a 1 by 1 table with a numeric value.
         * For example, SELECT COUNT(*) FROM table;
         *
         * @return The result of the query.
         */
        public long count() {
            if (DEBUG_SWITCHES.DB_SYNC_QUERY_FOR_LONG && BuildConfig.DEBUG) {
                if (!mSql.toUpperCase().startsWith("SELECT COUNT(")) {
                    Logger.debug("count statement not a count?");
                }
            }
            return this.simpleQueryForLongOrZero();
        }

        /**
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute a statement that returns a 1 by 1 table with a text value.
         *
         * @return The result of the query.
         *
         * @throws android.database.sqlite.SQLiteDoneException if the query returns zero rows
         */
        @NonNull
        public String simpleQueryForString() throws SQLiteDoneException {
            SyncLock sharedLock = mSync.getSharedLock();
            try {
                return mStatement.simpleQueryForString();
            } finally {
                sharedLock.unlock();
            }
        }

        /**
         * Syntax sugar
         *
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute a statement that returns a 1 by 1 table with a text value.
         *
         * @return The result of the query.
         */
        @Nullable
        String simpleQueryForStringOrNull() {
            SyncLock sharedLock = mSync.getSharedLock();
            try {
                return mStatement.simpleQueryForString();
            } catch (SQLiteDoneException e) {
                if (/* always print debug */ BuildConfig.DEBUG) {
                    Logger.error(e, "simpleQueryForStringOrNull got NULL");
                }
                return null;
            } finally {
                sharedLock.unlock();
            }
        }

        /**
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute this SQL statement, if it is not a SELECT / INSERT / DELETE / UPDATE, for example
         * CREATE / DROP table, view, trigger, index etc.
         *
         * @throws android.database.SQLException If the SQL string is invalid for some reason
         */
        public void execute() throws SQLException {
            SyncLock txLock;
            if (mIsReadOnly) {
                txLock = mSync.getSharedLock();
            } else {
                txLock = mSync.getExclusiveLock();
            }
            try {
                mStatement.execute();
            } finally {
                txLock.unlock();
            }
        }

        /**
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute this SQL statement, if the the number of rows affected by execution of this SQL
         * statement is of any importance to the caller - for example, UPDATE / DELETE SQL statements.
         *
         * @return the number of rows affected by this SQL statement execution.
         * @throws android.database.SQLException If the SQL string is invalid for some reason
         */
        @SuppressWarnings("UnusedReturnValue")
        public int executeUpdateDelete() {
            SyncLock exclusiveLock = mSync.getExclusiveLock();
            try {
                return mStatement.executeUpdateDelete();
            } finally {
                exclusiveLock.unlock();
            }
        }
        /**
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute this SQL statement and return the ID of the row inserted due to this call.
         * The SQL statement should be an INSERT for this to be a useful call.
         *
         * @return the row ID of the last row inserted, if this insert is successful. -1 otherwise.
         *
         * @throws android.database.SQLException If the SQL string is invalid for some reason
         */
        public long executeInsert() throws SQLException {
            SyncLock exclusiveLock = mSync.getExclusiveLock();
            try {
                return mStatement.executeInsert();
            } finally {
                exclusiveLock.unlock();
            }
        }

        public void finalize() {
            if (!mIsClosed && DEBUG_SWITCHES.DB_SYNC && BuildConfig.DEBUG) {
                Logger.info(this,"Finalizing non-closed statement (potential error/small)");
                if (DEBUG_SWITCHES.SQL) {
                    Logger.debug(mSql);
                }
            }

            try {
                mStatement.close();
            } catch (Exception ignore) {
                // Ignore; may have been finalized
            }
        }

        @Override
        public String toString() {
            return "SynchronizedStatement{" +
                    "mSql='" + mSql + '\'' +
                    '}';
        }
    }


    /**
     * Cursor wrapper that tries to apply locks as necessary. Unfortunately, most cursor
     * movement methods are final and, if they involve any database locking, could theoretically
     * still result in 'database is locked' exceptions. So far in testing, none have occurred.
     */
    public static class SynchronizedCursor extends SQLiteCursor {
        @NonNull
        private final Synchronizer mSync;
        private int mCount = -1;

        protected SynchronizedCursor(final @NonNull SQLiteCursorDriver driver,
                                     final @NonNull String editTable,
                                     final @NonNull SQLiteQuery query,
                                     final @NonNull Synchronizer sync) {
            super(driver, editTable, query);
            mSync = sync;
        }

        /**
         * Wrapper that uses a lock before calling underlying method.
         */
        @Override
        public int getCount() {
            // Cache the count (it's what SQLiteCursor does), and we avoid locking
            if (mCount == -1) {
                SyncLock sharedLock = mSync.getSharedLock();
                try {
                    mCount = super.getCount();
                } finally {
                    sharedLock.unlock();
                }
            }
            return mCount;
        }

        /**
         * Wrapper that uses a lock before calling underlying method.
         */
        @Override
        @CallSuper
        public boolean requery() {
            SyncLock sharedLock = mSync.getSharedLock();
            try {
                return super.requery();
            } finally {
                sharedLock.unlock();
            }
        }
    }
}
