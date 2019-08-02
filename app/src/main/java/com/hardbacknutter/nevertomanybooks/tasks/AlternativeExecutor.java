package com.hardbacknutter.nevertomanybooks.tasks;

import android.os.AsyncTask;

import androidx.annotation.NonNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertomanybooks.CoverBrowserFragment;

/**
 * A copy of {@link AsyncTask#THREAD_POOL_EXECUTOR} with lower configuration numbers.
 * <p>
 * Main (only?) purpose is to provide a <strong>second</strong> Executor.
 * This allows to run specific tasks that we don't want to submit (and wait on) the
 * shared one in AsyncTask.
 * <p>
 * <br>Example: {@link CoverBrowserFragment} uses the default one to get thumbnails, but uses this
 * alternative one to get the larger preview image without waiting in the shared queue.
 */
public final class AlternativeExecutor {

    /**
     * An {@link Executor} that can be used to execute tasks in parallel.
     */
    public static final Executor THREAD_POOL_EXECUTOR;

    /** Same as original. */
    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(@NonNull final Runnable r) {
            return new Thread(r, "AlternativeTask #" + mCount.getAndIncrement());
        }
    };

    /** Only 16 instead of 128. */
    private static final BlockingQueue<Runnable> POOL_WORK_QUEUE =
            new LinkedBlockingQueue<>(16);

    static {
        // core pool size set to 1, instead of 2..4 (cpu dependent)
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                1, Runtime.getRuntime().availableProcessors(),
                30, TimeUnit.SECONDS, POOL_WORK_QUEUE, THREAD_FACTORY);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }

    private AlternativeExecutor() {
    }
}
