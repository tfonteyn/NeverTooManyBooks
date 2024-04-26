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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookPublisherContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.ParcelableDialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditPublisherViewModel;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

/**
 * Add/Edit a single Publisher from the book's publisher list.
 * <p>
 * Can already exist (i.e. have an id) or can be a previously added/new one (id==0).
 * <p>
 * {@link EditAction#Add}:
 * <ul>
 * <li>used for list-dialogs needing to add a NEW item to the list</li>
 * <li>the item is NOT stored in the database</li>
 * <li>returns the new item</li>
 * </ul>
 * <p>
 * {@link EditAction#Edit}:
 * <ul>
 * <li>used for list-dialogs needing to EDIT an existing item in the list</li>
 * <li>the modifications are NOT stored in the database</li>
 * <li>returns the original untouched + a new copy with the modifications</li>
 * </ul>
 * Must be a public static class to be properly recreated from instance state.
 */
public class EditBookPublisherDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditPublisherForBookDlg";

    /** View model. Must be in the Activity scope. */
    private EditBookViewModel vm;
    /** Publisher View model. Fragment scope. */
    private EditPublisherViewModel publisherVm;
    /** View Binding. */
    private DialogEditBookPublisherContentBinding vb;

    /** Adding or Editing. */
    private EditAction action;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookPublisherDialogFragment() {
        super(R.layout.dialog_edit_book_publisher, R.layout.dialog_edit_book_publisher_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        action = Objects.requireNonNull(
                args.getParcelable(EditAction.BKEY), EditAction.BKEY);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(EditBookViewModel.class);
        publisherVm = new ViewModelProvider(this).get(EditPublisherViewModel.class);
        publisherVm.init(args);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditBookPublisherContentBinding.bind(view.findViewById(R.id.dialog_content));
        setSubtitle(vm.getBook().getTitle());

        //noinspection DataFlowIssue
        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAllPublisherNames());

        vb.publisherName.setText(publisherVm.getCurrentEdit().getName());
        vb.publisherName.setAdapter(nameAdapter);
        autoRemoveError(vb.publisherName, vb.lblPublisherName);

        vb.publisherName.requestFocus();
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();

        final Publisher currentEdit = publisherVm.getCurrentEdit();
        // basic check only, we're doing more extensive checks later on.
        if (currentEdit.getName().isEmpty()) {
            vb.lblPublisherName.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        ParcelableDialogLauncher.setResult(this, publisherVm.getRequestKey(), action,
                                           publisherVm.getPublisher(), currentEdit);
        return true;
    }

    private void viewToModel() {
        publisherVm.getCurrentEdit().setName(vb.publisherName.getText().toString().trim());
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
