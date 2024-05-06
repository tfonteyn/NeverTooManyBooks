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

package com.hardbacknutter.nevertoomanybooks.widgets.popupmenu;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.databinding.PopupMenuBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;

public class ExtMenuBottomSheet
        extends BottomSheetDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "ExtMenuBottomSheet";

    private PopupMenuBinding vb;
    private List<ExtMenuItem> menuList;
    private String title;
    private String message;
    private String requestKey;
    private int menuOwner;
    private final MenuItemListAdapter.MenuCallback menuCallback =
            new MenuItemListAdapter.MenuCallback() {
                @Override
                public void onNewMenuTitle(@NonNull final CharSequence title) {
                    vb.title.setText(title);
                    vb.title.setVisibility(View.VISIBLE);
                }

                @Override
                public void onMenuItemClick(@IdRes final int menuItemId) {
                    ExtMenuBottomSheet.this.dismiss();
                    ExtMenuLauncher.setResult(ExtMenuBottomSheet.this, requestKey, menuOwner,
                                              menuItemId);
                }
            };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);

        title = args.getString(ExtMenuLauncher.BKEY_TITLE);
        message = args.getString(ExtMenuLauncher.BKEY_MESSAGE);
        menuList = args.getParcelableArrayList(ExtMenuLauncher.BKEY_MENU);
        menuOwner = args.getInt(ExtMenuLauncher.BKEY_MENU_OWNER);
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


        if (title != null) {
            vb.title.setText(title);
            vb.title.setVisibility(View.VISIBLE);
        }
        if (message != null) {
            vb.message.setText(message);
            vb.message.setVisibility(View.VISIBLE);
        }

        //noinspection DataFlowIssue
        final MenuItemListAdapter adapter = new MenuItemListAdapter(getContext(), menuCallback);
        adapter.setMenu(menuList);
        vb.itemList.setAdapter(adapter);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final Dialog d = super.onCreateDialog(savedInstanceState);
        // Paranoia...
        if (d instanceof BottomSheetDialog) {

            final BottomSheetBehavior<FrameLayout> behavior = ((BottomSheetDialog) d).getBehavior();
            // Needed for drag closing
            behavior.setSkipCollapsed(true);
            // Needed to open fully immediately.
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }

        return d;
    }
}
