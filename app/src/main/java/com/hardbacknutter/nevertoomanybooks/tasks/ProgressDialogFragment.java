/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.tasks;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;

/**
 * Progress support for a {@link Canceller}. There can only be one Canceller at a time.
 * When the user cancels the dialog, we will cancel the Canceller.
 */
public class ProgressDialogFragment
        extends DialogFragment {

    public static final String TAG = "ProgressDialogFragment";

    private static final String BKEY_DIALOG_IS_INDETERMINATE = TAG + ":isIndeterminate";
    private static final String BKEY_MAX = TAG + ":max";
    private static final String BKEY_CURRENT_MESSAGE = TAG + ":message";
    private static final String BKEY_CURRENT_VALUE = TAG + ":current";

    private static final String BKEY_PREVENT_SLEEP = TAG + ":preventSleep";

    /** the current Canceller. */
    @Nullable
    private Canceller mCanceller;

    /** Type of ProgressBar. */
    private boolean mIsIndeterminate;
    /** Control FLAG_KEEP_SCREEN_ON. (e.g. during a backup etc...) */
    private boolean mPreventSleep;
    @Nullable
    private ProgressBar mProgressBar;
    @Nullable
    private TextView mMessageView;

    /** flag indicating the max value was updated. No need to add to onSaveInstanceState. */
    private boolean mUpdateMax;
    /** intermediate storage, as we'll only update the dialog from {@link #onProgress}. */
    private int mMax;
    /** intermediate storage, needed for onSaveInstanceState. */
    private int mCurrent;
    /** intermediate storage, needed for onSaveInstanceState. */
    @Nullable
    private String mMessage;

    /**
     * Constructor.
     *
     * @param titleId         resource id for the dialog title, can be 0 for no title.
     * @param isIndeterminate type of progress
     * @param preventSleep    whether to block the device from sleeping while the action is ongoing
     * @param maxValue        maximum value for progress if isIndeterminate==false
     *                        Pass in 0 to keep the max as set in the layout file.
     *
     * @return instance
     */
    @NonNull
    @UiThread
    public static ProgressDialogFragment newInstance(@StringRes final int titleId,
                                                     final boolean isIndeterminate,
                                                     final boolean preventSleep,
                                                     final int maxValue) {
        final ProgressDialogFragment frag = new ProgressDialogFragment();
        final Bundle args = new Bundle(4);
        args.putInt(StandardDialogs.BKEY_DIALOG_TITLE, titleId);
        args.putBoolean(BKEY_DIALOG_IS_INDETERMINATE, isIndeterminate);
        args.putInt(BKEY_MAX, maxValue);
        args.putBoolean(BKEY_PREVENT_SLEEP, preventSleep);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        mIsIndeterminate = args.getBoolean(BKEY_DIALOG_IS_INDETERMINATE, false);
        mPreventSleep = args.getBoolean(BKEY_PREVENT_SLEEP, false);
        args = savedInstanceState != null ? savedInstanceState : args;
        // initial/current message.
        mMessage = args.getString(BKEY_CURRENT_MESSAGE);
        mMax = args.getInt(BKEY_MAX);
        mCurrent = args.getInt(BKEY_CURRENT_VALUE);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final View root = getLayoutInflater().inflate(R.layout.dialog_task_progress, null);

        mMessageView = root.findViewById(R.id.progressMessage);
        mProgressBar = root.findViewById(R.id.progressBar);

        initProgressBar();

        if (mMessage != null) {
            //noinspection ConstantConditions
            mMessageView.setText(mMessage);
        }

        //noinspection ConstantConditions
        AlertDialog dialog = new MaterialAlertDialogBuilder(getContext())
                .setView(root)
                .create();
        // this is really needed, as it would be to easy to cancel without.
        // Cancel by 'back' press only.
        dialog.setCanceledOnTouchOutside(false);

        Bundle args = getArguments();
        if (args != null) {
            @StringRes
            int titleId = args.getInt(StandardDialogs.BKEY_DIALOG_TITLE,
                                      R.string.progress_msg_please_wait);
            if (titleId != 0) {
                dialog.setTitle(titleId);
            }
        }
        return dialog;
    }

    @UiThread
    private void initProgressBar() {
        // yes, check... we can get a new message calling here, before our dialog is even up.
        if (mProgressBar != null) {
            mProgressBar.setIndeterminate(mIsIndeterminate);
            // current and max values for a 'determinate' progress bar.
            if (!mIsIndeterminate) {
                mProgressBar.setProgress(mCurrent);
                if (mMax > 0) {
                    mProgressBar.setMax(mMax);
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mPreventSleep) {
            //noinspection ConstantConditions
            getDialog().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onStop() {
        if (mPreventSleep) {
            //noinspection ConstantConditions
            getDialog().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mProgressBar != null) {
            mCurrent = mProgressBar.getProgress();
        }
    }

    /**
     * Optionally link this object with a Canceller.
     *
     * @param canceller that will be cancelled when this dialog is cancelled (really?)
     */
    public void setCanceller(@Nullable final Canceller canceller) {
        mCanceller = canceller;
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        if (mCanceller != null) {
            mCanceller.cancel(false);
        }
    }

    @UiThread
    public void onProgress(@NonNull final TaskListener.ProgressMessage message) {
        @NonNull
        Boolean previousMode = mIsIndeterminate;
        // mode change requested ?
        if (!previousMode.equals(message.indeterminate)) {
            if (message.indeterminate == null) {
                // reset to the mode when we started.
                mIsIndeterminate = requireArguments()
                        .getBoolean(BKEY_DIALOG_IS_INDETERMINATE, false);
            } else {
                mIsIndeterminate = message.indeterminate;
            }
            initProgressBar();
        }

        // always set these, even when (currently) in indeterminate mode.
        setPosition(message.position);
        setMax(message.maxPosition);

        if (message.text != null) {
            setMessage(message.text);
        }

    }

    /**
     * Set the progress max value.
     * <p>
     * Don't update the view here, as we could be in a WorkerThread.
     * It will get updated at the next (@link #setPosition}
     *
     * @param max the new maximum position
     */
    @AnyThread
    private void setMax(final int max) {
        mMax = max;
        // trigger the next setPosition to update the max value.
        mUpdateMax = true;
    }

    /**
     * Update the absolute position of progress, and if needed the max position.
     *
     * @param position of progress
     */
    @UiThread
    private void setPosition(@Nullable final Integer position) {
        if (mProgressBar != null) {
            if (mUpdateMax && (mMax != mProgressBar.getMax())) {
                mProgressBar.setMax(mMax);
                mUpdateMax = false;
            }

            if (position != null && (position != mProgressBar.getProgress())) {
                mProgressBar.setProgress(position);
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

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BKEY_MAX, mMax);
        outState.putString(BKEY_CURRENT_MESSAGE, mMessage);
        outState.putInt(BKEY_CURRENT_VALUE, mCurrent);
    }
}
