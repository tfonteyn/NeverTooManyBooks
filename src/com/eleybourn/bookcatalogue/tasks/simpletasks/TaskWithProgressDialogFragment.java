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
package com.eleybourn.bookcatalogue.tasks.simpletasks;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment Class to wrap a trivial progress dialog around (generally) a single task.
 *
 * @author pjw
 */
public class TaskWithProgressDialogFragment
        extends DialogFragment {

    private static final String BKEY_MESSAGE = "message";
    private static final String BKEY_DIALOG_IS_INDETERMINATE = "isIndeterminate";
    private static final String BKEY_TASK_ID = "taskId";

    /** The underlying task queue. */
    @NonNull
    private final SimpleTaskQueue mQueue;
    /** Handler so we can detect UI thread. */
    private final Handler mHandler = new Handler();
    /** List of messages to be sent to the underlying activity, but not yet sent. */
    private final List<TaskMessage> mTaskMessages = new ArrayList<>();
    /**
     * List of messages queued; only used if activity not present when
     * sendTaskUserMessage() is called.
     */
    @Nullable
    private List<String> mMessages;
    /** Flag indicating the progress dialog was cancelled. */
    private boolean mWasCancelled;
    /** Max value of progress (for determinate progress). */
    @Nullable
    private String mMessage;
    /** Max value of progress (for determinate progress). */
    private int mMax;
    /** Current value of progress (for determinate progress). */
    private int mProgress;
    /** Flag indicating underlying field has changed so that progress dialog will be updated. */
    private boolean mMessageChanged;
    /** Flag indicating underlying field has changed so that progress dialog will be updated. */
    private boolean mProgressChanged;
    /** Flag indicating underlying field has changed so that progress dialog will be updated. */
    private boolean mMaxChanged;
    /** Flag indicating underlying field has changed so that progress dialog will be updated. */
    private boolean mNumberFormatChanged;
    /** Format of number part of dialog. */
    @Nullable
    private String mNumberFormat;
    /** Unique ID for this task. Can be used like menu or activity IDs. */
    private int mTaskId;
    /** Flag, defaults to <pp>true</pp>, that can be set by tasks and is passed to listeners. */
    private boolean mSuccess = true;
    /** Flag indicating a Refresher has been posted but not run yet. */
    private boolean mRefresherQueued;
    /** Runnable object to reload the dialog. */
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
     * Constructor.
     */
    public TaskWithProgressDialogFragment() {
        mQueue = new SimpleTaskQueue("DialogFragmentQueue");

        SimpleTaskQueue.OnTaskFinishListener mTaskFinishListener =
                new SimpleTaskQueue.OnTaskFinishListener() {
                    @Override
                    public void onTaskFinish(@NonNull final SimpleTaskQueue.SimpleTask task,
                                             @Nullable final Exception e) {
                        /* Dismiss dialog if all tasks finished */
                        if (!mQueue.hasActiveTasks()) {
                            queueAllTasksFinished();
                        }
                    }
                };
        mQueue.setTaskFinishListener(mTaskFinishListener);
    }

    /**
     * Convenience routine to show a dialog fragment and start the task.
     *
     * @param context         FragmentActivity of caller
     * @param messageId       Message to display
     * @param task            FragmentTask to run
     * @param isIndeterminate type of progress
     * @param taskId          Unique ID for this task. Can be used like menu or activity IDs
     */
    @NonNull
    public static TaskWithProgressDialogFragment newInstance(@NonNull final FragmentActivity context,
                                                             @StringRes final int messageId,
                                                             @NonNull final FragmentTask task,
                                                             final boolean isIndeterminate,
                                                             final int taskId) {
        TaskWithProgressDialogFragment frag = new TaskWithProgressDialogFragment();
        Bundle args = new Bundle();
        args.putInt(BKEY_MESSAGE, messageId);
        args.putInt(BKEY_TASK_ID, taskId);
        args.putBoolean(BKEY_DIALOG_IS_INDETERMINATE, isIndeterminate);
        frag.setArguments(args);
        frag.enqueue(task);
        frag.show(context.getSupportFragmentManager(), null);
        return frag;
    }

    /**
     * Queue a TaskMessage and then try to process the queue.
     */
    private void queueMessage(@NonNull final TaskMessage message) {

        synchronized (mTaskMessages) {
            mTaskMessages.add(message);
        }
        deliverMessages();
    }

    /**
     * Queue a TaskFinished message.
     */
    private void queueTaskFinished(@NonNull final FragmentTask task,
                                   @Nullable final Exception e) {
        queueMessage(new TaskFinishedMessage(task, e));
    }

    /**
     * Queue an AllTasksFinished message.
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
                    } catch (RuntimeException e) {
                        Logger.error(e);
                    }
                }
                toDeliver.clear();
            } while (count > 0);
        }
    }

    /**
     * Displays a message, or queue it as appropriate.
     */
    public void showUserMessage(@NonNull final String message) {
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
     * Post a runnable to the UI thread.
     */
    public void post(@NonNull final Runnable runnable) {
        mHandler.post(runnable);
    }

    /**
     * Enqueue a task for this fragment.
     */
    private void enqueue(@NonNull final FragmentTask task) {
        mQueue.enqueue(new FragmentTaskWrapper(task));
    }

    /**
     * Show any initial user message that needs showing.
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
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
    public void onCreate(@Nullable final Bundle savedInstanceState) {
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
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this, savedInstanceState);
        super.onActivityCreated(savedInstanceState);

        // Deliver any outstanding messages
        deliverMessages();

        // If no tasks left, exit
        if (!mQueue.hasActiveTasks()) {
            if (DEBUG_SWITCHES.SQP_FRAGMENT && BuildConfig.DEBUG) {
                Logger.info(this, "Tasks finished while activity absent, closing");
            }
            dismiss();
        }
        Tracker.exitOnActivityCreated(this);
    }

    /**
     * Create the underlying ProgressDialog.
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // savedInstanceState not used
        Bundle args = getArguments();
        @Deprecated
        ProgressDialog progressDialog = new ProgressDialog(getActivity());
        progressDialog.setCancelable(true);
        progressDialog.setCanceledOnTouchOutside(false);

        //noinspection ConstantConditions
        int messageId = args.getInt(BKEY_MESSAGE);
        if (messageId != 0) {
            progressDialog.setMessage(requireActivity().getString(messageId));
        }

        boolean isIndeterminate = args.getBoolean(BKEY_DIALOG_IS_INDETERMINATE);
        progressDialog.setIndeterminate(isIndeterminate);
        progressDialog.setProgressStyle(isIndeterminate ? ProgressDialog.STYLE_SPINNER
                                                        : ProgressDialog.STYLE_HORIZONTAL);

        // We can't use "this.requestUpdateProgress()" because getDialog() will still return null
        if (!isIndeterminate) {
            progressDialog.setMax(mMax);
            progressDialog.setProgress(mProgress);
            if (mMessage != null) {
                progressDialog.setMessage(mMessage);
            }
            progressDialog.setProgressNumberFormat(mNumberFormat);
        }

        return progressDialog;
    }

    @Override
    @CallSuper
    public void onCancel(@NonNull final DialogInterface dialog) {
        super.onCancel(dialog);
        mWasCancelled = true;
        mQueue.terminate();
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

    public void setSuccess(final boolean success) {
        mSuccess = success;
    }

    /**
     * Refresh the dialog, or post a reload to the UI thread.
     */
    private void requestUpdateProgress() {
        if (DEBUG_SWITCHES.SQP_FRAGMENT && BuildConfig.DEBUG) {
            Logger.info(this, mMessage + " (" + mProgress + '/' + mMax + ')');
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
     * Convenience method to step the progress by the passed delta.
     */
    public void onProgressStep(@Nullable final String message,
                               final int delta) {
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
     * Direct update of message and progress value.
     */
    public void onProgress(@Nullable final String message,
                           final int progress) {

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
        @Deprecated
        ProgressDialog progressDialog = (ProgressDialog) getDialog();
        if (progressDialog != null) {
            synchronized (this) {
                if (mMaxChanged) {
                    progressDialog.setMax(mMax);
                    mMaxChanged = false;
                }
                if (mNumberFormatChanged) {
                    // Called in a separate function so we can set API attributes
                    progressDialog.setProgressNumberFormat(mNumberFormat);
                    mNumberFormatChanged = false;
                }
                if (mMessageChanged) {
                    progressDialog.setMessage(mMessage);
                    mMessageChanged = false;
                }

                if (mProgressChanged) {
                    progressDialog.setProgress(mProgress);
                    mProgressChanged = false;
                }

            }
        }
    }

    /**
     * Set the progress max value.
     */
    public void setMax(final int max) {
        mMax = max;
        mMaxChanged = true;
        requestUpdateProgress();
    }

    /**
     * Set the progress number format.
     */
    public void setNumberFormat(@Nullable final String format) {
        synchronized (this) {
            mNumberFormat = format;
            mNumberFormatChanged = true;
        }
        requestUpdateProgress();
    }

    /**
     * ENHANCE: Work-around for bug in compatibility library.
     * <p>
     * https://issuetracker.google.com/issues/36929400
     * <p>
     * Still not fixed in January 2019
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

        void deliver(@NonNull Activity activity);
    }

    /**
     * Listener for OnTaskFinished messages.
     */
    public interface OnTaskFinishedListener {

        void onTaskFinished(@NonNull TaskWithProgressDialogFragment fragment,
                            int taskId,
                            boolean success,
                            boolean cancelled,
                            @NonNull FragmentTask task);
    }

    /**
     * Listener for OnAllTasksFinished messages.
     */
    public interface OnAllTasksFinishedListener {

        @SuppressWarnings("EmptyMethod")
        void onAllTasksFinished(@NonNull TaskWithProgressDialogFragment fragment,
                                int taskId,
                                boolean success,
                                boolean cancelled);
    }

    /**
     * Interface for 'FragmentTask' objects. Closely based on SimpleTask, but takes the
     * fragment as a parameter to all calls.
     * <p>
     * The {@link #setTag} & {@link #getTag} calls can be used to store generic info by a caller.
     *
     * @author pjw
     */
    public interface FragmentTask {

        /**
         * Run the task in it's own thread.
         */
        void run(@NonNull TaskWithProgressDialogFragment fragment,
                 @NonNull SimpleTaskContext taskContext)
                throws Exception;

        /**
         * Called in UI thread after task complete.
         */
        void onFinish(@NonNull TaskWithProgressDialogFragment fragment,
                      @Nullable Exception e);

        /** get an optional generic tag. */
        @Nullable
        Object getTag();

        /** set an optional generic tag. */
        void setTag(@NonNull Object tag);
    }

    /**
     * Trivial implementation of {@link FragmentTask}.
     *
     * @author pjw
     */
    public abstract static class FragmentTaskAbstract
            implements FragmentTask {

        private Object mTag;

        @Override
        public void onFinish(@NonNull final TaskWithProgressDialogFragment fragment,
                             @Nullable final Exception e) {
            if (e != null) {
                Logger.error(e);
                StandardDialogs.showUserMessage(fragment.requireActivity(),
                                                R.string.error_unexpected_error);
            }
        }

        @NonNull
        public Object getTag() {
            return mTag;
        }

        public void setTag(@NonNull final Object tag) {
            mTag = tag;
        }
    }

    /**
     * TaskFinished message.
     */
    private class TaskFinishedMessage
            implements TaskMessage {

        @NonNull
        private final FragmentTask mTask;
        @Nullable
        private final Exception mException;

        TaskFinishedMessage(@NonNull final FragmentTask task,
                            @Nullable final Exception e) {
            mTask = task;
            mException = e;
        }

        @Override
        public void deliver(@NonNull final Activity activity) {
            try {
                mTask.onFinish(TaskWithProgressDialogFragment.this, mException);
            } catch (RuntimeException e) {
                Logger.error(e);
            }
            try {
                if (activity instanceof OnTaskFinishedListener) {
                    ((OnTaskFinishedListener) activity).onTaskFinished(
                            TaskWithProgressDialogFragment.this,
                            mTaskId, mSuccess, mWasCancelled, mTask);
                }
            } catch (RuntimeException e) {
                Logger.error(e);
            }
        }
    }

    /**
     * AllTasksFinished message.
     */
    private class AllTasksFinishedMessage
            implements TaskMessage {

        AllTasksFinishedMessage() {
        }

        @Override
        public void deliver(@NonNull final Activity activity) {
            if (activity instanceof OnAllTasksFinishedListener) {
                ((OnAllTasksFinishedListener) activity).onAllTasksFinished(
                        TaskWithProgressDialogFragment.this, mTaskId, mSuccess,
                        mWasCancelled);
            }
            dismiss();
        }

    }

    /**
     * A SimpleTask wrapper for a FragmentTask.
     */
    private class FragmentTaskWrapper
            implements SimpleTaskQueue.SimpleTask {

        @NonNull
        private final FragmentTask mInnerTask;

        FragmentTaskWrapper(@NonNull final FragmentTask task) {
            mInnerTask = task;
        }

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext)
                throws Exception {
            try {
                mInnerTask.run(TaskWithProgressDialogFragment.this, taskContext);
            } catch (Exception e) {
                mSuccess = false;
                throw e;
            }
        }

        @Override
        public void onFinish(@Nullable final Exception e) {
            //  Queue a TaskFinished message to whoever created us.
            TaskWithProgressDialogFragment.this.queueTaskFinished(mInnerTask, e);
        }

    }
}
