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

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BookChangedListenerOwner;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Author}.
 */
public class EditAuthorDialogFragment
        extends BaseDialogFragment
        implements BookChangedListenerOwner {

    /** Fragment/Log tag. */
    public static final String TAG = "EditAuthorDialogFrag";

    /** Database Access. */
    private DAO mDb;
    /** Where to send the result. */
    @Nullable
    private WeakReference<BookChangedListener> mListener;
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
        // Always force full screen as this dialog is to large/complicated.
        super(R.layout.dialog_edit_author, true);
    }

    /**
     * Constructor.
     *
     * @param author to edit.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final Author author) {
        final DialogFragment frag = new EditAuthorDialogFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(DBDefinitions.KEY_FK_AUTHOR, author);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mAuthor = args.getParcelable(DBDefinitions.KEY_FK_AUTHOR);
        Objects.requireNonNull(mAuthor, ErrorMsg.NULL_AUTHOR);

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

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogEditAuthorBinding.bind(view);

        mVb.toolbar.setNavigationOnClickListener(v -> dismiss());
        mVb.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.MENU_SAVE) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        final Context context = getContext();

        //noinspection ConstantConditions
        final DiacriticArrayAdapter<String> familyNameAdapter = new DiacriticArrayAdapter<>(
                context, R.layout.dropdown_menu_popup_item,
                mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FAMILY_NAME));
        final DiacriticArrayAdapter<String> givenNameAdapter = new DiacriticArrayAdapter<>(
                context, R.layout.dropdown_menu_popup_item,
                mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES));

        mVb.familyName.setText(mFamilyName);
        mVb.familyName.setAdapter(familyNameAdapter);
        mVb.givenNames.setText(mGivenNames);
        mVb.givenNames.setAdapter(givenNameAdapter);
        mVb.cbxIsComplete.setChecked(mIsComplete);
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

        final boolean success;
        if (mAuthor.getId() == 0) {
            //noinspection ConstantConditions
            success = mDb.insert(getContext(), mAuthor) > 0;
        } else {
            //noinspection ConstantConditions
            success = mDb.update(getContext(), mAuthor);
        }
        if (success) {
            if (mListener != null && mListener.get() != null) {
                mListener.get().onChange(0, BookChangedListener.AUTHOR, null);
            } else {
                if (BuildConfig.DEBUG /* always */) {
                    Log.w(TAG, "onBookChanged|"
                               + (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                    : ErrorMsg.LISTENER_WAS_DEAD));
                }
            }
            return true;
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

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    @Override
    public void setListener(@NonNull final BookChangedListener listener) {
        mListener = new WeakReference<>(listener);
    }

    @Override
    public void onPause() {
        viewToModel();
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
