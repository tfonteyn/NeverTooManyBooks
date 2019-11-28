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
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskStatus;


/**
 * The base for a task with our standard setup.
 *
 * @param <Result> the type of the result of the background computation.
 */
public abstract class TaskBase<Result>
        extends AsyncTask<Void, TaskListener.ProgressMessage, Result>
        implements ProgressDialogFragment.Cancellable {

    private static final String TAG = "TaskBase";

    /** id set at construction time, passed back in all messages. */
    protected final int mTaskId;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can check it.
     */
    @Nullable
    protected Exception mException;

    @NonNull
    private WeakReference<TaskListener<Result>> mTaskListener;

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
     * (Re)set the listener.
     *
     * @param taskListener for sending progress and finish messages to.
     */
    public void setListener(final TaskListener<Result> taskListener) {
        synchronized (this) {
            mTaskListener = new WeakReference<>(taskListener);
        }
    }

    /**
     * Access for other classes.
     *
     * @return task ID
     */
    public int getId() {
        return mTaskId;
    }

    @Override
    @UiThread
    protected void onProgressUpdate(@NonNull final TaskListener.ProgressMessage... values) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onProgress(values[0]);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Log.d(TAG, "onProgressUpdate|" + Logger.WEAK_REFERENCE_DEAD);
            }
        }
    }

    @Override
    @CallSuper
    protected void onCancelled(@NonNull final Result result) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onFinished(new TaskListener.FinishMessage<>(
                    mTaskId, TaskStatus.Cancelled, result, mException));
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Log.d(TAG, "onCancelled|" + Logger.WEAK_REFERENCE_DEAD);
            }
        }
    }

    @Override
    @UiThread
    protected void onPostExecute(@NonNull final Result result) {
        if (mTaskListener.get() != null) {
            TaskStatus status = mException == null ? TaskStatus.Success
                                                   : TaskStatus.Failed;
            mTaskListener.get().onFinished(new TaskListener.FinishMessage<>(
                    mTaskId, status, result, mException));
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Log.d(TAG, "onPostExecute|" + Logger.WEAK_REFERENCE_DEAD);
            }
        }
    }
}
