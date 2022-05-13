/*
 * @Copyright 2018-2021 HardBackNutter
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
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.RowChangedListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Author}.
 */
public class EditAuthorDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditAuthorDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    /** View Binding. */
    private DialogEditAuthorBinding mVb;

    /** The Author we're editing. */
    private Author mAuthor;

    /** Current edit. */
    private Author mCurrentEdit;

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
        args.putString(BKEY_REQUEST_KEY, RowChangedListener.REQUEST_KEY);
        args.putParcelable(DBKey.FK_AUTHOR, author);

        final DialogFragment frag = new EditAuthorDialogFragment();
        frag.setArguments(args);
        frag.show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        mAuthor = Objects.requireNonNull(args.getParcelable(DBKey.FK_AUTHOR), DBKey.FK_AUTHOR);

        if (savedInstanceState == null) {
            mCurrentEdit = new Author(mAuthor.getFamilyName(),
                                      mAuthor.getGivenNames(),
                                      mAuthor.isComplete());
        } else {
            //noinspection ConstantConditions
            mCurrentEdit = savedInstanceState.getParcelable(DBKey.FK_AUTHOR);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogEditAuthorBinding.bind(view);

        final Context context = getContext();

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> familyNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBKey.AUTHOR_FAMILY_NAME));

        final ExtArrayAdapter<String> givenNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBKey.AUTHOR_GIVEN_NAMES));

        mVb.familyName.setText(mCurrentEdit.getFamilyName());
        mVb.familyName.setAdapter(familyNameAdapter);
        mVb.givenNames.setText(mCurrentEdit.getGivenNames());
        mVb.givenNames.setAdapter(givenNameAdapter);
        mVb.cbxIsComplete.setChecked(mCurrentEdit.isComplete());

        // don't requestFocus() as we have multiple fields.
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            if (saveChanges()) {
                dismiss();
            }
            return true;
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();
        if (mCurrentEdit.getFamilyName().isEmpty()) {
            showError(mVb.lblFamilyName, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        if (mAuthor.getFamilyName().equals(mCurrentEdit.getFamilyName())
            && mAuthor.getGivenNames().equals(mCurrentEdit.getGivenNames())
            && mAuthor.isComplete() == mCurrentEdit.isComplete()) {
            return true;
        }

        // store changes
        mAuthor.copyFrom(mCurrentEdit, false);

        final Context context = getContext();

        // There is no book involved here, so use the users Locale instead
        final Locale bookLocale = getResources().getConfiguration().getLocales().get(0);

        final AuthorDao authorDao = ServiceLocator.getInstance().getAuthorDao();

        // check if it already exists (will be 0 if not)
        //noinspection ConstantConditions
        final long existingId = authorDao.find(context, mAuthor, true, bookLocale);

        if (existingId == 0) {
            final boolean success;
            if (mAuthor.getId() == 0) {
                success = authorDao.insert(context, mAuthor) > 0;
            } else {
                success = authorDao.update(context, mAuthor);
            }
            if (success) {
                RowChangedListener.setResult(this, mRequestKey,
                                             DBKey.FK_AUTHOR, mAuthor.getId());
                return true;
            }
        } else {
            // Merge the 2
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(mAuthor.getLabel(context))
                    .setMessage(R.string.confirm_merge_authors)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.action_merge, (d, w) -> {
                        dismiss();
                        // move all books from the one being edited to the existing one
                        try {
                            authorDao.merge(context, mAuthor, existingId);
                            RowChangedListener.setResult(
                                    this, mRequestKey,
                                    // return the author who 'lost' their books
                                    DBKey.FK_AUTHOR, mAuthor.getId());
                        } catch (@NonNull final DaoWriteException e) {
                            Logger.error(TAG, e);
                            StandardDialogs.showError(context, R.string.error_storage_not_writable);
                        }
                    })
                    .create()
                    .show();
        }
        return false;
    }

    private void viewToModel() {
        mCurrentEdit.setName(mVb.familyName.getText().toString().trim(),
                             mVb.givenNames.getText().toString().trim());
        mCurrentEdit.setComplete(mVb.cbxIsComplete.isChecked());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(DBKey.FK_AUTHOR, mCurrentEdit);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
