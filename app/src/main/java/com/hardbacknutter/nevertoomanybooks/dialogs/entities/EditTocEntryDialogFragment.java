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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTocBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link TocEntry}.
 */
public class EditTocEntryDialogFragment
        extends BaseDialogFragment
        implements BookChangedListener.Owner {

    /** Log tag. */
    public static final String TAG = "EditTocEntryDialogFrag";

    public static final String BKEY_HAS_MULTIPLE_AUTHORS = TAG + ":hasMultipleAuthors";
    public static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";
    private static final String BKEY_BOOK_ID = TAG + ":bookId";

    /** Database Access. */
    private DAO mDb;
    /** Where to send the result. */
    @Nullable
    private WeakReference<BookChangedListener> mListener;
    @Nullable
    private String mBookTitle;
    private long mBookId;

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
     * No-arg constructor for OS use.
     */
    public EditTocEntryDialogFragment() {
        super(R.layout.dialog_edit_book_toc);
    }

    /**
     * Constructor.
     *
     * @param book               the entry belongs to
     * @param tocEntry           to edit.
     * @param hasMultipleAuthors Flag that will enable/disable the author edit field
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final Book book,
                                             @NonNull final TocEntry tocEntry,
                                             final boolean hasMultipleAuthors) {
        final DialogFragment frag = new EditTocEntryDialogFragment();
        final Bundle args = new Bundle(4);
        args.putString(DBDefinitions.KEY_TITLE, book.getTitle());
        args.putLong(BKEY_BOOK_ID, book.getId());
        args.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, hasMultipleAuthors);
        args.putParcelable(BKEY_TOC_ENTRY, tocEntry);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mBookTitle = args.getString(DBDefinitions.KEY_TITLE);
        mBookId = args.getLong(BKEY_BOOK_ID);
        mTocEntry = args.getParcelable(BKEY_TOC_ENTRY);
        Objects.requireNonNull(mTocEntry, ErrorMsg.NULL_TOC_ENTRY);

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

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditBookTocBinding.bind(view);

        mVb.toolbar.setSubtitle(mBookTitle);
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
            showError(mVb.lblTitle, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        //noinspection ConstantConditions
        if (mTocEntry.getTitle().equals(mTitle)
            && mTocEntry.getFirstPublication().equals(mFirstPublication)
            && mTocEntry.getAuthor().getLabel(getContext()).equals(mAuthorName)) {
            return true;
        }

        // store changes
        mTocEntry.setTitle(mTitle);
        mTocEntry.setFirstPublication(mFirstPublication);
        if (mHasMultipleAuthors) {
            mTocEntry.setAuthor(Author.from(mAuthorName));
        }

        // We don't update/insert to the database here, but just send the data back.
        // TOCs are updated in bulk/list per Book
        final Bundle data = new Bundle();
        data.putParcelable(BKEY_TOC_ENTRY, mTocEntry);
        data.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, mHasMultipleAuthors);

        if (mListener != null && mListener.get() != null) {
            mListener.get().onChange(mBookId, BookChangedListener.TOC_ENTRY, data);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "addOrUpdateEntry|"
                           + (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
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
