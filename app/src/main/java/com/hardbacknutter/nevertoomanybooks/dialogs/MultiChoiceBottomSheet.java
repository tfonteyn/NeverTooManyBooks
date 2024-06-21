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
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import com.hardbacknutter.nevertoomanybooks.databinding.DialogChooseMultipleBinding;

/**
 * Note that {@link #onDismiss(DialogInterface)} will <strong>save</strong> the selection.
 */
public class MultiChoiceBottomSheet
        extends BottomSheetDialogFragment {

    private MultiChoiceDelegate delegate;
    private DialogChooseMultipleBinding vb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        delegate = new MultiChoiceDelegate(this, requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = DialogChooseMultipleBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Ensure the drag handle is visible.
        vb.dragHandle.setVisibility(View.VISIBLE);
        delegate.onViewCreated(vb);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final Dialog dialog = super.onCreateDialog(savedInstanceState);
        // Paranoia...
        if (dialog instanceof BottomSheetDialog) {
            // Due to multi-use of the layouts, we don't set these in xml:
            final BottomSheetBehavior<FrameLayout> behavior =
                    ((BottomSheetDialog) dialog).getBehavior();
            // Close fully when the user is dragging us down
            behavior.setSkipCollapsed(true);
            // Open fully when started.
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }

        return dialog;
    }

    @Override
    public void onDismiss(@NonNull final DialogInterface dialog) {
        delegate.saveChanges();
        super.onDismiss(dialog);
    }
}
