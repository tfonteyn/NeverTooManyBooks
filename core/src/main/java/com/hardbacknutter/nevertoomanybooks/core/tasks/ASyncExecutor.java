/*
 * @Copyright 2018-2025 HardBackNutter
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
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.util.logger.BuildConfig;

public final class ASyncExecutor {

    /**
     * Dedicated {@link ExecutorService} for accessing the network.
     * <p>
     * <strong>Note:</strong> this executor uses an unbounded
     * <strong>FIFO</strong> {@link BlockingQueue}.
     */
    public static final ExecutorService NETWORK;

    /**
     * General purpose {@link ExecutorService} that can be used to execute tasks in parallel.
     * This is also where the serialized tasks run.
     * <p>
     * <strong>Note:</strong> this executor uses a bounded
     * <strong>FIFO</strong> {@link BlockingQueue}.
     * <p>
     * Dev. note: it's configured identical to the deprecated {@code android.os.ASyncTask}
     * including a backup-executor for rejections.
     */
    @NonNull
    public static final ExecutorService MAIN;

    /**
     * An {@link Executor} that executes tasks one at a time in serial order.
     * This serialization is global to the app.
     * Actual execution is done on {@link #MAIN}.
     */
    @SuppressWarnings("WeakerAccess")
    public static final Executor SERIAL;

    /**
     * Dedicated {@link ExecutorService} for loading & scaling images
     * from the file-system or database.
     * <p>
     * <strong>Note:</strong> this executor uses an unbounded
     * <strong>LIFO</strong> {@link BlockingDeque}.
     */
    @NonNull
    public static final ExecutorService IMAGES;

    /** Log tag. */
    private static final String TAG = "ASyncExecutor";

    // These values copied from the android.os.ASyncTask code
    private static final int CORE_POOL_SIZE = 1;
    private static final int MAXIMUM_POOL_SIZE = 20;
    private static final int KEEP_ALIVE_SECONDS = 3;
    private static final int BACKUP_POOL_SIZE = 5;

    /**
     * Used for rejected executions from the {@link #MAIN} service.
     */
    private static ThreadPoolExecutor sBackupExecutor;
    private static final RejectedExecutionHandler REJECTED_EXECUTION_HANDLER =
            new RejectedExecutionHandler() {
                public void rejectedExecution(@NonNull final Runnable r,
                                              @NonNull final ThreadPoolExecutor e) {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.w(TAG, "Exceeded ThreadPoolExecutor pool size");
                    }
                    // As a last ditch fallback, run it on an executor with an unbounded queue.
                    // Create this executor lazily, hopefully almost never.
                    synchronized (this) {
                        if (sBackupExecutor == null) {
                            sBackupExecutor = new ThreadPoolExecutor(
                                    BACKUP_POOL_SIZE, BACKUP_POOL_SIZE,
                                    KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                                    new LinkedBlockingQueue<>(),
                                    createThreadFactory("BACKUP_EXECUTOR"));
                            sBackupExecutor.allowCoreThreadTimeOut(true);
                        }
                    }
                    sBackupExecutor.execute(r);
                }
            };

    static {
        final ThreadPoolExecutor main = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                createThreadFactory("MAIN"));
        main.setRejectedExecutionHandler(REJECTED_EXECUTION_HANDLER);
        MAIN = main;

        SERIAL = new SerialExecutor(MAIN);

        NETWORK = Executors.newCachedThreadPool(createThreadFactory("NETWORK"));

        final int corePoolSize = Runtime.getRuntime().availableProcessors();
        IMAGES = new LifoThreadPoolExecutor(corePoolSize, corePoolSize * 2,
                                            1, TimeUnit.SECONDS,
                                            new LinkedBlockingDeque<>(),
                                            createThreadFactory("IMAGES"));
    }

    private ASyncExecutor() {
    }

    /**
     * Create a <strong>new</strong> ThreadFactory.
     *
     * @param threadName to use for the base thread names
     *
     * @return a new ThreadFactory
     */
    @NonNull
    private static ThreadFactory createThreadFactory(@NonNull final String threadName) {
        return new ThreadFactory() {
            private final AtomicInteger threadIdCounter = new AtomicInteger();

            @NonNull
            public Thread newThread(@NonNull final Runnable r) {
                return new Thread(r, threadName + "#" + threadIdCounter.incrementAndGet());
            }
        };
    }

    /**
     * Create a <strong>new</strong> ExecutorService.
     * This allows to run specific tasks separate from any shared/preconfigured executor.
     * <p>
     * <strong>Note:</strong> this ExecutorService uses a bound {@link BlockingQueue}.
     *
     * @param threadName to use for the ThreadFactory base thread names
     *
     * @return a new ExecutorService
     */
    @NonNull
    public static ExecutorService create(@NonNull final String threadName) {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                createThreadFactory(threadName));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    static class SerialExecutor
            implements Executor {

        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @NonNull
        private final Executor executor;
        @Nullable
        private Runnable active;

        SerialExecutor(@NonNull final Executor executor) {
            this.executor = executor;
        }

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
                executor.execute(active);
            }
        }
    }
}
