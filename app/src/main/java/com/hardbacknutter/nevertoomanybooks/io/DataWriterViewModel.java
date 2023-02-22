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
package com.hardbacknutter.nevertoomanybooks.io;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;

public abstract class DataWriterViewModel<RESULTS>
        extends ViewModel {

    @NonNull
    private final DataWriterTask<RESULTS> writerTask = new DataWriterTask<>();

    @Override
    protected void onCleared() {
        writerTask.cancel();
        super.onCleared();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return writerTask.onProgress();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Throwable>>> onWriteDataFailure() {
        return writerTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<RESULTS>>> onWriteDataCancelled() {
        return writerTask.onCancelled();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<RESULTS>>> onWriteDataFinished() {
        return writerTask.onFinished();
    }

    /**
     * Check if we have sufficient data to start an export.
     *
     * @return {@code true} if the "Go" button should be made available
     */
    public abstract boolean isReadyToGo();

    @UiThread
    protected void startWritingData(@NonNull final DataWriterHelperBase<RESULTS> helper) {
        writerTask.start(helper);
    }

    public boolean isRunning() {
        return writerTask.isActive();
    }

    public void cancelTask(@IdRes final int taskId) {
        if (taskId == writerTask.getTaskId()) {
            writerTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }

    private static class DataWriterTask<RESULTS>
            extends MTask<RESULTS> {

        private static final String TAG = "DataWriterTask";

        @Nullable
        private DataWriterHelperBase<RESULTS> helper;

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
            helper = exportHelper;
            execute();
        }

        @Override
        public void cancel() {
            synchronized (this) {
                super.cancel();
                if (helper != null) {
                    helper.cancel();
                }
            }
        }

        @WorkerThread
        @Override
        @NonNull
        protected RESULTS doWork()
                throws DataWriterException,
                       CertificateException,
                       CredentialsException,
                       SSLException,
                       StorageException,
                       IOException {
            final Context context = ServiceLocator.getInstance().getLocalizedAppContext();


            //noinspection ConstantConditions
            return helper.write(context, this);
        }
    }
}
