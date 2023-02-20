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
package com.hardbacknutter.nevertoomanybooks.core.tasks;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ASyncExecutor {

    public static final ExecutorService SERVICE;

    /**
     * An {@link Executor} that can be used to execute tasks in parallel.
     * This is also where the serialized tasks run.
     * <p>
     * <strong>Note:</strong> this executor uses an unbounded {@link LinkedBlockingQueue}.
     */
    @NonNull
    public static final Executor MAIN;
    /**
     * An {@link Executor} that executes tasks one at a time in serial
     * order.  This serialization is global to a particular process.
     * Actual execution is done on {@link #MAIN}.
     */
    @SuppressWarnings("WeakerAccess")
    public static final Executor SERIAL = new SerialExecutor();
    /** Log tag. */
    private static final String TAG = "ASyncExecutor";
    // We keep only a single pool thread around all the time.
    // We let the pool grow to a fairly large number of threads if necessary,
    // but let them time out quickly. In the unlikely case that we run out of threads,
    // we fall back to a simple unbounded-queue executor.
    // This combination ensures that:
    // 1. We normally keep few threads (1) around.
    // 2. We queue only after launching a significantly larger, but still bounded, set of threads.
    // 3. We keep the total number of threads bounded, but still allow an unbounded set
    //    of tasks to be queued.
    private static final int CORE_POOL_SIZE = 1;
    private static final int MAXIMUM_POOL_SIZE = 20;
    private static final int KEEP_ALIVE_SECONDS = 3;

    private static final int BACKUP_POOL_SIZE = 5;

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger threadIdCounter = new AtomicInteger(1);

        @NonNull
        public Thread newThread(@NonNull final Runnable r) {
            return new Thread(r, "CustomTask #" + threadIdCounter.getAndIncrement());
        }
    };

    /** Used for rejected executions. Initialization protected by sRunOnSerialPolicy lock. */
    private static ThreadPoolExecutor sBackupExecutor;
    private static final RejectedExecutionHandler REJECTED_EXECUTION_HANDLER =
            new RejectedExecutionHandler() {
                public void rejectedExecution(@NonNull final Runnable r,
                                              @NonNull final ThreadPoolExecutor e) {
                    Log.w(TAG, "Exceeded ThreadPoolExecutor pool size");
                    // As a last ditch fallback, run it on an executor with an unbounded queue.
                    // Create this executor lazily, hopefully almost never.
                    synchronized (this) {
                        if (sBackupExecutor == null) {
                            sBackupExecutor = new ThreadPoolExecutor(
                                    BACKUP_POOL_SIZE, BACKUP_POOL_SIZE,
                                    KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                                    new LinkedBlockingQueue<>(), THREAD_FACTORY);
                            sBackupExecutor.allowCoreThreadTimeOut(true);
                        }
                    }
                    sBackupExecutor.execute(r);
                }
            };

    static {
        // Values copied from the android.os.ASyncTask code
        final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new SynchronousQueue<>(), THREAD_FACTORY);
        threadPoolExecutor.setRejectedExecutionHandler(REJECTED_EXECUTION_HANDLER);
        MAIN = threadPoolExecutor;

        SERVICE = Executors.newCachedThreadPool(THREAD_FACTORY);
    }

    private ASyncExecutor() {
    }

    /**
     * Create a <strong>new</strong> Executor.
     * This allows to run specific tasks that we don't want to submit (and wait on) the
     * shared one.
     * <p>
     * <strong>Note:</strong> this executor uses a {@link SynchronousQueue}.
     *
     * @param threadName to use for the ThreadFactory
     *
     * @return a new Executor
     */
    @NonNull
    public static Executor create(@NonNull final String threadName) {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), new ThreadFactory() {
            private final AtomicInteger threadIdCounter = new AtomicInteger(1);

            @NonNull
            public Thread newThread(@NonNull final Runnable r) {
                return new Thread(r, threadName + '#' + threadIdCounter.getAndIncrement());
            }
        });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    static class SerialExecutor
            implements Executor {

        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Nullable
        private Runnable active;

        public synchronized void execute(@NonNull final Runnable r) {
            tasks.offer(() -> {
                try {
                    r.run();
                } finally {
                    scheduleNext();
                }
            });
            if (active == null) {
                scheduleNext();
            }
        }

        synchronized void scheduleNext() {
            active = tasks.poll();
            if (active != null) {
                MAIN.execute(active);
            }
        }
    }
}
