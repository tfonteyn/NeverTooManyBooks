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

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTocBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogFragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link TocEntry}.
 */
public class EditTocEntryDialogFragment
        extends BaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "EditTocEntryDialogFrag";
    private static final String BKEY_HAS_MULTIPLE_AUTHORS = TAG + ":hasMultipleAuthors";
    private static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    private String mRequestKey;
    /** Database Access. */
    private DAO mDb;
    /** View Binding. */
    private DialogEditBookTocBinding mVb;

    @Nullable
    private String mBookTitle;

    private ExtArrayAdapter<String> mAuthorAdapter;

    /** The TocEntry we're editing. */
    private TocEntry mTocEntry;

    /** Current edit. */
    private String mTitle;
    /** Current edit. */
    private PartialDate mFirstPublicationDate;
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

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
        mBookTitle = args.getString(DBDefinitions.KEY_TITLE);
        mTocEntry = Objects.requireNonNull(args.getParcelable(BKEY_TOC_ENTRY), "BKEY_TOC_ENTRY");

        if (savedInstanceState == null) {
            mTitle = mTocEntry.getTitle();
            mFirstPublicationDate = mTocEntry.getFirstPublicationDate();
            //noinspection ConstantConditions
            mAuthorName = mTocEntry.getPrimaryAuthor().getLabel(getContext());
        } else {
            //noinspection ConstantConditions
            mTitle = savedInstanceState.getString(DBDefinitions.KEY_TITLE);
            //noinspection ConstantConditions
            mFirstPublicationDate = savedInstanceState
                    .getParcelable(DBDefinitions.KEY_DATE_FIRST_PUBLICATION);
            //noinspection ConstantConditions
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

        mVb.title.setText(mTitle);
        mVb.firstPublication.setText(String.valueOf(mFirstPublicationDate.getYearValue()));

        updateMultiAuthor(mHasMultipleAuthors);
        mVb.cbxMultipleAuthors.setOnCheckedChangeListener(
                (v, isChecked) -> updateMultiAuthor(isChecked));
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

    private void updateMultiAuthor(final boolean isChecked) {
        mHasMultipleAuthors = isChecked;
        mVb.cbxMultipleAuthors.setChecked(mHasMultipleAuthors);
        if (mHasMultipleAuthors) {
            if (mAuthorAdapter == null) {
                //noinspection ConstantConditions
                mAuthorAdapter = new ExtArrayAdapter<>(
                        getContext(), R.layout.dropdown_menu_popup_item,
                        ExtArrayAdapter.FilterType.Diacritic,
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
            && mTocEntry.getFirstPublicationDate().equals(mFirstPublicationDate)
            && mTocEntry.getPrimaryAuthor().getLabel(getContext()).equals(mAuthorName)) {
            return true;
        }

        // store changes
        mTocEntry.setTitle(mTitle);
        mTocEntry.setFirstPublicationDate(mFirstPublicationDate);
        if (mHasMultipleAuthors) {
            mTocEntry.setPrimaryAuthor(Author.from(mAuthorName));
        }

        // We don't update/insert to the database here, but just send the data back.
        // TOCs are updated in bulk/list per Book
        Launcher.setResult(this, mRequestKey, mTocEntry, mHasMultipleAuthors);
        return true;
    }

    private void viewToModel() {
        //noinspection ConstantConditions
        mTitle = mVb.title.getText().toString().trim();
        //noinspection ConstantConditions
        mFirstPublicationDate = new PartialDate(mVb.firstPublication.getText().toString().trim());
        if (mHasMultipleAuthors) {
            mAuthorName = mVb.author.getText().toString().trim();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_TITLE, mTitle);
        outState.putParcelable(DBDefinitions.KEY_DATE_FIRST_PUBLICATION, mFirstPublicationDate);
        outState.putString(DBDefinitions.KEY_AUTHOR_FORMATTED, mAuthorName);
        outState.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, mHasMultipleAuthors);
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

    public abstract static class Launcher
            extends DialogFragmentLauncherBase {

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final TocEntry tocEntry,
                              final boolean hasMultipleAuthors) {

            final Bundle result = new Bundle(2);
            result.putParcelable(BKEY_TOC_ENTRY, tocEntry);
            result.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, hasMultipleAuthors);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Constructor.
         *
         * @param book               the entry belongs to
         * @param tocEntry           to edit.
         * @param hasMultipleAuthors Flag that will enable/disable the author edit field
         */
        public void launch(@NonNull final Book book,
                           @NonNull final TocEntry tocEntry,
                           final boolean hasMultipleAuthors) {

            final Bundle args = new Bundle(4);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putString(DBDefinitions.KEY_TITLE, book.getTitle());
            args.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, hasMultipleAuthors);
            args.putParcelable(BKEY_TOC_ENTRY, tocEntry);

            final DialogFragment frag = new EditTocEntryDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getParcelable(BKEY_TOC_ENTRY)),
                     result.getBoolean(BKEY_HAS_MULTIPLE_AUTHORS));
        }

        /**
         * Callback handler.
         *
         * @param tocEntry           the modified entry
         * @param hasMultipleAuthors flag as set by the user
         */
        public abstract void onResult(@NonNull TocEntry tocEntry,
                                      boolean hasMultipleAuthors);
    }
}
