/*
 * @Copyright 2018-2022 HardBackNutter
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
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.RowChangedListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

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
    private static final String TAG = "EditAuthorDialogFrag";

    /** Author View model. Fragment scope. */
    private EditAuthorViewModel authorVm;

    /** View Binding. */
    private DialogEditAuthorBinding vb;

    /**
     * No-arg constructor for OS use.
     */
    public EditAuthorDialogFragment() {
        super(R.layout.dialog_edit_author);
    }

    /**
     * Launch the dialog.
     *
     * @param fm     The FragmentManager this fragment will be added to.
     * @param author to edit.
     */
    public static void launch(@NonNull final FragmentManager fm,
                              @NonNull final Author author) {
        final Bundle args = new Bundle(2);
        args.putString(EditAuthorViewModel.BKEY_REQUEST_KEY, RowChangedListener.REQUEST_KEY);
        args.putParcelable(DBKey.FK_AUTHOR, author);

        final DialogFragment frag = new EditAuthorDialogFragment();
        frag.setArguments(args);
        frag.show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authorVm = new ViewModelProvider(this).get(EditAuthorViewModel.class);
        authorVm.init(requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vb = DialogEditAuthorBinding.bind(view);

        final Context context = getContext();
        final Author currentEdit = authorVm.getCurrentEdit();

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();

        //noinspection ConstantConditions
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

        final ExtArrayAdapter<String> realNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBKey.AUTHOR_FORMATTED));
        vb.realAuthor.setText(authorVm.getCurrentRealAuthorName(), false);
        vb.realAuthor.setAdapter(realNameAdapter);
        autoRemoveError(vb.realAuthor, vb.lblRealAuthor);

        vb.cbxIsComplete.setChecked(currentEdit.isComplete());

        vb.familyName.requestFocus();
    }

    @Nullable
    @Override
    protected Button mapButton(@NonNull final Button actionButton,
                               @NonNull final View buttonPanel) {
        if (actionButton.getId() == R.id.btn_save) {
            return buttonPanel.findViewById(R.id.btn_positive);
        }
        return null;
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem menuItem,
                                             @Nullable final Button button) {
        if (menuItem.getItemId() == R.id.MENU_ACTION_CONFIRM && button != null) {
            if (button.getId() == R.id.btn_save) {
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

        //noinspection ConstantConditions
        if (!authorVm.validateAndSetRealAuthor(context, bookLocale, createRealAuthorIfNeeded)) {
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.err_real_author_must_be_valid)
                    .setMessage(context.getString(R.string.confirm_create_real_author,
                                                  authorVm.getCurrentRealAuthorName()))
                    .setNegativeButton(R.string.action_edit, (d, w) -> vb.lblRealAuthor.setError(
                            getString(R.string.err_real_author_must_be_valid)))
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

        final Author author = authorVm.getAuthor();
        final Author realAuthor = author.getRealAuthor();

        final boolean nameChanged = author.hashCodeOfNameOnly()
                                    != currentEdit.hashCodeOfNameOnly();

        // anything changed at all ? If not, we're done.
        if (!nameChanged
            && author.isComplete() == currentEdit.isComplete()
            && Objects.equals(realAuthor, currentEdit.getRealAuthor())) {
            return true;
        }

        // store changes
        author.copyFrom(currentEdit, false);

        return SaveChangesHelper
                .save(this, ServiceLocator.getInstance().getAuthorDao(),
                      author, nameChanged, bookLocale,
                      updatedId -> RowChangedListener.setResult(
                              this, authorVm.getRequestKey(), DBKey.FK_AUTHOR, updatedId),
                      R.string.confirm_merge_authors);
    }

    private void viewToModel() {
        final Author currentEdit = authorVm.getCurrentEdit();
        currentEdit.setName(vb.familyName.getText().toString().trim(),
                            vb.givenNames.getText().toString().trim());
        currentEdit.setComplete(vb.cbxIsComplete.isChecked());

        authorVm.setCurrentRealAuthorName(vb.realAuthor.getText().toString().trim());
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
