/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
package com.hardbacknutter.nevertoomanybooks.tasks;

import android.os.AsyncTask;

import androidx.annotation.NonNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.CoverBrowserFragment;

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
