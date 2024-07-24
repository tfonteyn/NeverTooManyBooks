/*
 * @Copyright 2018-2024 HardBackNutter
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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.Side;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByTextBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;

public class SearchBookByTextFragment
        extends SearchBookBaseFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "SearchBookByTextFragment";

    /** adapter for the AutoCompleteTextView. */
    private ExtArrayAdapter<String> authorAdapter;
    /** adapter for the AutoCompleteTextView. */
    private ExtArrayAdapter<String> publisherAdapter;
    /** View Binding. */
    private FragmentBooksearchByTextBinding vb;

    private SearchBookByTextViewModel vm;

    @Override
    @NonNull
    Intent createResultIntent() {
        return vm.createResultIntent();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vm = new ViewModelProvider(this).get(SearchBookByTextViewModel.class);
        //noinspection DataFlowIssue
        vm.init(getContext(), getArguments());
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentBooksearchByTextBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Effectively disable edge-to-edge for the root view.
        InsetsListenerBuilder.create()
                             .padding()
                             .sides(Side.Left, Side.Right, Side.Bottom)
                             .applyTo(view);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_search_for_books);
        toolbar.addMenuProvider(new SearchSitesToolbarMenuProvider(), getViewLifecycleOwner());

        if (vm.usePublisher()) {
            vb.lblPublisher.setVisibility(View.VISIBLE);

            vb.title.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            vb.title.setOnEditorActionListener(null);

            vb.publisher.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            vb.publisher.setOnEditorActionListener(this::onEditorAction);
        } else {
            vb.lblPublisher.setVisibility(View.GONE);

            vb.title.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            vb.title.setOnEditorActionListener(this::onEditorAction);
        }

        vb.author.setText(coordinator.getAuthorSearchText());
        vb.title.setText(coordinator.getTitleSearchText());
        vb.publisher.setText(coordinator.getPublisherSearchText());

        populateAuthorList();
        populatePublisherList();

        vb.btnSearch.setOnClickListener(v -> startSearch());
        explainSitesSupport(coordinator.getSiteList());
    }

    protected void explainSitesSupport(@Nullable final List<Site> sites) {
        final Context context = getContext();

        if (sites != null) {
            //noinspection DataFlowIssue
            final List<String> engines = sites
                    .stream()
                    .filter(Site::isActive)
                    .map(Site::getEngineId)
                    .filter(engineId -> engineId.supports(SearchEngine.SearchBy.Text))
                    .map(engineId -> engineId.getName(context))
                    .collect(Collectors.toList());

            if (!engines.isEmpty()) {
                // Explicitly let the user known which sites will be searched.
                vb.btnSearch.setEnabled(true);
                final int textColor = AttrUtils
                        .getColorInt(context, com.google.android.material.R.attr.colorOnBackground);
                vb.txtLimitations.setTextColor(textColor);
                vb.txtLimitations.setText(getString(R.string.info_site_list,
                                                    String.join(", ", engines)));
                return;
            }
        }
        // There are no sites which support searching by Text
        vb.btnSearch.setEnabled(false);
        //noinspection DataFlowIssue
        final int textColor = AttrUtils
                .getColorInt(context, com.google.android.material.R.attr.colorError);
        vb.txtLimitations.setTextColor(textColor);
        vb.txtLimitations.setText(getString(R.string.warning_no_site_supports_this_method,
                                            getString(R.string.lbl_author)
                                            + " / " + getString(R.string.lbl_title)));
    }

    private boolean onEditorAction(@NonNull final View view,
                                   final int actionId,
                                   @Nullable final KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            hideKeyboard(view);
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
        coordinator.setAuthorSearchText(vb.author.getText().toString().trim());
        //noinspection DataFlowIssue
        coordinator.setTitleSearchText(vb.title.getText().toString().trim());
        coordinator.setPublisherSearchText(vb.publisher.getText().toString().trim());
    }

    /**
     * Setup the adapter for the Author AutoCompleteTextView field.
     */
    private void populateAuthorList() {
        //noinspection DataFlowIssue
        authorAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAuthorNames(getContext()));
        vb.author.setAdapter(authorAdapter);
    }

    /**
     * Setup the adapter for the Publisher AutoCompleteTextView field.
     */
    private void populatePublisherList() {
        //noinspection DataFlowIssue
        publisherAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getPublisherNames(getContext()));
        vb.publisher.setAdapter(publisherAdapter);
    }

    @Override
    boolean onPreSearch() {
        viewToModel();

        final String authorSearchText = coordinator.getAuthorSearchText();
        if (!authorSearchText.isEmpty()) {
            // Always add the current search text (if not already present)
            // to the list of recent searches.
            if (authorAdapter.getPosition(authorSearchText) < 0) {
                if (vm.addAuthorName(authorSearchText)) {
                    // Add to adapter, in case search produces no results
                    authorAdapter.add(authorSearchText);
                }
            }
        }

        final String publisherSearchText = coordinator.getPublisherSearchText();
        if (vm.usePublisher() && !publisherSearchText.isEmpty()) {
            // Always add the current search text (if not already present)
            // to the list of recent searches.
            if (publisherAdapter.getPosition(publisherSearchText) < 0) {
                if (vm.addPublisherName(publisherSearchText)) {
                    // Add to adapter, in case search produces no results
                    publisherAdapter.add(publisherSearchText);
                }
            }
        }

        //sanity check
        final String titleSearchText = coordinator.getTitleSearchText();
        if (authorSearchText.isEmpty() && titleSearchText.isEmpty()) {
            Snackbar.make(vb.getRoot(), R.string.warning_requires_at_least_1_field,
                          Snackbar.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    @Override
    void onSearchResults(@NonNull final Book book) {
        // Don't check on any results... just accept them and create a new book.

        // If any of the search fields are not present in the result,
        // we add them manually as the template for a new book.

        if (!book.contains(DBKey.TITLE)) {
            book.putString(DBKey.TITLE, coordinator.getTitleSearchText());
        }

        final List<Author> authors = book.getAuthors();
        if (authors.isEmpty()) {
            // do NOT use the array, that's reserved for verified names.
            book.putString(SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR,
                           coordinator.getAuthorSearchText());
        }

        final List<Publisher> publishers = book.getPublishers();
        if (publishers.isEmpty()) {
            // do NOT use the array, that's reserved for verified names.
            book.putString(SearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER,
                           coordinator.getPublisherSearchText());
        }

        editBookLauncher.launch(new EditBookContract.Input(book, vm.getStyle()));
    }

    @Override
    void onClearSearchCriteria() {
        super.onClearSearchCriteria();
        vb.author.setText("");
        vb.title.setText("");
        vb.publisher.setText("");
    }

    /**
     * The user finished editing a book. Store results and refresh the dropdown lists.
     *
     * @param data from the edit
     */
    @Override
    void onBookEditingDone(@NonNull final EditBookOutput data) {
        vm.onBookEditingDone(data);

        // refresh, we could have modified/created Authors/Publishers while editing
        // (even when the edit was cancelled )
        populateAuthorList();
        populatePublisherList();
    }
}
