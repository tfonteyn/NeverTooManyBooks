package com.eleybourn.bookcatalogue.tasks;

import android.os.AsyncTask;

import androidx.annotation.Nullable;

public interface OnTaskFinishedListener<Result> {

    /**
     * Called when a task finishes.
     *
     * @param taskId  id for the task which was provided at construction time.
     * @param success {@code true} if the task finished successfully
     * @param result  the result object from the {@link AsyncTask}.
     *                Nullable/NonNull is up to the implementation.
     * @param e       if the task finished with an exception, or null.
     */
    void onTaskFinished(int taskId,
                        boolean success,
                        Result result,
                        @Nullable final Exception e);
}
