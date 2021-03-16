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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.tasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.BaseTQTask;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.ImportGrTask;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

/**
 * Start a background task that imports books from Goodreads.
 * It can either import 'all' or 'sync' books.
 * <p>
 * We runs a network and authorization check first.
 * If successful, an actual GoodReads task {@link BaseTQTask}
 * is kicked of to do the real work.
 */
public class ImportTask
        extends LTask<GrStatus> {

    /** Log tag. */
    private static final String TAG = "GR.ImportTask";

    /** Whether to do a two-way sync, or a simple import. */
    private boolean mIsSync;

    /**
     * Constructor.
     *
     * @param taskListener for sending progress and finish messages to.
     */
    public ImportTask(@NonNull final TaskListener<GrStatus> taskListener) {
        super(R.id.TASK_ID_GR_IMPORT, TAG, taskListener);
    }

    /**
     * Start the import.
     *
     * @param isSync Flag to indicate sync data or import all.
     */
    public void startImport(final boolean isSync) {
        mIsSync = isSync;
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected GrStatus doWork(@NonNull final Context context) {

        if (!NetworkUtils.isNetworkAvailable()) {
            return new GrStatus(GrStatus.FAILED_NETWORK_UNAVAILABLE);
        }

        // Check that no other sync-related jobs are queued
        if (QueueManager.getInstance().hasActiveTasks(TQTask.CAT_IMPORT)) {
            return new GrStatus(GrStatus.FAILED_IMPORT_TASK_ALREADY_QUEUED);
        }
        if (QueueManager.getInstance().hasActiveTasks(TQTask.CAT_EXPORT)) {
            return new GrStatus(GrStatus.FAILED_EXPORT_TASK_ALREADY_QUEUED);
        }

        // Make sure Goodreads is authorized for this app
        final GoodreadsAuth grAuth = new GoodreadsAuth();
        if (!grAuth.hasValidCredentials(context)) {
            return new GrStatus(GrStatus.FAILED_CREDENTIALS);
        }

        if (isCancelled()) {
            return new GrStatus(GrStatus.CANCELLED);
        }

        final String desc = context.getString(R.string.gr_title_sync_with_goodreads);
        final TQTask task = new ImportGrTask(desc, context, mIsSync);
        QueueManager.getInstance().enqueueTask(QueueManager.Q_MAIN, task);

        return new GrStatus(GrStatus.SUCCESS_TASK_QUEUED);
    }
}
