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
    void onFinished(@NonNull FinishMessage<Result> message);

    /**
     * Progress messages.
     */
    void onProgress(@NonNull ProgressMessage message);

    /** Possible outcomes as passed in {@link FinishMessage}. */
    enum TaskStatus {
        Success, Cancelled, Failed
    }

    /**
     * Value class holding task-finished value.
     *
     * @param <Result> type of the actual result of the task.
     */
    class FinishMessage<Result> {

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
        public FinishMessage(final int taskId,
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
            return "FinishMessage{"
                   + "taskId=" + taskId
                   + ", status=" + status
                   + ", result=" + result
                   + ", exception=" + exception
                   + '}';
        }
    }

    /**
     * Value class holding progress value.
     */
    class ProgressMessage {

        public final int taskId;
        public final int maxPosition;
        public final int absPosition;
        @Nullable
        public final String text;

        public ProgressMessage(final int taskId,
                               final int maxPosition,
                               final int absPosition,
                               @Nullable final String text) {
            this.taskId = taskId;
            this.maxPosition = maxPosition;
            this.absPosition = absPosition;
            this.text = text;
        }

        /**
         * Constructor using a simple/single {@code StringRes}.
         *
         * @param taskId task ID
         * @param text   string
         */
        public ProgressMessage(final int taskId,
                               @Nullable final String text) {
            this.taskId = taskId;
            this.maxPosition = 0;
            this.absPosition = 0;
            this.text = text;
        }

        @Override
        @NonNull
        public String toString() {
            return "ProgressMessage{"
                   + "taskId=" + taskId
                   + ", maxPosition=" + maxPosition
                   + ", absPosition=" + absPosition
                   + ", text=" + text
                   + '}';
        }
    }
}
