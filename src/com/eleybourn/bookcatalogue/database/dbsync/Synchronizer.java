package com.eleybourn.bookcatalogue.database.dbsync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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
public class Synchronizer {

    /** Main lock for synchronization. */
    private final ReentrantLock mLock = new ReentrantLock();
    /** Condition fired when a reader releases a lock. */
    private final Condition mReleased = mLock.newCondition();
    /** Collection of threads that have shared locks. */
    private final Map<Thread, Integer> mSharedOwners =
            Collections.synchronizedMap(new HashMap<Thread, Integer>());
    /** Lock used to pass back to consumers of shared locks. */
    private final SharedLock mSharedLock = new SharedLock();
    /** Lock used to pass back to consumers of exclusive locks. */
    private final ExclusiveLock mExclusiveLock = new ExclusiveLock();

    /**
     * Routine to purge shared locks held by dead threads.
     * Can only be called while mLock is held.
     */
    private void purgeOldLocks() {
        if (!mLock.isHeldByCurrentThread()) {
            throw new LockException("Can not cleanup old locks if not locked");
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
            int count;
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
    private void releaseSharedLock() {
        final Thread t = Thread.currentThread();
        //Logger.debug(t.getName() + " releasing SHARED lock");
        mLock.lock();
        //Logger.info(t.getName() + " locked lock held by " + mLock.getHoldCount());
        try {
            if (mSharedOwners.containsKey(t)) {
                int count = mSharedOwners.get(t) - 1;
                //Logger.info(t.getName() + " now has " + count + " SHARED locks");
                if (count < 0) {
                    throw new LockException("Release a lock count already zero");
                }
                if (count != 0) {
                    mSharedOwners.put(t, count);
                } else {
                    mSharedOwners.remove(t);
                    mReleased.signal();
                }
            } else {
                throw new LockException("Release a lock when not held");
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
                //Logger.debug(t.getName() + " requesting EXCLUSIVE lock with "
                // + mSharedOwners.size() + " shared locks (attempt #" + i + ")");
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
                } catch (InterruptedException | RuntimeException e) {
                    // Probably happens because thread was interrupted. Just die.
                    try {
                        mLock.unlock();
                    } catch (RuntimeException ignored) {
                    }
                    throw new LockException("Unable to get exclusive lock", e);
                }
            }
        } finally {
            if (DEBUG_SWITCHES.TIMERS && BuildConfig.DEBUG) {
                if (mLock.isHeldByCurrentThread()) {
                    Logger.info(this,
                                ourThread.getName() + " waited "
                                        + (System.currentTimeMillis() - t0)
                                        + "ms for EXCLUSIVE access");
                } else {
                    Logger.info(this,
                                ourThread.getName() + " waited "
                                        + (System.currentTimeMillis() - t0)
                                        + "ms AND FAILED TO GET EXCLUSIVE access");
                }
            }
        }
    }

    /**
     * Release the lock previously taken.
     */
    private void releaseExclusiveLock() {
        //Logger.info(Thread.currentThread().getName() + " releasing EXCLUSIVE lock");
        if (!mLock.isHeldByCurrentThread()) {
            throw new LockException("Exclusive Lock is not held by this thread");
        }
        mLock.unlock();
        //Logger.info("Release lock held by " + mLock.getHoldCount());
        //Logger.info(t.getName() + " released EXCLUSIVE lock");
    }

    /** Enum of lock types supported. */
    public enum LockType {
        shared,
        exclusive
    }

    /**
     * Interface common to all lock types.
     */
    public interface SyncLock {

        void unlock();

        @NonNull
        LockType getType();
    }

    public static class LockException extends RuntimeException {
        private static final long serialVersionUID = -6266684663589932716L;

        LockException(@Nullable final String msg) {
            super(msg);
        }
        LockException(@SuppressWarnings("SameParameterValue") @Nullable final String msg,
                      @Nullable final Exception inner) {
            super(msg, inner);
        }
    }

    /**
     * Internal implementation of a Shared Lock.
     */
    private class SharedLock
            implements SyncLock {

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
     */
    private class ExclusiveLock
            implements SyncLock {

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
