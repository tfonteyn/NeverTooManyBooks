/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.settings.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;

public class ExtEditTextPreferenceDialogFragment
        extends EditTextPreferenceDialogFragmentCompat {

    private static final int SHOW_REQUEST_TIMEOUT = 1000;
    private static final int SHOW_REQUEST_DELAY_MILLIS = 50;
    private static final int NOT_SCHEDULED = -1;

    @Nullable
    private EditText editText;
    private long showRequestTime = NOT_SCHEDULED;

    @SuppressWarnings("deprecation")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        final DialogPreference.TargetFragment fragment = Objects.requireNonNull(
                (DialogPreference.TargetFragment) getTargetFragment());
        final String key = Objects.requireNonNull(requireArguments().getString(ARG_KEY));
        final DialogPreference preference = Objects.requireNonNull(fragment.findPreference(key));

        // equivalent of: mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE;
        //noinspection DataFlowIssue
        onClick(null, DialogInterface.BUTTON_NEGATIVE);

        // use TextInputLayout/TextInputEditText
        preference.setDialogLayoutResource(R.layout.dialog_edit_simple_text);
        final View contentView = onCreateDialogView(requireContext());
        //noinspection DataFlowIssue
        editText = contentView.findViewById(android.R.id.edit);
        // Ensure the drag handle is hidden.
        contentView.findViewById(R.id.drag_handle).setVisibility(View.GONE);
        // Not using the dedicated title field
        contentView.findViewById(R.id.title).setVisibility(View.GONE);

        onBindDialogView(contentView);

        final Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(preference.getDialogTitle())
                .setIcon(preference.getDialogIcon())
                .setPositiveButton(preference.getPositiveButtonText(), this)
                .setNegativeButton(preference.getNegativeButtonText(), this)
                .setView(contentView)
                .create();

        //This will force the IME to show up immediately
        // instead of only when the user taps the field.
        requestInputMethod(dialog);
        return dialog;
    }

    /**
     * Sets the required flags on the dialog window to enable input method window to show up.
     * <p>
     * Note that starting from Android R, the new WindowInsets API supports showing soft-input
     * on-demand, so there is no longer a need to schedule showing soft-input when input connection
     * established by the focused editor.</p>
     */
    private void requestInputMethod(@NonNull final Dialog dialog) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //noinspection DataFlowIssue
            dialog.getWindow()
                  .getDecorView()
                  .getWindowInsetsController()
                  .show(WindowInsets.Type.ime());
        } else {
            // scheduleShowSoftInput(); maps to:
            setPendingShowSoftInputRequest(true);
            scheduleShowSoftInputInner();
        }
    }

    private void setPendingShowSoftInputRequest(final boolean pendingShowSoftInputRequest) {
        showRequestTime = pendingShowSoftInputRequest ? SystemClock.currentThreadTimeMillis() : -1;
    }

    private void scheduleShowSoftInputInner() {
        if (hasPendingShowSoftInputRequest()) {
            if (editText == null || !editText.isFocused()) {
                setPendingShowSoftInputRequest(false);
                return;
            }
            final InputMethodManager imm = (InputMethodManager)
                    editText.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            // Schedule showSoftInput once the input connection of the editor established.
            if (imm.showSoftInput(editText, 0)) {
                setPendingShowSoftInputRequest(false);
            } else {
                editText.removeCallbacks(mShowSoftInputRunnable);
                editText.postDelayed(mShowSoftInputRunnable, SHOW_REQUEST_DELAY_MILLIS);
            }
        }
    }

    private boolean hasPendingShowSoftInputRequest() {
        return (showRequestTime != -1 && ((showRequestTime + SHOW_REQUEST_TIMEOUT)
                                          > SystemClock.currentThreadTimeMillis()));
    }

    private final Runnable mShowSoftInputRunnable = this::scheduleShowSoftInputInner;
}
