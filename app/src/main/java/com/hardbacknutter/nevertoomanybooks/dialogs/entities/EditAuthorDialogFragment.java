/*
 * @Copyright 2019 HardBackNutter
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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Checkable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.EditBookAuthorsActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * Dialog to edit an existing single author.
 * <p>
 * Calling point is a List; see {@link EditBookAuthorsActivity} for book
 */
public class EditAuthorDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "EditAuthorDialogFragment";

    /** Database Access. */
    protected DAO mDb;

    private WeakReference<BookChangedListener> mBookChangedListener;

    private AutoCompleteTextView mFamilyNameView;
    private AutoCompleteTextView mGivenNamesView;
    private Checkable mIsCompleteView;

    /** The Author we're editing. */
    private Author mAuthor;
    /** Current edit. */
    private String mFamilyName;
    /** Current edit. */
    private String mGivenNames;
    /** Current edit. */
    private boolean mIsComplete;

    /**
     * Constructor.
     *
     * @param author to edit.
     *
     * @return the instance
     */
    public static EditAuthorDialogFragment newInstance(@NonNull final Author author) {
        EditAuthorDialogFragment frag = new EditAuthorDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(DBDefinitions.KEY_FK_AUTHOR, author);
        frag.setArguments(args);
        return frag;
    }

    protected int getLayoutId() {
        return R.layout.dialog_edit_author;
    }


    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO();

        Bundle args = requireArguments();
        mAuthor = Objects.requireNonNull(args.getParcelable(DBDefinitions.KEY_FK_AUTHOR));
        Objects.requireNonNull(mAuthor);
        if (savedInstanceState == null) {
            mFamilyName = mAuthor.getFamilyName();
            mGivenNames = mAuthor.getGivenNames();
            mIsComplete = mAuthor.isComplete();
        } else {
            mFamilyName = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
            mGivenNames = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
            mIsComplete = savedInstanceState.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View root = layoutInflater.inflate(getLayoutId(), null);

        Context context = getContext();

        @SuppressWarnings("ConstantConditions")
        ArrayAdapter<String> mFamilyNameAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                                   mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FAMILY_NAME));
        ArrayAdapter<String> mGivenNameAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line,
                                   mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES));

        // the dialog fields != screen fields.
        mFamilyNameView = root.findViewById(R.id.family_name);
        mFamilyNameView.setText(mFamilyName);
        mFamilyNameView.setAdapter(mFamilyNameAdapter);

        mGivenNamesView = root.findViewById(R.id.given_names);
        mGivenNamesView.setText(mGivenNames);
        mGivenNamesView.setAdapter(mGivenNameAdapter);

        mIsCompleteView = root.findViewById(R.id.cbx_is_complete);
        mIsCompleteView.setChecked(mIsComplete);

        return new AlertDialog.Builder(context)
                .setIcon(R.drawable.ic_edit)
                .setView(root)
                .setTitle(R.string.title_edit_author)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(R.string.btn_confirm_save, (d, which) -> {
                    mFamilyName = mFamilyNameView.getText().toString().trim();
                    if (mFamilyName.isEmpty()) {
                        UserMessage.show(mFamilyNameView, R.string.warning_missing_name);
                        return;
                    }

                    mGivenNames = mGivenNamesView.getText().toString().trim();
                    mIsComplete = mIsCompleteView.isChecked();

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
                    Bundle data = new Bundle();
                    data.putLong(DBDefinitions.KEY_FK_AUTHOR, mAuthor.getId());
                    if (mBookChangedListener.get() != null) {
                        mBookChangedListener.get()
                                            .onBookChanged(0, BookChangedListener.AUTHOR, data);
                    } else {
                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                            Logger.debug(this, "onBookChanged",
                                         Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
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
        mFamilyName = mFamilyNameView.getText().toString().trim();
        mGivenNames = mGivenNamesView.getText().toString().trim();
        mIsComplete = mIsCompleteView.isChecked();
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
