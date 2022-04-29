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
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTocBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link TocEntry}.
 */
public class EditTocEntryDialogFragment
        extends FFBaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "EditTocEntryDialogFrag";
    private static final String BKEY_HAS_MULTIPLE_AUTHORS = TAG + ":hasMultipleAuthors";
    private static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";
    private static final String BKEY_POSITION = TAG + ":pos";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    private String mRequestKey;

    /** View Binding. */
    private DialogEditBookTocBinding mVb;

    @Nullable
    private String mBookTitle;

    /** The one we're editing. */
    private TocEntry mTocEntry;
    /** the position of the tocEntry in the TOC list. */
    private int mEditPosition;

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

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        mBookTitle = args.getString(DBKey.KEY_TITLE);
        mHasMultipleAuthors = args.getBoolean(BKEY_HAS_MULTIPLE_AUTHORS, false);
        mTocEntry = Objects.requireNonNull(args.getParcelable(BKEY_TOC_ENTRY), BKEY_TOC_ENTRY);
        mEditPosition = args.getInt(BKEY_POSITION, 0);

        if (savedInstanceState == null) {
            mTitle = mTocEntry.getTitle();
            mFirstPublicationDate = mTocEntry.getFirstPublicationDate();
            //noinspection ConstantConditions
            mAuthorName = mTocEntry.getPrimaryAuthor().getLabel(getContext());
        } else {
            //noinspection ConstantConditions
            mTitle = savedInstanceState.getString(DBKey.KEY_TITLE);
            //noinspection ConstantConditions
            mFirstPublicationDate = savedInstanceState.getParcelable(DBKey.DATE_FIRST_PUBLICATION);
            //noinspection ConstantConditions
            mAuthorName = savedInstanceState.getString(DBKey.KEY_AUTHOR_FORMATTED);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditBookTocBinding.bind(view);

        mVb.toolbar.setSubtitle(mBookTitle);

        mVb.title.setText(mTitle);

        mFirstPublicationDate.ifPresent(date -> mVb.firstPublication.setText(
                String.valueOf(date.getYearValue())));

        if (mHasMultipleAuthors) {
            //noinspection ConstantConditions
            final ExtArrayAdapter<String> authorAdapter = new ExtArrayAdapter<>(
                    getContext(), R.layout.popup_dropdown_menu_item,
                    ExtArrayAdapter.FilterType.Diacritic,
                    ServiceLocator.getInstance().getAuthorDao()
                                  .getNames(DBKey.KEY_AUTHOR_FORMATTED));
            mVb.author.setAdapter(authorAdapter);
            mVb.author.setText(mAuthorName);
            mVb.author.selectAll();
            mVb.author.requestFocus();

            mVb.lblAuthor.setVisibility(View.VISIBLE);
            mVb.author.setVisibility(View.VISIBLE);

        } else {
            mVb.title.requestFocus();

            mVb.lblAuthor.setVisibility(View.GONE);
            mVb.author.setVisibility(View.GONE);
        }
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
        Launcher.setResult(this, mRequestKey, mTocEntry, mEditPosition);
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
        outState.putString(DBKey.KEY_TITLE, mTitle);
        outState.putParcelable(DBKey.DATE_FIRST_PUBLICATION, mFirstPublicationDate);
        outState.putString(DBKey.KEY_AUTHOR_FORMATTED, mAuthorName);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    public abstract static class Launcher
            implements FragmentResultListener {

        private String mRequestKey;
        private FragmentManager mFragmentManager;

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final TocEntry tocEntry,
                              final int position) {

            final Bundle result = new Bundle(2);
            result.putParcelable(BKEY_TOC_ENTRY, tocEntry);
            result.putInt(BKEY_POSITION, position);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKey,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            mFragmentManager = fragmentManager;
            mRequestKey = requestKey;
            mFragmentManager.setFragmentResultListener(mRequestKey, lifecycleOwner, this);
        }

        /**
         * Constructor.
         *
         * @param book               the entry belongs to
         * @param position           of the tocEntry in the list
         * @param tocEntry           to edit.
         * @param hasMultipleAuthors Flag that will enable/disable the author edit field
         */
        public void launch(@NonNull final Book book,
                           final int position,
                           @NonNull final TocEntry tocEntry,
                           final boolean hasMultipleAuthors) {

            final Bundle args = new Bundle(5);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putString(DBKey.KEY_TITLE, book.getTitle());
            args.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, hasMultipleAuthors);
            args.putInt(BKEY_POSITION, position);
            args.putParcelable(BKEY_TOC_ENTRY, tocEntry);

            final DialogFragment frag = new EditTocEntryDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getParcelable(BKEY_TOC_ENTRY), BKEY_TOC_ENTRY),
                     result.getInt(BKEY_POSITION));
        }

        /**
         * Callback handler.
         *
         * @param tocEntry the modified entry
         */
        public abstract void onResult(@NonNull TocEntry tocEntry,
                                      final int position);
    }
}
