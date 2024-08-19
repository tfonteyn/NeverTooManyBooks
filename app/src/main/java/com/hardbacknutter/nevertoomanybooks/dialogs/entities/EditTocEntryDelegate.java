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
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTocContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogType;
import com.hardbacknutter.nevertoomanybooks.dialogs.FlexDialogDelegate;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.widgets.TilUtil;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link TocEntry}.
 */
class EditTocEntryDelegate
        implements FlexDialogDelegate {

    private final EditTocEntryViewModel vm;
    @NonNull
    private final DialogFragment owner;
    @NonNull
    private final String requestKey;
    /** View Binding. */
    private DialogEditBookTocContentBinding vb;
    @Nullable
    private Toolbar toolbar;

    EditTocEntryDelegate(@NonNull final DialogFragment owner,
                         @NonNull final Bundle args) {
        this.owner = owner;
        requestKey = Objects.requireNonNull(args.getString(DialogLauncher.BKEY_REQUEST_KEY),
                                            DialogLauncher.BKEY_REQUEST_KEY);
        vm = new ViewModelProvider(owner).get(EditTocEntryViewModel.class);
        //noinspection DataFlowIssue
        vm.init(owner.getContext(), args);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container) {
        vb = DialogEditBookTocContentBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onCreateView(@NonNull final View view) {
        vb = DialogEditBookTocContentBinding.bind(view.findViewById(R.id.dialog_content));
    }

    @Override
    public void setToolbar(@Nullable final Toolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override
    public void onViewCreated(@NonNull final DialogType dialogType) {
        if (toolbar != null) {
            initToolbar(owner, dialogType, toolbar);
        }

        final Context context = vb.getRoot().getContext();

        final TocEntry currentEdit = vm.getCurrentEdit();

        //ENHANCE: should we provide a AuthorWorksAdapter to aid manually adding TOC titles?
        // What about the publication year?
        vb.title.setText(currentEdit.getTitle());
        TilUtil.autoRemoveError(vb.title, vb.lblTitle);

        final PartialDate firstPublicationDate = currentEdit.getFirstPublicationDate();
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

    @Override
    public void initToolbar(@NonNull final DialogFragment owner,
                            @NonNull final DialogType dialogType,
                            @NonNull final Toolbar toolbar) {
        FlexDialogDelegate.super.initToolbar(owner, dialogType, toolbar);
        final String toolbarTitle = vm.getBookTitle();
        if (toolbarTitle != null) {
            toolbar.setTitle(toolbarTitle);
        }
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

        EditTocEntryLauncher.setResult(owner, requestKey, vm.getTocEntry(), vm.getEditPosition());
        return true;
    }

    @Override
    public void onPause(@NonNull final LifecycleOwner lifecycleOwner) {
        viewToModel();
    }

    private void viewToModel() {
        //noinspection DataFlowIssue
        vm.setTitle(vb.title.getText().toString().trim());
        //noinspection DataFlowIssue
        vm.setFirstPublicationDate(vb.firstPublication.getText().toString().trim());

        if (vm.isAnthology()) {
            vm.setCurrentAuthorName(vb.author.getText().toString().trim());
        }
    }
}
