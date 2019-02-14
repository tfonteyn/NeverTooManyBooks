package com.eleybourn.bookcatalogue.goodreads;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.TaskWithProgress;
import com.eleybourn.bookcatalogue.tasks.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Task;

public final class GoodreadsUtils {

    /** file suffix for cover files. */
    public static final String FILENAME_SUFFIX = "_GR";

    /** can be part of an image 'name' from Goodreads indicating there is no cover image. */
    private static final String NO_COVER = "nocover";

    private GoodreadsUtils() {
    }

    /**
     * @param imageName to check
     *
     * @return <tt>true</tt> if the name does NOT contain the string 'nocover'
     */
    @SuppressWarnings("WeakerAccess")
    @AnyThread
    public static boolean hasCover(final String imageName) {
        return imageName != null && !imageName.toLowerCase().contains(NO_COVER);
    }

    /**
     * @param imageName to check
     *
     * @return <tt>true</tt> if the name DOES contain the string 'nocover'
     */
    @AnyThread
    public static boolean hasNoCover(final String imageName) {
        return imageName != null && imageName.toLowerCase().contains(NO_COVER);
    }

    /**
     * Show the goodreads options list.
     */
    @SuppressWarnings("unused")
    @UiThread
    public static void showGoodreadsOptions(@NonNull final BaseActivity activity) {
        LayoutInflater inf = activity.getLayoutInflater();
        @SuppressLint("InflateParams")
        View root = inf.inflate(R.layout.goodreads_options_list, null);

        final AlertDialog dialog = new AlertDialog.Builder(activity).setView(root).create();
        dialog.setTitle(R.string.title_select_an_action);
        dialog.show();

        View view;
        /* Goodreads SYNC Link */
        view = root.findViewById(R.id.lbl_sync_with_goodreads);
        // Make line flash when clicked.
        view.setBackgroundResource(android.R.drawable.list_selector_background);
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                importAllFromGoodreads(activity, true);
                dialog.dismiss();
            }
        });


        /* Goodreads IMPORT Link */
        view = root.findViewById(R.id.lbl_import_all_from_goodreads);
        // Make line flash when clicked.
        view.setBackgroundResource(android.R.drawable.list_selector_background);
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                importAllFromGoodreads(activity, false);
                dialog.dismiss();
            }
        });


        /* Goodreads EXPORT Link */
        view = root.findViewById(R.id.lbl_send_books_to_goodreads);
        // Make line flash when clicked.
        view.setBackgroundResource(android.R.drawable.list_selector_background);
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                sendBooksToGoodreads(activity);
                dialog.dismiss();
            }
        });

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
    private static int checkCanSendToGoodreads() {
        if (QueueManager.getQueueManager().hasActiveTasks(Task.CAT_GOODREADS_EXPORT_ALL)) {
            return R.string.gr_tq_requested_task_is_already_queued;
        }
        if (QueueManager.getQueueManager().hasActiveTasks(Task.CAT_GOODREADS_IMPORT_ALL)) {
            return R.string.gr_tq_import_task_is_already_queued;
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
    private static int checkGoodreadsAuth() {
        // Make sure GR is authorized for this app
        GoodreadsManager grMgr = new GoodreadsManager();

        if (!GoodreadsManager.hasCredentials() || !grMgr.hasValidCredentials()) {
            return -1;
        }

        return 0;
    }

    /**
     * Start a background task that exports a single books to goodreads.
     */
    public static void sendOneBookToGoodreads(@NonNull final FragmentActivity context,
                                              final long bookId) {
        new TaskWithProgress<Integer>(0, context,
                                      R.string.progress_msg_connecting_to_web_site, true) {

            @Override
            protected Integer doInBackground(final Void... params) {
                int msg = checkCanSendToGoodreads();
                if (msg == 0) {
                    QueueManager.getQueueManager()
                                .enqueueTask(new SendOneBookTask(bookId), QueueManager.QUEUE_MAIN);
                    return R.string.gr_tq_task_has_been_queued_in_background;
                }
                return msg;
            }

            @Override
            protected void onPostExecute(final Integer result) {
                if (result == -1) {
                    goodreadsAuthAlert(mFragment.requireActivity());
                } else {
                    StandardDialogs.showUserMessage(result);
                }

                super.onPostExecute(result);
            }
        }.execute();
    }

    /**
     * Ask the user which books to send, then send them.
     * <p>
     * Optionally, display a dialog warning the user that goodreads authentication is
     * required; gives them the options: 'request now', 'more info' or 'cancel'.
     */
    public static void sendBooksToGoodreads(@NonNull final FragmentActivity context) {

        new TaskWithProgress<Integer>(0, context,
                                      R.string.progress_msg_connecting_to_web_site, true) {

            @Override
            protected Integer doInBackground(final Void... params) {
                return checkCanSendToGoodreads();
            }

            @Override
            protected void onPostExecute(@NonNull final Integer result) {
                final FragmentActivity context = mFragment.requireActivity();
                switch (result) {
                    case 0:
                        showConfirmationDialog(context);
                        break;

                    case -1:
                        goodreadsAuthAlert(context);
                        return;

                    default:
                        StandardDialogs.showUserMessage(result);
                        break;
                }

                super.onPostExecute(null);
            }
        }.execute();
    }

    @UiThread
    private static void showConfirmationDialog(@NonNull final FragmentActivity context) {
        // Get the title
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.gr_title_send_book)
                .setMessage(R.string.gr_send_books_to_goodreads_blurb)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                         context.getString(
                                 R.string.gr_btn_send_updated),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                                 sendBooksToGoodreads(context, true);
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                         context.getString(R.string.gr_btn_send_all),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                                 sendBooksToGoodreads(context, false);
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         context.getString(android.R.string.cancel),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                             }
                         });

        dialog.show();
    }

    /**
     * Start a background task that exports all books to goodreads.
     */
    private static void sendBooksToGoodreads(@NonNull final FragmentActivity context,
                                             final boolean updatesOnly) {

        new TaskWithProgress<Integer>(0, context,
                                      R.string.progress_msg_connecting_to_web_site,
                                      true) {

            @Override
            protected Integer doInBackground(final Void... params) {
                int msg = checkCanSendToGoodreads();
                if (msg == 0) {
                    QueueManager.getQueueManager().enqueueTask(new SendAllBooksTask(updatesOnly),
                                                               QueueManager.QUEUE_MAIN);
                    return R.string.gr_tq_task_has_been_queued_in_background;
                }
                return msg;
            }

            @Override
            protected void onPostExecute(@NonNull final Integer result) {
                if (result == -1) {
                    goodreadsAuthAlert(mFragment.requireActivity());
                } else {
                    StandardDialogs.showUserMessage(result);
                }
                super.onPostExecute(result);
            }
        }.execute();
    }

    /**
     * Start a background task that imports books from goodreads.
     * <p>
     * We use a FragmentTask so that network access does not occur in the UI thread.
     */
    public static void importAllFromGoodreads(@NonNull final BaseActivity context,
                                              final boolean isSync) {

        new TaskWithProgress<Integer>(0, context,
                                      R.string.progress_msg_connecting_to_web_site,
                                      true) {

            @Override
            @Nullable
            protected Integer doInBackground(final Void... params) {
                if (QueueManager.getQueueManager().hasActiveTasks(Task.CAT_GOODREADS_IMPORT_ALL)) {
                    return R.string.gr_tq_requested_task_is_already_queued;
                }

                if (QueueManager.getQueueManager().hasActiveTasks(Task.CAT_GOODREADS_EXPORT_ALL)) {
                    return R.string.gr_tq_export_task_is_already_queued;
                }

                int msg = checkGoodreadsAuth();
                if (msg == -1 || msg != 0) {
                    return msg;
                }

                if (!mFragment.isCancelled()) {
                    QueueManager.getQueueManager()
                                .enqueueTask(new ImportAllTask(isSync), QueueManager.QUEUE_MAIN);
                    return R.string.gr_tq_task_has_been_queued_in_background;
                }
                return null;
            }

            @Override
            protected void onPostExecute(@Nullable final Integer result) {
                if (result != null) {
                    if (result == -1) {
                        goodreadsAuthAlert(context);
                    } else {
                        StandardDialogs.showUserMessage(result);
                    }
                }
                super.onPostExecute(result);
            }

        }.execute();
    }

    /**
     * Display a dialog warning the user that Goodreads authentication is required.
     * Gives the options: 'request now', 'more info' or 'cancel'.
     */
    @UiThread
    private static void goodreadsAuthAlert(@NonNull final FragmentActivity context) {
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.gr_title_auth_access)
                .setMessage(R.string.gr_action_cannot_be_completed)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                                 GoodreadsRegisterActivity
                                         .requestAuthorizationInBackground(context);
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                         context.getString(R.string.btn_tell_me_more),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                                 Intent i = new Intent(context, GoodreadsRegisterActivity.class);
                                 context.startActivity(i);
                             }
                         });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                         context.getString(android.R.string.cancel),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                             }
                         });

        dialog.show();

    }
}
