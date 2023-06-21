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
package com.hardbacknutter.nevertoomanybooks.database.tasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskFileUtils;

/**
 * Run 'PRAGMA optimize' on our databases.
 */
public class OptimizeDbTask
        extends LTask<Boolean>
        implements StartupViewModel.StartupTask {

    /** Log tag. */
    private static final String TAG = "OptimizeDbTask";

    /**
     * Constructor.
     *
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public OptimizeDbTask(@NonNull final TaskListener<Boolean> taskListener) {
        super(R.id.TASK_ID_DB_OPTIMIZE, TAG, taskListener);
    }

    @Override
    @UiThread
    public void start() {
        execute();
    }

    @SuppressWarnings("OverlyBroadThrowsClause")
    @NonNull
    @Override
    @WorkerThread
    protected Boolean doWork()
            throws StorageException {
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final Context context = serviceLocator.getLocalizedAppContext();

        publishProgress(1, context.getString(R.string.progress_msg_optimizing));

        // Cleanup temp files. Out of precaution we only trash jpg files
        final File tempDir = serviceLocator.getCoverStorage().getTempDir();
        TaskFileUtils.deleteDirectory(tempDir, file -> file.getName().endsWith(".jpg"),
                                      this);

        serviceLocator.getDb().optimize();
        serviceLocator.getCacheDb().optimize();

        return true;
    }
}
