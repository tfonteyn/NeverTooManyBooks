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
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Author}.
 */
public class EditAuthorDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditAuthorDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    /** View Binding. */
    private DialogEditAuthorBinding mVb;

    /** The Author we're editing. */
    private Author mAuthor;

    /** Current edit. */
    private String mFamilyName;
    /** Current edit. */
    private String mGivenNames;
    /** Current edit. */
    private boolean mIsComplete;

    /**
     * No-arg constructor for OS use.
     */
    public EditAuthorDialogFragment() {
        super(R.layout.dialog_edit_author);
    }

    /**
     * Launch the dialog.
     *
     * @param author to edit.
     */
    public static void launch(@NonNull final FragmentActivity activity,
                              @NonNull final Author author) {
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, BooksOnBookshelf.RowChangeListener.REQUEST_KEY);
        args.putParcelable(DBDefinitions.KEY_FK_AUTHOR, author);

        final DialogFragment frag = new EditAuthorDialogFragment();
        frag.setArguments(args);
        frag.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
        mAuthor = Objects.requireNonNull(args.getParcelable(DBDefinitions.KEY_FK_AUTHOR),
                                         "KEY_FK_AUTHOR");

        if (savedInstanceState == null) {
            mFamilyName = mAuthor.getFamilyName();
            mGivenNames = mAuthor.getGivenNames();
            mIsComplete = mAuthor.isComplete();
        } else {
            //noinspection ConstantConditions
            mFamilyName = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
            //noinspection ConstantConditions
            mGivenNames = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
            mIsComplete = savedInstanceState.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE,
                                                        false);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogEditAuthorBinding.bind(view);

        final Context context = getContext();

        final AuthorDao authorDao = AuthorDao.getInstance();

        //noinspection ConstantConditions
        final ExtArrayAdapter<String> familyNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.dropdown_menu_popup_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBDefinitions.KEY_AUTHOR_FAMILY_NAME));

        final ExtArrayAdapter<String> givenNameAdapter = new ExtArrayAdapter<>(
                context, R.layout.dropdown_menu_popup_item,
                ExtArrayAdapter.FilterType.Diacritic,
                authorDao.getNames(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES));

        mVb.familyName.setText(mFamilyName);
        mVb.familyName.setAdapter(familyNameAdapter);
        mVb.givenNames.setText(mGivenNames);
        mVb.givenNames.setAdapter(givenNameAdapter);
        mVb.cbxIsComplete.setChecked(mIsComplete);
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
        if (mFamilyName.isEmpty()) {
            showError(mVb.lblFamilyName, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        if (mAuthor.getFamilyName().equals(mFamilyName)
            && mAuthor.getGivenNames().equals(mGivenNames)
            && mAuthor.isComplete() == mIsComplete) {
            return true;
        }

        // store changes
        mAuthor.setName(mFamilyName, mGivenNames);
        mAuthor.setComplete(mIsComplete);

        final Context context = getContext();

        // There is no book involved here, so use the users Locale instead
        //noinspection ConstantConditions
        final Locale bookLocale = AppLocale.getInstance().getUserLocale(context);

        final AuthorDao authorDao = AuthorDao.getInstance();
        // check if it already exists (will be 0 if not)
        final long existingId = authorDao.find(context, mAuthor, true, bookLocale);

        if (existingId == 0) {
            final boolean success;
            if (mAuthor.getId() == 0) {
                success = authorDao.insert(context, mAuthor) > 0;
            } else {
                success = authorDao.update(context, mAuthor);
            }
            if (success) {
                BooksOnBookshelf.RowChangeListener
                        .setResult(this, mRequestKey,
                                   BooksOnBookshelf.RowChangeListener.AUTHOR, mAuthor.getId());
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
                            BooksOnBookshelf.RowChangeListener.setResult(
                                    this, mRequestKey,
                                    // return the author who 'lost' their books
                                    BooksOnBookshelf.RowChangeListener.AUTHOR, mAuthor.getId());
                        } catch (@NonNull final DaoWriteException e) {
                            Logger.error(context, TAG, e);
                            StandardDialogs.showError(context, R.string.error_storage_not_writable);
                        }
                    })
                    .create()
                    .show();
        }
        return false;
    }

    private void viewToModel() {
        mFamilyName = mVb.familyName.getText().toString().trim();
        mGivenNames = mVb.givenNames.getText().toString().trim();
        mIsComplete = mVb.cbxIsComplete.isChecked();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME, mFamilyName);
        outState.putString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES, mGivenNames);
        outState.putBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE, mIsComplete);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }
}
