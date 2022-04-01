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
package com.hardbacknutter.nevertoomanybooks.search;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ResultIntentOwner;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByTextBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

public class SearchBookByTextFragment
        extends SearchBookBaseFragment {

    /** Log tag. */
    public static final String TAG = "SearchBookByTextFragment";

    /** adapter for the AutoCompleteTextView. */
    private ExtArrayAdapter<String> mAuthorAdapter;
    /** adapter for the AutoCompleteTextView. */
    private ExtArrayAdapter<String> mPublisherAdapter;
    /** View Binding. */
    private FragmentBooksearchByTextBinding mVb;

    private SearchBookByTextViewModel mVm;

    @SuppressWarnings("FieldCanBeLocal")
    private MenuProvider mSearchSitesToolbarMenuProvider;

    @NonNull
    @Override
    protected ResultIntentOwner getResultOwner() {
        return mVm;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mVm = new ViewModelProvider(this).get(SearchBookByTextViewModel.class);
        mVm.init();
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

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_search_for_books);
        mSearchSitesToolbarMenuProvider = new SearchSitesToolbarMenuProvider();
        toolbar.addMenuProvider(mSearchSitesToolbarMenuProvider, getViewLifecycleOwner());

        if (mVm.usePublisher()) {
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

        mVb.author.setText(mCoordinator.getAuthorSearchText());
        mVb.title.setText(mCoordinator.getTitleSearchText());
        mVb.publisher.setText(mCoordinator.getPublisherSearchText());

        populateAuthorList();
        populatePublisherList();

        mVb.btnSearch.setOnClickListener(v -> startSearch());
        explainSitesSupport(mCoordinator.getSiteList());

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.getInstance().display(getContext(), R.string.tip_book_search_by_text, () ->
                    Site.promptToRegister(getContext(), mCoordinator.getSiteList(),
                                          "searchByText", null));
        }
    }

    protected void explainSitesSupport(@Nullable final ArrayList<Site> sites) {
        if (sites != null
            && sites.stream()
                    .filter(Site::isEnabled)
                    .map(Site::getSearchEngine)
                    .anyMatch(se -> se instanceof SearchEngine.ByText)) {
            mVb.btnSearch.setEnabled(true);
            mVb.txtCanSearch.setVisibility(View.GONE);
        } else {
            mVb.btnSearch.setEnabled(false);
            mVb.txtCanSearch.setVisibility(View.VISIBLE);
            mVb.txtCanSearch.setText(getString(R.string.warning_no_site_supports_this_method,
                                               getString(R.string.txt_author_title)));
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
    public void onPause() {
        super.onPause();
        viewToModel();
    }

    private void viewToModel() {
        mCoordinator.setAuthorSearchText(mVb.author.getText().toString().trim());
        //noinspection ConstantConditions
        mCoordinator.setTitleSearchText(mVb.title.getText().toString().trim());
        mCoordinator.setPublisherSearchText(mVb.publisher.getText().toString().trim());
    }

    /**
     * Setup the adapter for the Author AutoCompleteTextView field.
     */
    private void populateAuthorList() {
        //noinspection ConstantConditions
        mAuthorAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, mVm.getAuthorNames(getContext()));
        mVb.author.setAdapter(mAuthorAdapter);
    }

    /**
     * Setup the adapter for the Publisher AutoCompleteTextView field.
     */
    private void populatePublisherList() {
        //noinspection ConstantConditions
        mPublisherAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, mVm.getPublisherNames(getContext()));
        mVb.publisher.setAdapter(mPublisherAdapter);
    }

    @Override
    boolean onPreSearch() {
        viewToModel();

        final String authorSearchText = mCoordinator.getAuthorSearchText();
        if (!authorSearchText.isEmpty()) {
            // Always add the current search text (if not already present)
            // to the list of recent searches.
            if (mAuthorAdapter.getPosition(authorSearchText) < 0) {
                if (mVm.addAuthorName(authorSearchText)) {
                    // Add to adapter, in case search produces no results
                    mAuthorAdapter.add(authorSearchText);
                }
            }
        }

        final String publisherSearchText = mCoordinator.getPublisherSearchText();
        if (mVm.usePublisher() && !publisherSearchText.isEmpty()) {
            // Always add the current search text (if not already present)
            // to the list of recent searches.
            if (mPublisherAdapter.getPosition(publisherSearchText) < 0) {
                if (mVm.addPublisherName(publisherSearchText)) {
                    // Add to adapter, in case search produces no results
                    mPublisherAdapter.add(publisherSearchText);
                }
            }
        }

        //sanity check
        final String titleSearchText = mCoordinator.getTitleSearchText();
        if (authorSearchText.isEmpty() && titleSearchText.isEmpty()) {
            Snackbar.make(mVb.getRoot(), R.string.warning_requires_at_least_1_field,
                          Snackbar.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // Don't check on any results... just accept them and create a new book.

        // If any of the search fields are not present in the result,
        // we add them manually as the template for a new book.

        if (!bookData.containsKey(DBKey.KEY_TITLE)) {
            bookData.putString(DBKey.KEY_TITLE, mCoordinator.getTitleSearchText());
        }

        final ArrayList<Author> authors =
                bookData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        if (authors == null || authors.isEmpty()) {
            // do NOT use the array, that's reserved for verified names.
            bookData.putString(SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR,
                               mCoordinator.getAuthorSearchText());
        }

        final ArrayList<Publisher> publishers =
                bookData.getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        if (publishers == null || publishers.isEmpty()) {
            // do NOT use the array, that's reserved for verified names.
            bookData.putString(SearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER,
                               mCoordinator.getPublisherSearchText());
        }

        // edit book
        super.onSearchResults(bookData);
    }

    @Override
    void onClearSearchCriteria() {
        super.onClearSearchCriteria();
        mVb.author.setText("");
        mVb.title.setText("");
        mVb.publisher.setText("");
    }

    @Override
    void onBookEditingDone(@Nullable final EditBookOutput data) {
        super.onBookEditingDone(data);

        // refresh, we could have modified/created Authors/Publishers while editing
        // (even when the edit was cancelled )
        populateAuthorList();
        populatePublisherList();
    }
}
