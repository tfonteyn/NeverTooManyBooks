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
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogStylePickerContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.RadioGroupRecyclerAdapter;

class StylePickerDelegate
        implements FlexDialogDelegate<DialogStylePickerContentBinding> {

    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;

    private final StylePickerViewModel vm;

    /** Adapter for the selection. */
    private RadioGroupRecyclerAdapter<Style> adapter;


    StylePickerDelegate(@NonNull final DialogFragment owner,
                        @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        vm = new ViewModelProvider(owner).get(StylePickerViewModel.class);
        vm.init(args);
    }

    @Override
    public void onViewCreated(@NonNull final DialogStylePickerContentBinding vb) {
        final Context context = vb.getRoot().getContext();
        //noinspection DataFlowIssue
        adapter = new RadioGroupRecyclerAdapter<>(context,
                                                  vm.getStyles(),
                                                  position -> vm.getLabel(context, position),
                                                  vm.getCurrentStyle(),
                                                  vm::setCurrentStyle);
        vb.stylesList.setAdapter(adapter);
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
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
                    owner.dismiss();
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

        StylePickerLauncher.setResult(owner, requestKey, selectedStyle);
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
        owner.dismiss();

        // use the activity so we get the results there.
        //noinspection DataFlowIssue
        ((BooksOnBookshelf) owner.getActivity()).editStyle(selectedStyle);
    }
}
