/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Rebuild FTS. Can take several seconds.
 */
public class RebuildFtsTask
        extends TaskBase<Boolean> {

    private static final String TAG = "RebuildFtsTask";

    /** Database Access. */
    @NonNull
    private final DAO mDb;

    /**
     * Constructor.
     *
     * @param taskId       a task identifier, will be returned in the task finished listener.
     * @param db           Database Access
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public RebuildFtsTask(final int taskId,
                          @NonNull final DAO db,
                          @NonNull final TaskListener<Boolean> taskListener) {
        super(taskId, taskListener);
        mDb = db;
    }

    @Override
    @WorkerThread
    protected Boolean doInBackground(@Nullable final Void... voids) {
        Thread.currentThread().setName(TAG);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
            Log.d(TAG, "doInBackground|taskId=" + getTaskId());
        }
        publishProgress(new TaskListener.ProgressMessage(getTaskId(), context.getString(
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
