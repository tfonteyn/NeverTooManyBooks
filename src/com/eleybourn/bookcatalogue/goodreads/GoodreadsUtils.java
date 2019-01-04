package com.eleybourn.bookcatalogue.goodreads;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Task;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

public final class GoodreadsUtils {

    /** file suffix for cover files. */
    public static final String FILENAME_SUFFIX = "_GR";

    private GoodreadsUtils() {
    }

    /**
     * Show the goodreads options list.
     */
    public static void showGoodreadsOptions(@NonNull final BaseActivity activity) {
        LayoutInflater inf = activity.getLayoutInflater();
        @SuppressLint("InflateParams") // root==null as it's a dialog
                View root = inf.inflate(R.layout.goodreads_options_list, null);

        final AlertDialog dialog = new AlertDialog.Builder(activity).setView(root).create();
        dialog.setTitle(R.string.title_select_an_action);
        dialog.show();

        /* Goodreads SYNC Link */
        {
            View view = root.findViewById(R.id.lbl_sync_with_goodreads);
            // Make line flash when clicked.
            //noinspection ConstantConditions
            view.setBackgroundResource(android.R.drawable.list_selector_background);
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    importAllFromGoodreads(activity, true);
                    dialog.dismiss();
                }
            });
        }

        /* Goodreads IMPORT Link */
        {
            View view = root.findViewById(R.id.lbl_import_all_from_goodreads);
            // Make line flash when clicked.
            //noinspection ConstantConditions
            view.setBackgroundResource(android.R.drawable.list_selector_background);
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    importAllFromGoodreads(activity, false);
                    dialog.dismiss();
                }
            });
        }

        /* Goodreads EXPORT Link */
        {
            View view = root.findViewById(R.id.lbl_send_books_to_goodreads);
            // Make line flash when clicked.
            //noinspection ConstantConditions
            view.setBackgroundResource(android.R.drawable.list_selector_background);
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendBooksToGoodreads(activity);
                    dialog.dismiss();
                }
            });
        }
    }

    /**
     * Check that no other sync-related jobs are queued, and that Goodreads is
     * authorized for this app.
     *
     * This does network access and should not be called in the UI thread.
     *
     * @return StringRes id of message for user; or 0 for all ok, -1 when there are no credentials.
     */
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
     *
     * This does network access and should not be called in the UI thread.
     *
     * @return StringRes id of message for user; or 0 for all ok, -1 when there are no credentials.
     */
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
        SimpleTaskQueueProgressDialogFragment.FragmentTask task =
                new SimpleTaskQueueProgressDialogFragment.FragmentTaskAbstract() {
                    @Override
                    public void run(@NonNull final SimpleTaskQueueProgressDialogFragment fragment,
                                    @NonNull final SimpleTaskQueue.SimpleTaskContext taskContext) {
                        int msg = checkCanSendToGoodreads();
                        if (msg == 0) {
                            QueueManager.getQueueManager().enqueueTask(
                                    new SendOneBookTask(bookId), QueueManager.QUEUE_MAIN);
                            msg = R.string.gr_tq_task_has_been_queued_in_background;
                        }
                        setTag(msg);
                    }

                    @Override
                    public void onFinish(@NonNull final SimpleTaskQueueProgressDialogFragment fragment,
                                         @Nullable final Exception e) {
                        final Integer msg = (Integer) getTag();
                        if (msg == -1) {
                            fragment.post(new Runnable() {

                                @Override
                                public void run() {
                                    StandardDialogs.goodreadsAuthAlert(fragment.requireActivity());
                                }
                            });
                        } else {
                            fragment.showUserMessage(fragment.getString(msg));
                        }

                    }
                };
        SimpleTaskQueueProgressDialogFragment.newInstance(context,
                R.string.progress_msg_connecting_to_web_site, task, true, 0);
    }

    /**
     * Ask the user which books to send, then send them.
     *
     * Optionally, display a dialog warning the user that goodreads authentication is
     * required; gives them the options: 'request now', 'more info' or 'cancel'.
     */
    public static void sendBooksToGoodreads(@NonNull final FragmentActivity context) {

        SimpleTaskQueueProgressDialogFragment.FragmentTaskAbstract task =
                new SimpleTaskQueueProgressDialogFragment.FragmentTaskAbstract() {
                    /**
                     * Just check we can send. If so, onFinish() will be called.
                     */
                    @Override
                    public void run(@NonNull final SimpleTaskQueueProgressDialogFragment fragment,
                                    @NonNull final SimpleTaskQueue.SimpleTaskContext taskContext) {
                        int msg = checkCanSendToGoodreads();
                        setTag(msg);
                    }

                    @Override
                    public void onFinish(@NonNull final SimpleTaskQueueProgressDialogFragment fragment,
                                         @Nullable final Exception e) {
                        final FragmentActivity context = fragment.getActivity();
                        switch ((Integer) getTag()) {
                            case 0:
                                if (context != null) {
                                    // Get the title
                                    final AlertDialog dialog = new AlertDialog.Builder(context)
                                            .setTitle(R.string.gr_title_send_book)
                                            .setMessage(R.string.gr_send_books_to_goodreads_blurb)
                                            .setIconAttribute(android.R.attr.alertDialogIcon)
                                            .create();

                                    dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                                            context.getString(R.string.gr_btn_send_updated),
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(@NonNull final DialogInterface dialog, final int which) {
                                                    dialog.dismiss();
                                                    sendBooksToGoodreads(context, true);
                                                }
                                            });

                                    dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                                            context.getString(R.string.gr_btn_send_all),
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(@NonNull final DialogInterface dialog, final int which) {
                                                    dialog.dismiss();
                                                    sendBooksToGoodreads(context, false);
                                                }
                                            });

                                    dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                                            context.getString(android.R.string.cancel),
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(@NonNull final DialogInterface dialog, final int which) {
                                                    dialog.dismiss();
                                                }
                                            });

                                    dialog.show();
                                }
                                break;

                            case -1:
                                if (context != null) {
                                    fragment.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            StandardDialogs.goodreadsAuthAlert(context);
                                        }
                                    });
                                }
                                return;

                            default:
                                fragment.showUserMessage(fragment.getString((Integer) getTag()));
                                break;
                        }
                    }
                };
        // Run the task
        SimpleTaskQueueProgressDialogFragment.newInstance(context,
                R.string.progress_msg_connecting_to_web_site, task, true, 0);

    }

    /**
     * Start a background task that exports all books to goodreads.
     */
    private static void sendBooksToGoodreads(@NonNull final FragmentActivity context,
                                             final boolean updatesOnly) {
        SimpleTaskQueueProgressDialogFragment.FragmentTask task =
                new SimpleTaskQueueProgressDialogFragment.FragmentTaskAbstract() {
                    @Override
                    public void run(@NonNull final SimpleTaskQueueProgressDialogFragment fragment,
                                    @NonNull final SimpleTaskQueue.SimpleTaskContext taskContext) {
                        int msg = checkCanSendToGoodreads();
                        if (msg == 0) {
                            QueueManager.getQueueManager().enqueueTask(new SendAllBooksTask(updatesOnly), QueueManager.QUEUE_MAIN);
                            msg = R.string.gr_tq_task_has_been_queued_in_background;
                        }
                        setTag(msg);
                    }

                    @Override
                    public void onFinish(@NonNull final SimpleTaskQueueProgressDialogFragment fragment,
                                         @Nullable final Exception e) {
                        final int msg = (Integer) getTag();
                        if (msg == -1) {
                            fragment.post(new Runnable() {

                                @Override
                                public void run() {
                                    StandardDialogs.goodreadsAuthAlert(fragment.requireActivity());
                                }
                            });
                        } else {
                            fragment.showUserMessage(fragment.getString(msg));
                        }

                    }
                };
        SimpleTaskQueueProgressDialogFragment.newInstance(context,
                R.string.progress_msg_connecting_to_web_site, task, true, 0);
    }

    /**
     * Start a background task that imports books from goodreads.
     *
     * We use a FragmentTask so that network access does not occur in the UI thread.
     */
    public static void importAllFromGoodreads(@NonNull final BaseActivity context,
                                              final boolean isSync) {

        SimpleTaskQueueProgressDialogFragment.FragmentTask task =
                new SimpleTaskQueueProgressDialogFragment.FragmentTaskAbstract() {
                    @Override
                    public void run(@NonNull final SimpleTaskQueueProgressDialogFragment fragment,
                                    @NonNull final SimpleTaskQueue.SimpleTaskContext taskContext) {

                        if (QueueManager.getQueueManager()
                                .hasActiveTasks(Task.CAT_GOODREADS_IMPORT_ALL)) {
                            fragment.showUserMessage(fragment.getString(R.string.gr_tq_requested_task_is_already_queued));
                            return;
                        }
                        if (QueueManager.getQueueManager()
                                .hasActiveTasks(Task.CAT_GOODREADS_EXPORT_ALL)) {
                            fragment.showUserMessage(fragment.getString(R.string.gr_tq_export_task_is_already_queued));
                            return;
                        }

                        int msg = checkGoodreadsAuth();
                        if (msg == -1) {
                            fragment.post(new Runnable() {

                                @Override
                                public void run() {
                                    StandardDialogs.goodreadsAuthAlert(context);
                                }
                            });
                            return;
                        } else if (msg != 0) {
                            fragment.showUserMessage(fragment.getString(msg));
                            return;
                        }

                        if (!fragment.isCancelled()) {
                            QueueManager.getQueueManager()
                                    .enqueueTask(new ImportAllTask(isSync), QueueManager.QUEUE_MAIN);
                            fragment.showUserMessage(fragment.getString(R.string.gr_tq_task_has_been_queued_in_background));
                        }
                    }
                };
        SimpleTaskQueueProgressDialogFragment.newInstance(context,
                R.string.progress_msg_connecting_to_web_site, task, true, 0);
    }
}
