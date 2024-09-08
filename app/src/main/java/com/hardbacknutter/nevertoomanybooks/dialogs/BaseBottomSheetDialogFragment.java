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

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;

public class BaseBottomSheetDialogFragment
        extends BottomSheetDialogFragment {

    /** Must be created/set in {@link #onCreate(Bundle)}. */
    protected FlexDialogDelegate delegate;

    @Override
    @NonNull
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        // Sanity check
        Objects.requireNonNull(delegate, "delegate not set");

        final View view = delegate.onCreateView(inflater, container);

        getLifecycle().addObserver(delegate);
        return view;
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
        //   Ensures that the first EditText field will be fully visible
        //   when the onscreen keyboard pops up.
        // SOFT_INPUT_ADJUST_RESIZE
        //   Ensures the BottomSheet sits above the keyboard.
        //noinspection DataFlowIssue
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);


        initDragHandle(view);
        initToolbar(view);
        initButtonBar(view);

        delegate.onViewCreated(DialogType.BottomSheet);
    }

    private void initDragHandle(@NonNull final View parent) {
        // Ensure the drag handle is visible.
        final View dragHandle = parent.findViewById(R.id.drag_handle);
        if (dragHandle != null) {
            dragHandle.setVisibility(View.VISIBLE);
        }
    }

    private void initToolbar(@NonNull final View parent) {
        // the dialog toolbar == bottom-sheet toolbar; can be null, that's ok
        delegate.setToolbar(parent.findViewById(R.id.dialog_toolbar));
    }

    private void initButtonBar(@NonNull final View parent) {
        // Hide the button bar at the bottom of the dialog
        final View buttonPanel = parent.findViewById(R.id.button_panel_layout);
        if (buttonPanel != null) {
            buttonPanel.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        delegate.onCancel(dialog);
        super.onCancel(dialog);
    }
}
