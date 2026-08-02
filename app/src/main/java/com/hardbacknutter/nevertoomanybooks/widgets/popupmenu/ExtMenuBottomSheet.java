/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.widgets.popupmenu;

import android.app.Dialog;
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

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.databinding.PopupMenuBinding;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

public class ExtMenuBottomSheet
        extends BottomSheetDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "ExtMenuBottomSheet";

    private PopupMenuBinding vb;
    private List<ExtMenuItem> menuList;
    @Nullable
    private CharSequence title;
    @Nullable
    private CharSequence message;
    private String requestKey;
    private int menuOwner;
    private final MenuItemListAdapter.MenuCallback menuCallback =
            new MenuItemListAdapter.MenuCallback() {
                @Override
                public boolean onSubMenuClick(@NonNull final View subMenuView,
                                              @NonNull final ExtMenuItem subMenuItem) {
                    vb.title.setText(subMenuItem.getTitle());
                    vb.title.setVisibility(View.VISIBLE);
                    return true;
                }

                @Override
                public void onMenuItemClick(@NonNull final ExtMenuItem menuItem) {
                    ExtMenuBottomSheet.this.dismiss();

                    new ExtMenuLauncher.Output(menuOwner, menuItem.getItemId())
                            .send(ExtMenuBottomSheet.this, requestKey);
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ExtMenuInput args = ExtMenuInput.fromBundle(requireArguments());
        requestKey = args.getRequestKey();

        title = args.getMenuTitle();
        message = args.getMessage();
        menuList = args.getItems();
        menuOwner = args.getMenuOwner();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = PopupMenuBinding.inflate(inflater, container, false);
        // Ensure the drag handle is visible.
        vb.dragHandle.setVisibility(View.VISIBLE);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        new InsetsListenerBuilder(view)
                .ime()
                .apply();

        if (title != null) {
            vb.title.setText(title);
            vb.title.setVisibility(View.VISIBLE);
        }
        if (message != null) {
            vb.message.setText(message);
            vb.message.setVisibility(View.VISIBLE);
        }

        final MenuItemListAdapter adapter = new MenuItemListAdapter(menuCallback);
        adapter.setMenu(menuList);
        vb.itemList.setAdapter(adapter);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final Dialog dialog = super.onCreateDialog(savedInstanceState);
        // Paranoia...
        if (dialog instanceof BottomSheetDialog) {
            // Due to multi-use of the layouts, we don't set these in XML:
            final BottomSheetBehavior<FrameLayout> behavior =
                    ((BottomSheetDialog) dialog).getBehavior();
            // Close fully when the user is dragging us down
            behavior.setSkipCollapsed(true);
            // Open fully when started.
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }

        return dialog;
    }
}
