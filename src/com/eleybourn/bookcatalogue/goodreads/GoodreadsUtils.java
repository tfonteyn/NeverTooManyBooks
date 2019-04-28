package com.eleybourn.bookcatalogue.goodreads;

import android.annotation.SuppressLint;
import android.content.Context;
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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.GoodreadsTask;
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
    public static void showGoodreadsOptions(@NonNull final Fragment fragment) {
        @SuppressLint("InflateParams")
        View root = fragment.getLayoutInflater().inflate(R.layout.goodreads_options_list, null);

        //noinspection ConstantConditions
        final AlertDialog dialog = new AlertDialog.Builder(fragment.getContext())
                .setView(root)
                .setTitle(R.string.title_select_an_action)
                .create();

        // Goodreads SYNC Link
        root.findViewById(R.id.lbl_sync_with_goodreads)
            .setOnClickListener(v -> {
                importAll(fragment, true);
                dialog.dismiss();
            });

        // Goodreads IMPORT Link
        root.findViewById(R.id.lbl_import_all_from_goodreads)
            .setOnClickListener(v -> {
                importAll(fragment, false);
                dialog.dismiss();
            });

        // Goodreads EXPORT Link
        root.findViewById(R.id.lbl_send_books_to_goodreads)
            .setOnClickListener(v -> {
                sendBooks(fragment);
                dialog.dismiss();
            });

        dialog.show();
    }

    /**
     * Ask the user which books to send, then send them.
     * <p>
     * Optionally, display a dialog warning the user that goodreads authentication is required;
     * gives them the options: 'request now', 'more info' or 'cancel'.
     */
    public static void sendBooks(@NonNull final Fragment fragment) {

        new AsyncTask<Void, Object, Integer>() {
            ProgressDialogFragment<Integer> mProgressDialog;
            /**
             * {@link #doInBackground} should catch exceptions, and set this field.
             * {@link #onPostExecute} can then check it.
             */
            @Nullable
            private Exception mException;

            @Override
            protected void onPreExecute() {
                FragmentManager fm = fragment.getFragmentManager();
                //noinspection ConstantConditions,unchecked
                mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(
                        TAG_GOODREADS_SEND_BOOKS);
                if (mProgressDialog == null) {
                    mProgressDialog = ProgressDialogFragment.newInstance(
                            R.string.progress_msg_connecting_to_web_site, true, 0);
                    mProgressDialog.setTask(R.id.TASK_ID_GR_SEND_BOOKS, this);
                    mProgressDialog.show(fm, TAG_GOODREADS_SEND_BOOKS);
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
                mProgressDialog.onTaskFinished(mException == null, result);
                switch (result) {
                    case 0:
                        // let the user choose which books to send
                        showConfirmationDialog(fragment);
                        break;

                    case -1:
                        // ask to register
                        goodreadsAuthAlert(fragment.getContext(), fragment.getFragmentManager());
                        break;

                    default:
                        // specific response.
                        //noinspection ConstantConditions
                        UserMessage.showUserMessage(fragment.getView(), result);
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
    private static void showConfirmationDialog(@NonNull final Fragment activity) {
        //noinspection ConstantConditions
        final AlertDialog dialog = new AlertDialog.Builder(activity.getContext())
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
     * @param fragment    the caller context
     * @param updatesOnly {@code true} if you only want to send updated book,
     *                    {@code false} to send ALL books.
     */
    private static void sendAllBooks(@NonNull final Fragment fragment,
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
                FragmentManager fm = fragment.getFragmentManager();
                //noinspection unchecked,ConstantConditions
                mFragment = (ProgressDialogFragment) fm.findFragmentByTag(
                        TAG_GOODREADS_SEND_ALL_BOOKS);
                if (mFragment == null) {
                    mFragment = ProgressDialogFragment.newInstance(
                            R.string.progress_msg_connecting_to_web_site, true, 0);
                    mFragment.setTask(R.id.TASK_ID_GR_SEND_ALL_BOOKS, this);
                    mFragment.show(fm, TAG_GOODREADS_SEND_ALL_BOOKS);
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
                        //noinspection ConstantConditions
                        QueueManager.getQueueManager().enqueueTask(
                                new SendAllBooksTask(fragment.getContext(), updatesOnly),
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
                    goodreadsAuthAlert(fragment.getContext(), fragment.getFragmentManager());
                } else {
                    //noinspection ConstantConditions
                    UserMessage.showUserMessage(fragment.getView(), result);
                }
            }
        }.execute();
    }

    /**
     * Start a background task that exports a single books to goodreads.
     * <p>
     * * @param context  caller context
     *
     * @param bookId the book to send
     */
    public static void sendOneBook(@NonNull final FragmentActivity activity,
                                   final long bookId) {

        FragmentManager fm = activity.getSupportFragmentManager();

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
                mFragment = (ProgressDialogFragment) fm.findFragmentByTag(
                        TAG_GOODREADS_SEND_ONE_BOOK);
                if (mFragment == null) {
                    mFragment = ProgressDialogFragment.newInstance(
                            R.string.progress_msg_connecting_to_web_site, true, 0);
                    mFragment.setTask(R.id.TASK_ID_GR_SEND_ONE_BOOK, this);
                    mFragment.show(fm, TAG_GOODREADS_SEND_ONE_BOOK);
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
                    goodreadsAuthAlert(activity, fm);
                } else {
                    UserMessage.showUserMessage(activity, result);
                }
            }
        }.execute();
    }

    /**
     * Start a background task that imports books from goodreads.
     * <p>
     * The AsyncTask does the "can we connect" check.
     * The actual work is done by a {@link GoodreadsTask}.
     */
    public static void importAll(@NonNull final Fragment fragment,
                                 final boolean isSync) {

        final Context context = fragment.getContext();
        final FragmentManager fm = fragment.getFragmentManager();
        final View userView = fragment.getView();

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
                //noinspection unchecked,ConstantConditions
                mFragment = (ProgressDialogFragment) fm.findFragmentByTag(TAG_GOODREADS_IMPORT_ALL);
                if (mFragment == null) {
                    mFragment = ProgressDialogFragment.newInstance(
                            R.string.progress_msg_connecting_to_web_site, true, 0);
                    mFragment.setTask(R.id.TASK_ID_GR_IMPORT_ALL, this);
                    mFragment.show(fm, TAG_GOODREADS_IMPORT_ALL);
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

                        //noinspection ConstantConditions
                        QueueManager.getQueueManager()
                                    .enqueueTask(new ImportAllTask(context, isSync),
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
                        goodreadsAuthAlert(context, fm);
                        break;

                    default:
                        // specific response.
                        //noinspection ConstantConditions
                        UserMessage.showUserMessage(userView, result);
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
     *
     * @param context         caller context
     * @param fragmentManager fm
     */
    @UiThread
    private static void goodreadsAuthAlert(final Context context,
                                           final FragmentManager fragmentManager) {
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.gr_title_auth_access)
                .setMessage(R.string.gr_action_cannot_be_completed)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok),
                         (d, which) -> {
                             d.dismiss();
                             RequestAuthTask.start(fragmentManager);
                         });

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                         context.getString(R.string.btn_tell_me_more),
                         (d, which) -> {
                             d.dismiss();
                             Intent intent = new Intent(context, GoodreadsRegisterActivity.class);
                             context.startActivity(intent);
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         context.getString(android.R.string.cancel),
                         (d, which) -> d.dismiss());

        dialog.show();
    }
}
