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

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.dao.InlineStringDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditStringContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialog;

/**
 * Dialog to edit an <strong>in-line in Books table</strong> Color.
 */
public class EditStringDelegate
        implements FlexDialog {

    @NonNull
    private final String toolbarTitle;
    @NonNull
    private final String label;
    @NonNull
    private final Supplier<InlineStringDao> daoSupplier;

    private final EditStringViewModel vm;
    @NonNull
    private final DialogFragment owner;
    /** View Binding. */
    protected DialogEditStringContentBinding vb;

    /**
     * Constructor.
     *
     * @param owner         hosting DialogFragment
     * @param dialogTitleId for the dialog (i.e. the toolbar)
     * @param labelResId    to use for the 'hint' of the input field
     * @param args          {@link Fragment#requireArguments()}
     */
    EditStringDelegate(@NonNull final DialogFragment owner,
                       @StringRes final int dialogTitleId,
                       @StringRes final int labelResId,
                       @NonNull final Supplier<InlineStringDao> daoSupplier,
                       @NonNull final Bundle args) {
        this.owner = owner;
        this.daoSupplier = daoSupplier;
        final Context context = owner.getContext();
        //noinspection DataFlowIssue
        this.toolbarTitle = context.getString(dialogTitleId);
        this.label = context.getString(labelResId);

        vm = new ViewModelProvider(owner).get(EditStringViewModel.class);
        vm.init(args);
    }

    void onViewCreated(@NonNull final DialogEditStringContentBinding vb) {
        this.vb = vb;
        final Context context = vb.getRoot().getContext();

        vb.lblEditString.setHint(label);
        vb.editString.setText(vm.getCurrentText());
        autoRemoveError(vb.editString, vb.lblEditString);

        // soft-keyboards 'done' button act as a shortcut to confirming/saving the changes
        vb.editString.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (saveChanges()) {
                    owner.dismiss();
                }
                return true;
            }
            return false;
        });

        final ExtArrayAdapter<String> adapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, getList());
        vb.editString.setAdapter(adapter);

        vb.editString.requestFocus();
    }

    @Nullable
    String getToolbarTitle() {
        return toolbarTitle;
    }

    @Override
    public void onToolbarNavigationClick(@NonNull final View v) {
        owner.dismiss();
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

        if (vm.getCurrentText().isEmpty()) {
            vb.lblEditString.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        final String storedText = onSave(vm.getOriginalText(), vm.getCurrentText());
        EditStringLauncher.setResult(owner, vm.getRequestKey(), vm.getOriginalText(), storedText);
        return true;
    }

    /**
     * Get the (optional) list of strings for the auto-complete.
     * <p>
     * Override if needed.
     *
     * @return list
     */
    @NonNull
    protected List<String> getList() {
        return daoSupplier.get().getList();
    }

    /**
     * Save the modifications to the database.
     * <p>
     * Override if needed.
     *
     * @param originalText the original text which was passed in to be edited
     * @param currentText  the modified text
     *
     * @return the text as <strong>stored</strong> which can be different from
     *         the modified text passed in.
     */
    @NonNull
    protected String onSave(@NonNull final String originalText,
                            @NonNull final String currentText) {
        daoSupplier.get().rename(originalText, currentText);
        return currentText;
    }

    void viewToModel() {
        vm.setCurrentText(vb.editString.getText().toString().trim());
    }
}
