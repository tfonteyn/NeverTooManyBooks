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

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

/**
 * Run 'PRAGMA optimize' on our databases.
 */
public class OptimizeDbTask
        extends LTask<Boolean> {

    /** Log tag. */
    private static final String TAG = "OptimizeDbTask";


    /**
     * Constructor.
     *
     * @param taskListener for sending progress and finish messages to.
     */
    @UiThread
    public OptimizeDbTask(@NonNull final TaskListener<Boolean> taskListener) {
        super(R.id.TASK_ID_DB_OPTIMIZE, taskListener);
    }

    @NonNull
    @Override
    @WorkerThread
    protected Boolean doWork(@NonNull final Context context) {
        Thread.currentThread().setName(TAG);

        publishProgress(new ProgressMessage(getTaskId(), context.getString(
                R.string.progress_msg_optimizing)));
        try (final DAO db = new DAO(TAG)) {
            // small hack to make sure we always update the triggers.
            // Makes creating/modifying triggers MUCH easier.
            if (BuildConfig.DEBUG /* always */) {
                db.getDBHelper().createTriggers(db.getSyncDb());
            }

            db.getSyncDb().optimize();
            if (ImageUtils.isImageCachingEnabled(context)) {
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
