/*
 * @Copyright 2019 HardBackNutter
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
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;

/**
 * Warning: To prevent unintended garbage collection, you must store a strong reference to
 * the listener.
 * <p>
 * The AsyncTask implementations as used, store only a WeakReference to the listener which is good.
 * We don't want them to store a strong reference, as that could/would lead to memory leaks.
 * <ul>This does mean that the creator of the AsyncTask must:
 * <li>implemented the interface on the caller itself, and pass the caller; i.e. 'this'</li>
 * <li>or create an instance variable in the caller of the listener type, and pass that</li>
 * </ul>
 * <strong>Do NOT simply pass a lambda, or anonymous class.</strong>
 * <br>
 *
 * @param <Result> type of the task result
 */
public interface TaskListener<Result> {

    /**
     * Called when a task finishes.
     */
    @UiThread
    void onTaskFinished(@NonNull TaskFinishedMessage<Result> message);

    /**
     * Optional progress messages.
     */
    @UiThread
    default void onTaskProgress(@NonNull final TaskProgressMessage message) {
        // do nothing.
        if (BuildConfig.DEBUG /* always */) {
            throw new IllegalStateException("taskId=" + message.taskId);
        }
    }

    /** Possible outcomes as passed in {@link TaskFinishedMessage}. */
    enum TaskStatus {
        Success, Cancelled, Failed
    }

    /**
     * Value class holding task-finished values.
     *
     * @param <Result> type of the actual result of the task.
     */
    class TaskFinishedMessage<Result> {

        public final int taskId;
        public final Result result;

        @NonNull
        public final TaskStatus status;
        @Nullable
        public final Exception exception;

        /**
         * Constructor.
         *
         * @param taskId    ID for the task which was provided at construction time.
         * @param status    Success, Failed, Cancelled, ...
         * @param result    the result object from the {@link AsyncTask}.
         *                  Nullable/NonNull is up to the implementation.
         * @param exception if the task finished with an exception, or {@code null}.
         */
        public TaskFinishedMessage(final int taskId,
                                   @NonNull final TaskStatus status,
                                   final Result result,
                                   @Nullable final Exception exception) {
            this.taskId = taskId;
            this.status = status;
            this.result = result;
            this.exception = exception;
        }

        @Override
        @NonNull
        public String toString() {
            return "TaskFinishedMessage{"
                   + "taskId=" + taskId
                   + ", status=" + status
                   + ", result=" + result
                   + ", exception=" + exception
                   + '}';
        }
    }

    /**
     * Value class holding progress values.
     */
    class TaskProgressMessage {

        public final int taskId;
        @SuppressWarnings("WeakerAccess")
        @Nullable
        public final Integer maxPosition;
        @SuppressWarnings("WeakerAccess")
        @Nullable
        public final Integer absPosition;
        @Nullable
        public final Object[] values;

        public TaskProgressMessage(final int taskId,
                                   @Nullable final Integer maxPosition,
                                   @Nullable final Integer absPosition,
                                   @Nullable final Object[] values) {
            this.taskId = taskId;
            this.maxPosition = maxPosition;
            this.absPosition = absPosition;
            this.values = values;
        }

        /**
         * Constructor using a simple/single {@code StringRes}.
         *
         * @param taskId   task ID
         * @param stringId string resource if
         */
        public TaskProgressMessage(final int taskId,
                                   @StringRes final int stringId) {
            this.taskId = taskId;
            this.maxPosition = null;
            this.absPosition = null;
            this.values = new Object[]{stringId};
        }

        @Override
        @NonNull
        public String toString() {
            return "TaskProgressMessage{"
                   + "taskId=" + taskId
                   + ", maxPosition=" + maxPosition
                   + ", absPosition=" + absPosition
                   + ", values=" + Arrays.toString(values)
                   + '}';
        }
    }
}
