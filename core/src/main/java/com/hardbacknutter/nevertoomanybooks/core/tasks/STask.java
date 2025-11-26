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

import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import java.io.UncheckedIOException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.core.database.UncheckedDaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.network.UncheckedSAXException;
import com.hardbacknutter.nevertoomanybooks.core.storage.UncheckedStorageException;

/**
 * A simplified alternative to the {@link MTask}.
 * <p>
 * Provides the equivalent of onFinished/onFailure
 * but does not support cancellation or progress updates.
 */
public final class STask {

    public static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());

    private STask() {
    }

    /**
     * Runs the work on an executor and returns success or failure on the UI thread.
     *
     * @param executor   to use
     * @param worker     code to run
     * @param onFinished callback with the result
     * @param onFailure  callback with an Exception, if it's an 'Unchecked' exception
     *                   it will be unpacked and the actual cause will be passed in instead.
     * @param <T>        result type
     */
    @UiThread
    public static <T> void execute(@NonNull final Executor executor,
                                   @NonNull final Supplier<T> worker,
                                   @NonNull final Consumer<T> onFinished,
                                   @NonNull final Consumer<Throwable> onFailure) {
        executor.execute(() -> {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            //noinspection CheckStyle
            try {
                final T result = worker.get();
                UI_HANDLER.post(() -> onFinished.accept(result));
            } catch (@NonNull final UncheckedIOException
                                    | UncheckedStorageException
                                    | UncheckedDaoWriteException
                                    | UncheckedSAXException e) {
                UI_HANDLER.post(() -> onFailure.accept(e.getCause()));
            } catch (@NonNull final Throwable t) {
                UI_HANDLER.post(() -> onFailure.accept(t));
            }
        });
    }

    /**
     * Check if the current thread <strong>is</strong> the UiThread.
     *
     * @return {@code true} if {@code true}   (duh...)
     */
    public static boolean isUiThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }
}
