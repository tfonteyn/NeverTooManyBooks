/*
 * @Copyright 2018-2023 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;

/**
 * The base for a task which uses a {@link TaskListener} for the results.
 *
 * @param <Result> the type of the result of the background computation.
 */
public abstract class LTask<Result>
        extends TaskBase<Result> {

    private static final String LISTENER_WAS_DEAD = "Listener was dead";

    /** The client listener where to send our results to. */
    @NonNull
    private final WeakReference<TaskListener<Result>> taskListener;

    @NonNull
    private final Handler handler;

    /**
     * Constructor.
     *
     * @param taskId       a unique task identifier, returned with each message
     * @param taskName     a (preferably unique) name used for identification of this task
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    protected LTask(final int taskId,
                    @NonNull final String taskName,
                    @NonNull final TaskListener<Result> taskListener) {
        super(taskId, taskName);
        this.taskListener = new WeakReference<>(taskListener);
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    @WorkerThread
    public void publishProgress(@NonNull final TaskProgress message) {
        handler.post(() -> {
            if (taskListener.get() != null) {
                taskListener.get().onProgress(message);
            } else {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(getTaskName(), "publishProgress|" + LISTENER_WAS_DEAD);
                }
            }
        });
    }

    @Override
    @WorkerThread
    protected void setTaskFinished(@Nullable final Result result) {
        handler.post(() -> {
            if (taskListener.get() != null) {
                taskListener.get().onFinished(getTaskId(), result);
            } else {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(getTaskName(), "onFinished|" + LISTENER_WAS_DEAD);
                }
            }
        });
    }


    @Override
    @WorkerThread
    protected void setTaskCancelled(@Nullable final Result result) {
        handler.post(() -> {
            if (taskListener.get() != null) {
                taskListener.get().onCancelled(getTaskId(), result);
            } else {
                if (BuildConfig.DEBUG /* always */) {
                    // Will be shown on genuine bug,
                    // but also when the ViewModel onCleared() is called.
                    // The latter situation is normal and can be ignored.
                    Log.d(getTaskName(), "onCancelled|" + LISTENER_WAS_DEAD);
                }
            }
        });
    }

    @Override
    @WorkerThread
    protected void setTaskFailure(@NonNull final Throwable e) {
        handler.post(() -> {
            if (taskListener.get() != null) {
                taskListener.get().onFailure(getTaskId(), e);
            } else {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(getTaskName(), "onFailure|" + LISTENER_WAS_DEAD);
                }
            }
        });
    }
}
