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
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTocBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Dialog to add a new TOCEntry, or edit an existing one.
 */
public class EditTocEntryDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "EditTocEntryDialogFrag";

    private static final String BKEY_HAS_MULTIPLE_AUTHORS = TAG + ":hasMultipleAuthors";
    private static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";

    @Nullable
    private WeakReference<EditTocEntryResults> mListener;

    /** Database Access. */
    private DAO mDb;

    private boolean mHasMultipleAuthors;

    private DiacriticArrayAdapter<String> mAuthorAdapter;

    /** The TocEntry we're editing. */
    private TocEntry mTocEntry;
    /** View Binding. */
    private DialogEditBookTocBinding mVb;

    /**
     * Constructor.
     *
     * @param dialogTitle        to use
     * @param tocEntry           to edit.
     * @param hasMultipleAuthors Flag that will enable/disable the author edit field
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final String dialogTitle,
                                             @NonNull final TocEntry tocEntry,
                                             final boolean hasMultipleAuthors) {
        final DialogFragment frag = new EditTocEntryDialogFragment();
        final Bundle args = new Bundle(2);
        args.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, hasMultipleAuthors);
        args.putParcelable(BKEY_TOC_ENTRY, tocEntry);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final EditTocEntryResults listener) {
        mListener = new WeakReference<>(listener);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        final Bundle args = savedInstanceState != null ? savedInstanceState : requireArguments();
        mTocEntry = args.getParcelable(BKEY_TOC_ENTRY);
        Objects.requireNonNull(mTocEntry, ErrorMsg.ARGS_MISSING_TOC_ENTRIES);

        mHasMultipleAuthors = args.getBoolean(BKEY_HAS_MULTIPLE_AUTHORS, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        mVb = DialogEditBookTocBinding.inflate(inflater);

        updateMultiAuthor(mHasMultipleAuthors);
        mVb.cbxMultipleAuthors.setOnCheckedChangeListener(
                (v, isChecked) -> updateMultiAuthor(isChecked));

        mVb.title.setText(mTocEntry.getTitle());
        mVb.firstPublication.setText(mTocEntry.getFirstPublication());

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setView(mVb.getRoot())
                .setNegativeButton(android.R.string.cancel, (d, w) -> dismiss())
                .setPositiveButton(android.R.string.ok, (d1, which) -> saveChanges())
                .create();
    }

    private void saveChanges() {
        getFields();
        if (mListener != null && mListener.get() != null) {
            mListener.get().addOrUpdateEntry(mTocEntry, mHasMultipleAuthors);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "addOrUpdateEntry|" +
                           (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                              : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }
    }

    private void updateMultiAuthor(final boolean isChecked) {
        mHasMultipleAuthors = isChecked;
        mVb.cbxMultipleAuthors.setChecked(mHasMultipleAuthors);
        if (mHasMultipleAuthors) {
            if (mAuthorAdapter == null) {
                //noinspection ConstantConditions
                mAuthorAdapter = new DiacriticArrayAdapter<>(
                        getContext(), R.layout.dropdown_menu_popup_item,
                        mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED));
                mVb.author.setAdapter(mAuthorAdapter);
            }

            //noinspection ConstantConditions
            mVb.author.setText(mTocEntry.getAuthor().getLabel(getContext()));
            mVb.author.selectAll();
            mVb.lblAuthor.setVisibility(View.VISIBLE);
            mVb.author.setVisibility(View.VISIBLE);
        } else {
            mVb.lblAuthor.setVisibility(View.GONE);
            mVb.author.setVisibility(View.GONE);
        }
    }

    private void getFields() {
        //noinspection ConstantConditions
        mTocEntry.setTitle(mVb.title.getText().toString().trim());
        //noinspection ConstantConditions
        mTocEntry.setFirstPublication(mVb.firstPublication.getText().toString().trim());
        if (mHasMultipleAuthors) {
            mTocEntry.setAuthor(Author.from(mVb.author.getText().toString().trim()));
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(BKEY_TOC_ENTRY, mTocEntry);
        outState.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, mHasMultipleAuthors);
    }

    @Override
    public void onPause() {
        getFields();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    public interface EditTocEntryResults {

        void addOrUpdateEntry(@NonNull TocEntry tocEntry,
                              boolean hasMultipleAuthors);
    }
}
