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
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;

public abstract class DataReaderViewModel<METADATA, RESULTS>
        extends ViewModel {

    private final MetaDataReaderTask<METADATA, RESULTS> metaDataTask =
            new MetaDataReaderTask<>();
    private final DataReaderTask<METADATA, RESULTS> readerTask =
            new DataReaderTask<>();

    @Override
    protected void onCleared() {
        metaDataTask.cancel();
        readerTask.cancel();
        super.onCleared();
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception; {@link TaskResult#getResult()} will always
     *         return a valid {@link Throwable} and never {@code null}
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Throwable>>> onReadMetaDataFailure() {
        return metaDataTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Optional<METADATA>>>> onReadMetaDataCancelled() {
        return metaDataTask.onCancelled();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Optional<METADATA>>>> onReadMetaDataFinished() {
        return metaDataTask.onFinished();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return readerTask.onProgress();
    }

    /**
     * Observable to receive failure.
     *
     * @return the result is the Exception; {@link TaskResult#getResult()} will always
     *         return a valid {@link Throwable} and never {@code null}
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Throwable>>> onReadDataFailure() {
        return readerTask.onFailure();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<RESULTS>>> onReadDataCancelled() {
        return readerTask.onCancelled();
    }

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<RESULTS>>> onReadDataFinished() {
        return readerTask.onFinished();
    }

    /**
     * Check if we have sufficient data to start an import.
     *
     * @return {@code true} if the "Go" button should be made available
     */
    public abstract boolean isReadyToGo();

    @NonNull
    public abstract DataReaderHelperBase<METADATA, RESULTS> getDataReaderHelper();

    @UiThread
    public void readMetaData() {
        metaDataTask.start(getDataReaderHelper());
    }

    @UiThread
    public void readData() {
        readerTask.start(getDataReaderHelper());
    }

    public boolean isRunning() {
        return readerTask.isActive();
    }

    public void cancelTask(@IdRes final int taskId) {
        if (taskId == metaDataTask.getTaskId()) {
            metaDataTask.cancel();

        } else if (taskId == readerTask.getTaskId()) {
            readerTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }

    private static class MetaDataReaderTask<METADATA, RESULTS>
            extends MTask<Optional<METADATA>> {

        private static final String TAG = "MetaDataReaderTask";

        @Nullable
        private DataReaderHelperBase<METADATA, RESULTS> helper;

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
            this.helper = helper;
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
        protected Optional<METADATA> doWork()
                throws DataReaderException,
                       CertificateException,
                       CredentialsException,
                       StorageException,
                       IOException {
            final Context context = ServiceLocator.getInstance().getLocalizedAppContext();


            //noinspection ConstantConditions
            return helper.readMetaData(context);
        }
    }

    private static class DataReaderTask<METADATA, RESULTS>
            extends MTask<RESULTS> {

        private static final String TAG = "DataReaderTask";

        @Nullable
        private DataReaderHelperBase<METADATA, RESULTS> helper;

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
            this.helper = helper;
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
                throws CertificateException,
                       CredentialsException,
                       DataReaderException,
                       StorageException,
                       IOException {
            final Context context = ServiceLocator.getInstance().getLocalizedAppContext();


            //noinspection ConstantConditions
            return helper.read(context, this);
        }
    }
}
