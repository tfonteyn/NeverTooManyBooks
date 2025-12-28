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

import androidx.annotation.NonNull;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ASyncExecutor {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    // Allow at least 2 threads
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = 20;

    // Based on internet 3 seconds was too short and 30 or 60 seems to be more widely used...
    // i.o.w a wild guess
    private static final int KEEP_ALIVE_SECONDS = 60;

    /**
     * Dedicated {@link ExecutorService} for accessing the network.
     * <p>
     * <strong>Note:</strong> this executor uses an unbounded
     * <strong>FIFO</strong> {@link BlockingQueue}.
     */
    public static final ExecutorService NETWORK;

    /**
     * General purpose {@link ExecutorService} that can be used to execute tasks in parallel.
     * <p>
     * <strong>Note:</strong> this executor uses a bounded
     * <strong>FIFO</strong> {@link BlockingQueue}.
     */
    @NonNull
    public static final ExecutorService PARALLEL;

    /**
     * An {@link Executor} that executes tasks <strong>one at a time</strong> in serial order.
     * This serialization is global to the app.
     * <p>
     * The main purpose would be storage writes.
     */
    @SuppressWarnings("WeakerAccess")
    public static final ExecutorService STORAGE_WRITES;

    /**
     * Dedicated {@link ExecutorService} for <strong>loading & scaling</strong> images
     * from the file-system or database. Do <strong>NOT</strong> use for writing.
     * <p>
     * <strong>Note:</strong> this executor uses an unbounded
     * <strong>LIFO</strong> {@link BlockingDeque}.
     */
    @NonNull
    public static final ExecutorService IMAGES;



    static {
        STORAGE_WRITES = Executors.newSingleThreadExecutor(
                createThreadFactory("STORAGE_WRITES",
                                    android.os.Process.THREAD_PRIORITY_DEFAULT));

        // Higher priority than images and network, but less than the UI.
        final ThreadPoolExecutor main = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(128),
                createThreadFactory("PARALLEL",
                                    android.os.Process.THREAD_PRIORITY_BACKGROUND));
        main.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        PARALLEL = main;

        // Higher priority than images, but less than the UI.
        NETWORK = Executors.newCachedThreadPool(
                createThreadFactory("NETWORK",
                                    android.os.Process.THREAD_PRIORITY_BACKGROUND));

        final LifoThreadPoolExecutor images = new LifoThreadPoolExecutor(
                CPU_COUNT, CPU_COUNT,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(),
                createThreadFactory("IMAGES",
                                    android.os.Process.THREAD_PRIORITY_LOWEST));
        images.allowCoreThreadTimeOut(true);
        IMAGES = images;
    }

    private ASyncExecutor() {
    }

    /**
     * Create a <strong>new</strong> ThreadFactory.
     *
     * @param threadName to use for the base thread names
     * @param priority   one of the {@link android.os.Process#THREAD_PRIORITY_LOWEST} values.
     *
     * @return a new ThreadFactory
     */
    @NonNull
    private static ThreadFactory createThreadFactory(@NonNull final String threadName,
                                                     final int priority) {
        return new ThreadFactory() {
            private final AtomicInteger threadIdCounter = new AtomicInteger();

            @SuppressWarnings({"CheckStyle", "OverlyBroadCatchBlock"})
            @NonNull
            public Thread newThread(@NonNull final Runnable r) {
                // We wrap the runnable so the priority is set
                // the moment the thread is actually started
                final Runnable priorityWrapper = () -> {
                    try {
                        // This is the Android-specific Linux 'Nice' call
                        android.os.Process.setThreadPriority(priority);
                    } catch (@NonNull final Throwable ignored) {
                        // ignore
                    }
                    r.run();
                };

                final Thread t = new Thread(priorityWrapper,
                                            threadName + "#" + threadIdCounter.incrementAndGet());

                t.setDaemon(true);
                return t;
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
     * @param priority   to use
     *
     * @return a new ExecutorService
     */
    @NonNull
    public static ExecutorService create(@NonNull final String threadName,
                                         final int priority) {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                createThreadFactory(threadName, priority));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
