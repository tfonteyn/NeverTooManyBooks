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

import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.backup.common.DataReaderTask;
import com.hardbacknutter.nevertoomanybooks.backup.common.MetaDataReaderTask;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.ReaderResults;

public class SyncReaderViewModel
        extends ViewModel {

    private final MetaDataReaderTask<SyncReaderMetaData, ReaderResults> mMetaDataTask =
            new MetaDataReaderTask<>();
    private final DataReaderTask<SyncReaderMetaData, ReaderResults> mReaderTask =
            new DataReaderTask<>();

    @Nullable
    private SyncReaderHelper mHelper;

    @Override
    protected void onCleared() {
        mMetaDataTask.cancel();
        mReaderTask.cancel();
        super.onCleared();
    }

    /**
     * Pseudo constructor.
     */
    public void init(@NonNull final Bundle args) {
        if (mHelper == null) {
            final SyncServer syncServer = Objects.requireNonNull(
                    args.getParcelable(SyncServer.BKEY_SITE), SyncServer.BKEY_SITE);
            mHelper = new SyncReaderHelper(syncServer);
        }
    }

    @NonNull
    SyncReaderHelper getSyncReaderHelper() {
        return Objects.requireNonNull(mHelper, "mHelper");
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Optional<SyncReaderMetaData>>>> onMetaDataRead() {
        return mMetaDataTask.onFinished();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Exception>>> onMetaDataFailure() {
        return mMetaDataTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return mReaderTask.onProgress();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<ReaderResults>>> onImportCancelled() {
        return mReaderTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Exception>>> onImportFailure() {
        return mReaderTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<ReaderResults>>> onImportFinished() {
        return mReaderTask.onFinished();
    }

    public void readMetaData() {
        Objects.requireNonNull(mHelper, "mHelper");

        mMetaDataTask.start(mHelper);
    }

    /**
     * Check if we have sufficient data to start an import.
     *
     * @return {@code true} if the "Go" button should be made available
     */
    boolean isReadyToGo() {
        Objects.requireNonNull(mHelper, "mHelper");

        switch (mHelper.getSyncServer()) {
            case CalibreCS: {
                @Nullable
                final CalibreLibrary selected = mHelper
                        .getExtraArgs().getParcelable(CalibreContentServer.BKEY_LIBRARY);
                return selected != null && selected.getTotalBooks() > 0;
            }
            case StripInfo:
                return true;

            default:
                throw new IllegalArgumentException();
        }
    }

    void startImport() {
        Objects.requireNonNull(mHelper, "mHelper");
        mReaderTask.start(mHelper);
    }

    void cancelTask(@IdRes final int taskId) {
        if (taskId == mReaderTask.getTaskId()) {
            mReaderTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }
}
