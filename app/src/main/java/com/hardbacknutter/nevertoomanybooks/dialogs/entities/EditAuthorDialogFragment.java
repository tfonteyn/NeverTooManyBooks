/*
 * @Copyright 2018-2023 HardBackNutter
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
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditAuthorContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditInPlaceParcelableLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Author}.
 * <p>
 * For now this + its 3 sibling classes are not in fact called to create a new entry.
 * We do however keep the code flexible enough to allow it for future usage.
 *
 * @see EditBookshelfDialogFragment
 * @see EditSeriesDialogFragment
 * @see EditPublisherDialogFragment
 */
public class EditAuthorDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditAuthorDialogFrag";

    /** Author View model. Fragment scope. */
    private EditAuthorViewModel authorVm;

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

        authorVm = new ViewModelProvider(this).get(EditAuthorViewModel.class);
        //noinspection DataFlowIssue
        authorVm.init(getContext(), requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditAuthorContentBinding.bind(view.findViewById(R.id.dialog_content));

        final Context context = getContext();
        final Author currentEdit = authorVm.getCurrentEdit();

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
        if (authorVm.useRealAuthorName()) {
            vb.lblRealAuthorHeader.setVisibility(View.VISIBLE);
            vb.lblRealAuthor.setVisibility(View.VISIBLE);

            final ExtArrayAdapter<String> realNameAdapter = new ExtArrayAdapter<>(
                    context, R.layout.popup_dropdown_menu_item,
                    ExtArrayAdapter.FilterType.Diacritic,
                    authorDao.getNames(DBKey.AUTHOR_FORMATTED));
            vb.realAuthor.setText(authorVm.getCurrentRealAuthorName(), false);
            vb.realAuthor.setAdapter(realNameAdapter);
            autoRemoveError(vb.realAuthor, vb.lblRealAuthor);

        } else {
            vb.lblRealAuthorHeader.setVisibility(View.GONE);
            vb.lblRealAuthor.setVisibility(View.GONE);
        }
    }

    @Override
    protected boolean onToolbarButtonClick(@Nullable final View button) {
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

        final Author currentEdit = authorVm.getCurrentEdit();
        // basic check only, we're doing more extensive checks later on.
        if (currentEdit.getFamilyName().isEmpty()) {
            vb.lblFamilyName.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        final Context context = getContext();
        // There is no book involved here, so use the users Locale instead
        final Locale bookLocale = getResources().getConfiguration().getLocales().get(0);

        final Author originalAuthor = authorVm.getAuthor();
        final Author originalRealAuthor = originalAuthor.getRealAuthor();

        // We let this call go ahead even if real-author is switched off by the user
        // so we can clean up as needed.
        //noinspection DataFlowIssue
        if (!authorVm.validateAndSetRealAuthor(context, bookLocale, createRealAuthorIfNeeded)) {
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.vldt_real_author_must_be_valid)
                    .setMessage(context.getString(R.string.confirm_create_real_author,
                                                  authorVm.getCurrentRealAuthorName()))
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
            return false;
        }

        // Case-sensitive! We must allow the user to correct case.
        final boolean nameChanged = !originalAuthor.isSameName(currentEdit);

        // anything actually changed ? If not, we're done.
        if (!nameChanged
            && originalAuthor.isComplete() == currentEdit.isComplete()
            && Objects.equals(originalRealAuthor, currentEdit.getRealAuthor())) {
            return true;
        }

        // store changes
        originalAuthor.copyFrom(currentEdit, false);

        return SaveChangesHelper
                .save(this, ServiceLocator.getInstance().getAuthorDao(),
                      originalAuthor, nameChanged, bookLocale,
                      savedAuthor -> EditInPlaceParcelableLauncher.setResult(
                              this, authorVm.getRequestKey(), savedAuthor),
                      R.string.confirm_merge_authors);
    }

    private void viewToModel() {
        final Author currentEdit = authorVm.getCurrentEdit();
        currentEdit.setName(vb.familyName.getText().toString().trim(),
                            vb.givenNames.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());

        if (authorVm.useRealAuthorName()) {
            authorVm.setCurrentRealAuthorName(vb.realAuthor.getText().toString().trim());
        }
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
