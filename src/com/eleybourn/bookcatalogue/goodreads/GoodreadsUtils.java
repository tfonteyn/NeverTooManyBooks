package com.eleybourn.bookcatalogue.goodreads;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.FormattedMessageException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.GoodreadsTask;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.Task;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;

/**
 * AsyncTask classes that run an authorization check first.
 * If successful, a GoodReadsTasks is kicked of.
 */
public final class GoodreadsUtils {

    /** file suffix for cover files. */
    public static final String FILENAME_SUFFIX = "_GR";
    /** can be part of an image 'name' from Goodreads indicating there is no cover image. */
    public static final String NO_COVER = "nocover";

    /** Task Integer 'Results' code. */
    public static final int GR_RESULT_CODE_AUTHORIZATION_NEEDED = -1;
    /** Task Integer 'Results' code. */
    public static final int GR_RESULT_CODE_AUTHORIZED_FAILED = -2;
    /** Task Integer 'Results' code. */
    private static final int GR_RESULT_CODE_AUTHORIZED = 0;

    private GoodreadsUtils() {
    }

    /**
     * Check that no other sync-related jobs are queued, and that Goodreads is
     * authorized for this app.
     * <p>
     * This does network access and should not be called in the UI thread.
     *
     * @return StringRes id of message for user,
     * or {@link #GR_RESULT_CODE_AUTHORIZED}
     * or {@link #GR_RESULT_CODE_AUTHORIZATION_NEEDED}.
     */
    @WorkerThread
    @StringRes
    private static int checkWeCanExport() {
        if (QueueManager.getQueueManager().hasActiveTasks(Task.CAT_GOODREADS_EXPORT_ALL)) {
            return R.string.gr_tq_requested_task_is_already_queued;
        }
        if (QueueManager.getQueueManager().hasActiveTasks(Task.CAT_GOODREADS_IMPORT_ALL)) {
            return R.string.gr_tq_import_task_is_already_queued;
        }

        return checkGoodreadsAuth();
    }

    /**
     * Check that no other sync-related jobs are queued, and that Goodreads is
     * authorized for this app.
     * <p>
     * This does network access and should not be called in the UI thread.
     *
     * @return StringRes id of message for user,
     * or {@link #GR_RESULT_CODE_AUTHORIZED}
     * or {@link #GR_RESULT_CODE_AUTHORIZATION_NEEDED}.
     */
    @WorkerThread
    @StringRes
    private static int checkWeCanImport() {
        if (QueueManager.getQueueManager()
                        .hasActiveTasks(Task.CAT_GOODREADS_IMPORT_ALL)) {
            return R.string.gr_tq_requested_task_is_already_queued;
        }
        if (QueueManager.getQueueManager()
                        .hasActiveTasks(Task.CAT_GOODREADS_EXPORT_ALL)) {
            return R.string.gr_tq_export_task_is_already_queued;
        }

        return checkGoodreadsAuth();
    }

    /**
     * Check that goodreads is authorized for this app, and optionally allow user to request
     * auth or more info.
     * <p>
     * This does network access and should not be called in the UI thread.
     *
     * @return StringRes id of message for user,
     * or {@link #GR_RESULT_CODE_AUTHORIZED}
     * or {@link #GR_RESULT_CODE_AUTHORIZATION_NEEDED}.
     */
    @WorkerThread
    @StringRes
    private static int checkGoodreadsAuth() {
        // Make sure GR is authorized for this app
        GoodreadsManager grMgr = new GoodreadsManager();
        if (!GoodreadsManager.hasCredentials() || !grMgr.hasValidCredentials()) {
            return GR_RESULT_CODE_AUTHORIZATION_NEEDED;
        }
        return GR_RESULT_CODE_AUTHORIZED;
    }

    public static class RequestAuthTask
            extends TaskWithProgress<Object, Integer> {

        /**
         * Constructor.
         *
         * @param progressDialog ProgressDialogFragment
         */
        @UiThread
        public RequestAuthTask(@NonNull ProgressDialogFragment<Object, Integer> progressDialog) {
            super(progressDialog);
        }

        protected int getId() {
            return R.id.TASK_ID_GR_REQUEST_AUTH;
        }

        @Override
        @NonNull
        @WorkerThread
        protected Integer doInBackground(final Void... params) {
            GoodreadsManager grMgr = new GoodreadsManager();
            // should only happen if the developer forgot to add the Goodreads keys.... (me)
            if (grMgr.noKey()) {
                return R.string.gr_auth_error;
            }

            // This next step can take several seconds....
            if (!grMgr.hasValidCredentials()) {
                try {
                    grMgr.requestAuthorization();
                } catch (IOException e) {
                    Logger.error(this, e);
                    return R.string.gr_access_error;
                } catch (AuthorizationException e) {
                    return GR_RESULT_CODE_AUTHORIZED_FAILED;
                }
            } else {
                return R.string.gr_auth_access_already_auth;
            }
            if (isCancelled()) {
                // return value not used as onPostExecute is not called
                return R.string.progress_end_cancelled;
            }
            return R.string.info_authorized;
        }
    }

    /**
     * Start a background task that exports a single books to goodreads.
     */
    public static class SendOneBookTask
            extends AsyncTask<Void, Void, Integer> {

        @NonNull
        private final String mTaskDescription;
        private final long mBookId;
        @Nullable
        protected Exception mException;

        public SendOneBookTask(@NonNull final Context context,
                               final long bookId) {
            mBookId = bookId;
            mTaskDescription = context.getString(R.string.gr_send_book_to_goodreads,
                                                 bookId);
        }

        protected int getId() {
            return R.id.TASK_ID_GR_SEND_ONE_BOOK;
        }

        @Override
        @NonNull
        @WorkerThread
        protected Integer doInBackground(final Void... params) {
            try {
                int msg = checkWeCanExport();
                if (isCancelled()) {
                    return R.string.progress_end_cancelled;
                }
                if (msg == GR_RESULT_CODE_AUTHORIZED) {
                    QueueManager.getQueueManager().enqueueTask(
                            new GrSendOneBookTask(mTaskDescription, mBookId),
                            QueueManager.Q_SMALL_JOBS);
                    return R.string.gr_tq_task_has_been_queued_in_background;
                }
                return msg;
            } catch (RuntimeException e) {
                Logger.error(this, e);
                mException = e;
                return R.string.error_unexpected_error;
            }
        }

        @Override
        @UiThread
        protected void onPostExecute(@NonNull final Integer result) {
            Context c = App.getAppContext();
            //TODO: quick and dirty using toast and app context. Revisit at a late date.
            if (mException == null) {
                Toast.makeText(c, result, Toast.LENGTH_SHORT).show();
            } else {
                String msg;
                if (mException instanceof FormattedMessageException) {
                    msg = ((FormattedMessageException) mException).getFormattedMessage(
                            c.getResources());
                } else {
                    msg = mException.getLocalizedMessage();
                }
                msg = c.getString(result) + ' ' + msg;
                Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Start a background task that export books to goodreads.
     * It can either send 'all' or 'updated-only' books.
     * <p>
     * The AsyncTask does the "can we connect" check.
     * The actual work is done by a {@link GoodreadsTask}.
     */
    public static class SendBooksTask
            extends TaskWithProgress<Object, Integer> {

        @NonNull
        private final String mTaskDescription;
        private final boolean mUpdatesOnly;

        public SendBooksTask(@NonNull final ProgressDialogFragment<Object, Integer> progressDialog,
                             final boolean updatesOnly) {
            super(progressDialog);

            mUpdatesOnly = updatesOnly;
            mTaskDescription = mProgressDialog.getString(R.string.gr_title_send_book);
        }

        protected int getId() {
            return R.id.TASK_ID_GR_SEND_BOOKS;
        }

        @Override
        @NonNull
        @WorkerThread
        protected Integer doInBackground(final Void... params) {
            try {
                int msg = checkWeCanExport();
                if (msg == GR_RESULT_CODE_AUTHORIZED) {
                    if (isCancelled()) {
                        return R.string.progress_end_cancelled;
                    }

                    QueueManager.getQueueManager().enqueueTask(
                            new GrSendAllBooksTask(mTaskDescription, mUpdatesOnly),
                            QueueManager.Q_MAIN);
                    return R.string.gr_tq_task_has_been_queued_in_background;
                }
                return msg;
            } catch (RuntimeException e) {
                Logger.error(this, e);
                mException = e;
                return R.string.error_unexpected_error;
            }
        }
    }

    /**
     * Start a background task that imports books from goodreads.
     * It can either import 'all' or 'sync' books.
     * <p>
     * The AsyncTask does the "can we connect" check.
     * The actual work is done by a {@link GoodreadsTask}.
     */
    public static class ImportTask
            extends TaskWithProgress<Object, Integer> {

        @NonNull
        private final String mTaskDescription;
        private final boolean mIsSync;

        public ImportTask(@NonNull final ProgressDialogFragment<Object, Integer> progressDialog,
                          final boolean isSync) {
            super(progressDialog);

            mIsSync = isSync;
            mTaskDescription = mProgressDialog.getString(R.string.gr_import_all_from_goodreads);
        }

        protected int getId() {
            return R.id.TASK_ID_GR_IMPORT;
        }

        @Override
        @NonNull
        @WorkerThread
        protected Integer doInBackground(final Void... params) {
            try {
                int msg = checkWeCanImport();
                if (msg == GR_RESULT_CODE_AUTHORIZED) {
                    if (isCancelled()) {
                        return R.string.progress_end_cancelled;
                    }

                    QueueManager.getQueueManager().enqueueTask(
                            new GrImportAllTask(mTaskDescription, mIsSync),
                            QueueManager.Q_MAIN);
                    return R.string.gr_tq_task_has_been_queued_in_background;
                }
                return msg;
            } catch (RuntimeException e) {
                Logger.error(this, e);
                mException = e;
                return R.string.error_unexpected_error;
            }
        }
    }
}
