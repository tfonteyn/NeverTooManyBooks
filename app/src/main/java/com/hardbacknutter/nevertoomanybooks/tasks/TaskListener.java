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
import androidx.annotation.Nullable;

/**
 * <strong>Warning:</strong> To prevent unintended garbage collection, you must store
 * a strong reference to the listener. Do NOT simply pass a lambda, or anonymous class.
 * <p>
 * The AsyncTask implementations as used, store only a WeakReference to the listener which is good.
 * We don't want them to store a strong reference, as that could/would lead to memory leaks.
 * <ul>This does mean that the creator of the AsyncTask must:
 * <li>implemented the interface on the caller itself, and pass the caller; i.e. 'this'</li>
 * <li>or create an instance variable in the caller of the listener type, and pass that</li>
 * </ul>
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
    default void onProgress(@NonNull final ProgressMessage message) {
        // ignore by default
    }

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
     * Value class holding progress data.
     */
    class ProgressMessage {

        public final int taskId;

        /** No-op if {@code null} otherwise change mode to the requested one. */
        @Nullable
        final Boolean indeterminate;

        /**
         * The maximum position for the progressbar,
         * should be ignored if a mode change was requested with indeterminate.
         */
        public final int maxPosition;

        /**
         * Absolute position for the progressbar,
         * should be ignored if a mode change was requested with indeterminate.
         */
        public final int position;

        /** Optional text to display. */
        @Nullable
        public final String text;

        public ProgressMessage(final int taskId,
                               @Nullable final Boolean indeterminate,
                               final int maxPosition,
                               final int position,
                               @Nullable final String text) {
            this.taskId = taskId;
            this.text = text;

            this.indeterminate = indeterminate;
            this.maxPosition = maxPosition;
            this.position = position;
        }

        public ProgressMessage(final int taskId,
                               @Nullable final String text) {
            this.taskId = taskId;
            this.text = text;

            this.indeterminate = null;
            this.maxPosition = 0;
            this.position = 0;
        }

        @Override
        @NonNull
        public String toString() {
            return "ProgressMessage{"
                   + "taskId=" + taskId
                   + ", indeterminate=" + indeterminate
                   + ", maxPosition=" + maxPosition
                   + ", position=" + position
                   + ", text=" + text
                   + '}';
        }
    }
}
