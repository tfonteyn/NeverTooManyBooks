package com.eleybourn.bookcatalogue.tasks;

import android.os.AsyncTask;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * The base for a task with our standard setup.
 *
 * @param <Result> the type of the result of the background computation.
 */
public abstract class TaskBase<Result>
        extends AsyncTask<Void, TaskListener.TaskProgressMessage, Result> {

    /** ID set at construction time, passed back in all messages. */
    protected final int mTaskId;

    @NonNull
    private final WeakReference<TaskListener<Result>> mTaskListener;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    @Nullable
    protected Exception mException;

    /**
     * Constructor.
     *
     * @param taskId       a task identifier, will be returned in the task listener.
     * @param taskListener for sending progress and finish messages to.
     */
    protected TaskBase(final int taskId,
                       @NonNull final TaskListener<Result> taskListener) {
        mTaskId = taskId;
        mTaskListener = new WeakReference<>(taskListener);
    }

    /**
     * Access for other classes.
     *
     * @return task id
     */
    public int getId() {
        return mTaskId;
    }

    @Override
    @CallSuper
    protected void onCancelled(@Nullable final Result result) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onTaskCancelled(mTaskId, result);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onCancelled",
                             "WeakReference to listener was dead");
            }
        }
    }

    @Override
    @UiThread
    protected void onProgressUpdate(@NonNull final TaskListener.TaskProgressMessage... values) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onTaskProgress(values[0]);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onProgressUpdate",
                             "WeakReference to listener was dead");
            }
        }
    }

    @Override
    @UiThread
    protected void onPostExecute(@NonNull final Result result) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onTaskFinished(
                    new TaskListener.TaskFinishedMessage<>(mTaskId, mException == null,
                                                           result, mException));
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onPostExecute",
                             "WeakReference to listener was dead");
            }
        }
    }
}
