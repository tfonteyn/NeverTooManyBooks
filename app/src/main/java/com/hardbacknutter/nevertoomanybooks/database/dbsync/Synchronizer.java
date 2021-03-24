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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of a Readers/Writer lock that is fully reentrant.
 * <p>
 * URGENT: is this still true ? Because SQLite throws exception on locking conflicts,
 * this class can be used to serialize
 * WRITE access while allowing concurrent read access.
 * <p>
 * Each logical database should have its own {@link Synchronizer}
 * Before any read, or group of reads, a call to getSharedLock() should be made.
 * A call to getExclusiveLock() should be made before any update.
 * Multiple calls can be made as necessary so long as an unlock() is called for all get*()
 * calls by using the SyncLock object returned from the get*() call.
 * <p>
 * These can be called in any order and locks in the current thread never block requests.
 * <p>
 * Deadlocks are not possible because the implementation involves a single lock object.
 * <p>
 * <strong>Note:</strong> This lock can cause writer starvation since it does not introduce
 * pending locks.
 */
public class Synchronizer {

    static final int LOCK_SHARED = 1;
    static final int LOCK_EXCLUSIVE = 2;

    /** Main lock for synchronization. */
    private final ReentrantLock mLock = new ReentrantLock();
    /** Condition fired when a reader releases a lock. */
    private final Condition mReleased = mLock.newCondition();
    /** Collection of threads that have shared locks. */
    private final Map<Thread, Integer> mSharedOwners =
            Collections.synchronizedMap(new HashMap<>());

    /** Lock used to pass back to consumers of shared locks. */
    private final SyncLock mSharedLock = new SharedLock();
    /** Lock used to pass back to consumers of exclusive locks. */
    private final SyncLock mExclusiveLock = new ExclusiveLock();

    /**
     * Routine to purge shared locks held by dead threads.
     * Can only be called while mLock is held.
     */
    private void purgeOldLocks() {
        if (!mLock.isHeldByCurrentThread()) {
            throw new LockException("Can not cleanup old locks if not locked");
        }

        for (final Thread thread : mSharedOwners.keySet()) {
            if (!thread.isAlive()) {
                mSharedOwners.remove(thread);
            }
        }
    }

    /**
     * Add a new {@link SharedLock} to the collection and return it.
     *
     * @return lock
     */
    @NonNull
    SyncLock getSharedLock() {
        final Thread thread = Thread.currentThread();
        mLock.lock();
        purgeOldLocks();
        try {
            Integer count = mSharedOwners.get(thread);
            if (count != null) {
                count++;
            } else {
                count = 1;
            }
            mSharedOwners.put(thread, count);
            return mSharedLock;
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Release a {@link SharedLock}. If no more locks in thread, remove from list.
     */
    private void releaseSharedLock() {
        final Thread thread = Thread.currentThread();
        mLock.lock();
        try {
            Integer count = mSharedOwners.get(thread);
            if (count != null) {
                count--;
                if (count < 0) {
                    throw new LockException("Releasing a lock with count already zero");
                }
                if (count != 0) {
                    mSharedOwners.put(thread, count);
                } else {
                    mSharedOwners.remove(thread);
                    mReleased.signal();
                }
            } else {
                throw new LockException("Releasing a lock when not held");
            }
        } finally {
            mLock.unlock();
        }
    }

    /**
     * Return the {@link ExclusiveLock} when exclusive access is available.
     * <ol>
     *      <li>take a lock on the collection</li>
     *      <li>see if there are any other locks</li>
     *      <li>if not, return with the lock still held -- this prevents more EX or SH locks.</li>
     *      <li>if there are other SH locks, wait for one to be release and loop.</li>
     * </ol>
     *
     * @return lock
     */
    @NonNull
    SyncLock getExclusiveLock() {
        final Thread thread = Thread.currentThread();
        // Synchronize with other code
        mLock.lock();
        while (true) {
            // Cleanup any old threads that are dead.
            purgeOldLocks();
            try {
                // Simple case -- no locks held, just return and keep the lock
                if (mSharedOwners.isEmpty()) {
                    return mExclusiveLock;
                }
                // Check for one lock, and it being this thread.
                if (mSharedOwners.size() == 1 && mSharedOwners.containsKey(thread)) {
                    // One locker, and it is us...so upgrade is OK.
                    return mExclusiveLock;
                }

                // Someone else has it. Wait.
                mReleased.await();

            } catch (@NonNull final InterruptedException | RuntimeException e) {
                // Probably happens because thread was interrupted. Just die.
                mLock.unlock();
                throw new LockException("Unable to get exclusive lock", e);
            }
        }
    }

    /**
     * Release the {@link ExclusiveLock} previously taken.
     */
    private void releaseExclusiveLock() {
        if (!mLock.isHeldByCurrentThread()) {
            throw new LockException("Exclusive Lock is not held by this thread");
        }
        mLock.unlock();
    }

    @IntDef({LOCK_SHARED, LOCK_EXCLUSIVE})
    @Retention(RetentionPolicy.SOURCE)
    @interface LockType {

    }

    /**
     * Interface common to all lock types.
     */
    public interface SyncLock {

        void unlock();

        @LockType
        int getType();
    }

    static class LockException
            extends RuntimeException {

        private static final long serialVersionUID = -6266684663589932716L;

        LockException(@Nullable final String message) {
            super(message);
        }

        LockException(@SuppressWarnings("SameParameterValue") @Nullable final String message,
                      @Nullable final Exception cause) {
            super(message, cause);
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
        @Override
        @LockType
        public int getType() {
            return LOCK_SHARED;
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
        @Override
        @LockType
        public int getType() {
            return LOCK_EXCLUSIVE;
        }
    }
}
