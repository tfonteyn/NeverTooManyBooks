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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.backup.common.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.common.DataReaderTask;
import com.hardbacknutter.nevertoomanybooks.backup.common.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.common.MetaDataReaderTask;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;

/**
 * Coordinate between the UI and the {@link ImportHelper}.
 * Handle the import related background tasks.
 */
public class ImportViewModel
        extends ViewModel {

    private final MetaDataReaderTask<ArchiveMetaData, ImportResults> mMetaDataTask =
            new MetaDataReaderTask<>();
    private final DataReaderTask<ArchiveMetaData, ImportResults> mReaderTask =
            new DataReaderTask<>();

    @Nullable
    private ImportHelper mHelper;

    @Override
    protected void onCleared() {
        mMetaDataTask.cancel();
        mReaderTask.cancel();
        super.onCleared();
    }

    // not an 'init' as the helper can only be created after the user selected a uri
    @NonNull
    ImportHelper createImportHelper(@NonNull final Context context,
                                    @NonNull final Uri uri)
            throws InvalidArchiveException, FileNotFoundException {

        mHelper = new ImportHelper(context, uri);
        return mHelper;
    }

    boolean hasUri() {
        // simple check... the uri will always exist if the helper exists.
        return mHelper != null;
    }

    @NonNull
    ImportHelper getImportHelper() {
        return Objects.requireNonNull(mHelper, "mImportHelper");
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Optional<ArchiveMetaData>>>> onMetaDataRead() {
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
    LiveData<LiveDataEvent<TaskResult<ImportResults>>> onImportCancelled() {
        return mReaderTask.onCancelled();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Exception>>> onImportFailure() {
        return mReaderTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<ImportResults>>> onImportFinished() {
        return mReaderTask.onFinished();
    }

    void readMetaData() {
        Objects.requireNonNull(mHelper, "mImportHelper");
        mMetaDataTask.start(mHelper);
    }

    /**
     * Check if we have sufficient data to start an import.
     *
     * @return {@code true} if the "Go" button should be made available
     */
    boolean isReadyToGo() {
        return mHelper != null && mHelper.getMetaData().isPresent();
    }

    void startImport() {
        Objects.requireNonNull(mHelper, "mImportHelper");
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
