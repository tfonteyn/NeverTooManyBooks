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

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTocContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link TocEntry}.
 */
public class EditTocEntryDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditTocEntryDialogFrag";


    /** View Binding. */
    private DialogEditBookTocContentBinding vb;
    private EditTocEntryViewModel vm;

    /**
     * No-arg constructor for OS use.
     */
    public EditTocEntryDialogFragment() {
        super(R.layout.dialog_edit_book_toc, R.layout.dialog_edit_book_toc_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(EditTocEntryViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vb = DialogEditBookTocContentBinding.bind(view.findViewById(R.id.dialog_content));
        setTitle(vm.getBookTitle());

        //ENHANCE: should we provide a TocAdapter to aid manually adding TOC titles?
        // What about the publication year?
        vb.title.setText(vm.getCurrentEdit().getTitle());
        autoRemoveError(vb.title, vb.lblTitle);

        final PartialDate firstPublicationDate = vm.getCurrentEdit().getFirstPublicationDate();
        if (firstPublicationDate.isPresent()) {
            vb.firstPublication.setText(String.valueOf(firstPublicationDate.getYearValue()));
        }

        if (vm.isAnthology()) {
            //noinspection DataFlowIssue
            final ExtArrayAdapter<String> authorAdapter = new ExtArrayAdapter<>(
                    getContext(), R.layout.popup_dropdown_menu_item,
                    ExtArrayAdapter.FilterType.Diacritic,
                    ServiceLocator.getInstance().getAuthorDao()
                                  .getNames(DBKey.AUTHOR_FORMATTED));
            vb.author.setAdapter(authorAdapter);
            vb.author.setText(vm.getAuthorName());
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
        if (vm.getCurrentEdit().getTitle().isEmpty()) {
            vb.lblTitle.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        //noinspection DataFlowIssue
        if (!vm.isModified(getContext())) {
            return true;
        }

        vm.copyChanges();

        EditTocEntryLauncher.setResult(this, vm.getRequestKey(), vm.getTocEntry(),
                                       vm.getEditPosition());
        return true;
    }

    private void viewToModel() {
        final TocEntry currentEdit = vm.getCurrentEdit();
        //noinspection DataFlowIssue
        currentEdit.setTitle(vb.title.getText().toString().trim());
        //noinspection DataFlowIssue
        currentEdit.setFirstPublicationDate(new PartialDate(
                vb.firstPublication.getText().toString().trim()));

        if (vm.isAnthology()) {
            vm.setAuthorName(vb.author.getText().toString().trim());
        }
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
