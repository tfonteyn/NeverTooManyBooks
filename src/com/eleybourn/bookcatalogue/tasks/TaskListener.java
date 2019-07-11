package com.eleybourn.bookcatalogue.tasks;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Warning: To prevent unintended garbage collection, you must store a strong reference to
 * the listener.
 * <p>
 * The AsyncTask implementations as used, store only a WeakReference to the listener which is good.
 * We don't want them to store a strong reference, as that could/would lead to memory leaks.
 * <p>
 * This does mean that the creator of the AsyncTask must:
 * <ul>
 * <li>implemented the interface on the caller itself, and pass the caller; i.e. 'this'</li>
 * <li>or create an instance variable in the caller of the listener type, and pass that</li>
 * </ul>
 * <strong>Do NOT simply pass a lambda, or anonymous class.</strong>
 * <br>
 *
 * @param <Progress> type of progress values
 * @param <Result>   type of the task result
 */
public interface TaskListener<Progress, Result> {

    /**
     * Called when a task finishes.
     * <p>
     * Note that 'success' only means the call itself was successful.
     * It usually still depends on the 'result' from the call what the next step should be.
     *
     * @param taskId  id for the task which was provided at construction time.
     * @param success {@code true} if the task finished successfully
     * @param result  the result object from the {@link AsyncTask}.
     *                Nullable/NonNull is up to the implementation.
     * @param e       if the task finished with an exception, or {@code null}.
     */
    void onTaskFinished(int taskId,
                        boolean success,
                        Result result,
                        @Nullable Exception e);

    /**
     * Optional cancellation callback.
     *
     * @param taskId id for the task which was provided at construction time, or {@code null}
     *               if the task was cancelled before it even started.
     */
    default void onTaskCancelled(@Nullable Integer taskId) {
        // do nothing.
    }

    /**
     * Optional progress messages.
     *
     * @param taskId id for the task which was provided at construction time.
     * @param values progress objects from the {@link AsyncTask}.
     */
    default void onTaskProgress(int taskId,
                                @NonNull Progress[] values) {
        // do nothing.
    }
}
