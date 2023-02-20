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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskResult;

/**
 * The base for a task which uses {@link MutableLiveData} for the results.
 *
 * @param <Result> the type of the result of the background computation.
 */
public abstract class MTask<Result>
        extends TaskBase<Result> {

    private final MutableLiveData<LiveDataEvent<TaskResult<Result>>> onFinished =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<TaskResult<Result>>> onCanceled =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<TaskResult<Throwable>>> onFailure =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<TaskProgress>> onProgress =
            new MutableLiveData<>();

    /**
     * Constructor.
     *
     * @param taskId   a unique task identifier, returned with each message
     * @param taskName a (preferably unique) name used for identification of this task
     */
    @UiThread
    protected MTask(final int taskId,
                    @NonNull final String taskName) {
        super(taskId, taskName);
    }

    /**
     * Observable to receive success.
     *
     * @return the {@link Result} which can be considered to be complete and correct.
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Result>>> onFinished() {
        return onFinished;
    }

    @Override
    @WorkerThread
    protected void setTaskFinished(@Nullable final Result result) {
        onFinished.postValue(new LiveDataEvent<>(new TaskResult<>(getTaskId(), result)));
    }

    /**
     * Observable to receive cancellation.
     *
     * @return the {@link Result}. It will depend on the implementation how
     * complete/correct (if at all) this result is.
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Result>>> onCancelled() {
        return onCanceled;
    }


    @Override
    @WorkerThread
    protected void setTaskCancelled(@Nullable final Result result) {
        onCanceled.postValue(new LiveDataEvent<>(new TaskResult<>(getTaskId(), result)));
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Throwable>>> onFailure() {
        return onFailure;
    }

    @Override
    @WorkerThread
    protected void setTaskFailure(@NonNull final Throwable e) {
        onFailure.postValue(new LiveDataEvent<>(new TaskResult<>(getTaskId(), e)));
    }

    /**
     * Observable to receive progress.
     *
     * @return a {@link TaskProgress} with the progress counter, a text message, ...
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return onProgress;
    }

    @Override
    @WorkerThread
    public void publishProgress(@NonNull final TaskProgress message) {
        onProgress.postValue(new LiveDataEvent<>(message));
    }
}
