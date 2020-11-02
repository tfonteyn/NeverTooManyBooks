/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import android.os.Handler;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.databinding.DialogTaskProgressBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

/**
 * Progress support for a {@link Canceller} (usually, but not limited to, a Task).
 * There can only be one Canceller at a time.
 * When the user cancels the dialog, we will cancel the Canceller.
 */
public class ProgressDialogFragment
        extends DialogFragment {

    public static final String TAG = "ProgressDialogFragment";

    private static final String BKEY_PREVENT_SLEEP = TAG + ":preventSleep";

    private static final String BKEY_IS_INDETERMINATE = TAG + ":indeterminate";
    private static final String BKEY_MAX_POSITION = TAG + ":max";
    private static final String BKEY_TEXT = TAG + ":text";
    private static final String BKEY_CURRENT_POSITION = TAG + ":pos";

    /** Handles progress updates send before {@link #onCreateDialog} is called. */
    @SuppressWarnings("FieldNotUsedInToString")
    private final Handler mHandler = new Handler();
    /** the current Canceller (i.e. Task). */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private Canceller mCanceller;

    /** Control FLAG_KEEP_SCREEN_ON. (e.g. during a backup etc...) */
    private boolean mPreventSleep;

    /** intermediate storage for the type of ProgressBar. */
    private boolean mIsIndeterminate;
    /** intermediate storage. */
    private int mMaxPosition;
    /** intermediate storage. */
    private int mCurrentPosition;
    /** intermediate storage. */
    @Nullable
    private String mText;

    /** View Binding. */
    private DialogTaskProgressBinding mVb;

    /**
     * Constructor.
     *
     * @param title           (optional) title for the dialog title, {@code null} for no title.
     * @param isIndeterminate default type of progress
     * @param preventSleep    whether to block the device from sleeping while the action is ongoing
     *
     * @return instance
     */
    @NonNull
    @UiThread
    public static ProgressDialogFragment newInstance(@Nullable final String title,
                                                     final boolean isIndeterminate,
                                                     final boolean preventSleep) {
        final ProgressDialogFragment frag = new ProgressDialogFragment();
        final Bundle args = new Bundle(3);
        args.putString(StandardDialogs.BKEY_DIALOG_TITLE, title);
        args.putBoolean(BKEY_IS_INDETERMINATE, isIndeterminate);
        args.putBoolean(BKEY_PREVENT_SLEEP, preventSleep);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        mPreventSleep = args.getBoolean(BKEY_PREVENT_SLEEP, false);

        args = savedInstanceState != null ? savedInstanceState : args;
        // initial/current message.
        mIsIndeterminate = args.getBoolean(BKEY_IS_INDETERMINATE, false);
        mMaxPosition = args.getInt(BKEY_MAX_POSITION);
        mCurrentPosition = args.getInt(BKEY_CURRENT_POSITION);
        mText = args.getString(BKEY_TEXT);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        mVb = DialogTaskProgressBinding.inflate(getLayoutInflater());

        //noinspection ConstantConditions
        final AlertDialog dialog = new MaterialAlertDialogBuilder(getContext())
                .setView(mVb.getRoot())
                .create();
        // Cancel by tapping the 'back' key only.
        dialog.setCanceledOnTouchOutside(false);

        final Bundle args = getArguments();
        if (args != null) {
            final String title = args.getString(StandardDialogs.BKEY_DIALOG_TITLE);
            if (title != null) {
                dialog.setTitle(title);
            }
        }

        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();

        updateProgress(mText, mIsIndeterminate, mCurrentPosition, mMaxPosition);
    }

    @UiThread
    public void onProgress(@NonNull final ProgressMessage message) {

        @NonNull
        final Boolean previousMode = mIsIndeterminate;
        // mode change requested ?
        if (!previousMode.equals(message.indeterminate)) {
            if (message.indeterminate == null) {
                // reset to the mode when we started.
                mIsIndeterminate = requireArguments().getBoolean(BKEY_IS_INDETERMINATE, false);
            } else {
                mIsIndeterminate = message.indeterminate;
            }
        }

        mMaxPosition = message.maxPosition;
        mCurrentPosition = message.position;
        mText = message.text;

        tryUpdating(mText, mIsIndeterminate, mCurrentPosition, mMaxPosition);
    }

    @UiThread
    private void tryUpdating(@Nullable final CharSequence text,
                             final boolean isIndeterminate,
                             final int currentPosition,
                             final int maxPosition) {
        if (mVb == null) {
            mHandler.post(() -> {
                if (!isRemoving()) {
                    tryUpdating(text, isIndeterminate, currentPosition, maxPosition);
                }
            });
        } else {
            updateProgress(text, isIndeterminate, currentPosition, maxPosition);
        }
    }

    @UiThread
    private void updateProgress(@Nullable final CharSequence text,
                                final boolean isIndeterminate,
                                final int currentPosition,
                                final int maxPosition) {

        mVb.progressBar.setIndeterminate(isIndeterminate);
        mVb.progressBar.setProgress(currentPosition);
        if (!isIndeterminate && maxPosition > 0) {
            mVb.progressBar.setMax(maxPosition);
        }

        // if we have no new text, we leave mVb.progressMessage text untouched.
        if (text != null && !text.equals((mVb.progressMessage.getText()))) {
            mVb.progressMessage.setText(text);
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

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BKEY_IS_INDETERMINATE, mIsIndeterminate);
        outState.putInt(BKEY_MAX_POSITION, mMaxPosition);
        outState.putInt(BKEY_CURRENT_POSITION, mCurrentPosition);
        outState.putString(BKEY_TEXT, mText);
    }

    @Override
    @NonNull
    public String toString() {
        return "ProgressDialogFragment{"
               + "mPreventSleep=" + mPreventSleep
               + ", mIsIndeterminate=" + mIsIndeterminate
               + ", mMaxPosition=" + mMaxPosition
               + ", mCurrentPosition=" + mCurrentPosition
               + ", mText='" + mText + '\''
               + ", mVb=" + (mVb != null)
               + '}';
    }
}
