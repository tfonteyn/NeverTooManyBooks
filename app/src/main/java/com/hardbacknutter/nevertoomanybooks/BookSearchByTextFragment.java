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
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

public class BookSearchByTextFragment
        extends BookSearchBaseFragment {

    /** Log tag. */
    public static final String TAG = "BookSearchByTextFragment";

    /** A list of author names we have already searched for in this session. */
    @NonNull
    private final Collection<String> mRecentAuthorNames = new ArrayList<>();
    /** A list of Publisher names we have already searched for in this session. */
    @NonNull
    private final Collection<String> mRecentPublisherNames = new ArrayList<>();
    /** adapter for the AutoCompleteTextView. */
    private DiacriticArrayAdapter<String> mAuthorAdapter;
    /** adapter for the AutoCompleteTextView. */
    private DiacriticArrayAdapter<String> mPublisherAdapter;
    /** Flag: show the publisher field or not. */
    private boolean mUsePublisher;

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
        mUsePublisher = Prefs.usePublisher(getContext());

        if (mUsePublisher) {
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

        mVb.author.setText(mCoordinator.getAuthorSearchText());
        mVb.title.setText(mCoordinator.getTitleSearchText());
        mVb.publisher.setText(mCoordinator.getPublisherSearchText());
        populateAuthorList();
        populatePublisherList();

        if (savedInstanceState == null) {
            TipManager.display(getContext(), R.string.tip_book_search_by_text, () ->
                    Site.promptToRegister(getContext(), mCoordinator.getSiteList(),
                                          "search"));
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
        final String publisherSearchText = mCoordinator.getPublisherSearchText();

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

        if (mUsePublisher && !publisherSearchText.isEmpty()) {
            // Always add the current search text to the list of recent searches.
            if (mPublisherAdapter.getPosition(publisherSearchText) < 0) {
                boolean found = false;
                for (String s : mRecentPublisherNames) {
                    if (s.equalsIgnoreCase(publisherSearchText)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Keep a list of names as typed to use when we recreate list
                    mRecentPublisherNames.add(publisherSearchText);
                    // Add to adapter, in case search produces no results
                    mPublisherAdapter.add(publisherSearchText);
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
        final ArrayList<String> authorNames = getAuthorNames(mRecentAuthorNames);
        // Now get an adapter based on the combined names
        //noinspection ConstantConditions
        mAuthorAdapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item, authorNames);
        mVb.author.setAdapter(mAuthorAdapter);
    }

    @NonNull
    private ArrayList<String> getAuthorNames(@NonNull final Iterable<String> authorNames) {

        //noinspection ConstantConditions
        final Locale userLocale = LocaleUtils.getUserLocale(getContext());

        final ArrayList<String> authors =
                mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED_GIVEN_FIRST);

        final Collection<String> uniqueNames = new HashSet<>(authors.size());
        for (String s : authors) {
            uniqueNames.add(s.toLowerCase(userLocale));
        }

        // Add the names the user has already tried (to handle errors and mistakes)
        for (String s : authorNames) {
            if (!uniqueNames.contains(s.toLowerCase(userLocale))) {
                authors.add(s);
            }
        }

        return authors;
    }

    /**
     * Setup the adapter for the Publisher AutoCompleteTextView field.
     */
    private void populatePublisherList() {
        // Get all known publishers and build a Set of the names
        final ArrayList<String> publisherNames = getPublisherNames(mRecentPublisherNames);
        // Now get an adapter based on the combined names
        //noinspection ConstantConditions
        mPublisherAdapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item, publisherNames);
        mVb.publisher.setAdapter(mPublisherAdapter);
    }

    @NonNull
    private ArrayList<String> getPublisherNames(@NonNull final Iterable<String> publisherNames) {

        //noinspection ConstantConditions
        final Locale userLocale = LocaleUtils.getUserLocale(getContext());

        final ArrayList<String> publishers = mDb.getPublisherNames();

        final Collection<String> uniqueNames = new HashSet<>(publishers.size());
        for (String s : publishers) {
            uniqueNames.add(s.toLowerCase(userLocale));
        }

        // Add the names the user has already tried (to handle errors and mistakes)
        for (String s : publisherNames) {
            if (!uniqueNames.contains(s.toLowerCase(userLocale))) {
                publishers.add(s);
            }
        }

        return publishers;
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // Don't check on any results... just accept them and create a new book.

        // If any of the search fields are not present in the result,
        // we add them manually as the template for a new book.

        if (!bookData.containsKey(DBDefinitions.KEY_TITLE)) {
            bookData.putString(DBDefinitions.KEY_TITLE, mCoordinator.getTitleSearchText());
        }

        final ArrayList<Author> authors =
                bookData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
        if (authors == null || authors.isEmpty()) {
            // do NOT use the array, that's reserved for verified names.
            bookData.putString(BooksOnBookshelfModel.SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR,
                               mCoordinator.getAuthorSearchText());
        }

        final ArrayList<Publisher> publishers =
                bookData.getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
        if (publishers == null || publishers.isEmpty()) {
            // do NOT use the array, that's reserved for verified names.
            bookData.putString(BooksOnBookshelfModel.SearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER,
                               mCoordinator.getPublisherSearchText());
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

    private void viewToModel() {
        mCoordinator.setAuthorSearchText(mVb.author.getText().toString().trim());
        //noinspection ConstantConditions
        mCoordinator.setTitleSearchText(mVb.title.getText().toString().trim());
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

        // refresh, we could have modified/created Authors/Publishers while editing
        // (even when the edit was cancelled )
        populateAuthorList();
        populatePublisherList();
    }
}
