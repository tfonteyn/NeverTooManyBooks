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

import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.StorageMoverTask;

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

        //URGENT: add verification that mStoredVolumeIndex HAS our dir,
        mStorageMoverTask.setDirs(context, sourceIndex, destIndex);
        if (mStorageMoverTask.checkSpace()) {
            mStorageMoverTask.start();
            return true;
        } else {
            return false;
        }
    }

    @NonNull
    LiveData<ProgressMessage> onProgress() {
        return mStorageMoverTask.onProgressUpdate();
    }

    @NonNull
    LiveData<FinishedMessage<Integer>> onMoveCancelled() {
        return mStorageMoverTask.onCancelled();
    }

    @NonNull
    LiveData<FinishedMessage<Exception>> onMoveFailure() {
        return mStorageMoverTask.onFailure();
    }

    @NonNull
    LiveData<FinishedMessage<Integer>> onMoveFinished() {
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
