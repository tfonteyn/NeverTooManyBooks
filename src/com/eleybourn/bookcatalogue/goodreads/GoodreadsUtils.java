package com.eleybourn.bookcatalogue.goodreads;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.Task;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * TOMF: reverse logic on tasks/progress-fragment
 */
public final class GoodreadsUtils {

    /** file suffix for cover files. */
    public static final String FILENAME_SUFFIX = "_GR";

    /** task progress fragment tag. */
    private static final String TAG_GOODREADS_IMPORT_ALL = "grImportAll";
    private static final String TAG_GOODREADS_SEND_BOOKS = "grSendBooks";
    private static final String TAG_GOODREADS_SEND_ALL_BOOKS = "grSendAllBooks";
    private static final String TAG_GOODREADS_SEND_ONE_BOOK = "grSendOneBook";

    /** can be part of an image 'name' from Goodreads indicating there is no cover image. */
    private static final String NO_COVER = "nocover";

    private GoodreadsUtils() {
    }

    /**
     * @param imageName to check
     *
     * @return {@code true} if the name does NOT contain the string 'nocover'
     */
    @SuppressWarnings("WeakerAccess")
    @AnyThread
    public static boolean hasCover(final String imageName) {
        return imageName != null
                && !imageName.toLowerCase(LocaleUtils.getSystemLocale()).contains(NO_COVER);
    }

    /**
     * @param imageName to check
     *
     * @return {@code true} if the name DOES contain the string 'nocover'
     */
    @AnyThread
    public static boolean hasNoCover(final String imageName) {
        return imageName != null
                && imageName.toLowerCase(LocaleUtils.getSystemLocale()).contains(NO_COVER);
    }

    /**
     * Show the goodreads options list.
     */
    @SuppressWarnings("unused")
    @UiThread
    public static void showGoodreadsOptions(@NonNull final BaseActivity activity) {
        @SuppressLint("InflateParams")
        View root = activity.getLayoutInflater().inflate(R.layout.goodreads_options_list, null);

        final AlertDialog dialog = new AlertDialog.Builder(activity).setView(root).create();
        dialog.setTitle(R.string.title_select_an_action);
        dialog.show();

        View view;
        /* Goodreads SYNC Link */
        view = root.findViewById(R.id.lbl_sync_with_goodreads);
        view.setOnClickListener(v -> {
            importAll(activity, true);
            dialog.dismiss();
        });

        /* Goodreads IMPORT Link */
        view = root.findViewById(R.id.lbl_import_all_from_goodreads);
        view.setOnClickListener(v -> {
            importAll(activity, false);
            dialog.dismiss();
        });

        /* Goodreads EXPORT Link */
        view = root.findViewById(R.id.lbl_send_books_to_goodreads);
        view.setOnClickListener(v -> {
            sendBooks(activity);
            dialog.dismiss();
        });
    }

    /**
     * Ask the user which books to send, then send them.
     * <p>
     * Optionally, display a dialog warning the user that goodreads authentication is required;
     * gives them the options: 'request now', 'more info' or 'cancel'.
     */
    public static void sendBooks(@NonNull final FragmentActivity activity) {

        new AsyncTask<Void, Object, Integer>() {
            ProgressDialogFragment<Integer> mFragment;
            /**
             * {@link #doInBackground} should catch exceptions, and set this field.
             * {@link #onPostExecute} can then check it.
             */
            @Nullable
            private Exception mException;

            @Override
            protected void onPreExecute() {
                //noinspection unchecked
                mFragment = (ProgressDialogFragment)
                        activity.getSupportFragmentManager()
                                .findFragmentByTag(TAG_GOODREADS_SEND_BOOKS);
                if (mFragment == null) {
                    mFragment = ProgressDialogFragment.newInstance(
                            R.string.progress_msg_connecting_to_web_site, true, 0);
                    mFragment.setTask(R.id.TASK_ID_GR_SEND_BOOKS, this);
                    mFragment.show(activity.getSupportFragmentManager(), TAG_GOODREADS_SEND_BOOKS);
                }
            }

            @Override
            @NonNull
            @WorkerThread
            protected Integer doInBackground(final Void... params) {
                try {
                    return checkWeCanExport();
                } catch (RuntimeException e) {
                    Logger.error(this, e);
                    mException = e;
                    return R.string.error_unexpected_error;
                }
            }

            @Override
            @UiThread
            protected void onPostExecute(@NonNull final Integer result) {
                // cleanup the progress first
                mFragment.onTaskFinished(mException == null, result);
                switch (result) {
                    case 0:
                        // let the user choose which books to send
                        showConfirmationDialog(activity);
                        break;

                    case -1:
                        // ask to register
                        goodreadsAuthAlert(activity);
                        break;

                    default:
                        // specific response.
                        UserMessage.showUserMessage(activity, result);
                        break;
                }
            }
        }.execute();
    }

    /**
     * Called from {@link #sendBooks} to let the user confirm which books to send.
     *
     * @param activity the caller context
     */
    @UiThread
    private static void showConfirmationDialog(@NonNull final FragmentActivity activity) {
        // Get the title
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.gr_title_send_book)
                .setMessage(R.string.gr_send_books_to_goodreads_blurb)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                         activity.getString(
                                 R.string.gr_btn_send_updated),
                         (d, which) -> {
                             d.dismiss();
                             sendAllBooks(activity, true);
                         });

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                         activity.getString(R.string.gr_btn_send_all),
                         (d, which) -> {
                             d.dismiss();
                             sendAllBooks(activity, false);
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         activity.getString(android.R.string.cancel),
                         (d, which) -> d.dismiss());

        dialog.show();
    }

    /**
     * Start a background task that exports all books to goodreads.
     *
     * @param activity    the caller context
     * @param updatesOnly {@code true} if you only want to send updated book,
     *                    {@code false} to send ALL books.
     */
    private static void sendAllBooks(@NonNull final FragmentActivity activity,
                                     final boolean updatesOnly) {

        new AsyncTask<Void, Object, Integer>() {
            ProgressDialogFragment<Integer> mFragment;
            /**
             * {@link #doInBackground} should catch exceptions, and set this field.
             * {@link #onPostExecute} can then check it.
             */
            @Nullable
            private Exception mException;

            @Override
            protected void onPreExecute() {
                //noinspection unchecked
                mFragment = (ProgressDialogFragment)
                        activity.getSupportFragmentManager()
                                .findFragmentByTag(TAG_GOODREADS_SEND_ALL_BOOKS);
                if (mFragment == null) {
                    mFragment = ProgressDialogFragment.newInstance(
                            R.string.progress_msg_connecting_to_web_site, true, 0);
                    mFragment.setTask(R.id.TASK_ID_GR_SEND_ALL_BOOKS, this);
                    mFragment.show(activity.getSupportFragmentManager(),
                                   TAG_GOODREADS_SEND_ALL_BOOKS);
                }
            }

            @Override
            @WorkerThread
            @NonNull
            protected Integer doInBackground(final Void... params) {
                try {
                    int msg = checkWeCanExport();
                    if (msg == 0) {
                        if (isCancelled()) {
                            return R.string.progress_end_cancelled;
                        }
                        QueueManager.getQueueManager().enqueueTask(
                                new SendAllBooksTask(activity, updatesOnly),
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
            protected void onPostExecute(@NonNull final Integer result) {
                mFragment.onTaskFinished(mException == null, result);
                if (result == -1) {
                    goodreadsAuthAlert(activity);
                } else {
                    UserMessage.showUserMessage(activity, result);
                }
            }
        }.execute();
    }

    /**
     * Start a background task that exports a single books to goodreads.
     *
     * @param activity the caller context
     * @param bookId   the book to send
     */
    public static void sendOneBook(@NonNull final FragmentActivity activity,
                                   final long bookId) {

        new AsyncTask<Void, Object, Integer>() {
            ProgressDialogFragment<Integer> mFragment;
            /**
             * {@link #doInBackground} should catch exceptions, and set this field.
             * {@link #onPostExecute} can then check it.
             */
            @Nullable
            private Exception mException;

            @Override
            protected void onPreExecute() {
                //noinspection unchecked
                mFragment = (ProgressDialogFragment)
                        activity.getSupportFragmentManager()
                                .findFragmentByTag(TAG_GOODREADS_SEND_ONE_BOOK);
                if (mFragment == null) {
                    mFragment = ProgressDialogFragment.newInstance(
                            R.string.progress_msg_connecting_to_web_site, true, 0);
                    mFragment.setTask(R.id.TASK_ID_GR_SEND_ONE_BOOK, this);
                    mFragment.show(activity.getSupportFragmentManager(),
                                   TAG_GOODREADS_SEND_ONE_BOOK);
                }
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
                    if (msg == 0) {
                        QueueManager.getQueueManager()
                                    .enqueueTask(new SendOneBookTask(activity, bookId),
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
                mFragment.onTaskFinished(mException == null, result);
                if (result == -1) {
                    goodreadsAuthAlert(activity);
                } else {
                    UserMessage.showUserMessage(activity, result);
                }
            }
        }.execute();
    }

    /**
     * Start a background task that imports books from goodreads.
     */
    public static void importAll(@NonNull final BaseActivity activity,
                                 final boolean isSync) {

        new AsyncTask<Void, Object, Integer>() {

            ProgressDialogFragment<Integer> mFragment;

            /**
             * {@link #doInBackground} should catch exceptions, and set this field.
             * {@link #onPostExecute} can then check it.
             */
            @Nullable
            private Exception mException;

            @Override
            protected void onPreExecute() {
                //noinspection unchecked
                mFragment = (ProgressDialogFragment)
                        activity.getSupportFragmentManager()
                                .findFragmentByTag(TAG_GOODREADS_IMPORT_ALL);
                if (mFragment == null) {
                    mFragment = ProgressDialogFragment.newInstance(
                            R.string.progress_msg_connecting_to_web_site, true, 0);
                    mFragment.setTask(R.id.TASK_ID_GR_IMPORT_ALL, this);
                    mFragment.show(activity.getSupportFragmentManager(), TAG_GOODREADS_IMPORT_ALL);
                }
            }

            @Override
            @NonNull
            @WorkerThread
            protected Integer doInBackground(final Void... params) {
                try {
                    int msg = checkWeCanImport();
                    if (msg == 0) {
                        if (isCancelled()) {
                            return R.string.progress_end_cancelled;
                        }

                        QueueManager.getQueueManager()
                                    .enqueueTask(new ImportAllTask(activity, isSync),
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
            protected void onPostExecute(@NonNull final Integer result) {
                // cleanup the progress first
                mFragment.onTaskFinished(mException == null, result);
                //noinspection SwitchStatementWithTooFewBranches
                switch (result) {
                    case -1:
                        // ask to register
                        goodreadsAuthAlert(activity);
                        break;

                    default:
                        // specific response.
                        UserMessage.showUserMessage(activity, result);
                        break;
                }
            }

        }.execute();
    }

    /**
     * Check that no other sync-related jobs are queued, and that Goodreads is
     * authorized for this app.
     * <p>
     * This does network access and should not be called in the UI thread.
     *
     * @return StringRes id of message for user; or 0 for all ok, -1 when there are no credentials.
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
     * @return StringRes id of message for user; or 0 for all ok, -1 when there are no credentials.
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
     * @return StringRes id of message for user; or 0 for all ok, -1 when there are no credentials.
     */
    @WorkerThread
    @StringRes
    private static int checkGoodreadsAuth() {
        // Make sure GR is authorized for this app
        GoodreadsManager grMgr = new GoodreadsManager();
        if (!GoodreadsManager.hasCredentials() || !grMgr.hasValidCredentials()) {
            return -1;
        }
        return 0;
    }

    /**
     * Display a dialog warning the user that Goodreads authentication is required.
     * Gives the options: 'request now', 'more info' or 'cancel'.
     */
    @UiThread
    private static void goodreadsAuthAlert(@NonNull final FragmentActivity activity) {
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.gr_title_auth_access)
                .setMessage(R.string.gr_action_cannot_be_completed)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getString(android.R.string.ok),
                         (d, which) -> {
                             d.dismiss();
                             RequestAuthTask.start(activity.getSupportFragmentManager());
                         });

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                         activity.getString(R.string.btn_tell_me_more),
                         (d, which) -> {
                             d.dismiss();
                             Intent intent = new Intent(activity, GoodreadsRegisterActivity.class);
                             activity.startActivity(intent);
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         activity.getString(android.R.string.cancel),
                         (d, which) -> d.dismiss());

        dialog.show();
    }
}
