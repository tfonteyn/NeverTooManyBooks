/*
 * @Copyright 2018-2022 HardBackNutter
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
    private final MutableLiveData<LiveDataEvent<TaskResult<Result>>> onFinished;
    @NonNull
    private final MutableLiveData<LiveDataEvent<TaskResult<Exception>>> onFailure;

    @Nullable
    private MutableLiveData<LiveDataEvent<TaskResult<Result>>> onCancelled;
    @Nullable
    private MutableLiveData<LiveDataEvent<TaskProgress>> onProgress;

    /**
     * Constructor.
     *
     * @param onFinished observable
     * @param onFailure  observable
     */
    public TaskListenerToLiveData(
            @NonNull final MutableLiveData<LiveDataEvent<TaskResult<Result>>> onFinished,
            @NonNull final MutableLiveData<LiveDataEvent<TaskResult<Exception>>> onFailure) {
        this.onFinished = onFinished;
        this.onFailure = onFailure;
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
        this.onCancelled = onCancelled;
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
        this.onProgress = onProgress;
        return this;
    }

    public void onFinished(final int taskId,
                           @Nullable final Result result) {
        onFinished.setValue(new LiveDataEvent<>(new TaskResult<>(taskId, result)));
    }

    public void onFailure(final int taskId,
                          @Nullable final Exception exception) {
        onFailure.setValue(new LiveDataEvent<>(new TaskResult<>(taskId, exception)));
    }

    public void onCancelled(final int taskId,
                            @Nullable final Result result) {
        if (onCancelled != null) {
            onCancelled.setValue(new LiveDataEvent<>(new TaskResult<>(taskId, result)));
        }
    }

    public void onProgress(@NonNull final TaskProgress message) {
        if (onProgress != null) {
            onProgress.setValue(new LiveDataEvent<>(message));
        }
    }
}
