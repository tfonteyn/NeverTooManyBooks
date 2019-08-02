package com.hardbacknutter.nevertomanybooks.goodreads.tasks;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TQTask;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.utils.NetworkUtils;

/**
 * Start a background task that imports books from Goodreads.
 * It can either import 'all' or 'sync' books.
 * <p>
 * We runs a network and authorization check first.
 * If successful, an actual GoodReads task {@link TQTask} is kicked of to do the actual work.
 */
public class ImportTask
        extends TaskBase<Integer> {

    @NonNull
    private final String mTaskDescription;

    private final boolean mIsSync;

    /**
     * Constructor.
     *
     * @param isSync       Flag to indicate sync data or import all.
     * @param taskListener for sending progress and finish messages to.
     */
    public ImportTask(final boolean isSync,
                      @NonNull final TaskListener<Integer> taskListener) {
        super(R.id.TASK_ID_GR_IMPORT, taskListener);
        mIsSync = isSync;
        mTaskDescription = App.getAppContext().getString(R.string.gr_import_all_from_goodreads);
    }

    @Override
    @NonNull
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("GR.ImportTask");

        try {
            if (!NetworkUtils.isNetworkAvailable()) {
                return R.string.error_no_internet_connection;
            }
            int msg = ImportLegacyTask.checkWeCanImport();
            if (msg == GoodreadsTasks.GR_RESULT_CODE_AUTHORIZED) {
                if (isCancelled()) {
                    return R.string.progress_end_cancelled;
                }

                QueueManager.getQueueManager().enqueueTask(
                        new ImportLegacyTask(mTaskDescription, mIsSync),
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
