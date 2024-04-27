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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditLoanContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;

/**
 * Dialog to create a new loan, edit an existing one or remove it (book is returned).
 * <p>
 * Note the special treatment of the Book's current/original loanee.
 * This is done to minimize trips to the database.
 */
public class EditLenderDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "LendBookDialogFrag";

    /** View Binding. */
    private DialogEditLoanContentBinding vb;

    private ExtArrayAdapter<String> adapter;
    private EditLenderViewModel vm;

    /**
     * See <a href="https://developer.android.com/training/permissions/requesting">
     * developer.android.com</a>
     */
    @SuppressLint("MissingPermission")
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(), isGranted -> {
                        if (isGranted) {
                            addContacts();
                        }
                    });

    /**
     * No-arg constructor for OS use.
     */
    public EditLenderDialogFragment() {
        super(R.layout.dialog_edit_loan, R.layout.dialog_edit_loan_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(EditLenderViewModel.class);
        vm.init(requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditLoanContentBinding.bind(view.findViewById(R.id.dialog_content));
        setSubtitle(vm.getBookTitle());

        //noinspection DataFlowIssue
        adapter = new ExtArrayAdapter<>(getContext(), R.layout.popup_dropdown_menu_item,
                                        ExtArrayAdapter.FilterType.Diacritic,
                                        vm.getPeople());
        vb.lendTo.setAdapter(adapter);
        vb.lendTo.setText(vm.getCurrentEdit());

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CONTACTS)
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
        //noinspection DataFlowIssue
        adapter.addAll(vm.getContacts(getContext()));
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

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        if (vm.saveChanges()) {
            //noinspection DataFlowIssue
            EditLenderLauncher.setResult(this, vm.getRequestKey(),
                                         vm.getBookId(), vm.getCurrentEdit());
            return true;
        }
        return false;
    }

    private void viewToModel() {
        vm.setCurrentEdit(vb.lendTo.getText().toString().trim());
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
