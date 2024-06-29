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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class Ext2ListPreferenceDialogFragment
        extends DialogFragment {

    private Ext2ListPreferenceDelegate delegate;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new Ext2ListPreferenceDelegate(this, requireArguments());
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        return new MaterialAlertDialogBuilder(requireContext())
                .setIcon(delegate.getDialogIcon())
                .setTitle(delegate.getDialogTitle())
                .setMessage(delegate.getDialogMessage())
                .setNegativeButton(delegate.getNegativeButtonText(), (d, which) -> dismiss())
                // There is no 'positive' button.
                // Selecting a line will set the value and dismiss the dialog
                .setSingleChoiceItems(delegate.getEntries(),
                                      delegate.getInitialSelectedIndex(),
                                      (d, which) -> delegate.saveValue(which))
                .create();
    }
}
