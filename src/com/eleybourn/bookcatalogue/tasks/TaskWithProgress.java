package com.eleybourn.bookcatalogue.tasks;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

/**
 * The base for a task which needs access to a progress dialog.
 *
 * @param <Progress> the type of the progress units published during the background computation.
 * @param <Result>   the type of the result of the background computation.
 */
public abstract class TaskWithProgress<Progress, Result>
        extends AsyncTask<Void, Progress, Result> {

    @NonNull
    protected final ProgressDialogFragment<Progress, Result> mProgressDialog;

    private final int mTaskId;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    protected Exception mException;

    protected TaskWithProgress(final int taskId,
                               @NonNull final ProgressDialogFragment<Progress, Result> progressDialog) {
        mTaskId = taskId;
        mProgressDialog = progressDialog;
    }

    public int getId() {
        return mTaskId;
    }

    @Override
    protected void onPreExecute() {
        mProgressDialog.setTask(mTaskId, this);
    }

    /**
     * @param values: [0] Integer absPosition, [1] String message
     *                [0] Integer absPosition, [1] StringRes messageId
     */
    @Override
    @UiThread
    protected void onProgressUpdate(@NonNull final Object... values) {
        mProgressDialog.onProgress((Integer) values[0], values[1]);
    }

    /**
     * If the task was cancelled (by the user cancelling the progress dialog) then
     * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
     *
     * @param result of the task
     */
    @Override
    @UiThread
    protected void onPostExecute(@Nullable final Result result) {
        mProgressDialog.onTaskFinished(mTaskId,mException == null, result, mException);
    }
}
