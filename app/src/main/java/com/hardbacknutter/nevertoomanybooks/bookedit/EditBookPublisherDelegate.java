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

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookPublisherContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
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
 */
public class EditBookPublisherDelegate
        implements FlexDialogDelegate<DialogEditBookPublisherContentBinding> {

    @NonNull
    private final DialogFragment owner;

    /** View model. Must be in the Activity scope. */
    private final EditBookViewModel vm;
    /** Publisher View model. Fragment scope. */
    private final EditPublisherViewModel publisherVm;
    /** Adding or Editing. */
    private final EditAction action;
    /** View Binding. */
    private DialogEditBookPublisherContentBinding vb;

    EditBookPublisherDelegate(@NonNull final DialogFragment owner,
                              @NonNull final Bundle args) {
        this.owner = owner;
        action = Objects.requireNonNull(args.getParcelable(EditAction.BKEY), EditAction.BKEY);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(owner.getActivity()).get(EditBookViewModel.class);
        publisherVm = new ViewModelProvider(owner).get(EditPublisherViewModel.class);
        publisherVm.init(args);
    }

    @Nullable
    public String getToolbarSubtitle() {
        return vm.getBook().getTitle();
    }

    public void onViewCreated(@NonNull final DialogEditBookPublisherContentBinding vb) {
        this.vb = vb;

        final Context context = vb.getRoot().getContext();

        final ExtArrayAdapter<String> nameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAllPublisherNames());

        vb.publisherName.setText(publisherVm.getCurrentEdit().getName());
        vb.publisherName.setAdapter(nameAdapter);
        autoRemoveError(vb.publisherName, vb.lblPublisherName);

        vb.publisherName.requestFocus();
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
    }

    @Override
    public boolean onToolbarMenuItemClick(@Nullable final MenuItem menuItem) {
        return false;
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();

        final Context context = vb.getRoot().getContext();

        final Publisher currentEdit = publisherVm.getCurrentEdit();
        // basic check only, we're doing more extensive checks later on.
        if (currentEdit.getName().isEmpty()) {
            vb.lblPublisherName.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        EditParcelableLauncher.setResult(owner, publisherVm.getRequestKey(), action,
                                         publisherVm.getPublisher(), currentEdit);
        return true;
    }

    @Override
    public void onPause() {
        viewToModel();
    }

    private void viewToModel() {
        publisherVm.getCurrentEdit().setName(vb.publisherName.getText().toString().trim());
    }

}
