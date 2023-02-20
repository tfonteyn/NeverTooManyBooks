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
package com.hardbacknutter.nevertoomanybooks.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;

/**
 * <strong>Warning:</strong> To prevent unintended garbage collection, you must store
 * a strong reference to the listener. Do NOT simply pass a lambda, or anonymous class.
 * <p>
 * The task implementations store only a WeakReference to the listener which is good.
 * We don't want them to store a strong reference, as that could/would lead to memory leaks.
 * <ul>This does mean that the creator of the task must:
 *      <li>implemented the interface on the caller itself, and pass the caller; i.e. 'this'</li>
 *      <li>or create an instance variable in the caller of the listener type, and pass that</li>
 * </ul>
 *
 * @param <Result> type of the task result
 *                 The result, if any, computed in the task, can be {@code null}.
 *                 If the task was cancelled before starting the result
 *                 <strong>WILL ALWAYS BE {@code null}</strong>
 */
public interface TaskListener<Result> {

    /**
     * Called when a task finishes.
     *
     * @param taskId id
     * @param result result of the task
     */
    void onFinished(int taskId,
                    @Nullable Result result);

    /**
     * Called when a task was cancelled.
     *
     * @param taskId id
     * @param result (potential/partial) result of the task
     */
    void onCancelled(int taskId,
                     @Nullable Result result);

    /**
     * Called when a task failed.
     *
     * @param taskId id
     * @param e      which was thrown
     */
    void onFailure(int taskId,
                   @Nullable Throwable e);

    /**
     * Progress messages.
     *
     * @param message with updates
     */
    default void onProgress(@NonNull final TaskProgress message) {
        // ignore by default
    }
}
