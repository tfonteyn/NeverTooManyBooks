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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.multichoice;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogSelectMultipleContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
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

    /** The labels of the items to display. */
    @NonNull
    private final List<String> itemLabels;
    /** The ids of the items to display. */
    @NonNull
    private final List<Long> itemIds;

    private DialogSelectMultipleContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    private ChecklistRecyclerAdapter<Long> adapter;

    MultiChoiceDelegate(@NonNull final DialogFragment owner,
                        @NonNull final Bundle args) {
        this.owner = owner;

        final MultiChoiceInput input = MultiChoiceInput.fromBundle(args);

        requestKey = input.getRequestKey();
        //noinspection DataFlowIssue
        dialogTitle = input.getDialogTitle(owner.getContext());
        dialogMessage = input.getDialogMessage();

        itemIds = input.getIds();
        itemLabels = input.getLabels();

        vm = new ViewModelProvider(owner).get(MultiChoiceViewModel.class);
        vm.init(input);
    }

    @Nullable
    @Override
    public Boolean isForceFullscreen() {
        return false;
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
                toolbar.inflateMenu(R.menu.toolbar_action_clear_and_save);
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

        adapter = new ChecklistRecyclerAdapter<>(
                itemIds, itemLabels::get, vm.getCurrentSelection(),
                new ChecklistRecyclerAdapter.SelectionListener<>() {
                    @Override
                    public void onSelected(@NonNull final Long id,
                                           final boolean checked) {
                        vm.setSelection(id, checked);
                    }

                    @Override
                    public void setSelection(@NonNull final Set<Long> selection) {
                        vm.setSelection(selection);
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

            if (id == R.id.toolbar_btn_clear || id == R.id.btn_neutral) {
                // clear all. The adapter will inform any listeners.
                adapter.setSelection(Set.of());
                return true;

            } else if (id == R.id.toolbar_btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        // the model is already updated by the adapters selection listener.

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        MultiChoiceLauncher.setResult(owner, requestKey,
                                      vm.getPreviousSelection(),
                                      vm.getCurrentSelection(),
                                      vm.getExtras());
        return true;
    }
}
