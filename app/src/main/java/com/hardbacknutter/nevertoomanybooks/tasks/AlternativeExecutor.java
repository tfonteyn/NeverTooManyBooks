/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.CoverBrowserDialogFragment;

/**
 * A copy of {@link AsyncTask#THREAD_POOL_EXECUTOR} with lower configuration numbers.
 * <p>
 * Main (only?) purpose is to provide a <strong>second</strong> Executor.
 * This allows to run specific tasks that we don't want to submit (and wait on) the
 * shared one in AsyncTask.
 * <p>
 * <br>Example: {@link CoverBrowserDialogFragment} uses the default one
 * to get thumbnails, but uses this alternative one to get the larger
 * preview image without waiting in the shared queue.
 */
public final class AlternativeExecutor {

    public static Executor create(@NonNull final String threadName,
                                  final int poolWorkQueue) {
        // core pool size set to 1, instead of 2..4 (cpu dependent)
        final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                1, Runtime.getRuntime().availableProcessors(),
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(poolWorkQueue),
                new ThreadFactory() {
                    private final AtomicInteger mCount = new AtomicInteger(1);

                    public Thread newThread(@NonNull final Runnable r) {
                        return new Thread(r, threadName + '#' + mCount.getAndIncrement());
                    }
                });
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        return threadPoolExecutor;
    }

    private AlternativeExecutor() {
    }
}
