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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.StorageMoverTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;

/**
 * Shared on the Activity level as it's needed by more than 1 Fragment.
 */
@SuppressWarnings("WeakerAccess")
public class SettingsViewModel
        extends ViewModel {

    private final StorageMoverTask mStorageMoverTask = new StorageMoverTask();
    private boolean mRequiresActivityRecreation;

    boolean getRequiresActivityRecreation() {
        return mRequiresActivityRecreation;
    }

    void setRequiresActivityRecreation() {
        mRequiresActivityRecreation = true;
    }

    boolean moveData(@NonNull final Context context,
                     final int sourceIndex,
                     final int destIndex) {

        mStorageMoverTask.setDirs(context, sourceIndex, destIndex);
        try {
            if (mStorageMoverTask.checkSpace()) {
                mStorageMoverTask.start();
                return true;
            }
        } catch (@NonNull final IOException ignore) {
            // ignore, just report we can't move
        }
        return false;
    }

    @NonNull
    LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return mStorageMoverTask.onProgressUpdate();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Integer>>> onMoveCancelled() {
        return mStorageMoverTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Exception>>> onMoveFailure() {
        return mStorageMoverTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Integer>>> onMoveFinished() {
        return mStorageMoverTask.onFinished();
    }

    void cancelTask(@IdRes final int taskId) {
        if (taskId == mStorageMoverTask.getTaskId()) {
            mStorageMoverTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }
}
