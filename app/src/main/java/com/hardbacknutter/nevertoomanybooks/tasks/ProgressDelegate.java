/*
 * @Copyright 2018-2023 HardBackNutter
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

import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogProgressBinding;

public class ProgressDelegate {

    /** View Binding. */
    @NonNull
    private final DialogProgressBinding vb;
    /** Control FLAG_KEEP_SCREEN_ON. (e.g. during a backup etc...) */
    private boolean preventSleep;
    /** intermediate storage for the type of ProgressBar. */
    private boolean originalIndeterminate;

    /**
     * Constructor.
     *
     * @param view the FrameLayout view of the Progress dialog
     */
    public ProgressDelegate(@NonNull final View view) {
        vb = DialogProgressBinding.bind(view);
    }

    /**
     * Set the dialog title.
     *
     * @param title to set
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ProgressDelegate setTitle(@NonNull final CharSequence title) {
        vb.progressTitle.setText(title);
        vb.progressTitle.setVisibility(View.VISIBLE);
        return this;
    }

    /**
     * Set the dialog title.
     *
     * @param title string resource to set
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ProgressDelegate setTitle(@StringRes final int title) {
        vb.progressTitle.setText(title);
        vb.progressTitle.setVisibility(View.VISIBLE);
        return this;
    }

    /**
     * Set an initial message.
     *
     * @param message string resource to set
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ProgressDelegate setMessage(@StringRes final int message) {
        vb.progressMessage.setText(message);
        vb.progressMessage.setVisibility(View.VISIBLE);
        return this;
    }

    /**
     * Suppress or allow the device from going into power-save mode.
     *
     * @param preventSleep {@code true} to keep the display on until finished.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ProgressDelegate setPreventSleep(final boolean preventSleep) {
        this.preventSleep = preventSleep;
        return this;
    }

    /**
     * Set the mode.
     *
     * @param indeterminate {@code true} for an indeterminate progress bar,
     *                      * or {@code false} for an exact length bar.
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ProgressDelegate setIndeterminate(final boolean indeterminate) {
        originalIndeterminate = indeterminate;
        vb.progressBar.setIndeterminate(indeterminate);
        return this;
    }

    /**
     * Install a callback listener for when the user cancels the dialog.
     *
     * @param listener to call hen cancelling
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ProgressDelegate setOnCancelListener(@Nullable final View.OnClickListener listener) {
        vb.btnCancel.setOnClickListener(listener);
        return this;
    }

    /**
     * Make the dialog visible.
     *
     * @param windowSupplier deferred supplier for the {@link Window} to update
     *
     * @return {@code this} (for chaining)
     */
    @NonNull
    public ProgressDelegate show(@NonNull final Supplier<Window> windowSupplier) {
        if (vb.getRoot().getVisibility() == View.VISIBLE) {
            return this;
        }
        if (preventSleep) {
            windowSupplier.get().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        vb.getRoot().setVisibility(View.VISIBLE);
        return this;
    }

    /**
     * Close the dialog.
     *
     * @param window to update
     */
    public void dismiss(@NonNull final Window window) {
        if (preventSleep) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        vb.getRoot().setVisibility(View.GONE);
    }

    /**
     * Update the message shown.
     *
     * @param message to show
     */
    public void onProgress(@NonNull final TaskProgress message) {

        // mode change requested ?
        if (message.indeterminate == null) {
            // reset to the mode when we started.
            updateIndeterminate(originalIndeterminate);

        } else if (vb.progressBar.isIndeterminate() != message.indeterminate) {
            updateIndeterminate(message.indeterminate);
        }

        if (!vb.progressBar.isIndeterminate()) {
            vb.progressBar.setProgress(message.position);
            if (message.maxPosition > 0) {
                vb.progressBar.setMax(message.maxPosition);
            }
        }

        // if we have no new text, we leave the progress message text untouched.
        if (message.text != null
            && !message.text.equals(vb.progressMessage.getText().toString())) {
            vb.progressMessage.setText(message.text);
        }
    }

    private void updateIndeterminate(final boolean indeterminate) {
        // Flipping the new com.google.android.material.progressindicator.*
        // from determinate to indeterminate requires this step.
        // For simplicity, we do it the other way around as well.
        vb.progressBar.hide();
        vb.progressBar.setIndeterminate(indeterminate);
        vb.progressBar.show();
    }
}
