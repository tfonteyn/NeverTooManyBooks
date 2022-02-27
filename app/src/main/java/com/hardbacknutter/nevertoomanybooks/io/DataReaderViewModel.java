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
package com.hardbacknutter.nevertoomanybooks.io;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public abstract class DataReaderViewModel<METADATA, RESULTS>
        extends ViewModel {

    private final MetaDataReaderTask<METADATA, RESULTS> mMetaDataTask =
            new MetaDataReaderTask<>();
    private final DataReaderTask<METADATA, RESULTS> mReaderTask =
            new DataReaderTask<>();

    @Override
    protected void onCleared() {
        mMetaDataTask.cancel();
        mReaderTask.cancel();
        super.onCleared();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Exception>>> onReadMetaDataFailure() {
        return mMetaDataTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Optional<METADATA>>>> onReadMetaDataFinished() {
        return mMetaDataTask.onFinished();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return mReaderTask.onProgress();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Exception>>> onReadDataFailure() {
        return mReaderTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<RESULTS>>> onReadDataCancelled() {
        return mReaderTask.onCancelled();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<RESULTS>>> onReadDataFinished() {
        return mReaderTask.onFinished();
    }

    @UiThread
    protected void startReadingMetaData(@NonNull final
                                        DataReaderHelperBase<METADATA, RESULTS> helper) {
        mMetaDataTask.start(helper);
    }

    @UiThread
    protected void startReadingData(@NonNull final DataReaderHelperBase<METADATA, RESULTS> helper) {
        mReaderTask.start(helper);
    }

    /**
     * Check if we have sufficient data to start an import.
     *
     * @return {@code true} if the "Go" button should be made available
     */
    public abstract boolean isReadyToGo();

    public boolean isRunning() {
        return mReaderTask.isRunning();
    }

    public void cancelTask(@IdRes final int taskId) {
        if (taskId == mReaderTask.getTaskId()) {
            mReaderTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }

    private static class MetaDataReaderTask<METADATA, RESULTS>
            extends MTask<Optional<METADATA>> {

        private static final String TAG = "MetaDataReaderTask";

        private DataReaderHelperBase<METADATA, RESULTS> mHelper;

        MetaDataReaderTask() {
            super(R.id.TASK_ID_READ_META_DATA, TAG);
        }

        /**
         * Start the task.
         *
         * @param helper configuration
         */
        @UiThread
        public void start(@NonNull final DataReaderHelperBase<METADATA, RESULTS> helper) {
            mHelper = helper;
            execute();
        }

        @WorkerThread
        @Override
        @NonNull
        protected Optional<METADATA> doWork(@NonNull final Context context)
                throws DataReaderException,
                       IOException,
                       StorageException,
                       CredentialsException,
                       CertificateException {

            return mHelper.readMetaData(context);
        }
    }

    private static class DataReaderTask<METADATA, RESULTS>
            extends MTask<RESULTS> {

        private static final String TAG = "DataReaderTask";

        private DataReaderHelperBase<METADATA, RESULTS> mHelper;

        DataReaderTask() {
            super(R.id.TASK_ID_IMPORT, TAG);
        }

        /**
         * Start the task.
         *
         * @param helper configuration
         */
        @UiThread
        void start(@NonNull final DataReaderHelperBase<METADATA, RESULTS> helper) {
            mHelper = helper;
            execute();
        }

        @WorkerThread
        @Override
        @NonNull
        protected RESULTS doWork(@NonNull final Context context)
                throws DataReaderException,
                       IOException,
                       StorageException,
                       CredentialsException,
                       CertificateException {

            return mHelper.read(context, this);
        }
    }
}
