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

import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;

/**
 * Progress support for {@link AsyncTask}. There can only be ONE task at a time.
 * <p>
 * We're using setRetainInstance(true); so the task survives together with this fragment.
 *
 * TODO: all in all.. this was a bad design. Redo!
 */
public class ProgressDialogFragment<Progress, Result>
        extends DialogFragment {

    /**
     * Fragment manager tag used if no custom tag is needed; e.g. when this dialog is shared
     * among multiple tasks.
     */
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
    private AsyncTask<Void, Progress, Result> mTask;
    /** the current task. */
    @Nullable
    private Integer mTaskId;

    @Nullable
    private WeakReference<TaskListener<Progress, Result>> mTaskListener;

    /**
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
    ProgressDialogFragment<Object, Results> newInstance(@StringRes final int titelId,
                                                        final boolean isIndeterminate,
                                                        final int maxValue) {
        ProgressDialogFragment<Object, Results> frag = new ProgressDialogFragment<>();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, titelId);
        args.putBoolean(BKEY_DIALOG_IS_INDETERMINATE, isIndeterminate);
        args.putInt(BKEY_MAX, maxValue);
        frag.setArguments(args);
        return frag;
    }

    public void setTaskListener(@Nullable final TaskListener<Progress, Result> taskListener) {
        mTaskListener = new WeakReference<>(taskListener);
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

        mMessageView = root.findViewById(R.id.progressMessage);
        mProgressBar = root.findViewById(R.id.progressBar);

        Bundle args = requireArguments();

        // these are fixed
        @StringRes
        int titleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.progress_msg_please_wait);
        boolean isIndeterminate = args.getBoolean(BKEY_DIALOG_IS_INDETERMINATE);
        //noinspection ConstantConditions
        mProgressBar.setIndeterminate(isIndeterminate);

        // the other settings can live in the savedInstance
        args = savedInstanceState == null ? args : savedInstanceState;

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

        @SuppressWarnings("ConstantConditions")
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(root)
                .create();
        if (titleId != 0) {
            dialog.setTitle(titleId);
        }
        // this is really needed, as it would be to easy to cancel without.
        // Cancel by 'back' press only.
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
    void setTask(final int taskId,
                 @Nullable final AsyncTask<Void, Progress, Result> task) {
        mTaskId = taskId;
        if (mTask != null) {
            mTask.cancel(true);
        }
        mTask = task;
    }

    /**
     * Called when the task finishes.
     *
     * @param success {@code true} if the task finished successfully
     * @param result  task result object
     * @param e       if the task finished with an exception, or {@code null}.
     */
    @UiThread
    public void onTaskFinished(final int taskId,
                               final boolean success,
                               @Nullable final Result result,
                               @Nullable final Exception e) {
        // Make sure we check if it is resumed (running) because we will crash if trying to dismiss
        // the dialog after the user has switched to another app.
        if (isResumed()) {
            if (BuildConfig.DEBUG) {
                Logger.debug(this, "onTaskFinished", "calling dismiss()");
            }
            dismiss();
        }

        // invalidate the current task as its finished. The dialog can be reused.
        mTaskId = null;
        mTask = null;

        // Tell the caller we're done.
        if (mTaskListener != null) {
            TaskListener<Progress, Result> listener = mTaskListener.get();
            if (listener != null) {
                listener.onTaskFinished(taskId, success, result, e);
            } else {
                // keep this as a throw, as not having the listener here would be bug
                throw new RuntimeException("WeakReference to listener was dead");
            }
        } else {
            throw new IllegalStateException("no TaskListener set.");
        }

    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {

        if (mTask != null) {
            mTask.cancel(true);
        }

        Integer tmpTaskId = mTaskId;
        // invalidate the current task as its finished. The dialog can be reused.
        mTaskId = null;
        mTask = null;

        // Tell the caller we're done. mTaskId will be null if there is no task.
        if (mTaskListener != null) {
            TaskListener<Progress, Result> listener = mTaskListener.get();
            if (listener != null) {
                listener.onTaskCancelled(tmpTaskId);
            } else {
                // keep this as a throw, as not having the listener here would be bug
                throw new RuntimeException("WeakReference to listener was dead");
            }
        } else {
            throw new IllegalStateException("no UserCancelledListener set.");
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

    /**
     * Set the progress max value.
     * <p>
     * Don't update the view here, as we could be in a WorkerThread.
     */
    @AnyThread
    public void setMax(final int max) {
        mMax = max;
        // trigger the next onTaskProgress to update the max value.
        mUpdateMax = true;
    }

    /**
     * Update the message and progress value.
     * <p>
     * Typically called from {@link AsyncTask} #onProgressUpdate
     *
     * @param absPosition absolute position, can be {@code null}
     * @param message     to display, can be either {@code String}, {@code null} or
     *                    {@code StringRes} or 0.
     *                    The null/0 gets morphed into an empty string.
     */
    @UiThread
    public void onProgress(@Nullable final Integer absPosition,
                           @Nullable final Object message) {
        synchronized (this) {
            setAbsPosition(absPosition);

            if (message instanceof String) {
                setMessage((String) message);
                return;

            } else if (message instanceof Integer) {
                @StringRes
                int resId = (int) message;
                // the id should never by 0, but future bugs and paranoia...
                if (resId != 0) {
                    setMessage(getResources().getString(resId));
                    return;
                }
            }

            setMessage("");
        }
    }

    /**
     * Update the message on the progress dialog.
     *
     * @param message to display.
     */
    @UiThread
    private void setMessage(@NonNull final String message) {
        if (mMessageView != null && !message.equals(mMessage)) {
            mMessage = message;
            mMessageView.setText(mMessage);
        } else {
            Logger.warnWithStackTrace(this, "mMessageView was NULL",
                                      "newMessage=" + message);
        }
    }

    private void setAbsPosition(@Nullable final Integer absPosition) {
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

    /**
     * ENHANCE: Work-around for bug in androidx library.
     * <p>
     * https://issuetracker.google.com/issues/36929400
     * <p>
     * Still not fixed in April 2019
     *
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }
}
