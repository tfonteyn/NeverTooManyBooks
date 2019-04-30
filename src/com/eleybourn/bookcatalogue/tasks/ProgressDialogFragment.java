package com.eleybourn.bookcatalogue.tasks;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;

/**
 * Progress support for {@link AsyncTask}.
 * <p>
 * We're using setRetainInstance(true); so the task survives together with this fragment.
 */
public class ProgressDialogFragment<Results>
        extends DialogFragment {

    /** Fragment manager tag used if no custom tag is needed. */
    public static final String TAG = ProgressDialogFragment.class.getSimpleName();

    private static final String BKEY_DIALOG_IS_INDETERMINATE = TAG + ":isIndeterminate";
    private static final String BKEY_MAX = TAG + ":max";
    private static final String BKEY_CURRENT_MESSAGE = TAG + ":message";
    private static final String BKEY_CURRENT_VALUE = TAG + ":current";
    private static final String BKEY_WAS_CANCELLED = TAG + ":mWasCancelled";

    @Nullable
    private TextView mMessageView;
    @Nullable
    private ProgressBar mProgressBar;

    private boolean mIsIndeterminate;

    private boolean mWasCancelled;

    /** intermediate storage, as we'll only update this when progress is updated. */
    private int mMax;
    /** flag indicating the max value was updated. No need to add to onSaveInstanceState. */
    private boolean mUpdateMax;
    /** intermediate storage, needed for onSaveInstanceState. */
    @Nullable
    private String mMessage;
    /** the current task. */
    @Nullable
    private AsyncTask<Void, Object, Results> mTask;
    /** the current task. */
    @Nullable
    private Integer mTaskId;

    /**
     * @param <Results>       the type of the result object from the task.
     * @param titelId         Titel for the dialog, can be 0 for no title.
     * @param isIndeterminate type of progress
     * @param maxValue        maximum value for progress if isIndeterminate==false
     *                        Pass in 0 to keep the max as set in the layout file.
     *
     * @return the fragment.
     */
    @NonNull
    @UiThread
    public static <Results>
    ProgressDialogFragment<Results> newInstance(@StringRes final int titelId,
                                                final boolean isIndeterminate,
                                                final int maxValue) {
        ProgressDialogFragment<Results> frag = new ProgressDialogFragment<>();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, titelId);
        args.putBoolean(BKEY_DIALOG_IS_INDETERMINATE, isIndeterminate);
        args.putInt(BKEY_MAX, maxValue);
        frag.setArguments(args);
        return frag;
    }

    /**
     * This constructor is meant to be shared between all tasks used by:
     * {@link com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks}.
     * and should not be used/needed elsewhere.
     *
     * @param <Results>       the type of the result object from the task.
     * @param titelId         Titel for the dialog, can be 0 for no title.
     * @param isIndeterminate type of progress
     * @param maxValue        maximum value for progress if isIndeterminate==false
     *                        Pass in 0 to keep the max as set in the layout file.
     * @param message         initial message
     * @param currentValue    initial value
     *
     * @return the fragment.
     */
    @NonNull
    @UiThread
    public static <Results>
    ProgressDialogFragment<Results> newInstance(@StringRes final int titelId,
                                                final boolean isIndeterminate,
                                                final int maxValue,
                                                @Nullable final String message,
                                                final int currentValue) {
        ProgressDialogFragment<Results> frag = new ProgressDialogFragment<>();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, titelId);
        args.putBoolean(BKEY_DIALOG_IS_INDETERMINATE, isIndeterminate);
        if (message != null) {
            args.putString(BKEY_CURRENT_MESSAGE, message);
        }
        args.putInt(BKEY_CURRENT_VALUE, currentValue);
        args.putInt(BKEY_MAX, maxValue);
        frag.setArguments(args);
        return frag;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Control whether a fragment instance is retained across Activity
        // re-creation (such as from a configuration change).
        // VERY IMPORTANT. We do not want this destroyed as our task would get killed!
        setRetainInstance(true);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreateDialog(this, savedInstanceState);

        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_task_progress, null);

        mMessageView = root.findViewById(R.id.message);
        mProgressBar = root.findViewById(R.id.progressBar);

        Bundle args = requireArguments();

        // these are fixed
        @StringRes
        int titleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.progress_msg_please_wait);
        mIsIndeterminate = args.getBoolean(BKEY_DIALOG_IS_INDETERMINATE);
        //noinspection ConstantConditions
        mProgressBar.setIndeterminate(mIsIndeterminate);

        // the other settings can live in the savedInstance
        args = savedInstanceState == null ? args : savedInstanceState;

        // initial/current message.
        mMessage = args.getString(BKEY_CURRENT_MESSAGE);
        if (mMessage != null) {
            //noinspection ConstantConditions
            mMessageView.setText(mMessage);
        }
        // current and max values for a 'determinate' progress bar.
        if (!mIsIndeterminate) {
            mProgressBar.setProgress(args.getInt(BKEY_CURRENT_VALUE));
            mMax = args.getInt(BKEY_MAX);
            if (mMax > 0) {
                mProgressBar.setMax(mMax);
            }
        }
        mWasCancelled = args.getBoolean(BKEY_WAS_CANCELLED);

        //noinspection ConstantConditions
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(root)
                .create();
        if (titleId != 0) {
            dialog.setTitle(titleId);
        }
        //ENHANCE: this is really needed, as it would be to easy to cancel. But we SHOULD add a specific cancel-button!
        dialog.setCanceledOnTouchOutside(false);

        Tracker.exitOnCreateDialog(this);
        return dialog;
    }

    /**
     * Can optionally (but usually is) be linked with a task.
     *
     * @param taskId id of task
     * @param task   that will use us for progress updates.
     */
    public void setTask(final int taskId,
                        @Nullable final AsyncTask<Void, Object, Results> task) {
        mTaskId = taskId;
        mTask = task;
    }

    /**
     * Set the progress max value.
     * <p>
     * Don't update the view here, as we could be in a WorkerThread.
     */
    @AnyThread
    public void setMax(final int max) {
        mMax = max;
        // trigger the next onProgress to update the max value.
        mUpdateMax = true;
    }

    /**
     * Update the message and progress value.
     * <p>
     * Typically called from {@link AsyncTask} #onProgressUpdate
     *
     * @param absPosition absolute position
     * @param messageId   to display
     */
    @UiThread
    public void onProgress(@Nullable final Integer absPosition,
                           @Nullable final Integer messageId) {
        synchronized (this) {

            setAbsPosition(absPosition);


            if (messageId != null) {
                //noinspection ConstantConditions
                setMessage(getContext().getString(messageId));
            } else {
                setMessage("");
            }

        }
    }

    /**
     * Update the message and progress value.
     * <p>
     * Typically called from {@link AsyncTask} #onProgressUpdate
     *
     * @param absPosition absolute position
     * @param message     to display
     */
    @UiThread
    public void onProgress(@Nullable final Integer absPosition,
                           @Nullable final String message) {
        synchronized (this) {

            setAbsPosition(absPosition);
            setMessage(message);
        }
    }

    /**
     * Update the message on the progress dialog.
     *
     * @param message to display
     */
    @UiThread
    public void onProgress(@Nullable final String message) {
        synchronized (this) {
            setMessage(message);
        }
    }

    /**
     * Update the counter (bar) on the progress dialog.
     *
     * @param absPosition absolute position
     */
    @UiThread
    public void onProgress(final Integer absPosition) {
        synchronized (this) {
            setAbsPosition(absPosition);
        }
    }

    private void setMessage(@Nullable final String message) {
        if (message != null && !message.equals(mMessage)) {
            mMessage = message;
            if (mMessageView != null) {
                mMessageView.setText(mMessage);
            } else {
                Logger.warnWithStackTrace(this, "mMessageView was NULL");
            }
        }
    }

    private void setAbsPosition(final Integer absPosition) {
        if (mProgressBar != null) {
            if (mUpdateMax && (mMax != mProgressBar.getMax())) {
                mProgressBar.setMax(mMax);
                mUpdateMax = false;
            }

            if (absPosition != null && (absPosition != mProgressBar.getProgress())) {
                mProgressBar.setProgress(absPosition);
            }
        } else {
            Logger.warnWithStackTrace(this, "mProgressBar was NULL");
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BKEY_WAS_CANCELLED, mWasCancelled);

        outState.putInt(BKEY_MAX, mMax);
        outState.putString(BKEY_CURRENT_MESSAGE, mMessage);
        if (mProgressBar != null) {
            outState.putInt(BKEY_CURRENT_VALUE, mProgressBar.getProgress());
        }
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        // Tell the caller we're done. mTaskId will be null if there is no task.
        OnProgressCancelledListener.onProgressCancelled(this, mTaskId);
    }

    /** When we are dismissed we need to cancel the task if it's still alive. */
    @Override
    public void onDismiss(@NonNull final DialogInterface dialog) {
        super.onDismiss(dialog);
        // If true, the thread is interrupted immediately, which may do bad things.
        // If false, it guarantees a result is never returned (onPostExecute() isn't called)
        // but you have to repeatedly call isCancelled() in your doInBackground()
        // function to check if it should exit. For some tasks that might not be feasible.
        if (mTask != null) {
            mTask.cancel(false);
            mWasCancelled = true;
        }

        // Tell our caller the task was cancelled.
        if (mWasCancelled) {
            onCancel(dialog);
        }
    }

    /**
     * Called when the task finishes.
     *
     * @param success {@code true} if the task finished successfully
     * @param result  task result object
     * @param e       if the task finished with an exception, or null.
     */
    @UiThread
    public void onTaskFinished(final boolean success,
                               @Nullable final Results result,
                               @Nullable final Exception e) {
        // Make sure we check if it is resumed because we will crash if trying to dismiss
        // the dialog after the user has switched to another app.
        if (isResumed()) {
            dismiss();
        }

        // sanity check
        if (mTaskId == null) {
            throw new IllegalArgumentException("task id was NULL");
        }

        // Tell the caller we're done.
        OnTaskFinishedListener.onTaskFinished(this, mTaskId, success, result, e);

        // invalidate the current task as its finished. The dialog can be reused.
        mTaskId = null;
        mTask = null;
    }

    /**
     * ENHANCE: Work-around for bug in androidx library.
     * <p>
     * https://issuetracker.google.com/issues/36929400
     * <p>
     * Still not fixed in April 2019
     *
     * <p>
     * <p>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }

    public boolean isIndeterminate() {
        //don't get it from the actual progressbar as that might be null.
        return mIsIndeterminate;
    }

    /**
     * Used when the user cancels.
     */
    public interface OnProgressCancelledListener {

        /**
         * Convenience method. Try in order:
         * <li>getTargetFragment()</li>
         * <li>getParentFragment()</li>
         * <li>getActivity()</li>
         */
        @SuppressWarnings("InstanceofIncompatibleInterface")
        static void onProgressCancelled(@NonNull final Fragment fragment,
                                        final Integer taskId) {

            if (fragment.getTargetFragment() instanceof OnProgressCancelledListener) {
                ((OnProgressCancelledListener) fragment.getTargetFragment())
                        .onProgressCancelled(taskId);
            } else if (fragment.getParentFragment() instanceof OnProgressCancelledListener) {
                ((OnProgressCancelledListener) fragment.getParentFragment())
                        .onProgressCancelled(taskId);
            } else if (fragment.getActivity() instanceof OnProgressCancelledListener) {
                ((OnProgressCancelledListener) fragment.getActivity())
                        .onProgressCancelled(taskId);
            }
        }

        /**
         * @param taskId for the task; null if there was no embedded task.
         */
        void onProgressCancelled(@Nullable Integer taskId);
    }
}
