/*
 * @Copyright 2018-2025 HardBackNutter
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
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditStringContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;

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
                       @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        dialogTitle = args.getString(EditStringLauncher.BKEY_DIALOG_TITLE,
                                     owner.getString(R.string.action_edit));
        dialogMessage = args.getString(EditStringLauncher.BKEY_DIALOG_MESSAGE, null);

        inputType = args.getInt(EditStringLauncher.BKEY_INPUT_TYPE,
                                InputType.TYPE_CLASS_TEXT);

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

        if (dialogMessage != null && !dialogMessage.isEmpty()) {
            vb.message.setText(dialogMessage);
            vb.message.setVisibility(View.VISIBLE);
        } else {
            vb.message.setVisibility(View.GONE);
        }

        vb.editString.setInputType(inputType);
        vb.editString.setText(vm.getCurrentValue());
        vb.editString.requestFocus();
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
        vm.setCurrentValue(text != null ? text.toString().trim() : "");
    }
}
