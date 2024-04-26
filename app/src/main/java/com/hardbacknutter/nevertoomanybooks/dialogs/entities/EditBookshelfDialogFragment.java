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

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
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

        final String currentEdit = vm.getCurrentEdit().getName();
        if (currentEdit.isEmpty()) {
            vb.lblBookshelf.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isChanged()) {
            return true;
        }

        // store changes
        final Bookshelf bookshelf = vm.getBookshelf();
        bookshelf.setName(currentEdit);

        final Context context = requireContext();
        final BookshelfDao dao = ServiceLocator.getInstance().getBookshelfDao();
        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);

        // The logic flow here is different from the default one as used for e.g. an Author.
        // Here we reject using a name which already exists IF the user meant to create a NEW shelf.

        // Check if there is an existing one with the same name
        final Optional<Bookshelf> existingEntity = dao.findByName(context, bookshelf, locale);

        // Are we adding a new one but trying to use an existing name? -> REJECT
        if (bookshelf.getId() == 0 && existingEntity.isPresent()) {
            vb.lblBookshelf.setError(getString(R.string.warning_x_already_exists,
                                               getString(R.string.lbl_bookshelf)));
            return false;
        }

        final Consumer<Bookshelf> onSuccess = savedBookshelf -> ParcelableDialogLauncher
                .setEditInPlaceResult(this, vm.getRequestKey(), savedBookshelf);

        try {
            if (existingEntity.isPresent()) {
                // There is one with the same name; ask whether to merge the 2
                StandardDialogs.askToMerge(context, R.string.confirm_merge_bookshelves,
                                           bookshelf.getLabel(context), () -> {
                            dismiss();
                            try {
                                // Note that we ONLY move the books. No other attributes from
                                // the source item are copied to the target item!
                                dao.moveBooks(context, bookshelf, existingEntity.get());

                                // return the item which 'lost' it's books
                                onSuccess.accept(bookshelf);
                            } catch (@NonNull final DaoWriteException e) {
                                ErrorDialog.show(context, TAG, e);
                            }
                        });
                return false;
            } else {
                // Just insert or update as needed
                if (bookshelf.getId() == 0) {
                    dao.insert(context, bookshelf, locale);
                } else {
                    dao.update(context, bookshelf, locale);
                }
                onSuccess.accept(bookshelf);
                return true;
            }
        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, bookshelf);
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
}
