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
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;

public class SyncWriterViewModel
        extends ViewModel {

    private final SyncWriterTask mWriterTask = new SyncWriterTask();
    private final SyncWriterConfig mConfig = new SyncWriterConfig();

    @Nullable
    private SyncServer mSyncServer;

    private boolean mQuickOptionsAlreadyShown;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Bundle args) {
        if (mSyncServer == null) {
            mSyncServer = Objects.requireNonNull(args.getParcelable(SyncServer.BKEY_SITE));
        }
    }

    @Override
    protected void onCleared() {
        mWriterTask.cancel();
        super.onCleared();
    }

    @NonNull
    public SyncServer getSyncServer() {
        return Objects.requireNonNull(mSyncServer, "mSyncServer");
    }


    @NonNull
    SyncWriterConfig getConfig() {
        return mConfig;
    }

    boolean isQuickOptionsAlreadyShown() {
        return mQuickOptionsAlreadyShown;
    }

    void setQuickOptionsAlreadyShown(
            @SuppressWarnings("SameParameterValue") final boolean quickOptionsAlreadyShown) {
        mQuickOptionsAlreadyShown = quickOptionsAlreadyShown;
    }


    @NonNull
    LiveData<ProgressMessage> onProgress() {
        return mWriterTask.onProgressUpdate();
    }

    @NonNull
    LiveData<FinishedMessage<SyncWriterResults>> onExportCancelled() {
        return mWriterTask.onCancelled();
    }

    @NonNull
    LiveData<FinishedMessage<Exception>> onExportFailure() {
        return mWriterTask.onFailure();
    }

    @NonNull
    LiveData<FinishedMessage<SyncWriterResults>> onExportFinished() {
        return mWriterTask.onFinished();
    }

    boolean isExportRunning() {
        return mWriterTask.isRunning();
    }

    void startExport() {
        Objects.requireNonNull(mSyncServer, "mSyncServer");
        mWriterTask.start(mSyncServer, mConfig);
    }

    void cancelTask(@IdRes final int taskId) {
        if (taskId == mWriterTask.getTaskId()) {
            mWriterTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }

}
