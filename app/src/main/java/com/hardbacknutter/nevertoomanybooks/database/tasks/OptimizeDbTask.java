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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Run 'PRAGMA optimize' on our databases.
 */
public class OptimizeDbTask
        extends TaskBase<Boolean> {

    /** Log tag. */
    private static final String TAG = "OptimizeDbTask";

    /** Database Access. */
    @NonNull
    private final DAO mDb;
    /** Flag: also do the covers database (or not). */
    private final boolean mDoCoversDb;

    /**
     * Constructor.
     *
     * @param taskId       a task identifier, will be returned in the task finished listener.
     * @param db           Database Access
     * @param doCoversDb   Flag: also do the covers database (or not)
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public OptimizeDbTask(final int taskId,
                          @NonNull final DAO db,
                          final boolean doCoversDb,
                          @NonNull final TaskListener<Boolean> taskListener) {
        super(taskId, taskListener);
        mDb = db;
        mDoCoversDb = doCoversDb;
    }

    @Override
    protected Boolean doInBackground(@Nullable final Void... voids) {
        Thread.currentThread().setName(TAG);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
            Log.d(TAG, "doInBackground|taskId=" + getTaskId());
        }
        publishProgress(new TaskListener.ProgressMessage(getTaskId(), context.getString(
                R.string.progress_msg_optimizing)));
        try {
            // small hack to make sure we always update the triggers.
            // Makes creating/modifying triggers MUCH easier.
            if (BuildConfig.DEBUG /* always */) {
                mDb.rebuildTriggers();
            }

            mDb.optimize();
            if (mDoCoversDb) {
                CoversDAO.optimize(context);
            }
            return true;

        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return false;
        }
    }
}
