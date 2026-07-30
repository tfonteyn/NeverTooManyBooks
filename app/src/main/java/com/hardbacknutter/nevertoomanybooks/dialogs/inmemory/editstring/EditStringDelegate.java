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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.editstring;

import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditStringContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.widgets.endicon.ExtClearTextEndIconDelegate;

class EditStringDelegate
        implements FlexDialogDelegate {

    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;
    @NonNull
    private final String dialogTitle;
    @Nullable
    private final String dialogMessage;

    private final EditStringViewModel vm;
    private final int inputType;
    private DialogEditStringContentBinding vb;

    @Nullable
    private Toolbar toolbar;

    EditStringDelegate(@NonNull final DialogFragment owner,
                       @NonNull final EditStringInput args) {
        this.owner = owner;
        requestKey = args.getRequestKey();
        //noinspection DataFlowIssue
        dialogTitle = args.getDialogTitle(owner.getContext());
        dialogMessage = args.getDialogMessage();

        inputType = args.getInputType();

        vm = new ViewModelProvider(owner).get(EditStringViewModel.class);
        vm.init(args);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogEditStringContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    @NonNull
    public View onCreateFullscreen(@NonNull final LayoutInflater inflater,
                                   @Nullable final ViewGroup container) {
        final View view = inflater.inflate(R.layout.dialog_edit_string, container, false);
        vb = DialogEditStringContentBinding.bind(view.findViewById(R.id.dialog_content));
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

    @Override
    public void onViewCreated(@NonNull final DialogType dialogType) {
        if (toolbar != null) {
            if (dialogType == DialogType.BottomSheet) {
                toolbar.inflateMenu(R.menu.toolbar_action_save);
            }
            initToolbar(owner, dialogType, toolbar);
            toolbar.setTitle(dialogTitle);
        }

        bindMessageView(vb.message);
        bindEditText(vb.editString);
        ExtClearTextEndIconDelegate.attach(vb.lblEditString, null);
    }

    private void bindMessageView(@Nullable final TextView messageView) {
        if (messageView != null) {
            if (dialogMessage == null || dialogMessage.isEmpty()) {
                messageView.setVisibility(View.GONE);
            } else {
                messageView.setText(dialogMessage);
                messageView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void bindEditText(@NonNull final TextInputEditText editText) {
        editText.setInputType(inputType);
        editText.setText(vm.getCurrentValue());
        // Place cursor at the end
        //noinspection DataFlowIssue
        editText.setSelection(editText.getText().length());
        editText.requestFocus();
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

    @Override
    public void onPause(@NonNull final LifecycleOwner lifecycleOwner) {
        viewToModel();
    }

    private boolean saveChanges() {
        viewToModel();

        // Note we allow the empty string as a result!

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        EditStringLauncher.setResult(owner, requestKey,
                                     vm.getPreviousValue(),
                                     vm.getCurrentValue(),
                                     vm.getExtras());
        return true;
    }

    private void viewToModel() {
        final Editable text = vb.editString.getText();
        vm.setCurrentValue(text != null ? text.toString().strip() : "");
    }
}
