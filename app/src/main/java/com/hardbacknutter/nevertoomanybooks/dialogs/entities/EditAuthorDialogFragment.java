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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditAuthorContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.ParcelableDialogLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Author}.
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
public class EditAuthorDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditAuthorDialogFrag";

    /** Author View model. Fragment scope. */
    private EditAuthorViewModel vm;

    /** View Binding. */
    private DialogEditAuthorContentBinding vb;

    /**
     * No-arg constructor for OS use.
     */
    public EditAuthorDialogFragment() {
        super(R.layout.dialog_edit_author, R.layout.dialog_edit_author_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(EditAuthorViewModel.class);
        vm.init(requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditAuthorContentBinding.bind(view.findViewById(R.id.dialog_content));

        final Context context = getContext();
        final Author currentEdit = vm.getCurrentEdit();

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();

        //noinspection DataFlowIssue
        final ExtArrayAdapter<String> familyNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBKey.AUTHOR_FAMILY_NAME));
        vb.familyName.setText(currentEdit.getFamilyName());
        vb.familyName.setAdapter(familyNameAdapter);
        autoRemoveError(vb.familyName, vb.lblFamilyName);

        final ExtArrayAdapter<String> givenNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBKey.AUTHOR_GIVEN_NAMES));
        vb.givenNames.setText(currentEdit.getGivenNames());
        vb.givenNames.setAdapter(givenNameAdapter);

        setupRealAuthorField(context, authorDao);

        vb.cbxIsComplete.setChecked(currentEdit.isComplete());

        vb.familyName.requestFocus();
    }

    private void setupRealAuthorField(@NonNull final Context context,
                                      @NonNull final AuthorDao authorDao) {
        if (vm.useRealAuthorName()) {
            vb.lblRealAuthorHeader.setVisibility(View.VISIBLE);
            vb.lblRealAuthor.setVisibility(View.VISIBLE);

            final ExtArrayAdapter<String> realNameAdapter = new ExtArrayAdapter<>(
                    context, R.layout.popup_dropdown_menu_item,
                    ExtArrayAdapter.FilterType.Diacritic,
                    authorDao.getNames(DBKey.AUTHOR_FORMATTED));
            vb.realAuthor.setText(vm.getCurrentRealAuthorName(), false);
            vb.realAuthor.setAdapter(realNameAdapter);
            autoRemoveError(vb.realAuthor, vb.lblRealAuthor);

        } else {
            vb.lblRealAuthorHeader.setVisibility(View.GONE);
            vb.lblRealAuthor.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges(false)) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges(final boolean createRealAuthorIfNeeded) {
        viewToModel();

        final Author currentEdit = vm.getCurrentEdit();
        // basic check only, we're doing more extensive checks later on.
        if (currentEdit.getFamilyName().isEmpty()) {
            vb.lblFamilyName.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        final Context context = getContext();
        final Locale locale = getResources().getConfiguration().getLocales().get(0);

        // We let this call go ahead even if real-author is switched off by the user
        // so we can clean up as needed.
        //noinspection DataFlowIssue
        if (!vm.validateAndSetRealAuthor(context, locale, createRealAuthorIfNeeded)) {
            warnThatRealAuthorMustBeValid();
            return false;
        }

        // anything actually changed ? If not, we're done.
        if (!vm.isModified()) {
            return true;
        }

        try {
            final Optional<Author> existingEntity = vm.saveIfUnique(context);
            if (existingEntity.isEmpty()) {
                sendResultBack(vm.getAuthor());
                return true;
            }

            // There is one with the same name; ask whether to merge the 2
            StandardDialogs.askToMerge(context, R.string.confirm_merge_authors,
                                       vm.getAuthor().getLabel(context), () -> {
                        dismiss();
                        try {
                            vm.move(context, existingEntity.get());
                            // return the item which 'lost' it's books
                            sendResultBack(vm.getAuthor());
                        } catch (@NonNull final DaoWriteException e) {
                            // log, but ignore - should never happen unless disk full
                            LoggerFactory.getLogger().e(TAG, e, vm.getAuthor());
                        }
                    });
            return false;

        } catch (@NonNull final DaoWriteException e) {
            // log, but ignore - should never happen unless disk full
            LoggerFactory.getLogger().e(TAG, e, vm.getAuthor());
            return false;
        }
    }

    private void warnThatRealAuthorMustBeValid() {
        final Context context = requireContext();
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.vldt_real_author_must_be_valid)
                .setMessage(context.getString(R.string.confirm_create_real_author,
                                              vm.getCurrentRealAuthorName()))
                .setNegativeButton(R.string.action_edit, (d, w) -> vb.lblRealAuthor.setError(
                        getString(R.string.vldt_real_author_must_be_valid)))
                .setPositiveButton(R.string.action_create, (d, w) -> {
                    if (saveChanges(true)) {
                        // finish the DialogFragment
                        dismiss();
                    }
                })
                .create()
                .show();
    }

    private void viewToModel() {
        final Author currentEdit = vm.getCurrentEdit();
        currentEdit.setName(vb.familyName.getText().toString().trim(),
                            vb.givenNames.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());

        if (vm.useRealAuthorName()) {
            vm.setCurrentRealAuthorName(vb.realAuthor.getText().toString().trim());
        }
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    private void sendResultBack(@NonNull final Author author) {
        ParcelableDialogLauncher.setEditInPlaceResult(this, vm.getRequestKey(), author);
    }
}
