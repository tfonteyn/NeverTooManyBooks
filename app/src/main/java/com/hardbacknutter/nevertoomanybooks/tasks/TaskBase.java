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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * The base for a task with our standard setup.
 *
 * @param <Result> the type of the result of the background computation.
 */
public abstract class TaskBase<Result>
        extends AsyncTask<Void, TaskListener.TaskProgressMessage, Result> {

    /** id set at construction time, passed back in all messages. */
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
     * @return task ID
     */
    public int getId() {
        return mTaskId;
    }

    @Override
    @UiThread
    protected void onProgressUpdate(@NonNull final TaskListener.TaskProgressMessage... values) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onTaskProgress(values[0]);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onProgressUpdate",
                             Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
            }
        }
    }

    @Override
    @CallSuper
    protected void onCancelled(@Nullable final Result result) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onTaskCancelled(
                    new TaskListener.TaskFinishedMessage<>(mTaskId, mException == null,
                                                           result, mException));
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onCancelled",
                             Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
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
                             Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
            }
        }
    }
}
