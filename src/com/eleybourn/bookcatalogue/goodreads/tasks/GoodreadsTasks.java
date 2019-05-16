package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.FormattedMessageException;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsRegisterActivity;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.BaseTask;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.Task;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Wrapper / single point of access to Goodreads tasks.
 *
 * These are AsyncTask classes that run a network and authorization check first.
 * If successful, an actual GoodReadsTasks is kicked of.
 * <p>
 * Note that currently there is a bit of a round-about way of starting and handling results.
 * The caller starts the task, with the caller being the listener.
 * The task finishes and sends results to the listener.
 * The listener redirects to {@link #handleGoodreadsTaskResult}
 * <p>
 * Why? well... because this is 'clean' although obviously not efficient.
 * BUT... as the plan is to move to WorkManager instead of task-queue, this at least makes it
 * invisible/transparent to the caller.
 * On the other hand, the above reason (mine) is just dumb.
 */
public final class GoodreadsTasks {

    /** file suffix for cover files. */
    public static final String FILENAME_SUFFIX = "_GR";
    /** can be part of an image 'name' from Goodreads indicating there is no cover image. */
    public static final String NO_COVER = "nocover";

    /** Task Integer 'Results' code. */
    private static final int GR_RESULT_CODE_AUTHORIZED = 0;
    /** Task Integer 'Results' code. */
    private static final int GR_RESULT_CODE_AUTHORIZATION_NEEDED = -1;
    /** Task Integer 'Results' code. */
    private static final int GR_RESULT_CODE_AUTHORIZED_FAILED = -2;

    private GoodreadsTasks() {
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

    /**
     * When a typical Goodreads AsyncTask finishes, the 'result' will be a {@code StringRes}
     * to display to the user (or an exception),
     * or a specific code indicating issues authentication.
     * <p>
     * This method provides handling for these outcomes.
     *
     * @param view     to tie user messages to
     * @param listener used if authorization needs to be requested.
     *                 Handles a recursive "auth needed" safely.
     */
    public static void handleGoodreadsTaskResult(final int taskId,
                                                 final boolean success,
                                                 @StringRes final Integer result,
                                                 @Nullable final Exception e,
                                                 @NonNull final View view,
                                                 @NonNull final TaskListener<Object, Integer> listener) {
        //Reminder:  'success' only means the call itself was successful.
        // It still depends on the 'result' code what the next step is.

        Context context = view.getContext();

        // if auth failed, either first or second time, complain and bail out.
        if (result == GR_RESULT_CODE_AUTHORIZED_FAILED
                || (result == GR_RESULT_CODE_AUTHORIZATION_NEEDED && taskId == R.id.TASK_ID_GR_REQUEST_AUTH)) {
            UserMessage.showUserMessage(
                    view, context.getString(R.string.error_authorization_failed,
                                            context.getString(R.string.goodreads)));
            return;
        }

        // ask to register
        if (result == GR_RESULT_CODE_AUTHORIZATION_NEEDED) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.gr_title_auth_access)
                    .setMessage(R.string.gr_action_cannot_be_completed)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setNegativeButton(android.R.string.cancel,
                                       (d, which) -> d.dismiss())
                    .setNeutralButton(R.string.btn_tell_me_more, (d, which) -> {
                        Intent intent = new Intent(context, GoodreadsRegisterActivity.class);
                        context.startActivity(intent);
                    })
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        UserMessage.showUserMessage(view, R.string.progress_msg_connecting);
                        new RequestAuthTask(listener).execute();
                    })
                    .create()
                    .show();
        } else {
            // authenticated fine, just show info results.
            if (success) {
                UserMessage.showUserMessage(view, result);

            } else {
                // some non-auth related error occurred.
                String msg = context.getString(result);
                if (e instanceof FormattedMessageException) {
                    msg += ' ' + ((FormattedMessageException) e)
                            .getFormattedMessage(context.getResources());
                } else if (e != null) {
                    msg += ' ' + e.getLocalizedMessage();
                }
                UserMessage.showUserMessage(view, msg);
            }
        }
    }

    public static class RequestAuthTask
            extends AsyncTask<Void, Object, Integer> {

        private final int mTaskId = R.id.TASK_ID_GR_REQUEST_AUTH;

        private final WeakReference<TaskListener<Object, Integer>> mTaskListener;

        @Nullable
        protected Exception mException;

        /**
         * Constructor.
         */
        @UiThread
        public RequestAuthTask(@NonNull TaskListener<Object, Integer> taskListener) {
            mTaskListener = new WeakReference<>(taskListener);
        }

        @Override
        @NonNull
        @WorkerThread
        protected Integer doInBackground(final Void... params) {
            Thread.currentThread().setName("GR.RequestAuthTask");

            //FIXME: should be done BEFORE starting the task
            if (!NetworkUtils.isNetworkAvailable()) {
                return R.string.error_no_internet_connection;
            }
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

        /**
         * If the task was cancelled (by the user cancelling the progress dialog) then
         * onPostExecute will NOT be called. See {@link #cancel(boolean)} java docs.
         *
         * @param result of the task
         */
        @Override
        @UiThread
        protected void onPostExecute(@Nullable final Integer result) {
            if (mTaskListener.get() != null) {
                mTaskListener.get().onTaskFinished(mTaskId, mException == null, result, mException);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
        }
    }

    /**
     * Start a background task that exports a single books to goodreads.
     */
    public static class SendOneBookTask
            extends AsyncTask<Void, Void, Integer> {

        @NonNull
        private final WeakReference<TaskListener<Object, Integer>> mTaskListener;

        @NonNull
        private final String mTaskDescription;
        private final int mTaskId = R.id.TASK_ID_GR_SEND_ONE_BOOK;
        private final long mBookId;
        @Nullable
        protected Exception mException;

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
                //FIXME: should be done BEFORE starting the task
                if (!NetworkUtils.isNetworkAvailable()) {
                    return R.string.error_no_internet_connection;
                }
                int msg = checkWeCanExport();
                if (msg == GR_RESULT_CODE_AUTHORIZED) {
                    if (isCancelled()) {
                        return R.string.progress_end_cancelled;
                    }
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
            if (mTaskListener.get() != null) {
                mTaskListener.get().onTaskFinished(mTaskId, mException == null, result, mException);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
        }
    }

    /**
     * Start a background task that export books to goodreads.
     * It can either send 'all' or 'updated-only' books.
     * <p>
     * The AsyncTask does the "can we connect" check.
     * The actual work is done by a {@link BaseTask}.
     */
    public static class SendBooksTask
            extends AsyncTask<Void, Object, Integer> {

        @NonNull
        private final WeakReference<TaskListener<Object, Integer>> mTaskListener;
        @NonNull
        private final String mTaskDescription;
        private final int mTaskId = R.id.TASK_ID_GR_SEND_BOOKS;

        private final boolean mUpdatesOnly;

        @Nullable
        protected Exception mException;

        public SendBooksTask(final boolean updatesOnly,
                             @NonNull final TaskListener<Object, Integer> taskListener) {

            mTaskListener = new WeakReference<>(taskListener);
            mUpdatesOnly = updatesOnly;
            mTaskDescription = App.getAppContext().getString(R.string.gr_title_send_book);
        }

        @Override
        @NonNull
        @WorkerThread
        protected Integer doInBackground(final Void... params) {
            Thread.currentThread().setName("GR.SendBooksTask");

            try {
                //FIXME: should be done BEFORE starting the task
                if (!NetworkUtils.isNetworkAvailable()) {
                    return R.string.error_no_internet_connection;
                }
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

        @Override
        @UiThread
        protected void onPostExecute(@Nullable final Integer result) {
            if (mTaskListener.get() != null) {
                mTaskListener.get().onTaskFinished(mTaskId, mException == null, result, mException);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
        }
    }

    /**
     * Start a background task that imports books from goodreads.
     * It can either import 'all' or 'sync' books.
     * <p>
     * The AsyncTask does the "can we connect" check.
     * The actual work is done by a {@link BaseTask}.
     */
    public static class ImportTask
            extends AsyncTask<Void, Object, Integer> {

        @NonNull
        private final WeakReference<TaskListener<Object, Integer>> mTaskListener;
        @NonNull
        private final String mTaskDescription;
        private final int mTaskId = R.id.TASK_ID_GR_IMPORT;

        private final boolean mIsSync;

        @Nullable
        protected Exception mException;

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
                //FIXME: should be done BEFORE starting the task
                if (!NetworkUtils.isNetworkAvailable()) {
                    return R.string.error_no_internet_connection;
                }
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

        @Override
        @UiThread
        protected void onPostExecute(@Nullable final Integer result) {
            if (mTaskListener.get() != null) {
                mTaskListener.get().onTaskFinished(mTaskId, mException == null, result, mException);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
        }
    }
}
