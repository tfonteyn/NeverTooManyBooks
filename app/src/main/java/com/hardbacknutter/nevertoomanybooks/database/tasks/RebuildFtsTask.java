/*
 * @Copyright 2020 HardBackNutter
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
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

/**
 * Rebuild FTS. Can take several seconds.
 */
public class RebuildFtsTask
        extends LTask<Boolean> {

    /** Log tag. */
    private static final String TAG = "RebuildFtsTask";

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    /**
     * Constructor.
     *
     * @param db           Database Access
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public RebuildFtsTask(@NonNull final DAO db,
                          @NonNull final TaskListener<Boolean> taskListener) {
        super(R.id.TASK_ID_DB_REBUILD_FTS, taskListener);
        mDb = db;
    }

    @NonNull
    @Override
    @WorkerThread
    protected Boolean doWork(@NonNull final Context context) {
        Thread.currentThread().setName(TAG);

        publishProgress(new ProgressMessage(getTaskId(), context.getString(
                R.string.progress_msg_rebuilding_search_index)));
        try {
            mDb.ftsRebuild();
            return true;

        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return false;

        } finally {
            // regardless of result, always disable as we do not want to rebuild/fail/rebuild...
            Scheduler.scheduleFtsRebuild(context, false);
        }
    }
}
