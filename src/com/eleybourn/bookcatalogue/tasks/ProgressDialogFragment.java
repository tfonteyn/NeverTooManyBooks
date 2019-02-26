package com.eleybourn.bookcatalogue.tasks;

import android.annotation.SuppressLint;
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

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;

/**
 * // At this point the fragment may have been recreated due to a rotation,
 * // and there may be a TaskFragment lying around. So see if we can find it.
 * // Check to see if we have retained the worker fragment.
 * ProgressDialogFragment taskFragment = (ProgressDialogFragment)
 * getFragmentManager().findFragmentByTag(TASK_FRAGMENT_TAG);
 * if (taskFragment != null)
 * {
 * // Update the target fragment so it goes to this fragment instead of the old one.
 * // This will also allow the GC to reclaim the old MainFragment, which the TaskFragment
 * // keeps a reference to. Note that I looked in the code and setTargetFragment() doesn't
 * // use weak references. To be sure you aren't leaking, you may wish to make your own
 * // setTargetFragment() which does.
 * taskFragment.setTargetFragment(this, TASK_FRAGMENT);
 * }
 * }
 * <p>
 * <p>
 * <p>
 * <p>
 * Progress support for {@link TaskWithProgress}.
 * <p>
 * We're using setRetainInstance(true); so the task survives together with this fragment.
 * Do we still need to use onSaveInstanceState ? I suppose not ? TEST
 */
public class ProgressDialogFragment<Results>
        extends DialogFragment {

    public static final String TAG = ProgressDialogFragment.class.getSimpleName();

    private static final String BKEY_DIALOG_IS_INDETERMINATE = "isIndeterminate";
    private static final String BKEY_MAX = "max";
    private static final String BKEY_CURRENT_MESSAGE = "message";
    private static final String BKEY_CURRENT_VALUE = "current";
    private static final String BKEY_WAS_CANCELLED = "mWasCancelled";

    @Nullable
    private ProgressBar mProgressBar;
    @Nullable
    private TextView mMessageView;

    private boolean mWasCancelled;

    /** intermediate storage, as we'll only update this when progress is updated. */
    private int mMax;
    /** flag indicating the max value was updated. No need to add to onSaveInstanceState */
    private boolean mUpdateMax;
    /** intermediate storage, needed for onSaveInstanceState. */
    @Nullable
    private String mMessage;
    /** the task. */
    @Nullable
    private TaskWithProgress<Results> mTask;

    /**
     * @param titelId         Titel for the dialog, can be 0 for no title.
     * @param isIndeterminate type of progress
     * @param max             maximum value for progress if isIndeterminate==false
     *                        Pass in 0 to keep the max as set in the layout file.
     *
     * @return the fragment.
     */
    @NonNull
    @UiThread
    public static <Results>
    ProgressDialogFragment<Results> newInstance(@StringRes final int titelId,
                                                final boolean isIndeterminate,
                                                final int max) {
        ProgressDialogFragment<Results> frag = new ProgressDialogFragment<>();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, titelId);
        args.putBoolean(BKEY_DIALOG_IS_INDETERMINATE, isIndeterminate);
        args.putInt(BKEY_MAX, max);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Can optionally (but usually is) be linked with a task.
     *
     * @param task that will use us for progress updates.
     */
    public void setTask(@Nullable final TaskWithProgress<Results> task) {
        mTask = task;
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

        @SuppressLint("InflateParams")
        View root = requireActivity().getLayoutInflater()
                                     .inflate(R.layout.fragment_task_progress, null);
        mProgressBar = root.findViewById(R.id.progressBar);
        mMessageView = root.findViewById(R.id.message);

        // these are fixed
        int titleId = requireArguments().getInt(UniqueId.BKEY_DIALOG_TITLE);
        boolean isIndeterminate = requireArguments().getBoolean(BKEY_DIALOG_IS_INDETERMINATE);
        //noinspection ConstantConditions
        mProgressBar.setIndeterminate(isIndeterminate);

        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        // initial/current message.
        mMessage = args.getString(BKEY_CURRENT_MESSAGE);
        if (mMessage != null) {
            //noinspection ConstantConditions
            mMessageView.setText(mMessage);
        }
        // current and max values for a 'determinate' progress bar.
        if (!isIndeterminate) {
            mProgressBar.setProgress(args.getInt(BKEY_CURRENT_VALUE));
            mMax = args.getInt(BKEY_MAX);
            if (mMax > 0) {
                mProgressBar.setMax(mMax);
            }
        }
        mWasCancelled = args.getBoolean(BKEY_WAS_CANCELLED);

        AlertDialog dialog = new AlertDialog.Builder(requireActivity())
                .setView(root)
                .create();
        if (titleId != 0) {
            dialog.setTitle(titleId);
        }
        //ENHANCE: this is really needed, as it would be to easy to cancel. But we SHOULD add a specific cancel-button!
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    /**
     * Direct update of message and progress value.
     *
     * @param message       to display
     * @param progressCount absolute position
     */
    @UiThread
    public void onProgress(@Nullable final String message,
                           @Nullable final Integer progressCount) {
        synchronized (this) {
            if (mUpdateMax) {
                //noinspection ConstantConditions
                mProgressBar.setMax(mMax);
                mUpdateMax = false;
            }
            // only update when changed
            if (message != null && !message.equals(mMessage)) {
                mMessage = message;
                //noinspection ConstantConditions
                mMessageView.setText(mMessage);
            }
            if (progressCount != null) {
                mProgressBar.setProgress(progressCount);
            }
        }
    }

    /**
     * Update the message on the progress dialog.
     *
     * @param message to display
     */
    @UiThread
    public void setMessage(@Nullable final String message) {
        synchronized (this) {
            // only update when changed
            if (message != null && !message.equals(mMessage)) {
                mMessage = message;
                //noinspection ConstantConditions
                mMessageView.setText(mMessage);
            }
        }
    }

    /**
     * Update the counter (bar) on the progress dialog.
     *
     * @param progressCount absolute position
     */
    @UiThread
    public void onProgress(final int progressCount) {
        synchronized (this) {
            //noinspection ConstantConditions
            mProgressBar.setProgress(progressCount);
        }
    }

    /**
     * Set the progress max value.
     * <p>
     * Don't update the view here, as we could be in a WorkerThread.
     */
    @AnyThread
    public void setMax(final int max) {
        synchronized (this) {
            mMax = max;
            // trigger the next onProgress to update the max value.
            mUpdateMax = true;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BKEY_WAS_CANCELLED, mWasCancelled);
        outState.putString(BKEY_CURRENT_MESSAGE, mMessage);
        //noinspection ConstantConditions
        outState.putInt(BKEY_CURRENT_VALUE, mProgressBar.getProgress());
        outState.putInt(BKEY_MAX, mMax);
    }

    @Override
    public void onResume() {
        super.onResume();
        // This is a little hacky, but we will see if the task has finished while we weren't
        // in this activity, and then we can dismiss ourselves.
        if (mTask == null) {
            dismiss();
        }
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        Integer taskId = null;
        if (mTask != null) {
            taskId = mTask.getTaskId();
        }

        // Tell the caller we're done.
        if (getTargetFragment() instanceof ProgressDialogFragment.OnProgressCancelledListener) {
            ((OnProgressCancelledListener) getTargetFragment()).onProgressCancelled(taskId);
        } else if (getActivity() instanceof ProgressDialogFragment.OnProgressCancelledListener) {
            ((OnProgressCancelledListener) getActivity()).onProgressCancelled(taskId);
        }
    }

    /** When we are dismissed we need to cancel the task if it's still alive */
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
     * @param taskId  a task identifier, will be returned in the task finished listener.
     * @param success <tt>true</tt> if the task finished successfully
     * @param result  task result object
     */
    @UiThread
    void taskFinished(final int taskId,
                      final boolean success,
                      @Nullable final Results result) {
        // Make sure we check if it is resumed because we will crash if trying to dismiss
        // the dialog after the user has switched to another app.
        if (isResumed()) {
            dismiss();
        }

        // If we aren't resumed, setting the task to null will allow us to dismiss ourselves in
        // onResume().
        mTask = null;

        // Tell the caller we're done.
        if (getTargetFragment() instanceof OnTaskFinishedListener) {
            ((OnTaskFinishedListener) getTargetFragment()).onTaskFinished(taskId, success, result);
        } else if (getActivity() instanceof OnTaskFinishedListener) {
            ((OnTaskFinishedListener) getActivity()).onTaskFinished(taskId, success, result);
        }
    }

    /**
     * ENHANCE: Work-around for bug in androidx library.
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

    public boolean isIndeterminate() {
        //noinspection ConstantConditions
        return mProgressBar.isIndeterminate();
    }

    /**
     * Listener.
     */
    public interface OnTaskFinishedListener {

        /**
         * @param success <tt>true</tt> if the task finished successfully
         * @param result  the return object from the {@link AsyncTask#doInBackground} call
         */
        void onTaskFinished(int taskId,
                            boolean success,
                            Object result);
    }

    /**
     * Listener.
     */
    public interface OnProgressCancelledListener {

        /**
         * @param taskId for the task; null if there was no embedded task.
         */
        void onProgressCancelled(@Nullable Integer taskId);
    }
}
