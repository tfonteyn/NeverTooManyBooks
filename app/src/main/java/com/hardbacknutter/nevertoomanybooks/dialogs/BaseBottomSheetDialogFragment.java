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

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import com.hardbacknutter.nevertoomanybooks.R;

public class BaseBottomSheetDialogFragment
        extends BottomSheetDialogFragment {

    /** Must be created/set in {@link #onCreate(Bundle)}. */
    protected FlexDialogDelegate delegate;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return delegate.onCreateView(inflater, container);
    }

    @CallSuper
    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final BottomSheetDialog dialog = (BottomSheetDialog) requireDialog();

        // Due to multi-use of the layouts, we don't set these in xml:
        final BottomSheetBehavior<FrameLayout> behavior = dialog.getBehavior();
        // Close fully when the user is dragging us down
        behavior.setSkipCollapsed(true);
        // Open fully when started.
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        // SOFT_INPUT_STATE_ALWAYS_VISIBLE
        // This ensures that the first EditText field will be fully visible
        // when the onscreen keyboard pops up.
        // SOFT_INPUT_ADJUST_RESIZE
        // Make sure the BottomSheet sits above the keyboard
        //noinspection DataFlowIssue
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);


        // Ensure the drag handle is visible.
        final View dragHandle = view.findViewById(R.id.drag_handle);
        if (dragHandle != null) {
            dragHandle.setVisibility(View.VISIBLE);
        }
        // Hide the button bar at the bottom of the dialog
        final View buttonPanel = view.findViewById(R.id.button_panel_layout);
        if (buttonPanel != null) {
            buttonPanel.setVisibility(View.GONE);
        }

        // the dialog toolbar == bottom-sheet toolbar; can be null, that's ok
        delegate.setToolbar(view.findViewById(R.id.dialog_toolbar));
        delegate.onViewCreated();
    }

    @Override
    public void onStart() {
        super.onStart();
        delegate.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        delegate.onResume();
    }

    @Override
    public void onPause() {
        delegate.onPause();
        super.onPause();
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        delegate.onCancel(dialog);
        super.onCancel(dialog);
    }
}
