package com.hardbacknutter.nevertomanybooks.goodreads.tasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TQTask;
import com.hardbacknutter.nevertomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.utils.NetworkUtils;

/**
 * Start a background task that export books to Goodreads.
 * It can either send 'all' or 'updated-only' books.
 * <p>
 * We runs a network and authorization check first.
 * If successful, an actual GoodReads task {@link TQTask} is kicked of to do the actual work.
 */
public class SendBooksTask
        extends TaskBase<Integer> {

    @NonNull
    private final String mTaskDescription;

    private final boolean mUpdatesOnly;

    /**
     * Constructor.
     *
     * @param context      Current context for accessing resources.
     * @param updatesOnly  {@code true} for updated books only, or {@code false} all books.
     * @param taskListener for sending progress and finish messages to.
     */
    public SendBooksTask(@NonNull final Context context,
                         final boolean updatesOnly,
                         @NonNull final TaskListener<Integer> taskListener) {
        super(R.id.TASK_ID_GR_SEND_BOOKS, taskListener);
        mUpdatesOnly = updatesOnly;
        mTaskDescription = context.getString(R.string.gr_title_send_book);
    }

    @Override
    @NonNull
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("GR.SendBooksTask");

        try {
            if (!NetworkUtils.isNetworkAvailable()) {
                return R.string.error_no_internet_connection;
            }
            GoodreadsManager grManager = new GoodreadsManager();
            int msg = SendBooksLegacyTask.checkWeCanExport(grManager);
            if (msg == GoodreadsTasks.GR_RESULT_CODE_AUTHORIZED) {
                if (isCancelled()) {
                    return R.string.progress_end_cancelled;
                }

                QueueManager.getQueueManager().enqueueTask(
                        new SendBooksLegacyTask(mTaskDescription, mUpdatesOnly),
                        QueueManager.Q_MAIN);
                return R.string.gr_tq_task_has_been_queued;
            }
            return msg;
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            mException = e;
            return R.string.error_unexpected_error;
        }
    }
}
