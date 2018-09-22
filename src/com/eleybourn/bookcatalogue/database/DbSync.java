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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.eleybourn.bookcatalogue.BookDetailsFragment;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.LockTypes;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer.SyncLock;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static com.eleybourn.bookcatalogue.DEBUG_SWITCHES.DB_SYNC_QUERY_FOR_LONG;

/**
 * Classes used to help synchronize database access across threads.
 *
 * @author Philip Warner
 */
public class DbSync {

    /**
     * Implementation of a Readers/Writer lock that is fully reentrant.
     *
     * Because SQLite throws exception on locking conflicts, this class can be used to serialize WRITE
     * access while allowing concurrent read access.
     *
     * Each logical database should have its own 'Synchronizer' and before any read, or group or reads, a call
     * to getSharedLock() should be made. A call to getExclusiveLock() should be made before any update. Multiple
     * calls can be made as necessary so long as an unlock() is called for all get*() calls by using the
     * SyncLock object returned from the get*() call.
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
                throw new RuntimeException("Can not cleanup old locks if not locked");
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
        public SyncLock getSharedLock() {
            final Thread t = Thread.currentThread();
            //System.out.println(t.getName() + " requesting SHARED lock");
            mLock.lock();
            //System.out.println(t.getName() + " locked lock held by " + mLock.getHoldCount());
            purgeOldLocks();
            try {
                Integer count;
                if (mSharedOwners.containsKey(t)) {
                    count = mSharedOwners.get(t) + 1;
                } else {
                    count = 1;
                }
                mSharedOwners.put(t, count);
                //System.out.println(t.getName() + " " + count + " SHARED threads");
                return mSharedLock;
            } finally {
                mLock.unlock();
                //System.out.println(t.getName() + " unlocked lock held by " + mLock.getHoldCount());
            }
        }

        /**
         * Release a shared lock. If no more locks in thread, remove from list.
         */
        void releaseSharedLock() {
            final Thread t = Thread.currentThread();
            //System.out.println(t.getName() + " releasing SHARED lock");
            mLock.lock();
            //System.out.println(t.getName() + " locked lock held by " + mLock.getHoldCount());
            try {
                if (mSharedOwners.containsKey(t)) {
                    Integer count = mSharedOwners.get(t) - 1;
                    //System.out.println(t.getName() + " now has " + count + " SHARED locks");
                    if (count < 0) {
                        throw new RuntimeException("Release a lock count already zero");
                    }
                    if (count != 0) {
                        mSharedOwners.put(t, count);
                    } else {
                        mSharedOwners.remove(t);
                        mReleased.signal();
                    }
                } else {
                    throw new RuntimeException("Release a lock when not held");
                }
            } finally {
                mLock.unlock();
                //System.out.println(t.getName() + " unlocked lock held by " + mLock.getHoldCount());
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
        public SyncLock getExclusiveLock() {
            final Thread ourThread = Thread.currentThread();
            long t0;
            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                t0 = System.currentTimeMillis();
            }
            // Synchronize with other code
            mLock.lock();
            try {
                while (true) {
                    // Cleanup any old threads that are dead.
                    purgeOldLocks();
                    //System.out.println(t.getName() + " requesting EXCLUSIVE lock with " + mSharedOwners.size() + " shared locks (attempt #" + i + ")");
                    //System.out.println("Lock held by " + mLock.getHoldCount());
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
                        //System.out.println("Thread " + t.getName() + " waiting for DB access");
                        mReleased.await();
                    } catch (Exception e) {
                        // Probably happens because thread was interrupted. Just die.
                        try {
                            mLock.unlock();
                        } catch (Exception ignored) {
                        }
                        throw new RuntimeException("Unable to get exclusive lock", e);
                    }
                }
            } finally {
                if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                    //noinspection UnusedAssignment
                    long t1 = System.currentTimeMillis();
                    if (mLock.isHeldByCurrentThread()) {
                        System.out.println(ourThread.getName() + " waited " + (t1 - t0) + "ms for EXCLUSIVE access");
                    } else {
                        System.out.println(ourThread.getName() + " waited " + (t1 - t0) + "ms AND FAILED TO GET EXCLUSIVE access");
                    }
                }
            }
        }

        /**
         * Release the lock previously taken
         */
        void releaseExclusiveLock() {
            //final Thread t = Thread.currentThread();
            //System.out.println(t.getName() + " releasing EXCLUSIVE lock");
            if (!mLock.isHeldByCurrentThread()) {
                throw new RuntimeException("Exclusive Lock is not held by this thread");
            }
            mLock.unlock();
            //System.out.println("Release lock held by " + mLock.getHoldCount());
            //System.out.println(t.getName() + " released EXCLUSIVE lock");
        }

        /** Enum of lock types supported */
        public enum LockTypes {
            shared, exclusive
        }

        /**
         * Interface common to all lock types.
         *
         * @author Philip Warner
         */
        public interface SyncLock {
            void unlock();

            LockTypes getType();
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

            @Override
            public LockTypes getType() {
                return LockTypes.shared;
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

            @Override
            public LockTypes getType() {
                return LockTypes.exclusive;
            }
        }
    }

    /**
     * Database wrapper class that performs thread synchronization on all operations.
     *
     * @author Philip Warner
     */
    public static class SynchronizedDb {
        /** Underlying database */
        final SQLiteDatabase mDb;
        /** Sync object to use */
        final Synchronizer mSync;
        /** Factory object to create the custom cursor. Can not be static because it needs mSync */
        final SynchronizedCursorFactory mCursorFactory = new SynchronizedCursorFactory();
        /** Currently held transaction lock, if any */
        private SyncLock mTxLock = null;

        /**
         * Constructor. Use of this method is not recommended. It is better to use
         * the methods that take a {@link SQLiteOpenHelper} object since opening the database may block
         * another thread, or vice versa.
         *
         * @param db   Underlying database
         * @param sync Synchronizer to use
         */
        SynchronizedDb(@NonNull final SQLiteDatabase db, @NonNull final Synchronizer sync) {
            mDb = db;
            mSync = sync;
            if (BuildConfig.DEBUG) {
                System.out.println("Reminder: Use of this method is not recommended. It is better to use\n" +
                        "\t\t  the methods that take a {@link SQLiteOpenHelper} object since opening the database may block\n" +
                        "\t\t  another thread, or vice versa.");
            }
        }

        /**
         * Constructor.
         *
         * @param helper SQLiteOpenHelper to open underlying database
         * @param sync   Synchronizer to use
         */
        SynchronizedDb(@NonNull final  SQLiteOpenHelper helper, @NonNull final Synchronizer sync) {
            mSync = sync;
            mDb = openWithRetries(new DbOpener() {
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
        public static void printRefCount(@Nullable final String msg, @NonNull final SQLiteDatabase db) {
            if (DEBUG_SWITCHES.DB_SYNC && BuildConfig.DEBUG) {
                System.gc();
                try {
                    Field f = SQLiteClosable.class.getDeclaredField("mReferenceCount");
                    f.setAccessible(true);
                    int refs = (Integer) f.get(db); //IllegalAccessException
                    if (msg != null) {
                        System.out.println("DBRefs (" + msg + "): " + refs);
                        //if (refs < 100) {
                        //	System.out.println("DBRefs (" + msg + "): " + refs + " <-- TOO LOW (< 100)!");
                        //} else if (refs < 1001) {
                        //	System.out.println("DBRefs (" + msg + "): " + refs + " <-- TOO LOW (< 1000)!");
                        //} else {
                        //	System.out.println("DBRefs (" + msg + "): " + refs);
                        //}

                    }
                } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
                    Logger.logError(e);
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
        private SQLiteDatabase openWithRetries(@NonNull final DbOpener opener) {
            int wait = 10; // 10ms
            //int retriesLeft = 5; // up to 320ms
            int retriesLeft = 10; // 2^10 * 10ms = 10.24sec (actually 2x that due to total wait time)
            SQLiteDatabase db;
            do {
                SyncLock l = mSync.getExclusiveLock();
                try {
                    db = opener.open();
                    return db;
                } catch (Exception e) {
                    if (l != null) {
                        l.unlock();
                        l = null;
                    }
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
                    if (l != null) {
                        l.unlock();
                    }
                }
            } while (true);

        }

        /**
         * Locking-aware wrapper for underlying database method.
         */
        public SynchronizedCursor rawQuery(@NonNull final String sql, @Nullable final String[] selectionArgs) {
            return rawQueryWithFactory(mCursorFactory, sql, selectionArgs, "");
        }

        /**
         * Locking-aware wrapper for underlying database method.
         */
        public SynchronizedCursor rawQuery(String sql) {
            return rawQuery(sql, new String[]{});
        }

        /**
         * Locking-aware wrapper for underlying database method.
         */
        SynchronizedCursor rawQueryWithFactory(@NonNull final SynchronizedCursorFactory factory,
                                               @NonNull final String sql,
                                               @Nullable final String[] selectionArgs,
                                               @SuppressWarnings("SameParameterValue") @NonNull final String editTable) {
            SyncLock l = null;
            if (mTxLock == null) {
                l = mSync.getSharedLock();
            }

            try {
                return (SynchronizedCursor) mDb.rawQueryWithFactory(factory, sql, selectionArgs, editTable);
            } finally {
                if (l != null) {
                    l.unlock();
                }
            }
        }

        /**
         * Locking-aware wrapper for underlying database method.
         */
        public void execSQL(@NonNull final String sql) throws SQLException {
            if (mTxLock != null) {
                if (mTxLock.getType() != LockTypes.exclusive) {
                    throw new RuntimeException("Update inside shared TX");
                }
                mDb.execSQL(sql);
            } else {
                SyncLock l = mSync.getExclusiveLock();
                try {
                    mDb.execSQL(sql);
                } finally {
                    l.unlock();
                }
            }
        }

        /**
         * Locking-aware wrapper for underlying database method.
         */
        public Cursor query(@NonNull final String table,
                            @NonNull final String[] columns,
                            @NonNull final String selection,
                            @Nullable final String[] selectionArgs,
                            @Nullable final String groupBy,
                            @Nullable final String having,
                            @Nullable final String orderBy) {
            SyncLock l = null;
            if (mTxLock == null) {
                l = mSync.getSharedLock();
            }

            try {
                return mDb.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
            } finally {
                if (l != null) {
                    l.unlock();
                }
            }
        }

        /**
         * Locking-aware wrapper for underlying database method; actually
         * calls insertOrThrow since this method also throws exceptions
         *
         * @return the row ID of the newly inserted row, or {@link SynchronizedStatement#INSERT_FAILED} if an error occurred
         */
        public long insert(@NonNull final String table,
                           @Nullable final String nullColumnHack,
                           @NonNull final ContentValues values) throws SQLException {
            SyncLock l = null;
            if (mTxLock != null) {
                if (mTxLock.getType() != LockTypes.exclusive) {
                    throw new RuntimeException("Update inside shared TX");
                }
            } else {
                l = mSync.getExclusiveLock();
            }

            try {
                return mDb.insertOrThrow(table, nullColumnHack, values);
            } finally {
                if (l != null) {
                    l.unlock();
                }
            }
        }

        /**
         * Locking-aware wrapper for underlying database method.
         *
         * @return the number of rows affected
         */
        public int update(@NonNull final String table,
                          @NonNull final ContentValues values,
                          @NonNull final String whereClause,
                          @Nullable final String[] whereArgs) throws IllegalArgumentException, SQLException {
            SyncLock l = null;
            if (mTxLock != null) {
                if (mTxLock.getType() != LockTypes.exclusive) {
                    throw new RuntimeException("Update inside shared TX");
                }
            } else {
                l = mSync.getExclusiveLock();
            }

            try {
                return mDb.update(table, values, whereClause, whereArgs);
            } finally {
                if (l != null) {
                    l.unlock();
                }
            }
        }

        /**
         * Locking-aware wrapper for underlying database method.
         *
         * @return the number of rows affected if a whereClause is passed in, 0
         *         otherwise. To remove all rows and get a count pass "1" as the
         *         whereClause.
         */
        public int delete(@NonNull final String table,
                          @NonNull final String whereClause,
                          @Nullable final String[] whereArgs) {
            SyncLock l = null;
            if (mTxLock != null) {
                if (mTxLock.getType() != LockTypes.exclusive) {
                    throw new RuntimeException("Update inside shared TX");
                }
            } else {
                l = mSync.getExclusiveLock();
            }

            try {
                return mDb.delete(table, whereClause, whereArgs);
            } finally {
                if (l != null) {
                    l.unlock();
                }
            }
        }

        /**
         * Wrapper for underlying database method. It is recommended that custom cursors subclass SynchronizedCursor.
         */
        public Cursor rawQueryWithFactory(@NonNull final SQLiteDatabase.CursorFactory cursorFactory,
                                          @NonNull final String sql,
                                          @NonNull final String[] selectionArgs,
                                          @NonNull final String editTable) {
            SyncLock l = null;
            if (mTxLock == null) {
                l = mSync.getSharedLock();
            }
            try {
                return mDb.rawQueryWithFactory(cursorFactory, sql, selectionArgs, editTable);
            } finally {
                if (l != null) {
                    l.unlock();
                }
            }
        }

        /**
         * Locking-aware wrapper for underlying database method.
         */
        public SynchronizedStatement compileStatement(@NonNull final String sql) {
            SyncLock l = null;
            if (mTxLock != null) {
                if (mTxLock.getType() != LockTypes.exclusive) {
                    throw new RuntimeException("Compile inside shared TX");
                }
            } else {
                l = mSync.getExclusiveLock();
            }

            try {
                return new SynchronizedStatement(this, sql);
            } finally {
                if (l != null) {
                    l.unlock();
                }
            }
        }

        /**
         * @return the underlying SQLiteDatabase object.
         */
        public SQLiteDatabase getUnderlyingDatabase() {
            return mDb;
        }

        /**
         * Wrapper.
         */
        public boolean inTransaction() {
            return mDb.inTransaction();
        }

        /**
         * Locking-aware wrapper for underlying database method.
         *
         * @param isUpdate Indicates if updates will be done in TX
         *
         * @return the lock
         */
        public SyncLock beginTransaction(final boolean isUpdate) {
            SyncLock l;
            if (isUpdate) {
                l = mSync.getExclusiveLock();
            } else {
                l = mSync.getSharedLock();
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
                    throw new RuntimeException("Starting a transaction when one is already started");
                }

                mDb.beginTransaction();
            } catch (Exception e) {
                l.unlock();
                throw new RuntimeException("Unable to start database transaction: " + e.getMessage(), e);
            }
            mTxLock = l;
            return l;
        }

        /**
         * Locking-aware wrapper for underlying database method.
         *
         * @param l Lock returned from BeginTransaction().
         */
        public void endTransaction(@NonNull final SyncLock l) {
            if (mTxLock == null) {
                throw new RuntimeException("Ending a transaction when none is started");
            }
            if (!mTxLock.equals(l)) {
                throw new RuntimeException("Ending a transaction with wrong transaction lock");
            }

            try {
                mDb.endTransaction();
            } finally {
                // Clear mTxLock before unlocking so another thread does not
                // see the old lock when it gets the lock
                mTxLock = null;
                l.unlock();
            }
        }

        /**
         * Wrapper for underlying database method.
         */
        public void setTransactionSuccessful() {
            mDb.setTransactionSuccessful();
        }

        /**
         * Wrapper for underlying database method.
         */
        public boolean isOpen() {
            return mDb.isOpen();
        }

        /**
         * @return the underlying synchronizer object.
         */
        public Synchronizer getSynchronizer() {
            return mSync;
        }

        /**
         * Interface to an object that can return an open SQLite database object
         *
         * @author pjw
         */
        private interface DbOpener {
            SQLiteDatabase open();
        }

        /**
         * Factory for Synchronized Cursor objects. This can be subclassed by other
         * Cursor implementations.
         *
         * @author Philip Warner
         */
        public class SynchronizedCursorFactory implements CursorFactory {
            @Override
            public SynchronizedCursor newCursor(final SQLiteDatabase db,
                                                final SQLiteCursorDriver masterQuery,
                                                final String editTable,
                                                final SQLiteQuery query) {
                return new SynchronizedCursor(masterQuery, editTable, query, mSync);
            }
        }
    }

    /**
     * Wrapper for statements that ensures locking is used.
     *
     * @author Philip Warner
     */
    public static class SynchronizedStatement implements Closeable {

        /** insert calls indicate failure by returning -1L */
        public static final long INSERT_FAILED = -1L;

        /** Synchronizer from database */
        final Synchronizer mSync;
        /** Underlying statement */
        final SQLiteStatement mStatement;
        /** Indicates this is a 'read-only' statement */
        final boolean mIsReadOnly;
        /** Copy of SQL used for debugging */
        private final String mSql;
        /** Indicates close() has been called */
        private boolean mIsClosed = false;

        private SynchronizedStatement(@NonNull final SynchronizedDb db, @NonNull final String sql) {
            mSync = db.getSynchronizer();
            mSql = sql;

            mIsReadOnly = sql.trim().toLowerCase().startsWith("select");
            mStatement = db.getUnderlyingDatabase().compileStatement(sql);

            if (DEBUG_SWITCHES.DB_SYNC && BuildConfig.DEBUG) {
                System.out.println("SynchronizedStatement(new): " + sql + "\n\n");
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
        public void bindString(final int index, final String value) {
            if (value == null) {
                if (BuildConfig.DEBUG) {
                    Logger.printStackTrace("binding NULL");
                }
                mStatement.bindNull(index);
            } else {
                mStatement.bindString(index, value);
            }
        }

        /**
         * Wrapper for underlying method on SQLiteStatement.
         */
        public void bindBlob(final int index, @Nullable final byte[] value) {
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
        public void clearBindings() {
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
         * For example, SELECT COUNT(*) FROM table;
         *
         * @return The result of the query.
         *
         * @throws android.database.sqlite.SQLiteDoneException if the query returns zero rows
         */
        public long simpleQueryForLong() throws SQLiteDoneException {
            SyncLock l = mSync.getSharedLock();
            try {
                long result =  mStatement.simpleQueryForLong();
                if (DB_SYNC_QUERY_FOR_LONG && BuildConfig.DEBUG && result <= 0 ) {
                    Logger.printStackTrace("simpleQueryForLong got: " + result);
                }
                return result;
            } finally {
                l.unlock();
            }
        }
        /**
         * Syntax sugar
         *
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute a statement that returns a 1 by 1 table with a numeric value.
         * For example, SELECT COUNT(*) FROM table;
         *
         * @return The result of the query, or 0 when no rows found
         */
        public long simpleQueryForLongOrZero() {
            SyncLock l = mSync.getSharedLock();
            try {
                long result =  mStatement.simpleQueryForLong();
                if (DB_SYNC_QUERY_FOR_LONG && BuildConfig.DEBUG && result <= 0 ) {
                    Logger.printStackTrace("simpleQueryForLongOrZero got: " + result);
                }
                return result;
            } catch (SQLiteDoneException ignore) {
                return 0;
            } finally {
                l.unlock();
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
         *
         * @throws android.database.sqlite.SQLiteDoneException if the query returns zero rows
         */
        public long count() throws SQLiteDoneException {
            if (DB_SYNC_QUERY_FOR_LONG && BuildConfig.DEBUG) {
                if (!mSql.toLowerCase().startsWith("select count(")) {
                    Logger.printStackTrace("count statement not a count?");
                }
            }
            return this.simpleQueryForLong();
        }

        /**
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute a statement that returns a 1 by 1 table with a text value.
         * For example, SELECT COUNT(*) FROM table;
         *
         * @return The result of the query.
         *
         * @throws android.database.sqlite.SQLiteDoneException if the query returns zero rows
         */
        @NonNull
        public String simpleQueryForString() throws SQLiteDoneException {
            SyncLock l = mSync.getSharedLock();
            try {
                return mStatement.simpleQueryForString();
            } finally {
                l.unlock();
            }
        }

        /**
         * Syntax sugar
         *
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute a statement that returns a 1 by 1 table with a text value.
         * For example, SELECT COUNT(*) FROM table;
         *
         * @return The result of the query.
         *
         * @throws android.database.sqlite.SQLiteDoneException if the query returns zero rows
         */
        @Nullable
        public String simpleQueryForStringOrNull() {
            SyncLock l = mSync.getSharedLock();
            try {
                return mStatement.simpleQueryForString();
            } catch (SQLiteDoneException e) {
                if (BuildConfig.DEBUG) {
                    Logger.printStackTrace("simpleQueryForStringOrNull got NULL");
                }
                return null;
            } finally {
                l.unlock();
            }
        }
        /**
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute this SQL statement, if it is not a SELECT / INSERT / DELETE / UPDATE, for example
         * CREATE / DROP table, view, trigger, index etc.
         *
         * @throws android.database.SQLException If the SQL string is invalid for
         *         some reason
         */
        public void execute() throws SQLException {
            SyncLock l;
            if (mIsReadOnly) {
                l = mSync.getSharedLock();
            } else {
                l = mSync.getExclusiveLock();
            }
            try {
                if (DEBUG_SWITCHES.DB_SYNC && BuildConfig.DEBUG) {
                    System.out.println("SynchronizedStatement execute: " + mStatement);
                }
                mStatement.execute();
            } finally {
                l.unlock();
            }
        }

        /**
         * Wrapper that uses a lock before calling underlying method on SQLiteStatement.
         *
         * Execute this SQL statement and return the ID of the row inserted due to this call.
         * The SQL statement should be an INSERT for this to be a useful call.
         *
         * @return the row ID of the last row inserted, if this insert is successful. {@link #INSERT_FAILED} otherwise.
         *
         * @throws android.database.SQLException If the SQL string is invalid for
         *         some reason
         */
        public long executeInsert() throws SQLException {
            SyncLock l = mSync.getExclusiveLock();
            try {
                return mStatement.executeInsert();
            } finally {
                l.unlock();
            }
        }

        public void finalize() {
            if (!mIsClosed && DEBUG_SWITCHES.DB_SYNC && BuildConfig.DEBUG) {
                Logger.logError(new RuntimeException("DbSync.SynchronizedStatement: Finalizing non-closed statement (potential error/normal)" + (DEBUG_SWITCHES.DB_SYNC ? ": " + mSql : "")));
            }

            // Try to close the underlying statement.
            try {
                mStatement.close();
            } catch (Exception ignore) {
                // Ignore; may have been finalized
            }
        }
    }

    /**
     * Cursor wrapper that tries to apply locks as necessary. Unfortunately, most cursor
     * movement methods are final and, if they involve any database locking, could theoretically
     * still result in 'database is locked' exceptions. So far in testing, none have occurred.
     */
    public static class SynchronizedCursor extends SQLiteCursor {
        private final Synchronizer mSync;
        private int mCount = -1;

        public SynchronizedCursor(@NonNull final SQLiteCursorDriver driver,
                                  @NonNull final String editTable,
                                  @NonNull final SQLiteQuery query,
                                  @NonNull final Synchronizer sync) {
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
                SyncLock l = mSync.getSharedLock();
                try {
                    mCount = super.getCount();
                } finally {
                    l.unlock();
                }
            }
            return mCount;
        }

        /**
         * Wrapper that uses a lock before calling underlying method.
         */
        @Override
        public boolean requery() {
            SyncLock l = mSync.getSharedLock();
            try {
                return super.requery();
            } finally {
                l.unlock();
            }
        }
    }
}
