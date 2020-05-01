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

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTocBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
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

    /** Database Access. */
    private DAO mDb;
    /** Where to send the result. */
    @Nullable
    private WeakReference<EditTocEntryResults> mListener;
    @Nullable
    private String mDialogTitle;
    /** View Binding. */
    private DialogEditBookTocBinding mVb;
    private DiacriticArrayAdapter<String> mAuthorAdapter;

    /** The TocEntry we're editing. */
    private TocEntry mTocEntry;

    /** Current edit. */
    private String mTitle;
    /** Current edit. */
    private String mFirstPublication;
    /** Current edit. */
    private String mAuthorName;

    /** Helper to show/hide the author edit field. */
    private boolean mHasMultipleAuthors;

    /**
     * Constructor.
     *
     * @param dialogTitle        the dialog title
     * @param tocEntry           to edit.
     * @param hasMultipleAuthors Flag that will enable/disable the author edit field
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final String dialogTitle,
                                             @NonNull final TocEntry tocEntry,
                                             final boolean hasMultipleAuthors) {
        final DialogFragment frag = new EditTocEntryDialogFragment();
        final Bundle args = new Bundle(3);
        args.putString(StandardDialogs.BKEY_DIALOG_TITLE, dialogTitle);
        args.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, hasMultipleAuthors);
        args.putParcelable(BKEY_TOC_ENTRY, tocEntry);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_App_FullScreen);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mDialogTitle = args.getString(StandardDialogs.BKEY_DIALOG_TITLE,
                                      getString(R.string.lbl_edit_toc_entry));

        mTocEntry = args.getParcelable(BKEY_TOC_ENTRY);
        Objects.requireNonNull(mTocEntry, ErrorMsg.ARGS_MISSING_TOC_ENTRIES);

        if (savedInstanceState == null) {
            mTitle = mTocEntry.getTitle();
            mFirstPublication = mTocEntry.getFirstPublication();
            //noinspection ConstantConditions
            mAuthorName = mTocEntry.getAuthor().getLabel(getContext());
        } else {
            mTitle = savedInstanceState.getString(DBDefinitions.KEY_TITLE);
            mFirstPublication = savedInstanceState
                    .getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION);
            mAuthorName = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);

            mHasMultipleAuthors = args.getBoolean(BKEY_HAS_MULTIPLE_AUTHORS, false);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = DialogEditBookTocBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mVb.toolbar.setNavigationOnClickListener(v -> dismiss());
        mVb.toolbar.setTitle(mDialogTitle);
        mVb.toolbar.inflateMenu(R.menu.toolbar_save);
        mVb.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        mVb.title.setText(mTitle);
        mVb.firstPublication.setText(mFirstPublication);

        updateMultiAuthor(mHasMultipleAuthors);
        mVb.cbxMultipleAuthors.setOnCheckedChangeListener(
                (v, isChecked) -> updateMultiAuthor(isChecked));
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

            mVb.author.setText(mAuthorName);
            mVb.author.selectAll();
            mVb.lblAuthor.setVisibility(View.VISIBLE);
            mVb.author.setVisibility(View.VISIBLE);
        } else {
            mVb.lblAuthor.setVisibility(View.GONE);
            mVb.author.setVisibility(View.GONE);
        }
    }

    private boolean saveChanges() {
        viewToModel();
        if (mTitle.isEmpty()) {
            Snackbar.make(mVb.title, R.string.warning_missing_name,
                          Snackbar.LENGTH_LONG).show();
            return false;
        }

        // anything actually changed ?
        //noinspection ConstantConditions
        if (mTocEntry.getTitle().equals(mTitle)
            && mTocEntry.getFirstPublication().equals(mFirstPublication)
            && mTocEntry.getAuthor().getLabel(getContext()).equals(mAuthorName)) {
            return true;
        }

        // we don't update here, but just send the new data back; TOCs are updated in bulk/book
        mTocEntry.setTitle(mTitle);
        mTocEntry.setFirstPublication(mFirstPublication);
        if (mHasMultipleAuthors) {
            mTocEntry.setAuthor(Author.from(mAuthorName));
        }

        if (mListener != null && mListener.get() != null) {
            mListener.get().addOrUpdateEntry(mTocEntry, mHasMultipleAuthors);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "addOrUpdateEntry|" +
                           (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                              : ErrorMsg.LISTENER_WAS_DEAD));
            }
        }
        return true;
    }

    private void viewToModel() {
        //noinspection ConstantConditions
        mTitle = mVb.title.getText().toString().trim();
        //noinspection ConstantConditions
        mFirstPublication = mVb.firstPublication.getText().toString().trim();
        if (mHasMultipleAuthors) {
            mAuthorName = mVb.author.getText().toString().trim();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_TITLE, mTitle);
        outState.putString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION, mFirstPublication);
        outState.putString(DBDefinitions.KEY_AUTHOR_FORMATTED, mAuthorName);
        outState.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, mHasMultipleAuthors);
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

    /**
     * Listener interface to receive notifications when dialog is confirmed.
     */
    public interface EditTocEntryResults {

        /**
         * Reports the results after this dialog was confirmed.
         *
         * @param tocEntry           with new data
         * @param hasMultipleAuthors {@code true} if the author is used
         */
        void addOrUpdateEntry(@NonNull TocEntry tocEntry,
                              boolean hasMultipleAuthors);
    }
}
