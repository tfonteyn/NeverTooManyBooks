/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.tasks;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fragment Class to wrap a trivial progress dialog around (generally) a single task.
 *
 * @author pjw
 */
public class SimpleTaskQueueProgressDialogFragment extends DialogFragment {
    private static final String BKEY_MESSAGE = "message";
    private static final String BKEY_DIALOG_IS_INDETERMINATE = "isIndeterminate";
    private static final String BKEY_TASK_ID = "taskId";

    /** The underlying task queue */
    @NonNull
    private final SimpleTaskQueue mQueue;
    /** Handler so we can detect UI thread */
    private final Handler mHandler = new Handler();
    /** List of messages to be sent to the underlying activity, but not yet sent */
    private final List<TaskMessage> mTaskMessages = new ArrayList<>();
    /** List of messages queued; only used if activity not present when showUserMessage() is called */
    @Nullable
    private List<String> mMessages = null;
    /** Options indicating dialog was cancelled */
    private boolean mWasCancelled = false;
    /** Max value of progress (for determinate progress) */
    @Nullable
    private String mMessage = null;
    /** Max value of progress (for determinate progress) */
    private int mMax;
    /** Current value of progress (for determinate progress) */
    private int mProgress = 0;
    /** Options indicating underlying field has changed so that progress dialog will be updated */
    private boolean mMessageChanged = false;
    /** Options indicating underlying field has changed so that progress dialog will be updated */
    private boolean mProgressChanged = false;
    /** Options indicating underlying field has changed so that progress dialog will be updated */
    private boolean mMaxChanged = false;
    /** Options indicating underlying field has changed so that progress dialog will be updated */
    private boolean mNumberFormatChanged = false;
    /** Format of number part of dialog */
    @Nullable
    private String mNumberFormat = null;
    /** Unique ID for this task. Can be used like menu or activity IDs */
    private int mTaskId;
    /** Options, defaults to true, that can be set by tasks and is passed to listeners */
    private boolean mSuccess = true;
    /** Options indicating a Refresher has been posted but not run yet */
    private boolean mRefresherQueued = false;
    /** Runnable object to reload the dialog */
    private final Runnable mRefresher = new Runnable() {
        @Override
        public void run() {
            synchronized (mRefresher) {
                mRefresherQueued = false;
                updateProgress();
            }
        }
    };

    /**
     * Constructor
     */
    public SimpleTaskQueueProgressDialogFragment() {
        mQueue = new SimpleTaskQueue("FragmentQueue");
		/* Dismiss dialog if all tasks finished */
        SimpleTaskQueue.OnTaskFinishListener mTaskFinishListener = new SimpleTaskQueue.OnTaskFinishListener() {

            @Override
            public void onTaskFinish(final @NonNull SimpleTaskQueue.SimpleTask task, final @Nullable Exception e) {
                // If there are no more tasks, close this dialog
                if (!mQueue.hasActiveTasks()) {
                    queueAllTasksFinished();
                }
            }
        };
        mQueue.setTaskFinishListener(mTaskFinishListener);
    }

    /**
     * Convenience routine to show a dialog fragment and start the task
     *
     * @param context Activity of caller
     * @param messageId Message to display
     * @param task    Task to run
     */
    @NonNull
    public static SimpleTaskQueueProgressDialogFragment runTaskWithProgress(final @NonNull FragmentActivity context,
                                                                            final @StringRes int messageId,
                                                                            final @NonNull FragmentTask task,
                                                                            final boolean isIndeterminate,
                                                                            final int taskId) {
        SimpleTaskQueueProgressDialogFragment frag = SimpleTaskQueueProgressDialogFragment.newInstance(messageId, isIndeterminate, taskId);
        frag.enqueue(task);
        frag.show(context.getSupportFragmentManager(), null);
        return frag;
    }

    @NonNull
    private static SimpleTaskQueueProgressDialogFragment newInstance(final @StringRes int messageId,
                                                                     final boolean isIndeterminate,
                                                                     final int taskId) {
        SimpleTaskQueueProgressDialogFragment frag = new SimpleTaskQueueProgressDialogFragment();
        Bundle args = new Bundle();
        args.putInt(BKEY_MESSAGE, messageId);
        args.putInt(BKEY_TASK_ID, taskId);
        args.putBoolean(BKEY_DIALOG_IS_INDETERMINATE, isIndeterminate);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Queue a TaskMessage and then try to process the queue.
     */
    private void queueMessage(final @NonNull TaskMessage m) {

        synchronized (mTaskMessages) {
            mTaskMessages.add(m);
        }
        deliverMessages();
    }

    /**
     * Queue a TaskFinished message
     */
    private void queueTaskFinished(final @NonNull FragmentTask t, final @Nullable Exception e) {
        queueMessage(new TaskFinishedMessage(t, e));
    }

    /**
     * Queue an AllTasksFinished message
     */
    private void queueAllTasksFinished() {
        queueMessage(new AllTasksFinishedMessage());
    }

    /**
     * If we have an Activity, deliver the current queue.
     */
    private void deliverMessages() {
        Activity activity = getActivity();
        if (activity != null) {
            List<TaskMessage> toDeliver = new ArrayList<>();
            int count;
            do {
                synchronized (mTaskMessages) {
                    toDeliver.addAll(mTaskMessages);
                    mTaskMessages.clear();
                }
                count = toDeliver.size();
                for (TaskMessage message : toDeliver) {
                    try {
                        message.deliver(activity);
                    } catch (Exception e) {
                        Logger.error(e);
                    }
                }
                toDeliver.clear();
            } while (count > 0);
        }
    }

    /**
     * Utility routine to display a message or queue it as appropriate.
     */
    public void showUserMessage(final @NonNull String message) {
        // Can only display in main thread.
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            synchronized (this) {
                Activity activity = getActivity();
                if (activity != null) {
                    StandardDialogs.showUserMessage(activity, message);
                } else {
                    // Assume the message was sent before the fragment was displayed; this
                    // list will be read in onAttach
                    if (mMessages == null) {
                        mMessages = new ArrayList<>();
                    }
                    mMessages.add(message);
                }
            }
        } else {
            // Post() it to main thread.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    showUserMessage(message);
                }
            });
        }
    }

    /**
     * Post a runnable to the UI thread
     */
    public void post(final @NonNull Runnable runnable) {
        mHandler.post(runnable);
    }

    /**
     * Enqueue a task for this fragment
     */
    private void enqueue(final @NonNull FragmentTask task) {
        mQueue.enqueue(new FragmentTaskWrapper(task));
    }

    /**
     * Show any user message that need showing
     */
    @Override
    @CallSuper
    public void onAttach(final @NonNull Context context) {
        super.onAttach(context);

        synchronized (this) {
            if (mMessages != null) {
                for (String message : mMessages) {
                    if (message != null && !message.isEmpty()) {
                        StandardDialogs.showUserMessage(requireActivity(), message);
                    }
                }
                mMessages.clear();
            }
        }
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Control whether a fragment instance is retained across Activity
        // re-creation (such as from a configuration change).
        // VERY IMPORTANT. We do not want this destroyed!
        setRetainInstance(true);

        //noinspection ConstantConditions
        mTaskId = getArguments().getInt(BKEY_TASK_ID);
    }

    @Override
    @CallSuper
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Deliver any outstanding messages
        deliverMessages();

        // If no tasks left, exit
        if (!mQueue.hasActiveTasks()) {
            if (DEBUG_SWITCHES.SQPFragment && BuildConfig.DEBUG) {
                Logger.info(this,"Tasks finished while activity absent, closing");
            }
            dismiss();
        }
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(final @Nullable Bundle savedInstanceState) {
        // savedInstanceState not used
        Bundle args = getArguments();
        Objects.requireNonNull(args);

        ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);

        int messageId = args.getInt(BKEY_MESSAGE);
        if (messageId != 0) {
            dialog.setMessage(requireActivity().getString(messageId));
        }

        boolean isIndeterminate = args.getBoolean(BKEY_DIALOG_IS_INDETERMINATE);
        dialog.setIndeterminate(isIndeterminate);
        dialog.setProgressStyle(isIndeterminate ? ProgressDialog.STYLE_SPINNER : ProgressDialog.STYLE_HORIZONTAL);

        // We can't use "this.requestUpdateProgress()" because getDialog() will still return null
        if (!isIndeterminate) {
            dialog.setMax(mMax);
            dialog.setProgress(mProgress);
            if (mMessage != null) {
                dialog.setMessage(mMessage);
            }
            dialog.setProgressNumberFormat(mNumberFormat);
        }

        return dialog;
    }

    @Override
    @CallSuper
    public void onCancel(final @NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        mWasCancelled = true;
        mQueue.finish();
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        // If task finished, dismiss.
        if (!mQueue.hasActiveTasks()) {
            dismiss();
        }
    }

    public boolean isCancelled() {
        return mWasCancelled;
    }

    public void setSuccess(boolean success) {
        mSuccess = success;
    }

    /**
     * Refresh the dialog, or post a reload to the UI thread
     */
    private void requestUpdateProgress() {
        if (DEBUG_SWITCHES.SQPFragment && BuildConfig.DEBUG) {
            Logger.info(this,mMessage + " (" + mProgress + "/" + mMax + ")");
        }
        if (Thread.currentThread() == mHandler.getLooper().getThread()) {
            updateProgress();
        } else {
            synchronized (mRefresher) {
                if (!mRefresherQueued) {
                    mHandler.post(mRefresher);
                    mRefresherQueued = true;
                }
            }
        }
    }

    /**
     * Convenience method to step the progress by the passed delta
     */
    public void step(final @Nullable String message, final int delta) {
        synchronized (this) {
            if (message != null) {
                mMessage = message;
                mMessageChanged = true;
            }
            mProgress += delta;
            mProgressChanged = true;
        }
        requestUpdateProgress();
    }

    /**
     * Direct update of message and progress value
     */
    public void onProgress(final @Nullable String message, final int progress) {

        synchronized (this) {
            if (message != null) {
                mMessage = message;
                mMessageChanged = true;
            }
            mProgress = progress;
            mProgressChanged = true;
        }

        requestUpdateProgress();
    }

    /**
     * Method, run in the UI thread, that updates the various dialog fields.
     */
    private void updateProgress() {
        ProgressDialog dialog = (ProgressDialog) getDialog();
        if (dialog != null) {
            synchronized (this) {
                if (mMaxChanged) {
                    dialog.setMax(mMax);
                    mMaxChanged = false;
                }
                if (mNumberFormatChanged) {
                    // Called in a separate function so we can set API attributes
                    dialog.setProgressNumberFormat(mNumberFormat);
                    mNumberFormatChanged = false;
                }
                if (mMessageChanged) {
                    dialog.setMessage(mMessage);
                    mMessageChanged = false;
                }

                if (mProgressChanged) {
                    dialog.setProgress(mProgress);
                    mProgressChanged = false;
                }

            }
        }
    }

    /**
     * Set the progress max value
     */
    public void setMax(final int max) {
        mMax = max;
        mMaxChanged = true;
        requestUpdateProgress();
    }

    /**
     * Set the progress number format
     */
    public void setNumberFormat(final @Nullable String format) {
        synchronized (this) {
            mNumberFormat = format;
            mNumberFormatChanged = true;
        }
        requestUpdateProgress();
    }

    /**
     * ENHANCE Work-around for bug in compatibility library:
     *
     * http://code.google.com/p/android/issues/detail?id=17423
     *
     * Still not fixed in September 2018
     */
    @Override
    @CallSuper
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }

    /**
     * Each message has a single method to deliver it and will only be called
     * when the underlying Activity is actually present.
     */
    private interface TaskMessage {
        void deliver(final @NonNull Activity activity);
    }

    /**
     * Listener for OnTaskFinished messages
     */
    public interface OnTaskFinishedListener {
        void onTaskFinished(final @NonNull SimpleTaskQueueProgressDialogFragment fragment,
                            final int taskId,
                            final boolean success,
                            final boolean cancelled,
                            final @NonNull FragmentTask task);
    }

    /**
     * Listener for OnAllTasksFinished messages
     */
    public interface OnAllTasksFinishedListener {
        @SuppressWarnings("EmptyMethod")
        void onAllTasksFinished(final @NonNull SimpleTaskQueueProgressDialogFragment fragment,
                                final int taskId,
                                final boolean success,
                                final boolean cancelled);
    }

    /**
     * Interface for 'FragmentTask' objects. Closely based on SimpleTask, but takes the
     * fragment as a parameter to all calls.
     *
     * @author pjw
     */
    public interface FragmentTask {
        /**
         * Run the task in it's own thread
         */
        void run(final @NonNull SimpleTaskQueueProgressDialogFragment fragment,
                 final @NonNull SimpleTaskContext taskContext);

        /**
         * Called in UI thread after task complete  TODO
         */
        void onFinish(final @NonNull SimpleTaskQueueProgressDialogFragment fragment,
                      final @Nullable Exception exception);
    }

    /**
     * Trivial implementation of {@link FragmentTask} that never calls {@link #onFinish}.
     * The {@link #setState} & {@link #getState} calls can be used to store state info by a caller.
     *
     * @author pjw
     */
    public abstract static class FragmentTaskAbstract implements FragmentTask {
        private int mState = 0;

        @Override
        public void onFinish(final @NonNull SimpleTaskQueueProgressDialogFragment fragment, final @Nullable Exception e) {
            if (e != null) {
                Logger.error(e);
                StandardDialogs.showUserMessage(fragment.requireActivity(), R.string.error_unexpected_error);
            }
        }

        public int getState() {
            return mState;
        }

        public void setState(final int state) {
            mState = state;
        }
    }

    /**
     * TaskFinished message.
     */
    private class TaskFinishedMessage implements TaskMessage {
        @NonNull
        final FragmentTask mTask;
        @Nullable
        final Exception mException;

        TaskFinishedMessage(final @NonNull FragmentTask task, final @Nullable Exception e) {
            mTask = task;
            mException = e;
        }

        @Override
        public void deliver(final @NonNull Activity activity) {
            try {
                mTask.onFinish(SimpleTaskQueueProgressDialogFragment.this, mException);
            } catch (Exception e) {
                Logger.error(e);
            }
            try {
                if (activity instanceof OnTaskFinishedListener) {
                    ((OnTaskFinishedListener) activity).onTaskFinished(
                            SimpleTaskQueueProgressDialogFragment.this,
                            mTaskId, mSuccess, mWasCancelled, mTask);
                }
            } catch (Exception e) {
                Logger.error(e);
            }

        }

    }

    /**
     * AllTasksFinished message.
     */
    private class AllTasksFinishedMessage implements TaskMessage {

        AllTasksFinishedMessage() {
        }

        @Override
        public void deliver(final @NonNull Activity activity) {
            if (activity instanceof OnAllTasksFinishedListener) {
                ((OnAllTasksFinishedListener) activity).onAllTasksFinished(SimpleTaskQueueProgressDialogFragment.this, mTaskId, mSuccess, mWasCancelled);
            }
            dismiss();
        }

    }

    /**
     * A SimpleTask wrapper for a FragmentTask.
     *
     * @author pjw
     */
    private class FragmentTaskWrapper implements SimpleTaskQueue.SimpleTask {
        @NonNull
        private final FragmentTask mInnerTask;

        FragmentTaskWrapper(final @NonNull FragmentTask task) {
            mInnerTask = task;
        }

        @Override
        public void run(final @NonNull SimpleTaskContext taskContext) {
            try {
                mInnerTask.run(SimpleTaskQueueProgressDialogFragment.this, taskContext);
            } catch (Exception e) {
                mSuccess = false;
                throw e;
            }
        }

        @Override
        public void onFinish(final @Nullable Exception e) {
            SimpleTaskQueueProgressDialogFragment.this.queueTaskFinished(mInnerTask, e);
        }

    }
}
