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
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTocContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link TocEntry}.
 */
public class EditTocEntryDelegate
        implements FlexDialogDelegate<DialogEditBookTocContentBinding> {

    private final EditTocEntryViewModel vm;
    @NonNull
    private final DialogFragment owner;
    /** View Binding. */
    private DialogEditBookTocContentBinding vb;

    EditTocEntryDelegate(@NonNull final DialogFragment owner,
                         @NonNull final Bundle args) {
        this.owner = owner;
        vm = new ViewModelProvider(owner).get(EditTocEntryViewModel.class);
        //noinspection DataFlowIssue
        vm.init(owner.getContext(), args);
    }

    public void onViewCreated(@NonNull final DialogEditBookTocContentBinding vb) {
        this.vb = vb;
        final Context context = vb.getRoot().getContext();

        //ENHANCE: should we provide a TocAdapter to aid manually adding TOC titles?
        // What about the publication year?
        vb.title.setText(vm.getCurrentEdit().getTitle());
        autoRemoveError(vb.title, vb.lblTitle);

        final PartialDate firstPublicationDate = vm.getCurrentEdit().getFirstPublicationDate();
        if (firstPublicationDate.isPresent()) {
            vb.firstPublication.setText(String.valueOf(firstPublicationDate.getYearValue()));
        }

        if (vm.isAnthology()) {
            final ExtArrayAdapter<String> authorAdapter = new ExtArrayAdapter<>(
                    context, R.layout.popup_dropdown_menu_item,
                    ExtArrayAdapter.FilterType.Diacritic,
                    ServiceLocator.getInstance().getAuthorDao()
                                  .getNames(DBKey.AUTHOR_FORMATTED));
            vb.author.setAdapter(authorAdapter);
            vb.author.setText(vm.getCurrentAuthorName());
            vb.author.selectAll();
            vb.author.requestFocus();

            vb.lblAuthor.setVisibility(View.VISIBLE);
            vb.author.setVisibility(View.VISIBLE);

        } else {
            vb.title.requestFocus();

            vb.lblAuthor.setVisibility(View.GONE);
            vb.author.setVisibility(View.GONE);
        }
    }

    @Nullable
    public String getToolbarTitle() {
        return vm.getBookTitle();
    }

    @Override
    public boolean onToolbarMenuItemClick(@Nullable final MenuItem menuItem) {
        return false;
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

        if (vm.getCurrentEdit().getTitle().isEmpty()) {
            vb.lblTitle.setError(context.getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified(context)) {
            return true;
        }

        vm.copyChanges();

        EditTocEntryLauncher.setResult(owner, vm.getRequestKey(), vm.getTocEntry(),
                                       vm.getEditPosition());
        return true;
    }

    public void viewToModel() {
        final TocEntry currentEdit = vm.getCurrentEdit();
        //noinspection DataFlowIssue
        currentEdit.setTitle(vb.title.getText().toString().trim());
        //noinspection DataFlowIssue
        currentEdit.setFirstPublicationDate(new PartialDate(
                vb.firstPublication.getText().toString().trim()));

        if (vm.isAnthology()) {
            vm.setCurrentAuthorName(vb.author.getText().toString().trim());
        }
    }
}
