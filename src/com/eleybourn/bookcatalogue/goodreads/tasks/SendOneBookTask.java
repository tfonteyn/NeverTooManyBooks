package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.BaseTask;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * Start a background task that exports a single books to Goodreads.
 * <p>
 * The AsyncTask runs a network and authorization check first.
 * If successful, an actual GoodReadsTasks {@link BaseTask} is kicked of to do the actual work.
 */
public class SendOneBookTask
        extends AsyncTask<Void, Void, Integer> {

    @NonNull
    private final WeakReference<TaskListener<Object, Integer>> mTaskListener;

    @NonNull
    private final String mTaskDescription;
    private final long mBookId;
    @Nullable
    private Exception mException;

    public SendOneBookTask(@NonNull final Context context,
                           final long bookId,
                           @NonNull final TaskListener<Object, Integer> taskListener) {

        mTaskListener = new WeakReference<>(taskListener);
        mBookId = bookId;
        mTaskDescription = context.getString(R.string.gr_send_book_to_goodreads, bookId);
    }

    @Override
    @NonNull
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("GR.SendOneBookTask");

        try {
            if (!NetworkUtils.isNetworkAvailable()) {
                return R.string.error_no_internet_connection;
            }
            int msg = SendBooksLegacyTaskBase.checkWeCanExport();
            if (msg == GoodreadsTasks.GR_RESULT_CODE_AUTHORIZED) {
                if (isCancelled()) {
                    return R.string.progress_end_cancelled;
                }
                QueueManager.getQueueManager().enqueueTask(
                        new SendOneBookLegacyTask(mTaskDescription, mBookId),
                        QueueManager.Q_SMALL_JOBS);
                return R.string.gr_tq_task_has_been_queued;
            }
            return msg;
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            mException = e;
            return R.string.error_unexpected_error;
        }
    }

    @Override
    @UiThread
    protected void onPostExecute(@NonNull final Integer result) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onTaskFinished(R.id.TASK_ID_GR_SEND_ONE_BOOK, mException == null,
                                               result, mException);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onPostExecute",
                             "WeakReference to listener was dead");
            }
        }
    }
}
