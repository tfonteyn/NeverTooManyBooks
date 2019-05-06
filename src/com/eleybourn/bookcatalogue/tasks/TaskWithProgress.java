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

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    protected Exception mException;

    public TaskWithProgress(@NonNull final ProgressDialogFragment<Progress, Result> progressDialog) {
        mProgressDialog = progressDialog;
    }

    protected abstract int getId();

    @Override
    protected void onPreExecute() {
        mProgressDialog.setTask(getId(), this);
    }

    /**
     * @param values: [0] Integer absPosition, [1] String message
     *                [0] Integer absPosition, [1] StringRes messageId
     */
    @Override
    @UiThread
    protected void onProgressUpdate(@NonNull final Object... values) {
        if (values[1] instanceof String) {
            mProgressDialog.onProgress((Integer) values[0], (String) values[1]);
        } else {
            mProgressDialog.onProgress((Integer) values[0], (Integer) values[1]);
        }
    }

    /**
     * If the task was cancelled (by the user cancelling the progress dialog) then
     * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
     *
     * @param result of the task
     */
    @Override
    @UiThread
    protected void onPostExecute(@NonNull final Result result) {
        mProgressDialog.onTaskFinished(mException == null, result, mException);
    }
}
