/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;

/**
 * Start a background task that imports books from Goodreads.
 * It can either import 'all' or 'sync' books.
 * <p>
 * We runs a network and authorization check first.
 * If successful, an actual GoodReads task {@link TQTask} is kicked of to do the real work.
 */
public class ImportTask
        extends TaskBase<Integer> {

    private static final String TAG = "ImportTask";

    @NonNull
    private final String mTaskDescription;

    private final boolean mIsSync;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param isSync       Flag to indicate sync data or import all.
     * @param taskListener for sending progress and finish messages to.
     */
    public ImportTask(@NonNull final Context context,
                      final boolean isSync,
                      @NonNull final TaskListener<Integer> taskListener) {
        super(R.id.TASK_ID_GR_IMPORT, taskListener);
        mIsSync = isSync;
        mTaskDescription = context.getString(R.string.gr_title_sync_with_goodreads);
    }

    @Override
    @NonNull
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("GR.ImportTask");
        Context context = App.getAppContext();

        try {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return R.string.error_network_no_connection;
            }
            int msg = ImportLegacyTask.checkWeCanImport();
            if (msg == GoodreadsTasks.GR_RESULT_CODE_AUTHORIZED) {
                if (isCancelled()) {
                    return R.string.progress_end_cancelled;
                }

                QueueManager.getQueueManager().enqueueTask(
                        new ImportLegacyTask(mTaskDescription, mIsSync), QueueManager.Q_MAIN);
                return R.string.gr_tq_task_has_been_queued;
            }
            return msg;
        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return R.string.error_unexpected_error;
        }
    }
}
