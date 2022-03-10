/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.databinding.DialogProgressBinding;

public class ProgressDelegate {

    /** View Binding. */
    private final DialogProgressBinding mVb;
    /** Control FLAG_KEEP_SCREEN_ON. (e.g. during a backup etc...) */
    private boolean mPreventSleep;
    /** intermediate storage for the type of ProgressBar. */
    private boolean mOriginalIndeterminate;

    /**
     * Constructor.
     *
     * @param view the FrameLayout view of the Progress dialog
     */
    public ProgressDelegate(@NonNull final View view) {
        mVb = DialogProgressBinding.bind(view);
    }

    @NonNull
    public ProgressDelegate setTitle(@NonNull final CharSequence title) {
        mVb.progressTitle.setText(title);
        mVb.progressTitle.setVisibility(View.VISIBLE);
        return this;
    }

    @NonNull
    public ProgressDelegate setTitle(@StringRes final int title) {
        mVb.progressTitle.setText(title);
        mVb.progressTitle.setVisibility(View.VISIBLE);
        return this;
    }

    @NonNull
    public ProgressDelegate setPreventSleep(final boolean preventSleep) {
        mPreventSleep = preventSleep;
        return this;
    }

    @NonNull
    public ProgressDelegate setIndeterminate(final boolean indeterminate) {
        mOriginalIndeterminate = indeterminate;
        mVb.progressBar.setIndeterminate(indeterminate);
        return this;
    }

    //URGENT: when cancelling during connecting to a remote server (e.g. calibre) this does not work.
    // need to break the actual TerminatorConnection
    @NonNull
    public ProgressDelegate setOnCancelListener(@Nullable final View.OnClickListener listener) {
        mVb.btnCancel.setOnClickListener(listener);
        return this;
    }

    @NonNull
    public ProgressDelegate show(@NonNull final Supplier<Window> windowSupplier) {
        if (mVb.getRoot().getVisibility() == View.VISIBLE) {
            return this;
        }
        if (mPreventSleep) {
            windowSupplier.get().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        mVb.getRoot().setVisibility(View.VISIBLE);
        return this;
    }

    public void dismiss(@NonNull final Window window) {
        if (mPreventSleep) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        mVb.getRoot().setVisibility(View.GONE);
    }

    public void onProgress(@NonNull final TaskProgress message) {

        // mode change requested ?
        if (message.indeterminate == null) {
            // reset to the mode when we started.
            updateIndeterminate(mOriginalIndeterminate);

        } else if (mVb.progressBar.isIndeterminate() != message.indeterminate) {
            updateIndeterminate(message.indeterminate);
        }

        if (!mVb.progressBar.isIndeterminate()) {
            mVb.progressBar.setProgress(message.position);
            if (message.maxPosition > 0) {
                mVb.progressBar.setMax(message.maxPosition);
            }
        }

        // if we have no new text, we leave mVb.progressMessage text untouched.
        if (message.text != null
            && !((CharSequence) message.text).equals(mVb.progressMessage.getText())) {
            mVb.progressMessage.setText(message.text);
        }
    }

    private void updateIndeterminate(final boolean indeterminate) {
        // Flipping the new com.google.android.material.progressindicator.*
        // from determinate to indeterminate requires this step.
        // For simplicity, we do it the other way around as well.
        mVb.progressBar.hide();
        mVb.progressBar.setIndeterminate(indeterminate);
        mVb.progressBar.show();
    }
}
