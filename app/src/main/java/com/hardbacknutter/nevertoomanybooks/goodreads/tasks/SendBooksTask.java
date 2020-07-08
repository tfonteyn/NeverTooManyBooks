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
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;

/**
 * Start a background task that export books to Goodreads.
 * It can either send 'all' or 'updated-only' books.
 * <p>
 * We runs a network and authorization check first.
 * If successful, an actual GoodReads task {@link BaseTQTask}
 * is kicked of to do the actual work.
 */
public class SendBooksTask
        extends VMTask<GrStatus> {

    /** Log tag. */
    private static final String TAG = "GR.SendBooksTask";

    /** Flag: send only starting from the last book we did earlier, or all books. */
    private boolean mFromLastBookId;
    /** Flag: send only the updated, or all books. */
    private boolean mUpdatesOnly;

    /**
     * Constructor.
     *
     * @param fromLastBookId {@code true} to send from the last book we did earlier,
     *                       {@code false} for all books.
     * @param updatesOnly    {@code true} to send updated books only,
     *                       {@code false} for all books.
     */
    public void startTask(final boolean fromLastBookId,
                          final boolean updatesOnly) {
        mFromLastBookId = fromLastBookId;
        mUpdatesOnly = updatesOnly;

        execute(R.id.TASK_ID_GR_SEND_BOOKS);
    }

    @Override
    @NonNull
    @WorkerThread
    protected GrStatus doWork() {
        Thread.currentThread().setName(TAG);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        if (!NetworkUtils.isNetworkAvailable(context)) {
            return new GrStatus(GrStatus.FAILED_NETWORK_UNAVAILABLE);
        }

        // Check that no other sync-related jobs are queued
        if (QueueManager.getQueueManager().hasActiveTasks(TQTask.CAT_EXPORT)) {
            return new GrStatus(GrStatus.FAILED_EXPORT_TASK_ALREADY_QUEUED);
        }
        if (QueueManager.getQueueManager().hasActiveTasks(TQTask.CAT_IMPORT)) {
            return new GrStatus(GrStatus.FAILED_IMPORT_TASK_ALREADY_QUEUED);
        }

        final GoodreadsAuth grAuth = new GoodreadsAuth(context);
        if (!grAuth.hasValidCredentials(context)) {
            return new GrStatus(GrStatus.FAILED_CREDENTIALS);
        }

        if (isCancelled()) {
            return new GrStatus(GrStatus.CANCELLED);
        }

        final String desc = context.getString(R.string.gr_title_send_book);
        final TQTask task = new SendBooksGrTask(desc, mFromLastBookId, mUpdatesOnly);
        QueueManager.getQueueManager().enqueueTask(QueueManager.Q_MAIN, task);

        return new GrStatus(GrStatus.SUCCESS_TASK_QUEUED);
    }
}
