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
package com.hardbacknutter.nevertoomanybooks;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.EnumSet;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogStylePickerContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.utils.WindowSizeClass;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RadioGroupRecyclerAdapter;

/**
 * TODO: unify with {@link StylePickerBottomSheet}.
 */
public class StylePickerDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "StylePickerDialogFrag";

    /** Adapter for the selection. */
    private RadioGroupRecyclerAdapter<Style> adapter;
    /** View Binding. */
    @SuppressWarnings("FieldCanBeLocal")
    private DialogStylePickerContentBinding vb;

    private StylePickerViewModel vm;

    /**
     * No-arg constructor for OS use.
     */
    public StylePickerDialogFragment() {
        super(R.layout.dialog_style_picker,
              R.layout.dialog_style_picker_content,
              // Fullscreen on Medium screens
              // for consistency with BookshelfFiltersDialogFragment
              EnumSet.of(WindowSizeClass.Medium),
              EnumSet.of(WindowSizeClass.Medium));
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(StylePickerViewModel.class);
        vm.init(requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogStylePickerContentBinding.bind(view.findViewById(R.id.dialog_content));

        //noinspection DataFlowIssue
        adapter = new RadioGroupRecyclerAdapter<>(getContext(),
                                                  vm.getStyles(),
                                                  position -> vm.getLabel(getContext(), position),
                                                  vm.getCurrentStyle(),
                                                  style -> vm.setCurrentStyle(style));
        vb.stylesList.setAdapter(adapter);

        adjustWindowSize(vb.stylesList, 0.33f);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public boolean onToolbarMenuItemClick(@Nullable final MenuItem menuItem) {
        if (menuItem == null) {
            return false;
        }

        final int menuItemId = menuItem.getItemId();
        if (menuItemId == R.id.MENU_EDIT) {
            onEditStyle();
            return true;

        } else if (menuItemId == R.id.MENU_STYLE_LIST_TOGGLE) {
            if (vm.flipShowAllStyles()) {
                menuItem.setTitle(R.string.action_less_ellipsis);
                menuItem.setIcon(R.drawable.ic_baseline_unfold_less_24);
            } else {
                menuItem.setTitle(R.string.action_more_ellipsis);
                menuItem.setIcon(R.drawable.ic_baseline_unfold_more_24);
            }

            adapter.notifyDataSetChanged();
            return true;
        }
        return false;
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {

        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_select || id == R.id.btn_positive) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        final Style selectedStyle = adapter.getSelection();
        if (selectedStyle == null) {
            // We should never get here.
            return false;
        }

        StylePickerLauncher.setResult(this, vm.getRequestKey(), selectedStyle);
        return true;
    }

    /**
     * Edit the selected style.
     */
    private void onEditStyle() {
        final Style selectedStyle = adapter.getSelection();
        if (selectedStyle == null) {
            // We should never get here.
            return;
        }
        dismiss();

        // use the activity so we get the results there.
        //noinspection DataFlowIssue
        ((BooksOnBookshelf) getActivity()).editStyle(selectedStyle);
    }
}
