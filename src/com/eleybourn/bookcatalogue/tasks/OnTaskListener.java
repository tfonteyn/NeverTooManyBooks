package com.eleybourn.bookcatalogue.tasks;

import android.os.AsyncTask;

import androidx.annotation.Nullable;

/**
 * Reminder: this is probably obvious to some, but:
 *
 * If you pass the listener as a lambda to the AsyncTask constructor and store it in the task as
 * a WeakReference, the lambda gets garbage collected by the time you want to use it
 * in onPostExecute.
 *
 * So... using a WeakReference to store the listener: good. But do not pass a lambda, either:
 * <ul>
 *     <li>implemented the interface on the caller, and pass the caller</li>
 *     <li>or create a member variable in the caller of the listener type, and pass that</li>
 * </ul>
 *
 * The other possibility is also obvious: I'm missing something obvious and the above is paranoia.
 *
 * @param <Progress>
 * @param <Result>
 */
public interface OnTaskListener<Progress, Result> {

    /**
     * Called when a task finishes.
     *
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
                        @Nullable final Exception e);

    /**
     * Optional progress messages.
     *
     * @param taskId id for the task which was provided at construction time.
     * @param values progress objects from the {@link AsyncTask}.
     *               Nullable/NonNull is up to the implementation.
     *
     * <br>Note: not using varargs (Possible heap pollution from parameterized vararg type).
     */
    default void onTaskProgress(int taskId,
                                Progress[] values) {
        // do nothing.
    }

    /**
     * Optional cancellation callback. By default calls onTaskCancelled with success==false
     *
     * @param taskId id for the task which was provided at construction time.
     */
    default void onTaskCancelled(int taskId,
                                 Result result,
                                 @Nullable final Exception e) {
        onTaskFinished(taskId, false, result, e);
    }
}
