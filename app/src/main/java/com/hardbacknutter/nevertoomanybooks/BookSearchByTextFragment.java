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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

public class BookSearchByTextFragment
        extends BookSearchBaseFragment {

    /** Log tag. */
    public static final String TAG = "BookSearchByTextFragment";

    /** A list of author names we have already searched for in this session. */
    @NonNull
    private final Collection<String> mRecentAuthorNames = new ArrayList<>();
    /** adapter for the AutoCompleteTextView. */
    private DiacriticArrayAdapter<String> mAuthorAdapter;

    /** User input field. */
    private AutoCompleteTextView mAuthorView;
    /** User input field. */
    private EditText mTitleView;
    /** User input field. ENHANCE: add auto-completion for publishers? */
    private EditText mPublisherView;
    /** Used to set visibility on a group of widgets all related to the Publisher. */
    private Group mPublisherGroup;

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
        View view = inflater.inflate(R.layout.fragment_booksearch_by_text, container, false);
        mAuthorView = view.findViewById(R.id.author);
        mTitleView = view.findViewById(R.id.title);
        mPublisherView = view.findViewById(R.id.publisher);
        mPublisherGroup = view.findViewById(R.id.publisher_group);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        boolean usePublisher = SearchSites.usePublisher(getContext());
        mPublisherGroup.setVisibility(usePublisher ? View.VISIBLE : View.GONE);

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.title_search_for_books);

        copyModel2View();
        populateAuthorList();

        //noinspection ConstantConditions
        getView().findViewById(R.id.btn_search).setOnClickListener(v -> {
            copyView2Model();

            String authorSearchText = mSearchCoordinator.getAuthorSearchText();
            String titleSearchText = mSearchCoordinator.getTitleSearchText();

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
                Snackbar.make(getView(), R.string.warning_requires_at_least_one_field,
                              Snackbar.LENGTH_LONG).show();
                return;
            }

            startSearch();
        });

        if (savedInstanceState == null) {
            mSearchCoordinator.getSiteList().promptToRegister(getContext(), false, "search");

            TipManager.display(getContext(), R.string.tip_book_search_by_text, null);
        }
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
                getContext(), android.R.layout.simple_dropdown_item_1line, authors);
        mAuthorView.setAdapter(mAuthorAdapter);
    }

    @NonNull
    private ArrayList<String> getAuthorNames(@NonNull final Iterable<String> authorNames) {

        final ArrayList<String> authors =
                mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);

        final Collection<String> uniqueNames = new HashSet<>(authors.size());
        for (String s : authors) {
            uniqueNames.add(s.toLowerCase(Locale.getDefault()));
        }

        // Add the names the user has already tried (to handle errors and mistakes)
        for (String s : authorNames) {
            if (!uniqueNames.contains(s.toLowerCase(Locale.getDefault()))) {
                authors.add(s);
            }
        }

        return authors;
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // if any of the search fields are not present in the result,
        // we add them manually as the template for a new book.

        if (!bookData.containsKey(DBDefinitions.KEY_TITLE)) {
            bookData.putString(DBDefinitions.KEY_TITLE,
                               mSearchCoordinator.getTitleSearchText());
        }

        //noinspection ConstantConditions
        if (!bookData.containsKey(UniqueId.BKEY_AUTHOR_ARRAY)
            || bookData.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY).isEmpty()) {
            // do NOT use the array, that's reserved for verified names.
            bookData.putString(UniqueId.BKEY_SEARCH_AUTHOR,
                               mSearchCoordinator.getAuthorSearchText());
        }

        if (!bookData.containsKey(DBDefinitions.KEY_PUBLISHER)) {
            bookData.putString(DBDefinitions.KEY_PUBLISHER,
                               mSearchCoordinator.getPublisherSearchText());
        }

        Intent intent = new Intent(getContext(), EditBookActivity.class)
                .putExtra(UniqueId.BKEY_BOOK_DATA, bookData);
        startActivityForResult(intent, UniqueId.REQ_BOOK_EDIT);
        clearPreviousSearchCriteria();
    }

    @Override
    public void onPause() {
        super.onPause();
        copyView2Model();
    }

    @Override
    void clearPreviousSearchCriteria() {
        super.clearPreviousSearchCriteria();
        mAuthorView.setText("");
        mTitleView.setText("");
        mPublisherView.setText("");
    }

    private void copyModel2View() {
        mAuthorView.setText(mSearchCoordinator.getAuthorSearchText());
        mTitleView.setText(mSearchCoordinator.getTitleSearchText());
        mPublisherView.setText(mSearchCoordinator.getPublisherSearchText());
    }

    private void copyView2Model() {
        mSearchCoordinator.setAuthorSearchText(mAuthorView.getText().toString().trim());
        mSearchCoordinator.setTitleSearchText(mTitleView.getText().toString().trim());
        mSearchCoordinator.setPublisherSearchText(mPublisherView.getText().toString().trim());
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
