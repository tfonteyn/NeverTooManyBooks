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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.sync.goodreads.tasks.AuthTask;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListenerToLiveData;

public class GoodreadsAuthenticationViewModel
        extends ViewModel {

    private final MutableLiveData<FinishedMessage<GrStatus>> mFinished = new MutableLiveData<>();
    private final MutableLiveData<FinishedMessage<GrStatus>> mCancelled = new MutableLiveData<>();
    private final MutableLiveData<FinishedMessage<Exception>> mFailure = new MutableLiveData<>();
    private final MutableLiveData<ProgressMessage> mProgress = new MutableLiveData<>();

    final TaskListener<GrStatus> mTaskListener =
            new TaskListenerToLiveData<>(mFinished, mFailure)
                    .setOnCancelled(mCancelled)
                    .setOnProgress(mProgress);

    private final AuthTask mAuthTask = new AuthTask(mTaskListener);


    @NonNull
    public LiveData<FinishedMessage<GrStatus>> onFinished() {
        return mFinished;
    }

    @NonNull
    public LiveData<FinishedMessage<GrStatus>> onCancelled() {
        return mCancelled;
    }

    @NonNull
    public LiveData<FinishedMessage<Exception>> onFailure() {
        return mFailure;
    }

    @NonNull
    public LiveData<ProgressMessage> onProgress() {
        return mProgress;
    }


    void promptForAuthentication(@NonNull final Context context) {
        mAuthTask.authorize(context);
    }

    void authenticate() {
        mAuthTask.authenticate();
    }

    protected void cancelTask(@IdRes final int taskId) {
        if (taskId == mAuthTask.getTaskId()) {
            mAuthTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }
}
