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
import androidx.lifecycle.MutableLiveData;

/**
 * A gateway between an {@link LTask} and {@link MutableLiveData} clients.
 * <p>
 * Mainly used to provide multiple {@link LTask} with a single {@link MutableLiveData} client.
 * It's limited to a single result type which does not make it universally usable for now.
 *
 * @param <Result> type of the task result
 */
public class TaskListenerToLiveData<Result>
        implements TaskListener<Result> {

    @NonNull
    private final MutableLiveData<LiveDataEvent<TaskResult<Result>>> mOnFinished;
    @NonNull
    private final MutableLiveData<LiveDataEvent<TaskResult<Exception>>> mOnFailure;

    @Nullable
    private MutableLiveData<LiveDataEvent<TaskResult<Result>>> mOnCancelled;
    @Nullable
    private MutableLiveData<LiveDataEvent<TaskProgress>> mOnProgress;

    /**
     * Constructor.
     *
     * @param onFinished observable
     * @param onFailure  observable
     */
    public TaskListenerToLiveData(
            @NonNull final MutableLiveData<LiveDataEvent<TaskResult<Result>>> onFinished,
            @NonNull final MutableLiveData<LiveDataEvent<TaskResult<Exception>>> onFailure) {
        mOnFinished = onFinished;
        mOnFailure = onFailure;
    }

    /**
     * Set the optional cancel observable.
     *
     * @param onCancelled observable
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public TaskListenerToLiveData<Result> setOnCancelled(
            @Nullable final MutableLiveData<LiveDataEvent<TaskResult<Result>>> onCancelled) {
        mOnCancelled = onCancelled;
        return this;
    }

    /**
     * Set the optional progress observable.
     *
     * @param onProgress observable
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public TaskListenerToLiveData<Result> setOnProgress(
            @Nullable final MutableLiveData<LiveDataEvent<TaskProgress>> onProgress) {
        mOnProgress = onProgress;
        return this;
    }

    public void onFinished(@NonNull final TaskResult<Result> message) {
        mOnFinished.setValue(new LiveDataEvent<>(message));
    }

    public void onFailure(@NonNull final TaskResult<Exception> message) {
        mOnFailure.setValue(new LiveDataEvent<>(message));
    }

    public void onCancelled(@NonNull final TaskResult<Result> message) {
        if (mOnCancelled != null) {
            mOnCancelled.setValue(new LiveDataEvent<>(message));
        }
    }

    public void onProgress(@NonNull final TaskProgress message) {
        if (mOnProgress != null) {
            mOnProgress.setValue(new LiveDataEvent<>(message));
        }
    }
}
