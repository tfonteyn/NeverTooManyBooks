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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;

/**
 * Start a background task that export books to Goodreads.
 * It can either send 'all' or 'updated-only' books.
 * <p>
 * We runs a network and authorization check first.
 * If successful, an actual GoodReads task {@link TQTask} is kicked of to do the actual work.
 */
public class SendBooksTask
        extends TaskBase<Void, GrStatus> {

    /** Log tag. */
    private static final String TAG = "GR.SendBooksTask";

    private final boolean mUpdatesOnly;

    /**
     * Constructor.
     *
     * @param updatesOnly  {@code true} for updated books only, or {@code false} all books.
     * @param taskListener for sending progress and finish messages to.
     */
    public SendBooksTask(final boolean updatesOnly,
                         @NonNull final TaskListener<GrStatus> taskListener) {
        super(R.id.TASK_ID_GR_SEND_BOOKS, taskListener);
        mUpdatesOnly = updatesOnly;
    }

    @Override
    @NonNull
    @WorkerThread
    protected GrStatus doInBackground(final Void... params) {
        Thread.currentThread().setName(TAG);
        final Context context = App.getTaskContext();

        try {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return GrStatus.NoInternet;
            }

            // Check that no other sync-related jobs are queued
            final GoodreadsAuth grAuth = new GoodreadsAuth(context);
            if (QueueManager.getQueueManager().hasActiveTasks(TQTask.CAT_EXPORT_ALL)) {
                return GrStatus.ExportTaskAlreadyQueued;
            }
            if (QueueManager.getQueueManager().hasActiveTasks(TQTask.CAT_IMPORT_ALL)) {
                return GrStatus.ImportTaskAlreadyQueued;
            }

            // Make sure Goodreads is authorized for this app
            if (!grAuth.hasValidCredentials(context)) {
                return GrStatus.CredentialsMissing;
            }

            if (isCancelled()) {
                return GrStatus.Cancelled;
            }

            QueueManager.getQueueManager().enqueueTask(
                    new SendBooksLegacyTask(context.getString(R.string.gr_title_send_book),
                                            mUpdatesOnly),
                    QueueManager.Q_MAIN);
            return GrStatus.TaskQueuedWithSuccess;

        } catch (@NonNull final RuntimeException e) {
            Logger.error(context, TAG, e);
            mException = e;
            return GrStatus.UnexpectedError;
        }
    }
}
