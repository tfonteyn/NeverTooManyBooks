/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Dialog to edit an existing single author.
 * <p>
 * Calling point is a List
 */
public class EditAuthorDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "EditAuthorDialogFrag";

    /** Database Access. */
    private DAO mDb;

    private WeakReference<BookChangedListener> mBookChangedListener;

    /** The Author we're editing. */
    private Author mAuthor;
    /** Current edit. */
    private String mFamilyName;
    /** Current edit. */
    private String mGivenNames;
    /** Current edit. */
    private boolean mIsComplete;
    /** View Binding. */
    private DialogEditAuthorBinding mVb;

    /**
     * Constructor.
     *
     * @param author to edit.
     *
     * @return the instance
     */
    public static EditAuthorDialogFragment newInstance(@NonNull final Author author) {
        EditAuthorDialogFragment frag = new EditAuthorDialogFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(DBDefinitions.KEY_FK_AUTHOR, author);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        mAuthor = requireArguments().getParcelable(DBDefinitions.KEY_FK_AUTHOR);
        Objects.requireNonNull(mAuthor, ErrorMsg.ARGS_MISSING_AUTHOR);

        if (savedInstanceState == null) {
            mFamilyName = mAuthor.getFamilyName();
            mGivenNames = mAuthor.getGivenNames();
            mIsComplete = mAuthor.isComplete();
        } else {
            mFamilyName = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
            mGivenNames = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
            mIsComplete = savedInstanceState.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE,
                                                        false);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        mVb = DialogEditAuthorBinding.inflate(inflater);

        final Context context = getContext();

        //noinspection ConstantConditions
        DiacriticArrayAdapter<String> mFamilyNameAdapter = new DiacriticArrayAdapter<>(
                context, R.layout.dropdown_menu_popup_item,
                mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FAMILY_NAME));
        DiacriticArrayAdapter<String> mGivenNameAdapter = new DiacriticArrayAdapter<>(
                context, R.layout.dropdown_menu_popup_item,
                mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES));

        // the dialog fields != screen fields.
        mVb.familyName.setText(mFamilyName);
        mVb.familyName.setAdapter(mFamilyNameAdapter);

        mVb.givenNames.setText(mGivenNames);
        mVb.givenNames.setAdapter(mGivenNameAdapter);

        mVb.cbxIsComplete.setChecked(mIsComplete);

        return new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_edit)
                .setView(mVb.getRoot())
                .setTitle(R.string.lbl_edit_author)
                .setNegativeButton(android.R.string.cancel, (d, w) -> dismiss())
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    mFamilyName = mVb.familyName.getText().toString().trim();
                    if (mFamilyName.isEmpty()) {
                        Snackbar.make(mVb.familyName, R.string.warning_missing_name,
                                      Snackbar.LENGTH_LONG).show();
                        return;
                    }

                    mGivenNames = mVb.givenNames.getText().toString().trim();
                    mIsComplete = mVb.cbxIsComplete.isChecked();

                    // anything actually changed ?
                    if (mAuthor.getFamilyName().equals(mFamilyName)
                        && mAuthor.getGivenNames().equals(mGivenNames)
                        && mAuthor.isComplete() == mIsComplete) {
                        return;
                    }

                    // this is a global update, so just set and update.
                    mAuthor.setName(mFamilyName, mGivenNames);
                    mAuthor.setComplete(mIsComplete);
                    // There is no book involved here, so use the users Locale instead
                    // and store the changes
                    mDb.updateOrInsertAuthor(context, mAuthor);

                    // and spread the news of the changes.
//                    Bundle data = new Bundle();
//                    data.putLong(DBDefinitions.KEY_FK_AUTHOR, mAuthor.getId());
                    if (mBookChangedListener.get() != null) {
                        mBookChangedListener.get()
                                            .onBookChanged(0, BookChangedListener.AUTHOR, null);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                            Log.d(TAG, "onBookChanged|" + ErrorMsg.WEAK_REFERENCE);
                        }
                    }
                })
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME, mFamilyName);
        outState.putString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES, mGivenNames);
        outState.putBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE, mIsComplete);
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final BookChangedListener listener) {
        mBookChangedListener = new WeakReference<>(listener);
    }

    @Override
    public void onPause() {
        mFamilyName = mVb.familyName.getText().toString().trim();
        mGivenNames = mVb.givenNames.getText().toString().trim();
        mIsComplete = mVb.cbxIsComplete.isChecked();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
