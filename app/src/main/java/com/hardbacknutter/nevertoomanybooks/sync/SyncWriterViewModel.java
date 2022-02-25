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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;

public class SyncWriterViewModel
        extends ViewModel {

    private final SyncWriterTask mWriterTask = new SyncWriterTask();
    private SyncWriterHelper mHelper;

    /** UI helper. */
    private boolean mQuickOptionsAlreadyShown;

    @Override
    protected void onCleared() {
        mWriterTask.cancel();
        super.onCleared();
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (mHelper == null) {
            final SyncServer syncServer = Objects.requireNonNull(
                    args.getParcelable(SyncServer.BKEY_SITE), SyncServer.BKEY_SITE);

            mHelper = new SyncWriterHelper(syncServer);
        }
    }

    @NonNull
    SyncWriterHelper getSyncWriterHelper() {
        return mHelper;
    }

    boolean isQuickOptionsAlreadyShown() {
        return mQuickOptionsAlreadyShown;
    }

    void setQuickOptionsAlreadyShown(
            @SuppressWarnings("SameParameterValue") final boolean quickOptionsAlreadyShown) {
        mQuickOptionsAlreadyShown = quickOptionsAlreadyShown;
    }

    @NonNull
    LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return mWriterTask.onProgress();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<SyncWriterResults>>> onExportCancelled() {
        return mWriterTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Exception>>> onExportFailure() {
        return mWriterTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<SyncWriterResults>>> onExportFinished() {
        return mWriterTask.onFinished();
    }

    boolean isExportRunning() {
        return mWriterTask.isRunning();
    }

    void startExport() {
        Objects.requireNonNull(mHelper, "mHelper");
        mWriterTask.start(mHelper);
    }

    void cancelTask(@IdRes final int taskId) {
        if (taskId == mWriterTask.getTaskId()) {
            mWriterTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }

}
