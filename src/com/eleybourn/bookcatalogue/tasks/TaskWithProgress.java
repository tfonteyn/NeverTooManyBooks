package com.eleybourn.bookcatalogue.tasks;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

/**
 * As Mr. Internet states in many places what you can't do... and gets it wrong, here is a copy
 * from the java docs of {@link AsyncTask}.
 *
 * <h2>Memory observability</h2>
 * <p>AsyncTask guarantees that all callback calls are synchronized in such a way that the
 * following operations are safe without explicit synchronizations.</p>
 * <ul>
 * <li>Set member fields in the constructor or {@link #onPreExecute}, and refer to them
 * in {@link #doInBackground}.
 * <li>Set member fields in {@link #doInBackground}, and refer to them in
 * {@link #onProgressUpdate} and {@link #onPostExecute}.
 * </ul>
 * <p>
 * So yes, you CAN use member variables.
 *
 * @param <Results> the type of the result objects for {@link #onPostExecute}.
 */
public abstract class TaskWithProgress<Results>
        extends AsyncTask<Void, Object, Results> {

    @NonNull
    protected final ProgressDialogFragment mFragment;
    /** Generic identifier. */
    private final int mTaskId;

    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can then check it.
     */
    protected Exception mException;

    /**
     * Constructor.
     *
     * @param taskId          a generic identifier
     * @param context         the calling fragment
     * @param titelId         titel resource for the progress
     * @param isIndeterminate flag for the progress
     */
    public TaskWithProgress(final int taskId,
                            @NonNull final FragmentActivity context,
                            final int titelId,
                            final boolean isIndeterminate) {
        mTaskId = taskId;
        mFragment = ProgressDialogFragment.newInstance(context, titelId, isIndeterminate);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    /**
     * @param values: [0] String message, [1] Integer position/delta
     */
    @Override
    protected void onProgressUpdate(final Object... values) {
        mFragment.onProgress((String) values[0], (Integer) values[1]);
    }

    /**
     * Note that the results parameter is not annotated null/non-null, as overrides need a choice.
     *
     * @param result of the task
     */
    @Override
    protected void onPostExecute(final Results result) {
        Activity activity = mFragment.requireActivity();
        if (activity instanceof OnTaskFinishedListener) {
            ((OnTaskFinishedListener) activity)
                    .onTaskFinished(mTaskId, mException != null, mFragment.isCancelled(),
                                    result);
        }
        mFragment.dismiss();
    }

    /**
     * Listener for OnTaskFinished messages from {@link TaskWithProgress#onPostExecute}.
     */
    public interface OnTaskFinishedListener {

        /**
         * @param failed    if the task completed but was a failure
         * @param cancelled if the task was cancelled before completion.
         * @param result    the return object from the {@link #doInBackground} call
         */
        void onTaskFinished(int taskId,
                            boolean failed,
                            boolean cancelled,
                            Object result);
    }

    /**
     * Progress support for {@link TaskWithProgress}.
     */
    public static class ProgressDialogFragment
            extends DialogFragment {

        private static final String BKEY_MESSAGE = "message";
        private static final String BKEY_DIALOG_IS_INDETERMINATE = "isIndeterminate";

        /** Flag indicating the progress dialog was cancelled. */
        private boolean mWasCancelled;
        private int mMax;
        private boolean mUpdateMax;

        /**
         * Constructor.
         */
        public ProgressDialogFragment() {
        }

        /**
         * Convenience routine to show a dialog fragment and start the task.
         *
         * @param context         FragmentActivity of caller
         * @param messageId       Message to display
         * @param isIndeterminate type of progress
         */
        @NonNull
        @UiThread
        public static ProgressDialogFragment newInstance(@NonNull final FragmentActivity context,
                                                         @StringRes final int messageId,
                                                         final boolean isIndeterminate) {
            ProgressDialogFragment frag = new ProgressDialogFragment();
            Bundle args = new Bundle();
            args.putInt(BKEY_MESSAGE, messageId);
            args.putBoolean(BKEY_DIALOG_IS_INDETERMINATE, isIndeterminate);
            frag.setArguments(args);
            frag.show(context.getSupportFragmentManager(), null);
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

            return progressDialog;
        }

        /**
         * The DialogFragment will call this if it was cancelled by the user.
         */
        @Override
        @CallSuper
        public void onCancel(@NonNull final DialogInterface dialog) {
            mWasCancelled = true;
        }

        /**
         * Our task will call this to see if the dialog was cancelled.
         */
        @AnyThread
        public boolean isCancelled() {
            return mWasCancelled;
        }

        /**
         * Direct update of message and progress value.
         */
        @UiThread
        public void onProgress(@Nullable final String message,
                               final int progress) {

            ProgressDialog progressDialog = (ProgressDialog) getDialog();
            if (progressDialog != null) {
                synchronized (this) {
                    if (mUpdateMax) {
                        progressDialog.setMax(mMax);
                        mUpdateMax = false;
                    }
                    if (message != null) {
                        progressDialog.setMessage(message);
                    }
                    progressDialog.setProgress(progress);
                }
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
                mUpdateMax = true;
            }
        }

        /**
         * Set the progress number format.
         */
        @UiThread
        public void setNumberFormat(@Nullable final String format) {
            ProgressDialog progressDialog = (ProgressDialog) getDialog();
            if (progressDialog != null) {
                progressDialog.setProgressNumberFormat(format);
            }
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
    }
}
