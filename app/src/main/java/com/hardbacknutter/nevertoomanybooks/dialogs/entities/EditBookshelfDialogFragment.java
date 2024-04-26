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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.ParcelableDialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Bookshelf}.
 * For now this class is not in fact called to create a new entry.
 * We do however keep the code flexible enough to allow it for future usage.
 * <ul>
 * <li>Direct/in-place editing.</li>
 * <li>Modifications ARE STORED in the database</li>
 * <li>Returns the modified item.</li>
 * </ul>
 *
 * @see EditAuthorDialogFragment
 * @see EditSeriesDialogFragment
 * @see EditPublisherDialogFragment
 * @see EditBookshelfDialogFragment
 */
public class EditBookshelfDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditBookshelfDialogFrag";

    /** View Binding. */
    private DialogEditBookshelfContentBinding vb;
    private EditBookshelfViewModel vm;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookshelfDialogFragment() {
        super(R.layout.dialog_edit_bookshelf, R.layout.dialog_edit_bookshelf_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(EditBookshelfViewModel.class);
        vm.init(requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditBookshelfContentBinding.bind(view.findViewById(R.id.dialog_content));

        vb.bookshelf.setText(vm.getCurrentEdit().getName());
        autoRemoveError(vb.bookshelf, vb.lblBookshelf);

        vb.bookshelf.requestFocus();
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

        if (vm.getCurrentEdit().getName().isEmpty()) {
            vb.lblBookshelf.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isChanged()) {
            return true;
        }

        final Context context = requireContext();

        try {
            final Optional<Bookshelf> existingEntity = vm.saveIfUnique(context);
            // IF the user meant to create a NEW shelf
            // REJECT an already existing Bookshelf with the same name.
            if (vm.getBookshelf().getId() == 0 && existingEntity.isPresent()) {
                vb.lblBookshelf.setError(getString(R.string.warning_x_already_exists,
                                                   getString(R.string.lbl_bookshelf)));
                return false;
            }

            if (existingEntity.isEmpty()) {
                sendResultBack(vm.getBookshelf());
                return true;
            }

            // There is one with the same name; ask whether to merge the 2
            StandardDialogs.askToMerge(context, R.string.confirm_merge_bookshelves,
                                       vm.getBookshelf().getLabel(context), () -> {
                        dismiss();
                        try {
                            vm.move(context, existingEntity.get());
                            // return the item which 'lost' it's books
                            sendResultBack(vm.getBookshelf());
                        } catch (@NonNull final DaoWriteException e) {
                            // log, but ignore - should never happen unless disk full
                            LoggerFactory.getLogger().e(TAG, e, vm.getBookshelf());
                        }
                    });
            return false;

        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, vm.getBookshelf());
            return false;
        }
    }

    private void viewToModel() {
        //noinspection DataFlowIssue
        vm.getCurrentEdit().setName(vb.bookshelf.getText().toString().trim());
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    private void sendResultBack(@NonNull final Bookshelf bookshelf) {
        ParcelableDialogLauncher.setEditInPlaceResult(this, vm.getRequestKey(), bookshelf);
    }
}
