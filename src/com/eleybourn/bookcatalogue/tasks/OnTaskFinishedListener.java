package com.eleybourn.bookcatalogue.tasks;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.debug.MustImplementException;

public interface OnTaskFinishedListener {

    /**
     * Convenience method. Try in order:
     * <li>getTargetFragment()</li>
     * <li>getParentFragment()</li>
     * <li>getActivity()</li>
     */
    static void onTaskFinished(@NonNull final Fragment sourceFragment,
                               final int taskId,
                               final boolean success,
                               @Nullable final Object result,
                               @Nullable final Exception e) {

        if (sourceFragment.getTargetFragment() instanceof OnTaskFinishedListener) {
            ((OnTaskFinishedListener) sourceFragment.getTargetFragment())
                    .onTaskFinished(taskId, success, result, e);
        } else if (sourceFragment.getParentFragment() instanceof OnTaskFinishedListener) {
            ((OnTaskFinishedListener) sourceFragment.getParentFragment())
                    .onTaskFinished(taskId, success, result, e);
        } else if (sourceFragment.getActivity() instanceof OnTaskFinishedListener) {
            ((OnTaskFinishedListener) sourceFragment.getActivity())
                    .onTaskFinished(taskId, success, result, e);
        } else {
            throw new MustImplementException(OnTaskFinishedListener.class);
        }
    }

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
                        Object result,
                        @Nullable final Exception e);
}
