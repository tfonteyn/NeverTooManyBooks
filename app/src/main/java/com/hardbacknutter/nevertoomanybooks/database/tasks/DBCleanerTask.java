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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.StartupViewModel;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.database.DBCleaner;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;

/**
 * Data cleaning. Done at app startup when deemed needed.
 */
public class DBCleanerTask
        extends LTask<Boolean>
        implements StartupViewModel.StartupTask {

    /** Log tag. */
    private static final String TAG = "DBCleanerTask";

    /**
     * Constructor.
     *
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public DBCleanerTask(@NonNull final TaskListener<Boolean> taskListener) {
        super(R.id.TASK_ID_DB_CLEANER, TAG, taskListener);
    }

    @Override
    @UiThread
    public void start() {
        execute();
    }

    @WorkerThread
    @Override
    @NonNull
    protected Boolean doWork() {
        final ServiceLocator serviceLocator = ServiceLocator.getInstance();
        final Context context = serviceLocator.getLocalizedAppContext();

        publishProgress(1, context.getString(R.string.progress_msg_optimizing));

        try {
            final int modified = new DBCleaner(
                    serviceLocator.getDb(),
                    serviceLocator::getAuthorDao,
                    serviceLocator::getSeriesDao,
                    serviceLocator::getPublisherDao,
                    serviceLocator::getBookshelfDao,
                    serviceLocator::getTocEntryDao,
                    serviceLocator::getLanguageDao)
                    .clean(context);

            if (modified > 0) {
                LoggerFactory.getLogger().w(TAG, "reposition modified=" + modified);
            }
            return true;

        } finally {
            // regardless of result, always disable as we do not want to rebuild/fail/rebuild...
            StartupViewModel.schedule(context, StartupViewModel.PK_RUN_MAINTENANCE, false);
        }
    }
}
