/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener.TaskProgressMessage;

public class RestoreTask
        extends TaskBase<ImportOptions> {

    @NonNull
    private final ImportOptions mSettings;

    /**
     * Constructor.
     *
     * @param settings     the import settings
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public RestoreTask(@NonNull final ImportOptions /* in/out */ settings,
                       @NonNull final TaskListener<ImportOptions> taskListener) {
        super(R.id.TASK_ID_READ_FROM_ARCHIVE, taskListener);
        mSettings = settings;

        if (((mSettings.what & ImportOptions.MASK) == 0) || (mSettings.file == null)) {
            throw new IllegalArgumentException("Options must be specified");
        }
    }

    @Override
    @NonNull
    @WorkerThread
    protected ImportOptions doInBackground(final Void... params) {
        Thread.currentThread().setName("RestoreTask");

        Context userContext = App.getFakeUserContext();
        //noinspection ConstantConditions
        try (BackupReader reader = BackupManager.getReader(userContext, mSettings.file)) {

            reader.restore(mSettings, new ProgressListener() {

                private int mAbsPosition;
                private int mMaxPosition;

                @Override
                public void setMax(final int maxPosition) {
                    mMaxPosition = maxPosition;
                }

                @Override
                public void onProgressStep(final int delta,
                                           @Nullable final Object message) {
                    mAbsPosition += delta;
                    Object[] values = {message};
                    publishProgress(new TaskProgressMessage(mTaskId, mMaxPosition,
                                                            mAbsPosition, values));
                }

                @Override
                public void onProgress(final int absPosition,
                                       @Nullable final Object message) {
                    mAbsPosition = absPosition;
                    Object[] values = {message};
                    publishProgress(new TaskProgressMessage(mTaskId, mMaxPosition,
                                                            mAbsPosition, values));
                }

                @Override
                public boolean isCancelled() {
                    return RestoreTask.this.isCancelled();
                }
            });

        } catch (@NonNull final IOException e) {
            Logger.error(this, e);
            mException = e;
        } catch (@NonNull final ImportException e) {
            Logger.error(this, e);
            mException = e;
        }
        return mSettings;
    }
}
