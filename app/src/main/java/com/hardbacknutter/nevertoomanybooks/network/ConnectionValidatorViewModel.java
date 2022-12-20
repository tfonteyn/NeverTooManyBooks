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
package com.hardbacknutter.nevertoomanybooks.network;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;

public class ConnectionValidatorViewModel
        extends ViewModel {

    private ConnectionValidatorTask validatorTask;

    @Override
    protected void onCleared() {
        validatorTask.cancel();
        super.onCleared();
    }

    public void init(@StringRes final int siteResId) {
        validatorTask = new ConnectionValidatorTask(siteResId);
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Boolean>>> onConnectionSuccessful() {
        return validatorTask.onFinished();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Boolean>>> onConnectionCancelled() {
        return validatorTask.onCancelled();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Throwable>>> onConnectionFailed() {
        return validatorTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return validatorTask.onProgress();
    }

    public void cancelTask(@IdRes final int taskId) {
        if (taskId == validatorTask.getTaskId()) {
            validatorTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }

    public void validateConnection() {
        validatorTask.connect();
    }
}
