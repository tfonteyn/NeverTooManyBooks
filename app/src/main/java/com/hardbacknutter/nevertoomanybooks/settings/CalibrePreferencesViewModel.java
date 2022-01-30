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
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.security.cert.CertificateException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.MTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;

public class CalibrePreferencesViewModel
        extends ViewModel {

    private final ValidateConnectionTask mValidateConnectionTask = new ValidateConnectionTask();

    @Override
    protected void onCleared() {
        mValidateConnectionTask.cancel();
        super.onCleared();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Boolean>>> onConnectionSuccessful() {
        return mValidateConnectionTask.onFinished();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskResult<Exception>>> onConnectionFailed() {
        return mValidateConnectionTask.onFailure();
    }

    @NonNull
    LiveData<LiveDataEvent<TaskProgress>> onProgressUpdate() {
        return mValidateConnectionTask.onProgressUpdate();
    }

    void cancelTask(@IdRes final int taskId) {
        if (taskId == mValidateConnectionTask.getTaskId()) {
            mValidateConnectionTask.cancel();
        } else {
            throw new IllegalArgumentException("taskId=" + taskId);
        }
    }

    void validateConnection() {
        mValidateConnectionTask.connect();
    }

    /**
     * A simple connection validation.
     */
    private static class ValidateConnectionTask
            extends MTask<Boolean> {

        /** Log tag. */
        private static final String TAG = "CalibreValidateConTask";

        ValidateConnectionTask() {
            super(R.id.TASK_ID_VALIDATE_CONNECTION, TAG);
        }

        void connect() {
            execute();
        }

        /**
         * Make a test connection.
         *
         * @param context The localised Application context
         *
         * @return {@code true} on success
         *
         * @throws CertificateException on failures related to the user installed CA.
         * @throws IOException          on failures
         */
        @Nullable
        @Override
        protected Boolean doWork(@NonNull final Context context)
                throws IOException, CertificateException {
            return new CalibreContentServer(context).validateConnection();
        }
    }
}
