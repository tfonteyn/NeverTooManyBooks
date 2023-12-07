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
package com.hardbacknutter.nevertoomanybooks.network;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;

public class ConnectionValidatorViewModel
        extends ViewModel {

    private ConnectionValidatorTask validatorTask;

    @Override
    protected void onCleared() {
        validatorTask.cancel();
        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param siteResId string resource for the site name
     */
    public void init(@StringRes final int siteResId) {
        validatorTask = new ConnectionValidatorTask(siteResId);
    }

    @NonNull
    public LiveData<LiveDataEvent<Boolean>> onConnectionSuccessful() {
        return validatorTask.onFinished();
    }

    @NonNull
    public LiveData<LiveDataEvent<Boolean>> onConnectionCancelled() {
        return validatorTask.onCancelled();
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception
     */
    @NonNull
    public LiveData<LiveDataEvent<Throwable>> onConnectionFailed() {
        return validatorTask.onFailure();
    }

    /**
     * Observable to receive progress.
     *
     * @return a {@link TaskProgress} with the progress counter, a text message, ...
     */
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

    /**
     * Run the validation connection.
     */
    public void validateConnection() {
        validatorTask.connect();
    }
}
