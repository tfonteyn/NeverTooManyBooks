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
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.settings.DialogMode;

public class Ext2EditTextPreferenceDialogFragment
        extends DialogFragment {

    private Ext2EditTextPreferenceDelegate delegate;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new Ext2EditTextPreferenceDelegate(this, requireArguments());
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(delegate.getDialogTitle())
                .setIcon(delegate.getDialogIcon())
                .setNegativeButton(delegate.getPositiveButtonText(),
                                   (d, which) -> dismiss())
                .setPositiveButton(delegate.getNegativeButtonText(),
                                   (d, which) -> delegate.saveValue());

        final View contentView = delegate.onCreateView(getLayoutInflater(), null);
        builder.setView(contentView);
        delegate.onViewCreated(DialogMode.Dialog);

        final Dialog dialog = builder.create();
        requestIME(dialog);
        return dialog;
    }

    private void requestIME(@NonNull final Dialog dialog) {
        // We want the input method to show, if possible, when dialog is displayed
        final Window window = dialog.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //noinspection DataFlowIssue
            window.getDecorView().getWindowInsetsController().show(WindowInsets.Type.ime());
        } else {
            delegate.requestIME();
        }
    }
}
