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

package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditLoanContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.dialogs.ToolbarWithActionButtons;

/**
 * Dialog to create a new loan, edit an existing one or remove it (book is returned).
 * <p>
 * Note the special treatment of the Book's current/original loanee.
 * This is done to minimize trips to the database.
 */
class EditLenderDelegate
        implements FlexDialogDelegate<DialogEditLoanContentBinding> {

    private final EditLenderViewModel vm;
    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;

    /**
     * See <a href="https://developer.android.com/training/permissions/requesting">
     * developer.android.com</a>
     */
    private final ActivityResultLauncher<String> requestPermissionLauncher;
    private ExtArrayAdapter<String> adapter;
    private DialogEditLoanContentBinding vb;

    @SuppressLint("MissingPermission")
    EditLenderDelegate(@NonNull final DialogFragment owner,
                       @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        vm = new ViewModelProvider(owner).get(EditLenderViewModel.class);
        vm.init(args);

        requestPermissionLauncher = owner.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        addContacts();
                    }
                });
    }

    @Override
    public void onViewCreated(@NonNull final DialogEditLoanContentBinding vb) {
        this.vb = vb;
        final Context context = vb.getRoot().getContext();
        adapter = new ExtArrayAdapter<>(context, R.layout.popup_dropdown_menu_item,
                                        ExtArrayAdapter.FilterType.Diacritic,
                                        vm.getPeople());
        vb.lendTo.setAdapter(adapter);
        vb.lendTo.setText(vm.getCurrentEdit());

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            addContacts();
            // } else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            // FIXME: implement shouldShowRequestPermissionRationale
            //  but without using a dialog box inside a dialog box
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
        }

        vb.lendTo.requestFocus();
    }

    @RequiresPermission(Manifest.permission.READ_CONTACTS)
    private void addContacts() {
        adapter.clear();
        adapter.addAll(vm.getContacts(adapter.getContext()));
    }

    @Override
    public void initToolbarActionButtons(@NonNull final Toolbar dialogToolbar,
                                         final int menuResId,
                                         @NonNull final ToolbarWithActionButtons listener) {
        FlexDialogDelegate.super.initToolbarActionButtons(dialogToolbar, menuResId, listener);
        dialogToolbar.setSubtitle(vm.getBookTitle());
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

    @Override
    public void onPause() {
        viewToModel();
    }

    private void viewToModel() {
        vm.setCurrentEdit(vb.lendTo.getText().toString().trim());
    }

    private boolean saveChanges() {
        viewToModel();

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        if (vm.saveChanges()) {
            //noinspection DataFlowIssue
            EditLenderLauncher.setResult(owner, requestKey, vm.getBookId(), vm.getCurrentEdit());
            return true;
        }
        return false;
    }
}
