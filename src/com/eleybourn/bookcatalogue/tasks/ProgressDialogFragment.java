package com.eleybourn.bookcatalogue.tasks;

import android.app.Dialog;
import android.content.DialogInterface;
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
 * Progress support for an {@link TaskBase}. There can only be ONE task at a time.
 */
public class ProgressDialogFragment
        extends DialogFragment {

    /**
     * Fragment manager tag used if no custom tag is needed; e.g. when this dialog is shared
     * among multiple tasks.
     */
    public static final String TAG = "ProgressDialogFragment";

    private static final String BKEY_DIALOG_IS_INDETERMINATE = TAG + ":isIndeterminate";
    private static final String BKEY_MAX = TAG + ":max";
    private static final String BKEY_CURRENT_MESSAGE = TAG + ":message";
    private static final String BKEY_CURRENT_VALUE = TAG + ":current";

    /** the current task. */
    @Nullable
    private TaskBase mTask;

    /** Type of ProgressBar. */
    private boolean mIsIndeterminate;
    @Nullable
    private ProgressBar mProgressBar;
    @Nullable
    private TextView mMessageView;

    /** flag indicating the max value was updated. No need to add to onSaveInstanceState. */
    private boolean mUpdateMax;
    /** intermediate storage, as we'll only update this when progress is updated. */
    private int mMax;
    /** intermediate storage, needed for onSaveInstanceState. */
    private int mCurrent;
    /** intermediate storage, needed for onSaveInstanceState. */
    @Nullable
    private String mMessage;

    /**
     * Constructor.
     *
     * @param titelId         Titel for the dialog, can be 0 for no title.
     * @param isIndeterminate type of progress
     * @param maxValue        maximum value for progress if isIndeterminate==false
     *                        Pass in 0 to keep the max as set in the layout file.
     *
     * @return the fragment.
     */
    @NonNull
    @UiThread
    public static ProgressDialogFragment newInstance(@StringRes final int titelId,
                                                     final boolean isIndeterminate,
                                                     final int maxValue) {
        ProgressDialogFragment frag = new ProgressDialogFragment();
        Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, titelId);
        args.putBoolean(BKEY_DIALOG_IS_INDETERMINATE, isIndeterminate);
        args.putInt(BKEY_MAX, maxValue);
        frag.setArguments(args);
        return frag;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        mIsIndeterminate = args.getBoolean(BKEY_DIALOG_IS_INDETERMINATE);

        args = savedInstanceState == null ? args : savedInstanceState;
        // initial/current message.
        mMessage = args.getString(BKEY_CURRENT_MESSAGE);
        mMax = args.getInt(BKEY_MAX);
        mCurrent = args.getInt(BKEY_CURRENT_VALUE);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_task_progress, null);

        mMessageView = root.findViewById(R.id.progressMessage);
        mProgressBar = root.findViewById(R.id.progressBar);
        mProgressBar.setIndeterminate(mIsIndeterminate);

        // current and max values for a 'determinate' progress bar.
        if (!mIsIndeterminate) {
            mProgressBar.setProgress(mCurrent);
            if (mMax > 0) {
                mProgressBar.setMax(mMax);
            }
        }

        if (mMessage != null) {
            //noinspection ConstantConditions
            mMessageView.setText(mMessage);
        }

        @SuppressWarnings("ConstantConditions")
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(root)
                .create();
        // this is really needed, as it would be to easy to cancel without.
        // Cancel by 'back' press only.
        dialog.setCanceledOnTouchOutside(false);

        //noinspection ConstantConditions
        @StringRes
        int titleId = getArguments().getInt(UniqueId.BKEY_DIALOG_TITLE,
                                            R.string.progress_msg_please_wait);
        if (titleId != 0) {
            dialog.setTitle(titleId);
        }

        return dialog;
    }

    /**
     * Can optionally (but usually is) be linked with a task.
     * This class needs to be able to send a 'cancel' to the task when our dialog is cancelled.
     *
     * @param task that will use us for progress updates.
     */
    public void setTask(@Nullable final TaskBase task) {
        // cancel any previous task.
        if (mTask != null) {
            mTask.cancel(true);
        }
        mTask = task;
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        if (mTask != null) {
            mTask.cancel(true);
            // invalidate the current task as its finished. The dialog can be reused.
            mTask = null;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
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
     * It will get updated at the next (@link #setAbsPosition}
     *
     * @param max the new maximum position
     */
    @AnyThread
    private void setMax(final int max) {
        mMax = max;
        // trigger the next setAbsPosition to update the max value.
        mUpdateMax = true;
    }

    /**
     * Update the absolute position of progress, and if needed the max position.
     *
     * @param absPosition of progress
     */
    @UiThread
    private void setAbsPosition(@Nullable final Integer absPosition) {
        if (mProgressBar != null) {
            if (mUpdateMax && (mMax != mProgressBar.getMax())) {
                mProgressBar.setMax(mMax);
                mUpdateMax = false;
            }

            if (absPosition != null && (absPosition != mProgressBar.getProgress())) {
                mProgressBar.setProgress(absPosition);
            }
        }
    }

    /**
     * Update the message on the progress dialog.
     *
     * @param message to display.
     */
    @UiThread
    private void setMessage(@NonNull final String message) {
        // tests have shown the dialog can already be closed before the last (couple of) messages
        // arrive here. We don't care that much, so ignore if the view is null.
        if (mMessageView != null && !message.equals(mMessage)) {
            mMessage = message;
            mMessageView.setText(mMessage);
        }
    }

    @UiThread
    public void onProgress(@NonNull final TaskListener.TaskProgressMessage message) {
        if (message.maxPosition != null) {
            setMax(message.maxPosition);
        }
        if (message.absPosition != null) {
            setAbsPosition(message.absPosition);
        }
        if (message.values != null && message.values.length > 0) {
            Object obj = message.values[0];
            synchronized (this) {
                if (obj instanceof String) {
                    setMessage((String) obj);

                } else if (obj instanceof Integer) {
                    @StringRes
                    int resId = (int) obj;
                    // the id should never by 0, but future bugs and paranoia...
                    if (resId != 0) {
                        setMessage(getResources().getString(resId));
                    }
                } else {
                    setMessage("");
                }
            }

        }
    }

    /**
     * Work-around for bug in androidx library.
     * Currently (2019-08-01) no longer needed as we're no longer retaining this fragment.
     * Leaving as a reminder for now.
     * <p>
     * https://issuetracker.google.com/issues/36929400
     * <p>
     * Still not fixed in July 2019
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
