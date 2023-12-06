/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.core.database;

import android.database.SQLException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of a Readers/Writer lock that is fully reentrant.
 * <p>
 * Because SQLite throws exception on locking conflicts, this class can be used to serialize
 * WRITE access while allowing concurrent read access.
 * <p>
 * Each logical database should have its own {@link Synchronizer}.
 * Before any read, or group of reads, a call to {@link #getSharedLock()} should be made.
 * A call to {@link #getExclusiveLock()} should be made before any update.
 * <p>
 * Multiple calls can be made as necessary so long as an {@link SyncLock#unlock()}
 * is called for all get*() calls by using the {@link SyncLock} object returned from
 * the get*() call.
 * <p>
 * These can be called in any order and locks in the current thread never block requests.
 * <p>
 * Deadlocks are not possible because the implementation involves a single lock object.
 * <p>
 * <strong>Note:</strong> This lock can cause writer starvation since it does not introduce
 * pending locks.
 */
public class Synchronizer {

    /** Main lock for synchronization. */
    private final ReentrantLock mainLock = new ReentrantLock();
    /** Condition fired when a reader releases a shared lock; see {@link #releaseSharedLock()}. */
    private final Condition lockReleased = mainLock.newCondition();

    /** Collection of threads that have shared locks. */
    private final Map<Thread, Integer> sharedLockOwners =
            Collections.synchronizedMap(new HashMap<>());
    /** Lock used to pass back to consumers of shared locks. */
    private final SyncLock sharedLock = new SharedLock();

    /** Lock used to pass back to consumers of exclusive locks. */
    private final SyncLock exclusiveLock = new ExclusiveLock();

    /**
     * Routine to purge shared locks held by dead threads.
     * Can only be called while {@link #mainLock} is held.
     *
     * @throws LockException on any failure
     */
    private void purgeOldLocks() {
        if (!mainLock.isHeldByCurrentThread()) {
            throw new LockException("Can not cleanup old locks if not locked");
        }

        for (final Thread thread : sharedLockOwners.keySet()) {
            if (!thread.isAlive()) {
                sharedLockOwners.remove(thread);
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
        mainLock.lock();
        purgeOldLocks();
        try {
            Integer count = sharedLockOwners.get(thread);
            if (count != null) {
                count++;
            } else {
                count = 1;
            }
            sharedLockOwners.put(thread, count);
            return sharedLock;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Release a {@link SharedLock}. If no more locks in thread, remove from list.
     *
     * @throws LockException on any failure
     */
    private void releaseSharedLock() {
        final Thread thread = Thread.currentThread();
        mainLock.lock();
        try {
            Integer count = sharedLockOwners.get(thread);
            if (count != null) {
                count--;
                if (count < 0) {
                    throw new LockException("Releasing a lock with count already zero");
                }
                if (count != 0) {
                    sharedLockOwners.put(thread, count);
                } else {
                    sharedLockOwners.remove(thread);
                    lockReleased.signal();
                }
            } else {
                throw new LockException("Releasing a lock when not held");
            }
        } finally {
            mainLock.unlock();
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
     *
     * @throws LockException on any failure
     */
    @NonNull
    SyncLock getExclusiveLock() {
        final Thread thread = Thread.currentThread();
        // Synchronize with other code
        mainLock.lock();
        while (true) {
            // Cleanup any old threads that are dead.
            purgeOldLocks();
            //noinspection CheckStyle
            try {
                // Simple case -- no locks held, just return and keep the lock
                if (sharedLockOwners.isEmpty()) {
                    return exclusiveLock;
                }
                // Check for one lock, and it being this thread.
                if (sharedLockOwners.size() == 1 && sharedLockOwners.containsKey(thread)) {
                    // One locker, and it is us...so upgrade is OK.
                    return exclusiveLock;
                }

                // Someone else has it. Wait.
                lockReleased.await();

            } catch (@NonNull final InterruptedException | RuntimeException e) {
                // Probably happens because thread was interrupted. Just die.
                mainLock.unlock();
                throw new LockException("Unable to get exclusive lock", e);
            }
        }
    }

    /**
     * Release the {@link ExclusiveLock} previously taken.
     *
     * @throws LockException on any failure
     */
    private void releaseExclusiveLock() {
        if (!mainLock.isHeldByCurrentThread()) {
            throw new LockException("Exclusive Lock is not held by this thread");
        }
        mainLock.unlock();
    }

    enum LockType {
        Shared,
        Exclusive
    }

    /**
     * Interface common to all lock types.
     */
    public interface SyncLock {

        void unlock();

        @NonNull
        LockType getType();
    }

    static class LockException
            extends SQLException {

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
    class SharedLock
            implements SyncLock {

        @Override
        public void unlock() {
            releaseSharedLock();
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        @NonNull
        public LockType getType() {
            return LockType.Shared;
        }
    }

    /**
     * Internal implementation of an Exclusive Lock.
     */
    class ExclusiveLock
            implements SyncLock {

        @Override
        public void unlock() {
            releaseExclusiveLock();
        }

        @SuppressWarnings("SameReturnValue")
        @Override
        @NonNull
        public LockType getType() {
            return LockType.Exclusive;
        }
    }
}
