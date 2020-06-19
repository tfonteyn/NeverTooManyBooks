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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByTextBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * ENHANCE: perhaps add auto-completion for publishers.
 */
public class BookSearchByTextFragment
        extends BookSearchBaseFragment {

    /** Log tag. */
    public static final String TAG = "BookSearchByTextFragment";

    /** A list of author names we have already searched for in this session. */
    @NonNull
    private final Collection<String> mRecentAuthorNames = new ArrayList<>();
    /** adapter for the AutoCompleteTextView. */
    private DiacriticArrayAdapter<String> mAuthorAdapter;

    /** View Binding. */
    private FragmentBooksearchByTextBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentBooksearchByTextBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        final boolean usePublisher = SearchSites.usePublisher(getContext());
        //TEST: do we need to set the ime options explicitly ?
        if (usePublisher) {
            mVb.lblPublisher.setVisibility(View.VISIBLE);

            mVb.title.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            mVb.title.setOnEditorActionListener(null);

            mVb.publisher.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            mVb.publisher.setOnEditorActionListener(this::onEditorAction);

        } else {
            mVb.lblPublisher.setVisibility(View.GONE);

            mVb.title.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            mVb.title.setOnEditorActionListener(this::onEditorAction);
        }
        mVb.btnSearch.setOnClickListener(v -> startSearch());

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_search_for_books);

        modelToView();
        populateAuthorList();

        if (savedInstanceState == null) {
            mCoordinator.getSiteList().promptToRegister(getContext(), false, "search");

            TipManager.display(getContext(), R.string.tip_book_search_by_text, null);
        }
    }

    private boolean onEditorAction(@NonNull final TextView v,
                                   final int actionId,
                                   @Nullable final KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            BaseActivity.hideKeyboard(v);
            startSearch();
            return true;
        }
        return false;
    }

    @Override
    boolean onPreSearch() {
        viewToModel();

        final String authorSearchText = mCoordinator.getAuthorSearchText();
        final String titleSearchText = mCoordinator.getTitleSearchText();

        if (!authorSearchText.isEmpty()) {
            // Always add the current search text to the list of recent searches.
            if (mAuthorAdapter.getPosition(authorSearchText) < 0) {
                boolean found = false;
                for (String s : mRecentAuthorNames) {
                    if (s.equalsIgnoreCase(authorSearchText)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Keep a list of names as typed to use when we recreate list
                    mRecentAuthorNames.add(authorSearchText);
                    // Add to adapter, in case search produces no results
                    mAuthorAdapter.add(authorSearchText);
                }
            }
        }

        //sanity check
        if (authorSearchText.isEmpty() && titleSearchText.isEmpty()) {
            Snackbar.make(mVb.getRoot(), R.string.warning_requires_at_least_one_field,
                          Snackbar.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    /**
     * Setup the adapter for the Author AutoCompleteTextView field.
     * Uses {@link DBDefinitions#KEY_AUTHOR_FORMATTED_GIVEN_FIRST} as not all
     * search sites can copy with the formatted version.
     */
    private void populateAuthorList() {
        // Get all known authors and build a Set of the names
        final ArrayList<String> authors = getAuthorNames(mRecentAuthorNames);
        // Now get an adapter based on the combined names
        //noinspection ConstantConditions
        mAuthorAdapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item, authors);
        mVb.author.setAdapter(mAuthorAdapter);
    }

    @NonNull
    private ArrayList<String> getAuthorNames(@NonNull final Iterable<String> authorNames) {

        //noinspection ConstantConditions
        final Locale locale = LocaleUtils.getUserLocale(getContext());

        final ArrayList<String> authors =
                mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);

        final Collection<String> uniqueNames = new HashSet<>(authors.size());
        for (String s : authors) {
            uniqueNames.add(s.toLowerCase(locale));
        }

        // Add the names the user has already tried (to handle errors and mistakes)
        for (String s : authorNames) {
            if (!uniqueNames.contains(s.toLowerCase(locale))) {
                authors.add(s);
            }
        }

        return authors;
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // Don't check on any results... just accept them and create a new book.

        // If any of the search fields are not present in the result,
        // we add them manually as the template for a new book.

        if (!bookData.containsKey(DBDefinitions.KEY_TITLE)) {
            bookData.putString(DBDefinitions.KEY_TITLE, mCoordinator.getTitleSearchText());
        }

        final ArrayList<Author> authors = bookData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
        if (authors == null || authors.isEmpty()) {
            // do NOT use the array, that's reserved for verified names.
            bookData.putString(BooksOnBookshelfModel.SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR,
                               mCoordinator.getAuthorSearchText());
        }

        if (!bookData.containsKey(DBDefinitions.KEY_PUBLISHER)) {
            bookData.putString(DBDefinitions.KEY_PUBLISHER, mCoordinator.getPublisherSearchText());
        }

        // edit book
        super.onSearchResults(bookData);
    }

    @Override
    public void onPause() {
        super.onPause();
        viewToModel();
    }

    @Override
    void onClearPreviousSearchCriteria() {
        super.onClearPreviousSearchCriteria();
        mVb.author.setText("");
        mVb.title.setText("");
        mVb.publisher.setText("");
    }

    private void modelToView() {
        mVb.author.setText(mCoordinator.getAuthorSearchText());
        mVb.title.setText(mCoordinator.getTitleSearchText());
        mVb.publisher.setText(mCoordinator.getPublisherSearchText());
    }

    private void viewToModel() {
        mCoordinator.setAuthorSearchText(mVb.author.getText().toString().trim());
        //noinspection ConstantConditions
        mCoordinator.setTitleSearchText(mVb.title.getText().toString().trim());
        //noinspection ConstantConditions
        mCoordinator.setPublisherSearchText(mVb.publisher.getText().toString().trim());
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }
        // first do the common action when the user has saved the data for the book.
        super.onActivityResult(requestCode, resultCode, data);
        // refresh, we could have modified/created Authors while editing
        // (even when the edit was cancelled )
        populateAuthorList();
    }
}
