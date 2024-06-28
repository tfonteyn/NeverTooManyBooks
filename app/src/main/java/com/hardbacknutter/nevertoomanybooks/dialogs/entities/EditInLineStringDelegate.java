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
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.dao.InlineStringDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookInlineStringContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;

/**
 * Dialog to edit an <strong>in-line {@code String} in the Books table</strong>
 * supported by an {@link InlineStringDao}.
 */
class EditInLineStringDelegate
        implements FlexDialogDelegate {

    @NonNull
    private final String toolbarTitle;
    @NonNull
    private final String label;
    @NonNull
    private final Supplier<InlineStringDao> daoSupplier;

    private final EditInLineStringViewModel vm;
    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;
    private DialogEditBookInlineStringContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    /**
     * Constructor.
     *
     * @param owner         hosting DialogFragment
     * @param args          {@link Fragment#requireArguments()}
     * @param dialogTitleId for the dialog (i.e. the toolbar)
     * @param labelResId    to use for the 'hint' of the input field
     * @param daoSupplier   the {@link InlineStringDao} supplier
     */
    EditInLineStringDelegate(@NonNull final DialogFragment owner,
                             @NonNull final Bundle args,
                             @StringRes final int dialogTitleId,
                             @StringRes final int labelResId,
                             @NonNull final Supplier<InlineStringDao> daoSupplier) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);

        vm = new ViewModelProvider(owner).get(EditInLineStringViewModel.class);
        vm.init(args);

        final Context context = owner.getContext();
        //noinspection DataFlowIssue
        this.toolbarTitle = context.getString(dialogTitleId);
        this.label = context.getString(labelResId);

        this.daoSupplier = daoSupplier;
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogEditBookInlineStringContentBinding.inflate(inflater, container, false);
        toolbar = vb.dialogToolbar;
        return vb.getRoot();
    }

    @Override
    public void onCreateView(@NonNull final View view) {
        vb = DialogEditBookInlineStringContentBinding.bind(view.findViewById(R.id.dialog_content));
        toolbar = vb.dialogToolbar;
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;

    }

    @Override
    public void onViewCreated() {
        if (toolbar != null) {
            initToolbar(toolbar);
        }

        final Context context = vb.getRoot().getContext();

        vb.lblEditString.setHint(label);
        vb.editString.setText(vm.getCurrentText());
        TilUtil.autoRemoveError(vb.editString, vb.lblEditString);

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
                ExtArrayAdapter.FilterType.Diacritic, getList(context));
        vb.editString.setAdapter(adapter);

        vb.editString.requestFocus();
    }

    @Override
    public void initToolbar(@NonNull final Toolbar toolbar) {
        FlexDialogDelegate.super.initToolbar(toolbar);
        toolbar.setTitle(toolbarTitle);
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

        if (vm.getCurrentText().isEmpty()) {
            vb.lblEditString.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        final String storedText = onSave(context, vm.getOriginalText(), vm.getCurrentText());
        EditInLineStringLauncher.setResult(owner, requestKey, vm.getOriginalText(), storedText);
        return true;
    }

    /**
     * Get the (optional) list of strings for the auto-complete.
     * <p>
     * Override if needed.
     *
     * @param context Current context
     *
     * @return list
     */
    @NonNull
    List<String> getList(@NonNull final Context context) {
        return daoSupplier.get().getList();
    }

    /**
     * Save the modifications to the database.
     * <p>
     * Override if needed.
     *
     * @param context      Current context
     * @param originalText the original text which was passed in to be edited
     * @param currentText  the modified text
     *
     * @return the text as <strong>stored</strong> which can be different from
     *         the modified text passed in.
     */
    @NonNull
    String onSave(@NonNull final Context context,
                  @NonNull final String originalText,
                  @NonNull final String currentText) {
        daoSupplier.get().rename(originalText, currentText);
        return currentText;
    }

    @Override
    public void onPause() {
        viewToModel();
    }

    private void viewToModel() {
        vm.setCurrentText(vb.editString.getText().toString().trim());
    }
}
