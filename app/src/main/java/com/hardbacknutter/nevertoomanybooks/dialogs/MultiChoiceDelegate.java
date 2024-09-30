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

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogSelectMultipleContentBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.ChecklistRecyclerAdapter;

/**
 * Replacement for an AlertDialog with checkbox setup.
 */
class MultiChoiceDelegate
        implements FlexDialogDelegate {

    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final MultiChoiceViewModel vm;

    @NonNull
    private final String requestKey;
    @NonNull
    private final String dialogTitle;
    @Nullable
    private final String dialogMessage;

    /** The list of items to display. */
    @NonNull
    private final List<Long> items;
    @NonNull
    private final List<String> itemLabels;

    private DialogSelectMultipleContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    MultiChoiceDelegate(@NonNull final DialogFragment owner,
                        @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        dialogTitle = args.getString(MultiChoiceLauncher.BKEY_DIALOG_TITLE,
                                     owner.getString(R.string.action_edit));
        dialogMessage = args.getString(MultiChoiceLauncher.BKEY_DIALOG_MESSAGE, null);

        items = Arrays.stream(Objects.requireNonNull(
                              args.getLongArray(MultiChoiceLauncher.BKEY_ITEMS),
                              MultiChoiceLauncher.BKEY_ITEMS))
                      .boxed().collect(Collectors.toList());
        itemLabels = Arrays.stream(Objects.requireNonNull(
                                   args.getStringArray(MultiChoiceLauncher.BKEY_ITEM_LABELS),
                                   MultiChoiceLauncher.BKEY_ITEM_LABELS))
                           .collect(Collectors.toList());

        vm = new ViewModelProvider(owner).get(MultiChoiceViewModel.class);
        vm.init(args);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogSelectMultipleContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_select_multiple, container, false);
        vb = DialogSelectMultipleContentBinding.bind(view.findViewById(R.id.dialog_content));
        return view;
    }

    @NonNull
    public Toolbar getToolbar() {
        return Objects.requireNonNull(toolbar, "No toolbar set");
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    public void onViewCreated(@NonNull final DialogType dialogType) {
        if (toolbar != null) {
            if (dialogType == DialogType.BottomSheet) {
                toolbar.inflateMenu(R.menu.toolbar_action_save);
            }
            initToolbar(owner, dialogType, toolbar);
            toolbar.setTitle(dialogTitle);
        }

        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            vb.message.setText(dialogMessage);
            vb.message.setVisibility(View.VISIBLE);
        } else {
            vb.message.setVisibility(View.GONE);
        }

        final Context context = vb.getRoot().getContext();
        final ChecklistRecyclerAdapter<Long> adapter = new ChecklistRecyclerAdapter<>(
                context, items, itemLabels::get, vm.getSelectedItems(),
                (id, checked) -> {
                    if (checked) {
                        vm.getSelectedItems().add(id);
                    } else {
                        vm.getSelectedItems().remove(id);
                    }
                });
        vb.itemList.setAdapter(adapter);
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {

        if (button != null) {
            final int id = button.getId();
            if (id == R.id.toolbar_btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        MultiChoiceLauncher.setResult(owner, requestKey,
                                      vm.getPreviousSelection(),
                                      vm.getSelectedItems(),
                                      vm.getExtras());
        return true;
    }
}
