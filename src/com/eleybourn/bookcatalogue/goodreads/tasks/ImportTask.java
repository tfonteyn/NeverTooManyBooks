package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.BaseTask;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * Start a background task that imports books from Goodreads.
 * It can either import 'all' or 'sync' books.
 * <p>
 * The AsyncTask runs a network and authorization check first.
 * If successful, an actual GoodReadsTasks {@link BaseTask} is kicked of to do the actual work.
 */
public class ImportTask
        extends AsyncTask<Void, Object, Integer> {

    @NonNull
    private final WeakReference<TaskListener<Object, Integer>> mTaskListener;
    @NonNull
    private final String mTaskDescription;

    private final boolean mIsSync;

    @Nullable
    private Exception mException;

    public ImportTask(final boolean isSync,
                      @NonNull final TaskListener<Object, Integer> taskListener) {

        mTaskListener = new WeakReference<>(taskListener);
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

    @Override
    @UiThread
    protected void onPostExecute(@Nullable final Integer result) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onTaskFinished(R.id.TASK_ID_GR_IMPORT, mException == null,
                                               result, mException);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onPostExecute",
                             "WeakReference to listener was dead");
            }
        }
    }
}
