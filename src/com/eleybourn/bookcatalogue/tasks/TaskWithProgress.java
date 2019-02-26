package com.eleybourn.bookcatalogue.tasks;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.fragment.app.FragmentActivity;

/**
 * As Mr. Internet states in many places what you can't do... and gets it wrong, here is a copy
 * from the java docs of {@link AsyncTask}.
 *
 * <h2>Memory observability</h2>
 * <p>AsyncTask guarantees that all callback calls are synchronized in such a way that the
 * following operations are safe without explicit synchronizations.</p>
 * <ul>
 * <li>Set member fields in the constructor or {@link #onPreExecute}, and refer to them
 * in {@link #doInBackground}.
 * <li>Set member fields in {@link #doInBackground}, and refer to them in
 * {@link #onProgressUpdate} and {@link #onPostExecute}.
 * </ul>
 * <p>
 * So yes, you CAN use member variables.
 * <p>
 *
 * @param <Results> the type of the result objects for {@link #onPostExecute}.
 */
public abstract class TaskWithProgress<Results>
        extends AsyncTask<Void, Object, Results> {

    /** Generic identifier. */
    private final int mTaskId;
    protected final ProgressDialogFragment<Results> mFragment;
    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    protected Exception mException;

    /**
     * Constructor.
     *
     * @param taskId          a task identifier, will be returned in the task finished listener.
     * @param fragmentTag     tag for the fragment
     * @param context         the caller context
     * @param isIndeterminate flag for the progress
     * @param titelId         titel resource for the progress
     */
    @UiThread
    public TaskWithProgress(final int taskId,
                            @NonNull final String fragmentTag,
                            @NonNull final FragmentActivity context,
                            final boolean isIndeterminate,
                            final int titelId) {
        mTaskId = taskId;
        mFragment = ProgressDialogFragment.newInstance(titelId, isIndeterminate, 0);
        mFragment.show(context.getSupportFragmentManager(), fragmentTag);
        mFragment.setTask(this);
    }

    public int getTaskId() {
        return mTaskId;
    }

    /**
     * @param values: [0] String message
     *                [0] String message, [1] Integer position/delta
     */
    @Override
    @UiThread
    protected void onProgressUpdate(@NonNull final Object... values) {
        switch (values.length) {
            case 1:
                mFragment.onProgress((String) values[0], null);
                break;
            case 2:
                mFragment.onProgress((String) values[0], (Integer) values[1]);
                break;
        }
    }

    /**
     * Note that the results parameter is not annotated null/non-null, as overrides need a choice.
     * <p>
     * If the task was cancelled (by the user cancelling the progress dialog) then
     * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
     *
     * @param result of the task
     */
    @Override
    @UiThread
    protected void onPostExecute(final Results result) {
        mFragment.taskFinished(mTaskId, mException == null, result);
    }

}
