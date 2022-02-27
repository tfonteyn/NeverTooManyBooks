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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public abstract class DataWriterViewModel<RESULTS>
        extends ViewModel {

    @NonNull
    private final DataWriterTask<RESULTS> mWriterTask = new DataWriterTask<>();

    @Override
    protected void onCleared() {
        mWriterTask.cancel();
        super.onCleared();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return mWriterTask.onProgress();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Exception>>> onWriteDataFailure() {
        return mWriterTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<RESULTS>>> onWriteDataCancelled() {
        return mWriterTask.onCancelled();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<RESULTS>>> onWriteDataFinished() {
        return mWriterTask.onFinished();
    }

    /**
     * Check if we have sufficient data to start an export.
     *
     * @return {@code true} if the "Go" button should be made available
     */
    public abstract boolean isReadyToGo();

    @UiThread
    protected void startWritingData(@NonNull final DataWriterHelperBase<RESULTS> helper) {
        mWriterTask.start(helper);
    }

    public boolean isRunning() {
        return mWriterTask.isRunning();
    }

    public void cancelTask(@IdRes final int taskId) {
        if (taskId == mWriterTask.getTaskId()) {
            mWriterTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }

    private static class DataWriterTask<RESULTS>
            extends MTask<RESULTS> {

        private static final String TAG = "DataWriterTask";

        private DataWriterHelperBase<RESULTS> mHelper;

        DataWriterTask() {
            super(R.id.TASK_ID_EXPORT, TAG);
        }

        /**
         * Start the task.
         *
         * @param exportHelper configuration
         */
        @UiThread
        void start(@NonNull final DataWriterHelperBase<RESULTS> exportHelper) {
            mHelper = exportHelper;
            execute();
        }

        @WorkerThread
        @Override
        @NonNull
        protected RESULTS doWork(@NonNull final Context context)
                throws DataWriterException,
                       IOException,
                       StorageException,
                       CertificateException {

            return mHelper.write(context, this);
        }
    }
}
