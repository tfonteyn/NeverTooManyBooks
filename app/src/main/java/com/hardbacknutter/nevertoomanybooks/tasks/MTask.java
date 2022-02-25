/*
 * @Copyright 2018-2021 HardBackNutter
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

/**
 * The base for a task which uses {@link MutableLiveData} for the results.
 *
 * @param <Result> the type of the result of the background computation.
 */
public abstract class MTask<Result>
        extends TaskBase<Result> {

    private final MutableLiveData<LiveDataEvent<TaskResult<Result>>> mFinishedObservable =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<TaskResult<Result>>> mCancelObservable =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<TaskResult<Exception>>> mFailureObservable =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<TaskProgress>> mProgressObservable =
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
        return mFinishedObservable;
    }

    @Override
    @WorkerThread
    protected void setFinished(@Nullable final Result result) {
        mFinishedObservable.postValue(new LiveDataEvent<>(new TaskResult<>(getTaskId(), result)));
    }

    /**
     * Observable to receive cancellation.
     *
     * @return the {@link Result}. It will depend on the implementation how
     * complete/correct (if at all) this result is.
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Result>>> onCancelled() {
        return mCancelObservable;
    }


    @Override
    @WorkerThread
    protected void setCancelled(@Nullable final Result result) {
        mCancelObservable.postValue(new LiveDataEvent<>(new TaskResult<>(getTaskId(), result)));
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Exception>>> onFailure() {
        return mFailureObservable;
    }

    @Override
    @WorkerThread
    protected void setFailure(@NonNull final Exception e) {
        mFailureObservable.postValue(new LiveDataEvent<>(new TaskResult<>(getTaskId(), e)));
    }

    /**
     * Observable to receive progress.
     *
     * @return a {@link TaskProgress} with the progress counter, a text message, ...
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return mProgressObservable;
    }

    @Override
    @WorkerThread
    public void publishProgress(@NonNull final TaskProgress message) {
        mProgressObservable.postValue(new LiveDataEvent<>(message));
    }
}
