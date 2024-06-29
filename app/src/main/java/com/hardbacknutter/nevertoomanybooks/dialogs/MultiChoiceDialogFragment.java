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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.databinding.DialogSelectMultipleBinding;

public class MultiChoiceDialogFragment
        extends DialogFragment {

    private MultiChoiceDelegate delegate;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new MultiChoiceDelegate(this, requireArguments());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        final DialogSelectMultipleBinding vb = DialogSelectMultipleBinding.inflate(
                getLayoutInflater(), null, false);
        // Ensure the drag handle is hidden.
        vb.dragHandle.setVisibility(View.GONE);
        // Ensure the unused title field is hidden
        vb.title.setVisibility(View.GONE);

        delegate.onViewCreated(vb);

        //noinspection DataFlowIssue
        return new MaterialAlertDialogBuilder(getContext())
                .setView(vb.getRoot())
                .setTitle(delegate.getDialogTitle())
                .setIcon(null)
                .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> delegate.saveChanges())
                .create();
    }
}
