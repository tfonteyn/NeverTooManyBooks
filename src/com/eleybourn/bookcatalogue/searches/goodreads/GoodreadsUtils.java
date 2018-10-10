package com.eleybourn.bookcatalogue.searches.goodreads;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BookCatalogueActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressFragment.FragmentTaskAbstract;

public class GoodreadsUtils {
    public static final String GOODREADS_FILENAME_SUFFIX = "_GR";

    /**
     * Show the goodreads options list
     */
    public static void showGoodreadsOptions(@NonNull final BookCatalogueActivity activity) {
        LayoutInflater inf = activity.getLayoutInflater();
        @SuppressLint("InflateParams") // root==null as it's a dialog
        View root = inf.inflate(R.layout.goodreads_options_list, null);

        final AlertDialog grDialog = new AlertDialog.Builder(activity).setView(root).create();
        grDialog.setTitle(R.string.select_an_action);
        grDialog.show();

        /* Goodreads SYNC Link */
        {
            View view = grDialog.findViewById(R.id.sync_with_goodreads_label);
            // Make line flash when clicked.
            view.setBackgroundResource(android.R.drawable.list_selector_background);
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    GoodreadsUtils.importAllFromGoodreads(activity, true);
                    grDialog.dismiss();
                }
            });
        }

        /* Goodreads IMPORT Link */
        {
            View view = grDialog.findViewById(R.id.import_all_from_goodreads_label);
            // Make line flash when clicked.
            view.setBackgroundResource(android.R.drawable.list_selector_background);
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    GoodreadsUtils.importAllFromGoodreads(activity, false);
                    grDialog.dismiss();
                }
            });
        }

        /* Goodreads EXPORT Link */
        {
            View view = grDialog.findViewById(R.id.send_books_to_goodreads_label);
            // Make line flash when clicked.
            view.setBackgroundResource(android.R.drawable.list_selector_background);
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendBooksToGoodreads(activity);
                    grDialog.dismiss();
                }
            });
        }
    }

    /**
     * Start a background task that imports books from goodreads.
     *
     * We use a FragmentTask so that network access does not occur in the UI thread.
     */
    public static void importAllFromGoodreads(@NonNull final BookCatalogueActivity context, final boolean isSync) {

        FragmentTask task = new FragmentTaskAbstract() {
            @Override
            public void run(@NonNull final SimpleTaskQueueProgressFragment fragment,
                            @NonNull final SimpleTaskContext taskContext) {

                if (BCQueueManager.getQueueManager().hasActiveTasks(BCQueueManager.CAT_GOODREADS_IMPORT_ALL)) {
                    fragment.showToast(fragment.getString(R.string.requested_task_is_already_queued));
                    return;
                }
                if (BCQueueManager.getQueueManager().hasActiveTasks(BCQueueManager.CAT_GOODREADS_EXPORT_ALL)) {
                    fragment.showToast(fragment.getString(R.string.export_task_is_already_queued));
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
                    fragment.showToast(fragment.getString(msg));
                    return;
                }

                if (!fragment.isCancelled()) {
                    QueueManager.getQueueManager().enqueueTask(new ImportAllTask(isSync), BCQueueManager.QUEUE_MAIN);
                    fragment.showToast(fragment.getString(R.string.task_has_been_queued_in_background));
                }
            }
        };
        SimpleTaskQueueProgressFragment.runTaskWithProgress(context, R.string.connecting_to_web_site, task, true, 0);
    }

    /**
     * Check that goodreads is authorized for this app, and optionally allow user to request auth or more info
     *
     * This does network access and should not be called in the UI thread.
     *
     * @return Options indicating OK
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
     * Check that no other sync-related jobs are queued, and that goodreads is authorized for this app.
     *
     * This does network access and should not be called in the UI thread.
     *
     * @return Options indicating OK
     */
    private static int checkCanSendToGoodreads() {
        if (BCQueueManager.getQueueManager().hasActiveTasks(BCQueueManager.CAT_GOODREADS_EXPORT_ALL)) {
            return R.string.requested_task_is_already_queued;
        }
        if (BCQueueManager.getQueueManager().hasActiveTasks(BCQueueManager.CAT_GOODREADS_IMPORT_ALL)) {
            return R.string.import_task_is_already_queued;
        }

        return checkGoodreadsAuth();
    }

    /**
     * Start a background task that exports all books to goodreads.
     */
    private static void sendToGoodreads(@NonNull final FragmentActivity context, final boolean updatesOnly) {
        FragmentTask task = new FragmentTaskAbstract() {
            @Override
            public void run(@NonNull final SimpleTaskQueueProgressFragment fragment, @NonNull final SimpleTaskContext taskContext) {
                int msg = checkCanSendToGoodreads();
                if (msg == 0) {
                    QueueManager.getQueueManager().enqueueTask(new SendAllBooksTask(updatesOnly), BCQueueManager.QUEUE_MAIN);
                    msg = R.string.task_has_been_queued_in_background;
                }
                setState(msg);
            }

            @Override
            public void onFinish(@NonNull final SimpleTaskQueueProgressFragment fragment,
                                 @Nullable final Exception e) {
                final int msg = getState();
                if (msg == -1) {
                    fragment.post(new Runnable() {

                        @Override
                        public void run() {
                            StandardDialogs.goodreadsAuthAlert(fragment.getActivity());
                        }
                    });
                } else {
                    fragment.showToast(fragment.getString(msg));
                }

            }
        };
        SimpleTaskQueueProgressFragment.runTaskWithProgress(context, R.string.connecting_to_web_site, task, true, 0);
    }

    /**
     * Ask the user which books to send, then send them.
     *
     * Optionally, display a dialog warning the user that goodreads authentication is required; gives them
     * the options: 'request now', 'more info' or 'cancel'.
     */
    public static void sendBooksToGoodreads(@NonNull final BookCatalogueActivity ctx) {

        FragmentTaskAbstract task = new FragmentTaskAbstract() {
            /**
             * Just check we can send. If so, onFinish() will be called.
             */
            @Override
            public void run(@NonNull final SimpleTaskQueueProgressFragment fragment,
                            @NonNull final SimpleTaskContext taskContext) {
                int msg = GoodreadsUtils.checkCanSendToGoodreads();
                setState(msg);
            }

            @Override
            public void onFinish(@NonNull final SimpleTaskQueueProgressFragment fragment,
                                 @Nullable final Exception e) {
                final FragmentActivity context = fragment.getActivity();
                switch (getState()) {
                    case 0:
                        if (context != null) {
                            // Get the title
                            final AlertDialog dialog = new AlertDialog.Builder(context)
                                    .setTitle(R.string.send_books_to_goodreads)
                                    .setMessage(R.string.send_books_to_goodreads_blurb)
                                    .setIconAttribute(android.R.attr.alertDialogIcon)
                                    .create();

                            dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                                    context.getString(R.string.send_updated),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(@NonNull final DialogInterface dialog, final int which) {
                                            dialog.dismiss();
                                            GoodreadsUtils.sendToGoodreads(context, true);
                                        }
                                    });

                            dialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                                    context.getString(R.string.send_all),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(@NonNull final DialogInterface dialog, final int which) {
                                            dialog.dismiss();
                                            GoodreadsUtils.sendToGoodreads(context, false);
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
                        fragment.showToast(fragment.getString(getState()));
                        break;
                }
            }
        };
        // Run the task
        SimpleTaskQueueProgressFragment.runTaskWithProgress(ctx, R.string.connecting_to_web_site, task, true, 0);

    }

}
