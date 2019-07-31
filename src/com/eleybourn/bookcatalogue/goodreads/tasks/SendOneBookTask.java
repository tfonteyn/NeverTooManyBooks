package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.BookNotFoundException;
import com.eleybourn.bookcatalogue.utils.CredentialsException;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

/**
 * Start a background task that exports a single books to Goodreads.
 * This is used for sending single books, <strong>initiated by the user</strong>.
 * <p>
 * See also {@link SendOneBookLegacyTask} which is used internally by
 * {@link SendBooksLegacyTask}. The core of the task is (should be) identical.
 *
 * Note: this could/should be based on {@link com.eleybourn.bookcatalogue.tasks.TaskWithProgress}.
 * Instead, we experiment with the task listener.
 * pro: lightweight
 * con: caller restart means the listener is disconnected, so no progress updates.
 * But this task usually only taken 3-4 seconds (on wifi).
 */
public class SendOneBookTask
        extends AsyncTask<Void, Object, Integer> {

    @NonNull
    private final WeakReference<TaskListener<Object, Integer>> mTaskListener;

    private final long mBookId;
    @Nullable
    private Exception mException;

    public SendOneBookTask(final long bookId,
                           @NonNull final TaskListener<Object, Integer> taskListener) {
        mBookId = bookId;
        mTaskListener = new WeakReference<>(taskListener);
    }

    @Override
    @NonNull
    @WorkerThread
    protected Integer doInBackground(final Void... params) {
        Thread.currentThread().setName("GR.SendOneBookTask " + mBookId);

        GoodreadsManager.ExportResult result = null;

        publishProgress(R.string.progress_msg_connecting);

        try {
            if (!NetworkUtils.isNetworkAvailable()) {
                return R.string.error_no_internet_connection;
            }

            GoodreadsManager grManager = new GoodreadsManager();
            if (!grManager.hasValidCredentials()) {
                return GoodreadsTasks.GR_RESULT_CODE_AUTHORIZATION_NEEDED;
            }

            try (DAO db = new DAO();
                 BookCursor bookCursor = db.fetchBookForExportToGoodreads(mBookId)) {
                if (bookCursor.moveToFirst()) {
                    if (isCancelled()) {
                        return R.string.progress_end_cancelled;
                    }
                    publishProgress(R.string.progress_msg_please_wait);
                    result = grManager.sendOneBook(db, bookCursor.getCursorRow());
                    if (result == GoodreadsManager.ExportResult.sent) {
                        // Record the update
                        db.setGoodreadsSyncDate(mBookId);
                    }
                }
            }
        } catch (@NonNull final CredentialsException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.credentialsError;
            mException = e;
        } catch (@NonNull final BookNotFoundException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.notFound;
            mException = e;
        } catch (@NonNull final IOException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.ioError;
            mException = e;
        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            result = GoodreadsManager.ExportResult.error;
            mException = e;
        }

        if (result != null) {
            return result.getReasonStringId();
        }
        return R.string.error_unexpected_error;
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

    @Override
    protected void onProgressUpdate(final Object... values) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onTaskProgress(R.id.TASK_ID_GR_SEND_ONE_BOOK, values);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onProgressUpdate",
                             "WeakReference to listener was dead");
            }
        }
    }
}
